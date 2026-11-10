package com.dillon.starsectormarines.battle.world.gen.hull;

import java.util.ArrayList;
import java.util.List;

/**
 * The fallen hull's interior in its own cells ({@code fallen-hull-nouns.md}, Fallen hull): a survey mothership lying on
 * its keel, laid out before it meets any map so its rules are proved here. {@code u} runs along the hull from the
 * stern (0) to the bow ({@link #LENGTH} - 1); {@code v} runs across it, 0 to {@link #BREADTH} - 1, its keel line between
 * {@code v} 15 and 16. The bow tapers; everything inside the outline that is not carved is solid hull.
 *
 * <p>The plant's ways are sized for the derelict line ({@code derelict-watch.md}, section 3): a four-cell freight spine
 * from the stern's cargo door to the forge hall, four-cell cargo docks into every hall, so a hall frame walks the
 * whole hull, and a two-cell service door to the crew deck, which only the heavy and the rounds walker pass. The forge
 * hall is the interior's vault: the autoforge stands at its far end.
 *
 * <p>Entrances from open ground: the stern's cargo door (onto the trench), the launch bay's door on the port flank and
 * the breach torn through the starboard flank in the crash.
 */
public final class HullPlan {

    /** The hull's length and breadth, cells. UNTUNED. */
    public static final int LENGTH = 88, BREADTH = 32;
    /** Where the bow's taper begins, and how far in it has drawn each flank by the bow's tip. */
    static final int TAPER_FROM = 70, TAPER_IN = 10;

    /** What a cell of the plan is. */
    public enum Kind {
        /** Outside the hull's outline. */
        OUTSIDE,
        /** Solid hull: wall, under the one roof. */
        HULL,
        /** Open deck under the roof. */
        FLOOR,
        /** Deck torn open in the crash, strewn with the hull's own wreckage; walkable. */
        TORN
    }

    /** A rectangle of plan cells, inclusive. */
    public record Rect(int u0, int v0, int u1, int v1) {
        public Rect {
            if (u1 < u0 || v1 < v0) throw new IllegalArgumentException("empty rect");
        }
        public boolean contains(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }
        public int length() { return u1 - u0 + 1; }
        public int breadth() { return v1 - v0 + 1; }
    }

    /** What a hall of the hull was for. */
    public enum HallUse {
        /** A cargo hold, racks mostly stripped. */
        HOLD,
        /** The launch bay the hull's drones and stake trains went out of. */
        BAY,
        /** The autoforge's hall: the interior's vault. */
        FORGE,
        /** The crew deck, behind a service door. */
        CREW
    }

    /** A hall: its floor and the door that joins it to the spine. */
    public record Hall(String name, HallUse use, Rect floor, Rect door) { }

    /**
     * A way in from open ground: {@code door} its cells through the outer wall, {@code du, dv} the step outward.
     */
    public record Entrance(String name, Rect door, int du, int dv) { }

    public static final String STERN = "stern", BAY = "bay", BREACH = "breach";

    /** The freight spine, keel-line, from the stern door to the forge hall's dock. */
    public static final Rect SPINE = new Rect(3, 14, 52, 17);

    private static final List<Hall> HALLS = List.of(
            new Hall("hold.1", HallUse.HOLD, new Rect(6, 20, 21, 29), new Rect(12, 18, 15, 19)),
            new Hall("hold.2", HallUse.HOLD, new Rect(26, 20, 41, 29), new Rect(32, 18, 35, 19)),
            new Hall("hold.3", HallUse.HOLD, new Rect(6, 2, 21, 11), new Rect(12, 12, 15, 13)),
            new Hall("bay", HallUse.BAY, new Rect(26, 1, 45, 11), new Rect(33, 12, 36, 13)),
            new Hall("crew", HallUse.CREW, new Rect(47, 3, 51, 11), new Rect(48, 12, 49, 13)),
            new Hall("forge", HallUse.FORGE, new Rect(53, 5, 72, 26), new Rect(52, 14, 52, 17)));

