package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;

import java.util.ArrayList;
import java.util.List;

/**
 * What a wall-crushing hull ({@link VehicleType#crushesWalls()}) pushes through, how fast, and the view of the map its
 * route proof reads. An ultra-heavy hull crushes by its own weight on its nose: there is no blade on any hull.
 *
 * <p><b>The rule.</b> A blocking cell is <em>pushable</em> when its structure is spent through the ordinary wall
 * channel — a wall cell, or a sandbag, fence or crate barricade — and that structure is above zero and no more than
 * the hull's ceiling ({@link #ceiling}: {@link #ULTRA_HEAVY_CEILING} by weight). Ordinary city walls (100) and
 * gated-housing walls (80) fall; fortified and fortress walls (150 and up) do not, until fire has worn them under the
 * ceiling. Natural obstructions (rock, trees, summit terrain),
 * registered equipment and entity-owned fixtures (a turret's mount) are never pushed. Low crash debris under the hull
 * and its bearing strip is graded flat: the cell keeps its terrain and loses the slowing tag.
 *
 * <p><b>The bearing strip.</b> A hull bears on a strip just past its nose ({@link #bearingWidth}): the hull's own
 * width, since it has nothing wider than itself to push with, so the gap it cuts is its own width.
 *
 * <p><b>The rate.</b> The hull spends its rate ({@link #rate}: {@link #ULTRA_HEAVY_PER_SECOND} by weight) of
 * structure per second on every pushable cell it bears on at once, through
 * {@link MapEditor#damageWall} — the same destruction path a breaching charge uses. A pushed wall becomes rubble and a
 * pushed barricade its destroyed art, and the cell is ordinary walkable ground for every vehicle and unit from then
 * on. Opening is the only direction it edits: like every destruction op, it can only make the map more permissive, so
 * nothing derived from it is invalidated beyond the ordinary opened-cell flush.
 *
 * <p><b>Momentum.</b> A hull driving onto what it pushes spends its way on the cells its nose reaches that tick
 * ({@link HullMomentum}), by its mass ({@link VehicleType#massTonnes()}). Where the structure its speed carries and
 * this tick's push cover all those cells have left, they fall at once and the hull drives on at the speed the rest of
 * its energy gives; where they do not, they share its momentum and the hull is brought up against them, to push them
 * down at its rate from a standstill.
 *
 * <p><b>The hull.</b> A crushing hull is held by pushable ground only while it pushes it down. Where its flank, not
 * its nose, is what bears on pushable cells, its tail or flank swung into a house wall as it turns, and nothing else
 * holds it, it {@link #grind grinds} them at its rate. Its route proof counts that ground open, so its turns may sweep
 * there; without this a hull stood against a wall its own plan had driven it into.
 *
 * <p><b>Route proof.</b> {@link #breachView} is a routing copy of the grid with every pushable cell open. A crushing
 * hull's move-order proof and its rolling local planner read that view, laid over the grid it moves over (its
 * tracks' crush view, or a heavy skirt's), so ground it can open counts as reachable for it and for nobody else; its
 * physical sweep still reads the grid it moves over, which is what holds the hull against a wall until it has pushed
 * it down.
 */
public final class WallCrush {

    /**
     * The heaviest structure an ultra-heavy hull crushes: ordinary city and gated-housing walls and every barricade,
     * while a fortified wall (150 and up) holds until fire has worn it under, so a fort is still
     * opened by fire and charges first. Its greater mass shows in the momentum it carries through, not in the ceiling.
     */
    public static final int ULTRA_HEAVY_CEILING = 100;
    /** Structure an ultra-heavy hull's weight spends per second on each cell it bears on: a full ordinary wall in 1.25 s. */
    public static final float ULTRA_HEAVY_PER_SECOND = 80f;
    /** How far past the hull's nose the bearing strip reaches, cells. */
    static final float REACH_CELLS = .6f;
    /** How far back from the nose the bearing strip starts, so a cell already touching the hull is still pushed. */
    static final float OVERLAP_CELLS = .2f;
    /** Cells past where its nose gets to this tick that a hull counts as hitting: the sweep's contact skin. */
    static final float CONTACT_CELLS = .05f;

    private final NavigationService navigation;
    private final MapEditor editor;
    private final DoodadService doodads;
    /** Cells the hull bears on this tick, as x/y pairs. Host-thread scratch: vehicles are driven serially. */
    private final int[] bearing = new int[64];
    private int bearingCount;
    /** Cells the hull's nose hits this tick, as x/y pairs. */
    private final int[] struck = new int[64];
    private int struckCount;
    /** Whether something the hull cannot push holds it this tick ({@link #grind}). */
    private boolean heldElsewhere;
    /** Breach views by ceiling and base, rebuilt when the map's topology has moved. */
    private final List<View> views = new ArrayList<>(2);

