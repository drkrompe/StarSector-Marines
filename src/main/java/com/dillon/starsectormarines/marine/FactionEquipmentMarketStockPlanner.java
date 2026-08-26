package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pure monthly selection policy for faction-authored equipment-card stock. */
final class FactionEquipmentMarketStockPlanner {

    private static final int MAX_MARKET_SLOTS = 4;
    private static final int MAX_LICENSE_SLOTS = 2;

    private FactionEquipmentMarketStockPlanner() {}

    static StockPlan plan(String factionId, String marketId, int marketSize,
                          long rotation, boolean hasLicenseAccess,
                          Set<String> ownedTemplateIds) {
        Set<String> unavailable = new HashSet<>(ownedTemplateIds != null
                ? ownedTemplateIds : Set.of());
        List<String> market = pick(
                FactionEquipmentCatalog.offers(factionId, FactionEquipmentSource.MARKET),
                marketSlots(marketSize), seed(factionId, marketId, rotation, "market"),
                unavailable, FactionEquipmentSource.MARKET);
        unavailable.addAll(market);

        List<String> licensed = hasLicenseAccess
                ? pick(FactionEquipmentCatalog.offers(
                                factionId, FactionEquipmentSource.LICENSE),
                        licenseSlots(marketSize),
                        seed(factionId, marketId, rotation, "license"),
                        unavailable, FactionEquipmentSource.LICENSE)
                : List.of();
        return new StockPlan(market, licensed);
    }

    private static int marketSlots(int marketSize) {
        return Math.max(1, Math.min(MAX_MARKET_SLOTS, marketSize - 2));
    }

    private static int licenseSlots(int marketSize) {
        if (marketSize < 4) return 0;
        return Math.max(1, Math.min(MAX_LICENSE_SLOTS, marketSize - 4));
    }

    private static List<String> pick(List<FactionEquipmentOffer> offers, int limit,
                                     long seed, Set<String> unavailable,
                                     FactionEquipmentSource source) {
        if (limit <= 0 || offers.isEmpty()) return List.of();
        List<FactionEquipmentOffer> candidates = offers.stream()
                .filter(offer -> !unavailable.contains(offer.templateId()))
                .sorted(Comparator.comparing(FactionEquipmentOffer::templateId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Random random = new Random(seed);
        List<String> selected = new ArrayList<>();
        while (!candidates.isEmpty() && selected.size() < limit) {
            int totalWeight = candidates.stream().mapToInt(offer -> offer.weight(source)).sum();
            int roll = random.nextInt(totalWeight);
            for (int index = 0; index < candidates.size(); index++) {
                FactionEquipmentOffer candidate = candidates.get(index);
                roll -= candidate.weight(source);
                if (roll < 0) {
                    selected.add(candidate.templateId());
                    candidates.remove(index);
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }

    private static long seed(String factionId, String marketId, long rotation, String channel) {
        long hash = 0xcbf29ce484222325L;
        for (String part : List.of(text(factionId), text(marketId), channel,
                Long.toString(rotation))) {
            for (int index = 0; index < part.length(); index++) {
                hash ^= part.charAt(index);
                hash *= 0x100000001b3L;
            }
            hash ^= 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String text(String value) {
        return value != null ? value : "";
    }

    record StockPlan(List<String> marketTemplateIds, List<String> licensedTemplateIds) {
        StockPlan {
            marketTemplateIds = List.copyOf(marketTemplateIds);
            licensedTemplateIds = List.copyOf(licensedTemplateIds);
        }

        List<String> allTemplateIds() {
            Set<String> ordered = new LinkedHashSet<>(marketTemplateIds);
            ordered.addAll(licensedTemplateIds);
            return List.copyOf(ordered);
        }
    }
}
