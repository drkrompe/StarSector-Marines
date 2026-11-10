package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * A wall-crushing walker ({@link MechVariant#crushesWalls()}: the Pilgrim, `mech-lines.md`) pushing down the ordinary
 * walls it walks into by its weight, as a heavy hull does ({@link WallCrush}): the same walls and barricades under the
 * same ceiling ({@link WallCrush#HEAVY_CEILING}) at a heavy hull's rate ({@link WallCrush#HEAVY_PER_SECOND}), a
 * fortified wall still holding until fire has worn it under.
 *
 * <p>Its route is planned over the breach view ({@link WallCrush#planningGrid(int, NavigationGrid)}, installed on
 * {@link NavigationService#setOwnRouteGrid}), so a wall it can push is open ground to its planner, while its body
 * still moves over the live grid, which holds it at the wall. Each tick a walker that has a route bears on the strip
 * just ahead of it, toward its next waypoint, as wide as it is: every pushable cell and window bay there takes the
 * tick's share of its rate, until it falls and the walker walks on.
 */
public final class MechWallCrush {

    /** How far past the walker's body the strip it bears on reaches, cells. */
    static final float REACH_CELLS = .6f;
    /** How far back inside its body the strip starts, so a wall already touching it is still pushed. */
    static final float OVERLAP_CELLS = .2f;

    private final EntityWorld world;
    private final BattleComponents components;
    private final UnitRosterService roster;
    private final NavigationService navigation;
    private final MapEditor editor;
    private final DoodadService doodads;
    private final Query walkers;
    /** Push not yet spent as whole structure points, per walker. */
    private final Map<Long, Float> carry = new HashMap<>();

    public MechWallCrush(EntityWorld world, BattleComponents components, UnitRosterService roster,
                         NavigationService navigation, MapEditor editor, DoodadService doodads) {
        this.world = world;
        this.components = components;
        this.roster = roster;
        this.navigation = navigation;
        this.editor = editor;
        this.doodads = doodads;
        this.walkers = world.query(new ComponentType[]{components.MECH_LOCOMOTION, components.POSITION,
                components.MOVEMENT, components.HEALTH}, null);
    }

    /** The structure a walker's push spends this tick, carrying the fraction to the next: {@code [whole, carried]}. */
    static float[] spend(float carried, float dt) {
        float pending = carried + WallCrush.HEAVY_PER_SECOND * dt;
        int whole = (int) pending;
        return new float[]{whole, pending - whole};
    }

    /**
     * The facing, in the footprint's degrees (0 toward +y, turning toward -x), of travel along ({@code dx}, {@code dy}).
     */
    static float facingToward(float dx, float dy) {
        return (float) Math.toDegrees(Math.atan2(-dx, dy));
    }

    /**
     * Visits each cell of the strip a walker of {@code radius} at ({@code x}, {@code y}) bears on travelling along
     * ({@code dx}, {@code dy}): from just inside its body to {@link #REACH_CELLS} past it, as wide as it is.
     */
    static void forEachBearingCell(NavigationGrid grid, float x, float y, float dx, float dy, float radius,
                                   VehicleFootprint.CellConsumer consumer) {
        float length = REACH_CELLS + OVERLAP_CELLS;
        float offset = radius - OVERLAP_CELLS + length * .5f;
        VehicleFootprint.forEachOverlappedCell(x, y, facingToward(dx, dy), offset, length, radius * 2f, grid,
                consumer);
    }

    /**
     * Whether walker {@code id} is a wall-crusher with a route whose way ahead is a wall it can push down: held there,
     * it keeps its route while it pushes, where any other walker held by a wall drops it. Reads only.
     */
    public boolean heldByPushableWall(long id) {
        MechVariant variant = roster.identity().mechVariant(id);
        if (variant == null || !variant.crushesWalls()) return false;
        int[] path = world.getObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH) instanceof int[] p
                ? p : null;
        int index = world.getInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX);
        if (path == null || index >= Paths.cellCount(path)) return false;
        float x = world.getFloat(id, components.POSITION, BattleComponents.POSITION_X);
        float y = world.getFloat(id, components.POSITION, BattleComponents.POSITION_Y);
        float dx = Paths.cellX(path, index) + .5f - x, dy = Paths.cellY(path, index) + .5f - y;
        if (dx * dx + dy * dy < 1e-4f) return false;
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        boolean[] pushable = {false};
        forEachBearingCell(grid, x, y, dx, dy, roster.radius(id), (cx, cy) -> {
            if (WallCrush.canPush(grid, topology, doodads, cx, cy, WallCrush.HEAVY_CEILING)) pushable[0] = true;
        });
        return pushable[0];
    }

    public void tick(float dt) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        for (ArchetypeTable table : world.matched(walkers)) {
            float[] posX = table.floats(components.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION, BattleComponents.POSITION_Y).array();
            float[] hp = table.floats(components.HEALTH, BattleComponents.HEALTH_HP).array();
            Object[] paths = table.objects(components.MOVEMENT, BattleComponents.MOVEMENT_PATH).array();
            int[] pathIdx = table.ints(components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX).array();
            for (int row = 0, n = table.rowCount(); row < n; row++) {
                long id = table.entityAt(row);
                MechVariant variant = roster.identity().mechVariant(id);
                if (variant == null || !variant.crushesWalls()) continue;
                int[] path = (int[]) paths[row];
                if (hp[row] <= 0f || path == null || pathIdx[row] >= Paths.cellCount(path)) {
                    carry.remove(id);
                    continue;
                }
                float dx = Paths.cellX(path, pathIdx[row]) + .5f - posX[row];
                float dy = Paths.cellY(path, pathIdx[row]) + .5f - posY[row];
                if (dx * dx + dy * dy < 1e-4f) continue;
                bear(grid, topology, id, posX[row], posY[row], dx, dy, roster.radius(id), dt);
            }
        }
    }

    private void bear(NavigationGrid grid, CellTopology topology, long id, float x, float y, float dx, float dy,
                      float radius, float dt) {
        int most = WallCrush.HEAVY_CEILING;
        int[] cells = new int[64];
        int[] count = {0};
        forEachBearingCell(grid, x, y, dx, dy, radius, (cx, cy) -> {
            if (count[0] * 2 >= cells.length) return;
            cells[count[0] * 2] = cx;
            cells[count[0] * 2 + 1] = cy;
            count[0]++;
        });
        boolean bearing = false;
        for (int i = 0; i < count[0] && !bearing; i++) {
            int cx = cells[i * 2], cy = cells[i * 2 + 1];
            bearing = WallCrush.canPush(grid, topology, doodads, cx, cy, most) || pushableEdge(grid, cells, count[0], cx,
                    cy, most) != null;
        }
        if (!bearing) {
            carry.remove(id);
            return;
        }
        float[] spent = spend(carry.getOrDefault(id, 0f), dt);
        carry.put(id, spent[1]);
        int amount = (int) spent[0];
        if (amount <= 0) return;
        for (int i = 0; i < count[0]; i++) {
            int cx = cells[i * 2], cy = cells[i * 2 + 1];
            if (WallCrush.canPush(grid, topology, doodads, cx, cy, most)) editor.damageWall(cx, cy, amount);
            Direction edge = pushableEdge(grid, cells, count[0], cx, cy, most);
            if (edge != null) editor.damageEdgeBarrier(cx, cy, edge, amount);
        }
    }

    /** A pushable window bay between ({@code x}, {@code y}) and a neighbour also in the strip, read east or north. */
    private static Direction pushableEdge(NavigationGrid grid, int[] cells, int count, int x, int y, int most) {
        for (Direction direction : new Direction[]{Direction.E, Direction.N}) {
            int nx = x + direction.dx, ny = y + direction.dy;
            for (int i = 0; i < count; i++) {
                if (cells[i * 2] == nx && cells[i * 2 + 1] == ny
                        && WallCrush.canPushEdge(grid, x, y, direction, most)) {
                    return direction;
                }
            }
        }
        return null;
    }
}
