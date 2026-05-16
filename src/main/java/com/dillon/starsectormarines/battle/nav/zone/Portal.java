package com.dillon.starsectormarines.battle.nav.zone;

/**
 * A connection between two {@link NavigationZone}s through a single doorway
 * cell. The doorway cell itself is its own 1-cell zone — the portal links
 * that doorway zone to a neighboring zone on one side.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.zone.Portal}.
 * Dropped from the original: multi-level cell coords ({@code cellALevel} /
 * {@code cellBLevel}), {@code PortalSide} as a separate type. This grid is
 * single-level and the "side" data is the (zone, cell) pair encoded directly
 * here — no need for a separate {@code PortalSide} record.
 *
 * <p>{@link #doorwayCellIdx} is the flat cell index of the doorway; both sides
 * of the portal share it (you enter and exit through the same cell). To find
 * the "outside" cell from one zone's perspective, walk one cardinal step from
 * the doorway and see which neighbor lives in the target zone.
 */
public final class Portal {

    private final int portalId;
    private final int zoneIdA;
    private final int zoneIdB;
    private final int doorwayCellIdx;

    public Portal(int portalId, int zoneIdA, int zoneIdB, int doorwayCellIdx) {
        this.portalId = portalId;
        this.zoneIdA = zoneIdA;
        this.zoneIdB = zoneIdB;
        this.doorwayCellIdx = doorwayCellIdx;
    }

    public int getPortalId()       { return portalId; }
    public int getZoneIdA()        { return zoneIdA; }
    public int getZoneIdB()        { return zoneIdB; }
    public int getDoorwayCellIdx() { return doorwayCellIdx; }

    /** Returns the zone on the other side of this portal from {@code fromZone}, or -1 if {@code fromZone} isn't on this portal. */
    public int otherZone(int fromZone) {
        if (fromZone == zoneIdA) return zoneIdB;
        if (fromZone == zoneIdB) return zoneIdA;
        return -1;
    }
}
