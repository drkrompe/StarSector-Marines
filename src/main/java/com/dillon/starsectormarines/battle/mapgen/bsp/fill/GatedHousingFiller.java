package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.map.BuildingKind;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.mapgen.bsp.Compound;
import com.dillon.starsectormarines.battle.mapgen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Multi-leaf compound filler for {@link BlockKind#GATED_HOUSING} — a walled
 * residential cluster ("gated subdivision") with a single main entrance,
 * GRASS yards between member buildings, and a lighter-HP fence-style wall.
 *
 * <p>Structurally similar to {@link MilitaryBaseFiller}: same bridged-road
 * + concave-notch absorption, same wall ring, but with domestic flavor:
 * INDOOR-ground wall (no STRIPED military look), GRASS yards instead of
 * STONE parade ground, residential sub-building configs, no corner gun
 * emplacements, exactly one gate (the "main entrance").
 */
public final class GatedHousingFiller implements CompoundFiller {

    private static final GroundKind WALL_GROUND = GroundKind.INDOOR;
    private static final GroundKind YARD_GROUND = GroundKind.GRASS;
    private static final int WALL_HP = 80;
    private static final int BRIDGE_SCAN_DEPTH = 5;

    private static final BuildingShellCore.BuildingConfig MAIN_HOUSE_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, TileManifest.RESIDENTIAL_DOODADS, PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME, BuildingKind.RESIDENTIAL);
    private static final BuildingShellCore.BuildingConfig SECONDARY_HOUSE_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, TileManifest.RESIDENTIAL_DOODADS, PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME, BuildingKind.RESIDENTIAL);
    private static final BuildingShellCore.BuildingConfig OUTBUILDING_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, TileManifest.WAREHOUSE_DOODADS, PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.RESIDENTIAL);

    @Override public BlockKind kind() { return BlockKind.GATED_HOUSING; }

    @Override
    public void fill(Compound compound,
                     NavigationGrid grid,
                     CellTopology topology,
                     boolean[][] roadCells,
                     List<PointOfInterest> pois,
                     List<Doodad> doodads,
                     List<TacticalNode> tactical,
                     Random rng) {
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
        markBridgedRoads(compound, roadCells, memberCells, inCompound);
        absorbConcaveNotches(compound, inCompound);

        repaintYard(compound, inCompound, memberCells, grid, topology);
        carveSubBuildings(compound, grid, topology, doodads, pois, rng);
        paintWallRing(inCompound, grid, topology);
        punchSingleGate(compound, inCompound, roadCells, grid, topology, rng);
    }

    private void markBridgedRoads(Compound compound, boolean[][] roadCells,
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

    private void repaintYard(Compound compound, boolean[][] inCompound,
                             boolean[][] memberCells, NavigationGrid grid, CellTopology topology) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!inCompound[x][y]) continue;
                if (memberCells[x][y]) continue;
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, YARD_GROUND);
            }
        }
        for (BlockLeaf m : compound.members) {
            for (int x = m.left; x <= m.right; x++) {
                grid.setWalkableFloor(x, m.top);
                grid.setWalkableFloor(x, m.bottom);
                topology.setGroundKind(x, m.top,    YARD_GROUND);
                topology.setGroundKind(x, m.bottom, YARD_GROUND);
            }
            for (int y = m.top + 1; y <= m.bottom - 1; y++) {
                grid.setWalkableFloor(m.left,  y);
                grid.setWalkableFloor(m.right, y);
                topology.setGroundKind(m.left,  y, YARD_GROUND);
                topology.setGroundKind(m.right, y, YARD_GROUND);
            }
        }
    }

    private void carveSubBuildings(Compound compound, NavigationGrid grid, CellTopology topology,
                                   List<Doodad> doodads, List<PointOfInterest> pois, Random rng) {
        for (BlockLeaf m : compound.members) {
            int subL = m.left   + 1;
            int subT = m.top    + 1;
            int subR = m.right  - 1;
            int subB = m.bottom - 1;
            if (subR - subL < 1 || subB - subT < 1) continue;
            BlockLeaf inset = new BlockLeaf(subL, subT, subR, subB, false);
            inset.kind = m.kind;
            BuildingShellCore.BuildingConfig config = configFor(compound.roles.get(m));
            PointOfInterest poi = BuildingShellCore.carve(inset, grid, topology, doodads, rng, config);
            if (poi != null) pois.add(poi);
        }
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

    private void paintWallRing(boolean[][] inCompound, NavigationGrid grid, CellTopology topology) {
        int w = inCompound.length;
        int h = inCompound[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (inCompound[x][y]) continue;
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) continue;
                if (!touchesCompound(inCompound, x, y, w, h)) continue;
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
     * One gate only — a gated community has a single grand entrance. Picks
     * the wall cell whose outside neighbor has the widest contiguous walkable
     * road, so the gate faces the main street rather than an alley.
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
        int[] gate = candidates.get(0);
        openGate(gate, grid, topology);
        // Widen to 2 cells when an adjacent wall is also gate-eligible.
        int px1 = gate[0] - gate[3];
        int py1 = gate[1] + gate[2];
        int px2 = gate[0] + gate[3];
        int py2 = gate[1] - gate[2];
        for (int[] c : candidates) {
            if ((c[0] == px1 && c[1] == py1) || (c[0] == px2 && c[1] == py2)) {
                openGate(c, grid, topology);
                break;
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
