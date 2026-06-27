package com.dillon.starsectormarines.battle.nav.zone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A connected region of walkable, non-doorway cells on the battle grid.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.zone.NavigationZone}.
 * Dropped from the original: multi-level support, the {@code fastutil} primitive
 * sets, the global level-aware id space. Kept the cells + portal-ids shape so
 * the AI side can ask "which portals border this zone" in O(1).
 *
 * <p>Cell membership is stored as flat indices ({@code y * gridWidth + x}) so
 * lookups stay primitive — no Coord allocations per query.
 *
 * <p><b>Mutable membership.</b> Cells are immutable from a consumer's view
 * ({@link #getCellIndices} hands back the live tight array, never modified in
 * place under a reader), but {@link ZoneGraph}'s incremental rebuild grows a
 * survivor zone ({@link #absorb}/{@link #addCell}) and empties absorbed
 * tombstones ({@link #clearCells}) as wall-breaches merge zones. Each mutator
 * reallocates a right-sized array, so {@code getCellIndices().length} always
 * equals the true membership — readers between ticks see a consistent snapshot.
 */
public final class NavigationZone {

    private static final int[] EMPTY = new int[0];

    private final int zoneId;
    /** Flat cell indices belonging to this zone; replaced wholesale on each merge mutator. */
    private int[] cellIndices;
    /** Portals that touch this zone. Populated by {@link ZoneGraph} after portal detection. */
    private final List<Integer> portalIds = new ArrayList<>();

    public NavigationZone(int zoneId, int[] cellIndices) {
        this.zoneId = zoneId;
        this.cellIndices = cellIndices;
    }

    public int getZoneId()       { return zoneId; }
    public int[] getCellIndices(){ return cellIndices; }
    public int getCellCount()    { return cellIndices.length; }
    public List<Integer> getPortalIds() { return portalIds; }

    void addPortalId(int portalId) {
        portalIds.add(portalId);
    }

    /** Append one cell (the just-opened cell) to this zone. */
    void addCell(int cellIndex) {
        int n = cellIndices.length;
        cellIndices = Arrays.copyOf(cellIndices, n + 1);
        cellIndices[n] = cellIndex;
    }

    /** Append a batch of cells (an absorbed zone's membership) in one reallocation. */
    void absorb(int[] cells) {
        if (cells.length == 0) return;
        int n = cellIndices.length;
        cellIndices = Arrays.copyOf(cellIndices, n + cells.length);
        System.arraycopy(cells, 0, cellIndices, n, cells.length);
    }

    /** Empty this zone — it's an absorbed tombstone (its id slot stays, keeping {@code zoneId == index}). */
    void clearCells() {
        cellIndices = EMPTY;
    }
}
