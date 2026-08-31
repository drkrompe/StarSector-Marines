package com.dillon.starsectormarines.battle.fabrication;

import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.StructureWatch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vehicle bay builds machines out of the work its technicians actually do,
 * and out of nothing else.
 *
 * <p>This is the whole claim of the feature and the one thing about it worth
 * protecting. A shed that turned out a machine every so many seconds would be a
 * timer wearing a building's clothes: an attacker could stand in its doorway
 * killing technicians and the line would run at exactly the same rate, and
 * taking the place would deny the defender nothing until the moment it changed
 * hands. Counting hands instead is what makes every consequence fall out on its
 * own — and it is invisible from outside, because both versions produce
 * machines.
 *
 * <p>So the control is half the test rather than an afterthought: the same shed,
 * the same berths, the same stocks, nobody in it. If that one advances too, the
 * measurement is of the clock.
 */
class AShedBuildsWhatItsCrewWorksTest {

    private static final int W = 40;
    private static final int H = 24;
    /** The bay, as a rectangle of stamped floor. */
    private static final int BAY_X = 6;
    private static final int BAY_Y = 6;
    private static final int BAY_W = 20;
    private static final int BAY_H = 12;

    /** A shed with a crew in it advances its build; the same shed empty does not. */
    @Test
    void theWorkIsTheCrewsAndNobodyElsesClock() {
        Shed manned = shed(true);
        Shed empty = shed(false);

        assertTrue(manned.crew.size() >= 2,
                "the bay took on " + manned.crew.size() + " hands, which is not a crew");
        assertTrue(empty.crew.isEmpty(), "the control shed hired somebody");

        run(manned, 60f);
        run(empty, 60f);

        assertTrue(handSeconds(manned) > 0f,
                "a minute of a manned shed advanced its build not at all");
        assertEquals(0f, handSeconds(empty), 0f,
                "an empty shed built " + handSeconds(empty)
                        + " hand-seconds of machine on its own");
    }

    /**
     * What is credited is welding, not being in the room.
     *
     * <p>A technician's rotation is servicing, then the parts run, then the
     * readout, then servicing again — so most of a shift is spent in the bay
     * doing something that is not building a machine, and a good deal of it
     * walking between the two. A count that could not tell those apart would
     * make three technicians worth three hand-seconds a second and the crew's
     * rotation decorative.
     */
    @Test
    void standingInTheBayIsNotWorkingOnTheMachine() {
        Shed shed = shed(true);
        float seconds = 60f;

        run(shed, seconds);

        float credited = handSeconds(shed);
        float ifEverybodyWelded = shed.crew.size() * seconds;
        assertTrue(credited < ifEverybodyWelded,
                "every hand in the bay was credited for the whole minute (" + credited
                        + " of a possible " + ifEverybodyWelded + "), so the rotation"
                        + " is not being read");
    }

    /**
     * A finished bay puts an ordinary machine on one of its berths.
     *
     * <p>The work is set to done rather than waited for, because how long a
     * machine takes is a tuning figure and this is about what happens when it is
     * finished. What matters is that the thing that comes out is a unit like any
     * other, on the side that built it, standing where a machine stands.
     */
    @Test
    void aFinishedBayRollsAMachineOut() {
        Shed shed = shed(true);
        assertEquals(0, mechs(shed), "the bay had a machine in it before it built one");

        shed.works.work(shed.works.bays().get(0).siteId,
                FabricationService.HAND_SECONDS_PER_MACHINE);
        run(shed, 1f);

        assertEquals(1, mechs(shed), "the finished build did not come out of the bay");
        assertEquals(1, shed.works.bays().get(0).completed, "the bay did not count its output");
        assertTrue(shed.works.bays().get(0).worked < FabricationService.HAND_SECONDS_PER_MACHINE,
                "the bay kept its finished work rather than laying the next machine down");
    }

    /** A field shed lays down light chassis and not an assault one. */
    @Test
    void aFieldShedBuildsWhatAFieldShedCanBuild() {
        Shed shed = shed(false);

        assertTrue(shed.works.bays().get(0).chassis.maxStructure
                        <= FabricationService.FIELD_SHED_STRUCTURE_LIMIT,
                "the shed has laid a keel for a "
                        + shed.works.bays().get(0).chassis.displayName);
    }

