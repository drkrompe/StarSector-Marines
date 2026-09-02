package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile.ForceTier;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.testsupport.GroundRosterDigest;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolityRosterDerivationTest {

    private static final int ROLLS = 4000;

    @Test
    void theDerivedProfileIsThePlayerFactionsOwnDoctrine() {
        GroundRosterProfile profile = derive(GroundProductionQuality.BASIC, PolityDoctrine.NONE);

        assertEquals(PolityRosterDerivation.PROFILE_ID, profile.id());
        assertEquals(Set.of(Factions.PLAYER), profile.factionIds());
        assertEquals(Factions.PLAYER, profile.primaryFactionId());
        assertEquals(UnitType.MILITIA, profile.unitType(ForceTier.BULK));
        assertEquals(UnitType.MARINE_RED, profile.unitType(ForceTier.ELITE));
    }

    /**
     * Law 3. Two points of quality doctrine on a polity with no industry still
     * fields nothing the polity could not have made.
     */
    @Test
    void qualityDoctrineTightensTheTableAndNeverReachesPastTheIndustry() {
        GroundRosterProfile spent = derive(GroundProductionQuality.NONE,
                PolityDoctrine.of(2, 0, 1));

        for (ForceTier tier : ForceTier.values()) {
            for (RiskLevel risk : RiskLevel.values()) {
                assertEquals(Set.of(EquipmentGrade.SURPLUS), grades(spent, tier, risk),
                        tier + "/" + risk + " must field only what NONE can build");
            }
        }
    }

    @Test
    void masterworkArrivesOnlyAtTheTopStepAndItsShareRisesWithQualityPoints() {
        assertFalse(grades(derive(GroundProductionQuality.ADVANCED, PolityDoctrine.of(2, 0, 1)),
                ForceTier.ELITE, RiskLevel.HIGH).contains(EquipmentGrade.MASTERWORK),
                "Milspec is the ceiling at ADVANCED, whatever doctrine says");

        GroundRosterProfile unspent =
                derive(GroundProductionQuality.ADVANCED_FULL, PolityDoctrine.NONE);
        GroundRosterProfile spent =
                derive(GroundProductionQuality.ADVANCED_FULL, PolityDoctrine.of(2, 0, 1));

        int unspentTail = countGrade(unspent, ForceTier.BULK, RiskLevel.LOW,
                EquipmentGrade.MASTERWORK);
        int spentTail = countGrade(spent, ForceTier.BULK, RiskLevel.LOW,
                EquipmentGrade.MASTERWORK);

        assertTrue(unspentTail > 0, "the top step has a Masterwork tail even unspent");
        assertTrue(spentTail > unspentTail,
                "quality points must move weight up the admitted range: "
                        + unspentTail + " -> " + spentTail);
    }

    @Test
    void higherRiskLeansTheSameAdmittedRangeUpwards() {
        GroundRosterProfile profile =
                derive(GroundProductionQuality.ADVANCED_FULL, PolityDoctrine.NONE);

        int low = countGrade(profile, ForceTier.BULK, RiskLevel.LOW, EquipmentGrade.SURPLUS);
        int high = countGrade(profile, ForceTier.BULK, RiskLevel.HIGH, EquipmentGrade.SURPLUS);
        assertTrue(high < low, "a HIGH-risk defence should carry less Surplus: "
                + low + " -> " + high);

        int bulk = countGrade(profile, ForceTier.BULK, RiskLevel.MEDIUM, EquipmentGrade.SURPLUS);
        int elite = countGrade(profile, ForceTier.ELITE, RiskLevel.MEDIUM, EquipmentGrade.SURPLUS);
        assertTrue(elite < bulk, "the elite tier is the bulk tier one band up: "
                + bulk + " -> " + elite);
    }

    @Test
    void theCommonFloorArmsAPolityThatHasBeenReleasedNothing() {
        GroundRosterProfile profile = PolityRosterDerivation.derive(
                List.of(), GroundProductionQuality.BASIC, PolityDoctrine.NONE);

        assertEquals(commonPrimaryWeaponIds(), primaries(profile, ForceTier.BULK));
    }

    @Test
    void aReleasedPrimaryIsIssuedAndAnUnreleasedOneNeverIs() {
        List<EquipmentTemplateCard> floor = commonFloorWithOnlyOnePrimary(
                WeaponRegistry.STARTER_PRIMARY_ID);
        List<EquipmentTemplateCard> released = List.of(EquipmentTemplateCatalog.primary(
                WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MILSPEC));

        GroundRosterProfile profile = PolityRosterDerivation.derive(released, floor,
                GroundProductionQuality.ADVANCED, PolityDoctrine.NONE);

        assertEquals(Set.of(WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.PULSE_RIFLE_ID),
                primaries(profile, ForceTier.BULK));
        assertEquals(Set.of(WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.PULSE_RIFLE_ID),
                primaries(profile, ForceTier.ELITE));
    }

    @Test
    void aReleasedCardGrantsTheItemAndTheIndustryStillSetsTheGrade() {
        List<EquipmentTemplateCard> released = List.of(EquipmentTemplateCatalog.primary(
                WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MASTERWORK));
        GroundRosterProfile profile = PolityRosterDerivation.derive(released,
                GroundProductionQuality.BASIC, PolityDoctrine.of(2, 0, 1));

        assertEquals(Set.of(EquipmentGrade.SURPLUS, EquipmentGrade.SERVICE),
                grades(profile, ForceTier.ELITE, RiskLevel.HIGH),
                "a Masterwork card is a definition, not a Masterwork rifle");
    }

    @Test
    void armourAboveWhatTheColonyCanMakeIsNeverIssued() {
        GroundRosterProfile importing = derive(GroundProductionQuality.NONE, PolityDoctrine.NONE);
        for (MarineArmorCatalogDef pattern : armor(importing, ForceTier.ELITE, RiskLevel.HIGH)) {
            assertEquals(1, pattern.tier(),
                    pattern.id() + " is above what a polity with no industry can make");
        }

        GroundRosterProfile basic = derive(GroundProductionQuality.BASIC, PolityDoctrine.NONE);
        Set<Integer> tiers = new TreeSet<>();
        for (MarineArmorCatalogDef pattern : armor(basic, ForceTier.ELITE, RiskLevel.HIGH)) {
            tiers.add(pattern.tier());
        }
        assertEquals(Set.of(1, 2), tiers, "Heavy Industry reaches tier 2 and stops");
    }

    @Test
    void specialsAreIssuedMoreOftenAtHigherRisk() {
        GroundRosterProfile profile = derive(GroundProductionQuality.BASIC, PolityDoctrine.NONE);

        int low = countSpecials(profile, ForceTier.BULK, RiskLevel.LOW);
        int high = countSpecials(profile, ForceTier.BULK, RiskLevel.HIGH);
        int elite = countSpecials(profile, ForceTier.ELITE, RiskLevel.HIGH);

        assertTrue(low > 0 && low < ROLLS / 2, "most of a LOW-risk militia carries nothing extra");
        assertTrue(high > low, "risk raises the special share: " + low + " -> " + high);
        assertTrue(elite > high, "so does the elite tier: " + high + " -> " + elite);
    }

    @Test
    void aLanceNeedsBothTheDoctrinePointAndTheIndustry() {
        assertEquals(List.of(), derive(GroundProductionQuality.NONE,
                PolityDoctrine.of(0, 0, 1)).heavySupport());
        assertEquals(List.of(), derive(GroundProductionQuality.ADVANCED_FULL,
                PolityDoctrine.of(2, 1, 0)).heavySupport());
        assertEquals(List.of(MechVariant.BULWARK), derive(GroundProductionQuality.BASIC,
                PolityDoctrine.of(0, 0, 1)).heavySupport());
        assertEquals(List.of(MechVariant.BULWARK), derive(GroundProductionQuality.ADVANCED_FULL,
                PolityDoctrine.of(0, 1, 2)).heavySupport());
    }

    @Test
    void aProfileWithNoLanceIsLegalForEveryConsumerOfTheSupportCycle() {
        GroundRosterProfile profile = derive(GroundProductionQuality.NONE, PolityDoctrine.NONE);

        assertTrue(profile.heavySupport().isEmpty());
        assertEquals(List.of(), profile.heavySupportCycle(6));
        assertEquals(List.of(), profile.heavySupportCycle(0));
    }

    @Test
    void theSameInputsAlwaysDeriveTheSameProfile() {
        List<EquipmentTemplateCard> released = List.of(
                EquipmentTemplateCatalog.armor("armor.combat"),
                EquipmentTemplateCatalog.special("special.rocket-launcher"));

        List<EquipmentTemplateCard> shuffled = new ArrayList<>(released);
        Collections.reverse(shuffled);

        String first = GroundRosterDigest.of(PolityRosterDerivation.derive(released,
                GroundProductionQuality.ADVANCED, PolityDoctrine.of(1, 1, 1)));
        String second = GroundRosterDigest.of(PolityRosterDerivation.derive(shuffled,
                GroundProductionQuality.ADVANCED, PolityDoctrine.of(1, 1, 1)));

        assertEquals(first, second);
    }

    @Test
    void aReleasedAdvancedPatternIsIssuedOnceTheIndustryCanBuildIt() {
        List<EquipmentTemplateCard> released =
                List.of(EquipmentTemplateCatalog.armor("armor.combat"));

        Set<String> withoutIndustry = armorIds(PolityRosterDerivation.derive(released,
                GroundProductionQuality.BASIC, PolityDoctrine.NONE));
        Set<String> withIndustry = armorIds(PolityRosterDerivation.derive(released,
                GroundProductionQuality.ADVANCED, PolityDoctrine.NONE));
        Set<String> unreleased = armorIds(PolityRosterDerivation.derive(List.of(),
                GroundProductionQuality.ADVANCED, PolityDoctrine.NONE));

        assertFalse(withoutIndustry.contains("armor.combat"),
                "a tier-3 pattern is knowledge a Heavy Industry cannot act on");
        assertTrue(withIndustry.contains("armor.combat"));
        assertFalse(unreleased.contains("armor.combat"),
                "and it is never issued by a polity nobody released it to");
    }

    private static GroundRosterProfile derive(GroundProductionQuality quality,
                                              PolityDoctrine doctrine) {
        return PolityRosterDerivation.derive(List.of(), quality, doctrine);
    }

    private static Set<String> primaries(GroundRosterProfile profile, ForceTier tier) {
        Random rng = new Random(4);
        Set<String> ids = new TreeSet<>();
        for (int roll = 0; roll < ROLLS; roll++) {
            ids.add(profile.issue(tier).pickPrimaryDef(rng).id);
        }
        return ids;
    }

    private static Set<EquipmentGrade> grades(GroundRosterProfile profile, ForceTier tier,
                                              RiskLevel risk) {
        Random rng = new Random(5);
        Set<EquipmentGrade> seen = new LinkedHashSet<>();
        for (int roll = 0; roll < ROLLS; roll++) {
            seen.add(profile.issue(tier).pickGrade(risk, rng));
        }
        return seen;
    }

    private static int countGrade(GroundRosterProfile profile, ForceTier tier, RiskLevel risk,
                                  EquipmentGrade grade) {
        Random rng = new Random(6);
        int count = 0;
        for (int roll = 0; roll < ROLLS; roll++) {
            if (profile.issue(tier).pickGrade(risk, rng) == grade) count++;
        }
        return count;
    }

    private static Set<MarineArmorCatalogDef> armor(GroundRosterProfile profile, ForceTier tier,
                                                    RiskLevel risk) {
        Random rng = new Random(7);
        Set<MarineArmorCatalogDef> seen = new LinkedHashSet<>();
        for (int roll = 0; roll < ROLLS; roll++) {
            seen.add(profile.issue(tier).pickArmorDef(risk, rng));
        }
        return seen;
    }

    private static Set<String> armorIds(GroundRosterProfile profile) {
        Set<String> ids = new TreeSet<>();
        for (MarineArmorCatalogDef pattern : armor(profile, ForceTier.ELITE, RiskLevel.HIGH)) {
            ids.add(pattern.id());
        }
        return ids;
    }

    private static int countSpecials(GroundRosterProfile profile, ForceTier tier, RiskLevel risk) {
        Random rng = new Random(8);
        int count = 0;
        for (int roll = 0; roll < ROLLS; roll++) {
            SpecialEquipmentDef special = profile.issue(tier).pickSpecialDef(risk, rng);
            if (special != null) count++;
        }
        return count;
    }

    private static Set<String> commonPrimaryWeaponIds() {
        Set<String> ids = new TreeSet<>();
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (card.kind() == EquipmentTemplateCard.Kind.PRIMARY
                    && card.accessTier() == EquipmentAccessTier.COMMON) {
                ids.add(card.equipmentId());
            }
        }
        return ids;
    }

    /**
     * The Common band with every primary but one struck out. The shipped
     * catalog authors surplus and service of every marine primary as Common, so
     * a floor taken whole cannot show a weapon being absent; this is the
     * restricted floor {@code derive}'s test seam exists for.
     */
    private static List<EquipmentTemplateCard> commonFloorWithOnlyOnePrimary(String weaponId) {
        List<EquipmentTemplateCard> floor = new ArrayList<>();
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (card.accessTier() != EquipmentAccessTier.COMMON) continue;
            if (card.kind() == EquipmentTemplateCard.Kind.PRIMARY
                    && !card.equipmentId().equals(weaponId)) {
                continue;
            }
            floor.add(card);
        }
        return floor;
    }
}
