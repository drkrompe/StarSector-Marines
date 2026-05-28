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
     * <p>A doodad at (cx, cy) contributes cover two ways:
     * <ol>
     *   <li><b>Isotropic on its own cell.</b> All four facings of (cx, cy)
     *       gain the doodad's cover level — a marine standing on the crate
     *       cell is "co-located with the cover," counted as covered from any
     *       angle.</li>
     *   <li><b>Per-facing on each cardinal neighbor.</b> The doodad at (cx, cy)
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

    public DoodadService(NavigationGrid grid) {
        this.grid = grid;
    }

    public List<Doodad> getDoodads() { return doodads; }

    public void addDoodad(Doodad d) {
        doodads.add(d);
        if (d.cover <= 0) return;
        if (!grid.inBounds(d.cellX, d.cellY)) return;
        if (doodadCoverByFacing == null) {
            doodadCoverByFacing = new byte[grid.getWidth() * grid.getHeight() * NavigationGrid.FACING_COUNT];
        }
        // Isotropic on own cell — a marine standing on the crate cell counts
        // as covered from any angle. Max-merge with existing so stacked
        // doodads use the heaviest cover.
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_N, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_E, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_S, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_W, d.cover);
        // Cardinal neighbors gain cover toward the doodad — the marine on
        // (cx, cy-1) reads S-facing cover because the doodad sits between them
        // and any southward threat. Same logic in the other three cardinals.
        maxMergeDoodadFacing(d.cellX, d.cellY - 1, NavigationGrid.FACING_S, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY + 1, NavigationGrid.FACING_N, d.cover);
        maxMergeDoodadFacing(d.cellX - 1, d.cellY, NavigationGrid.FACING_E, d.cover);
        maxMergeDoodadFacing(d.cellX + 1, d.cellY, NavigationGrid.FACING_W, d.cover);
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
