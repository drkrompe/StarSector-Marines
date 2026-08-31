package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.StructureWatch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An airfield's apron holds work, and the work names the aircraft it is done on.
 *
 * <p>The same trade that works a motor pool, doing the same thing: a machine is
 * standing in a berth and somebody is servicing it. What is different is who
 * authors the work. A fitted room's jobs are cut with the room, and the berth a
 * job names is one that fitting laid; an airfield is a paved lot with hardstands
 * marked on it, and which of them are berths — in what order, holding what — is
 * settled at battle setup by {@link AirfieldService}.
 *
 * <p>That is the thing worth guarding. A servicing job names its berth by
 * <em>index</em>, and an index means nothing except against the list it indexes.
 * Get it wrong and the field still generates, the crew still walks out, and
 * every technician services the wrong aircraft — a fault with no symptom
 * anything checks. So these ask what the index actually points at.
 */
class AnApronIsWorkedByItsGroundCrewTest {

    private static final int W = 40;
    private static final int H = 30;

    /** Every stand is worked from both flanks, with its board ahead of the nose. */
    @Test
    void everyStandIsWorkedFromBothFlanksAndItsOwnBoard() {
        NavigationGrid grid = openField();
        AirfieldService field = fieldWith(grid, 10, 12, 20, 12, 30, 12);

        List<FixtureTask> work = AirfieldWork.onTheApron(field, grid);

        assertEquals(9, work.size(), "three stands published " + work.size() + " jobs");
        assertEquals(6, count(work, Affordance.SERVICE));
        assertEquals(3, count(work, Affordance.READOUT));
    }

    /**
     * A servicing job names the berth it is beside, and no other.
     *
     * <p>The whole point of the index. A job that named the next stand along
     * would have a technician walk to one aircraft and be credited against
     * another, and nothing downstream could tell: both are berths, both are on
     * the field, and both have somebody standing at them.
     */
    @Test
    void aServicingJobNamesTheStandItIsBeside() {
        NavigationGrid grid = openField();
        AirfieldService field = fieldWith(grid, 10, 12, 20, 12, 30, 12);

        for (FixtureTask task : AirfieldWork.onTheApron(field, grid)) {
            if (task.affordance() != Affordance.SERVICE) continue;
            assertTrue(task.berth() >= 0 && task.berth() < field.berths().size(),
                    "a servicing job names berth " + task.berth() + " of "
                            + field.berths().size());
            AirfieldService.Berth named = field.berths().get(task.berth());
            assertEquals(named.centerX, nearestStand(field, task).centerX,
                    "the job at " + task.cellX() + "," + task.cellY()
                            + " names a stand that is not the one it is standing at");
            assertEquals(named.centerX, task.fixtureX(),
                    "the job is not being done on the aircraft it names");
            assertEquals(named.centerY, task.fixtureY());
        }
    }

    /** Nobody is asked to stand where an aircraft is standing. */
    @Test
    void nobodyStandsOnAnAircraft() {
        NavigationGrid grid = openField();
        // Close enough that one stand's flank falls on the next stand's middle.
        AirfieldService field = fieldWith(grid, 10, 12, 12, 12);

        Set<Long> stands = new HashSet<>();
        for (AirfieldService.Berth berth : field.berths()) {
            stands.add(key(berth.centerX, berth.centerY));
        }
        for (FixtureTask task : AirfieldWork.onTheApron(field, grid)) {
            assertFalse(stands.contains(key(task.cellX(), task.cellY())),
                    "a job at " + task.cellX() + "," + task.cellY()
                            + " stands somebody inside a parked aircraft");
        }
    }

    /** A stand backed against something is worked from whatever side is left. */
    @Test
    void aStandAgainstAWallIsWorkedFromTheOtherSide() {
        NavigationGrid grid = openField();
        AirfieldService field = fieldWith(grid, 10, 12);
        for (int y = 0; y < H; y++) grid.setWalkable(8, y, false);

        List<FixtureTask> work = AirfieldWork.onTheApron(field, grid);

        assertEquals(1, count(work, Affordance.SERVICE),
                "the walled flank was still offered as somewhere to stand");
        assertEquals(1, count(work, Affordance.READOUT));
    }

    /**
     * Occupancy is read off the berth, not off whether a unit is standing there
     * yet.
     *
     * <p>The board is published during setup and the field puts its aircraft out
     * on its first tick. Asking for the unit would find every stand empty, offer
     * no servicing anywhere, and leave the field unmanned for the whole battle —
     * a crew lost to one tick of ordering, and it would look exactly like an
     * airfield nobody had got round to staffing.
     */
    @Test
    void aStandIsOccupiedBeforeItsAircraftIsPlaced() {
        NavigationGrid grid = openField();
        AirfieldService field = fieldWith(grid, 10, 12, 20, 12, 30, 12);
        for (AirfieldService.Berth berth : field.berths()) {
            assertEquals(0L, berth.airframeId, "nothing is placed before the first tick");
        }

        boolean[] occupied = AirfieldWork.occupied(field);
        assertEquals(3, occupied.length);
        for (int berth = 0; berth < occupied.length; berth++) {
            assertTrue(occupied[berth], "stand " + berth + " reads as empty before its"
                    + " aircraft is placed, so the field publishes no servicing at all");
        }

        field.launch(field.berths().get(0));
        field.destroyed(field.berths().get(1));
        occupied = AirfieldWork.occupied(field);
        assertFalse(occupied[0], "a stand whose aircraft is away is being serviced");
        assertFalse(occupied[1], "a written-off stand is being serviced");
        assertTrue(occupied[2], "the one aircraft still on the field is not being serviced");
    }

