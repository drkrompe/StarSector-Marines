package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;

import java.util.ArrayList;
import java.util.List;

/**
 * A port's Raid hub ({@code raid-site-templates.md}, The planet's chokepoint; spaceport): the planet's travel and
 * freight hub, laid in bands across the works' half of the map as the owner's reference port is. Pure geometry: where
 * each band and each thing in it stands, with nothing laid on a map ({@link PortHubDressing} lays it).
 *
 * <p>Measured outward from the map's middle ({@code u}), the bands run in the order cargo moves through the port:
 * <ol>
 *   <li><b>The lines in</b>: the highway and the rail line from the planet's towns, run the hub's whole length
 *       and back past the cargo terminal to the town, off the map at the deep edge.</li>
 *   <li><b>The warehouse rows</b>: one or two rows of bonded warehouses, in bays of two with a cross street between
 *       bays, the near row's doors on the rail road, the far row's on the yard road.</li>
 *   <li><b>Container staging</b>: blocks of two container rows and a lane, broken at the cross streets, gantry
 *       cranes over every other block.</li>
 *   <li><b>The fuel main</b> along the back of the pads, from the fuel farm at the terminal's end, a hydrant at each
 *       pad, and the service lane.</li>
 *   <li><b>The pad row</b> along the outer edge: lighters' pads with a blast wall between each pair, and the heavy
 *       pad, a ring twice a pad's size, at the deep end.</li>
 * </ol>
 * Along the hub ({@code v}) everything runs from the terminal's side to the map's deep edge.
 *
 * <p>Every rectangle is inclusive world cells.
 */
