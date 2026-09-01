package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Pure quantity/quality read model for a mission deployment. */
public final class MissionForceEnvelope {

    public static final int MINIMUM_READY_PERSONNEL = 4;
    public static final int REINFORCED_CONQUEST_SQUADS = 42;
    public static final int FULL_STRENGTH_CONQUEST_SQUADS = 84;

    private MissionForceEnvelope() {}

    public static boolean allowsUnderstrength(Mission mission) {
        return mission != null && mission.source == MissionSource.GENERATED
                && mission.type != MissionType.CONQUEST;
    }

    public static int minimumPersonnel(Mission mission, int authoredSeats) {
        return allowsUnderstrength(mission)
                ? MINIMUM_READY_PERSONNEL : Math.max(0, authoredSeats);
    }

    public static int recommendedPersonnel(Mission mission) {
        return mission != null
                ? recommendedPersonnel(mission.type, mission.tier) : 0;
    }

    public static int recommendedPersonnel(MissionType type, OperationTier tier) {
        return recommendedSquads(type, tier) * MarineSquad.CAPACITY;
    }

    /**
     * Tier supplies the ordinary baseline; Conquest authors the battalion-scale
     * jump required by its three simultaneous lanes and sustained ferry battle.
     */
    public static int recommendedSquads(Mission mission) {
        return mission != null ? recommendedSquads(mission.type, mission.tier) : 0;
    }

    public static int recommendedSquads(MissionType type, OperationTier tier) {
        OperationTier resolved = tier != null ? tier : OperationTier.ESTABLISHED;
        if (type == MissionType.CONQUEST) {
            return resolved == OperationTier.FULL_STRENGTH
                    ? FULL_STRENGTH_CONQUEST_SQUADS
                    : REINFORCED_CONQUEST_SQUADS;
        }
        return resolved.squadsDemanded;
    }

    public static ExperienceMix selectedExperience(MarineRoster roster,
                                                   Set<String> selectedSquadIds) {
        EnumMap<ExperienceTier, Integer> counts = new EnumMap<>(ExperienceTier.class);
        if (roster != null && selectedSquadIds != null) {
            for (MarineSquad squad : roster.squads()) {
                if (squad.reserve() || !selectedSquadIds.contains(squad.id())) continue;
                for (MarineSoldier soldier : roster.squadMembers(squad)) {
                    if (soldier.status() != MarineSoldierStatus.ACTIVE) continue;
                    counts.merge(SquadExperienceStandard.bandFor(soldier), 1, Integer::sum);
                }
            }
        }
        return new ExperienceMix(counts);
    }

    public static String oppositionExpectation(RiskLevel risk) {
        if (risk == null) return "Unknown issue";
        return switch (risk) {
            case LOW -> "Green / Regular core";
            case MEDIUM -> "Regular core · Veterans possible";
            case HIGH -> "Regular / Veteran core · Elites possible";
        };
    }

    public record ExperienceMix(Map<ExperienceTier, Integer> counts) {
        public ExperienceMix {
            counts = counts == null ? Map.of() : Map.copyOf(counts);
        }

        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public String display() {
            if (counts.isEmpty()) return "No ready personnel selected";
            StringBuilder out = new StringBuilder();
            for (ExperienceTier tier : ExperienceTier.values()) {
                int count = counts.getOrDefault(tier, 0);
                if (count <= 0) continue;
                if (out.length() > 0) out.append(" · ");
                out.append(count).append(' ').append(tier.displayName);
            }
            return out.toString();
        }
    }
}
