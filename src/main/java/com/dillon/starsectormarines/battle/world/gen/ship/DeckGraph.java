package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.ambient.JobSite;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * The ship deck's published structure: the rooms that were placed, and the ones
 * the hull could not hold.
 *
 * <p>This is deliberately <em>not</em> the station's room/corridor graph. There,
 * corridors are edges between room vertices, which suits a layout organized
 * around a core. On a ship the spine and the passages branching off it are
 * places in their own right — the main line of advance and the local access to
 * each block of compartments — so they are cut into the deck rather than
 * reduced to adjacency.
 *
 * <p>{@link #unplaced()} is deliberately visible rather than swallowed. A deck
 * that could not fit part of its program is a sizing defect, and hiding it would
 * turn a measurable shortfall into a deck that merely looks a little empty.
 *
 * <p>Topological roles such as depth-from-entry and must-pass chokepoints are
 * not here yet; they arrive with the bulkhead stage that gives the deck its
 * ordered chokepoint sequence. See {@code ship-interiors-nouns.md}.
 */
public final class DeckGraph {

    /**
     * One placed room: the shape actually laid down, where it was laid, and the
     * doors cut into it.
     *
     * <p>The oriented {@link RoomShape} is carried rather than a bounding box
     * because a fill needs the floor the room really owns — a bounding box
     * includes the corners a diamond or an L does not have, and furnishing those
     * would put bunks inside the bulkhead. The doors matter for the same reason:
     * circulation inside a room is authored from its entries outward, so a fill
     * that does not know where people come in cannot leave them a way through.
     *
     * <p>The {@link RoomPose} is carried for the half of the story the mask
     * cannot tell. A flipped rectangle has the same mask as an unflipped one, so
     * a fitting that recovered its bearings from the footprint would lay its
     * shop and its doors at the wrong ends of half the rooms it was given. A
     * fitting authors facing one way and reads the pose to find out where that
     * ended up.
     *
     * <p>A compartment is the ship family's {@link FurnishableRoom}. The deck
     * decides which rooms exist, where they sit, and which side and zone they
     * belong to; none of that reaches the fill, which is handed the same floor,
     * pose, doors and purpose a compound sub-building presents.
     *
     * @param originX cell the shape's local origin sits on
     * @param originY cell the shape's local origin sits on
     * @param pose how the shape was turned and flipped to get here
     * @param doors cells cut through this room's bulkhead
     */
    public record Compartment(int id, RoomShape shape, int originX, int originY,
                              RoomPose pose, DeckSide side, DeckZone zone,
                              RoomPurpose purpose, List<Doorway> doors)
            implements JobSite, FurnishableRoom {

        public Compartment {
            doors = List.copyOf(doors);
        }

        /** Whether this compartment's floor covers the given deck cell. */
        public boolean contains(int x, int y) {
            return shape.contains(x - originX, y - originY);
        }

        @Override
        public int centreX() {
            return originX + shape.width() / 2;
        }

        @Override
        public int centreY() {
            return originY + shape.height() / 2;
        }

        public int left() {
            return originX;
        }

        public int top() {
            return originY;
        }

        public int right() {
            return originX + shape.width() - 1;
        }

        public int bottom() {
            return originY + shape.height() - 1;
        }

        /** First frame this compartment spans. */
        public int foreFrame() {
            return left();
        }

        /** Last frame this compartment spans. */
        public int aftFrame() {
            return right();
        }

        public int width() {
            return shape.width();
        }

        /** Extent across the beam, in cells. */
        public int depth() {
            return shape.height();
        }

        /** Cells of floor this compartment owns, which is not its bounding box. */
        public int area() {
            return shape.area();
        }
    }

    private final List<Compartment> compartments;
    private final List<RoomRecipe> unplaced;
    private final List<BayAperture> apertures;

    /**
     * @param compartments placed rooms, in deterministic placement order
     * @param unplaced rooms from the program the deck had no space for
     */
    public DeckGraph(List<Compartment> compartments, List<RoomRecipe> unplaced) {
        this(compartments, unplaced, List.of());
    }

    /**
     * @param compartments placed rooms, in deterministic placement order
     * @param unplaced rooms from the program the deck had no space for
     * @param apertures the deck's doors onto the outside, one per bay that has
     *     one; carried here rather than on the map because a hull is the only
     *     kind of place that has an outside in this sense
     */
    public DeckGraph(List<Compartment> compartments, List<RoomRecipe> unplaced,
                     List<BayAperture> apertures) {
        this.compartments = List.copyOf(compartments);
        this.unplaced = List.copyOf(unplaced);
        this.apertures = List.copyOf(apertures);
    }

    public List<Compartment> compartments() {
        return compartments;
    }

    public int compartmentCount() {
        return compartments.size();
    }

    /**
     * The compartment a screen means when it names a purpose, or {@code null}
     * if the deck has none.
     *
     * <p>Largest rather than first placed. A deck may hold more than one room
     * of a purpose — a second stockroom, a forward parts cage — and a view
     * asking for the vehicle bay means the one the ship is organized around,
     * not whichever scrap of that purpose the packer happened to lay down
     * first.
     */
    public Compartment largest(RoomPurpose purpose) {
        Compartment best = null;
        for (Compartment candidate : compartments) {
            if (candidate.purpose() != purpose) continue;
            if (best == null || candidate.area() > best.area()) best = candidate;
        }
        return best;
    }

    /**
     * Every door on this deck that opens onto nothing.
     *
     * <p>Empty on a deck whose program owed no bay, and — worth knowing —
     * on one whose bay the packer could only fit amidships. The recipe demands
     * hull contact, so that should not happen; a bay that reached the outside
     * has a door here and one that did not is a sizing defect the same way an
     * unplaced room is.
     */
    public List<BayAperture> apertures() {
        return apertures;
    }

    /** The door out of one compartment, or null where it has none. */
    public BayAperture apertureOf(int compartmentId) {
        for (BayAperture door : apertures) {
            if (door.compartmentId() == compartmentId) return door;
        }
        return null;
    }

    /** Rooms the program owed that would not fit. Empty on a correctly sized deck. */
    public List<RoomRecipe> unplaced() {
        return unplaced;
    }
}
