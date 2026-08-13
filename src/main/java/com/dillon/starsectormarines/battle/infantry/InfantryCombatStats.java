package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.RangeFalloff;

/** Pure resolver for family × equipment grade × individual profile stats. */
public final class InfantryCombatStats {

    private InfantryCombatStats() {}

    public static float range(MarineWeapon family, EquipmentGrade grade) {
        return family.range * grade.rangeMult;
    }

    public static float damage(MarineWeapon family, EquipmentGrade grade) {
        return family.damage * grade.damageMult;
    }

    public static float accuracy(MarineWeapon family, EquipmentGrade grade,
                                 SoldierProfile profile) {
        ExperienceTier exp = profile.experienceTier();
        return clamp01(family.accuracy * grade.accuracyMult
                * profile.aptitude().accuracyMult * exp.accuracyMult);
    }

    public static float cooldown(MarineWeapon family, EquipmentGrade grade,
                                 SoldierProfile profile) {
        return family.cooldown * grade.cooldownMult
                * profile.experienceTier().cooldownMult;
    }

    public static float spread(MarineWeapon family, EquipmentGrade grade,
                               SoldierProfile profile) {
        return family.hitSpread * grade.spreadMult
                * profile.aptitude().spreadMult
                * profile.experienceTier().spreadMult;
    }

    /** Total raw damage released by one trigger pull, before hit rolls. */
    public static float volleyDamage(MarineWeapon family, EquipmentGrade grade) {
        return damage(family, grade) * family.burstCount;
    }

    /** Sustained raw output based on the interval between trigger pulls. */
    public static float estimatedDps(MarineWeapon family, EquipmentGrade grade,
                                     SoldierProfile profile) {
        return volleyDamage(family, grade) / Math.max(0.01f, cooldown(family, grade, profile));
    }

    /** Standing hit chance at a fraction of this weapon's effective range. */
    public static float accuracyAtRangeFraction(MarineWeapon family, EquipmentGrade grade,
                                                SoldierProfile profile, float rangeFraction) {
        float effectiveRange = range(family, grade);
        return clamp01(RangeFalloff.accuracy(accuracy(family, grade, profile),
                family.accuracyFalloff, effectiveRange * clamp01(rangeFraction), effectiveRange));
    }

    /** Marksmanship portion shared by primary and secondary direct fire. */
    public static float shooterAccuracyMult(SoldierProfile profile) {
        return profile.aptitude().accuracyMult * profile.experienceTier().accuracyMult;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
