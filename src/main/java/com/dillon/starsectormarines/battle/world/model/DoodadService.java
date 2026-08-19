package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the battle's persistent {@link Doodad} list plus the per-cell /
 * per-facing cover lookup that {@link com.dillon.starsectormarines.battle.decision.TacticalScoring}
 * consults when scoring firing positions. Sibling slice to the other
 * services owned by {@link BattleSimulation}.
 *
 * <p>Cover-by-facing is allocated lazily on the first {@link #addDoodad}
 * call so battles with no doodads pay no memory cost. Doodads aren't
 * removed mid-fight, so the array is append-only — values only ever
 * monotonically increase via the max-merge rule.
 */
public final class DoodadService {

    private final NavigationGrid grid;
    private final List<Doodad> doodads = new ArrayList<>();

    /**
     * Per-cell, per-facing doodad cover. Indexed as
     * {@code (y * gridWidth + x) * FACING_COUNT + facing}. Updated on
     * {@link #addDoodad}; never decreases during a battle. Lazy-initialized —
     * the array is allocated on first {@code addDoodad} call.
     *
     * <p>Each cell in a doodad's authored footprint contributes cover two ways:
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
     * Per-cell doodad ballistic half-height by cover level — footprint only,
     * no cardinal-neighbor bleed (unlike {@link #doodadCoverByFacing}). Indexed
     * as {@code grid.index(x, y) * (MAX_COVER + 1) + level}. Lazily allocated
     * on the first {@link #addDoodad} call with {@code cover > 0}; each level's
     * height is max-merged independently. Exists for
     * ballistic ray crossings ({@link com.dillon.starsectormarines.battle.combat.BallisticResolver}),
     * which must roll a block chance only against a cell a round's ray
     * actually passes through a doodad's own footprint and vertical silhouette
     * — the facing array's neighbor bleed is a firing-position-scoring concept,
     * not a physical interception one (see {@code roadmap/ballistics/overview.md}
     * §4).
     */
    private float[] doodadHalfHeightByLevelOnCell;

    public DoodadService(NavigationGrid grid) {
        this.grid = grid;
    }

    public List<Doodad> getDoodads() { return doodads; }

    public void addDoodad(Doodad d) {
        doodads.add(d);
        if (d.cover <= 0) return;
        if (doodadCoverByFacing == null) {
            doodadCoverByFacing = new byte[grid.getWidth() * grid.getHeight() * NavigationGrid.FACING_COUNT];
        }
        if (doodadHalfHeightByLevelOnCell == null) {
            doodadHalfHeightByLevelOnCell = new float[
                    grid.getWidth() * grid.getHeight() * (NavigationGrid.MAX_COVER + 1)];
        }
        for (int dy = 0; dy < d.footprintCellsY; dy++) {
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                addFootprintCell(d.cellX + dx, d.cellY + dy,
                        d.cover, d.ballisticHalfHeight);
            }
        }
    }

    private void addFootprintCell(int cellX, int cellY, int cover, float ballisticHalfHeight) {
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
        if (doodadCoverByFacing == null) return 0;
        if (!grid.inBounds(x, y)) return 0;
        if (facing < 0 || facing >= NavigationGrid.FACING_COUNT) return 0;
        return doodadCoverByFacing[(grid.index(x, y) * NavigationGrid.FACING_COUNT) + facing] & 0xFF;
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
        if (doodadHalfHeightByLevelOnCell == null) return 0;
        if (!grid.inBounds(x, y)) return 0;
        int base = grid.index(x, y) * (NavigationGrid.MAX_COVER + 1);
        float absZ = Math.abs(roundZ);
        for (int level = NavigationGrid.MAX_COVER; level > 0; level--) {
            float halfHeight = doodadHalfHeightByLevelOnCell[base + level];
            if (halfHeight > 0f && absZ <= halfHeight) return level;
        }
        return 0;
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
        if (doodadCoverByFacing == null) return 0;
        if (!grid.inBounds(x, y)) return 0;
        int base = grid.index(x, y) * NavigationGrid.FACING_COUNT;
        int n = doodadCoverByFacing[base    ] & 0xFF;
        int e = doodadCoverByFacing[base + 1] & 0xFF;
        int s = doodadCoverByFacing[base + 2] & 0xFF;
        int w = doodadCoverByFacing[base + 3] & 0xFF;
        return Math.max(Math.max(n, e), Math.max(s, w));
    }
}
