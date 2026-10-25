package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.staffed.Loading;
import com.dillon.starsectormarines.battle.staffed.LoadingPressure;
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
 * A loading's own bore ({@code set-piece-blocks.md}, Lanes and pressure): a bore opened out of sight down
 * the street from a freight yard, whose broods a director sends at the yard down the yard's two ways in,
 * the vehicle gate and the side gap, rather than letting them wander into the colony. The mission's swarm
 * commander is never told about the loading; this owns its own bodies and hands back the survivors when
 * the objective ends.
 *
 * <p>Paced to the loading, not the field: the bore is held, so it never surges on its own clock or swells
 * the mission's population target, and this calls its surges ({@link SwarmReinforcementSystem#surgeScripted}),
 * before the tremor as after it. A small probe goes every forty seconds while the crew start; at half the
 * count a wave; when the truck sets off a rush, and everything it owns is sent ahead of the truck along
 * its road out. Collapsing the bore stops it, which is the gamble
 * a guard can take.
 *
 * <p>Serial: advanced from the objective board inside the breach sequence's step.
 */
public final class BorePressure implements LoadingPressure {

    /** Bore numbers this pressure opens from, clear of the breach's own eruption numbers. */
    public static final int NUMBER = 90;
    /** Seconds after the loading starts before the bore opens. */
    static final float OPEN_SECONDS = 10f;
    /** Street steps from the yard gate the bore opens at, nearest and farthest. */
    static final int SITE_MIN = 28, SITE_MAX = 45;
    /** Cells out from a way in that a lane's first waypoint stands. */
    static final int APPROACH = 6;
    /** A driven body this near its waypoint is sent on to the next. */
    static final float REACH = 3f;
    /** Ticks between looks for the bore's new bodies. */
    static final int SCAN_TICKS = 10;
    /** Surge weights: a probe while the loading starts, the wave at half the count, the rush at departure. */
    static final float PROBE = 3f, WAVE = 7f, RUSH = 12f;
    /** Seconds between probes until the wave goes. */
    static final float PROBE_SECONDS = 40f;
    /** How far along the truck's road out the departure rush is sent, as a share of the road. */
    static final float AMBUSH_ALONG = 0.4f;

    private final int[] site;
    private final List<int[]> gateLane;
    private final List<int[]> sideLane;
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

    private BorePressure(int[] site, List<int[]> gateLane, List<int[]> sideLane) {
        this.site = site;
        this.gateLane = gateLane;
        this.sideLane = sideLane;
        this.bodyPrefix = "Bore " + NUMBER + " ";
    }

    /**
     * The pressure against a loading at {@code bay}, or {@link LoadingPressure#NONE} where no street
     * within reach of the gate has room for a bore.
     */
    public static LoadingPressure against(BattleSimulation sim, LoadingBay bay) {
        if (bay == null) return NONE;
        NavigationGrid grid = sim.getGrid();
        int cx = Math.round((bay.standX() + bay.doorX() + .5f) / 2f), cy = Math.round((bay.standY() + bay.doorY() + .5f) / 2f);
        int[] site = site(grid, sim.getTopology(), bay.gateX(), bay.gateY(), SITE_MIN, SITE_MAX, cx, cy);
        if (site == null) return NONE;
        List<int[]> gate = List.of(outFrom(grid, bay.gateX(), bay.gateY(), cx, cy), new int[]{bay.gateX(), bay.gateY()},
                new int[]{bay.doorX(), bay.doorY()});
        List<int[]> side = List.of(outFrom(grid, bay.sideX(), bay.sideY(), cx, cy), new int[]{bay.sideX(), bay.sideY()},
                new int[]{bay.doorX(), bay.doorY()});
        return new BorePressure(site, gate, side);
    }

    @Override
    public void advance(BattleSimulation sim, Loading loading, int tick) {
        if (startTick < 0) startTick = tick;
        if (!opened && tick - startTick >= Math.round(OPEN_SECONDS / BattleSimulation.TICK_DT)) {
            sim.swarmBores().open(sim, NUMBER, false, site[0], site[1]);
            opened = true;
        }
        SwarmBore bore = bore(sim);
        if (bore != null && !bore.collapsed()) {
            if (!held) {
                // Its surges are the director's to stream but this pressure's to call, on the loading's beats.
                sim.swarmBores().hold(bore);
                held = true;
            }
            if (!departed && loading.phase() == Loading.Phase.DEPARTING) {
                departed = sim.surgeScripted(bore, RUSH);
            } else if (!halfway && loading.delivered() * 2 >= loading.target()) {
                halfway = sim.surgeScripted(bore, WAVE);
            } else if (!halfway && tick >= nextProbeTick && sim.surgeScripted(bore, PROBE)) {
                nextProbeTick = tick + Math.round(PROBE_SECONDS / BattleSimulation.TICK_DT);
            }
        }
        if (loading.phase() == Loading.Phase.DEPARTING && ambush == null) ambush = ambush(loading);
        if ((tick - startTick) % SCAN_TICKS == 0) adopt(sim);
        steer(sim);
    }

    @Override
    public void end(BattleSimulation sim) {
        for (long body : lane.keySet()) sim.swarmDrives().release(body);
        lane.clear();
        waypoint.clear();
    }

    /** Where the bore opens. */
    public int[] site() { return site; }

    /** Whether the bore has been opened. */
    public boolean opened() { return opened; }

    /** Bodies this pressure is steering now. */
    public int steering() { return lane.size(); }

    private SwarmBore bore(BattleSimulation sim) {
        for (SwarmBore b : sim.swarmBores().bores()) if (!b.mother() && b.number() == NUMBER) return b;
        return null;
    }

    /** Takes on the bore's bodies not yet steered, alternately down the gate and the side gap. */
    private void adopt(BattleSimulation sim) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long body = sim.liveUnitAt(i);
            if (lane.containsKey(body)) continue;
            if (!fighter(sim, body)) continue;
            lane.put(body, assigned++ % 2);
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
                List<int[]> path = entry.getValue() == 0 ? gateLane : sideLane;
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

    /** A cell on the truck's road out, ahead of it past the gate. */
    private static int[] ambush(Loading loading) {
        float[][] road = loading.exitRoute();
        int i = Math.min(road[0].length - 1, Math.max(0, Math.round((road[0].length - 1) * AMBUSH_ALONG)));
        return new int[]{(int) Math.floor(road[0][i]), (int) Math.floor(road[1][i])};
    }

    /** The walkable cell {@link #APPROACH} cells out from {@code (x, y)}, away from the yard's middle. */
    static int[] outFrom(NavigationGrid grid, int x, int y, int cx, int cy) {
        float dx = x - cx, dy = y - cy;
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
