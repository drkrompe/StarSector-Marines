package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileCover;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the battle's persistent {@link Doodad} list plus the per-cell /
 * per-facing cover lookup that {@link com.dillon.starsectormarines.battle.decision.TacticalScoring}
 * consults when scoring firing positions. Nature overlays publish their
 * authored cover into the same lookup without becoming render doodads.
 * Sibling slice to the other services owned by {@link BattleSimulation}.
 *
 * <p>A doodad's navigation footprint chooses exactly one interception shape:
 * walkable props publish physical ray-crossing cover; non-walkable props
 * publish directional edge cover onto adjacent standable cells. This keeps a
 * fixture from rolling both crossing and edge interception for the same shot.
 * Physical-cover storage is allocated lazily on the first covered addition.
 * Static prop cover arrays are append-only; nature cover is separately removable. Destructible cell-blocking props instead publish
 * into grid facings; a breach rebakes those facings and restores surviving neighbors.
 */
public final class DoodadService {

    private final NavigationGrid grid;
    private final List<Doodad> doodads = new ArrayList<>();
    private final CoverPropDamage destructibleCover;
    private final TreeDamage trees;
    private final RockDamage rocks;
    /** Mutable overlay cover stays separate from append-only props so shattered rocks can release it. */
    private TileCover[] natureCoverOnCell;

    /**
     * Per-cell, per-facing cover for static props. Mutable nature cover is merged at query time.
     * Indexed as
     * {@code (y * gridWidth + x) * FACING_COUNT + facing}. Updated on
     * {@link #addDoodad} or {@link #addStaticCover}; never decreases
     * during a battle. Lazy-initialized on first use.
     *
     * <p>Each cell in a feature's authored footprint contributes cover two ways:
     * <ol>
     *   <li><b>Isotropic on its occupied cell.</b> All four facings
     *       gain the doodad's cover level — a marine standing on the crate
     *       cell is "co-located with the cover," counted as covered from any
     *       angle.</li>
     *   <li><b>Per-facing on each cardinal neighbor.</b> An occupied cell
     *       sits between the neighbor at (cx, cy-1) and threats further north,
     *       so that neighbor gains S-facing cover (threat south = doodad is
     *       between). Same for the other three cardinals — the doodad blocks
     *       LOS toward itself, so the neighbor reads cover from the facing
     *       <em>toward</em> the doodad.</li>
     * </ol>
     *
     * <p>Multiple doodads stacking on the same cell+facing take the max.
     * Combined with cell-grid wall cover ({@link NavigationGrid#getCoverAtFacing})
     * at the consumer site —
     * {@link com.dillon.starsectormarines.battle.decision.TacticalScoring} sums the
     * two when scoring candidate firing positions.
     */
    private byte[] doodadCoverByFacing;

    /**
     * Per-cell physical-cover ballistic half-height by cover level — footprint only,
     * no cardinal-neighbor bleed (unlike {@link #doodadCoverByFacing}). Indexed
     * as {@code grid.index(x, y) * (MAX_COVER + 1) + level}. Lazily allocated
     * on the first covered static prop addition; each level's
     * height is max-merged independently. Exists for
     * ballistic ray crossings ({@link com.dillon.starsectormarines.battle.combat.BallisticResolver}),
     * which must roll a block chance only against a cell a round's ray
     * actually passes through a feature's own footprint and vertical silhouette
     * — the facing array's neighbor bleed is a firing-position-scoring concept,
     * not a physical interception one (see {@code ballistics-nouns.md}).
     */
    private float[] doodadHalfHeightByLevelOnCell;

    public DoodadService(NavigationGrid grid) {
        this.grid = grid;
        this.destructibleCover = new CoverPropDamage(grid, doodads);
        this.trees = new TreeDamage(grid, doodads);
        this.rocks = new RockDamage(grid, doodads);
    }

    public List<Doodad> getDoodads() { return doodads; }

