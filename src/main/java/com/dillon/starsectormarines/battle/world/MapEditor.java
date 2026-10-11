package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.model.TreeDamage;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;

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
    private DoodadService doodads;
    private long structuralAttack;
    private final TreeDamage.ClearedCell treeCellCleared = this::clearTreeCell;

    public void setDoodads(DoodadService doodads) { this.doodads = doodads; }

    public void setRoofCollapseSink(CellCallback sink) { this.roofCollapseSink = sink; }

    /**
     * Breaches a wall cell: opens grid walkability ({@code grid.damageCell}),
     * clears the WALL/window tags + flips the ground to
     * {@link CellTopology.GroundKind#RUBBLE},
     * cracks the four adjacent roof cells open, and marks the zone graph dirty
     * so the new portal is picked up at tick end. Returns {@code false} (no-op)
     * when the cell wasn't breached. Registered fences and sandbags use the same
     * entry point, retaining the ground and replacing their prop with damaged
     * art or debris; they do not become masonry rubble or roof-collapse sources.
     * Trees resolve one whole-crown structure pool before the cell-wall path.
     * Nature rocks spend per-cell structure, removing their cover and leaving low rubble.
     */
    public boolean damageWall(int x, int y, int amount) {
        return damageWall(x, y, amount, ++structuralAttack);
    }

    /** One blast spends each tree's structure once, regardless of canopy size or cell visitation order. */
    public void damageWallsInRadius(float centerX, float centerY, float radius, int amount, CellCallback broken) {
        long attack = ++structuralAttack;
        int minX = Math.max(0, (int)Math.floor(centerX - radius));
        int maxX = Math.min(grid.getWidth() - 1, (int)Math.floor(centerX + radius));
        int minY = Math.max(0, (int)Math.floor(centerY - radius));
        int maxY = Math.min(grid.getHeight() - 1, (int)Math.floor(centerY + radius));
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            float dx = x + .5f - centerX, dy = y + .5f - centerY;
            if (dx * dx + dy * dy > radius * radius) continue;
            if (damageWall(x, y, amount, attack) && broken != null) broken.accept(x, y);
        }
    }

    private boolean damageWall(int x, int y, int amount, long attack) {
        if (doodads != null) {
            RockDamage.Result rock = doodads.damageRock(x, y, amount, topology);
            if (rock == RockDamage.Result.DESTROYED) {
                if (!grid.isWalkable(x, y)) {
                    grid.setWalkable(x, y, true);
                    grid.setSeeThrough(x, y, false);
                    grid.setDoorway(x, y, false);
                    grid.setWallHp(x, y, 0);
                    grid.openAllEdges(x, y);
                    topology.setFixture(x, y, false);
                    grid.recomputeCoverAt(x, y);
                    grid.recomputeCoverAt(x - 1, y); grid.recomputeCoverAt(x + 1, y);
                    grid.recomputeCoverAt(x, y - 1); grid.recomputeCoverAt(x, y + 1);
                    doodads.refreshBlockingCover(x, y, topology);
                    navigation.markCellOpened(x, y);
                }
                return true;
            }
            if (rock != RockDamage.Result.NOT_ROCK) return false;
            TreeDamage.Result tree = doodads.damageTree(x, y, amount, attack, treeCellCleared);
            if (tree != TreeDamage.Result.NOT_TREE) return tree == TreeDamage.Result.DESTROYED;
            CoverPropDamage.Result result = doodads.damageCoverProp(x, y, amount);
            if (result == CoverPropDamage.Result.DESTROYED) {
                topology.setFixture(x, y, false);
                // damageCell's wall-breach portal must not turn a cover gap into a doorway.
                grid.setDoorway(x, y, false);
                grid.setSeeThrough(x, y, false);
                doodads.refreshBlockingCover(x, y, topology);
                navigation.markCellOpened(x, y);
                return true;
            }
            if (result != CoverPropDamage.Result.NOT_COVER_PROP) return false;
        }
        if (!grid.damageCell(x, y, amount)) return false;
        if (topology.isWall(x, y)) rememberWall(x, y);
        topology.setWall(x, y, false);
        topology.setWindow(x, y, false);
        grid.setSeeThrough(x, y, false);
        topology.setGroundKind(x, y, CellTopology.GroundKind.RUBBLE);
        topology.setSurface(x, y, 0);
        peelRoofAround(x, y);
        navigation.markCellOpened(x, y);
        return true;
    }

    private void clearTreeCell(int x, int y) {
        // Summit trees share their cells with permanent terrain; felling the crown cannot excavate the hill.
        if (ForestBrush.isGrassySummit(grid, topology, x, y)) return;
        grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, false);
        grid.setWalkable(x, y, true);
        grid.setSeeThrough(x, y, false);
        grid.setDoorway(x, y, false);
        grid.setWallHp(x, y, 0);
        grid.openAllEdges(x, y);
        topology.setFixture(x, y, false);
        grid.recomputeCoverAt(x, y);
        grid.recomputeCoverAt(x - 1, y); grid.recomputeCoverAt(x + 1, y);
        grid.recomputeCoverAt(x, y - 1); grid.recomputeCoverAt(x, y + 1);
        doodads.refreshBlockingCover(x, y, topology);
        navigation.markCellOpened(x, y);
    }

    /** Marks an exposed solid floor for its material-matched overlay; no navigation mutation. */
    public boolean damageGround(int x, int y) {
        if (!topology.inBounds(x, y) || topology.isWall(x, y)
                || topology.hasTag(x, y, CellTopology.Tag.GROUND_DAMAGED)) return false;
        CellTopology.GroundKind kind = topology.getGroundKind(x, y);
        if (kind == CellTopology.GroundKind.VOID || kind == CellTopology.GroundKind.WATER
                || kind == CellTopology.GroundKind.RUBBLE) return false;
        topology.setTag(x, y, CellTopology.Tag.GROUND_DAMAGED, true);
        return true;
    }

    private void rememberWall(int x, int y) {
        int faces = 0;
        if (wasExposed(x, y + 1)) faces |= CellTopology.WALL_DIR_N;
        if (wasExposed(x + 1, y)) faces |= CellTopology.WALL_DIR_E;
        if (wasExposed(x, y - 1)) faces |= CellTopology.WALL_DIR_S;
        if (wasExposed(x - 1, y)) faces |= CellTopology.WALL_DIR_W;
        topology.setWallRemnant(x, y, new CellTopology.WallRemnant(
                topology.getSurfaceId(x, y), topology.getGroundKind(x, y), groundSurfaceAt(x, y), faces));
    }

    private String groundSurfaceAt(int x, int y) {
        String result = null;
        for (Direction side : Direction.CARDINALS) {
            int nx = x + side.dx, ny = y + side.dy;
            if (!topology.inBounds(nx, ny) || topology.isWall(nx, ny)) continue;
            CellTopology.WallRemnant previous = topology.getWallRemnant(nx, ny);
            CellTopology.GroundKind kind = previous == null ? topology.getGroundKind(nx, ny) : previous.groundKind();
            if (kind != topology.getGroundKind(x, y)) continue;
            String id = previous == null ? topology.getSurfaceId(nx, ny) : previous.groundSurfaceId();
            if (id != null && (result == null || id.compareTo(result) < 0)) result = id;
        }
        return result;
    }

    // Previously collapsed neighbours still belong to the original footprint. This
    // makes one explosion's debris independent of the order its wall cells fall.
    private boolean wasExposed(int x, int y) {
        return topology.inBounds(x, y) && !topology.isWall(x, y)
                && topology.getWallRemnant(x, y) == null;
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
     * refused outright. What remains — cover, presentation, structure
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
     * whole structure through the ordinary destruction path. Expiry and
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
        topology.setSurface(cellX, cellY, 0);
        grid.recomputeCoverAt(cellX, cellY);
        grid.recomputeCoverAt(cellX + 1, cellY);
        grid.recomputeCoverAt(cellX - 1, cellY);
        grid.recomputeCoverAt(cellX, cellY + 1);
        grid.recomputeCoverAt(cellX, cellY - 1);
        navigation.markCellOpened(cellX, cellY);
    }
}
