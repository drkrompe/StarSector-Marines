package com.dillon.starsectormarines.battle.infantry;

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

    /** Marksmanship portion shared by primary and secondary direct fire. */
    public static float shooterAccuracyMult(SoldierProfile profile) {
        return profile.aptitude().accuracyMult * profile.experienceTier().accuracyMult;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
