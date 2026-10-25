package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectivePressure;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An objective's own bore ({@code set-piece-blocks.md}, Lanes and pressure): a bore opened out of sight down
 * the street from a set piece, whose broods a director sends at it down the set piece's ways in, rather than
 * letting them wander into the colony. The mission's swarm commander is never told about the objective; this
 * owns its own bodies and hands back the survivors when the objective ends.
 *
 * <p>Three things make one, all the set piece's: the <b>site rule</b> (open street {@code min} to
 * {@code max} steps' walk from a cell, the reachable cell farthest from the set piece), the <b>lanes</b>
 * (each a walk of waypoints: a point out in the street, the way in, the goal inside), and the objective's
 * <b>beats</b> ({@link ObjectivePressure.Beat}).
 *
 * <p>Paced to the objective, not the field: the bore is held, so it never surges on its own clock or swells
 * the mission's population target, and this calls its surges ({@link SwarmReinforcementSystem#surgeScripted}),
 * before the tremor as after it. A small probe goes every forty seconds until the objective is half done;
 * at half a wave; at its final stretch a rush, and everything it owns is sent where the objective says.
 * Collapsing the bore stops it, which is the gamble a guard can take.
 *
 * <p>Serial: advanced from the objective board inside the mission sequence's step.
 */
public final class BorePressure implements ObjectivePressure {

    /** The loading's bore number, clear of the breach's own eruption numbers; other objectives count up from it. */
    public static final int NUMBER = 90;
    /** Seconds after the objective starts before the bore opens. */
    static final float OPEN_SECONDS = 10f;
    /** Street steps from the yard gate the loading's bore opens at, nearest and farthest. */
    public static final int SITE_MIN = 28, SITE_MAX = 45;
    /** Cells out from a way in that a lane's first waypoint stands. */
    static final int APPROACH = 6;
    /** A driven body this near its waypoint is sent on to the next. */
    static final float REACH = 3f;
    /** Ticks between looks for the bore's new bodies. */
    static final int SCAN_TICKS = 10;

    /**
     * When the bore surges and how hard, in the director's weight: a probe every {@code probeSeconds} until
     * the objective is {@code waveAt} of the way through, the wave then, and the rush at its final stretch.
     */
    public record Pacing(float probe, float wave, float rush, float probeSeconds, float waveAt) {
        /** The loading's pacing, the first one tuned against a guard. */
        public static final Pacing LOADING = new Pacing(3f, 7f, 12f, 40f, .5f);
    }

    private final int number;
    private final int[] site;
    private final List<List<int[]>> lanes;
    private final Pacing pacing;
    private final Map<Long, Integer> lane = new HashMap<>();
    private final Map<Long, Integer> waypoint = new HashMap<>();
    private final String bodyPrefix;
    private int startTick = -1;
    private boolean opened;
    private boolean held;
    private int nextProbeTick;
    private boolean halfway;
    private boolean departed;
    private int[] ambush;
    private int assigned;

    private BorePressure(int number, int[] site, List<List<int[]>> lanes, Pacing pacing) {
        this.number = number;
        this.site = site;
        this.lanes = List.copyOf(lanes);
        this.pacing = pacing;
        this.bodyPrefix = "Bore " + number + " ";
    }

    /**
     * The pressure against a loading at {@code bay}, or {@link ObjectivePressure#NONE} where no street
     * within reach of the gate has room for a bore: the gate lane (out in the street, the gate, the door)
     * and the side lane (out past the gap, the gap, the door).
     */
    public static ObjectivePressure against(BattleSimulation sim, LoadingBay bay) {
        if (bay == null) return NONE;
        NavigationGrid grid = sim.getGrid();
        int cx = Math.round((bay.standX() + bay.doorX() + .5f) / 2f), cy = Math.round((bay.standY() + bay.doorY() + .5f) / 2f);
        List<int[]> gate = List.of(outFrom(grid, bay.gateX(), bay.gateY(), cx, cy), new int[]{bay.gateX(), bay.gateY()},
                new int[]{bay.doorX(), bay.doorY()});
        List<int[]> side = List.of(outFrom(grid, bay.sideX(), bay.sideY(), cx, cy), new int[]{bay.sideX(), bay.sideY()},
                new int[]{bay.doorX(), bay.doorY()});
        return of(sim, NUMBER, new int[]{bay.gateX(), bay.gateY()}, SITE_MIN, SITE_MAX, new int[]{cx, cy},
                List.of(gate, side), Pacing.LOADING);
    }

