package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Emergency roadblocks ({@code colony-breach-nouns.md}, Roadblock): lines the
 * colony militia throw across a street on an approach to the main redoubt, not
 * round a building.
 *
 * <p>A roadblock stands on a straight run of a street that leads toward the
 * shelter, clear of every junction, and spans the street from frontage to
 * frontage, sidewalks included, so it closes the street rather than leaving its
 * pavements open. From the threat side in: an <b>obstacle row</b> of hedgehogs
 * and crate stacks (sometimes a truck across one lane), closed to movement and
 * open to sight; a <b>sandbag course</b> behind it facing out, the
 * fighting-position run its team mans; and the post's heavy gun at one end,
 * behind the bags, with a floodlight beside it. Where the cohort's walk to the
 * lift crosses the line, its lane is left open. The pieces are the redoubt
 * works' own, laid by {@link RedoubtWorks#lay}.
 *
 * <p>Planning is read-only and refuses rather than half-builds: a site whose
 * line would leave a hole, seat no team, or cut the colony in two is no site.
 */
final class ColonyRoadblocks {

    /** Cells of unchanged street, either side of the works, that keep a roadblock out of a junction. */
    static final int JUNCTION_CLEAR = 4;
    /** Rows the works take along the street: truck tail, obstacles, bags, gun. */
    private static final int BEHIND = 2, AHEAD = 1;
    /** The widest street, frontage to frontage, a line is thrown across: wider is open ground, not a street. */
    static final int MAX_SPAN = 13;
    /** The narrowest asphalt that is a street worth closing. */
    static final int MIN_ASPHALT = 3;
    /** Cells along the street over which the walk to the shelter is measured. */
    private static final int PROBE = 6;
    /** Of {@code 2 * PROBE} cells along the street, how many the walk to the shelter must shorten by. */
    private static final int TOWARD = 8;
    /** The nearest a roadblock stands to the compound (or the shelter, without one). */
    static final int MIN_OUT = 10;
    /** Roadblocks closer than this are one approach. */
    private static final int APART = 24;
    /** Every this many obstacle cells, a crate stack stands in place of a hedgehog. */
    private static final int CRATE_EVERY = 4;
    /** One roadblock in this many drags a truck across a lane. */
    private static final int TRUCK_ONE_IN = 3;
    static final String HEDGEHOG = "doodad.fort-hedgehog";
    static final String CRATES = "doodad.industrial-crate-stack";
    private static final long SEED_SALT = 0x524F4144424C4B53L;

    private ColonyRoadblocks() {}

