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
 * <b>pull-out zone</b>, the north-east quarter of the logistics base's goods yard behind the line (round the rally
 * point where a map has no base). The Road is finished there: every Marine behind the line with Marines holding the
 * zone for {@link #HOLD_SECONDS} without a break.
 *
 * <p>The order is any of the player's own squad orders whose target cell lies in the zone: the command layer's
 * ordinary move or hold order, read rather than a new control. The company's autonomous commander never gives it; it
 * falls back to the zone only once the town is finished.
 */
public final class CordonPullOut {

    /** Half the side of the zone round the rally point where the map has no base to put it in. */
    public static final int RADIUS = 8;
    /** Cells the zone keeps in from the base's fence. */
    static final int INSET = 2;
    /** Seconds the company must hold the zone without a break to finish (owner, 2026-10-31). */
    public static final int HOLD_SECONDS = 10;

    private final int left, top, right, bottom;
    private final List<int[]> cells;

    public CordonPullOut(int left, int top, int right, int bottom, List<int[]> cells) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.cells = List.copyOf(cells);
    }

    /** The zone over the box {@code [left, top, right, bottom]}: its walkable cells. */
    public static CordonPullOut over(NavigationGrid grid, int[] box) {
        List<int[]> cells = new ArrayList<>();
        for (int y = box[1]; y <= box[3]; y++) {
            for (int x = box[0]; x <= box[2]; x++) if (grid.inBounds(x, y) && grid.isWalkable(x, y)) cells.add(new int[]{x, y});
        }
        return new CordonPullOut(box[0], box[1], box[2], box[3], cells);
    }

    /**
     * The zone's box: the north-east quadrant (high x, low y) of the base's fence {@code [minX, minY, maxX, maxY]}, kept
     * {@link #INSET} in from the fence (the concrete goods yard's quarter away from the camp on its west side and the
     * muster yard); {@code null} for no fence.
     */
    static int[] quadrant(int[] fence) {
        if (fence == null) return null;
        int cx = (fence[0] + fence[2]) / 2, cy = (fence[1] + fence[3]) / 2;
        int[] box = {cx, fence[1] + INSET, fence[2] - INSET, cy};
        return box[0] > box[2] || box[1] > box[3] ? null : box;
    }

    /**
     * The Road's zone: the north-east quadrant of the base's fence when it holds open ground and lies wholly behind the
     * line; else the square of {@link #RADIUS} round {@code rally}, the reserve's place behind the gap.
     */
    public static CordonPullOut place(NavigationGrid grid, int[] fence, int[] rally, float lineY, int frontward) {
        int[] box = quadrant(fence);
        if (box != null) {
            int nearRow = frontward > 0 ? box[3] : box[1];
            CordonPullOut zone = over(grid, box);
            if (!zone.cells.isEmpty() && CordonSequence.behindLine(nearRow, lineY, frontward)) return zone;
        }
        return over(grid, new int[]{rally[0] - RADIUS, rally[1] - RADIUS, rally[0] + RADIUS, rally[1] + RADIUS});
    }

    /** The zone's middle: where the company is sent and the beacon stands. */
    public int x() { return (left + right) / 2; }
    public int y() { return (top + bottom) / 2; }
    /** The zone's box, {@code [left, top, right, bottom]}. */
    public int[] box() { return new int[]{left, top, right, bottom}; }
    /** The zone's walkable cells: the HUD beacon's footprint. */
    public List<int[]> cells() { return cells; }

    /** Whether a cell is in the zone. */
    public boolean contains(int cx, int cy) {
        return cx >= left && cx <= right && cy >= top && cy <= bottom;
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
     * Whether the company has withdrawn: none of it still ahead of the line, some of it behind, and Marines in the zone
     * for {@code needTicks} without a break ({@code heldTicks}, reset whenever the zone empties).
     */
    static boolean withdrawn(int behind, int ahead, int heldTicks, int needTicks) {
        return ahead == 0 && behind > 0 && heldTicks >= needTicks;
    }

    /** The zone's hold timer after a tick: one more while Marines stand in it, back to nothing when it empties. */
    static int held(int heldTicks, int inZone) {
        return inZone > 0 ? heldTicks + 1 : 0;
    }
}
