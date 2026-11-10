package com.dillon.starsectormarines.battle.world.model;

/**
 * The one thing that tells a planet type apart from the others sharing its surface palette: a cryovolcanic world
 * from any other frozen one, a bombarded rock from a plain barren one. Selected beside the palette by
 * {@code SurfaceZoning.signatureFor}, carried on the target profile, and written on the {@link CellTopology} at
 * generation so the renderer draws it.
 *
 * <p>A signature is detail, never a palette: the ground stays the palette's own material, and nothing here is a
 * battle rule. Fissures and crater rims are the excavation and raised-mass contracts every map already has; vents,
 * scars, ejecta, flecks, tint and haze are presentation only ({@code mapgen-nouns.md}, Surface signatures).
 */
public enum SurfaceSignature {

    /** Nothing beyond the palette. Every battle with no planet behind it, and most planets. */
    NONE,

    /** Cryovolcanic: frost-rimmed vent mounds breathing cold vapour. */
    CRYO_VENTS,

    /** Bombarded: fields of craters, crescent rims and scars with ejecta thrown round them. */
    CRATER_FIELD,

    /** Venus-like: hot, thick air; the ground amber and a haze drifting low over it, under the units. */
    HOT_HAZE,

    /** Unstable: fissures opened in the ground, with quake rubble along their banks. */
    FISSURES,

    /** Cast-iron and metallic: darker ground and formations, flecked with metal that catches the light. */
    METAL_FLECKED
}
