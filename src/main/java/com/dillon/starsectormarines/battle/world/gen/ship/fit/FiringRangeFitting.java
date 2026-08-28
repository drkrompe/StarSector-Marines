package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.Hookup;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * A small-arms range as a firing line looking down a bank of lanes, with the
 * ready end behind it.
 *
 * <p>The lane is the room. Everything else — where the firing line stands, which
 * end the butts are at, where a hatch may be cut — follows from the fact that
 * there is a stretch of deck rounds travel down and nobody may be on it. That is
 * also why this is the one compartment whose empty floor needs no excuse: law 10
 * asks every open region to be lane or to carry a stated tactical reason, and a
 * beaten zone is the stated reason.
 *
 * <p>The arrangement is read off the room's own outline rather than authored
 * against a remembered footprint. A range is an <b>L</b> because the ready end
 * is deeper than the lanes are, so where the outline stops being deep is where
 * the firing line goes, and everything beyond it is lane as far as the butts. A
 * recipe reshaped later moves the firing line with it instead of leaving a
 * number here that has quietly stopped matching.
 *
 * <p>Everything past the barrier is <b>shut</b> rather than reserved. A
 * reservation is a rule about furniture: it keeps the fill from standing
 * anything in the lane and leaves everybody else free to stroll down it. So the
 * beaten zone is closed to movement while staying open to sight and to shot —
 * the treatment water gets, and for the same reason, since what blocks the deck
 * here is not a wall.
 *
 * <p>Shooters stand <em>behind</em> the barrier and face the butts, which is why
 * the practice points are placed rather than searched for. A task point found
 * beside its fixture prefers a cell already reserved as circulation, and on a
 * range the cell reserved as circulation is downrange.
 *
 * <p>Not {@linkplain RoomFitting#handed() handed}, unlike a berth, and the
 * difference is the footprint rather than the room. Poses are deduplicated by
 * mask, so a <em>rectangle</em> has only two — which is why a berth needs the
 * flips to be entered from all four sides. An L already has four distinct
 * masks, so the flips add chirality and nothing else, and chirality is
 * expensive here: this is the second-largest room on the deck and it is placed
 * early, so letting it take a mirrored pocket cost one seed in three
 * twenty-one of its programmed rooms — eighteen berths among them, backfilled
 * with parts cages. Author the flips where the arrangement needs them, not
 * wherever a room happens to have a front and a back.
 */
public final class FiringRangeFitting implements RoomFitting {

    /** Cells between one firing point and the next. A shooter needs elbow room. */
    private static final int LANE_PITCH = 2;
    /** Cells of deck at the far end given over to the butts. */
    private static final int BUTTS = 1;
    /** Shortest run of deck worth calling a lane. Below this the room is a store. */
    private static final int MIN_LANE = 4;
    /** Cells between the loose fixtures on the rank inboard of the arms bulkhead. */
    private static final int READY_GAP = 3;

    /** The barrier a shooter fires over, by which way the rounds go. */
    private static final String BARRIER_NORTH = "doodad.sandbag-straight-n";
    private static final String BARRIER_SOUTH = "doodad.sandbag-straight-s";
    private static final String BARRIER_WEST = "doodad.sandbag-straight-w";
    private static final String BARRIER_EAST = "doodad.sandbag-straight-e";

    /** What the rounds go into. Stacked, and replaced often enough to be scruffy. */
    private static final String[] BUTT = {
            "doodad.crate",
            "doodad.industrial-scrap-pile",
            "doodad.box" };

    /**
     * The ready end: arms, ammunition, and the position the range is run from.
     *
     * <p>Scenery, deliberately. Drawing and returning weapons is the armory's
     * job and there is no role aboard yet whose watch is spent on a range — and
     * a fixture published as work that nobody has any business at is how a deck
     * fills up with people solemnly tending things. What this room publishes is
     * practice, at the firing line, which is what a range is for.
     */
    private static final String[] READY = {
            "doodad.shelf-1",
            "doodad.crate",
            "doodad.shelf-2",
            "doodad.box",
            "doodad.shelf-3",
            "doodad.industrial-crate-stack" };

    /** The position the range is run from, at the end of the ready line. */
    private static final String CONTROL = "doodad.industrial-control-console";

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.FIRING_RANGE;
    }

    /**
     * Every way in is behind the firing line.
     *
     * <p>This is the case an authored hookup exists for. A hatch cut wherever
     * the passage search arrived can open halfway down a lane, and a door into
     * a lane is somebody stepping into the beaten zone — not a fill defect that
     * costs a fixture, but a room that is wrong about what it is. The
     * alternatives below differ only in which bulkhead of the ready end is used,
     * because that part is the deck's business and this part is not.
     */
    @Override
    public List<Hookup> hookups(RoomShape canonical) {
        Plan plan = plan(canonical.width(), canonical.height(), canonical::contains);
        if (plan == null) return List.of();
        int ready = Math.max(1, plan.firingLine() - 1);
        return List.of(
                Hookup.of(Hookup.DoorSlot.run(-1, plan.back() - 1, 1, 2)),
                Hookup.of(Hookup.DoorSlot.run(ready / 2, plan.back() + 1, 2, 1)),
                Hookup.of(Hookup.DoorSlot.run(0, -1, Math.min(2, ready), 1)));
    }

    @Override
    public void fit(CompartmentFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        Plan plan = plan(along, across, floor::contains);
        if (plan == null) return;

        int butts = along - BUTTS;
        // The lane run is marked before anything is placed, so nothing the ready
        // end does later can reach into it.
        mark(floor, plan.firingLine(), plan.laneFrom(),
                butts - plan.firingLine(), plan.laneSpan());
        layFiringLine(floor, plan, butts);
        layButts(floor, plan, butts, along);
        // Everything past the barrier is shut, not merely reserved. Reserving it
        // keeps the fill out and lets everybody else stroll down it, which is the
        // one thing a range cannot have: a reservation is a rule about furniture,
        // and a beaten zone is a rule about people.
        closeOff(floor, plan.firingLine() + 1, plan.laneFrom(),
                along - plan.firingLine() - 1, plan.laneSpan());

        // Behind the firing line: the run people come up, and the gear either
        // side of it. Hatches are joined to that run before anything is placed
        // — the ready end works the deepest bulkhead, which is exactly where a
        // hatch is, and an arms rack across the only way in seals the room.
        reserve(floor, 0, plan.laneFrom(), plan.firingLine(), plan.laneSpan());
        clearApproaches(floor, plan, along, across);
        layReadyEnd(floor, plan, across);
    }

    /**
     * The barrier at each lane, and the shooter standing behind it.
     *
     * <p>Bound to a cell rather than to a berth: a lane is somewhere to shoot
     * whether or not anybody is on it, unlike a gantry bay, which is only work
     * while a machine is parked in it.
     */
    private void layFiringLine(CompartmentFloor floor, Plan plan, int butts) {
        int[] downrange = floor.pose().mapDirection(1, 0);
        String barrier = barrierFacing(downrange);
        for (int lane = plan.laneFrom(); lane < plan.laneFrom() + plan.laneSpan();
                lane += LANE_PITCH) {
            place(floor, plan.firingLine(), lane, barrier);
            int[] stand = floor.toLocal(plan.firingLine() - 1, lane);
            int[] target = floor.toLocal(butts, lane);
            floor.fixtureTask(stand[0], stand[1], Affordance.PRACTICE, target[0], target[1]);
        }
    }

    /** The butts, packed across the far end so every lane has something to stop it. */
    private void layButts(CompartmentFloor floor, Plan plan, int butts, int along) {
        int index = 0;
        for (int column = butts; column < along; column++) {
            for (int lane = plan.laneFrom(); lane < plan.laneFrom() + plan.laneSpan(); lane++) {
                place(floor, column, lane, BUTT[index++ % BUTT.length]);
            }
        }
    }

    /**
     * Join every hatch to the run up to the firing line.
     *
     * <p>Reserved before the ready end is worked, not after. The gear goes
     * against the deepest bulkhead, which is where a hatch into this room most
     * naturally is, so furnishing first put an arms rack across the only way in
     * — and a fill that seals its compartment is thrown away entire, which is
     * how a range came out as bare deck on one seed in two and looked like
     * nothing had been attempted.
     */
    private void clearApproaches(CompartmentFloor floor, Plan plan, int along, int across) {
        for (DeckGraph.Compartment.Door door : floor.localDoors()) {
            int[] canonical = floor.toCanonical(door.x(), door.y());
            int column = Math.max(0, Math.min(along - 1, canonical[0]));
            int row = Math.max(0, Math.min(across - 1, canonical[1]));
            int from = Math.min(row, plan.laneFrom());
            int to = Math.max(row, plan.laneFrom() + plan.laneSpan() - 1);
            reserve(floor, column, from, 1, to - from + 1);
        }
    }

    /**
     * The ready end: arms and ammunition packed along the deepest bulkhead, a
     * looser rank inboard of them, the range control beside the firing line, and
     * a clear run between the two for a detail to come up to the line.
     *
     * <p>Two ranks rather than one because a single row against the bulkhead
     * left most of the ready end as unargued deck. The run itself is argued —
     * it is how a detail reaches the line — and it is reserved rather than
     * merely left over, so it stays that way.
     */
    private void layReadyEnd(CompartmentFloor floor, Plan plan, int across) {
        int index = 0;
        for (int row = plan.laneFrom() + plan.laneSpan(); row < across; row++) {
            boolean inboard = floor.contains(0, row + 1) && !floor.contains(0, row + 2);
            for (int column = 0; column < plan.firingLine(); column++) {
                if (!floor.contains(column, row)) continue;
                boolean outermost = !floor.contains(column, row + 1);
                if (!outermost && !(inboard && column % READY_GAP == 0)) {
                    reserve(floor, column, row, 1, 1);
                    continue;
                }
                place(floor, column, row,
                        outermost && column == plan.firingLine() - 1
                                ? CONTROL : READY[index++ % READY.length]);
            }
        }
    }

    /** The barrier whose front faces the way the rounds go. */
    private static String barrierFacing(int[] downrange) {
        if (downrange[0] < 0) return BARRIER_WEST;
        if (downrange[0] > 0) return BARRIER_EAST;
        if (downrange[1] < 0) return BARRIER_NORTH;
        return BARRIER_SOUTH;
    }

    /**
     * Where this outline puts the firing line, the lanes, and the deep end.
     *
     * @param firingLine the first column at which the room stops being deep
     * @param laneFrom the first row of the lane band
     * @param laneSpan how many rows of lane there are
     * @param back the deepest row the ready end reaches
     */
    private record Plan(int firingLine, int laneFrom, int laneSpan, int back) {}

    /** Read a cell of a footprint, in the fitting's canonical frame. */
    @FunctionalInterface
    private interface Footprint {
        boolean has(int along, int across);
    }

    /**
     * Work the arrangement out from the outline, or null where this footprint
     * cannot hold a range at all.
     *
     * <p>A plain rectangle has no narrowing to read, so the ready end is taken
     * as the first third. That is a worse range and it is still a range; the
     * alternative is a compartment that declares itself a firing range and comes
     * out as bare deck.
     */
    private static Plan plan(int along, int across, Footprint footprint) {
        int[] depth = new int[along];
        int deepest = 0;
        for (int column = 0; column < along; column++) {
            for (int row = 0; row < across; row++) {
                if (footprint.has(column, row)) depth[column]++;
            }
            deepest = Math.max(deepest, depth[column]);
        }
        int firingLine = 0;
        while (firingLine < along && depth[firingLine] >= deepest) firingLine++;
        if (firingLine >= along) firingLine = along / 3;
        if (firingLine < 1 || along - firingLine < MIN_LANE + BUTTS) return null;

        List<Integer> lanes = new ArrayList<>();
        for (int row = 0; row < across; row++) {
            if (footprint.has(firingLine, row)) lanes.add(row);
        }
        if (lanes.isEmpty()) return null;
        int laneFrom = lanes.get(0);
        int laneSpan = lanes.get(lanes.size() - 1) - laneFrom + 1;

        int back = laneFrom + laneSpan - 1;
        while (back + 1 < across && footprint.has(0, back + 1)) back++;
        return new Plan(firingLine, laneFrom, laneSpan, back);
    }

    private void place(CompartmentFloor floor, int along, int across, String id) {
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1]);
    }

    private void reserve(CompartmentFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }

    private void closeOff(CompartmentFloor floor,
                          int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.closeOff(rect[0], rect[1], rect[2], rect[3]);
    }

    private void mark(CompartmentFloor floor,
                      int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.markGround(rect[0], rect[1], rect[2], rect[3], GroundKind.STRIPED);
    }
}
