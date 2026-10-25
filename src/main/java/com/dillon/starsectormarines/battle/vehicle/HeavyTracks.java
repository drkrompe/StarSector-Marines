package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;

/**
 * What a medium or heavy hull flattens, and the view of the map its planning <em>and</em> its motion read.
 *
 * <p><b>The rule.</b> A cell is <em>crushable</em> when it is closed and carries {@link CellTag#CRUSHABLE}:
 * undergrowth (a young tree, or any tree the creep has killed, wherever no other living tree's crown holds the cell)
 * or a lone boulder. Infantry and light hulls are stopped by it as by any natural solid. A medium or heavy hull
 * ({@link VehicleType#crushesUndergrowth()}) drives into it: every crushable cell under its footprint after a move is
 * crushed through {@link MapEditor#crush}, the same destruction path a shell takes, so a tree falls whole to its
 * felled art and a boulder to its rubble, and the ground is open for everyone afterwards. Crushing costs the hull its
 * way: a tick that crushes anything leaves it at no more than {@link #CRUSH_SPEED_FRACTION} of its top speed.
 *
 * <p><b>Why both planning and motion.</b> Unlike the dozer's wall, which really is there until the blade has worn it
 * down ({@link DozerBlade}), undergrowth gives at once, so the hull's route proof, rolling planner, recovery and
 * collision sweep all read {@link #motionGrid()}, as a hover hull's read its skirt's view ({@link HoverSkirt}).
 *
 * <p><b>The view is kept, not rebuilt.</b> A hover hull is rare and its view is copied afresh whenever the map moves;
 * a heavy tank is common and the map moves all battle, and a fresh copy is a fresh grid, which costs every derivation
 * keyed on it (the clearance mask, above all) a whole-map pass. So the view keeps its identity and catches up with
 * the live grid's change log cell by cell, and the masks over it catch up from the view's own log in turn.
 */
public final class HeavyTracks {

    /**
     * Control switch: crushing is on unless it reads {@code false}, when every hull stops at undergrowth as it did
     * before. Kept as the standing control for forest drives (`convoy-nouns.md`, heavy tracks).
     */
    public static final String PROPERTY = "battle.vehicle.crushUndergrowth";

    /** The share of top speed a hull keeps on a tick it crushes something. */
    public static final float CRUSH_SPEED_FRACTION = .5f;

    private final NavigationService navigation;
    private final MapEditor editor;
    /** Cells to crush this tick, as x/y pairs. Host-thread scratch: vehicles are driven serially. */
    private final int[] crushing = new int[128];
    private int crushingCount;
    private NavigationGrid view;
    private NavigationGrid viewOf;
    private long caughtUp;

    /** {@code editor} may be null where nothing can be crushed (bare control tests): the hull then only plans. */
    public HeavyTracks(NavigationService navigation, MapEditor editor) {
        this.navigation = navigation;
        this.editor = editor;
    }

    /** Whether a medium or heavy hull flattens cell {@code (x, y)} by driving into it. See the class rule. */
    public static boolean crushes(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && !grid.isWalkable(x, y) && grid.hasTag(x, y, CellTag.CRUSHABLE);
    }

    /**
     * A routing copy of {@code grid} with every crushable cell open, as the tracks would leave it. The live grid is
     * not touched. Cells only: a closed shared edge stays closed.
     */
    public static NavigationGrid crushView(NavigationGrid grid) {
        NavigationGrid copy = grid.copyRoutingTopology();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (crushes(grid, x, y)) copy.setWalkable(x, y, true);
            }
        }
        return copy;
    }

    /** The crush view of the live map, caught up with every change the live grid has logged since it was last asked. */
    public NavigationGrid motionGrid() {
        NavigationGrid grid = navigation.getGrid();
        long now = grid.changeCount();
        if (view == null || viewOf != grid || !grid.hasCaughtUpFrom(caughtUp)) {
            view = crushView(grid);
            viewOf = grid;
        } else {
            for (long sequence = caughtUp; sequence < now; sequence++) {
                int cell = grid.changedCellAt(sequence);
                int x = cell % grid.getWidth(), y = cell / grid.getWidth();
                view.syncRoutingCell(grid, cell, crushes(grid, x, y) ? CellTag.WALKABLE.mask() : 0L);
            }
        }
        caughtUp = now;
        return view;
    }

    /** The view as last caught up, or {@code null} before anything asked for it. */
    NavigationGrid currentView() { return view; }

    /**
     * Crushes every crushable cell under {@code body}'s footprint as it stands now, and holds the hull to
     * {@link #CRUSH_SPEED_FRACTION} of its top speed if anything gave.
     *
     * @return how many cells were crushed
     */
    public int crush(GroundBody body, VehicleType type) {
        if (editor == null) return 0;
        NavigationGrid grid = navigation.getGrid();
        crushingCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells, type.visualWidthCells, grid, (x, y) -> {
                    if (!crushes(grid, x, y) || crushingCount * 2 >= crushing.length) return;
                    crushing[crushingCount * 2] = x;
                    crushing[crushingCount * 2 + 1] = y;
                    crushingCount++;
                });
        int crushed = 0;
        for (int i = 0; i < crushingCount; i++) {
            if (editor.crush(crushing[i * 2], crushing[i * 2 + 1])) crushed++;
        }
        if (crushed > 0) {
            float cap = type.maxSpeed * CRUSH_SPEED_FRACTION;
            body.speed = Math.copySign(Math.min(Math.abs(body.speed), cap), body.speed);
        }
        return crushed;
    }

    /**
     * The route cost a crushing hull pays: what a wheel pays, but crushable ground costs what rubble does, so the
     * hull goes round a thicket where going round is cheap and through it where it is not.
     */
    public static TerrainCostField costField(NavigationGrid grid, TerrainCostField base) {
        return base.withFloor(grid, CellTag.CRUSHABLE, TerrainCostField.COST_RUBBLE);
    }
}
