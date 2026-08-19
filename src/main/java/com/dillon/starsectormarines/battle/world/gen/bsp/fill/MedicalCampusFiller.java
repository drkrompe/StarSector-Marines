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

/**
 * Three-parcel medical campus. The large seed becomes a purpose-partitioned
 * clinic, the larger neighboring parcel becomes a supplies/support building,
 * and the remaining parcel stays open as an ambulance court. Both buildings
 * face the shared striped circulation while its vehicle reservation remains
 * untouched.
 */
public final class MedicalCampusFiller implements CompoundFiller {

    private static final int BRIDGE_SCAN_DEPTH = 5;

    static final BuildingShellCore.BuildingConfig CLINIC_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.TILE, "COMMERCIAL", PointOfInterest.Kind.MEDICAL,
                    BuildingLayouts.LayoutRecipe.MEDICAL_CLINIC, BuildingKind.MEDICAL,
                    null, MedicalPartitionStrategy.DEFAULT);

    private static final BuildingShellCore.BuildingConfig SUPPORT_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.TILE, "WAREHOUSE", PointOfInterest.Kind.MEDICAL,
                    BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.MEDICAL);

    @Override
    public BlockKind kind() {
        return BlockKind.MEDICAL_CAMPUS;
    }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        if (compound.members.size() != 3) {
            throw new IllegalArgumentException("Medical campus requires exactly three parcels");
        }

        boolean[][] memberCells = memberMask(compound, ctx.grid);
        boolean[][] apron = repaintSharedApron(compound, memberCells,
                ctx.get(BspKeys.ROAD_CELLS), ctx.get(BspKeys.ROAD_RESERVATION),
                ctx.grid, ctx.topology);

        BlockLeaf clinic = compound.seed;
        List<BlockLeaf> neighbors = new ArrayList<>();
        for (BlockLeaf member : compound.members) {
            if (member != clinic) neighbors.add(member);
        }
        neighbors.sort(Comparator.comparingInt(BlockLeaf::area).reversed());
        BlockLeaf support = neighbors.get(0);
        BlockLeaf ambulanceCourt = neighbors.get(1);

        carveBuilding(clinic, CLINIC_CONFIG, placementToward(clinic, apron), ctx);
        carveBuilding(support, SUPPORT_CONFIG, placementToward(support, apron), ctx);
        carveAmbulanceCourt(ambulanceCourt, ctx);
    }

    private static void carveBuilding(BlockLeaf leaf,
                                      BuildingShellCore.BuildingConfig config,
                                      BuildingPlacement placement,
                                      GenContext ctx) {
        PointOfInterest poi = BuildingShellCore.carve(
                leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, config, placement);
        if (poi != null) ctx.pois.add(poi);
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
        boolean[][] apron = new boolean[grid.getWidth()][grid.getHeight()];
        int minX = Math.max(0, compound.left - 1);
        int maxX = Math.min(grid.getWidth() - 1, compound.right + 1);
        int minY = Math.max(0, compound.top - 1);
        int maxY = Math.min(grid.getHeight() - 1, compound.bottom + 1);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (memberCells[x][y] || !roadCells[x][y]) continue;
                boolean north = scan(memberCells, x, y, 0, -1);
                boolean south = scan(memberCells, x, y, 0, 1);
                boolean east = scan(memberCells, x, y, 1, 0);
                boolean west = scan(memberCells, x, y, -1, 0);
                if (!((north && south) || (east && west))) continue;
                apron[x][y] = true;
                if (roadReservation[x][y]) continue;
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }
        return apron;
    }

    private static boolean scan(boolean[][] memberCells, int x, int y, int dx, int dy) {
        int cx = x + dx;
        int cy = y + dy;
        for (int depth = 0; depth < BRIDGE_SCAN_DEPTH; depth++) {
            if (cx < 0 || cx >= memberCells.length
                    || cy < 0 || cy >= memberCells[0].length) return false;
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
        boolean horizontal = side == BuildingPlacement.Side.TOP
                || side == BuildingPlacement.Side.BOTTOM;
        int min = horizontal ? leaf.left + 1 : leaf.top + 1;
        int max = horizontal ? leaf.right - 1 : leaf.bottom - 1;
        int score = 0;
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

    private static void carveAmbulanceCourt(BlockLeaf court, GenContext ctx) {
        for (int y = court.top; y <= court.bottom; y++) {
            for (int x = court.left; x <= court.right; x++) {
                ctx.grid.setWalkableFloor(x, y);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.topology.setGroundKind(x, y, GroundKind.STRIPED);
                ctx.topology.setWall(x, y, false);
                ctx.topology.setFixture(x, y, false);
            }
        }

        TileRegistry registry = TileRegistry.installed();
        DoodadDef[] cover = {
                registry.doodad("doodad.industrial-pallet-stack"),
                registry.doodad("doodad.industrial-cable-reel"),
                registry.doodad("doodad.box")
        };
        int[][] cells = {
                {court.left + 1, court.top + 1},
                {court.right - 1, court.top + 1},
                {court.left + 1, court.bottom - 1},
                {court.right - 1, court.bottom - 1}
        };
        int offset = ctx.rng.nextInt(cells.length);
        for (int i = 0; i < 3; i++) {
            int[] cell = cells[(offset + i) % cells.length];
            stampLowCover(ctx, cell[0], cell[1], cover[i]);
        }
    }

    private static void stampLowCover(GenContext ctx, int x, int y, DoodadDef prop) {
        if (!ctx.grid.inBounds(x, y) || !ctx.grid.isWalkable(x, y)) return;
        for (Doodad doodad : ctx.doodads) {
            if (doodad.occupiesCell(x, y)) return;
        }
        ctx.grid.setWalkable(x, y, false);
        ctx.grid.setSeeThrough(x, y, true);
        ctx.topology.setFixture(x, y, true);
        ctx.doodads.add(new Doodad(x, y, prop));
    }
}
