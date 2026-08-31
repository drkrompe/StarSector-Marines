package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The airfield's berths, and the aircraft standing on them.
 *
 * <p>A based aircraft is an ordinary unit while it is on the ground, which is
 * the point: it can be seen and shot by everything that already shoots units.
 * These exercise the berth state machine directly rather than through a battle.
 */
class AirfieldSystemTest {

    private static final int W = 20;
    private static final int H = 20;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static AirfieldService.Berth berth(BattleSimulation sim, int x, int y) {
        return sim.getAirfieldService().addBerth(
                LandingPad.garrison(x, y, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
    }

    /** A tick puts an aircraft on every berth that has none. */
    @Test
    void aBerthStandsAnAircraftOnItsPad() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        assertEquals(0L, berth.airframeId, "nothing is placed before the first tick");

        new AirfieldSystem(Faction.DEFENDER)
                .tick(1f / 30f, sim, sim.getAirfieldService());

        assertNotEquals(0L, berth.airframeId, "the berth has an aircraft on it");
        assertEquals(UnitType.BASED_AIRCRAFT,
                sim.identity().type(berth.airframeId));
        assertEquals(Faction.DEFENDER, sim.identity().faction(berth.airframeId));
        assertEquals(10, sim.world().cellX(berth.airframeId));
        assertEquals(10, sim.world().cellY(berth.airframeId));
        assertTrue(sim.getAirfieldService().hasAirworthyAirframe());
    }

    /**
     * An airframe destroyed where it stands takes its berth with it.
     *
     * <p>Permanent on purpose: an attacker who spends the effort to burn an
     * aircraft should be able to see that it stays burned, and a field with
     * nothing left on it stops being an air arm.
     */
    @Test
    void anAircraftBurnedOnItsPadIsNotReplaced() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        long airframe = berth.airframeId;

        sim.applyDamage(airframe, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        assertEquals(AirfieldService.BerthState.DESTROYED, berth.state);
        assertFalse(sim.getAirfieldService().hasAirworthyAirframe(),
                "a field with nothing left on it cannot fly");

        // And stays that way however long the battle runs.
        for (int i = 0; i < 200; i++) {
            system.tick(1f / 30f, sim, sim.getAirfieldService());
        }
        assertEquals(AirfieldService.BerthState.DESTROYED, berth.state);
        assertEquals(0L, berth.airframeId, "nothing replaces it");
    }

    /**
     * The hull stays on the concrete.
     *
     * <p>What a raider gets for walking onto an apron is a burnt aircraft that
     * is visibly still there. The renderer draws that off the berth, because
     * the unit is dead and released long before anybody looks at the pad again.
     */
    @Test
    void anAircraftBurnedOnItsPadLeavesItsHullThere() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        assertFalse(berth.wreckOnPad, "an aircraft standing on its pad is not a wreck");

        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        assertTrue(berth.wreckOnPad, "the hull is still on the hardstand");
    }

    /**
     * One aircraft on the strip at a time.
     *
     * <p>The whole reason a strip is worth attacking: a field with three
     * aircraft and one runway launches them one after another, and anything
     * standing between a shed and the threshold delays every one of them. Two
     * craft rolling down the same strip would be a race the simulation is not
     * entitled to lose.
     */
    @Test
    void theStripTakesOneAircraftAtATime() {
        AirfieldService airfield = new AirfieldService();
        airfield.installRunway(new Runway(4.5f, 10.5f, 30.5f, 10.5f, 4f));

        assertFalse(airfield.runwayBusy(), "an empty field has a free strip");
        assertTrue(airfield.claimRunway(11L), "the first craft takes it");
        assertTrue(airfield.runwayBusy());
        assertEquals(11L, airfield.runwayOccupant());

        assertFalse(airfield.claimRunway(22L), "the second holds short");
        assertTrue(airfield.claimRunway(11L), "the holder may re-assert every tick");

        // Somebody who never had it cannot give it away.
        airfield.releaseRunway(22L);
        assertEquals(11L, airfield.runwayOccupant(), "released by the wrong craft");

        airfield.releaseRunway(11L);
        assertFalse(airfield.runwayBusy(), "and now the next one can go");
        assertTrue(airfield.claimRunway(22L));
    }