    /**
     * A pressure of bore {@code number}, opened on street {@code min} to {@code max} steps' walk from
     * {@code from} as far as it can be from {@code away} (the set piece's middle), its broods sent down
     * {@code lanes} in turn; {@link ObjectivePressure#NONE} where no street in reach has room for a bore or
     * there is no lane.
     */
    public static ObjectivePressure of(BattleSimulation sim, int number, int[] from, int min, int max, int[] away,
                                       List<List<int[]>> lanes, Pacing pacing) {
        if (lanes.isEmpty()) return NONE;
        int[] site = site(sim.getGrid(), sim.getTopology(), from[0], from[1], min, max, away[0], away[1]);
        return site == null ? NONE : new BorePressure(number, site, lanes, pacing);
    }

    /** A lane in at a way {@code (x, y)}: a point {@link #APPROACH} out along {@code (dx, dy)}, the way, the goal. */
    public static List<int[]> lane(NavigationGrid grid, int x, int y, float dx, float dy, int goalX, int goalY) {
        return List.of(out(grid, x, y, dx, dy), new int[]{x, y}, new int[]{goalX, goalY});
    }

    @Override
    public void advance(BattleSimulation sim, Beat beat, int tick) {
        if (startTick < 0) startTick = tick;
        if (!opened && tick - startTick >= Math.round(OPEN_SECONDS / BattleSimulation.TICK_DT)) {
            sim.swarmBores().open(sim, number, false, site[0], site[1]);
            opened = true;
        }
        SwarmBore bore = bore(sim);
        if (bore != null && !bore.collapsed()) {
            if (!held) {
                // Its surges are the director's to stream but this pressure's to call, on the objective's beats.
                sim.swarmBores().hold(bore);
                held = true;
            }
            if (!departed && beat.finalStretch()) {
                departed = sim.surgeScripted(bore, pacing.rush());
            } else if (!halfway && beat.progress() >= pacing.waveAt()) {
                halfway = sim.surgeScripted(bore, pacing.wave());
            } else if (!halfway && tick >= nextProbeTick && sim.surgeScripted(bore, pacing.probe())) {
                nextProbeTick = tick + Math.round(pacing.probeSeconds() / BattleSimulation.TICK_DT);
            }
        }
        if (beat.finalStretch() && ambush == null) ambush = beat.rushTo();
        if ((tick - startTick) % SCAN_TICKS == 0) adopt(sim);
        steer(sim);
    }

    @Override
    public void end(BattleSimulation sim) {
        for (long body : lane.keySet()) sim.swarmDrives().release(body);
        lane.clear();
        waypoint.clear();
    }

    /** The bore's number. */
    public int number() { return number; }

    /** Where the bore opens. */
    public int[] site() { return site; }

    /** Whether the bore has been opened. */
    public boolean opened() { return opened; }

    /** Bodies this pressure is steering now. */
    public int steering() { return lane.size(); }

    private SwarmBore bore(BattleSimulation sim) {
        for (SwarmBore b : sim.swarmBores().bores()) if (!b.mother() && b.number() == number) return b;
        return null;
    }

