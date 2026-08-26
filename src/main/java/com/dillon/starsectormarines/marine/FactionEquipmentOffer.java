package com.dillon.starsectormarines.marine;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** One collectible template and its weighted acquisition channels for a faction. */
public record FactionEquipmentOffer(
        EquipmentTemplateCard template,
        Map<FactionEquipmentSource, Integer> sourceWeights) {

    public FactionEquipmentOffer {
        if (template == null || sourceWeights == null || sourceWeights.isEmpty()) {
            throw new IllegalArgumentException("Faction equipment offers require a template and source");
        }
        EnumMap<FactionEquipmentSource, Integer> copy =
                new EnumMap<>(FactionEquipmentSource.class);
        for (Map.Entry<FactionEquipmentSource, Integer> entry : sourceWeights.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Faction equipment source weights must be positive");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        sourceWeights = Collections.unmodifiableMap(copy);
    }

    public String templateId() {
        return template.id();
    }

    public int weight(FactionEquipmentSource source) {
        return sourceWeights.getOrDefault(source, 0);
    }

    public Set<FactionEquipmentSource> sources() {
        return sourceWeights.keySet();
    }
}
