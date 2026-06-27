package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entity-tests the {@link UnitRosterService} roster contract: monotonic id
 * allocation, dense {@code [0, liveCount())} iteration, swap-and-pop release
 * moving the tail entity into the freed slot, stale-id lookups returning
 * {@link UnitRosterService#INVALID_INDEX}, and growth on overflow without index
 * corruption. The per-entity component columns are seeded by
 * {@link UnitRosterService#allocate} and read by id through the {@code r.world()}
 * facade.
 */
public class UnitRosterServiceTest {

    private static Entity unit(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    /** A marine pre-seeded with a secondary weapon, so allocate gives it the optional SECONDARY_WEAPON component. */
    private static Entity secondaryUnit(String label) {
        Entity u = unit(label);
        u.seedSecondaryWeapon = MarineSecondary.ROCKET_LAUNCHER;
        u.seedSecondaryAmmo = MarineSecondary.ROCKET_LAUNCHER.startingAmmo;
        return u;
    }

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    @Test
    public void allocateAssignsMonotonicIdsAndPacksDenseSlots() {
        UnitRosterService r = roster();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");

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
        UnitRosterService r = roster();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        long idC = r.allocate(c);

        // Release the middle entity — slot 0 should become c (the tail),
        // not b (which is unrelated to the swap target).
        r.release(idA);

        assertEquals(2, r.liveCount());
        assertFalse(r.isLive(idA));
        assertEquals(UnitRosterService.INVALID_INDEX, r.indexOf(idA));
        // c moved from slot 2 into slot 0; its id should now resolve to index 0.
        assertEquals(0, r.indexOf(idC));
        assertSame(c, r.get(0));
        // b stayed at slot 1.
        assertSame(b, r.get(1));
    }

    @Test
    public void releaseOfTailEntityIsSimplePop() {
        UnitRosterService r = roster();
        Entity a = unit("a");
        Entity b = unit("b");
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
        UnitRosterService r = roster();
        Entity a = unit("a");
        long idA = r.allocate(a);

        r.release(9999L);   // never allocated
        r.release(idA);
        r.release(idA);     // duplicate release — should not corrupt count

        assertEquals(0, r.liveCount());
        assertFalse(r.isLive(idA));
    }

    @Test
    public void staleIdAfterReleaseReturnsInvalidIndex() {
        UnitRosterService r = roster();
        Entity a = unit("a");
        long idA = r.allocate(a);
        r.release(idA);

        assertEquals(UnitRosterService.INVALID_INDEX, r.indexOf(idA));
        assertFalse(r.isLive(idA));
    }

    @Test
    public void backingArrayGrowsWithoutCorruptingIndices() {
        UnitRosterService r = roster();
        // Allocate past the initial capacity (64) so the doubling growth
        // path runs. Every previously-allocated id must still resolve to
        // the same dense slot.
        int n = 200;
        long[] ids = new long[n];
        Entity[] units = new Entity[n];
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
        UnitRosterService r = roster();
        Entity a = unit("a");
        Entity b = unit("b");
        r.allocate(a);
        long idB = r.allocate(b);

        r.release(idB);

        Entity[] arr = r.denseArray();
        assertSame(a, arr[0]);
        // The just-released tail slot must be nulled so the GC can reclaim
        // the unit even though the array reference outlives the entity.
        assertEquals(1, r.liveCount());
        assertNull(arr[1], "tail slot nulled after pop");
    }

    @Test
    public void newRegistryHasZeroLiveCount() {
        UnitRosterService r = roster();
        assertEquals(0, r.liveCount());
        assertFalse(r.isLive(1L));
        assertEquals(UnitRosterService.INVALID_INDEX, r.indexOf(1L));
        assertNotNull(r.denseArray());
    }

    @Test
    public void allocateRejectsAlreadyAllocatedUnit() {
        UnitRosterService r = roster();
        Entity a = unit("a");
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
        UnitRosterService r = roster();
        Entity a = unit("a");
        Entity b = unit("b");
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
    public void allocateSeedsHealthIntoTheEntityWorldFromUnitsSeedFields() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");
        // Pre-allocate: ctor seeded seedHp + seedMaxHp from type.maxHp
        // (MARINE_BLUE). hp is unreadable pre-allocate (no world entity yet),
        // so read the seed fields directly here.
        float typeMaxHp = u.seedMaxHp;
        assertTrue(typeMaxHp > 0f, "test prerequisite: type seeds a non-zero maxHp");
        assertEquals(typeMaxHp, u.seedHp, 1e-6f);

        r.allocate(u);

        // Post-allocate: hp lives in the entity world's HEALTH columns under the
        // minted id (migration step 3) — the by-id world facade reads it.
        assertEquals(typeMaxHp, w.hp(u.entityId), 1e-6f);
        assertEquals(typeMaxHp, w.maxHp(u.entityId), 1e-6f);
        assertTrue(r.isLive(u.entityId));
        // And the world entity carries the spawn-written IDENTITY alongside.
        assertTrue(r.entityWorld().has(u.entityId, r.components().IDENTITY));

        // setHp writes the world slot — the world is the sole canonical
        // store once allocated.
        w.setHp(u.entityId, 42f);
        assertEquals(42f, w.hp(u.entityId), 1e-6f);
    }

    @Test
    public void releaseDropsTheDenseSlotButHpStaysWorldSideUntilTheDeathTransmute() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");
        r.allocate(u);

        // Production order: every release path zeroes hp first (resolve /
        // cascade / TestUnits.kill), THEN releases the dense slot.
        w.setHp(u.entityId, 0f);
        r.release(u.entityId);

        // After release: the dense slot is dropped, and liveness reads the
        // world HEALTH — dead via hp <= 0 even though the component is still
        // present (the death drain's corpse transmute removes it later).
        assertFalse(r.isLive(u.entityId));
        assertEquals(-1, r.indexOf(u.entityId));
        assertFalse(r.isAliveById(u.entityId));
        assertTrue(r.entityWorld().has(u.entityId, r.components().HEALTH),
                "HEALTH survives release until the corpse transmute");
    }

    @Test
    public void releaseUpdatesDenseIdxOfTheSwappedTailUnit() {
        UnitRosterService r = roster();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);

        // Release the head — tail (c) swaps into slot 0; its index mapping must
        // update or by-index column reads through c would hit the wrong slot.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        // Every per-unit column is now id-keyed in the entity world (immune to
        // dense swaps by design), so the swap-and-pop moves only the dense
        // Entity[] slot + its id↔index mapping: the tail (c) must now occupy
        // slot 0 and resolve there.
        assertSame(c, r.get(0));
    }

    @Test
    public void allocateSeedsCellPosIntoTheEntityWorldFromUnitsSeedFields() {
        UnitRosterService r = roster();
        World w = r.world();
        // Entity ctor takes initial cellX/cellY, stamped into seedCellX/Y pre-alloc.
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 7, 3);

        r.allocate(u);

        // The cell pair lives in the world's POSITION columns under the minted
        // id (migration step 3b) — read/written through the by-id facade.
        assertEquals(7, w.cellX(u.entityId));
        assertEquals(3, w.cellY(u.entityId));

        w.setCellPos(u.entityId, 12, 9);
        assertEquals(12, w.cellX(u.entityId));
        assertEquals(9, w.cellY(u.entityId));
    }

    @Test
    public void cellSurvivesReleaseAndRidesTheDeathTransmute() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        r.allocate(u);

        w.setCellPos(u.entityId, 42, 17);
        w.setHp(u.entityId, 0f);
        r.release(u.entityId);

        // POSITION persists alive→dead — "the corpse keeps its cell" is now the
        // component's own lifecycle, not a DeathEvent re-write: release drops
        // only the dense slot, the world entity keeps its cell, and the corpse
        // transmute's row-move carries it.
        assertFalse(r.isLive(u.entityId));
        assertEquals(-1, r.indexOf(u.entityId));
        assertEquals(42, w.cellX(u.entityId));
        assertEquals(17, w.cellY(u.entityId));
    }

    @Test
    public void allocateCooldownTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, w.cooldownTimer(u.entityId), 1e-6f);

        w.setCooldownTimer(u.entityId, 0.3f);
        assertEquals(0.3f, w.cooldownTimer(u.entityId), 1e-6f);
    }

    @Test
    public void cooldownTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setCooldownTimer(c.entityId, 4.2f);

        // Releasing a swap-pops c into a's old dense slot — COMBAT is keyed by
        // entity id in the world, not by dense index, so c's value is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(4.2f, w.cooldownTimer(c.entityId), 1e-6f);
    }

    @Test
    public void allocateMoveProgressDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, w.moveProgress(u.entityId), 1e-6f);

        w.setMoveProgress(u.entityId, 0.2f);
        assertEquals(0.2f, w.moveProgress(u.entityId), 1e-6f);
    }

    @Test
    public void moveProgressIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setMoveProgress(c.entityId, 0.9f);

        // Releasing a swap-pops c into a's old dense slot — MOVEMENT is keyed by
        // entity id in the world, not by dense index, so c's value is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.9f, w.moveProgress(c.entityId), 1e-6f);
    }

    @Test
    public void allocatePathDefaultsToEmptySentinelAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        // The OBJECT path column seeds to the shared empty-path sentinel (a null
        // append would NPE every path reader); the cursor zero-inits.
        assertSame(GridPathfinder.EMPTY_PATH, w.path(u.entityId));
        assertEquals(0, w.pathIdx(u.entityId));

        int[] p = {3, 4, 5, 6};
        w.setPathRef(u.entityId, p);
        w.setPathIdx(u.entityId, 1);
        assertSame(p, w.path(u.entityId));
        assertEquals(1, w.pathIdx(u.entityId));
    }

    @Test
    public void pathIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        int[] p = {7, 8};
        w.setPathRef(c.entityId, p);
        w.setPathIdx(c.entityId, 1);

        // Releasing a swap-pops c into a's old dense slot — MOVEMENT is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertSame(p, w.path(c.entityId));
        assertEquals(1, w.pathIdx(c.entityId));
    }

    @Test
    public void allocateSeedsRenderPosIntoTheWorldComponent() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 5, 8);

        long id = r.allocate(u);

        // Seeded from the unit's pre-allocate cell into the universal
        // RENDER_POSITION world component, read by id.
        assertEquals(5f, w.renderX(id), 1e-6f);
        assertEquals(8f, w.renderY(id), 1e-6f);

        w.setRenderPos(id, 5.3f, 8.7f);
        assertEquals(5.3f, w.renderX(id), 1e-6f);
        assertEquals(8.7f, w.renderY(id), 1e-6f);
    }

    @Test
    public void renderPosSurvivesReleaseForTheCorpse() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        long id = r.allocate(u);

        w.setRenderPos(id, 3.5f, 7.2f);
        r.release(u.entityId);

        // Dropped from the live dense table...
        assertFalse(r.isLive(u.entityId));
        assertEquals(-1, r.indexOf(u.entityId));
        // ...but RENDER_POSITION is a universal world component kept off the
        // corpse-remove mask, so the dense-table release alone leaves it intact
        // (it rides the death transmute when the corpse forms — see
        // DeadBodySystemTest) and the entity still resolves where it fell.
        assertTrue(r.entityWorld().has(id, r.components().RENDER_POSITION));
        assertEquals(3.5f, w.renderX(id), 1e-6f);
        assertEquals(7.2f, w.renderY(id), 1e-6f);
    }

    @Test
    public void renderPosIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = new Entity("a", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        Entity b = new Entity("b", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        Entity c = new Entity("c", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        long idA = r.allocate(a);
        r.allocate(b);
        long idC = r.allocate(c);
        w.setRenderPos(idC, 11.5f, 22.3f);

        // Releasing a swap-pops c into a's old dense slot — render position is
        // id-keyed in the world, not dense index, so c's render pos is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(11.5f, w.renderX(idC), 1e-6f);
        assertEquals(22.3f, w.renderY(idC), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackDamageAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");
        float typeDmg = u.seedAttackDamage;
        assertTrue(typeDmg > 0f, "test prerequisite: type seeds a non-zero attackDamage");

        r.allocate(u);

        assertEquals(typeDmg, w.attackDamage(u.entityId), 1e-6f);

        w.setAttackDamage(u.entityId, 77f);
        assertEquals(77f, w.attackDamage(u.entityId), 1e-6f);
    }

    @Test
    public void attackDamageIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setAttackDamage(c.entityId, 55f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(55f, w.attackDamage(c.entityId), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackRangeAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");
        float typeRange = u.seedAttackRange;
        assertTrue(typeRange > 0f, "test prerequisite: type seeds a non-zero attackRange");

        r.allocate(u);

        assertEquals(typeRange, w.attackRange(u.entityId), 1e-6f);

        w.setAttackRange(u.entityId, 20f);
        assertEquals(20f, w.attackRange(u.entityId), 1e-6f);
    }

    @Test
    public void attackRangeIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setAttackRange(c.entityId, 99f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(99f, w.attackRange(c.entityId), 1e-6f);
    }

    @Test
    public void allocateSeedsAccuracyAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");
        float typeAcc = u.seedAccuracy;
        assertTrue(typeAcc > 0f, "test prerequisite: type seeds a non-zero accuracy");

        r.allocate(u);

        assertEquals(typeAcc, w.accuracy(u.entityId), 1e-6f);

        w.setAccuracy(u.entityId, 0.5f);
        assertEquals(0.5f, w.accuracy(u.entityId), 1e-6f);
    }

    @Test
    public void accuracyIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setAccuracy(c.entityId, 0.95f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.95f, w.accuracy(c.entityId), 1e-6f);
    }

    @Test
    public void allocateWithoutSecondaryLacksTheSecondaryWeaponComponent() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        // No seedSecondaryWeapon → the optional capability isn't in the unit's
        // archetype. Presence IS the capability — there's nothing else to check.
        assertFalse(w.hasSecondaryWeapon(u.entityId));
    }

    @Test
    public void allocateWithSecondarySeedsSpecAmmoAndDefaultTimers() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = secondaryUnit("u");

        r.allocate(u);

        assertTrue(w.hasSecondaryWeapon(u.entityId));
        assertSame(MarineSecondary.ROCKET_LAUNCHER, w.secondaryWeapon(u.entityId));
        assertEquals(MarineSecondary.ROCKET_LAUNCHER.startingAmmo, w.secondaryAmmo(u.entityId));
        // Mid-combat scalars start zeroed by the world's row append.
        assertEquals(0f, w.secondaryCooldownTimer(u.entityId), 1e-6f);
        assertEquals(0f, w.secondaryActionTimer(u.entityId), 1e-6f);
        assertEquals(0L, w.secondaryAimTargetId(u.entityId));
        assertFalse(w.secondaryFired(u.entityId));
    }

    @Test
    public void secondaryScalarsRoundTripThroughByIdAccessors() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = secondaryUnit("u");
        r.allocate(u);
        long id = u.entityId;

        w.setSecondaryAmmo(id, 2);
        w.setSecondaryCooldownTimer(id, 0.4f);
        w.setSecondaryActionTimer(id, 0.6f);
        w.setSecondaryAimTargetId(id, 7L);
        w.setSecondaryFired(id, true);

        assertEquals(2, w.secondaryAmmo(id));
        assertEquals(0.4f, w.secondaryCooldownTimer(id), 1e-6f);
        assertEquals(0.6f, w.secondaryActionTimer(id), 1e-6f);
        assertEquals(7L, w.secondaryAimTargetId(id));
        assertTrue(w.secondaryFired(id));
    }

    @Test
    public void secondaryStateIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = secondaryUnit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setSecondaryActionTimer(c.entityId, 0.7f);
        w.setSecondaryAimTargetId(c.entityId, 999L);

        // Releasing a swap-pops c into a's old dense slot — SECONDARY_WEAPON is a
        // world component keyed by entity id, untouched by the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.7f, w.secondaryActionTimer(c.entityId), 1e-6f);
        assertEquals(999L, w.secondaryAimTargetId(c.entityId));
    }

    @Test
    public void allocateBurstRemainingDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0, w.burstRemaining(u.entityId));

        w.setBurstRemaining(u.entityId, 1);
        assertEquals(1, w.burstRemaining(u.entityId));
    }

    @Test
    public void burstRemainingIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setBurstRemaining(c.entityId, 5);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(5, w.burstRemaining(c.entityId));
    }

    @Test
    public void allocateBurstTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, w.burstTimer(u.entityId), 1e-6f);

        w.setBurstTimer(u.entityId, 0.1f);
        assertEquals(0.1f, w.burstTimer(u.entityId), 1e-6f);
    }

    @Test
    public void burstTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setBurstTimer(c.entityId, 0.33f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.33f, w.burstTimer(c.entityId), 1e-6f);
    }

    @Test
    public void allocateBurstTargetIdDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0L, w.burstTargetId(u.entityId));

        w.setBurstTargetId(u.entityId, 9L);
        assertEquals(9L, w.burstTargetId(u.entityId));
    }

    @Test
    public void burstTargetIdIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setBurstTargetId(c.entityId, 777L);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(777L, w.burstTargetId(c.entityId));
    }

    @Test
    public void allocateTargetIdDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0L, w.targetId(u.entityId));

        w.setTargetId(u.entityId, 8L);
        assertEquals(8L, w.targetId(u.entityId));
    }

    @Test
    public void targetIdIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setTargetId(c.entityId, 642L);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(642L, w.targetId(c.entityId));
    }

    @Test
    public void allocateRepositionCooldownDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, w.repositionCooldown(u.entityId), 1e-6f);

        w.setRepositionCooldown(u.entityId, 0.75f);
        assertEquals(0.75f, w.repositionCooldown(u.entityId), 1e-6f);
    }

    @Test
    public void repositionCooldownIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setRepositionCooldown(c.entityId, 0.9f);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is keyed by
        // entity id in the world, not by dense index, so c's value is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.9f, w.repositionCooldown(c.entityId), 1e-6f);
    }

    @Test
    public void releaseOfReservedZeroSentinelIsNoOp() {
        UnitRosterService r = roster();
        Entity a = unit("a");
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
    public void allocateGivesAFreshUnitDefaultsAfterReusingAFreedSlot() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        long idA = r.allocate(a);
        // Dirty several mid-combat columns — all now id-keyed in the world:
        // COMBAT scalars and the AI_STATE fall-back cell.
        w.setCooldownTimer(a.entityId, 2.5f);
        w.setTargetId(a.entityId, 99L);
        w.setBurstRemaining(a.entityId, 3);
        w.setFallbackCell(a.entityId, 7, 8);
        r.release(idA);

        // A fresh unit reusing the freed dense slot 0 must see defaults: its
        // world row is a fresh per-id append (a's stale row persists under a's
        // own id), so COMBAT scalars are zero-init and allocate re-seeds the
        // AI_STATE fall-back cell to the -1/-1 sentinel (the one non-zero
        // default).
        Entity b = unit("b");
        r.allocate(b);
        assertEquals(0, r.indexOf(b.entityId));
        assertEquals(0f, w.cooldownTimer(b.entityId), 1e-6f);
        assertEquals(0L, w.targetId(b.entityId));
        assertEquals(0, w.burstRemaining(b.entityId));
        assertEquals(-1, w.fallbackCellX(b.entityId));
        assertEquals(-1, w.fallbackCellY(b.entityId));
    }

    @Test
    public void allocateFallbackCellDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");
        // The -1/-1 "no cached cell" sentinel must be seeded by allocate (a
        // zero-init world row would otherwise read (0,0) as a live destination).
        r.allocate(u);
        assertEquals(-1, w.fallbackCellX(u.entityId));
        assertEquals(-1, w.fallbackCellY(u.entityId));

        w.setFallbackCell(u.entityId, 12, 9);
        assertEquals(12, w.fallbackCellX(u.entityId));
        assertEquals(9, w.fallbackCellY(u.entityId));
    }

    @Test
    public void fallbackCellIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setFallbackCell(c.entityId, 99, 88);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(99, w.fallbackCellX(c.entityId));
        assertEquals(88, w.fallbackCellY(c.entityId));
    }

    @Test
    public void staticEmplacementsGetNoMovementOrAiStateComponents() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity marine = new Entity("m", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        Entity turret = new Entity("t", Faction.MARINE, UnitType.TURRET, 1, 1);
        Entity hub = new Entity("h", Faction.MARINE, UnitType.DRONE_HUB_STRUCTURE, 2, 2);
        r.allocate(marine);
        r.allocate(turret);
        r.allocate(hub);

        // A mobile unit is a mover AND a thinker; a static emplacement (turret,
        // drone hub; UnitType.isStatic) is neither — presence IS the capability.
        assertTrue(w.hasMovement(marine.entityId));
        assertTrue(w.hasAiState(marine.entityId));
        assertFalse(w.hasMovement(turret.entityId));
        assertFalse(w.hasAiState(turret.entityId));
        assertFalse(w.hasMovement(hub.entityId));
        assertFalse(w.hasAiState(hub.entityId));

        // The mobile unit's non-zero seeds still run (the mobile-gated allocate
        // block): the empty-path sentinel and the -1/-1 fall-back cell.
        assertSame(GridPathfinder.EMPTY_PATH, w.path(marine.entityId));
        assertEquals(-1, w.fallbackCellX(marine.entityId));

        // The field accessors are fail-loud on a unit that lacks the component.
        assertThrows(RuntimeException.class, () -> w.moveProgress(turret.entityId));
        assertThrows(RuntimeException.class, () -> w.repositionCooldown(hub.entityId));
    }

    @Test
    public void allocateFallbackTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, w.fallbackTimer(u.entityId), 1e-6f);

        w.setFallbackTimer(u.entityId, 1.25f);
        assertEquals(1.25f, w.fallbackTimer(u.entityId), 1e-6f);
    }

    @Test
    public void fallbackTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setFallbackTimer(c.entityId, 0.4f);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.4f, w.fallbackTimer(c.entityId), 1e-6f);
    }

    @Test
    public void allocateWanderDwellTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, w.wanderDwellTimer(u.entityId), 1e-6f);

        w.setWanderDwellTimer(u.entityId, 0.75f);
        assertEquals(0.75f, w.wanderDwellTimer(u.entityId), 1e-6f);
    }

    @Test
    public void wanderDwellTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        w.setWanderDwellTimer(c.entityId, 0.9f);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.9f, w.wanderDwellTimer(c.entityId), 1e-6f);
    }
}
