package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionEquipmentMarketStockPlannerTest {

    private static final EquipmentAcquisitionEligibility.Progress FULL_ACCESS =
            new EquipmentAcquisitionEligibility.Progress(15, 20);

    @Test
    void stockIsStableWithinAMonthAndRotatesAcrossMonths() {
        FactionEquipmentMarketStockPlanner.StockPlan first = plan(
                "independent", "jangala", 3, 12L, false, Set.of());
        FactionEquipmentMarketStockPlanner.StockPlan repeated = plan(
                "independent", "jangala", 3, 12L, false, Set.of());

        assertEquals(first, repeated);
        assertEquals(1, first.marketCardIds().size());
        Set<List<String>> rotations = new HashSet<>();
        for (long month = 0; month < 24; month++) {
            rotations.add(plan("independent", "jangala", 3, month,
                    false, Set.of()).marketCardIds());
        }
        assertTrue(rotations.size() > 1);
    }

    @Test
    void marketSizeLicenseStandingAndOwnershipConstrainStock() {
        FactionEquipmentMarketStockPlanner.StockPlan unlicensed = plan(
                "hegemony", "chicomoztoc", 6, 30L, false, Set.of());
        FactionEquipmentMarketStockPlanner.StockPlan licensed = plan(
                "hegemony", "chicomoztoc", 6, 30L, true, Set.of());

        // Relationships, not census figures: a licence adds a separate shelf on
        // top of ordinary stock and never offers the same card twice. Pinning the
        // literal sizes meant every added item broke this test for no reason.
        assertFalse(unlicensed.marketCardIds().isEmpty(),
                "a sizeable hegemony market should stock something");
        assertTrue(unlicensed.licensedCardIds().isEmpty(),
                "no licence means no licensed shelf");
        assertEquals(unlicensed.marketCardIds(), licensed.marketCardIds(),
                "a licence adds to ordinary stock rather than reshuffling it");
        assertFalse(licensed.licensedCardIds().isEmpty(),
                "standing should open a licensed shelf");
        assertEquals(licensed.marketCardIds().size() + licensed.licensedCardIds().size(),
                new HashSet<>(licensed.allCardIds()).size(),
                "ordinary and licensed stock must be disjoint");

        Set<String> owned = Set.copyOf(licensed.allCardIds());
        FactionEquipmentMarketStockPlanner.StockPlan filtered = plan(
                "hegemony", "chicomoztoc", 6, 30L, true, owned);
        assertTrue(filtered.allCardIds().stream().noneMatch(owned::contains));

        assertFalse(EquipmentStore.hasLicenseAccess(RepLevel.NEUTRAL));
        assertTrue(EquipmentStore.hasLicenseAccess(RepLevel.FAVORABLE));
        assertTrue(EquipmentStore.hasLicenseAccess(RepLevel.COOPERATIVE));
    }

    @Test
    void licenseStockUsesMrbAccessBandsBeforeWeightedSelection() {
        FactionEquipmentMarketStockPlanner.StockPlan opening =
                FactionEquipmentMarketStockPlanner.plan(
                        "hegemony", "chicomoztoc", 6, 30L, true, Set.of(),
                        new EquipmentAcquisitionEligibility.Progress(100, 0));
        FactionEquipmentMarketStockPlanner.StockPlan advanced =
                FactionEquipmentMarketStockPlanner.plan(
                        "hegemony", "chicomoztoc", 6, 30L, true, Set.of(),
                        new EquipmentAcquisitionEligibility.Progress(0, 5));

        assertTrue(opening.licensedCardIds().stream().map(LoadoutCard::parse)
                .allMatch(card -> card.accessTier() == EquipmentAccessTier.COMMON));
        assertEquals(2, advanced.licensedCardIds().size());
        assertTrue(advanced.licensedCardIds().stream()
                .map(LoadoutCard::parse)
                .allMatch(card -> card.accessTier() != EquipmentAccessTier.PRESTIGE));
    }

    @Test
    void openMarketNeverLeaksAdvancedCardsAtHighCompanyStanding() {
        for (long month = 0; month < 100; month++) {
            FactionEquipmentMarketStockPlanner.StockPlan plan =
                    FactionEquipmentMarketStockPlanner.plan(
                            "independent", "access-audit", 8, month, true, Set.of(),
                            new EquipmentAcquisitionEligibility.Progress(100, 100));
            assertTrue(plan.marketCardIds().stream()
                    .map(LoadoutCard::parse)
                    .allMatch(card -> card.accessTier() == EquipmentAccessTier.COMMON));
        }
    }

    @Test
    void authoredWeightsAffectLongRunSelection() {
        Map<String, Integer> appearances = new HashMap<>();
        // Plans are seeded by month, so the count is a fixed sample rather than a chance: 500
        // months separate the heaviest and lightest common cards several times over.
        for (long month = 0; month < 500; month++) {
            String selected = plan("independent", "weight-audit", 3, month,
                    false, Set.of()).marketCardIds().get(0);
            appearances.merge(selected, 1, Integer::sum);
        }

        Map<String, Integer> fielded = new HashMap<>();
        for (FactionEquipmentOffer offer : FactionEquipmentCatalog.offers(
                "independent", FactionEquipmentSource.MARKET)) {
            fielded.put(offer.templateId(), offer.weight(FactionEquipmentSource.MARKET));
        }
        String heaviest = null;
        String lightest = null;
        int heavy = 0;
        int light = Integer.MAX_VALUE;
        for (LoadoutCard card : LoadoutCards.all()) {
            if (card.accessTier() != EquipmentAccessTier.COMMON) continue;
            int weight = card.templateIds().stream()
                    .mapToInt(id -> fielded.getOrDefault(id, 0)).max().orElse(0);
            if (weight <= 0) continue;
            if (weight > heavy) { heavy = weight; heaviest = card.id(); }
            if (weight < light) { light = weight; lightest = card.id(); }
        }
        assertTrue(heavy > light, "the fixture needs two distinct authored weights");
        assertTrue(appearances.getOrDefault(heaviest, 0)
                > appearances.getOrDefault(lightest, 0),
                heaviest + " x" + appearances.getOrDefault(heaviest, 0) + " vs " + lightest + " x" + appearances.getOrDefault(lightest, 0));
    }

    @Test
    void explicitExclusionsProduceNoCards() {
        assertTrue(plan("remnant", "redacted", 8, 50L,
                true, Set.of()).allCardIds().isEmpty());
    }

    private static FactionEquipmentMarketStockPlanner.StockPlan plan(
            String factionId, String marketId, int marketSize, long rotation,
            boolean licensed, Set<String> owned) {
        return FactionEquipmentMarketStockPlanner.plan(
                factionId, marketId, marketSize, rotation, licensed, owned, FULL_ACCESS);
    }
}
