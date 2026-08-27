package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.Hookup;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * A berthing compartment as two ranks of racks facing each other across a
 * fore-and-aft passage, entered from one side.
 *
 * <p>Racks run <b>athwart</b> the passage rather than along the bulkhead, head
 * to the hull and feet to the aisle, which is what makes this berthing rather
 * than a bedroom. Laid the other way a bunk takes two cells of bulkhead to berth
 * one hand; laid this way it takes one, and the compartment holds twice as many
 * people in the same floor. That is not a packing trick — it is why a ship's
 * berthing looks the way it does, and the recipe's four square metres a hand is
 * only reachable at this density.
 *
 * <p>Both doors are on the same side. A compartment entered from opposite sides
 * is a passage with bunks in it: the through route runs between the ranks and
 * every hand sleeping there is walked past all watch. Keeping the doors together
 * leaves the far rank against unbroken hull and costs the near rank one slot per
 * hatch, which is the whole price of the arrangement.
 *
 * <p>What breaks the two runs up is structure rather than spacing. The near rank
 * is interrupted by its own hatches and stows kit at the ends where somebody
 * coming off watch would drop it; the far rank is unbroken, so a stanchion pair
 * stands amidships and divides it. A rack run subdivided by a frame reads as a
 * compartment; the same run at even spacing reads as a diagram.
 */
public final class BerthingFitting implements RoomFitting {

    /** Cells one rack takes, head to foot. A bunk is two metres of somebody. */
    private static final int RACK_DEPTH = 2;
    /** Clear deck between the ranks. Two abreast, as everywhere else. */
    private static final int AISLE = 2;

    /**
     * The frame that divides the unbroken rank. A pipe run is the closest thing
     * in the registry to a stanchion seen from above, and on a ship it is also
     * the honest one: what actually stands floor to deckhead in a berthing space
     * is trunking.
     */
    private static final String STANCHION = "doodad.industrial-pipe-bundle";

    /** Kit that has not been stowed yet, behind the locker it belongs in. */
    private static final String KIT = "doodad.box";

    /** Racks by which way the head points, on the deck rather than in the frame. */
    private static final String RACK_HEAD_NORTH = "doodad.residential-bed-head-n";
    private static final String RACK_HEAD_SOUTH = "doodad.residential-bed-v";
    private static final String RACK_HEAD_WEST = "doodad.residential-bed-h";
    private static final String RACK_HEAD_EAST = "doodad.residential-bed-head-e";

    private final RoomPurpose purpose;
    private final String locker;

    /**
     * @param locker the stowage this compartment's occupants keep their kit in,
     *     which is the one thing that differs between the ship's berthing and
     *     the company's
     */
    public BerthingFitting(RoomPurpose purpose, String locker) {
        this.purpose = purpose;
        this.locker = locker;
    }

    @Override
    public RoomPurpose purpose() {
        return purpose;
    }

    /**
     * A berth is handed: its hatches are on one side and unbroken hull is on the
     * other, so the mirror image is a different room rather than the same one
     * seen from behind.
     *
     * <p>This is the expensive answer for the most numerous compartment on the
     * deck, and it is the correct one only because the doors are authored. Turns
     * alone are deduplicated by mask, and a rectangle has two distinct masks —
     * so without the flips a berth could only ever be entered from two of its
     * four sides, and the rest would fall back to a hatch cut wherever the
     * passage search happened to arrive.
     */
    @Override
    public boolean handed() {
        return true;
    }

    /**
     * Two hatches on the same side, or one where the deck can only reach the
     * compartment once.
     *
     * <p>Spaced a quarter in from each end rather than at the ends themselves: a
     * hatch in the corner opens onto the end bulkhead, and the two cells offered
     * per doorway are alternatives, so a single square of awkward hull plating
     * does not lose the compartment its arrangement.
     */
    @Override
    public List<Hookup> hookups(RoomShape canonical) {
        int along = canonical.width();
        int forward = Math.max(0, along / 4);
        int aft = Math.min(along - 2, along - 1 - along / 4);
        if (aft - forward < 2) {
            return List.of(Hookup.of(doorway(along / 2)));
        }
        return List.of(
                Hookup.of(doorway(forward), doorway(aft)),
                Hookup.of(doorway(along / 2)));
    }

    /** One doorway on the near bulkhead, as a pair of cells either of which serves. */
    private static Hookup.DoorSlot doorway(int along) {
        return Hookup.DoorSlot.run(along, -1, 2, 1);
    }

