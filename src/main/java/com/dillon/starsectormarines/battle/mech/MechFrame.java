package com.dillon.starsectormarines.battle.mech;

/**
 * Frozen frame values one deployed mech takes into battle: what its installed internals
 * (engine, plating, capacitor bank, vent array) resolved to in the campaign. Battle reads only these
 * numbers; the internals themselves and their mass budget stay campaign authority.
 *
 * <p>{@link #stock(MechVariant)} is the factory frame every chassis carries, enemy and
 * employer mechs included.</p>
 */
public record MechFrame(float armorCapacity, float moveSpeed,
                        float fluxCapacity, float dissipationPerSecond) {

    public MechFrame {
        if (!(armorCapacity >= 0f) || !Float.isFinite(armorCapacity)
                || !(moveSpeed > 0f) || !Float.isFinite(moveSpeed)
                || !(fluxCapacity > 0f) || !Float.isFinite(fluxCapacity)
                || !(dissipationPerSecond >= 0f) || !Float.isFinite(dissipationPerSecond)) {
            throw new IllegalArgumentException("Mech frame values must be finite and positive");
        }
    }

    /** The factory frame: the chassis's own armor and speed, stock flux reservoir and dissipation. */
    public static MechFrame stock(MechVariant variant) {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        return new MechFrame(variant.armorCapacity, variant.moveSpeed,
                MechFlux.CAPACITY, MechFlux.DISSIPATION_PER_SECOND);
    }
}
