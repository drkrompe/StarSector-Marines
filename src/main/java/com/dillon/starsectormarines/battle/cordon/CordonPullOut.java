package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Road's pull-out ({@code cordon-nouns.md}, The pull-out): once enough townspeople are out and no truck is loading
 * at the town's gate the company has done enough, and the player may pull it out by ordering a squad to the
 * <b>pull-out zone</b>, the logistics base's muster yard behind the line (the rally point where a map has no base).
 * The Road is finished there: every Marine behind the line with some of them standing in the zone.
 *
 * <p>The order is any of the player's own squad orders whose target cell lies in the zone: the command layer's
 * ordinary move or hold order, read rather than a new control. The company's autonomous commander never gives it; it
 * falls back to the zone only once the town is finished.
 */
public final class CordonPullOut {

    /** Cells round the zone's middle that are in it. */
    public static final int RADIUS = 8;

    private final int x, y;
    private final List<int[]> cells;

    public CordonPullOut(int x, int y, List<int[]> cells) {
        this.x = x;
        this.y = y;
        this.cells = List.copyOf(cells);
    }

    /** The zone round ({@code x}, {@code y}): its walkable cells within {@link #RADIUS}. */
    public static CordonPullOut at(NavigationGrid grid, int x, int y) {
        List<int[]> cells = new ArrayList<>();
        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int cx = x + dx, cy = y + dy;
                if (dx * dx + dy * dy <= RADIUS * RADIUS && grid.inBounds(cx, cy) && grid.isWalkable(cx, cy)) {
                    cells.add(new int[]{cx, cy});
                }
            }
        }
        return new CordonPullOut(x, y, cells);
    }

    /**
     * The Road's zone: round the logistics base's muster yard, when the base stands far enough behind the line that the
     * whole zone is behind it; else round {@code rally}, the reserve's place behind the gap.
     */
    public static CordonPullOut place(NavigationGrid grid, CordonLogistics logistics, int[] rally, float lineY,
                                      int frontward) {
        if (logistics != null
                && CordonSequence.behindLine(logistics.musterY() + frontward * RADIUS, lineY, frontward)) {
            return at(grid, logistics.musterX(), logistics.musterY());
        }
        return at(grid, rally[0], rally[1]);
    }

    public int x() { return x; }
    public int y() { return y; }
    /** The zone's walkable cells: the HUD beacon's footprint. */
    public List<int[]> cells() { return cells; }

    /** Whether a cell is in the zone. */
    public boolean contains(int cx, int cy) {
        int dx = cx - x, dy = cy - y;
        return dx * dx + dy * dy <= RADIUS * RADIUS;
    }

    /**
     * Whether the company has done enough to pull out: at least the briefing's number of townspeople out and no truck
     * loading at the town's gate, so pulling out strands nobody already boarding.
     */
    static boolean offered(int peopleOut, int needed, boolean loading) {
        return needed > 0 && peopleOut >= needed && !loading;
    }

    /** Whether any of {@code targets} (the cells the player's own squad orders point at) lies in the zone. */
    boolean ordered(List<int[]> targets) {
        for (int[] t : targets) if (contains(t[0], t[1])) return true;
        return false;
    }

    /** The cells the company's squads under the player's own orders are sent to. */
    static List<int[]> playerTargets(Collection<Squad> squads) {
        List<int[]> out = new ArrayList<>();
        for (Squad squad : squads) {
            if (squad.faction != Faction.MARINE || !squad.underPlayerOrder()) continue;
            ObjectiveAssignment a = squad.assignedObjective;
            if (a != null && a.targetCellX() >= 0 && a.targetCellY() >= 0) out.add(new int[]{a.targetCellX(), a.targetCellY()});
        }
        return out;
    }

    /**
     * Whether the company has withdrawn: none of it still ahead of the line, some of it behind, and at least one Marine
     * standing in the zone.
     */
    static boolean withdrawn(int behind, int ahead, int inZone) {
        return ahead == 0 && behind > 0 && inZone > 0;
    }
}
