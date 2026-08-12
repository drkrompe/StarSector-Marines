package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(MarineWeapon.SMG,
                InfantryLoadoutRolls.defenderPrimary(UnitType.MILITIA, new FixedRandom(20)));
        assertEquals(MarineWeapon.DMR,
                InfantryLoadoutRolls.defenderPrimary(UnitType.MILITIA, new FixedRandom(45)));
        assertEquals(MarineWeapon.PULSE_RIFLE,
                InfantryLoadoutRolls.defenderPrimary(UnitType.MILITIA, new FixedRandom(70)));

        assertEquals(MarineWeapon.SMG,
                InfantryLoadoutRolls.defenderPrimary(UnitType.MARINE_RED, new FixedRandom(10)));
        assertEquals(MarineWeapon.DMR,
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
}
