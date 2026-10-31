package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The picket nests that close the line's gaps ({@code cordon-nouns.md}, Picket line; {@link CordonGapLaw}): after the
 * redoubts are built, every neighbouring pair whose best chain of guns still has a step wider than the law gets
 * more nests between them, each as far along the line as a gun's reach from the guns already joined to the western
 * redoubt, until the chain reaches the eastern redoubt's guns. In open country they stand on the crest the picket
 * course is dug along; in a town or a city a nest at a <b>street mouth</b> (beside a street where it meets the line,
 * looking down it toward the front) is preferred: the whole gap at street mouths when that costs at most one nest
 * more, else a mouth in any one place it costs none, because a range ring overstates what a gun sees between
 * buildings and a street is where it sees furthest and where the swarm comes.
 *
 * <p>The added nests join the picket course's own ({@link Laid#nests()} of {@code picket-N-M}), so the garrison mans
 * them, the ammunition trucks rearm them and the allied rules count them exactly as the nests the course dug.
 *
 * <p>{@link #place}, {@link #close} and {@link #candidates} are pure; {@link #lay} applies them to a finished map.
 */
public final class CordonGapNests {

    private CordonGapNests() {}

    /** The furthest a nest is put from the gun it joins: the law's limit, less a cell either side of slack. */
    static final double REACH = CordonGapLaw.LIMIT - 2;
    /** Nests more than the fewest a gap may take to stand them all at street mouths. */
    static final int MOUTH_PREMIUM = 1;
    /** Nests one pair may take. A pair that needs more than this is not closed, and the map fails the law. */
    static final int MAX_ADDED = 8;
    /** Rows of the line a nest may stand in, in open country: one in front of the course's middle, six behind (on the crest). */
    static final int FRONT_BAND = 1, REAR_BAND = 6;
    /**
     * Rows in a town or a city, where houses stand across the line: the street line runs on along the house fronts up
     * to a dozen cells in front of its course, and the open ground behind is in yards and squares further back.
     */
    static final int BUILT_FRONT_BAND = 6, BUILT_REAR_BAND = 12;
    /** How far beside a street a mouth's nest stands, at most; it never stands nearer than two cells. */
    static final int MOUTH_REACH = 6;
    /** Cells frontward the street must run on from the line for its mouth to look down it. */
    static final int MOUTH_DEPTH = 6;
    /** Cells round each way a line leaves open (its gaps' recorded cells) a nest keeps clear of, besides a cell. */
    static final int WAY_CLEAR = 3;
    /** Nearest two guns on the line stand, Chebyshev. */
    static final int SPACING = 3;

    /**
     * What a picket course's record says of the nests added to it, the count following: they are the last that many
     * of its {@link Laid#nests()}.
     */
    public static final String GAP_NESTS = "gap nests ";

    /** How many of a picket course's nests were added to close its gap: the last that many of them. */
    public static int added(Laid picket) {
        if (picket == null) return 0;
        int at = picket.reason().indexOf(GAP_NESTS);
        if (at < 0) return 0;
        int from = at + GAP_NESTS.length(), to = from;
        while (to < picket.reason().length() && Character.isDigit(picket.reason().charAt(to))) to++;
        return to == from ? 0 : Integer.parseInt(picket.reason().substring(from, to));
    }

    /** A cell a nest may stand on, and whether it is a street mouth. */
    public record Candidate(int x, int y, boolean mouth) {}

    /** The ground the candidates are read from. */
    public interface Ground {
        /** Open floor a nest may stand on without closing a way: not road, not inside a building, room round it. */
        boolean takes(int x, int y);

        boolean road(int x, int y);

        boolean seeThrough(int x, int y);

        /** The cell's height tier; a nest stands on the highest open ground across the line. */
        int tier(int x, int y);
    }

    /**
     * The nests to add so the chain from {@code west}'s guns through {@code nests} to {@code east}'s has no step over
     * {@link CordonGapLaw#LIMIT}: none when it already holds. Greedy: each nest is the candidate within {@code reach}
     * of a gun already joined to the west that leaves the joined guns nearest the east, the street mouth that
     * goes furthest in its place whenever that leaves no more nests to place. Stops at {@code maxAdded}, or when no candidate makes way, so
     * the result may still leave the law broken; the caller measures again.
     */
    public static List<int[]> close(List<int[]> west, List<int[]> east, List<int[]> nests, List<Candidate> candidates,
                                    double reach, int maxAdded) {
        List<int[]> added = new ArrayList<>();
        if (west.isEmpty() || east.isEmpty()) return added;
        List<int[]> extra = new ArrayList<>(nests);
        while (true) {
            List<int[]> joined = joined(west, extra);
            double remaining = remaining(joined, east);
            if (remaining <= CordonGapLaw.LIMIT || added.size() >= maxAdded) return added;
            Candidate best = null, mouth = null;
            double bestScore = Double.POSITIVE_INFINITY, mouthScore = Double.POSITIVE_INFINITY;
            for (Candidate c : candidates) {
                int[] cell = {c.x(), c.y()};
                if (!within(joined, cell, reach) || crowded(west, extra, east, cell)) continue;
                extra.add(cell);
                double score = remaining(joined(west, extra), east);
                extra.remove(extra.size() - 1);
                if (score >= remaining - 1) continue;
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
                if (c.mouth() && score < mouthScore) {
                    mouthScore = score;
                    mouth = c;
                }
            }
            if (best == null) return added;
            // A mouth in the best cell's place, so long as it costs no nest more than the best cell would.
            boolean mouthWill = mouth != null && still(mouthScore, reach) <= still(bestScore, reach);
            Candidate pick = mouthWill ? mouth : best;
            int[] cell = {pick.x(), pick.y()};
            extra.add(cell);
            added.add(cell);
        }
    }

    /**
     * The nests for one gap: {@link #close} over every candidate, unless a chain through street mouths alone closes
     * the gap with at most {@link #MOUTH_PREMIUM} nest more, in which case that one: in built country a gun at a
     * street mouth sees down the street the swarm comes by, where one in a yard between houses sees a wall.
     */
    public static List<int[]> place(List<int[]> west, List<int[]> east, List<int[]> nests, List<Candidate> candidates,
                                    double reach, int maxAdded) {
        List<int[]> any = close(west, east, nests, candidates, reach, maxAdded);
        List<Candidate> mouths = new ArrayList<>();
        for (Candidate c : candidates) if (c.mouth()) mouths.add(c);
        if (mouths.isEmpty()) return any;
        List<int[]> atMouths = close(west, east, nests, mouths, reach, maxAdded);
        if (atMouths.isEmpty() || atMouths.size() > any.size() + MOUTH_PREMIUM) return any;
        List<int[]> chain = new ArrayList<>(nests);
        chain.addAll(atMouths);
        return CordonGapLaw.widestStep(west, east, chain) <= CordonGapLaw.LIMIT ? atMouths : any;
    }

    /**
     * Every cell between two redoubts a nest may stand on: on the straight line from {@code westSeed} to
     * {@code eastSeed} and up to {@link #FRONT_BAND} rows in front of it or {@link #REAR_BAND} behind (in built country,
     * where {@code mouths} is asked for, {@link #BUILT_FRONT_BAND} and {@link #BUILT_REAR_BAND}), open
     * ({@link Ground#takes}), on the highest open ground of its column (the crest, where the line stands on one),
     * not within a cell of a road, and outside every box of
     * {@code keepOut} ({@code [x0, y0, x1, y1]}, the redoubts' own ground). With {@code mouths}, each is marked a
     * street mouth where a street within {@link #MOUTH_REACH} cells beside it runs on {@link #MOUTH_DEPTH} cells
     * toward the front and nothing blocks its view across to it. West to east, the line's own row first.
     */
    public static List<Candidate> candidates(Ground ground, int[] westSeed, int[] eastSeed, int front,
                                             List<int[]> keepOut, boolean mouths) {
        List<Candidate> out = new ArrayList<>();
        int x0 = Math.min(westSeed[0], eastSeed[0]) + 1, x1 = Math.max(westSeed[0], eastSeed[0]) - 1;
        double span = eastSeed[0] - westSeed[0];
        for (int x = x0; x <= x1; x++) {
            double t = span == 0 ? 0 : (x - westSeed[0]) / span;
            int line = (int) Math.round(westSeed[1] + (eastSeed[1] - westSeed[1]) * t);
            // The line's row, then the rows in front of it, then behind it, nearest first.
            int ahead = mouths ? BUILT_FRONT_BAND : FRONT_BAND, behind = mouths ? BUILT_REAR_BAND : REAR_BAND;
            int[] rows = new int[behind + ahead + 1];
            for (int k = 0; k < rows.length; k++) rows[k] = line + front * (k <= ahead ? k : ahead - k);
            // Only the highest open ground in the column: the crest where the line has one, never the foot of a cliff.
            int top = Integer.MIN_VALUE;
            for (int y : rows) if (open(ground, keepOut, x, y)) top = Math.max(top, ground.tier(x, y));
            for (int y : rows) {
                if (!open(ground, keepOut, x, y) || ground.tier(x, y) < top) continue;
                out.add(new Candidate(x, y, mouths && mouth(ground, x, y, front)));
            }
        }
        return out;
    }

    /** Whether a street beside {@code (x, y)} runs from the line toward the front, in view of it. */
    public static boolean mouth(Ground ground, int x, int y, int front) {
        for (int d = 2; d <= MOUTH_REACH; d++) {
            for (int side = -1; side <= 1; side += 2) {
                int rx = x + side * d;
                boolean street = true;
                for (int k = 0; k <= MOUTH_DEPTH && street; k++) street = ground.road(rx, y + front * k);
                if (!street) continue;
                boolean view = true;
                for (int s = 1; s < d && view; s++) view = ground.seeThrough(x + side * s, y + front);
                if (view) return true;
            }
        }
        return false;
    }

    private static boolean open(Ground ground, List<int[]> keepOut, int x, int y) {
        return !inside(keepOut, x, y) && ground.takes(x, y) && !nearRoad(ground, x, y);
    }

    private static boolean nearRoad(Ground ground, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) if (ground.road(x + dx, y + dy)) return true;
        }
        return false;
    }

    private static boolean inside(List<int[]> boxes, int x, int y) {
        for (int[] b : boxes) if (x >= b[0] - 1 && x <= b[2] + 1 && y >= b[1] - 1 && y <= b[3] + 1) return true;
        return false;
    }

    /** Nests still wanted, at the least, with the joined guns {@code remaining} cells short of the far redoubt. */
    private static int still(double remaining, double reach) {
        return remaining <= CordonGapLaw.LIMIT ? 0 : (int) Math.ceil((remaining - CordonGapLaw.LIMIT) / reach);
    }

    /** The guns joined to {@code west} by steps within the law, {@code west}'s own first. */
    static List<int[]> joined(List<int[]> west, List<int[]> others) {
        List<int[]> joined = new ArrayList<>(west);
        boolean[] in = new boolean[others.size()];
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < others.size(); i++) {
                if (in[i] || !within(joined, others.get(i), CordonGapLaw.LIMIT)) continue;
                in[i] = true;
                joined.add(others.get(i));
                grew = true;
            }
        }
        return joined;
    }

    private static double remaining(List<int[]> joined, List<int[]> east) {
        double best = Double.POSITIVE_INFINITY;
        for (int[] a : joined) for (int[] b : east) best = Math.min(best, Math.hypot(a[0] - b[0], a[1] - b[1]));
        return best;
    }

    private static boolean within(List<int[]> guns, int[] c, double reach) {
        for (int[] g : guns) if (Math.hypot(g[0] - c[0], g[1] - c[1]) <= reach) return true;
        return false;
    }

    private static boolean crowded(List<int[]> west, List<int[]> extra, List<int[]> east, int[] c) {
        for (List<int[]> guns : List.of(west, extra, east)) {
            for (int[] g : guns) if (Math.max(Math.abs(g[0] - c[0]), Math.abs(g[1] - c[1])) < SPACING) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- on the map

    /**
     * The map with every gap closed as far as {@link #close} can: each added nest's cell closed to movement as a dug
     * nest's is, bags across its front and flanks where it stands off the course's trench, and the nest appended to
     * its picket course's record. The grid, topology and props are edited in place; the map is returned with the
     * new layout.
     */
    public static MapResult lay(MapResult map, PrecinctPlan plan, CordonPlan.Density density, long seed) {
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        if (redoubts.size() < 2) return map;
        int front = CordonFront.frontward(plan);
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        List<List<int[]>> towers = new ArrayList<>();
        List<int[]> keepOut = new ArrayList<>();
        for (int i = 0; i < redoubts.size(); i++) {
            towers.add(CordonGarrisons.towers(map, redoubts.get(i), i + 1, front));
            int[] box = ground(map, redoubts.get(i), i + 1);
            if (box != null) keepOut.add(box);
        }
        List<Laid> laid = new ArrayList<>(map.layout.pieces());
        // The ways the layout left open: every line's gaps (a road's crossing, a wire funnel) and every laid road. A
        // road the layout carved is not always paved as a street, so it is read from the layout as well.
        Set<Long> ways = new HashSet<>();
        for (Laid piece : laid) {
            if (piece.kind() == Laid.Kind.ROAD) for (int[] c : piece.cells()) ways.add(key(c[0], c[1]));
            if (piece.kind() != Laid.Kind.LINE) continue;
            for (int[] g : piece.gaps()) {
                for (int dy = -WAY_CLEAR; dy <= WAY_CLEAR; dy++) {
                    for (int dx = -WAY_CLEAR; dx <= WAY_CLEAR; dx++) ways.add(key(g[0] + dx, g[1] + dy));
                }
            }
        }
        boolean changed = false;
        for (int n = 1; n < redoubts.size(); n++) {
            String name = CordonPlan.PICKET_LINE + n + "-" + (n + 1);
            Laid picket = map.layout.find(name);
            List<int[]> nests = picket == null || !picket.isLaid() ? List.of() : picket.nests();
            Precinct w = redoubts.get(n - 1), e = redoubts.get(n);
            Set<Long> trench = new HashSet<>();
            if (picket != null) for (int[] c : picket.cells()) trench.add(key(c[0], c[1]));
            Ground ground = mapGround(grid, topology, ways);
            List<Candidate> candidates = candidates(ground, new int[]{w.seedX(), w.seedY()},
                    new int[]{e.seedX(), e.seedY()}, front, keepOut, density != CordonPlan.Density.RURAL);
            List<int[]> added = place(towers.get(n - 1), towers.get(n), nests, candidates, REACH, MAX_ADDED);
            if (added.isEmpty()) continue;
            CordonWorks works = new CordonWorks(grid, topology, map.doodads, seed ^ n * 0x9E3779B97F4A7C15L,
                    w.seedX(), w.seedY());
            for (int[] c : added) {
                map.doodads.removeIf(d -> d.cellX == c[0] && d.cellY == c[1]);
                if (!trench.contains(key(c[0], c[1]))) {
                    works.sandbag(c[0], c[1] + front, new int[]{0, front}, null);
                    works.sandbag(c[0] - 1, c[1], new int[]{-1, 0}, null);
                    works.sandbag(c[0] + 1, c[1], new int[]{1, 0}, null);
                }
                grid.setWalkable(c[0], c[1], false);
                grid.setSeeThrough(c[0], c[1], true);
            }
            works.finish();
            List<int[]> all = new ArrayList<>(nests);
            all.addAll(added);
            String note = GAP_NESTS + added.size();
            Laid out = picket != null && picket.isLaid()
                    ? new Laid(name, picket.kind(), picket.status(),
                            picket.reason().isEmpty() ? note : picket.reason() + "; " + note, picket.required(),
                            picket.cells(), picket.gaps(), all)
                    : new Laid(name, Laid.Kind.LINE, Laid.Status.PARTIAL, note + "; the course "
                            + (picket == null ? "was not planned" : "was refused: " + picket.reason()), false, List.of(),
                            List.of(), all);
            int at = picket == null ? -1 : laid.indexOf(picket);
            if (at >= 0) laid.set(at, out);
            else laid.add(out);
            changed = true;
        }
        return changed ? map.withLayout(new LaidLayout(laid, map.layout == null ? null : map.layout.onWays())) : map;
    }

    /** The open floor of the finished map a nest may stand on, its streets and its sight. */
    public static Ground mapGround(NavigationGrid grid, CellTopology topology, Set<Long> ways) {
        return new Ground() {
            @Override
            public boolean takes(int x, int y) {
                if (!grid.inBounds(x, y) || !open(x, y) || topology.getBuildingId(x, y) != 0
                        || topology.getGroundKind(x, y) == CellTopology.GroundKind.INDOOR) return false;
                ElevationMap elevation = grid.elevation();
                if (elevation != null && (elevation.isFace(x, y) || elevation.isRamp(x, y))) return false;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (!grid.inBounds(x + dx, y + dy) || !grid.isWalkable(x + dx, y + dy)) return false;
                    }
                }
                return true;
            }

            private boolean open(int x, int y) {
                return grid.isWalkable(x, y) && !grid.isDoorway(x, y) && !topology.isFixture(x, y)
                        && !topology.isWall(x, y);
            }

            @Override
            public boolean road(int x, int y) {
                if (!grid.inBounds(x, y) || topology.isWall(x, y)) return false;
                if (topology.isStreet(x, y) || ways.contains(key(x, y))) return true;
                RoadSurface surface = topology.roadSurface();
                RoadSurface.Underlay under = surface == null ? null : surface.underlay(x, y);
                return under != null && !under.shoulder();
            }

            @Override
            public boolean seeThrough(int x, int y) {
                return grid.inBounds(x, y) && grid.isSeeThrough(x, y);
            }

            @Override
            public int tier(int x, int y) {
                return CordonGapNests.tier(grid, x, y);
            }
        };
    }

    private static int tier(NavigationGrid grid, int x, int y) {
        ElevationMap elevation = grid.elevation();
        return elevation == null ? 0 : elevation.tier(x, y);
    }

    /** A redoubt's own ground, {@code [x0, y0, x1, y1]}: its construction's recorded bounds, or its compound's wall. */
    private static int[] ground(MapResult map, Precinct place, int number) {
        Laid works = map.layout.find(CordonConstructions.WORKS + number);
        if (works != null && works.cells().size() == 2) {
            return new int[]{works.cells().get(0)[0], works.cells().get(0)[1], works.cells().get(1)[0],
                    works.cells().get(1)[1]};
        }
        return CordonGarrisons.perimeterBox(map.topology, place);
    }

    private static long key(int x, int y) {
        return (long) x << 32 | (y & 0xffffffffL);
    }
}
