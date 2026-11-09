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
 * a gate on the road in, an avenue from it, and the garrison's buildings stood in rows square to that avenue, each
 * facing a street, with a parade ground they muster on and the headquarters across it. The owner's note it answers:
 * the packed garrison read as "very messy".
 *
 * <p>The layout is drawn in the place's own frame, with the gate at the near edge: {@code u} runs across the place and
 * {@code v} runs in from the gate. Every building is placed on a footprint (its floor and the wall ring round it) the
 * ground can take whole, so a road the town grew through the place is stepped round, never built over.
 *
 * <p>Pure: it reads a buildable mask and returns placements, streets and open ground, so it is decided here and tested
 * here; {@code PrecinctWardStage} authors and dresses what it returns.
 */
public final class Cantonment {

    /**
     * How the rows are arranged. Three are offered to the owner (picks {@code raid-garrison}); the property
     * {@value #PLAN_PROPERTY} chooses one for the cards and is removed, with the losing plans, once the owner picks.
     */
    public enum Plan {
        /** An avenue from the gate to the headquarters: service row, barrack rows, the parade ground, the HQ row. */
        PARADE,
        /** Two avenues crossing in the middle; motor pool, barracks, depot and HQ each hold a quarter. */
        QUADRANTS,
        /** Every building faces one central parade ground from the four sides of a ring road. */
        QUADRANGLE
    }

    /**
     * Temporary A/B lever for the garrison-layout cards: {@code PARADE}, {@code QUADRANTS} or {@code QUADRANGLE}.
     * Removed with the losing plans once the owner picks {@code raid-garrison}.
     */
    public static final String PLAN_PROPERTY = "battle.garrison.plan";

    /** The plan the property names, {@link Plan#PARADE} when it names none. */
    public static Plan chosenPlan() {
        String named = System.getProperty(PLAN_PROPERTY, "");
        for (Plan plan : Plan.values()) {
            if (plan.name().equalsIgnoreCase(named.trim())) return plan;
        }
        return Plan.PARADE;
    }

    /** Cells from the place's edge to the nearest footprint: the wall's own cell and a patrol lane inside it. */
    static final int MARGIN = 3;
    /** Half the main avenue's width beside its centre cell: a five-cell avenue. */
    static final int AVENUE_HALF = 2;
    /** Street between two rows of buildings. */
    static final int STREET = 3;
    /** Alley between two buildings side by side in one row. */
    static final int ALLEY = 2;
    /**
     * Cells added to a cantonment's side for the roads the town grows through it, which its rows step round, and for
     * its flight line. UNTUNED.
     */
    public static final int ROAD_SLACK = 14;
    /** Shallowest parade ground worth the name. UNTUNED. */
    static final int PARADE_MIN = 10;
    /** Widest a parade ground grows across. UNTUNED. */
    static final int PARADE_MAX_ACROSS = 44;
    /** How far a building may slide along its row to step round ground it cannot take. */
    private static final int SLIDE = 12;

    /** One building of the order, as the program states its floor. */
    record Want(RoomPurpose purpose, RoomShape shape) { }

    /** What a garrison of this program owes, sorted into the roles the rows are made of. */
    record Order(List<Want> barracks, List<Want> service, List<Want> depot, Want hq, Want gatehouse,
                 Want mess, int gatePosts, int towers, int airfields) {

        static Order of(FortressProgram program) {
            List<Want> barracks = new ArrayList<>();
            List<Want> service = new ArrayList<>();
            List<Want> depot = new ArrayList<>();
            Want hq = null, gatehouse = null, mess = null;
            int posts = 0;
            for (FortressBuilding b : program.buildings()) {
                for (int i = 0; i < b.count(); i++) {
                    Want w = new Want(b.purpose(), b.shape());
                    switch (b.purpose()) {
                        case BARRACKS -> barracks.add(w);
                        case VEHICLE_BAY, PARTS_CAGE -> service.add(w);
                        case ARMORY, STOCKROOM, ENGINE_ROOM -> depot.add(w);
                        case KEEP_THRONE -> hq = w;
                        case KEEP_ENTRY -> gatehouse = w;
                        case MESS_HALL -> { if (mess == null) mess = w; else barracks.add(w); }
                        case CONTROL_ROOM -> posts++;
                        default -> depot.add(w);
                    }
                }
            }
            int gatePosts = Math.min(2, posts);
            return new Order(barracks, service, depot, hq, gatehouse, mess, gatePosts,
                    Math.min(4, posts - gatePosts), program.airfields());
        }

        int buildings() {
            return barracks.size() + service.size() + depot.size() + (hq == null ? 0 : 1)
                    + (gatehouse == null ? 0 : 1) + (mess == null ? 0 : 1) + gatePosts + towers;
        }
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

        /** The frame across-position of map cell {@code (x, y)}. */
        int across(int x, int y) {
            return swaps() ? y - bottom : x - left;
        }
    }

