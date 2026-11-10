package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A garrison laid out as a military installation rather than packed ({@code raid-mission.md}, Garrison district):
 * a gate on the road in, a motor pool whose vehicles have a paved way out of that gate that no building stands on, a
 * quadrangle of barracks round a parade ground with the headquarters at its head, the depot to either hand, and a
 * flight line. The owner's notes it answers: the packed garrison read as "very messy", and the first rowed plans left
 * the motor pool's vehicles no clear way out and too small a motor pool and airfield for the garrison.
 *
 * <p>The layout is drawn in the place's own frame, with the gate at the near edge: {@code u} runs across the place and
 * {@code v} runs in from the gate. Every building is placed on a footprint (its floor and the wall ring round it) the
 * ground can take whole, so a road the town grew through the place is stepped round, never built over.
 *
 * <p>Pure: it reads a buildable mask and returns placements, streets, the motor pool's hardstand and open ground, so it
 * is decided here and tested here; {@code PrecinctWardStage} authors and dresses what it returns.
 */
public final class Cantonment {

    /**
     * How the garrison's districts are laid out across its square. Offered to the owner as picture options (picks
     * {@code raid-garrison-districts}); the property {@value #LAYOUT_PROPERTY} chooses one for the cards and is removed,
     * with the losing layouts, once the owner picks.
     */
    public enum Layout {
        /** Everything along the avenue: the motor yard at the gate, the quadrangle behind it, the flight line at the back. */
        YARD,
        /**
         * Three bands across the square, a cross street between each: logistics at the gate (workshops, the motor yard,
         * stores), the garrison in the middle (barracks lines along both side walls, a wide parade ground, the
         * headquarters at its head), operations at the back (the flight line and the magazines kept apart).
         */
        BANDS,
        /**
         * Two wings down the sides of the square, each on its own street: the barracks wing and the logistics wing
         * (workshops, stores, the generator and, at its far end, the magazines). Down the middle: the motor yard at
         * the gate, the parade ground with the headquarters at its head, the flight line at the back.
         */
        WINGS
    }

    /**
     * Temporary A/B lever for the district-layout cards: {@code YARD}, {@code BANDS} or {@code WINGS}. Removed with the
     * losing layouts once the owner picks {@code raid-garrison-districts}.
     */
    public static final String LAYOUT_PROPERTY = "battle.garrison.layout";

    /** The layout the property names, {@link Layout#YARD} when it names none. */
    public static Layout chosenLayout() {
        String named = System.getProperty(LAYOUT_PROPERTY, "");
        for (Layout layout : Layout.values()) {
            if (layout.name().equalsIgnoreCase(named.trim())) return layout;
        }
        return Layout.YARD;
    }

    /** Width of a cross street between two districts, and of a district's own street. UNTUNED. */
    static final int CROSS = 4;
    /** Clear ground between two magazines, and round the magazine yard: kept apart so one going up takes no other. */
    static final int DISPERSE = 5;

    /** Cells from the place's edge to the nearest footprint: the wall's own cell and a patrol lane inside it. */
    static final int MARGIN = 3;
    /** Half the main avenue's width beside its centre cell: a five-cell avenue. */
    static final int AVENUE_HALF = 2;
    /** Street between two rows of buildings. */
    static final int STREET = 3;
    /** Alley between two buildings side by side in one row. */
    static final int ALLEY = 2;
    /** Cells the avenue widens by on each side where the motor pool's sheds open on it. UNTUNED. */
    static final int FLARE = 3;
    /** Depth of a motor yard's hardstand in front of its sheds' doors: room to turn a vehicle out. UNTUNED. */
    static final int STAND = 9;
    /** Half the parade ground's width beside its centre cell. UNTUNED. */
    static final int PARADE_HALF = 10;
    /**
     * Cells added to a cantonment's side for a road another place grows through it, which its rows step round.
     * UNTUNED.
     */
    public static final int ROAD_SLACK = 8;
    /** Shallowest parade ground worth the name. UNTUNED. */
    static final int PARADE_MIN = 10;
    /** How far a building may slide to step round ground it cannot take. */
    private static final int SLIDE = 12;
    /** Cells across a vehicle's way out: its own width and a cell either side. */
    public static final int VEHICLE_WAY = 3;

    /** One building of the order, as the program states its floor. */
    record Want(RoomPurpose purpose, RoomShape shape) { }

    /** What a garrison of this program owes, sorted into the roles the layout is made of. */
    record Order(List<Want> barracks, List<Want> sheds, List<Want> depot, Want hq, Want gatehouse,
                 List<Want> messes, int gatePosts, int towers, AirbaseLot.Size field) {

        static Order of(FortressProgram program) {
            List<Want> barracks = new ArrayList<>();
            List<Want> sheds = new ArrayList<>();
            List<Want> depot = new ArrayList<>();
            List<Want> messes = new ArrayList<>();
            Want hq = null, gatehouse = null;
            int posts = 0;
            for (FortressBuilding b : program.buildings()) {
                for (int i = 0; i < b.count(); i++) {
                    Want w = new Want(b.purpose(), b.shape());
                    switch (b.purpose()) {
                        case BARRACKS -> barracks.add(w);
                        case VEHICLE_BAY -> sheds.add(w);
                        case KEEP_THRONE -> hq = w;
                        case KEEP_ENTRY -> gatehouse = w;
                        case MESS_HALL -> messes.add(w);
                        case CONTROL_ROOM -> posts++;
                        default -> depot.add(w);
                    }
                }
            }
            int gatePosts = Math.min(2, posts);
            return new Order(barracks, sheds, depot, hq, gatehouse, messes, gatePosts,
                    Math.min(4, posts - gatePosts), program.airfields() > 0 ? fieldFor(barracks.size()) : null);
        }
    }

    /**
     * The flight line a garrison of {@code barracks} blocks flies from: a field with its runway and sheds for a large
     * one, a pad for the rest. The garrison's size is the market's, so its air arm is too. A station is a wing's
     * ground, more than a Raid site's garrison has room for. UNTUNED.
     */
    static AirbaseLot.Size fieldFor(int barracks) {
        return barracks >= 5 ? AirbaseLot.Size.FIELD : AirbaseLot.Size.PAD;
    }

