package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.layout.Block;
import com.dillon.starsectormarines.battle.world.gen.layout.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.LineCourse;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureFit;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Where the Cordon's beats put their set pieces ({@code cordon-map-intent.md},
 * The beats' set pieces): resolved against the grown ground, each optional, so
 * a map that cannot hold one simply lacks it.
 */
final class CordonSetPieces {

    /** Set-piece names, as the layout reports them. */
    static final String FARMSTEAD = "farmstead", AMMO_DUMP = "ammo-dump", BELL_SQUARE = "bell-square",
            GRANARY = "granary", OP_WEST = "op-west", OP_EAST = "op-east",
            CORRIDOR_WRECK = CordonPlan.CORRIDOR_WRECK, GAP_POSITIONS = "gap-positions";
    /** Where beat L1's stragglers wait: the farmstead, or in a city its tenement ({@code OptionalObjective} alternatives). */
    static final String STRAGGLERS_HOME = FARMSTEAD + "|" + CordonCityPieces.TENEMENT;
    /** How far in front of the redoubts' front edges the farmstead's ground is looked for. */
    static final int FARM_NEAR = 10, FARM_FAR = 55;
    /** How far in front of a flank redoubt its observation post stands, and how far round it may move. */
    static final int OP_AHEAD = 20, OP_REACH = 16;
    /** Cells from any vehicle berth the ammo dump keeps, so a shed's apron is not stacked with crates. */
    static final int DUMP_OFF_BERTHS = 16;
    /** Corridor cells past the gap the wreck lies at the soonest, and short of the compound at the latest. */
    static final int WRECK_PAST_GAP = 25, WRECK_SHORT = 20;
    /** Cells either side of the corridor a wreck reaches across. */
    static final int WRECK_HALF = 6;

    private CordonSetPieces() {}

    /** The structures every Cordon map may hold, at {@code front} (+1 or -1, the front's y direction). */
    static List<Piece> structures(LayoutGround ground, int front) {
        List<Piece> out = new ArrayList<>();
        // A ring's way in faces the rear: 0 opens it to the south, 2 to the north.
        int rearward = front > 0 ? 0 : 2;
        add(out, farmstead(ground, front));
        add(out, ammoDump(ground, rearward));
        add(out, bellSquare(ground));
        add(out, granary(ground, front));
        add(out, observationPost(ground, OP_WEST, CordonPlan.REDOUBT_PLACE + 1, front, rearward));
        add(out, observationPost(ground, OP_EAST, CordonPlan.REDOUBT_PLACE + CordonPlan.REDOUBTS, front, rearward));
        return out;
    }

    private static void add(List<Piece> out, Piece piece) {
        if (piece != null) out.add(piece);
    }

    /** Open country nobody claimed, and nobody built on. */
    private static LineCourse.CellTest country(LayoutGround ground) {
        return (x, y) -> ground.buildable(x, y) && !ground.isClaimed(x, y);
    }

    /** L1: a farmstead in the near front between two redoubts, the middle pair first. */
    static Structure farmstead(LayoutGround ground, int front) {
        int[][] pairs = {{2, 3}, {1, 2}, {3, 4}};
        for (int[] pair : pairs) {
            int[] a = ground.claimBounds(CordonPlan.REDOUBT_PLACE + pair[0]);
            int[] b = ground.claimBounds(CordonPlan.REDOUBT_PLACE + pair[1]);
            if (a == null || b == null) continue;
            int edge = front > 0 ? Math.max(a[3], b[3]) : Math.min(a[1], b[1]);
            int near = edge + front * FARM_NEAR, far = edge + front * FARM_FAR;
            int[] region = {a[2] + 6, Math.min(near, far), b[0] - 6, Math.max(near, far)};
            int[] at = StructureFit.nearest(country(ground), region, StructureKind.FARMSTEAD.along,
                    StructureKind.FARMSTEAD.deep, (a[2] + b[0]) / 2, edge + front * (FARM_NEAR + FARM_FAR) / 2, 2);
            if (at != null) return new Structure(FARMSTEAD, StructureKind.FARMSTEAD, at[0], at[1], 0, false);
        }
        return null;
    }