    /** What the layout came to: rooms on the map, streets and open ground as map cells, and anything it could not seat. */
    public record Laid(List<RoomPacker.Placed> rooms, List<int[]> streets, List<int[]> parade,
                       int[] airfield, AirbaseLot.Size airfieldSize, AirbaseLot.Facing facing,
                       List<RoomPurpose> unplaced) {
        public Laid {
            rooms = List.copyOf(rooms);
            streets = List.copyOf(streets);
            parade = List.copyOf(parade);
            unplaced = List.copyOf(unplaced);
        }
    }

    private final boolean[][] buildable;
    private final Frame frame;
    private final int axis;
    private final List<Stood> stood = new ArrayList<>();
    private final List<Rect> taken = new ArrayList<>();
    private final List<Rect> streets = new ArrayList<>();
    private final List<Rect> parade = new ArrayList<>();
    private final List<RoomPurpose> unplaced = new ArrayList<>();
    private final Map<RoomPurpose, Want> wanted = new EnumMap<>(RoomPurpose.class);
    private Rect airfield;
    private AirbaseLot.Size airfieldSize;
    private final AirbaseLot.Facing facing;
    /** Depth the rows start at: inside the patrol lane, or behind a forecourt street. */
    private int front = MARGIN;

    private Cantonment(boolean[][] buildable, Frame frame, int axis) {
        this.buildable = buildable;
        this.frame = frame;
        this.axis = axis;
        this.facing = AirbaseLot.Facing.valueOf(frame.gate().name());
    }

    /**
     * Lays a garrison's program in rows.
     *
     * @param buildable the ground the place may build on, map-sized: its own claim inside the glacis, less roads
     * @param left      the place's square, map cells inclusive
     * @param gate      the side the main gate is on
     * @param axis      the main avenue's centre, as a map coordinate across the gate side
     */
    public static Laid lay(boolean[][] buildable, int left, int bottom, int right, int top, Side gate, int axis,
                           FortressProgram program, Plan plan) {
        Frame frame = new Frame(left, bottom, right, top, gate);
        int entry = frame.swaps() ? axis - bottom : axis - left;
        int a = frame.across() / 2;
        Cantonment c = new Cantonment(buildable, frame, a);
        if (Math.abs(entry - a) > AVENUE_HALF) c.forecourt(entry);
        Order order = Order.of(program);
        for (FortressBuilding b : program.buildings()) c.wanted.putIfAbsent(b.purpose(), new Want(b.purpose(), b.shape()));
        switch (plan) {
            case PARADE -> c.parade(order);
            case QUADRANTS -> c.quadrants(order);
            case QUADRANGLE -> c.quadrangle(order);
        }
        c.seatLeftovers();
        return c.result();
    }

    /**
     * The smallest square side whose open ground seats the whole program in this plan. The flight line is not
     * counted: it takes the depth the rows leave, as a packed garrison's lot takes the ground its buildings leave.
     */
    public static int side(FortressProgram program, Plan plan) {
        for (int s = 40; s < 240; s += 2) {
            boolean[][] open = new boolean[s][s];
            for (boolean[] col : open) Arrays.fill(col, true);
            Laid laid = lay(open, 0, 0, s - 1, s - 1, Side.SOUTH, s / 2, program, plan);
            if (laid.unplaced().isEmpty()) return s;
        }
        return 240;
    }

    /** Weight per cell of a road's width in choosing the gate: the town's arterial before a side street. UNTUNED. */
    private static final double WIDE_ROAD = 0.15;

    /** Where the main gate is: the side of the place it is in, and the avenue's centre across that side (map cells). */
    public record Gate(Side side, int axis) { }

