package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;

/**
 * The dozer breacher's blade: what it can push through, how fast, and the view of the map its route proof reads.
 *
 * <p><b>The rule.</b> A blocking cell is <em>pushable</em> when its structure is spent through the ordinary wall
 * channel — a wall cell, or a sandbag, fence or crate barricade — and that structure is above zero and no more than
 * {@link #MAX_PUSH_STRUCTURE}. Ordinary city walls (100) and gated-housing walls (80) fall to it; fortified and
 * fortress walls (150 and up) do not, until fire has worn them under the threshold. Natural obstructions (rock,
 * trees, summit terrain), registered equipment and entity-owned fixtures (a turret's mount) are never pushed. Low
 * crash debris under the blade is graded flat: the cell keeps its terrain and loses the slowing tag.
 *
 * <p><b>The rate.</b> The blade spends {@link #PUSH_PER_SECOND} structure per second on every pushable cell it bears
 * on at once, through {@link MapEditor#damageWall} — the same destruction path a breaching charge uses. A pushed wall
 * becomes rubble and a pushed barricade its destroyed art, and the cell is ordinary walkable ground for every vehicle
 * and unit from then on. Opening is the only direction it edits: like every destruction op, it can only make the map
 * more permissive, so nothing derived from it is invalidated beyond the ordinary opened-cell flush.
 *
 * <p><b>Momentum.</b> A hull driving onto what it pushes spends its way on the cells its nose reaches that tick
 * ({@link HullMomentum}). Where the structure its speed carries and this tick's push cover all those cells have left,
 * they fall at once and the hull drives on at the speed the rest of its energy gives; where they do not, they share
 * its momentum and the hull is brought up against them, for the blade to push down at its rate from a standstill.
 *
 * <p><b>Route proof.</b> {@link #breachView} is a routing copy of the grid with every pushable cell open. A bladed
 * hull's move-order proof and its rolling local planner read that view, so ground the dozer can open counts as
 * reachable for the dozer and for nobody else; its physical sweep still reads the live grid, which is what holds the
 * hull against a wall until the blade has pushed it down. The dozer is a heavy-lift hull, so where it has tracks the
 * view is laid over their crush view ({@link HeavyTracks}), and its sweep reads that.
 */
public final class DozerBlade {

    /** The heaviest structure the blade will push. Ordinary walls and barricades are at or under it. */
    public static final int MAX_PUSH_STRUCTURE = 100;
    /** Structure spent per second on each cell the blade bears on. A full ordinary wall goes in 1.25 s. */
    public static final float PUSH_PER_SECOND = 80f;
    /** How far past the hull's nose the blade bears, cells. */
    static final float REACH_CELLS = .6f;
    /** How far back from the nose the bearing strip starts, so a cell already touching the hull is still pushed. */
    static final float OVERLAP_CELLS = .2f;
    /** Cells past where its nose gets to this tick that a hull counts as hitting: the sweep's contact skin. */
    static final float CONTACT_CELLS = .05f;

    private final NavigationService navigation;
    private final MapEditor editor;
    private final DoodadService doodads;
    /** Cells the blade bears on this tick, as x/y pairs. Host-thread scratch: vehicles are driven serially. */
    private final int[] bearing = new int[64];
    private int bearingCount;
    /** Cells the hull's nose hits this tick, as x/y pairs. */
    private final int[] struck = new int[64];
    private int struckCount;
    private HeavyTracks tracks;
    private NavigationGrid view;
    private long viewRevision = Long.MIN_VALUE;

    public DozerBlade(NavigationService navigation, MapEditor editor, DoodadService doodads) {
        this.navigation = navigation;
        this.editor = editor;
        this.doodads = doodads;
    }

    /** Lays the breach view over the tracks' crush view, the ground the dozer's own tracks open. */
    public void setTracks(HeavyTracks tracks) { this.tracks = tracks; }