    private static final List<Entrance> ENTRANCES = List.of(
            new Entrance(STERN, new Rect(0, 14, 2, 17), -1, 0),
            new Entrance(BAY, new Rect(30, 0, 41, 0), 0, -1),
            new Entrance(BREACH, new Rect(44, 18, 47, 31), 0, 1));

    /** The autoforge's cell: on the forge hall's keel line, near its far wall. */
    public static final int FORGE_U = 69, FORGE_V = 15;

    private final Kind[] cells = new Kind[LENGTH * BREADTH];

    private HullPlan() {
        for (int u = 0; u < LENGTH; u++) {
            for (int v = 0; v < BREADTH; v++) cells[u * BREADTH + v] = inOutline(u, v) ? Kind.HULL : Kind.OUTSIDE;
        }
        carve(SPINE, Kind.FLOOR);
        for (Hall hall : HALLS) {
            carve(hall.floor(), Kind.FLOOR);
            carve(hall.door(), Kind.FLOOR);
        }
        for (Entrance e : ENTRANCES) carve(e.door(), e.name().equals(BREACH) ? Kind.TORN : Kind.FLOOR);
    }

    private static final HullPlan PLAN = new HullPlan();

    /** The one plan: every fallen hull is the same survey mothership. */
    public static HullPlan plan() { return PLAN; }

    /** Half the hull's breadth at {@code u}: full abaft the taper, drawing in to the bow. */
    static int halfBreadth(int u) {
        if (u <= TAPER_FROM) return BREADTH / 2;
        return BREADTH / 2 - (u - TAPER_FROM) * TAPER_IN / (LENGTH - 1 - TAPER_FROM);
    }

    static boolean inOutline(int u, int v) {
        if (u < 0 || u >= LENGTH || v < 0 || v >= BREADTH) return false;
        int half = halfBreadth(u);
        return v >= BREADTH / 2 - half && v < BREADTH / 2 + half;
    }

    private void carve(Rect r, Kind kind) {
        for (int u = r.u0(); u <= r.u1(); u++) {
            for (int v = r.v0(); v <= r.v1(); v++) {
                if (u < 0 || u >= LENGTH || v < 0 || v >= BREADTH) throw new IllegalStateException("carved off the plan");
                cells[u * BREADTH + v] = kind;
            }
        }
    }

    /** The cell's kind; {@link Kind#OUTSIDE} off the plan. */
    public Kind at(int u, int v) {
        if (u < 0 || u >= LENGTH || v < 0 || v >= BREADTH) return Kind.OUTSIDE;
        return cells[u * BREADTH + v];
    }

    /** Whether a body can stand on the cell. */
    public boolean open(int u, int v) {
        Kind k = at(u, v);
        return k == Kind.FLOOR || k == Kind.TORN;
    }

    public List<Hall> halls() { return HALLS; }

    public List<Entrance> entrances() { return ENTRANCES; }

    public Hall hall(String name) {
        for (Hall hall : HALLS) if (hall.name().equals(name)) return hall;
        throw new IllegalArgumentException("no hall " + name);
    }

    /**
     * The narrowest a way gets along a door: cells across it, measured square to the step through it. A door's span
     * is its long side; a door one cell deep through a wall is as wide as it is long.
     */
    public static int doorWidth(Rect door, boolean throughU) {
        return throughU ? door.breadth() : door.length();
    }

    /** Every open cell reachable from {@code (u, v)} by cardinal steps, row-major by {@code u * BREADTH + v}. */
    public boolean[] reach(int u, int v) {
        boolean[] seen = new boolean[LENGTH * BREADTH];
        if (!open(u, v)) return seen;
        List<int[]> frontier = new ArrayList<>();
        frontier.add(new int[]{u, v});
        seen[u * BREADTH + v] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!frontier.isEmpty()) {
            int[] c = frontier.remove(frontier.size() - 1);
            for (int[] s : steps) {
                int nu = c[0] + s[0], nv = c[1] + s[1];
                if (!open(nu, nv) || seen[nu * BREADTH + nv]) continue;
                seen[nu * BREADTH + nv] = true;
                frontier.add(new int[]{nu, nv});
            }
        }
        return seen;
    }
}
