package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One round leaving its carrier for a point on the ground — the presentation
 * record of a delivery the simulation has already resolved.
 *
 * <p>Published into {@link EffectsService}'s per-frame drain at the moment the
 * round is released, and drained by whichever host is drawing the battle. It
 * carries geometry and a {@link OrdnanceDelivery} and nothing else: what the
 * round looks like and sounds like is the presentation tier's business, and
 * the simulation neither knows nor reads it.
 *
 * <p>Deliberately <em>not</em> a {@code ShotEvent}. A shot is a fired round the
 * ballistic resolver owns — it makes noise squads hear, it drains morale as it
 * goes past, and it carries a weapon definition. A delivery has already
 * detonated by the time this record exists; posting one as a shot would hand
 * the perception and morale systems a second, parallel source of gunfire that
 * nothing fired.
 *
 * @param sourceId    the entity that released it — the key a held loop voice
 *                    hangs on, so two carriers firing at once stay distinct
 * @param radiusCells the delivered blast radius, which sizes the arrival
 */
public record OrdnanceRelease(long sourceId, OrdnanceDelivery delivery,
                              float fromX, float fromY, float toX, float toY,
                              float radiusCells, Faction faction) {

    public OrdnanceRelease {
        if (delivery == null) throw new IllegalArgumentException("delivery is required");
    }

    /** Bearing from muzzle to impact in degrees, in the FX composition frame. */
    public float bearingDegrees() {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    /** Stable presentation timestamp for deterministic effect composition. */
    public float seed() {
        return fromX * 0.7548777f + fromY * 0.5698403f
                + toX * 0.438289f + toY * 0.327491f;
    }
}
