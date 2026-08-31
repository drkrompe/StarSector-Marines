package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A minute of open ground is only worth something if somebody can use it.
 *
 * <p>The whole case for a runway is that an aircraft has to cross open concrete
 * instead of rising vertically off a stand, and that every second of the
 * crossing is somewhere a raider can be standing. That was not true: air could
 * be engaged only by defence posts and only while airborne, so a fighter
 * taxiing past a fire team was in no danger at all and the trade the strip
 * exists to make was a fiction.
 */
class ExposedOnTheGroundTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);
    private static final float SHELTER_X = 25.5f;
    private static final float SHELTER_Y = 25.5f;

    private static BattleSimulation openField() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        sim.getAirfieldService().installRunway(STRIP);
        return sim;
    }

    /** A fighter taxiing out of its shed toward the threshold. */
    private static long taxiingFighter(BattleSimulation sim) {
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth shed = airfield.addShelterBerth(
                new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                FighterProfile.BROADSWORD);
        long craft = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                50.5f, 30.5f, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        mission.homeBerth = shed;
        mission.hp = airfield.launch(shed);
        mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, 50.5f, 30.5f);
        sim.world().kinematics(craft).teleport(SHELTER_X, SHELTER_Y, 0f);
        return craft;
    }

    /** A fire team standing on the apron beside the taxiing aircraft. */
    private static void fireTeamBesideTheTaxiway(BattleSimulation sim, int size) {
        for (int i = 0; i < size; i++) {
            sim.spawn(new EntitySpec("r" + i, Faction.MARINE, UnitType.MARINE,
                    (int) SHELTER_X - 3 + i, (int) SHELTER_Y - 4));
        }
    }

    /**
     * A fire team alongside the taxiway takes a taxiing aircraft apart.
     *
     * <p>Asserted on the hull rather than on a kill, because what matters is
     * that the crossing costs the aircraft something at all — before this it
     * cost exactly nothing however many rifles were pointed at it.
     */
    @Test
    void aTaxiingAircraftIsShotAtByTroopsBesideIt() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        float startedWith = mission.hp;
        fireTeamBesideTheTaxiway(sim, 6);

        for (int i = 0; i < 90 && mission.state == ShuttleState.TAXI_OUT; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(mission.hp < startedWith,
                "a fire team stood beside the taxiway and the aircraft took nothing");
    }

    /** And with nobody there it crosses untouched, so the exposure is the troops. */
    @Test
    void anUninterferedTaxiCostsTheAircraftNothing() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        float startedWith = mission.hp;
        // Somebody far away, so the battle keeps ticking without being a threat.
        sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 2, 38));

        for (int i = 0; i < 600 && mission.state == ShuttleState.TAXI_OUT; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(startedWith, mission.hp, 0.001f,
                "an unopposed taxi cost the aircraft hull anyway");
    }

    /**
     * An aircraft making gun runs can be engaged by the defences it is
     * attacking.
     *
     * <p>Replacing the armed loiter with attack runs quietly took the strike
     * out of reach of every anti-air gun on the map, because the list of
     * hittable phases named the states by hand and the new ones were not on it.
     * A strike that cannot be shot at is not a raid worth defending against.
     */
    @Test
    void anAircraftMakingGunRunsCanStillBeEngaged() {
        BattleSimulation sim = openField();
        long craft = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                30.5f, 20.5f, 5f, 5f, 5f, 5f, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        mission.strikeSortie = true;
        mission.passesLeft = 3;
        sim.world().kinematics(craft).teleport(30.5f, 20.5f, 0f);
        mission.state = ShuttleState.ATTACK_RUN;
        mission.runFromX = 20f; mission.runFromY = 20.5f;
        mission.runToX = 45f;  mission.runToY = 20.5f;
        float startedWith = mission.hp;

        // An anti-air post right under the run.
        sim.spawn(new EntitySpec("aa", Faction.MARINE, UnitType.TURRET, 30, 20)
                .turretStructureId("structure.turret-vulcan")
                .health(400f));

        for (int i = 0; i < 60 && mission.state == ShuttleState.ATTACK_RUN; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(mission.hp < startedWith,
                "an aircraft made gun runs over an anti-air post and took nothing");
    }

    /**
     * A machine killed rolling under its own power owes what one killed on its
     * pad owes: the same fireball, and a hull that stays exactly where the
     * fire caught it.
     *
     * <p>It is the same full tank under the same thin skin either way, so
     * whether it was parked or moving when the fire started is not a reason
     * for the fire to be different. Before this, a fighter shot off its own
     * taxiway simply stopped existing — no blast, no wreck, nothing to show
     * for the raid but an empty stand on the far side of the field.
     */
    @Test
    void aTaxiingAircraftKilledOnTheGroundLeavesAWreck() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        // A heavier team than the earlier "took something" test — this one
        // has to actually finish the aircraft off.
        fireTeamBesideTheTaxiway(sim, 12);

        for (int i = 0; i < 300 && mission.state != ShuttleState.GONE; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(ShuttleState.GONE, mission.state,
                "the fire team beside the taxiway did not bring it down");
        List<GroundWreck> wrecks = sim.getAirfieldService().groundWrecks();
        assertEquals(1, wrecks.size(), "the hull it died with should be sitting on the taxiway");
        GroundWreck wreck = wrecks.get(0);
        assertFalse(sim.getGrid().isWalkable(wreck.cellX(), wreck.cellY()),
                "the wreck blocks the ground it came down on");

        // The berth it flew from is written off, but the wreck is not there —
        // it is wherever the taxiway kill actually happened.
        List<AirfieldService.Berth> berths = sim.getAirfieldService().berths();
        AirfieldService.Berth shed = berths.get(berths.size() - 1);
        assertEquals(AirfieldService.BerthState.DESTROYED, shed.state,
                "the field lost the aircraft it sent out");
        assertFalse(shed.wreckOnPad,
                "the hull came down on the taxiway, not back on the berth it left");
    }

    /**
     * A machine lost at altitude falls; it does not leave a neat hull at the
     * coordinates it happened to be flying over.
     *
     * <p>The distinction {@code air-nouns.md} draws between the two ways a
     * sortie can end has to survive a taxiway kill acquiring its own wreck —
     * airborne stays airborne's own picture.
     */
    @Test
    void anAircraftShotDownInTheAirLeavesNoGroundWreck() {
        BattleSimulation sim = openField();
        long craft = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                30.5f, 20.5f, 5f, 5f, 5f, 5f, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        mission.strikeSortie = true;
        mission.passesLeft = 3;
        sim.world().kinematics(craft).teleport(30.5f, 20.5f, 0f);
        mission.state = ShuttleState.ATTACK_RUN;
        mission.runFromX = 20f; mission.runFromY = 20.5f;
        mission.runToX = 45f;  mission.runToY = 20.5f;

        // A cluster of anti-air posts right under the run — enough to bring
        // the craft down rather than merely dent it.
        for (int i = 0; i < 4; i++) {
            sim.spawn(new EntitySpec("aa" + i, Faction.MARINE, UnitType.TURRET, 30 + i, 20)
                    .turretStructureId("structure.turret-vulcan")
                    .health(400f));
        }

        for (int i = 0; i < 200 && mission.state != ShuttleState.GONE; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(ShuttleState.GONE, mission.state,
                "the anti-air cluster did not bring it down");
        assertTrue(sim.getAirfieldService().groundWrecks().isEmpty(),
                "a craft lost at altitude falls; it does not leave a hull on the ground");
    }
}
