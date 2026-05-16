package com.dillon.starsectormarines.battle.nav.zone;

import java.util.ArrayList;
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
 */
public final class NavigationZone {

    private final int zoneId;
    /** Flat cell indices belonging to this zone. Unmodifiable after construction. */
    private final int[] cellIndices;
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
}
