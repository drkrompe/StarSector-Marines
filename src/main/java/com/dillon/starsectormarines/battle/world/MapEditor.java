package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Coordinates the runtime map-modification cycle — the cross-domain operations
 * that fire when the battlefield changes shape mid-battle: a wall breached by a
 * detonation, a roof cracked open above it, a destroyed turret-mount or drone
 * hub flipped to walkable rubble.
 *
 * <p>Almost all of it is destruction, which is the easy direction: the grid
 * only ever becomes more permissive, so nothing derived from it can be
 * invalidated by the change. The single construction op,
 * {@link #placeDeployedBarrier}, carries the rule that makes construction
 * safe — see its contract.
 *
 * <p>Each op is inherently cross-domain — it touches navigation walkability,
 * {@link CellTopology} state, and (for roof cave-ins) a decal FX sink — so
 * neither {@link NavigationService} nor {@link CellTopology} is the natural
 * owner of the whole sequence. MapEditor is the thin coordinator that
 * sequences each domain's slice: it mutates topology directly (CellTopology
 * stays a data holder) and delegates walkability plus derived-navigation
 * invalidation to
 * the navigation service ({@code grid.*} + {@link NavigationService#markNavigationTopologyDirty()}).
 *
 * <p>The {@link #roofCollapseSink} (a rubble-decal effect, not topology and
 * not navigation) lives here because it's the cross-cutting glue this
 * coordinator is the right home for. Setter-injected at sim construction.
 *
 * <p>Owns no component state of its own — the {@code grid}/{@code topology}
 * fields are aliases of state the {@link NavigationService} owns. It is a
 * stateless cross-domain mutation coordinator, hence {@code MapEditor}, not
 * {@code *Service}, under the Service(data-owner)/System(processor) convention.
 *
 * <p>Sits alongside the {@link NavigationService},
 * {@link com.dillon.starsectormarines.battle.combat.DamageService},
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService}, et al.
 * Runtime modification is its complete boundary; generation orchestration stays
 * separate. See {@code ecs-nouns.md}.
 */
public final class MapEditor {

    private final NavigationService navigation;
    /** Walkability + cover layer. Aliased from {@link NavigationService#getGrid()} so the sequenced walkability writes stay direct. */
    private final NavigationGrid grid;
    /** Per-cell topology data (walls, ground kinds, roof state). Aliased from {@link NavigationService#getTopology()}; MapEditor is the only behavior owner that mutates it. */
    private final CellTopology topology;

    public MapEditor(NavigationService navigation) {
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
     * clears the WALL/window tags + flips the ground to
     * {@link CellTopology.GroundKind#RUBBLE},
     * cracks the four adjacent roof cells open, and marks the zone graph dirty
     * so the new portal is picked up at tick end. Returns {@code false} (no-op)
     * when the cell wasn't a breachable wall.
     */
    public boolean damageWall(int x, int y, int amount) {
        if (!grid.damageCell(x, y, amount)) return false;
        topology.setWall(x, y, false);
        topology.setWindow(x, y, false);
        grid.setSeeThrough(x, y, false);
        topology.setGroundKind(x, y, CellTopology.GroundKind.RUBBLE);
        peelRoofAround(x, y);
        navigation.markCellOpened(x, y);
        return true;
    }

    /**
     * Damages one shared-edge feature. Destruction removes its
     * presentation/cover identity first, then — for a profile that had closed
     * its transition — opens the owned edge through the navigation service so
     * every derived topology layer advances at the ordinary batched flush
     * boundary. A profile that never blocked movement skips that flush
     * entirely: there is no closed edge to reopen, and asking for a zone,
     * mesh, and retained-path rebuild that changes nothing is pure cost.
     */
    public boolean damageEdgeBarrier(int x, int y, Direction direction,
                                     int amount) {
        SharedEdgeBarrier barrier = grid.getEdgeBarrier(x, y, direction);
        if (barrier == null) return false;
        boolean blockedMovement = barrier.kind().blocksMovement();
        if (!grid.damageEdgeBarrier(x, y, direction, amount)) return false;
        if (blockedMovement) {
            navigation.openSharedEdge(
                    barrier.cellX(), barrier.cellY(), barrier.direction());
        }
        return true;
    }

    /**
     * The runtime construction seam, and the whole of it.
     *
     * <p>Every other operation on this coordinator only ever makes the world
     * <em>more</em> permissive: a breached wall stays walkable, a demolished
     * mount becomes rubble, a broken barrier opens its edge. That asymmetry is
     * what lets destruction be cheap — a path that was valid stays valid, a
     * zone that was connected stays connected, and a unit standing anywhere
     * legal is still standing somewhere legal. Construction has no such
     * guarantee for free: closing a transition under existing paths can strand
     * a unit and invalidate a route a squad is halfway through.
     *
     * <p>So construction is admitted on exactly one condition, enforced here
     * rather than trusted to callers: <b>a runtime-placed feature may not make
     * the navigation grid less permissive.</b> A profile that
     * {@linkplain SharedEdgeBarrier.Kind#blocksMovement blocks movement} is
     * refused outright. What remains — cover, presentation, a structure pool
     * that explosions can deplete — is invisible to walkability, to zones, to
     * the navigation mesh, and to retained paths, so nothing derived needs
     * invalidating and no flush is required.
     *
     * <p>Returns {@code null} when the edge cannot take a feature right now
     * (off-map, either side unwalkable, transition already closed, or already
     * occupied by another feature). A live placement declines; it does not
     * throw.
     */
    public SharedEdgeBarrier placeDeployedBarrier(int x, int y, Direction direction,
                                                  SharedEdgeBarrier.Kind kind) {
        if (kind == null) return null;
        if (kind.blocksMovement()) {
            throw new IllegalArgumentException("Runtime construction may not close a"
                    + " navigation transition; barrier kind '" + kind
                    + "' blocks movement and is generation-only");
        }
        return grid.tryPlaceEdgeBarrier(x, y, direction, kind);
    }

    /**
     * Removes a runtime-placed feature that ran out its clock, by spending its
     * whole structure pool through the ordinary destruction path. Expiry and
     * being blown apart therefore leave the map in exactly the same state —
     * there is one removal path, not two.
     */
    public boolean retireDeployedBarrier(SharedEdgeBarrier barrier) {
        if (barrier == null) return false;
        return damageEdgeBarrier(barrier.cellX(), barrier.cellY(),
                barrier.direction(), barrier.maxStructure());
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
    /**
     * Whether {@code (cellX, cellY)} is currently closed to movement. The
     * demolition handlers read this before flipping a dead emplacement's cell
     * to rubble: a map turret or drone hub was stamped onto a sealed cell at
     * setup and leaving it sealed would orphan an invisible obstacle, whereas a
     * carrier-placed emplacement stood on ordinary floor and never sealed
     * anything. Flipping the latter would open authored edges under existing
     * paths for no reason.
     */
    public boolean isCellSealed(int cellX, int cellY) {
        return !grid.isWalkable(cellX, cellY);
    }

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
