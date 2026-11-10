package com.dillon.starsectormarines.battle.world.gen.raid;

import java.util.ArrayList;
import java.util.List;

/**
 * The lighter apron of a port's Raid site ({@code raid-site-templates.md}, The planet's chokepoint; spaceport): the
 * planet's only orbital lift, laid out in the order cargo moves through it. Pure geometry: where each thing stands on
 * the apron's ground, with nothing laid on a map ({@link PortApronDressing} lays it).
 *
 * <p>The apron is laid across the ground held beside the cargo terminal, from the terminal's side to the deep edge of
 * the map, where the lighters lift. Measured from the terminal outward ({@code u}):
 * <ol>
 *   <li><b>Container staging</b>: a block per pad (two rows of containers back to back and the lane behind them),
 *       cleared from the bonded sheds and waiting for a lighter, and a gantry crane over the first block.</li>
 *   <li><b>The fuel main</b>: one line along the back of the pads, from the fuel farm at its outer end past every
 *       pad, with a hydrant skid at each.</li>
 *   <li><b>The service lane</b> between the main and the pads, where the tankers and carts work a lighter.</li>
 *   <li><b>The pads</b>: a row of hardstands facing the deep edge, a blast wall between each pair, the ground beyond
 *       them clear.</li>
 * </ol>
 * Across the apron ({@code v}) the pads run from the end nearer the map's middle; the fuel farm stands at the outer
 * end, past the last pad, so a fire there burns away from the terminal's flank.
 *
 * <p>Every rectangle is inclusive world cells.
 */
