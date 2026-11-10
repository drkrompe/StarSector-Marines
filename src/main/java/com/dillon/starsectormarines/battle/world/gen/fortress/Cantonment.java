package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A garrison laid out as a planned installation ({@code raid-mission.md}, Garrison district), in three bands across its
 * square, each read by what it does, and laid out from its logistics outward (the owner's picks {@code raid-garrison}
 * and {@code raid-garrison-districts}):
 *
 * <ul>
 *   <li><b>Logistics</b> at the gate. Along the inside of the gate wall runs the front lane. The motor yard is on the
 *       avenue: the vehicle sheds either side of it, their doors on the hardstand where vehicles park. To the wider
 *       hand is the depot, its warehouses' loading doors on an unloading apron that opens on the first cross street.
 *       To the other hand are the workshops, the generator and the vehicle fuel point.</li>
 *   <li><b>The garrison</b> in the middle: a line of barracks and a mess along each side wall, each on its own street,
 *       and the parade ground either side of the avenue between them.</li>
 *   <li><b>Operations</b> at the back, on the second cross street: the flight line in the middle with the avenue
 *       running on to its apron, the headquarters to one side, and to the other the armoury's magazines kept apart
 *       with the flight line's own fuel point.</li>
 * </ul>
 *
 * <p>A supply truck's round ({@link Laid#truckRoute}) runs from the gate up the avenue to the depot's apron, then to
 * the fuel point, to the yard to park, and out of the gate again. The flight line is supplied on its own round
 * ({@link Laid#flightRoute}) along the second cross street. Every gate's road inside the wall joins the streets
 * ({@link #gateLanes}).
 *
 * <p>The layout is drawn in the place's own frame, with the gate at the near edge: {@code u} runs across the place and
 * {@code v} runs in from the gate. Every building is placed on a footprint (its floor and the wall ring round it) the
 * ground can take whole, so ground the place cannot build on is stepped round, never built over.
 *
 * <p>Pure: it reads a buildable mask and returns placements, streets, paving, open ground and routes, so it is decided
 * here and tested here; {@code PrecinctWardStage} authors and dresses what it returns.
 */
public final class Cantonment {

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
    /** Depth of the depot's unloading apron behind its warehouses: a truck's length and room to turn. UNTUNED. */
    static final int APRON = 10;
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
    /** Width of a cross street between two bands, the front lane, and a band's own street. UNTUNED. */
    static final int CROSS = 4;
    /** Clear ground between two magazines, and between a magazine and any other building. */
    static final int DISPERSE = 5;

    /** One building of the order, as the program states its floor. */
    record Want(RoomPurpose purpose, RoomShape shape) { }

    /** What a garrison of this program owes, sorted into the roles the layout is made of. */
    record Order(List<Want> barracks, List<Want> sheds, List<Want> warehouses, List<Want> workshops,
                 List<Want> magazines, List<Want> fuel, Want hq, Want gatehouse, List<Want> messes, int gatePosts,
                 int towers, AirbaseLot.Size field) {

        static Order of(FortressProgram program) {
            List<Want> barracks = new ArrayList<>(), sheds = new ArrayList<>(), warehouses = new ArrayList<>();
            List<Want> workshops = new ArrayList<>(), magazines = new ArrayList<>(), fuel = new ArrayList<>();
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
                        case ARMORY -> magazines.add(w);
                        case STOCKROOM -> (b.shape().equals(FortressProgram.FUEL_POINT) ? fuel : warehouses).add(w);
                        default -> workshops.add(w);
                    }
                }
            }
            int gatePosts = Math.min(2, posts);
            return new Order(barracks, sheds, warehouses, workshops, magazines, fuel, hq, gatehouse, messes,
                    gatePosts, Math.min(4, posts - gatePosts),
                    program.airfields() > 0 ? fieldFor(barracks.size()) : null);
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

        /** The frame cell just outside the middle of its first door's face. */
        int[] doorstep() {
            return switch (doors.get(0)) {
                case FRONT -> new int[]{u + across() / 2, v - 1};
                case BACK -> new int[]{u + across() / 2, v + deep()};
                case LEFT -> new int[]{u - 1, v + deep() / 2};
                case RIGHT -> new int[]{u + across(), v + deep() / 2};
            };
        }
    }

    /** A rectangle of the place's own frame, inclusive of its near corner and exclusive of its far one. */
    record Rect(int u, int v, int across, int deep) {
        boolean overlaps(Rect o) {
            return u < o.u + o.across && o.u < u + across && v < o.v + o.deep && o.v < v + deep;
        }

        boolean contains(int cu, int cv) {
            return cu >= u && cu < u + across && cv >= v && cv < v + deep;
        }

        int[] middle() {
            return new int[]{u + across / 2, v + deep / 2};
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

        /** The frame cell of map cell {@code (x, y)}. */
        int[] frameOf(int x, int y) {
            return switch (gate) {
                case WEST -> new int[]{y - bottom, x - left};
                case EAST -> new int[]{y - bottom, right - x};
                case SOUTH -> new int[]{x - left, y - bottom};
                case NORTH -> new int[]{x - left, top - y};
            };
        }
    }

    /**
     * What the layout came to, in map cells: rooms; streets (the avenue, the front lane, the cross streets, the band
     * streets and every gate's lane); the motor yard's hardstand; the depot's unloading apron; the parade ground; the
     * flight line's lot; the main gate's cell on the avenue just inside the wall; a supply truck's round and the flight
     * line's supply round, each a path of cells on the paving; what had to be seated wherever there was room
     * ({@code strays}); and anything it could not seat at all.
     */
    public record Laid(List<RoomPacker.Placed> rooms, List<int[]> streets, List<int[]> pool, List<int[]> apron,
                       List<int[]> parade, int[] airfield, AirbaseLot.Size airfieldSize, AirbaseLot.Facing facing,
                       int[] gate, List<int[]> truckRoute, List<int[]> flightRoute, List<RoomPurpose> strays,
                       List<RoomPurpose> unplaced) {
        public Laid {
            rooms = List.copyOf(rooms);
            streets = List.copyOf(streets);
            pool = List.copyOf(pool);
            apron = List.copyOf(apron);
            parade = List.copyOf(parade);
            truckRoute = List.copyOf(truckRoute);
            flightRoute = List.copyOf(flightRoute);
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
    private final List<Rect> apron = new ArrayList<>();
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
    /** Depth added to the motor yard's band and to the parade ground, from what the square leaves spare. */
    private int yardExtra, paradeExtra;
    /** The stops of a supply truck's round and of the flight line's, in the frame, in order. */
    private final List<int[]> truckStops = new ArrayList<>();
    private final List<int[]> flightStops = new ArrayList<>();

    private Cantonment(boolean[][] buildable, Frame frame, int axis) {
        this.buildable = buildable;
        this.frame = frame;
        this.axis = axis;
        this.entry = axis;
    }

    /** Lays a garrison's program with only its main gate: {@link #lay(boolean[][], int, int, int, int, Side, int, FortressProgram, List)}. */
    public static Laid lay(boolean[][] buildable, int left, int bottom, int right, int top, Side gate, int axis,
                           FortressProgram program) {
        return lay(buildable, left, bottom, right, top, gate, axis, program, List.of());
    }

    /**
     * Lays a garrison's program.
     *
     * @param buildable the ground the place may build on, map-sized: its own claim inside the glacis, less roads
     * @param left      the place's square, map cells inclusive
     * @param gate      the side the main gate is on
     * @param axis      where the road comes in, as a map coordinate across the gate side
     * @param further   the map cells where its further gates' roads come through the wall, each joined to the streets
     */
    public static Laid lay(boolean[][] buildable, int left, int bottom, int right, int top, Side gate, int axis,
                           FortressProgram program, List<int[]> further) {
        Frame frame = new Frame(left, bottom, right, top, gate);
        int entry = frame.swaps() ? axis - bottom : axis - left;
        Cantonment c = laid(buildable, frame, entry, program, 0, 0);
        // The square is as deep as it is across, and the bands rarely need all of it: what is left goes to the open
        // ground that wants it (a third to the motor yard's hardstand and the depot's apron, the rest to the parade
        // ground), rather than lying empty behind the flight line. Kept only where it seats as much as before;
        // Halved until it does.
        for (int give = c.slack(); give > 1; give /= 2) {
            Cantonment d = laid(buildable, frame, entry, program, give / 3, give - give / 3);
            if (d.unplaced.size() + d.strays.size() <= c.unplaced.size() + c.strays.size()
                    && d.airfieldSize == c.airfieldSize) {
                c = d;
                break;
            }
        }
        c.gateLanes(further);
        return c.result();
    }

    private static Cantonment laid(boolean[][] buildable, Frame frame, int entry, FortressProgram program,
                                   int yardExtra, int paradeExtra) {
        Cantonment c = new Cantonment(buildable, frame, frame.across() / 2);
        c.yardExtra = yardExtra;
        c.paradeExtra = paradeExtra;
        if (Math.abs(entry - c.axis) > AVENUE_HALF) c.forecourt(entry);
        Order order = Order.of(program);
        for (FortressBuilding b : program.buildings()) c.wanted.putIfAbsent(b.purpose(), new Want(b.purpose(), b.shape()));
        c.bands(order);
        c.seatLeftovers();
        return c;
    }

    /**
     * Depth left behind the deepest building, open ground or flight line, inside the patrol lane; the corner towers,
     * which stand against the back wall whatever the bands need, do not count.
     */
    private int slack() {
        int used = 0;
        for (Stood t : stood) if (t.want.purpose != RoomPurpose.CONTROL_ROOM) used = Math.max(used, t.v + t.deep());
        for (List<Rect> rs : List.of(pool, apron, parade)) for (Rect r : rs) used = Math.max(used, r.v + r.deep);
        if (airfield != null) used = Math.max(used, airfield.v + airfield.deep);
        return depth() - MARGIN - used;
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

    /** Where a gate is: the side of the place it is in, and the road's centre across that side (map cells). */
    public record Gate(Side side, int axis) {
        /** The map cell where this gate's road crosses the place's edge. */
        public int[] cell(int left, int bottom, int right, int top) {
            return switch (side) {
                case NORTH -> new int[]{axis, top};
                case SOUTH -> new int[]{axis, bottom};
                case EAST -> new int[]{right, axis};
                case WEST -> new int[]{left, axis};
            };
        }
    }

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
        for (int[] run : entries(own, road, left, bottom, right, top)) {
            Side side = Side.values()[run[0]];
            int mid = run[1], width = run[2];
            boolean alongX = side == Side.NORTH || side == Side.SOUTH;
            int lo = alongX ? left : bottom, hi = alongX ? right : top;
            int fixed = switch (side) { case NORTH -> top; case SOUTH -> bottom; case EAST -> right; case WEST -> left; };
            int ox = (alongX ? mid : fixed) + (alongX ? 0 : side.dx), oy = (alongX ? fixed : mid) + (alongX ? side.dy : 0);
            boolean offMap = ox < 0 || oy < 0 || ox >= w || oy >= h;
            double half = Math.max(1, (hi - lo) / 2.0);
            double score = WIDE_ROAD * Math.min(width, 9) + (side.dx * tx + side.dy * ty) / norm
                    - 0.5 * Math.abs(mid - (lo + hi) / 2.0) / half - (offMap ? OFF_MAP : 0);
            if (score > bestScore) {
                bestScore = score;
                best = new Gate(side, mid);
            }
        }
        if (best != null) return best;
        Side side = Side.facing((int) Math.round(tx), (int) Math.round(ty));
        return new Gate(side, side == Side.NORTH || side == Side.SOUTH ? (left + right) / 2 : (bottom + top) / 2);
    }

    /**
     * Every gate: each run of road crossing the place's edge, other than {@code main}. A further gate's road comes in
     * across the glacis ({@code CantonmentRoads}) and the layout joins it to its streets.
     */
    public static List<Gate> further(boolean[][] own, boolean[][] road, int left, int bottom, int right, int top,
                                     Gate main) {
        List<Gate> out = new ArrayList<>();
        for (int[] run : entries(own, road, left, bottom, right, top)) {
            Side side = Side.values()[run[0]];
            if (side == main.side() && Math.abs(run[1] - main.axis()) <= run[2]) continue;
            out.add(new Gate(side, run[1]));
        }
        return out;
    }

    /** Each run of road on the place's edge: {@code {side ordinal, centre, width}}. */
    private static List<int[]> entries(boolean[][] own, boolean[][] road, int left, int bottom, int right, int top) {
        int w = own.length, h = own[0].length;
        List<int[]> out = new ArrayList<>();
        for (Side side : Side.values()) {
            boolean alongX = side == Side.NORTH || side == Side.SOUTH;
            int fixed = switch (side) { case NORTH -> top; case SOUTH -> bottom; case EAST -> right; case WEST -> left; };
            int lo = alongX ? left : bottom, hi = alongX ? right : top;
            int runStart = -1;
            for (int k = lo; k <= hi + 1; k++) {
                int x = alongX ? k : fixed, y = alongX ? fixed : k;
                boolean on = k <= hi && x >= 0 && y >= 0 && x < w && y < h && own[x][y] && road[x][y];
                if (on && runStart < 0) runStart = k;
                if (on || runStart < 0) continue;
                out.add(new int[]{side.ordinal(), (runStart + k - 1) / 2, k - runStart});
                runStart = -1;
            }
        }
        return out;
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

    // ---- The layout ------------------------------------------------------------------------------------------

    private void bands(Order o) {
        int inLo = MARGIN, inHi = frame.across() - MARGIN, back = depth() - MARGIN;
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        int v = front;
        int gate = gateRow(o, v, inLo, inHi);
        towers(o.towers, inLo, inHi, front, back);
        v += gate;
        // The front lane along the inside of the gate wall, behind the gate row, from wall to wall across the avenue.
        streets.add(new Rect(inLo, v, inHi - inLo, CROSS));
        v += CROSS;

        // ---- Logistics: the motor yard on the avenue; to the wider hand the depot, its warehouses down the wall with
        // their loading doors on an unloading apron that runs the band's depth beside them; to the other the vehicle
        // fuel point and the workshops down their own service street. Apron and street both run from the front lane
        // to the cross street, so a truck drives through and never has to turn back.
        int[] yard = sheds(o, v, inLo, inHi);
        int lHi = yard[1] - ALLEY, rLo = yard[2] + ALLEY;
        boolean depotLeft = lHi - inLo >= inHi - rLo;
        List<Want> shops = new ArrayList<>();
        if (!o.fuel.isEmpty()) shops.add(o.fuel.get(0));
        shops.addAll(of(o.workshops, RoomPurpose.PARTS_CAGE));
        int fuelIndex = stood.size();
        // Depot: the apron against the yard, the warehouses between it and the wall. The workshops on the other hand,
        // their street against the yard; where that hand is too narrow for them (one shed leaves it so), they stand
        // on the depot's hand behind the warehouses, their street between.
        int depotW = maxAcross(o.warehouses) + APRON, shopW = maxAcross(shops) + CROSS;
        int otherW = depotLeft ? inHi - rLo : lHi - inLo;
        boolean together = otherW < shopW;
        int dir = depotLeft ? -1 : +1;
        int aU = depotLeft ? lHi - APRON : rLo;
        int depotEnd = stack(o.warehouses, false, depotLeft ? aU - 1 : aU + APRON, dir, v,
                Integer.MAX_VALUE, depotLeft ? Face.RIGHT : Face.LEFT, ALLEY, inLo, inHi);
        int sU;
        int shopEnd;
        if (together) {
            // [yard | apron | warehouses | street | workshops | wall], mirrored on the left.
            int behind = depotLeft ? aU - maxAcross(o.warehouses) - ALLEY : aU + depotW + ALLEY;
            sU = depotLeft ? behind - CROSS : behind;
            shopEnd = stack(shops, false, depotLeft ? sU - 1 : sU + CROSS, dir, v, Integer.MAX_VALUE,
                    depotLeft ? Face.RIGHT : Face.LEFT, ALLEY, inLo, inHi);
        } else {
            sU = depotLeft ? rLo : lHi - CROSS;
            shopEnd = stack(shops, false, depotLeft ? sU + CROSS : sU - 1, -dir, v, Integer.MAX_VALUE,
                    depotLeft ? Face.LEFT : Face.RIGHT, ALLEY, inLo, inHi);
        }
        int c1 = Math.max(yard[0], Math.max(depotEnd, shopEnd) - ALLEY) + yardExtra;
        Rect unload = new Rect(aU, v, APRON, c1 - v);
        apron.add(unload);
        streets.add(new Rect(sU, v, CROSS, c1 - v));
        pool.add(new Rect(av0 - FLARE, v, av1 - av0 + 2 * FLARE, c1 - v));
        streets.add(new Rect(av0, 0, av1 - av0, c1));
        streets.add(new Rect(inLo, c1, inHi - inLo, CROSS));
        List<Want> over = new ArrayList<>();
        Stood fuel = null;
        for (int i = fuelIndex; i < stood.size(); i++) {
            if (!o.fuel.isEmpty() && stood.get(i).want == o.fuel.get(0)) fuel = stood.get(i);
        }
        int[] gateCell = {entry, 1};
        truckStops.add(gateCell);
        truckStops.add(unload.middle());
        if (fuel != null) truckStops.add(fuel.doorstep());
        truckStops.add(new Rect(av0 - FLARE, v, av1 - av0 + 2 * FLARE, c1 - v).middle());
        truckStops.add(gateCell);

        // ---- The garrison: a line of barracks and a mess along each side wall on its street, the parade either side
        // of the avenue between them.
        int m0 = c1 + CROSS + ALLEY;
        List<Want> lines = new ArrayList<>(o.barracks);
        lines.addAll(o.messes);
        int col = maxAcross(lines);
        int ls = inLo + col + ALLEY, rs = inHi - col - ALLEY - CROSS;
        List<Want> bl = new ArrayList<>(), br = new ArrayList<>();
        split(lines, bl, br);
        // Each line flush with its street, so every door opens on it.
        int colEnd = Math.max(stack(bl, false, ls - 1, -1, m0, back, Face.RIGHT, ALLEY, inLo, inHi),
                stack(br, false, rs + CROSS, +1, m0, back, Face.LEFT, ALLEY, inLo, inHi)) - ALLEY;
        int c2 = Math.max(colEnd, m0 + PARADE_MIN) + paradeExtra + ALLEY;
        if (av0 - ALLEY > ls + CROSS + ALLEY) {
            parade.add(new Rect(ls + CROSS + ALLEY, m0, av0 - ALLEY - (ls + CROSS + ALLEY), c2 - ALLEY - m0));
        }
        if (rs - ALLEY > av1 + ALLEY) parade.add(new Rect(av1 + ALLEY, m0, rs - ALLEY - (av1 + ALLEY), c2 - ALLEY - m0));
        streets.add(new Rect(av0, c1, av1 - av0, c2 - c1));
        streets.add(new Rect(ls, c1, CROSS, c2 - c1));
        streets.add(new Rect(rs, c1, CROSS, c2 - c1));
        streets.add(new Rect(inLo, c2, inHi - inLo, CROSS));

        // ---- Operations, on the second cross street: the flight line in the middle with the avenue run on to its
        // apron, the headquarters to one hand, the armoury and the flight line's fuel point to the other.
        int b0 = c2 + CROSS;
        if (o.field != null) flightLine(o.field, away(), inLo, inHi, b0, back, false);
        int fLo = airfield == null ? axis : airfield.u, fHi = airfield == null ? axis + 1 : airfield.u + airfield.across;
        if (airfield != null && airfield.v > b0) streets.add(new Rect(av0, b0, av1 - av0, airfield.v - b0));
        boolean hqLeft = fLo - inLo >= inHi - fHi;
        int hqLo = hqLeft ? inLo : fHi + ALLEY, hqHi = hqLeft ? fLo - ALLEY : inHi;
        headquarters(o, b0, hqLo, hqHi, hqLeft);
        // The generator beside command, on the same street: the base's power where it is run from.
        for (Want w : of(o.workshops, RoomPurpose.ENGINE_ROOM)) {
            int across = w.shape.width() + 2, deep = w.shape.height() + 2;
            Stood g = null;
            for (int k = 0; k + across <= hqHi - hqLo && g == null; k++) {
                int u = hqLeft ? hqHi - across - k : hqLo + k;
                if (seats(u, b0, across, deep)) g = stand(w, false, u, b0, List.of(Face.FRONT));
            }
            if (g == null) unplaced.add(w.purpose);
        }
        int aLo = hqLeft ? fHi + ALLEY : inLo, aHi = hqLeft ? inHi : fLo - ALLEY;
        Stood flightFuel = null;
        if (o.fuel.size() > 1) {
            Want w = o.fuel.get(1);
            int across = w.shape.width() + 2, deep = w.shape.height() + 2;
            for (int k = 0; k + across <= aHi - aLo && flightFuel == null; k++) {
                int u = hqLeft ? aLo + k : aHi - across - k;
                if (seats(u, b0, across, deep)) flightFuel = stand(w, false, u, b0, List.of(Face.FRONT));
            }
            if (flightFuel == null) unplaced.add(w.purpose);
        }
        int xLo = hqLeft && flightFuel != null ? flightFuel.u + flightFuel.across() + ALLEY : aLo;
        int xHi = !hqLeft && flightFuel != null ? flightFuel.u - ALLEY : aHi;
        List<Stood> magazines = armoury(o.magazines, xLo, xHi, b0, back);
        for (int i = 2; i < o.fuel.size(); i++) over.add(o.fuel.get(i));
        if (airfield != null) {
            int[] lotFront = {airfield.u + airfield.across / 2, airfield.v};
            for (Stood m : magazines) {
                flightStops.add(m.doorstep());
                flightStops.add(lotFront);
            }
            if (flightFuel != null) {
                flightStops.add(flightFuel.doorstep());
                flightStops.add(lotFront);
            }
        }
        for (Want w : over) unplaced.add(w.purpose);
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
     * The headquarters on the second cross street at depth {@code v}, its door on the street, in {@code [lo, hi)} as
     * near the flight line as the ground takes it ({@code nearHi}: the flight line is past {@code hi}).
     */
    private void headquarters(Order o, int v, int lo, int hi, boolean nearHi) {
        if (o.hq == null) return;
        int across = o.hq.shape.width() + 2, deep = o.hq.shape.height() + 2;
        for (int k = 0; k + across <= hi - lo; k++) {
            int u = nearHi ? hi - across - k : lo + k;
            if (seats(u, v, across, deep)) {
                stand(o.hq, false, u, v, List.of(Face.FRONT));
                return;
            }
        }
        unplaced.add(o.hq.purpose);
    }

    /**
     * Posts either side of the avenue where it comes through the gate, flush with it, and the guardroom beyond the
     * right one opening on the front lane behind; returns the row's depth, or 0 for none.
     */
    private int gateRow(Order o, int v, int inLo, int inHi) {
        int av0 = axis - AVENUE_HALF, av1 = axis + AVENUE_HALF + 1;
        List<Want> left = new ArrayList<>(), right = new ArrayList<>();
        if (o.gatePosts >= 1) left.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        if (o.gatePosts >= 2) right.add(new Want(RoomPurpose.CONTROL_ROOM, POST));
        int deep = Math.max(row(left, av0 - 1, -1, v, Face.RIGHT, inLo, inHi),
                row(right, av1, +1, v, Face.LEFT, inLo, inHi));
        if (o.gatehouse == null) return deep;
        // The guardroom beyond the right post, its door at the back on the front lane.
        int u = av1 + (right.isEmpty() ? 0 : POST.width() + 2 + ALLEY);
        int at = v + Math.max(0, deep - (o.gatehouse.shape.height() + 2));
        return Math.max(deep, at - v + row(List.of(o.gatehouse), u, +1, at, Face.BACK, inLo, inHi));
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
     * building whose band is taken steps back until one seats it. Returns the depth after the last one and its gap;
     * what cannot stand is recorded unplaced.
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

    /**
     * The armoury in {@code [u0, u1)} from the street at {@code v0} to {@code v1}: an ammunition lane off the street
     * down the middle, the magazines either side of it with their doors on it, each {@link #DISPERSE} clear of every
     * other building, so one going up takes no other; the lane runs as far as the last of them. What cannot stand is
     * recorded unplaced; returns those stood.
     */
    private List<Stood> armoury(List<Want> magazines, int u0, int u1, int v0, int v1) {
        List<Stood> out = new ArrayList<>();
        if (magazines.isEmpty()) return out;
        int lane = (u0 + u1) / 2 - CROSS / 2;
        Rect full = new Rect(lane, v0, CROSS, v1 - v0);
        streets.add(full);
        int end = v0;
        for (int i = 0; i < magazines.size(); i++) {
            Want w = magazines.get(i);
            int across = w.shape.width() + 2, deep = w.shape.height() + 2;
            Stood s = null;
            for (int v = v0; v + deep <= v1 && s == null; v++) {
                for (int side = 0; side < 2 && s == null; side++) {
                    boolean left = (i + side) % 2 == 0;
                    int u = left ? lane - across : lane + CROSS;
                    if (u < u0 || u + across > u1) continue;
                    if (seats(u, v, across, deep) && apart(new Rect(u - DISPERSE, v - DISPERSE,
                            across + 2 * DISPERSE, deep + 2 * DISPERSE))) {
                        s = stand(w, false, u, v, List.of(left ? Face.RIGHT : Face.LEFT));
                    }
                }
            }
            if (s == null) {
                unplaced.add(w.purpose);
            } else {
                out.add(s);
                end = Math.max(end, s.v + s.deep());
            }
        }
        streets.remove(full);
        streets.add(new Rect(lane, v0, CROSS, end - v0));
        return out;
    }

    /** Whether no building's footprint overlaps {@code r}. */
    private boolean apart(Rect r) {
        for (Rect t : taken) if (t.overlaps(r)) return false;
        return true;
    }

    /**
     * Whether a footprint stands on buildable ground clear of every footprint, street, hardstand, apron, open ground
     * and flight line already laid.
     */
    private boolean seats(int u, int v, int across, int deep) {
        if (u < 0 || v < 0 || u + across > frame.across() || v + deep > frame.depth()) return false;
        Rect r = new Rect(u, v, across, deep);
        for (Rect t : taken) if (t.overlaps(r)) return false;
        for (Rect t : streets) if (t.overlaps(r)) return false;
        for (Rect t : pool) if (t.overlaps(r)) return false;
        for (Rect t : apron) if (t.overlaps(r)) return false;
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
     * Seats what the layout could not, where ground the place cannot build on took its place: each at the free
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

    // ---- Gates and routes ------------------------------------------------------------------------------------

    /**
     * Joins each further gate's road to the streets: a lane {@link #VEHICLE_WAY} wide from where it comes through the
     * wall, by the shortest way clear of every building and the flight line, to the nearest street. A gate whose road
     * led nowhere inside would be a road for nothing.
     */
    private void gateLanes(List<int[]> further) {
        int w = frame.across(), h = frame.depth();
        boolean[][] clear = new boolean[w][h];
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < h; v++) clear[u][v] = !blocked(u, v);
        }
        int half = VEHICLE_WAY / 2;
        for (int[] cell : further) {
            int[] at = frame.frameOf(cell[0], cell[1]);
            List<int[]> path = search(at, (u, v) -> wayClear(clear, u, v, half), (u, v) -> onStreet(u, v));
            for (int[] c : path) streets.add(new Rect(c[0] - half, c[1] - half, VEHICLE_WAY, VEHICLE_WAY));
        }
    }

    /** Whether frame cell {@code (u, v)} is under a building or the flight line, or off the frame. */
    private boolean blocked(int u, int v) {
        if (u < 0 || v < 0 || u >= frame.across() || v >= frame.depth()) return true;
        for (Rect t : taken) if (t.contains(u, v)) return true;
        return airfield != null && airfield.contains(u, v);
    }

    /** Whether a vehicle's way centred on {@code (u, v)} is clear: every cell within {@code half} of it. */
    private static boolean wayClear(boolean[][] clear, int u, int v, int half) {
        for (int du = -half; du <= half; du++) {
            for (int dv = -half; dv <= half; dv++) {
                int a = u + du, b = v + dv;
                // The wall's own cells at the gate: the lane comes through it.
                if (a < 0 || b < 0 || a >= clear.length || b >= clear[0].length) continue;
                if (!clear[a][b]) return false;
            }
        }
        return true;
    }

    private boolean onStreet(int u, int v) {
        for (Rect r : streets) if (r.contains(u, v)) return true;
        return false;
    }

    private boolean paved(int u, int v) {
        for (List<Rect> rs : List.of(streets, pool, apron, parade)) {
            for (Rect r : rs) if (r.contains(u, v)) return true;
        }
        return false;
    }

    /** A predicate over frame cells. */
    private interface CellTest {
        boolean test(int u, int v);
    }

    /**
     * The shortest four-way path of frame cells from {@code start} through cells {@code pass} allows to the first
     * cell {@code goal} accepts, both ends included; empty when there is none.
     */
    private List<int[]> search(int[] start, CellTest pass, CellTest goal) {
        int w = frame.across(), h = frame.depth();
        if (start[0] < 0 || start[1] < 0 || start[0] >= w || start[1] >= h) return List.of();
        int[] from = new int[w * h];
        Arrays.fill(from, -2);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int s = start[0] * h + start[1];
        from[s] = -1;
        queue.add(s);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int at = queue.poll();
            int u = at / h, v = at % h;
            if (goal.test(u, v)) {
                List<int[]> path = new ArrayList<>();
                for (int k = at; k >= 0; k = from[k]) path.add(new int[]{k / h, k % h});
                Collections.reverse(path);
                return path;
            }
            for (int[] d : steps) {
                int nu = u + d[0], nv = v + d[1];
                if (nu < 0 || nv < 0 || nu >= w || nv >= h || from[nu * h + nv] != -2 || !pass.test(nu, nv)) continue;
                from[nu * h + nv] = at;
                queue.add(nu * h + nv);
            }
        }
        return List.of();
    }

    /**
     * A round through {@code stops} on the paving, in map cells: each leg the shortest way over streets, hardstand,
     * apron and parade ground from one stop to the next, each stop taken from the nearest paved cell to it. Empty when
     * a leg has no way.
     */
    private List<int[]> round(List<int[]> stops) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < stops.size(); i++) {
            int[] a = nearestPaved(stops.get(i)), b = nearestPaved(stops.get(i + 1));
            if (a == null || b == null) return List.of();
            List<int[]> leg = search(a, this::paved, (u, v) -> u == b[0] && v == b[1]);
            if (leg.isEmpty()) return List.of();
            for (int[] c : leg) out.add(frame.cell(c[0], c[1]));
        }
        return out;
    }

    /** The paved frame cell nearest {@code at}, or {@code null} for none. */
    private int[] nearestPaved(int[] at) {
        List<int[]> path = search(at, (u, v) -> true, this::paved);
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    // ---- Onto the map ----------------------------------------------------------------------------------------

    private Laid result() {
        List<RoomPacker.Placed> rooms = new ArrayList<>();
        for (Stood s : stood) rooms.add(placed(s));
        List<int[]> streetCells = new ArrayList<>(), poolCells = new ArrayList<>(), apronCells = new ArrayList<>();
        List<int[]> paradeCells = new ArrayList<>();
        for (Rect r : streets) cells(r, streetCells);
        for (Rect r : pool) cells(r, poolCells);
        for (Rect r : apron) cells(r, apronCells);
        for (Rect r : parade) cells(r, paradeCells);
        int[] lot = null;
        if (airfield != null) {
            int[] a = frame.cell(airfield.u, airfield.v);
            int[] b = frame.cell(airfield.u + airfield.across - 1, airfield.v + airfield.deep - 1);
            lot = new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
        }
        return new Laid(rooms, streetCells, poolCells, apronCells, paradeCells, lot, airfieldSize,
                airfieldFacing == null ? away() : airfieldFacing,
                frame.cell(entry, 1), round(truckStops), round(flightStops), strays, unplaced);
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
