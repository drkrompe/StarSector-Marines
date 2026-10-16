package com.dillon.starsectormarines.battle.mech;

/**
 * Frozen frame values one deployed mech takes into battle: what its installed internals
 * (engine, plating, heat sink, cooler) resolved to in the campaign. Battle reads only these
 * numbers; the internals themselves and their mass budget stay campaign authority.
 *
 * <p>{@link #stock(MechVariant)} is the factory frame every chassis carries, enemy and
 * employer mechs included.</p>
 */
public record MechFrame(float armorCapacity, float moveSpeed,
                        float heatCapacity, float coolingPerSecond) {

    public MechFrame {
        if (!(armorCapacity >= 0f) || !Float.isFinite(armorCapacity)
                || !(moveSpeed > 0f) || !Float.isFinite(moveSpeed)
                || !(heatCapacity > 0f) || !Float.isFinite(heatCapacity)
                || !(coolingPerSecond >= 0f) || !Float.isFinite(coolingPerSecond)) {
            throw new IllegalArgumentException("Mech frame values must be finite and positive");
        }
    }

    /** The factory frame: the chassis's own armor and speed, stock heat reservoir and cooling. */
    public static MechFrame stock(MechVariant variant) {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        return new MechFrame(variant.armorCapacity, variant.moveSpeed,
                MechHeat.CAPACITY, MechHeat.COOLING_PER_SECOND);
    }
}
