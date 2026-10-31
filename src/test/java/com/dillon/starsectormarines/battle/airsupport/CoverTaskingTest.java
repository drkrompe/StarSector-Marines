package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.air.CoverDuty;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a cover fighter will chase: a hostile aircraft over ground the side has sighted anti-air on is left alone, and the
 * same aircraft over ground where the side has seen nothing is not. Fair knowledge is the whole point, so both halves are
 * asked: a battery out of every watcher's sight changes nothing.
 */
class CoverTaskingTest {

    private static final String CARBINE = "weapon.smg";
    private static final int W = 160;
    private static final int H = 60;

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
    }

    @AfterAll
    static void release() {
        TurretSlotResolver.useHullSpecs(null);
    }

    /** A company at (20, 30), its own pool with one fighter, and a battery of three anti-air guns at {@code batteryX}. */
    private static SceneWorld scene(int batteryX) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).missionCompletion(false);
        builder.squad("observers").faction(Faction.MARINE).size(3).at(20, 30).primary(CARBINE)
                .special("special.comms-radio", 1).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 20, 30)).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(2).at(W - 4, 4).primary(CARBINE).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, W - 4, 4)).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        for (int dy : new int[]{-4, 0, 4}) {
            sim.getGrid().setWalkable(batteryX, 30 + dy, false);
            sim.spawn(MapTurret.create("aa-" + dy, Faction.DEFENDER, TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID, batteryX, 30 + dy));
        }
        sim.setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE,
                AirPoolAllowance.of(null, new AirPoolStock(2, 2, 0f), false))));
        return world;
    }

    private static long bomberAt(BattleSimulation sim, float x, float y) {
        long id = sim.spawnSortie(FighterProfile.DAGGER, Faction.DEFENDER, 20f, 30f, x, y, x, y, 0f);
        ShuttleMission m = sim.world().mission(id);
        m.strikeSortie = true;
        m.poolSortie = true;
        m.passesLeft = 1;
        m.ordnance = AirOrdnance.STRIKE_BOMBS;
        // Put the aircraft in the air at the point rather than waiting for it to fly there.
        sim.world().kinematics(id).teleport(x, y, 90f);
        sim.world().setAltitudeT(id, 1f);
        m.state = ShuttleState.INCOMING;
        return id;
    }

    private static void run(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    @Test
    void anAircraftOverASightedBatteryIsNotChasedButTheSameOneElsewhereIs() {
        SceneWorld world = scene(50);
        BattleSimulation sim = world.sim();
        run(sim, 10);
        AirPoolService pool = sim.airPool();
        pool.director(Faction.MARINE).look(sim);
        assertFalse(pool.director(Faction.MARINE).knownGuns().isEmpty(), "the company has the battery in sight");
        long over = bomberAt(sim, 50f, 30f);
        long clear = bomberAt(sim, 90f, 30f);
        var b = sim.world().kinematics(over);
        assertTrue(CoverTasking.underKnownAntiAir(sim, pool, Faction.MARINE, over), "risk " + pool.director(Faction.MARINE).risk(sim, b.x, b.y, AirPoolKind.FIGHTER) + " at " + b.x + "," + b.y + " known " + pool.director(Faction.MARINE).knownGuns().size());
        assertFalse(CoverTasking.underKnownAntiAir(sim, pool, Faction.MARINE, clear));
        CoverDuty duty = new CoverDuty(20f, 30f);
        assertEquals(clear, CoverTasking.pickPrey(sim, pool, 0L, Faction.MARINE, duty),
                "the nearer aircraft is under the battery, so the patrol takes the other");
    }

    @Test
    void aHostileFlierNearTheCoveredGroundIsPreyButAnAircraftAttackingItComesFirst() {
        SceneWorld world = scene(150);
        BattleSimulation sim = world.sim();
        long harrier = sim.spawn(new EntitySpec("harrier", Faction.DEFENDER, UnitType.SWARM_HARRIER, 40, 30));
        run(sim, 2);
        AirPoolService pool = sim.airPool();
        CoverDuty duty = new CoverDuty(20f, 30f);
        assertEquals(harrier, CoverTasking.pickPrey(sim, pool, 0L, Faction.MARINE, duty), "a Harrier is a fighter's prey");
        long bomber = bomberAt(sim, 60f, 30f);
        assertEquals(bomber, CoverTasking.pickPrey(sim, pool, 0L, Faction.MARINE, duty),
                "an aircraft bombing the covered ground before a flier over it");
        assertNotEquals(harrier, CoverTasking.pickPrey(sim, pool, 0L, Faction.DEFENDER, new CoverDuty(40f, 30f)),
                "never one's own side's flier");
    }

    @Test
    void aBatteryNobodyHasSightedDoesNotSpareTheAircraftOverIt() {
        SceneWorld world = scene(120);
        BattleSimulation sim = world.sim();
        run(sim, 10);
        AirPoolService pool = sim.airPool();
        pool.director(Faction.MARINE).look(sim);
        assertTrue(pool.director(Faction.MARINE).knownGuns().isEmpty(), "a hundred cells off: unseen");
        long over = bomberAt(sim, 120f, 30f);
        assertFalse(CoverTasking.underKnownAntiAir(sim, pool, Faction.MARINE, over));
    }
}
