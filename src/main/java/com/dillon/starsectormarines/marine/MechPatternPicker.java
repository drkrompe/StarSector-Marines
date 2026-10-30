package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

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
 * <p>A pattern is on offer through a channel when the company's progress opens it there
 * ({@link EquipmentAcquisitionEligibility}): its Kit Ladder station on the store's channels, its
 * access tier on patron and recovery, exactly as a loadout card is gated. Every faction may offer every offerable pattern at a modest base weight;
 * a faction weights up the hardware it actually issues to its own mechs
 * ({@link FactionMechLoadouts}) and the vehicle variant it is known for. A faction that offers no loadout cards through a channel
 * (an explicit no-player-equipment faction) offers no patterns there either.
 */
public final class MechPatternPicker {

    static final int BASE_WEIGHT = 2;
    static final int FACTION_ISSUE_WEIGHT = 8;

    /** Hardware each faction fields on its own mechs, mirrored from {@link FactionMechLoadouts}. */
    private static final Map<String, Set<MechPattern>> FACTION_ISSUE = Map.of(
            FactionMechLoadouts.TRI_TACHYON_ID, Set.of(
                    MechPattern.of(MechVariant.BULWARK),
                    MechPattern.of(MechWeaponComponent.PULSE_LASER),
                    MechPattern.of(MechWeaponComponent.SHOULDER_LASER_CANNON),
                    MechPattern.of(MechWeaponComponent.SRM_15),
                    MechPattern.of(MechWeaponComponent.LRM_15),
                    MechPattern.of(VehicleType.HOVER_SCOUT),
                    MechPattern.of(VehicleType.DRONE_SKIMMER)),
            FactionMechLoadouts.HEGEMONY_ID, Set.of(
                    MechPattern.of(MechVariant.BULWARK),
                    MechPattern.of(MechWeaponComponent.AC_6),
                    MechPattern.of(MechWeaponComponent.SRM_15),
                    MechPattern.of(MechWeaponComponent.LRM_15),
                    MechPattern.of(VehicleType.HEAVY_APC),
                    MechPattern.of(VehicleType.LRM_CARRIER)),
            FactionMechLoadouts.LUDDIC_PATH_ID, Set.of(
                    MechPattern.of(MechWeaponComponent.DC_5),
                    MechPattern.of(VehicleType.DOZER_BREACHER)),
            FactionMechLoadouts.LIONS_GUARD_ID, Set.of(
                    MechPattern.of(MechVariant.WARDEN),
                    MechPattern.of(MechWeaponComponent.THERMAL_LANCE),
                    MechPattern.of(VehicleType.FIRE_SUPPORT_APC)));

    /**
     * Who weights up a field rig. A pickup-based hull ({@link VehicleType#isPickup()}) is a jerry-rig a workshop
     * welds together, never a navy's issue: no military faction is known for one, and the outfits that build them
     * out of what they have are.
     */
    static final Set<String> FIELD_RIG_FACTIONS = Set.of("pirates", "independent");

    /**
     * Who weights up a fragmentation rack: the outfits whose wars are fought against men rather than
     * armor. The navies weight up the anti-armor racks instead ({@link #FACTION_ISSUE}).
     */
    static final Set<String> FRAG_RACK_FACTIONS = Set.of("pirates", "independent", FactionMechLoadouts.LUDDIC_PATH_ID);

    private MechPatternPicker() {}

    /** The patterns a faction weights up as its own issue. */
    static Set<MechPattern> factionIssue(String factionId) {
        return FACTION_ISSUE.getOrDefault(factionId, Set.of());
    }

    static Set<String> issuingFactions() {
        return FACTION_ISSUE.keySet();
    }

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
                    || !EquipmentAcquisitionEligibility.allows(pattern, source, progress)) {
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
        if (issue.contains(pattern)) return FACTION_ISSUE_WEIGHT;
        boolean fieldRig = pattern.kind() == MechPattern.Kind.VEHICLE && pattern.vehicleType().isPickup();
        if (fieldRig && FIELD_RIG_FACTIONS.contains(key)) return FACTION_ISSUE_WEIGHT;
        boolean fragRack = pattern.kind() == MechPattern.Kind.WEAPON && isFragRack(pattern.component());
        return fragRack && FRAG_RACK_FACTIONS.contains(key) ? FACTION_ISSUE_WEIGHT : BASE_WEIGHT;
    }

    private static boolean isFragRack(MechWeaponComponent component) {
        return component == MechWeaponComponent.SRM_5_FRAG || component == MechWeaponComponent.SRM_15_FRAG
                || component == MechWeaponComponent.LRM_5_FRAG || component == MechWeaponComponent.LRM_15_FRAG;
    }

    private record Candidate(String id, int weight) {}
}
