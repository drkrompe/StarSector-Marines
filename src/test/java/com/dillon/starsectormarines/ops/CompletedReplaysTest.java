package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
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

    @Test
    void onlyChaptersFoughtOnThisPlanetAreOffered() {
        MarineRoster roster = new MarineRoster();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.markStoryComplete(ShepherdRecoveryProgress.PREFIX + "0");
        roster.markStoryComplete(AgreusArcProgress.PREFIX + "0");

        var atAsharu = CompletedReplays.offers(roster, "asharu", "Asharu",
                scenario -> scenario == Scenario.RELIEF ? "jangala" : CompletedReplays.fixedSiteId(scenario));

        assertEquals(List.of(Scenario.SALVAGE_RIGHTS),
                atAsharu.stream().map(CompletedReplays::scenario).toList());
        assertEquals("Asharu", atAsharu.get(0).targetPlanetName);
    }
}