public record PortApron(Rect paved, List<Rect> pads, List<Rect> blastWalls, Rect fuelMain, List<int[]> hydrants,
                        Rect fuelFarm, List<Rect> containers, Rect crane, int deepX) {

    /** Side of a pad: a lighter's hardstand and its keep-clear rim. UNTUNED. */
    public static final int PAD = 11;
    /** Cells between two pads: the blast wall and a walk past it either side. */
    public static final int PAD_GAP = 3;
    /** Clear ground between the pads and the map's deep edge: the lighters' approach. */
    public static final int APPROACH = 3;
    /** The service lane between the fuel main and the pads. */
    public static final int SERVICE = 4;
    /** Depth of the fuel farm: tanks in rows, along {@code u}. */
    public static final int FARM_DEPTH = 7;
    /** Breadth of the fuel farm, along {@code v}. */
    public static final int FARM_BREADTH = 7;
    /** A container, laid along {@code v}: 4 cells long, 2 deep. */
    public static final int BOX_LONG = 4;
    public static final int BOX_DEEP = 2;
    /** A staging block: two rows of containers back to back, then a lane. */
    public static final int LANE = 3;
    /** Gap between containers along a row, so the row is walked through. */
    public static final int BOX_GAP = 2;
    /** Clear cells between the terminal's side of the apron and the first container row: the yard's road. */
    public static final int YARD_ROAD = 4;
    /** Fewest pads an apron is laid with: one fewer and it is not the planet's lift. */
    public static final int MIN_PADS = 2;
    /** Most container rows a staging yard holds, however deep the ground. UNTUNED. */
    public static final int MAX_STAGING_BLOCKS = 5;
    /** Depth of one staging block: two rows of containers and the lane behind them. */
    public static final int BLOCK = 2 * BOX_DEEP + LANE;

    /** An inclusive rectangle of cells. */
    public record Rect(int x0, int y0, int x1, int y1) {
        public Rect {
            if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("an empty rect");
        }

        static Rect of(int ax, int ay, int bx, int by) {
            return new Rect(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by));
        }

        public int width() { return x1 - x0 + 1; }

        public int height() { return y1 - y0 + 1; }

        public int centerX() { return (x0 + x1) / 2; }

        public int centerY() { return (y0 + y1) / 2; }

        public boolean contains(int x, int y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }

        public boolean overlaps(Rect o) { return x0 <= o.x1 && o.x0 <= x1 && y0 <= o.y1 && o.y0 <= y1; }
    }

    public PortApron {
        pads = List.copyOf(pads);
        blastWalls = List.copyOf(blastWalls);
        hydrants = List.copyOf(hydrants);
        containers = List.copyOf(containers);
    }

    /**
     * Pads a port's lift keeps for a market of this size: a lift sized for the planet's traffic, not a town's.
     * UNTUNED.
     */
    public static int padsFor(int marketSize) {
        return Math.max(3, Math.min(6, marketSize - 1));
    }

    /** Cells of ground along {@code u} an apron needs before it holds no staging: pads, approach, lane, main. */
    public static int depthWithoutStaging() {
        return APPROACH + PAD + 1 + SERVICE + 1 + 1 + YARD_ROAD;
    }

    /**
     * The apron on the held ground {@code (x0..x1, y0..y1)}, or null when it holds fewer than {@link #MIN_PADS}
     * pads.
     *
     * @param deepEast  the map's deep edge, where the lighters lift, is east of the ground (else west)
     * @param outerNorth the outer flank, away from the map's middle, is north of the ground (else south): the fuel
     *                   farm's end
     * @param wanted    pads the market's traffic wants ({@link #padsFor})
     */
    public static PortApron lay(int x0, int y0, int x1, int y1, boolean deepEast, boolean outerNorth, int wanted) {
        int depth = x1 - x0 + 1, breadth = y1 - y0 + 1;
        if (depth < depthWithoutStaging()) return null;
        // v runs from the middle end toward the outer end; the farm takes the outer end.
        int across = breadth - FARM_BREADTH - PAD_GAP;
        int fit = (across + PAD_GAP) / (PAD + PAD_GAP);
        int count = Math.min(wanted, fit);
        if (count < MIN_PADS) return null;
        int run = count * PAD + (count - 1) * PAD_GAP;
        // u: 0 at the terminal's side. The apron is laid out from there in the order the cargo moves, a staging
        // block per pad as the ground allows, and the ground past the pads is the lighters' approach.
        int blocks = Math.min(Math.min(count, MAX_STAGING_BLOCKS), (depth - depthWithoutStaging()) / BLOCK);
        int mainU = YARD_ROAD + blocks * BLOCK + 1;
        int padU0 = mainU + SERVICE + 1, padU1 = padU0 + PAD - 1;
        Frame f = new Frame(x0, y0, x1, y1, deepEast, outerNorth);
        int v = 0;
        List<Rect> pads = new ArrayList<>(), walls = new ArrayList<>();
        List<int[]> hydrants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pads.add(f.rect(padU0, v, padU1, v + PAD - 1));
            hydrants.add(f.cell(mainU + 1, v + PAD / 2));
            if (i + 1 < count) walls.add(f.rect(padU0 + 1, v + PAD + 1, padU1 - 1, v + PAD + 1));
            v += PAD + PAD_GAP;
        }
        int farmV0 = run + PAD_GAP, farmV1 = farmV0 + FARM_BREADTH - 1;
        int farmU0 = mainU - FARM_DEPTH / 2, farmU1 = farmU0 + FARM_DEPTH - 1;
        Rect farm = f.rect(farmU0, farmV0, farmU1, farmV1);
        Rect main = f.rect(mainU, 0, mainU, farmV0 - 1);
        // Staging: blocks of two container rows and a lane, from the yard road out to the main.
        List<Rect> boxes = new ArrayList<>();
        Rect crane = null;
        for (int b = 0, u = YARD_ROAD; b < blocks; b++, u += BLOCK) {
            for (int row = 0; row < 2; row++) {
                int ru0 = u + row * BOX_DEEP;
                for (int bv = 0; bv + BOX_LONG - 1 < run; bv += BOX_LONG + BOX_GAP) {
                    boxes.add(f.rect(ru0, bv, ru0 + BOX_DEEP - 1, bv + BOX_LONG - 1));
                }
            }
            // The crane straddles the first block's rows at the run's middle: a leg in the road before them and one
            // in the lane behind, its span over the stacks.
            if (crane == null) {
                int cv = run / 2;
                crane = f.rect(u - 1, cv - 1, u + 2 * BOX_DEEP + 1, cv + 1);
            }
        }
        Rect paved = f.rect(0, 0, padU1 + 1, Math.max(farmV1, run - 1));
        int deepX = deepEast ? x1 : x0;
        return new PortApron(paved, pads, walls, main, hydrants, farm, boxes, crane, deepX);
    }

    /** The apron's {@code (u, v)} frame on the held ground, turned as the site was. */
    private record Frame(int x0, int y0, int x1, int y1, boolean deepEast, boolean outerNorth) {

        /** World x of {@code u} cells from the terminal's side. */
        int x(int u) { return deepEast ? x0 + u : x1 - u; }

        /**
         * World y of {@code v} cells from the middle end: the ground's edge nearer the map's middle, so the pads
         * start beside the terminal's flank and the farm stands outermost.
         */
        int y(int v) { return outerNorth ? y0 + v : y1 - v; }

        Rect rect(int u0, int v0, int u1, int v1) { return Rect.of(x(u0), y(v0), x(u1), y(v1)); }

        int[] cell(int u, int v) { return new int[]{x(u), y(v)}; }
    }
}