    /**
     * The largest square of {@code own} ground, {@code {left, bottom, side}} in map cells, the one nearest the middle
     * of all the ground where several tie; {@code null} for none. A neighbour's claim may take a corner of a
     * cantonment's square, and the cantonment is laid on the square that is left whole rather than across the gap.
     */
    public static int[] square(boolean[][] own) {
        int w = own.length, h = own[0].length;
        long sx = 0, sy = 0, n = 0;
        int[][] run = new int[w][h];
        int best = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!own[x][y]) continue;
                sx += x;
                sy += y;
                n++;
                run[x][y] = x == 0 || y == 0 ? 1
                        : 1 + Math.min(run[x - 1][y], Math.min(run[x][y - 1], run[x - 1][y - 1]));
                best = Math.max(best, run[x][y]);
            }
        }
        if (best == 0) return null;
        double cx = (double) sx / n, cy = (double) sy / n;
        int[] out = null;
        double near = Double.MAX_VALUE;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (run[x][y] != best) continue;
                double d = Math.hypot(x - (best - 1) / 2.0 - cx, y - (best - 1) / 2.0 - cy);
                if (d < near) {
                    near = d;
                    out = new int[]{x - best + 1, y - best + 1, best};
                }
            }
        }
        return out;
    }

    /** The flight line {@code program} is owed, or {@code null} for none. */
    public static AirbaseLot.Size fieldFor(FortressProgram program) {
        return Order.of(program).field();
    }

    /** A guard post's floor: a gate post or a corner tower. */
    static final RoomShape POST = RoomShape.rectangle(4, 4);

    /** A footprint's face, in the place's own frame. */
    enum Face { FRONT, BACK, LEFT, RIGHT }

    /** A building stood on its footprint, in the place's own frame; {@code (u, v)} is the footprint's near corner. */
    record Stood(Want want, boolean turned, int u, int v, List<Face> doors) {
        int across() { return (turned ? want.shape.height() : want.shape.width()) + 2; }
        int deep() { return (turned ? want.shape.width() : want.shape.height()) + 2; }
    }

    /** A rectangle of the place's own frame, inclusive of its near corner and exclusive of its far one. */
    record Rect(int u, int v, int across, int deep) {
        boolean overlaps(Rect o) {
            return u < o.u + o.across && o.u < u + across && v < o.v + o.deep && o.v < v + deep;
        }
    }

    /** The place's own frame on the map: its square, and the side the gate is on. */
    record Frame(int left, int bottom, int right, int top, Side gate) {
        boolean swaps() { return gate == Side.WEST || gate == Side.EAST; }
        int across() { return swaps() ? top - bottom + 1 : right - left + 1; }
        int depth() { return swaps() ? right - left + 1 : top - bottom + 1; }

        /** The map cell of frame cell {@code (u, v)}. */
        int[] cell(int u, int v) {
            return switch (gate) {
                case WEST -> new int[]{left + v, bottom + u};
                case EAST -> new int[]{right - v, bottom + u};
                case SOUTH -> new int[]{left + u, bottom + v};
                case NORTH -> new int[]{left + u, top - v};
            };
        }
    }

    /**
     * What the layout came to, in map cells: rooms, streets (the avenue and any forecourt), the motor
     * pool's hardstand, the parade ground, the flight line's lot, the gate's cell on the avenue just inside the wall,
     * what had to be seated wherever there was room ({@code strays}: a road the town grew or the map took its place),
     * and anything it could not seat at all.
     */
    public record Laid(List<RoomPacker.Placed> rooms, List<int[]> streets, List<int[]> pool, List<int[]> parade,
                       int[] airfield, AirbaseLot.Size airfieldSize, AirbaseLot.Facing facing, int[] gate,
                       List<RoomPurpose> strays, List<RoomPurpose> unplaced) {
        public Laid {
            rooms = List.copyOf(rooms);
            streets = List.copyOf(streets);
            pool = List.copyOf(pool);
            parade = List.copyOf(parade);
            strays = List.copyOf(strays);
            unplaced = List.copyOf(unplaced);
        }
    }

    private final boolean[][] buildable;
    private final Frame frame;
    private final int axis;
    private final List<Stood> stood = new ArrayList<>();
    private final List<Rect> taken = new ArrayList<>();
    private final List<Rect> streets = new ArrayList<>();
    private final List<Rect> pool = new ArrayList<>();
    private final List<Rect> parade = new ArrayList<>();
    private final List<RoomPurpose> unplaced = new ArrayList<>();
    private final List<RoomPurpose> strays = new ArrayList<>();
    private final Map<RoomPurpose, Want> wanted = new EnumMap<>(RoomPurpose.class);
    private Rect airfield;
    private AirbaseLot.Size airfieldSize;
    private AirbaseLot.Facing airfieldFacing;
    /** Depth the layout starts at: inside the patrol lane, or behind a forecourt street. */
    private int front = MARGIN;
    /** The across-position the road comes in at. */
    private int entry;

    private Cantonment(boolean[][] buildable, Frame frame, int axis) {
        this.buildable = buildable;
        this.frame = frame;
        this.axis = axis;
        this.entry = axis;
    }

    /**
     * Lays a garrison's program.
     *
     * @param buildable the ground the place may build on, map-sized: its own claim inside the glacis, less roads
     * @param left      the place's square, map cells inclusive
     * @param gate      the side the main gate is on
     * @param axis      where the road comes in, as a map coordinate across the gate side
     */
    public static Laid lay(boolean[][] buildable, int left, int bottom, int right, int top, Side gate, int axis,
                           FortressProgram program) {
        Frame frame = new Frame(left, bottom, right, top, gate);
        int entry = frame.swaps() ? axis - bottom : axis - left;
        Cantonment c = new Cantonment(buildable, frame, frame.across() / 2);
        if (Math.abs(entry - c.axis) > AVENUE_HALF) c.forecourt(entry);
        Order order = Order.of(program);
        for (FortressBuilding b : program.buildings()) c.wanted.putIfAbsent(b.purpose(), new Want(b.purpose(), b.shape()));
        switch (chosenLayout()) {
            case BANDS -> c.bands(order);
            case WINGS -> c.wings(order);
            default -> c.yard(order);
        }
        c.seatLeftovers();
        return c.result();
    }

    /**
     * The smallest square side whose open ground seats the whole program in its places, its flight line at the size
     * the garrison is owed.
     */
    public static int side(FortressProgram program) {
        AirbaseLot.Size owed = fieldFor(program);
        for (int s = 40; s < 240; s += 2) {
            boolean[][] open = new boolean[s][s];
            for (boolean[] col : open) Arrays.fill(col, true);
            Laid laid = lay(open, 0, 0, s - 1, s - 1, Side.SOUTH, s / 2, program);
            if (laid.unplaced().isEmpty() && laid.strays().isEmpty() && laid.airfieldSize() == owed) return s;
        }
        return 240;
    }

    /**
     * Weight per cell of a road's width in choosing the gate. Large enough that the widest road in is the main gate
     * whichever side it is on: a cantonment lays its avenue wider than its further gates' roads
     * ({@code CantonmentRoads}), and its rows are laid square to the avenue. UNTUNED.
     */
    private static final double WIDE_ROAD = 3.0;
    /** What a road in off the map's edge costs a gate: more than any width buys, so it is the last choice. */
    private static final double OFF_MAP = 100;

    /** Where the main gate is: the side of the place it is in, and the road's centre across that side (map cells). */
    public record Gate(Side side, int axis) { }

    /**
     * The main gate: where a road the town grew comes in through the place's edge, the widest road first, on the side
     * that faces the middle of the map (where the rest of the site is), nearest that side's middle. A road leaving off
     * the map's edge is a gate too, but the last choice. With no road in at all, the side facing the map's middle, at
     * its middle.
     *
     * @param own  the place's ground inside its glacis, map-sized
     * @param road whether a road runs on each cell, map-sized
     */
    public static Gate gate(boolean[][] own, boolean[][] road, int left, int bottom, int right, int top) {
        int w = own.length, h = own[0].length;
        double cx = (left + right) / 2.0, cy = (bottom + top) / 2.0;
        double tx = w / 2.0 - cx, ty = h / 2.0 - cy;
        double norm = Math.max(1e-6, Math.hypot(tx, ty));
        Gate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Side side : Side.values()) {
            boolean alongX = side == Side.NORTH || side == Side.SOUTH;
            int fixed = switch (side) { case NORTH -> top; case SOUTH -> bottom; case EAST -> right; case WEST -> left; };
            int lo = alongX ? left : bottom, hi = alongX ? right : top;
            int outX = alongX ? 0 : side.dx, outY = alongX ? side.dy : 0;
            int runStart = -1;
            for (int k = lo; k <= hi + 1; k++) {
                int x = alongX ? k : fixed, y = alongX ? fixed : k;
                boolean entry = k <= hi && x >= 0 && y >= 0 && x < w && y < h && own[x][y] && road[x][y];
                if (entry && runStart < 0) runStart = k;
                if (entry || runStart < 0) continue;
                int mid = (runStart + k - 1) / 2;
                int width = k - runStart;
                runStart = -1;
                int ox = (alongX ? mid : fixed) + outX, oy = (alongX ? fixed : mid) + outY;
                boolean offMap = ox < 0 || oy < 0 || ox >= w || oy >= h;
                double half = Math.max(1, (hi - lo) / 2.0);
                double score = WIDE_ROAD * Math.min(width, 9) + (side.dx * tx + side.dy * ty) / norm
                        - 0.5 * Math.abs(mid - (lo + hi) / 2.0) / half - (offMap ? OFF_MAP : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = new Gate(side, mid);
                }
            }
        }
        if (best != null) return best;
        Side side = Side.facing((int) Math.round(tx), (int) Math.round(ty));
        return new Gate(side, side == Side.NORTH || side == Side.SOUTH ? (left + right) / 2 : (bottom + top) / 2);
    }

    /**
     * The road in comes through the wall off the avenue's line: a street along the inside of the gate wall from where
     * it enters to the avenue, and the layout starts behind it.
     */
    private void forecourt(int at) {
        entry = Math.max(MARGIN + AVENUE_HALF, Math.min(frame.across() - MARGIN - AVENUE_HALF - 1, at));
        int lo = Math.max(1, Math.min(entry, axis) - AVENUE_HALF);
        int hi = Math.min(frame.across() - 1, Math.max(entry, axis) + AVENUE_HALF + 1);
        streets.add(new Rect(lo, 0, hi - lo, FORECOURT));
        front = FORECOURT + ALLEY;
    }

    /** Depth of the street along the gate wall where the road in is off the avenue's line. */
    static final int FORECOURT = 6;

    // ---- The layout --------------------------------------------------------------------------------------------

    /**
     * The motor pool on the avenue. Gate posts either side of the avenue; then the sheds either side of it, each
     * standing with its door end on the avenue where it widens into the hardstand, the depot taking what that band
     * leaves; then the quadrangle with the depot down both sides of it, and the flight line against the back wall.
     */
    private void yard(Order o) {
        int inLo = MARGIN, inHi = frame.across() - MARGIN;
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        int v = front;
        int gate = gateRow(o, v, inLo, inHi);
        if (gate > 0) v += gate + STREET;
        // The sheds, their door ends on the hardstand; the depot fills the side with fewer.
        int h0 = av0 - FLARE, h1 = av1 + FLARE;
        List<Want> sl = new ArrayList<>(), sr = new ArrayList<>();
        split(o.sheds, sl, sr);
        int poolV = v;
        int endL = stack(sl, false, h0 - 1, -1, v, Integer.MAX_VALUE, Face.RIGHT, ALLEY, inLo, inHi);
        int endR = stack(sr, false, h1, +1, v, Integer.MAX_VALUE, Face.LEFT, ALLEY, inLo, inHi);
        int poolEnd = Math.max(endL, endR);
        List<Want> depot = new ArrayList<>(o.depot);
        if (!o.sheds.isEmpty()) {
            if (endL < poolEnd) fill(depot, h0 - 1, -1, endL, poolEnd, Face.RIGHT, inLo, inHi);
            if (endR < poolEnd) fill(depot, h1, +1, endR, poolEnd, Face.LEFT, inLo, inHi);
            pool.add(new Rect(h0, poolV, h1 - h0, poolEnd - poolV));
            v = poolEnd - ALLEY + STREET;
        }
        int back = depth() - MARGIN;
        streets.add(new Rect(av0, 0, av1 - av0, v + 1));
        quadrangle(o, depot, v, inLo, inHi, back);
        // The flight line against the back wall, nearest the avenue's line: behind the headquarters where the square
        // is deep enough, beside it where it is not, so the back of the square is used rather than left open.
        if (o.field != null) flightLine(o.field, away(), inLo, inHi, v, back, true);
        towers(o.towers, inLo, inHi, front, back);
    }

    /**
     * Three bands across the square, a cross street between each. Logistics at the gate: the motor yard on the avenue,
     * the workshops and generator to one hand and the stores to the other, in rows off the cross street behind. The
     * garrison in the middle: a line of barracks and a mess along each side wall on its own street, the parade ground
     * across the width between them, the headquarters at its head. Operations at the back: the
     * flight line against the back wall, the magazines kept apart in a back corner.
     */
    private void bands(Order o) {
        int inLo = MARGIN, inHi = frame.across() - MARGIN, back = depth() - MARGIN;
        int v = front;
        int gate = gateRow(o, v, inLo, inHi);
        towers(o.towers, inLo, inHi, front, back);
        if (gate > 0) v += gate + STREET;
        List<Want> over = new ArrayList<>();
        int c1 = gateBand(o, v, inLo, inHi,
                of(o.depot, RoomPurpose.PARTS_CAGE, RoomPurpose.ENGINE_ROOM, RoomPurpose.STOCKROOM), over);
        streets.add(new Rect(inLo, c1, inHi - inLo, CROSS));

        // The garrison: a line of barracks and a mess along each side wall, each on its street, the parade ground
        // across the width between them and the headquarters at its head.
        int m0 = c1 + CROSS + ALLEY;
        List<Want> lines = new ArrayList<>(o.barracks);
        lines.addAll(o.messes);
        int col = maxAcross(lines);
        int ls = inLo + col + ALLEY, rs = inHi - col - ALLEY - CROSS;
        List<Want> bl = new ArrayList<>(), br = new ArrayList<>();
        split(lines, bl, br);
        int colEnd = Math.max(stack(bl, false, ls - ALLEY - 1, -1, m0, back, Face.RIGHT, ALLEY, inLo, inHi),
                stack(br, false, rs + CROSS + ALLEY, +1, m0, back, Face.LEFT, ALLEY, inLo, inHi)) - ALLEY;
        int hqDeep = o.hq == null ? 0 : o.hq.shape.height() + 2;
        int hv = Math.max(m0 + PARADE_MIN + ALLEY, colEnd - hqDeep);
        parade.add(new Rect(ls + CROSS + ALLEY, m0, rs - ls - CROSS - 2 * ALLEY, hv - ALLEY - m0));
        int midEnd = Math.max(colEnd, hv + headquarters(o, hv, ls + CROSS + ALLEY, rs - ALLEY));
        int c2 = midEnd + ALLEY;
        streets.add(new Rect(ls, c1, CROSS, c2 - c1));
        streets.add(new Rect(rs, c1, CROSS, c2 - c1));
        streets.add(new Rect(inLo, c2, inHi - inLo, CROSS));

        // Operations: the flight line against the back wall, the magazines kept apart in the back corner beside it.
        int b0 = c2 + CROSS + ALLEY;
        if (o.field != null) flightLine(o.field, away(), inLo, inHi, b0, back, true);
        int fLo = airfield == null ? inLo : airfield.u, fHi = airfield == null ? inLo : airfield.u + airfield.across;
        boolean left = fLo - inLo >= inHi - fHi;
        dispersed(of(o.depot, RoomPurpose.ARMORY), left ? inLo : fHi + DISPERSE, left ? fLo - DISPERSE : inHi, b0,
                back, left);
        for (Want w : over) unplaced.add(w.purpose);
    }

    /**
     * Two wings down the sides of the square behind the motor yard, each on its own street: the barracks wing with
     * its messes, and the logistics wing with the stores and generator and, at its far end, the magazines kept apart.
     * At the gate, the motor yard with its workshops beside it; down the middle between the wing streets, the parade
     * ground with the headquarters at its head, a cross street, the flight line against the back wall.
     */
    private void wings(Order o) {
        int inLo = MARGIN, inHi = frame.across() - MARGIN, back = depth() - MARGIN;
        int v = front;
        int gate = gateRow(o, v, inLo, inHi);
        towers(o.towers, inLo, inHi, front, back);
        if (gate > 0) v += gate + STREET;
        List<Want> over = new ArrayList<>();
        int c1 = gateBand(o, v, inLo, inHi, of(o.depot, RoomPurpose.PARTS_CAGE), over);
        streets.add(new Rect(inLo, c1, inHi - inLo, CROSS));

        int w0 = c1 + CROSS + ALLEY;
        List<Want> lines = new ArrayList<>(o.barracks);
        lines.addAll(o.messes);
        List<Want> stores = of(o.depot, RoomPurpose.STOCKROOM, RoomPurpose.ENGINE_ROOM);
        List<Want> magazines = of(o.depot, RoomPurpose.ARMORY);
        int ls = inLo + maxAcross(lines) + ALLEY;
        int rs = inHi - Math.max(maxAcross(stores), maxAcross(magazines)) - ALLEY - CROSS;
        streets.add(new Rect(ls, c1, CROSS, back - c1));
        streets.add(new Rect(rs, c1, CROSS, back - c1));
        // Short of the back corner, where a tower stands.
        int corner = o.towers > 0 ? POST.height() + 2 + ALLEY : 0;
        over.addAll(column(lines, ls - ALLEY - 1, -1, w0, back - corner, Face.RIGHT, ALLEY));
        // The magazine yard at the wing's far end, below the corner tower there and clear of the stores above.
        int magDepth = o.towers > 0 ? POST.height() + 2 + DISPERSE : 0;
        for (Want w : magazines) magDepth += w.shape.height() + 2 + DISPERSE;
        int magV = Math.max(w0, back - magDepth);
        over.addAll(column(stores, rs + CROSS + ALLEY, +1, w0, magV - DISPERSE, Face.LEFT, ALLEY));
        dispersed(magazines, rs + CROSS + ALLEY, inHi, magV, back, false);

        int cLo = ls + CROSS + ALLEY, cHi = rs - ALLEY;
        int fieldDeep = o.field == null ? 0 : span(o.field, away())[1] + ALLEY + CROSS + ALLEY;
        int hqDeep = o.hq == null ? 0 : o.hq.shape.height() + 2;
        // The parade ground takes two thirds of the depth to spare, the flight line the rest to settle in.
        int spare = Math.max(0, back - fieldDeep - hqDeep - (w0 + PARADE_MIN + ALLEY));
        int hv = w0 + PARADE_MIN + ALLEY + spare * 2 / 3;
        parade.add(new Rect(cLo, w0, cHi - cLo, hv - ALLEY - w0));
        int c2 = hv + headquarters(o, hv, cLo, cHi) + ALLEY;
        if (o.field != null) {
            streets.add(new Rect(ls, c2, rs + CROSS - ls, CROSS));
            flightLine(o.field, away(), cLo, cHi, c2 + CROSS + ALLEY, back, true);
        }
        for (Want w : over) unplaced.add(w.purpose);
    }

    /**
     * The band inside the gate: the motor yard on the avenue and {@code logistics} in rows to either hand, the wider
     * hand first, off the cross street behind. Returns the depth the cross street starts at; what did not fit is added
     * to {@code over}.
     */
    private int gateBand(Order o, int v, int inLo, int inHi, List<Want> logistics, List<Want> over) {
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        int[] yard = sheds(o, v, inLo, inHi);
        int lHi = yard[1] - ALLEY, rLo = yard[2] + ALLEY;
        boolean leftFirst = lHi - inLo >= inHi - rLo;
        int firstLo = leftFirst ? inLo : rLo, firstHi = leftFirst ? lHi : inHi;
        int otherLo = leftFirst ? rLo : inLo, otherHi = leftFirst ? inHi : lHi;
        List<Want> first = new ArrayList<>(logistics), second = new ArrayList<>();
        while (!first.isEmpty() && rowsHold(first, firstHi - firstLo) < first.size()) {
            second.add(0, first.remove(first.size() - 1));
        }
        int bandEnd = Math.max(yard[0], Math.max(v + need(first, firstHi - firstLo),
                v + need(second, otherHi - otherLo)));
        over.addAll(rows(first, firstLo, firstHi, v, bandEnd));
        over.addAll(rows(second, otherLo, otherHi, v, bandEnd));
        pool.add(new Rect(av0 - FLARE, v, av1 - av0 + 2 * FLARE, bandEnd - v));
        streets.add(new Rect(av0, 0, av1 - av0, bandEnd + ALLEY));
        return bandEnd + ALLEY;
    }

    /**
     * The motor yard's sheds either side of the avenue from depth {@code v}, their door ends on the hardstand between:
     * {@code {end, lo, hi}}, the depth the yard ends at and its span across.
     */
    private int[] sheds(Order o, int v, int inLo, int inHi) {
        int h0 = axis - AVENUE_HALF - FLARE, h1 = axis + AVENUE_HALF + 1 + FLARE;
        List<Want> sl = new ArrayList<>(), sr = new ArrayList<>();
        split(o.sheds, sl, sr);
        int before = stood.size();
        int endL = stack(sl, false, h0 - 1, -1, v, Integer.MAX_VALUE, Face.RIGHT, ALLEY, inLo, inHi);
        int endR = stack(sr, false, h1, +1, v, Integer.MAX_VALUE, Face.LEFT, ALLEY, inLo, inHi);
        int lo = h0, hi = h1;
        for (int i = before; i < stood.size(); i++) {
            lo = Math.min(lo, stood.get(i).u);
            hi = Math.max(hi, stood.get(i).u + stood.get(i).across());
        }
        return new int[]{Math.max(Math.max(endL, endR) - ALLEY, v + STAND), lo, hi};
    }

    /**
     * The headquarters at depth {@code hv} on the avenue's line, facing the gate, or as near it within
     * {@code [inLo, inHi)} as the ground takes it (a road the town brought in to the middle); its depth.
     */
    private int headquarters(Order o, int hv, int inLo, int inHi) {
        if (o.hq == null) return 0;
        int across = o.hq.shape.width() + 2, deep = o.hq.shape.height() + 2;
        int at = axis - across / 2;
        for (int d = 0; d <= SLIDE; d++) {
            for (int u : new int[]{at - d, at + d}) {
                if (u >= inLo && u + across <= inHi && seats(u, hv, across, deep)) {
                    return stand(o.hq, false, u, hv, List.of(Face.FRONT)).deep();
                }
            }
        }
        unplaced.add(o.hq.purpose);
        return 0;
    }

    /** The buildings of {@code wants} of the given purposes, in order. */
    private static List<Want> of(List<Want> wants, RoomPurpose... purposes) {
        List<Want> out = new ArrayList<>();
        for (Want w : wants) if (Arrays.asList(purposes).contains(w.purpose)) out.add(w);
        return out;
    }

    /** The widest footprint across among {@code wants}, standing square to the frame. */
    private static int maxAcross(List<Want> wants) {
        int across = 0;
        for (Want w : wants) across = Math.max(across, w.shape.width() + 2);
        return across;
    }

    /** As many of {@code rest} as stand in one row {@code len} long, an alley apart, taken off its front. */
    private static List<Want> take(List<Want> rest, int len) {
        List<Want> out = new ArrayList<>();
        int used = -ALLEY;
        while (!rest.isEmpty() && used + ALLEY + rest.get(0).shape.width() + 2 <= len) {
            used += ALLEY + rest.get(0).shape.width() + 2;
            out.add(rest.remove(0));
        }
        return out;
    }

    private static int deepest(List<Want> wants) {
        int deep = 0;
        for (Want w : wants) deep = Math.max(deep, w.shape.height() + 2);
        return deep;
    }

    /** How many of {@code wants}, from the front, {@link #rows} stands in its two rows {@code len} across. */
    private static int rowsHold(List<Want> wants, int len) {
        List<Want> rest = new ArrayList<>(wants);
        return take(rest, len).size() + take(rest, len).size();
    }

    /** Depth {@link #rows} takes for {@code wants} in a block {@code len} across. */
    private static int need(List<Want> wants, int len) {
        List<Want> rest = new ArrayList<>(wants);
        int a = deepest(take(rest, len)), b = deepest(take(rest, len));
        return b == 0 ? a : a + ALLEY + b;
    }

    /**
     * Up to two rows across {@code [u0, u1)} by depths {@code [v0, v1)}: the first flush with {@code v1}, its doors to
     * the back on the street there, the second flush with {@code v0}, its doors to the front; each spread evenly along
     * its length. Returns what did not fit.
     */
    private List<Want> rows(List<Want> wants, int u0, int u1, int v0, int v1) {
        List<Want> rest = new ArrayList<>(wants);
        List<Want> a = take(rest, u1 - u0), b = take(rest, u1 - u0);
        if (deepest(a) + ALLEY + deepest(b) > v1 - v0) {
            rest.addAll(0, b);
            b.clear();
        }
        rest.addAll(spread(a, u0, u1, v1, Face.BACK));
        rest.addAll(spread(b, u0, u1, v0, Face.FRONT));
        return rest;
    }

    /** One row spread evenly across {@code [u0, u1)}, flush with depth {@code v} on its door side; what could not stand. */
    private List<Want> spread(List<Want> row, int u0, int u1, int v, Face door) {
        List<Want> out = new ArrayList<>();
        if (row.isEmpty()) return out;
        int sum = 0;
        for (Want w : row) sum += w.shape.width() + 2;
        int gap = Math.max(ALLEY, (u1 - u0 - sum) / (row.size() + 1));
        int u = u0 + Math.max(0, (u1 - u0 - sum - gap * (row.size() - 1)) / 2);
        for (Want w : row) {
            int across = w.shape.width() + 2, deep = w.shape.height() + 2;
            int at = door == Face.BACK ? v - deep : v;
            Stood s = null;
            for (int d = 0; d <= SLIDE && s == null; d++) {
                for (int sign : new int[]{1, -1}) {
                    int uu = u + sign * d;
                    if (s == null && uu >= u0 && uu + across <= u1 && seats(uu, at, across, deep)) {
                        s = stand(w, false, uu, at, List.of(door));
                    }
                }
            }
            if (s == null) out.add(w);
            u += across + gap;
        }
        return out;
    }

    /**
     * Stands {@code wants} one behind another down depths {@code [v0, v1)}, spread evenly, each with its door face
     * toward the street at {@code u} and running away from it ({@code dir}); returns what did not fit.
     */
    private List<Want> column(List<Want> wants, int u, int dir, int v0, int v1, Face door, int gapMin) {
        List<Want> fit = new ArrayList<>(), out = new ArrayList<>();
        int used = -gapMin;
        for (Want w : wants) {
            if (used + gapMin + w.shape.height() + 2 <= v1 - v0) {
                used += gapMin + w.shape.height() + 2;
                fit.add(w);
            } else {
                out.add(w);
            }
        }
        if (fit.isEmpty()) return out;
        int sum = used - gapMin * (fit.size() - 1);
        int gap = Math.max(gapMin, (v1 - v0 - sum) / (fit.size() + 1));
        int v = v0 + Math.max(0, (v1 - v0 - sum - gap * (fit.size() - 1)) / 2);
        for (Want w : fit) {
            Stood s = null;
            for (int d = 0; d <= SLIDE && s == null && v + d + w.shape.height() + 2 <= v1; d++) {
                s = slide(w, false, u, dir, v + d, door, MARGIN, frame.across() - MARGIN, true);
            }
            if (s == null) out.add(w);
            v = (s == null ? v : s.v) + w.shape.height() + 2 + gap;
        }
        return out;
    }

    /**
     * The magazines in {@code [u0, u1)} by {@code [v0, v1)}, each {@link #DISPERSE} clear of every other building, from
     * the back wall's corner on the {@code left} or right; what cannot stand is recorded unplaced.
     */
    private void dispersed(List<Want> magazines, int u0, int u1, int v0, int v1, boolean left) {
        for (Want w : magazines) {
            int across = w.shape.width() + 2, deep = w.shape.height() + 2;
            Stood s = null;
            for (int v = v1 - deep; v >= v0 && s == null; v--) {
                for (int k = 0; k + across <= u1 - u0 && s == null; k++) {
                    int u = left ? u0 + k : u1 - across - k;
                    if (seats(u, v, across, deep) && apart(new Rect(u - DISPERSE, v - DISPERSE,
                            across + 2 * DISPERSE, deep + 2 * DISPERSE))) {
                        s = stand(w, false, u, v, List.of(Face.FRONT));
                    }
                }
            }
            if (s == null) unplaced.add(w.purpose);
        }
    }

    /** Whether no building's footprint overlaps {@code r}. */
    private boolean apart(Rect r) {
        for (Rect t : taken) if (t.overlaps(r)) return false;
        return true;
    }

    /**
     * Posts either side of the avenue where it comes through the gate, the guardroom beside the right one; returns
     * the row's depth, or 0 for none.
     */
    private int gateRow(Order o, int v, int inLo, int inHi) {
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        List<Want> left = new ArrayList<>(), right = new ArrayList<>();
        if (o.gatePosts >= 1) left.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        if (o.gatePosts >= 2) right.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        if (o.gatehouse != null) right.add(o.gatehouse);
        return Math.max(row(left, av0 - ALLEY - 1, -1, v, Face.RIGHT, inLo, inHi),
                row(right, av1 + ALLEY, +1, v, Face.LEFT, inLo, inHi));
    }

    /**
     * The quadrangle from depth {@code v}: the parade ground on the avenue's line with a column of barracks down each
     * side facing it, and the headquarters across its head facing the gate with the messes either hand. Outside the
     * barracks the depot stands in rows down both sides. Returns the depth it ends at.
     */
    private int quadrangle(Order o, List<Want> depot, int v, int inLo, int inHi, int limit) {
        List<Want> bl = new ArrayList<>(), br = new ArrayList<>();
        split(o.barracks, bl, br);
        int colAcross = 0;
        for (Want w : o.barracks) colAcross = Math.max(colAcross, w.shape.height() + 2);
        int pLo = axis - PARADE_HALF, pHi = axis + PARADE_HALF + 1;
        int endL = stack(bl, true, pLo - STREET - 1, -1, v, limit, Face.RIGHT, ALLEY, inLo, inHi);
        int endR = stack(br, true, pHi + STREET, +1, v, limit, Face.LEFT, ALLEY, inLo, inHi);
        int paradeEnd = Math.max(Math.max(endL, endR) - ALLEY, v + PARADE_MIN);
        parade.add(new Rect(pLo, v, pHi - pLo, paradeEnd - v));
        // The head row: the headquarters on the avenue's line, the messes either hand.
        int hv = paradeEnd + STREET;
        int headDeep = 0;
        if (o.hq != null) {
            int u = axis - (o.hq.shape.width() + 2) / 2;
            if (seats(u, hv, o.hq.shape.width() + 2, o.hq.shape.height() + 2)) {
                Stood s = stand(o.hq, false, u, hv, List.of(Face.FRONT));
                headDeep = s.deep();
            } else {
                unplaced.add(o.hq.purpose);
            }
        }
        int hqLo = o.hq == null ? axis : axis - (o.hq.shape.width() + 2) / 2;
        int hqHi = o.hq == null ? axis + 1 : hqLo + o.hq.shape.width() + 2;
        List<Want> ml = new ArrayList<>(), mr = new ArrayList<>();
        split(o.messes, ml, mr);
        headDeep = Math.max(headDeep, row(ml, hqLo - ALLEY - 1, -1, hv, Face.FRONT, inLo, inHi));
        headDeep = Math.max(headDeep, row(mr, hqHi + ALLEY, +1, hv, Face.FRONT, inLo, inHi));
        int end = hv + headDeep;
        // Outside the barracks: the depot.
        int outL = pLo - STREET - 1 - colAcross - ALLEY;
        int outR = pHi + STREET + colAcross + ALLEY;
        List<Want> dl = new ArrayList<>(), dr = new ArrayList<>();
        split(depot, dl, dr);
        end = Math.max(end, block(dl, outL, -1, v, limit, inLo, inHi));
        end = Math.max(end, block(dr, outR, +1, v, limit, inLo, inHi));
        depot.clear();
        return end;
    }

    /**
     * Lays {@code wants} in rows outward from {@code u} (direction {@code dir}), a street between rows, every door to
     * the front; returns the depth the last row ends at. What cannot stand is recorded unplaced.
     */
    private int block(List<Want> wants, int u, int dir, int v, int limit, int inLo, int inHi) {
        int end = v;
        int i = 0;
        while (i < wants.size() && v < limit) {
            int at = u, deep = 0, start = i;
            while (i < wants.size()) {
                Want w = wants.get(i);
                if (v + w.shape.height() + 2 > limit) break;
                Stood s = slide(w, false, at, dir, v, Face.FRONT, inLo, inHi, true);
                if (s == null) break;
                at = dir < 0 ? s.u - ALLEY - 1 : s.u + s.across() + ALLEY;
                deep = Math.max(deep, s.deep());
                i++;
            }
            if (i == start) {
                v++;
                continue;
            }
            end = v + deep;
            v += deep + STREET;
        }
        for (; i < wants.size(); i++) unplaced.add(wants.get(i).purpose);
        return end;
    }

    /** The flight line's facing with its strip toward the back wall, its sheds toward the gate. */
    private AirbaseLot.Facing away() {
        return AirbaseLot.Facing.valueOf(frame.gate().opposite().name());
    }


    // ---- Rows and columns --------------------------------------------------------------------------------------

    /** Splits a list between the two sides of the avenue, alternating, so both sides fill evenly. */
    private static void split(List<Want> wants, List<Want> left, List<Want> right) {
        for (int i = 0; i < wants.size(); i++) (i % 2 == 0 ? left : right).add(wants.get(i));
    }

    /**
     * Stands {@code wants} one behind another from depth {@code v}, each with its door face toward {@code u} and its
     * footprint running away from it in direction {@code dir}, {@code gap} apart, no deeper than {@code limit}. A
     * building whose band is taken (a road the town grew) steps back until one seats it. Returns the depth after the
     * last one and its gap; what cannot stand is recorded unplaced.
     */
    private int stack(List<Want> wants, boolean turned, int u, int dir, int v, int limit, Face door, int gap,
                      int inLo, int inHi) {
        for (Want w : wants) {
            int deep = (turned ? w.shape.width() : w.shape.height()) + 2;
            Stood s = null;
            for (int dv = 0; dv <= SLIDE && s == null && v + dv + deep <= Math.min(limit, depth() - MARGIN); dv++) {
                s = slide(w, turned, u, dir, v + dv, door, inLo, inHi, true);
            }
            if (s == null) {
                unplaced.add(w.purpose);
                continue;
            }
            v = s.v + s.deep() + gap;
        }
        return v;
    }

    /**
     * Stands as many of {@code depot} as fit one behind another between depths {@code v} and {@code limit}, removing
     * those it stands.
     */
    private void fill(List<Want> depot, int u, int dir, int v, int limit, Face door, int inLo, int inHi) {
        for (int i = 0; i < depot.size(); ) {
            Want w = depot.get(i);
            int deep = w.shape.height() + 2;
            if (v + deep > limit) {
                i++;
                continue;
            }
            Stood s = slide(w, false, u, dir, v, door, inLo, inHi, true);
            if (s == null) {
                i++;
                continue;
            }
            depot.remove(i);
            v = s.v + s.deep() + ALLEY;
        }
    }

    /**
     * Lays {@code wants} in one row outward from {@code u} (in direction {@code dir}) at depth {@code v}; returns the
     * row's depth. A building the ground cannot seat is recorded unplaced.
     */
    private int row(List<Want> wants, int u, int dir, int v, Face door, int inLo, int inHi) {
        int deep = 0;
        for (Want w : wants) deep = Math.max(deep, w.shape.height() + 2);
        for (Want w : wants) {
            Stood s = slide(w, false, u, dir, v, door, inLo, inHi, false);
            if (s == null) {
                unplaced.add(w.purpose);
                continue;
            }
            u = dir < 0 ? s.u - ALLEY - 1 : s.u + s.across() + ALLEY;
        }
        return wants.isEmpty() ? 0 : deep;
    }

    /**
     * Stands {@code w} at the first position from {@code u} outward ({@code dir}) its footprint fits: with
     * {@code dir} negative its far edge at {@code u}, else its near edge; {@code null} when nothing fits within
     * {@link #SLIDE} or the place's span.
     */
    private Stood slide(Want w, boolean turned, int u, int dir, int v, Face door, int inLo, int inHi, boolean quiet) {
        int across = (turned ? w.shape.height() : w.shape.width()) + 2;
        int deep = (turned ? w.shape.width() : w.shape.height()) + 2;
        for (int step = 0; step <= SLIDE; step++) {
            int near = dir < 0 ? u - step - across + 1 : u + step;
            if (near < inLo || near + across > inHi) return null;
            if (seats(near, v, across, deep)) return stand(w, turned, near, v, List.of(door));
        }
        return null;
    }

    /**
     * Whether a footprint stands on buildable ground clear of every footprint, street, hardstand, open ground and
     * flight line already laid.
     */
    private boolean seats(int u, int v, int across, int deep) {
        if (u < 0 || v < 0 || u + across > frame.across() || v + deep > frame.depth()) return false;
        Rect r = new Rect(u, v, across, deep);
        for (Rect t : taken) if (t.overlaps(r)) return false;
        for (Rect t : streets) if (t.overlaps(r)) return false;
        for (Rect t : pool) if (t.overlaps(r)) return false;
        for (Rect t : parade) if (t.overlaps(r)) return false;
        if (airfield != null && airfield.overlaps(r)) return false;
        for (int du = 0; du < across; du++) {
            for (int dv = 0; dv < deep; dv++) {
                int[] c = frame.cell(u + du, v + dv);
                if (c[0] < 0 || c[1] < 0 || c[0] >= buildable.length || c[1] >= buildable[0].length
                        || !buildable[c[0]][c[1]]) return false;
            }
        }
        return true;
    }

    private Stood stand(Want w, boolean turned, int u, int v, List<Face> doors) {
        Stood s = new Stood(w, turned, u, v, doors);
        stood.add(s);
        taken.add(new Rect(u, v, s.across(), s.deep()));
        return s;
    }

    private int depth() {
        return frame.depth();
    }

    /** Corner towers inside the four corners of the place, as many as the program owes, the far corners first. */
    private void towers(int count, int inLo, int inHi, int v0, int v1) {
        int[][] corners = {{inLo, v1 - 6}, {inHi - 6, v1 - 6}, {inLo, v0}, {inHi - 6, v0}};
        Face[] faces = {Face.RIGHT, Face.LEFT, Face.RIGHT, Face.LEFT};
        int placed = 0;
        for (int i = 0; i < corners.length && placed < count; i++) {
            if (!seats(corners[i][0], corners[i][1], 6, 6)) continue;
            stand(new Want(RoomPurpose.CONTROL_ROOM, POST), false, corners[i][0], corners[i][1], List.of(faces[i]));
            placed++;
        }
        for (; placed < count; placed++) unplaced.add(RoomPurpose.CONTROL_ROOM);
    }

    /**
     * The flight line in the span {@code [u0, u1)} by depths {@code [v0, v1)}, facing {@code facing}, against the back
     * ({@code fromBack}) or the front of that span and as near the avenue's line as it seats: the size the garrison is
     * owed, or the largest smaller one that fits.
     */
    private void flightLine(AirbaseLot.Size owed, AirbaseLot.Facing facing, int u0, int u1, int v0, int v1,
                            boolean fromBack) {
        AirbaseLot.Size[] ladder = {AirbaseLot.Size.STATION, AirbaseLot.Size.FIELD, AirbaseLot.Size.PAD,
                AirbaseLot.Size.STRIP};
        boolean reached = false;
        for (AirbaseLot.Size size : ladder) {
            reached |= size == owed;
            if (!reached) continue;
            int[] span = span(size, facing);
            for (int k = 0; k <= v1 - v0 - span[1]; k++) {
                int v = fromBack ? v1 - span[1] - k : v0 + k;
                int best = Integer.MIN_VALUE;
                for (int u = u0; u + span[0] <= u1; u++) {
                    if (!seats(u, v, span[0], span[1])) continue;
                    if (best == Integer.MIN_VALUE
                            || Math.abs(u + span[0] / 2 - axis) < Math.abs(best + span[0] / 2 - axis)) {
                        best = u;
                    }
                }
                if (best != Integer.MIN_VALUE) {
                    airfield = new Rect(best, v, span[0], span[1]);
                    airfieldSize = size;
                    airfieldFacing = facing;
                    return;
                }
            }
        }
    }

    /** A lot's span in the frame, {across, deep}, clearance included. */
    private int[] span(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        int x = AirbaseLot.reservedSpanX(size, facing), y = AirbaseLot.reservedSpanY(size, facing);
        return frame.swaps() ? new int[]{y, x} : new int[]{x, y};
    }

    /**
     * Seats what the layout could not, where a road the town grew or the map's edge took its place: each at the free
     * position nearest the gate, square to the rest, its door toward the gate. Still squared, never lost.
     */
    private void seatLeftovers() {
        List<RoomPurpose> left = new ArrayList<>(unplaced);
        unplaced.clear();
        for (RoomPurpose purpose : left) {
            Want w = leftover(purpose);
            Stood s = null;
            int across = w.shape.width() + 2, deep = w.shape.height() + 2;
            for (int v = MARGIN; v + deep <= frame.depth() - MARGIN + 1 && s == null; v++) {
                for (int u = MARGIN; u + across <= frame.across() - MARGIN + 1 && s == null; u++) {
                    if (seats(u - 1, v - 1, across + 2, deep + 2)) s = stand(w, false, u, v, List.of(Face.FRONT));
                }
            }
            if (s == null) unplaced.add(w.purpose);
            else strays.add(w.purpose);
        }
    }

    /** The building of {@code purpose} this order owes, as its program states it. */
    private Want leftover(RoomPurpose purpose) {
        for (Stood s : stood) if (s.want.purpose == purpose) return s.want;
        return purpose == RoomPurpose.CONTROL_ROOM ? new Want(purpose, POST) : wanted.get(purpose);
    }

    // ---- Onto the map ----------------------------------------------------------------------------------------

    private Laid result() {
        List<RoomPacker.Placed> rooms = new ArrayList<>();
        for (Stood s : stood) rooms.add(placed(s));
        List<int[]> streetCells = new ArrayList<>(), poolCells = new ArrayList<>(), paradeCells = new ArrayList<>();
        for (Rect r : streets) cells(r, streetCells);
        for (Rect r : pool) cells(r, poolCells);
        for (Rect r : parade) cells(r, paradeCells);
        int[] lot = null;
        if (airfield != null) {
            int[] a = frame.cell(airfield.u, airfield.v);
            int[] b = frame.cell(airfield.u + airfield.across - 1, airfield.v + airfield.deep - 1);
            lot = new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
        }
        return new Laid(rooms, streetCells, poolCells, paradeCells, lot, airfieldSize,
                airfieldFacing == null ? away() : airfieldFacing,
                frame.cell(entry, 1), strays, unplaced);
    }

    private void cells(Rect r, List<int[]> out) {
        for (int du = 0; du < r.across; du++) {
            for (int dv = 0; dv < r.deep; dv++) {
                int[] c = frame.cell(r.u + du, r.v + dv);
                if (c[0] >= 0 && c[1] >= 0 && c[0] < buildable.length && c[1] < buildable[0].length) out.add(c);
            }
        }
    }

    /** A stood building as the map's room: its floor posed to the map and its doors cut in the faces it was given. */
    private RoomPacker.Placed placed(Stood s) {
        int fu = s.u + 1, fv = s.v + 1, lu = s.across() - 2, lv = s.deep() - 2;
        int[] a = frame.cell(fu, fv), b = frame.cell(fu + lu - 1, fv + lv - 1);
        int ox = Math.min(a[0], b[0]), oy = Math.min(a[1], b[1]);
        int worldW = Math.abs(a[0] - b[0]) + 1;
        RoomShape canon = s.want.shape;
        RoomPose pose = canon.width() == worldW ? new RoomPose(0, false) : new RoomPose(1, false);
        List<Doorway> doors = new ArrayList<>();
        for (Face f : s.doors) {
            int span = (f == Face.FRONT || f == Face.BACK) ? lu : lv;
            int mid = span / 2;
            int[] offs = span >= 8 ? new int[]{mid - 1, mid} : new int[]{mid};
            for (int o : offs) {
                int du, dv;
                switch (f) {
                    case FRONT -> { du = fu + o; dv = s.v; }
                    case BACK -> { du = fu + o; dv = s.v + s.deep() - 1; }
                    case LEFT -> { du = s.u; dv = fv + o; }
                    default -> { du = s.u + s.across() - 1; dv = fv + o; }
                }
                int[] c = frame.cell(du, dv);
                doors.add(new Doorway(c[0], c[1]));
            }
        }
        return new RoomPacker.Placed(canon.posed(pose), pose, ox, oy, s.want.purpose, doors);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Cantonment[%d stood, %d unplaced]", stood.size(), unplaced.size());
    }
}