    /**
     * A field with a crew in it has people at its aircraft.
     *
     * <p>The end of the chain rather than a piece of it: the work is published,
     * the trade is based on it, hands are taken on, and a minute later somebody
     * is standing at a stand doing the job. Any link broken and this is empty,
     * which is what an unmanned airfield looks like from outside.
     */
    @Test
    void aFieldWithACrewHasPeopleAtItsAircraft() {
        NavigationGrid grid = openField();
        CellTopology topology = new CellTopology(W, H);
        for (int x = 4; x < W - 4; x++) {
            for (int y = 4; y < H - 4; y++) {
                topology.setRoomPurpose(x, y, RoomPurpose.HANGAR);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology);
        // One side only, so the terminal check would end it before the first tick.
        sim.setMissionCompletionEnabled(false);
        AirfieldService field = sim.getAirfieldService();
        stand(field, 10, 12);
        stand(field, 20, 12);
        stand(field, 30, 12);

        List<FixtureTask> apron = AirfieldWork.onTheApron(field, grid);
        List<RoomSite> rooms = RoomSite.findAll(topology, W, H);
        List<Long> crew = StructureWatch.man(sim, Faction.DEFENDER, rooms, apron,
                AirfieldWork.occupied(field), EnumSet.of(RoomPurpose.HANGAR), 2);

        assertFalse(crew.isEmpty(), "the field took nobody on");
        for (int tick = 0; tick < 30 * 60; tick++) sim.advance(BattleSimulation.TICK_DT);

        int atWork = 0;
        for (long hand : crew) {
            assertTrue(sim.getRoster().isLive(hand), "a technician left the roster");
            if (sim.ambientTasks().jobInHand(hand) != null) atWork++;
        }
        assertTrue(atWork > 0, "a minute in, none of the " + crew.size()
                + " hands taken on is at any job");
    }

    /**
     * A crew on the apron works an aircraft's hull back up; the same field
     * without them does not.
     *
     * <p>The end of the chain and the point of the whole thing. A turnaround
     * used to be a countdown, so a field answered its next request on schedule
     * whether or not anybody was working — an attacker who killed the ground
     * crew denied it nothing at all. The control is half the test: leave the
     * field unmanned for the same two minutes and the aircraft has to still be
     * sitting there with the hull it came home with.
     */
    @Test
    void aCrewOnTheApronWorksAnAircraftBackUp() {
        assertTrue(hullMended(true) > 0f,
                "two minutes of a manned apron put no hull back on anything");
        assertEquals(0f, hullMended(false), 0.5f,
                "an apron with nobody on it turned an aircraft round by itself");
    }

    /**
     * How much hull a field puts back on one shot-up aircraft in two minutes.
     *
     * <p>One stand rather than a rank of them, because the question is whether
     * the work reaches the aircraft at all. A field with six stands and three
     * hands turns one round more slowly, which is its shape rather than a
     * separate fact.
     */
    private static float hullMended(boolean manned) {
        NavigationGrid grid = openField();
        CellTopology topology = new CellTopology(W, H);
        for (int x = 4; x < W - 4; x++) {
            for (int y = 4; y < H - 4; y++) {
                topology.setRoomPurpose(x, y, RoomPurpose.HANGAR);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology);
        sim.setMissionCompletionEnabled(false);
        AirfieldService field = sim.getAirfieldService();
        AirfieldService.Berth berth = stand(field, 20, 15);

        List<FixtureTask> apron = AirfieldWork.onTheApron(field, grid);
        field.installApronWork(apron);
        if (manned) {
            StructureWatch.man(sim, Faction.DEFENDER,
                    RoomSite.findAll(topology, W, H), apron,
                    AirfieldWork.occupied(field), EnumSet.of(RoomPurpose.HANGAR), 2);
        }

        // Out and home shot up, which is what puts it on the turnaround.
        sim.advance(BattleSimulation.TICK_DT);
        field.launch(berth);
        sim.advance(BattleSimulation.TICK_DT);
        float damaged = ShuttleType.AEROSHUTTLE.maxHp * 0.2f;
        field.recover(berth, damaged);

        for (int tick = 0; tick < 30 * 120; tick++) sim.advance(BattleSimulation.TICK_DT);

        assertTrue(berth.airframeId != 0L, "the aircraft never came back onto its stand");
        return sim.world().hp(berth.airframeId) - damaged;
    }

    private static AirfieldService.Berth stand(AirfieldService field, int x, int y) {
        return field.addBerth(LandingPad.garrison(x, y, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
    }

    /** A field of stands at the given cells, in the order they are named. */
    private static AirfieldService fieldWith(NavigationGrid grid, int... cells) {
        AirfieldService field = new AirfieldService();
        for (int index = 0; index + 1 < cells.length; index += 2) {
            stand(field, cells[index], cells[index + 1]);
        }
        return field;
    }

    private static NavigationGrid openField() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /** Whichever stand this job is physically closest to. */
    private static AirfieldService.Berth nearestStand(AirfieldService field, FixtureTask task) {
        AirfieldService.Berth nearest = null;
        int best = Integer.MAX_VALUE;
        for (AirfieldService.Berth berth : field.berths()) {
            int distance = Math.abs(berth.centerX - task.cellX())
                    + Math.abs(berth.centerY - task.cellY());
            if (distance >= best) continue;
            best = distance;
            nearest = berth;
        }
        return nearest;
    }

    private static int count(List<FixtureTask> work, Affordance affordance) {
        int found = 0;
        for (FixtureTask task : work) if (task.affordance() == affordance) found++;
        return found;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
