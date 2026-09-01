package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;

/** Applies the DEBUG briefing's operation-scale choice to one mission. */
final class DebugMissionDifficulty {

    private DebugMissionDifficulty() {
    }

    /**
     * Returns the same mission for production work. DEBUG work keeps its
     * authored risk and field-presence policy while tier and tier-owned lift
     * move together.
     */
    static Mission atTier(Mission mission, OperationTier requestedTier) {
        if (mission == null || !mission.source.isDebug()) return mission;

        OperationTier tier = OperationTier.clampTo(requestedTier,
                mission.type != null ? mission.type.tierFloor : null);
        int requiredDrops = MissionGenerator.requiredDropsFor(mission.type, tier);
        if (DevConfig.DROP_COUNT_OVERRIDE > 0) {
            requiredDrops = DevConfig.DROP_COUNT_OVERRIDE;
        }

        Mission.Builder adjusted = Mission.builder(mission)
                .tier(tier)
                .requiredDrops(requiredDrops)
                .employerShuttles(Math.min(mission.employerShuttles, requiredDrops));
        if (isTierGridEntry(mission)) {
            adjusted.name(mission.type.name() + " — " + tier.displayName);
        }
        return adjusted.build();
    }

    private static boolean isTierGridEntry(Mission mission) {
        return mission.source == MissionSource.DEBUG
                && mission.type != null
                && mission.id != null
                && mission.id.startsWith("debug:" + mission.type.name() + ":");
    }
}