    @Override
    public void fit(CompartmentFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        if (along < 1 || across < RACK_DEPTH + AISLE) return;

        boolean facingRanks = across >= 2 * RACK_DEPTH + AISLE;
        int aisleFrom = RACK_DEPTH;
        int aisleSpan = across - (facingRanks ? 2 * RACK_DEPTH : RACK_DEPTH);

        // The passage is reserved before a single rack goes down, so what the
        // ranks get is what is left over rather than the other way round.
        reserve(floor, 0, aisleFrom, along, aisleSpan);

        boolean[] nearTaken = new boolean[along];
        boolean[] farTaken = new boolean[along];
        clearApproaches(floor, along, across, aisleFrom, aisleSpan, nearTaken, farTaken);

        layRank(floor, along, 0, true, nearTaken, true);
        if (facingRanks) {
            layRank(floor, along, across - RACK_DEPTH, false, farTaken, false);
        }
    }

    /**
     * Join every hatch to the passage, and record which rack slot it cost.
     *
     * <p>A hatch always takes a slot out of the rank it opens through, because
     * there is no arrangement in which somebody walks through a bunk. What the
     * authored hookup buys is that it takes a slot the room chose — one per
     * hatch, at a known place — instead of whichever one the passage search
     * arrived at.
     */
    private void clearApproaches(CompartmentFloor floor, int along, int across,
                                 int aisleFrom, int aisleSpan,
                                 boolean[] nearTaken, boolean[] farTaken) {
        for (DeckGraph.Compartment.Door door : floor.localDoors()) {
            int[] canonical = floor.toCanonical(door.x(), door.y());
            int column = Math.max(0, Math.min(along - 1, canonical[0]));
            int depth = canonical[1];
            if (depth < aisleFrom) {
                reserve(floor, column, 0, 1, aisleFrom);
                nearTaken[column] = true;
            } else if (depth >= aisleFrom + aisleSpan) {
                int from = aisleFrom + aisleSpan;
                reserve(floor, column, from, 1, across - from);
                farTaken[column] = true;
            }
        }
    }

    /**
     * One rank: racks athwart the passage, with the slots a hatch took left as
     * deck and one slot given over to whatever divides the run.
     *
     * @param headOutboard whether these racks put their heads against the low
     *     bulkhead, which decides both the sprite and which cell of a slot is
     *     the one reachable from the passage
     * @param doorSide whether this is the rank the compartment is entered
     *     through, which is where kit gets stowed
     */
    private void layRank(CompartmentFloor floor, int along, int band,
                         boolean headOutboard, boolean[] taken, boolean doorSide) {
        int outboard = headOutboard ? band : band + RACK_DEPTH - 1;
        int inboard = headOutboard ? band + RACK_DEPTH - 1 : band;
        // The head points at the bulkhead the rank backs onto. Chosen on the
        // deck, not in the frame: a compass-named sprite cannot be turned with
        // the room, so a rank that is athwartships once the berth is quarter
        // turned needs the sprite that is athwartships too.
        int[] head = floor.pose().mapDirection(0, headOutboard ? -1 : 1);
        String rack = rackFacing(head);

        int divider = along / 2;
        for (int slot = 0; slot < along; slot++) {
            if (taken[slot]) continue;
            if (doorSide && (slot == 0 || slot == along - 1)) {
                // Stowage at the ends of the rank the hatches are in, which is
                // where somebody coming off watch actually drops their kit. The
                // locker is the cell against the passage so it can be opened
                // from it; the bag behind it is scenery.
                place(floor, slot, outboard, KIT);
                place(floor, slot, inboard, locker, Affordance.STOW);
            } else if (!doorSide && slot == divider) {
                place(floor, slot, outboard, STANCHION);
                place(floor, slot, inboard, STANCHION);
            } else {
                layRack(floor, slot, band, rack);
            }
        }
    }

    /**
     * One rack, and the fact that somebody sleeps in it.
     *
     * <p>The standing cell falls in the passage of its own accord, because that
     * is the only side of a rack that is not another rack or the hull.
     */
    private void layRack(CompartmentFloor floor, int slot, int band, String rack) {
        int[] rect = floor.toLocalRect(slot, band, 1, RACK_DEPTH);
        floor.place(rack, rect[0], rect[1], Affordance.REST);
    }

    /** The rack sprite whose head lies the way this rank's heads point. */
    private static String rackFacing(int[] head) {
        if (head[0] < 0) return RACK_HEAD_WEST;
        if (head[0] > 0) return RACK_HEAD_EAST;
        if (head[1] < 0) return RACK_HEAD_NORTH;
        return RACK_HEAD_SOUTH;
    }

    private void place(CompartmentFloor floor, int along, int across, String id) {
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1]);
    }

    private void place(CompartmentFloor floor, int along, int across,
                       String id, Affordance affordance) {
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1], affordance);
    }

    private void reserve(CompartmentFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
