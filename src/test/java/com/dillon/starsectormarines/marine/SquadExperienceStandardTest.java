package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Coverage for issued experience bands ({@code progression-nouns.md}).
 *
 * <p>The property under test throughout: the band is a function of armour the
 * player can see, and of nothing else — not of what the marine has personally
 * done, and not of weapon grade.
 */
class SquadExperienceStandardTest {

    private static MarineSoldier soldierWearing(MarineArmorPattern armor) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSoldier soldier = roster.activeSoldiers().get(0);
        soldier.setArmor(armor);
        return soldier;
    }

    @Test
    void theArmourPatternDecidesTheBand() {
        assertEquals(ExperienceTier.GREEN,
                SquadExperienceStandard.bandFor(soldierWearing(MarineArmorPattern.ARMORLESS)),
                "tier 1 field kit fields recruits");
        assertEquals(ExperienceTier.REGULAR,
                SquadExperienceStandard.bandFor(soldierWearing(MarineArmorPattern.BLUE_SCOUT)),
                "tier 2");
        assertEquals(ExperienceTier.VETERAN,
                SquadExperienceStandard.bandFor(soldierWearing(MarineArmorPattern.CHARCOAL)),
                "tier 3");
        assertEquals(ExperienceTier.ELITE,
                SquadExperienceStandard.bandFor(soldierWearing(MarineArmorPattern.RED_ELITE)),
                "tier 4 heavy battlesuits field an elite element");
    }

    @Test
    void allFourBandsAreReachableFromAuthoredArmourAlone() {
        Set<ExperienceTier> reached = EnumSet.noneOf(ExperienceTier.class);
        for (MarineArmorPattern pattern : MarineArmorPattern.values()) {
            reached.add(SquadExperienceStandard.bandFor(soldierWearing(pattern)));
        }
        assertEquals(EnumSet.allOf(ExperienceTier.class), reached,
                "no promotion rule is needed: the four authored tiers cover the four bands");
    }

    @Test
    void weaponGradeDoesNotMoveTheBand() {
        MarineSoldier surplus = soldierWearing(MarineArmorPattern.CHARCOAL);
        MarineSoldier masterwork = soldierWearing(MarineArmorPattern.CHARCOAL);
        surplus.setPrimary(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SURPLUS);
        masterwork.setPrimary(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.MASTERWORK);

        // Primary access tier IS a strict function of grade in the authored
        // catalog, so sourcing the band there would have fused grade and profile.
        assertNotEquals(
                EquipmentTemplateCatalog.primary(
                        WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SURPLUS).accessTier(),
                EquipmentTemplateCatalog.primary(
                        WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.MASTERWORK).accessTier(),
                "fixture assumption: these grades sit in different access tiers");
        assertEquals(SquadExperienceStandard.bandFor(surplus),
                SquadExperienceStandard.bandFor(masterwork),
                "grade supplies weapon quality; it must not also supply the person");
    }

    @Test
    void theProfileKeepsPersistedAptitudeAndIgnoresAccumulatedXp() {
        MarineSoldier soldier = soldierWearing(MarineArmorPattern.CHARCOAL);
        soldier.addExperience(5000);

        assertEquals(soldier.aptitude(),
                SquadExperienceStandard.profileFor(soldier).aptitude(),
                "aptitude stays innate and per marine");
        assertEquals(ExperienceTier.VETERAN,
                SquadExperienceStandard.profileFor(soldier).experienceTier(),
                "a personal XP hoard cannot outrank what the company issued");
    }

    @Test
    void anUnresolvableSuitFieldsRecruitsRatherThanThrowing() {
        assertEquals(ExperienceTier.GREEN, SquadExperienceStandard.bandFor(null));
        assertEquals(ExperienceTier.GREEN, SquadExperienceStandard.bandForArmorTier(0));
    }
}
