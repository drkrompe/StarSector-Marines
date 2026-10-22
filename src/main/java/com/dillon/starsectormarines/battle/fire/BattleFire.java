package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
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
 * <p><b>Buildings burn by material.</b> A light wall or window (at most
 * {@link #LIGHT_WALL_HP}) is structure fuel and falls to rubble once burnt
 * through; a plain indoor or panelled floor is furnishing fuel, and a room that
 * catches loses its roof. A fortified structure, one any of whose walls is
 * heavier than that, burns at none of its walls, windows or floors, and a
 * striped (military) floor never burns. A standing wall or window stops heat
 * passing over it, so fire gets in through doorways and through what burns.
 *
 * <p>Holds no state of its own beyond the field; the map, the creep field and
 * the doodads remain the owners of what they hold. A brush or crown fire
 * smokes ({@link FireSmoke}).
 */
public final class BattleFire implements FireTerrain, FireField.Listener {

    /** Structure a burnt-out crown cell takes from its tree: a 200-point tree falls after three cells. */
    public static final int CROWN_BURN_DAMAGE = 70;
    /** Coverage at or above which dying creep is dry fuel. */
    static final int DEAD_CREEP_COVERAGE = 64;
    /** The heaviest wall that burns: an ordinary building's. Compound, redoubt and fortified walls are heavier. */
    public static final int LIGHT_WALL_HP = 100;
    /** Damage that knocks a burnt-through wall cell down whatever it had left. */
    static final int BURNT_THROUGH_DAMAGE = 1_000_000;

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final DoodadService doodads;
    private final MapEditor mapEditor;
    private final Supplier<CreepField> creep;
    private final FireField field;
    private final FireSmoke smoke;
    /** Per registry index: the fuel a nature overlay tile is, 0 for none. */
    private final int[] overlayFuel;
    private final int width;
    /** Per building id: whether it is a fortified structure, read off its walls when the fire is made. */
    private final boolean[] fortified;

    public BattleFire(NavigationGrid grid, CellTopology topology, DoodadService doodads, MapEditor mapEditor,
                      Supplier<CreepField> creep, long seed) {
        this.grid = grid;
        this.topology = topology;
        this.doodads = doodads;
        this.mapEditor = mapEditor;
        this.creep = creep;
        this.width = grid.getWidth();
        this.overlayFuel = overlayFuel(TileRegistry.installed());
        this.fortified = fortifiedBuildings(grid, topology);
        this.field = new FireField(grid.getWidth(), grid.getHeight(), seed, this);
        this.smoke = new FireSmoke(grid);
        field.setListener(this);
    }

    public FireField field() { return field; }

    public FireSmoke smoke() { return smoke; }

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
        // A standing wall is what it is built of, whatever ground lies under it.
        if (!grid.isWalkableAt(idx) && (topology.isWall(x, y) || topology.isWindow(x, y))) {
            return structureFuel(idx, x, y, ground);
        }
        if (ground == CellTopology.GroundKind.GRASS) mask |= FireFuel.GRASS.bit;
        int overlay = topology.getNatureOverlayIndex(x, y);
        if (overlay >= 0 && overlay < overlayFuel.length) mask |= overlayFuel[overlay];
        if (!grid.isWalkableAt(idx) && grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID) && isTree(x, y)) {
            mask |= FireFuel.CROWN.bit;
        }
        mask |= structureFuel(idx, x, y, ground);
        CreepField c = creep.get();
        if (c != null) {
            int cov = c.coverageAt(idx);
            if (cov >= CreepField.CREEPED && c.vitalityAt(idx) >= CreepField.CREEPED) mask |= FireFuel.CREEP.bit;
            else if (cov >= DEAD_CREEP_COVERAGE) mask |= FireFuel.DEAD_CREEP.bit;
        }
        return mask;
    }

    /** What a building cell offers: its light wall or window, or its furnished floor. */
    private int structureFuel(int idx, int x, int y, CellTopology.GroundKind ground) {
        int building = topology.getBuildingId(x, y);
        if (building > 0 && building < fortified.length && fortified[building]) return 0;
        if (!grid.isWalkableAt(idx)) {
            if (!topology.isWall(x, y) && !topology.isWindow(x, y)) return 0;
            int hp = grid.getWallHp(x, y);
            return hp <= LIGHT_WALL_HP ? FireFuel.STRUCTURE.bit : 0;
        }
        if (building == 0) return 0;
        return ground == CellTopology.GroundKind.INDOOR || ground == CellTopology.GroundKind.TILE
                ? FireFuel.FURNISHING.bit : 0;
    }

    @Override
    public boolean blocksHeat(int idx) {
        if (grid.isWalkableAt(idx)) return false;
        int x = idx % width, y = idx / width;
        // A cliff face is bare rock between two tiers: fire on either side stops at it.
        ElevationMap elevation = grid.elevation();
        return topology.isWall(x, y) || topology.isWindow(x, y)
                || (elevation != null && elevation.isFaceAt(idx));
    }

    @Override
    public long barrierRevision() { return grid.topologyRevision(); }

    /** Buildings walled by anything heavier than a light wall, or floored as military: they do not burn. */
    static boolean[] fortifiedBuildings(NavigationGrid grid, CellTopology topology) {
        int max = 0;
        for (int y = 0; y < grid.getHeight(); y++)
            for (int x = 0; x < grid.getWidth(); x++) max = Math.max(max, topology.getBuildingId(x, y));
        boolean[] out = new boolean[max + 1];
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int b = topology.getBuildingId(x, y);
                if (b <= 0 || out[b]) continue;
                if (topology.getGroundKind(x, y) == CellTopology.GroundKind.STRIPED) { out[b] = true; continue; }
                // Walls carry no building id of their own: a building is as heavy as the walls round its cells.
                for (int dy = -1; dy <= 1 && !out[b]; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (!grid.inBounds(nx, ny) || grid.isWalkable(nx, ny)) continue;
                    if ((topology.isWall(nx, ny) || topology.isWindow(nx, ny)) && grid.getWallHp(nx, ny) > LIGHT_WALL_HP) {
                        out[b] = true;
                        break;
                    }
                }
            }
        }
        return out;
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
        smoke.ignited(idx, fuelMask, field.windX(), field.windY(), field.windStrength());
        // A room on fire burns through its roof: the fire is seen, and the room is open to the sky.
        if (FireFuel.FURNISHING.in(fuelMask) && mapEditor != null) mapEditor.destroyRoof(idx % width, idx / width);
    }

    @Override
    public void burntOut(int idx, int fuelMask) {
        smoke.burntOut(idx);
        int x = idx % width, y = idx / width;
        if (FireFuel.BRUSH.in(fuelMask) || overlayIsFuel(x, y)) topology.setNatureOverlayIndex(x, y, -1);
        if (FireFuel.STRUCTURE.in(fuelMask) && mapEditor != null && !grid.isWalkableAt(idx)) {
            mapEditor.damageWall(x, y, BURNT_THROUGH_DAMAGE);
        }
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
