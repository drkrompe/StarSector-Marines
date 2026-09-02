package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.LandingPad;

/**
 * Where the attacking force actually arrives, once the places have grown.
 *
 * <p>A mission states two things — {@link MapPlacement} for roughly where, and
 * {@link Standoff} for how much approach it wants — and neither can be turned
 * into cells until the map exists, because the objective's claim is grown rather
 * than authored. This is that resolution, and it is one function on purpose:
 * the spawn anchor and the beachhead are the same arrival, and two copies of the
 * rule would be two answers the first time either moved.
 *
 * <p>The stated band keeps its own depth and lateral extent and is slid along
 * the traversal axis toward the objective until its objective-facing side is
 * {@code standoff} cells short of the claim. It never slides past the map edge
 * it came from — a standoff longer than the map already gives is simply the map
 * it already gives — and it can never reach the claim, because every standoff
 * that slides at all asks for ground between the two.
 *
 * <p><b>The approach is carried rather than inferred.</b> A region against a map
 * edge lands on it, which is what a stated band always is; a region slid inward
 * touches no edge at all, and asking it which edge it is nearest would hand an
 * interior band a side approach it never had. So the approach is read off the
 * band the mission stated, before any slide, and the berths are scanned inward
 * from the region's side that faces it.
 *
 * @param approach      which way the shuttles come in, from the stated band
 * @param standoffCells the approach this map actually affords, which is the
 *                      requested value whenever it could be met
 */
