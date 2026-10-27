package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * What a medium or heavy hull drives through: a light barricade (a sandbag, fence or crate piece) its way covers.
 *
 * <p><b>The rule.</b> A cell is <em>rammable</em> when it is closed, holds a registered cover prop
 * ({@link DoodadService#barricadeAt}) with structure above zero and at most {@link #MAX_STRUCTURE}, and is not a
 * fort's or an objective's works ({@link CellTag#WORKS}): those are rammed by nothing, as a Brute's charge rams them
 * rather than bowling them over. A hull of medium weight or more ({@link VehicleType#ramsBarricades}) driving into
 * rammable cells spends its momentum on them ({@link HullMomentum}), as the dozer does on what its nose reaches. Where
 * its way covers all they have left they fall now, each to its destroyed art through the ordinary wall channel, and
 * it drives on at the speed the rest gives: an APC at full speed keeps about nine tenths of its way through one crate,
 * a gun tank barely notices a row. Where its way does not cover them they share it, and the hull is brought up.
 *
 * <p><b>Held.</b> A hull brought up against rammable cells, and held by nothing else, shoves at them at
 * {@link #SHOVE_PER_SECOND} until they give, so a hull that came at a barricade slowly still gets through, slowly. It
 * counts as progress for the hull's stall and wall-contact recovery, as the blade's grinding does.
 *
 * <p><b>The breach view.</b> Each hull plans over the ground it can open: its tracks' crush view with every rammable
 * cell its top speed carries the structure to break opened ({@link #planningGrid}). The view is per hull, by the
 * structure it carries at full way, and kept rather than rebuilt: it catches up with the live grid's change log cell
 * by cell, as the crush view does. Its physical sweep still reads the crush view, where the barricade stands until it
 * is rammed down. Rammable ground costs a route {@link #ROUTE_COST}, so a hull goes round a barricade where going
 * round is cheap and through it where it is not.
 */
public final class BarricadeRam {

    /**
     * Control switch: ramming is on unless it reads {@code false}, when every hull stops at a barricade as it did
     * before. Kept as the standing control for barricaded streets (`convoy-nouns.md`, ramming).
     */
    public static final String PROPERTY = "battle.vehicle.ramBarricades";

    /** The heaviest piece a hull rams: a whole sandbag, fence or crate piece. Works-hardened ones are tougher. */
    public static final int MAX_STRUCTURE = 100;
    /** Structure a held hull shoves off each rammable cell it bears on, per second. */
    public static final float SHOVE_PER_SECOND = 40f;
    /** What a rammable cell costs a route: dearer than rubble, so the hull goes round where that is short. */
    static final float ROUTE_COST = 8f;
    /** Cells past where its nose gets to this tick that a hull counts as hitting: the sweep's contact skin. */
    static final float CONTACT_CELLS = .05f;

    private final NavigationService navigation;
    private final MapEditor editor;
    private final DoodadService doodads;
    private HeavyTracks tracks;
    /** Kept breach views by the structure a hull can break. */
    private final Int2ObjectOpenHashMap<View> views = new Int2ObjectOpenHashMap<>();
    /** Cells struck this tick, as x/y pairs. Host-thread scratch: vehicles are driven serially. */
    private final int[] struck = new int[64];
    private int struckCount;
    private boolean heldElsewhere;

    public BarricadeRam(NavigationService navigation, MapEditor editor, DoodadService doodads) {
        this.navigation = navigation;
        this.editor = editor;
        this.doodads = doodads;
    }

    /** Lays the breach views over the tracks' crush view, the ground the hull's own tracks open. */
    public void setTracks(HeavyTracks tracks) { this.tracks = tracks; }

    /** Whether a ramming hull can break cell {@code (x, y)} at all. See the class rule. */
    public static boolean rammable(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y) {
        return rammable(grid, topology, doodads, x, y, MAX_STRUCTURE);
    }

    /** {@link #rammable(NavigationGrid, CellTopology, DoodadService, int, int)} for a piece of at most {@code most}. */
    static boolean rammable(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y, int most) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y) || grid.hasTag(x, y, CellTag.WORKS)) return false;
        int structure = grid.getWallHp(x, y);
        return structure > 0 && structure <= most && doodads != null && doodads.barricadeAt(x, y, topology);
    }

    /** The heaviest piece {@code type} plans to ram: what its top speed carries, up to {@link #MAX_STRUCTURE}. */
    static int reach(VehicleType type) {
        return (int) Math.min(MAX_STRUCTURE, HullMomentum.structure(type.massTonnes(), type.maxSpeed));
    }

    /** The breach view {@code type} plans over, kept and caught up with the live grid. See the class rule. */
    public NavigationGrid planningGrid(VehicleType type) {
        int most = reach(type);
        View view = views.get(most);
        if (view == null) views.put(most, view = new View(most));
        return view.caughtUp();
    }

    /** Whether {@code grid} is one of the kept breach views. */
    boolean isView(NavigationGrid grid) {
        for (View view : views.values()) if (view.grid == grid) return true;
        return false;
    }

    /** The route cost a ramming hull pays: rammable cells at {@link #ROUTE_COST}. */
    TerrainCostField costField(VehicleType type, TerrainCostField base) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        int most = reach(type);
        return base.withFloor((x, y) -> rammable(grid, topology, doodads, x, y, most), ROUTE_COST);
    }

    /**
     * The hull's momentum spent on the rammable cells its footprint reaches at {@code body}'s pose, the pose its move
     * this tick would take it to. Where its way covers all they have left they fall and it keeps the rest; where it
     * does not they share it and it is brought up.
     *
     * @return how many cells fell
     */
    public int strike(GroundBody body, VehicleType type) {
        if (editor == null) return 0;
        float tonnes = type.massTonnes();
        float impact = HullMomentum.structure(tonnes, Math.abs(body.speed));
        if (impact <= 0f) return 0;
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        struckCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells + CONTACT_CELLS * 2f, type.visualWidthCells, grid, (x, y) -> {
                    if (!rammable(grid, topology, doodads, x, y) || struckCount * 2 >= struck.length) return;
                    struck[struckCount * 2] = x;
                    struck[struckCount * 2 + 1] = y;
                    struckCount++;
                });
        if (struckCount == 0) return 0;
        int standing = 0;
        for (int i = 0; i < struckCount; i++) standing += grid.getWallHp(struck[i * 2], struck[i * 2 + 1]);
        if (impact >= standing) {
            for (int i = 0; i < struckCount; i++) {
                int x = struck[i * 2], y = struck[i * 2 + 1];
                editor.damageWall(x, y, grid.getWallHp(x, y));
            }
            body.speed = Math.copySign(HullMomentum.speedAfter(tonnes, Math.abs(body.speed), standing), body.speed);
            return struckCount;
        }
        int share = (int) (impact / struckCount);
        if (share > 0) for (int i = 0; i < struckCount; i++) editor.damageWall(struck[i * 2], struck[i * 2 + 1], share);
        body.speed = 0f;
        return 0;
    }

    /**
     * One tick of a hull held where it means to move: at {@code body}'s pose its footprint overlaps blocking cells.
     * Where every one is rammable it shoves at them at {@link #SHOVE_PER_SECOND}.
     *
     * @return whether it bore on rammable cells and on nothing else — progress, as the blade's grinding is
     */
    public boolean shove(GroundBody body, VehicleType type, VehicleControlComponent control, float dt) {
        if (editor == null) return false;
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        struckCount = 0;
        heldElsewhere = false;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells, type.visualWidthCells, grid, (x, y) -> {
                    if (grid.isWalkable(x, y)) return;
                    if (!rammable(grid, topology, doodads, x, y) || struckCount * 2 >= struck.length) {
                        heldElsewhere = true;
                        return;
                    }
                    struck[struckCount * 2] = x;
                    struck[struckCount * 2 + 1] = y;
                    struckCount++;
                });
        if (heldElsewhere || struckCount == 0) return false;
        float pending = control.bladeCarry + SHOVE_PER_SECOND * dt;
        int spend = (int) pending;
        control.bladeCarry = pending - spend;
        if (spend > 0) for (int i = 0; i < struckCount; i++) editor.damageWall(struck[i * 2], struck[i * 2 + 1], spend);
        return true;
    }

    /** One hull class's breach view: the crush view with every cell it can ram open, caught up cell by cell. */
    private final class View {
        final int most;
        NavigationGrid grid;
        NavigationGrid of;
        long caughtUp;

        View(int most) { this.most = most; }

        NavigationGrid caughtUp() {
            NavigationGrid live = navigation.getGrid();
            NavigationGrid base = tracks != null ? tracks.motionGrid() : live;
            CellTopology topology = navigation.getTopology();
            long now = live.changeCount();
            if (grid == null || of != live || !live.hasCaughtUpFrom(caughtUp)) {
                grid = base.copyRoutingTopology();
                of = live;
                for (int y = 0; y < live.getHeight(); y++) {
                    for (int x = 0; x < live.getWidth(); x++) open(live, topology, x, y);
                }
            } else {
                for (long sequence = caughtUp; sequence < now; sequence++) {
                    int cell = live.changedCellAt(sequence);
                    int x = cell % live.getWidth(), y = cell / live.getWidth();
                    grid.syncRoutingCell(base, cell, 0L);
                    open(live, topology, x, y);
                }
            }
            caughtUp = now;
            return grid;
        }

        private void open(NavigationGrid live, CellTopology topology, int x, int y) {
            if (!rammable(live, topology, doodads, x, y, most)) return;
            grid.setWalkable(x, y, true);
            grid.openAllEdges(x, y);
        }
    }
}
