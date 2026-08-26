package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InfantryLoadoutRollsTest {

    private static final class FixedRandom extends Random {
        private final int[] values;
        private int index;

        private FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(values[index++ % values.length], bound);
        }
    }

    @Test
    public void playerGradeRollCoversProfessionalTiers() {
        assertEquals(EquipmentGrade.MASTERWORK,
                InfantryLoadoutRolls.playerEquipmentGrade(new FixedRandom(0)));
        assertEquals(EquipmentGrade.MILSPEC,
                InfantryLoadoutRolls.playerEquipmentGrade(new FixedRandom(20)));
        assertEquals(EquipmentGrade.SERVICE,
                InfantryLoadoutRolls.playerEquipmentGrade(new FixedRandom(80)));
    }

    @Test
    public void defenderFamilyDoctrineDiffersByTroopType() {
        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID),
                InfantryLoadoutRolls.defenderPrimary(UnitType.MILITIA, new FixedRandom(20)));
        assertEquals(WeaponRegistry.require(WeaponRegistry.DMR_ID),
                InfantryLoadoutRolls.defenderPrimary(UnitType.MILITIA, new FixedRandom(45)));
        assertEquals(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID),
                InfantryLoadoutRolls.defenderPrimary(UnitType.MILITIA, new FixedRandom(70)));

        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID),
                InfantryLoadoutRolls.defenderPrimary(UnitType.MARINE_RED, new FixedRandom(10)));
        assertEquals(WeaponRegistry.require(WeaponRegistry.DMR_ID),
                InfantryLoadoutRolls.defenderPrimary(UnitType.MARINE_RED, new FixedRandom(30)));
    }

    @Test
    public void highRiskCanIssueTopTierEquipment() {
        assertEquals(EquipmentGrade.MASTERWORK,
                InfantryLoadoutRolls.defenderEquipmentGrade(
                        UnitType.MILITIA, RiskLevel.HIGH, new FixedRandom(99)));
        assertEquals(EquipmentGrade.MILSPEC,
                InfantryLoadoutRolls.defenderEquipmentGrade(
                        UnitType.MILITIA, RiskLevel.MEDIUM, new FixedRandom(99)));
        assertEquals(EquipmentGrade.SERVICE,
                InfantryLoadoutRolls.defenderEquipmentGrade(
                        UnitType.MILITIA, RiskLevel.LOW, new FixedRandom(99)));
    }

    @Test
    public void riskAndTroopClassCanReachDistinctProfiles() {
        SoldierProfile greenMilitia = InfantryLoadoutRolls.defenderProfile(
                UnitType.MILITIA, RiskLevel.LOW, new FixedRandom(0, 0, 0));
        SoldierProfile eliteRegular = InfantryLoadoutRolls.defenderProfile(
                UnitType.MARINE_RED, RiskLevel.HIGH, new FixedRandom(99, 99, 0));

        assertEquals(SoldierAptitude.LIMITED, greenMilitia.aptitude());
        assertEquals(ExperienceTier.GREEN, greenMilitia.experienceTier());
        assertEquals(SoldierAptitude.EXCEPTIONAL, eliteRegular.aptitude());
        assertEquals(ExperienceTier.ELITE, eliteRegular.experienceTier());
    }

    @Test
    public void defenderAmrAvailabilityIsExplicitHighRiskDoctrine() {
        MarineLoadout[] high = InfantryLoadoutRolls.defenderSquad(
                4, UnitType.MARINE_RED, RiskLevel.HIGH, new FixedRandom(50));
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), high[3].specialDef());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID).startingAmmo(),
                high[3].secondaryAmmo);

        MarineLoadout[] militia = InfantryLoadoutRolls.defenderSquad(
                4, UnitType.MILITIA, RiskLevel.HIGH, new FixedRandom(50));
        assertNull(militia[3].specialDef(),
                "local militia do not gain the elite anti-materiel issue");
    }
}
