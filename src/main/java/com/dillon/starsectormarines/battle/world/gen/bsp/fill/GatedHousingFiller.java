package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Multi-leaf compound filler for {@link BlockKind#GATED_HOUSING} — a walled
 * residential cluster ("gated subdivision") with a single main entrance,
 * a shared courtyard between member buildings, and a lighter-HP wall.
 *
 * <p>Structurally similar to {@link MilitaryBaseFiller}: same bridged-road
 * + concave-notch absorption, same wall ring, but with domestic flavor:
 * INDOOR-ground wall (no STRIPED military look), COURTYARD paving instead of
 * a STONE parade ground, courtyard-facing residential sub-buildings, no corner
 * gun emplacements, exactly one gate (the "main entrance"). Large members
 * become purpose-labeled apartment blocks with a clear two-cell common hall.
 */
public final class GatedHousingFiller implements CompoundFiller {

    private static final GroundKind WALL_GROUND = GroundKind.INDOOR;
    private static final GroundKind YARD_GROUND = GroundKind.COURTYARD;
    private static final int WALL_HP = 80;
    private static final int BRIDGE_SCAN_DEPTH = 5;
    static final int BUILDING_SETBACK = 2;
    static final int GATE_WIDTH = 2;

    private static final BuildingShellCore.BuildingConfig MAIN_HOUSE_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "RESIDENTIAL", PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.APARTMENT_BLOCK, BuildingKind.RESIDENTIAL,
            null, ResidentialPartitionStrategy.DEFAULT);
    private static final BuildingShellCore.BuildingConfig SECONDARY_HOUSE_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "RESIDENTIAL", PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.APARTMENT_BLOCK, BuildingKind.RESIDENTIAL,
            null, ResidentialPartitionStrategy.DEFAULT);
    private static final BuildingShellCore.BuildingConfig OUTBUILDING_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "WAREHOUSE", PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.RESIDENTIAL);

    @Override public BlockKind kind() { return BlockKind.GATED_HOUSING; }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        boolean[][] roadCells = ctx.get(BspKeys.ROAD_CELLS);
        boolean[][] roadReservation = ctx.get(BspKeys.ROAD_RESERVATION);
        List<PointOfInterest> pois = ctx.pois;
        List<Doodad> doodads = ctx.doodads;
        List<TacticalNode> tactical = ctx.tactical;
        Random rng = ctx.rng;
        int w = grid.getWidth();
        int h = grid.getHeight();

        boolean[][] memberCells = new boolean[w][h];
        for (BlockLeaf m : compound.members) {
            for (int y = m.top; y <= m.bottom; y++) {
                for (int x = m.left; x <= m.right; x++) memberCells[x][y] = true;
            }
        }
        boolean[][] inCompound = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) inCompound[x][y] = memberCells[x][y];
        }
        markBridgedRoads(compound, roadCells, roadReservation, memberCells, inCompound);
        absorbConcaveNotches(compound, inCompound);

        boolean[][] courtyardCells = repaintYard(
                inCompound, memberCells, grid, topology);
        carveSubBuildings(compound, courtyardCells, grid, topology, doodads, pois, rng);
        paintWallRing(inCompound, roadReservation, grid, topology);
        punchSingleGate(compound, inCompound, roadCells, grid, topology, rng);
        furnishCourtyard(compound, roadReservation,
                grid, topology, doodads, rng);
        CompoundWallApertures.stamp(inCompound, grid, topology);
    }

    private void markBridgedRoads(Compound compound, boolean[][] roadCells, boolean[][] roadReservation,
                                  boolean[][] memberCells, boolean[][] inCompound) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        int lo = Math.max(0, compound.left - 1);
        int hi = Math.min(w - 1, compound.right + 1);
        int top = Math.max(0, compound.top - 1);
        int bot = Math.min(h - 1, compound.bottom + 1);
        for (int y = top; y <= bot; y++) {
            for (int x = lo; x <= hi; x++) {
                if (inCompound[x][y]) continue;
                if (!roadCells[x][y]) continue;
                if (roadReservation[x][y]) continue;
                boolean north = scanForMember(memberCells, x, y, 0, -1, w, h);
                boolean south = scanForMember(memberCells, x, y, 0,  1, w, h);
                boolean east  = scanForMember(memberCells, x, y, 1,  0, w, h);
                boolean west  = scanForMember(memberCells, x, y, -1, 0, w, h);
                if ((north && south) || (east && west)) {
                    inCompound[x][y] = true;
                }
            }
        }
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

    private void absorbConcaveNotches(Compound compound, boolean[][] inCompound) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        boolean[][] outside = new boolean[w][h];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int[][] seeds = {
                {compound.left  - 1, compound.top    - 1},
                {compound.right + 1, compound.top    - 1},
                {compound.left  - 1, compound.bottom + 1},
                {compound.right + 1, compound.bottom + 1},
                {compound.left  - 1, (compound.top + compound.bottom) / 2},
                {compound.right + 1, (compound.top + compound.bottom) / 2},
                {(compound.left + compound.right) / 2, compound.top    - 1},
                {(compound.left + compound.right) / 2, compound.bottom + 1},
        };
        for (int[] s : seeds) {
            int sx = Math.max(0, Math.min(w - 1, s[0]));
            int sy = Math.max(0, Math.min(h - 1, s[1]));
            if (!inCompound[sx][sy] && !outside[sx][sy]) {
                outside[sx][sy] = true;
                q.add(new int[]{sx, sy});
            }
        }
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (outside[nx][ny] || inCompound[nx][ny]) continue;
                outside[nx][ny] = true;
                q.add(new int[]{nx, ny});
            }
        }
        for (int y = Math.max(0, compound.top - 1); y <= Math.min(h - 1, compound.bottom + 1); y++) {
            for (int x = Math.max(0, compound.left - 1); x <= Math.min(w - 1, compound.right + 1); x++) {
                if (inCompound[x][y]) continue;
                if (outside[x][y]) continue;
                inCompound[x][y] = true;
            }
        }
    }

    private boolean[][] repaintYard(boolean[][] inCompound, boolean[][] memberCells,
                                    NavigationGrid grid, CellTopology topology) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        boolean[][] courtyard = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!inCompound[x][y]) continue;
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, YARD_GROUND);
                courtyard[x][y] = !memberCells[x][y];
            }
        }
        return courtyard;
    }

    private void carveSubBuildings(Compound compound, boolean[][] courtyard,
                                   NavigationGrid grid, CellTopology topology,
                                   List<Doodad> doodads, List<PointOfInterest> pois, Random rng) {
        for (BlockLeaf m : compound.members) {
            int subL = m.left   + BUILDING_SETBACK;
            int subT = m.top    + BUILDING_SETBACK;
            int subR = m.right  - BUILDING_SETBACK;
            int subB = m.bottom - BUILDING_SETBACK;
            if (subR - subL < 1 || subB - subT < 1) continue;
            BlockLeaf inset = new BlockLeaf(subL, subT, subR, subB, false);
            inset.kind = m.kind;
            BuildingShellCore.BuildingConfig config = configFor(compound.roles.get(m));
            BuildingPlacement.Side frontage = findFrontage(inset, courtyard);
            BuildingPlacement placement = frontage == null ? BuildingPlacement.DEFAULT
                    : new BuildingPlacement(frontage, config != OUTBUILDING_CONFIG);
            PointOfInterest poi = BuildingShellCore.carve(
                    inset, grid, topology, doodads, rng, config, placement);
            if (poi != null) pois.add(poi);
        }
    }

    private BuildingPlacement.Side findFrontage(BlockLeaf leaf, boolean[][] courtyard) {
        BuildingPlacement.Side best = null;
        int bestScore = 0;
        for (BuildingPlacement.Side side : BuildingPlacement.Side.values()) {
            int score = frontageScore(leaf, side, courtyard);
            if (score > bestScore) {
                best = side;
                bestScore = score;
            }
        }
        return best;
    }

    private int frontageScore(BlockLeaf leaf, BuildingPlacement.Side side,
                              boolean[][] courtyard) {
        int score = 0;
        int min = horizontalSide(side) ? leaf.left + 1 : leaf.top + 1;
        int max = horizontalSide(side) ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            for (int depth = 1; depth <= BRIDGE_SCAN_DEPTH; depth++) {
                int x = side == BuildingPlacement.Side.LEFT ? leaf.left - depth
                        : side == BuildingPlacement.Side.RIGHT ? leaf.right + depth : along;
                int y = side == BuildingPlacement.Side.TOP ? leaf.top - depth
                        : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom + depth : along;
                if (x < 0 || x >= courtyard.length
                        || y < 0 || y >= courtyard[0].length) break;
                if (!courtyard[x][y]) continue;
                score += BRIDGE_SCAN_DEPTH + 1 - depth;
                break;
            }
        }
        return score;
    }

    private boolean horizontalSide(BuildingPlacement.Side side) {
        return side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM;
    }

    private BuildingShellCore.BuildingConfig configFor(Compound.Role role) {
        if (role == null) return MAIN_HOUSE_CONFIG;
        switch (role) {
            case COMMAND:     return MAIN_HOUSE_CONFIG;
            case BARRACKS:    return SECONDARY_HOUSE_CONFIG;
            case ARMORY:
            case VEHICLE_BAY:
            default:          return OUTBUILDING_CONFIG;
        }
    }

    /**
     * Raised planters create sparse tactical cover and benches turn the full
     * two-cell apron into a lived-in shared courtyard. Planters are physical;
     * benches are visual/cover doodads so the parallel circulation lane stays
     * open. This runs before wall apertures so blocked planter cells cannot be
     * selected as the firing space behind a shoot-through.
     */
    private void furnishCourtyard(Compound compound,
                                  boolean[][] roadReservation,
                                  NavigationGrid grid,
                                  CellTopology topology,
                                  List<Doodad> doodads,
                                  Random rng) {
        List<int[]> candidates = new ArrayList<>();
        for (int y = Math.max(1, compound.top - 1);
             y <= Math.min(grid.getHeight() - 2, compound.bottom + 1); y++) {
            for (int x = Math.max(1, compound.left - 1);
                 x <= Math.min(grid.getWidth() - 2, compound.right + 1); x++) {
                if (topology.getGroundKind(x, y) != YARD_GROUND
                        || roadReservation[x][y]) continue;
                if (!grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                if (nearDoorway(grid, x, y, 2) || doodadAt(doodads, x, y)) continue;
                if (!clearPlanterCell(grid, x, y)) continue;
                candidates.add(new int[]{x, y});
            }
        }
        Collections.shuffle(candidates, rng);
        List<int[]> placed = new ArrayList<>();
        int target = Math.min(5, Math.max(4, compound.members.size() + 1));
        for (int[] cell : candidates) {
            if (placed.size() >= target) break;
            if (nearPlaced(cell, placed, 3) || !clearPlanterCell(grid, cell[0], cell[1])) continue;
            String id = rng.nextBoolean()
                    ? "doodad.residential-planter-h" : "doodad.residential-planter-v";
            DoodadDef planter = TileRegistry.installed().doodad(id);
            if (planter == null) throw new IllegalStateException("Missing courtyard planter " + id);
            grid.setWalkable(cell[0], cell[1], false);
            grid.setSeeThrough(cell[0], cell[1], true);
            topology.setWall(cell[0], cell[1], false);
            topology.setFixture(cell[0], cell[1], true);
            doodads.add(new Doodad(cell[0], cell[1], planter));
            placed.add(cell);
        }

        DoodadDef bench = TileRegistry.installed().doodad("doodad.desk-dam");
        if (bench == null) throw new IllegalStateException("Missing courtyard bench doodad.desk-dam");
        Collections.shuffle(candidates, rng);
        int benchTarget = Math.max(2, compound.members.size() - 1);
        int benches = 0;
        for (int[] cell : candidates) {
            if (benches >= benchTarget) break;
            int x = cell[0], y = cell[1];
            if (!grid.isWalkable(x, y) || grid.isDoorway(x, y)
                    || roadReservation[x][y] || nearDoorway(grid, x, y, 1)) continue;
            if (topology.getGroundKind(x, y) != YARD_GROUND
                    || doodadAt(doodads, x, y) || nearPlaced(cell, placed, 2)) continue;
            doodads.add(new Doodad(x, y, bench));
            placed.add(cell);
            benches++;
        }
    }

    private boolean clearPlanterCell(NavigationGrid grid, int x, int y) {
        if (!grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
        int open = 0;
        if (grid.isWalkable(x + 1, y)) open++;
        if (grid.isWalkable(x - 1, y)) open++;
        if (grid.isWalkable(x, y + 1)) open++;
        if (grid.isWalkable(x, y - 1)) open++;
        return open >= 3;
    }

    private boolean nearDoorway(NavigationGrid grid, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (grid.isDoorway(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    private boolean nearPlaced(int[] cell, List<int[]> placed, int spacing) {
        for (int[] prior : placed) {
            if (Math.max(Math.abs(cell[0] - prior[0]),
                    Math.abs(cell[1] - prior[1])) < spacing) return true;
        }
        return false;
    }

    private boolean doodadAt(List<Doodad> doodads, int x, int y) {
        for (Doodad doodad : doodads) {
            if (doodad.occupiesCell(x, y)) return true;
        }
        return false;
    }

    private void paintWallRing(boolean[][] inCompound, boolean[][] roadReservation,
                               NavigationGrid grid, CellTopology topology) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (inCompound[x][y]) continue;
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) continue;
                if (!touchesCompound(inCompound, x, y, w, h)) continue;
                // Implicit gate where the road graph crosses the perimeter.
                if (roadReservation[x][y]) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, WALL_HP);
                topology.setGroundKind(x, y, WALL_GROUND);
            }
        }
    }

    private boolean touchesCompound(boolean[][] inCompound, int x, int y, int w, int h) {
        if (x + 1 < w  && inCompound[x + 1][y]) return true;
        if (x - 1 >= 0 && inCompound[x - 1][y]) return true;
        if (y + 1 < h  && inCompound[x][y + 1]) return true;
        if (y - 1 >= 0 && inCompound[x][y - 1]) return true;
        return false;
    }

    /**
     * One marine-width gate only — a gated community has a single grand
     * entrance. Both cells must face the same contiguous road edge, so an
     * isolated road pixel cannot produce a one-cell tactical choke point.
     */
    private void punchSingleGate(Compound compound, boolean[][] inCompound, boolean[][] roadCells,
                                 NavigationGrid grid, CellTopology topology, Random rng) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        List<int[]> candidates = new ArrayList<>();
        for (int y = compound.top - 1; y <= compound.bottom + 1; y++) {
            for (int x = compound.left - 1; x <= compound.right + 1; x++) {
                if (x < 0 || x >= w || y < 0 || y >= h) continue;
                if (inCompound[x][y]) continue;
                if (grid.isWalkable(x, y)) continue;
                int[] inside = compoundNeighbor(inCompound, x, y, w, h);
                if (inside == null) continue;
                int ox = x - (inside[0] - x);
                int oy = y - (inside[1] - y);
                if (ox < 0 || ox >= w || oy < 0 || oy >= h) continue;
                if (inCompound[ox][oy]) continue;
                if (!roadCells[ox][oy]) continue;
                candidates.add(new int[]{x, y, ox - x, oy - y});
            }
        }
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates, rng);
        for (int[] gate : candidates) {
            int px1 = gate[0] - gate[3];
            int py1 = gate[1] + gate[2];
            int px2 = gate[0] + gate[3];
            int py2 = gate[1] - gate[2];
            for (int[] adjacent : candidates) {
                if (adjacent[2] != gate[2] || adjacent[3] != gate[3]) continue;
                if ((adjacent[0] == px1 && adjacent[1] == py1)
                        || (adjacent[0] == px2 && adjacent[1] == py2)) {
                    openGate(gate, grid, topology);
                    openGate(adjacent, grid, topology);
                    return;
                }
            }
        }
    }

    private void openGate(int[] gate, NavigationGrid grid, CellTopology topology) {
        int x = gate[0];
        int y = gate[1];
        grid.setWalkable(x, y, true);
        grid.setDoorway(x, y, true);
        grid.openAllEdges(x, y);
        topology.setGroundKind(x, y, GroundKind.STONE); // a small paved gate threshold
    }

    private int[] compoundNeighbor(boolean[][] inCompound, int x, int y, int w, int h) {
        if (x + 1 < w  && inCompound[x + 1][y]) return new int[]{x + 1, y};
        if (x - 1 >= 0 && inCompound[x - 1][y]) return new int[]{x - 1, y};
        if (y + 1 < h  && inCompound[x][y + 1]) return new int[]{x, y + 1};
        if (y - 1 >= 0 && inCompound[x][y - 1]) return new int[]{x, y - 1};
        return null;
    }
}
