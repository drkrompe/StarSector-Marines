package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.Direction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where a story map's patch puts a fallen hull ({@code fallen-hull-nouns.md}, In a battle: Setup): the hull's middle
 * cell, the way its bow points, how long the trench it ploughed runs behind its stern, and the stakes that already
 * stand at the chapter's point in the arc, each by a cell the nearest lattice point to it takes.
 *
 * <p>Read from a pool entry's patch as
 * {@code "hull": {"x", "y", "heading", "trench", "stakes": [{"x", "y", "stage"}], "yards",
 * "dress": [{"kind", "x", "y", "length", "turns"}], "blocks": [[x0, y0, x1, y1]], "plough",
 * "port": [{"kind", "box": [x0, y0, x1, y1]}]}};
 * {@code heading} is {@code north} (+y), {@code east}, {@code south} or {@code west}. {@code yards}, a row, makes the
 * worked fields wholly south of it the port's yards ({@link PortYards}); absent or 0, the fields are left as laid.
 * {@code dress} lays a mine's country round the hull ({@link Dress}, {@link MineCountry}). {@code blocks} are boxes
 * where the city's town lots join into bigger blocks ({@code TownBlockStage}). {@code plough}, cells, is how far out
 * open ground is ploughed under the band where a commons runs on past it ({@link ImpactZone}); absent or 0, the band's
 * own reach. {@code port} dresses a free port's quarters in the town's open lots ({@link Port}, {@link PortQuarter}).
 */
