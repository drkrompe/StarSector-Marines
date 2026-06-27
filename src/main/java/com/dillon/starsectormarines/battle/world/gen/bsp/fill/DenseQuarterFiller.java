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

import java.util.List;
import java.util.Random;

/**
 * Multi-leaf compound filler for {@link BlockKind#DENSE_QUARTER} — a 2D
 * top-down representation of a downtown skyscraper district. Each member
 * leaf carves a large commercial sub-building that fills the leaf entirely
 * (no rim inset, no parade ground); the building's outer wall reads as
 * the tower facade. Inter-leaf road frames bridged within the compound
 * are repainted as {@link GroundKind#TILE} paved plaza — the upscale
 * downtown "central street between towers" look.
 *
 * <p>No outer wall, no gates, no emplacements. The compound's visual
 * identity comes from its clustering of big-footprint commercial buildings
 * over TILE pavement, contrasted with the regular city outside.
 *
 * <p>Each member leaf is carved with {@link BuildingShellCore#carve} using
 * a commercial config. {@link BuildingShellCore} already adds an interior
 * partition wall when the building is large enough, giving 2-room
 * interiors — close-quarters lobby-and-back-room layouts that read as
 * downtown commercial space in top-down.
 */
public final class DenseQuarterFiller implements CompoundFiller {

    private static final GroundKind PLAZA_GROUND = GroundKind.BRICK;
    private static final int BRIDGE_SCAN_DEPTH = 5;

    private static final BuildingShellCore.BuildingConfig TOWER_PRIMARY_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.TILE, "SKY_PORT", PointOfInterest.Kind.COMMS,
            BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.COMMERCIAL);
    private static final BuildingShellCore.BuildingConfig TOWER_SECONDARY_CONFIG = new BuildingShellCore.BuildingConfig(
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
        repaintBridgedPlaza(compound, roadCells, memberCells, grid, topology);
        carveTowers(compound, grid, topology, doodads, pois, rng);
    }

    /**
     * Find bridged road cells inside the compound — cells between member
     * leaves where members exist on opposite sides within
     * {@link #BRIDGE_SCAN_DEPTH} cells — and repaint them as TILE plaza.
     * No outer wall, so we don't need to absorb concave notches; bridged
     * cells stay walkable and look like upscale paved street.
     */
    private void repaintBridgedPlaza(Compound compound, boolean[][] roadCells,
                                     boolean[][] memberCells,
                                     NavigationGrid grid, CellTopology topology) {
        int w = memberCells.length;
        int h = memberCells[0].length;
        int lo = Math.max(0, compound.left - 1);
        int hi = Math.min(w - 1, compound.right + 1);
        int top = Math.max(0, compound.top - 1);
        int bot = Math.min(h - 1, compound.bottom + 1);
        for (int y = top; y <= bot; y++) {
            for (int x = lo; x <= hi; x++) {
                if (memberCells[x][y]) continue;
                if (!roadCells[x][y]) continue;
                boolean north = scanForMember(memberCells, x, y, 0, -1, w, h);
                boolean south = scanForMember(memberCells, x, y, 0,  1, w, h);
                boolean east  = scanForMember(memberCells, x, y, 1,  0, w, h);
                boolean west  = scanForMember(memberCells, x, y, -1, 0, w, h);
                if ((north && south) || (east && west)) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, PLAZA_GROUND);
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

    /**
     * Carve one tower per member leaf — the building fills the entire leaf,
     * outer wall coincides with the leaf perimeter. Tower role from the
     * compound's role assignment: COMMAND seed gets the SKYPORT-decorated
     * commercial config; others get a warehouse / back-office variant.
     */
    private void carveTowers(Compound compound, NavigationGrid grid, CellTopology topology,
                             List<Doodad> doodads, List<PointOfInterest> pois, Random rng) {
        for (BlockLeaf m : compound.members) {
            BuildingShellCore.BuildingConfig config = configFor(compound.roles.get(m));
            PointOfInterest poi = BuildingShellCore.carve(m, grid, topology, doodads, rng, config);
            if (poi != null) pois.add(poi);
        }
    }

    private BuildingShellCore.BuildingConfig configFor(Compound.Role role) {
        if (role == null) return TOWER_SECONDARY_CONFIG;
        switch (role) {
            case COMMAND: return TOWER_PRIMARY_CONFIG;
            default:      return TOWER_SECONDARY_CONFIG;
        }
    }
}
