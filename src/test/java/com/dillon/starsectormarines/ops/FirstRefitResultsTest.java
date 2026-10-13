package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.ops.loot.LootCandidate;
import com.dillon.starsectormarines.ops.loot.LootKind;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ops.loot.LootStack;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FirstRefitResultsTest {
    @Test void noticeTracksAvailableUpgradeAndRoundTripPreservesPendingSalvage() {
        var roster = new MarineRoster();
        var outcome = MissionOutcome.builder().victory(true).missionSource(MissionSource.STORY)
                .missionId(OpeningOperationKind.RELIEF.missionId).build();
        var context = new MarineOpsContext(null);
        var candidate = new LootCandidate(LootKind.COMMODITY, "supplies", "Supplies", null, 100, 1f, 1f, 1, 1);
        var manifest = new LootManifest(1L, 100, 100, List.of(new LootStack(candidate, 1)));
        context.setLastOutcome(outcome); context.setLootManifest(manifest); context.goTo(ScreenId.RESULTS);
        var props = ResultsScreen.previewProps(true, true);
        Runnable open = () -> context.openFleetArmoryWorkspaceFrom(ScreenId.RESULTS);
        ResultsScreen.putFirstRefit(props, outcome, roster, open);
        assertEquals("hidden", props.get("refitClasses"));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        ResultsScreen.putFirstRefit(props, outcome, roster, open);
        assertNotEquals("hidden", props.get("refitClasses"));
        ((Runnable) props.get("refitAction")).run();
        assertEquals(ScreenId.FLEET_ARMORY, context.getCurrentScreen());
        assertSame(manifest, context.getLootManifest()); assertSame(outcome, context.getLastOutcome());
        assertFalse(context.isLootSettlementStarted());
        context.returnFromFleetArmoryWorkspace();
        assertEquals(ScreenId.RESULTS, context.getCurrentScreen());
        assertSame(manifest, context.getLootManifest()); assertFalse(context.isLootSettlementStarted());
        ResultsScreen.putFirstRefit(props, MissionOutcome.builder().missionSource(MissionSource.DEBUG).build(), roster, open);
        assertEquals("hidden", props.get("refitClasses"));
        roster.firstRefit().claim(roster, FirstRefit.Path.STEADY_LINE);
        roster.firstRefit().issue(roster, roster.createSquad().id(), FirstRefit.Path.STEADY_LINE, EquipmentIssueResources.UNLIMITED);
        ResultsScreen.putFirstRefit(props, outcome, roster, open);
        assertEquals("hidden", props.get("refitClasses"));
    }
}