    /**
     * A shed is a berth, and it is not a stand.
     *
     * <p>The two are asked for by different callers wanting different things: a
     * vertical-lift transport needs somewhere it can rise off, and handing it a
     * shed would strand it there. Nothing lands in a shelter, so a shelter
     * berth carries no landing pad at all.
     */
    @Test
    void aShelterIsABerthButNeverAStand() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth stand = berth(sim, 10, 10);
        AirfieldService.Berth shed = airfield.addShelterBerth(
                new Gantry(4, 4, 2, 2, Gantry.Facing.SOUTH), ShuttleType.AEROSHUTTLE);

        assertEquals(AirfieldService.Kind.HARDSTAND, stand.kind);
        assertEquals(AirfieldService.Kind.SHELTER, shed.kind);
        assertNull(shed.pad, "nothing lands in a shed");
        assertEquals(4, shed.centerX);
        assertEquals(4, shed.centerY);

        // A lift-off caller asking for the nearest berth gets the stand even
        // though the shed is closer.
        AirfieldService.Berth lift = airfield.nearestAirworthy(4.5f, 4.5f);
        assertEquals(stand, lift, "a vertical lift was offered a shed");
        assertEquals(shed, airfield.nearestAirworthy(4.5f, 4.5f,
                AirfieldService.Kind.SHELTER), "asked for a shed and got something else");
    }

    /**
     * A shed can keep a fighter, and the fighter it keeps is the one that gets
     * shot at.
     *
     * <p>The berth used to name a {@link ShuttleType}, which made every
     * aircraft on every base a transport whatever the base was for. What it
     * holds now is an {@link Airframe}, so a station's sheds hold the hulls the
     * game actually flies fighters in — and the difference has to reach the
     * unit standing on the ground, or a Broadsword is a Kite with a different
     * picture.
     */
    @Test
    void aShedCanHoldAFighterAndTheFighterIsWhatStandsThere() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth shed = airfield.addShelterBerth(
                new Gantry(4, 4, 2, 2, Gantry.Facing.SOUTH), FighterProfile.BROADSWORD);

        assertEquals(FighterProfile.BROADSWORD, shed.airframe);
        assertEquals(FighterProfile.BROADSWORD.maxHp(), shed.hullHp, 1e-3f,
                "a fresh berth starts on its own airframe's hull");

        new AirfieldSystem(Faction.DEFENDER)
                .tick(1f / 30f, sim, airfield);

        assertNotEquals(0L, shed.airframeId, "nothing was stood in the shed");
        assertEquals(FighterProfile.BROADSWORD.maxHp(),
                sim.world().maxHp(shed.airframeId), 1e-3f,
                "the unit was built from something other than its own airframe");
        assertTrue(sim.world().maxHp(shed.airframeId) < ShuttleType.AEROSHUTTLE.maxHp(),
                "a parked fighter is no lighter to write off than a transport");
    }

    /**
     * A turnaround puts back the fighter's own hull and not a transport's.
     *
     * <p>The repair is a fraction of the ceiling, so a berth that took its
     * ceiling from the wrong airframe hands back an aircraft with more
     * structure than the type has.
     */
    @Test
    void aFighterIsRepairedTowardItsOwnCeiling() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth shed = airfield.addShelterBerth(
                new Gantry(4, 4, 2, 2, Gantry.Facing.SOUTH), FighterProfile.WASP);
        airfield.launch(shed);

        airfield.recover(shed, 10_000f);

        assertEquals(FighterProfile.WASP.maxHp(), shed.hullHp, 1e-3f,
                "recovered above its own airframe's ceiling");
    }

    /** A field with no strip says so rather than pretending to have one. */
    @Test
    void aFieldWithoutAStripHasNone() {
        AirfieldService airfield = new AirfieldService();
        assertNull(airfield.runway());
        assertFalse(airfield.runwayBusy());
    }

    /**
     * The hull is an obstacle, and only an obstacle.
     *
     * <p>Walk around it; see and shoot straight through it. A non-walkable cell
     * is opaque here unless it says otherwise, and a burnt-out airframe is a
     * frame with holes in it — where the intact aircraft that stood there was a
     * solid object on the cell it stood on.
     */
    @Test
    void theWreckBlocksTheApronWithoutBlindingIt() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        assertTrue(sim.getGrid().blocksLineOfSight(10, 10),
                "an aircraft standing on its pad is something to see round");

        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        for (int y = 9; y <= 11; y++) {
            for (int x = 9; x <= 11; x++) {
                assertFalse(sim.getGrid().isWalkable(x, y),
                        "nobody walks through the wreck at (" + x + "," + y + ")");
                assertFalse(sim.getGrid().blocksLineOfSight(x, y),
                        "sight and fire cross it at (" + x + "," + y + ")");
            }
        }
        assertTrue(sim.getGrid().isWalkable(12, 10), "the apron beside it is untouched");
    }

    /**
     * Anybody standing where the hull comes down steps out from under it.
     *
     * <p>The ground crew walk onto the pad to fly the thing; a raider walks
     * onto it to burn the thing. Either can be standing on the concrete at the
     * moment it stops being concrete.
     */
    @Test
    void somebodyUnderTheWreckIsSteppedClear() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        long crew = sim.spawn(new EntitySpec("crew", Faction.DEFENDER, UnitType.MARINE, 10, 10));

        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        int x = sim.world().cellX(crew);
        int y = sim.world().cellY(crew);
        assertTrue(Math.abs(x - 10) > 1 || Math.abs(y - 10) > 1,
                "still under the hull at (" + x + "," + y + ")");
        assertTrue(sim.getGrid().isWalkable(x, y), "and standing somewhere they can stand");
    }

    /**
     * With nowhere to step, the wreck leaves that cell open rather than sealing
     * somebody into it.
     *
     * <p>A unit that cannot move stops answering its orders for the rest of the
     * battle, which is a far worse outcome than a hull with a gap in it.
     */
    @Test
    void theWreckWillNotSealSomebodyIn() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        long crew = sim.spawn(new EntitySpec("crew", Faction.DEFENDER, UnitType.MARINE, 10, 10));
        // Nothing outside the hull's own footprint will take a step.
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (Math.abs(x - 10) > 1 || Math.abs(y - 10) > 1) {
                    sim.getGrid().setWalkable(x, y, false);
                }
            }
        }
        // The intact aircraft comes down on them first and leaves the cell open
        // for the same reason the wreck is about to.
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        assertEquals(10, sim.world().cellX(crew), "there was nowhere to put them");
        assertEquals(10, sim.world().cellY(crew));
        assertTrue(sim.getGrid().isWalkable(10, 10), "so the wreck left that cell alone");
        assertFalse(sim.getGrid().isWalkable(9, 9), "and closed the rest of itself");
    }

    /**
     * An aircraft lost over the objective leaves an empty stand.
     *
     * <p>The same terminal state as burning on the pad, and deliberately not
     * the same picture: nothing came down here.
     */
    @Test
    void anAircraftLostOverTheObjectiveLeavesTheStandEmpty() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldService airfield = sim.getAirfieldService();
        airfield.launch(berth);

        airfield.destroyed(berth);

        assertEquals(AirfieldService.BerthState.DESTROYED, berth.state);
        assertFalse(berth.wreckOnPad, "it did not come down on its own hardstand");
    }

    /** The aircraft is a target, never a shooter. */
    @Test
    void aBasedAircraftNeverFires() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        new AirfieldSystem(Faction.DEFENDER)
                .tick(1f / 30f, sim, sim.getAirfieldService());

        assertEquals(0f, sim.world().attackDamage(berth.airframeId),
                "a parked hull is a target, not a weapon");
        assertEquals(0f, sim.world().attackRange(berth.airframeId));
        assertTrue(UnitType.BASED_AIRCRAFT.isStatic(),
                "it neither paths nor thinks");
    }

    /** A sortie takes an airframe off its pad, and the pad is empty while it is gone. */
    @Test
    void aLaunchedAircraftLeavesAnEmptyPad() {
        BattleSimulation sim = openSim();
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, service);
        long airframe = berth.airframeId;

        float hull = service.launch(berth);
        assertEquals(ShuttleType.AEROSHUTTLE.maxHp, hull, "it flies with the hull it had");
        assertEquals(AirfieldService.BerthState.AWAY, berth.state);
        assertFalse(service.hasAirworthyAirframe(), "there is nothing left to send");

        system.tick(1f / 30f, sim, service);
        assertEquals(0L, berth.airframeId, "the pad is empty while its aircraft is out");
        assertFalse(onMap(sim, airframe),
                "and the unit that stood in for it is off the map");
        assertTrue(sim.getDeathsThisFrame().isEmpty(),
                "taking off is not a death: no wreck, no casualty");
    }

    /**
     * Whether a unit is still on the live roster. Released units keep readable
     * component state — the same as a marine taken aboard a shuttle — so
     * liveness is the roster walk, not an HP read.
     */
    private static boolean onMap(BattleSimulation sim, long unit) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.liveUnitAt(i) == unit) return true;
        }
        return false;
    }

    /**
     * A returned airframe stands on its pad and is not available again until it
     * has been worked back up.
     *
     * <p>Two halves, and the second is the whole of what a turnaround now is.
     * Time alone does nothing: a field whose ground crew are dead never answers
     * another request, however long it is left, because a countdown that ran
     * without them made killing them worth nothing. And the aircraft is on the
     * concrete while it waits, which is what gives an attacker something to
     * interrupt — servicing that took the aircraft off the map for its duration
     * was a promise nobody could touch.
     */
    @Test
    void aReturnedAircraftStandsOnItsPadUntilItIsWorkedBackUp() {
        BattleSimulation sim = openSim();
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, service);
        service.launch(berth);

        // Home, shot up.
        float damaged = ShuttleType.AEROSHUTTLE.maxHp * 0.2f;
        service.recover(berth, damaged);
        assertEquals(AirfieldService.BerthState.REFITTING, berth.state);
        assertFalse(service.hasAirworthyAirframe(),
                "a field cannot answer two requests back to back");

        for (int i = 0; i < 30 * 120; i++) system.tick(1f / 30f, sim, service);

        assertEquals(AirfieldService.BerthState.REFITTING, berth.state,
                "two minutes of nobody working turned the aircraft round anyway");
        assertNotEquals(0L, berth.airframeId,
                "the aircraft is not on its pad, so there is nothing to work on"
                        + " and nothing for a raid to catch");
        assertEquals(damaged, sim.world().hp(berth.airframeId), 0.5f,
                "it is standing there with the hull it came home with");

        // What the ground crew do, done to it.
        sim.world().setHp(berth.airframeId, berth.refitTarget);
        berth.refitWork = 0f;
        system.tick(1f / 30f, sim, service);

        assertEquals(AirfieldService.BerthState.PARKED, berth.state);
        assertTrue(service.hasAirworthyAirframe());
        assertTrue(berth.hullHp > damaged,
                "a turnaround patches the hull: " + berth.hullHp);
        assertTrue(berth.hullHp < ShuttleType.AEROSHUTTLE.maxHp,
                "but a field does not rebuild an airframe: " + berth.hullHp);
    }

    /** A sortie flies from the stand nearest what it is going to. */
    @Test
    void theNearestAirworthyStandSendsTheSortie() {
        BattleSimulation sim = openSim();
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Berth near = berth(sim, 4, 4);
        AirfieldService.Berth far = berth(sim, 16, 16);

        assertEquals(near, service.nearestAirworthy(2f, 2f));
        assertEquals(far, service.nearestAirworthy(18f, 18f));

        service.launch(near);
        assertEquals(far, service.nearestAirworthy(2f, 2f),
                "with the near stand empty the sortie comes off the far one");
    }
}
