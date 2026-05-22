package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-tests the {@link UnitRegistry} contract: monotonic id allocation,
 * dense {@code [0, liveCount())} iteration, swap-and-pop release moving
 * the tail entity into the freed slot, stale-id lookups returning
 * {@link UnitRegistry#INVALID_INDEX}, growth on overflow without index
 * corruption.
 */
public class UnitRegistryTest {

    private static Unit unit(String label) {
        return new Unit(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void allocateAssignsMonotonicIdsAndPacksDenseSlots() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");

        long idA = r.allocate(a);
        long idB = r.allocate(b);
        long idC = r.allocate(c);

        assertTrue(idA > 0L, "ids start at 1");
        assertTrue(idB > idA);
        assertTrue(idC > idB);
        assertEquals(idA, a.entityId);
        assertEquals(idB, b.entityId);
        assertEquals(idC, c.entityId);
        assertEquals(3, r.liveCount());
        assertSame(a, r.get(0));
        assertSame(b, r.get(1));
        assertSame(c, r.get(2));
    }

    @Test
    public void releaseSwapsTailIntoFreedSlot() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        long idC = r.allocate(c);

        // Release the middle entity — slot 0 should become c (the tail),
        // not b (which is unrelated to the swap target).
        r.release(idA);

        assertEquals(2, r.liveCount());
        assertFalse(r.isLive(idA));
        assertEquals(UnitRegistry.INVALID_INDEX, r.indexOf(idA));
        // c moved from slot 2 into slot 0; its id should now resolve to index 0.
        assertEquals(0, r.indexOf(idC));
        assertSame(c, r.get(0));
        // b stayed at slot 1.
        assertSame(b, r.get(1));
    }

    @Test
    public void releaseOfTailEntityIsSimplePop() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        long idA = r.allocate(a);
        long idB = r.allocate(b);

        r.release(idB);

        assertEquals(1, r.liveCount());
        assertFalse(r.isLive(idB));
        assertTrue(r.isLive(idA));
        assertEquals(0, r.indexOf(idA));
        assertSame(a, r.get(0));
    }

    @Test
    public void releaseOfUnknownIdIsNoOp() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        long idA = r.allocate(a);

        r.release(9999L);   // never allocated
        r.release(idA);
        r.release(idA);     // duplicate release — should not corrupt count

        assertEquals(0, r.liveCount());
        assertFalse(r.isLive(idA));
    }

    @Test
    public void staleIdAfterReleaseReturnsInvalidIndex() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        long idA = r.allocate(a);
        r.release(idA);

        assertEquals(UnitRegistry.INVALID_INDEX, r.indexOf(idA));
        assertFalse(r.isLive(idA));
    }

    @Test
    public void backingArrayGrowsWithoutCorruptingIndices() {
        UnitRegistry r = new UnitRegistry();
        // Allocate past the initial capacity (64) so the doubling growth
        // path runs. Every previously-allocated id must still resolve to
        // the same dense slot.
        int n = 200;
        long[] ids = new long[n];
        Unit[] units = new Unit[n];
        for (int i = 0; i < n; i++) {
            units[i] = unit("u" + i);
            ids[i] = r.allocate(units[i]);
        }

        assertEquals(n, r.liveCount());
        for (int i = 0; i < n; i++) {
            assertEquals(i, r.indexOf(ids[i]), "id " + i + " resolves to original slot");
            assertSame(units[i], r.get(i));
        }
    }

    @Test
    public void denseArrayNullsTheFreedTailSlot() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        r.allocate(a);
        long idB = r.allocate(b);

        r.release(idB);

        Unit[] arr = r.denseArray();
        assertSame(a, arr[0]);
        // The just-released tail slot must be nulled so the GC can reclaim
        // the unit even though the array reference outlives the entity.
        assertEquals(1, r.liveCount());
        assertNull(arr[1], "tail slot nulled after pop");
    }

    @Test
    public void newRegistryHasZeroLiveCount() {
        UnitRegistry r = new UnitRegistry();
        assertEquals(0, r.liveCount());
        assertFalse(r.isLive(1L));
        assertEquals(UnitRegistry.INVALID_INDEX, r.indexOf(1L));
        assertNotNull(r.denseArray());
    }

    @Test
    public void allocateRejectsAlreadyAllocatedUnit() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        r.allocate(a);
        // Same instance, second allocate — would otherwise mint a new id and
        // leave the old id->slot mapping stale, so a later release on the
        // old id would null a slot the new id still resolves to.
        assertThrows(IllegalStateException.class, () -> r.allocate(a));
        // Registry state unchanged by the rejected call.
        assertEquals(1, r.liveCount());
    }

    @Test
    public void getOrNullResolvesAliveReturnsNullForReleasedAndZeroSentinel() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        long idA = r.allocate(a);
        long idB = r.allocate(b);

        // Alive id → its unit.
        assertSame(a, r.getOrNull(idA));
        assertSame(b, r.getOrNull(idB));

        // Released id → null (the dangling-ref case the helper exists for).
        r.release(idA);
        assertNull(r.getOrNull(idA));
        // Sibling still resolves.
        assertSame(b, r.getOrNull(idB));

        // Reserved 0L sentinel → null without probing the map.
        assertNull(r.getOrNull(0L));

        // Never-allocated id → null.
        assertNull(r.getOrNull(9999L));
    }

    @Test
    public void allocateSeedsHpFromUnitsLocalFieldsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        // Pre-allocate: ctor seeded localHp from type.maxHp (MARINE_BLUE),
        // accessors fall through to the local field.
        float typeMaxHp = u.getMaxHp();
        assertTrue(typeMaxHp > 0f, "test prerequisite: type seeds a non-zero maxHp");

        r.allocate(u);

        // Post-allocate: getter reads the registry slot, which should mirror
        // the localHp/localMaxHp values from the pre-allocate ctor.
        assertEquals(typeMaxHp, r.getHp(u.denseIdx), 1e-6f);
        assertEquals(typeMaxHp, r.getMaxHp(u.denseIdx), 1e-6f);
        assertEquals(typeMaxHp, u.getHp(), 1e-6f);
        assertSame(r, u.registry);

        // A setHp call routes through the registry, not the now-stale local
        // field. The two end up disagreeing — that's the whole point of the
        // accessor; the local field is a pre-/post-life snapshot, not canon.
        u.setHp(42f);
        assertEquals(42f, r.getHp(u.denseIdx), 1e-6f);
        assertEquals(42f, u.getHp(), 1e-6f);
    }

    @Test
    public void releaseSnapshotsHpBackToLocalFieldForPostReleaseReaders() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        r.allocate(u);

        u.setHp(17f);
        u.setMaxHp(99f);
        r.release(u.entityId);

        // After release: registry no longer holds the slot, but legacy
        // consumers iterating sim.getUnits() can still read sane HP from the
        // unit reference because release snapshotted the moment-of-death
        // value back onto localHp/localMaxHp.
        assertNull(u.registry);
        assertEquals(-1, u.denseIdx);
        assertEquals(17f, u.getHp(), 1e-6f);
        assertEquals(99f, u.getMaxHp(), 1e-6f);
    }

    @Test
    public void releaseUpdatesDenseIdxOfTheSwappedTailUnit() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);

        // Release the head — tail (c) swaps into slot 0; its denseIdx must
        // update or future getHp/setHp through c would index the wrong slot.
        r.release(idA);

        assertEquals(0, c.denseIdx);
        // And c's hp accessor still resolves through the registry correctly.
        c.setHp(123f);
        assertEquals(123f, r.getHp(0), 1e-6f);
    }

    @Test
    public void releaseOfReservedZeroSentinelIsNoOp() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        long idA = r.allocate(a);
        // Setup-discarded units (constructed but never registered) carry
        // entityId == 0. Routing that into release() must not corrupt the
        // live entry — and crucially must not bump any "missing key" path
        // that could be confused with a real id later.
        r.release(0L);
        assertEquals(1, r.liveCount());
        assertTrue(r.isLive(idA));
        assertEquals(0, r.indexOf(idA));
    }
}
