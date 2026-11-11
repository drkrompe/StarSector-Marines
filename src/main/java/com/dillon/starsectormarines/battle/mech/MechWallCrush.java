package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.derelict.WalkerFootprint;
import com.dillon.starsectormarines.battle.derelict.WalkerKnees;
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
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * A wall-crushing walker ({@link MechVariant#crushesWalls()}: every heavy-lift walker, `mechs-nouns.md`, Wall crush)
 * breaking the ordinary walls and props it walks into, as a heavy hull does ({@link WallCrush}): the same walls,
 * barricades and props under the same ceiling ({@link WallCrush#HEAVY_CEILING}), a fortified wall still holding until
 * fire has worn it under, through the same destruction path ({@link MapEditor#damageWall},
 * {@link MapEditor#damageEdgeBarrier}).
 *
 * <p><b>As easily as a heavy tank</b> (owner: these walkers are large and should crush a wall as easily as a heavy
 * tank): what a collider meets falls at once, its whole structure spent the tick the walker reaches it, so the walker
 * walks through at its own pace as a heavy tank drives through a city wall at speed. A walker is too slow for a hull's
 * momentum ({@code HullMomentum}) to carry it through a row, so it is not modelled by momentum: it never grinds and
 * never waits at the wall. Its route pays for a breach as a crushing hull's does ({@code ClearanceRoutePlanner#BREACH_COST}).
 *
 * <p><b>A body and its legs, each its own collider</b> (owner: each leg should have its own collider and break only
 * what it hits). Each tick it walks its route it breaks what these overlap, and nothing between them:
 * <ul>
 *   <li>its <b>body</b>: the strip just ahead of it toward its next waypoint, as wide as it is
 *       ({@link #forEachBearingCell});</li>
 *   <li>each <b>planted foot</b>: a disc at the foot as the walker's legs are drawn ({@link #forEachFootCell}). A
 *       derelict walker's four feet are its leg rig's ({@link DerelictLegRig}), which this owns and steps every tick
 *       and the renderer draws ({@link #legRig}); a company walker's two are its gait's ({@link MechGaitState}),
 *       which the renderer draws too. A foot in the air breaks nothing: a fence the walker straddles stands, save
 *       where a foot comes down on it or the body walks into it. To a heavy walker's feet what it can break is floor
 *       ({@link #footHolds}): its feet land at their stride on a fence, never reach for it to grip.</li>
 * </ul>
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
     * A derelict walker's foot collider at the MG-40's size, cells: the drawn shin's tip where it meets the ground,
     * narrower than the battle's blocking footprint ({@link WalkerFootprint#FOOT_RADIUS}), so a foot breaks the cell it
     * stands in and seldom its neighbour. Scaled with the rig ({@link #rigSize}).
     */
    static final float RIG_FOOT_CELLS = .18f;
    /** A company walker's foot collider, in hull widths ({@link LayeredMechAppearance#hullWidthCells}). */
    static final float GAIT_FOOT_HULL_WIDTHS = .18f;
    /** Most feet a walker stands on: a derelict walker's four. */
    static final int MAX_FEET = DerelictLegRig.LEGS;
    private static final Direction[] FORWARD_EDGES = {Direction.E, Direction.N};
    /** Ticks between sweeps of the rigs of walkers no longer alive. */
    private static final int PRUNE_TICKS = 150;

    private final EntityWorld world;
    private final BattleComponents components;
    private final UnitRosterService roster;
    private final NavigationService navigation;
    private final MapEditor editor;
    private final DoodadService doodads;
    private final Query walkers;
    private final Bearing bearing = new Bearing();
    /** The leg rig of each heavy derelict walker, stepped here every tick and drawn by the renderer. */
    private final Map<Long, DerelictLegRig> rigs = new HashMap<>();
    private final DerelictLegRig.Solid solid;
    private LongFunction<WalkerKnees> knees = id -> null;
    private final float[] feet = new float[MAX_FEET * 2];
    private int ticks;

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
        this.solid = (x, y) -> footHolds(navigation.getGrid(), navigation.getTopology(), doodads, x, y);
    }

    /** Where a derelict walker's knees are read from, so a lost leg drags and grips nothing ({@code WalkerKnees}). */
    public void setKnees(LongFunction<WalkerKnees> knees) {
        this.knees = knees != null ? knees : id -> null;
    }

    /** The leg rig heavy derelict walker {@code id} stands on, stepped every tick; null for any other unit. */
    public DerelictLegRig legRig(long id) {
        return rigs.get(id);
    }

    /**
     * Whether cell ({@code x}, {@code y}) is solid to a heavy walker's foot: off the map, or closed by something it
     * cannot break. A wall or prop it breaks is floor to its feet, so a foot comes down on it at its stride rather than
     * reaching for its face to grip, and breaks it; a fortified wall, rock or works is still gripped as the leg rig
     * grips any wall ({@link DerelictLegRig}).
     */
    static boolean footHolds(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y) {
        if (!grid.inBounds(x, y)) return true;
        return !grid.isWalkable(x, y) && !WallCrush.canPush(grid, topology, doodads, x, y, WallCrush.HEAVY_CEILING);
    }

    /** Whether {@code variant}'s legs are a derelict leg rig rather than a company walker's gait. */
    static boolean rigged(MechVariant variant) {
        return variant.chassisAppearance == LayeredMechAppearance.CHASSIS_DERELICT;
    }

    /** {@code variant}'s size against the MG-40's, by which its leg rig is scaled. */
    static float rigSize(MechVariant variant) {
        return variant.renderScale / MechVariant.SECURITY_WALKER.renderScale;
    }

    /**
     * The planted feet of {@code rig} into {@code out} as x/y pairs: every leg not lost and not in the air. Returns the
     * count.
     */
    static int plantedFeet(DerelictLegRig rig, float[] out) {
        int n = 0;
        if (!rig.posed()) return 0;
        for (int i = 0; i < rig.legs(); i++) {
            if (rig.lost(i) || rig.stepping(i)) continue;
            out[n * 2] = rig.footX(i);
            out[n * 2 + 1] = rig.footY(i);
            n++;
        }
        return n;
    }

    /** The planted feet of a company walker's gait into {@code out} as x/y pairs: those not swinging. */
    static int plantedFeet(MechGaitState gait, float[] out) {
        int n = 0;
        if (gait.swingFoot() != MechGaitState.LEFT_FOOT) {
            out[0] = gait.leftFootX();
            out[1] = gait.leftFootY();
            n++;
        }
        if (gait.swingFoot() != MechGaitState.RIGHT_FOOT) {
            out[n * 2] = gait.rightFootX();
            out[n * 2 + 1] = gait.rightFootY();
            n++;
        }
        return n;
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

    /** Visits each in-bounds cell a foot's disc of {@code radius} at ({@code x}, {@code y}) overlaps. */
    static void forEachFootCell(NavigationGrid grid, float x, float y, float radius,
                                VehicleFootprint.CellConsumer consumer) {
        int x0 = (int) Math.floor(x - radius), x1 = (int) Math.floor(x + radius);
        int y0 = (int) Math.floor(y - radius), y1 = (int) Math.floor(y + radius);
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                if (!grid.inBounds(cx, cy)) continue;
                float nx = Math.max(cx, Math.min(cx + 1f, x)) - x, ny = Math.max(cy, Math.min(cy + 1f, y)) - y;
                if (nx * nx + ny * ny < radius * radius) consumer.accept(cx, cy);
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
                if (variant == null || !variant.crushesWalls() || hp[row] <= 0f) continue;
                int planted;
                float footRadius;
                if (rigged(variant)) {
                    DerelictLegRig rig = stepRig(id, variant, posX[row], posY[row], dt);
                    planted = plantedFeet(rig, feet);
                    footRadius = RIG_FOOT_CELLS * rigSize(variant);
                } else {
                    MechGaitState gait = world.has(id, components.MECH_GAIT_STATE)
                            && world.getObject(id, components.MECH_GAIT_STATE,
                            BattleComponents.MECH_GAIT_STATE_STATE) instanceof MechGaitState g ? g : null;
                    planted = gait != null ? plantedFeet(gait, feet) : 0;
                    footRadius = LayeredMechAppearance.hullWidthCells(variant.renderScale) * GAIT_FOOT_HULL_WIDTHS;
                }
                int[] path = (int[]) paths[row];
                if (path == null || pathIdx[row] >= Paths.cellCount(path)) continue;
                float dx = Paths.cellX(path, pathIdx[row]) + .5f - posX[row];
                float dy = Paths.cellY(path, pathIdx[row]) + .5f - posY[row];
                if (dx * dx + dy * dy < 1e-4f) continue;
                bearing.crush(grid, topology, doodads, posX[row], posY[row], dx, dy, roster.radius(id), feet,
                        planted, footRadius, breaker);
            }
        }
        if (++ticks % PRUNE_TICKS == 0) rigs.keySet().removeIf(id -> !roster.isAliveById(id));
    }

    /** Steps heavy derelict walker {@code id}'s leg rig to where it stands this tick, making it on first sight. */
    private DerelictLegRig stepRig(long id, MechVariant variant, float x, float y, float dt) {
        DerelictLegRig rig = rigs.computeIfAbsent(id, key -> new DerelictLegRig(
                DerelictLegRig.walkerAt(rigSize(variant))));
        WalkerKnees walkerKnees = knees.apply(id);
        if (walkerKnees != null) rig.legsLost(walkerKnees.lostMask(), walkerKnees.downed());
        float hip = world.has(id, components.MECH_LAYERED_ANIMATION)
                ? world.getFloat(id, components.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_HIP_FACING_DEGREES)
                : world.getFloat(id, components.MECH_LOCOMOTION, BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        rig.advance(x, y, hip, dt, solid);
        return rig;
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

    /** One walker's colliders for one tick, with their scratch: the crush runs on the serial tick. */
    static final class Bearing {
        /** Cells a collider overlaps, as x/y pairs. */
        private int[] cells = new int[128];
        private int count;

        /**
         * Brings down at once every pushable cell and window bay a walker's colliders overlap: its body, of
         * {@code radius}, at ({@code x}, {@code y}) walking along ({@code dx}, {@code dy}), and each of the
         * {@code footCount} planted feet in {@code feet} (x/y pairs), a disc of {@code footRadius}. Each piece takes
         * its whole structure, so it falls this tick; what lies between them is not touched.
         */
        void crush(NavigationGrid grid, CellTopology topology, DoodadService doodads, float x, float y, float dx,
                   float dy, float radius, float[] feet, int footCount, float footRadius, Breaker breaker) {
            count = 0;
            forEachBearingCell(grid, x, y, dx, dy, radius, this::collect);
            for (int f = 0; f < footCount; f++) forEachFootCell(grid, feet[f * 2], feet[f * 2 + 1], footRadius,
                    this::collect);
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
