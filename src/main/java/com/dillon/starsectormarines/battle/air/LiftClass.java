package com.dillon.starsectormarines.battle.air;

/**
 * What a machine asks of the aircraft that carries it to the ground.
 *
 * <p>Weight class seen from the air: a Hound is Light, a Sirocco Medium, a
 * Bulwark Heavy, and the vehicles that share their bay sort the same way. It is
 * deliberately not {@code relativeMass}, which is a manual-drive inertia value
 * and answers a different question. See {@code heavy-equipment-progression.md}.
 */
public enum LiftClass {
    LIGHT,
    MEDIUM,
    HEAVY
}
