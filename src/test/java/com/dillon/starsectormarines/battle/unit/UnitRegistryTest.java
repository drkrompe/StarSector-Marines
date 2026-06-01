package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
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

        // After release: registry no longer holds the slot, but post-release
        // readers still holding the unit reference (test fixtures, any
        // corpse-side consumer) can read sane HP because release snapshotted
        // the moment-of-death value back onto localHp/localMaxHp.
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
    public void allocateSeedsCellPosFromUnitsLocalFieldsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        // Unit ctor takes initial cellX/cellY, stamped into localCellX/Y pre-alloc.
        Unit u = new Unit("u", Faction.MARINE, UnitType.MARINE_BLUE, 7, 3);

        r.allocate(u);

        assertEquals(7, r.getCellX(u.denseIdx));
        assertEquals(3, r.getCellY(u.denseIdx));
        assertEquals(7, u.getCellX());
        assertEquals(3, u.getCellY());

        // setCellPos routes through the registry now that the unit is allocated.
        u.setCellPos(12, 9);
        assertEquals(12, r.getCellX(u.denseIdx));
        assertEquals(9, r.getCellY(u.denseIdx));
    }

    @Test
    public void releaseSnapshotsCellPosBackToLocalFieldForPostReleaseReaders() {
        UnitRegistry r = new UnitRegistry();
        Unit u = new Unit("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        r.allocate(u);

        u.setCellPos(42, 17);
        r.release(u.entityId);

        // Post-release: the legacy units list keeps the corpse, and the
        // drone-crash sprite / equipment-drop emit reads cellX/Y off the
        // released unit. Without the snapshot they'd read 0 instead of the
        // moment-of-death cell.
        assertNull(u.registry);
        assertEquals(-1, u.denseIdx);
        assertEquals(42, u.getCellX());
        assertEquals(17, u.getCellY());
    }

    @Test
    public void releaseTailSwapMovesCellPosCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = new Unit("a", Faction.MARINE, UnitType.MARINE_BLUE, 1, 1);
        Unit b = new Unit("b", Faction.MARINE, UnitType.MARINE_BLUE, 2, 2);
        Unit c = new Unit("c", Faction.MARINE, UnitType.MARINE_BLUE, 3, 3);
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        // Move c so the swap-in carries fresh values, not the ctor seed.
        c.setCellPos(99, 88);

        // Release the head — tail (c) swaps into slot 0; its cellX/Y must
        // follow the swap or future accessor reads through c would index
        // the wrong slot.
        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(99, r.getCellX(0));
        assertEquals(88, r.getCellY(0));
        assertEquals(99, c.getCellX());
        assertEquals(88, c.getCellY());
    }

    @Test
    public void allocateCooldownTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getCooldownTimer(), 1e-6f);
        assertEquals(0f, r.getCooldownTimer(u.denseIdx), 1e-6f);

        u.setCooldownTimer(0.3f);
        assertEquals(0.3f, r.getCooldownTimer(u.denseIdx), 1e-6f);
        assertEquals(0.3f, u.getCooldownTimer(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesCooldownTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setCooldownTimer(4.2f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(4.2f, r.getCooldownTimer(0), 1e-6f);
        assertEquals(4.2f, c.getCooldownTimer(), 1e-6f);
    }

    @Test
    public void allocateMoveProgressDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getMoveProgress(), 1e-6f);
        assertEquals(0f, r.getMoveProgress(u.denseIdx), 1e-6f);

        u.setMoveProgress(0.2f);
        assertEquals(0.2f, r.getMoveProgress(u.denseIdx), 1e-6f);
        assertEquals(0.2f, u.getMoveProgress(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesMoveProgressCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setMoveProgress(0.9f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(0.9f, r.getMoveProgress(0), 1e-6f);
        assertEquals(0.9f, c.getMoveProgress(), 1e-6f);
    }

    @Test
    public void allocateSeedsRenderPosAndAccessorsRouteThroughService() {
        UnitRegistry r = new UnitRegistry();
        Unit u = new Unit("u", Faction.MARINE, UnitType.MARINE_BLUE, 5, 8);

        long id = r.allocate(u);

        // Seeded from the unit's pre-allocate cell into the decomposed service.
        assertEquals(5f, u.getRenderX(), 1e-6f);
        assertEquals(8f, u.getRenderY(), 1e-6f);
        assertEquals(5f, r.getRenderPositions().getX(id), 1e-6f);
        assertEquals(8f, r.getRenderPositions().getY(id), 1e-6f);

        u.setRenderPos(5.3f, 8.7f);
        assertEquals(5.3f, r.getRenderPositions().getX(id), 1e-6f);
        assertEquals(8.7f, r.getRenderPositions().getY(id), 1e-6f);
        assertEquals(5.3f, u.getRenderX(), 1e-6f);
        assertEquals(8.7f, u.getRenderY(), 1e-6f);
    }

    @Test
    public void renderPosSurvivesReleaseForTheCorpse() {
        UnitRegistry r = new UnitRegistry();
        Unit u = new Unit("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        long id = r.allocate(u);

        u.setRenderPos(3.5f, 7.2f);
        r.release(u.entityId);

        // Dropped from the live dense table...
        assertNull(u.registry);
        assertEquals(-1, u.denseIdx);
        // ...but render position lives in the entity-id-keyed service, which is
        // not cleared on release, so the corpse still resolves where it fell —
        // directly through the service, no local* snapshot involved.
        assertTrue(r.getRenderPositions().has(id));
        assertEquals(3.5f, u.getRenderX(), 1e-6f);
        assertEquals(7.2f, u.getRenderY(), 1e-6f);
        assertEquals(3.5f, r.getRenderPositions().getX(id), 1e-6f);
    }

    @Test
    public void renderPosIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Unit a = new Unit("a", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        Unit b = new Unit("b", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        Unit c = new Unit("c", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        long idA = r.allocate(a);
        r.allocate(b);
        long idC = r.allocate(c);
        c.setRenderPos(11.5f, 22.3f);

        // Releasing a swap-pops c into a's old dense slot — render position is
        // keyed by entity id, not dense index, so c's render pos is untouched.
        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(11.5f, r.getRenderPositions().getX(idC), 1e-6f);
        assertEquals(22.3f, r.getRenderPositions().getY(idC), 1e-6f);
        assertEquals(11.5f, c.getRenderX(), 1e-6f);
        assertEquals(22.3f, c.getRenderY(), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackDamageAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        float typeDmg = u.getAttackDamage();
        assertTrue(typeDmg > 0f, "test prerequisite: type seeds a non-zero attackDamage");

        r.allocate(u);

        assertEquals(typeDmg, r.getAttackDamage(u.denseIdx), 1e-6f);
        assertEquals(typeDmg, u.getAttackDamage(), 1e-6f);

        u.setAttackDamage(77f);
        assertEquals(77f, r.getAttackDamage(u.denseIdx), 1e-6f);
        assertEquals(77f, u.getAttackDamage(), 1e-6f);
    }

    @Test
    public void releaseSnapshotsAttackDamageBackToLocalField() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        r.allocate(u);

        u.setAttackDamage(33f);
        r.release(u.entityId);

        assertNull(u.registry);
        assertEquals(-1, u.denseIdx);
        assertEquals(33f, u.getAttackDamage(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesAttackDamageCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setAttackDamage(55f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(55f, r.getAttackDamage(0), 1e-6f);
        assertEquals(55f, c.getAttackDamage(), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackRangeAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        float typeRange = u.getAttackRange();
        assertTrue(typeRange > 0f, "test prerequisite: type seeds a non-zero attackRange");

        r.allocate(u);

        assertEquals(typeRange, r.getAttackRange(u.denseIdx), 1e-6f);
        assertEquals(typeRange, u.getAttackRange(), 1e-6f);

        u.setAttackRange(20f);
        assertEquals(20f, r.getAttackRange(u.denseIdx), 1e-6f);
        assertEquals(20f, u.getAttackRange(), 1e-6f);
    }

    @Test
    public void releaseSnapshotsAttackRangeBackToLocalField() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        r.allocate(u);

        u.setAttackRange(15f);
        r.release(u.entityId);

        assertNull(u.registry);
        assertEquals(-1, u.denseIdx);
        assertEquals(15f, u.getAttackRange(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesAttackRangeCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setAttackRange(99f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(99f, r.getAttackRange(0), 1e-6f);
        assertEquals(99f, c.getAttackRange(), 1e-6f);
    }

    @Test
    public void allocateSeedsAccuracyAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        float typeAcc = u.getAccuracy();
        assertTrue(typeAcc > 0f, "test prerequisite: type seeds a non-zero accuracy");

        r.allocate(u);

        assertEquals(typeAcc, r.getAccuracy(u.denseIdx), 1e-6f);
        assertEquals(typeAcc, u.getAccuracy(), 1e-6f);

        u.setAccuracy(0.5f);
        assertEquals(0.5f, r.getAccuracy(u.denseIdx), 1e-6f);
        assertEquals(0.5f, u.getAccuracy(), 1e-6f);
    }

    @Test
    public void releaseSnapshotsAccuracyBackToLocalField() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        r.allocate(u);

        u.setAccuracy(0.8f);
        r.release(u.entityId);

        assertNull(u.registry);
        assertEquals(-1, u.denseIdx);
        assertEquals(0.8f, u.getAccuracy(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesAccuracyCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setAccuracy(0.95f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(0.95f, r.getAccuracy(0), 1e-6f);
        assertEquals(0.95f, c.getAccuracy(), 1e-6f);
    }

    @Test
    public void allocateSecondaryCooldownTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getSecondaryCooldownTimer(), 1e-6f);
        assertEquals(0f, r.getSecondaryCooldownTimer(u.denseIdx), 1e-6f);

        u.setSecondaryCooldownTimer(0.4f);
        assertEquals(0.4f, r.getSecondaryCooldownTimer(u.denseIdx), 1e-6f);
        assertEquals(0.4f, u.getSecondaryCooldownTimer(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesSecondaryCooldownTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setSecondaryCooldownTimer(5.1f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(5.1f, r.getSecondaryCooldownTimer(0), 1e-6f);
        assertEquals(5.1f, c.getSecondaryCooldownTimer(), 1e-6f);
    }

    @Test
    public void allocateSecondaryActionTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getSecondaryActionTimer(), 1e-6f);
        assertEquals(0f, r.getSecondaryActionTimer(u.denseIdx), 1e-6f);

        u.setSecondaryActionTimer(0.6f);
        assertEquals(0.6f, r.getSecondaryActionTimer(u.denseIdx), 1e-6f);
        assertEquals(0.6f, u.getSecondaryActionTimer(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesSecondaryActionTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setSecondaryActionTimer(0.7f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(0.7f, r.getSecondaryActionTimer(0), 1e-6f);
        assertEquals(0.7f, c.getSecondaryActionTimer(), 1e-6f);
    }

    @Test
    public void allocateSecondaryAimTargetIdDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0L, u.getSecondaryAimTargetId());
        assertEquals(0L, r.getSecondaryAimTargetId(u.denseIdx));

        u.setSecondaryAimTargetId(7L);
        assertEquals(7L, r.getSecondaryAimTargetId(u.denseIdx));
        assertEquals(7L, u.getSecondaryAimTargetId());
    }

    @Test
    public void releaseTailSwapMovesSecondaryAimTargetIdCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setSecondaryAimTargetId(999L);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(999L, r.getSecondaryAimTargetId(0));
        assertEquals(999L, c.getSecondaryAimTargetId());
    }

    @Test
    public void allocateBurstRemainingDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0, u.getBurstRemaining());
        assertEquals(0, r.getBurstRemaining(u.denseIdx));

        u.setBurstRemaining(1);
        assertEquals(1, r.getBurstRemaining(u.denseIdx));
        assertEquals(1, u.getBurstRemaining());
    }

    @Test
    public void releaseTailSwapMovesBurstRemainingCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setBurstRemaining(5);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(5, r.getBurstRemaining(0));
        assertEquals(5, c.getBurstRemaining());
    }

    @Test
    public void allocateBurstTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getBurstTimer(), 1e-6f);
        assertEquals(0f, r.getBurstTimer(u.denseIdx), 1e-6f);

        u.setBurstTimer(0.1f);
        assertEquals(0.1f, r.getBurstTimer(u.denseIdx), 1e-6f);
        assertEquals(0.1f, u.getBurstTimer(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesBurstTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setBurstTimer(0.33f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(0.33f, r.getBurstTimer(0), 1e-6f);
        assertEquals(0.33f, c.getBurstTimer(), 1e-6f);
    }

    @Test
    public void allocateBurstTargetIdDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0L, u.getBurstTargetId());
        assertEquals(0L, r.getBurstTargetId(u.denseIdx));

        u.setBurstTargetId(9L);
        assertEquals(9L, r.getBurstTargetId(u.denseIdx));
        assertEquals(9L, u.getBurstTargetId());
    }

    @Test
    public void releaseTailSwapMovesBurstTargetIdCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setBurstTargetId(777L);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(777L, r.getBurstTargetId(0));
        assertEquals(777L, c.getBurstTargetId());
    }

    @Test
    public void allocateTargetIdDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0L, u.getTargetId());
        assertEquals(0L, r.getTargetId(u.denseIdx));

        u.setTargetId(8L);
        assertEquals(8L, r.getTargetId(u.denseIdx));
        assertEquals(8L, u.getTargetId());
    }

    @Test
    public void releaseTailSwapMovesTargetIdCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setTargetId(642L);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(642L, r.getTargetId(0));
        assertEquals(642L, c.getTargetId());
    }

    @Test
    public void allocateRepositionCooldownDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getRepositionCooldown(), 1e-6f);
        assertEquals(0f, r.getRepositionCooldown(u.denseIdx), 1e-6f);

        u.setRepositionCooldown(0.75f);
        assertEquals(0.75f, r.getRepositionCooldown(u.denseIdx), 1e-6f);
        assertEquals(0.75f, u.getRepositionCooldown(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesRepositionCooldownCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setRepositionCooldown(0.9f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(0.9f, r.getRepositionCooldown(0), 1e-6f);
        assertEquals(0.9f, c.getRepositionCooldown(), 1e-6f);
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

    @Test
    public void allocateResetsMidCombatColumnsWhenReusingAFreedSlot() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        long idA = r.allocate(a);
        // Dirty several mid-combat columns on slot 0.
        a.setCooldownTimer(2.5f);
        a.setTargetId(99L);
        a.setBurstRemaining(3);
        a.setFallbackCell(7, 8);
        r.release(idA);

        // A fresh unit reusing slot 0 must see defaults, not the stale values.
        Unit b = unit("b");
        r.allocate(b);
        assertEquals(0, b.denseIdx);
        assertEquals(0f, b.getCooldownTimer(), 1e-6f);
        assertEquals(0L, b.getTargetId());
        assertEquals(0, b.getBurstRemaining());
        assertEquals(-1, b.getFallbackCellX());
        assertEquals(-1, b.getFallbackCellY());
    }

    @Test
    public void midCombatAccessorsReturnDefaultsWhenUnregistered() {
        // Mid-combat columns carry no local* shadow on Unit, so their accessors
        // must be null-safe for the unregistered window — pre-allocate AND the
        // released-corpse case that lives on the legacy units list and gets
        // iterated by systems like InfantryWeapons.tick (regression: that path
        // called getBurstRemaining() before the isAlive() gate and NPE'd).
        Unit fresh = unit("fresh");   // never allocated → registry == null
        assertEquals(0, fresh.getBurstRemaining());
        assertEquals(0L, fresh.getTargetId());
        assertEquals(0f, fresh.getCooldownTimer(), 1e-6f);
        assertEquals(-1, fresh.getFallbackCellX());
        // Setters on an unregistered unit are no-ops, not throws.
        fresh.setBurstRemaining(5);
        fresh.setTargetId(7L);
        assertEquals(0, fresh.getBurstRemaining());
        assertEquals(0L, fresh.getTargetId());

        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        r.allocate(u);
        u.setBurstRemaining(3);
        u.setBurstTimer(0.5f);
        r.release(u.entityId);   // corpse: registry == null again
        assertEquals(0, u.getBurstRemaining());
        assertEquals(0f, u.getBurstTimer(), 1e-6f);
        assertEquals(0L, u.getTargetId());
    }

    @Test
    public void allocateFallbackCellDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        // Default -1/-1 sentinel must ride the seed through allocate.
        r.allocate(u);
        assertEquals(-1, r.getFallbackCellX(u.denseIdx));
        assertEquals(-1, r.getFallbackCellY(u.denseIdx));
        assertEquals(-1, u.getFallbackCellX());
        assertEquals(-1, u.getFallbackCellY());

        u.setFallbackCell(12, 9);
        assertEquals(12, r.getFallbackCellX(u.denseIdx));
        assertEquals(9, r.getFallbackCellY(u.denseIdx));
        assertEquals(12, u.getFallbackCellX());
        assertEquals(9, u.getFallbackCellY());
    }

    @Test
    public void releaseTailSwapMovesFallbackCellCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setFallbackCell(99, 88);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(99, r.getFallbackCellX(0));
        assertEquals(88, r.getFallbackCellY(0));
        assertEquals(99, c.getFallbackCellX());
        assertEquals(88, c.getFallbackCellY());
    }

    @Test
    public void allocateFallbackTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getFallbackTimer(), 1e-6f);
        assertEquals(0f, r.getFallbackTimer(u.denseIdx), 1e-6f);

        u.setFallbackTimer(1.25f);
        assertEquals(1.25f, r.getFallbackTimer(u.denseIdx), 1e-6f);
        assertEquals(1.25f, u.getFallbackTimer(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesFallbackTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setFallbackTimer(0.4f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(0.4f, r.getFallbackTimer(0), 1e-6f);
        assertEquals(0.4f, c.getFallbackTimer(), 1e-6f);
    }

    @Test
    public void allocateWanderDwellTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, u.getWanderDwellTimer(), 1e-6f);
        assertEquals(0f, r.getWanderDwellTimer(u.denseIdx), 1e-6f);

        u.setWanderDwellTimer(0.75f);
        assertEquals(0.75f, r.getWanderDwellTimer(u.denseIdx), 1e-6f);
        assertEquals(0.75f, u.getWanderDwellTimer(), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesWanderDwellTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Unit a = unit("a");
        Unit b = unit("b");
        Unit c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        c.setWanderDwellTimer(0.9f);

        r.release(idA);

        assertEquals(0, c.denseIdx);
        assertEquals(0.9f, r.getWanderDwellTimer(0), 1e-6f);
        assertEquals(0.9f, c.getWanderDwellTimer(), 1e-6f);
    }
}