    /**
     * The main gate: where a road the town grew comes in through the place's edge, the widest road first, on the side
     * that faces the middle of the map (where the rest of the site is), nearest that side's middle. A road leaving off the map's edge is a
     * gate too, but the last choice. With no road in at all, the side facing the map's middle, at its middle.
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
                        - 0.5 * Math.abs(mid - (lo + hi) / 2.0) / half - (offMap ? 3 : 0);
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
     * it enters to the avenue, and the rows start behind it.
     */
    private void forecourt(int entry) {
        int lo = Math.max(1, Math.min(entry, axis) - AVENUE_HALF);
        int hi = Math.min(frame.across() - 1, Math.max(entry, axis) + AVENUE_HALF + 1);
        streets.add(new Rect(lo, 0, hi - lo, FORECOURT));
        front = FORECOURT + ALLEY;
    }

    /** Depth of the street along the gate wall where the road in is off the avenue's line. */
    static final int FORECOURT = 6;

    // ---- The three plans ---------------------------------------------------------------------------------------

    /**
     * Avenue from the gate to the HQ. Gate posts either side of the gate, a service row (motor pool, workshops), the
     * barrack rows facing each other across the avenue, the parade ground the avenue opens into, and the HQ row
     * across its far side with the depot either hand of the HQ. A flight line, where one is owed, stands behind.
     */
    private void parade(Order o) {
        int across = frame.across(), depth = frame.depth();
        int inLo = MARGIN, inHi = across - MARGIN;          // footprint span across
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        int v = front;
        // Gate row: the posts hug the avenue, the gatehouse beside the left post.
        int gateDeep = 0;
        List<Want> gateLeft = new ArrayList<>(), gateRight = new ArrayList<>();
        if (o.gatePosts >= 1) gateLeft.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        if (o.gatePosts >= 2) gateRight.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        if (o.gatehouse != null) gateRight.add(o.gatehouse);
        gateDeep = Math.max(row(gateLeft, av0 - ALLEY, -1, v, false, Face.RIGHT, inLo, inHi),
                row(gateRight, av1 + ALLEY, +1, v, false, Face.LEFT, inLo, inHi));
        if (gateDeep > 0) v += gateDeep + STREET;
        // Service row: the motor pool's shed opens on the avenue, workshops beyond it.
        List<Want> sl = new ArrayList<>(), sr = new ArrayList<>();
        split(o.service, sl, sr);
        if (o.mess != null) sr.add(o.mess);
        int serviceDeep = Math.max(row(sl, av0 - ALLEY, -1, v, false, Face.RIGHT, inLo, inHi),
                row(sr, av1 + ALLEY, +1, v, false, Face.LEFT, inLo, inHi));
        if (serviceDeep > 0) v += serviceDeep + STREET;
        // The far end, laid from the back: flight line, then the HQ row.
        int back = depth - MARGIN;
        Want hq = o.hq;
        int hqDeep = hq == null ? 0 : hq.shape.height() + 2;
        for (Want w : o.depot) hqDeep = Math.max(hqDeep, w.shape.height() + 2);
        int barrackRows = rowsFor(o.barracks.size(), sideCapacity(o, inLo, inHi));
        int barrackDeep = barrackRows * (8 + STREET);
        int spare = back - v - barrackDeep - hqDeep - STREET - PARADE_MIN;
        if (o.airfields > 0) {
            int[] lot = flightLine(inLo, inHi, back, spare);
            if (lot != null) back = lot[1] - STREET;
        }
        int hqV = back - hqDeep;
        if (hq != null) {
            int u = axis - (hq.shape.width() + 2) / 2;
            stand(hq, false, u, hqV, List.of(Face.FRONT));
        }
        int hqLeft = hq == null ? av0 : axis - (hq.shape.width() + 2) / 2;
        int hqRight = hq == null ? av1 : hqLeft + hq.shape.width() + 2;
        List<Want> dl = new ArrayList<>(), dr = new ArrayList<>();
        split(o.depot, dl, dr);
        row(dl, hqLeft - ALLEY, -1, hqV, false, Face.FRONT, inLo, inHi);
        row(dr, hqRight + ALLEY, +1, hqV, false, Face.FRONT, inLo, inHi);
        // Barrack rows, each facing the avenue, then the parade ground up to the HQ row.
        List<Want> bl = new ArrayList<>(), br = new ArrayList<>();
        split(o.barracks, bl, br);
        v = barrackRows(bl, br, v, hqV - STREET - PARADE_MIN, inLo, inHi);
        int paradeEnd = hqV - STREET;
        if (paradeEnd > v) {
            int half = Math.min(PARADE_MAX_ACROSS / 2, Math.min(axis - inLo, inHi - axis) - 2);
            parade.add(new Rect(axis - half, v, 2 * half + 1, paradeEnd - v));
        }
        streets.add(new Rect(av0, 0, av1 - av0, Math.max(0, Math.min(v, paradeEnd) + 1)));
        towers(o.towers, inLo, inHi, front, depth - MARGIN);
    }

