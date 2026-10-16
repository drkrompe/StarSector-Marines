package com.dillon.starsectormarines.battle.warship;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * A warship's armour grid: where on the hull a hit lands, how much plate is left there, and which cells have been
 * holed through ({@code warship-hull-damage.md}).
 *
 * <p>The grid is the base game's idea ({@code ArmorGridAPI}) read off the hull's own geometry and nothing else: the
 * sprite's box is divided into cells of about {@link #CELL_HULL_PIXELS} hull pixels, a cell belongs to the hull when
 * its centre is inside the hull's {@code bounds} polygon, the outer {@link #PLATE_DEPTH} cells carry plate and the
 * deeper ones are the <b>core</b>. Every hull cell carries an equal share of the hull's hit points as
 * <b>structure</b>.
 *
 * <p>Cells are addressed {@code (col, row)}: {@code col} across the sprite from its left (port) edge,
 * {@code row} along it from the nose, the same lattice orientation as {@code HullBreakup}, so a renderer can cut
 * the hull sprite on it. Positions are normalised: {@code u} across in [0, 1], {@code v} along in [0, 1] from the
 * nose.
 *
 * <p>A hit reads the base game's 21-cell block about the cell it entered (the 3x3 at full weight, the rest of the
 * 5x5 without its corners at half), over cells that carry plate only, so a fresh edge reads exactly the rating.
 * The plate there loses the share of the hit the armour rule lets through (the base game's armour damage), the
 * hull loses the same share as it always has, and that hull damage is attributed to the block's structure and to
 * the entry cell's {@link Section}. A cell with no plate left and no structure left is <b>breached</b> (a round's
 * line passes through it) once its section has given way: an outer section holds until the hull has lost
 * {@link #SECTION_BREACH_SHARE} of its hit points through it.
 *
 * <p>Hull hit points stay {@link Warship}'s and stay the one authority on when the ship dies. Deterministic: no
 * random source.
 */
public final class WarshipPlating {

    /** The base game's armour cell, in hull pixels. */
    public static final float CELL_HULL_PIXELS = 15f;
    /** Rows along the hull are kept in this range, so a frigate still has a shape and a capital is not a mosaic. */
    static final int MIN_ROWS = 6;
    static final int MAX_ROWS = 28;
    /** How many cells in from the edge carry plate: the two-cell reach of the base game's hit block. */
    public static final int PLATE_DEPTH = 2;
    /** Effective armour never falls below this share of the rating (the base game's {@code minArmorFraction}). */
    public static final float MIN_ARMOUR_FRACTION = 0.05f;
    /** The base game's weighted cell count of a full block: nine at full weight and twelve at half. */
    static final float BLOCK_WEIGHT = 15f;
    /** Plate under this share of a cell's is gone. */
    static final float PLATE_GONE = 0.02f;
    /**
     * Share of the hull's hit points that must be lost through an outer section before any of its cells is holed
     * through. Until then a stripped side still turns rounds onto the frames behind it rather than letting them into
     * the core: the section is a compartment, and it gives way as one.
     */
    public static final float SECTION_BREACH_SHARE = 0.25f;

    /** Where a hull cell belongs, for reading the grid by region. */
    public enum Section { BOW, PORT, STARBOARD, STERN, CORE }

    /** Forward share of the hull length that is the bow, and aft share that is the stern. */
    static final float BOW_SHARE = 0.25f;
    static final float STERN_SHARE = 0.20f;

    private static final int[][] BLOCK = block();

    public final int cols;
    public final int rows;
    private final boolean[] hull;
    private final boolean[] plated;
    private final Section[] section;
    private final float[] plate;
    private final float[] structure;
    private final boolean[] breached;
    private final float[] breachedAt;
    private final float[] lastHitAt;
    private final float rating;
    private final float structurePerCell;
    private final int hullCells;
    private int revision;
    private int breaches;
    private final int[] hitsBySection = new int[Section.values().length];
    /** Hull points lost through each section's cells. */
    private final float[] takenBySection = new float[Section.values().length];
    private final float breachThreshold;
    private final boolean soak;
    private float firstBreachAt = Float.NaN;
    private float firstCoreEntryAt = Float.NaN;

    private WarshipPlating(int cols, int rows, boolean[] hull, float rating, float hitPoints, boolean soak) {
        this.soak = soak;
        this.cols = cols;
        this.rows = rows;
        this.hull = hull;
        int n = cols * rows;
        this.plated = new boolean[n];
        this.section = new Section[n];
        this.plate = new float[n];
        this.structure = new float[n];
        this.breached = new boolean[n];
        this.breachedAt = new float[n];
        this.lastHitAt = new float[n];
        Arrays.fill(breachedAt, Float.NaN);
        Arrays.fill(lastHitAt, Float.NaN);
        this.rating = rating;
        int count = 0;
        for (boolean h : hull) if (h) count++;
        this.hullCells = count;
        this.structurePerCell = count == 0 ? 0f : hitPoints / count;
        this.breachThreshold = hitPoints * SECTION_BREACH_SHARE;
        int[] depth = depths(cols, rows, hull);
        for (int i = 0; i < n; i++) {
            if (!hull[i]) continue;
            plated[i] = depth[i] <= PLATE_DEPTH;
            plate[i] = plated[i] ? 1f : 0f;
            structure[i] = 1f;
            section[i] = sectionOf(i % cols, i / cols, plated[i]);
        }
    }

    // ------------------------------------------------------------------------------------------------ construction

    /**
     * The grid of a hull whose sprite is {@code width x height} hull pixels with its centre of gravity at
     * {@code (centerX, centerY)} from the sprite's lower-left corner, and whose collision polygon is {@code bounds}:
     * alternating x (toward the nose) and y (toward port) in hull pixels about that centre, as a {@code .ship}
     * writes them. A missing or degenerate polygon takes the sprite's inscribed ellipse.
     */
    public static WarshipPlating fromHull(float[] bounds, float width, float height, float centerX, float centerY,
                                          float armourRating, float hitPoints) {
        if (!(width > 0f) || !(height > 0f)) throw new IllegalArgumentException("a hull needs a positive sprite box");
        int rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, Math.round(height / CELL_HULL_PIXELS)));
        float cellPx = height / rows;
        int cols = Math.max(3, Math.round(width / cellPx));
        float[] u = null;
        float[] v = null;
        if (bounds != null && bounds.length >= 6 && bounds.length % 2 == 0) {
            int corners = bounds.length / 2;
            u = new float[corners];
            v = new float[corners];
            for (int k = 0; k < corners; k++) {
                // Hull frame (x toward the nose, y toward port) to the sprite, measured from its top-left.
                u[k] = (centerX - bounds[2 * k + 1]) / width;
                v[k] = 1f - (centerY + bounds[2 * k]) / height;
            }
        }
        boolean[] hull = new boolean[cols * rows];
        int inside = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float cu = (c + 0.5f) / cols;
                float cv = (r + 0.5f) / rows;
                boolean in = u != null ? insidePolygon(u, v, cu, cv) : insideEllipse(cu, cv);
                hull[r * cols + c] = in;
                if (in) inside++;
            }
        }
        if (inside == 0) {
            // A polygon that covers no cell centre (a sliver, or a wrong frame): the ellipse is the honest stand-in.
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) hull[r * cols + c] = insideEllipse((c + 0.5f) / cols, (r + 0.5f) / rows);
            }
        }
        return new WarshipPlating(cols, rows, hull, armourRating, hitPoints, soakEnabled());
    }

    /** Whether the plate soaks before the hull takes the rest; {@code -Dbattle.warship.plateSoak=false} is the wear control. */
    public static boolean soakEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("battle.warship.plateSoak"));
    }

    /** A grid over an explicit hull mask, row-major with row 0 at the nose; for tests and tools. */
    static WarshipPlating ofMask(int cols, int rows, boolean[] hull, float armourRating, float hitPoints) {
        if (hull.length != cols * rows) throw new IllegalArgumentException("mask is not cols x rows");
        return new WarshipPlating(cols, rows, hull.clone(), armourRating, hitPoints, true);
    }

    /** {@link #ofMask(int, int, boolean[], float, float)} with the plate wearing rather than soaking. */
    static WarshipPlating ofMaskWearing(int cols, int rows, boolean[] hull, float armourRating, float hitPoints) {
        return new WarshipPlating(cols, rows, hull.clone(), armourRating, hitPoints, false);
    }

    private static boolean insideEllipse(float u, float v) {
        float du = (u - 0.5f) / 0.5f;
        float dv = (v - 0.5f) / 0.5f;
        return du * du + dv * dv <= 1f;
    }

    private static boolean insidePolygon(float[] px, float[] py, float x, float y) {
        boolean in = false;
        for (int i = 0, j = px.length - 1; i < px.length; j = i++) {
            if ((py[i] > y) != (py[j] > y)
                    && x < (px[j] - px[i]) * (y - py[i]) / (py[j] - py[i]) + px[i]) {
                in = !in;
            }
        }
        return in;
    }

    /** Distance in cells (4-neighbour) from each hull cell to the outside; the edge is 1. */
    private static int[] depths(int cols, int rows, boolean[] hull) {
        int[] depth = new int[cols * rows];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (!hull[i]) continue;
                boolean edge = c == 0 || r == 0 || c == cols - 1 || r == rows - 1
                        || !hull[i - 1] || !hull[i + 1] || !hull[i - cols] || !hull[i + cols];
                if (edge) {
                    depth[i] = 1;
                    queue.add(i);
                }
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int c = i % cols;
            int r = i / cols;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] s : steps) {
                int nc = c + s[0];
                int nr = r + s[1];
                if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue;
                int j = nr * cols + nc;
                if (!hull[j] || depth[j] != 0) continue;
                depth[j] = depth[i] + 1;
                queue.add(j);
            }
        }
        return depth;
    }

    private Section sectionOf(int c, int r, boolean plate) {
        if (!plate) return Section.CORE;
        float v = (r + 0.5f) / rows;
        if (v < BOW_SHARE) return Section.BOW;
        if (v > 1f - STERN_SHARE) return Section.STERN;
        return (c + 0.5f) / cols < 0.5f ? Section.PORT : Section.STARBOARD;
    }

    /** The base game's hit block: offsets and weights, the inner 3x3 at full weight, the 5x5 ring less corners at half. */
    private static int[][] block() {
        int[][] out = new int[21][];
        int k = 0;
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (Math.abs(dr) == 2 && Math.abs(dc) == 2) continue;
                int weight = Math.abs(dr) <= 1 && Math.abs(dc) <= 1 ? 2 : 1;
                out[k++] = new int[]{dc, dr, weight};
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ hits

    /**
     * The cell a round entering along the line from {@code (fromU, fromV)} toward {@code (toU, toV)} meets first:
     * the first hull cell on that line that is not breached, walked from outside the sprite's box. When the line
     * passes the hull by, or through nothing but breaches, the live cell nearest {@code (toU, toV)}; -1 when no
     * cell is left.
     */
    public int entryCell(float fromU, float fromV, float toU, float toV) {
        float du = (toU - fromU) * cols;
        float dv = (toV - fromV) * rows;
        float len = (float) Math.sqrt(du * du + dv * dv);
        if (len > 1e-4f) {
            // Walk the line in quarter cells from where it enters the box to where it leaves.
            float[] span = clipToBox(fromU * cols, fromV * rows, du, dv);
            if (span != null) {
                float step = 0.25f / len;
                for (float t = span[0]; t <= span[1]; t += step) {
                    int c = (int) Math.floor(fromU * cols + du * t);
                    int r = (int) Math.floor(fromV * rows + dv * t);
                    if (c < 0 || r < 0 || c >= cols || r >= rows) continue;
                    int i = r * cols + c;
                    if (hull[i] && !breached[i]) return i;
                }
            }
        }
        return nearestLive(toU, toV);
    }

    /** Parameter range {@code [t0, t1]} over which {@code p + d t} lies inside {@code [0, cols] x [0, rows]}. */
    private float[] clipToBox(float px, float py, float dx, float dy) {
        float t0 = 0f;
        float t1 = Float.MAX_VALUE;
        float[][] axes = {{px, dx, cols}, {py, dy, rows}};
        for (float[] a : axes) {
            if (Math.abs(a[1]) < 1e-6f) {
                if (a[0] < 0f || a[0] > a[2]) return null;
                continue;
            }
            float ta = (0f - a[0]) / a[1];
            float tb = (a[2] - a[0]) / a[1];
            t0 = Math.max(t0, Math.min(ta, tb));
            t1 = Math.min(t1, Math.max(ta, tb));
        }
        if (t1 == Float.MAX_VALUE) t1 = t0;
        return t0 <= t1 ? new float[]{t0, t1} : null;
    }

    /** The live (unbreached) hull cell nearest {@code (u, v)}, or -1 when none is left. */
    public int nearestLive(float u, float v) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < hull.length; i++) {
            if (!hull[i] || breached[i]) continue;
            float du = ((i % cols) + 0.5f) / cols - u;
            float dv = ((i / cols) + 0.5f) / rows - v;
            // In cells, so a long hull's along-axis is not squashed.
            float d = du * du * cols * cols + dv * dv * rows * rows;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * The armour a hit entering {@code cell} meets: the rating times the weighted plate left over the block's plated
     * cells, never below {@link #MIN_ARMOUR_FRACTION} of the rating. A block with no plate in it (deep in the core)
     * meets only the floor.
     */
    public float localArmour(int cell) {
        if (cell < 0) return rating * MIN_ARMOUR_FRACTION;
        int c0 = cell % cols;
        int r0 = cell / cols;
        float weight = 0f;
        float left = 0f;
        for (int[] b : BLOCK) {
            int c = c0 + b[0];
            int r = r0 + b[1];
            if (c < 0 || r < 0 || c >= cols || r >= rows) continue;
            int i = r * cols + c;
            if (!plated[i]) continue;
            weight += b[2];
            left += b[2] * plate[i];
        }
        float share = weight > 0f ? left / weight : 0f;
        return rating * Math.max(MIN_ARMOUR_FRACTION, share);
    }

    /**
     * Lands one hit of {@code damage} at {@code penetration} on {@code cell} and returns the hull points it takes.
     * The armour rule is {@link Warship#effectiveDamage} against the local armour; a hit that would glance off the
     * hull's full rating still glances, so a weapon that could never hurt the hull cannot once its plate is gone.
     * What gets through is the base game's armour damage: it is dealt over the block by weight, each plated cell
     * soaks its share out of the plate it has left, and what the plate cannot soak reaches the hull (with
     * {@link #soaks()} false, the wear control, the hull takes all of it and the plate wears by the same amount).
     * The hull points are charged to the block's structure and the entry cell's section; cells that run out of
     * both are breached at {@code nowSeconds} once their section has given way.
     */
    public float hit(int cell, float damage, float penetration, float nowSeconds) {
        if (cell < 0 || damage <= 0f || penetration <= 0f) return 0f;
        float strength = damage * penetration;
        if (strength < rating * Warship.GLANCE_SHARE) return 0f;
        float armour = localArmour(cell);
        float through = damage * strength / (armour + strength);
        hitsBySection[section[cell].ordinal()]++;
        if (section[cell] == Section.CORE && Float.isNaN(firstCoreEntryAt)) firstCoreEntryAt = nowSeconds;
        float hull = strip(cell, through, nowSeconds);
        takenBySection[section[cell].ordinal()] += hull;
        charge(cell, hull, nowSeconds);
        revision++;
        return hull;
    }

    /** Whether the plate soaks a hit before the hull takes the rest (the base game's rule; the default). */
    public boolean soaks() {
        return soak;
    }

    /** Deals {@code amount} of armour damage over the block about {@code cell} and returns what reaches the hull. */
    private float strip(int cell, float amount, float now) {
        int c0 = cell % cols;
        int r0 = cell / cols;
        float liveWeight = 0f;
        float plateWeight = 0f;
        for (int[] b : BLOCK) {
            int i = blockCell(c0, r0, b);
            if (i < 0) continue;
            liveWeight += b[2];
            if (plated[i] && plate[i] > 0f) plateWeight += b[2];
        }
        if (liveWeight <= 0f) return amount;
        // A cell's plate is a fifteenth of the rating, the base game's.
        float platePerCell = rating / BLOCK_WEIGHT;
        float toHull = 0f;
        for (int[] b : BLOCK) {
            int i = blockCell(c0, r0, b);
            if (i < 0) continue;
            lastHitAt[i] = now;
            if (!soak) {
                if (plated[i] && plate[i] > 0f && plateWeight > 0f) {
                    plate[i] = Math.max(0f, plate[i] - amount * b[2] / plateWeight / platePerCell);
                }
                continue;
            }
            float share = amount * b[2] / liveWeight;
            float left = plated[i] ? plate[i] * platePerCell : 0f;
            float soaked = Math.min(share, left);
            if (soaked > 0f) plate[i] = Math.max(0f, (left - soaked) / platePerCell);
            toHull += share - soaked;
        }
        return soak ? toHull : amount;
    }

    /** Charges {@code hull} points to the structure of the block about {@code cell}, breaching what gives way. */
    private void charge(int cell, float hull, float now) {
        int c0 = cell % cols;
        int r0 = cell / cols;
        float liveWeight = 0f;
        for (int[] b : BLOCK) if (blockCell(c0, r0, b) >= 0) liveWeight += b[2];
        if (liveWeight <= 0f || structurePerCell <= 0f) return;
        for (int[] b : BLOCK) {
            int i = blockCell(c0, r0, b);
            if (i < 0) continue;
            if (hull > 0f) structure[i] = Math.max(0f, structure[i] - hull * b[2] / liveWeight / structurePerCell);
            if (structure[i] <= 0f && (!plated[i] || plate[i] <= PLATE_GONE) && mayBreach(i)) {
                breached[i] = true;
                breachedAt[i] = now;
                breaches++;
                if (Float.isNaN(firstBreachAt)) firstBreachAt = now;
            }
        }
    }

    /** The live hull cell at a block offset from {@code (c0, r0)}, or -1. */
    private int blockCell(int c0, int r0, int[] b) {
        int c = c0 + b[0];
        int r = r0 + b[1];
        if (c < 0 || r < 0 || c >= cols || r >= rows) return -1;
        int i = r * cols + c;
        return hull[i] && !breached[i] ? i : -1;
    }

    /**
     * Whether a spent cell may be holed through: an outer cell once its section has given way, a core cell once a
     * neighbour is already open, so the core is holed from the breach inward and never under standing plate.
     */
    private boolean mayBreach(int i) {
        if (section[i] != Section.CORE) return sectionGivesWay(section[i]);
        int c = i % cols;
        int r = i / cols;
        return (c > 0 && open(i - 1)) || (c < cols - 1 && open(i + 1))
                || (r > 0 && open(i - cols)) || (r < rows - 1 && open(i + cols));
    }

    private boolean open(int i) {
        return !hull[i] || breached[i];
    }

    /** Whether a section's cells may be holed through: the core always, an outer section once it has given way. */
    private boolean sectionGivesWay(Section s) {
        return s == Section.CORE || takenBySection[s.ordinal()] >= breachThreshold;
    }

    // ------------------------------------------------------------------------------------------------ readings

    /** Hull points lost through a section's cells. */
    public float takenThrough(Section s) {
        return takenBySection[s.ordinal()];
    }

    /** Whether an outer section has given way (the core always has nothing to give). */
    public boolean givenWay(Section s) {
        return s != Section.CORE && sectionGivesWay(s);
    }

    public boolean isHull(int col, int row) {
        return hull[row * cols + col];
    }

    public boolean isPlated(int col, int row) {
        return plated[row * cols + col];
    }

    /** Plate left in a cell, 0 to 1; 0 for a core cell, which never had any. */
    public float plate(int col, int row) {
        return plate[row * cols + col];
    }

    /** Structure left in a cell, 0 to 1. */
    public float structure(int col, int row) {
        return structure[row * cols + col];
    }

    public boolean breached(int col, int row) {
        return breached[row * cols + col];
    }

    /** When a cell was breached, in battle seconds, or NaN while it stands. */
    public float breachedAt(int col, int row) {
        return breachedAt[row * cols + col];
    }

    /** When a cell last took part of a hit, in battle seconds, or NaN when it never has. */
    public float lastHitAt(int col, int row) {
        return lastHitAt[row * cols + col];
    }

    public Section section(int col, int row) {
        return section[row * cols + col];
    }

    /** Bumped by every hit that lands, so a renderer can tell a grid that has changed. */
    public int revision() {
        return revision;
    }

    /** Hits that entered the hull in a section (glancing rounds are not counted). */
    public int hitsOn(Section s) {
        return hitsBySection[s.ordinal()];
    }

    /** When the first cell was breached, in battle seconds, or NaN. */
    public float firstBreachAt() {
        return firstBreachAt;
    }

    /** When a round first entered a core cell, in battle seconds, or NaN. */
    public float firstCoreEntryAt() {
        return firstCoreEntryAt;
    }

    /** Cells breached so far. */
    public int breachCount() {
        return breaches;
    }

    public int hullCellCount() {
        return hullCells;
    }

    /** Plate left over a section's plated cells, 0 to 1; 1 for the core, which carries none. */
    public float plateLeft(Section s) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < hull.length; i++) {
            if (!hull[i] || !plated[i] || section[i] != s) continue;
            sum += plate[i];
            n++;
        }
        return n == 0 ? 1f : sum / n;
    }

    /** Structure left over a section's cells, 0 to 1. */
    public float structureLeft(Section s) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < hull.length; i++) {
            if (!hull[i] || section[i] != s) continue;
            sum += structure[i];
            n++;
        }
        return n == 0 ? 1f : sum / n;
    }

    /** Share of a section's cells that are breached. */
    public float breachedShare(Section s) {
        int b = 0;
        int n = 0;
        for (int i = 0; i < hull.length; i++) {
            if (!hull[i] || section[i] != s) continue;
            n++;
            if (breached[i]) b++;
        }
        return n == 0 ? 0f : (float) b / n;
    }

    /** Cells a section holds. */
    public int cellCount(Section s) {
        int n = 0;
        for (int i = 0; i < hull.length; i++) if (hull[i] && section[i] == s) n++;
        return n;
    }

    /** The cell at normalised {@code (u, v)}, or -1 off the sprite's box. */
    public int cellAt(float u, float v) {
        int c = (int) Math.floor(u * cols);
        int r = (int) Math.floor(v * rows);
        if (c < 0 || r < 0 || c >= cols || r >= rows) return -1;
        return r * cols + c;
    }

    /** Normalised across-position of a cell's centre. */
    public float cellU(int cell) {
        return ((cell % cols) + 0.5f) / cols;
    }

    /** Normalised along-position of a cell's centre, from the nose. */
    public float cellV(int cell) {
        return ((cell / cols) + 0.5f) / rows;
    }
}
