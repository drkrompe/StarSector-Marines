package com.dillon.starsectormarines.battle.world.gen.ship;

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

    /** One placed room. Bounds are the inclusive walkable cell rect, excluding its bulkheads. */
    public record Compartment(int id, int left, int top, int right, int bottom,
                              DeckSide side, DeckZone zone, RoomPurpose purpose) {

        /** First frame this compartment spans. */
        public int foreFrame() {
            return left;
        }

        /** Last frame this compartment spans. */
        public int aftFrame() {
            return right;
        }

        public int width() {
            return right - left + 1;
        }

        /** Extent across the beam, in cells. */
        public int depth() {
            return bottom - top + 1;
        }

        public int area() {
            return width() * depth();
        }
    }

    private final List<Compartment> compartments;
    private final List<RoomRecipe> unplaced;

    /**
     * @param compartments placed rooms, in deterministic placement order
     * @param unplaced rooms from the program the deck had no space for
     */
    public DeckGraph(List<Compartment> compartments, List<RoomRecipe> unplaced) {
        this.compartments = List.copyOf(compartments);
        this.unplaced = List.copyOf(unplaced);
    }

    public List<Compartment> compartments() {
        return compartments;
    }

    public int compartmentCount() {
        return compartments.size();
    }

    /** Rooms the program owed that would not fit. Empty on a correctly sized deck. */
    public List<RoomRecipe> unplaced() {
        return unplaced;
    }
}
