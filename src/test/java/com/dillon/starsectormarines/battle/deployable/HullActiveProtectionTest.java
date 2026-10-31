package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.marine.MechCompartment;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The active-protection kit: a mount its hull carries, which engages top-attack rounds coming down on it. */
class HullActiveProtectionTest {

    private static final String MISSILE_ID = "weapon.mech-lrm";
    private static final String GUN_ID = "weapon.mech-chaingun";
    private static final String FLAT_ROCKET_ID = "weapon.mech-srm";

    @Test
    void aKittedTankCarriesItsMountWhereverItDrivesAndAHullWithoutTheKitCarriesNone() {
        BattleSimulation sim = openArena(60, 20);
        long tank = sim.convoy().spawn(VehicleStockFit.PARAPET, Faction.DEFENDER, VehicleMission.deployed(20.5f, 10.5f));
        sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER, VehicleMission.deployed(40.5f, 10.5f));
        sim.advance(BattleSimulation.TICK_DT);

        List<PointDefenseService.EmplacementView> live = sim.pointDefense().activeEmplacements();
        assertEquals(1, live.size(), "only the tank with the kit on its rack carries active protection");
        assertEquals(tank, live.get(0).entityId());
        assertNull(MechCompartment.systems(List.of(MechInternal.SENSOR_PAIR)).activeProtectionStructureId());

        GroundBody body = sim.convoy().body(tank);
        body.teleport(30.5f, 12.5f, 0f);
        sim.advance(BattleSimulation.TICK_DT);
        PointDefenseService.EmplacementView moved = sim.pointDefense().activeEmplacements().get(0);
        assertEquals(body.x, moved.x(), 1e-3f);
        assertEquals(body.y, moved.y(), 1e-3f);
    }

    @Test
    void itTakesOnlyArcingRoundsComingDownOnTheHull() {
        WeaponDef lrm = WeaponRegistry.require(MISSILE_ID);
        WeaponDef srm = WeaponRegistry.require(FLAT_ROCKET_ID);
        assertTrue(PointDefenseService.comingDownOn(round(lrm, 25f, 10f, 21f, 10f), 20f, 10f));
        assertFalse(PointDefenseService.comingDownOn(round(srm, 25f, 10f, 20f, 10f), 20f, 10f),
                "a flat rocket is left to the plate");
        assertFalse(PointDefenseService.comingDownOn(round(lrm, 25f, 10f, 20f, 15f), 20f, 10f),
                "a round falling on somebody else is not this hull's");
    }

    @Test
    void itEngagesAHostileMissileInsideItsRadiusAndLeavesAGunRoundAlone() {
        BattleSimulation sim = openArena(60, 20);
        long tank = sim.convoy().spawn(VehicleStockFit.PARAPET, Faction.DEFENDER, VehicleMission.deployed(20.5f, 10.5f));
        sim.advance(BattleSimulation.TICK_DT);

        queueRound(sim, GUN_ID, 21.5f, 10.5f, 20.5f, 10.5f);
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(0, sim.pointDefense().hullInterceptions(tank)[0], "a gun round is not a point-defence target");

        Projectile missile = queueRound(sim, MISSILE_ID, 21.5f, 10.5f, 20.5f, 10.5f);
        sim.advance(BattleSimulation.TICK_DT);
        int[] counts = sim.pointDefense().hullInterceptions(tank);
        assertEquals(1, counts[0], "the missile inside the radius is engaged");
        assertEquals(counts[1] == 1, missile.intercepted, "a stop takes the round out of the air");
    }

    @Test
    void aSpentMountIsNotRearmed() {
        BattleSimulation sim = openArena(60, 20);
        long tank = sim.convoy().spawn(VehicleStockFit.PARAPET, Faction.DEFENDER, VehicleMission.deployed(20.5f, 10.5f));
        sim.advance(BattleSimulation.TICK_DT);
        WeaponDef gun = WeaponRegistry.require("weapon.tank-aps");
        int magazine = sim.pointDefense().activeEmplacements().get(0).engagementsRemaining();
        int ticksPerEngagement = Math.round(gun.cooldown / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < magazine + 2; i++) {
            queueRound(sim, MISSILE_ID, 21.5f, 10.5f, 20.5f, 10.5f);
            for (int t = 0; t < ticksPerEngagement; t++) sim.advance(BattleSimulation.TICK_DT);
        }
        assertEquals(magazine, sim.pointDefense().hullInterceptions(tank)[0]);
        assertTrue(sim.pointDefense().activeEmplacements().isEmpty(), "a dry mount stops covering");
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.pointDefense().activeEmplacements().isEmpty(), "and is not given a fresh magazine");
        assertFalse(sim.convoy().structure(tank) <= 0f, "running dry does not hurt the hull");
    }

    @Test
    void aMechWhoseCompartmentCarriesActiveProtectionAndASensorPairIsHostedAsAHullIs() {
        BattleSimulation sim = openArena(60, 20);
        MechVariant variant = MechVariant.WARDEN;
        long mech = sim.spawn(variant.applyTo(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 20, 10)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(MechRole.BALANCED));
        sim.getRoster().machineHosts().register(mech,
                MechCompartment.systems(List.of(MechInternal.ACTIVE_PROTECTION, MechInternal.SENSOR_PAIR)));
        long plain = sim.spawn(variant.applyTo(new EntitySpec("plain", Faction.MARINE, UnitType.HEAVY_MECH, 40, 10)));
        sim.world().attachMechLoadout(plain, variant.createLoadout(MechRole.BALANCED));
        sim.advance(BattleSimulation.TICK_DT);

        List<PointDefenseService.EmplacementView> live = sim.pointDefense().activeEmplacements();
        assertEquals(1, live.size(), "only the mech with active protection in its compartment carries the mount");
        assertEquals(mech, live.get(0).entityId());
        assertTrue(sim.droneBays().wing(mech) != null, "its sensor pair flies off the mech as off a hull");
        assertNull(sim.droneBays().wing(plain));
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(width, height));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** A hostile round with a harmless payload, slow enough to be in the air for the engagement. */
    private static Projectile queueRound(BattleSimulation sim, String weaponId, float fromX, float fromY,
                                         float toX, float toY) {
        WeaponDef weapon = WeaponRegistry.require(weaponId);
        PendingDetonation payload = new PendingDetonation(0L, toX, toY, 1f,
                0f, 0f, 0f, 0, Faction.MARINE, /*aerialDelivery*/ false);
        Projectile round = new Projectile(fromX, fromY, toX, toY, weapon.boostRamp, weapon.arcHeight, Faction.MARINE,
                false, 1f, payload, weapon.id, weapon.pointDefenseTarget);
        sim.queueProjectile(round);
        return round;
    }

    private static Projectile round(WeaponDef weapon, float fromX, float fromY, float toX, float toY) {
        return new Projectile(fromX, fromY, toX, toY, weapon.boostRamp, weapon.arcHeight, Faction.MARINE,
                false, 1f, null, weapon.id, weapon.pointDefenseTarget);
    }
}