    /** L2: an ammo dump inside the logistics base, clear of its shed's berths. */
    static Structure ammoDump(LayoutGround ground, int turns) {
        int[] base = ground.claimBounds(CordonPlan.BASE_PLACE);
        if (base == null) return null;
        List<int[]> berths = ground.berths();
        LineCourse.CellTest clear = (x, y) -> {
            if (!ground.buildable(x, y)) return false;
            for (int[] b : berths) {
                if (Math.abs(b[0] - x) < DUMP_OFF_BERTHS && Math.abs(b[1] - y) < DUMP_OFF_BERTHS) return false;
            }
            return true;
        };
        int[] region = {base[0] + 2, base[1] + 2, base[2] - 2, base[3] - 2};
        int side = StructureKind.AMMO_DUMP.along;
        int[] at = StructureFit.nearest(clear, region, side, side, (base[0] + base[2]) / 2, (base[1] + base[3]) / 2, 1);
        return at == null ? null : new Structure(AMMO_DUMP, StructureKind.AMMO_DUMP, at[0], at[1], turns, false);
    }

    /** L5: a square with a bell in the rear hamlet farthest from the base. */
    static Structure bellSquare(LayoutGround ground) {
        int[] base = ground.claimBounds(CordonPlan.BASE_PLACE);
        int[] best = null;
        long bestD = -1;
        for (String hamlet : new String[]{"rear-west", "rear-east"}) {
            int[] h = ground.claimBounds(hamlet);
            if (h == null) continue;
            long d = base == null ? 0 : sq((h[0] + h[2]) / 2 - (base[0] + base[2]) / 2)
                    + sq((h[1] + h[3]) / 2 - (base[1] + base[3]) / 2);
            if (d > bestD) {
                bestD = d;
                best = h;
            }
        }
        if (best == null) return null;
        LineCourse.CellTest paveable = (x, y) -> ground.buildable(x, y) || ground.isRoad(x, y) && ground.walkable(x, y);
        int side = StructureKind.BELL_SQUARE.along;
        int[] at = StructureFit.nearest(paveable, best, side, side, (best[0] + best[2]) / 2, (best[1] + best[3]) / 2, 1);
        return at == null ? null : new Structure(BELL_SQUARE, StructureKind.BELL_SQUARE, at[0], at[1], 0, false);
    }

    /** L6: a granary in the colony, on its side toward the line. */
    static Structure granary(LayoutGround ground, int front) {
        int[] colony = ground.claimBounds("colony");
        if (colony == null) return null;
        int nearY = front > 0 ? colony[1] : colony[3];
        int[] at = StructureFit.nearest(ground::buildable, colony, StructureKind.GRANARY.along,
                StructureKind.GRANARY.deep, (colony[0] + colony[2]) / 2, nearY, 2);
        return at == null ? null : new Structure(GRANARY, StructureKind.GRANARY, at[0], at[1], 0, false);
    }

    /** An observation post forward of a flank redoubt. */
    static Structure observationPost(LayoutGround ground, String name, String redoubt, int front, int turns) {
        int[] r = ground.claimBounds(redoubt);
        if (r == null) return null;
        int cx = (r[0] + r[2]) / 2, cy = (front > 0 ? r[3] : r[1]) + front * OP_AHEAD;
        int[] region = {cx - OP_REACH, cy - OP_REACH, cx + OP_REACH, cy + OP_REACH};
        int side = StructureKind.OBSERVATION_POST.along;
        int[] at = StructureFit.nearest(country(ground), region, side, side, cx, cy, 1);
        return at == null ? null : new Structure(name, StructureKind.OBSERVATION_POST, at[0], at[1], turns, false);
    }

    /**
     * The Road's set pieces on its corridor, found on the grown ground the way
     * acceptance finds it: R6, sandbag positions either side of the road where
     * it crosses the line, and R1, the ground of a wreck across it past the gap
     * (held, not laid: see below).
     */
    static List<Piece> corridorPieces(LayoutGround ground) {
        PrecinctPlan plan = ground.plan();
        if (plan == null) return List.of();
        List<int[]> corridor = CordonCorridor.find(ground.grid(), ground.topology(), plan);
        if (corridor == null || corridor.size() < WRECK_PAST_GAP + WRECK_SHORT + 2) return List.of();
        int front = CordonFront.frontward(plan);
        float line = CordonFront.lineY(plan);
        int gap = 0;
        for (int i = 0; i < corridor.size(); i++) {
            if (Math.abs(corridor.get(i)[1] - line) < Math.abs(corridor.get(gap)[1] - line)) gap = i;
        }
        List<Piece> out = new ArrayList<>();
        List<int[]> positions = gapPositions(ground, corridor.get(gap), front);
        if (!positions.isEmpty()) {
            out.add(new Block(GAP_POSITIONS, positions, BlockKind.SANDBAG, new int[]{0, front}, false));
        }
        // The wreck's ground is held, not built on: laid at generation it closed the road to every convoy all battle
        // (nothing clears it until the R1 dozer exists), so it is the beat's to lay when it runs.
        List<int[]> wreck = wreck(ground, corridor, gap);
        if (!wreck.isEmpty()) {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (int[] c : wreck) {
                x0 = Math.min(x0, c[0]);
                y0 = Math.min(y0, c[1]);
                x1 = Math.max(x1, c[0]);
                y1 = Math.max(y1, c[1]);
            }
            out.add(new Site(CORRIDOR_WRECK, x0, y0, x1, y1, SitePurpose.OPEN, false));
        }
        return out;
    }

