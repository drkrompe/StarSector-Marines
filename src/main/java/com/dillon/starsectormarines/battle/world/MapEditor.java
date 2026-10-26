package com.dillon.starsectormarines.battle.world;

import java.util.BitSet;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.model.NaturalPropDamage;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.world.model.PropDamage;

/**
 * Coordinates the runtime map-modification cycle — the cross-domain operations
 * that fire when the battlefield changes shape mid-battle: a wall breached by a
 * detonation, a roof cracked open above it, a destroyed turret-mount or drone
 * hub flipped to walkable rubble.
 *
 * <p>Almost all of it is destruction, which is the easy direction: the grid
 * only becomes more permissive, so a route that was legal stays legal. The
 * construction op, {@link #placeDeployedBarrier}, carries the rule that makes
 * construction safe — see its contract — and the one op that closes ground,
 * {@link #closePassage} (a sally door shut again), carries the terms on which
 * it is admitted.
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
    private final NaturalPropDamage.ClearedCell naturalCellCleared = this::clearNaturalPropCell;
    private final PropDamage.ClearedCell propCellCleared = this::clearPropCell;

    public void setDoodads(DoodadService doodads) {
        this.doodads = doodads;
        // A tree on a summit shares the cell with the cliff: felling it leaves the cliff, so crushing never opens it.
        doodads.setPermanentNatural(cell -> topology.terrainSurface() != null
                && topology.terrainSurface().isFullSummit(cell % grid.getWidth(), cell / grid.getWidth()));
    }

    public void setRoofCollapseSink(CellCallback sink) { this.roofCollapseSink = sink; }

    /**
     * Breaches a wall cell: opens grid walkability ({@code grid.damageCell}),
     * clears the WALL/window tags + flips the ground to
     * {@link CellTopology.GroundKind#RUBBLE},
     * cracks the four adjacent roof cells open, and marks the zone graph dirty
     * so the new portal is picked up at tick end. Returns {@code false} (no-op)
     * when the cell wasn't breached. Registered fences, sandbags and crates use the same
     * entry point, retaining the ground and replacing their prop with damaged
     * art or debris; they do not become masonry rubble or roof-collapse sources.
     * Walkable crates and registered equipment release cover without changing navigation.
     * Registered prop groups share a pool across their footprint; stock overlays retain underlying racks.
     * Trees resolve one whole-crown structure pool before the cell-wall path.
     * Nature rocks spend per-cell structure, removing their cover and leaving low rubble.
     */
    public boolean damageWall(int x, int y, int amount) {
        return damageWall(x, y, amount, ++structuralAttack);
    }

    /** One blast spends each tree or prop group's structure once, regardless of footprint or visitation order. */
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

    /** One authored area hit; a multi-cell prop spends structure once even across several selected cells. */
    public void damageSelectedCells(BitSet cells, int amount) {
        long attack = ++structuralAttack;
        for (int at = cells.nextSetBit(0); at >= 0; at = cells.nextSetBit(at + 1)) {
            int x = at % grid.getWidth(), y = at / grid.getWidth();
            if (grid.inBounds(x, y)) damageWall(x, y, amount, attack);
        }
    }

    /** More structure than any crushable object holds, so a crush always fells or grinds it at once. */
    private static final int CRUSH_DAMAGE = 1_000_000;

    /**
     * A heavy-lift hull drives into a {@link NavigationGrid.CellTag#CRUSHABLE} cell (`HeavyTracks`): the undergrowth
     * owning it is felled whole, or the boulder ground to its rubble, through the ordinary destruction path. Returns
     * {@code false} (no-op) when the cell is not crushable.
     */
    public boolean crush(int x, int y) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)
                || !grid.hasTag(x, y, NavigationGrid.CellTag.CRUSHABLE)) return false;
        return damageWall(x, y, CRUSH_DAMAGE, ++structuralAttack);
    }

    /**
     * The creep has killed the tree placed at this origin (`CreepVegetation`): it is undergrowth from now on, so a
     * heavy-lift hull pushes it over where no other living tree's crown holds its cells.
     */
    public void witherTree(int originX, int originY) {
        if (doodads != null) doodads.witherNaturalProp(originX, originY);
    }

    private boolean damageWall(int x, int y, int amount, long attack) {
        if (doodads != null) {
            RockDamage.Result rock = doodads.damageRock(x, y, amount, topology);
            if (rock == RockDamage.Result.DESTROYED) {
                grid.setTag(x, y, NavigationGrid.CellTag.CRUSHABLE, false);
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
            NaturalPropDamage.Result natural = doodads.damageNaturalProp(x, y, amount, attack, naturalCellCleared);
            if (natural != NaturalPropDamage.Result.NOT_NATURAL_PROP) return natural == NaturalPropDamage.Result.DESTROYED;
            CoverPropDamage.Result prop = doodads.damageProp(x, y, amount, attack, propCellCleared);
            // A registered prop already spent here takes nothing, and the hit goes on to what else stands on the cell:
            // a wreck's crates laid over a trolley's debris turned every blade push into nothing.
            if (prop == CoverPropDamage.Result.DAMAGED || prop == CoverPropDamage.Result.DESTROYED)
                return prop == CoverPropDamage.Result.DESTROYED;
            boolean alreadyWalkable = grid.inBounds(x, y) && grid.isWalkable(x, y);
            CoverPropDamage.Result result = doodads.damageCoverProp(x, y, amount);
            if (result == CoverPropDamage.Result.DESTROYED) {
                if (alreadyWalkable) return true;
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
        // A non-structural fixture belongs to its prop or entity damage owner.
        // Missing wall HP must not make its footprint a free structural breach.
        if (topology.isFixture(x, y)) return false;
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

    private void clearPropCell(int x, int y) {
        if (grid.isWalkable(x, y) || topology.isWall(x, y)
                || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) return;
        openFixtureCell(x, y);
    }

    private void clearNaturalPropCell(int x, int y) {
        // Trees and mineral props share summit cells with permanent terrain, on every planet.
        if (topology.terrainSurface() != null && topology.terrainSurface().isFullSummit(x, y)) return;
        grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, false);
        openFixtureCell(x, y);
    }

    /**
     * A tree crown cell the creep has killed (`CreepVegetation`): the leaves are
     * gone, so sight passes it and it gives no edge cover, while its crusted
     * limbs still stop a body. Felling it later opens it as any crown cell.
     */
    public void witherCrownCell(int x, int y) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)
                || !grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) return;
        grid.setSeeThrough(x, y, true);
        grid.setEdgeCoverSuppressed(x, y, true);
        grid.recomputeCoverAt(x - 1, y); grid.recomputeCoverAt(x + 1, y);
        grid.recomputeCoverAt(x, y - 1); grid.recomputeCoverAt(x, y + 1);
    }

    private void openFixtureCell(int x, int y) {
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

    /** Visual aftermath after structural damage opens the impact cell; cover still shields terrain. */
    public void damageGroundInRadius(float x, float y, float radius) {
        int originX = (int)Math.floor(x), originY = (int)Math.floor(y);
        if (!topology.inBounds(originX, originY)) return;
        damageGround(originX, originY);
        int reach = (int)Math.ceil(radius);
        for (int cy = Math.max(0,originY-reach); cy <= Math.min(topology.getHeight()-1,originY+reach); cy++) {
            for (int cx = Math.max(0,originX-reach); cx <= Math.min(topology.getWidth()-1,originX+reach); cx++) {
                float dx = cx+.5f-x, dy = cy+.5f-y;
                if (dx*dx+dy*dy <= radius*radius && grid.hasLineOfSight(originX,originY,cx,cy))
                    damageGround(cx,cy);
            }
        }
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
        String authored = topology.getWallGroundSurfaceId(x, y);
        if (authored != null) return authored;
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
        destroyRoof(wallX - 1, wallY, RoofDamage.COLLAPSED);
        destroyRoof(wallX + 1, wallY, RoofDamage.COLLAPSED);
        destroyRoof(wallX, wallY - 1, RoofDamage.COLLAPSED);
        destroyRoof(wallX, wallY + 1, RoofDamage.COLLAPSED);
    }

    /** {@link #destroyRoof(int, int, RoofDamage)} for a cave-in: the roof falls in. */
    public void destroyRoof(int x, int y) {
        destroyRoof(x, y, RoofDamage.COLLAPSED);
    }

    /**
     * Damages the roof over a cell by what hit it ({@link RoofDamage}). Over a building's floor that opens the roof
     * — flips {@link CellTopology#setRoofDestroyed} and fires the {@link #roofCollapseSink} cave-in decal the first
     * time — and records the damage, keeping the worse of a second blow. Over a wall the roof's eave has no shield
     * to lose, so only its damage is recorded ({@link RoofCover}). No-op off-map or where no roof is drawn.
     */
    public void destroyRoof(int x, int y, RoofDamage cause) {
        if (!grid.inBounds(x, y) || cause == RoofDamage.INTACT) return;
        if (topology.getBuildingId(x, y) == 0) {
            if (RoofCover.isRoofed(topology, x, y)) {
                topology.setRoofDamage(x, y, topology.getRoofDamage(x, y).worse(cause));
            }
            return;
        }
        boolean wasOpen = topology.isRoofDestroyed(x, y);
        topology.setRoofDamage(x, y, topology.getRoofDamage(x, y).worse(cause));
        if (wasOpen) return;
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

    /**
     * What a wall cell was before {@link #openPassage} made it a passage, so {@link #closePassage} can put it back:
     * its navigation flags, own edges and wall hit points, its topology (wall, window, fixture, ground and surface),
     * and the cover it gave and had, per facing, on itself ({@code own*}) and on each cardinal neighbour's facing
     * towards it ({@code toward*}, indexed N, E, S, W by the neighbour's side).
     */
    public record PassageRecord(int x, int y, long flags, byte edges, int wallHp, boolean wall, boolean window,
                                boolean fixture, CellTopology.GroundKind groundKind, int surface,
                                byte[] ownCover, float[] ownCatch, byte[] towardCover, float[] towardCatch) { }

    /** Neighbour offsets N, E, S, W and the neighbour's facing that looks back at the cell. */
    private static final int[][] NEIGHBOURS = {
            {0, -1, NavigationGrid.FACING_S}, {1, 0, NavigationGrid.FACING_W},
            {0, 1, NavigationGrid.FACING_N}, {-1, 0, NavigationGrid.FACING_E}};

    /** The cell as it stands, for a later {@link #closePassage}; null off the map. */
    public PassageRecord recordPassage(int x, int y) {
        if (!grid.inBounds(x, y)) return null;
        byte[] ownCover = new byte[NavigationGrid.FACING_COUNT];
        float[] ownCatch = new float[NavigationGrid.FACING_COUNT];
        for (int f = 0; f < NavigationGrid.FACING_COUNT; f++) {
            ownCover[f] = (byte) grid.getCellCoverAtFacing(x, y, f);
            ownCatch[f] = grid.getCellCoverCatchHalfHeightAtFacing(x, y, f);
        }
        byte[] towardCover = new byte[NEIGHBOURS.length];
        float[] towardCatch = new float[NEIGHBOURS.length];
        for (int n = 0; n < NEIGHBOURS.length; n++) {
            int nx = x + NEIGHBOURS[n][0], ny = y + NEIGHBOURS[n][1];
            towardCover[n] = (byte) grid.getCellCoverAtFacing(nx, ny, NEIGHBOURS[n][2]);
            towardCatch[n] = grid.getCellCoverCatchHalfHeightAtFacing(nx, ny, NEIGHBOURS[n][2]);
        }
        return new PassageRecord(x, y, grid.cellFlags(x, y), grid.getEdgePassabilityArray()[grid.index(x, y)],
                grid.getWallHp(x, y), topology.isWall(x, y), topology.isWindow(x, y), topology.isFixture(x, y),
                topology.getGroundKind(x, y), topology.getSurface(x, y), ownCover, ownCatch, towardCover, towardCatch);
    }

    /**
     * Shuts a passage {@link #openPassage} made, putting the cell back exactly as {@code record} found it: wall again,
     * with the hit points it had, the ground and surface it had, and the cover it gave its neighbours. This is the one
     * runtime change that makes the grid <b>less</b> permissive, so it is admitted on the terms construction was refused
     * on (see {@link #placeDeployedBarrier}), and they are the caller's to keep: nobody stands in the cell (a sally door
     * never shuts on a body), and the routes that cross it are dropped. The navigation layers are marked for a full
     * rebuild at the tick's flush, because the zone graph's incremental update can only merge. Returns {@code false}
     * when the cell is not open floor (already shut, or never opened).
     */
    public boolean closePassage(PassageRecord record) {
        int x = record.x(), y = record.y();
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return false;
        grid.restoreCell(x, y, record.flags(), record.edges(), record.wallHp());
        topology.setWall(x, y, record.wall());
        topology.setWindow(x, y, record.window());
        topology.setFixture(x, y, record.fixture());
        topology.setGroundKind(x, y, record.groundKind());
        topology.setSurface(x, y, record.surface());
        for (int f = 0; f < NavigationGrid.FACING_COUNT; f++) {
            grid.setCoverAtFacing(x, y, f, record.ownCover()[f], record.ownCatch()[f]);
        }
        for (int n = 0; n < NEIGHBOURS.length; n++) {
            int nx = x + NEIGHBOURS[n][0], ny = y + NEIGHBOURS[n][1];
            if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
            grid.setCoverAtFacing(nx, ny, NEIGHBOURS[n][2], record.towardCover()[n], record.towardCatch()[n]);
        }
        navigation.markCellClosed(x, y);
        return true;
    }

    /**
     * Opens a standing wall cell as a built passage rather than a breach: walkable, no wall, on the same ground as the
     * floor cell ({@code likeX}, {@code likeY}) beside it, with no rubble and no roof peeled, since nothing broke. A
     * fort's sally port is unbarred through this. Take a {@link #recordPassage} first to be able to shut it again.
     * Returns {@code false} for a cell that is already open.
     */
    public boolean openPassage(int x, int y, int likeX, int likeY) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)) return false;
        grid.setWalkable(x, y, true);
        grid.setSeeThrough(x, y, false);
        grid.setDoorway(x, y, false);
        grid.setWallHp(x, y, 0);
        grid.openAllEdges(x, y);
        topology.setWall(x, y, false);
        topology.setWindow(x, y, false);
        topology.setFixture(x, y, false);
        if (grid.inBounds(likeX, likeY)) {
            topology.setGroundKind(x, y, topology.getGroundKind(likeX, likeY));
            topology.setSurface(x, y, topology.getSurface(likeX, likeY));
        }
        grid.recomputeCoverAt(x, y);
        grid.recomputeCoverAt(x + 1, y);
        grid.recomputeCoverAt(x - 1, y);
        grid.recomputeCoverAt(x, y + 1);
        grid.recomputeCoverAt(x, y - 1);
        navigation.markCellOpened(x, y);
        return true;
    }

    public void flipCellToRubble(int cellX, int cellY) {
        // Destroying a summit emplacement does not demolish the mountain beneath it.
        if (grid.hasTag(cellX, cellY, NavigationGrid.CellTag.NATURAL_SOLID)) return;
        grid.setWalkable(cellX, cellY, true);
        grid.setWallHp(cellX, cellY, 0);
        topology.setWall(cellX, cellY, false);
        topology.setFixture(cellX, cellY, false);
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

    /** Raises or lowers the eye of a gun on a plinth in one cell ({@link NavigationGrid#setRaisedEye}). */
    public void setRaisedEye(int x, int y, int tiers) {
        grid.setRaisedEye(x, y, tiers);
    }

    /**
     * Raises the plinth a structure stands on over its footprint, the
     * {@code w x h} cells from {@code (x0, y0)}, or clears it with zero
     * ({@link CellTopology#setPlinthTiers}).
     */
    public void setPlinth(int x0, int y0, int w, int h, int tiers) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) topology.setPlinthTiers(x, y, tiers);
        }
    }
}