    /**
     * Adds a render doodad and publishes its authored cover through exactly
     * one ballistic shape per footprint cell. Walkable cells are physical
     * crossings; navigation blockers behave like windows and upgrade adjacent
     * grid facings instead.
     */
    public void addDoodad(Doodad d) {
        destructibleCover.register(doodads.size(), d, TileRegistry.installed());
        trees.register(doodads.size(), d, TileRegistry.installed());
        doodads.add(d);
        if (d.cover <= 0) return;
        ensureCoverStorage();
        for (int dy = 0; dy < d.footprintCellsY; dy++) {
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                int cellX = d.cellX + dx;
                int cellY = d.cellY + dy;
                if (grid.isWalkable(cellX, cellY)) {
                    addCoverCell(cellX, cellY, d.cover, d.ballisticHalfHeight);
                } else {
                    addDirectionalEdgeCover(cellX, cellY,
                            d.cover, d.ballisticHalfHeight);
                }
            }
        }
    }

    public CoverPropDamage.Result damageCoverProp(int x, int y, int amount) {
        return destructibleCover.damage(x, y, amount);
    }

    public TreeDamage.Result damageTree(int x, int y, int amount, long attack, TreeDamage.ClearedCell cleared) {
        return trees.damage(x, y, amount, attack, cleared);
    }

    public RockDamage.Result damageRock(int x, int y, int amount, CellTopology topology) {
        RockDamage.Result result = rocks.damage(x, y, amount, topology, TileRegistry.installed());
        if (result == RockDamage.Result.DESTROYED && natureCoverOnCell != null)
            natureCoverOnCell[grid.index(x, y)] = null;
        return result;
    }

    /** Restore surviving prop/nature contributions after the grid rebakes a breached cell's neighbors. */
    public void refreshBlockingCover(int x, int y, CellTopology topology) {
        for (Doodad prop : doodads) {
            if (prop.cover <= 0) continue;
            if (prop.cellX > x + 2 || prop.cellY > y + 2
                    || prop.cellX + prop.footprintCellsX <= x - 2
                    || prop.cellY + prop.footprintCellsY <= y - 2) continue;
            for (int yy = prop.cellY; yy < prop.cellY + prop.footprintCellsY; yy++)
                for (int xx = prop.cellX; xx < prop.cellX + prop.footprintCellsX; xx++)
                    if (Math.abs(xx - x) <= 2 && Math.abs(yy - y) <= 2 && !grid.isWalkable(xx, yy))
                        addDirectionalEdgeCover(xx, yy, prop.cover, prop.ballisticHalfHeight);
        }
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        for (int yy = y - 2; yy <= y + 2; yy++) for (int xx = x - 2; xx <= x + 2; xx++) {
            if (!grid.inBounds(xx, yy)) continue;
            int index = topology.getNatureOverlayIndex(xx, yy);
            if (index < 0) continue;
            TileDef def = registry.byIndex(index);
            if (!def.passable && def.cover.level() > 0)
                addDirectionalEdgeCover(xx, yy, def.cover.level(), def.cover.defaultBallisticHalfHeight());
        }
    }

    /**
     * Publishes every covered nature overlay in {@code topology} into the
     * battle's tactical and ballistic cover profile. Walkable overlays use a
     * physical cell-crossing profile like doodads. Non-walkable see-through
     * overlays use directional edge cover on adjacent standable cells, like a
     * window, so the same rock cannot roll both crossing and edge interception.
     * Overlay visuals remain owned by {@link CellTopology}; no {@link Doodad}
     * is added to the render list. A missing registry is the established
     * degraded no-overlay path.
     */
    public void addNatureOverlayCover(CellTopology topology, TileRegistry registry) {
        if (topology == null || registry == null) return;
        if (topology.getWidth() != grid.getWidth()
                || topology.getHeight() != grid.getHeight()) {
            throw new IllegalArgumentException("Nature overlay topology dimensions must match the navigation grid");
        }
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int tileIndex = topology.getNatureOverlayIndex(x, y);
                if (tileIndex < 0) continue;
                TileDef def = registry.byIndex(tileIndex);
                if (def.cover.level() <= 0) continue;
                if (def.passable) {
                    if (natureCoverOnCell == null) natureCoverOnCell = new TileCover[grid.getWidth() * grid.getHeight()];
                    natureCoverOnCell[grid.index(x, y)] = def.cover;
                } else {
                    addDirectionalEdgeCover(x, y, def.cover.level(),
                            def.cover.defaultBallisticHalfHeight());
                }
            }
        }
    }

    private void addDirectionalEdgeCover(int cellX, int cellY, int cover,
                                         float ballisticHalfHeight) {
        maxMergeGridFacing(cellX, cellY - 1, NavigationGrid.FACING_S,
                cover, ballisticHalfHeight);
        maxMergeGridFacing(cellX, cellY + 1, NavigationGrid.FACING_N,
                cover, ballisticHalfHeight);
        maxMergeGridFacing(cellX - 1, cellY, NavigationGrid.FACING_E,
                cover, ballisticHalfHeight);
        maxMergeGridFacing(cellX + 1, cellY, NavigationGrid.FACING_W,
                cover, ballisticHalfHeight);
    }

    private void maxMergeGridFacing(int x, int y, int facing, int cover,
                                    float ballisticHalfHeight) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return;
        int existing = grid.getCellCoverAtFacing(x, y, facing);
        float existingHeight = grid.getCellCoverCatchHalfHeightAtFacing(
                x, y, facing);
        if (cover < existing) return;
        grid.setCoverAtFacing(x, y, facing, cover,
                cover > existing
                        ? ballisticHalfHeight
                        : Math.max(existingHeight, ballisticHalfHeight));
    }

    /** Adds one permanent non-doodad physical cover cell. */
    public void addStaticCover(int cellX, int cellY, int cover,
                               float ballisticHalfHeight) {
        if (cover <= 0 || !grid.inBounds(cellX, cellY)) return;
        ensureCoverStorage();
        addCoverCell(cellX, cellY,
                Math.min(cover, NavigationGrid.MAX_COVER), ballisticHalfHeight);
    }

    private void ensureCoverStorage() {
        if (doodadCoverByFacing == null) {
            doodadCoverByFacing = new byte[grid.getWidth() * grid.getHeight() * NavigationGrid.FACING_COUNT];
        }
        if (doodadHalfHeightByLevelOnCell == null) {
            doodadHalfHeightByLevelOnCell = new float[
                    grid.getWidth() * grid.getHeight() * (NavigationGrid.MAX_COVER + 1)];
        }
    }

    private void addCoverCell(int cellX, int cellY, int cover, float ballisticHalfHeight) {
        if (!grid.inBounds(cellX, cellY)) return;
        // Isotropic on each occupied cell. Max-merge with existing props.
        maxMergeDoodadFacing(cellX, cellY, NavigationGrid.FACING_N, cover);
        maxMergeDoodadFacing(cellX, cellY, NavigationGrid.FACING_E, cover);
        maxMergeDoodadFacing(cellX, cellY, NavigationGrid.FACING_S, cover);
        maxMergeDoodadFacing(cellX, cellY, NavigationGrid.FACING_W, cover);
        // Cardinal neighbors gain cover toward this occupied cell. Internal
        // footprint neighbors merely max-merge the same value.
        maxMergeDoodadFacing(cellX, cellY - 1, NavigationGrid.FACING_S, cover);
        maxMergeDoodadFacing(cellX, cellY + 1, NavigationGrid.FACING_N, cover);
        maxMergeDoodadFacing(cellX - 1, cellY, NavigationGrid.FACING_E, cover);
        maxMergeDoodadFacing(cellX + 1, cellY, NavigationGrid.FACING_W, cover);
        // Ballistic silhouette is physical-footprint-only, with no neighbor bleed.
        int idx = grid.index(cellX, cellY);
        int slot = idx * (NavigationGrid.MAX_COVER + 1) + cover;
        doodadHalfHeightByLevelOnCell[slot] = Math.max(
                doodadHalfHeightByLevelOnCell[slot], ballisticHalfHeight);
    }

    /** Writes {@code level} to a cell+facing slot if higher than the current value. Out-of-bounds calls are no-ops so callers don't need to bounds-check the four neighbor writes around an edge doodad. */
    private void maxMergeDoodadFacing(int x, int y, int facing, int level) {
        if (!grid.inBounds(x, y)) return;
        int slot = (grid.index(x, y) * NavigationGrid.FACING_COUNT) + facing;
        int existing = doodadCoverByFacing[slot] & 0xFF;
        if (level > existing) doodadCoverByFacing[slot] = (byte) level;
    }

    /** Directional doodad cover at (x, y) against a threat in direction {@code (fromDx, fromDy)} (offset from this cell to the threat). 0 if no doodad covers that facing. */
    public int getDoodadCoverAt(int x, int y, int fromDx, int fromDy) {
        return getDoodadCoverAtFacing(x, y, NavigationGrid.facingFor(fromDx, fromDy));
    }

    public int getDoodadCoverAtFacing(int x, int y, int facing) {
        if (!grid.inBounds(x, y)) return 0;
        if (facing < 0 || facing >= NavigationGrid.FACING_COUNT) return 0;
        int fixed = doodadCoverByFacing == null ? 0 : doodadCoverByFacing[(grid.index(x, y) * NavigationGrid.FACING_COUNT) + facing] & 0xFF;
        int nx = x, ny = y;
        switch (facing) {
            case NavigationGrid.FACING_N -> ny--;
            case NavigationGrid.FACING_S -> ny++;
            case NavigationGrid.FACING_E -> nx++;
            case NavigationGrid.FACING_W -> nx--;
        }
        return Math.max(fixed, Math.max(natureCover(x, y).level(), natureCover(nx, ny).level()));
    }

    private TileCover natureCover(int x, int y) {
        if (natureCoverOnCell == null || !grid.inBounds(x, y)) return TileCover.NONE;
        TileCover cover = natureCoverOnCell[grid.index(x, y)];
        return cover == null ? TileCover.NONE : cover;
    }

    /**
     * Doodad level physically present on cell ({@code x}, {@code y}) —
     * own-cell only, no cardinal-neighbor bleed (unlike
     * {@link #getDoodadCoverAt(int, int)}, which also reads the bled facing
     * cover on a doodad's four neighbors). Ballistic ray crossings must key
     * on this: a round's ray only rolls a block chance against a cell it
     * physically passes through a doodad's footprint, never a neighbor
     * that's merely adjacent to one. 0 if no doodad occupies this cell.
     */
    public int getDoodadLevelOnCell(int x, int y) {
        return getDoodadLevelOnCell(x, y, 0f);
    }

    /**
     * Strongest doodad cover level on this exact cell whose authored vertical
     * silhouette contains {@code roundZ}. Returns zero when the trajectory
     * clears every stacked prop; no neighbor-facing cover participates.
     */
    public int getDoodadLevelOnCell(int x, int y, float roundZ) {
        if (!grid.inBounds(x, y)) return 0;
        int base = grid.index(x, y) * (NavigationGrid.MAX_COVER + 1);
        float absZ = Math.abs(roundZ);
        TileCover nature = natureCover(x, y);
        int natureLevel = nature.defaultBallisticHalfHeight() > 0 && absZ <= nature.defaultBallisticHalfHeight() ? nature.level() : 0;
        if (doodadHalfHeightByLevelOnCell == null) return natureLevel;
        for (int level = NavigationGrid.MAX_COVER; level > natureLevel; level--) {
            float halfHeight = doodadHalfHeightByLevelOnCell[base + level];
            if (halfHeight > 0f && absZ <= halfHeight) return level;
        }
        return natureLevel;
    }

    /** Package-visible diagnostic for deterministic stacked-profile tests. */
    float getDoodadHalfHeightOnCell(int x, int y, int level) {
        if (doodadHalfHeightByLevelOnCell == null) return 0f;
        if (!grid.inBounds(x, y)) return 0f;
        if (level <= 0 || level > NavigationGrid.MAX_COVER) return 0f;
        int base = grid.index(x, y) * (NavigationGrid.MAX_COVER + 1);
        return doodadHalfHeightByLevelOnCell[base + level];
    }

    /** Direction-agnostic doodad cover at (x, y) — max across all 4 facings. Back-compat accessor for {@link com.dillon.starsectormarines.battle.decision.TacticalScoring#findFallbackPosition} and other callers that don't carry a threat direction. */
    public int getDoodadCoverAt(int x, int y) {
        if (!grid.inBounds(x, y)) return 0;
        int n = getDoodadCoverAtFacing(x, y, NavigationGrid.FACING_N);
        int e = getDoodadCoverAtFacing(x, y, NavigationGrid.FACING_E);
        int s = getDoodadCoverAtFacing(x, y, NavigationGrid.FACING_S);
        int w = getDoodadCoverAtFacing(x, y, NavigationGrid.FACING_W);
        return Math.max(Math.max(n, e), Math.max(s, w));
    }
}