    public WallCrush(NavigationService navigation, MapEditor editor, DoodadService doodads) {
        this.navigation = navigation;
        this.editor = editor;
        this.doodads = doodads;
    }

    /** The heaviest structure {@code type} pushes: {@link #ULTRA_HEAVY_CEILING} by weight, none for a hull that crushes no walls. */
    public static int ceiling(VehicleType type) { return ceiling(type.weight()); }

    /** The ceiling of a hull of {@code weight}. */
    static int ceiling(VehicleType.Weight weight) {
        return weight.crushesWalls() ? ULTRA_HEAVY_CEILING : 0;
    }

    /** Structure a second {@code type} spends on each cell it bears on; none for a hull that crushes no walls. */
    public static float rate(VehicleType type) { return rate(type.weight()); }

    /** The rate of a hull of {@code weight}. */
    static float rate(VehicleType.Weight weight) {
        return weight.crushesWalls() ? ULTRA_HEAVY_PER_SECOND : 0f;
    }

    /** How wide {@code type} bears, cells: its own hull. */
    public static float bearingWidth(VehicleType type) { return type.visualWidthCells; }

    /** Whether an ultra-heavy hull can push cell {@code (x, y)} down right now. See the class rule. */
    public static boolean canPush(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y) {
        return canPush(grid, topology, doodads, x, y, ULTRA_HEAVY_CEILING);
    }

