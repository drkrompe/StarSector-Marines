package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ops.DebugMissionCatalog.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletedReplaysTest {

    @Test
    void wonStoryIdsUnlockTheirChaptersInCampaignOrder() {
        Set<String> won = Set.of(
                AgreusArcProgress.WARRANT_PREFIX + "2",
                OpeningOperationKind.RELIEF.missionId,
                BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.JUNCTION + "_0_1",
                ShepherdRecoveryProgress.COLD_PREFIX + "0");

        assertEquals(List.of(Scenario.RELIEF, Scenario.JUNCTION, Scenario.COLD_START, Scenario.LIMITED_WARRANT),
                CompletedReplays.completed(won));
    }

    @Test
    void recurringWorkAndUnknownIdsUnlockNothing() {
        Set<String> won = Set.of(OpeningOperationKind.RELIEF.recurringId,
                BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.RELAY + "_3_0", "something_else");

        assertTrue(CompletedReplays.completed(won).isEmpty());
    }
}
