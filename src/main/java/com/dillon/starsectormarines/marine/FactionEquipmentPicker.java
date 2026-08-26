package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Shared deterministic weighted picker for live faction-equipment consumers. */
public final class FactionEquipmentPicker {

    private FactionEquipmentPicker() {}

    public static List<String> pick(String factionId, FactionEquipmentSource source,
                                    int limit, long seed,
                                    Set<String> unavailableTemplateIds,
                                    EquipmentAcquisitionEligibility.Progress progress) {
        if (source == null || limit <= 0) return List.of();
        Set<String> unavailable = unavailableTemplateIds != null
                ? unavailableTemplateIds : Set.of();
        List<FactionEquipmentOffer> candidates =
                FactionEquipmentCatalog.offers(factionId, source).stream()
                        .filter(offer -> !unavailable.contains(offer.templateId()))
                        .filter(offer -> EquipmentAcquisitionEligibility.allows(
                                EquipmentTemplateCatalog.require(offer.templateId()),
                                source, progress))
                        .sorted(Comparator.comparing(FactionEquipmentOffer::templateId))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Random random = new Random(seed);
        List<String> selected = new ArrayList<>();
        while (!candidates.isEmpty() && selected.size() < limit) {
            long totalWeight = candidates.stream()
                    .mapToLong(offer -> offer.weight(source)).sum();
            long roll = random.nextLong(totalWeight);
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
}
