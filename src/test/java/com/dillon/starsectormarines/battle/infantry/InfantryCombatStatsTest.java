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
        // Without a magazine the sustained output is one volley per cycle.
        WeaponDef pulse = WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);
        float pulseVolley = pulse.damage() * pulse.burstCount() * pulse.projectilesPerShot();
        assertEquals(pulseVolley, InfantryCombatStats.volleyDamage(pulse, EquipmentGrade.SERVICE), 1e-6f);
        assertEquals(pulseVolley / pulse.cooldown(), InfantryCombatStats.estimatedDps(
                pulse, EquipmentGrade.SERVICE, SoldierProfile.REGULAR), 1e-4f);

        // With one, a load is averaged over its trigger pulls and the reload that ends it, so a
        // fast cycle never reads as free damage.
        WeaponDef smg = WeaponRegistry.require(WeaponRegistry.SMG_ID);
        int rounds = smg.magazine().size(EquipmentGrade.SERVICE);
        int pulls = (rounds + smg.burstCount() - 1) / smg.burstCount();
        float load = (pulls - 1) * smg.cooldown()
                + Math.max(smg.cooldown(), smg.magazine().reloadSeconds());
        assertEquals(smg.damage() * rounds * smg.projectilesPerShot() / load,
                InfantryCombatStats.estimatedDps(smg, EquipmentGrade.SERVICE, SoldierProfile.REGULAR), 1e-4f);
        assertTrue(InfantryCombatStats.estimatedDps(smg, EquipmentGrade.SERVICE, SoldierProfile.REGULAR)
                < InfantryCombatStats.volleyDamage(smg, EquipmentGrade.SERVICE) / smg.cooldown());

        float near = InfantryCombatStats.accuracyAtRangeFraction(smg, EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 0.2f);
        float middle = InfantryCombatStats.accuracyAtRangeFraction(smg, EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 0.6f);
        float maximum = InfantryCombatStats.accuracyAtRangeFraction(smg, EquipmentGrade.SERVICE, SoldierProfile.REGULAR, 1f);
        assertTrue(near > middle);
        assertTrue(middle > maximum);
        assertEquals(smg.accuracy() * (1f - smg.accuracyFalloff()), maximum, 1e-6f);
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
