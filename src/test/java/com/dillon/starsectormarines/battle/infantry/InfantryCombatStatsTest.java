package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfantryCombatStatsTest {

    @Test
    public void fieldRifleIsARealDowngradeFromPulseIssue() {
        assertTrue(MarineWeapon.FIELD_RIFLE.cooldown > MarineWeapon.PULSE_RIFLE.cooldown);
        assertTrue(MarineWeapon.FIELD_RIFLE.accuracy < MarineWeapon.PULSE_RIFLE.accuracy);
        assertTrue(MarineWeapon.FIELD_RIFLE.damage < MarineWeapon.PULSE_RIFLE.damage);
        assertEquals(1, MarineWeapon.FIELD_RIFLE.burstCount);
    }

    @Test
    public void serviceRegularIsTheFamilyBaseline() {
        MarineWeapon family = MarineWeapon.PULSE_RIFLE;
        SoldierProfile regular = SoldierProfile.REGULAR;

        assertEquals(family.range,
                InfantryCombatStats.range(family, EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(family.damage,
                InfantryCombatStats.damage(family, EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(family.accuracy,
                InfantryCombatStats.accuracy(family, EquipmentGrade.SERVICE, regular), 1e-6f);
        assertEquals(family.cooldown,
                InfantryCombatStats.cooldown(family, EquipmentGrade.SERVICE, regular), 1e-6f);
        assertEquals(family.hitSpread,
                InfantryCombatStats.spread(family, EquipmentGrade.SERVICE, regular), 1e-6f);
    }

    @Test
    public void gradeAndProfileImproveWithoutChangingWeaponFamily() {
        SoldierProfile green = new SoldierProfile(SoldierAptitude.LIMITED, 0);
        SoldierProfile elite = new SoldierProfile(SoldierAptitude.EXCEPTIONAL,
                ExperienceTier.ELITE.minimumXp);
        MarineWeapon family = MarineWeapon.DMR;

        float roughAccuracy = InfantryCombatStats.accuracy(
                family, EquipmentGrade.SURPLUS, green);
        float eliteAccuracy = InfantryCombatStats.accuracy(
                family, EquipmentGrade.MASTERWORK, elite);
        assertTrue(eliteAccuracy > roughAccuracy);
        assertTrue(InfantryCombatStats.cooldown(family, EquipmentGrade.MASTERWORK, elite)
                < InfantryCombatStats.cooldown(family, EquipmentGrade.SURPLUS, green));
        assertTrue(InfantryCombatStats.spread(family, EquipmentGrade.MASTERWORK, elite)
                < InfantryCombatStats.spread(family, EquipmentGrade.SURPLUS, green));
    }

    @Test
    public void comparisonStatsRepresentBurstOutputAndRangeFalloff() {
        assertEquals(3f, InfantryCombatStats.volleyDamage(
                MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(3f, InfantryCombatStats.estimatedDps(
                MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE, SoldierProfile.REGULAR), 1e-6f);

        float near = InfantryCombatStats.accuracyAtRangeFraction(
                MarineWeapon.SMG, EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 0.2f);
        float middle = InfantryCombatStats.accuracyAtRangeFraction(
                MarineWeapon.SMG, EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 0.6f);
        float maximum = InfantryCombatStats.accuracyAtRangeFraction(
                MarineWeapon.SMG, EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 1f);
        assertTrue(near > middle);
        assertTrue(middle > maximum);
        assertEquals(0.2f, maximum, 1e-6f);
    }

    @Test
    public void experienceThresholdsAreStable() {
        assertEquals(ExperienceTier.GREEN, ExperienceTier.fromXp(99));
        assertEquals(ExperienceTier.REGULAR, ExperienceTier.fromXp(100));
        assertEquals(ExperienceTier.VETERAN, ExperienceTier.fromXp(350));
        assertEquals(ExperienceTier.ELITE, ExperienceTier.fromXp(800));
    }

    @Test
    public void entitySpecSeedsResolvedTieredStats() {
        SoldierProfile profile = new SoldierProfile(SoldierAptitude.GIFTED, 400);
        EntitySpec spec = new EntitySpec("u", Faction.MARINE, UnitType.MARINE, 0, 0)
                .primaryWeapon(MarineWeapon.SMG, EquipmentGrade.MILSPEC, profile);

        assertEquals(MarineWeapon.SMG, spec.primaryWeapon);
        assertEquals(EquipmentGrade.MILSPEC, spec.equipmentGrade);
        assertEquals(profile, spec.soldierProfile);
        assertEquals(InfantryCombatStats.accuracy(MarineWeapon.SMG,
                EquipmentGrade.MILSPEC, profile), spec.accuracy, 1e-6f);
    }
}
