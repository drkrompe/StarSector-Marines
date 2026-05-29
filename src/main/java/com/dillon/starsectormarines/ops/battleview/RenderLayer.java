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
 * <p>Story C (battle-render reorg) only routes {@link #SHOTS} through the
 * {@link DrawList}/{@link BattleRenderer#drainLayer} model; the remaining layers
 * still draw inline from {@code renderWorld} and are migrated one slice at a
 * time (stories D…N).
 */
public enum RenderLayer {
    GROUND,
    DECALS,
    VEHICLES,
    DOODADS,
    HIGHLIGHTS,
    FOG,
    UNITS,
    ROOFS,
    DRONES,
    OBJECTIVES,
    COMPOUND,
    CONVOY,
    SHUTTLES,
    SHOTS,
    IMPACT_FX,
    FLYBY,
    LIGHTING
}
