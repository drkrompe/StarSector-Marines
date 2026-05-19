package com.dillon.starsectormarines.battle.map;

/**
 * Coarse classification of a {@link Building}, voted from per-cell
 * {@link CellTopology#getBuildingKindHint} stamps by the flood-fill pass.
 * Drives roof flavor (tint palette today, dedicated per-kind roof sheet later)
 * and downstream gameplay hooks (faction allegiance, destruction debris kind,
 * future objective placement).
 *
 * <p>OTHER is the catch-all for buildings the flood-fill detected (a closed
 * INDOOR/TILE component) whose stamper didn't write a hint, or whose hints
 * tied. UNKNOWN is reserved for code paths that genuinely don't have a kind
 * yet (e.g. mid-construction state) — the generator should never emit it.
 */
public enum BuildingKind {
    UNKNOWN,
    RESIDENTIAL,
    COMMERCIAL,
    INDUSTRIAL,
    FORTIFIED,
    OTHER
}
