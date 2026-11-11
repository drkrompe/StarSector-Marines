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
 * <p>The hull broke up coming in ({@code fallen-hull-nouns.md}, The ground): it lies in three {@link Section sections},
 * stern, midsection and bow, each a run of the plan's {@code u} laid apart from the next by a gap of open ground and
 * shifted a little across the line, so the plan's cells are placed on the ground through {@link #laidU} and
 * {@link #laidV}. Nothing joins two sections but the ground between them: a cell's neighbours are its own section's
 * ({@link #near}). Each section is torn open at its broken ends, and those torn ends are the big breaches.
 *
 * <p>Entrances from open ground: the stern's cargo door, the launch bay's door on the port flank, the four torn ends,
 * and three smaller rents in the flanks (into the first, second and third holds). A breach is open to the sky: its
 * cells are {@link Kind#TORN}.
 *
 * <p>The ship is built as a ship ({@code fallen-hull-nouns.md}, The ground): its {@link #shell outer shell}, the solid
 * hull within {@link #SHELL_DEPTH} of the outside, is armour; the rest of its solid hull is bulkhead between
 * compartments. Every open deck cell belongs to one {@link #compartment}: a hall (its floor and its dock), or the
 * spine (the spine and the stern's door), so boarding one opens that compartment alone.
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
        /** Deck torn open in the crash, open to the sky and strewn with the hull's own wreckage; walkable. */
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
    public record Entrance(String name, Rect door, int du, int dv) {
        /** Whether this way in is a breach the crash tore, rather than a door. */
        public boolean torn() { return name.startsWith(BREACH) || broken(); }
        /** Whether this way in is a section's broken end. */
        public boolean broken() { return name.startsWith(BREAK); }
    }

    /**
     * A piece of the broken hull: plan {@code u0..u1}, laid {@code gap} cells of ground after the section before it and
     * {@code shift} cells across the line (toward plan {@code v} increasing).
     */
    public record Section(String name, int u0, int u1, int gap, int shift) {
        public boolean contains(int u) { return u >= u0 && u <= u1; }
    }

    public static final String STERN = "stern", BAY = "bay", BREACH = "breach", BREAK = "break";
    /** The compartment that is no hall: the freight spine in each section, with the stern's door ({@link #spine}). */
    public static final String SPINE_NAME = "spine";

    /** The sections, stern to bow. Gaps and shifts UNTUNED; a gap holds both torn ends' aprons. */
    public static final List<Section> SECTIONS = List.of(
            new Section("stern", 0, 23, 0, 0),
            new Section("mid", 24, 45, 18, 3),
            new Section("bow", 46, LENGTH - 1, 22, -2));
    /** How far a torn end is opened, across the plan: the keel's middle, so its corners stand. */
    static final int TEAR_V0 = 6, TEAR_V1 = 25;
    /** The least and most cross shift of any section. */
    static final int MIN_SHIFT, MAX_SHIFT;
    /** The broken hull's laid length (its sections and the gaps between) and breadth (with the shifts). */
    public static final int LAID_LENGTH, LAID_BREADTH;

    static {
        int lo = 0, hi = 0, gaps = 0;
        for (Section s : SECTIONS) {
            lo = Math.min(lo, s.shift());
            hi = Math.max(hi, s.shift());
            gaps += s.gap();
        }
        MIN_SHIFT = lo;
        MAX_SHIFT = hi;
        LAID_LENGTH = LENGTH + gaps;
        LAID_BREADTH = BREADTH + hi - lo;
    }
    /** How deep the armour shell runs in from the outside, cells. */
    public static final int SHELL_DEPTH = 2;

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
            new Entrance(BREAK + ".stern", new Rect(22, TEAR_V0, 23, TEAR_V1), 1, 0),
            new Entrance(BREAK + ".mid.aft", new Rect(24, TEAR_V0, 25, TEAR_V1), -1, 0),
            new Entrance(BREAK + ".mid.fore", new Rect(44, TEAR_V0, 45, TEAR_V1), 1, 0),
            new Entrance(BREAK + ".bow", new Rect(46, TEAR_V0, 47, TEAR_V1), -1, 0),
            new Entrance(BREACH + ".port", new Rect(14, 0, 17, 1), 0, -1),
            new Entrance(BREACH + ".aft", new Rect(8, 30, 11, 31), 0, 1),
            new Entrance(BREACH + ".mid", new Rect(34, 30, 37, 31), 0, 1));

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
        for (Entrance e : ENTRANCES) carve(e.door(), e.torn() ? Kind.TORN : Kind.FLOOR);
    }

    private static final HullPlan PLAN = new HullPlan();

    /** The one plan: every fallen hull is the same survey mothership. */
    public static HullPlan plan() { return PLAN; }

    /** The section plan {@code u} lies in; the nearest end's section off the plan. */
    public static Section section(int u) {
        for (Section s : SECTIONS) if (s.contains(u)) return s;
        return u < 0 ? SECTIONS.get(0) : SECTIONS.get(SECTIONS.size() - 1);
    }

    /** Where plan {@code u} lies along the broken hull, cells from the stern, as laid with {@code section}'s gaps. */
    public static int laidU(Section section, int u) {
        int gaps = 0;
        for (Section s : SECTIONS) {
            gaps += s.gap();
            if (s == section) break;
        }
        return u + gaps;
    }

    /** Where plan {@code v} lies across the broken hull, in {@code section}: 0 to {@link #LAID_BREADTH} - 1. */
    public static int laidV(Section section, int v) {
        return v + section.shift() - MIN_SHIFT;
    }

    /** The plan {@code u} at laid {@code s} (cells from the stern), or -1 in a gap between sections. */
    public static int planU(int s) {
        int gaps = 0;
        for (Section sec : SECTIONS) {
            gaps += sec.gap();
            int u = s - gaps;
            if (sec.contains(u)) return u;
        }
        return -1;
    }

    /** The section {@code (u, v)}'s neighbour {@code (u + du, v + dv)} is in, as a cell of {@code (u, v)}'s section. */
    public Kind near(int u, int v, int du, int dv) {
        if (section(u) != section(u + du)) return Kind.OUTSIDE;
        return at(u + du, v + dv);
    }

    /** The name of the spine's compartment in a section. */
    public static String spine(Section section) { return SPINE_NAME + "." + section.name(); }

    /** Whether the cell is solid hull at a section's broken end: the cut face, showing the hull's cross-section. */
    public boolean sheared(int u, int v) {
        if (at(u, v) != Kind.HULL) return false;
        Section s = section(u);
        return (u == s.u0() && s.u0() > 0) || (u == s.u1() && s.u1() < LENGTH - 1);
    }

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

    /**
     * Whether the cell is the hull's armour: solid hull with the outside within {@link #SHELL_DEPTH} cells, any way
     * (a torn breach counts as outside: the crash opened the shell there).
     */
    public boolean shell(int u, int v) {
        if (at(u, v) != Kind.HULL) return false;
        for (int du = -SHELL_DEPTH; du <= SHELL_DEPTH; du++) {
            for (int dv = -SHELL_DEPTH; dv <= SHELL_DEPTH; dv++) {
                Kind k = near(u, v, du, dv);
                if (k == Kind.OUTSIDE || k == Kind.TORN) return true;
            }
        }
        return false;
    }

    /**
     * The compartment an open deck cell is in: a hall's name for its floor and dock, its section's {@link #spine},
     * and for a door's cells the compartment it opens into; null off the deck and on a breach, which is open ground.
     */
    public String compartment(int u, int v) {
        if (at(u, v) != Kind.FLOOR) return null;
        for (Hall hall : HALLS) if (hall.floor().contains(u, v) || hall.door().contains(u, v)) return hall.name();
        for (Entrance e : ENTRANCES) {
            if (e.torn() || !e.door().contains(u, v)) continue;
            // Step in from the door until the deck is a hall's or the spine's.
            for (int k = 1; k < LENGTH; k++) {
                int nu = u - e.du() * k, nv = v - e.dv() * k;
                if (!e.door().contains(nu, nv)) return compartment(nu, nv);
            }
        }
        return spine(section(u));
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
                if (section(nu) != section(c[0])) continue;
                if (!open(nu, nv) || seen[nu * BREADTH + nv]) continue;
                seen[nu * BREADTH + nv] = true;
                frontier.add(new int[]{nu, nv});
            }
        }
        return seen;
    }
}
