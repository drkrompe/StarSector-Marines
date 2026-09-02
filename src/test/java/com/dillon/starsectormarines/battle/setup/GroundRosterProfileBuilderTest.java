package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.testsupport.GroundRosterDigest;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The builder and the catalog parser are one construction path, so a derived
 * doctrine cannot satisfy weaker rules than an authored one. This builds the
 * same small profile both ways and asserts they issue identically.
 */
class GroundRosterProfileBuilderTest {

    private static final String FIXTURE = """
            {
              "fallbackProfile": "roster.fixture",
              "profiles": [{
                "id": "roster.fixture",
                "factionIds": ["fixture_faction", "fixture_ally"],
                "heavySupport": ["bulwark", "hound"],
                "bulk": {
                  "unitType": "MILITIA",
                  "primaries": [
                    {"id": "weapon.field-rifle", "weight": 40},
                    {"id": "weapon.smg", "weight": 10}
                  ],
                  "gradesByRisk": {
                    "low": [{"id": "surplus", "weight": 70}, {"id": "service", "weight": 30}],
                    "medium": [{"id": "surplus", "weight": 45}, {"id": "service", "weight": 55}],
                    "high": [{"id": "service", "weight": 80}, {"id": "milspec", "weight": 20}]
                  },
                  "armorByRisk": {
                    "low": [{"id": "armor.field-fatigues", "weight": 60},
                            {"id": "armor.militia", "weight": 40}],
                    "medium": [{"id": "armor.militia", "weight": 100}],
                    "high": [{"id": "armor.militia", "weight": 70},
                             {"id": "armor.combat", "weight": 30}]
                  },
                  "specialsByRisk": {
                    "low": [{"id": "none", "weight": 90},
                            {"id": "special.frag-grenade", "weight": 10}],
                    "medium": [{"id": "none", "weight": 75},
                               {"id": "special.frag-grenade", "weight": 25}],
                    "high": [{"id": "none", "weight": 60},
                             {"id": "special.frag-grenade", "weight": 40}]
                  }
                },
                "elite": {
                  "unitType": "MARINE_RED",
                  "primaries": [{"id": "weapon.pulse-rifle", "weight": 100}],
                  "gradesByRisk": {
                    "low": [{"id": "service", "weight": 100}],
                    "medium": [{"id": "service", "weight": 60}, {"id": "milspec", "weight": 40}],
                    "high": [{"id": "milspec", "weight": 100}]
                  },
                  "armorByRisk": {
                    "low": [{"id": "armor.combat", "weight": 100}],
                    "medium": [{"id": "armor.combat", "weight": 100}],
                    "high": [{"id": "armor.combat", "weight": 60},
                             {"id": "armor.line", "weight": 40}]
                  },
                  "specialsByRisk": {
                    "low": [{"id": "none", "weight": 50},
                            {"id": "special.smoke-grenade", "weight": 50}],
                    "medium": [{"id": "none", "weight": 50},
                               {"id": "special.smoke-grenade", "weight": 50}],
                    "high": [{"id": "special.smoke-grenade", "weight": 100}]
                  }
                }
              }]
            }
            """;

    @Test
    void aBuiltProfileAndAParsedOneIssueIdentically() throws Exception {
        GroundRosterProfile parsed = parseFixture();
        GroundRosterProfile built = buildFixture();

        assertEquals(parsed.id(), built.id());
        assertEquals(parsed.factionIds(), built.factionIds());
        assertEquals(parsed.primaryFactionId(), built.primaryFactionId());
        assertEquals(parsed.heavySupport(), built.heavySupport());
        assertEquals(GroundRosterDigest.of(parsed), GroundRosterDigest.of(built));
    }

    @Test
    void theBuilderRefusesEverythingTheParserRefuses() {
        assertThrows(IllegalArgumentException.class, () -> GroundRosterProfile.builder(" "));
        assertThrows(IllegalArgumentException.class,
                () -> GroundRosterProfile.Issue.builder(UnitType.CIVILIAN));
        assertThrows(IllegalArgumentException.class,
                () -> GroundRosterProfile.Issue.builder(UnitType.MILITIA)
                        .primary(WeaponRegistry.require(WeaponRegistry.MECH_CHAINGUN_ID), 10));

        GroundRosterProfile.Issue issue = bulkIssue();
        assertThrows(IllegalArgumentException.class, () -> GroundRosterProfile
                .builder("roster.fixture").bulk(issue).elite(issue).build());
        assertThrows(IllegalArgumentException.class, () -> GroundRosterProfile
                .builder("roster.fixture").factionId("fixture_faction").bulk(issue).build());
    }

