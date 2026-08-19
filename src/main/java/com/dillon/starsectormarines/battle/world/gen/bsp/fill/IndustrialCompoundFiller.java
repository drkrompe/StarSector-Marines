package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Three-parcel industrial works: the qualifying seed becomes a tactical
 * factory, the larger neighbor becomes a transparent-fenced service yard,
 * and the remaining parcel becomes a utility warehouse. All three frontages
 * face an internal striped apron while the reserved road centerline remains
 * open for vehicles.
 */
public final class IndustrialCompoundFiller implements CompoundFiller {

    private static final int BRIDGE_SCAN_DEPTH = 5;
    static final int GATE_WIDTH = 2;

    private static final BuildingShellCore.BuildingConfig UTILITY_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR, "WAREHOUSE", PointOfInterest.Kind.DEPOT,
                    BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.INDUSTRIAL);

    @Override
    public BlockKind kind() {
        return BlockKind.INDUSTRIAL_COMPOUND;
    }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        if (compound.members.size() != 3) {
            throw new IllegalArgumentException("Industrial compound requires exactly three parcels");
        }

        boolean[][] memberCells = memberMask(compound, ctx.grid);
        boolean[][] apron = repaintSharedApron(compound, memberCells,
                ctx.get(BspKeys.ROAD_CELLS), ctx.get(BspKeys.ROAD_RESERVATION),
                ctx.grid, ctx.topology);

        BlockLeaf factory = compound.seed;
        List<BlockLeaf> neighbors = new ArrayList<>();
        for (BlockLeaf member : compound.members) {
            if (member != factory) neighbors.add(member);
        }
        neighbors.sort(Comparator.comparingInt(BlockLeaf::area).reversed());
        BlockLeaf yard = neighbors.get(0);
        BlockLeaf utility = neighbors.get(1);

        BuildingPlacement factoryPlacement = placementToward(factory, apron);
        BuildingPlacement utilityPlacement = placementToward(utility, apron);
        BuildingPlacement.Side yardFrontage = findFrontage(yard, apron);

        carveBuilding(factory, BuildingIndustrialFiller.CONFIG, factoryPlacement, ctx);
        carveBuilding(utility, UTILITY_CONFIG, utilityPlacement, ctx);
        carveFencedYard(yard, yardFrontage, apron, ctx);
        furnishApron(compound, apron, ctx.get(BspKeys.ROAD_RESERVATION), ctx);
    }

    private static boolean[][] memberMask(Compound compound, NavigationGrid grid) {
        boolean[][] mask = new boolean[grid.getWidth()][grid.getHeight()];
        for (BlockLeaf member : compound.members) {
            for (int y = member.top; y <= member.bottom; y++) {
                for (int x = member.left; x <= member.right; x++) mask[x][y] = true;
            }
        }
        return mask;
    }

    private static boolean[][] repaintSharedApron(Compound compound,
                                                    boolean[][] memberCells,
                                                    boolean[][] roadCells,
                                                    boolean[][] roadReservation,
                                                    NavigationGrid grid,
                                                    CellTopology topology) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        boolean[][] apron = new boolean[width][height];
        int minX = Math.max(0, compound.left - 1);
        int maxX = Math.min(width - 1, compound.right + 1);
        int minY = Math.max(0, compound.top - 1);
        int maxY = Math.min(height - 1, compound.bottom + 1);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (memberCells[x][y] || !roadCells[x][y]) continue;
                boolean north = scan(memberCells, x, y, 0, -1);
                boolean south = scan(memberCells, x, y, 0, 1);
                boolean east = scan(memberCells, x, y, 1, 0);
                boolean west = scan(memberCells, x, y, -1, 0);
                if (!((north && south) || (east && west))) continue;
                // Frontages and yard gates need to recognize the entire shared
                // circulation corridor, including its reserved vehicle lane.
                // Only the unreserved shoulders receive apron paint and props.
                apron[x][y] = true;
                if (roadReservation[x][y]) continue;
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }
        return apron;
    }

    private static boolean scan(boolean[][] memberCells, int x, int y, int dx, int dy) {
        int width = memberCells.length;
        int height = memberCells[0].length;
        int cx = x + dx;
        int cy = y + dy;
        for (int depth = 0; depth < BRIDGE_SCAN_DEPTH; depth++) {
            if (cx < 0 || cx >= width || cy < 0 || cy >= height) return false;
            if (memberCells[cx][cy]) return true;
            cx += dx;
            cy += dy;
        }
        return false;
    }

    private static BuildingPlacement placementToward(BlockLeaf leaf, boolean[][] apron) {
        BuildingPlacement.Side frontage = findFrontage(leaf, apron);
        return frontage == null ? BuildingPlacement.DEFAULT
                : new BuildingPlacement(frontage, true);
    }

    private static BuildingPlacement.Side findFrontage(BlockLeaf leaf, boolean[][] apron) {
        BuildingPlacement.Side best = null;
        int bestScore = 0;
        for (BuildingPlacement.Side side : BuildingPlacement.Side.values()) {
            int score = frontageScore(leaf, side, apron);
            if (score > bestScore) {
                best = side;
                bestScore = score;
            }
        }
        return best;
    }

    private static int frontageScore(BlockLeaf leaf, BuildingPlacement.Side side,
                                     boolean[][] apron) {
        int score = 0;
        int min = horizontalSide(side) ? leaf.left + 1 : leaf.top + 1;
        int max = horizontalSide(side) ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            for (int depth = 1; depth <= BRIDGE_SCAN_DEPTH; depth++) {
                int x = side == BuildingPlacement.Side.LEFT ? leaf.left - depth
                        : side == BuildingPlacement.Side.RIGHT ? leaf.right + depth : along;
                int y = side == BuildingPlacement.Side.TOP ? leaf.top - depth
                        : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom + depth : along;
                if (x < 0 || x >= apron.length || y < 0 || y >= apron[0].length) break;
                if (!apron[x][y]) continue;
                score += BRIDGE_SCAN_DEPTH + 1 - depth;
                break;
            }
        }
        return score;
    }

    private static void carveBuilding(BlockLeaf leaf,
                                      BuildingShellCore.BuildingConfig config,
                                      BuildingPlacement placement,
                                      GenContext ctx) {
        PointOfInterest poi = BuildingShellCore.carve(
                leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, config, placement);
        if (poi != null) ctx.pois.add(poi);
    }

    private static void carveFencedYard(BlockLeaf yard,
                                        BuildingPlacement.Side frontage,
                                        boolean[][] apron,
                                        GenContext ctx) {
        if (frontage == null) frontage = BuildingPlacement.Side.LEFT;
        int gateStart = gateStart(yard, frontage, apron);
        IndustrialYardFiller.paintOpenDirt(yard, ctx.grid, ctx.topology);
        if (yard.width() > 4 && yard.height() > 4) {
            BlockLeaf inset = new BlockLeaf(
                    yard.left + 1, yard.top + 1, yard.right - 1, yard.bottom - 1, false);
            IndustrialYardFiller.furnishArea(inset, ctx, true, ctx.rng.nextInt(4));
        }
        clearGateApproach(yard, frontage, gateStart, ctx);
        stampFence(yard, frontage, gateStart, ctx);
    }

    private static void stampFence(BlockLeaf yard,
                                   BuildingPlacement.Side frontage,
                                   int gateStart,
                                   GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        for (int y = yard.top; y <= yard.bottom; y++) {
            for (int x = yard.left; x <= yard.right; x++) {
                if (x != yard.left && x != yard.right && y != yard.top && y != yard.bottom) continue;
                if (onGate(x, y, yard, frontage, gateStart)) {
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.grid.setDoorway(x, y, true);
                    ctx.grid.openAllEdges(x, y);
                    ctx.topology.setGroundKind(x, y, GroundKind.STRIPED);
                    continue;
                }
                String id = fenceId(x, y, yard);
                DoodadDef fence = registry.doodad(id);
                if (fence == null) throw new IllegalStateException("Missing industrial fence " + id);
                ctx.grid.setWalkable(x, y, false);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.topology.setWall(x, y, false);
                ctx.topology.setFixture(x, y, true);
                ctx.doodads.add(new Doodad(x, y, fence));
            }
        }
    }

    /**
     * Yard grammars place equipment along their short edges. When an apron
     * approaches one of those edges, a fixture can otherwise land directly
     * behind both gate cells and seal the whole enclosure. Reserve a
     * two-cell-deep, marine-width entrance throat after furnishing.
     */
    private static void clearGateApproach(BlockLeaf yard,
                                          BuildingPlacement.Side frontage,
                                          int gateStart,
                                          GenContext ctx) {
        for (int along = gateStart; along < gateStart + GATE_WIDTH; along++) {
            for (int depth = 0; depth <= 2; depth++) {
                int x = frontage == BuildingPlacement.Side.LEFT ? yard.left + depth
                        : frontage == BuildingPlacement.Side.RIGHT ? yard.right - depth : along;
                int y = frontage == BuildingPlacement.Side.TOP ? yard.top + depth
                        : frontage == BuildingPlacement.Side.BOTTOM ? yard.bottom - depth : along;
                ctx.doodads.removeIf(d -> d.occupiesCell(x, y));
                ctx.grid.setWalkableFloor(x, y);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.topology.setWall(x, y, false);
                ctx.topology.setFixture(x, y, false);
                ctx.topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }
    }

    private static int gateStart(BlockLeaf yard, BuildingPlacement.Side frontage,
                                 boolean[][] apron) {
        int min = horizontalSide(frontage) ? yard.left + 1 : yard.top + 1;
        int max = horizontalSide(frontage) ? yard.right - 1 : yard.bottom - 1;
        int center = (min + max) / 2;
        int best = Math.max(min, Math.min(max - GATE_WIDTH + 1, center));
        int bestScore = -1;
        int bestCenterDistance = Integer.MAX_VALUE;
        for (int start = min; start <= max - GATE_WIDTH + 1; start++) {
            int score = 0;
            for (int offset = 0; offset < GATE_WIDTH; offset++) {
                score += apronExposure(yard, frontage, start + offset, apron);
            }
            int distance = Math.abs((start * 2 + GATE_WIDTH - 1) - center * 2);
            if (score > bestScore || (score == bestScore && distance < bestCenterDistance)) {
                best = start;
                bestScore = score;
                bestCenterDistance = distance;
            }
        }
        return best;
    }

    private static int apronExposure(BlockLeaf yard, BuildingPlacement.Side frontage,
                                     int along, boolean[][] apron) {
        for (int depth = 1; depth <= BRIDGE_SCAN_DEPTH; depth++) {
            int x = frontage == BuildingPlacement.Side.LEFT ? yard.left - depth
                    : frontage == BuildingPlacement.Side.RIGHT ? yard.right + depth : along;
            int y = frontage == BuildingPlacement.Side.TOP ? yard.top - depth
                    : frontage == BuildingPlacement.Side.BOTTOM ? yard.bottom + depth : along;
            if (x < 0 || x >= apron.length || y < 0 || y >= apron[0].length) return 0;
            if (apron[x][y]) return BRIDGE_SCAN_DEPTH + 1 - depth;
        }
        return 0;
    }

    private static boolean onGate(int x, int y, BlockLeaf yard,
                                  BuildingPlacement.Side frontage, int gateStart) {
        int along = horizontalSide(frontage) ? x : y;
        boolean onSide = frontage == BuildingPlacement.Side.TOP ? y == yard.top
                : frontage == BuildingPlacement.Side.BOTTOM ? y == yard.bottom
                : frontage == BuildingPlacement.Side.LEFT ? x == yard.left
                : x == yard.right;
        return onSide && along >= gateStart && along < gateStart + GATE_WIDTH;
    }

    private static String fenceId(int x, int y, BlockLeaf yard) {
        if (x == yard.left && y == yard.top) return "doodad.industrial-fence-corner-nw";
        if (x == yard.right && y == yard.top) return "doodad.industrial-fence-corner-ne";
        if (x == yard.left && y == yard.bottom) return "doodad.industrial-fence-corner-sw";
        if (x == yard.right && y == yard.bottom) return "doodad.industrial-fence-corner-se";
        return y == yard.top || y == yard.bottom
                ? "doodad.industrial-fence-straight-h"
                : "doodad.industrial-fence-straight-v";
    }

    private static boolean horizontalSide(BuildingPlacement.Side side) {
        return side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM;
    }

    private static void furnishApron(Compound compound,
                                     boolean[][] apron,
                                     boolean[][] roadReservation,
                                     GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef[] props = {
                registry.doodad("doodad.industrial-pallet-stack"),
                registry.doodad("doodad.industrial-pipe-bundle"),
                registry.doodad("doodad.industrial-cable-reel")
        };
        List<int[]> candidates = new ArrayList<>();
        for (int y = compound.top; y <= compound.bottom; y++) {
            for (int x = compound.left; x <= compound.right; x++) {
                if (!apron[x][y] || roadReservation[x][y] || nearDoorway(ctx.grid, x, y)) continue;
                candidates.add(new int[]{x, y});
            }
        }
        int target = Math.min(3, candidates.size());
        Random rng = ctx.rng;
        for (int i = 0; i < target; i++) {
            int swap = i + rng.nextInt(candidates.size() - i);
            int[] temp = candidates.get(i);
            candidates.set(i, candidates.get(swap));
            candidates.set(swap, temp);
            int[] cell = candidates.get(i);
            ctx.doodads.add(new Doodad(cell[0], cell[1], props[i % props.length]));
        }
    }

    private static boolean nearDoorway(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(x + dx, y + dy) && grid.isDoorway(x + dx, y + dy)) return true;
            }
        }
        return false;
    }
}