    /**
     * Two avenues crossing in the middle, a function to each quarter: the gate posts and motor pool in the near-left,
     * the barracks near-right, the depot far-left, and the HQ with its parade ground far-right.
     */
    private void quadrants(Order o) {
        int across = frame.across(), depth = frame.depth();
        int inLo = MARGIN, inHi = across - MARGIN;
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        int[] grown = crossRoad(depth / 3, 2 * depth / 3);
        int cr0, cr1;
        if (grown != null) {
            cr0 = grown[0];
            cr1 = grown[1];
        } else {
            int mid = depth / 2;
            cr0 = mid - AVENUE_HALF;
            cr1 = mid + AVENUE_HALF + 1;
            streets.add(new Rect(MARGIN - 1, cr0, across - 2 * (MARGIN - 1), cr1 - cr0));
        }
        streets.add(new Rect(av0, 0, av1 - av0, depth));
        // Near-left: gate post, then the motor pool rows from the crossing back to the gate.
        int v = front;
        List<Want> nearLeft = new ArrayList<>();
        if (o.gatePosts >= 1) nearLeft.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        if (o.gatehouse != null) nearLeft.add(o.gatehouse);
        int d = row(nearLeft, av0 - ALLEY, -1, v, false, Face.RIGHT, inLo, inHi);
        if (d > 0) v += d + STREET;
        fillRows(o.service, av0 - ALLEY, -1, v, cr0 - ALLEY, Face.RIGHT, inLo, inHi);
        // Near-right: gate post, then barracks.
        v = front;
        List<Want> nearRight = new ArrayList<>();
        if (o.gatePosts >= 2) nearRight.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        d = row(nearRight, av1 + ALLEY, +1, v, false, Face.LEFT, inLo, inHi);
        if (d > 0) v += d + STREET;
        List<Want> men = new ArrayList<>(o.barracks);
        fillRows(men, av1 + ALLEY, +1, v, cr0 - ALLEY, Face.LEFT, inLo, inHi);
        // Far-left: the depot.
        fillRows(o.depot, av0 - ALLEY, -1, cr1 + ALLEY, depth - MARGIN, Face.RIGHT, inLo, inHi);
        // Far-right: the HQ at the crossing's corner, the mess beside it, the parade ground behind.
        v = cr1 + ALLEY;
        List<Want> hqRow = new ArrayList<>();
        if (o.hq != null) hqRow.add(o.hq);
        if (o.mess != null) hqRow.add(o.mess);
        d = row(hqRow, av1 + ALLEY, +1, v, false, Face.FRONT, inLo, inHi);
        int pv = v + d + STREET;
        int pEnd = depth - MARGIN;
        if (o.airfields > 0) {
            int[] lot = flightLineIn(av1 + ALLEY, inHi, pv, pEnd);
            if (lot != null) pEnd = lot[1] - STREET;
        }
        if (pEnd - pv >= PARADE_MIN) {
            parade.add(new Rect(av1 + ALLEY, pv, Math.min(PARADE_MAX_ACROSS, inHi - av1 - ALLEY), pEnd - pv));
        }
        if (o.airfields > 0 && airfield == null) flightLineIn(inLo, av0 - ALLEY, cr1 + ALLEY, depth - MARGIN);
        towers(o.towers, inLo, inHi, front, depth - MARGIN);
    }

