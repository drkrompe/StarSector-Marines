package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.GarrisonDefenseResolution;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which settled garrison defence sends its raid home. The writeback that carries the
 * decision reaches {@code CampaignStateScript.getInstance()}, which needs a live sector,
 * so the decision itself is asked directly.
 */
class MissionResolverRaidEndTest {

    @Test
    void wonVanillaRaidDefenceEndsTheRaid() {
        assertTrue(MissionResolver.shouldEndRaid(
                GarrisonDefenseResolution.Result.DEFENSE_WON,
                GarrisonDefenseTriggerType.VANILLA_RAID));
    }

    @Test
    void modSimulatedTriggersHaveNoRaidToEnd() {
        for (GarrisonDefenseTriggerType trigger : new GarrisonDefenseTriggerType[]{
                GarrisonDefenseTriggerType.RIVAL_STRIKE,
                GarrisonDefenseTriggerType.INTERNAL_FLIP,
                GarrisonDefenseTriggerType.NONE}) {
            assertFalse(MissionResolver.shouldEndRaid(
                    GarrisonDefenseResolution.Result.DEFENSE_WON, trigger), trigger.name());
        }
    }

    @Test
    void anUnwonDefenceLeavesTheRaidRunning() {
        assertFalse(MissionResolver.shouldEndRaid(
                GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED,
                GarrisonDefenseTriggerType.VANILLA_RAID));
        assertFalse(MissionResolver.shouldEndRaid(
                null, GarrisonDefenseTriggerType.VANILLA_RAID));
        assertFalse(MissionResolver.shouldEndRaid(
                GarrisonDefenseResolution.Result.DEFENSE_WON, null));
    }
}
