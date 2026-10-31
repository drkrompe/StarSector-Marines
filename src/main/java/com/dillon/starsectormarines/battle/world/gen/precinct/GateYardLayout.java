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
                       int[] stop, int[] exit) {
        public Laid {
            apron = List.copyOf(apron);
            yard = List.copyOf(yard);
            staging = List.copyOf(staging);
        }

        /** A yard with its one gate. */
        public Laid(List<int[]> apron, List<int[]> yard, List<int[]> staging, int[] gate, int[] entry, int[] turn,
                    int[] stop) {
            this(apron, yard, staging, gate, entry, turn, stop, null);
        }

        /** Whether the yard is a one-way loop with an exit gate of its own. */
        public boolean loop() { return exit != null; }
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
        if (spec.exitWidth() > 0) {
            Laid loop = loop(claim, own, who, width, height, spec, facing, wx, wy);
            if (loop != null) return loop;
        }
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

    /**
     * A postern ({@link GateYard#posterns}).
     *
     * @param apron its cells from the wall's line out across the glacis
     * @param way   the ground just inside it kept clear of buildings
     * @param gate  its middle cell on the wall's line
     */
    public record Postern(List<int[]> apron, List<int[]> way, int[] gate) {
        public Postern {
            apron = List.copyOf(apron);
            way = List.copyOf(way);
        }
    }

    /**
     * The postern of place {@code who} in the middle of its wall's straight run on side {@code side}: the cells of
     * its own ground furthest out that way, the middle one along the side. {@code null} when the place has no ground
     * on that side with its own ground behind it.
     */
    public static Postern postern(int[][] claim, int[][] own, int who, int width, int height, Side side,
                                  int gateWidth, int depth) {
        int nx = side.dx, ny = side.dy;
        int lx = Math.abs(ny), ly = Math.abs(nx);
        long far = Long.MIN_VALUE;
        List<int[]> face = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!in(own, who, x, y, width, height) || in(own, who, x + nx, y + ny, width, height)) continue;
                long reach = (long) x * nx + (long) y * ny;
                if (reach > far) {
                    far = reach;
                    face.clear();
                }
                if (reach == far) face.add(new int[]{x, y});
            }
        }
        if (face.isEmpty()) return null;
        face.sort((a, b) -> Integer.compare(a[0] * lx + a[1] * ly, b[0] * lx + b[1] * ly));
        int[] at = face.get(face.size() / 2);
        List<int[]> apron = new ArrayList<>();
        List<int[]> way = new ArrayList<>();
        for (int t = -(gateWidth - 1) / 2; t <= gateWidth / 2; t++) {
            for (int k = 0; k <= Fortification.GLACIS + 1; k++) {
                int x = at[0] + nx * k + lx * t, y = at[1] + ny * k + ly * t;
                if (!in(claim, who, x, y, width, height)) break;
                apron.add(new int[]{x, y});
            }
            for (int k = 1; k <= depth; k++) {
                int x = at[0] - nx * k + lx * t, y = at[1] - ny * k + ly * t;
                if (interior(own, who, x, y, width, height)) way.add(new int[]{x, y});
            }
        }
        if (way.isEmpty()) return null;
        return new Postern(apron, way, at);
    }

    /**
     * The one-way yard ({@link GateYard#withExit}): the exit gate in the same straight run of wall, two cells of wall
     * at least past the entry toward {@link GateYard#side} (the nearest such place that fits), the yard the ground
     * inside the wall between and behind the two gates, {@link GateYard#depth} cells deep, and the staging ground
     * behind the yard between the gates' axes. A truck drives in at the entry, along the yard's {@link #laneDepth}
     * row past its stop beside the staging ground, and out at the exit. {@code null} where no exit fits.
     *
     * @param wx the entry gate's middle on the wall's line
     */
    static Laid loop(int[][] claim, int[][] own, int who, int width, int height, GateYard spec, Side facing,
                     int wx, int wy) {
        int nx = facing.dx, ny = facing.dy;
        int lx = Math.abs(ny), ly = Math.abs(nx);
        int toward = Integer.signum(lx * spec.side().dx + ly * spec.side().dy);
        int gw = spec.gateWidth(), ew = spec.exitWidth();
        int entryLo = -(gw - 1) / 2, entryHi = gw / 2, exitLo = -(ew - 1) / 2, exitHi = ew / 2;
        for (int s : toward == 0 ? new int[]{-1, 1} : new int[]{toward, -toward}) {
            int first = s > 0 ? entryHi + 3 - exitLo : entryLo - 3 - exitHi;
            for (int k = 0; k < EXIT_SLIDE; k++) {
                int te = first + s * k;
                if (!wallRun(claim, own, who, width, height, facing, wx, wy, te + exitLo, te + exitHi)) continue;
                List<int[]> apron = new ArrayList<>();
                for (int[] range : new int[][]{{entryLo, entryHi}, {te + exitLo, te + exitHi}}) {
                    for (int t = range[0]; t <= range[1]; t++) {
                        for (int d = 0; d <= Fortification.GLACIS + 1; d++) {
                            int x = wx + nx * d + lx * t, y = wy + ny * d + ly * t;
                            if (!in(claim, who, x, y, width, height)) break;
                            apron.add(new int[]{x, y});
                        }
                    }
                }
                int lo = Math.min(entryLo, te + exitLo), hi = Math.max(entryHi, te + exitHi);
                int lane = laneDepth(spec);
                List<int[]> yard = new ArrayList<>();
                for (int d = 1; d <= spec.depth(); d++) {
                    for (int t = lo; t <= hi; t++) {
                        int x = wx - nx * d + lx * t, y = wy - ny * d + ly * t;
                        if (interior(own, who, x, y, width, height)) yard.add(new int[]{x, y});
                    }
                }
                // The lane a truck drives must be whole: in from each gate, and along between them.
                boolean whole = true;
                for (int d = 1; d <= lane && whole; d++) {
                    whole = contains(yard, cell(wx, wy, nx, ny, lx, ly, d, 0))
                            && contains(yard, cell(wx, wy, nx, ny, lx, ly, d, te));
                }
                for (int t = Math.min(0, te); t <= Math.max(0, te) && whole; t++) {
                    whole = contains(yard, cell(wx, wy, nx, ny, lx, ly, lane, t));
                }
                if (!whole) continue;
                List<int[]> staging = new ArrayList<>();
                for (int d = spec.depth() + 1; d <= spec.depth() + spec.stagingWidth(); d++) {
                    for (int t = Math.min(0, te); t <= Math.max(0, te); t++) {
                        int x = wx - nx * d + lx * t, y = wy - ny * d + ly * t;
                        if (interior(own, who, x, y, width, height)) staging.add(new int[]{x, y});
                    }
                }
                int[] entry = cell(wx, wy, nx, ny, lx, ly, 1, 0);
                int[] turn = cell(wx, wy, nx, ny, lx, ly, lane, 0);
                int[] stop = cell(wx, wy, nx, ny, lx, ly, lane, te / 2);
                int[] exit = cell(wx, wy, nx, ny, lx, ly, 0, te);
                return new Laid(apron, yard, staging, new int[]{wx, wy}, entry, turn, stop, exit);
            }
        }
        return null;
    }

    /** Cells further along the wall an exit gate is looked for, past the nearest place it could stand. */
    static final int EXIT_SLIDE = 4;

    /** The row of a one-way yard trucks drive along between its gates: two short of its depth, so they stand by the staging ground. */
    static int laneDepth(GateYard spec) {
        return Math.max(1, spec.depth() - 2);
    }

    /** Whether lateral offsets {@code lo..hi} of the wall's line through {@code (wx, wy)} are all its outline facing out. */
    private static boolean wallRun(int[][] claim, int[][] own, int who, int width, int height, Side facing, int wx,
                                   int wy, int lo, int hi) {
        int lx = Math.abs(facing.dy), ly = Math.abs(facing.dx);
        for (int t = lo; t <= hi; t++) {
            int x = wx + lx * t, y = wy + ly * t;
            if (!in(own, who, x, y, width, height) || in(own, who, x + facing.dx, y + facing.dy, width, height)) return false;
            if (!in(claim, who, x + facing.dx, y + facing.dy, width, height)) return false;
            if (!interior(own, who, x - facing.dx, y - facing.dy, width, height)) return false;
        }
        return true;
    }

    private static int[] cell(int wx, int wy, int nx, int ny, int lx, int ly, int depth, int along) {
        return new int[]{wx - nx * depth + lx * along, wy - ny * depth + ly * along};
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