public record HullSitePlan(int x, int y, Direction heading, int trench, List<StakeAt> stakes, int yards,
                           List<Dress> dress, List<Box> blocks, int plough, List<Port> port) {

    /** A box of map cells, inclusive. */
    public record Box(int x0, int y0, int x1, int y1) {
        public Box {
            if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("an empty box");
        }

        /** Whether the inclusive rectangle lies wholly inside, whichever way round its corners are given. */
        public boolean contains(int ax, int ay, int bx, int by) {
            return Math.min(ax, bx) >= x0 && Math.max(ax, bx) <= x1 && Math.min(ay, by) >= y0 && Math.max(ay, by) <= y1;
        }
    }

    /**
     * The shortest and longest trench a hull may have ploughed off its bow, cells. Short is allowed: the broken hull
     * also ploughs the ground between its sections.
     */
    public static final int MIN_TRENCH = 12, MAX_TRENCH = 160;

    /** A stake standing at the chapter's start: the cell it is wanted at, and how far it has risen. */
    public record StakeAt(int x, int y, HullSite.StakeStage stage) {
        public StakeAt {
            if (stage == null) throw new IllegalArgumentException("a stake names its stage");
        }
    }

    /**
     * A piece of the country round the hull the patch lays ({@link MineCountry}), from {@code (x, y)}: its kind, a
     * length (a train's run, a tailings mound's radius, a pumping station's main, a route's run north) and quarter
     * turns (a train along x when odd).
     */
    public record Dress(Kind kind, int x, int y, int length, int turns) {
        /**
         * What a piece of a mine's country is: a set piece, or the hard cover a route is fought along ({@code ROUTE}, a
         * piece every few cells north from the cell) and round a stake ({@code CLUSTER}, pieces about the cell).
         */
        public enum Kind { SPOIL, TAILINGS, HEADFRAME, TRAIN, CARTS, PUMPS, ROUTE, CLUSTER }

        public Dress {
            if (kind == null) throw new IllegalArgumentException("a piece names its kind");
        }
    }

    /**
     * A free port's quarter the patch dresses ({@link PortQuarter}): what its open lots inside the box are given over to.
     */
    public record Port(Kind kind, Box box) {
        /**
         * A container yard under its cranes, an apron of landing pads, or a market's stall rows, each taking one in
         * {@link #openEvery} of its quarter's lots at least {@link #leastLot} on a side: a pad takes every lot it fits.
         */
        public enum Kind {
            CONTAINERS(2, 9), PADS(1, 13), STALLS(3, 8);

            private final int openEvery, leastLot;

            Kind(int openEvery, int leastLot) {
                this.openEvery = openEvery;
                this.leastLot = leastLot;
            }

            /** The shortest side of a town lot the quarter takes for its trade. UNTUNED. */
            public int leastLot() {
                return leastLot;
            }

            /** One lot in this many of the quarter's is left open for the trade. UNTUNED. */
            public int openEvery() {
                return openEvery;
            }
        }

        public Port {
            if (kind == null || box == null) throw new IllegalArgumentException("a quarter names its kind and box");
        }
    }

    public HullSitePlan {
        if (heading == null || heading.dx * heading.dy != 0) throw new IllegalArgumentException("a hull lies on a cardinal heading");
        if (trench < MIN_TRENCH || trench > MAX_TRENCH) throw new IllegalArgumentException("trench of " + trench + " cells");
        stakes = stakes == null ? List.of() : List.copyOf(stakes);
        dress = dress == null ? List.of() : List.copyOf(dress);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        port = port == null ? List.of() : List.copyOf(port);
        if (yards < 0) throw new IllegalArgumentException("yards south of row " + yards);
        if (plough < 0) throw new IllegalArgumentException("plough of " + plough + " cells");
    }

    /**
     * Whether the cell lies under the ground the hull's band ploughs: within the band's reach ({@link #plough}, at
     * least {@link ImpactZone#BATTER}) of the laid hull or its trench, read from the plan before the hull is laid. Raised
     * ground is not grown there, since the band would bury it.
     */
    public boolean underBand(int cx, int cy) {
        double half = HullPlan.LAID_LENGTH / 2.0, reach = HullPlan.LAID_BREADTH / 2.0 + Math.max(ImpactZone.BATTER, plough);
        double ax = x - heading.dx * half, ay = y - heading.dy * half;
        double bx = x + heading.dx * (half + trench), by = y + heading.dy * (half + trench);
        double vx = bx - ax, vy = by - ay, t = ((cx - ax) * vx + (cy - ay) * vy) / (vx * vx + vy * vy);
        t = Math.max(0, Math.min(1, t));
        double dx = cx - (ax + t * vx), dy = cy - (ay + t * vy);
        return dx * dx + dy * dy <= reach * reach;
    }

    /** The patch op's form; null for an absent one. */
    public static HullSitePlan read(JSONObject json) throws JSONException {
        if (json == null) return null;
        Direction heading = switch (json.getString("heading").toLowerCase(Locale.ROOT)) {
            case "north" -> Direction.N;
            case "east" -> Direction.E;
            case "south" -> Direction.S;
            case "west" -> Direction.W;
            default -> throw new JSONException("a hull heads north, east, south or west, not " + json.getString("heading"));
        };
        List<StakeAt> stakes = new ArrayList<>();
        JSONArray array = json.optJSONArray("stakes");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject s = array.getJSONObject(i);
                stakes.add(new StakeAt(s.getInt("x"), s.getInt("y"),
                        HullSite.StakeStage.valueOf(s.getString("stage").toUpperCase(Locale.ROOT))));
            }
        }
        List<Dress> dress = new ArrayList<>();
        JSONArray pieces = json.optJSONArray("dress");
        if (pieces != null) {
            for (int i = 0; i < pieces.length(); i++) {
                JSONObject d = pieces.getJSONObject(i);
                dress.add(new Dress(Dress.Kind.valueOf(d.getString("kind").toUpperCase(Locale.ROOT)), d.getInt("x"),
                        d.getInt("y"), d.optInt("length", 0), d.optInt("turns", 0)));
            }
        }
        List<Box> blocks = new ArrayList<>();
        JSONArray boxes = json.optJSONArray("blocks");
        if (boxes != null) {
            for (int i = 0; i < boxes.length(); i++) {
                JSONArray b = boxes.getJSONArray(i);
                blocks.add(new Box(b.getInt(0), b.getInt(1), b.getInt(2), b.getInt(3)));
            }
        }
        List<Port> port = new ArrayList<>();
        JSONArray quarters = json.optJSONArray("port");
        if (quarters != null) {
            for (int i = 0; i < quarters.length(); i++) {
                JSONObject q = quarters.getJSONObject(i);
                JSONArray b = q.getJSONArray("box");
                port.add(new Port(Port.Kind.valueOf(q.getString("kind").toUpperCase(Locale.ROOT)),
                        new Box(b.getInt(0), b.getInt(1), b.getInt(2), b.getInt(3))));
            }
        }
        return new HullSitePlan(json.getInt("x"), json.getInt("y"), heading, json.optInt("trench", 72), stakes,
                json.optInt("yards", 0), dress, blocks, json.optInt("plough", 0), port);
    }
}
