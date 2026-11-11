package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * The seam between the ground and the fallen hull's battle rules (`fallen-hull-nouns.md`, In a battle: setup): what
 * the crash-site ground lays and {@link FallenHull#install} stands the watch on. Every cell is a battle grid cell. The
 * ground fills it from its hull place and the chapter's patch; until it does, a test fixture stands in.
 *
 * @param hull           the hull's laid box: its sections, the ground between them and its trench
 * @param forgeX         the autoforge's cell, deep in the hull's forge hall (the root zone's vault)
 * @param mouthX         the hull's bay mouth, where printed walkers and stake trains come out: open ground just
 *                       outside the hull, walkable for a heavy
 * @param perimeterCells how far the hull zone keeps the ground off its sections, Chebyshev from the nearest section
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
 * @param sections       the hull's sections as they lie (a broken hull lies in several; plan order, stern first): the
 *                       ground the root zone keeps, and what a lattice point links to
 */
public record FallenHullSite(Rect hull, int forgeX, int forgeY, int mouthX, int mouthY, int perimeterCells,
                             List<ClaimLattice.Point> lattice, int linkCells, List<int[]> haulRoads,
                             List<StartingStake> stakes, List<Pile> salvage, List<Posting> walkers,
                             List<int[]> egress, float feedstock, List<Rect> sections) {

    /**
     * A site whose hull lies in one piece, its laid box its only section, with {@code perimeterCells} read the old
     * way, Chebyshev from the box's middle, and turned here into a reach off its plates.
     */
    public FallenHullSite(Rect hull, int forgeX, int forgeY, int mouthX, int mouthY, int perimeterCells,
                          List<ClaimLattice.Point> lattice, int linkCells, List<int[]> haulRoads,
                          List<StartingStake> stakes, List<Pile> salvage, List<Posting> walkers,
                          List<int[]> egress, float feedstock) {
        this(hull, forgeX, forgeY, mouthX, mouthY, Math.max(1, perimeterCells
                        - Math.max(hull.right() - hull.centreX(), hull.top() - hull.centreY())), lattice, linkCells,
                haulRoads, stakes, salvage, walkers, egress, feedstock, List.of(hull));
    }

    public FallenHullSite {
        sections = List.copyOf(sections);
        if (sections.isEmpty()) throw new IllegalArgumentException("A hull lies in at least one section");
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

        /** The cell of the rectangle nearest a cell: the cell itself inside. */
        public int[] nearest(int x, int y) {
            return new int[] {Math.max(left, Math.min(right, x)), Math.max(bottom, Math.min(top, y))};
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

        /** The nearest of several rectangles to a cell (the first on a tie). */
        public static Rect nearestOf(List<Rect> rects, int x, int y) {
            Rect best = null;
            int bestD = Integer.MAX_VALUE;
            for (Rect r : rects) {
                int d = r.chebyshev(x, y);
                if (d < bestD) {
                    best = r;
                    bestD = d;
                }
            }
            return best;
        }
    }

    /** A stake the ground stands before the battle. */
    public record StartingStake(int pointId, SurveyStake.Stage stage) { }

    /** A walker of the watch out at the start, at its post. */
    public record Posting(DerelictWalkerFrame frame, int x, int y) { }

    /** Metal standing on the claim: what a stripper cuts into feedstock. */
    public record Pile(int x, int y, float metal) { }
}
