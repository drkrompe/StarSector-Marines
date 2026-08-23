package com.dillon.starsectormarines.battle.combat;

/**
 * Pure armor-and-structure damage calculation shared by live application,
 * combat prediction, and balance tests.
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
     * Resolves post-cover damage into clamped armor and structure loss.
     * Positive armor requires a positive rating. All inputs must be finite and
     * non-negative.
     */
    public static void resolveInto(float postCoverDamage,
                                   float penetration,
                                   float currentArmor,
                                   float armorRating,
                                   float currentStructure,
                                   Resolution out) {
        if (out == null) throw new IllegalArgumentException("out must not be null");
        requireNonNegativeFinite("postCoverDamage", postCoverDamage);
        requireNonNegativeFinite("penetration", penetration);
        requireNonNegativeFinite("currentArmor", currentArmor);
        requireNonNegativeFinite("armorRating", armorRating);
        requireNonNegativeFinite("currentStructure", currentStructure);
        if (currentArmor > 0f && armorRating <= 0f) {
            throw new IllegalArgumentException("positive armor requires a positive armorRating");
        }

        out.reset();
        if (postCoverDamage <= 0f || currentStructure <= 0f) return;
        if (currentArmor <= 0f) {
            out.structureDamage = Math.min(postCoverDamage, currentStructure);
            return;
        }

        float efficiency = armorEfficiency(penetration, armorRating);
        float damageToBreak = currentArmor / efficiency;
        if (postCoverDamage < damageToBreak) {
            out.armorDamage = Math.min(currentArmor, postCoverDamage * efficiency);
            return;
        }

        out.armorDamage = currentArmor;
        out.armorBroken = true;
        float overflow = Math.max(0f, postCoverDamage - damageToBreak);
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
        private boolean armorBroken;

        public float armorDamage() {
            return armorDamage;
        }

        public float structureDamage() {
            return structureDamage;
        }

        public boolean armorBroken() {
            return armorBroken;
        }

        private void reset() {
            armorDamage = 0f;
            structureDamage = 0f;
            armorBroken = false;
        }
    }
}

