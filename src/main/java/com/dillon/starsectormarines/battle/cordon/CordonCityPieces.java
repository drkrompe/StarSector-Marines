package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.layout.Block;
import com.dillon.starsectormarines.battle.world.gen.layout.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.LineCourse;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The beats' set pieces a city holds in place of open country's ({@code cordon-map-intent.md}, The beats' set
 * pieces): a city leaves no ground to raise a farmstead or ring a post on, so its pieces are buildings already
 * standing, taken over with the works. A tenement in the near front for the stragglers (L1), and an observation post
 * in the biggest building forward of each flank redoubt.
 */
final class CordonCityPieces {

    /** The stragglers' tenement (beat L1): a city's farmstead. */
    static final String TENEMENT = "tenement";
    /** Interior cells a tenement has at the least and the most: a house of flats, not a shed nor a whole block. */
    static final int TENEMENT_MIN = 40, TENEMENT_MAX = 500;
    /** Steps the walk from a tenement back to the gap may take. */
    static final int LANE_STEPS = 400;
    /** Sandbagged window cells an observation post has at the most, and the fewest it is worth laying. */
    static final int OP_BAGS = 5, OP_MIN_BAGS = 2;

    private CordonCityPieces() {}

    /** The city's pieces at {@code front} (+1 or -1, the front's y direction). */
    static List<Piece> pieces(LayoutGround ground, int front) {
        List<Building> buildings = new ArrayList<>(ground.buildings());
        buildings.sort(Comparator.comparingInt(b -> b.id));
        List<Piece> out = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();
        Building tenement = null;
        int[][] pairs = {{2, 3}, {1, 2}, {3, 4}};
        for (int[] pair : pairs) {
            int[] a = ground.claimBounds(CordonPlan.REDOUBT_PLACE + pair[0]);
            int[] b = ground.claimBounds(CordonPlan.REDOUBT_PLACE + pair[1]);
            if (a == null || b == null) continue;
            tenement = tenement(buildings, a, b, front, ground::walkable);
            if (tenement != null) break;
        }
        if (tenement != null) {
            taken.add(tenement.id);
            out.add(new Site(TENEMENT, tenement.minX, tenement.minY, tenement.maxX, tenement.maxY, SitePurpose.OPEN, false));
        }
        String[][] posts = {{CordonSetPieces.OP_WEST, CordonPlan.REDOUBT_PLACE + 1},
                {CordonSetPieces.OP_EAST, CordonPlan.REDOUBT_PLACE + CordonPlan.REDOUBTS}};
        for (String[] post : posts) {
            int[] r = ground.claimBounds(post[1]);
            if (r == null) continue;
            for (Building b : lookouts(buildings, r, front, taken)) {
                List<int[]> bags = windows(b, front, ground::open);
                if (bags.size() < OP_MIN_BAGS) continue;
                taken.add(b.id);
                out.add(new Block(post[0], bags, BlockKind.SANDBAG, new int[]{0, front}, false));
                break;
            }
        }
        return out;
    }

    /**
     * L1's tenement between redoubts {@code a} and {@code b} (claimed bounds, west then east): a residential building
     * wholly in the near front between them, where the farmstead would stand, of a tenement's size, from which a walk
     * over {@code walkable} ground reaches the line between the two; the one nearest the middle of that ground.
     */
    static Building tenement(Collection<Building> buildings, int[] a, int[] b, int front, LineCourse.CellTest walkable) {
        int edge = front > 0 ? Math.max(a[3], b[3]) : Math.min(a[1], b[1]);
        int near = edge + front * CordonSetPieces.FARM_NEAR, far = edge + front * CordonSetPieces.FARM_FAR;
        int x0 = a[2] + 6, x1 = b[0] - 6, y0 = Math.min(near, far), y1 = Math.max(near, far);
        int mx = (a[2] + b[0]) / 2, my = edge + front * (CordonSetPieces.FARM_NEAR + CordonSetPieces.FARM_FAR) / 2;
        Building best = null;
        long bestD = Long.MAX_VALUE;
        for (Building building : buildings) {
            if (building.kind != BuildingKind.RESIDENTIAL) continue;
            if (building.minX < x0 || building.maxX > x1 || building.minY < y0 || building.maxY > y1) continue;
            if (building.cellCount() < TENEMENT_MIN || building.cellCount() > TENEMENT_MAX) continue;
            long dx = (building.minX + building.maxX) / 2 - mx, dy = (building.minY + building.maxY) / 2 - my;
            long d = dx * dx + dy * dy;
            if (d >= bestD || !laneBack(building, a, b, edge, front, walkable)) continue;
            best = building;
            bestD = d;
        }
        return best;
    }

