package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A town's riflemen in the house fronts ({@code cordon-allied-forces.md}, Town): a rifle team just past each
 * redoubt's flank walls, at the windows that look out on the front, or where a town's houses have none there, behind
 * the low cover in front of them (a hedge, a fence, a garden wall: what a rifle fires over, never a wall it cannot).
 * The redoubt holds the road; the houses either side of it are where a town's line would put its riflemen, so the
 * swarm coming round a flank meets rifles before it meets the wall. They hold their posts and never leave them.
 */
public final class CordonHouseFronts {

    /** Cells out past a flank wall a house front is looked for. */
    static final int REACH = 16;
    /** Rows either side of the redoubt's front row a house front may be on. */
    static final int BAND = 8;
    /** The farthest a team's windows lie from its first: one house, or two side by side, not a street apart. */
    static final int SPREAD = 6;
    /** Cells round its windows a team holds. */
    static final int HOLD = 3;

    /** A test on a cell. */
    interface CellTest {
        boolean test(int x, int y);
    }

    private CordonHouseFronts() { }

    /**
     * Seats a team in the house fronts past each flank of each redoubt that has one.
     *
     * @param front +1 where the front is north (high y), -1 where it is south
     */
    public static List<Integer> stand(BattleSimulation sim, List<CordonRedoubt> redoubts, int front, RiskLevel risk,
                                      Random rng) {
        NavigationGrid grid = sim.getGrid();
        Direction ahead = front > 0 ? Direction.N : Direction.S;
        CellTest post = (x, y) -> {
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
            SharedEdgeBarrier edge = grid.getEdgeBarrier(x, y, ahead);
            return edge != null && edge.kind() == SharedEdgeBarrier.Kind.WINDOW
                    || sim.getDoodadCoverAt(x, y, 0, front) > 0;
        };
        List<Integer> teams = new ArrayList<>();
        for (CordonRedoubt r : redoubts) {
            int frontRow = front > 0 ? r.bottom() : r.top();
            for (int side = -1; side <= 1; side += 2) {
                List<int[]> cells = posts(post, r.left(), r.right(), frontRow, side, ColonyMilitia.POST_SIZE);
                if (cells == null) continue;
                String name = "Redoubt " + r.number() + (side < 0 ? " west" : " east") + " house team";
                int squad = ColonyMilitia.seatTeam(sim, cells, name, risk, rng);
                sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, cells.get(0)[0], cells.get(0)[1], HOLD),
                        CommandAuthority.GARRISON, CordonGarrisons.ISSUER, name + " holds the house fronts");
                teams.add(squad);
            }
        }
        return teams;
    }

    /**
     * The posts a flank's team holds: of the cells looking out on the front within {@link #REACH} past the flank wall
     * ({@code side} -1 west, +1 east) and {@link #BAND} rows of the front row, the {@code seats} nearest the redoubt's
     * front corner on that side, all within {@link #SPREAD} of the first; {@code null} where there are not that many.
     */
    static List<int[]> posts(CellTest post, int left, int right, int frontRow, int side, int seats) {
        int cornerX = side < 0 ? left : right;
        List<int[]> found = new ArrayList<>();
        for (int d = 1; d <= REACH; d++) {
            int x = cornerX + side * d;
            for (int y = frontRow - BAND; y <= frontRow + BAND; y++) if (post.test(x, y)) found.add(new int[]{x, y});
        }
        found.sort(Comparator.<int[]>comparingDouble(c -> Math.hypot(c[0] - cornerX, c[1] - frontRow))
                .thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
        if (found.isEmpty()) return null;
        int[] first = found.get(0);
        List<int[]> out = new ArrayList<>();
        for (int[] c : found) {
            if (out.size() == seats) break;
            if (Math.hypot(c[0] - first[0], c[1] - first[1]) <= SPREAD) out.add(c);
        }
        return out.size() < seats ? null : out;
    }
}
