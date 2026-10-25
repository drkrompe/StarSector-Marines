package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.ambient.Shift;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A party of the colony's people standing together where an objective put them, and sent on where it says
 * ({@code set-piece-blocks.md}, Lanes and pressure). Each stands at a place of their own and talks with the
 * rest. They hold their ground until something of the swarm comes within {@link #YIELD_RADIUS} cells, then
 * run as any colonist does ({@link UnitRole#FLEE}) and come back once it has been quiet a while; a guard
 * that keeps the swarm off them keeps them together.
 *
 * <p>Civilians, never counted toward the mission's result: what came of them is the objective's.
 */
final class Gathering {

    /** How near the swarm comes before a person runs. */
    static final float YIELD_RADIUS = 5f;
    /** How far from the middle a place to stand is looked for, in cells. */
    static final int REACH = 10;
    /** How long a person stands at a place before the loop of one asks them to stand there again. */
    private static final float DWELL_SECONDS = 20f;

    private final String prefix;
    private final List<Long> people;

    private Gathering(String prefix, List<Long> people) {
        this.prefix = prefix;
        this.people = people;
    }

    /** One person spawned standing at each of {@code places}, facing {@code (focusX, focusY)}. */
    static Gathering spawn(BattleSimulation sim, String prefix, List<int[]> places, float focusX, float focusY) {
        List<Long> people = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            int[] at = places.get(i);
            long actor = sim.spawn(new EntitySpec(prefix + " " + (i + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                    at[0], at[1]).role(UnitRole.FLEE));
            sim.ambientTasks().assign(actor, stand(prefix + "-" + i, i, at[0], at[1], focusX, focusY));
            people.add(actor);
        }
        if (!people.isEmpty()) sim.ambientTasks().settle();
        return new Gathering(prefix, people);
    }

    /** Sends person {@code i} to stand at {@code (x, y)}: they walk there and stay. */
    void send(BattleSimulation sim, int i, int x, int y, float focusX, float focusY) {
        long actor = people.get(i);
        if (!sim.getRoster().isLive(actor)) return;
        sim.ambientTasks().reroute(actor, stand(prefix + "-" + i, i, x, y, focusX, focusY));
    }

    /** Lets everyone still standing go: they are ordinary colonists again. */
    void disperse(BattleSimulation sim) {
        for (long actor : people) if (sim.getRoster().isLive(actor)) sim.ambientTasks().release(actor);
    }

    /** How many are left alive. */
    int alive(BattleSimulation sim) {
        int n = 0;
        for (long actor : people) if (sim.getRoster().isLive(actor)) n++;
        return n;
    }

    /** Whether person {@code i} is alive and within {@code within} cells of {@code (x, y)}. */
    boolean at(BattleSimulation sim, int i, int x, int y, float within) {
        long actor = people.get(i);
        if (!sim.getRoster().isLive(actor)) return false;
        return Math.hypot(sim.world().x(actor) - (x + .5f), sim.world().y(actor) - (y + .5f)) <= within;
    }

    /** Whether person {@code i} is alive. */
    boolean alive(BattleSimulation sim, int i) {
        return sim.getRoster().isLive(people.get(i));
    }

    /** How many people. */
    int size() { return people.size(); }

    /** The people, in order. */
    List<Long> people() { return people; }

    private static AmbientTaskRoute stand(String id, int i, int x, int y, float focusX, float focusY) {
        return new AmbientTaskRoute(id, i * .9f, Shift.WALK_SPEED, YIELD_RADIUS, AmbientThreatPolicy.HOSTILE_COMBATANT,
                List.of(new AmbientTaskRoute.Stop(x + .5f, y + .5f, DWELL_SECONDS, AmbientActivity.SOCIALIZING,
                        focusX, focusY)));
    }

    /**
     * Up to {@code count} places for people to stand around {@code (x, y)}: open outdoor ground within
     * {@link #REACH} cells of it, nearest first, never a doorway, no two touching. The search crosses what it
     * cannot stand on, so a fountain or a bandstand at the middle is stood round.
     */
    static List<int[]> placesAround(NavigationGrid grid, CellTopology topology, int x, int y, int count) {
        List<int[]> places = new ArrayList<>();
        if (!grid.inBounds(x, y)) return places;
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{x, y});
        seen[y * w + x] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty() && places.size() < count) {
            int[] at = open.poll();
            if (standable(grid, topology, at[0], at[1])) {
                boolean apart = true;
                for (int[] p : places) if (Math.max(Math.abs(p[0] - at[0]), Math.abs(p[1] - at[1])) < 2) apart = false;
                if (apart) places.add(at);
            }
            for (int[] d : dirs) {
                int nx = at[0] + d[0], ny = at[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny * w + nx]) continue;
                if (Math.max(Math.abs(nx - x), Math.abs(ny - y)) > REACH) continue;
                seen[ny * w + nx] = true;
                open.add(new int[]{nx, ny});
            }
        }
        return places;
    }

    private static boolean standable(NavigationGrid grid, CellTopology topology, int x, int y) {
        return grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                && topology.getGroundKind(x, y) != CellTopology.GroundKind.INDOOR;
    }
}
