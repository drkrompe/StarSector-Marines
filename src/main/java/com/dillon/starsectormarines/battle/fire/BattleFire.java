package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.function.Supplier;

/**
 * The battle's side of the fire field (`fire-nouns.md`): reads fuel off the
 * map as it stands, and carries what burning does back to the world — creep
 * under a fire dies and is held back while the ground is refused, a crown that
 * burns out spends its tree's structure until the tree falls, and a plant on
 * the nature overlay is gone once it has burnt.
 *
 * <p>Holds no state of its own beyond the field; the map, the creep field and
 * the doodads remain the owners of what they hold.
 */
public final class BattleFire implements FireTerrain, FireField.Listener {

    /** Structure a burnt-out crown cell takes from its tree: a 200-point tree falls after three cells. */
    public static final int CROWN_BURN_DAMAGE = 70;
    /** Coverage at or above which dying creep is dry fuel. */
    static final int DEAD_CREEP_COVERAGE = 64;

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final DoodadService doodads;
    private final MapEditor mapEditor;
    private final Supplier<CreepField> creep;
    private final FireField field;
    /** Per registry index: the fuel a nature overlay tile is, 0 for none. */
    private final int[] overlayFuel;
    private final int width;

    public BattleFire(NavigationGrid grid, CellTopology topology, DoodadService doodads, MapEditor mapEditor,
                      Supplier<CreepField> creep, long seed) {
        this.grid = grid;
        this.topology = topology;
        this.doodads = doodads;
        this.mapEditor = mapEditor;
        this.creep = creep;
        this.width = grid.getWidth();
        this.overlayFuel = overlayFuel(TileRegistry.installed());
        this.field = new FireField(grid.getWidth(), grid.getHeight(), seed, this);
        field.setListener(this);
    }

    public FireField field() { return field; }

    /** Steps the field, after the creep has spread, so a cell that catches this tick reads the creep as it now is. */
    public void tick(int simTick) {
        CreepField c = creep.get();
        if (c != null && c.refusal() == null) c.setRefusal(field::holdsCreepBack);
        field.tick(simTick);
    }

    // ---- FireTerrain ----------------------------------------------------

    @Override
    public int fuelMask(int idx) {
        int x = idx % width, y = idx / width;
        int mask = 0;
        CellTopology.GroundKind ground = topology.getGroundKind(x, y);
        if (ground == CellTopology.GroundKind.GRASS) mask |= FireFuel.GRASS.bit;
        int overlay = topology.getNatureOverlayIndex(x, y);
        if (overlay >= 0 && overlay < overlayFuel.length) mask |= overlayFuel[overlay];
        if (!grid.isWalkableAt(idx) && grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID) && isTree(x, y)) {
            mask |= FireFuel.CROWN.bit;
        }
        CreepField c = creep.get();
        if (c != null) {
            int cov = c.coverageAt(idx);
            if (cov >= CreepField.CREEPED && c.vitalityAt(idx) >= CreepField.CREEPED) mask |= FireFuel.CREEP.bit;
            else if (cov >= DEAD_CREEP_COVERAGE) mask |= FireFuel.DEAD_CREEP.bit;
        }
        return mask;
    }

    private boolean isTree(int x, int y) {
        if (doodads == null) return false;
        String id = doodads.damageablePropIdAt(x, y, topology);
        return id != null && id.startsWith("forest.");
    }

    // ---- Listener -------------------------------------------------------

    @Override
    public void ignited(int idx, int fuelMask) {
        CreepField c = creep.get();
        if (c != null && (FireFuel.CREEP.in(fuelMask) || FireFuel.DEAD_CREEP.in(fuelMask))) c.scorch(idx);
    }

    @Override
    public void burntOut(int idx, int fuelMask) {
        int x = idx % width, y = idx / width;
        if (FireFuel.BRUSH.in(fuelMask) || overlayIsFuel(x, y)) topology.setNatureOverlayIndex(x, y, -1);
        if (FireFuel.CROWN.in(fuelMask) && mapEditor != null && !grid.isWalkableAt(idx)) {
            mapEditor.damageWall(x, y, CROWN_BURN_DAMAGE);
        }
    }

    private boolean overlayIsFuel(int x, int y) {
        int overlay = topology.getNatureOverlayIndex(x, y);
        return overlay >= 0 && overlay < overlayFuel.length && overlayFuel[overlay] != 0;
    }

    /** Grass overlays are grass, shrubs and tufts are brush; rocks, water, sand and dirt are nothing. */
    static int[] overlayFuel(TileRegistry registry) {
        if (registry == null) return new int[0];
        int n = registry.size();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = overlayFuel(registry.byIndex(i).id);
        return out;
    }

    static int overlayFuel(String id) {
        if (id == null || !id.startsWith("nature.")) return 0;
        String name = id.substring("nature.".length());
        if (name.startsWith("shrub") || name.startsWith("tuft")) return FireFuel.BRUSH.bit;
        if (name.startsWith("grass")) return FireFuel.GRASS.bit;
        return 0;
    }
}