public record PortHub(Rect lines, Rect highway, List<Rect> tracks, Rect railRoad, List<Shed> sheds, Rect apron,
                      List<Rect> containers, List<Rect> cranes, Rect fuelMain, List<int[]> hydrants, Rect fuelFarm,
                      List<Rect> pads, List<Rect> blastWalls, Rect heavyPad, Rect paved, boolean deepEast,
                      boolean outerNorth) {

    /** The highway's carriageway: two lanes each way. */
    public static final int HIGHWAY = 5;
    /** Open verge either side of the rail line. */
    public static final int VERGE = 1;
    /** One rail track, and the gap between two. */
    public static final int TRACK = 2, TRACK_GAP = 1;
    /** Tracks the rail line runs: a planet's freight, not a branch line. UNTUNED. */
    public static final int TRACKS = 3;
    /** The road along the rail line, the warehouses' near row's doors on it. */
    public static final int RAIL_ROAD = 3;
    /** A warehouse's ground: a long store on its yard, as a layout structure raises it. */
    public static final int SHED_ALONG = StructureKind.GRANARY.along, SHED_DEEP = StructureKind.GRANARY.deep;
    /** Cells between the two warehouses of a bay, and the cross street between bays. */
    public static final int SHED_GAP = 3, CROSS = 5;
    /** Warehouses in a bay. */
    public static final int SHEDS_PER_BAY = 2;
    /** The lane between the two warehouse rows. */
    public static final int ROW_LANE = 3;
    /** Most warehouse rows the hub holds, as the reference port's. */
    public static final int MAX_ROWS = 2;
    /** The yard road between the warehouses and the container staging. */
    public static final int YARD_ROAD = 4;
    /** A container, laid along {@code v}: 4 cells long, 2 deep, with a gap between boxes along a row. */
    public static final int BOX_LONG = 4, BOX_DEEP = 2, BOX_GAP = 2;
    /** The lane behind a block's two rows of containers. */
    public static final int LANE = 3;
    /** Depth of one staging block. */
    public static final int BLOCK = 2 * BOX_DEEP + LANE;
    /** Most staging blocks however deep the ground. UNTUNED. */
    public static final int MAX_BLOCKS = 5;
    /** The service lane between the fuel main and the pads. */
    public static final int SERVICE = 4;
    /** A lighter's pad, the gap between two (the blast wall and a walk past it), and the heavy pad. UNTUNED. */
    public static final int PAD = 11, PAD_GAP = 3, HEAVY_PAD = 21;
    /** The fuel farm: tanks in a bund at the pad row's terminal end. */
    public static final int FARM = 7;
    /** Clear ground between the pads and the outer edge: the lighters' approach. */
    public static final int APPROACH = 3;
    /** Fewest lighters' pads a hub is laid with: fewer and it is not the planet's lift. */
    public static final int MIN_PADS = 2;

    /** A warehouse's ground and the quarter turns its door faces by (0: doors toward the map's south). */
    public record Shed(Rect at, int turns) { }

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

    public PortHub {
        tracks = List.copyOf(tracks);
        sheds = List.copyOf(sheds);
        containers = List.copyOf(containers);
        cranes = List.copyOf(cranes);
        hydrants = List.copyOf(hydrants);
        pads = List.copyOf(pads);
        blastWalls = List.copyOf(blastWalls);
    }

    /** Depth of the lines' band: the highway, the rail line between its verges, and the rail road. */
    public static int linesDepth() {
        return HIGHWAY + VERGE + TRACKS * TRACK + (TRACKS - 1) * TRACK_GAP + VERGE + RAIL_ROAD;
    }

    /** Depth the apron needs past its staging: yard road, main, service lane, pads and approach. */
    static int apronFixed() {
        return YARD_ROAD + 1 + SERVICE + PAD + APPROACH;
    }

    /**
     * The hub on the ground {@code (x0..x1, y0..y1)}, or null where it holds no staging block or fewer than
     * {@link #MIN_PADS} pads.
     *
     * @param deepEast   the map's deep edge is east of the ground: {@code v} runs east (else west)
     * @param outerNorth the map's outer edge is north of the ground: {@code u} runs north from the highway (else south)
     * @param back       cells the lines run back past the ground's terminal end, under the terminal to the town
     */
    public static PortHub lay(int x0, int y0, int x1, int y1, boolean deepEast, boolean outerNorth, int back) {
        int length = x1 - x0 + 1, depth = y1 - y0 + 1;
        int lines = linesDepth();
        int spare = depth - lines - apronFixed();
        if (spare < BLOCK) return null;
        // A block of staging first, then the warehouse rows, then more staging as the ground allows.
        int blocks = 1, rows = 0;
        spare -= BLOCK;
        if (spare >= SHED_DEEP) {
            rows = 1;
            spare -= SHED_DEEP;
        }
        if (rows == 1 && MAX_ROWS > 1 && spare >= ROW_LANE + SHED_DEEP) {
            rows = 2;
            spare -= ROW_LANE + SHED_DEEP;
        }
        int more = Math.min(MAX_BLOCKS - 1, spare / BLOCK);
        blocks += more;
        spare -= more * BLOCK;
        // Ground left over lies between the lines and the map's middle: the hub keeps to the outer edge, as far from
        // the garrison across the lines as it can.
        Frame f = new Frame(x0, y0, x1, y1, deepEast, outerNorth, spare);
        int last = length - 1;

        // The pad row along the outer edge: the farm at the terminal's end, the lighters' pads, the heavy pad last.
        int apronU0 = lines + rows * SHED_DEEP + Math.max(0, rows - 1) * ROW_LANE;
        int blockU0 = apronU0 + YARD_ROAD;
        int mainU = blockU0 + blocks * BLOCK;
        int padU0 = mainU + 1 + SERVICE, padU1 = padU0 + PAD - 1;
        int heavyV0 = last - HEAVY_PAD + 1;
        boolean heavy = heavyV0 >= FARM + PAD_GAP + MIN_PADS * (PAD + PAD_GAP) && padU1 - HEAVY_PAD + 1 >= blockU0;
        int padsEnd = heavy ? heavyV0 - PAD_GAP : length;
        List<Rect> pads = new ArrayList<>(), walls = new ArrayList<>();
        List<int[]> hydrants = new ArrayList<>();
        int v = FARM + PAD_GAP;
        while (v + PAD <= padsEnd) {
            pads.add(f.rect(padU0, v, padU1, v + PAD - 1));
            hydrants.add(f.cell(mainU + 1, v + PAD / 2));
            int wallV = v + PAD + PAD_GAP / 2;
            if (wallV < padsEnd || heavy) walls.add(f.rect(padU0 + 1, wallV, padU1 - 1, wallV));
            v += PAD + PAD_GAP;
        }
        if (pads.size() < MIN_PADS) return null;
        int padRun = v - PAD_GAP;
        Rect heavyPad = heavy ? f.rect(padU1 - HEAVY_PAD + 1, heavyV0, padU1, last) : null;
        Rect farm = f.rect(padU0, 0, padU0 + FARM - 1, FARM - 1);
        Rect main = f.rect(mainU, 0, mainU, padRun - 1);

        // The warehouse bays along the rows, and the cross streets between bays the staging breaks at.
        List<int[]> segments = new ArrayList<>();
        List<Integer> shedV = new ArrayList<>();
        int segStart = 0;
        v = 0;
        for (int n = 1; v + SHED_ALONG <= length; n++) {
            shedV.add(v);
            boolean bayEnd = n % SHEDS_PER_BAY == 0;
            v += SHED_ALONG + (bayEnd ? CROSS : SHED_GAP);
            if (bayEnd) {
                segments.add(new int[]{segStart, v - CROSS - 1});
                segStart = v;
            }
        }
        if (segStart < length) segments.add(new int[]{segStart, last});
        List<Shed> sheds = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            int u0 = lines + r * (SHED_DEEP + ROW_LANE);
            // The near row's doors face the rail road, toward the map's middle; the far row's the yard road.
            boolean doorsSouth = (r == 0) == outerNorth;
            for (int sv : shedV) sheds.add(new Shed(f.rect(u0, sv, u0 + SHED_DEEP - 1, sv + SHED_ALONG - 1),
                    doorsSouth ? 0 : 2));
        }

        // Staging: two rows of containers and a lane per block, each row broken at the cross streets and kept off
        // the heavy pad's ground; a gantry crane over every other block, one to a bay.
        List<Rect> boxes = new ArrayList<>(), cranes = new ArrayList<>();
        for (int b = 0; b < blocks; b++) {
            int u0 = blockU0 + b * BLOCK;
            boolean underHeavy = heavyPad != null && u0 + 2 * BOX_DEEP - 1 >= padU1 - HEAVY_PAD + 1;
            int limit = underHeavy ? heavyV0 - PAD_GAP - 1 : last;
            for (int[] seg : segments) {
                int end = Math.min(seg[1], limit);
                if (end - seg[0] + 1 < BOX_LONG) continue;
                for (int row = 0; row < 2; row++) {
                    int ru0 = u0 + row * BOX_DEEP;
                    for (int bv = seg[0]; bv + BOX_LONG - 1 <= end; bv += BOX_LONG + BOX_GAP) {
                        boxes.add(f.rect(ru0, bv, ru0 + BOX_DEEP - 1, bv + BOX_LONG - 1));
                    }
                }
                if (b % 2 == 0 && end - seg[0] + 1 >= 3 * BOX_LONG) {
                    int cv = (seg[0] + end) / 2;
                    cranes.add(f.rect(u0 - 1, cv - 1, u0 + 2 * BOX_DEEP + 1, cv + 1));
                }
            }
        }

        Rect lineRect = f.rect(0, -back, lines - 1, last);
        Rect highway = f.rect(0, -back, HIGHWAY - 1, last);
        List<Rect> tracks = new ArrayList<>();
        for (int t = 0, u = HIGHWAY + VERGE; t < TRACKS; t++, u += TRACK + TRACK_GAP) {
            tracks.add(f.rect(u, -back, u + TRACK - 1, last));
        }
        Rect railRoad = f.rect(lines - RAIL_ROAD, -back, lines - 1, last);
        int outer = depth - 1 - spare;
        Rect apron = f.rect(apronU0, 0, outer, last);
        Rect paved = f.rect(HIGHWAY, 0, outer, last);
        return new PortHub(lineRect, highway, tracks, railRoad, sheds, apron, boxes, cranes, main, hydrants, farm,
                pads, walls, heavyPad, paved, deepEast, outerNorth);
    }

    /** The world x of the hub's terminal end, where {@code v} is 0. */
    public int terminalX() {
        return deepEast ? paved.x0() : paved.x1();
    }

    /** The hub's {@code (u, v)} frame on its ground, turned as the site was. */
    private record Frame(int x0, int y0, int x1, int y1, boolean deepEast, boolean outerNorth, int shift) {

        /** World x of {@code v} cells from the terminal's end (negative: back past it). */
        int x(int v) { return deepEast ? x0 + v : x1 - v; }

        /** World y of {@code u} cells out from the highway's middle-side edge, {@code shift} out from the ground's. */
        int y(int u) { return outerNorth ? y0 + shift + u : y1 - shift - u; }

        Rect rect(int u0, int v0, int u1, int v1) { return Rect.of(x(v0), y(u0), x(v1), y(u1)); }

        int[] cell(int u, int v) { return new int[]{x(v), y(u)}; }
    }
}
