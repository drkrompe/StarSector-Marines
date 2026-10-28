package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic choice of boat part patterns for one faction and channel, the boat deck's
 * counterpart to {@link MechPatternPicker}.
 *
 * <p>A part is on offer through a channel when the company's progress reaches its access tier
 * there ({@link EquipmentAcquisitionEligibility}). Every part is equally likely: boats have no
 * per-faction issue to weight by. A faction that offers no loadout cards through a channel (an
 * explicit no-player-equipment faction) offers no parts there either.
 */
public final class BoatPartPicker {

    private BoatPartPicker() {}

    public static List<String> pick(String factionId, FactionEquipmentSource source, int limit, long seed,
                                    Set<String> unavailablePartIds,
                                    EquipmentAcquisitionEligibility.Progress progress) {
        if (source == null || limit <= 0) return List.of();
        if (FactionEquipmentCatalog.installed() != null
                && FactionEquipmentCatalog.offers(factionId, source).isEmpty()) {
            return List.of();
        }
        Set<String> unavailable = unavailablePartIds != null ? unavailablePartIds : Set.of();
        List<String> candidates = new ArrayList<>();
        for (String id : BoatParts.offerable()) {
            if (!unavailable.contains(id)
                    && EquipmentAcquisitionEligibility.allows(BoatParts.accessTier(id), source, progress)) {
                candidates.add(id);
            }
        }
        candidates.sort(String::compareTo);
        Random random = new Random(seed);
        List<String> selected = new ArrayList<>();
        while (!candidates.isEmpty() && selected.size() < limit) {
            selected.add(candidates.remove(random.nextInt(candidates.size())));
        }
        return List.copyOf(selected);
    }
}
