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
    public void allocateSeedsHpFromUnitsSeedFieldsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        // Pre-allocate: ctor seeded seedHp + seedMaxHp from type.maxHp
        // (MARINE_BLUE). The hp/maxHp accessors are fail-loud pre-allocate,
        // so read the seed fields directly here.
        float typeMaxHp = u.seedMaxHp;
        assertTrue(typeMaxHp > 0f, "test prerequisite: type seeds a non-zero maxHp");
        assertEquals(typeMaxHp, u.seedHp, 1e-6f);

        r.allocate(u);

        // Post-allocate: getter reads the registry slot, which should mirror
        // the seedHp / seedMaxHp values from the pre-allocate ctor.
        assertEquals(typeMaxHp, r.getHp(r.indexOf(u.entityId)), 1e-6f);
        assertEquals(typeMaxHp, r.getMaxHp(r.indexOf(u.entityId)), 1e-6f);
        assertTrue(r.isLive(u.entityId));

        // setHp routes through the registry slot — there is no local hp field
        // anymore; the registry is the sole canonical store once allocated.
        r.setHp(r.indexOf(u.entityId), 42f);
        assertEquals(42f, r.getHp(r.indexOf(u.entityId)), 1e-6f);
    }

    @Test
    public void releaseMarksUnitDeadViaRegistryNullWithNoHpSnapshot() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        r.allocate(u);

        r.setHp(r.indexOf(u.entityId), 17f);
        r.release(u.entityId);

        // After release: the registry pointer is nulled and the dense slot is
        // dropped. There is NO post-release hp snapshot anymore — held-ref
        // liveness goes through isAlive(), which short-circuits on the
        // registry==null release marker and reports the corpse dead. getHp()
        // itself is fail-loud post-release (a programming error to call), just
        // like getMaxHp().
        assertFalse(r.isLive(u.entityId));
        assertEquals(-1, r.indexOf(u.entityId));
        assertFalse(r.isAliveById(u.entityId));
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

        assertEquals(0, r.indexOf(c.entityId));
        // And c's hp accessor still resolves through the registry correctly.
        r.setHp(r.indexOf(c.entityId), 123f);
        assertEquals(123f, r.getHp(0), 1e-6f);
    }

    @Test
    public void allocateSeedsCellPosFromUnitsSeedFieldsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        // Unit ctor takes initial cellX/cellY, stamped into seedCellX/Y pre-alloc.
        Unit u = new Unit("u", Faction.MARINE, UnitType.MARINE_BLUE, 7, 3);

        r.allocate(u);

        assertEquals(7, r.getCellX(r.indexOf(u.entityId)));
        assertEquals(3, r.getCellY(r.indexOf(u.entityId)));

        // setCellPos routes through the registry now that the unit is allocated.
        r.setCellPos(r.indexOf(u.entityId), 12, 9);
        assertEquals(12, r.getCellX(r.indexOf(u.entityId)));
        assertEquals(9, r.getCellY(r.indexOf(u.entityId)));
    }

    @Test
    public void releaseDoesNotSnapshotCellPosCellIsSeedOnly() {
        UnitRegistry r = new UnitRegistry();
        Unit u = new Unit("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        r.allocate(u);

        r.setCellPos(r.indexOf(u.entityId), 42, 17);
        r.release(u.entityId);

        // Cell is Group-C seed-only now: release no longer snapshots it back.
        // The death cell its former post-release readers (turret/hub demolition,
        // mech wreck) want travels on the DeathEvent snapshot instead, so the
        // cell accessors are fail-loud on a released unit — like the Group-S
        // stats and the mid-combat columns.
        assertFalse(r.isLive(u.entityId));
        assertEquals(-1, r.indexOf(u.entityId));
        assertThrows(IllegalArgumentException.class, () -> r.requireLiveIndex(u.entityId));
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
        r.setCellPos(r.indexOf(c.entityId), 99, 88);

        // Release the head — tail (c) swaps into slot 0; its cellX/Y must
        // follow the swap or future accessor reads through c would index
        // the wrong slot.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(99, r.getCellX(0));
        assertEquals(88, r.getCellY(0));
    }

    @Test
    public void allocateCooldownTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getCooldownTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setCooldownTimer(r.indexOf(u.entityId), 0.3f);
        assertEquals(0.3f, r.getCooldownTimer(r.indexOf(u.entityId)), 1e-6f);
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
        r.setCooldownTimer(r.indexOf(c.entityId), 4.2f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(4.2f, r.getCooldownTimer(0), 1e-6f);
    }

    @Test
    public void allocateMoveProgressDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getMoveProgress(r.indexOf(u.entityId)), 1e-6f);

        r.setMoveProgress(r.indexOf(u.entityId), 0.2f);
        assertEquals(0.2f, r.getMoveProgress(r.indexOf(u.entityId)), 1e-6f);
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
        r.setMoveProgress(r.indexOf(c.entityId), 0.9f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.9f, r.getMoveProgress(0), 1e-6f);
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
        assertFalse(r.isLive(u.entityId));
        assertEquals(-1, r.indexOf(u.entityId));
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

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(11.5f, r.getRenderPositions().getX(idC), 1e-6f);
        assertEquals(22.3f, r.getRenderPositions().getY(idC), 1e-6f);
        assertEquals(11.5f, c.getRenderX(), 1e-6f);
        assertEquals(22.3f, c.getRenderY(), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackDamageAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        float typeDmg = u.seedAttackDamage;
        assertTrue(typeDmg > 0f, "test prerequisite: type seeds a non-zero attackDamage");

        r.allocate(u);

        assertEquals(typeDmg, r.getAttackDamage(r.indexOf(u.entityId)), 1e-6f);

        r.setAttackDamage(r.indexOf(u.entityId), 77f);
        assertEquals(77f, r.getAttackDamage(r.indexOf(u.entityId)), 1e-6f);
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
        r.setAttackDamage(r.indexOf(c.entityId), 55f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(55f, r.getAttackDamage(0), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackRangeAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        float typeRange = u.seedAttackRange;
        assertTrue(typeRange > 0f, "test prerequisite: type seeds a non-zero attackRange");

        r.allocate(u);

        assertEquals(typeRange, r.getAttackRange(r.indexOf(u.entityId)), 1e-6f);

        r.setAttackRange(r.indexOf(u.entityId), 20f);
        assertEquals(20f, r.getAttackRange(r.indexOf(u.entityId)), 1e-6f);
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
        r.setAttackRange(r.indexOf(c.entityId), 99f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(99f, r.getAttackRange(0), 1e-6f);
    }

    @Test
    public void allocateSeedsAccuracyAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        float typeAcc = u.seedAccuracy;
        assertTrue(typeAcc > 0f, "test prerequisite: type seeds a non-zero accuracy");

        r.allocate(u);

        assertEquals(typeAcc, r.getAccuracy(r.indexOf(u.entityId)), 1e-6f);

        r.setAccuracy(r.indexOf(u.entityId), 0.5f);
        assertEquals(0.5f, r.getAccuracy(r.indexOf(u.entityId)), 1e-6f);
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
        r.setAccuracy(r.indexOf(c.entityId), 0.95f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.95f, r.getAccuracy(0), 1e-6f);
    }

    @Test
    public void allocateSecondaryCooldownTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getSecondaryCooldownTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setSecondaryCooldownTimer(r.indexOf(u.entityId), 0.4f);
        assertEquals(0.4f, r.getSecondaryCooldownTimer(r.indexOf(u.entityId)), 1e-6f);
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
        r.setSecondaryCooldownTimer(r.indexOf(c.entityId), 5.1f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(5.1f, r.getSecondaryCooldownTimer(0), 1e-6f);
    }

    @Test
    public void allocateSecondaryActionTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getSecondaryActionTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setSecondaryActionTimer(r.indexOf(u.entityId), 0.6f);
        assertEquals(0.6f, r.getSecondaryActionTimer(r.indexOf(u.entityId)), 1e-6f);
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
        r.setSecondaryActionTimer(r.indexOf(c.entityId), 0.7f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.7f, r.getSecondaryActionTimer(0), 1e-6f);
    }

    @Test
    public void allocateSecondaryAimTargetIdDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0L, r.getSecondaryAimTargetId(r.indexOf(u.entityId)));

        r.setSecondaryAimTargetId(r.indexOf(u.entityId), 7L);
        assertEquals(7L, r.getSecondaryAimTargetId(r.indexOf(u.entityId)));
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
        r.setSecondaryAimTargetId(r.indexOf(c.entityId), 999L);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(999L, r.getSecondaryAimTargetId(0));
    }

    @Test
    public void allocateBurstRemainingDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0, r.getBurstRemaining(r.indexOf(u.entityId)));

        r.setBurstRemaining(r.indexOf(u.entityId), 1);
        assertEquals(1, r.getBurstRemaining(r.indexOf(u.entityId)));
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
        r.setBurstRemaining(r.indexOf(c.entityId), 5);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(5, r.getBurstRemaining(0));
    }

    @Test
    public void allocateBurstTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getBurstTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setBurstTimer(r.indexOf(u.entityId), 0.1f);
        assertEquals(0.1f, r.getBurstTimer(r.indexOf(u.entityId)), 1e-6f);
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
        r.setBurstTimer(r.indexOf(c.entityId), 0.33f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.33f, r.getBurstTimer(0), 1e-6f);
    }

    @Test
    public void allocateBurstTargetIdDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0L, r.getBurstTargetId(r.indexOf(u.entityId)));

        r.setBurstTargetId(r.indexOf(u.entityId), 9L);
        assertEquals(9L, r.getBurstTargetId(r.indexOf(u.entityId)));
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
        r.setBurstTargetId(r.indexOf(c.entityId), 777L);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(777L, r.getBurstTargetId(0));
    }

    @Test
    public void allocateTargetIdDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0L, r.getTargetId(r.indexOf(u.entityId)));

        r.setTargetId(r.indexOf(u.entityId), 8L);
        assertEquals(8L, r.getTargetId(r.indexOf(u.entityId)));
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
        r.setTargetId(r.indexOf(c.entityId), 642L);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(642L, r.getTargetId(0));
    }

    @Test
    public void allocateRepositionCooldownDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getRepositionCooldown(r.indexOf(u.entityId)), 1e-6f);

        r.setRepositionCooldown(r.indexOf(u.entityId), 0.75f);
        assertEquals(0.75f, r.getRepositionCooldown(r.indexOf(u.entityId)), 1e-6f);
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
        r.setRepositionCooldown(r.indexOf(c.entityId), 0.9f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.9f, r.getRepositionCooldown(0), 1e-6f);
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
        r.setCooldownTimer(r.indexOf(a.entityId), 2.5f);
        r.setTargetId(r.indexOf(a.entityId), 99L);
        r.setBurstRemaining(r.indexOf(a.entityId), 3);
        r.setFallbackCell(r.indexOf(a.entityId), 7, 8);
        r.release(idA);

        // A fresh unit reusing slot 0 must see defaults, not the stale values.
        Unit b = unit("b");
        r.allocate(b);
        assertEquals(0, r.indexOf(b.entityId));
        assertEquals(0f, r.getCooldownTimer(r.indexOf(b.entityId)), 1e-6f);
        assertEquals(0L, r.getTargetId(r.indexOf(b.entityId)));
        assertEquals(0, r.getBurstRemaining(r.indexOf(b.entityId)));
        assertEquals(-1, r.getFallbackCellX(r.indexOf(b.entityId)));
        assertEquals(-1, r.getFallbackCellY(r.indexOf(b.entityId)));
    }

    @Test
    public void allocateFallbackCellDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        // Default -1/-1 sentinel must ride the seed through allocate.
        r.allocate(u);
        assertEquals(-1, r.getFallbackCellX(r.indexOf(u.entityId)));
        assertEquals(-1, r.getFallbackCellY(r.indexOf(u.entityId)));

        r.setFallbackCell(r.indexOf(u.entityId), 12, 9);
        assertEquals(12, r.getFallbackCellX(r.indexOf(u.entityId)));
        assertEquals(9, r.getFallbackCellY(r.indexOf(u.entityId)));
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
        r.setFallbackCell(r.indexOf(c.entityId), 99, 88);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(99, r.getFallbackCellX(0));
        assertEquals(88, r.getFallbackCellY(0));
    }

    @Test
    public void allocateFallbackTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getFallbackTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setFallbackTimer(r.indexOf(u.entityId), 1.25f);
        assertEquals(1.25f, r.getFallbackTimer(r.indexOf(u.entityId)), 1e-6f);
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
        r.setFallbackTimer(r.indexOf(c.entityId), 0.4f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.4f, r.getFallbackTimer(0), 1e-6f);
    }

    @Test
    public void allocateWanderDwellTimerDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getWanderDwellTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setWanderDwellTimer(r.indexOf(u.entityId), 0.75f);
        assertEquals(0.75f, r.getWanderDwellTimer(r.indexOf(u.entityId)), 1e-6f);
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
        r.setWanderDwellTimer(r.indexOf(c.entityId), 0.9f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.9f, r.getWanderDwellTimer(0), 1e-6f);
    }
}
