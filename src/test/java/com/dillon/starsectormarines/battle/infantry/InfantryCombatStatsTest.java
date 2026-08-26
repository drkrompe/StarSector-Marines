package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfantryCombatStatsTest {

    @Test
    public void fieldRifleIsARealDowngradeFromPulseIssue() {
        assertTrue(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).cooldown() > WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).cooldown());
        assertTrue(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).accuracy() < WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).accuracy());
        assertTrue(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).accuracyFalloff() > WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).accuracyFalloff());
        assertEquals(1, WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).burstCount());
        // The downgrade is in sustained output, not per-round damage: recruit
        // issue fires a heavier round precisely because a single-shot weapon
        // that also lost on damage could not kill anything (S1 measured 0
        // kills in 120 trials against an unarmored marine).
        assertTrue(InfantryCombatStats.estimatedDps(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                        EquipmentGrade.SERVICE, SoldierProfile.REGULAR)
                < InfantryCombatStats.estimatedDps(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID),
                        EquipmentGrade.SERVICE, SoldierProfile.REGULAR));
    }

    @Test
    public void serviceRegularIsTheFamilyBaseline() {
        WeaponDef family = WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);
        SoldierProfile regular = SoldierProfile.REGULAR;

        assertEquals(family.range(),
                InfantryCombatStats.range(family, EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(family.damage(),
                InfantryCombatStats.damage(family, EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(family.accuracy(),
                InfantryCombatStats.accuracy(family, EquipmentGrade.SERVICE, regular), 1e-6f);
        assertEquals(family.cooldown(),
                InfantryCombatStats.cooldown(family, EquipmentGrade.SERVICE, regular), 1e-6f);
        assertEquals(family.hitSpread(),
                InfantryCombatStats.spread(family, EquipmentGrade.SERVICE, regular), 1e-6f);
    }

    @Test
    public void gradeAndProfileImproveWithoutChangingWeaponFamily() {
        SoldierProfile green = new SoldierProfile(SoldierAptitude.LIMITED, 0);
        SoldierProfile elite = new SoldierProfile(SoldierAptitude.EXCEPTIONAL,
                ExperienceTier.ELITE.minimumXp);
        WeaponDef family = WeaponRegistry.require(WeaponRegistry.DMR_ID);

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
        assertEquals(27f, InfantryCombatStats.volleyDamage(
                WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(27f, InfantryCombatStats.estimatedDps(
                WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.SERVICE, SoldierProfile.REGULAR), 1e-6f);
        assertEquals(18f, InfantryCombatStats.volleyDamage(
                WeaponRegistry.require(WeaponRegistry.SMG_ID), EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(24f, InfantryCombatStats.estimatedDps(
                WeaponRegistry.require(WeaponRegistry.SMG_ID), EquipmentGrade.SERVICE, SoldierProfile.REGULAR), 1e-6f);
        assertEquals(46.4f, InfantryCombatStats.volleyDamage(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(29f, InfantryCombatStats.estimatedDps(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.SERVICE, SoldierProfile.REGULAR), 1e-6f);

        float near = InfantryCombatStats.accuracyAtRangeFraction(
                WeaponRegistry.require(WeaponRegistry.SMG_ID), EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 0.2f);
        float middle = InfantryCombatStats.accuracyAtRangeFraction(
                WeaponRegistry.require(WeaponRegistry.SMG_ID), EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 0.6f);
        float maximum = InfantryCombatStats.accuracyAtRangeFraction(
                WeaponRegistry.require(WeaponRegistry.SMG_ID), EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 1f);
        assertTrue(near > middle);
        assertTrue(middle > maximum);
        assertEquals(0.17f, maximum, 1e-6f);
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
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.SMG_ID), EquipmentGrade.MILSPEC, profile);

        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID), spec.primaryWeaponDef);
        assertEquals(EquipmentGrade.MILSPEC, spec.equipmentGrade);
        assertEquals(profile, spec.soldierProfile);
        assertEquals(InfantryCombatStats.accuracy(WeaponRegistry.require(WeaponRegistry.SMG_ID),
                EquipmentGrade.MILSPEC, profile), spec.accuracy, 1e-6f);
    }
}
