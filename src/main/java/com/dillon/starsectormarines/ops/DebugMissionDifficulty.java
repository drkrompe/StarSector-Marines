package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

/** Applies the DEBUG briefing's operation-scale and map-sprawl choices to one mission. */
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

    /**
     * Returns the same mission for production work. DEBUG work states how
     * settled its map is directly, so the three maps a market could produce
     * can be played back to back on one board.
     *
     * @param requestedSprawl the sprawl to state, or {@code null} to hand the
     *                        answer back to the target market's size.
     */
    static Mission atSprawl(Mission mission, PrecinctPlan.Sprawl requestedSprawl) {
        if (mission == null || !mission.source.isDebug()) return mission;
        return Mission.builder(mission).sprawl(requestedSprawl).build();
    }

    private static boolean isTierGridEntry(Mission mission) {
        return mission.source == MissionSource.DEBUG
                && mission.type != null
                && mission.id != null
                && mission.id.startsWith("debug:" + mission.type.name() + ":");
    }
}
