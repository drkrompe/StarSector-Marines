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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vehicle bay builds machines out of the work its technicians actually do,
 * and out of nothing else — onto a body anybody can shoot.
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
 * the same berths, nobody in it. If that one builds too, the measurement is of
 * the clock.
 *
 * <p>The other half is the body. A machine on the stocks is a unit whose
 * structure is how built it is, so welding is putting it together and a marine's
 * fire is taking it apart, and neither needs a rule of its own.
 */
class AShedBuildsWhatItsCrewWorksTest {

    private static final int W = 40;
    private static final int H = 24;
    /** The bay, as a rectangle of stamped floor. */
    private static final int BAY_X = 6;
    private static final int BAY_Y = 6;
    private static final int BAY_W = 20;
    private static final int BAY_H = 12;

    /** A shed with a crew in it builds; the same shed empty does not. */
    @Test
    void theWorkIsTheCrewsAndNobodyElsesClock() {
        Shed manned = shed(true);
        Shed empty = shed(false);

        assertTrue(manned.crew.size() >= 2,
                "the bay took on " + manned.crew.size() + " hands, which is not a crew");
        assertTrue(empty.crew.isEmpty(), "the control shed hired somebody");

        run(manned, 60f);
        run(empty, 60f);

        assertTrue(manned.works.bays().get(0).hasFrame(),
                "a minute of a manned shed put nothing on its stocks at all");
        assertTrue(structureBuilt(manned) > keel(manned),
                "the crew laid a keel and then welded nothing onto it");
        assertFalse(empty.works.bays().get(0).hasFrame(),
                "a shed with nobody in it laid a keel by itself");
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

        float welded = structureBuilt(shed) - keel(shed);
        float ifEverybodyWelded = shed.crew.size() * seconds
                * FabricationService.STRUCTURE_PER_HAND_SECOND;
        assertTrue(welded < ifEverybodyWelded,
                "every hand in the bay was credited for the whole minute (" + welded
                        + " of a possible " + ifEverybodyWelded + "), so the rotation"
                        + " is not being read");
    }

    /**
     * A machine finished is a machine off the stocks and an ordinary unit in its
     * place.
     *
     * <p>The structure is set to whole rather than waited for, because how long
     * a machine takes is a tuning figure and this is about what happens when it
     * is done. What matters is that the frame goes and a chassis of the side
     * that built it stands where it stood — not both, which would be a garrison
     * gaining a mech and keeping the scaffolding.
     */
    @Test
    void aFinishedMachineComesOffTheStocks() {
        Shed shed = shed(true);
        assertEquals(0, mechs(shed), "the bay had a machine in it before it built one");

        run(shed, 2f);
        FabricationService.Works bay = shed.works.bays().get(0);
        assertTrue(bay.hasFrame(), "the crew laid nothing to finish");
        long frame = bay.frameId;
        shed.sim.world().setHp(frame, shed.sim.world().maxHp(frame));
        run(shed, BattleSimulation.TICK_DT);

        assertEquals(1, mechs(shed), "the finished machine did not come out of the bay");
        assertEquals(1, bay.completed, "the bay did not count its output");
        assertFalse(shed.sim.getRoster().isLive(frame),
                "the frame is still standing in the gantry beside the machine it became");
    }

    /**
     * A machine shot on the stocks is work lost.
     *
     * <p>This is what a body is for. Nothing is refunded and nothing is
     * remembered: the crew comes back to an empty gantry and starts from a keel,
     * which is what makes an attacker's rounds worth spending on the building
     * rather than only on the people in it.
     */
    @Test
    void aMachineShotOnTheStocksIsWorkLost() {
        Shed shed = shed(true);
        run(shed, 30f);

        FabricationService.Works bay = shed.works.bays().get(0);
        assertTrue(bay.hasFrame(), "there was nothing on the stocks to shoot");
        long frame = bay.frameId;
        float built = shed.sim.world().hp(frame);
        assertTrue(built > keel(shed), "the machine had not been worked on yet");

        shed.sim.applyDamage(frame, shed.sim.world().maxHp(frame) * 2f, 1f);
        run(shed, BattleSimulation.TICK_DT);

        assertFalse(shed.sim.getRoster().isLive(frame),
                "the frame survived twice its own structure");
        assertEquals(0, bay.completed, "a destroyed machine was counted as output");

        // Caught on the tick it appears. Nobody lays a keel from the stores or
        // the readout, so the crew starts again on their own cadence rather than
        // on the tick the wreck was cleared — and by the time a fixed wait was
        // over they would have welded the replacement past the point the
        // question is about.
        long replacement = layNext(shed, bay, 30f);
        assertTrue(replacement != 0L, "the crew never started again");
        assertNotEquals(frame, replacement, "the destroyed machine came back");
        assertTrue(shed.sim.world().hp(replacement)
                        <= keel(shed) + FabricationService.STRUCTURE_PER_HAND_SECOND,
                "the replacement started at " + shed.sim.world().hp(replacement)
                        + " rather than at a keel, so the "  + built
                        + " points the destroyed machine had were refunded");
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

    /**
     * A bay offers servicing where its machine stands, and not at its empty
     * gantries.
     *
     * <p>Both halves. Without the first the room is nobody's posting and the
     * shed generates furnished and unstaffed; without the second the crew is
     * sent to weld on empty air, which is the mistake the berth-bound link
     * exists to prevent, arrived at from the generous end.
     */
    @Test
    void aBayOffersServicingWhereTheMachineIs() {
        Shed shed = shed(false);

        boolean[] berthed = shed.works.berthed();
        assertEquals(2, berthed.length);
        int stocks = shed.works.bays().get(0).stocks;
        assertTrue(berthed[stocks],
                "the berth the machine stands in publishes no servicing,"
                        + " so the bay is nobody's posting");
        assertFalse(berthed[stocks == 0 ? 1 : 0],
                "an empty gantry is offering work on the machine in it");
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
     * Every point of structure this shed has ever put on a machine.
     *
     * <p>Not the structure standing in the gantry. A bay that finishes a machine
     * clears its stocks, so reading the body alone reports a shed that has just
     * built one as a shed that has built nothing — the opposite of the truth,
     * and it reads as a pass.
     */
    private static float structureBuilt(Shed shed) {
        FabricationService.Works bay = shed.works.bays().get(0);
        float standing = bay.hasFrame() ? shed.sim.world().hp(bay.frameId) : 0f;
        return standing + bay.completed * bay.chassis.maxStructure;
    }

    /** What a keel of this bay's chassis is worth, so welding can be told from laying. */
    private static float keel(Shed shed) {
        return shed.works.bays().get(0).chassis.maxStructure
                * FabricationService.KEEL_FRACTION;
    }

    /**
     * Tick until this bay has something on its stocks again, and answer with it
     * the moment it appears.
     *
     * @return the new body, or 0 if none was laid inside {@code within}
     */
    private static long layNext(Shed shed, FabricationService.Works bay, float within) {
        int ticks = Math.round(within / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            shed.sim.advance(BattleSimulation.TICK_DT);
            if (bay.hasFrame()) return bay.frameId;
        }
        return 0L;
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
}