    /** Two cells out from each kerb of the road at the crossing, on the crossing's row and the one in front. */
    static List<int[]> gapPositions(LayoutGround ground, int[] crossing, int front) {
        int left = crossing[0], right = crossing[0];
        while (left > 0 && ground.isRoad(left - 1, crossing[1]) && crossing[0] - left < WRECK_HALF * 2) left--;
        while (right < ground.width() - 1 && ground.isRoad(right + 1, crossing[1]) && right - crossing[0] < WRECK_HALF * 2) right++;
        List<int[]> out = new ArrayList<>();
        for (int k = 1; k <= 2; k++) {
            for (int row = 0; row <= 1; row++) {
                int y = crossing[1] + row * front;
                out.add(new int[]{left - k, y});
                out.add(new int[]{right + k, y});
            }
        }
        return out;
    }

    /**
     * Across the corridor past the gap, from the corridor cell out to either side while the ground is walkable: at the
     * corridor cell nearest the suburb's first (else halfway between the gap and the compound) where the street is
     * narrow enough for the wreck to close it kerb to kerb ({@link #wreckAt}). Laid at the suburb's first cell
     * whatever stood there, it lay across a square on the town Road, nine cells of crates in open ground the trucks
     * could have driven round.
     */
    static List<int[]> wreck(LayoutGround ground, List<int[]> corridor, int gap) {
        int from = gap + WRECK_PAST_GAP, to = corridor.size() - 1 - WRECK_SHORT;
        if (from > to) return List.of();
        int at = (from + to) / 2;
        int[] suburb = ground.claimBounds("suburb");
        if (suburb != null) {
            for (int i = from; i <= to; i++) {
                int[] c = corridor.get(i);
                if (c[0] >= suburb[0] && c[0] <= suburb[2] && c[1] >= suburb[1] && c[1] <= suburb[3]) {
                    at = i;
                    break;
                }
            }
        }
        at = wreckAt(at, from, to, i -> across(ground, corridor, i).closed());
        return across(ground, corridor, at).cells();
    }

    /**
     * The corridor cell the wreck lies at: the nearest to {@code preferred} within {@code from..to} where it
     * {@code closes} the street, the earlier of two equally near; {@code preferred} where none does.
     */
    static int wreckAt(int preferred, int from, int to, IntPredicate closes) {
        for (int d = 0; preferred - d >= from || preferred + d <= to; d++) {
            if (preferred - d >= from && closes.test(preferred - d)) return preferred - d;
            if (d > 0 && preferred + d <= to && closes.test(preferred + d)) return preferred + d;
        }
        return preferred;
    }

    /** A wreck's cells across the corridor at one of its cells, and whether unwalkable ground stops it both sides. */
    record Across(List<int[]> cells, boolean closed) { }

    static Across across(LayoutGround ground, List<int[]> corridor, int at) {
        int[] c = corridor.get(at);
        int[] before = corridor.get(Math.max(0, at - 2)), after = corridor.get(Math.min(corridor.size() - 1, at + 2));
        // Across the way the corridor runs here.
        boolean alongX = Math.abs(after[0] - before[0]) >= Math.abs(after[1] - before[1]);
        int px = alongX ? 0 : 1, py = alongX ? 1 : 0;
        List<int[]> out = new ArrayList<>();
        out.add(c);
        boolean closed = true;
        for (int side : new int[]{-1, 1}) {
            boolean stopped = false;
            for (int k = 1; k <= WRECK_HALF + 1; k++) {
                int x = c[0] + px * side * k, y = c[1] + py * side * k;
                if (!ground.walkable(x, y)) {
                    stopped = true;
                    break;
                }
                if (k <= WRECK_HALF) out.add(new int[]{x, y});
            }
            closed &= stopped;
        }
        return new Across(out, closed);
    }

    private static long sq(long v) {
        return v * v;
    }
}
