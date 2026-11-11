package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
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

import java.util.Arrays;

/**
 * A wall-crushing walker ({@link MechVariant#crushesWalls()}: every heavy-lift walker, `mechs-nouns.md`, Wall crush)
 * breaking the ordinary walls and props it walks into, as a heavy hull does ({@link WallCrush}): the same walls,
 * barricades and props under the same ceiling ({@link WallCrush#HEAVY_CEILING}), a fortified wall still holding until
 * fire has worn it under, through the same destruction path ({@link MapEditor#damageWall},
 * {@link MapEditor#damageEdgeBarrier}).
 *
 * <p><b>As easily as a heavy tank</b> (owner: these walkers are large and should crush a wall as easily as a heavy
 * tank): what it bears on falls at once, its whole structure spent the tick the walker reaches it, so the walker walks
 * through at its own pace as a heavy tank drives through a city wall at speed. A walker is too slow for a hull's
 * momentum ({@code HullMomentum}) to carry it through a row, so it is not modelled by momentum: it never grinds and
 * never waits at the wall. Its route pays for a breach as a crushing hull's does ({@code ClearanceRoutePlanner#BREACH_COST}).
 *
 * <p><b>Its legs crush too</b> (owner: the legs of the large walker should rip through structures as well). Each tick
 * it walks its route it bears on two areas: the strip just ahead of its body toward its next waypoint, as wide as it
 * is ({@link #forEachBearingCell}), and the ground its legs stand over and stride through, every cell whose centre lies
 * within its drawn leg span of its body ({@link #legReach}, {@link #forEachLegCell}). A leg striding into a house's
 * corner or a fence beside it breaks it, not only the torso.
 *
 * <p>Its route is planned over the breach view ({@link WallCrush#planningGrid(int, NavigationGrid)}, installed on
 * {@link NavigationService#setOwnRouteGrid}), so a wall it can push is open ground to its planner, while its body still
 * moves over the live grid.
 */
public final class MechWallCrush {

    /** How far past the walker's body the strip it bears on reaches, cells. */
    static final float REACH_CELLS = .6f;
    /** How far back inside its body the strip starts, so a wall already touching it is still pushed. */
    static final float OVERLAP_CELLS = .2f;
    /**
     * The drawn leg span's reach from the walker's centre, in hull widths
     * ({@link LayeredMechAppearance#hullWidthCells}): measured off the look frames, an MG-40's splayed legs reach about
     * 1.4 cells from its centre and an MG-70's about 2.4, against body radii of 0.7 and 1.25.
     */
    static final float LEG_REACH_HULL_WIDTHS = .62f;
    private static final Direction[] FORWARD_EDGES = {Direction.E, Direction.N};

    private final EntityWorld world;
    private final BattleComponents components;
    private final UnitRosterService roster;
    private final NavigationService navigation;
    private final MapEditor editor;
    private final DoodadService doodads;
    private final Query walkers;
    private final Bearing bearing = new Bearing();

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

    /** How far {@code variant}'s legs reach from its centre, cells: half its drawn leg span. */
    static float legReach(MechVariant variant) {
        return LayeredMechAppearance.hullWidthCells(variant.renderScale) * LEG_REACH_HULL_WIDTHS;
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

    /** Visits each in-bounds cell whose centre lies within {@code reach} of ({@code x}, {@code y}): under its legs. */
    static void forEachLegCell(NavigationGrid grid, float x, float y, float reach,
                               VehicleFootprint.CellConsumer consumer) {
        int x0 = (int) Math.floor(x - reach), x1 = (int) Math.floor(x + reach);
        int y0 = (int) Math.floor(y - reach), y1 = (int) Math.floor(y + reach);
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                if (!grid.inBounds(cx, cy)) continue;
                float ox = cx + .5f - x, oy = cy + .5f - y;
                if (ox * ox + oy * oy <= reach * reach) consumer.accept(cx, cy);
            }
        }
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
                if (hp[row] <= 0f || path == null || pathIdx[row] >= Paths.cellCount(path)) continue;
                float dx = Paths.cellX(path, pathIdx[row]) + .5f - posX[row];
                float dy = Paths.cellY(path, pathIdx[row]) + .5f - posY[row];
                if (dx * dx + dy * dy < 1e-4f) continue;
                bearing.crush(grid, topology, doodads, posX[row], posY[row], dx, dy, roster.radius(id),
                        legReach(variant), breaker);
            }
        }
    }

    /** Where a crush lands: the map editor in a battle, a recorder in a test. */
    interface Breaker {
        void wall(int x, int y, int structure);

        void edge(int x, int y, Direction direction, int structure);
    }

    private final Breaker breaker = new Breaker() {
        @Override public void wall(int x, int y, int structure) { editor.damageWall(x, y, structure); }

        @Override public void edge(int x, int y, Direction direction, int structure) {
            editor.damageEdgeBarrier(x, y, direction, structure);
        }
    };

    /** One walker's bearing for one tick, with its scratch: the crush runs on the serial tick. */
    static final class Bearing {
        /** Cells borne on, as x/y pairs. */
        private int[] cells = new int[128];
        private int count;

        /**
         * Brings down at once every pushable cell and window bay a walker of {@code radius} with legs reaching
         * {@code legReach}, at ({@code x}, {@code y}) and walking along ({@code dx}, {@code dy}), bears on: each takes
         * its whole structure, so it falls this tick.
         */
        void crush(NavigationGrid grid, CellTopology topology, DoodadService doodads, float x, float y, float dx,
                   float dy, float radius, float legReach, Breaker breaker) {
            count = 0;
            forEachBearingCell(grid, x, y, dx, dy, radius, this::collect);
            forEachLegCell(grid, x, y, legReach, this::collect);
            int most = WallCrush.HEAVY_CEILING;
            for (int i = 0; i < count; i++) {
                int cx = cells[i * 2], cy = cells[i * 2 + 1];
                if (WallCrush.canPush(grid, topology, doodads, cx, cy, most)) {
                    breaker.wall(cx, cy, WallCrush.structure(grid, topology, doodads, cx, cy));
                }
                for (Direction direction : FORWARD_EDGES) {
                    if (!collected(cx + direction.dx, cy + direction.dy)
                            || !WallCrush.canPushEdge(grid, cx, cy, direction, most)) continue;
                    breaker.edge(cx, cy, direction, grid.getEdgeBarrier(cx, cy, direction).structure());
                }
            }
        }

        private void collect(int cx, int cy) {
            if (collected(cx, cy)) return;
            if (count * 2 >= cells.length) cells = Arrays.copyOf(cells, cells.length * 2);
            cells[count * 2] = cx;
            cells[count * 2 + 1] = cy;
            count++;
        }

        private boolean collected(int cx, int cy) {
            for (int i = 0; i < count; i++) if (cells[i * 2] == cx && cells[i * 2 + 1] == cy) return true;
            return false;
        }
    }
}
