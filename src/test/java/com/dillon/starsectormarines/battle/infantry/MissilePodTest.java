package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The shoulder micro-missile pod's acceptance bullets
 * ({@code integral-armor-systems.md}), driven end to end through a real
 * {@link BattleSimulation} against the shipped {@code armor.aegis-composite}
 * pattern so the test exercises the authored catalog rather than a synthetic
 * stand-in. Values it checks against are derived from the loaded catalog
 * entries, never hard-coded balance numbers.
 */
class MissilePodTest {

    private static final int W = 30;
    private static final int H = 16;
    private static final int ROW = 5;
    private static final int SHOOTER_X = 3;
    private static final int TARGET_X = 9;
    /**
     * Deliberately far past any authored missile-pod damage total: these
     * tests care about the pod's activation lifecycle and collateral
     * bookkeeping, not whether it can kill a militia, so the target must
     * outlast a full rack regardless of the catalog's authored numbers.
     */
    private static final float SURVIVES_THE_WHOLE_RACK = 1_000_000f;

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Fixture assumption every test here leans on: the pod's authored home. */
    private static IntegralSystemDef pod() {
        MarineArmorCatalogDef pattern = MarineArmorCatalogRegistry.require("armor.aegis-composite");
        assumeTrue(pattern.hasIntegralSystem(), "fixture assumption: the Aegis carries a system");
        IntegralSystemDef system = pattern.integralSystem();
        assumeTrue(system.effect() == IntegralSystemEffect.MISSILE_POD,
                "fixture assumption: the Aegis carries the missile pod, not the breach family");
        return system;
    }

    @Test
    void aPodCarryingSuitFiresRunsDryAndStaysDry() {
        IntegralSystemDef pod = pod();
        int startingAmmo = pod.startingAmmo();
        assertTrue(startingAmmo > 0, "fixture assumption: the pod carries at least one salvo");

        BattleSimulation sim = arena();
        long shooter = sim.spawn(new EntitySpec("gunner", Faction.MARINE, UnitType.MARINE,
                SHOOTER_X, ROW).integralSystem(pod));
        // Durable rather than merely alive: the pod must still have something
        // to spend its whole rack on. A hostile that dies to the first salvo
        // would leave later ammo stranded with nothing to fire at — a real
        // battlefield outcome, but not what these tests are checking.
        sim.spawn(new EntitySpec("hostile", Faction.DEFENDER, UnitType.MILITIA,
                TARGET_X, ROW).moveSpeed(0f).health(SURVIVES_THE_WHOLE_RACK));

        int ticks = 0;
        while (sim.integralSystems().ammo(shooter) > 0 && ticks < 6000) {
            sim.advance(BattleSimulation.TICK_DT);
            ticks++;
        }

        assertEquals(0, sim.integralSystems().ammo(shooter), "the pod should have run dry");
        assertFalse(sim.integralSystems().canActivate(shooter), "a dry pod cannot activate again");
        assertTrue(sim.telemetry().roundsFired(shooter) > 0,
                "running the pod dry means it actually fired at some point");

        int roundsWhenDry = sim.telemetry().roundsFired(shooter);
        for (int i = 0; i < 300; i++) sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0, sim.integralSystems().ammo(shooter), "the pod stays dry for the rest of the battle");
        assertEquals(roundsWhenDry, sim.telemetry().roundsFired(shooter),
                "a dry pod fires no further rounds");
    }

    /**
     * Ordinary friendly-fire discipline, as the shipped explosive specials
     * already have: a friendly caught in the splash takes damage recorded as
     * its own quantity, distinct from (and not netted against) the damage
     * credited against the intended hostile.
     */
    @Test
    void collateralIsRecordedAsItsOwnQuantityNotNettedAway() {
        IntegralSystemDef pod = pod();
        BattleSimulation sim = arena();
        long shooter = sim.spawn(new EntitySpec("gunner", Faction.MARINE, UnitType.MARINE,
                SHOOTER_X, ROW).integralSystem(pod));
        // Durable rather than merely alive: the pod must still have something
        // to spend its whole rack on. A hostile that dies to the first salvo
        // would leave later ammo stranded with nothing to fire at — a real
        // battlefield outcome, but not what these tests are checking.
        sim.spawn(new EntitySpec("hostile", Faction.DEFENDER, UnitType.MILITIA,
                TARGET_X, ROW).moveSpeed(0f).health(SURVIVES_THE_WHOLE_RACK));
        // A squadmate standing right on top of the intended target — well
        // inside any plausible micro-missile warhead radius — so the splash
        // cannot help but catch them too.
        sim.spawn(new EntitySpec("bystander", Faction.MARINE, UnitType.MARINE,
                TARGET_X, ROW).moveSpeed(0f));

        int ticks = 0;
        while (sim.integralSystems().ammo(shooter) > 0 && ticks < 6000) {
            sim.advance(BattleSimulation.TICK_DT);
            ticks++;
        }
        // Detonations fire on a short flight-time delay behind the salvo that
        // emptied the rack; give the last one time to land.
        for (int i = 0; i < 300; i++) sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.telemetry().damageDealt(shooter) > 0f,
                "the pod's salvo should have damaged its intended target");
        assertTrue(sim.telemetry().friendlyFireDamage(shooter) > 0f,
                "a bystander standing in the blast should be recorded as friendly fire,"
                        + " the same discipline the shipped explosive specials already have");
    }

    /** The rule stated twice in the story: a marine with a pod still has their grenades. */
    @Test
    void theBilletsCarriedSpecialItemIsUnaffectedThroughout() {
        IntegralSystemDef pod = pod();
        BattleSimulation sim = arena();
        int carriedAmmo = 3;
        // Hardened-direct-fire AI never spends this on a soft militia target,
        // so any change in its ammo can only be the pod's own bookkeeping
        // leaking into the wrong column.
        long shooter = sim.spawn(new EntitySpec("gunner", Faction.MARINE, UnitType.MARINE,
                        SHOOTER_X, ROW)
                .integralSystem(pod)
                .specialEquipment(SpecialEquipmentRegistry.require(
                        SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), carriedAmmo));
        // Durable rather than merely alive: the pod must still have something
        // to spend its whole rack on. A hostile that dies to the first salvo
        // would leave later ammo stranded with nothing to fire at — a real
        // battlefield outcome, but not what these tests are checking.
        sim.spawn(new EntitySpec("hostile", Faction.DEFENDER, UnitType.MILITIA,
                TARGET_X, ROW).moveSpeed(0f).health(SURVIVES_THE_WHOLE_RACK));

        int ticks = 0;
        while (sim.integralSystems().ammo(shooter) > 0 && ticks < 6000) {
            sim.advance(BattleSimulation.TICK_DT);
            ticks++;
        }

        assertEquals(0, sim.integralSystems().ammo(shooter), "fixture assumption: the pod ran dry");
        assertEquals(carriedAmmo, sim.world().secondaryAmmo(shooter),
                "the billet's carried special item must be untouched by the pod firing");
    }
}
