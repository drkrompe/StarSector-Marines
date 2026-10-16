package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic weighted choice of mech pattern cards for one faction and channel, the
 * mech-bay counterpart of {@link LoadoutCardPicker}.
 *
 * <p>A pattern is on offer through a channel when the company's progress reaches its access
 * tier there ({@link EquipmentAcquisitionEligibility}), exactly as a loadout card's scarcest
 * template is gated. Every faction may offer every offerable pattern at a modest base weight;
 * a faction weights up the hardware it actually issues to its own mechs
 * ({@link FactionMechLoadouts}). A faction that offers no loadout cards through a channel
 * (an explicit no-player-equipment faction) offers no patterns there either.
 */
public final class MechPatternPicker {

    static final int BASE_WEIGHT = 2;
    static final int FACTION_ISSUE_WEIGHT = 8;

    /** Hardware each faction fields on its own mechs, mirrored from {@link FactionMechLoadouts}. */
    private static final Map<String, Set<MechPattern>> FACTION_ISSUE = Map.of(
            FactionMechLoadouts.TRI_TACHYON_ID, Set.of(
                    MechPattern.of(MechVariant.BULWARK),
                    MechPattern.of(MechWeaponComponent.DUAL_PULSE_LASERS),
                    MechPattern.of(MechWeaponComponent.SHOULDER_LASER_CANNON)),
            FactionMechLoadouts.HEGEMONY_ID, Set.of(
                    MechPattern.of(MechVariant.BULWARK),
                    MechPattern.of(MechWeaponComponent.DUAL_BASTION_AUTOCANNONS)),
            FactionMechLoadouts.LUDDIC_PATH_ID, Set.of(
                    MechPattern.of(MechWeaponComponent.DEMOLITION_CANNON)),
            FactionMechLoadouts.LIONS_GUARD_ID, Set.of(
                    MechPattern.of(MechVariant.SIROCCO),
                    MechPattern.of(MechWeaponComponent.THERMAL_LANCE)));

    private MechPatternPicker() {}

    public static List<String> pick(String factionId, FactionEquipmentSource source,
                                    int limit, long seed, Set<String> unavailablePatternIds,
                                    EquipmentAcquisitionEligibility.Progress progress) {
        return pick(factionId, source, limit, seed, unavailablePatternIds, progress,
                EnumSet.allOf(MechPattern.Kind.class));
    }

    /** As above, restricted to the given pattern kinds. */
    public static List<String> pick(String factionId, FactionEquipmentSource source,
                                    int limit, long seed, Set<String> unavailablePatternIds,
                                    EquipmentAcquisitionEligibility.Progress progress,
                                    Set<MechPattern.Kind> kinds) {
        if (source == null || limit <= 0 || kinds == null || kinds.isEmpty()) return List.of();
        if (FactionEquipmentCatalog.installed() != null
                && FactionEquipmentCatalog.offers(factionId, source).isEmpty()) {
            return List.of();
        }
        Set<String> unavailable = unavailablePatternIds != null ? unavailablePatternIds : Set.of();
        List<Candidate> candidates = new ArrayList<>();
        for (MechPattern pattern : MechPattern.offerable()) {
            if (!kinds.contains(pattern.kind()) || unavailable.contains(pattern.id())
                    || !EquipmentAcquisitionEligibility.allows(pattern.accessTier(), source, progress)) {
                continue;
            }
            candidates.add(new Candidate(pattern.id(), weight(factionId, pattern)));
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

    static int weight(String factionId, MechPattern pattern) {
        String key = factionId != null ? factionId.trim().toLowerCase(Locale.ROOT) : "";
        Set<MechPattern> issue = FACTION_ISSUE.getOrDefault(key, Set.of());
        return issue.contains(pattern) ? FACTION_ISSUE_WEIGHT : BASE_WEIGHT;
    }

    private record Candidate(String id, int weight) {}
}
