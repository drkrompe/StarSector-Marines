package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Line's refugees ({@code cordon-nouns.md}, Refugees): groups of people
 * leaving the rear villages on the timetable and walking to the rear edge,
 * behind the line. A group is out when its last member has left the map with
 * at least one of them alive, caught the first time the swarm kills one.
 *
 * <p>They are walked, not commanded: each member is given a path to its exit
 * and stepped along it every tick, as the rescue cohort is walked to its lift.
 * On the rear edge a member leaves the battle. Out on the road they are prey,
 * harvested as any colonist the swarm reaches.
 */
public final class CordonRefugees {

    /** People in one group. */
    public static final int GROUP_SIZE = 6;
    /** Rows from the rear edge that count as off the map. */
    private static final int EXIT_BAND = 1;

    /** Where one group starts and leaves. */
    public record Route(int fromX, int fromY, int exitX, int exitY) { }

    private static final class Group {
        final int number;
        final Route route;
        final List<Long> members = new ArrayList<>();
        int out, lost;
        boolean moving, resolved, caught;

        Group(int number, Route route) {
            this.number = number;
            this.route = route;
        }
    }

    private final List<Integer> departSeconds;
    private final List<Group> groups = new ArrayList<>();
    private final int rearRow;

    /**
     * @param routes        one route per departure, in departure order
     * @param departSeconds when each group sets off
     * @param rearRow       the row of the rear edge
     */
    public CordonRefugees(List<Route> routes, List<Integer> departSeconds, int rearRow) {
        this.departSeconds = List.copyOf(departSeconds);
        this.rearRow = rearRow;
        for (int i = 0; i < Math.min(routes.size(), departSeconds.size()); i++) {
            groups.add(new Group(i + 1, routes.get(i)));
        }
    }

    public int groups() { return groups.size(); }

    /** People who left the map alive. */
    public int out() {
        int n = 0;
        for (Group g : groups) n += g.out;
        return n;
    }

    /** People the swarm killed on the road. */
    public int lost() {
        int n = 0;
        for (Group g : groups) n += g.lost;
        return n;
    }

    /** Whether every group has set off and is out or gone. */
    public boolean allResolved() {
        for (Group g : groups) if (!g.resolved) return false;
        return true;
    }

    /** One tick: groups due set off, everyone on the road takes a step, the ones at the edge leave. */
    void advance(BattleSimulation sim, int tick, Consumer<String> beat) {
        for (Group group : groups) {
            if (!group.moving && tick >= CordonSequence.ticks(departSeconds.get(group.number - 1))) {
                depart(sim, group);
                beat.accept("refugees_moving_" + group.number);
            }
            if (!group.moving || group.resolved) continue;
            walk(sim, group, beat);
        }
    }

    private void depart(BattleSimulation sim, Group group) {
        group.moving = true;
        List<int[]> cells = CordonGround.cluster(sim.getGrid(), sim.getOccupancyMap(),
                group.route.fromX(), group.route.fromY(), GROUP_SIZE);
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            group.members.add(sim.spawn(new EntitySpec("Refugee " + group.number + "-" + (i + 1),
                    Faction.CIVILIAN, UnitType.CIVILIAN, cell[0], cell[1]).role(UnitRole.VIP)));
        }
        if (group.members.isEmpty()) group.resolved = true;
    }

    private void walk(BattleSimulation sim, Group group, Consumer<String> beat) {
        NavigationGrid grid = sim.getGrid();
        for (int i = 0; i < group.members.size(); ) {
            long id = group.members.get(i);
            if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) {
                group.members.remove(i);
                group.lost++;
                if (!group.caught) {
                    group.caught = true;
                    beat.accept("refugees_caught_" + group.number);
                }
                continue;
            }
            int x = sim.world().cellX(id), y = sim.world().cellY(id);
            if (Math.abs(y - rearRow) <= EXIT_BAND) {
                sim.clearPath(id);
                sim.takeOffTheField(id);
                group.members.remove(i);
                group.out++;
                continue;
            }
            int[] path = sim.movement().path(id);
            boolean elsewhere = Paths.isEmpty(path) || Paths.destX(path) != group.route.exitX()
                    || Paths.destY(path) != group.route.exitY();
            boolean spent = sim.movement().pathIdx(id) >= Paths.cellCount(path);
            if ((elsewhere || spent) && sim.movement().mayRepath(id) && grid.inBounds(x, y)) {
                sim.setPath(id, sim.findPath(x, y, group.route.exitX(), group.route.exitY()));
            }
            sim.advanceMovement(id);
            i++;
        }
        if (group.members.isEmpty()) {
            group.resolved = true;
            if (group.out > 0) beat.accept("refugees_out_" + group.number);
        }
    }
}
