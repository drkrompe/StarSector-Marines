package com.dillon.starsectormarines.battle.world.model;

/**
 * Per-cell label naming the logical room a walkable interior cell belongs to.
 * Written by carve-time partitioners ({@link
 * com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingShellCore}'s
 * partition step) so post-fill stampers and AI consumers can identify "which
 * chamber is this cell in?" by direct lookup instead of reverse-engineering
 * via {@link com.dillon.starsectormarines.battle.nav.zone.ZoneGraph}.
 *
 * <p>Purpose sets currently cover fortress keeps, commercial shops, military
 * compounds, station corridors, and civic headquarters. Future map types can
 * extend this enum with their own purposes (HANGAR, HABITATION, LAB, CRYOBAY,
 * BRIDGE); the storage layer treats {@link #ordinal()} as opaque so adding
 * values is non-breaking.
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
    /** Commercial building — public sales floor and the store's primary combat space. */
    SHOP_FLOOR,
    /** Commercial building — staff-only stockroom behind the sales floor. */
    STOCKROOM,
    /** Military compound — open sleeping quarters with paired bunk rows. */
    BARRACKS,
    /** Military compound — secured weapons and supply storage. */
    ARMORY,
    /** Military compound — open service floor for vehicles and field equipment. */
    VEHICLE_BAY,
    /** Civic headquarters — public-facing lobby around the primary entrance. */
    CIVIC_RECEPTION,
    /** Civic headquarters — two-cell circulation spine joining public and service entrances. */
    OFFICE_CORRIDOR,
    /** Civic headquarters — enclosed administrative workspace. */
    CIVIC_OFFICE,
    /** Civic headquarters — enclosed planning and meeting room. */
    CONFERENCE_ROOM,
    /** Civic headquarters — secured data room whose racks block line of sight. */
    SERVER_ROOM,
    /** Industrial facility — two-cell service spine joining loading and rear entrances. */
    INDUSTRIAL_SPINE,
    /** Industrial facility — frontage-side material receiving and dispatch area. */
    LOADING_BAY,
    /** Industrial facility — main fabrication floor with opaque machine cover. */
    PRODUCTION_FLOOR,
    /** Industrial facility — enclosed supervisor and process-control station. */
    CONTROL_ROOM,
    /** Industrial facility — secured replacement-parts and tool storage. */
    PARTS_CAGE,
}
