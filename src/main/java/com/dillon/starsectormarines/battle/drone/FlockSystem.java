package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps every {@link Flock} on the field, in the serial part of the tick: forgets members shot down, has its cradle
 * rebuild them slowly while the flock is home ({@link FlockCradle}), sends a flock
 * home when it is under a third of its strength, moves each anchor for its task, rebuilds the field its members
 * descend when the anchor's cell has changed, publishes the roster the members read, and gathers what the members
 * see for the commander.
 *
 * <p>The anchor moves at {@link #ANCHOR_SPEED}, slower than a member flies, and waits while the flock's centre is more
 * than {@link #LAG_CELLS} behind the open cell the field leads to, so a body split round a building is not left behind by its own anchor. On a
 * strike the anchor sits on the target, or on its threshold when the target is under a roof: the open cell where
 * the target's way out meets open ground, so a flock waits at a doorway or breach and never goes in. Any other goal
 * under a roof (a station, a route point, a home inside a megastructure) is held at its threshold the same way. The
 * field is integrated from the open cell nearest the anchor.
 */
public final class FlockSystem {

    /** Light: a burst from a rifle drops one. */
    public static final float SWARM_HP = 10f;
    /** One light gun with a short reach, cells. */
    public static final float SWARM_RANGE_CELLS = 12f;
    /** A low flier sees walls as anyone on the ground does: only the wall it is beside is transparent to it. */
    public static final float SWARM_AIR_LOS_CELLS = 0.8f;
    /** How far a member sees, cells. Every member is a sensor. */
    public static final float SWARM_SIGHT_CELLS = 30f;
    /** How fast a flock's anchor moves, cells per second. */
    public static final float ANCHOR_SPEED = 3.8f;
    /** The anchor waits while the flock's centre is further behind it than this, cells. */
    public static final float LAG_CELLS = 8f;
    /** The anchor has arrived at a route point within this distance, cells. */
    static final float ARRIVE_CELLS = 1.5f;
    /** The field's reach round the anchor, cells. */
    public static final int FIELD_RADIUS = 48;
    /** Fewest ticks between two field rebuilds of one flock. */
    static final int FIELD_TICKS = 5;
    /** Ticks between two sight scans. */
    static final int SCAN_TICKS = 6;
    /** How far from its anchor a launched member may be placed, cells. */
    private static final int LAUNCH_SEARCH = 6;

    private final List<Flock> flocks = new ArrayList<>();
    /** Each cradle a flock was launched with, by unit, to its livery; a cradle outlives a wiped-out flock. */
    private final Map<Long, FlockLivery> cradles = new HashMap<>();
    private int nextFlock;
    private int nextMember;

    /** Every flock on the field, in launch order. */
    public List<Flock> flocks() {
        return List.copyOf(flocks);
    }

    /** Whether a low flier may be in a cell of this map: open ground or low cover, never a wall or under an intact roof. */
    public static FlockField.Open open(NavigationGrid grid, CellTopology topology) {
        return (x, y) -> grid.inBounds(x, y) && (topology == null || !topology.isRoofIntact(x, y))
                && (grid.isWalkable(x, y) || lowObstacle(grid, topology, x, y));
    }

    /**
     * Whether a cell is closed to walking only by something a low flier passes over: sandbags, a fence, crates, a
     * barricade, a parked hull, water. Never a wall, a window, a fixture (a shelf run, a large rock), a tree or boulder
     * (a natural solid) or the void. A swarm drone crosses low cover; it never crosses walls or goes under a roof.
     */
    static boolean lowObstacle(NavigationGrid grid, CellTopology topology, int x, int y) {
        return topology != null && !topology.isWall(x, y)
                && !topology.hasTag(x, y, CellTopology.Tag.WINDOW)
                && !topology.hasTag(x, y, CellTopology.Tag.FIXTURE)
                && !grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)
                && topology.getGroundKind(x, y) != CellTopology.GroundKind.VOID;
    }

    /** The ground a cradle may stand on: open to the flock and walkable, so never on the low cover it flies over. */
    static FlockField.Open ground(NavigationGrid grid, FlockField.Open open) {
        return (x, y) -> open.open(x, y) && grid.isWalkable(x, y);
    }

    /**
     * Puts a flock of {@code count} swarm drones up at {@code (x, y)} for {@code side}, homed there, on a leash of
     * {@code leashCells}; it starts screening its home. Members are placed on open cells round the point. A point under
     * a roof (a zone node inside a megastructure) puts the flock up at its threshold instead, the entrance its ground
     * route out comes to, and that is its home.
     */
    public Flock launch(BattleSimulation sim, Faction side, float x, float y, int count, FlockWeights weights,
                        float leashCells) {
        return launch(sim, side, x, y, count, weights, leashCells, FlockLivery.DOMAIN);
    }

    /** As {@link #launch(BattleSimulation, Faction, float, float, int, FlockWeights, float)}, in a lineage's livery. */
    public Flock launch(BattleSimulation sim, Faction side, float x, float y, int count, FlockWeights weights,
                        float leashCells, FlockLivery livery) {
        NavigationGrid grid = sim.getGrid();
        FlockField.Open open = sky(sim);
        // A flock is put up outside: a launch point under a roof moves to its threshold, which is then its home.
        FlockField.Open walk = (cx, cy) -> grid.inBounds(cx, cy) && grid.isWalkable(cx, cy);
        int[] door = null;
        if (!open.open((int) Math.floor(x), (int) Math.floor(y))) {
            // Each flock of a side takes its own entrance where there is more than one; a lone door is shared.
            FlockField.Open unheld = (cx, cy) -> open.open(cx, cy) && !held(side, cx + .5f, cy + .5f);
            door = FlockField.threshold((int) Math.floor(x), (int) Math.floor(y), reach(grid), walk, unheld);
            if (door == null) door = FlockField.threshold((int) Math.floor(x), (int) Math.floor(y), reach(grid), walk, open);
        }
        if (door != null) {
            x = door[0] + .5f;
            y = door[1] + .5f;
        }
        // The cradle stands set back in the open, and is the flock's home: drones land on it and leave from it.
        int[] site = FlockCradle.site((int) Math.floor(x), (int) Math.floor(y), ground(grid, open), LAUNCH_SEARCH);
        if (site != null) {
            x = site[0] + .5f;
            y = site[1] + .5f;
        }
        Flock flock = new Flock(nextFlock++, side, weights, x, y, leashCells);
        flock.livery = livery;
        flock.squadId = sim.mintSquad(side, UnitType.DRONE);
        sim.getSquad(flock.squadId).droneAnchor = flock;
        // Members go up on the open cells nearest home by the open ground itself, so a cradle at a cramped entrance
        // fills from the street beyond it rather than from cells behind a wall; cells are shared when there are few.
        List<int[]> cells = openAround((int) Math.floor(x), (int) Math.floor(y), open, count);
        for (int i = 0; i < count && !cells.isEmpty(); i++) {
            int[] cell = cells.get(i % cells.size());
            spawnMember(sim, flock, cell[0], cell[1]);
        }
        flocks.add(flock);
        flock.cradleId = sim.spawn(FlockCradle.create("flock-cradle-" + flock.id(), side,
                (int) Math.floor(x), (int) Math.floor(y)));
        if (flock.cradleId != 0L) cradles.put(flock.cradleId, livery);
        publish(flock, sim, true);
        return flock;
    }

    /** The livery of the flock whose cradle is {@code unitId}, or null when that unit is no flock's cradle. */
    public FlockLivery cradleLivery(long unitId) {
        return cradles.get(unitId);
    }

    /** How near another flock's home an entrance counts as already held, cells. */
    static final float HELD_CELLS = 8f;

    private boolean held(Faction side, float x, float y) {
        for (Flock other : flocks) {
            if (other.side() == side && Math.hypot(other.homeX() - x, other.homeY() - y) <= HELD_CELLS) return true;
        }
        return false;
    }

    /** One rebuilt drone from the cradle joins the flock when its clock says so. */
    void rebuild(Flock flock, float dt, BattleSimulation sim) {
        boolean cradle = cradleStands(flock, sim);
        if (!flock.rebuild.advance(dt, cradle, flock.nodeStands(), home(flock), flock.size() + flock.aboard(),
                flock.strength())) return;
        // A flock home to dock takes its new drone aboard; one sent out has it join in the air.
        if (flock.task() == FlockTask.RETURN) {
            flock.dock.board();
            return;
        }
        int[] cell = nearestOpen(flock.homeX(), flock.homeY(), sky(sim), LAUNCH_SEARCH);
        if (cell != null) spawnMember(sim, flock, cell[0], cell[1]);
    }

    /**
     * Docking (`FlockDock`): a flock going home lands each member that reaches its cradle, taking it off the field
     * (it leaves, it does not die); a flock sent out puts its drones aboard back up from the cradle.
     */
    private void dock(Flock flock, float dt, BattleSimulation sim) {
        boolean cradle = cradleStands(flock, sim);
        boolean home = home(flock);
        World world = sim.world();
        for (Iterator<Long> it = flock.members.iterator(); it.hasNext(); ) {
            long m = it.next();
            float d = (float) Math.hypot(world.x(m) - flock.homeX(), world.y(m) - flock.homeY());
            if (!FlockDock.lands(flock.task(), home, cradle, d)) continue;
            it.remove();
            sim.takeOffTheField(m);
            flock.dock.board();
        }
        int up = flock.dock.launches(dt, flock.task(), cradle);
        for (int i = 0; i < up; i++) spawnMember(sim, flock, (int) Math.floor(flock.homeX()), (int) Math.floor(flock.homeY()));
    }

    private static boolean cradleStands(Flock flock, BattleSimulation sim) {
        return flock.cradleId != 0L && sim.resolveUnit(flock.cradleId) != 0L;
    }

    /** Whether the flock's anchor is at home, where its cradle stands. */
    private static boolean home(Flock flock) {
        return Math.hypot(flock.anchorX() - flock.homeX(), flock.anchorY() - flock.homeY()) <= FlockCradle.HOME_CELLS;
    }

    private long spawnMember(BattleSimulation sim, Flock flock, int cx, int cy) {
        EntitySpec spec = Drone.create("flock-" + flock.id() + "-" + nextMember++, flock.side(), cx, cy, 0L,
                        flock.livery().drone())
                .squad(flock.squadId)
                .health(SWARM_HP)
                .attackRange(SWARM_RANGE_CELLS)
                .airLosRadius(SWARM_AIR_LOS_CELLS)
                .visionRange(SWARM_SIGHT_CELLS);
        long id = sim.spawn(spec);
        if (id == 0L) return 0L;
        flock.joined(id);
        if (sim.resolveUnit(sim.getSquad(flock.squadId).leaderId) == 0L) sim.getSquad(flock.squadId).leaderId = id;
        return id;
    }

    public void tick(float dt, BattleSimulation sim) {
        if (flocks.isEmpty()) return;
        boolean scan = sim.getSimTickIndex() % SCAN_TICKS == 0;
        for (Iterator<Flock> it = flocks.iterator(); it.hasNext(); ) {
            Flock flock = it.next();
            flock.members.removeIf(m -> sim.resolveUnit(m) == 0L);
            dock(flock, dt, sim);
            rebuild(flock, dt, sim);
            if (flock.members.isEmpty()) {
                if (flock.aboard() > 0) {
                    publish(flock, sim, false);
                    continue;
                }
                flock.disband();
                it.remove();
                continue;
            }
            if (flock.task() != FlockTask.RETURN && (flock.size() + flock.aboard()) * 3 < flock.strength()) {
                flock.returnHome();
            }
            moveAnchor(flock, dt, sim);
            publish(flock, sim, false);
            if (scan) {
                long[] seen = seen(flock, sim);
                int[] cells = new int[2 * seen.length];
                for (int i = 0; i < seen.length; i++) {
                    cells[2 * i] = sim.world().cellX(seen[i]);
                    cells[2 * i + 1] = sim.world().cellY(seen[i]);
                }
                flock.publishContacts(seen, cells, sim.getSimTickIndex());
            }
        }
    }

    private void moveAnchor(Flock flock, float dt, BattleSimulation sim) {
        World world = sim.world();
        float tx = flock.anchorX(), ty = flock.anchorY();
        FlockTask task = flock.task();
        if (task == FlockTask.STRIKE || task == FlockTask.PURSUE) {
            long target = flock.target();
            if (target == 0L || sim.resolveUnit(target) == 0L) {
                flock.screen(flock.anchorX(), flock.anchorY());
            } else {
                tx = world.x(target);
                ty = world.y(target);
            }
        }
        float[] goal = flock.goal(tx, ty);
        if (flock.task() == FlockTask.STRIKE) {
            int[] hold = threshold(flock, tx, ty, sim);
            if (hold != null) {
                flock.moveAnchor(hold[0] + .5f, hold[1] + .5f);
            } else {
                flock.moveAnchor(goal[0], goal[1]);
            }
            return;
        }
        // A station, route point or home under a roof is kept from its threshold: a flock homed on a zone inside a
        // megastructure waits at the entrance its ground route out comes to, and never goes in.
        int[] door = threshold(flock, goal[0], goal[1], sim);
        if (door != null) goal = new float[]{door[0] + .5f, door[1] + .5f};
        // The lag is to where the members are actually flying: the field's source, the open cell nearest the anchor.
        // Measured to the anchor itself, an anchor over a roof would wait for a flock that can never reach it.
        float[] centre = centre(flock, world);
        FlockField field = flock.field();
        float sx = field != null ? field.sourceX() + .5f : flock.anchorX();
        float sy = field != null ? field.sourceY() + .5f : flock.anchorY();
        float lag = (float) Math.hypot(centre[0] - sx, centre[1] - sy);
        float dx = goal[0] - flock.anchorX(), dy = goal[1] - flock.anchorY();
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d <= ARRIVE_CELLS) {
            flock.arrived();
            return;
        }
        if (lag > LAG_CELLS) return;
        float step = Math.min(d, ANCHOR_SPEED * dt);
        flock.moveAnchor(flock.anchorX() + dx / d * step, flock.anchorY() + dy / d * step);
    }

    /**
     * Where a strike on a target under a roof is held: the open cell where the target's way out by the ground meets
     * open ground ({@link FlockField#threshold}). Null when the target stands in the open, or has no way out at all,
     * the field's reach, when the anchor sits on it as on any strike.
     */
    private int[] threshold(Flock flock, float tx, float ty, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        FlockField.Open open = sky(sim);
        int cx = (int) Math.floor(tx), cy = (int) Math.floor(ty);
        if (open.open(cx, cy)) {
            flock.threshold = null;
            flock.thresholdForX = cx;
            flock.thresholdForY = cy;
            return null;
        }
        if (cx != flock.thresholdForX || cy != flock.thresholdForY) {
            flock.threshold = FlockField.threshold(cx, cy, reach(grid),
                    (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y), open);
            flock.thresholdForX = cx;
            flock.thresholdForY = cy;
        }
        return flock.threshold;
    }

    private static float[] centre(Flock flock, World world) {
        float sx = 0f, sy = 0f;
        for (long m : flock.members) {
            sx += world.x(m);
            sy += world.y(m);
        }
        return new float[]{sx / flock.size(), sy / flock.size()};
    }

    private void publish(Flock flock, BattleSimulation sim, boolean force) {
        World world = sim.world();
        int n = flock.size();
        long[] ids = new long[n];
        float[] at = new float[2 * n];
        float[] vel = new float[2 * n];
        for (int i = 0; i < n; i++) {
            long m = flock.members.get(i);
            ids[i] = m;
            AirBody body = world.kinematics(m);
            at[2 * i] = body != null ? body.x : world.x(m);
            at[2 * i + 1] = body != null ? body.y : world.y(m);
            vel[2 * i] = body != null ? body.vx : 0f;
            vel[2 * i + 1] = body != null ? body.vy : 0f;
        }
        FlockField.Open open = sky(sim);
        int[] source = nearestOpen(flock.anchorX(), flock.anchorY(), open, FIELD_RADIUS / 2);
        FlockField field = flock.field();
        int tick = sim.getSimTickIndex();
        boolean moved = source != null && (field == null
                || field.sourceX() != source[0] || field.sourceY() != source[1]);
        if (source != null && (force || field == null || (moved && tick - flock.fieldTick >= FIELD_TICKS))) {
            field = FlockField.build(source[0], source[1], FIELD_RADIUS, open);
            flock.fieldTick = tick;
        }
        boolean striking = flock.task() == FlockTask.STRIKE;
        long target = striking || flock.task() == FlockTask.PURSUE ? flock.target() : 0L;
        // A strike on a target under a roof is held at its threshold: members flock there and shoot what they see,
        // with no orbit ring to fly round a room they cannot enter.
        long strike = striking && flock.threshold == null ? target : 0L;
        flock.publish(ids, at, vel, field, strike, target);
    }

    /** A threshold is searched across the whole map: a megastructure's deepest hall may be far from its door. */
    static int reach(NavigationGrid grid) {
        return Math.max(grid.getWidth(), grid.getHeight());
    }

    /** How often the outside is labelled again, ticks: a roof brought down can join a light well to it. */
    static final int SKY_TICKS = 600;
    private NavigationGrid skyGrid;
    private int skyTick = Integer.MIN_VALUE / 2;
    private boolean[] outside;

    /**
     * Where a flock may be: open cells of the <b>outside</b>, the largest body of open ground on the map. A light well
     * or court inside a megastructure is open sky but reached only through its roofed halls, so it is not the
     * outside, and a flock is never put up, stationed or held there.
     */
    FlockField.Open sky(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        FlockField.Open open = open(grid, sim.getTopology());
        int tick = sim.getSimTickIndex();
        if (grid != skyGrid || tick - skyTick >= SKY_TICKS) {
            outside = outside(grid.getWidth(), grid.getHeight(), open);
            skyGrid = grid;
            skyTick = tick;
        }
        boolean[] mask = outside;
        int width = grid.getWidth();
        return (x, y) -> open.open(x, y) && mask[y * width + x];
    }

    /** The cells of the largest 4-connected body of {@code open} cells in a {@code width x height} grid. */
    static boolean[] outside(int width, int height, FlockField.Open open) {
        int[] label = new int[width * height];
        int best = 0, bestSize = 0, next = 0;
        int[] queue = new int[width * height];
        for (int start = 0; start < label.length; start++) {
            if (label[start] != 0 || !open.open(start % width, start / width)) continue;
            int id = ++next, head = 0, tail = 0;
            label[start] = id;
            queue[tail++] = start;
            while (head < tail) {
                int c = queue[head++], x = c % width, y = c / width;
                if (x > 0) tail = visit(c - 1, x - 1, y, id, label, queue, tail, open);
                if (x < width - 1) tail = visit(c + 1, x + 1, y, id, label, queue, tail, open);
                if (y > 0) tail = visit(c - width, x, y - 1, id, label, queue, tail, open);
                if (y < height - 1) tail = visit(c + width, x, y + 1, id, label, queue, tail, open);
            }
            if (tail > bestSize) {
                bestSize = tail;
                best = id;
            }
        }
        boolean[] out = new boolean[label.length];
        for (int i = 0; i < label.length; i++) out[i] = best != 0 && label[i] == best;
        return out;
    }

    private static int visit(int c, int x, int y, int id, int[] label, int[] queue, int tail, FlockField.Open open) {
        if (label[c] != 0 || !open.open(x, y)) return tail;
        label[c] = id;
        queue[tail] = c;
        return tail + 1;
    }

    /** Up to {@code max} open cells reached from {@code (x, y)} over open ground, nearest first; empty if it is closed. */
    static List<int[]> openAround(int x, int y, FlockField.Open open, int max) {
        List<int[]> out = new ArrayList<>();
        if (!open.open(x, y)) {
            int[] near = nearestOpen(x + .5f, y + .5f, open, LAUNCH_SEARCH);
            if (near == null) return out;
            x = near[0];
            y = near[1];
        }
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        frontier.add(new int[]{x, y});
        seen.add(((long) x << 32) ^ (y & 0xffffffffL));
        while (!frontier.isEmpty() && out.size() < max) {
            int[] c = frontier.poll();
            out.add(c);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (!open.open(nx, ny) || !seen.add(((long) nx << 32) ^ (ny & 0xffffffffL))) continue;
                frontier.add(new int[]{nx, ny});
            }
        }
        return out;
    }

    /** The open cell nearest {@code (x, y)} within {@code maxRadius} rings, or null. */
    static int[] nearestOpen(float x, float y, FlockField.Open open, int maxRadius) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        for (int r = 0; r <= maxRadius; r++) {
            int[] best = null;
            float bestD = Float.MAX_VALUE;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r || !open.open(cx + dx, cy + dy)) continue;
                    float ex = cx + dx + .5f - x, ey = cy + dy + .5f - y;
                    float d = ex * ex + ey * ey;
                    if (d < bestD) {
                        bestD = d;
                        best = new int[]{cx + dx, cy + dy};
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Hostiles any member can see, by id: the flock's shared sight. */
    private static long[] seen(Flock flock, BattleSimulation sim) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        NavigationGrid grid = sim.getGrid();
        long[] dense = roster.denseArray();
        long[] out = new long[8];
        int found = 0;
        float r2 = SWARM_SIGHT_CELLS * SWARM_SIGHT_CELLS;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (roster.isRiding(u) || !flock.side().hostileTo(roster.identity().faction(u))) continue;
            float ux = world.x(u), uy = world.y(u);
            for (long m : flock.members) {
                float dx = world.x(m) - ux, dy = world.y(m) - uy;
                if (dx * dx + dy * dy > r2) continue;
                if (!TacticalScoring.canSeePair(grid, world.cellX(m), world.cellY(m), world.cellX(u), world.cellY(u),
                        SWARM_AIR_LOS_CELLS, sim.vision().targetAirLosRadius(u))) continue;
                if (found == out.length) out = Arrays.copyOf(out, found * 2);
                out[found++] = u;
                break;
            }
        }
        long[] seen = Arrays.copyOf(out, found);
        Arrays.sort(seen);
        return seen;
    }
}
