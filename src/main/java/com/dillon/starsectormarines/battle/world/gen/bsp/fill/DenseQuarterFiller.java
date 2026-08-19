package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Multi-leaf compound filler for {@link BlockKind#DENSE_QUARTER} — a 2D
 * top-down representation of a downtown commercial complex. Each member
 * leaf carves a commercial sub-building that fills the leaf entirely
 * (no rim inset, no parade ground); the building's outer wall reads as
 * the building facade. Inter-leaf road frames bridged within the compound
 * are repainted as {@link GroundKind#BRICK} paving — the upscale
 * downtown "central street between buildings" look. Public doors face that
 * shared concourse, with opposed service doors on the outer facade.
 *
 * <p>No outer wall, no gates, no emplacements. The compound's visual
 * identity comes from its clustering of big-footprint commercial buildings
 * around paved pedestrian strips and a retained service lane, contrasted with
 * the regular city outside.
 *
 * <p>The largest member becomes the tactical anchor store, COMMAND / BARRACKS
 * members become smaller storefronts, and ARMORY / VEHICLE_BAY roles become
 * warehouse-service units.
 * Benches and loading crates add cover along unreserved concourse edges while
 * the road graph's reserved cells remain untouched and drivable.
 */
public final class DenseQuarterFiller implements CompoundFiller {

    private static final GroundKind PLAZA_GROUND = GroundKind.BRICK;
    private static final int BRIDGE_SCAN_DEPTH = 5;

    private static final BuildingShellCore.BuildingConfig ANCHOR_STORE_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.TILE, "SKY_PORT", PointOfInterest.Kind.COMMS,
            BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.COMMERCIAL,
            new RoomPurpose[]{RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM},
            CommercialPartitionStrategy.DEFAULT);
    private static final BuildingShellCore.BuildingConfig STOREFRONT_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.TILE, "COMMERCIAL", PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.COMMERCIAL,
            new RoomPurpose[]{RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM},
            CommercialPartitionStrategy.DEFAULT);
    private static final BuildingShellCore.BuildingConfig SERVICE_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "WAREHOUSE", PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.COMMERCIAL);

    @Override public BlockKind kind() { return BlockKind.DENSE_QUARTER; }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        boolean[][] roadCells = ctx.get(BspKeys.ROAD_CELLS);
        boolean[][] roadReservation = ctx.get(BspKeys.ROAD_RESERVATION);
        List<PointOfInterest> pois = ctx.pois;
        List<Doodad> doodads = ctx.doodads;
        Random rng = ctx.rng;
        int w = grid.getWidth();
        int h = grid.getHeight();

        boolean[][] memberCells = new boolean[w][h];
        for (BlockLeaf m : compound.members) {
            for (int y = m.top; y <= m.bottom; y++) {
                for (int x = m.left; x <= m.right; x++) memberCells[x][y] = true;
            }
        }
        boolean[][] concourseCells = repaintBridgedPlaza(
                compound, roadCells, roadReservation, memberCells, grid, topology);
        Map<BlockLeaf, BuildingPlacement> placements = planFrontages(compound, concourseCells);
        carveBuildings(compound, placements, grid, topology, doodads, pois, rng);
        furnishConcourse(compound, placements, concourseCells, roadReservation, grid, doodads, rng);
    }

    /**
     * Find bridged road cells inside the compound — cells between member
     * leaves where members exist on opposite sides within
     * {@link #BRIDGE_SCAN_DEPTH} cells — and repaint the unreserved edge
     * strips as BRICK concourse. The reserved road-graph centerline remains
     * STREET, forming a service lane through the site.
     * No outer wall, so we don't need to absorb concave notches; bridged
     * cells stay walkable and look like upscale paved street.
     */
    private boolean[][] repaintBridgedPlaza(Compound compound, boolean[][] roadCells,
                                            boolean[][] roadReservation,
                                            boolean[][] memberCells,
                                            NavigationGrid grid, CellTopology topology) {
        int w = memberCells.length;
        int h = memberCells[0].length;
        boolean[][] concourseCells = new boolean[w][h];
        int lo = Math.max(0, compound.left - 1);
        int hi = Math.min(w - 1, compound.right + 1);
        int top = Math.max(0, compound.top - 1);
        int bot = Math.min(h - 1, compound.bottom + 1);
        for (int y = top; y <= bot; y++) {
            for (int x = lo; x <= hi; x++) {
                if (memberCells[x][y]) continue;
                if (!roadCells[x][y]) continue;
                if (roadReservation[x][y]) continue;
                boolean north = scanForMember(memberCells, x, y, 0, -1, w, h);
                boolean south = scanForMember(memberCells, x, y, 0,  1, w, h);
                boolean east  = scanForMember(memberCells, x, y, 1,  0, w, h);
                boolean west  = scanForMember(memberCells, x, y, -1, 0, w, h);
                if ((north && south) || (east && west)) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, PLAZA_GROUND);
                    concourseCells[x][y] = true;
                }
            }
        }
        return concourseCells;
    }

    private boolean scanForMember(boolean[][] memberCells, int x, int y, int dx, int dy, int w, int h) {
        int cx = x + dx;
        int cy = y + dy;
        for (int i = 0; i < BRIDGE_SCAN_DEPTH; i++) {
            if (cx < 0 || cx >= w || cy < 0 || cy >= h) return false;
            if (memberCells[cx][cy]) return true;
            cx += dx;
            cy += dy;
        }
        return false;
    }

    private Map<BlockLeaf, BuildingPlacement> planFrontages(Compound compound,
                                                            boolean[][] concourseCells) {
        Map<BlockLeaf, BuildingPlacement> placements = new IdentityHashMap<>();
        for (BlockLeaf m : compound.members) {
            BuildingPlacement.Side frontage = findFrontage(m, concourseCells);
            placements.put(m, frontage == null
                    ? BuildingPlacement.DEFAULT
                    : new BuildingPlacement(frontage, true));
        }
        return placements;
    }

    /** Pick the facade with the longest, closest exposure to shared paving. */
    private BuildingPlacement.Side findFrontage(BlockLeaf leaf, boolean[][] concourseCells) {
        BuildingPlacement.Side best = null;
        int bestScore = 0;
        for (BuildingPlacement.Side side : BuildingPlacement.Side.values()) {
            int score = frontageScore(leaf, side, concourseCells);
            if (score > bestScore) {
                bestScore = score;
                best = side;
            }
        }
        return best;
    }

    private int frontageScore(BlockLeaf leaf, BuildingPlacement.Side side,
                              boolean[][] concourseCells) {
        int score = 0;
        int min = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.left + 1 : leaf.top + 1;
        int max = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            for (int depth = 1; depth <= BRIDGE_SCAN_DEPTH; depth++) {
                int x = side == BuildingPlacement.Side.LEFT ? leaf.left - depth
                        : side == BuildingPlacement.Side.RIGHT ? leaf.right + depth : along;
                int y = side == BuildingPlacement.Side.TOP ? leaf.top - depth
                        : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom + depth : along;
                if (x < 0 || x >= concourseCells.length
                        || y < 0 || y >= concourseCells[0].length) break;
                if (!concourseCells[x][y]) continue;
                score += BRIDGE_SCAN_DEPTH + 1 - depth;
                break;
            }
        }
        return score;
    }

    private void carveBuildings(Compound compound,
                                Map<BlockLeaf, BuildingPlacement> placements,
                                NavigationGrid grid, CellTopology topology,
                                List<Doodad> doodads, List<PointOfInterest> pois, Random rng) {
        BlockLeaf anchor = largestMember(compound);
        for (BlockLeaf m : compound.members) {
            BuildingShellCore.BuildingConfig config = configFor(m == anchor, compound.roles.get(m));
            PointOfInterest poi = BuildingShellCore.carve(
                    m, grid, topology, doodads, rng, config, placements.get(m));
            if (poi != null) pois.add(poi);
        }
    }

    private BlockLeaf largestMember(Compound compound) {
        BlockLeaf largest = compound.members.get(0);
        for (int i = 1; i < compound.members.size(); i++) {
            BlockLeaf candidate = compound.members.get(i);
            if (candidate.area() > largest.area()) largest = candidate;
        }
        return largest;
    }

    /**
     * Places sparse public seating or loading cover along each frontage. The
     * props remain non-blocking, but no prop is allowed on a reserved road
     * cell or in the one-cell doorway clearance envelope.
     */
    private void furnishConcourse(Compound compound,
                                  Map<BlockLeaf, BuildingPlacement> placements,
                                  boolean[][] concourseCells, boolean[][] roadReservation,
                                  NavigationGrid grid, List<Doodad> doodads, Random rng) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef bench = registry.doodad("doodad.desk-dam");
        DoodadDef[] loading = {
                registry.doodad("doodad.box"),
                registry.doodad("doodad.crate")
        };
        List<int[]> placed = new ArrayList<>();
        BlockLeaf anchor = largestMember(compound);
        for (BlockLeaf leaf : compound.members) {
            BuildingPlacement placement = placements.get(leaf);
            if (placement.frontage == null) continue;
            List<int[]> candidates = frontageCandidates(
                    leaf, placement.frontage, concourseCells, roadReservation, grid);
            int target = Math.min(leaf.width() >= 14 || leaf.height() >= 14 ? 2 : 1,
                    candidates.size());
            int accepted = 0;
            for (int i = 0; i < candidates.size() && accepted < target; i++) {
                int swap = i + rng.nextInt(candidates.size() - i);
                int[] tmp = candidates.get(i);
                candidates.set(i, candidates.get(swap));
                candidates.set(swap, tmp);
                int[] cell = candidates.get(i);
                if (nearPlaced(cell[0], cell[1], placed)) continue;

                Compound.Role role = compound.roles.get(leaf);
                boolean service = leaf != anchor
                        && (role == Compound.Role.ARMORY || role == Compound.Role.VEHICLE_BAY);
                DoodadDef def = service ? loading[rng.nextInt(loading.length)] : bench;
                doodads.add(new Doodad(cell[0], cell[1], def));
                placed.add(cell);
                accepted++;
            }
        }
    }

    private List<int[]> frontageCandidates(BlockLeaf leaf, BuildingPlacement.Side side,
                                           boolean[][] concourseCells,
                                           boolean[][] roadReservation,
                                           NavigationGrid grid) {
        List<int[]> candidates = new ArrayList<>();
        int min = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.left + 1 : leaf.top + 1;
        int max = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            for (int depth = 1; depth <= BRIDGE_SCAN_DEPTH; depth++) {
                int x = side == BuildingPlacement.Side.LEFT ? leaf.left - depth
                        : side == BuildingPlacement.Side.RIGHT ? leaf.right + depth : along;
                int y = side == BuildingPlacement.Side.TOP ? leaf.top - depth
                        : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom + depth : along;
                if (!grid.inBounds(x, y)) break;
                if (!concourseCells[x][y] || roadReservation[x][y]) continue;
                if (!nearDoorway(grid, x, y)) candidates.add(new int[]{x, y});
                break;
            }
        }
        return candidates;
    }

    private boolean nearDoorway(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(x + dx, y + dy) && grid.isDoorway(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    private boolean nearPlaced(int x, int y, List<int[]> placed) {
        for (int[] other : placed) {
            if (Math.abs(x - other[0]) + Math.abs(y - other[1]) <= 2) return true;
        }
        return false;
    }

    private BuildingShellCore.BuildingConfig configFor(boolean anchor, Compound.Role role) {
        if (anchor) return ANCHOR_STORE_CONFIG;
        if (role == null) return SERVICE_CONFIG;
        switch (role) {
            case COMMAND:
            case BARRACKS: return STOREFRONT_CONFIG;
            default:       return SERVICE_CONFIG;
        }
    }
}
