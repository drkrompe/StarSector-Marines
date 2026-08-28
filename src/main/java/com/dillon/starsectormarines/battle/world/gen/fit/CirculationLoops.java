package com.dillon.starsectormarines.battle.world.gen.fit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Finds the one cut that most shortens the walk between two parts of a
 * circulation network already built.
 *
 * <p>Circulation that grows by reaching each room from the nearest thing already
 * connected is a <b>tree</b>. Every branch is a dead end, and two rooms a few
 * cells apart on different branches are walked between by going all the way back
 * to the trunk and out again. That is not a defect of any one placement — each
 * passage took the cheapest honest route to what existed when it was cut — so it
 * cannot be fixed while the rooms are going down. It is fixed afterwards, by
 * looking at the finished network and asking where a short cut would buy a long
 * walk.
 *
 * <p><b>The search starts at the dead ends.</b> On a tree the pair of places with
 * the worst detour between them is always a pair of leaves, so a leaf is where a
 * cut is worth looking for and every other cell is somewhere it might come out.
 * Growing one frontier from the whole network at once cannot find these: it hands
 * each cell of open deck to the <em>nearest</em> passage, and the nearest passage
 * is nearly always one already joined a few cells away. Measured on five hulls,
 * that search proposed hundreds of links and not one of them joined places more
 * than forty cells apart along the halls.
 *
 * <p><b>A loop must earn itself.</b> Joining every pair of passages that happen
 * to run near each other turns a deck into an open field with furniture in it:
 * no route is defensible, no compartment is behind anything, and clearing the
 * ship means nothing because there is always a way round. So a cut is made only
 * where the existing walk is several times its own length, and only where it
 * saves a distance worth walking.
 *
 * <p>Nothing here knows what kind of place it is joining, or how to carve
 * anything. It is handed three grids and returns the best cut it can find; the
 * caller owns what the grids mean and what carving does. That is why the same
 * pass serves a hull and a walled compound.
 */
public final class CirculationLoops {

