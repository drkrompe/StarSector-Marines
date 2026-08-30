package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Reads an {@link AirOrdnance} load as a kind of delivery, and turns one
 * released round into the presentation record a host draws and plays.
 *
 * <p>This is the whole of the air domain's contribution to the gun run's
 * presentation. It reads {@code AirOrdnance}'s data and writes nothing back:
 * ordnance stays a statement about how much is delivered, how fast, and how
 * tightly, and never gains a sprite or a sound. What a delivery looks and
 * sounds like is resolved on the render tier from the {@link OrdnanceDelivery}
 * alone.
 *
 * <p><b>The presets are named, and a preset nobody named still works.</b> The
 * three shipped loads are matched by identity, because that survives their
 * numbers being re-tuned — which happens, and would silently re-classify a
 * load matched on its scatter or its rate of fire. Anything else falls back on
 * the one <em>structural</em> difference the model actually draws: a load that
 * runs out is a stick of bombs and a load that does not is a gun.
 */
public final class AirOrdnanceDelivery {

    /**
     * How far ahead of the body's origin the round is drawn leaving from, in
     * cells.
     *
     * <p>Purely where the flash is painted. The body's origin is the hull's
     * centre of gravity, so a flash drawn there sits in the middle of the
     * aircraft; a nose is roughly a hull's length ahead of it. Nothing about
     * aim, lead or scatter reads this.
     */
    public static final float MUZZLE_OFFSET_CELLS = 0.9f;

    private AirOrdnanceDelivery() {}

    /** The kind of delivery {@code load} makes. Never null. */
    public static OrdnanceDelivery of(AirOrdnance load) {
        if (load == AirOrdnance.BEAM) return OrdnanceDelivery.BEAM;
        if (load == AirOrdnance.BOMBS) return OrdnanceDelivery.BOMB;
        if (load == AirOrdnance.AUTOCANNON) return OrdnanceDelivery.SHELL;
        return load.firesContinuously() ? OrdnanceDelivery.SHELL : OrdnanceDelivery.BOMB;
    }

    /**
     * The presentation record for one round of {@code load} leaving
     * {@code body} on the heading {@code noseRadians} and arriving at
     * {@code (impactX, impactY)} after {@code flightTimeSec} in the air.
     */
    public static OrdnanceRelease release(long sourceId, AirOrdnance load, AirBody body,
                                          double noseRadians, float impactX, float impactY,
                                          Faction faction, float flightTimeSec) {
        return new OrdnanceRelease(sourceId, of(load),
                body.x + (float) Math.cos(noseRadians) * MUZZLE_OFFSET_CELLS,
                body.y + (float) Math.sin(noseRadians) * MUZZLE_OFFSET_CELLS,
                impactX, impactY, load.aoeRadiusCells, faction, flightTimeSec);
    }
}
