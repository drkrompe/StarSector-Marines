package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification.GateYard;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a square place's {@link GateYard} lies: the gate widened across the glacis and through the wall, the yard
 * inside it, and the staging ground beside the yard. Pure: worked out on the claim before the place packs, so the
 * packer builds round the yard and the wall leaves the wider gate open.
 */
public final class GateYardLayout {

    private GateYardLayout() { }

    /** The name the map's laid layout reports a place's gate yard under. */
    public static String pieceName(String place) {
        return place + "-yard";
    }

    /**
     * The laid yard.
     *
     * @param apron   the gate's cells from the wall's line out across the glacis, {@link GateYard#gateWidth} wide
     * @param yard    the yard's open paved ground inside the wall
     * @param staging the ground beside the yard kept open for the people waiting to board
     * @param gate    the gate's middle cell on the wall's line
     * @param entry   the yard's cell just inside the gate, where a truck drives in
     * @param turn    the yard's middle, where a truck turns
     * @param stop    the yard's cell beside the staging ground, where a truck stops to load
     */
    public record Laid(List<int[]> apron, List<int[]> yard, List<int[]> staging, int[] gate, int[] entry, int[] turn,
                       int[] stop) {
        public Laid {
            apron = List.copyOf(apron);
            yard = List.copyOf(yard);
            staging = List.copyOf(staging);
        }
    }

    /**
     * The yard of place {@code who} behind its gate on side {@code facing} whose middle on the whole claim's outline
     * is {@code (gateX, gateY)}, or {@code null} when that gate does not lead in to the place's ground inside the
     * glacis or no yard cell is left inside the wall.
     *
     * @param claim  the whole claim, glacis included
     * @param own    the part inside the glacis, where the wall stands on its outline
     * @param facing the side of the place the gate is in: the yard runs in from it
     */
    public static Laid lay(int[][] claim, int[][] own, int who, int width, int height, GateYard spec, Side facing,
                           int gateX, int gateY) {
        int nx = facing.dx, ny = facing.dy;
        int lx = Math.abs(ny), ly = Math.abs(nx);
        // The wall's line: in from the claim's edge across the glacis to the place's own ground.
        int wx = gateX, wy = gateY;
        boolean found = false;
        for (int k = 0; k <= Fortification.GLACIS + 3; k++) {
            if (in(own, who, wx, wy, width, height)) {
                found = true;
                break;
            }
            wx -= nx;
            wy -= ny;
        }
        if (!found) return null;
        List<int[]> apron = new ArrayList<>();
        for (int t = -(spec.gateWidth() - 1) / 2; t <= spec.gateWidth() / 2; t++) {
            for (int k = 0; k <= Fortification.GLACIS + 1; k++) {
                int x = wx + nx * k + lx * t, y = wy + ny * k + ly * t;
                if (!in(claim, who, x, y, width, height)) break;
                apron.add(new int[]{x, y});
            }
        }
        int lo = -(spec.breadth() - 1) / 2, hi = spec.breadth() / 2;
        List<int[]> yard = new ArrayList<>();
        for (int k = 1; k <= spec.depth(); k++) {
            for (int t = lo; t <= hi; t++) {
                int x = wx - nx * k + lx * t, y = wy - ny * k + ly * t;
                if (interior(own, who, x, y, width, height)) yard.add(new int[]{x, y});
            }
        }
        if (yard.isEmpty()) return null;
        List<int[]> high = staging(own, who, width, height, spec, facing, wx, wy, hi + 1, hi + spec.stagingWidth());
        List<int[]> low = staging(own, who, width, height, spec, facing, wx, wy, lo - spec.stagingWidth(), lo - 1);
        boolean toHigh = high.size() >= low.size();
        List<int[]> staging = toHigh ? high : low;
        int middle = (spec.depth() + 1) / 2;
        int[] entry = {wx - nx, wy - ny};
        int[] turn = {wx - nx * middle, wy - ny * middle};
        int edge = toHigh ? Math.max(0, hi - 1) : Math.min(0, lo + 1);
        int[] stop = {turn[0] + lx * edge, turn[1] + ly * edge};
        if (!contains(yard, stop)) stop = turn;
        return new Laid(apron, yard, staging, new int[]{wx, wy}, entry, turn, stop);
    }

    /** The staging ground between lateral offsets {@code from..to} of the gate, as deep as the yard. */
    private static List<int[]> staging(int[][] own, int who, int width, int height, GateYard spec, Side facing,
                                       int wx, int wy, int from, int to) {
        int nx = facing.dx, ny = facing.dy;
        int lx = Math.abs(ny), ly = Math.abs(nx);
        List<int[]> out = new ArrayList<>();
        for (int k = 1; k <= spec.depth(); k++) {
            for (int t = from; t <= to; t++) {
                int x = wx - nx * k + lx * t, y = wy - ny * k + ly * t;
                if (interior(own, who, x, y, width, height)) out.add(new int[]{x, y});
            }
        }
        return out;
    }

    /** A cell of the place inside its outline: its own, with its own ground on every side. */
    private static boolean interior(int[][] own, int who, int x, int y, int width, int height) {
        return in(own, who, x, y, width, height) && in(own, who, x + 1, y, width, height)
                && in(own, who, x - 1, y, width, height) && in(own, who, x, y + 1, width, height)
                && in(own, who, x, y - 1, width, height);
    }

    private static boolean in(int[][] claim, int who, int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height && claim[x][y] == who;
    }

    private static boolean contains(List<int[]> cells, int[] c) {
        for (int[] d : cells) if (d[0] == c[0] && d[1] == c[1]) return true;
        return false;
    }
}
