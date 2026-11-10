package com.dillon.starsectormarines.battle.mech;

/**
 * Frozen frame values one deployed mech takes into battle: what its installed internals
 * (engine, plating, capacitor bank, vent array, and the compartment's {@link MachineSystems}) resolved to in the
 * campaign. Battle reads only these numbers; the internals themselves and their mass budget stay campaign authority.
 *
 * <p>{@link #stock(MechVariant)} is the factory frame every chassis carries, enemy and
 * employer mechs included.</p>
 */
public record MechFrame(float armorCapacity, float moveSpeed,
                        float fluxCapacity, float dissipationPerSecond, MachineSystems systems) {

    /** This frame with no plate on it. */
    public MechFrame withNoArmor() {
        return new MechFrame(0f, moveSpeed, fluxCapacity, dissipationPerSecond, systems);
    }

    /** A frame whose compartment changes nothing beyond these values. */
    public MechFrame(float armorCapacity, float moveSpeed, float fluxCapacity, float dissipationPerSecond) {
        this(armorCapacity, moveSpeed, fluxCapacity, dissipationPerSecond, MachineSystems.NONE);
    }

    public MechFrame {
        if (systems == null) systems = MachineSystems.NONE;
        if (!(armorCapacity >= 0f) || !Float.isFinite(armorCapacity)
                || !(moveSpeed > 0f) || !Float.isFinite(moveSpeed)
                || !(fluxCapacity > 0f) || !Float.isFinite(fluxCapacity)
                || !(dissipationPerSecond >= 0f) || !Float.isFinite(dissipationPerSecond)) {
            throw new IllegalArgumentException("Mech frame values must be finite and positive");
        }
    }

    /**
     * The factory frame: the chassis's own armor and speed, stock flux reservoir and dissipation, and the systems its
     * compartment carries as built ({@link #builtIn}).
     */
    public static MechFrame stock(MechVariant variant) {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        return new MechFrame(variant.armorCapacity, variant.moveSpeed,
                MechFlux.CAPACITY, MechFlux.DISSIPATION_PER_SECOND, builtIn(variant, MachineSystems.NONE));
    }

    /** {@code systems} with what {@code variant}'s compartment carries as built: a crawler's shield emitter. */
    public static MachineSystems builtIn(MechVariant variant, MachineSystems systems) {
        if (systems == null) systems = MachineSystems.NONE;
        return variant != null && variant.shieldEmitter() ? systems.withShieldEmitter() : systems;
    }
}
