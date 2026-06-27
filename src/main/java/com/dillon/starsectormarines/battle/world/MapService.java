package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Owns the runtime map-modification cycle — the cross-domain operations that
 * fire when the battlefield changes shape mid-battle: a wall breached by a
 * detonation, a roof cracked open above it, a destroyed turret-mount or drone
 * hub flipped to walkable rubble.
 *
 * <p>Each op is inherently cross-domain — it touches navigation walkability,
 * {@link CellTopology} state, and (for roof cave-ins) a decal FX sink — so
 * neither {@link NavigationService} nor {@link CellTopology} is the natural
 * owner of the whole sequence. MapService is the thin coordinator that
 * sequences each domain's slice: it mutates topology directly (CellTopology
 * stays a data holder) and delegates the walkability + zone-graph writes to
 * the navigation service ({@code grid.*} + {@link NavigationService#markZoneGraphDirty()}).
 *
 * <p>The {@link #roofCollapseSink} (a rubble-decal effect, not topology and
 * not navigation) lives here because it's the cross-cutting glue this service
 * is the right home for. Setter-injected at sim construction.
 *
 * <p>Sibling service to {@link NavigationService},
 * {@link com.dillon.starsectormarines.battle.combat.DamageService},
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService}, et al.
 * Slice 1 of the map-service-coordinator story owns runtime modification;
 * generation orchestration is a later slice.
 */
public final class MapService {

    private final NavigationService navigation;
    /** Walkability + cover layer. Aliased from {@link NavigationService#getGrid()} so the sequenced walkability writes stay direct. */
    private final NavigationGrid grid;
    /** Per-cell topology data (walls, ground kinds, roof state). Aliased from {@link NavigationService#getTopology()}; MapService is the only behavior owner that mutates it. */
    private final CellTopology topology;

    public MapService(NavigationService navigation) {
        this.navigation = navigation;
        this.grid = navigation.getGrid();
        this.topology = navigation.getTopology();
    }

    @FunctionalInterface
    public interface CellCallback {
        void accept(int x, int y);
    }

    private CellCallback roofCollapseSink;

    public void setRoofCollapseSink(CellCallback sink) { this.roofCollapseSink = sink; }

    /**
     * Breaches a wall cell: opens grid walkability ({@code grid.damageCell}),
     * clears the WALL tag + flips the ground to {@link CellTopology.GroundKind#RUBBLE},
     * cracks the four adjacent roof cells open, and marks the zone graph dirty
     * so the new portal is picked up at tick end. Returns {@code false} (no-op)
     * when the cell wasn't a breachable wall.
     */
    public boolean damageWall(int x, int y, int amount) {
        if (!grid.damageCell(x, y, amount)) return false;
        topology.setWall(x, y, false);
        topology.setGroundKind(x, y, CellTopology.GroundKind.RUBBLE);
        peelRoofAround(x, y);
        navigation.markCellOpened(x, y);
        return true;
    }

    private void peelRoofAround(int wallX, int wallY) {
        destroyRoof(wallX - 1, wallY);
        destroyRoof(wallX + 1, wallY);
        destroyRoof(wallX, wallY - 1);
        destroyRoof(wallX, wallY + 1);
    }

    /**
     * Cracks open the roof above a building cell — flips
     * {@link CellTopology#setRoofDestroyed} and fires the
     * {@link #roofCollapseSink} cave-in decal. No-op off-map, on non-building
     * cells, or where the roof is already gone.
     */
    public void destroyRoof(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        if (topology.getBuildingId(x, y) == 0) return;
        if (topology.isRoofDestroyed(x, y)) return;
        topology.setRoofDestroyed(x, y, true);
        if (roofCollapseSink != null) roofCollapseSink.accept(x, y);
    }

    /**
     * Flips a dead structure cell (destroyed turret mount, demolished drone
     * hub) to walkable rubble: opens the cell + all four edges, sets the
     * topology to {@link CellTopology.GroundKind#RUBBLE}, recomputes cover
     * on the cell and its four cardinal neighbors, and marks the zone graph
     * dirty so the next end-of-tick rebuild picks up the new portal. Sibling
     * to the wall-collapse path inside {@link #damageWall} — same intent
     * ("obstacle removed, stamp rubble, refresh navigation") for the non-wall
     * obstacle kinds.
     */
    public void flipCellToRubble(int cellX, int cellY) {
        grid.setWalkable(cellX, cellY, true);
        grid.openAllEdges(cellX, cellY);
        topology.setGroundKind(cellX, cellY, CellTopology.GroundKind.RUBBLE);
        grid.recomputeCoverAt(cellX, cellY);
        grid.recomputeCoverAt(cellX + 1, cellY);
        grid.recomputeCoverAt(cellX - 1, cellY);
        grid.recomputeCoverAt(cellX, cellY + 1);
        grid.recomputeCoverAt(cellX, cellY - 1);
        navigation.markCellOpened(cellX, cellY);
    }
}