    /** Whether a hull whose ceiling is {@code most} can push cell {@code (x, y)} down right now. */
    public static boolean canPush(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y,
                                  int most) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)
                || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) return false;
        int structure = grid.getWallHp(x, y);
        if (structure <= 0 || structure > most) return false;
        if (doodads != null && doodads.damageablePropIdAt(x, y, topology) != null) {
            return doodads.barricadeAt(x, y, topology);
        }
        return topology.isWall(x, y) && !topology.isFixture(x, y);
    }

    /** Whether the hull grades cell {@code (x, y)}: low crash debris on open ground. */
    public static boolean grades(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y)
                && grid.hasTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS);
    }

    /**
     * A routing copy of {@code grid} with every cell an ultra-heavy hull could push open on all sides, as it would
     * leave it. The live grid is not touched. Cells only: a closed shared edge stays closed.
     */
    public static NavigationGrid breachView(NavigationGrid grid, CellTopology topology, DoodadService doodads) {
        return breachView(grid, grid, topology, doodads, ULTRA_HEAVY_CEILING);
    }

    /**
     * {@link #breachView(NavigationGrid, CellTopology, DoodadService)} for a ceiling of {@code most}, laid over
     * {@code base}, a view of {@code grid}.
     */
    static NavigationGrid breachView(NavigationGrid base, NavigationGrid grid, CellTopology topology,
                                     DoodadService doodads, int most) {
        NavigationGrid copy = base.copyRoutingTopology();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!canPush(grid, topology, doodads, x, y, most)) continue;
                copy.setWalkable(x, y, true);
                copy.openAllEdges(x, y);
            }
        }
        return copy;
    }

    /**
     * The breach view {@code type} plans over, laid over {@code base}, the grid it moves over (the live grid, its
     * crush view, or a skirt's view): rebuilt when the map's topology has moved since it was last asked for.
     */
    public NavigationGrid planningGrid(VehicleType type, NavigationGrid base) {
        NavigationGrid grid = navigation.getGrid();
        long revision = grid.topologyRevision();
        int most = ceiling(type);
        for (View view : views) {
            if (view.most != most || view.base != base) continue;
            if (view.revision != revision) {
                view.grid = breachView(base, grid, navigation.getTopology(), doodads, most);
                view.revision = revision;
            }
            return view.grid;
        }
        // A base that is gone (a skirt's view rebuilt) leaves its view behind: one view per ceiling.
        views.removeIf(view -> view.most == most);
        View view = new View(most, base, revision, breachView(base, grid, navigation.getTopology(), doodads, most));
        views.add(view);
        return view.grid;
    }

    /**
     * One tick of a crushing hull bearing ahead at {@code body}'s pose: grades the debris under the hull and the
     * strip, and spends this tick's push on every pushable cell in the strip.
     *
     * @return whether the hull bore on at least one pushable cell — work that counts as progress for the hull's
     *         stall and wall-contact recovery
     */
    public boolean push(GroundBody body, VehicleType type, VehicleControlComponent control, float dt) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        int most = ceiling(type);
        float halfL = type.visualLengthCells * .5f;
        float stripLength = REACH_CELLS + OVERLAP_CELLS;
        float stripOffset = halfL - OVERLAP_CELLS + stripLength * .5f;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells, type.visualWidthCells, grid, (x, y) -> {
                    if (grades(grid, x, y)) grid.setTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS, false);
                });
        bearingCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, stripOffset,
                stripLength, bearingWidth(type), grid, (x, y) -> {
                    if (grades(grid, x, y)) grid.setTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS, false);
                    if (!canPush(grid, topology, doodads, x, y, most)) return;
                    if (bearingCount * 2 >= bearing.length) return;
                    bearing[bearingCount * 2] = x;
                    bearing[bearingCount * 2 + 1] = y;
                    bearingCount++;
                });
        if (bearingCount == 0) {
            control.bladeCarry = 0f;
            return false;
        }
        float pending = control.bladeCarry + rate(type) * dt;
        int spend = (int) pending;
        control.bladeCarry = pending - spend;
        strike(body, type, grid, topology, spend, dt);
        if (spend > 0) {
            for (int i = 0; i < bearingCount; i++) editor.damageWall(bearing[i * 2], bearing[i * 2 + 1], spend);
        }
        return true;
    }

    /**
     * One tick of a hull held where it means to move: at {@code body}'s pose, the pose its move would reach, its
     * footprint overlaps blocking cells. Where every one is pushable it spends this tick's push on them, as its nose
     * does.
     *
     * @return whether it bore on pushable cells and on nothing else — work that counts as progress, as
     *         {@link #push}'s does; {@code false}, and nothing spent, where anything else holds it
     */
    public boolean grind(GroundBody body, VehicleType type, VehicleControlComponent control, float dt) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        int most = ceiling(type);
        bearingCount = 0;
        heldElsewhere = false;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells, type.visualWidthCells, grid, (x, y) -> {
                    if (grid.isWalkable(x, y)) return;
                    if (!canPush(grid, topology, doodads, x, y, most) || bearingCount * 2 >= bearing.length) {
                        heldElsewhere = true;
                        return;
                    }
                    bearing[bearingCount * 2] = x;
                    bearing[bearingCount * 2 + 1] = y;
                    bearingCount++;
                });
        if (heldElsewhere || bearingCount == 0) return false;
        float pending = control.bladeCarry + rate(type) * dt;
        int spend = (int) pending;
        control.bladeCarry = pending - spend;
        if (spend > 0) {
            for (int i = 0; i < bearingCount; i++) editor.damageWall(bearing[i * 2], bearing[i * 2 + 1], spend);
        }
        return true;
    }

    /**
     * The hull's momentum spent on what its nose reaches this tick: the pushable cells within the way it moves, not
     * the whole strip it bears on, so a cell beside its path that it never hits does not stop it. Where its momentum
     * and {@code spend} cover all those cells have left they fall now and it drives on at the speed the rest gives;
     * where they do not, they share its momentum and it is brought up against them.
     */
    private void strike(GroundBody body, VehicleType type, NavigationGrid grid, CellTopology topology, int spend,
                        float dt) {
        float tonnes = type.massTonnes();
        float impact = HullMomentum.structure(tonnes, body.speed);
        if (impact <= 0f) return;
        int most = ceiling(type);
        float reach = OVERLAP_CELLS + body.speed * dt + CONTACT_CELLS;
        struckCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees,
                type.visualLengthCells * .5f - OVERLAP_CELLS + reach * .5f, reach, bearingWidth(type), grid,
                (x, y) -> {
                    if (!canPush(grid, topology, doodads, x, y, most) || struckCount * 2 >= struck.length) return;
                    struck[struckCount * 2] = x;
                    struck[struckCount * 2 + 1] = y;
                    struckCount++;
                });
        if (struckCount == 0) return;
        int standing = 0;
        for (int i = 0; i < struckCount; i++) standing += Math.max(0, grid.getWallHp(struck[i * 2], struck[i * 2 + 1]) - spend);
        if (impact >= standing) {
            // It goes through: what it hits falls, and what that took comes off its way.
            for (int i = 0; i < struckCount; i++) {
                int x = struck[i * 2], y = struck[i * 2 + 1];
                editor.damageWall(x, y, Math.max(0, grid.getWallHp(x, y) - spend));
            }
            body.speed = HullMomentum.speedAfter(tonnes, body.speed, standing);
            return;
        }
        // It is brought up: its whole way goes into what it hits, shared.
        int share = (int) (impact / struckCount);
        for (int i = 0; i < struckCount; i++) editor.damageWall(struck[i * 2], struck[i * 2 + 1], share);
        body.speed = 0f;
    }

    private static final class View {
        final int most;
        final NavigationGrid base;
        long revision;
        NavigationGrid grid;

        View(int most, NavigationGrid base, long revision, NavigationGrid grid) {
            this.most = most;
            this.base = base;
            this.revision = revision;
            this.grid = grid;
        }
    }
}