public record ApproachRegion(int x0, int y0, int x1, int y1,
                             LandingPad.Approach approach, int standoffCells) {

    /** The region as the inclusive {@code {x0, y0, x1, y1}} rect every consumer reads. */
    public int[] bounds() {
        return new int[]{x0, y0, x1, y1};
    }

    /** Whether a cell falls inside the region. */
    public boolean contains(int x, int y) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }

    /**
     * The region a finished map's plan and claim resolve to.
     *
     * <p>The plan's own {@link PrecinctPlan#attackerFrom()} when it stated one,
     * and {@link #awayFrom} when it did not. A map with no claim yet, or nothing
     * programmed on it to stand off from, keeps the stated band.
     *
     * @param claim per-cell precinct index, or {@code null} before the claim pass
     */
    public static ApproachRegion resolve(PrecinctPlan plan, int[][] claim,
                                         int width, int height) {
        Precinct objective = plan.objective();
        MapPlacement from = plan.attackerFrom();
        if (from == null) from = awayFrom(objective, width, height);
        int[] claimBounds = objective == null || claim == null
                ? null
                : claimBounds(claim, plan.precincts().indexOf(objective), width, height);
        return resolve(from, plan.standoff(), claimBounds, width, height);
    }

    /**
     * The region a stated band and standoff resolve to against one claim.
     *
     * @param claimBounds the objective claim's inclusive {@code {x0, y0, x1, y1}}
     *                    extent, or {@code null} when there is nothing to stand
     *                    off from
     */
    public static ApproachRegion resolve(MapPlacement from, Standoff standoff,
                                         int[] claimBounds, int width, int height) {
        int[] stated = from.bounds(width, height);
        LandingPad.Approach approach = approachFor(stated, width, height);
        if (claimBounds == null) {
            return new ApproachRegion(stated[0], stated[1], stated[2], stated[3],
                    approach, Integer.MAX_VALUE);
        }
        int gap = gapTo(stated, approach, claimBounds);
        if (!standoff.slides()) {
            return new ApproachRegion(stated[0], stated[1], stated[2], stated[3],
                    approach, gap);
        }
        // Never backward: a standoff longer than the band already gives would
        // slide the region off the edge it arrived across.
        long slide = Math.max(0L, (long) gap - standoff.cells());
        slide = Math.min(slide, headroom(stated, approach, width, height));
        int step = (int) slide;
        return switch (approach) {
            case SOUTH -> new ApproachRegion(stated[0], stated[1] + step,
                    stated[2], stated[3] + step, approach, gap - step);
            case NORTH -> new ApproachRegion(stated[0], stated[1] - step,
                    stated[2], stated[3] - step, approach, gap - step);
            case WEST -> new ApproachRegion(stated[0] + step, stated[1],
                    stated[2] + step, stated[3], approach, gap - step);
            case EAST -> new ApproachRegion(stated[0] - step, stated[1],
                    stated[2] - step, stated[3], approach, gap - step);
        };
    }

    /**
     * The third of the map whose middle is furthest from the objective.
     *
     * <p>What an attacker that was told nothing gets. A force landing beside the
     * thing it is meant to take has no approach to fight through, which is most
     * of what a conquest map is for.
     */
    public static MapPlacement awayFrom(Precinct objective, int width, int height) {
        if (objective == null) return MapPlacement.ANYWHERE;
        MapPlacement[] corners = {
                MapPlacement.SOUTH_WEST, MapPlacement.SOUTH_EAST,
                MapPlacement.NORTH_WEST, MapPlacement.NORTH_EAST};
        MapPlacement best = corners[0];
        long bestDist = -1;
        for (MapPlacement corner : corners) {
            int[] centre = corner.centre(width, height);
            long dx = centre[0] - objective.seedX();
            long dy = centre[1] - objective.seedY();
            long dist = dx * dx + dy * dy;
            if (dist > bestDist) {
                bestDist = dist;
                best = corner;
            }
        }
        return best;
    }

    /**
     * The inclusive extent of one precinct's claimed ground, or {@code null} when
     * it claimed nothing at all.
     */
    public static int[] claimBounds(int[][] claim, int who, int width, int height) {
        int x0 = width;
        int y0 = height;
        int x1 = -1;
        int y1 = -1;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] != who) continue;
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                x1 = Math.max(x1, x);
                y1 = Math.max(y1, y);
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
    }

    /**
     * Which way the stated band faces off the map.
     *
     * <p>A band against an edge lands on it. A band against two — every corner
     * placement, which is what an unstated attacker gets — takes the edge with
     * the longer frontage inside it, because a beachhead wants room to put
     * several areas side by side. A band against none takes the nearest edge,
     * which is the {@link MapPlacement#CENTRE} case: the approach still has to be
     * a real direction for the shuttles to fly in on.
     */
    private static LandingPad.Approach approachFor(int[] stated, int width, int height) {
        int lateralX = stated[2] - stated[0] + 1;
        int lateralY = stated[3] - stated[1] + 1;
        boolean touchesSouth = stated[1] <= 0;
        boolean touchesWest = stated[0] <= 0;
        boolean touchesNorth = stated[3] >= height - 1;
        boolean touchesEast = stated[2] >= width - 1;
        if (touchesSouth || touchesWest || touchesNorth || touchesEast) {
            LandingPad.Approach best = null;
            int bestFrontage = -1;
            if (touchesSouth && lateralX > bestFrontage) {
                best = LandingPad.Approach.SOUTH;
                bestFrontage = lateralX;
            }
            if (touchesWest && lateralY > bestFrontage) {
                best = LandingPad.Approach.WEST;
                bestFrontage = lateralY;
            }
            if (touchesNorth && lateralX > bestFrontage) {
                best = LandingPad.Approach.NORTH;
                bestFrontage = lateralX;
            }
            if (touchesEast && lateralY > bestFrontage) {
                best = LandingPad.Approach.EAST;
            }
            return best;
        }
        int south = stated[1];
        int west = stated[0];
        int north = height - 1 - stated[3];
        int east = width - 1 - stated[2];
        int nearest = Math.min(Math.min(south, west), Math.min(north, east));
        if (south == nearest) return LandingPad.Approach.SOUTH;
        if (west == nearest) return LandingPad.Approach.WEST;
        if (north == nearest) return LandingPad.Approach.NORTH;
        return LandingPad.Approach.EAST;
    }

    /**
     * Cells between the band's objective-facing side and the claim's
     * attacker-facing boundary. Negative where the two already overlap, which a
     * slide of zero leaves alone.
     */
    private static int gapTo(int[] stated, LandingPad.Approach approach, int[] claim) {
        return switch (approach) {
            case SOUTH -> claim[1] - stated[3];
            case NORTH -> stated[1] - claim[3];
            case WEST -> claim[0] - stated[2];
            case EAST -> stated[0] - claim[2];
        };
    }

    /** How far the band could slide before its leading side leaves the map. */
    private static int headroom(int[] stated, LandingPad.Approach approach,
                                int width, int height) {
        return switch (approach) {
            case SOUTH -> height - 1 - stated[3];
            case NORTH -> stated[1];
            case WEST -> width - 1 - stated[2];
            case EAST -> stated[0];
        };
    }
}
