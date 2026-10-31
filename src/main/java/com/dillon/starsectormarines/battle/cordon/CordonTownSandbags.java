package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.world.gen.layout.Block;
import com.dillon.starsectormarines.battle.world.gen.layout.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sandbag lines outside the Road's town compound for the player's squads to occupy ({@code cordon-nouns.md}, Town
 * sandbags; owner, 2026-10-31). Unmanned knee-high cover across the mouth of each postern's approach, the street the
 * swarm comes down, facing down it, with a pass gap left open beside it. A line is laid only where it is worth
 * holding and harms nothing: open ground ahead of every bag for {@link #FIELD} cells, no building or wall within
 * {@link #ROOM} cells of any bag, its back reachable from the postern, and every bag {@link #CONVOY_CLEAR} cells off
 * the convoys' corridor and out of the yard gates' swing ({@link #SWING_OUT}). Where nothing qualifies none is laid.
 * Rule-based off the compound's gates ({@link CordonTownGarrison#gates}) and the ground; laid with the works.
 */
final class CordonTownSandbags {

    /** The layout's name for the lines, numbered. */
    static final String NAME = "town-sandbags-";
    /** The widest way in that gets a line: the posterns, never the yard's gates. */
    static final int POSTERN = 3;
    /** Cells out from the wall's line a line may stand: the glacis's outer edge, out to a street mouth. */
    static final int NEAR = 3, FAR = 12;
    /** Cells either side of a postern's axis a line may reach. */
    static final int REACH = 7;
    /** Cells along a line, at the most, and the fewest worth laying. */
    static final int LONGEST = 7, SHORTEST = 4;
    /** Cells of the return at a line's outer end, back toward the wall, laid only with its own field of fire. */
    static final int RETURN = 2;
    /** Open cells left beside a line, in its row, for infantry to pass: the pass gap. */
    static final int GAP = 3;
    /** Clear cells ahead of every bag, along the way it faces. */
    static final int FIELD = 10;
    /** Open cells kept between a bag and any building or wall, on every side. */
    static final int ROOM = 2;
    /** Cells every bag keeps off the convoys' corridor. */
    static final int CONVOY_CLEAR = 4;
    /** The yard gates' swing: cells out from a wide gate, and cells either side past its half-width. */
    static final int SWING_OUT = 8, SWING_SIDE = 4;
    /** Cells searched round a postern for a line's back to be reached from it. */
    static final int REACH_BOX = 16;

    private CordonTownSandbags() {}

    /** One line: its cells, the way its bags face, and the gate it covers ({x, y, width}). */
    record Line(List<int[]> cells, int[] facing, int[] gate) { }

    /** What a line reads of the ground. */
    interface Site {
        /** Whether a bag may stand on the cell: open ground, a street included. */
        boolean open(int x, int y);
        /** Whether the cell is clear: walkable, and no building nor wall. */
        boolean clear(int x, int y);
        /** Whether the cell is kept for the convoys: near their corridor or in a yard gate's swing. */
        boolean convoy(int x, int y);
    }

    /** The lines round the Road's compound on {@code ground}, as sandbag blocks. */
    static List<Piece> pieces(LayoutGround ground) {
        int[] c = ground.claimBounds(CivilDefenceCompound.PLACE);
        if (c == null) return List.of();
        // The wall stands on the claim's outline less its glacis; the gates are its open runs.
        int g = Fortification.GLACIS;
        int[] wall = {c[0] + g, c[1] + g, c[2] - g, c[3] - g};
        List<int[]> gates = CordonTownGarrison.gates(ground::walkable, wall[0] + 1, wall[1] + 1, wall[2] - 1,
                wall[3] - 1);
        PrecinctPlan plan = ground.plan();
        List<int[]> route = plan == null ? null : CordonCorridor.find(ground.grid(), ground.topology(), plan);
        CordonCorridor.CellTest convoy = convoyKept(route, gates, wall);
        CellTopology topology = ground.topology();
        CordonCorridor.CellTest built = (x, y) -> topology.getBuildingId(x, y) != 0
                || topology.getGroundKind(x, y) == CellTopology.GroundKind.INDOOR;
        Site site = new Site() {
            @Override public boolean open(int x, int y) {
                return ground.open(x, y) && !ground.isHeld(x, y) && !built.test(x, y);
            }
            @Override public boolean clear(int x, int y) {
                return ground.walkable(x, y) && !built.test(x, y);
            }
            @Override public boolean convoy(int x, int y) { return convoy.test(x, y); }
        };
        List<Piece> out = new ArrayList<>();
        for (Line line : lines(site, wall, gates)) {
            out.add(new Block(NAME + (out.size() + 1), line.cells(), BlockKind.SANDBAG, line.facing(), false));
        }
        return out;
    }

    /**
     * The cells kept for the convoys: within {@link #CONVOY_CLEAR} of any cell of {@code route} (may be null), and
     * in the swing before each way in wider than a postern: {@link #SWING_OUT} cells out from the wall's line, its
     * half-width and {@link #SWING_SIDE} more either side. Pure.
     */
    static CordonCorridor.CellTest convoyKept(List<int[]> route, List<int[]> gates, int[] wall) {
        Set<Long> near = new HashSet<>();
        if (route != null) {
            for (int[] r : route) {
                for (int dx = -CONVOY_CLEAR; dx <= CONVOY_CLEAR; dx++) {
                    for (int dy = -CONVOY_CLEAR; dy <= CONVOY_CLEAR; dy++) near.add(key(r[0] + dx, r[1] + dy));
                }
            }
        }
        List<int[]> swings = new ArrayList<>();
        for (int[] gate : gates) {
            int[] n = outward(wall, gate);
            if (gate[2] <= POSTERN || n == null) continue;
            int side = gate[2] / 2 + SWING_SIDE;
            int ax = gate[0] + n[0] * SWING_OUT, ay = gate[1] + n[1] * SWING_OUT;
            int lx = Math.abs(n[1]) * side, ly = Math.abs(n[0]) * side;
            swings.add(new int[]{Math.min(gate[0], ax) - lx, Math.min(gate[1], ay) - ly,
                    Math.max(gate[0], ax) + lx, Math.max(gate[1], ay) + ly});
        }
        return (x, y) -> {
            if (near.contains(key(x, y))) return true;
            for (int[] s : swings) if (x >= s[0] && x <= s[2] && y >= s[1] && y <= s[3]) return true;
            return false;
        };
    }

    /**
     * The lines across the approaches to the posterns among {@code gates} (each {x, y, width} on the wall's line
     * {@code wall}, its inclusive bounds): for each, the longest run of {@link #SHORTEST} to {@link #LONGEST} bags
     * on one row {@link #NEAR} to {@link #FAR} cells out that meets every rule ({@link #fits}), reaching across the
     * postern's axis or to within the pass gap of it, with {@link #GAP} clear cells beside it on the axis's side and
     * its back reachable from the postern; the farther row on a tie. A postern with none gets none. Pure.
     */
    static List<Line> lines(Site site, int[] wall, List<int[]> gates) {
        List<Line> out = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int[] gate : gates) {
            int[] n = outward(wall, gate);
            if (n == null || gate[2] > POSTERN) continue;
            int[] l = {Math.abs(n[1]), Math.abs(n[0])};
            List<int[]> best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int d = NEAR; d <= FAR; d++) {
                for (int a = -REACH; a <= REACH; a++) {
                    for (int len = LONGEST; len >= SHORTEST; len--) {
                        int b = a + len - 1;
                        if (b > REACH) continue;
                        int score = len * 100 + d;
                        if (score <= bestScore) continue;
                        List<int[]> cells = new ArrayList<>();
                        boolean ok = true;
                        for (int t = a; t <= b && ok; t++) {
                            int x = gate[0] + n[0] * d + l[0] * t, y = gate[1] + n[1] * d + l[1] * t;
                            ok = !taken.contains(key(x, y)) && fits(site, x, y, n);
                            cells.add(new int[]{x, y});
                        }
                        // Across the street mouth: the line and a pass gap beside it together span the axis.
                        ok = ok && (gapOpen(site, gate, n, l, d, b + 1, b + GAP, a)
                                || gapOpen(site, gate, n, l, d, a - GAP, a - 1, b));
                        if (!ok || !reached(site, gate, n, cells)) continue;
                        best = cells;
                        bestScore = score;
                    }
                }
            }
            if (best == null) continue;
            List<Line> here = new ArrayList<>();
            here.add(new Line(best, n.clone(), gate));
            // A short return at the outer end, back toward the wall, facing out to that side, only with its own field.
            int[] first = best.get(0), last = best.get(best.size() - 1);
            int tFirst = (first[0] - gate[0]) * l[0] + (first[1] - gate[1]) * l[1];
            int tLast = (last[0] - gate[0]) * l[0] + (last[1] - gate[1]) * l[1];
            boolean outerLast = Math.abs(tLast) >= Math.abs(tFirst);
            int[] end = outerLast ? last : first;
            int side = (outerLast ? tLast : tFirst) >= 0 ? 1 : -1;
            int[] facing = {l[0] * side, l[1] * side};
            List<int[]> back = new ArrayList<>();
            for (int k = 1; k <= RETURN; k++) {
                int x = end[0] - n[0] * k, y = end[1] - n[1] * k;
                if (taken.contains(key(x, y)) || !fits(site, x, y, facing)) break;
                back.add(new int[]{x, y});
            }
            if (back.size() == RETURN) here.add(new Line(back, facing, gate));
            for (Line line : here) {
                out.add(line);
                for (int[] cell : line.cells()) {
                    for (int dx = -ROOM; dx <= ROOM; dx++) {
                        for (int dy = -ROOM; dy <= ROOM; dy++) taken.add(key(cell[0] + dx, cell[1] + dy));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Whether a bag facing {@code facing} may stand on the cell: open, off the convoys' ground, no building nor wall
     * within {@link #ROOM} cells on any side, and {@link #FIELD} clear cells ahead of it.
     */
    static boolean fits(Site site, int x, int y, int[] facing) {
        if (!site.open(x, y) || site.convoy(x, y)) return false;
        for (int dx = -ROOM; dx <= ROOM; dx++) {
            for (int dy = -ROOM; dy <= ROOM; dy++) if (!site.clear(x + dx, y + dy)) return false;
        }
        for (int k = 1; k <= FIELD; k++) if (!site.clear(x + facing[0] * k, y + facing[1] * k)) return false;
        return true;
    }

    /**
     * Whether the gap {@code from..to} across the row {@code d} out is clear and, with the line's far end
     * {@code other}, spans the postern's axis.
     */
    private static boolean gapOpen(Site site, int[] gate, int[] n, int[] l, int d, int from, int to, int other) {
        if (Math.min(from, other) > 0 || Math.max(to, other) < 0) return false;
        for (int t = from; t <= to; t++) {
            if (!site.clear(gate[0] + n[0] * d + l[0] * t, gate[1] + n[1] * d + l[1] * t)) return false;
        }
        return true;
    }

    /** Whether the cell behind the line's first bag is reached from the postern over clear ground nearby. */
    private static boolean reached(Site site, int[] gate, int[] n, List<int[]> cells) {
        int tx = cells.get(0)[0] - n[0], ty = cells.get(0)[1] - n[1];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{gate[0], gate[1]});
        seen.add(key(gate[0], gate[1]));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] == tx && c[1] == ty) return true;
            for (int[] s : steps) {
                int x = c[0] + s[0], y = c[1] + s[1];
                if (Math.abs(x - gate[0]) > REACH_BOX || Math.abs(y - gate[1]) > REACH_BOX) continue;
                if (!site.clear(x, y) || !seen.add(key(x, y))) continue;
                queue.add(new int[]{x, y});
            }
        }
        return false;
    }

    /** Lines laid, a return counted with the line it ends. */
    static int lineCount(List<Line> lines) {
        int count = 0;
        for (Line line : lines) if (line.cells().size() > RETURN) count++;
        return count;
    }

    /** The way out of the wall at a gate on its line: the side of the wall's bounds the gate is on; {@code null} at a corner. */
    static int[] outward(int[] wall, int[] gate) {
        if (gate[1] == wall[1] && gate[0] > wall[0] && gate[0] < wall[2]) return new int[]{0, -1};
        if (gate[1] == wall[3] && gate[0] > wall[0] && gate[0] < wall[2]) return new int[]{0, 1};
        if (gate[0] == wall[0] && gate[1] > wall[1] && gate[1] < wall[3]) return new int[]{-1, 0};
        if (gate[0] == wall[2] && gate[1] > wall[1] && gate[1] < wall[3]) return new int[]{1, 0};
        return null;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }
}
