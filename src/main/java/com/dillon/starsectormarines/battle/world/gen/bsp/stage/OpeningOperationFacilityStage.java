package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.OpeningOperationMapPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.nav.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Authors the single mission place requested by a First Contract map.
 *
 * <p>This runs after ordinary district fillers have completed, then fits a
 * hollow, one-block facility into clear ground near the operation's existing
 * command anchor. The staging cells and road reservation remain untouched.
 */
public final class OpeningOperationFacilityStage implements GenStage {

    private static final int FACILITY_SIDE = 7;

    @Override
    public void run(GenContext ctx) {
        OpeningOperationMapPlan plan = ctx.get(BspKeys.OPENING_OPERATION_PLAN);
        if (plan == null) {
            throw new IllegalStateException(
                    "OpeningOperationFacilityStage requires an opening-operation plan");
        }

        FacilitySite site = findSite(ctx, plan);
        if (site == null) {
            throw new IllegalStateException("No clear footprint for opening-operation "
                    + plan.facilityKind() + " on map seed " + ctx.seed + " at "
                    + ctx.width + "x" + ctx.height);
        }

        long siteSeed = mix(ctx.seed, site.left(), site.top(),
                plan.facilityKind().ordinal());
        GenContext facility = new GenContext(ctx.grid, ctx.topology,
                new Random(siteSeed), ctx.width, ctx.height, siteSeed);
        BlockLeaf footprint = new BlockLeaf(site.left(), site.top(),
                site.left() + FACILITY_SIDE - 1,
                site.top() + FACILITY_SIDE - 1, false);
        OpeningOperationFacilityFiller.fill(
                plan.facilityKind(), footprint, facility, site.frontage());

        if (facility.pois.size() != 1
                || facility.pois.get(0).kind != plan.facilityKind()) {
            throw new IllegalStateException("Opening-operation facility filler did not "
                    + "produce " + plan.facilityKind() + " on map seed " + ctx.seed);
        }
        PointOfInterest place = facility.pois.get(0);
        if (!ctx.grid.isWalkable(place.anchorCellX, place.anchorCellY)
                || !ctx.grid.isWalkable(place.interiorAnchorX, place.interiorAnchorY)) {
            throw new IllegalStateException("Opening-operation facility has no usable "
                    + "ingress on map seed " + ctx.seed);
        }
        ctx.pois.add(place);
        ctx.doodads.addAll(facility.doodads);
        ctx.put(BspKeys.OPENING_OPERATION_PLACE, place);
    }

    private static FacilitySite findSite(GenContext ctx, OpeningOperationMapPlan plan) {
        boolean[][] reserved = new boolean[ctx.width][ctx.height];
        for (OpeningOperationMapPlan.Cell cell : plan.reservedCells()) {
            if (cell.x() >= 0 && cell.y() >= 0
                    && cell.x() < ctx.width && cell.y() < ctx.height) {
                reserved[cell.x()][cell.y()] = true;
            }
        }
        boolean[][] occupiedByDoodad = new boolean[ctx.width][ctx.height];
        for (Doodad doodad : ctx.doodads) {
            for (int y = doodad.cellY; y < doodad.cellY + doodad.footprintCellsY; y++) {
                for (int x = doodad.cellX; x < doodad.cellX + doodad.footprintCellsX; x++) {
                    if (x >= 0 && y >= 0 && x < ctx.width && y < ctx.height) {
                        occupiedByDoodad[x][y] = true;
                    }
                }
            }
        }

        boolean[][] roadReservation = ctx.get(BspKeys.ROAD_RESERVATION);
        List<SiteCandidate> candidates = new ArrayList<>();
        for (int top = 1; top <= ctx.height - FACILITY_SIDE - 1; top++) {
            for (int left = 1; left <= ctx.width - FACILITY_SIDE - 1; left++) {
                if (!isClearSite(ctx, left, top, reserved, occupiedByDoodad,
                        roadReservation)) continue;
                int centerX = left + FACILITY_SIDE / 2;
                int centerY = top + FACILITY_SIDE / 2;
                int distance = Math.abs(centerX - plan.targetAnchorX())
                        + Math.abs(centerY - plan.targetAnchorY());
                candidates.add(new SiteCandidate(left, top, distance));
            }
        }
        candidates.sort(Comparator.comparingInt(SiteCandidate::distance));
        for (SiteCandidate candidate : candidates) {
            boolean[][] reachable = reachableWithoutSite(ctx, plan, candidate);
            OpeningOperationFacilityFiller.Frontage frontage =
                    reachableFrontage(ctx, plan, candidate, reachable);
            if (frontage != null) {
                return new FacilitySite(candidate.left(), candidate.top(), frontage);
            }
        }
        return null;
    }