    @Test
    void aRiskLeftOutOfATableIsReportedRatherThanDefaulted() {
        GroundRosterProfile.Issue.Builder builder =
                GroundRosterProfile.Issue.builder(UnitType.MILITIA)
                        .primary(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), 10)
                        .grade(RiskLevel.LOW, EquipmentGrade.SURPLUS, 10)
                        .armor(RiskLevel.LOW, MarineArmorCatalogRegistry.require("armor.militia"), 10)
                        .special(RiskLevel.LOW, null, 10);

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(failure.getMessage().contains("MEDIUM"), failure.getMessage());
    }

    private static GroundRosterProfile parseFixture() throws Exception {
        GroundRosterRegistry prior = GroundRosterRegistry.installed();
        try {
            GroundRosterRegistry fixture = new GroundRosterRegistry();
            fixture.ingest(new JSONObject(FIXTURE));
            fixture.validateCompleteness();
            GroundRosterRegistry.install(fixture);
            return GroundRosterRegistry.requireProfile("roster.fixture");
        } finally {
            GroundRosterRegistry.install(prior);
        }
    }

    private static GroundRosterProfile buildFixture() {
        return GroundRosterProfile.builder("roster.fixture")
                .factionIds(List.of("fixture_faction", "fixture_ally"))
                .bulk(bulkIssue())
                .elite(eliteIssue())
                .heavySupport(List.of(MechVariant.BULWARK, MechVariant.HOUND))
                .build();
    }

    private static GroundRosterProfile.Issue bulkIssue() {
        return GroundRosterProfile.Issue.builder(UnitType.MILITIA)
                .primary(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), 40)
                .primary(WeaponRegistry.require(WeaponRegistry.SMG_ID), 10)
                .grade(RiskLevel.LOW, EquipmentGrade.SURPLUS, 70)
                .grade(RiskLevel.LOW, EquipmentGrade.SERVICE, 30)
                .grade(RiskLevel.MEDIUM, EquipmentGrade.SURPLUS, 45)
                .grade(RiskLevel.MEDIUM, EquipmentGrade.SERVICE, 55)
                .grade(RiskLevel.HIGH, EquipmentGrade.SERVICE, 80)
                .grade(RiskLevel.HIGH, EquipmentGrade.MILSPEC, 20)
                .armor(RiskLevel.LOW, armor("armor.field-fatigues"), 60)
                .armor(RiskLevel.LOW, armor("armor.militia"), 40)
                .armor(RiskLevel.MEDIUM, armor("armor.militia"), 100)
                .armor(RiskLevel.HIGH, armor("armor.militia"), 70)
                .armor(RiskLevel.HIGH, armor("armor.combat"), 30)
                .special(RiskLevel.LOW, null, 90)
                .special(RiskLevel.LOW, special("special.frag-grenade"), 10)
                .special(RiskLevel.MEDIUM, null, 75)
                .special(RiskLevel.MEDIUM, special("special.frag-grenade"), 25)
                .special(RiskLevel.HIGH, null, 60)
                .special(RiskLevel.HIGH, special("special.frag-grenade"), 40)
                .build();
    }

    private static GroundRosterProfile.Issue eliteIssue() {
        return GroundRosterProfile.Issue.builder(UnitType.MARINE_RED)
                .primary(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), 100)
                .grade(RiskLevel.LOW, EquipmentGrade.SERVICE, 100)
                .grade(RiskLevel.MEDIUM, EquipmentGrade.SERVICE, 60)
                .grade(RiskLevel.MEDIUM, EquipmentGrade.MILSPEC, 40)
                .grade(RiskLevel.HIGH, EquipmentGrade.MILSPEC, 100)
                .armor(RiskLevel.LOW, armor("armor.combat"), 100)
                .armor(RiskLevel.MEDIUM, armor("armor.combat"), 100)
                .armor(RiskLevel.HIGH, armor("armor.combat"), 60)
                .armor(RiskLevel.HIGH, armor("armor.line"), 40)
                .special(RiskLevel.LOW, null, 50)
                .special(RiskLevel.LOW, special("special.smoke-grenade"), 50)
                .special(RiskLevel.MEDIUM, null, 50)
                .special(RiskLevel.MEDIUM, special("special.smoke-grenade"), 50)
                .special(RiskLevel.HIGH, special("special.smoke-grenade"), 100)
                .build();
    }

    private static MarineArmorCatalogDef armor(String id) {
        return MarineArmorCatalogRegistry.require(id);
    }

    private static SpecialEquipmentDef special(String id) {
        return SpecialEquipmentRegistry.require(id);
    }
}
