package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * Simulation tuning for a reusable contact-demolition satchel kit.
 *
 * @param wallBreachDamage structural damage the charge does to every wall cell within {@code blastRadius} when it is
 *                         placed on a wall rather than on a hardened unit, or zero for a kit that cannot cut a wall.
 *                         A charge on a unit does no wall damage, as before
 */
public record SatchelChargeSpec(
        float contactRange,
        float plantDuration,
        float fuseSeconds,
        float cooldownSeconds,
        float blastRadius,
        float damage,
        float penetration,
        int wallBreachDamage) implements Serializable {

    /** A kit that cannot cut a wall. */
    public SatchelChargeSpec(float contactRange, float plantDuration, float fuseSeconds, float cooldownSeconds,
                             float blastRadius, float damage, float penetration) {
        this(contactRange, plantDuration, fuseSeconds, cooldownSeconds, blastRadius, damage, penetration, 0);
    }
}