    /**
     * A ring road round one central parade ground and every building facing it: the motor pool and gate posts on
     * the gate side, barracks down both flanks, the HQ across the far side with the depot either hand of it.
     */
    private void quadrangle(Order o) {
        int across = frame.across(), depth = frame.depth();
        int inLo = MARGIN, inHi = across - MARGIN;
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        // Gate side.
        int v = front;
        List<Want> gl = new ArrayList<>(), gr = new ArrayList<>();
        if (o.gatePosts >= 1) gl.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        if (o.gatePosts >= 2) gr.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        List<Want> sl = new ArrayList<>(), sr = new ArrayList<>();
        split(o.service, sl, sr);
        gl.addAll(sl);
        if (o.gatehouse != null) gr.add(o.gatehouse);
        gr.addAll(sr);
        int nearDeep = Math.max(row(gl, av0 - ALLEY, -1, v, false, Face.BACK, inLo, inHi),
                row(gr, av1 + ALLEY, +1, v, false, Face.BACK, inLo, inHi));
        int ringNear = v + nearDeep;
        // Far side, from the back.
        int back = depth - MARGIN;
        if (o.airfields > 0) {
            int[] lot = flightLine(inLo, inHi, back, back - ringNear - 40);
            if (lot != null) back = lot[1] - STREET;
        }
        int farDeep = o.hq == null ? 0 : o.hq.shape.height() + 2;
        for (Want w : o.depot) farDeep = Math.max(farDeep, w.shape.height() + 2);
        int farV = back - farDeep;
        int hqLeft = av0, hqRight = av1;
        if (o.hq != null) {
            hqLeft = axis - (o.hq.shape.width() + 2) / 2;
            hqRight = hqLeft + o.hq.shape.width() + 2;
            stand(o.hq, false, hqLeft, back - (o.hq.shape.height() + 2), List.of(Face.FRONT));
        }
        List<Want> dl = new ArrayList<>(), dr = new ArrayList<>();
        split(o.depot, dl, dr);
        row(dl, hqLeft - ALLEY, -1, farV, true, Face.FRONT, inLo, inHi);
        row(dr, hqRight + ALLEY, +1, farV, true, Face.FRONT, inLo, inHi);
        // Flanks: barracks turned along the depth, facing in, between the near and far rows.
        int flankLo = ringNear + STREET, flankHi = farV - STREET;
        List<Want> bl = new ArrayList<>(), br = new ArrayList<>();
        List<Want> men = new ArrayList<>(o.barracks);
        if (o.mess != null) men.add(o.mess);
        split(men, bl, br);
        int leftIn = column(bl, inLo, flankLo, flankHi, Face.RIGHT, false);
        int rightIn = column(br, inHi, flankLo, flankHi, Face.LEFT, true);
        // The parade ground inside the ring road.
        int p0 = leftIn + STREET, p1 = rightIn - STREET;
        if (p1 - p0 >= PARADE_MIN && flankHi - flankLo >= PARADE_MIN) {
            parade.add(new Rect(p0, flankLo, p1 - p0, flankHi - flankLo));
        }
        streets.add(new Rect(av0, 0, av1 - av0, flankLo));
        towers(o.towers, inLo, inHi, front, depth - MARGIN);
    }

    /**
     * A road the town grew straight across the place between depths {@code v0} and {@code v1}, as {@code {first,
     * end}} depths, or {@code null}: the quarters cross on it rather than on a second avenue beside it.
     */
    private int[] crossRoad(int v0, int v1) {
        int span = frame.across() - 2 * MARGIN;
        int start = -1;
        int[] best = null;
        for (int v = v0; v <= v1 + 1; v++) {
            boolean across = false;
            if (v <= v1) {
                int closed = 0;
                for (int u = MARGIN; u < frame.across() - MARGIN; u++) {
                    int[] c = frame.cell(u, v);
                    if (c[0] >= 0 && c[1] >= 0 && c[0] < buildable.length && c[1] < buildable[0].length
                            && !buildable[c[0]][c[1]]) closed++;
                }
                across = closed * 3 >= span * 2;
            }
            if (across && start < 0) start = v;
            if (!across && start >= 0) {
                if (best == null || v - start > best[1] - best[0]) best = new int[]{start, v};
                start = -1;
            }
        }
        return best;
    }

    // ---- Rows ------------------------------------------------------------------------------------------------

    /** Splits a list between the two sides of the avenue, alternating, so both sides fill evenly. */
    private static void split(List<Want> wants, List<Want> left, List<Want> right) {
        for (int i = 0; i < wants.size(); i++) (i % 2 == 0 ? left : right).add(wants.get(i));
    }

    /** How many barracks one side of the avenue takes in one row. */
    private int sideCapacity(Order o, int inLo, int inHi) {
        int half = Math.min(axis - AVENUE_HALF - ALLEY - inLo, inHi - axis - AVENUE_HALF - 1 - ALLEY);
        int block = 18 + ALLEY;
        return Math.max(1, (half + ALLEY) / block);
    }

