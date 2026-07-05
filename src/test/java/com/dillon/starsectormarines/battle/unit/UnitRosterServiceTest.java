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
 * {@link UnitRosterService#spawn} and read by id through the {@code r.world()}
 * facade.
 */
public class UnitRosterServiceTest {

    private static EntitySpec unit(String label) {
        return new EntitySpec(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    /** A marine pre-seeded with a secondary weapon, so spawn gives it the optional SECONDARY_WEAPON component. */
    private static EntitySpec secondaryUnit(String label) {
        return unit(label).secondary(MarineSecondary.ROCKET_LAUNCHER, MarineSecondary.ROCKET_LAUNCHER.startingAmmo);
    }

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    @Test
    public void allocateAssignsMonotonicIdsAndPacksDenseSlots() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));

        long idA = a;
        long idB = b;
        long idC = c;

        assertTrue(idA > 0L, "ids start at 1");
        assertTrue(idB > idA);
        assertTrue(idC > idB);
        assertEquals(idA, a);
        assertEquals(idB, b);
        assertEquals(idC, c);
        assertEquals(3, r.liveCount());
        assertEquals(a, r.get(0).entityId);
        assertEquals(b, r.get(1).entityId);
        assertEquals(c, r.get(2).entityId);
    }

    @Test
    public void releaseSwapsTailIntoFreedSlot() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        long idC = c;

        // Release the middle entity — slot 0 should become c (the tail),
        // not b (which is unrelated to the swap target).
        r.release(idA);

        assertEquals(2, r.liveCount());
        assertFalse(r.isLive(idA));
        assertEquals(UnitRosterService.INVALID_INDEX, r.indexOf(idA));
        // c moved from slot 2 into slot 0; its id should now resolve to index 0.
        assertEquals(0, r.indexOf(idC));
        assertEquals(c, r.get(0).entityId);
        // b stayed at slot 1.
        assertEquals(b, r.get(1).entityId);
    }

    @Test
    public void releaseOfTailEntityIsSimplePop() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long idA = a;
        long idB = b;

        r.release(idB);

        assertEquals(1, r.liveCount());
        assertFalse(r.isLive(idB));
        assertTrue(r.isLive(idA));
        assertEquals(0, r.indexOf(idA));
        assertEquals(a, r.get(0).entityId);
    }

    @Test
    public void releaseOfUnknownIdIsNoOp() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long idA = a;

        r.release(9999L);   // never allocated
        r.release(idA);
        r.release(idA);     // duplicate release — should not corrupt count

        assertEquals(0, r.liveCount());
        assertFalse(r.isLive(idA));
    }

    @Test
    public void staleIdAfterReleaseReturnsInvalidIndex() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long idA = a;
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
        long[] units = new long[n];
        for (int i = 0; i < n; i++) {
            units[i] = r.spawn(unit("u" + i));
            ids[i] = units[i];
        }

        assertEquals(n, r.liveCount());
        for (int i = 0; i < n; i++) {
            assertEquals(i, r.indexOf(ids[i]), "id " + i + " resolves to original slot");
            assertEquals(units[i], r.get(i).entityId);
        }
    }

    @Test
    public void denseArrayNullsTheFreedTailSlot() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long idB = b;

        r.release(idB);

        Entity[] arr = r.denseArray();
        assertEquals(a, arr[0].entityId);
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
    public void getOrNullResolvesAliveReturnsNullForReleasedAndZeroSentinel() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long idA = a;
        long idB = b;

        // Alive id → its unit.
        assertEquals(idA, r.getOrNull(idA).entityId);
        assertEquals(idB, r.getOrNull(idB).entityId);

        // Released id → null (the dangling-ref case the helper exists for).
        r.release(idA);
        assertNull(r.getOrNull(idA));
        // Sibling still resolves.
        assertEquals(idB, r.getOrNull(idB).entityId);

        // Reserved 0L sentinel → null without probing the map.
        assertNull(r.getOrNull(0L));

        // Never-allocated id → null.
        assertNull(r.getOrNull(9999L));
    }

    @Test
    public void allocateSeedsHealthIntoTheEntityWorldFromUnitsSeedFields() {
        UnitRosterService r = roster();
        World w = r.world();
        EntitySpec spec = unit("u");
        // Pre-spawn: the spec ctor seeded hp + maxHp from type.maxHp
        // (MARINE_BLUE). hp is unreadable pre-spawn (no world entity yet),
        // so read the spec fields directly here.
        float typeMaxHp = spec.maxHp;
        assertTrue(typeMaxHp > 0f, "test prerequisite: type seeds a non-zero maxHp");
        assertEquals(typeMaxHp, spec.hp, 1e-6f);

        long u = r.spawn(spec);

        // Post-spawn: hp lives in the entity world's HEALTH columns under the
        // minted id (migration step 3) — the by-id world facade reads it.
        assertEquals(typeMaxHp, w.hp(u), 1e-6f);
        assertEquals(typeMaxHp, w.maxHp(u), 1e-6f);
        assertTrue(r.isLive(u));
        // And the world entity carries the spawn-written IDENTITY alongside.
        assertTrue(r.entityWorld().has(u, r.components().IDENTITY));

        // setHp writes the world slot — the world is the sole canonical
        // store once allocated.
        w.setHp(u, 42f);
        assertEquals(42f, w.hp(u), 1e-6f);
    }

    @Test
    public void releaseDropsTheDenseSlotButHpStaysWorldSideUntilTheDeathTransmute() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        // Production order: every release path zeroes hp first (resolve /
        // cascade / TestUnits.kill), THEN releases the dense slot.
        w.setHp(u, 0f);
        r.release(u);

        // After release: the dense slot is dropped, and liveness reads the
        // world HEALTH — dead via hp <= 0 even though the component is still
        // present (the death drain's corpse transmute removes it later).
        assertFalse(r.isLive(u));
        assertEquals(-1, r.indexOf(u));
        assertFalse(r.isAliveById(u));
        assertTrue(r.entityWorld().has(u, r.components().HEALTH),
                "HEALTH survives release until the corpse transmute");
    }

    @Test
    public void releaseUpdatesDenseIdxOfTheSwappedTailUnit() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;

        // Release the head — tail (c) swaps into slot 0; its index mapping must
        // update or by-index column reads through c would hit the wrong slot.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        // Every per-unit column is now id-keyed in the entity world (immune to
        // dense swaps by design), so the swap-and-pop moves only the dense
        // Entity[] slot + its id↔index mapping: the tail (c) must now occupy
        // slot 0 and resolve there.
        assertEquals(c, r.get(0).entityId);
    }

    @Test
    public void allocateSeedsCellPosIntoTheEntityWorldFromUnitsSeedFields() {
        UnitRosterService r = roster();
        World w = r.world();
        // EntitySpec ctor takes initial cellX/cellY, carried into spawn's
        // POSITION seed.
        long u = r.spawn(new EntitySpec("u", Faction.MARINE, UnitType.MARINE_BLUE, 7, 3));

        // The cell pair lives in the world's POSITION columns under the minted
        // id (migration step 3b) — read/written through the by-id facade.
        assertEquals(7, w.cellX(u));
        assertEquals(3, w.cellY(u));

        w.setCellPos(u, 12, 9);
        assertEquals(12, w.cellX(u));
        assertEquals(9, w.cellY(u));
    }

    @Test
    public void cellSurvivesReleaseAndRidesTheDeathTransmute() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(new EntitySpec("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0));

        w.setCellPos(u, 42, 17);
        w.setHp(u, 0f);
        r.release(u);

        // POSITION persists alive→dead — "the corpse keeps its cell" is now the
        // component's own lifecycle, not a DeathEvent re-write: release drops
        // only the dense slot, the world entity keeps its cell, and the corpse
        // transmute's row-move carries it.
        assertFalse(r.isLive(u));
        assertEquals(-1, r.indexOf(u));
        assertEquals(42, w.cellX(u));
        assertEquals(17, w.cellY(u));
    }

    @Test
    public void allocateCooldownTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0f, w.cooldownTimer(u), 1e-6f);

        w.setCooldownTimer(u, 0.3f);
        assertEquals(0.3f, w.cooldownTimer(u), 1e-6f);
    }

    @Test
    public void cooldownTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setCooldownTimer(c, 4.2f);

        // Releasing a swap-pops c into a's old dense slot — COMBAT is keyed by
        // entity id in the world, not by dense index, so c's value is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(4.2f, w.cooldownTimer(c), 1e-6f);
    }

    @Test
    public void allocateMoveProgressDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0f, w.moveProgress(u), 1e-6f);

        w.setMoveProgress(u, 0.2f);
        assertEquals(0.2f, w.moveProgress(u), 1e-6f);
    }

    @Test
    public void moveProgressIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setMoveProgress(c, 0.9f);

        // Releasing a swap-pops c into a's old dense slot — MOVEMENT is keyed by
        // entity id in the world, not by dense index, so c's value is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(0.9f, w.moveProgress(c), 1e-6f);
    }

    @Test
    public void allocatePathDefaultsToEmptySentinelAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        // The OBJECT path column seeds to the shared empty-path sentinel (a null
        // append would NPE every path reader); the cursor zero-inits.
        assertSame(GridPathfinder.EMPTY_PATH, w.path(u));
        assertEquals(0, w.pathIdx(u));

        int[] p = {3, 4, 5, 6};
        w.setPathRef(u, p);
        w.setPathIdx(u, 1);
        assertSame(p, w.path(u));
        assertEquals(1, w.pathIdx(u));
    }

    @Test
    public void pathIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        int[] p = {7, 8};
        w.setPathRef(c, p);
        w.setPathIdx(c, 1);

        // Releasing a swap-pops c into a's old dense slot — MOVEMENT is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertSame(p, w.path(c));
        assertEquals(1, w.pathIdx(c));
    }

    @Test
    public void allocateSeedsRenderPosIntoTheWorldComponent() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(new EntitySpec("u", Faction.MARINE, UnitType.MARINE_BLUE, 5, 8));
        long id = u;

        // Seeded from the unit's pre-spawn cell into the universal
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
        long u = r.spawn(new EntitySpec("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0));
        long id = u;

        w.setRenderPos(id, 3.5f, 7.2f);
        r.release(u);

        // Dropped from the live dense table...
        assertFalse(r.isLive(u));
        assertEquals(-1, r.indexOf(u));
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
        long a = r.spawn(new EntitySpec("a", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0));
        long b = r.spawn(new EntitySpec("b", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0));
        long c = r.spawn(new EntitySpec("c", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0));
        long idA = a;
        long idC = c;
        w.setRenderPos(idC, 11.5f, 22.3f);

        // Releasing a swap-pops c into a's old dense slot — render position is
        // id-keyed in the world, not dense index, so c's render pos is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(11.5f, w.renderX(idC), 1e-6f);
        assertEquals(22.3f, w.renderY(idC), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackDamageAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        EntitySpec spec = unit("u");
        float typeDmg = spec.attackDamage;
        assertTrue(typeDmg > 0f, "test prerequisite: type seeds a non-zero attackDamage");

        long u = r.spawn(spec);

        assertEquals(typeDmg, w.attackDamage(u), 1e-6f);

        w.setAttackDamage(u, 77f);
        assertEquals(77f, w.attackDamage(u), 1e-6f);
    }

    @Test
    public void attackDamageIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setAttackDamage(c, 55f);

        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(55f, w.attackDamage(c), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackRangeAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        EntitySpec spec = unit("u");
        float typeRange = spec.attackRange;
        assertTrue(typeRange > 0f, "test prerequisite: type seeds a non-zero attackRange");

        long u = r.spawn(spec);

        assertEquals(typeRange, w.attackRange(u), 1e-6f);

        w.setAttackRange(u, 20f);
        assertEquals(20f, w.attackRange(u), 1e-6f);
    }

    @Test
    public void attackRangeIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setAttackRange(c, 99f);

        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(99f, w.attackRange(c), 1e-6f);
    }

    @Test
    public void allocateSeedsAccuracyAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        EntitySpec spec = unit("u");
        float typeAcc = spec.accuracy;
        assertTrue(typeAcc > 0f, "test prerequisite: type seeds a non-zero accuracy");

        long u = r.spawn(spec);

        assertEquals(typeAcc, w.accuracy(u), 1e-6f);

        w.setAccuracy(u, 0.5f);
        assertEquals(0.5f, w.accuracy(u), 1e-6f);
    }

    @Test
    public void accuracyIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setAccuracy(c, 0.95f);

        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(0.95f, w.accuracy(c), 1e-6f);
    }

    @Test
    public void allocateWithoutSecondaryLacksTheSecondaryWeaponComponent() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        // No secondary weapon on the spec → the optional capability isn't in
        // the unit's archetype. Presence IS the capability — there's nothing
        // else to check.
        assertFalse(w.hasSecondaryWeapon(u));
    }

    @Test
    public void allocateWithSecondarySeedsSpecAmmoAndDefaultTimers() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(secondaryUnit("u"));

        assertTrue(w.hasSecondaryWeapon(u));
        assertSame(MarineSecondary.ROCKET_LAUNCHER, w.secondaryWeapon(u));
        assertEquals(MarineSecondary.ROCKET_LAUNCHER.startingAmmo, w.secondaryAmmo(u));
        // Mid-combat scalars start zeroed by the world's row append.
        assertEquals(0f, w.secondaryCooldownTimer(u), 1e-6f);
        assertEquals(0f, w.secondaryActionTimer(u), 1e-6f);
        assertEquals(0L, w.secondaryAimTargetId(u));
        assertFalse(w.secondaryFired(u));
    }

    @Test
    public void secondaryScalarsRoundTripThroughByIdAccessors() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(secondaryUnit("u"));
        long id = u;

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
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(secondaryUnit("c"));
        long idA = a;
        w.setSecondaryActionTimer(c, 0.7f);
        w.setSecondaryAimTargetId(c, 999L);

        // Releasing a swap-pops c into a's old dense slot — SECONDARY_WEAPON is a
        // world component keyed by entity id, untouched by the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(0.7f, w.secondaryActionTimer(c), 1e-6f);
        assertEquals(999L, w.secondaryAimTargetId(c));
    }

    @Test
    public void allocateBurstRemainingDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0, w.burstRemaining(u));

        w.setBurstRemaining(u, 1);
        assertEquals(1, w.burstRemaining(u));
    }

    @Test
    public void burstRemainingIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setBurstRemaining(c, 5);

        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(5, w.burstRemaining(c));
    }

    @Test
    public void allocateBurstTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0f, w.burstTimer(u), 1e-6f);

        w.setBurstTimer(u, 0.1f);
        assertEquals(0.1f, w.burstTimer(u), 1e-6f);
    }

    @Test
    public void burstTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setBurstTimer(c, 0.33f);

        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(0.33f, w.burstTimer(c), 1e-6f);
    }

    @Test
    public void allocateBurstTargetIdDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0L, w.burstTargetId(u));

        w.setBurstTargetId(u, 9L);
        assertEquals(9L, w.burstTargetId(u));
    }

    @Test
    public void burstTargetIdIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setBurstTargetId(c, 777L);

        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(777L, w.burstTargetId(c));
    }

    @Test
    public void allocateTargetIdDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0L, w.targetId(u));

        w.setTargetId(u, 8L);
        assertEquals(8L, w.targetId(u));
    }

    @Test
    public void targetIdIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setTargetId(c, 642L);

        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(642L, w.targetId(c));
    }

    @Test
    public void allocateRepositionCooldownDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0f, w.repositionCooldown(u), 1e-6f);

        w.setRepositionCooldown(u, 0.75f);
        assertEquals(0.75f, w.repositionCooldown(u), 1e-6f);
    }

    @Test
    public void repositionCooldownIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setRepositionCooldown(c, 0.9f);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is keyed by
        // entity id in the world, not by dense index, so c's value is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(0.9f, w.repositionCooldown(c), 1e-6f);
    }

    @Test
    public void releaseOfReservedZeroSentinelIsNoOp() {
        UnitRosterService r = roster();
        long a = r.spawn(unit("a"));
        long idA = a;
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
        long a = r.spawn(unit("a"));
        long idA = a;
        // Dirty several mid-combat columns — all now id-keyed in the world:
        // COMBAT scalars and the AI_STATE fall-back cell.
        w.setCooldownTimer(a, 2.5f);
        w.setTargetId(a, 99L);
        w.setBurstRemaining(a, 3);
        w.setFallbackCell(a, 7, 8);
        r.release(idA);

        // A fresh unit reusing the freed dense slot 0 must see defaults: its
        // world row is a fresh per-id append (a's stale row persists under a's
        // own id), so COMBAT scalars are zero-init and spawn re-seeds the
        // AI_STATE fall-back cell to the -1/-1 sentinel (the one non-zero
        // default).
        long b = r.spawn(unit("b"));
        assertEquals(0, r.indexOf(b));
        assertEquals(0f, w.cooldownTimer(b), 1e-6f);
        assertEquals(0L, w.targetId(b));
        assertEquals(0, w.burstRemaining(b));
        assertEquals(-1, w.fallbackCellX(b));
        assertEquals(-1, w.fallbackCellY(b));
    }

    @Test
    public void allocateFallbackCellDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        // The -1/-1 "no cached cell" sentinel must be seeded by spawn (a
        // zero-init world row would otherwise read (0,0) as a live destination).
        long u = r.spawn(unit("u"));
        assertEquals(-1, w.fallbackCellX(u));
        assertEquals(-1, w.fallbackCellY(u));

        w.setFallbackCell(u, 12, 9);
        assertEquals(12, w.fallbackCellX(u));
        assertEquals(9, w.fallbackCellY(u));
    }

    @Test
    public void fallbackCellIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setFallbackCell(c, 99, 88);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(99, w.fallbackCellX(c));
        assertEquals(88, w.fallbackCellY(c));
    }

    @Test
    public void staticEmplacementsGetNoMovementOrAiStateComponents() {
        UnitRosterService r = roster();
        World w = r.world();
        long marine = r.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0));
        long turret = r.spawn(new EntitySpec("t", Faction.MARINE, UnitType.TURRET, 1, 1));
        long hub = r.spawn(new EntitySpec("h", Faction.MARINE, UnitType.DRONE_HUB_STRUCTURE, 2, 2));

        // A mobile unit is a mover AND a thinker; a static emplacement (turret,
        // drone hub; UnitType.isStatic) is neither — presence IS the capability.
        assertTrue(w.hasMovement(marine));
        assertTrue(w.hasAiState(marine));
        assertFalse(w.hasMovement(turret));
        assertFalse(w.hasAiState(turret));
        assertFalse(w.hasMovement(hub));
        assertFalse(w.hasAiState(hub));

        // The mobile unit's non-zero seeds still run (the mobile-gated spawn
        // block): the empty-path sentinel and the -1/-1 fall-back cell.
        assertSame(GridPathfinder.EMPTY_PATH, w.path(marine));
        assertEquals(-1, w.fallbackCellX(marine));

        // The field accessors are fail-loud on a unit that lacks the component.
        assertThrows(RuntimeException.class, () -> w.moveProgress(turret));
        assertThrows(RuntimeException.class, () -> w.repositionCooldown(hub));
    }

    @Test
    public void allocateFallbackTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0f, w.fallbackTimer(u), 1e-6f);

        w.setFallbackTimer(u, 1.25f);
        assertEquals(1.25f, w.fallbackTimer(u), 1e-6f);
    }

    @Test
    public void fallbackTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setFallbackTimer(c, 0.4f);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(0.4f, w.fallbackTimer(c), 1e-6f);
    }

    @Test
    public void allocateWanderDwellTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRosterService r = roster();
        World w = r.world();
        long u = r.spawn(unit("u"));

        assertEquals(0f, w.wanderDwellTimer(u), 1e-6f);

        w.setWanderDwellTimer(u, 0.75f);
        assertEquals(0.75f, w.wanderDwellTimer(u), 1e-6f);
    }

    @Test
    public void wanderDwellTimerIsUndisturbedByDenseTailSwap() {
        UnitRosterService r = roster();
        World w = r.world();
        long a = r.spawn(unit("a"));
        long b = r.spawn(unit("b"));
        long c = r.spawn(unit("c"));
        long idA = a;
        w.setWanderDwellTimer(c, 0.9f);

        // Releasing a swap-pops c into a's old dense slot — AI_STATE is id-keyed
        // in the world, immune to the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c));
        assertEquals(0.9f, w.wanderDwellTimer(c), 1e-6f);
    }
}
