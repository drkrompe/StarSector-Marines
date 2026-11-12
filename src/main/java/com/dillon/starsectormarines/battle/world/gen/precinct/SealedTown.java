package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.HabitatZoning;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A town on a world nobody lives on in the open ({@code mapgen-nouns.md}, Habitat): the whole town sealed inside one
 * continuous wall, with a couple of airlocks in it, rather than a lock on every building's door. Inside the wall it is
 * an ordinary town of streets, blocks and courts; outside it is bare ground, with no outlying places.
 *
 * <p>The wall is the place's {@link Precinct.Boundary#WALLED boundary}, drawn on the outline of its claim once that is
 * laid straight ({@link #lay}), so the town is a zoned precinct that is walled; that pairing exists for nothing else. It is an envelope,
 * not a line ({@link #envelope}): {@link #WALL_THICKNESS} cells through, on the town's own outermost ground, and each
 * lock is a chamber through it with a door at either end.
 */
public final class SealedTown {

    /**
     * The sealed wall: two locks, where its two widest roads meet it, and no emplacements of its own, since it is where
     * people live rather than a garrison. As hard to cut through as a garrison's wall.
     */
    public static final Fortification LOCKS = new Fortification(2, 240);

    /**
     * Cells through the envelope, and so the length of a lock's chamber: a one-cell outline read as a thin line wrapped
     * round the town rather than a wall that keeps the air in (owner). It stands on the town's own outermost cells,
     * held back from its parcels ({@link #band}), so nothing the town built is cut and it is as thick whatever lies
     * outside.
     */
    public static final int WALL_THICKNESS = 3;

    /**
     * Cells of open ground a region outside the wall holds before it is worth a lock of its own: the field a company
     * lands on, not a sliver left between two claims.
     */
    public static final int GROUND_WORTH_A_LOCK = 100;

    /**
     * The envelope's own wall, its body: a habitat wall standing taller than any compound's, drawn as itself
     * ({@code FreestandingWalls}). Never an installation's perimeter ({@code InstallationWalls.PERIMETER}), which the
     * Cordon reads as a compound's wall.
     */
    public static final String ENVELOPE_WALL = "habitat.envelope-wall";

    /**
     * The envelope's outer course, where it faces the world ({@link #outerCourse}): a lower plinth along its outer
     * face, the body's buttresses ({@link #BUTTRESS_BAY}) standing out of it.
     */
    public static final String ENVELOPE_PLINTH = ENVELOPE_WALL + "@plinth";

    /** A buttress: the body standing out through the plinth to the envelope's outer face ({@link #buttress}). */
    public static final String ENVELOPE_BUTTRESS = ENVELOPE_WALL + "@buttress";

    /** Cells between the buttresses along the envelope's outer course. UNTUNED. */
    public static final int BUTTRESS_BAY = 4;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** The eight cells round a cell: the envelope's depth is counted over these, so its inside corners come out square. */
    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private SealedTown() { }

    /** Cells along a side of one block a sealed town is laid in ({@link #lay}). UNTUNED. */
    public static final int BLOCK = 12;

    /**
     * Cells of free ground a laid town leaves open beside an open place's claim, measured square so the notch it
     * leaves is straight too: what a neighbour builds round its own edge stays clear. UNTUNED.
     */
    public static final int SETBACK = 6;

    /**
     * {@link #SETBACK} beside a walled place the town does not already touch (a Cordon redoubt out on the line): a
     * redoubt's terrace pushes its cliff out past built ground by up to {@code TerracePlanner.MAX_GROWTH} cells beyond
     * its margin, and the envelope's wall is built ground, so a laid town keeps that far back. A walled place the town
     * already touches is set back from by nothing: the envelope does not face a wall, so the town meets it.
     * UNTUNED.
     */
    public static final int WALLED_SETBACK = 24;

    /**
     * Lays each sealed town's claim straight, in place, so its envelope stands on long straight runs: the envelope
     * stands on the town's outermost cells ({@link #band}), and a claim grown along streets gave it a stair-stepped,
     * diagonal outline (owner, 2026-11-11: the fort with that wall was bad). The town is snapped to {@link #BLOCK}-cell
     * blocks from its bounding box: a block where the town holds at least as much as is free is the town's whole
     * (its free ground taken), and in any other block the town gives its cells up, so it keeps its shape in long axis
     * steps (owner pick {@code sealed-town-wall} b). Ground another place claimed stays that place's; ground {@code
     * kept} (may be {@code null}) and ground within a setback of a neighbour ({@link #SETBACK},
     * {@link #WALLED_SETBACK}) is never taken. Runs after the claim and before anything is built, so the town's
     * streets and parcels fill the laid ground.
     *
     * @param sealed which places are sealed towns, by index
     * @param walled which places are walled, by index ({@link #sealedSide})
     */
    public static void lay(int[][] claim, boolean[] sealed, boolean[] walled, boolean[][] kept) {
        int width = claim.length, height = claim[0].length;
        for (int who = 0; who < sealed.length; who++) {
            if (!sealed[who]) continue;
            boolean[][] beside = besideOthers(claim, who, walled);
            boolean[][] held = new boolean[width][height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) held[x][y] = beside[x][y] || kept != null && kept[x][y];
            }
            int l = Integer.MAX_VALUE, b = Integer.MAX_VALUE, r = -1, t = -1;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (claim[x][y] != who) continue;
                    l = Math.min(l, x); b = Math.min(b, y); r = Math.max(r, x); t = Math.max(t, y);
                }
            }
            if (r < 0) continue;
            for (int x0 = l; x0 <= r; x0 += BLOCK) {
                for (int y0 = b; y0 <= t; y0 += BLOCK) {
                    int x1 = Math.min(r, x0 + BLOCK - 1), y1 = Math.min(t, y0 + BLOCK - 1);
                    int mine = 0, free = 0;
                    for (int x = x0; x <= x1; x++) {
                        for (int y = y0; y <= y1; y++) {
                            if (claim[x][y] == who) mine++;
                            else if (claim[x][y] == GrownTrunkPlan.UNOWNED && !held[x][y]) free++;
                        }
                    }
                    boolean town = mine >= free;
                    for (int x = x0; x <= x1; x++) {
                        for (int y = y0; y <= y1; y++) {
                            if (town && claim[x][y] == GrownTrunkPlan.UNOWNED && !held[x][y]) {
                                claim[x][y] = who;
                            } else if (!town && claim[x][y] == who) {
                                claim[x][y] = GrownTrunkPlan.UNOWNED;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Cells within {@link #SETBACK} (square) of ground an open place other than {@code who} claims, and within
     * {@link #WALLED_SETBACK} of a walled one's the town does not already touch.
     */
    private static boolean[][] besideOthers(int[][] claim, int who, boolean[] walled) {
        int width = claim.length, height = claim[0].length;
        boolean[] touched = new boolean[walled.length];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] != who) continue;
                for (int[] s : SIDES) {
                    int nx = x + s[0], ny = y + s[1];
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int c = claim[nx][ny];
                    if (c >= 0 && c < touched.length) touched[c] = true;
                }
            }
        }
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int c = claim[x][y];
                if (c == who || c == GrownTrunkPlan.UNOWNED || !onEdge(claim, x, y)) continue;
                boolean wall = c < walled.length && walled[c];
                if (wall && touched[c]) continue;
                int r = wall ? WALLED_SETBACK : SETBACK;
                for (int nx = Math.max(0, x - r); nx <= Math.min(width - 1, x + r); nx++) {
                    for (int ny = Math.max(0, y - r); ny <= Math.min(height - 1, y + r); ny++) {
                        out[nx][ny] = true;
                    }
                }
            }
        }
        return out;
    }

    /** Whether a claimed cell borders a cell of another claim or the map's edge: what a setback is measured from. */
    private static boolean onEdge(int[][] claim, int x, int y) {
        for (int[] s : SIDES) {
            int nx = x + s[0], ny = y + s[1];
            if (nx < 0 || ny < 0 || nx >= claim.length || ny >= claim[0].length || claim[nx][ny] != claim[x][y]) {
                return true;
            }
        }
        return false;
    }

    /**
     * The envelope's outer course: its cells beside the world outside across a side (what no sealed town or walled place
     * holds, {@link #sealedSide}), not across a lock's chamber or the map's edge.
     *
     * @param wall  the envelope's cells
     * @param claim the place claim with the sealed towns as one place, {@code who}
     * @param side  which places are on the sealed side, by index
     */
    public static boolean[][] outerCourse(boolean[][] wall, int[][] claim, int who, boolean[] side) {
        int width = wall.length, height = wall[0].length;
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!wall[x][y]) continue;
                for (int[] s : SIDES) {
                    int nx = x + s[0], ny = y + s[1];
                    if (nx >= 0 && ny >= 0 && nx < width && ny < height && !wall[nx][ny]
                            && claim[nx][ny] != who && !onSealedSide(claim[nx][ny], side)) {
                        out[x][y] = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * Whether an outer-course cell is a buttress, the body standing out through the plinth: one in every
     * {@link #BUTTRESS_BAY} cells along a straight course.
     */
    public static boolean buttress(int x, int y) {
        return Math.floorMod(x + y, BUTTRESS_BAY) == 0;
    }

    /** Whether the town on this world is sealed: wherever its habitat is enclosed (toxic, airless, irradiated). */
    public static boolean on(TargetProfile profile) {
        return HabitatZoning.forProfile(profile).enclosed();
    }

    /**
     * Whether {@code place} is a sealed town: a zoned place that is walled, which nothing but sealing makes. Its
     * wall's gates are locks.
     */
    public static boolean is(Precinct place) {
        return !place.isProgrammed() && place.boundary() == Precinct.Boundary.WALLED;
    }

    /**
     * A mission's plan on a world nobody lives on in the open ({@code story-map-pools.md}, Sealed layout): every zoned
     * place a mission named (its towns, suburbs, depots and hamlets) sealed inside a wall of its own with
     * {@link #LOCKS}, where it stands, under its own name. Programmed places (garrisons, compounds, workings) and the
     * landing are the mission's own and keep their boundary. A mission's places are its story, so none is dropped,
     * where a derived town on such a world grows no outlying places at all.
     */
    public static PrecinctPlan seal(PrecinctPlan plan) {
        List<Precinct> out = new ArrayList<>();
        for (Precinct p : plan.precincts()) {
            boolean zoned = !p.isProgrammed() && !p.isLanding() && p.boundary() == Precinct.Boundary.OPEN;
            out.add(!zoned ? p : new Precinct(p.name(), p.seedX(), p.seedY(), p.growth(), null,
                    Precinct.Boundary.WALLED, LOCKS, p.character(), p.landing()));
        }
        return plan.withPrecincts(out);
    }

    /**
     * The plan for a map that would lay its town with no plan (a market's town grown alone, as an extraction or a
     * rescue lays it), on a world whose town is sealed ({@link #on}): that one town, grown as the plan-less map grows
     * it and at the map's middle, sealed, and nothing else, so the map gains its wall and locks and no other place.
     * {@code null} where there is no town to seal: no market, or a world people live on in the open.
     */
    public static PrecinctPlan alone(TargetProfile profile, int width, int height) {
        if (profile == null || profile.marketSize() <= 0 || !on(profile)) return null;
        GrownTrunkPlan.Profile growth = GrownTrunkPlan.Profile.of(
                SettlementZoning.densityFor(profile.marketSize()), profile.link());
        return seal(new PrecinctPlan(List.of(Precinct.settlement("settlement", width / 2, height / 2, growth))));
    }

    /**
     * Unclaimed ground the town closes round: every cell no place holds that the map's edge cannot be reached from
     * without crossing the town's own ground ({@code who}). It is the town's, so the envelope runs round the town
     * and never round a pocket inside it.
     */
    public static boolean[][] enclosedGround(int[][] claim, int who) {
        int width = claim.length, height = claim[0].length;
        boolean[][] outside = new boolean[width][height];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (edge && claim[x][y] != who) {
                    outside[x][y] = true;
                    queue.add(new int[]{x, y});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] s : SIDES) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height || outside[nx][ny] || claim[nx][ny] == who)
                    continue;
                outside[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) out[x][y] = !outside[x][y] && claim[x][y] < 0;
        }
        return out;
    }

    /** The world's town, sealed. Its character is decided with every other place's, after placement. */
    static Precinct settlement(int x, int y, GrownTrunkPlan.Profile growth) {
        return new Precinct("settlement", x, y, growth, null, Precinct.Boundary.WALLED, LOCKS);
    }

    /**
     * The locks a sealed town's wall needs besides its road gates: one onto every region of open ground outside it
     * that the wall borders and no gate opens onto. A gate opens only where a road crosses the outline, so a town
     * grown across a map with no road out to the field the company lands on was walled shut against it; ground on
     * both sides of a wall must stay one through its locks.
     *
     * <p>Each is up to {@link PrecinctBoundary#DRIVABLE_GATE_WIDTH} outline cells, sited where the town's open ground
     * runs deepest straight in from the wall, so it opens onto a street or yard rather than a building's side.
     *
     * @param claim the place claim, {@code who} where it is this place's
     * @param open  whether each cell is open ground now, built-on cells and other walls not
     * @param gates the wall's road gates, which serve the regions they open onto
     */
    public static List<PrecinctBoundary.Gate> groundLocks(int[][] claim, int who, boolean[][] open,
                                                          List<PrecinctBoundary.Gate> gates) {
        return groundLocks(claim, who, open, gates, null);
    }

    /**
     * As above, each lock sited where its way in reaches the town's streets ({@link #streetward}) where any spot does,
     * so it never opens into a yard shut in by buildings and the wall.
     *
     * @param streetward the town's ground that joins its streets, or {@code null} to ask only for open ground
     */
    public static List<PrecinctBoundary.Gate> groundLocks(int[][] claim, int who, boolean[][] open,
                                                          List<PrecinctBoundary.Gate> gates,
                                                          boolean[][] streetward) {
        int width = claim.length, height = claim[0].length;
        boolean[][] outline = PrecinctBoundary.outline(claim, who, width, height);
        // The open ground outside, labelled by region.
        int[][] region = new int[width][height];
        List<Integer> sizes = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (region[x][y] != 0 || claim[x][y] == who || !open[x][y]) continue;
                int label = sizes.size() + 1, size = 0;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{x, y});
                region[x][y] = label;
                while (!stack.isEmpty()) {
                    int[] c = stack.pop();
                    size++;
                    for (int[] s : SIDES) {
                        int nx = c[0] + s[0], ny = c[1] + s[1];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height || region[nx][ny] != 0
                                || claim[nx][ny] == who || !open[nx][ny]) continue;
                        region[nx][ny] = label;
                        stack.push(new int[]{nx, ny});
                    }
                }
                sizes.add(size);
            }
        }
        // The town's own parts: a town may stand in several pieces, and each needs its own way in.
        int[][] part = new int[width][height];
        int parts = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (part[x][y] != 0 || claim[x][y] != who) continue;
                parts++;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{x, y});
                part[x][y] = parts;
                while (!stack.isEmpty()) {
                    int[] c = stack.pop();
                    for (int[] s : SIDES) {
                        int nx = c[0] + s[0], ny = c[1] + s[1];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height || part[nx][ny] != 0
                                || claim[nx][ny] != who) continue;
                        part[nx][ny] = parts;
                        stack.push(new int[]{nx, ny});
                    }
                }
            }
        }
        Set<Long> served = new HashSet<>();
        for (PrecinctBoundary.Gate gate : gates) {
            for (int[] c : gate.cells()) {
                for (int r : outward(region, c)) served.add(key(part[c[0]][c[1]], r));
            }
        }
        // Per part and unserved region, the outline cell between them with the deepest open ground straight in.
        Map<Long, int[]> best = new LinkedHashMap<>();
        Map<Long, Integer> bestDepth = new HashMap<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!outline[x][y]) continue;
                for (int[] s : SIDES) {
                    int ox = x + s[0], oy = y + s[1];
                    if (ox < 0 || oy < 0 || ox >= width || oy >= height) continue;
                    int r = region[ox][oy];
                    long k = key(part[x][y], r);
                    if (r == 0 || served.contains(k) || sizes.get(r - 1) < GROUND_WORTH_A_LOCK) continue;
                    // The town's own open ground straight in: the chamber's cells, then the ground it opens onto. The
                    // outline is no stop, since a chamber is carved through it, but a building or a wall is: a lock
                    // whose way in is shallower than the wall ends against a building's side, a dead end.
                    int depth = 0;
                    boolean reaches = false;
                    for (int d = 1; d <= WALL_THICKNESS * 2; d++) {
                        int ix = x - d * s[0], iy = y - d * s[1];
                        if (ix < 0 || iy < 0 || ix >= width || iy >= height || claim[ix][iy] != who
                                || !open[ix][iy]) break;
                        if (d <= WALL_THICKNESS + 1) depth = d;
                        reaches |= streetward != null && d >= WALL_THICKNESS && streetward[ix][iy];
                    }
                    // A way in that reaches the streets beats any that does not, however deep.
                    if (reaches) depth += WALL_THICKNESS * 2;
                    if (depth > bestDepth.getOrDefault(k, 0)) {
                        bestDepth.put(k, depth);
                        best.put(k, new int[]{x, y, s[0], s[1], r});
                    }
                }
            }
        }
        List<PrecinctBoundary.Gate> out = new ArrayList<>();
        for (int[] at : best.values()) {
            int x = at[0], y = at[1], sx = at[2], sy = at[3], r = at[4];
            // Along the wall either side of the best cell, while the way is open on both sides.
            List<int[]> cells = new ArrayList<>(List.of(new int[]{x, y}));
            int ax = sy, ay = sx;
            for (int k = 1; k < PrecinctBoundary.DRIVABLE_GATE_WIDTH; k++) {
                for (int dir : new int[]{1, -1}) {
                    if (cells.size() >= PrecinctBoundary.DRIVABLE_GATE_WIDTH) break;
                    int cx = x + dir * k * ax, cy = y + dir * k * ay;
                    if (alongside(claim, who, open, outline, region, r, cx, cy, sx, sy)) cells.add(new int[]{cx, cy});
                }
            }
            out.add(new PrecinctBoundary.Gate(List.copyOf(cells),
                    cells.size() >= PrecinctBoundary.DRIVABLE_GATE_WIDTH));
        }
        return out;
    }

    /**
     * The town's ground that joins its streets: its open cells off the envelope's ground ({@link #band}) reached from
     * a road of its own without crossing a building or a wall, so a lock opening onto any of them opens onto the town.
     *
     * @param road which place's road each cell is, {@link GrownTrunkPlan#UNOWNED} where none
     */
    public static boolean[][] streetward(int[][] claim, int who, boolean[][] open, boolean[][] band, int[][] road) {
        int width = claim.length, height = claim[0].length;
        boolean[][] out = new boolean[width][height];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] == who && road[x][y] != GrownTrunkPlan.UNOWNED && !band[x][y]) {
                    out[x][y] = true;
                    queue.add(new int[]{x, y});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] s : SIDES) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height || out[nx][ny] || claim[nx][ny] != who
                        || band[nx][ny] || !open[nx][ny] && road[nx][ny] == GrownTrunkPlan.UNOWNED) continue;
                out[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return out;
    }

    private static long key(int part, int region) {
        return ((long) part << 32) | (region & 0xffffffffL);
    }

    /** The regions of open ground a cell's outward sides touch. */
    private static List<Integer> outward(int[][] region, int[] c) {
        List<Integer> out = new ArrayList<>();
        for (int[] s : SIDES) {
            int nx = c[0] + s[0], ny = c[1] + s[1];
            if (nx >= 0 && ny >= 0 && nx < region.length && ny < region[0].length && region[nx][ny] != 0) {
                out.add(region[nx][ny]);
            }
        }
        return out;
    }

    /** Whether the outline cell beside a lock widens it: open into region {@code r} outside and into the town inside. */
    private static boolean alongside(int[][] claim, int who, boolean[][] open, boolean[][] outline, int[][] region,
                                     int r, int x, int y, int sx, int sy) {
        int width = claim.length, height = claim[0].length;
        if (x < 0 || y < 0 || x >= width || y >= height || !outline[x][y]) return false;
        int ox = x + sx, oy = y + sy, ix = x - sx, iy = y - sy;
        return ox >= 0 && oy >= 0 && ox < width && oy < height && region[ox][oy] == r
                && ix >= 0 && iy >= 0 && ix < width && iy < height && claim[ix][iy] == who && open[ix][iy]
                && !outline[ix][iy];
    }

    /**
     * One lock through the envelope: its outer door on the town's outline, its inner door at the envelope's inner face
     * (none where no wall stands beside it there to hang from), the chamber between, and the way out.
     */
    public record Lock(List<int[]> outer, List<int[]> inner, List<int[]> chamber, int outX, int outY) { }

    /** Cells kept clear straight out before a lock and straight in behind it, past its chamber. UNTUNED. */
    public static final int LOCK_APRON = WALL_THICKNESS * 2;

    /**
     * The ground a sealed town keeps for moving through its locks: each chamber and {@link #LOCK_APRON} cells straight
     * out and straight in from it, so nothing the defences build later (a post, a bunker) stands across a lock's way in
     * and leaves it a dead end.
     */
    public static boolean[][] lockWays(Envelope envelope, int width, int height) {
        boolean[][] kept = new boolean[width][height];
        for (Lock lock : envelope.locks()) {
            for (int[] c : lock.chamber()) {
                for (int k = -LOCK_APRON - WALL_THICKNESS; k <= LOCK_APRON; k++) {
                    int x = c[0] + k * lock.outX(), y = c[1] + k * lock.outY();
                    if (x >= 0 && y >= 0 && x < width && y < height) kept[x][y] = true;
                }
            }
        }
        return kept;
    }

    /** Cells a lock's vestibule stands out from the envelope, at either mouth. UNTUNED. */
    public static final int VESTIBULE = 3;

    /**
     * A lock's vestibule at one mouth: the floor straight out of the chamber for {@link #VESTIBULE} cells, as wide as the
     * chamber, and the line of cells flanking it either side, nearest the mouth first; a cell is {@code {x, y}}.
     */
    public record Vestibule(List<int[]> floor, List<int[]> left, List<int[]> right) { }

    /** The vestibule at {@code lock}'s outer mouth, or at its inner one. */
    public static Vestibule vestibule(Lock lock, boolean outward) {
        int ox = lock.outX(), oy = lock.outY();
        int tLo = Integer.MAX_VALUE, tHi = Integer.MIN_VALUE, sLo = Integer.MAX_VALUE, sHi = Integer.MIN_VALUE;
        for (int[] c : lock.chamber()) {
            int t = c[0] * ox + c[1] * oy, s = ox != 0 ? c[1] : c[0];
            tLo = Math.min(tLo, t);
            tHi = Math.max(tHi, t);
            sLo = Math.min(sLo, s);
            sHi = Math.max(sHi, s);
        }
        List<int[]> floor = new ArrayList<>(), left = new ArrayList<>(), right = new ArrayList<>();
        int sign = outward ? 1 : -1, t0 = outward ? tHi : tLo;
        for (int d = 1; d <= VESTIBULE; d++) {
            int t = t0 + sign * d;
            for (int s = sLo; s <= sHi; s++) floor.add(along(lock, t, s));
            left.add(along(lock, t, sLo - 1));
            right.add(along(lock, t, sHi + 1));
        }
        return new Vestibule(floor, left, right);
    }

    /** The cell {@code t} along a lock's way out and {@code s} across it. */
    private static int[] along(Lock lock, int t, int s) {
        return lock.outX() != 0 ? new int[]{t * lock.outX(), s} : new int[]{s, t * lock.outY()};
    }

    /** The sealed wall as built: which cells stand, and its locks. */
    public record Envelope(boolean[][] wall, List<Lock> locks) { }

    /**
     * Which places are already sealed from the world, by index: the sealed towns, and every other walled place, whose
     * own wall keeps the air out where it stands. The envelope faces only what neither holds, so a compound standing in
     * the town is no hole in it.
     */
    public static boolean[] sealedSide(PrecinctPlan plan) {
        boolean[] out = new boolean[plan.precincts().size()];
        for (int i = 0; i < out.length; i++) out[i] = plan.precincts().get(i).boundary() == Precinct.Boundary.WALLED;
        return out;
    }

    /** Whether a cell's claim is on the sealed side ({@link #sealedSide}). */
    private static boolean onSealedSide(int c, boolean[] side) {
        return c >= 0 && c < side.length && side[c];
    }

    /**
     * The ground the envelope stands on: every sealed town's cells within {@link #WALL_THICKNESS} of the world outside,
     * counted in from the outline. The world outside is what no sealed town or walled place holds ({@link #sealedSide});
     * the map's edge is none of it. It is held back from the town's parcels before anything is built
     * ({@code PrecinctSkeletonStage}), so the envelope is built on the town's own ground and is as thick against a lane
     * or a field as against open country.
     *
     * @param claim  the place claim, a place's index where it is that place's
     * @param sealed which places are sealed towns, by index
     * @param side   which places are on the sealed side, by index
     */
    public static boolean[][] band(int[][] claim, boolean[] sealed, boolean[] side) {
        int width = claim.length, height = claim[0].length;
        boolean[][] group = new boolean[width][height];
        boolean[][] within = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int c = claim[x][y];
                group[x][y] = c >= 0 && c < sealed.length && sealed[c];
                within[x][y] = group[x][y] || onSealedSide(c, side);
            }
        }
        int[][] depth = new int[width][height];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!group[x][y]) continue;
                for (int[] s : AROUND) {
                    int nx = x + s[0], ny = y + s[1];
                    if (nx >= 0 && ny >= 0 && nx < width && ny < height && !within[nx][ny]) {
                        depth[x][y] = 1;
                        queue.add(new int[]{x, y});
                        break;
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (depth[c[0]][c[1]] >= WALL_THICKNESS) continue;
            for (int[] s : AROUND) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height || !group[nx][ny] || depth[nx][ny] != 0) continue;
                depth[nx][ny] = depth[c[0]][c[1]] + 1;
                queue.add(new int[]{nx, ny});
            }
        }
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) out[x][y] = depth[x][y] > 0;
        return out;
    }

    /**
     * The envelope round a sealed town: the outline wall (its gates and locks already open) and the band behind it
     * ({@link #band}), less any road that runs in it; every gate's straight way in through the band is the lock's
     * chamber, with a door at each end.
     *
     * @param claim   the place claim, {@code who} where it is the town's
     * @param outline the outline wall, gates open
     * @param band    the ground the envelope stands on
     * @param road    which place's road each cell is, {@link GrownTrunkPlan#UNOWNED} where none
     * @param gates   every way through the outline: road gates and ground locks
     */
    public static Envelope envelope(int[][] claim, int who, boolean[][] outline, boolean[][] band, int[][] road,
                                    List<PrecinctBoundary.Gate> gates) {
        return envelope(claim, who, outline, band, road, gates, null, null);
    }

    /**
     * As above, a lock open to the world on two sides (a one-cell lock on a corner of a stepped wall) cut through
     * toward the deeper open ground straight in, as {@link #groundLocks} sited it, never into a building's side.
     *
     * @param open       whether each cell is open ground now, or {@code null} to take the first way out
     * @param streetward the town's ground that joins its streets ({@link #streetward}), or {@code null}
     */
    public static Envelope envelope(int[][] claim, int who, boolean[][] outline, boolean[][] band, int[][] road,
                                    List<PrecinctBoundary.Gate> gates, boolean[][] open, boolean[][] streetward) {
        int width = claim.length, height = claim[0].length;
        boolean[][] wall = new boolean[width][height];
        boolean[][] mine = new boolean[width][height];
        boolean[][] gate = new boolean[width][height];
        for (PrecinctBoundary.Gate g : gates) for (int[] c : g.cells()) gate[c[0]][c[1]] = true;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                mine[x][y] = claim[x][y] == who;
                wall[x][y] = outline[x][y]
                        || mine[x][y] && band[x][y] && !gate[x][y] && road[x][y] == GrownTrunkPlan.UNOWNED;
            }
        }
        List<Lock> locks = new ArrayList<>();
        for (PrecinctBoundary.Gate g : gates) {
            DefenceDoctrine.Gate way = DefenceDoctrine.gateOf(g.cells(), mine);
            if (way == null) continue;
            int[] out = wayOut(g.cells(), mine, open, streetward, way);
            int ox = out[0], oy = out[1];
            List<int[]> chamber = new ArrayList<>(), inner = new ArrayList<>();
            for (int[] c : g.cells()) {
                chamber.add(c);
                // Through the wall, and on through any of the envelope still across the way (a stepped wall runs
                // deeper than its thickness straight in), so the chamber opens onto the town, not into its wall.
                for (int k = 1; k < WALL_THICKNESS * 2; k++) {
                    int x = c[0] - k * ox, y = c[1] - k * oy;
                    if (x < 0 || y < 0 || x >= width || y >= height || !mine[x][y]) break;
                    if (k >= WALL_THICKNESS && !wall[x][y]) break;
                    wall[x][y] = false;
                    chamber.add(new int[]{x, y});
                    if (k == WALL_THICKNESS - 1) inner.add(new int[]{x, y});
                }
            }
            locks.add(new Lock(List.copyOf(g.cells()), List.copyOf(inner), List.copyOf(chamber), ox, oy));
        }
        // Every chamber carved, the inner door hangs only where the envelope's own wall stands beside it as its jamb.
        List<Lock> settled = new ArrayList<>();
        for (Lock lock : locks) {
            boolean faced = false;
            for (int[] c : lock.inner()) faced |= flanked(wall, c[0], c[1], lock.outX(), lock.outY());
            settled.add(faced ? lock : new Lock(lock.outer(), List.of(), lock.chamber(), lock.outX(), lock.outY()));
        }
        return new Envelope(wall, settled);
    }

    /**
     * The outline less what faces no world outside: the map's edge, where a wall reads as a frame round the map rather
     * than the town's envelope and the town runs on past it, and a walled place's own wall ({@link #sealedSide}).
     *
     * @param claim the place claim with the sealed towns as one place, {@code who}
     * @param side  which places are on the sealed side, by index
     */
    public static boolean[][] facingOut(boolean[][] outline, int[][] claim, int who, boolean[] side) {
        int width = claim.length, height = claim[0].length;
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!outline[x][y]) continue;
                for (int[] s : SIDES) {
                    int nx = x + s[0], ny = y + s[1];
                    if (nx >= 0 && ny >= 0 && nx < width && ny < height && claim[nx][ny] != who
                            && !onSealedSide(claim[nx][ny], side)) {
                        out[x][y] = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * A gate's way out: {@code way}'s, unless another side faces out from as many of its cells, when the side whose way
     * in reaches the town's streets, then the deeper open ground straight in, wins.
     */
    private static int[] wayOut(List<int[]> cells, boolean[][] mine, boolean[][] open, boolean[][] streetward,
                                DefenceDoctrine.Gate way) {
        int[] chosen = {way.outX(), way.outY()};
        if (open == null) return chosen;
        int width = mine.length, height = mine[0].length;
        int[] votes = new int[SIDES.length];
        for (int[] c : cells) {
            for (int s = 0; s < SIDES.length; s++) {
                int nx = c[0] + SIDES[s][0], ny = c[1] + SIDES[s][1];
                boolean outside = nx < 0 || ny < 0 || nx >= width || ny >= height || !mine[nx][ny];
                boolean along = false;
                for (int[] o : cells) along |= o[0] == nx && o[1] == ny;
                if (outside && !along) votes[s]++;
            }
        }
        int most = 0;
        for (int v : votes) most = Math.max(most, v);
        int bestDepth = -1;
        for (int s = 0; s < SIDES.length; s++) {
            if (votes[s] != most) continue;
            int depth = 0;
            for (int[] c : cells) {
                boolean reaches = false;
                for (int d = 1; d <= WALL_THICKNESS * 2; d++) {
                    int ix = c[0] - d * SIDES[s][0], iy = c[1] - d * SIDES[s][1];
                    if (ix < 0 || iy < 0 || ix >= width || iy >= height || !mine[ix][iy] || !open[ix][iy]) break;
                    if (d <= WALL_THICKNESS + 1) depth++;
                    reaches |= streetward != null && d >= WALL_THICKNESS && streetward[ix][iy];
                }
                // As the lock was sited (groundLocks): a way in that reaches the streets beats any that does not.
                if (reaches) depth += WALL_THICKNESS * 2;
            }
            boolean same = SIDES[s][0] == way.outX() && SIDES[s][1] == way.outY();
            if (depth > bestDepth || depth == bestDepth && same) {
                bestDepth = depth;
                chosen = SIDES[s];
            }
        }
        return chosen;
    }

    /** Whether wall stands across the way out beside {@code (x, y)}: the jamb a door hangs from. */
    private static boolean flanked(boolean[][] wall, int x, int y, int ox, int oy) {
        int ax = oy, ay = ox;
        for (int dir : new int[]{1, -1}) {
            int nx = x + dir * ax, ny = y + dir * ay;
            if (nx >= 0 && ny >= 0 && nx < wall.length && ny < wall[0].length && wall[nx][ny]) return true;
        }
        return false;
    }
}
