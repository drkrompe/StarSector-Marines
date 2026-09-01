package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * A boat bay as four worked bulkhead runs around a clear deck: cargo down one
 * side, fuelling and plant down the other, the deck office at one end and the
 * ready stores at the other.
 *
 * <p>The clear middle is not the room. It is a <em>constraint</em> on the room —
 * a boat has to be moved through it, and troops form up on it to embark — and
 * for a long time the bay was fitted as though the constraint were the whole
 * design: gear ranked round the walls at a pitch, publishing stowage and nothing
 * else. That is a hold with an empty middle. What actually happens on a boat
 * deck is cargo going up and down, boats being fuelled and turned round between
 * lifts, a tally kept of both, and a snag list that never quite empties; the
 * clear deck is what all of that is arranged around rather than what it amounts
 * to.
 *
 * <p><b>And the boats stand in it.</b> A rank of them along the fuelling side,
 * which is why the fuelling run and the bowser are on that side at all, with the
 * rest of the clear deck left open in front of them — the lane a boat is moved
 * out through and the deck a landing party forms up on. Each boat publishes
 * {@link Affordance#SERVICE}, so the trade that turns an aircraft round on a
 * garrison apron is the trade that turns a boat round here; it is the same work
 * on the same kind of machine, and modelling it twice would give a ship's boats
 * a second, quietly different opinion about what servicing costs.
 *
 * <p>How many and how big is the bay's own answer, at a pitch, the way its
 * perimeter work already is. A gig bay on a frigate holds one small boat and a
 * cruiser's boat deck holds a rank of launches, and neither number is written
 * down here.
 *
 * <p>Their side is the fuelled side and their front is the deck. A boat nosed
 * into the fuelling run would have to be backed out past the bowser every lift,
 * and one ranked against the cargo side would put the tankage across the bay
 * from the things it fuels.
 *
 * <p>So the deck stays clear and the perimeter is worked hard. Each of the four
 * bulkheads carries a run {@link #WORKING_BAND} cells deep, and the first cell
 * of clear deck inboard of it is where somebody stands — which makes the
 * standing cells one continuous ring of lane rather than pockets between
 * stacks, and is why this room can be worked heavily without ever risking its
 * own circulation.
 *
 * <p>Jobs go at {@link #WORK_PITCH}, so the bay's capacity is its perimeter
 * rather than a number written here. A gig bay on a frigate and a boat deck on a
 * cruiser are the same arrangement holding different amounts of work, which is
 * the same rule the gantry bays follow.
 *
 * <p>Not {@linkplain RoomFitting#handed() handed} and publishing no
 * {@linkplain RoomFitting#hookups hookups}. Every side of this room is worked
 * the same way and every side can take a hatch, so the placer is left free to
 * put the bay against whichever run of hull it can reach — which matters more
 * here than in any other compartment, since a boat bay buried amidships opens
 * onto nothing.
 */
public final class BoatBayFitting implements RoomFitting {

    /**
     * Cells of gear ranked inboard from each bulkhead.
     *
     * <p>Two, because one is a wall with things against it and three starts
     * eating the deck the boat needs. The outer rank is stock and the inner rank
     * is what a job is published against, so the depth is also what decides
     * whether the bay has any reach at all.
     */
    public static final int WORKING_BAND = 2;

    /**
     * Cells between one job and the next along a run.
     *
     * <p>Shoulder to shoulder would double the count and halve the room: the
     * standing cells are the ring of deck everybody also walks round, and a job
     * in every cell of it is a bay whose perimeter is permanently blocked by the
     * people working it.
     */
    private static final int WORK_PITCH = 2;

    /** Shallowest room that can carry two working bands and a boat between them. */
    private static final int MIN_CLEAR = 3;

    /**
     * How much deck one boat stands on, along the bay and across it.
     *
     * <p>A boat, not an aircraft's drawn hull: what this reserves is the spot on
     * the deck it is kept in, which is a decision about how many a bay holds
     * rather than a measurement of any sprite. The same separation the aprons
     * keep, and for the same reason — a re-cut hull would otherwise re-berth
     * every ship in the fleet.
     */
    private static final int BOAT_SPAN = 7;
    private static final int BOAT_DEPTH = 5;

    /** The smallest boat there is: a gig, for a hull with a gig bay. */
    private static final int GIG_SPAN = 5;

    /** Cells of open deck between one boat and the next, to get round them by. */
    private static final int BOAT_GAP = 1;

    /**
     * Clear deck that must remain in front of the rank.
     *
     * <p>The lane is the point of the room, so it is subtracted before the boats
     * are sized rather than after: a bay too shallow to hold both gives the deck
     * to the lane and keeps fewer, shallower boats, and one too shallow for even
     * that keeps none. A gig bay on a frigate is the case that matters — it is
     * the same arrangement holding one boat, and the failure to guard against is
     * a frigate whose only way off the ship is packed solid.
     *
     * <p>One cell, because this is deck <em>inside</em> the working ring and the
     * ring is walkable too: the clear run in front of a boat is this plus the
     * cargo run's own standing row. Two would cost the smallest bays their boat
     * to buy a lane that is already there.
     */
    private static final int MIN_LANE = 1;

    /** Shallowest spot still worth calling a boat's. */
    private static final int MIN_BOAT_DEPTH = 3;

    /**
     * Which jobs along a run are a tally rather than the run's own trade, and
     * which are defects, as strides over the order the work is laid in.
     *
     * <p>Strides rather than draws, for the reason a defect list exists at all:
     * a snag has to still be there when somebody walks back to it, and a fitting
     * holds no seeded random source, so an unseeded roll would give the same bay
     * a different list on every build. Laying order is stable geometry.
     *
     * <p>The defect is checked first and replaces whatever the cell would
     * otherwise have offered. Unlike the mech bay's list, which falls on gear
     * that affords nothing else, a boat bay has no idle equipment to hang it on
     * — every fixture on these runs is already somebody's — so here a defect is
     * a job that has gone wrong rather than a job nobody had.
     */
    private static final int DEFECT_STRIDE = 5;
    private static final int DEFECT_PHASE = 3;
    private static final int TALLY_STRIDE = 4;
    private static final int TALLY_PHASE = 1;

    /**
     * Cargo down one long side: what comes up out of a boat and what goes back
     * down in it.
     */
    private static final String[] CARGO = {
            "doodad.industrial-crate-stack",
            "doodad.industrial-pallet-stack",
            "doodad.crate",
            "doodad.industrial-drum-cluster",
            "doodad.box",
            "doodad.industrial-dumpster" };

    /**
     * Fuelling and plant down the other: tankage, the lines that run off it, and
     * the set that drives them.
     */
    private static final String[] FUELLING = {
            "doodad.industrial-fluid-tank",
            "doodad.industrial-pipe-bundle",
            "doodad.industrial-cable-reel",
            "doodad.industrial-generator",
            "doodad.industrial-fluid-tank",
            "doodad.industrial-machine-tool" };

    /** The deck office at one end: where the bay is run from and written up. */
    private static final String[] OFFICE = {
            "doodad.office-workstation-bank",
            "doodad.military-command-console",
            "doodad.office-server-rack",
            "doodad.desk-1",
            "doodad.shelf-1",
            "doodad.desk-2" };

    /** The ready stores at the other: what a landing party draws on its way out. */
    private static final String[] READY = {
            "doodad.shelf-1",
            "doodad.crate",
            "doodad.shelf-2",
            "doodad.industrial-crate-stack",
            "doodad.shelf-3",
            "doodad.box" };

    /** The terminal a tally is entered at, wherever one falls along a run. */
    private static final String TALLY = "doodad.industrial-control-console";

    /**
     * The bowser, parked against the fuelling run.
     *
     * <p>Three cells by two, and the only fixture here large enough to read as a
     * vehicle rather than as stock — which is the point of it. A boat deck with
     * nothing on it bigger than a drum reads as a corridor that got wide.
     * Attempted rather than guaranteed: a footprint is laid in deck coordinates
     * and this room may have been turned, so on half the poses it does not fit
     * the band and the run simply stacks tankage there instead.
     */
    private static final String BOWSER = "doodad.parked-tanker-truck";

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.HANGAR;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        int band = Math.min(WORKING_BAND,
                Math.min((along - MIN_CLEAR) / 2, (across - MIN_CLEAR) / 2));
        if (band < 1) {
            // No room for a working band and a boat both. The deck the boat
            // needs wins, because it is the one thing this compartment is for.
            floor.reserveLane(0, 0, floor.width(), floor.height());
            return;
        }

        // The clear deck first, and everything else around it. It is reserved
        // rather than shut: a boat is moved through here and a landing party
        // forms up on it, so it is deck people are on and not deck they are kept
        // off - the difference between this and a firing range's beaten zone.
        reserve(floor, band, band, along - 2 * band, across - 2 * band);
        mark(floor, band, band, along - 2 * band, across - 2 * band);
        clearApproaches(floor);
        layCorners(floor, along, across, band);
        layBowser(floor, along, across, band);
        layBoats(floor, along, across, band);

        // One ordinal across the whole bay rather than one per run, so the
        // tallies and the defects are spread round the deck instead of landing
        // at the same offset on all four bulkheads.
        int ordinal = 0;
        ordinal = layRun(floor, run(band, 0, 1, 0, 0, 1, along - 2 * band),
                band, CARGO, Affordance.STOW, ordinal);
        ordinal = layRun(floor, run(band, across - 1, 1, 0, 0, -1, along - 2 * band),
                band, FUELLING, Affordance.TEND, ordinal);
        ordinal = layRun(floor, run(0, band, 0, 1, 1, 0, across - 2 * band),
                band, OFFICE, Affordance.READOUT, ordinal);
        layRun(floor, run(along - 1, band, 0, 1, -1, 0, across - 2 * band),
                band, READY, Affordance.STOW, ordinal);
    }

    /**
     * One bulkhead run: where it starts, which way it runs along the bulkhead,
     * and which way is inboard from it.
     *
     * <p>A run rather than four near-identical loops. Every side of this bay is
     * the same arrangement pointed a different way, and writing that out four
     * times is how the fore end quietly stops matching the aft one.
     */
    private record Run(int startAlong, int startAcross, int stepAlong, int stepAcross,
                       int inAlong, int inAcross, int length) {

        /** The cell {@code index} along the run and {@code depth} inboard of it. */
        int[] cell(int index, int depth) {
            return new int[]{
                    startAlong + index * stepAlong + depth * inAlong,
                    startAcross + index * stepAcross + depth * inAcross };
        }
    }

    private static Run run(int startAlong, int startAcross,
                           int stepAlong, int stepAcross,
                           int inAlong, int inAcross, int length) {
        return new Run(startAlong, startAcross, stepAlong, stepAcross,
                inAlong, inAcross, Math.max(0, length));
    }

    /**
     * Stack one bulkhead run and publish the work along it.
     *
     * <p>Gear goes down first and the work is published against what actually
     * landed. A placement is refused wherever a door approach was reserved or
     * the bowser already stands, and publishing as the stacking went would leave
     * a point aimed at bare deck — a job at nothing, which is worse than no job
     * because it still counts and somebody still walks to it.
     *
     * @return the ordinal the next run continues from
     */
    private int layRun(RoomFloor floor, Run line, int band,
                       String[] gear, Affordance trade, int ordinal) {
        int index = line.startAlong() + line.startAcross();
        boolean[] backed = new boolean[line.length()];
        for (int step = 0; step < line.length(); step++) {
            for (int depth = 0; depth < band; depth++) {
                int[] cell = line.cell(step, depth);
                boolean tally = depth == band - 1
                        && step % WORK_PITCH == 0
                        && isTally(ordinal + step / WORK_PITCH);
                boolean landed = place(floor, cell[0], cell[1],
                        tally ? TALLY : gear[index++ % gear.length]);
                if (depth == band - 1) backed[step] = landed;
            }
        }
        for (int step = 0; step < line.length(); step += WORK_PITCH) {
            if (backed[step]) {
                int[] stand = line.cell(step, band);
                int[] fixture = line.cell(step, band - 1);
                task(floor, stand[0], stand[1], job(ordinal, trade), fixture[0], fixture[1]);
            }
            ordinal++;
        }
        return ordinal;
    }

    /** What the job at this point along a run turns out to be. */
    private static Affordance job(int ordinal, Affordance trade) {
        if (ordinal % DEFECT_STRIDE == DEFECT_PHASE) return Affordance.REPAIR;
        if (isTally(ordinal)) return Affordance.READOUT;
        return trade;
    }

    /** Whether this point along a run is where a tally is taken rather than worked. */
    private static boolean isTally(int ordinal) {
        return ordinal % TALLY_STRIDE == TALLY_PHASE;
    }

    /**
     * The rank of boats, along the fuelling side of the clear deck.
     *
     * <p>Laid after the bowser and before the bulkhead runs. After, because the
     * bowser is a fixture on the band and this reserves deck inboard of it, so
     * whichever gets the contested cell first should be the one that cannot move
     * — and before the runs, because a run publishes its work against the first
     * clear cell inboard, which is deck a boat may now be standing on.
     *
     * <p>As many as fit at a pitch, which is the rule the rest of this room
     * already follows: a gig bay on a frigate and a boat deck on a cruiser are
     * the same arrangement holding different numbers of boats. A bay with no
     * depth for a boat and a lane both keeps none, and is a passage with stores
     * down it — which is what it was before boats existed and is a worse bay
     * rather than a broken one.
     */
    private void layBoats(RoomFloor floor, int along, int across, int band) {
        // Inside the ring, not on it. The first clear cell inboard of every
        // bulkhead is where that run's work is done from, so a boat laid on the
        // ring stands on the fuelling party — and because a berth reserves deck
        // rather than claiming it, nothing downstream would refuse the point.
        // It would simply be published inside a boat.
        int rim = band + 1;
        int deckAlong = along - 2 * rim;
        int deckAcross = across - 2 * rim;
        int depth = depth(deckAcross);
        if (depth < MIN_BOAT_DEPTH) return;
        int span = span(depth);

        int pitch = span + BOAT_GAP;
        int boats = (deckAlong + BOAT_GAP) / pitch;
        if (boats < 1) return;

        // Centred on the deck's long axis, so a bay that holds two boats in a
        // space that would nearly take three does not push them both to one end
        // and leave a corner of unexplained deck at the other.
        int spread = boats * pitch - BOAT_GAP;
        int first = rim + (deckAlong - spread) / 2;
        // Backed onto the fuelling side, nose to the deck.
        int rankAcross = across - rim - depth;
        int[] out = floor.pose().mapDirection(0, -1);

        for (int boat = 0; boat < boats; boat++) {
            int start = first + boat * pitch;
            int[] rect = floor.toLocalRect(start, rankAcross, span, depth);
            int berth = floor.berth(rect[0], rect[1], rect[2], rect[3],
                    Gantry.Facing.of(out[0], out[1]), Gantry.Holds.BOAT);
            layBoatWork(floor, start, rankAcross, span, berth);
        }
    }

    /**
     * How deep a boat is, given how much deck the bay has inside its ring.
     *
     * <p>Odd, always. A berth records half-extents about a centre cell, so an
     * even span rounds down and the berth comes out describing one cell less
     * deck than it reserved — a boat that is two cells of deck and one cell of
     * berth. Nothing refuses that and nothing looks wrong until something reads
     * the berth to decide how much of it is boat.
     */
    private static int depth(int deckAcross) {
        int most = Math.min(BOAT_DEPTH, deckAcross - MIN_LANE);
        return most % 2 == 0 ? most - 1 : most;
    }

    /**
     * How long a boat is, given how deep the bay could spare to make it.
     *
     * <p><b>A small bay holds a small boat, not no boat.</b> A frigate's gig bay
     * is four cells of deck once the working ring is off it, and a launch needs
     * five — so a fixed size gave the one hull whose only way off the ship is
     * its boat exactly nothing in the bay, which is worse than the empty deck it
     * replaced. It is the rule the rest of this room already follows: a gig bay
     * on a frigate and a boat deck on a cruiser are the same arrangement at
     * different sizes.
     *
     * <p>Tied to the depth so a shallow boat does not come out a plank, and
     * capped, because what this must never do is let a bay hold a boat longer
     * than a boat.
     */
    private static int span(int depth) {
        return Math.min(BOAT_SPAN, Math.max(GIG_SPAN, 2 * depth - 1));
    }

    /**
     * Where a boat is worked from: the deck in front of it, twice.
     *
     * <p>Both points on the same side, which is the one a boat has. It is backed
     * onto the fuelling run and its ends are its neighbours or the gap somebody
     * walks through, so the open side is the deck — and the deck is where the
     * work would be done anyway. The count matches an apron stand's two flanks;
     * only the geometry that produces it differs.
     */
    private void layBoatWork(RoomFloor floor, int start, int rankAcross, int span,
                             int berth) {
        int deck = rankAcross - 1;
        int bow = start + 1;
        int stern = start + span - 2;
        berthTask(floor, bow, deck, berth, bow, rankAcross);
        berthTask(floor, stern, deck, berth, stern, rankAcross);
    }

    /** Work done on whatever is berthed in {@code berth}, authored canonically. */
    private void berthTask(RoomFloor floor, int cellAlong, int cellAcross, int berth,
                           int fixtureAlong, int fixtureAcross) {
        int[] stand = floor.toLocal(cellAlong, cellAcross);
        int[] fixture = floor.toLocal(fixtureAlong, fixtureAcross);
        floor.berthFixtureTask(stand[0], stand[1], berth, fixture[0], fixture[1]);
    }

    /**
     * The bowser, on the fuelling side, amidships.
     *
     * <p>Placed before the runs are stacked so it has an empty band to land in,
     * and left to fail: {@link RoomFloor#place} refuses a footprint that does
     * not fit, and the fuelling run then stacks tankage over the cells it would
     * have taken. Nothing downstream can tell the difference except the picture.
     */
    private void layBowser(RoomFloor floor, int along, int across, int band) {
        int[] cell = floor.toLocal(along / 2 - 1, across - band);
        floor.place(BOWSER, cell[0], cell[1]);
    }

    /**
     * Join every hatch to the clear deck.
     *
     * <p>Reserved before anything is stacked. The runs go against all four
     * bulkheads, which is exactly where a hatch is, so stacking first walls the
     * door in — and a fill that seals its compartment is discarded entire, which
     * would put this room back to the bare deck it started as.
     *
     * <p>Done in the room's own frame rather than canonically, because a square
     * around a door is the same square whichever way the room was turned.
     */
    private void clearApproaches(RoomFloor floor) {
        for (Doorway door : floor.localDoors()) {
            floor.reserveLane(door.x() - WORKING_BAND, door.y() - WORKING_BAND,
                    2 * WORKING_BAND + 1, 2 * WORKING_BAND + 1);
        }
    }

    /**
     * The four corner pockets, where one run's band meets another's.
     *
     * <p>Stacked rather than reserved, and the distinction is not cosmetic. A
     * corner is enclosed by both runs' gear, so lane laid there is circulation
     * with no way into it — and a room whose own fill strands a reserved cell
     * fails its connectivity check and is discarded entire, which is how a fully
     * worked boat bay came out as bare deck. Filled instead, the pocket is what
     * it looks like: the corner everything nobody has dealt with gets pushed
     * into.
     */
    private void layCorners(RoomFloor floor, int along, int across, int band) {
        int index = 0;
        for (int step = 0; step < band; step++) {
            for (int depth = 0; depth < band; depth++) {
                place(floor, step, depth, CARGO[index++ % CARGO.length]);
                place(floor, along - 1 - step, depth, CARGO[index++ % CARGO.length]);
                place(floor, step, across - 1 - depth, CARGO[index++ % CARGO.length]);
                place(floor, along - 1 - step, across - 1 - depth,
                        CARGO[index++ % CARGO.length]);
            }
        }
    }

    /** @return whether it went down; a refusal is deck already spoken for */
    private boolean place(RoomFloor floor, int along, int across, String id) {
        int[] cell = floor.toLocal(along, across);
        return floor.place(id, cell[0], cell[1]);
    }

    /** Work at a cell this arrangement chose itself, authored canonically. */
    private void task(RoomFloor floor, int cellAlong, int cellAcross,
                      Affordance affordance, int fixtureAlong, int fixtureAcross) {
        int[] stand = floor.toLocal(cellAlong, cellAcross);
        int[] fixture = floor.toLocal(fixtureAlong, fixtureAcross);
        floor.fixtureTask(stand[0], stand[1], affordance, fixture[0], fixture[1]);
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        if (alongSpan <= 0 || acrossSpan <= 0) return;
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }

    private void mark(RoomFloor floor,
                      int along, int across, int alongSpan, int acrossSpan) {
        if (alongSpan <= 0 || acrossSpan <= 0) return;
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.markGround(rect[0], rect[1], rect[2], rect[3], GroundKind.STRIPED);
    }
}
