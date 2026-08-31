package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
 *
 * <p>It then became half true. The aircraft lost hull to an attrition field —
 * a headcount of enemies within ten cells, times a flat rate — which nothing
 * else in the battle knew about: nobody aimed at it, nothing was fired, no
 * cover or wall stopped anything, and no shooter was credited. What these
 * assert now is that the fire is real fire.
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
        sim.world().setHp(craft, airfield.launch(shed));
        mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, 50.5f, 30.5f);
        sim.world().kinematics(craft).teleport(SHELTER_X, SHELTER_Y, 0f);
        return craft;
    }

    /**
     * A fire team standing on the apron beside the taxiing aircraft.
     *
     * <p>Squadded and carrying rifles, because a marine spawned from a bare
     * spec has no loadout and cannot fire and acquisition runs off the squad —
     * an unarmed, unsquadded "ambush" is six people watching an aircraft go
     * past, which is exactly what the attrition field it replaces could not
     * tell apart from a fire team.
     */
    private static List<Long> fireTeamBesideTheTaxiway(BattleSimulation sim, int size) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(size, new Random(7L));
        List<Long> team = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            EntitySpec spec = new EntitySpec("r" + i, Faction.MARINE, UnitType.MARINE,
                    (int) SHELTER_X - 3 + i, (int) SHELTER_Y - 4);
            kit[i].seedInto(spec);
            spec.squad(squadId);
            team.add(sim.spawn(spec));
        }
        if (squad != null) squad.originalSize = size;
        return team;
    }

    /**
     * Whether an ordinary proximity query — the one every scan runs on — finds
     * the craft.
     *
     * <p>The index is rebuilt directly rather than by advancing the battle,
     * because the question is about membership at a given phase and the state
     * machine would have moved the craft on to a different one by the time a
     * tick finished.
     */
    private static boolean foundByLookingAround(BattleSimulation sim, long craft) {
        sim.getUnitIndex().rebuild(sim.getRoster());
        LongBucket near = new LongBucket();
        sim.getUnitIndex().gather(sim.world().x(craft), sim.world().y(craft), 2f, near);
        for (int i = 0; i < near.size; i++) {
            if (near.ids[i] == craft) return true;
        }
        return false;
    }

    /**
     * Every phase is placed on one side of the exposure line or the other.
     *
     * <p>The predicate derives from {@link AirLocomotion} rather than naming
     * phases, so this is the assertion that the derivation says what the model
     * says — and it is asked of every phase the enum holds, because the failure
     * being guarded is a phase quietly falling out.
     */
    @Test
    void everyPhaseKnowsWhetherTheAircraftIsExposedOnItsWheels() {
        ShuttleMission mission = new ShuttleMission(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0);
        for (ShuttleState state : ShuttleState.values()) {
            mission.state = state;
            boolean grounded = AirLocomotion.of(state) == AirLocomotion.GROUNDED;
            boolean rampOpen = state == ShuttleState.LOADING || state == ShuttleState.LANDED;
            assertEquals(grounded && !rampOpen, mission.isOnItsWheelsAndExposed(),
                    state + " is on the wrong side of the exposure line");
        }
    }

    /**
     * A craft on its wheels is a body anybody scanning around them finds; one
     * in the air is not.
     *
     * <p>This is the whole mechanism. Nothing in the combat stack was taught
     * what an aircraft is — it reaches acquisition, ballistics and splash by
     * being in the index, exactly as a convoy chassis does, and it leaves all
     * three by leaving the index when it lifts.
     */
    @Test
    void anAircraftOnItsWheelsIsABodyAndOneInTheAirIsNot() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        assertTrue(foundByLookingAround(sim, craft), "a taxiing aircraft is not a body");

        mission.state = ShuttleState.INCOMING;
        assertFalse(foundByLookingAround(sim, craft),
                "an aircraft in the air was reachable by ground fire");
    }

    /**
     * A craft with its ramp open stays exempt, at both ends of the trip.
     *
     * <p>Loading is the load-bearing one: its passengers have already been
     * taken off the roster, so shooting it down would owe them a disposition
     * nothing provides. Landed is the same craft at the far end.
     */
    @Test
    void aCraftWithItsRampOpenIsNotABody() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);

        for (ShuttleState rampOpen : new ShuttleState[]{ShuttleState.LOADING, ShuttleState.LANDED}) {
            mission.state = rampOpen;
            assertFalse(foundByLookingAround(sim, craft),
                    rampOpen + " was reachable by ground fire");
        }
    }

    /**
     * A fire team alongside the taxiway takes a taxiing aircraft apart, and the
     * rounds that do it come out of somebody's rifle.
     *
     * <p>Asserted on the shooters as well as the hull, because the fault this
     * replaces was an invisible drain: the aircraft lost structure while
     * nothing in the battle had aimed at it or fired a round.
     */
    @Test
    void aTaxiingAircraftIsShotAtByTroopsBesideIt() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        float startedWith = sim.world().hp(craft);
        List<Long> team = fireTeamBesideTheTaxiway(sim, 6);

        for (int i = 0; i < 120 && mission.state == ShuttleState.TAXI_OUT; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.world().hp(craft) < startedWith,
                "a fire team stood beside the taxiway and the aircraft took nothing");
        int fired = 0;
        for (long shooter : team) fired += sim.telemetry().roundsFired(shooter);
        assertTrue(fired > 0, "the aircraft lost hull without anybody firing at it");
    }

    /**
     * A wall between the fire team and the taxiway stops the fire.
     *
     * <p>The point of routing an aircraft through the ordinary pipeline rather
     * than a proximity drain: a drain does not know what is in the way, and
     * this one demonstrably did not — ten cells of solid building counted the
     * same as ten cells of concrete.
     */
    @Test
    void aWallBetweenThemStopsIt() {
        BattleSimulation sim = openField();
        NavigationGrid grid = sim.getGrid();
        for (int x = (int) SHELTER_X - 8; x <= (int) SHELTER_X + 8; x++) {
            grid.setWalkable(x, (int) SHELTER_Y - 2, false);
        }
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        float startedWith = sim.world().hp(craft);
        fireTeamBesideTheTaxiway(sim, 6);

        for (int i = 0; i < 120 && mission.state == ShuttleState.TAXI_OUT; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(startedWith, sim.world().hp(craft), 0.001f,
                "a fire team shot an aircraft through a building");
    }

    /** And with nobody there it crosses untouched, so the exposure is the troops. */
    @Test
    void anUninterferedTaxiCostsTheAircraftNothing() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        float startedWith = sim.world().hp(craft);
        // Somebody far away, so the battle keeps ticking without being a threat.
        sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 2, 38));

        for (int i = 0; i < 600 && mission.state == ShuttleState.TAXI_OUT; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(startedWith, sim.world().hp(craft), 0.001f,
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
        float startedWith = sim.world().hp(craft);

        // An anti-air post right under the run.
        sim.spawn(new EntitySpec("aa", Faction.MARINE, UnitType.TURRET, 30, 20)
                .turretStructureId("structure.turret-vulcan")
                .health(400f));

        for (int i = 0; i < 60 && mission.state == ShuttleState.ATTACK_RUN; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.world().hp(craft) < startedWith,
                "an aircraft made gun runs over an anti-air post and took nothing");
    }

    /**
     * A machine killed rolling under its own power owes what one killed on its
     * pad owes: the same fireball, a hull that stays exactly where the fire
     * caught it, the berth written off, the runway given back, and somebody
     * credited with it.
     *
     * <p>It is the same full tank under the same thin skin either way, so
     * whether it was parked or moving when the fire started is not a reason
     * for the fire to be different. The runway is in here because a claim that
     * outlives its aircraft is a field closed for the rest of the battle, and
     * that is what a kill on the strip used to leave behind.
     */
    @Test
    void aTaxiingAircraftKilledOnTheGroundLeavesAWreck() {
        BattleSimulation sim = openField();
        long craft = taxiingFighter(sim);
        ShuttleMission mission = sim.world().mission(craft);
        // A heavier team than the earlier "took something" test — this one
        // has to actually finish the aircraft off.
        List<Long> team = fireTeamBesideTheTaxiway(sim, 12);

        for (int i = 0; i < 600 && mission.state != ShuttleState.GONE; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(ShuttleState.GONE, mission.state,
                "the fire team beside the taxiway did not bring it down");
        List<GroundWreck> wrecks = sim.getAirfieldService().groundWrecks();
        assertEquals(1, wrecks.size(), "the hull it died with should be sitting on the taxiway");
        GroundWreck wreck = wrecks.get(0);
        assertFalse(sim.getGrid().isWalkable(wreck.cellX(), wreck.cellY()),
                "the wreck blocks the ground it came down on");

        int kills = 0;
        for (long shooter : team) kills += sim.telemetry().kills(shooter);
        assertEquals(1, kills, "nobody was credited with burning the aircraft");

        // The berth it flew from is written off, but the wreck is not there —
        // it is wherever the taxiway kill actually happened.
        List<AirfieldService.Berth> berths = sim.getAirfieldService().berths();
        AirfieldService.Berth shed = berths.get(berths.size() - 1);
        assertEquals(AirfieldService.BerthState.DESTROYED, shed.state,
                "the field lost the aircraft it sent out");
        assertFalse(shed.wreckOnPad,
                "the hull came down on the taxiway, not back on the berth it left");
        assertTrue(sim.getAirfieldService().claimRunway(craft + 1),
                "the strip stayed claimed by an aircraft that no longer exists");
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
