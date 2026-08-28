package com.dillon.starsectormarines.battle.combat;

/**
 * Pure mitigation-armor-and-structure damage calculation shared by live
 * application, combat prediction, and balance tests. There is one damage path;
 * prediction that ignored mitigation would make the AI systematically wrong
 * about exactly the moment the capability exists to create.
 *
 * <p>The caller owns the mutable {@link Resolution} scratch object. That keeps
 * the eventual damage-queue application path allocation-free without making a
 * second, primitive-only implementation of the durability law.</p>
 */
public final class DurabilityModel {

    public static final float MIN_ARMOR_EFFICIENCY = 0.10f;

    private DurabilityModel() {
    }

    /**
     * Resolves post-cover damage into clamped armor and structure loss, with no
     * mitigation in play. Positive armor requires a positive rating. All inputs
     * must be finite and non-negative.
     */
    public static void resolveInto(float postCoverDamage,
                                   float penetration,
                                   float currentArmor,
                                   float armorRating,
                                   float currentStructure,
                                   Resolution out) {
        resolveInto(postCoverDamage, penetration, 0f, currentArmor, armorRating,
                currentStructure, out);
    }

    /**
     * Resolves post-cover damage into clamped mitigated, armor, and structure
     * loss.
     *
     * <p>Mitigation resolves <b>after cover and before armor</b>. Cover is a
     * property of the world the shot crossed; mitigation is a property of the
     * target at that instant; armor is what the target is made of. That order
     * keeps physical protection from being either bypassed or double-counted,
     * and it means a mitigated hit still spends what is left against armor at
     * the ordinary efficiency rather than skipping a step.
     *
     * @param mitigationFraction the fraction of {@code postCoverDamage} the
     *        target's live screen refuses, already resolved against its arc by
     *        the caller. Must be finite and in {@code [0, 1)} — a screen that
     *        refuses everything is an off-switch, not a capability.
     */
    public static void resolveInto(float postCoverDamage,
                                   float penetration,
                                   float mitigationFraction,
                                   float currentArmor,
                                   float armorRating,
                                   float currentStructure,
                                   Resolution out) {
        if (out == null) throw new IllegalArgumentException("out must not be null");
        requireNonNegativeFinite("postCoverDamage", postCoverDamage);
        requireNonNegativeFinite("penetration", penetration);
        requireNonNegativeFinite("mitigationFraction", mitigationFraction);
        requireNonNegativeFinite("currentArmor", currentArmor);
        requireNonNegativeFinite("armorRating", armorRating);
        requireNonNegativeFinite("currentStructure", currentStructure);
        if (mitigationFraction >= 1f) {
            throw new IllegalArgumentException("mitigationFraction must stay below 1");
        }
        if (currentArmor > 0f && armorRating <= 0f) {
            throw new IllegalArgumentException("positive armor requires a positive armorRating");
        }

        out.reset();
        if (postCoverDamage <= 0f || currentStructure <= 0f) return;

        // Refused before armor or structure is consulted, and reported as its
        // own quantity: the same number that tells a player their screen worked
        // tells the balance harness whether the arc asymmetry moved.
        out.mitigatedDamage = postCoverDamage * mitigationFraction;
        float remaining = postCoverDamage - out.mitigatedDamage;
        if (remaining <= 0f) return;

        if (currentArmor <= 0f) {
            out.structureDamage = Math.min(remaining, currentStructure);
            return;
        }

        float efficiency = armorEfficiency(penetration, armorRating);
        float damageToBreak = currentArmor / efficiency;
        if (remaining < damageToBreak) {
            out.armorDamage = Math.min(currentArmor, remaining * efficiency);
            return;
        }

        out.armorDamage = currentArmor;
        out.armorBroken = true;
        float overflow = Math.max(0f, remaining - damageToBreak);
        out.structureDamage = Math.min(overflow, currentStructure);
    }

    /** Returns armor-removal efficiency in [0.10, 1.00]. */
    public static float armorEfficiency(float penetration, float armorRating) {
        requireNonNegativeFinite("penetration", penetration);
        requireNonNegativeFinite("armorRating", armorRating);
        if (armorRating <= 0f) {
            throw new IllegalArgumentException("armorRating must be positive");
        }
        float ratio = Math.min(1f, penetration / armorRating);
        return MIN_ARMOR_EFFICIENCY + (1f - MIN_ARMOR_EFFICIENCY) * ratio;
    }

    private static void requireNonNegativeFinite(String name, float value) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    /** Caller-owned output scratch; reuse is supported and clears stale state. */
    public static final class Resolution {
        private float armorDamage;
        private float structureDamage;
        private float mitigatedDamage;
        private boolean armorBroken;

        public float armorDamage() {
            return armorDamage;
        }

        public float structureDamage() {
            return structureDamage;
        }

        /** Post-cover damage a live screen refused, reaching neither armor nor structure. Never folded into {@link #armorDamage}. */
        public float mitigatedDamage() {
            return mitigatedDamage;
        }

        public boolean armorBroken() {
            return armorBroken;
        }

        private void reset() {
            armorDamage = 0f;
            structureDamage = 0f;
            mitigatedDamage = 0f;
            armorBroken = false;
        }
    }
}

