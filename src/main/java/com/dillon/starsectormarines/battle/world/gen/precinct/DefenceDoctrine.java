package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Where a walled place's emplacements stand, by doctrine rather than by draw ({@code precincts.md}, Defences are
 * sited by doctrine).
 *
 * <p>The owner's notes on the defended bases were that the guns stood on the roads and read as random, and they did:
 * the placer seeded one post a short way in along each gate's own road and drew the rest uniformly from the claim. A
 * base is laid out the way a garrison would site it:
 *
 * <ul>
 *   <li><b>Gates.</b> Every gate gets a post on each side of its approach, set back behind the wall, so the way in is
 *       crossed by two fields of fire and the road itself stays open. The first round of flanks covers every gate
 *       once before any gate gets its second, so a short loadout still watches every way in.</li>
 *   <li><b>Corners.</b> Then the wall's corners, the one facing the threat first: a corner post sees down two faces
 *       of the wall and out over the approach.</li>
 *   <li><b>Inner.</b> Then a post beside what the place exists to keep: its motor pool and its command.</li>
 *   <li><b>Depth.</b> Artillery and drone hubs stand deepest and furthest from the main gate, where an attacker has
 *       to get through the wall to reach them.</li>
 * </ul>
 *
 * <p>The heaviest weapons take the earliest sites, so the main gate holds the heaviest guns. Nothing stands on a
 * cell the caller marks to keep clear: the roads, the place's own streets and yards, and every gate's approach
 * ({@link #approaches}). A site the ground cannot take is moved to the nearest cell that can, within
 * {@link #RESOLVE_RADIUS}, and is otherwise left unsited rather than drawn somewhere at random.
 *
 * <p>Pure geometry on masks, so the rules are proved on small fixtures without a map.
 */
public final class DefenceDoctrine {

    private DefenceDoctrine() {}

    /** Why a site is where it is. */
    public enum Role { GATE, CORNER, INNER, DEPTH }

    /**
     * A gate in the wall: its centre on the wall line, the unit step that leads out of the place through it, and half
     * its width beside the centre.
     */
    public record Gate(int x, int y, int outX, int outY, int halfWidth) {
        public Gate {
            if (Math.abs(outX) + Math.abs(outY) != 1) throw new IllegalArgumentException("outward step " + outX + "," + outY);
        }
    }

    /** An emplacement's site: its anchor, why it is there, what stands there, and the gate it covers or -1. */
    public record Site(int x, int y, Role role, DefensePostKind kind, int gate) { }

    /** Whether an emplacement of a kind can stand with its anchor on a cell. */
    public interface Fits {
        boolean at(int x, int y, DefensePostKind kind);
    }

    /** Cells behind the wall a gate's flanking posts stand. UNTUNED. */
    static final int GATE_SETBACK = 5;
    /** Cells beside a gate's own half-width its flanking posts stand, so the pair clears {@link #SPACING}. UNTUNED. */
    static final int FLANK_OFFSET = 6;
    /** Cells inward from a corner, on each axis, a corner post stands. UNTUNED. */
    static final int CORNER_SETBACK = 6;
    /** Cells a gate's approach runs into the place, past the wall, kept clear of emplacements. UNTUNED. */
    public static final int APPROACH_IN = 10;
    /** Cells a gate's approach runs out from the wall, across the glacis and onto the road beyond. UNTUNED. */
    public static final int APPROACH_OUT = 14;
    /** Cells either side of a gate's width its approach keeps clear: the road's verge. UNTUNED. */
    public static final int APPROACH_VERGE = 2;
    /** Furthest a site moves to find ground that takes it. */
    static final int RESOLVE_RADIUS = 8;
    /**
     * Least distance between two emplacements' anchors. The placer refuses closer
     * ({@code DefensePostStamper.POST_MIN_SEPARATION}), so a site planned closer is a site lost.
     */
    public static final int SPACING = 14;
    /** Cells from the outline a depth position wants to be, where the place has that much inside. */
    static final int DEEP = 10;

    /** The kinds that stand on the perimeter and its sites, heaviest first. */
    static final List<DefensePostKind> PERIMETER =
            List.of(DefensePostKind.LARGE, DefensePostKind.MEDIUM, DefensePostKind.LIGHT);
    /** The kinds whose reason is standoff, heaviest first. */
    static final List<DefensePostKind> STANDOFF = List.of(DefensePostKind.ARTILLERY, DefensePostKind.DRONE_HUB);

    /**
     * Sites every emplacement of {@code posts} it can.
     *
     * @param inside  the place's ground behind its wall, map-sized
     * @param gates   the open gates, the main gate first
     * @param hearts  what inner posts cover (motor pool, command), most important first; may be empty
     * @param threat  where an attack comes from (the landing), or {@code null} to take the main gate's outward line
     * @param posts   how many of each kind the fortification holds
     * @param fits    whether the ground takes an emplacement; the caller folds the keep-clear mask into it
     * @return the sites, in the order they should be stamped
     */
    public static List<Site> plan(boolean[][] inside, List<Gate> gates, List<int[]> hearts, int[] threat,
                                  Map<DefensePostKind, Integer> posts, Fits fits) {
        int w = inside.length, h = inside[0].length;
        int[] box = bounds(inside);
        List<Site> sites = new ArrayList<>();
        if (box == null) return sites;
        int[] centre = centroid(inside);
        int[] toward = threat != null ? threat
                : gates.isEmpty() ? null
                : new int[]{gates.get(0).x() + gates.get(0).outX() * w, gates.get(0).y() + gates.get(0).outY() * h};

        // The perimeter's wanted sites in doctrine order, each tagged with the gate it covers.
        List<int[]> wanted = new ArrayList<>();      // {x, y, role ordinal, gate}
        for (int side : new int[]{1, -1}) {
            for (int g = 0; g < gates.size(); g++) {
                Gate gate = gates.get(g);
                int alongX = -gate.outY(), alongY = gate.outX();
                int lateral = side * (gate.halfWidth() + FLANK_OFFSET);
                wanted.add(new int[]{
                        gate.x() - gate.outX() * GATE_SETBACK + alongX * lateral,
                        gate.y() - gate.outY() * GATE_SETBACK + alongY * lateral,
                        Role.GATE.ordinal(), g});
            }
        }
        for (int[] corner : corners(box, centre, toward)) {
            wanted.add(new int[]{corner[0], corner[1], Role.CORNER.ordinal(), -1});
        }
        for (int[] heart : hearts) wanted.add(new int[]{heart[0], heart[1], Role.INNER.ordinal(), -1});

        List<DefensePostKind> perimeter = expand(posts, PERIMETER);
        int next = 0;
        for (int[] want : wanted) {
            if (next >= perimeter.size()) break;
            DefensePostKind kind = perimeter.get(next);
            int[] at = resolve(want[0], want[1], kind, inside, sites, fits);
            if (at == null) continue;
            sites.add(new Site(at[0], at[1], Role.values()[want[2]], kind, want[3]));
            next++;
        }

        // Depth: deepest ground, furthest from the main gate.
        List<DefensePostKind> standoff = expand(posts, STANDOFF);
        if (!standoff.isEmpty()) {
            int[][] depth = depth(inside);
            int deepest = 0;
            for (int[] col : depth) for (int d : col) deepest = Math.max(deepest, d);
            int floor = Math.min(DEEP, Math.max(1, deepest - 2));
            int[] from = gates.isEmpty() ? centre : new int[]{gates.get(0).x(), gates.get(0).y()};
            List<int[]> rear = new ArrayList<>();
            for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) if (depth[x][y] >= floor) rear.add(new int[]{x, y});
            rear.sort(Comparator.comparingLong((int[] c) -> -dist2(c, from)).thenComparingInt(c -> c[0])
                    .thenComparingInt(c -> c[1]));
            int k = 0;
            for (int[] cell : rear) {
                if (k >= standoff.size()) break;
                DefensePostKind kind = standoff.get(k);
                if (!fits.at(cell[0], cell[1], kind) || crowded(cell[0], cell[1], sites)) continue;
                sites.add(new Site(cell[0], cell[1], Role.DEPTH, kind, -1));
                k++;
            }
        }
        return sites;
    }

    /**
     * Every gate's approach: the gate's width and a verge either side, from {@link #APPROACH_IN} cells inside the
     * wall to {@link #APPROACH_OUT} outside it. Nothing defensive stands here: it is the road a convoy drives and the
     * ground the gate's posts are watching.
     */
    public static boolean[][] approaches(List<Gate> gates, int width, int height) {
        boolean[][] clear = new boolean[width][height];
        for (Gate gate : gates) {
            int alongX = -gate.outY(), alongY = gate.outX();
            int half = gate.halfWidth() + APPROACH_VERGE;
            for (int d = -APPROACH_IN; d <= APPROACH_OUT; d++) {
                for (int t = -half; t <= half; t++) {
                    int x = gate.x() + gate.outX() * d + alongX * t;
                    int y = gate.y() + gate.outY() * d + alongY * t;
                    if (x >= 0 && y >= 0 && x < width && y < height) clear[x][y] = true;
                }
            }
        }
        return clear;
    }

    /**
     * The gate a run of wall-line crossing cells makes: their middle, the step out of {@code inside} most of them
     * take, and half their width. {@code null} when no cell of the run borders the outside.
     */
    public static Gate gateOf(List<int[]> cells, boolean[][] inside) {
        if (cells.isEmpty()) return null;
        int w = inside.length, h = inside[0].length;
        long sx = 0, sy = 0;
        int[] votes = new int[4];
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] c : cells) {
            sx += c[0];
            sy += c[1];
            for (int s = 0; s < 4; s++) {
                int nx = c[0] + steps[s][0], ny = c[1] + steps[s][1];
                boolean out = nx < 0 || ny < 0 || nx >= w || ny >= h || !inside[nx][ny];
                // A step out that is also not back into the run: along the wall line, a neighbour is another gate cell.
                if (out && !contains(cells, nx, ny)) votes[s]++;
            }
        }
        int best = -1;
        for (int s = 0; s < 4; s++) if (votes[s] > 0 && (best < 0 || votes[s] > votes[best])) best = s;
        if (best < 0) return null;
        return new Gate((int) (sx / cells.size()), (int) (sy / cells.size()), steps[best][0], steps[best][1],
                cells.size() / 2);
    }

    /** The place's four corners, moved in by {@link #CORNER_SETBACK}, the one facing the threat first. */
    private static List<int[]> corners(int[] box, int[] centre, int[] toward) {
        List<int[]> out = new ArrayList<>();
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                int x = sx < 0 ? box[0] + CORNER_SETBACK : box[2] - CORNER_SETBACK;
                int y = sy < 0 ? box[1] + CORNER_SETBACK : box[3] - CORNER_SETBACK;
                out.add(new int[]{x, y});
            }
        }
        if (toward != null) {
            double tx = toward[0] - centre[0], ty = toward[1] - centre[1];
            out.sort(Comparator.comparingDouble((int[] c) -> -((c[0] - centre[0]) * tx + (c[1] - centre[1]) * ty)));
        }
        return out;
    }

    /** The nearest cell to {@code (x, y)} the ground takes, clear of sites already chosen, or null. */
    private static int[] resolve(int x, int y, DefensePostKind kind, boolean[][] inside, List<Site> sites, Fits fits) {
        int w = inside.length, h = inside[0].length;
        for (int r = 0; r <= RESOLVE_RADIUS; r++) {
            int[] best = null;
            long bestD = Long.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h || !inside[nx][ny]) continue;
                    if (!fits.at(nx, ny, kind) || crowded(nx, ny, sites)) continue;
                    long d = (long) dx * dx + (long) dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        best = new int[]{nx, ny};
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean crowded(int x, int y, List<Site> sites) {
        for (Site s : sites) {
            long dx = s.x() - x, dy = s.y() - y;
            if (dx * dx + dy * dy < (long) SPACING * SPACING) return true;
        }
        return false;
    }

    /** The posts of {@code kinds}, one entry each, in the order given. */
    private static List<DefensePostKind> expand(Map<DefensePostKind, Integer> posts, List<DefensePostKind> kinds) {
        List<DefensePostKind> out = new ArrayList<>();
        for (DefensePostKind kind : kinds) {
            for (int i = 0; i < posts.getOrDefault(kind, 0); i++) out.add(kind);
        }
        return out;
    }

    /** Steps from each inside cell to the nearest cell that is not inside; 0 outside. */
    static int[][] depth(boolean[][] inside) {
        int w = inside.length, h = inside[0].length;
        int[][] depth = new int[w][h];
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                boolean edge = !inside[x][y] || x == 0 || y == 0 || x == w - 1 || y == h - 1;
                depth[x][y] = edge ? (inside[x][y] ? 1 : 0) : -1;
                if (edge) queue[tail++] = x * h + y;
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int c = queue[head++];
            int cx = c / h, cy = c % h;
            for (int[] s : steps) {
                int nx = cx + s[0], ny = cy + s[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || depth[nx][ny] != -1) continue;
                depth[nx][ny] = depth[cx][cy] + 1;
                queue[tail++] = nx * h + ny;
            }
        }
        return depth;
    }

    private static boolean contains(List<int[]> cells, int x, int y) {
        for (int[] c : cells) if (c[0] == x && c[1] == y) return true;
        return false;
    }

    private static long dist2(int[] a, int[] b) {
        long dx = a[0] - b[0], dy = a[1] - b[1];
        return dx * dx + dy * dy;
    }

    /** {left, bottom, right, top} of the true cells, or null when there are none. */
    static int[] bounds(boolean[][] mask) {
        int l = Integer.MAX_VALUE, b = Integer.MAX_VALUE, r = Integer.MIN_VALUE, t = Integer.MIN_VALUE;
        for (int x = 0; x < mask.length; x++) {
            for (int y = 0; y < mask[0].length; y++) {
                if (!mask[x][y]) continue;
                l = Math.min(l, x);
                r = Math.max(r, x);
                b = Math.min(b, y);
                t = Math.max(t, y);
            }
        }
        return l == Integer.MAX_VALUE ? null : new int[]{l, b, r, t};
    }

    private static int[] centroid(boolean[][] mask) {
        long sx = 0, sy = 0, n = 0;
        for (int x = 0; x < mask.length; x++) {
            for (int y = 0; y < mask[0].length; y++) {
                if (!mask[x][y]) continue;
                sx += x;
                sy += y;
                n++;
            }
        }
        return n == 0 ? new int[]{0, 0} : new int[]{(int) (sx / n), (int) (sy / n)};
    }
}