    /** Whether the blade can push cell {@code (x, y)} down right now. See the class rule. */
    public static boolean canPush(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)
                || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) return false;
        int structure = grid.getWallHp(x, y);
        if (structure <= 0 || structure > MAX_PUSH_STRUCTURE) return false;
        if (doodads != null && doodads.damageablePropIdAt(x, y, topology) != null) {
            return doodads.barricadeAt(x, y, topology);
        }
        return topology.isWall(x, y) && !topology.isFixture(x, y);
    }

    /** Whether the blade grades cell {@code (x, y)}: low crash debris on open ground. */
    public static boolean grades(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y)
                && grid.hasTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS);
    }

    /**
     * A routing copy of {@code grid} with every pushable cell open on all sides, as the blade would leave it. The
     * live grid is not touched. Cells only: a closed shared edge stays closed.
     */
    public static NavigationGrid breachView(NavigationGrid grid, CellTopology topology, DoodadService doodads) {
        return breachView(grid, grid, topology, doodads);
    }

    /** {@link #breachView(NavigationGrid, CellTopology, DoodadService)} laid over {@code base}, a view of {@code grid}. */
    static NavigationGrid breachView(NavigationGrid base, NavigationGrid grid, CellTopology topology,
                                     DoodadService doodads) {
        NavigationGrid copy = base.copyRoutingTopology();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!canPush(grid, topology, doodads, x, y)) continue;
                copy.setWalkable(x, y, true);
                copy.openAllEdges(x, y);
            }
        }
        return copy;
    }

    /** The breach view of the live map, rebuilt when the map's topology has moved since it was last asked for. */
    public NavigationGrid planningGrid() {
        NavigationGrid grid = navigation.getGrid();
        long revision = grid.topologyRevision();
        if (view == null || revision != viewRevision) {
            NavigationGrid base = tracks != null ? tracks.motionGrid() : grid;
            view = breachView(base, grid, navigation.getTopology(), doodads);
            viewRevision = revision;
        }
        return view;
    }

    /**
     * One tick of the blade bearing ahead of a hull at {@code body}'s pose: grades the debris under the hull and the
     * strip, and spends this tick's push on every pushable cell in the strip.
     *
     * @return whether the blade bore on at least one pushable cell — work that counts as progress for the hull's
     *         stall and wall-contact recovery
     */
    public boolean push(GroundBody body, VehicleType type, VehicleControlComponent control, float dt) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        float halfL = type.visualLengthCells * .5f;
        float stripLength = REACH_CELLS + OVERLAP_CELLS;
        float stripOffset = halfL - OVERLAP_CELLS + stripLength * .5f;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells, type.visualWidthCells, grid, (x, y) -> {
                    if (grades(grid, x, y)) grid.setTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS, false);
                });
        bearingCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, stripOffset,
                stripLength, type.visualWidthCells, grid, (x, y) -> {
                    if (grades(grid, x, y)) grid.setTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS, false);
                    if (!canPush(grid, topology, doodads, x, y)) return;
                    if (bearingCount * 2 >= bearing.length) return;
                    bearing[bearingCount * 2] = x;
                    bearing[bearingCount * 2 + 1] = y;
                    bearingCount++;
                });
        if (bearingCount == 0) {
            control.bladeCarry = 0f;
            return false;
        }
        float pending = control.bladeCarry + PUSH_PER_SECOND * dt;
        int spend = (int) pending;
        control.bladeCarry = pending - spend;
        strike(body, type, grid, topology, spend, dt);
        if (spend > 0) {
            for (int i = 0; i < bearingCount; i++) editor.damageWall(bearing[i * 2], bearing[i * 2 + 1], spend);
        }
        return true;
    }

    /**
     * The hull's momentum spent on what its nose reaches this tick: the pushable cells within the way it moves, not
     * the whole strip the blade bears on, so a cell beside its path that it never hits does not stop it. Where its
     * momentum and {@code spend} cover all those cells have left they fall now and it drives on at the speed the rest
     * gives; where they do not, they share its momentum and it is brought up against them.
     */
    private void strike(GroundBody body, VehicleType type, NavigationGrid grid, CellTopology topology, int spend,
                        float dt) {
        float tonnes = type.massTonnes();
        float impact = HullMomentum.structure(tonnes, body.speed);
        if (impact <= 0f) return;
        float reach = OVERLAP_CELLS + body.speed * dt + CONTACT_CELLS;
        struckCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees,
                type.visualLengthCells * .5f - OVERLAP_CELLS + reach * .5f, reach, type.visualWidthCells, grid,
                (x, y) -> {
                    if (!canPush(grid, topology, doodads, x, y) || struckCount * 2 >= struck.length) return;
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
}
