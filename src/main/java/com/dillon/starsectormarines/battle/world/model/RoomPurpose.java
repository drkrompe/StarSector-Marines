package com.dillon.starsectormarines.battle.world.model;

/**
 * Per-cell label naming the logical room a walkable interior cell belongs to.
 * Written by carve-time partitioners ({@link
 * com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingShellCore}'s
 * partition step) so post-fill stampers and AI consumers can identify "which
 * chamber is this cell in?" by direct lookup instead of reverse-engineering
 * via {@link com.dillon.starsectormarines.battle.nav.zone.ZoneGraph}.
 *
 * <p>The current keep label set names the three chambers a fortress-style
 * COMMAND_POST sub-building may carve (entry / inner / throne). Other map
 * types will extend this enum with their own purposes (HANGAR, HABITATION,
 * COMMAND, CORRIDOR for stations; LAB, CRYOBAY, BRIDGE for ship interiors)
 * — the storage layer treats {@link #ordinal()} as opaque so adding values
 * is non-breaking.
 *
 * <p>{@code GENERIC} is the explicit "this room exists but has no special
 * tactical role" value; carvers that label rooms but have no specific
 * purpose write GENERIC instead of leaving the cell null. Null on a
 * walkable cell means "no carver labeled this" — the consumer can fall
 * back to whatever heuristic it used before labels existed.
 */
public enum RoomPurpose {
    /** No specific role. Carved by a labeling partitioner but the room has no tactical meaning. */
    GENERIC,
    /** Fortress keep — antechamber facing the compound exterior. Storming squads clear it before reaching the inner / throne chamber. Gets a forward INNER_POSITION garrison. */
    KEEP_ENTRY,
    /** Fortress keep — middle chamber in the three-chamber layout. Sits between {@link #KEEP_ENTRY} and {@link #KEEP_THRONE}. Gets a mid-strength INNER_POSITION garrison. */
    KEEP_INNER,
    /** Fortress keep — deepest chamber, contains the COMMAND_POST anchor. The conquest objective; defender doctrine elite garrisons here. */
    KEEP_THRONE,
    /**
     * Station/ship connective passage — a carved corridor cell, distinct from
     * the rooms it joins. Written by the station generator's corridor pass
     * ({@link com.dillon.starsectormarines.battle.world.gen.bsp.stage.CorridorStage})
     * so post-fill consumers and the preview can tell transit space from room
     * space. The "corridors as first-class connective structure" label —
     * topological role (degree / depth / on-spine) is a later layer that sits
     * on top of this membership marker.
     */
    CORRIDOR,
}