    /** Every berth of a working bay is occupied, which is what makes it a posting. */
    @Test
    void aWorkingBayHasItsBerthsFull() {
        Shed shed = shed(false);

        boolean[] berthed = shed.works.berthed();
        assertEquals(2, berthed.length);
        for (int berth = 0; berth < berthed.length; berth++) {
            assertTrue(berthed[berth], "berth " + berth + " of a working bay stands empty,"
                    + " so the bay publishes no servicing and is nobody's posting");
        }
    }

    /** One shed on a bare map: two berths, the work they publish, and its crew. */
    private record Shed(BattleSimulation sim, FabricationService works, List<Long> crew) { }

    private static Shed shed(boolean manned) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        CellTopology topology = new CellTopology(W, H);
        for (int dx = 0; dx < BAY_W; dx++) {
            for (int dy = 0; dy < BAY_H; dy++) {
                topology.setRoomPurpose(BAY_X + dx, BAY_Y + dy, RoomPurpose.VEHICLE_BAY);
            }
        }

        BattleSimulation sim = new BattleSimulation(grid, topology);
        // Nothing here is a battle and only one side is present, so the terminal
        // check would end it before the first tick and freeze the clock.
        sim.setMissionCompletionEnabled(false);

        List<Gantry> berths = List.of(
                new Gantry(BAY_X + 4, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH),
                new Gantry(BAY_X + 12, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH));
        List<FixtureTask> authored = work(berths);

        List<RoomSite> rooms = RoomSite.findAll(topology, W, H);
        FabricationService works = new FabricationService(berths, authored, rooms);
        sim.setFabrication(works);

        List<Long> crew = manned
                ? StructureWatch.man(sim, Faction.DEFENDER, rooms, authored,
                        works.berthed(), EnumSet.of(RoomPurpose.VEHICLE_BAY), 2)
                : List.of();
        return new Shed(sim, works, crew);
    }

    /**
     * What the bay publishes: servicing at each berth, and the stores and
     * readouts the same rotation also visits.
     *
     * <p>All three, because a rotation of one job is not a rotation — a
     * technician given only welding would weld for the whole minute, and the
     * test that most of a shift is not welding would be measuring a shift that
     * has nothing else to do.
     */
    private static List<FixtureTask> work(List<Gantry> berths) {
        List<FixtureTask> authored = new ArrayList<>();
        for (int berth = 0; berth < berths.size(); berth++) {
            Gantry gantry = berths.get(berth);
            authored.add(FixtureTask.servingBerth(gantry.left() - 1, gantry.centerY,
                    berth, gantry.centerX, gantry.centerY));
            authored.add(FixtureTask.servingBerth(gantry.right() + 1, gantry.centerY,
                    berth, gantry.centerX, gantry.centerY));
            authored.add(FixtureTask.at(gantry.centerX, gantry.top() + 2,
                    Affordance.STOW, gantry.centerX, gantry.top() + 3));
            authored.add(FixtureTask.at(gantry.centerX + 1, gantry.top() + 2,
                    Affordance.READOUT, gantry.centerX + 1, gantry.top() + 3));
        }
        return List.copyOf(authored);
    }

    /**
     * Every hand-second this shed has ever been credited.
     *
     * <p>Not the work standing on the stocks. A bay that finishes a machine
     * clears its counter and lays the next one down, so reading the counter
     * alone reports a shed that has just built something as a shed that has
     * built nothing — which is the opposite of the truth and reads as a pass.
     */
    private static float handSeconds(Shed shed) {
        FabricationService.Works bay = shed.works.bays().get(0);
        return bay.worked + bay.completed * FabricationService.HAND_SECONDS_PER_MACHINE;
    }

    private static void run(Shed shed, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            shed.sim.advance(BattleSimulation.TICK_DT);
        }
    }

    private static int mechs(Shed shed) {
        int found = 0;
        for (int index = 0; index < shed.sim.getRoster().liveCount(); index++) {
            long id = shed.sim.getRoster().get(index);
            if (shed.sim.identity().type(id) == UnitType.HEAVY_MECH) found++;
        }
        return found;
    }

    /** Guards the harness itself: a bay nobody could reach would pass by default. */
    @Test
    void theCrewCanReachTheirOwnWork() {
        Shed shed = shed(true);
        run(shed, 30f);

        assertFalse(shed.crew.isEmpty(), "no crew was hired at all");
        for (long hand : shed.crew) {
            assertTrue(shed.sim.getRoster().isLive(hand), "a technician left the roster");
            assertTrue(shed.sim.getGrid().isWalkable(
                            shed.sim.world().cellX(hand), shed.sim.world().cellY(hand)),
                    "a technician is standing where there is no floor");
        }
    }
}
