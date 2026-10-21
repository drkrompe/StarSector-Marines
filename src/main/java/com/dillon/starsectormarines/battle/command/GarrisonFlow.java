package com.dillon.starsectormarines.battle.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Pressure-proportional flow: the defender commander's anticipation tier. Believed hostile pressure is gathered into
 * <b>areas</b>, each area asks for as many squads as its pressure and its unmanned garrisonables warrant, and the pool
 * answers by posting squads at a <b>staging cell</b> on the defender's own side of the pressure, so that local
 * assignment (manning, structure work) finds somebody near. Pure planning over frozen inputs; the caller turns the
 * answers into orders and owns every piece of state.
 *
 * <p><b>Areas.</b> The distinct influence blocks holding a believed contact are linked when no more than
 * {@link #LINK_BLOCKS} apart (Chebyshev), and each connected group is one area. Its weight is the sum of the
 * believed hostile pressure over its blocks; its centre is the pressure-weighted mean of the block centres. Blocks are
 * taken in index order and groups ordered by weight then lowest block, so the result does not depend on the order the
 * contacts arrived in. Work is quadratic in the number of contact blocks, which is capped at {@link #MAX_BLOCKS}
 * (the most pressed kept).
 *
 * <p><b>Demand.</b> {@link #demand}: one squad per {@link #WEIGHT_PER_SQUAD} of weight, plus one for each pressed
 * garrisonable that nobody stands on, capped at {@link #MAX_PER_AREA}. What the area already has local (squads
 * manning in it, squads staged for it, free squads within {@link #LOCAL_ETA_TICKS} of its staging cell) is
 * subtracted by the caller.
 *
 * <p><b>Staging cell.</b> {@link #desiredStage}: from the area's centre, {@link #STANDOFF_CELLS} further along the
 * command axis toward the defender's rear, on the same lateral line; the same standoff the convoy's safe deployment
 * band keeps behind contact. {@link #snap} then moves it to the nearest acceptable cell. Belief only: the area centre
 * is a weighted mean of believed contact blocks.
 *
 * <p><b>Order.</b> {@link #sendOrder}: every area with nobody local gets one squad before any area gets a second,
 * most pressed first, within a per-plan bound. Cost is arithmetic over the contact blocks and the areas; nothing
 * here searches.
 *
 * <p><b>Without an axis.</b> {@link #towardPlaces} and {@link #forwardOf} measure the staging cell from an area
 * toward the places it presses instead, for a commander that has no command axis (the mission defenders,
 * {@link MissionGarrisons}), and {@link #nextIsForward} splits an area's squads between waiting at the places and
 * meeting the attack in the street. Conquest's defender stages along its track axis and does not use them yet.
 */
final class GarrisonFlow {

    /** Blocks (Chebyshev) within which two contact blocks belong to one area: 24 cells at the 8-cell lattice. UNTUNED. */
    static final int LINK_BLOCKS = 3;
    /** Contact blocks considered per plan; the most pressed are kept. */
    static final int MAX_BLOCKS = 96;
    /** Areas served per plan, most pressed first. UNTUNED. */
    static final int MAX_AREAS = 6;
    /** Area weight at which an area asks for staged squads. UNTUNED. */
    static final float AREA_START_WEIGHT = 2f;
    /** Area weight below which a staged squad's area counts as quiet. Lower than {@link #AREA_START_WEIGHT}. UNTUNED. */
    static final float AREA_STOP_WEIGHT = 1f;
    /**
     * Weight (summed believed pressure) that warrants one more squad. UNTUNED: on the fort control the first contact
     * group read about 40, a settled assault 100 to 280, so this is roughly one squad per marine squad's worth.
     */
    static final float WEIGHT_PER_SQUAD = 40f;
    /** Most squads an area asks for. UNTUNED. */
    static final int MAX_PER_AREA = 3;
    /** Cells from a post within which an area's centre makes the post that area's. UNTUNED. */
    static final int MATCH_RADIUS_CELLS = 40;
    /** Cells behind the area centre, toward the defender's rear, where squads wait. Matches the convoy's rear standoff. */
    static final int STANDOFF_CELLS = 12;
    /** A free squad this close to an area's staging cell counts as local to it: 10 s of marching. UNTUNED. */
    static final int LOCAL_ETA_TICKS = 300;
    /** Largest share of the command pool that may be standing staged at once. UNTUNED. */
    static final float MAX_STAGED_SHARE = 0.4f;
    /** Staged squads in one area are spread over these offsets from its staging cell. */
    private static final int[][] SPREAD = {{0, 0}, {-4, 0}, {4, 0}, {0, -4}, {0, 4}};

    /** The command axis the staging cell is measured along. {@code ConquestTrackLayout} is the production one. */
    interface Axis {
        float lateralCoordinate(float x, float y);

        float forwardCoordinate(float x, float y);

        int forwardExtent();

        int cellX(int lateral, int forward);

        int cellY(int lateral, int forward);
    }

    /** Whether a cell may be stood on. */
    @FunctionalInterface
    interface CellOk {
        boolean test(int x, int y);
    }

    /**
     * One cluster of pressed blocks: its pressure-weighted centre cell, summed believed pressure and block count.
     * {@code anchorBlock} is the lowest block index in it, which with the weight orders areas stably.
     */
    record Area(int centreX, int centreY, float weight, int blocks, int anchorBlock) { }

    private GarrisonFlow() { }

    /**
     * Gathers pressed blocks into areas, most pressed first, at most {@link #MAX_AREAS}.
     *
     * @param blocks      packed distinct blocks, {@code by * blockWidth + bx}, any order
     * @param pressure    believed hostile pressure at each of those blocks, same order
     * @param count       how many leading entries of both arrays are valid
     * @param blockWidth  width of the block lattice
     * @param blockSize   cells per block side
     */
    static List<Area> areas(int[] blocks, float[] pressure, int count, int blockWidth, int blockSize) {
        if (count <= 0) return List.of();
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        // Heaviest first so the cap drops the faintest; ties to the lower block.
        Arrays.sort(order, Comparator.<Integer>comparingDouble(i -> -pressure[i]).thenComparingInt(i -> blocks[i]));
        int n = Math.min(count, MAX_BLOCKS);
        Integer[] byBlock = Arrays.copyOf(order, n);
        Arrays.sort(byBlock, Comparator.comparingInt(i -> blocks[i]));

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            int bi = blocks[byBlock[i]];
            int xi = bi % blockWidth;
            int yi = bi / blockWidth;
            for (int j = i + 1; j < n; j++) {
                int bj = blocks[byBlock[j]];
                if (Math.abs(bj % blockWidth - xi) <= LINK_BLOCKS && Math.abs(bj / blockWidth - yi) <= LINK_BLOCKS) {
                    int a = find(parent, i);
                    int b = find(parent, j);
                    if (a != b) parent[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        float[] weight = new float[n];
        double[] centreWeight = new double[n];
        double[] sumX = new double[n];
        double[] sumY = new double[n];
        int[] members = new int[n];
        int[] anchor = new int[n];
        Arrays.fill(anchor, Integer.MAX_VALUE);
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            int source = byBlock[i];
            float p = Math.max(pressure[source], 0f);
            int block = blocks[source];
            double cx = (block % blockWidth) * blockSize + blockSize / 2.0;
            double cy = (block / blockWidth) * blockSize + blockSize / 2.0;
            // A faint block still places the area, so the centre stays defined when every block is faint.
            double w = Math.max(p, 1e-3f);
            weight[root] += p;
            centreWeight[root] += w;
            sumX[root] += cx * w;
            sumY[root] += cy * w;
            members[root]++;
            anchor[root] = Math.min(anchor[root], block);
        }
        List<Area> out = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            if (members[r] == 0) continue;
            out.add(new Area((int) Math.round(sumX[r] / centreWeight[r]), (int) Math.round(sumY[r] / centreWeight[r]),
                    weight[r], members[r], anchor[r]));
        }
        out.sort(Comparator.<Area>comparingDouble(a -> -a.weight()).thenComparingInt(Area::anchorBlock));
        return out.size() > MAX_AREAS ? new ArrayList<>(out.subList(0, MAX_AREAS)) : out;
    }
    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /**
     * Squads an area asks for in all: one per {@link #WEIGHT_PER_SQUAD} of weight (at least one once it is worth
     * answering at all), plus one per pressed garrisonable nobody stands on, never more than {@link #MAX_PER_AREA}.
     * Zero below {@link #AREA_START_WEIGHT}.
     */
    static int demand(float weight, int unmannedPressedPlaces) {
        if (weight < AREA_START_WEIGHT) return 0;
        int byWeight = Math.max(1, (int) Math.ceil(weight / WEIGHT_PER_SQUAD));
        return Math.min(MAX_PER_AREA, byWeight + Math.max(0, unmannedPressedPlaces));
    }

    /**
     * The cell squads should wait on for an area centred at {@code (centreX, centreY)}: {@link #STANDOFF_CELLS} further
     * toward the defender's rear along the command axis (forward grows toward the defender), on the same lateral line,
     * clamped to the map. {@code spread} offsets it so several squads in one area do not stand on one cell.
     */
    static int[] desiredStage(Axis axis, int centreX, int centreY, int spread) {
        int lateral = Math.round(axis.lateralCoordinate(centreX, centreY));
        int forward = Math.round(axis.forwardCoordinate(centreX, centreY)) + STANDOFF_CELLS;
        forward = Math.max(0, Math.min(axis.forwardExtent() - 1, forward));
        int x = axis.cellX(lateral, forward);
        int y = axis.cellY(lateral, forward);
        int[] offset = SPREAD[Math.floorMod(spread, SPREAD.length)];
        return new int[]{x + offset[0], y + offset[1]};
    }

    /**
     * Cells from an area's centre toward its places at which a <b>forward</b> squad meets the attack: in the street
     * between the attack and what it is coming for, not behind a parapet. UNTUNED.
     */
    static final int FORWARD_STANDOFF_CELLS = 6;
    /** A forward post needs at least this much ground between it and the places, or the meeting is at the places anyway. */
    static final int FORWARD_CLEARANCE_CELLS = 4;

    /**
     * The staging cell for an area measured toward the places it presses rather than along a command axis: the line
     * from the threat to what it threatens is the defender's side by construction, so this needs no notion of a rear
     * and serves any side on any map. A squad waits at the places themselves when they lie a standoff or more from the
     * area's centre, and {@link #STANDOFF_CELLS} from the centre (behind the places) when they lie nearer. {@code spread}
     * offsets it as {@link #desiredStage} does. With the places on the centre itself there is no direction and the
     * places' cell is the answer.
     *
     * @param placesX the (mean) cell of the places the area presses
     */
    static int[] towardPlaces(int centreX, int centreY, int placesX, int placesY, int spread) {
        double dx = placesX - centreX;
        double dy = placesY - centreY;
        double d = Math.sqrt(dx * dx + dy * dy);
        int x = placesX;
        int y = placesY;
        if (d >= 1 && d < STANDOFF_CELLS) {
            x = (int) Math.round(centreX + dx / d * STANDOFF_CELLS);
            y = (int) Math.round(centreY + dy / d * STANDOFF_CELLS);
        }
        int[] offset = SPREAD[Math.floorMod(spread, SPREAD.length)];
        return new int[]{x + offset[0], y + offset[1]};
    }

    /**
     * Where a forward squad meets an area's attack: {@link #FORWARD_STANDOFF_CELLS} from the area's centre toward its
     * places, or {@code null} when the places stand too near the attack for there to be any street between them
     * (closer than the standoff plus {@link #FORWARD_CLEARANCE_CELLS}).
     */
    static int[] forwardOf(int centreX, int centreY, int placesX, int placesY) {
        double dx = placesX - centreX;
        double dy = placesY - centreY;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < FORWARD_STANDOFF_CELLS + FORWARD_CLEARANCE_CELLS) return null;
        return new int[]{(int) Math.round(centreX + dx / d * FORWARD_STANDOFF_CELLS),
                (int) Math.round(centreY + dy / d * FORWARD_STANDOFF_CELLS)};
    }

    /**
     * Whether an area's next staged squad should meet the attack forward rather than wait at the places (`garrisonables.md`,
     * not every fight is into prepared positions): the first squad of an area goes back, so its places have somebody to
     * man them, and from then on back and forward alternate, back first.
     *
     * @param back    squads the area already has at or on its places (staged back, or manning there)
     * @param forward squads the area already has forward
     */
    static boolean nextIsForward(int back, int forward) {
        return back > forward;
    }

    /** The acceptable cell nearest {@code (x, y)} on the smallest diamond ring up to {@code maxRadius}, or {@code null}. */
    static int[] snap(int x, int y, int maxRadius, CellOk ok) {
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int dx = radius - Math.abs(dy);
                if (ok.test(x - dx, y + dy)) return new int[]{x - dx, y + dy};
                if (dx != 0 && ok.test(x + dx, y + dy)) return new int[]{x + dx, y + dy};
            }
        }
        return null;
    }

    /**
     * The order in which to try to satisfy the areas' unmet demand, as indices into the caller's area list: every area
     * with nothing local and something unmet first (a picket each), then second squads and later in rounds, areas in the
     * order given. At most {@code max} entries.
     *
     * @param unmet squads still wanted per area
     * @param local squads already local per area
     */
    static int[] sendOrder(int[] unmet, int[] local, int max) {
        List<Integer> out = new ArrayList<>();
        int[] left = unmet.clone();
        for (int i = 0; i < left.length && out.size() < max; i++) {
            if (left[i] > 0 && local[i] == 0) {
                out.add(i);
                left[i]--;
            }
        }
        boolean progressed = true;
        while (out.size() < max && progressed) {
            progressed = false;
            for (int i = 0; i < left.length && out.size() < max; i++) {
                if (left[i] > 0) {
                    out.add(i);
                    left[i]--;
                    progressed = true;
                }
            }
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    /** How many squads may be staged at once from a command pool of this size: a share of it, never fewer than one. */
    static int stagedCap(int poolSize) {
        return Math.max(1, (int) (poolSize * MAX_STAGED_SHARE));
    }

    /** Which command track a cell lies on, or a negative number off every track. */
    @FunctionalInterface
    interface TrackOf {
        int of(int x, int y);
    }

    /**
     * The area a threatened track's rally maps onto: the most pressed area (the list is ordered heaviest first) worth
     * answering whose centre lies on that track, or -1. A threatened track and a forward band are the commander's
     * coarse view of pressure; this ties the same pressure's finer area to it, so the rally and the flow reserve
     * squads against one area rather than two competing views.
     */
    static int areaOfTrack(List<Area> areas, TrackOf trackOf, int track) {
        for (int i = 0; i < areas.size(); i++) {
            Area area = areas.get(i);
            if (area.weight() < AREA_STOP_WEIGHT) continue;
            if (trackOf.of(area.centreX(), area.centreY()) == track) return i;
        }
        return -1;
    }

    /** Index of the area whose centre is nearest {@code (x, y)} and within {@link #MATCH_RADIUS_CELLS} (Chebyshev), or -1. */
    static int matchingArea(List<Area> areas, int x, int y) {
        int best = -1;
        long bestGap = Long.MAX_VALUE;
        for (int i = 0; i < areas.size(); i++) {
            Area area = areas.get(i);
            if (area.weight() < AREA_STOP_WEIGHT) continue;
            int dx = Math.abs(area.centreX() - x);
            int dy = Math.abs(area.centreY() - y);
            if (Math.max(dx, dy) > MATCH_RADIUS_CELLS) continue;
            long gap = (long) dx * dx + (long) dy * dy;
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }
}
