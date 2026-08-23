package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** Simulation tuning for a reusable contact-demolition satchel kit. */
public record SatchelChargeSpec(
        float contactRange,
        float plantDuration,
        float fuseSeconds,
        float cooldownSeconds,
        float blastRadius,
        float damage,
        float penetration) implements Serializable {
}
