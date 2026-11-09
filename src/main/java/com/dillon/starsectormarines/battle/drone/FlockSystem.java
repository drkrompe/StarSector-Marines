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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps every {@link Flock} on the field, in the serial part of the tick: forgets members shot down, sends a flock
 * home when it is under a third of its strength, moves each anchor for its task, rebuilds the field its members
 * descend when the anchor's cell has changed, publishes the roster the members read, and gathers what the members
 * see for the commander.
 *
 * <p>The anchor moves at {@link #ANCHOR_SPEED}, slower than a member flies, and waits while the flock's centre is more
 * than {@link #LAG_CELLS} behind the open cell the field leads to, so a body split round a building is not left behind by its own anchor. On a
 * strike the anchor sits on the target. The field is integrated from the open cell nearest the anchor, so a target
 * under a roof is wrapped from the open ground round it and never reached.
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
    private int nextFlock;
    private int nextMember;

    /** Every flock on the field, in launch order. */
    public List<Flock> flocks() {
        return List.copyOf(flocks);
    }

    /** Whether a low flier may be in a cell of this map: open ground, not a wall and not under an intact roof. */
    public static FlockField.Open open(NavigationGrid grid, CellTopology topology) {
        return (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y)
                && (topology == null || !topology.isRoofIntact(x, y));
    }

    /**
     * Puts a flock of {@code count} swarm drones up at {@code (x, y)} for {@code side}, homed there, on a leash of
     * {@code leashCells}; it starts screening its home. Members are placed on open cells round the point.
     */
    public Flock launch(BattleSimulation sim, Faction side, float x, float y, int count, FlockWeights weights,
                        float leashCells) {
        Flock flock = new Flock(nextFlock++, side, weights, x, y, leashCells);
        flock.squadId = sim.mintSquad(side, UnitType.DRONE);
        sim.getSquad(flock.squadId).droneAnchor = flock;
        FlockField.Open open = open(sim.getGrid(), sim.getTopology());
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        int placed = 0;
        for (int r = 0; r <= LAUNCH_SEARCH && placed < count; r++) {
            for (int dy = -r; dy <= r && placed < count; dy++) {
                for (int dx = -r; dx <= r && placed < count; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r || !open.open(cx + dx, cy + dy)) continue;
                    if (spawnMember(sim, flock, cx + dx, cy + dy) != 0L) placed++;
                }
            }
        }
        flocks.add(flock);
        publish(flock, sim, true);
        return flock;
    }

    private long spawnMember(BattleSimulation sim, Flock flock, int cx, int cy) {
        EntitySpec spec = Drone.create("flock-" + flock.id() + "-" + nextMember++, flock.side(), cx, cy, 0L,
                        DroneLook.SWARM)
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
            if (flock.members.isEmpty()) {
                flock.disband();
                it.remove();
                continue;
            }
            if (flock.task() != FlockTask.RETURN && flock.size() * 3 < flock.strength()) flock.returnHome();
            moveAnchor(flock, dt, sim);
            publish(flock, sim, false);
            if (scan) flock.publishContacts(seen(flock, sim));
        }
    }

    private static void moveAnchor(Flock flock, float dt, BattleSimulation sim) {
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
            flock.moveAnchor(goal[0], goal[1]);
            return;
        }
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

    private static float[] centre(Flock flock, World world) {
        float sx = 0f, sy = 0f;
        for (long m : flock.members) {
            sx += world.x(m);
            sy += world.y(m);
        }
        return new float[]{sx / flock.size(), sy / flock.size()};
    }

    private static void publish(Flock flock, BattleSimulation sim, boolean force) {
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
        FlockField.Open open = open(sim.getGrid(), sim.getTopology());
        int[] source = nearestOpen(flock.anchorX(), flock.anchorY(), open, FIELD_RADIUS / 2);
        FlockField field = flock.field();
        int tick = sim.getSimTickIndex();
        boolean moved = source != null && (field == null
                || field.sourceX() != source[0] || field.sourceY() != source[1]);
        if (source != null && (force || field == null || (moved && tick - flock.fieldTick >= FIELD_TICKS))) {
            field = FlockField.build(source[0], source[1], FIELD_RADIUS, open);
            flock.fieldTick = tick;
        }
        long strike = flock.task() == FlockTask.STRIKE ? flock.target() : 0L;
        flock.publish(ids, at, vel, field, strike);
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
