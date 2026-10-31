package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic weighted choice of loadout cards for one faction and channel: patron rewards and recovery. The
 * company store does not draw through it; the Kit Ladder alone stocks the store ({@link StoreStock}).
 *
 * <p>A faction's authored pool still lists the equipment it fields; a card is on offer
 * through a channel when the faction fields part of what it bundles there, at the
 * heaviest weight of those entries, and the company's progress reaches the scarcest
 * bundled tier. A Restricted card instead needs a military warrant naming it, and its
 * Restricted kit must be fielded by this faction itself.
 */
public final class LoadoutCardPicker {

    private LoadoutCardPicker() {}

    public static List<String> pick(String factionId, FactionEquipmentSource source,
                                    int limit, long seed, Set<String> unavailableCardIds,
                                    EquipmentAcquisitionEligibility.Progress progress) {
        if (source == null || limit <= 0) return List.of();
        Set<String> unavailable = unavailableCardIds != null ? unavailableCardIds : Set.of();
        Map<String, Integer> fielded = new HashMap<>();
        for (FactionEquipmentOffer offer : FactionEquipmentCatalog.offers(factionId, source)) {
            fielded.put(offer.templateId(), offer.weight(source));
        }
        List<Candidate> candidates = new ArrayList<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            if (unavailable.contains(card.id())
                    || !EquipmentAcquisitionEligibility.allows(card, source, progress)
                    || !fieldsEveryRestricted(card, fielded.keySet())) {
                continue;
            }
            int weight = 0;
            for (String templateId : card.templateIds()) {
                weight = Math.max(weight, fielded.getOrDefault(templateId, 0));
            }
            if (weight > 0) candidates.add(new Candidate(card.id(), weight));
        }
        candidates.sort(Comparator.comparing(Candidate::id));
        Random random = new Random(seed);
        List<String> selected = new ArrayList<>();
        while (!candidates.isEmpty() && selected.size() < limit) {
            long total = candidates.stream().mapToLong(Candidate::weight).sum();
            long roll = random.nextLong(total);
            for (int index = 0; index < candidates.size(); index++) {
                roll -= candidates.get(index).weight();
                if (roll < 0) {
                    selected.add(candidates.remove(index).id());
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Warranted equipment is issued only by the tradition that builds it, so a faction
     * offers a card carrying Restricted kit only when it fields that kit itself: an
     * independent licence that happens to field the card's scout suits does not thereby
     * sell Hegemony military issue.
     */
    private static boolean fieldsEveryRestricted(LoadoutCard card, Set<String> fielded) {
        for (String templateId : card.templateIds()) {
            if (EquipmentTemplateCatalog.require(templateId).accessTier() == EquipmentAccessTier.RESTRICTED
                    && !fielded.contains(templateId)) {
                return false;
            }
        }
        return true;
    }

    /** As above, for {@code factionId}'s pool through {@code source}: the store's licence shelf asks it too. */
    static boolean fieldsEveryRestricted(LoadoutCard card, String factionId, FactionEquipmentSource source) {
        Set<String> fielded = new HashSet<>();
        for (FactionEquipmentOffer offer : FactionEquipmentCatalog.offers(factionId, source)) {
            fielded.add(offer.templateId());
        }
        return fieldsEveryRestricted(card, fielded);
    }

    private record Candidate(String id, int weight) {}
}
