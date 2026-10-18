package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * What a hover hull's skirt lets it cross, and the view of the map its planning <em>and</em> its motion read.
 *
 * <p><b>The rule.</b> A hover hull ({@link VehicleType#isHover()}) floats over open water: a cell whose ground is
 * {@link GroundKind#WATER} and is otherwise only closed because it is water is ordinary going for it. It rides over
 * rubble and low crash debris at full speed, because nothing under the skirt is in contact with it. Nothing else
 * opens: walls, natural obstructions, barricades and every closed edge between two dry cells stay exactly as they are,
 * so low cover still stops it.
 *
 * <p><b>Why both planning and motion.</b> The dozer's breach view ({@link DozerBlade#breachView}) is read only by
 * route proof, and its sweep reads the live grid, because the wall really is there until the blade pushes it down.
 * Water really is crossable for a hover hull, so its rolling planner, its recovery and its collision sweep all read
 * this view; route proof that the sweep then refused would leave the hull holding at the shore.
 *
 * <p>Nobody else gains the cells: infantry, wheels and tracks read the live grid. The convoy's dispatched delivery
 * proof is unchanged; the skirt reaches player-ordered and armor-dropped hulls.
 */
public final class HoverSkirt {

    private final NavigationService navigation;
    private NavigationGrid view;
    private long viewRevision = Long.MIN_VALUE;

    public HoverSkirt(NavigationService navigation) {
        this.navigation = navigation;
    }

    /**
     * A routing copy of {@code grid} with every water cell open on all sides and every cell's crash-debris slowing
     * tag cleared. The live grid is not touched.
     */
    public static NavigationGrid hoverView(NavigationGrid grid, CellTopology topology) {
        NavigationGrid copy = grid.copyRoutingTopology();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                copy.setTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS, false);
                if (!floats(grid, topology, x, y)) continue;
                copy.setWalkable(x, y, true);
                copy.openAllEdges(x, y);
            }
        }
        return copy;
    }

    /** Whether a skirt floats over this cell where nothing else may stand: open water and nothing on it. */
    public static boolean floats(NavigationGrid grid, CellTopology topology, int x, int y) {
        return grid.inBounds(x, y) && !grid.isWalkable(x, y)
                && topology.getGroundKind(x, y) == GroundKind.WATER
                && !grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID);
    }

    /** The hover view of the live map, rebuilt when the map's topology has moved since it was last asked for. */
    public NavigationGrid motionGrid() {
        NavigationGrid grid = navigation.getGrid();
        long revision = grid.topologyRevision();
        if (view == null || revision != viewRevision) {
            view = hoverView(grid, navigation.getTopology());
            viewRevision = revision;
        }
        return view;
    }

    /**
     * The route cost a hover hull pays: water and rubble cost what open ground does, since the skirt neither wades
     * nor bumps; every other kind costs what it costs a wheel.
     */
    public static TerrainCostField costField(CellTopology topology) {
        return TerrainCostField.from(topology, HoverSkirt::cost);
    }

    static float cost(GroundKind kind) {
        return kind == GroundKind.WATER || kind == GroundKind.RUBBLE
                ? TerrainCostField.COST_OPEN_GROUND : TerrainCostField.costFor(kind);
    }
}
