package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * The ship deck's published structure: its compartments and the athwartships
 * corridors that divide them.
 *
 * <p>This is deliberately <em>not</em> the station's room/corridor graph. There,
 * corridors are edges between room vertices, which suits a layout organized
 * around a core. On a ship the spine and its transverse passages are places in
 * their own right — the main line of advance and the cross-connections that
 * make the deck something other than a comb — so they are recorded rather than
 * reduced to adjacency.
 *
 * <p>Topological roles such as depth-from-entry and must-pass chokepoints are
 * not here yet; they arrive with the bulkhead stage that gives the deck its
 * ordered chokepoint sequence. See {@code ship-interiors-nouns.md}.
 */
public final class DeckGraph {

    /** One carved compartment. Bounds are the inclusive walkable cell rect. */
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

        /** Depth outboard from the spine, in cells. */
        public int depth() {
            return bottom - top + 1;
        }
    }

    private final List<Compartment> compartments;
    private final int[] corridorFrames;

    /**
     * @param compartments carved compartments, in deterministic carve order
     * @param corridorFrames the fore-most column of each athwartships corridor,
     *     ascending bow to stern
     */
    public DeckGraph(List<Compartment> compartments, int[] corridorFrames) {
        this.compartments = List.copyOf(compartments);
        this.corridorFrames = corridorFrames.clone();
    }

    public List<Compartment> compartments() {
        return compartments;
    }

    public int compartmentCount() {
        return compartments.size();
    }

    /** The fore-most column of each athwartships corridor, ascending bow to stern. */
    public int[] corridorFrames() {
        return corridorFrames.clone();
    }
}
