package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entity-tests the {@link UnitRegistry} contract: monotonic id allocation,
 * dense {@code [0, liveCount())} iteration, swap-and-pop release moving
 * the tail entity into the freed slot, stale-id lookups returning
 * {@link UnitRegistry#INVALID_INDEX}, growth on overflow without index
 * corruption.
 */
public class UnitRegistryTest {

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

    @Test
    public void allocateAssignsMonotonicIdsAndPacksDenseSlots() {
        UnitRegistry r = new UnitRegistry();
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
        UnitRegistry r = new UnitRegistry();
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
        UnitRegistry r = new UnitRegistry();
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
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
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
        UnitRegistry r = new UnitRegistry();
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
        UnitRegistry r = new UnitRegistry();
        assertEquals(0, r.liveCount());
        assertFalse(r.isLive(1L));
        assertEquals(UnitRegistry.INVALID_INDEX, r.indexOf(1L));
        assertNotNull(r.denseArray());
    }

    @Test
    public void allocateRejectsAlreadyAllocatedUnit() {
        UnitRegistry r = new UnitRegistry();
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
        UnitRegistry r = new UnitRegistry();
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
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        // Pre-allocate: ctor seeded seedHp + seedMaxHp from type.maxHp
        // (MARINE_BLUE). hp is unreadable pre-allocate (no world entity yet),
        // so read the seed fields directly here.
        float typeMaxHp = u.seedMaxHp;
        assertTrue(typeMaxHp > 0f, "test prerequisite: type seeds a non-zero maxHp");
        assertEquals(typeMaxHp, u.seedHp, 1e-6f);

        r.allocate(u);

        // Post-allocate: hp lives in the entity world's HEALTH columns under the
        // minted id (migration step 3) — the registry's by-id adapters read it.
        assertEquals(typeMaxHp, r.hpById(u.entityId), 1e-6f);
        assertEquals(typeMaxHp, r.maxHpById(u.entityId), 1e-6f);
        assertTrue(r.isLive(u.entityId));
        // And the world entity carries the spawn-written IDENTITY alongside.
        assertTrue(r.entityWorld().has(u.entityId, r.components().IDENTITY));

        // setHpById writes the world slot — the world is the sole canonical
        // store once allocated.
        r.setHpById(u.entityId, 42f);
        assertEquals(42f, r.hpById(u.entityId), 1e-6f);
    }

    @Test
    public void releaseDropsTheDenseSlotButHpStaysWorldSideUntilTheDeathTransmute() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        r.allocate(u);

        // Production order: every release path zeroes hp first (resolve /
        // cascade / TestUnits.kill), THEN releases the dense slot.
        r.setHpById(u.entityId, 0f);
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
        UnitRegistry r = new UnitRegistry();
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
        // And c's dense columns still resolve through the swapped slot. (hp and
        // the cell pair are id-keyed in the entity world now — immune to dense
        // swaps by design — so the swap proof writes a column that still lives
        // densely.)
        r.setFallbackCell(r.indexOf(c.entityId), 123, 45);
        assertEquals(123, r.getFallbackCellX(0));
        assertEquals(45, r.getFallbackCellY(0));
    }

    @Test
    public void allocateSeedsCellPosIntoTheEntityWorldFromUnitsSeedFields() {
        UnitRegistry r = new UnitRegistry();
        // Entity ctor takes initial cellX/cellY, stamped into seedCellX/Y pre-alloc.
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 7, 3);

        r.allocate(u);

        // The cell pair lives in the world's POSITION columns under the minted
        // id (migration step 3b) — read/written through the by-id adapters.
        assertEquals(7, r.cellXById(u.entityId));
        assertEquals(3, r.cellYById(u.entityId));

        r.setCellPosById(u.entityId, 12, 9);
        assertEquals(12, r.cellXById(u.entityId));
        assertEquals(9, r.cellYById(u.entityId));
    }

    @Test
    public void cellSurvivesReleaseAndRidesTheDeathTransmute() {
        UnitRegistry r = new UnitRegistry();
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        r.allocate(u);

        r.setCellPosById(u.entityId, 42, 17);
        r.setHpById(u.entityId, 0f);
        r.release(u.entityId);

        // POSITION persists alive→dead — "the corpse keeps its cell" is now the
        // component's own lifecycle, not a DeathEvent re-write: release drops
        // only the dense slot, the world entity keeps its cell, and the corpse
        // transmute's row-move carries it.
        assertFalse(r.isLive(u.entityId));
        assertEquals(-1, r.indexOf(u.entityId));
        assertEquals(42, r.cellXById(u.entityId));
        assertEquals(17, r.cellYById(u.entityId));
    }

    @Test
    public void allocateCooldownTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.cooldownTimerById(u.entityId), 1e-6f);

        r.setCooldownTimerById(u.entityId, 0.3f);
        assertEquals(0.3f, r.cooldownTimerById(u.entityId), 1e-6f);
    }

    @Test
    public void cooldownTimerIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setCooldownTimerById(c.entityId, 4.2f);

        // Releasing a swap-pops c into a's old dense slot — COMBAT is keyed by
        // entity id in the world, not by dense index, so c's value is untouched.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(4.2f, r.cooldownTimerById(c.entityId), 1e-6f);
    }

    @Test
    public void allocateMoveProgressDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getMoveProgress(r.indexOf(u.entityId)), 1e-6f);

        r.setMoveProgress(r.indexOf(u.entityId), 0.2f);
        assertEquals(0.2f, r.getMoveProgress(r.indexOf(u.entityId)), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesMoveProgressCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
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
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 5, 8);

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
        Entity u = new Entity("u", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
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
        Entity a = new Entity("a", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        Entity b = new Entity("b", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
        Entity c = new Entity("c", Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
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
    public void allocateSeedsAttackDamageAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        float typeDmg = u.seedAttackDamage;
        assertTrue(typeDmg > 0f, "test prerequisite: type seeds a non-zero attackDamage");

        r.allocate(u);

        assertEquals(typeDmg, r.attackDamageById(u.entityId), 1e-6f);

        r.setAttackDamageById(u.entityId, 77f);
        assertEquals(77f, r.attackDamageById(u.entityId), 1e-6f);
    }

    @Test
    public void attackDamageIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setAttackDamageById(c.entityId, 55f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(55f, r.attackDamageById(c.entityId), 1e-6f);
    }

    @Test
    public void allocateSeedsAttackRangeAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        float typeRange = u.seedAttackRange;
        assertTrue(typeRange > 0f, "test prerequisite: type seeds a non-zero attackRange");

        r.allocate(u);

        assertEquals(typeRange, r.attackRangeById(u.entityId), 1e-6f);

        r.setAttackRangeById(u.entityId, 20f);
        assertEquals(20f, r.attackRangeById(u.entityId), 1e-6f);
    }

    @Test
    public void attackRangeIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setAttackRangeById(c.entityId, 99f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(99f, r.attackRangeById(c.entityId), 1e-6f);
    }

    @Test
    public void allocateSeedsAccuracyAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        float typeAcc = u.seedAccuracy;
        assertTrue(typeAcc > 0f, "test prerequisite: type seeds a non-zero accuracy");

        r.allocate(u);

        assertEquals(typeAcc, r.accuracyById(u.entityId), 1e-6f);

        r.setAccuracyById(u.entityId, 0.5f);
        assertEquals(0.5f, r.accuracyById(u.entityId), 1e-6f);
    }

    @Test
    public void accuracyIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setAccuracyById(c.entityId, 0.95f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.95f, r.accuracyById(c.entityId), 1e-6f);
    }

    @Test
    public void allocateWithoutSecondaryLacksTheSecondaryWeaponComponent() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        // No seedSecondaryWeapon → the optional capability isn't in the unit's
        // archetype. Presence IS the capability — there's nothing else to check.
        assertFalse(r.hasSecondaryWeapon(u.entityId));
    }

    @Test
    public void allocateWithSecondarySeedsSpecAmmoAndDefaultTimers() {
        UnitRegistry r = new UnitRegistry();
        Entity u = secondaryUnit("u");

        r.allocate(u);

        assertTrue(r.hasSecondaryWeapon(u.entityId));
        assertSame(MarineSecondary.ROCKET_LAUNCHER, r.secondaryWeaponOf(u.entityId));
        assertEquals(MarineSecondary.ROCKET_LAUNCHER.startingAmmo, r.secondaryAmmoById(u.entityId));
        // Mid-combat scalars start zeroed by the world's row append.
        assertEquals(0f, r.secondaryCooldownTimerById(u.entityId), 1e-6f);
        assertEquals(0f, r.secondaryActionTimerById(u.entityId), 1e-6f);
        assertEquals(0L, r.secondaryAimTargetIdById(u.entityId));
        assertFalse(r.secondaryFiredById(u.entityId));
    }

    @Test
    public void secondaryScalarsRoundTripThroughByIdAccessors() {
        UnitRegistry r = new UnitRegistry();
        Entity u = secondaryUnit("u");
        r.allocate(u);
        long id = u.entityId;

        r.setSecondaryAmmoById(id, 2);
        r.setSecondaryCooldownTimerById(id, 0.4f);
        r.setSecondaryActionTimerById(id, 0.6f);
        r.setSecondaryAimTargetIdById(id, 7L);
        r.setSecondaryFiredById(id, true);

        assertEquals(2, r.secondaryAmmoById(id));
        assertEquals(0.4f, r.secondaryCooldownTimerById(id), 1e-6f);
        assertEquals(0.6f, r.secondaryActionTimerById(id), 1e-6f);
        assertEquals(7L, r.secondaryAimTargetIdById(id));
        assertTrue(r.secondaryFiredById(id));
    }

    @Test
    public void secondaryStateIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = secondaryUnit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setSecondaryActionTimerById(c.entityId, 0.7f);
        r.setSecondaryAimTargetIdById(c.entityId, 999L);

        // Releasing a swap-pops c into a's old dense slot — SECONDARY_WEAPON is a
        // world component keyed by entity id, untouched by the dense reshuffle.
        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.7f, r.secondaryActionTimerById(c.entityId), 1e-6f);
        assertEquals(999L, r.secondaryAimTargetIdById(c.entityId));
    }

    @Test
    public void allocateBurstRemainingDefaultsAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0, r.burstRemainingById(u.entityId));

        r.setBurstRemainingById(u.entityId, 1);
        assertEquals(1, r.burstRemainingById(u.entityId));
    }

    @Test
    public void burstRemainingIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setBurstRemainingById(c.entityId, 5);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(5, r.burstRemainingById(c.entityId));
    }

    @Test
    public void allocateBurstTimerDefaultsAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.burstTimerById(u.entityId), 1e-6f);

        r.setBurstTimerById(u.entityId, 0.1f);
        assertEquals(0.1f, r.burstTimerById(u.entityId), 1e-6f);
    }

    @Test
    public void burstTimerIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setBurstTimerById(c.entityId, 0.33f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.33f, r.burstTimerById(c.entityId), 1e-6f);
    }

    @Test
    public void allocateBurstTargetIdDefaultsAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0L, r.burstTargetIdById(u.entityId));

        r.setBurstTargetIdById(u.entityId, 9L);
        assertEquals(9L, r.burstTargetIdById(u.entityId));
    }

    @Test
    public void burstTargetIdIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setBurstTargetIdById(c.entityId, 777L);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(777L, r.burstTargetIdById(c.entityId));
    }

    @Test
    public void allocateTargetIdDefaultsAndAccessorsRouteThroughWorld() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0L, r.targetIdById(u.entityId));

        r.setTargetIdById(u.entityId, 8L);
        assertEquals(8L, r.targetIdById(u.entityId));
    }

    @Test
    public void targetIdIsUndisturbedByDenseTailSwap() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setTargetIdById(c.entityId, 642L);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(642L, r.targetIdById(c.entityId));
    }

    @Test
    public void allocateRepositionCooldownDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getRepositionCooldown(r.indexOf(u.entityId)), 1e-6f);

        r.setRepositionCooldown(r.indexOf(u.entityId), 0.75f);
        assertEquals(0.75f, r.getRepositionCooldown(r.indexOf(u.entityId)), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesRepositionCooldownCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
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
    public void allocateResetsMidCombatColumnsWhenReusingAFreedSlot() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        long idA = r.allocate(a);
        // Dirty several mid-combat columns: COMBAT scalars (world, by id) and a
        // registry-dense fallback cell (by index).
        r.setCooldownTimerById(a.entityId, 2.5f);
        r.setTargetIdById(a.entityId, 99L);
        r.setBurstRemainingById(a.entityId, 3);
        r.setFallbackCell(r.indexOf(a.entityId), 7, 8);
        r.release(idA);

        // A fresh unit must see defaults: its COMBAT columns are a fresh,
        // zero-initialised world row (a's stale row persists under its own id),
        // and the reused dense slot 0 reset its fallback cell to the sentinel.
        Entity b = unit("b");
        r.allocate(b);
        assertEquals(0, r.indexOf(b.entityId));
        assertEquals(0f, r.cooldownTimerById(b.entityId), 1e-6f);
        assertEquals(0L, r.targetIdById(b.entityId));
        assertEquals(0, r.burstRemainingById(b.entityId));
        assertEquals(-1, r.getFallbackCellX(r.indexOf(b.entityId)));
        assertEquals(-1, r.getFallbackCellY(r.indexOf(b.entityId)));
    }

    @Test
    public void allocateFallbackCellDefaultsAndAccessorsRouteThroughRegistry() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
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
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
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
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getFallbackTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setFallbackTimer(r.indexOf(u.entityId), 1.25f);
        assertEquals(1.25f, r.getFallbackTimer(r.indexOf(u.entityId)), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesFallbackTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
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
        Entity u = unit("u");

        r.allocate(u);

        assertEquals(0f, r.getWanderDwellTimer(r.indexOf(u.entityId)), 1e-6f);

        r.setWanderDwellTimer(r.indexOf(u.entityId), 0.75f);
        assertEquals(0.75f, r.getWanderDwellTimer(r.indexOf(u.entityId)), 1e-6f);
    }

    @Test
    public void releaseTailSwapMovesWanderDwellTimerCorrectly() {
        UnitRegistry r = new UnitRegistry();
        Entity a = unit("a");
        Entity b = unit("b");
        Entity c = unit("c");
        long idA = r.allocate(a);
        r.allocate(b);
        r.allocate(c);
        r.setWanderDwellTimer(r.indexOf(c.entityId), 0.9f);

        r.release(idA);

        assertEquals(0, r.indexOf(c.entityId));
        assertEquals(0.9f, r.getWanderDwellTimer(0), 1e-6f);
    }
}