    private static int rowsFor(int count, int perSide) {
        return (count + 2 * perSide - 1) / (2 * perSide);
    }

    /** Lays both sides' barracks in rows from {@code v}, no deeper than {@code limit}; returns the next free depth. */
    private int barrackRows(List<Want> left, List<Want> right, int v, int limit, int inLo, int inHi) {
        int li = 0, ri = 0;
        while ((li < left.size() || ri < right.size()) && v + 8 <= limit) {
            int dl = 0, dr = 0;
            int startL = li, startR = ri;
            int uL = axis - AVENUE_HALF - ALLEY, uR = axis + AVENUE_HALF + 1 + ALLEY;
            while (li < left.size()) {
                Stood s = slide(left.get(li), false, uL, -1, v, Face.RIGHT, inLo, inHi, true);
                if (s == null) break;
                uL = s.u - ALLEY;
                dl = Math.max(dl, s.deep());
                li++;
            }
            while (ri < right.size()) {
                Stood s = slide(right.get(ri), false, uR, +1, v, Face.LEFT, inLo, inHi, true);
                if (s == null) break;
                uR = s.u + s.across() + ALLEY;
                dr = Math.max(dr, s.deep());
                ri++;
            }
            if (li == startL && ri == startR) {
                v++;                                   // a road across the band: step past it
                continue;
            }
            v += Math.max(dl, dr) + STREET;
        }
        for (; li < left.size(); li++) unplaced.add(left.get(li).purpose);
        for (; ri < right.size(); ri++) unplaced.add(right.get(ri).purpose);
        return v;
    }

    /**
     * Lays {@code wants} in one row outward from {@code u} (in direction {@code dir}) at depth {@code v}, aligned on
     * the row's near edge or, with {@code alignBack}, its far edge; returns the row's depth. A building the ground
     * cannot seat is recorded unplaced.
     */
    private int row(List<Want> wants, int u, int dir, int v, boolean alignBack, Face door, int inLo, int inHi) {
        int deep = 0;
        for (Want w : wants) deep = Math.max(deep, w.shape.height() + 2);
        for (Want w : wants) {
            int wv = alignBack ? v + deep - (w.shape.height() + 2) : v;
            Stood s = slide(w, false, u, dir, wv, door, inLo, inHi, false);
            if (s == null) {
                unplaced.add(w.purpose);
                continue;
            }
            u = dir < 0 ? s.u - ALLEY : s.u + s.across() + ALLEY;
        }
        return wants.isEmpty() ? 0 : deep;
    }

    /** Lays {@code wants} in as many rows as it takes between depths {@code v} and {@code limit}. */
    private void fillRows(List<Want> wants, int u0, int dir, int v, int limit, Face door, int inLo, int inHi) {
        int i = 0;
        while (i < wants.size() && v < limit) {
            int u = u0, deep = 0, start = i;
            while (i < wants.size()) {
                Want w = wants.get(i);
                if (v + w.shape.height() + 2 > limit) break;
                Stood s = slide(w, false, u, dir, v, door, inLo, inHi, true);
                if (s == null) break;
                u = dir < 0 ? s.u - ALLEY : s.u + s.across() + ALLEY;
                deep = Math.max(deep, s.deep());
                i++;
            }
            if (i == start) {
                v++;
                continue;
            }
            v += deep + STREET;
        }
        for (; i < wants.size(); i++) unplaced.add(wants.get(i).purpose);
    }

    /**
     * Stands {@code wants} turned along the depth in a column against one side, from {@code v0} to {@code v1};
     * returns the column's inner edge (the across-position the parade ground starts from).
     */
    private int column(List<Want> wants, int edge, int v0, int v1, Face door, boolean fromRight) {
        int inner = edge;
        int v = v0;
        for (Want w : wants) {
            int across = w.shape.height() + 2, deep = w.shape.width() + 2;
            Stood s = null;
            for (int dv = 0; dv <= SLIDE && s == null && v + dv + deep <= v1; dv++) {
                int u = fromRight ? edge - across : edge;
                if (seats(u, v + dv, across, deep)) s = stand(w, true, u, v + dv, List.of(door));
            }
            if (s == null) {
                unplaced.add(w.purpose);
                continue;
            }
            v = s.v + deep + ALLEY;
            inner = fromRight ? Math.min(inner, s.u) : Math.max(inner, s.u + across);
        }
        return inner;
    }

