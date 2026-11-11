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
 * <p>A gun fires only where it can see: a wall blocks direct fire whole, at every height (no gun in the sim fires
 * over one; only indirect fire, the artillery's, needs no line). So every direct-fire post stands outside its wall,
 * {@link #STANDOFF_OUT} cells out on the glacis inside its own sandbagged ring, with a clear lane over open ground
 * ahead of it ({@link #LANE} cells, {@link #ARC_DEGREES} either side of its bearing) that never crosses back over the
 * place (owner, 2026-11-11: "They can't shoot over walls!").
 *
 * <ul>
 *   <li><b>Gates.</b> Every gate gets a post on each side of its approach, out in front of the wall, so the way in is
 *       crossed by two fields of fire and the road itself stays open. The first round of flanks covers every gate
 *       once before any gate gets its second, so a short loadout still watches every way in.</li>
 *   <li><b>Corners.</b> Then the wall's corners, the one facing the threat first: a corner post off the corner sees
 *       along two faces of the wall and out over the approach.</li>
 *   <li><b>No inner guns.</b> A gun inside a base stands on the perimeter or has a standoff job (below); the guns
 *       once posted beside the motor pool and command covered nothing an attacker crosses (owner, 2026-11-11:
 *       "What are they guarding?"), so what a place keeps is held by its walls' guns.</li>
 *   <li><b>Wall.</b> A loadout with posts to spare lines the wall out in front of its face, the face toward the
 *       threat first.</li>
 *   <li><b>Depth.</b> Artillery and drone hubs stand deepest and furthest from the main gate, where an attacker has
 *       to get through the wall to reach them: the artillery fires indirect, over the wall, and the hubs launch
 *       drones, so neither needs a line.</li>
 * </ul>
 *
 * <p>Every site faces out ({@link Site#faceX}, {@link Site#faceY}): a gate post along its gate's way out, a corner
 * or wall post away from the nearest ground of the place, a standoff weapon toward the threat. The guns are laid on
 * that bearing before the fight, so they cover the ground in front of the wall.
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
    public enum Role { GATE, CORNER, WALL, DEPTH }

    /**
     * A gate in the wall: its centre on the wall line, the unit step that leads out of the place through it, and half
     * its width beside the centre.
     */
    public record Gate(int x, int y, int outX, int outY, int halfWidth) {
        public Gate {
            if (Math.abs(outX) + Math.abs(outY) != 1) throw new IllegalArgumentException("outward step " + outX + "," + outY);
        }
    }

    /**
     * An emplacement's site: its anchor, why it is there, what stands there, the gate it covers or -1, and the
     * outward step it faces ({@code faceX}, {@code faceY}; never both zero).
     */
    public record Site(int x, int y, Role role, DefensePostKind kind, int gate, int faceX, int faceY) {
        public Site {
            if (faceX == 0 && faceY == 0) throw new IllegalArgumentException("a site faces somewhere");
        }
    }

    /**
     * Whether an emplacement of a kind can stand with its anchor on a cell, its guns laid along the step
     * {@code (faceX, faceY)}: the caller checks the ground and that the lane ahead is open on the map.
     */
    public interface Fits {
        boolean at(int x, int y, DefensePostKind kind, int faceX, int faceY);
    }

    /**
     * Cells out from the place a direct-fire post's anchor stands: clear of the wall with a heavy post's box and its
     * verge between them. UNTUNED.
     */
    public static final int STANDOFF_OUT = 5;
    /** Cells of open ground a perimeter post's lane runs ahead of it before anything may stand in it. UNTUNED. */
    public static final int LANE = 10;
    /** Degrees either side of a perimeter post's bearing its lane must also be open: its laid arc. UNTUNED. */
    public static final int ARC_DEGREES = 20;
    /** Cells beside a gate's own half-width its flanking posts stand, so the pair clears {@link #SPACING}. UNTUNED. */
    static final int FLANK_OFFSET = 6;
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
     * @param inside  the place's ground, its wall line included, map-sized
     * @param gates   the open gates, the main gate first
     * @param hearts  what the place keeps (motor pool, command); no gun is sited on them, kept for the callers
     * @param threat  where an attack comes from (the landing), or {@code null} to take the main gate's outward line
     * @param posts   how many of each kind the fortification holds
     * @param fits    whether the ground takes an emplacement; the caller folds the keep-clear mask into it
     * @return the sites, in the order they should be stamped
     */
    public static List<Site> plan(boolean[][] inside, List<Gate> gates, List<int[]> hearts, int[] threat,
                                  Map<DefensePostKind, Integer> posts, Fits fits) {
        int w = inside.length, h = inside[0].length;
        List<Site> sites = new ArrayList<>();
        if (bounds(inside) == null) return sites;
        int[] centre = centroid(inside);
        int[][] depth = depth(inside);
        int deepest = 0;
        for (int[] col : depth) for (int d : col) deepest = Math.max(deepest, d);
        // The ring a corner or wall post stands on, out in front of the wall's face.
        int[][] beyond = beyond(inside);
        List<int[]> ring = new ArrayList<>();
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) if (beyond[x][y] == STANDOFF_OUT) ring.add(new int[]{x, y});
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
                        gate.x() + gate.outX() * STANDOFF_OUT + alongX * lateral,
                        gate.y() + gate.outY() * STANDOFF_OUT + alongY * lateral,
                        Role.GATE.ordinal(), g});
            }
        }
        for (int[] corner : corners(ring, centre, toward)) {
            wanted.add(new int[]{corner[0], corner[1], Role.CORNER.ordinal(), -1});
        }
        // Then along the wall, the face toward the threat first.
        List<int[]> along = new ArrayList<>(ring);
        if (toward != null) {
            double tx = toward[0] - centre[0], ty = toward[1] - centre[1];
            along.sort(Comparator.comparingDouble((int[] c) -> -((c[0] - centre[0]) * tx + (c[1] - centre[1]) * ty))
                    .thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
        }
        for (int[] cell : along) wanted.add(new int[]{cell[0], cell[1], Role.WALL.ordinal(), -1});

        List<DefensePostKind> perimeter = expand(posts, PERIMETER);
        int next = 0;
        for (int[] want : wanted) {
            if (next >= perimeter.size()) break;
            DefensePostKind kind = perimeter.get(next);
            boolean exact = want[2] == Role.WALL.ordinal();   // a ring cell is itself a candidate, not a target
            Gate covered = want[3] >= 0 ? gates.get(want[3]) : null;
            Facing facing = (x, y) -> covered != null ? new int[]{covered.outX(), covered.outY()} : away(inside, x, y);
            int[] at;
            if (exact) {
                int[] f = facing.at(want[0], want[1]);
                at = !inside[want[0]][want[1]] && lane(inside, want[0], want[1], f[0], f[1])
                        && fits.at(want[0], want[1], kind, f[0], f[1]) && !crowded(want[0], want[1], sites)
                        ? new int[]{want[0], want[1]} : null;
            } else {
                at = resolve(want[0], want[1], kind, inside, sites, fits, facing);
            }
            if (at == null) continue;
            int[] face = facing.at(at[0], at[1]);
            sites.add(new Site(at[0], at[1], Role.values()[want[2]], kind, want[3], face[0], face[1]));
            next++;
        }

        // Depth: deepest ground, furthest from the main gate.
        List<DefensePostKind> standoff = expand(posts, STANDOFF);
        if (!standoff.isEmpty()) {
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
                int[] face = toward != null && (toward[0] != cell[0] || toward[1] != cell[1])
                        ? new int[]{toward[0] - cell[0], toward[1] - cell[1]} : outward(inside, cell[0], cell[1]);
                if (!fits.at(cell[0], cell[1], kind, face[0], face[1]) || crowded(cell[0], cell[1], sites)) continue;
                sites.add(new Site(cell[0], cell[1], Role.DEPTH, kind, -1, face[0], face[1]));
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

    /**
     * The place's four corners: the cells of the ring {@link #STANDOFF_OUT} out from the wall furthest out along each
     * diagonal, so a grown claim's corners are its own and not its bounding box's. The one facing the threat first.
     */
    private static List<int[]> corners(List<int[]> ring, int[] centre, int[] toward) {
        List<int[]> out = new ArrayList<>();
        if (ring.isEmpty()) return out;
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                int[] best = null;
                for (int[] c : ring) {
                    if (best == null || sx * c[0] + sy * c[1] > sx * best[0] + sy * best[1]) best = c;
                }
                out.add(best);
            }
        }
        if (toward != null) {
            double tx = toward[0] - centre[0], ty = toward[1] - centre[1];
            out.sort(Comparator.comparingDouble((int[] c) -> -((c[0] - centre[0]) * tx + (c[1] - centre[1]) * ty)));
        }
        return out;
    }

    /** The bearing a perimeter post at a cell is laid on. */
    private interface Facing {
        int[] at(int x, int y);
    }

    /**
     * The nearest cell to {@code (x, y)} outside the place the ground takes with its lane open, clear of sites
     * already chosen, or null.
     */
    private static int[] resolve(int x, int y, DefensePostKind kind, boolean[][] inside, List<Site> sites, Fits fits,
                                 Facing facing) {
        int w = inside.length, h = inside[0].length;
        for (int r = 0; r <= RESOLVE_RADIUS; r++) {
            int[] best = null;
            long bestD = Long.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h || inside[nx][ny]) continue;
                    int[] f = facing.at(nx, ny);
                    if (!lane(inside, nx, ny, f[0], f[1])) continue;
                    if (!fits.at(nx, ny, kind, f[0], f[1]) || crowded(nx, ny, sites)) continue;
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

    /**
     * The step from {@code (x, y)} to the nearest cell that is not inside (off the map counts as outside): the way
     * out over the wall a post there faces. Straight up when nothing outside is found, which a place with ground
     * never gives.
     */
    static int[] outward(boolean[][] inside, int x, int y) {
        int w = inside.length, h = inside[0].length;
        int reach = Math.max(w, h);
        for (int r = 1; r <= reach; r++) {
            int[] best = null;
            long bestD = Long.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    boolean out = nx < 0 || ny < 0 || nx >= w || ny >= h || !inside[nx][ny];
                    long d = (long) dx * dx + (long) dy * dy;
                    if (out && d < bestD) {
                        bestD = d;
                        best = new int[]{dx, dy};
                    }
                }
            }
            if (best != null) return best;
        }
        return new int[]{0, 1};
    }

    /**
     * Whether a post at {@code (x, y)} laid along the step {@code (fx, fy)} has its lane open over the place's
     * outside: rays at its bearing and {@link #ARC_DEGREES} either side, each {@link #LANE} cells long, never cross the
     * place (its wall line included) and never leave the map: past the edge no attacker comes, so a gun laid that way
     * covers nothing (a garrison backed into the map's corner once lined its edge-side walls with guns facing off it).
     */
    public static boolean lane(boolean[][] inside, int x, int y, int fx, int fy) {
        return lane((cx, cy) -> inside[cx][cy], inside.length, inside[0].length, x, y, fx, fy, 0);
    }

    /** A cell a lane may not cross. */
    @FunctionalInterface
    public interface Blocks {
        boolean at(int x, int y);
    }

    /**
     * Whether a post at {@code (x, y)} laid along the step {@code (fx, fy)} has its lane open: rays at its bearing and
     * {@link #ARC_DEGREES} either side, each from {@code from} cells out (past the post's own footprint) to
     * {@link #LANE}, meet no cell that {@code blocked} names and stay on the map ({@code w} by {@code h}): past the
     * edge no attacker comes.
     */
    public static boolean lane(Blocks blocked, int w, int h, int x, int y, int fx, int fy, int from) {
        double bearing = Math.atan2(fy, fx);
        for (int turn = -1; turn <= 1; turn++) {
            double a = bearing + Math.toRadians(ARC_DEGREES * turn);
            double ux = Math.cos(a), uy = Math.sin(a);
            for (int t = from; t <= LANE; t++) {
                int cx = (int) Math.round(x + ux * t), cy = (int) Math.round(y + uy * t);
                if (cx < 0 || cy < 0 || cx >= w || cy >= h || blocked.at(cx, cy)) return false;
            }
        }
        return true;
    }

    /**
     * The step from the nearest cell of the place to {@code (x, y)}, a cell outside it: the way a post there faces,
     * out from the wall over the ground in front of it.
     */
    static int[] away(boolean[][] inside, int x, int y) {
        int w = inside.length, h = inside[0].length;
        for (int r = 1; r <= Math.max(w, h); r++) {
            int[] best = null;
            long bestD = Long.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h || !inside[nx][ny]) continue;
                    long d = (long) dx * dx + (long) dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        best = new int[]{-dx, -dy};
                    }
                }
            }
            if (best != null) return best;
        }
        return new int[]{0, 1};
    }

    /** Steps from the place to each cell outside it, 0 inside; a cell no step reaches keeps -1. */
    static int[][] beyond(boolean[][] inside) {
        int w = inside.length, h = inside[0].length;
        int[][] out = new int[w][h];
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                out[x][y] = inside[x][y] ? 0 : -1;
                if (inside[x][y]) queue[tail++] = x * h + y;
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int c = queue[head++];
            int cx = c / h, cy = c % h;
            for (int[] s : steps) {
                int nx = cx + s[0], ny = cy + s[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || out[nx][ny] != -1) continue;
                out[nx][ny] = out[cx][cy] + 1;
                queue[tail++] = nx * h + ny;
            }
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
