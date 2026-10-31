package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.world.gen.layout.Block;
import com.dillon.starsectormarines.battle.world.gen.layout.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sandbag lines outside the Road's town compound for the player's squads to occupy ({@code cordon-nouns.md}, Town
 * sandbags; owner, 2026-10-31: "sandbag lines outside the base for the player to occupy on likely approaches").
 * Unmanned knee-high cover on the swarm's ways in: across the approach to each of the compound's ways in, just past
 * the ring kept open round its wall, a line either side of the way itself so the way stays open (the pass gap: the
 * postern's lane, or the convoys' road at the yard's gates), each facing out down the approach, with a short return
 * at its outer end making an L. Never on road nor beside it, so the convoys' road, the town's streets and the yard's
 * gates stay clear; never inside the ring, so the gates' approaches stay open. Rule-based off the compound's gates
 * ({@link CordonTownGarrison#gates}) and the ground; laid with the works.
 */
final class CordonTownSandbags {

    /** The layout's name for the lines, numbered. */
    static final String NAME = "town-sandbags-";
    /** The most lines laid. */
    static final int MAX_LINES = 6;
    /** Cells out from the wall's line the lines are looked for: just past the six-cell ring, out to a street mouth. */
    static final int FROM = 7, TO = 11;
    /**
     * The nearest row tried when nothing fits past the ring, where the town stands close: inside the ring, but off
     * the glacis and with the way in's lane still open.
     */
    static final int NEAREST = 4;
    /** Cells a line may start past the pass gap, to clear a street wider than its way in. */
    static final int SKIP = 3;
    /** Cells along a line, at the most, and the fewest worth laying. */
    static final int LONGEST = 7, SHORTEST = 4;
    /** Cells of the return at a line's outer end, back toward the wall. */
    static final int RETURN = 2;
    /** Cells either side of a way in's axis left open past its own half-width: the pass gap. */
    static final int PASS = 1;
    /** Cells kept between two lines. */
    static final int APART = 2;

    private CordonTownSandbags() {}

    /** One line: its cells, the way its bags face, and the gate it covers ({x, y, width}). */
    record Line(List<int[]> cells, int[] facing, int[] gate) { }

    /** The lines round the Road's compound on {@code ground}, as sandbag blocks. */
    static List<Piece> pieces(LayoutGround ground) {
        int[] c = ground.claimBounds(CivilDefenceCompound.PLACE);
        if (c == null) return List.of();
        // The wall stands on the claim's outline less its glacis; the gates are its open runs.
        int g = Fortification.GLACIS;
        int[] wall = {c[0] + g, c[1] + g, c[2] - g, c[3] - g};
        List<int[]> gates = CordonTownGarrison.gates(ground::walkable, wall[0] + 1, wall[1] + 1, wall[2] - 1,
                wall[3] - 1);
        CordonCorridor.CellTest free = (x, y) -> ground.buildable(x, y) && !ground.isHeld(x, y) && !nearRoad(ground, x, y);
        List<Piece> out = new ArrayList<>();
        for (Line line : lines(free, wall, gates)) {
            out.add(new Block(NAME + (out.size() + 1), line.cells(), BlockKind.SANDBAG, line.facing(), false));
        }
        return out;
    }

    /**
     * The lines across the approaches to {@code gates} (each {x, y, width} on the wall's line {@code wall}, its
     * inclusive bounds), on cells {@code free} says may take a bag: for each gate, the nearest row {@link #FROM} to
     * {@link #TO} cells out (or, failing that, back in to {@link #NEAREST}) where a line of {@link #SHORTEST} or
     * more fits on at least one side of the pass gap, starting up to {@link #SKIP} cells past it,
     * the narrow ways in (people's) before the wide (the yard's), at most {@link #MAX_LINES}. Pure.
     */
    static List<Line> lines(CordonCorridor.CellTest free, int[] wall, List<int[]> gates) {
        List<int[]> order = new ArrayList<>(gates);
        order.sort((a, b) -> Integer.compare(a[2], b[2]));
        List<Line> out = new ArrayList<>();
        Set<Long> near = new HashSet<>();
        for (int[] gate : order) {
            int[] n = outward(wall, gate);
            if (n == null) continue;
            int[] l = {Math.abs(n[1]), Math.abs(n[0])};
            int half = gate[2] / 2 + PASS;
            for (int d : depths()) {
                boolean laid = false;
                for (int side : new int[]{-1, 1}) {
                    if (lineCount(out) >= MAX_LINES) break;
                    List<int[]> run = new ArrayList<>();
                    int t = half + 1;
                    while (t <= half + SKIP && !fits(free, near, gate, n, l, d, t, side)) t++;
                    for (int end = t + LONGEST; t < end && fits(free, near, gate, n, l, d, t, side); t++) {
                        run.add(new int[]{gate[0] + n[0] * d + l[0] * t * side, gate[1] + n[1] * d + l[1] * t * side});
                    }
                    if (run.size() < SHORTEST) continue;
                    // A short return at the outer end, back toward the wall, facing out to the line's own side.
                    int[] end = run.get(run.size() - 1);
                    List<int[]> back = new ArrayList<>();
                    for (int k = 1; k <= RETURN; k++) {
                        int x = end[0] - n[0] * k, y = end[1] - n[1] * k;
                        if (!free.test(x, y) || near.contains(key(x, y))) break;
                        back.add(new int[]{x, y});
                    }
                    List<Line> here = new ArrayList<>();
                    here.add(new Line(run, n.clone(), gate));
                    if (back.size() == RETURN) here.add(new Line(back, new int[]{l[0] * side, l[1] * side}, gate));
                    for (Line line : here) {
                        out.add(line);
                        for (int[] cell : line.cells()) {
                            for (int dx = -APART; dx <= APART; dx++) {
                                for (int dy = -APART; dy <= APART; dy++) near.add(key(cell[0] + dx, cell[1] + dy));
                            }
                        }
                    }
                    laid = true;
                }
                if (laid) break;
            }
            if (lineCount(out) >= MAX_LINES) break;
        }
        return out;
    }

    /** The rows tried, nearest first: past the ring out to a street mouth, then back inside the ring. */
    static int[] depths() {
        int[] out = new int[TO - FROM + 1 + FROM - NEAREST];
        int i = 0;
        for (int d = FROM; d <= TO; d++) out[i++] = d;
        for (int d = FROM - 1; d >= NEAREST; d--) out[i++] = d;
        return out;
    }

    private static boolean fits(CordonCorridor.CellTest free, Set<Long> near, int[] gate, int[] n, int[] l, int d, int t,
                                int side) {
        int x = gate[0] + n[0] * d + l[0] * t * side, y = gate[1] + n[1] * d + l[1] * t * side;
        return free.test(x, y) && !near.contains(key(x, y));
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

    private static boolean nearRoad(LayoutGround ground, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (ground.isRoad(x + dx, y + dy)) return true;
        return false;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }
}