    /** Takes on the bore's bodies not yet steered, down each lane in turn. */
    private void adopt(BattleSimulation sim) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long body = sim.liveUnitAt(i);
            if (lane.containsKey(body)) continue;
            if (!fighter(sim, body)) continue;
            lane.put(body, assigned++ % lanes.size());
            waypoint.put(body, 0);
        }
    }

    /** One of this bore's fighting broods: not its structures, spires or workers. */
    public boolean fighter(BattleSimulation sim, long body) {
        String name = sim.identity().name(body);
        return name != null && name.startsWith(bodyPrefix) && sim.role().role(body) == UnitRole.SWARM_PRESSURE
                && sim.identity().type(body) != UnitType.SWARM_WORKER;
    }

    /** Moves each steered body's goal on as it reaches it; the dead are dropped. */
    private void steer(BattleSimulation sim) {
        List<Long> gone = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : lane.entrySet()) {
            long body = entry.getKey();
            if (!sim.getRoster().isLive(body)) {
                gone.add(body);
                continue;
            }
            int[] goal;
            if (ambush != null) {
                goal = ambush;
            } else {
                List<int[]> path = lanes.get(entry.getValue());
                int at = waypoint.get(body);
                int[] next = path.get(at);
                float dx = sim.world().x(body) - (next[0] + .5f), dy = sim.world().y(body) - (next[1] + .5f);
                if (dx * dx + dy * dy <= REACH * REACH && at < path.size() - 1) waypoint.put(body, ++at);
                goal = path.get(at);
            }
            sim.swarmDrives().drive(body, goal[0], goal[1]);
        }
        for (long body : gone) {
            sim.swarmDrives().release(body);
            lane.remove(body);
            waypoint.remove(body);
        }
    }

    /** The walkable cell {@link #APPROACH} cells out from {@code (x, y)}, away from the yard's middle. */
    static int[] outFrom(NavigationGrid grid, int x, int y, int cx, int cy) {
        return out(grid, x, y, x - cx, y - cy);
    }

    /** The walkable cell {@link #APPROACH} cells out from {@code (x, y)} along {@code (dx, dy)}, or nearer; the way itself where none is. */
    static int[] out(NavigationGrid grid, int x, int y, float dx, float dy) {
        float length = (float) Math.max(1e-3, Math.hypot(dx, dy));
        for (int step = APPROACH; step >= 1; step--) {
            int ox = Math.round(x + dx / length * step), oy = Math.round(y + dy / length * step);
            if (grid.inBounds(ox, oy) && grid.isWalkable(ox, oy)) return new int[]{ox, oy};
        }
        return new int[]{x, y};
    }

    /**
     * Where the bore opens: open outdoor ground {@code min} to {@code max} steps' walk from the yard gate
     * {@code (fromX, fromY)}, the cell of those farthest from the yard {@code (awayX, awayY)} as the crow
     * flies, so the broods have a street's walk to come down. Null where no such ground is in reach.
     */
    static int[] site(NavigationGrid grid, CellTopology topology, int fromX, int fromY, int min, int max,
                      int awayX, int awayY) {
        if (!grid.inBounds(fromX, fromY) || !grid.isWalkable(fromX, fromY)) return null;
        int w = grid.getWidth(), h = grid.getHeight();
        int[] steps = new int[w * h];
        Arrays.fill(steps, -1);
        ArrayDeque<int[]> open = new ArrayDeque<>();
        steps[fromY * w + fromX] = 0;
        open.add(new int[]{fromX, fromY});
        int[] best = null;
        long bestFar = -1;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] at = open.poll();
            int s = steps[at[1] * w + at[0]];
            if (s >= min) {
                long far = (long) (at[0] - awayX) * (at[0] - awayX) + (long) (at[1] - awayY) * (at[1] - awayY);
                if (far > bestFar) {
                    bestFar = far;
                    best = at;
                }
            }
            if (s >= max) continue;
            for (int[] d : dirs) {
                int nx = at[0] + d[0], ny = at[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || steps[ny * w + nx] >= 0) continue;
                if (!grid.isWalkable(nx, ny) || topology.hasBuildingKindHint(nx, ny)
                        || topology.getGroundKind(nx, ny) == CellTopology.GroundKind.INDOOR) continue;
                steps[ny * w + nx] = s + 1;
                open.add(new int[]{nx, ny});
            }
        }
        return best;
    }
}
