package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.catalog.CatalogSource;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionEquipmentCatalogTest {

    private static final CatalogSource CORE =
            new CatalogSource("starsector_marines", "core.faction-equipment.json");
    private static final CatalogSource OC =
            new CatalogSource("example_oc", "oc.faction-equipment.json");

    @Test
    void corePoolsExpressFactionIdentityFallbackAndExplicitExclusion() {
        FactionEquipmentPool hegemony = FactionEquipmentCatalog.resolve("hegemony");
        FactionEquipmentPool triTachyon = FactionEquipmentCatalog.resolve("tritachyon");

        assertTrue(hegemony.offers(
                "equipment-template:weapon.field-rifle:service",
                FactionEquipmentSource.MARKET));
        assertFalse(hegemony.offers(
                "equipment-template:weapon.pulse-rifle:masterwork",
                FactionEquipmentSource.PATRON));
        assertTrue(triTachyon.offers(
                "equipment-template:weapon.pulse-rifle:masterwork",
                FactionEquipmentSource.PATRON));

        FactionEquipmentPool fallback = FactionEquipmentCatalog.resolve("unknown_oc_faction");
        assertEquals("independent", fallback.factionId());
        assertTrue(fallback.offers(
                "equipment-template:weapon.field-rifle:service",
                FactionEquipmentSource.MARKET));

        FactionEquipmentPool remnants = FactionEquipmentCatalog.resolve("remnant");
        assertTrue(remnants.offers().isEmpty());
        assertNotNull(remnants.noPlayerEquipmentReason());
        assertTrue(FactionEquipmentCatalog.hasExactFaction("remnant"));

        assertTrue(FactionEquipmentCatalog.resolve("sindrian_diktat").offers(
                "equipment-template:weapon.pulse-rifle:masterwork",
                FactionEquipmentSource.PATRON));
        assertTrue(FactionEquipmentCatalog.resolve("lions_guard").offers(
                "equipment-template:armor.heavy",
                FactionEquipmentSource.PATRON));
    }

    @Test
    void everyCollectibleCardHasAReachableFactionSourceAndMasterworkIsNotOpenMarket() {
        Set<String> offered = new HashSet<>();
        for (FactionEquipmentPool pool : FactionEquipmentCatalog.installed().entries()) {
            for (FactionEquipmentOffer offer : pool.offers()) offered.add(offer.templateId());
        }

        assertEquals(new HashSet<>(EquipmentTemplateCatalog.all().stream()
                .map(EquipmentTemplateCard::id).toList()), offered);
        assertFalse(FactionEquipmentCatalog.installed().entries().stream()
                .flatMap(pool -> pool.offers(FactionEquipmentSource.MARKET).stream())
                .anyMatch(offer -> offer.template().grade() == EquipmentGrade.MASTERWORK));
        FactionEquipmentCatalog.installed().validateReachability(
                new MarineArmory().ownedEquipmentTemplateIds());
    }

    @Test
    void collectibleWithoutStarterOrFactionSourceFailsReachabilityAudit() throws Exception {
        FactionEquipmentCatalog catalog = new FactionEquipmentCatalog();
        catalog.ingest(singleOffer("independent", "market"), CORE);
        catalog.validateCompleteness();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> catalog.validateReachability(
                        new MarineArmory().ownedEquipmentTemplateIds()));

        assertTrue(failure.getMessage().contains(
                "equipment-template:weapon.field-rifle:surplus"));
    }

    @Test
    void advancedCardWithOnlyAnOpenMarketClaimIsStillStranded() throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(
                "mod", "data", "marines", "faction-equipment.faction-equipment.json")));
        String templateId = "equipment-template:weapon.field-rifle:masterwork";
        for (int factionIndex = 0; factionIndex < root.getJSONArray("factions").length();
             factionIndex++) {
            JSONObject faction = root.getJSONArray("factions").getJSONObject(factionIndex);
            if (!faction.has("offers")) continue;
            for (int offerIndex = 0; offerIndex < faction.getJSONArray("offers").length();
                 offerIndex++) {
                JSONObject offer = faction.getJSONArray("offers").getJSONObject(offerIndex);
                if (templateId.equals(offer.getString("templateId"))) {
                    offer.put("sources", new JSONObject().put("market", 10));
                }
            }
        }
        FactionEquipmentCatalog catalog = new FactionEquipmentCatalog();
        catalog.ingest(root, CORE);
        catalog.validateCompleteness();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> catalog.validateReachability(
                        new MarineArmory().ownedEquipmentTemplateIds()));

        assertTrue(failure.getMessage().contains(templateId));
        assertTrue(failure.getMessage().contains("eventually eligible"));
    }

    @Test
    void providersCanAddNewCardsToCoreFactionsAndOwnOcFactionPools() throws Exception {
        FactionEquipmentCatalog prior = FactionEquipmentCatalog.installed();
        try {
            FactionEquipmentCatalog catalog = new FactionEquipmentCatalog();
            catalog.ingest(new JSONObject("""
                    {
                      "fallbackFactionId": "independent",
                      "factions": [{
                        "factionId": "independent",
                        "offers": [{
                          "templateId": "equipment-template:weapon.field-rifle:service",
                          "sources": {"market": 10}
                        }]
                      }, {
                        "factionId": "hegemony",
                        "offers": [{
                          "templateId": "equipment-template:weapon.field-rifle:milspec",
                          "sources": {"license": 8}
                        }]
                      }]
                    }
                    """), CORE);
            catalog.ingest(new JSONObject("""
                    {
                      "factions": [{
                        "factionId": "hegemony",
                        "offers": [{
                          "templateId": "equipment-template:weapon.smg:milspec",
                          "sources": {"recovery": 6}
                        }]
                      }, {
                        "factionId": "example_oc_faction",
                        "offers": [{
                          "templateId": "equipment-template:weapon.pulse-rifle:service",
                          "sources": {"market": 7, "patron": 3}
                        }]
                      }]
                    }
                    """), OC);
            catalog.validateCompleteness();
            FactionEquipmentCatalog.install(catalog);

            assertEquals(2, FactionEquipmentCatalog.offers(
                    "hegemony", FactionEquipmentSource.LICENSE).size()
                    + FactionEquipmentCatalog.offers(
                    "hegemony", FactionEquipmentSource.RECOVERY).size());
            assertTrue(FactionEquipmentCatalog.resolve("example_oc_faction").offers(
                    "equipment-template:weapon.pulse-rifle:service",
                    FactionEquipmentSource.PATRON));
            assertEquals("example_oc", catalog.sourceOf(
                    "hegemony", "equipment-template:weapon.smg:milspec",
                    FactionEquipmentSource.RECOVERY).modId());
        } finally {
            FactionEquipmentCatalog.install(prior);
        }
    }

    @Test
    void duplicateClaimsNameBothProviders() throws Exception {
        FactionEquipmentCatalog catalog = new FactionEquipmentCatalog();
        catalog.ingest(singleOffer("independent", "market"), CORE);

        JSONException failure = assertThrows(JSONException.class,
                () -> catalog.ingest(singleOffer("independent", "market"), OC));

        assertTrue(failure.getMessage().contains("starsector_marines"));
        assertTrue(failure.getMessage().contains("example_oc"));
        assertTrue(failure.getMessage().contains("weapon.field-rifle:service"));
    }

    @Test
    void malformedSourcesUnknownCardsAndMixedExclusionsFailLoud() throws Exception {
        FactionEquipmentCatalog catalog = new FactionEquipmentCatalog();
        assertThrows(JSONException.class, () -> catalog.ingest(new JSONObject("""
                {"factions": [{"factionId": "bad", "offers": [{
                  "templateId": "equipment-template:weapon.field-rifle:service",
                  "sources": {"blackMarketMaybe": 1}
                }]}]}
                """), OC));
        assertThrows(JSONException.class, () -> catalog.ingest(new JSONObject("""
                {"factions": [{"factionId": "bad", "offers": [{
                  "templateId": "equipment-template:missing",
                  "sources": {"recovery": 1}
                }]}]}
                """), OC));
        assertThrows(JSONException.class, () -> catalog.ingest(new JSONObject("""
                {"factions": [{
                  "factionId": "bad",
                  "noPlayerEquipmentReason": "none",
                  "offers": [{
                    "templateId": "equipment-template:weapon.field-rifle:service",
                    "sources": {"market": 1}
                  }]
                }]}
                """), OC));
    }

    private static JSONObject singleOffer(String factionId, String source) throws Exception {
        return new JSONObject("""
                {
                  "fallbackFactionId": "independent",
                  "factions": [{
                    "factionId": "%s",
                    "offers": [{
                      "templateId": "equipment-template:weapon.field-rifle:service",
                      "sources": {"%s": 10}
                    }]
                  }]
                }
                """.formatted(factionId, source));
    }
}
