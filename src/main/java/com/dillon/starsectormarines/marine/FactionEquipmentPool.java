package com.dillon.starsectormarines.marine;

import java.util.List;

/** Merged player-acquisition pool for one exact campaign faction id. */
public record FactionEquipmentPool(
        String factionId,
        List<FactionEquipmentOffer> offers,
        String noPlayerEquipmentReason) {

    public FactionEquipmentPool {
        if (factionId == null || factionId.isBlank() || offers == null) {
            throw new IllegalArgumentException("Faction equipment pools require identity and offers");
        }
        offers = List.copyOf(offers);
        if (offers.isEmpty() == (noPlayerEquipmentReason == null
                || noPlayerEquipmentReason.isBlank())) {
            throw new IllegalArgumentException(
                    "Faction equipment pools require offers or one explicit exclusion reason");
        }
    }

    public List<FactionEquipmentOffer> offers(FactionEquipmentSource source) {
        if (source == null) return List.of();
        return offers.stream().filter(offer -> offer.weight(source) > 0).toList();
    }

    public boolean offers(String templateId, FactionEquipmentSource source) {
        return offers.stream().anyMatch(offer -> offer.templateId().equals(templateId)
                && offer.weight(source) > 0);
    }
}
