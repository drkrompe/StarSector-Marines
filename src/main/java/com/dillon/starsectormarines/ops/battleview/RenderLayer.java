package com.dillon.starsectormarines.ops.battleview;

/**
 * The ordered world-render pass list, lifted verbatim from the call sequence in
 * {@link BattleRenderer#renderWorld}. <strong>Ordinal = paint order</strong> — the
 * enum makes the load-bearing ordering visible data instead of an implicit
 * method-call sequence.
 *
 * <p>The order here is semantic, not a depth sort: roofs paint over units,
 * drones over roofs, fog between ground and units, the lightmap multiply dead
 * last so darkness covers everything. See the inline comments in
 * {@code renderWorld} for the per-seam reasoning — do not re-derive this order.
 *
 * <p>Every world pass is now command-driven: {@code BattleRenderer.renderWorld}
 * collects each producer in {@code worldSystems} (paint order) then drains the
 * layers in this enum's order. The per-constant docs below carry the load-bearing
 * seam reasoning that used to live as inline comments in {@code renderWorld} — the
 * enum is the single source of truth for paint order, so reason about order here.
 */
public enum RenderLayer {
    /** Tiled floor/wall terrain (+ the zone debug overlay on top of it). */
    GROUND,
    /** Bullet holes / craters. Above ground, below vehicles + units so they draw on top. */
    DECALS,
    /** Parked map vehicles. */
    VEHICLES,
    /** Props / overlays (rocks, plants, road markings). */
    DOODADS,
    /** Debug cell highlights (plan-step cells, selected squad, captain). Above
     *  ground/doodads, below units so unit sprites stay legible over the tint. */
    HIGHLIGHTS,
    /** Fog-of-war darkening between terrain and units. */
    FOG,
    /** Turret bodies → hub bodies → dead → live infantry → HP bars (bars last = on top). */
    UNITS,
    /** Opaque roof tiles over interiors the player can't see — above units (hides
     *  them), but below objectives / drones / shuttles / shots / flyby, which pierce the roof. */
    ROOFS,
    /** Drones — above roofs (they hover at roof altitude, so they overlay the roof tile). */
    DRONES,
    /** Charge sites + equipment drops. Above units so the player always sees objectives. */
    OBJECTIVES,
    /** Compound capture-state markers — faction ring + capture-progress arc + kind glyph. */
    COMPOUND,
    /** Convoy trucks + turrets (+ docking/selected-vehicle debug overlays). Just under shuttles. */
    CONVOY,
    /** Aircraft hulls + turrets + engine FX. */
    SHUTTLES,
    /** Contrails, hitscan tracers, projectile sprites. */
    SHOTS,
    /** Sparks / dust / smoke at shot endpoints. */
    IMPACT_FX,
    /** Vanilla fighters flying overhead — above everything ground-side. */
    FLYBY,
    /** Lightmap multiply — dead last so darkness covers everything. */
    LIGHTING
}