    /** Cost at or below which a cell is open deck rather than structure. */
    private static final int OPEN_COST = 1;
    private static final int[][] STEPS = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };

    /**
     * How close two dead ends may be and still both be searched from. The tip of
     * a two-abreast passage is a small block of cells that are all equally deep;
     * without this the same dead end would be searched from four times.
     */
    private static final int TIP_SPACING = 4;

    /**
     * When a cut is worth making.
     *
     * @param maxCutCost longest cut to consider, in route cost. Bounds the
     *     search rather than expressing taste: what keeps a deck from filling
     *     with links is the detour factor, not the budget.
     * @param detourFactor how many times its own length the existing walk must
     *     be. This is the whole of the pass's restraint: at one it would join
     *     everything adjacent, and the deck would stop having routes at all.
     * @param minSaving cells of walking a cut must remove. Keeps the pass off
     *     links that are defensible by ratio but pointless in practice, which
     *     is most of what a ratio alone accepts near a junction.
     */
    public record Policy(int maxCutCost, int detourFactor, int minSaving) {}

    /**
     * One cut worth making.
     *
     * @param route cells to carve, in order from the dead end outward
     * @param cost cost of the deck actually carved, which is what "fewest cuts"
     *     is counted in
     * @param steps cells walked from the dead end to the far side of the cut,
     *     including any walk along existing circulation to reach where the cut
     *     sets out from
     * @param saving cells of walking it removes between the two ends it joins
     */
    public record Link(List<int[]> route, int cost, int steps, int saving) {}

    private CirculationLoops() {}

    /**
     * The best cut available, or null when nothing on this network earns one.
     *
     * @param routable per-cell cost of running a passage through a cell, or -1
     *     where a passage may not go. Cells at or below {@link #OPEN_COST} are
     *     open deck; dearer ones are structure being crossed.
     * @param circulation connective walkable space as it stands. These cells are
     *     both the ends a cut may join and the network the saving is measured
     *     over — a walk through somebody's quarters is not circulation, so a
     *     room lying between two passages does not count as joining them.
     * @param throughable cells a passage crossing structure may emerge into:
     *     unclaimed space, or circulation. Structure may be crossed only into
     *     one of these, which is what makes a crossing a door rather than a
     *     wall being followed and unzipped.
     */
    public static Link best(int[][] routable, boolean[][] circulation,
                            boolean[][] throughable, Policy policy) {
        Grid grid = new Grid(routable, circulation, throughable,
                routable.length, routable[0].length);

        Link best = null;
        long bestScore = 0;
        for (int tip : deadEnds(grid)) {
            Link link = fromDeadEnd(grid, policy, walk(grid, tip));
            if (link == null) continue;
            long score = (long) link.saving() * 1000L / link.cost();
            if (best != null && score <= bestScore) continue;
            best = link;
            bestScore = score;
        }
        return best;
    }

    /** The three grids and their extent, carried together so nothing needs six arguments. */
    private record Grid(int[][] routable, boolean[][] circulation,
                        boolean[][] throughable, int width, int height) {

        int index(int x, int y) {
            return x * height + y;
        }

        boolean inBounds(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height;
        }

        /**
         * Whether a passage entering this cell on this heading crosses the
         * structure rather than running along it. Open deck is always crossable;
         * structure may only be entered when the cell straight ahead is clear,
         * which is a door.
         */
        boolean crossesCleanly(int x, int y, int stepX, int stepY) {
            if (routable[x][y] <= OPEN_COST) return true;
            int aheadX = x + stepX;
            int aheadY = y + stepY;
            if (!inBounds(aheadX, aheadY)) return false;
            return throughable[aheadX][aheadY];
        }
    }

    /**
     * The tips of the network — the cells nothing lies beyond.
     *
     * <p>Found as the local maxima of depth from one end of the network's
     * longest run, which is the ordinary way to get at a tree's shape without
     * being told where its root is. Being told would not help: this pass serves
     * a hull whose trunk is its spine and a compound whose trunk is its
     * approach, and a dead end is the same idea in both.
     */
    private static List<Integer> deadEnds(Grid grid) {
        int first = -1;
        for (int x = 0; x < grid.width() && first < 0; x++) {
            for (int y = 0; y < grid.height(); y++) {
                if (grid.circulation()[x][y]) {
                    first = grid.index(x, y);
                    break;
                }
            }
        }
        if (first < 0) return List.of();

        int root = farthest(walk(grid, first));
        int[] depth = walk(grid, root);

        List<Integer> peaks = new ArrayList<>();
        for (int x = 0; x < grid.width(); x++) {
            for (int y = 0; y < grid.height(); y++) {
                if (!grid.circulation()[x][y]) continue;
                int here = grid.index(x, y);
                if (depth[here] <= 0) continue;
                boolean peak = true;
                for (int[] side : STEPS) {
                    int nx = x + side[0];
                    int ny = y + side[1];
                    if (!grid.inBounds(nx, ny) || !grid.circulation()[nx][ny]) continue;
                    if (depth[grid.index(nx, ny)] > depth[here]) peak = false;
                }
                if (peak) peaks.add(here);
            }
        }
        peaks.sort(Comparator.<Integer>comparingInt(cell -> -depth[cell])
                .thenComparingInt(cell -> cell));

        List<Integer> tips = new ArrayList<>();
        for (int peak : peaks) {
            boolean crowded = false;
            for (int held : tips) {
                int dx = peak / grid.height() - held / grid.height();
                int dy = peak % grid.height() - held % grid.height();
                if (Math.abs(dx) + Math.abs(dy) < TIP_SPACING) crowded = true;
            }
            if (!crowded) tips.add(peak);
        }
        return tips;
    }

    private static int farthest(int[] distances) {
        int best = 0;
        int bestAt = 0;
        for (int cell = 0; cell < distances.length; cell++) {
            if (distances[cell] > best) {
                best = distances[cell];
                bestAt = cell;
            }
        }
        return bestAt;
    }

    /**
     * The best cut this dead end can profit by, or null when nothing it can
     * reach is far enough away along the halls to be worth joining.
     *
     * <p>The cut is not required to start <em>at</em> the dead end. A stub that
     * ends among compartments has no open deck at its tip at all, and insisting
     * on leaving from the tip found nothing on four of five measured hulls. So a
     * link may set out from anywhere on the network — and the walk out to where
     * it sets out from is charged against it, which is what keeps the arithmetic
     * honest and what keeps a link that starts halfway back from being scored as
     * though it started here.
     *
     * <p>Two quantities are carried because they are two different questions.
     * <b>Reach</b> is how far out of its way the dead end goes, and orders the
     * search. <b>Cut</b> is what is actually carved, and is what the link is
     * judged by: "fewest cuts" is a count of new deck, not of steps walked.
     *
     * @param walk distance along existing circulation from the dead end, which
     *     is both what a departure is charged and what an arrival is scored
     *     against
     */
    private static Link fromDeadEnd(Grid grid, Policy policy, int[] walk) {
        int cells = grid.width() * grid.height();
        int[] reach = new int[cells];
        int[] cut = new int[cells];
        int[] steps = new int[cells];
        int[] cameFrom = new int[cells];
        Arrays.fill(reach, Integer.MAX_VALUE);
        Arrays.fill(cameFrom, -1);

        PriorityQueue<int[]> frontier = new PriorityQueue<>(
                Comparator.<int[]>comparingInt(entry -> entry[1])
                        .thenComparingInt(entry -> entry[0]));
        for (int x = 0; x < grid.width(); x++) {
            for (int y = 0; y < grid.height(); y++) {
                int step = grid.routable()[x][y];
                if (step < 0 || step > policy.maxCutCost()) continue;
                int departure = departure(grid, walk, x, y);
                if (departure < 0) continue;
                int index = grid.index(x, y);
                reach[index] = departure + step;
                cut[index] = step;
                steps[index] = departure + 1;
                frontier.add(new int[]{ index, reach[index] });
            }
        }

        Link best = null;
        long bestScore = 0;
        while (!frontier.isEmpty()) {
            int[] entry = frontier.poll();
            int index = entry[0];
            if (entry[1] > reach[index]) continue;
            int x = index / grid.height();
            int y = index % grid.height();

            Link arrival = arrival(grid, policy, walk, cut, steps, cameFrom, x, y);
            if (arrival != null) {
                long score = (long) arrival.saving() * 1000L / arrival.cost();
                if (best == null || score > bestScore) {
                    best = arrival;
                    bestScore = score;
                }
            }

            for (int[] side : STEPS) {
                int nx = x + side[0];
                int ny = y + side[1];
                if (!grid.inBounds(nx, ny)) continue;
                int step = grid.routable()[nx][ny];
                if (step < 0) continue;
                if (!grid.crossesCleanly(nx, ny, side[0], side[1])) continue;
                if (cut[index] + step > policy.maxCutCost()) continue;
                int next = reach[index] + step;
                int neighbour = grid.index(nx, ny);
                if (next >= reach[neighbour]) continue;
                reach[neighbour] = next;
                cut[neighbour] = cut[index] + step;
                steps[neighbour] = steps[index] + 1;
                cameFrom[neighbour] = index;
                frontier.add(new int[]{ neighbour, next });
            }
        }
        return best;
    }

    /**
     * How far the dead end must be walked to set out from this cell, or -1 when
     * a cut may not leave the network here at all.
     */
    private static int departure(Grid grid, int[] walk, int x, int y) {
        int nearest = -1;
        for (int[] side : STEPS) {
            int nx = x + side[0];
            int ny = y + side[1];
            if (!grid.inBounds(nx, ny) || !grid.circulation()[nx][ny]) continue;
            int from = walk[grid.index(nx, ny)];
            if (from < 0) continue;
            if (!grid.crossesCleanly(x, y, -side[0], -side[1])) continue;
            if (nearest < 0 || from < nearest) nearest = from;
        }
        return nearest;
    }

    /**
     * What arriving back at the network from this cell would be worth, or null
     * when it touches nothing, or nothing far enough away to bother with.
     */
    private static Link arrival(Grid grid, Policy policy, int[] walk, int[] cut,
                                int[] steps, int[] cameFrom, int x, int y) {
        int index = grid.index(x, y);
        if (cut[index] <= 0) return null;
        int furthest = -1;
        for (int[] side : STEPS) {
            int nx = x + side[0];
            int ny = y + side[1];
            if (!grid.inBounds(nx, ny) || !grid.circulation()[nx][ny]) continue;
            if (!grid.crossesCleanly(x, y, side[0], side[1])) continue;
            int reachedBy = walk[grid.index(nx, ny)];
            if (reachedBy > furthest) furthest = reachedBy;
        }
        if (furthest < 0) return null;
        // A link is one cell longer than the deck it crosses: both ends step
        // onto circulation that was already there.
        int span = steps[index] + 1;
        if (furthest < (long) policy.detourFactor() * span) return null;
        int saving = furthest - span;
        if (saving < policy.minSaving()) return null;
        return new Link(trace(cameFrom, index, grid.height()), cut[index],
                steps[index], saving);
    }

    /** Walk one cut back to the dead end it set out from, in the order it is carved. */
    private static List<int[]> trace(int[] cameFrom, int end, int height) {
        List<int[]> back = new ArrayList<>();
        for (int index = end; index >= 0; index = cameFrom[index]) {
            back.add(new int[]{ index / height, index % height });
        }
        List<int[]> route = new ArrayList<>(back.size());
        for (int i = back.size() - 1; i >= 0; i--) route.add(back.get(i));
        return List.copyOf(route);
    }

    /** Walking distance from one circulation cell to every other, or -1 where it does not reach. */
    private static int[] walk(Grid grid, int start) {
        int[] dist = new int[grid.width() * grid.height()];
        Arrays.fill(dist, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        dist[start] = 0;
        queue.add(start);
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int x = index / grid.height();
            int y = index % grid.height();
            for (int[] side : STEPS) {
                int nx = x + side[0];
                int ny = y + side[1];
                if (!grid.inBounds(nx, ny) || !grid.circulation()[nx][ny]) continue;
                int neighbour = grid.index(nx, ny);
                if (dist[neighbour] >= 0) continue;
                dist[neighbour] = dist[index] + 1;
                queue.add(neighbour);
            }
        }
        return dist;
    }
}