    /**
     * Stands {@code w} at the first position from {@code u} outward ({@code dir}) its footprint fits, its near edge
     * at {@code u} and running away from the avenue; {@code null} when nothing fits within {@link #SLIDE} or the
     * place's span.
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

    /** Whether a footprint stands on buildable ground clear of every footprint, street and open ground already laid. */
    private boolean seats(int u, int v, int across, int deep) {
        if (u < 0 || v < 0 || u + across > frame.across() || v + deep > frame.depth()) return false;
        Rect r = new Rect(u, v, across, deep);
        for (Rect t : taken) if (t.overlaps(r)) return false;
        for (Rect t : streets) if (t.overlaps(r)) return false;
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

    /** A flight line across the back of the place, the largest lot that leaves {@code spare} depth; {u,v} or null. */
    private int[] flightLine(int inLo, int inHi, int back, int spare) {
        for (AirbaseLot.Size size : new AirbaseLot.Size[]{AirbaseLot.Size.FIELD, AirbaseLot.Size.PAD,
                AirbaseLot.Size.STRIP}) {
            int[] span = span(size);
            if (span[1] > spare) continue;
            int u = axis - span[0] / 2;
            int v = back - span[1];
            if (u < inLo || u + span[0] > inHi) continue;
            if (!seatsLot(u, v, span[0], span[1])) continue;
            airfield = new Rect(u, v, span[0], span[1]);
            airfieldSize = size;
            return new int[]{u, v};
        }
        return null;
    }

    /** A flight line anywhere in the span {@code [u0, u1)} by depths {@code [v0, v1)}, nearest the back. */
    private int[] flightLineIn(int u0, int u1, int v0, int v1) {
        for (AirbaseLot.Size size : new AirbaseLot.Size[]{AirbaseLot.Size.FIELD, AirbaseLot.Size.PAD,
                AirbaseLot.Size.STRIP}) {
            int[] span = span(size);
            for (int v = v1 - span[1]; v >= v0; v--) {
                for (int u = u0; u + span[0] <= u1; u++) {
                    if (!seatsLot(u, v, span[0], span[1])) continue;
                    airfield = new Rect(u, v, span[0], span[1]);
                    airfieldSize = size;
                    return new int[]{u, v};
                }
            }
        }
        return null;
    }

    private boolean seatsLot(int u, int v, int across, int deep) {
        return seats(u, v, across, deep);
    }

    /** A lot's span in the frame, {across, deep}, clearance included. */
    private int[] span(AirbaseLot.Size size) {
        int x = AirbaseLot.reservedSpanX(size, facing), y = AirbaseLot.reservedSpanY(size, facing);
        return frame.swaps() ? new int[]{y, x} : new int[]{x, y};
    }

    /**
     * Seats what the rows could not, where a road the town grew or the map's edge took their place: each at the free
     * position nearest the gate, square to the rows, its door toward the gate. Still squared, never lost.
     */
    private void seatLeftovers() {
        List<RoomPurpose> left = new ArrayList<>(unplaced);
        unplaced.clear();
        List<Want> wants = new ArrayList<>();
        for (RoomPurpose purpose : left) wants.add(leftover(purpose));
        for (Want w : wants) {
            Stood s = null;
            int across = w.shape.width() + 2, deep = w.shape.height() + 2;
            for (int v = MARGIN; v + deep <= frame.depth() - MARGIN + 1 && s == null; v++) {
                for (int u = MARGIN; u + across <= frame.across() - MARGIN + 1 && s == null; u++) {
                    if (seats(u - 1, v - 1, across + 2, deep + 2)) s = stand(w, false, u, v, List.of(Face.FRONT));
                }
            }
            if (s == null) unplaced.add(w.purpose);
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
        List<int[]> streetCells = new ArrayList<>(), paradeCells = new ArrayList<>();
        for (Rect r : streets) cells(r, streetCells);
        for (Rect r : parade) cells(r, paradeCells);
        int[] lot = null;
        if (airfield != null) {
            int[] a = frame.cell(airfield.u, airfield.v);
            int[] b = frame.cell(airfield.u + airfield.across - 1, airfield.v + airfield.deep - 1);
            lot = new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
        }
        return new Laid(rooms, streetCells, paradeCells, lot, airfieldSize, facing, unplaced);
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
