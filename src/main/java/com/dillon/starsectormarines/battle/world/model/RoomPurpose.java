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
    /**
     * Shipboard — where the hands who work the ship sleep, as opposed to the
     * ground force she carries.
     *
     * <p>Distinct from {@link #BARRACKS} because a berth is an assignment rather
     * than an amenity. Everyone aboard eats and washes in the same rooms; nobody
     * sleeps in somebody else's bunk. Keeping them apart is also what makes the
     * company's billet count its own, rather than a figure inflated by the
     * ship's ratings.
     */
    CREW_QUARTERS,
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
    /**
     * Ship deck — main engine room, against the transom. The drive itself,
     * distinct from the auxiliary machinery spaces forward of it.
     */
    ENGINE_ROOM,
    /**
     * Ship deck — crew lounge. Somewhere to be that is not a bunk, a table or a
     * work station.
     *
     * <p>Distinct from {@link #MESS_HALL}, which is a room people are fed in on
     * a schedule and leave. This is the one compartment aboard whose purpose is
     * that nothing in particular is happening in it, which is why a ship that
     * lacks it has a complement who can only ever be asleep or at work.
     */
    CREW_LOUNGE,
    /**
     * Ship deck — exercise space. Clear deck with the gear round the edges of
     * it, like a range or a bay: what the room is for is the empty middle.
     */
    GYMNASIUM,
}
