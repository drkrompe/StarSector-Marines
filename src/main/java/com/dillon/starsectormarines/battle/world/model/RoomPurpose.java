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
    /** Residential compound — public threshold between the courtyard and apartment hall. */
    APARTMENT_LOBBY,
    /** Residential compound — two-cell common hall linking entrances and private rooms. */
    RESIDENTIAL_HALL,
    /** Residential compound — shared sitting and dining room with low furniture cover. */
    APARTMENT_LIVING,
    /** Residential compound — private sleeping room with a low bed fixture. */
    BEDROOM,
    /** Medical campus — public intake and waiting space at the primary entrance. */
    MEDICAL_RECEPTION,
    /** Medical campus — two-cell clear circulation spine joining public and service doors. */
    MEDICAL_CORRIDOR,
    /** Medical campus — first-assessment room adjacent to reception. */
    TRIAGE,
    /** Medical campus — enclosed clinical procedure and stabilization room. */
    TREATMENT_ROOM,
    /** Medical campus — recovery ward furnished with wall-oriented patient beds. */
    PATIENT_WARD,
    /** Medical campus — secured drug and consumable storage room. */
    PHARMACY,
    /**
     * Ship deck — shuttle bay opening on the hull. The deck's own connection
     * to outside: how troops reach the surface, how they come back, and the
     * obvious place for boarders to arrive. Appended rather than inserted
     * because the storage layer packs {@link #ordinal()} into a byte.
     */
    HANGAR,
    /**
     * Ship deck — crew mess and galley. The largest space aboard that is
     * neither a bay nor a hold, and the one room the whole complement passes
     * through every day.
     */
    MESS_HALL,
    /** Ship deck — enclosed small-arms range and the ready area serving it. */
    FIRING_RANGE,
    /** Ship deck — heads and washroom serving a berthing block. */
    WASHROOM,
}
