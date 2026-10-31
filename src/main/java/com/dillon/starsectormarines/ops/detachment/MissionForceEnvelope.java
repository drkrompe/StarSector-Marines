package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
import com.dillon.starsectormarines.ops.CompletedReplays;
import com.dillon.starsectormarines.ops.DebugMissionCatalog;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.SupplyRouteCampaign;
import com.dillon.starsectormarines.ops.CorvusWaylineCampaign;
import com.dillon.starsectormarines.ops.WorkingShipCompany;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.AgreusArcCampaign;
import com.dillon.starsectormarines.ops.DistrictEscortCampaign;
import com.dillon.starsectormarines.ops.WellhollowBloomCampaign;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure quantity/quality read model for a mission deployment. */
public final class MissionForceEnvelope {

    public static final int MINIMUM_READY_PERSONNEL = 1;
    public static final int REINFORCED_CONQUEST_SQUADS = 42;
    public static final int FULL_STRENGTH_CONQUEST_SQUADS = 84;

    private MissionForceEnvelope() {}

    /**
     * Whether the mission may launch below its authored seat count.
     *
     * <p>A polity defence joins the ordinary generated work here: it is one-shot,
     * unrepeatable work on the company's own colony, and a raid already on the ground
     * will not wait for a full task force ({@code mission-tier-nouns.md}). A Nexerelin
     * invasion is the one Conquest that may: it is the player's own decision with
     * whatever the company has, not an authored set piece a patron sized.
     * The opening Wayline operations, story and recurring, may too: their authored
     * sorties size the lift, not the company, so a seat count from the manifest is
     * never a launch gate for a starting company with one marine wounded.
     */
    public static boolean allowsUnderstrength(Mission mission) {
        return mission != null
                && (mission.source == MissionSource.GENERATED
                        || mission.source == MissionSource.POLITY_DEFENCE
                        || OpeningOperationKind.fromMission(mission) != null
                        || SupplyRouteCampaign.isBattle(mission) || CorvusWaylineCampaign.isBattle(mission) || ShepherdRecoveryCampaign.isBattle(mission) || AgreusArcCampaign.isBattle(mission)
                        || DistrictEscortCampaign.isBattle(mission) || WellhollowBloomCampaign.isBattle(mission))
                && mission.type != MissionType.CONQUEST
                || mission != null && mission.source == MissionSource.NEX_INVASION;
    }

    public static int minimumPersonnel(Mission mission, int authoredSeats) {
        return minimumPersonnel(mission, authoredSeats, false);
    }

    /**
     * The launch floor: one ready marine for any mission that may go understrength, or none
     * when a machine drops with the company instead (owner, 2026-10-27). Other work keeps
     * its authored seats.
     */
    public static int minimumPersonnel(Mission mission, int authoredSeats, boolean machineDrops) {
        if (!allowsUnderstrength(mission)) return Math.max(0, authoredSeats);
        return machineDrops ? 0 : MINIMUM_READY_PERSONNEL;
    }

    public static int recommendedPersonnel(Mission mission) {
        return mission != null ? recommendedSquads(mission) * MarineSquad.CAPACITY : 0;
    }

    public static int recommendedPersonnel(MissionType type, OperationTier tier) {
        return recommendedSquads(type, tier) * MarineSquad.CAPACITY;
    }

    /**
     * Tier supplies the ordinary baseline; Conquest authors the battalion-scale
     * jump required by its three simultaneous lanes and sustained ferry battle.
     */
    public static int recommendedSquads(Mission mission) {
        DebugMissionCatalog.Scenario replay = CompletedReplays.scenario(mission);
        if (replay != null) return replay.squads;
        if (ShepherdRecoveryCampaign.isBattle(mission)) return WorkingShipCompany.SQUADS;
        if (ShepherdRecoveryCampaign.isBattle(mission) || AgreusArcCampaign.isBattle(mission)
                || DistrictEscortCampaign.isBattle(mission)) return 2;
        if (WellhollowBloomCampaign.isBattle(mission)) return WellhollowBloomCampaign.RECOMMENDED_SQUADS;
        if (CorvusWaylineCampaign.isBattle(mission)) return CorvusWaylineProgress.forMission(mission.id).squads;
        if (mission != null && SupplyRouteCampaign.owns(mission.id)) return 2;
        return mission != null ? recommendedSquads(mission.type, mission.tier) : 0;
    }

    /** Mechs or vehicles recommended beside the squads: end-game challenge work and the Corvus chapters name any. */
    public static int recommendedMachines(Mission mission) {
        if (WellhollowBloomCampaign.isBattle(mission)) return WellhollowBloomCampaign.RECOMMENDED_MACHINES;
        if (ShepherdRecoveryCampaign.isBattle(mission)) return WorkingShipCompany.MACHINES;
        return CorvusWaylineCampaign.isBattle(mission)
                ? CorvusWaylineProgress.forMission(mission.id).recommendedMachines() : 0;
    }

    public static int recommendedSquads(MissionType type, OperationTier tier) {
        OperationTier resolved = tier != null ? tier : OperationTier.ESTABLISHED;
        if (type == MissionType.CONQUEST) {
            return resolved == OperationTier.FULL_STRENGTH
                    ? FULL_STRENGTH_CONQUEST_SQUADS
                    : REINFORCED_CONQUEST_SQUADS;
        }
        if (type == MissionType.SABOTAGE) {
            return switch (resolved) {
                case FIRST_CONTRACT -> 1;
                case ESTABLISHED, VETERAN -> 2;
                case REINFORCED -> 3;
                case FULL_STRENGTH -> 4;
            };
        }
        if (type == MissionType.RAID) {
            return switch (resolved) {
                case FIRST_CONTRACT -> 2;
                case ESTABLISHED -> 3;
                case VETERAN -> 4;
                case REINFORCED -> 6;
                case FULL_STRENGTH -> 8;
            };
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
        if (risk == null) return Strings.get("briefingOppositionUnknown");
        return Strings.get("briefingOpposition" + risk.name());
    }

    public record ExperienceMix(Map<ExperienceTier, Integer> counts) {
        public ExperienceMix {
            counts = counts == null ? Map.of() : Map.copyOf(counts);
        }

        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public String display() {
            if (counts.isEmpty()) return Strings.get("briefingExperienceNone");
            List<String> parts = new ArrayList<>();
            for (ExperienceTier tier : ExperienceTier.values()) {
                int count = counts.getOrDefault(tier, 0);
                if (count <= 0) continue;
                parts.add(Strings.format("briefingExperienceCount", count,
                        Strings.named("briefingExperienceTier", tier.name())));
            }
            return Strings.join("briefingExperienceSeparator", parts.toArray(new String[0]));
        }
    }
}