    /**
     * Whether the colony's roadblocks are thrown up.
     * {@code -Dbattle.colonyBreach.roadblocks=false} is the control.
     */
    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.roadblocks", "true"));
    }

    /**
     * How wide a line is thrown: {@code frontage} (the default) from building to
     * building, sidewalks included; {@code kerb} across the asphalt only, the
     * control that shows what open pavements leave.
     */
    static boolean kerbOnly() {
        return "kerb".equals(System.getProperty("battle.colonyBreach.roadblockSpan", "frontage"));
    }

    /** What the ground offers a line, read off the live map or a test's lambdas. */
    interface Ground {
        boolean inBounds(int x, int y);

        boolean walkable(int x, int y);

        /** A building, a wall or blocked ground: what closes a street's side. */
        boolean frontage(int x, int y);

        /** Open ground a piece can stand on: walkable, no building, door or prop. */
        boolean open(int x, int y);
    }

    /**
     * A planned roadblock. The line crosses the street at {@code (x, y)}, the
     * obstacle row's cell on the street's asphalt centre; {@code (ax, ay)} points
     * along the street inward, from the threat toward the shelter.
     *
     * @param gap obstacle-row cells left open for the cohort's walk
     */
    record Plan(int x, int y, int ax, int ay, List<RedoubtWorks.Piece> obstacles, List<RedoubtWorks.Bag> bags,
                int gunX, int gunY, int lightX, int lightY, int gap,
                int left, int top, int right, int bottom) {

        /** The same works as a redoubt plan, for {@link RedoubtWorks#lay}. */
        RedoubtWorks.Plan asWorks() {
            return new RedoubtWorks.Plan(left, top, right, bottom, obstacles, bags, x, y, gunX, gunY, 0);
        }
    }

    /**
     * The asphalt across the street through {@code (x, y)}, as offsets
     * {@code {lo, hi}} along {@code (px, py)}; or null off the asphalt.
     */
    static int[] asphaltSpan(RedoubtWorks.Roadway road, int x, int y, int px, int py) {
        if (!road.at(x, y)) return null;
        int lo = 0, hi = 0;
        while (road.at(x + px * (lo - 1), y + py * (lo - 1))) lo--;
        while (road.at(x + px * (hi + 1), y + py * (hi + 1))) hi++;
        return new int[]{lo, hi};
    }

    /**
     * Whether the street is the same street for {@code reach} cells either way
     * along {@code (ax, ay)}: the asphalt across it starts and ends on the same
     * lines. A junction, a bend or a widening changes it.
     */
    static boolean straight(RedoubtWorks.Roadway road, int x, int y, int ax, int ay, int from, int to) {
        int px = Math.abs(ay), py = Math.abs(ax);
        int[] here = asphaltSpan(road, x, y, px, py);
        if (here == null) return false;
        for (int k = from; k <= to; k++) {
            int[] there = asphaltSpan(road, x + ax * k, y + ay * k, px, py);
            if (there == null || there[0] != here[0] || there[1] != here[1]) return false;
        }
        return true;
    }

    /**
     * The open cross-section through {@code (x, y)} along {@code (px, py)},
     * from frontage to frontage, as offsets {@code {lo, hi}}; or null when either
     * side runs on for more than {@link #MAX_SPAN} cells in all without meeting
     * a building or a wall, which is open ground and not a street.
     */
    static int[] frontageSpan(Ground ground, int x, int y, int px, int py) {
        if (ground.frontage(x, y)) return null;
        int lo = 0, hi = 0;
        while (true) {
            int nx = x + px * (lo - 1), ny = y + py * (lo - 1);
            if (!ground.inBounds(nx, ny)) return null;
            if (ground.frontage(nx, ny)) break;
            if (hi - --lo + 1 > MAX_SPAN) return null;
        }
        while (true) {
            int nx = x + px * (hi + 1), ny = y + py * (hi + 1);
            if (!ground.inBounds(nx, ny)) return null;
            if (ground.frontage(nx, ny)) break;
            if (++hi - lo + 1 > MAX_SPAN) return null;
        }
        return new int[]{lo, hi};
    }

    /**
     * Lays a line out across the street at {@code (x, y)}, inward along
     * {@code (ax, ay)}: the obstacle row across the span, the course of bags one
     * row in, the gun and its light one row further in at an end. Returns null
     * when the line would leave a hole (open ground nothing could be set on that
     * is not the cohort's lane), when no gun fits, or when the course could not
     * seat a team.
     *
     * @param span    the line's cells as offsets along the perpendicular, {@code {lo, hi}}
     * @param asphalt the street's asphalt as the same offsets, for the truck
     * @param truck   a truck id dragged across a lane, or null; footprint 3 by 2
     */
    static Plan layout(Ground ground, RedoubtWorks.Keep keep, int x, int y, int ax, int ay,
                       int[] span, int[] asphalt, String truck, int seats, long seed) {
        int px = Math.abs(ay), py = Math.abs(ax);
        List<RedoubtWorks.Piece> obstacles = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        if (truck != null) {
            // A truck across one lane, nose to the kerb, its bed toward the threat.
            for (int start : new int[]{asphalt[0], asphalt[1] - 2}) {
                RedoubtWorks.Piece piece = truckAt(ground, keep, x, y, ax, ay, px, py, start, truck);
                if (piece == null) continue;
                obstacles.add(piece);
                for (int fy = piece.y(); fy < piece.y() + piece.height(); fy++)
                    for (int fx = piece.x(); fx < piece.x() + piece.width(); fx++) taken.add(key(fx, fy));
                break;
            }
        }
        int gap = 0, counter = (int) Math.floorMod(seed, CRATE_EVERY);
        for (int s = span[0]; s <= span[1]; s++) {
            int cx = x + px * s, cy = y + py * s;
            if (taken.contains(key(cx, cy))) continue;
            if (!ground.walkable(cx, cy)) continue;
            if (!keep.clear(cx, cy)) {
                gap++;
                continue;
            }
            if (!ground.open(cx, cy)) return null;
            String id = ++counter % CRATE_EVERY == 0 ? CRATES : HEDGEHOG;
            obstacles.add(new RedoubtWorks.Piece(cx, cy, id, 0, 1, 1));
            taken.add(key(cx, cy));
        }
        if (obstacles.isEmpty()) return null;

        // The course one row in, every bag facing the threat.
        String bag = "doodad.fort-sandbag-" + letter(-ax, -ay);
        List<RedoubtWorks.Bag> bags = new ArrayList<>();
        int bx = x + ax, by = y + ay;
        for (int s = span[0]; s <= span[1]; s++) {
            int cx = bx + px * s, cy = by + py * s;
            if (taken.contains(key(cx, cy)) || !ground.open(cx, cy) || !keep.clear(cx, cy)) continue;
            bags.add(new RedoubtWorks.Bag(cx, cy, bag));
            taken.add(key(cx, cy));
        }
        if (bags.size() < seats) return null;

        // The gun behind the bags at an end, on the pavement, its floodlight a cell in from it.
        int gx = bx + ax, gy = by + ay, gunX = -1, gunY = -1, lightX = -1, lightY = -1;
        for (int[] end : new int[][]{{span[1], -1}, {span[0], 1}}) {
            int cx = gx + px * end[0], cy = gy + py * end[0];
            if (!ground.open(cx, cy) || !keep.clear(cx, cy)) continue;
            gunX = cx;
            gunY = cy;
            int lx = cx + px * end[1], ly = cy + py * end[1];
            if (ground.open(lx, ly) && keep.clear(lx, ly)) {
                lightX = lx;
                lightY = ly;
            }
            break;
        }
        if (gunX < 0) return null;

        int l = gunX, t = gunY, r = gunX, b = gunY;
        for (RedoubtWorks.Piece p : obstacles) {
            l = Math.min(l, p.x());
            t = Math.min(t, p.y());
            r = Math.max(r, p.x() + p.width() - 1);
            b = Math.max(b, p.y() + p.height() - 1);
        }
        for (RedoubtWorks.Bag g : bags) {
            l = Math.min(l, g.x());
            t = Math.min(t, g.y());
            r = Math.max(r, g.x());
            b = Math.max(b, g.y());
        }
        return new Plan(x, y, ax, ay, obstacles, bags, gunX, gunY, lightX, lightY, gap, l, t, r, b);
    }

    /** A truck on the obstacle row from offset {@code start}, three cells along the line and two deep toward the threat; or null. */
    private static RedoubtWorks.Piece truckAt(Ground ground, RedoubtWorks.Keep keep, int x, int y, int ax, int ay,
                                              int px, int py, int start, String truck) {
        for (int i = 0; i < 3; i++) {
            for (int d = 0; d < 2; d++) {
                int cx = x + px * (start + i) - ax * d, cy = y + py * (start + i) - ay * d;
                if (!ground.open(cx, cy) || !keep.clear(cx, cy)) return null;
            }
        }
        int ox = x + px * start, oy = y + py * start;
        // Lying along x (a street running north-south) it is unturned; along y, a quarter turn.
        if (px == 1) return new RedoubtWorks.Piece(ox, Math.min(oy, oy - ay), truck, 0, 3, 2);
        return new RedoubtWorks.Piece(Math.min(ox, ox - ax), oy, truck, 1, 2, 3);
    }

    /**
     * The colony's roadblocks: one to three, seeded, on separate approaches to
     * the shelter, nearest the compound first. Read-only.
     *
     * @param compound the civil-defence compound's rectangle {@code {l, t, r, b}}, or null without one
     */
    static List<Plan> plan(BattleSimulation sim, CivilianEvacuationPlacement placement, int[] compound,
                           RedoubtWorks.Keep keep, int seats, long seed) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        if (topology.roadSurface() == null) return List.of();
        RedoubtWorks.Roadway road = RedoubtWorks.roadway(sim);
        Ground ground = ground(sim);
        int w = grid.getWidth(), h = grid.getHeight();
        int[] walk = distances(grid, placement.shelterApproachX, placement.shelterApproachY);
        int reach = Math.min(w, h) / 3;
        int cx = compound == null ? placement.shelterX : (compound[0] + compound[2]) / 2;
        int cy = compound == null ? placement.shelterY : (compound[1] + compound[3]) / 2;
        boolean kerb = kerbOnly();

        record Candidate(int x, int y, int ax, int ay, int out, long priority, int[] span, int[] asphalt) { }
        List<Candidate> candidates = new ArrayList<>();
        int[][] axes = {{1, 0}, {0, 1}};
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (!road.at(x, y)) continue;
                int out = outside(compound, placement, x, y);
                if (out < MIN_OUT || out > reach) continue;
                for (int[] axis : axes) {
                    int px = axis[1], py = axis[0];
                    int[] asphalt = asphaltSpan(road, x, y, px, py);
                    int width = asphalt[1] - asphalt[0] + 1;
                    // One candidate per cross-section: the asphalt's centre cell.
                    if (width < MIN_ASPHALT || asphalt[0] != -(width - 1) / 2) continue;
                    int bx = x - axis[0] * PROBE, by = y - axis[1] * PROBE;
                    int fx = x + axis[0] * PROBE, fy = y + axis[1] * PROBE;
                    if (!grid.inBounds(bx, by) || !grid.inBounds(fx, fy)) continue;
                    int before = walk[grid.index(bx, by)], after = walk[grid.index(fx, fy)];
                    if (before < 0 || after < 0 || Math.abs(before - after) < TOWARD) continue;
                    // Inward is the way the walk to the shelter shortens.
                    int ax = before > after ? axis[0] : -axis[0], ay = before > after ? axis[1] : -axis[1];
                    if (!straight(road, x, y, ax, ay, -AHEAD - JUNCTION_CLEAR, BEHIND + JUNCTION_CLEAR)) continue;
                    int[] span = kerb ? asphalt : frontageSpan(ground, x, y, px, py);
                    if (span == null) continue;
                    candidates.add(new Candidate(x, y, ax, ay, out, priority(seed, grid.index(x, y) * 2 + axis[1]),
                            span, asphalt));
                }
            }
        }
        // Nearest the compound first, by four-cell bands; the seed decides within a band.
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.out / 4)
                .thenComparingLong(Candidate::priority));

        int wanted = 1 + (int) Math.floorMod(priority(seed, -1), 3);
        List<Plan> chosen = new ArrayList<>();
        Set<Long> blocked = new HashSet<>();
        for (Candidate c : candidates) {
            if (chosen.size() >= wanted) break;
            if (!apart(chosen, c.x, c.y, cx, cy)) continue;
            String truck = Math.floorMod(c.priority, TRUCK_ONE_IN) == 0
                    ? RedoubtWorks.TRUCKS[(int) Math.floorMod(c.priority >>> 8, RedoubtWorks.TRUCKS.length)] : null;
            Plan plan = layout(ground, keep, c.x, c.y, c.ax, c.ay, c.span, c.asphalt, truck, seats, c.priority);
            if (plan == null && truck != null)
                plan = layout(ground, keep, c.x, c.y, c.ax, c.ay, c.span, c.asphalt, null, seats, c.priority);
            if (plan == null) continue;
            Set<Long> closed = new HashSet<>(blocked);
            for (RedoubtWorks.Piece p : plan.obstacles())
                for (int y = p.y(); y < p.y() + p.height(); y++)
                    for (int x = p.x(); x < p.x() + p.width(); x++) closed.add(key(x, y));
            closed.add(key(plan.gunX(), plan.gunY()));
            // The street's far end must still be reachable from the shelter's side: the line closes a street, not the colony.
            if (!connected(grid, closed, c.x + c.ax * (BEHIND + 2), c.y + c.ay * (BEHIND + 2),
                    c.x - c.ax * (AHEAD + 2), c.y - c.ay * (AHEAD + 2))) continue;
            chosen.add(plan);
            blocked = closed;
        }
        return chosen;
    }

    /** Whether a site lies on another approach from every roadblock already chosen. */
    private static boolean apart(List<Plan> chosen, int x, int y, int cx, int cy) {
        double bearing = Math.atan2(y - cy, x - cx);
        for (Plan p : chosen) {
            if (Math.max(Math.abs(p.x() - x), Math.abs(p.y() - y)) < APART) return false;
            double diff = Math.abs(Math.atan2(p.y() - cy, p.x() - cx) - bearing);
            if (Math.min(diff, 2 * Math.PI - diff) < Math.toRadians(45)) return false;
        }
        return true;
    }

    /** Chebyshev cells from the compound's rectangle, or from the shelter without one. */
    private static int outside(int[] compound, CivilianEvacuationPlacement placement, int x, int y) {
        if (compound == null) return Math.max(Math.abs(x - placement.shelterX), Math.abs(y - placement.shelterY));
        int dx = Math.max(0, Math.max(compound[0] - x, x - compound[2]));
        int dy = Math.max(0, Math.max(compound[1] - y, y - compound[3]));
        return Math.max(dx, dy);
    }

    /**
     * Throws the roadblocks up on the live map before the first tick: the
     * pieces as the redoubt works lay them, the gun, the floodlight and a
     * militia team on the course under an order to man its run.
     *
     * @return the teams' squads
     */
    static List<Integer> raise(BattleSimulation sim, List<Plan> plans, List<Long> emplacements,
                               RiskLevel risk, Random rng) {
        List<Integer> squads = new ArrayList<>();
        List<NightLighting.Lamp> lamps = new ArrayList<>();
        int n = 0;
        for (Plan plan : plans) {
            n++;
            RedoubtWorks.lay(sim, plan.asWorks());
            emplacements.add(ColonyMilitia.seatEmplacement(sim, new int[]{plan.gunX(), plan.gunY()}, "roadblock-gun-" + n));
            if (plan.lightX() >= 0) lamps.add(CivilDefenceCompound.floodlight(plan.lightX(), plan.lightY(), -plan.ax(), -plan.ay()));
            int team = ColonyRedoubts.seatLine(sim, plan.bags(), plan.gunX(), plan.gunY(),
                    plan.left(), plan.top(), plan.right(), plan.bottom(),
                    "Roadblock " + n, "man the roadblock", risk, rng);
            if (team >= 0) squads.add(team);
        }
        if (!lamps.isEmpty()) CivilDefenceCompound.addLamps(sim, lamps);
        return squads;
    }

    static Ground ground(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        Set<Long> dressed = new HashSet<>();
        for (Doodad d : sim.getDoodads())
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++)
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) dressed.add(key(x, y));
        return new Ground() {
            @Override public boolean inBounds(int x, int y) { return grid.inBounds(x, y); }

            @Override public boolean walkable(int x, int y) { return grid.inBounds(x, y) && grid.isWalkable(x, y); }

            @Override public boolean frontage(int x, int y) {
                return !grid.isWalkable(x, y) || topology.getBuildingId(x, y) != 0 || topology.isWall(x, y);
            }

            @Override public boolean open(int x, int y) {
                return grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                        && topology.getBuildingId(x, y) == 0 && !topology.isFixture(x, y) && !topology.isWall(x, y)
                        && !dressed.contains(key(x, y));
            }
        };
    }

    /** Walking steps from a cell to every cell, -1 where it cannot be walked to. */
    private static int[] distances(NavigationGrid grid, int fromX, int fromY) {
        int w = grid.getWidth();
        int[] out = new int[w * grid.getHeight()];
        Arrays.fill(out, -1);
        if (!grid.inBounds(fromX, fromY)) return out;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        out[grid.index(fromX, fromY)] = 0;
        queue.add(grid.index(fromX, fromY));
        while (!queue.isEmpty()) {
            int i = queue.poll(), x = i % w, y = i / w;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], ny = y + d[1];
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
                int j = grid.index(nx, ny);
                if (out[j] >= 0) continue;
                out[j] = out[i] + 1;
                queue.add(j);
            }
        }
        return out;
    }

    /** Whether {@code to} can be walked to from {@code from} with the given cells closed. */
    private static boolean connected(NavigationGrid grid, Set<Long> closed, int fromX, int fromY, int toX, int toY) {
        if (!grid.inBounds(fromX, fromY) || !grid.inBounds(toX, toY)) return false;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{fromX, fromY});
        seen.add(key(fromX, fromY));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] == toX && c[1] == toY) return true;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                long k = key(nx, ny);
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny) || closed.contains(k) || !seen.add(k)) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return false;
    }

    /** The sandbag art's facing letter for a world direction, as the prepared-ground courses name it. */
    private static char letter(int dx, int dy) {
        if (dy > 0) return 'n';
        if (dy < 0) return 's';
        return dx > 0 ? 'e' : 'w';
    }

    private static long priority(long seed, int cell) {
        long value = (seed ^ SEED_SALT) ^ (cell * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return (value ^ (value >>> 31)) & Long.MAX_VALUE;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
