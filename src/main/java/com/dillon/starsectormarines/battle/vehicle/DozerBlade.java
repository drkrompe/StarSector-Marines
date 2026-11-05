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
 * route proof reads. A heavy or ultra-heavy hull crushes by its own weight on its nose; the dozer breacher, until it
 * is retired, by its blade. The class keeps the blade's name until the dozer goes.
 *
 * <p><b>The rule.</b> A blocking cell is <em>pushable</em> when its structure is spent through the ordinary wall
 * channel — a wall cell, or a sandbag, fence or crate barricade — and that structure is above zero and no more than
 * the hull's ceiling ({@link #ceiling}: {@link #HEAVY_CEILING} and {@link #ULTRA_HEAVY_CEILING} by weight,
 * {@link #MAX_PUSH_STRUCTURE} by the blade). Ordinary city walls (100) and gated-housing walls (80) fall to each;
 * fortified and fortress walls (150 and up) do not, until fire has worn them under the ceiling. Natural obstructions
 * (rock, trees, summit terrain), a fort's or an objective's works ({@link NavigationGrid.CellTag#WORKS}), registered
 * equipment and entity-owned fixtures (a turret's mount) are never pushed. Low crash debris under the hull
 * and its bearing strip is graded flat: the cell keeps its terrain and loses the slowing tag.
 *
 * <p><b>The bearing strip.</b> A hull bears on a strip just past its nose ({@link #bearingWidth}): the hull's own
 * width for a hull crushing by weight, which has nothing wider than itself to push with, so the gap it cuts is its own
 * width; the painted blade's ({@link #BLADE_WIDTH_CELLS}) for the dozer.
 *
 * <p><b>The rate.</b> The hull spends its rate ({@link #rate}: {@link #HEAVY_PER_SECOND} for a heavy hull,
 * {@link #ULTRA_HEAVY_PER_SECOND} for an ultra-heavy one, the blade's {@link #PUSH_PER_SECOND}) of structure per second on every pushable cell it bears on at once, through
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
 * it down. Pushable cells cost its route {@link #ROUTE_COST}, so it breaches only where going round is dearer.
 */
public final class DozerBlade {

    /** The heaviest structure the blade will push. Ordinary walls and barricades are at or under it. */
    public static final int MAX_PUSH_STRUCTURE = 100;
    /** Structure spent per second on each cell the blade bears on. A full ordinary wall goes in 1.25 s. */
    public static final float PUSH_PER_SECOND = 80f;
    /**
     * The heaviest structure an ultra-heavy hull crushes: ordinary city and gated-housing walls and every barricade,
     * as the blade does, while a fortified wall (150 and up) holds until fire has worn it under, so a fort is still
     * opened by fire and charges first. Its greater mass shows in the momentum it carries through, not in the ceiling.
     */
    public static final int ULTRA_HEAVY_CEILING = 100;
    /** Structure an ultra-heavy hull's weight spends per second on each cell it bears on: the blade's rate. */
    public static final float ULTRA_HEAVY_PER_SECOND = 80f;
    /**
     * The heaviest structure a heavy hull crushes (owner: heavy vehicles break ordinary walls, not fortified ones):
     * the same walls and barricades as an ultra-heavy hull.
     */
    public static final int HEAVY_CEILING = 100;
    /**
     * Structure a heavy hull spends per second on each cell it bears on, slower than an ultra-heavy's: held against a
     * city wall it grinds through in two seconds, not one and a quarter. Its lesser mass carries it less far through
     * a row at speed, too.
     */
    public static final float HEAVY_PER_SECOND = 50f;
    /**
     * How wide the blade bears, cells: the painted blade, wider than the hull behind it, so the gap it cuts is one the
     * hull drives through.
     */
    static final float BLADE_WIDTH_CELLS = 2.5f;
    /** How far past the hull's nose the bearing strip reaches, cells. */
    static final float REACH_CELLS = .6f;
    /** How far back from the nose the bearing strip starts, so a cell already touching the hull is still pushed. */
    static final float OVERLAP_CELLS = .2f;
    /** Cells past where its nose gets to this tick that a hull counts as hitting: the sweep's contact skin. */
    static final float CONTACT_CELLS = .05f;
    /** What a pushable cell costs a crushing hull's route: a ramming hull's barricade price. */
    static final float ROUTE_COST = BarricadeRam.ROUTE_COST;

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
    private HeavyTracks tracks;

    public DozerBlade(NavigationService navigation, MapEditor editor, DoodadService doodads) {
        this.navigation = navigation;
        this.editor = editor;
        this.doodads = doodads;
    }

    /**
     * The heaviest structure {@code type} pushes: {@link #ULTRA_HEAVY_CEILING} or {@link #HEAVY_CEILING} by weight,
     * {@link #MAX_PUSH_STRUCTURE} by a blade, none for a hull that crushes no walls.
     */
    public static int ceiling(VehicleType type) { return ceiling(type.weight(), type.hasBlade()); }

    /** The ceiling of a hull of {@code weight}, with or without a {@code blade}: weight first, then the blade. */
    static int ceiling(VehicleType.Weight weight, boolean blade) {
        if (weight == VehicleType.Weight.ULTRA_HEAVY) return ULTRA_HEAVY_CEILING;
        if (weight.crushesWalls()) return HEAVY_CEILING;
        return blade ? MAX_PUSH_STRUCTURE : 0;
    }

    /** Structure a second {@code type} spends on each cell it bears on; none for a hull that crushes no walls. */
    public static float rate(VehicleType type) { return rate(type.weight(), type.hasBlade()); }

    /** The rate of a hull of {@code weight}, with or without a {@code blade}. */
    static float rate(VehicleType.Weight weight, boolean blade) {
        if (weight == VehicleType.Weight.ULTRA_HEAVY) return ULTRA_HEAVY_PER_SECOND;
        if (weight.crushesWalls()) return HEAVY_PER_SECOND;
        return blade ? PUSH_PER_SECOND : 0f;
    }

    /** How wide {@code type} bears, cells: its blade where it has one, else its own hull. */
    public static float bearingWidth(VehicleType type) {
        return type.hasBlade() ? BLADE_WIDTH_CELLS : type.visualWidthCells;
    }

    /** Whether the blade can push cell {@code (x, y)} down right now. See the class rule. */
    public static boolean canPush(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y) {
        return canPush(grid, topology, doodads, x, y, MAX_PUSH_STRUCTURE);
    }

    /** Whether a hull whose ceiling is {@code most} can push cell {@code (x, y)} down right now. */
    public static boolean canPush(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y,
                                  int most) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)
                || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)
                || grid.hasTag(x, y, NavigationGrid.CellTag.WORKS)) return false;
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
     * A routing copy of {@code grid} with every cell the blade could push open on all sides, as the blade would leave
     * it. The live grid is not touched. Cells only: a closed shared edge stays closed.
     */
    public static NavigationGrid breachView(NavigationGrid grid, CellTopology topology, DoodadService doodads) {
        return breachView(grid, grid, topology, doodads, MAX_PUSH_STRUCTURE);
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

    /** The crush view the dozer's breach view is laid over, for {@link #planningGrid()}. */
    public void setTracks(HeavyTracks tracks) { this.tracks = tracks; }

    /** The dozer's breach view, laid over its tracks' crush view, for scripted beats that drive the dozer. */
    public NavigationGrid planningGrid() {
        return planningGrid(VehicleType.DOZER_BREACHER, tracks != null ? tracks.motionGrid() : navigation.getGrid());
    }

    /**
     * The breach view {@code type} plans over, laid over {@code base}, the grid it moves over (the live grid, its
     * crush view, or a skirt's view). Gun tanks are common and the map moves all battle, so the view is kept, not
     * rebuilt: it catches up with the live grid's change log cell by cell, as the crush view does, and keeps its
     * identity while its base does. A skirt's view is copied afresh when the map moves, so a breach view over one is
     * too. One view is kept per ceiling and kind of base.
     */
    public NavigationGrid planningGrid(VehicleType type, NavigationGrid base) {
        int most = ceiling(type);
        boolean hover = type.isHover();
        for (View view : views) {
            if (view.most == most && view.hover == hover) return view.caughtUp(base);
        }
        View view = new View(most, hover);
        views.add(view);
        return view.caughtUp(base);
    }

    /**
     * The route cost a crushing hull pays: every cell under its ceiling at least {@link #ROUTE_COST}, what a ramming
     * hull pays for a barricade, so it goes round a wall or a barricade on a street where going round is short.
     */
    TerrainCostField costField(VehicleType type, TerrainCostField base) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        int most = ceiling(type);
        return base.withFloor((x, y) -> canPush(grid, topology, doodads, x, y, most), ROUTE_COST);
    }

    /** Whether {@code grid} is a breach view kept and caught up with the live grid: one not over a skirt's view. */
    boolean isKeptView(NavigationGrid grid) {
        for (View view : views) if (!view.hover && view.grid == grid) return true;
        return false;
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

    /** One ceiling's breach view over one kind of base, caught up cell by cell while its base keeps its identity. */
    private final class View {
        final int most;
        final boolean hover;
        NavigationGrid grid;
        NavigationGrid base;
        NavigationGrid of;
        long caughtUp;

        View(int most, boolean hover) {
            this.most = most;
            this.hover = hover;
        }

        NavigationGrid caughtUp(NavigationGrid over) {
            NavigationGrid live = navigation.getGrid();
            CellTopology topology = navigation.getTopology();
            long now = live.changeCount();
            if (grid == null || base != over || of != live || !live.hasCaughtUpFrom(caughtUp)) {
                grid = breachView(over, live, topology, doodads, most);
                base = over;
                of = live;
            } else {
                for (long sequence = caughtUp; sequence < now; sequence++) {
                    int cell = live.changedCellAt(sequence);
                    int x = cell % live.getWidth(), y = cell / live.getWidth();
                    grid.syncRoutingCell(over, cell, 0L);
                    if (!canPush(live, topology, doodads, x, y, most)) continue;
                    grid.setWalkable(x, y, true);
                    grid.openAllEdges(x, y);
                }
            }
            caughtUp = now;
            return grid;
        }
    }
}