    private static boolean[][] reachableWithoutSite(GenContext ctx,
                                                     OpeningOperationMapPlan plan,
                                                     SiteCandidate site) {
        int startX = plan.targetAnchorX();
        int startY = plan.targetAnchorY();
        if (!ctx.grid.inBounds(startX, startY) || !ctx.grid.isWalkable(startX, startY)
                || insideSite(startX, startY, site)) {
            return new boolean[ctx.width][ctx.height];
        }
        boolean[][] reachable = new boolean[ctx.width][ctx.height];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        reachable[startX][startY] = true;
        queue.add(startY * ctx.width + startX);
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int x = cell % ctx.width;
            int y = cell / ctx.width;
            for (Direction direction : Direction.CARDINALS) {
                int nx = x + direction.dx;
                int ny = y + direction.dy;
                if (!ctx.grid.inBounds(nx, ny) || reachable[nx][ny]
                        || insideSite(nx, ny, site)
                        || !ctx.grid.canTraverseCellStep(x, y, direction)) {
                    continue;
                }
                reachable[nx][ny] = true;
                queue.addLast(ny * ctx.width + nx);
            }
        }
        return reachable;
    }

    private static OpeningOperationFacilityFiller.Frontage reachableFrontage(
            GenContext ctx, OpeningOperationMapPlan plan, SiteCandidate site,
            boolean[][] reachable) {
        OpeningOperationFacilityFiller.Frontage best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (OpeningOperationFacilityFiller.Frontage frontage
                : OpeningOperationFacilityFiller.Frontage.values()) {
            if (!isReachableFrontage(ctx, site, reachable, frontage)) continue;
            int[] anchor = frontageAnchor(site, frontage);
            int distance = Math.abs(anchor[0] - plan.targetAnchorX())
                    + Math.abs(anchor[1] - plan.targetAnchorY());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = frontage;
            }
        }
        return best;
    }

    private static boolean isReachableFrontage(
            GenContext ctx, SiteCandidate site, boolean[][] reachable,
            OpeningOperationFacilityFiller.Frontage frontage) {
        int firstAlong;
        int lastAlong;
        int exteriorX;
        int exteriorY;
        int borderX;
        int borderY;
        switch (frontage) {
            case TOP:
                firstAlong = site.left() + 1;
                lastAlong = site.left() + FACILITY_SIDE - 2;
                exteriorY = site.top() - 1;
                borderY = site.top();
                for (int x = firstAlong; x <= lastAlong; x++) {
                    if (!reachable[x][exteriorY]
                            || !ctx.grid.canTraverseCellStep(x, exteriorY, x, borderY)) {
                        return false;
                    }
                }
                return true;
            case BOTTOM:
                firstAlong = site.left() + 1;
                lastAlong = site.left() + FACILITY_SIDE - 2;
                exteriorY = site.top() + FACILITY_SIDE;
                borderY = site.top() + FACILITY_SIDE - 1;
                for (int x = firstAlong; x <= lastAlong; x++) {
                    if (!reachable[x][exteriorY]
                            || !ctx.grid.canTraverseCellStep(x, exteriorY, x, borderY)) {
                        return false;
                    }
                }
                return true;
            case LEFT:
                firstAlong = site.top() + 1;
                lastAlong = site.top() + FACILITY_SIDE - 2;
                exteriorX = site.left() - 1;
                borderX = site.left();
                for (int y = firstAlong; y <= lastAlong; y++) {
                    if (!reachable[exteriorX][y]
                            || !ctx.grid.canTraverseCellStep(exteriorX, y, borderX, y)) {
                        return false;
                    }
                }
                return true;
            case RIGHT:
                firstAlong = site.top() + 1;
                lastAlong = site.top() + FACILITY_SIDE - 2;
                exteriorX = site.left() + FACILITY_SIDE;
                borderX = site.left() + FACILITY_SIDE - 1;
                for (int y = firstAlong; y <= lastAlong; y++) {
                    if (!reachable[exteriorX][y]
                            || !ctx.grid.canTraverseCellStep(exteriorX, y, borderX, y)) {
                        return false;
                    }
                }
                return true;
            default:
                throw new IllegalStateException("unknown opening-operation frontage");
        }
    }

    private static int[] frontageAnchor(
            SiteCandidate site, OpeningOperationFacilityFiller.Frontage frontage) {
        int centerX = site.left() + FACILITY_SIDE / 2;
        int centerY = site.top() + FACILITY_SIDE / 2;
        return switch (frontage) {
            case TOP -> new int[]{centerX, site.top() - 1};
            case BOTTOM -> new int[]{centerX, site.top() + FACILITY_SIDE};
            case LEFT -> new int[]{site.left() - 1, centerY};
            case RIGHT -> new int[]{site.left() + FACILITY_SIDE, centerY};
        };
    }

    private static boolean insideSite(int x, int y, SiteCandidate site) {
        return x >= site.left() && x < site.left() + FACILITY_SIDE
                && y >= site.top() && y < site.top() + FACILITY_SIDE;
    }

    private static boolean isClearSite(GenContext ctx, int left, int top,
                                       boolean[][] reserved,
                                       boolean[][] occupiedByDoodad,
                                       boolean[][] roadReservation) {
        for (int y = top; y < top + FACILITY_SIDE; y++) {
            for (int x = left; x < left + FACILITY_SIDE; x++) {
                if (!ctx.grid.isWalkable(x, y)
                        || ctx.topology.getBuildingId(x, y) != 0
                        || ctx.topology.getBuildingKindHint(x, y) != null
                        || ctx.topology.isFixture(x, y)
                        || ctx.topology.getGroundKind(x, y) == GroundKind.WATER
                        || ctx.topology.getGroundKind(x, y) == GroundKind.INDOOR
                        || ctx.grid.isDoorway(x, y)
                        || reserved[x][y]
                        || occupiedByDoodad[x][y]
                        || (roadReservation != null && roadReservation[x][y])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long mix(long seed, int x, int y, int kind) {
        long value = seed ^ ((long) x << 32) ^ (y & 0xffffffffL)
                ^ (kind * 0x9E3779B97F4A7C15L);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private record SiteCandidate(int left, int top, int distance) {}

    private record FacilitySite(int left, int top,
                                OpeningOperationFacilityFiller.Frontage frontage) {}
}
