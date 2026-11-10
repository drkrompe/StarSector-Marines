package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * The seam between the ground and the fallen hull's battle rules (`fallen-hull-nouns.md`, In a battle: setup): what
 * the crash-site ground lays and {@link FallenHull#install} stands the watch on. Every cell is a battle grid cell. The
 * ground fills it from its hull place and the chapter's patch; until it does, a test fixture stands in.
 *
 * @param hull           the hull's footprint: its trench and roofed megastructure, the network's root zone
 * @param forgeX         the autoforge's cell, deep in the hull's forge hall (the root zone's vault)
 * @param mouthX         the hull's bay mouth, where printed walkers and stake trains come out: open ground just
 *                       outside the hull, walkable for a heavy
 * @param perimeterCells the hull zone's perimeter, Chebyshev from the hull's middle
 * @param lattice        the claim lattice's points, spaced a zone's perimeter apart
 * @param linkCells      how far apart two lattice points (or a point and the hull's footprint) link, cells
 * @param haulRoads      the haul roads, each a polyline of cells from the hull outward
 * @param stakes         the stakes that already stand at the chapter's point in the arc, each at its stage
 * @param salvage        the metal standing on the claim at the start: headframes, fences, vehicles, the town's
 *                       structures, each a pile at a cell
 * @param walkers        the watch already out at the chapter's start: walkers, strippers and haulers at their posts;
 *                       each answers to the zone keeping its post, the hull's where none does
 * @param egress         the cells the hull's walkers hold in the contingency: the hull's ways in from the trench
 * @param feedstock      the hull store's metal at the start
 */
public record FallenHullSite(Rect hull, int forgeX, int forgeY, int mouthX, int mouthY, int perimeterCells,
                             List<ClaimLattice.Point> lattice, int linkCells, List<int[]> haulRoads,
                             List<StartingStake> stakes, List<Pile> salvage, List<Posting> walkers,
                             List<int[]> egress, float feedstock) {

    public FallenHullSite {
        lattice = List.copyOf(lattice);
        haulRoads = List.copyOf(haulRoads);
        stakes = List.copyOf(stakes);
        salvage = List.copyOf(salvage);
        walkers = List.copyOf(walkers);
        egress = List.copyOf(egress);
        if (perimeterCells < 1) throw new IllegalArgumentException("The hull keeps at least a cell");
        if (feedstock < 0f) throw new IllegalArgumentException("The hull's store is never below empty");
        for (int[] road : haulRoads) {
            if (road.length < 4 || road.length % 2 != 0) throw new IllegalArgumentException("A road is cell pairs");
        }
        for (StartingStake stake : stakes) {
            boolean found = false;
            for (ClaimLattice.Point p : lattice) found |= p.id() == stake.pointId();
            if (!found) throw new IllegalArgumentException("A starting stake stands on a lattice point");
        }
    }

    /** A rectangle of cells, edges inclusive. */
    public record Rect(int left, int bottom, int right, int top) {
        public Rect {
            if (right < left || top < bottom) throw new IllegalArgumentException("An empty rectangle");
        }

        public int centreX() {
            return (left + right) / 2;
        }

        public int centreY() {
            return (bottom + top) / 2;
        }

        public boolean contains(int x, int y) {
            return x >= left && x <= right && y >= bottom && y <= top;
        }

        /** Chebyshev distance from a cell to the nearest cell of the rectangle; 0 inside. */
        public int chebyshev(int x, int y) {
            int dx = Math.max(0, Math.max(left - x, x - right));
            int dy = Math.max(0, Math.max(bottom - y, y - top));
            return Math.max(dx, dy);
        }
    }

    /** A stake the ground stands before the battle. */
    public record StartingStake(int pointId, SurveyStake.Stage stage) { }

    /** A walker of the watch out at the start, at its post. */
    public record Posting(DerelictWalkerFrame frame, int x, int y) { }

    /** Metal standing on the claim: what a stripper cuts into feedstock. */
    public record Pile(int x, int y, float metal) { }
}