    /**
     * Whether a walk from inside the building, out of its doors and over walkable ground, reaches the line's depth
     * between the two redoubts within {@link #LANE_STEPS}: the stragglers' lane back to the gap.
     */
    static boolean laneBack(Building building, int[] a, int[] b, int edge, int front, LineCourse.CellTest walkable) {
        int bx0 = a[0], bx1 = b[2];
        int by0 = Math.min(edge, edge + front * CordonSetPieces.FARM_FAR), by1 = Math.max(edge, edge + front * CordonSetPieces.FARM_FAR);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int i = 0; i < building.cellsX.length; i++) {
            int x = building.cellsX[i], y = building.cellsY[i];
            if (walkable.test(x, y) && seen.add(key(x, y))) open.add(new int[]{x, y, 0});
        }
        while (!open.isEmpty()) {
            int[] c = open.poll();
            if (c[1] == edge && c[0] > a[2] && c[0] < b[0]) return true;
            if (c[2] >= LANE_STEPS) continue;
            for (int[] d : STEPS) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (x < bx0 || x > bx1 || y < by0 || y > by1 || !walkable.test(x, y) || !seen.add(key(x, y))) continue;
                open.add(new int[]{x, y, c[2] + 1});
            }
        }
        return false;
    }

    /**
     * The buildings an observation post forward of redoubt {@code r} may stand in, biggest first: every one whose
     * middle lies where the rural post would stand ({@link CordonSetPieces#OP_AHEAD} in front of the redoubt's front
     * edge, within {@link CordonSetPieces#OP_REACH} of it), not already {@code taken}. A building's size stands for its
     * height, which the map does not record: the biggest block on the street is the one that looks furthest.
     */
    static List<Building> lookouts(Collection<Building> buildings, int[] r, int front, Set<Integer> taken) {
        int cx = (r[0] + r[2]) / 2, cy = (front > 0 ? r[3] : r[1]) + front * CordonSetPieces.OP_AHEAD;
        int reach = CordonSetPieces.OP_REACH;
        List<Building> out = new ArrayList<>();
        for (Building b : buildings) {
            if (taken.contains(b.id) || b.kind == BuildingKind.FORTIFIED) continue;
            int mx = (b.minX + b.maxX) / 2, my = (b.minY + b.maxY) / 2;
            if (Math.abs(mx - cx) <= reach && Math.abs(my - cy) <= reach) out.add(b);
        }
        out.sort(Comparator.comparingInt(Building::cellCount).reversed().thenComparingInt(b -> b.id));
        return out;
    }

    /**
     * The post's sandbags: in each column of the building, its interior cell furthest toward the front (inside the
     * front wall's windows), where the ground is {@code open}; at most {@link #OP_BAGS}, nearest the building's middle.
     */
    static List<int[]> windows(Building building, int front, LineCourse.CellTest open) {
        int w = building.maxX - building.minX + 1;
        int[] row = new int[w];
        Arrays.fill(row, Integer.MIN_VALUE);
        for (int i = 0; i < building.cellsX.length; i++) {
            int k = building.cellsX[i] - building.minX, y = building.cellsY[i];
            if (row[k] == Integer.MIN_VALUE || (y - row[k]) * front > 0) row[k] = y;
        }
        int mid = (building.minX + building.maxX) / 2;
        List<int[]> out = new ArrayList<>();
        for (int k = 0; k < w; k++) {
            int x = building.minX + k;
            if (row[k] != Integer.MIN_VALUE && open.test(x, row[k])) out.add(new int[]{x, row[k]});
        }
        out.sort(Comparator.comparingInt(c -> Math.abs(c[0] - mid)));
        return List.copyOf(out.subList(0, Math.min(OP_BAGS, out.size())));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
}
