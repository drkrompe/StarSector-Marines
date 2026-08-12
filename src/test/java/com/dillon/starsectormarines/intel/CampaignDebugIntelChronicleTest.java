package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ChronicleBand;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignDebugIntelChronicleTest {

    @Test
    void summaryUsesLearnedSnapshotAndRegistryLabels() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("jangala");
        int industry = state.industryRegistry.intern("heavyindustry");
        long actor = house(state, market, "House Korvath");
        long target = house(state, market, "House Drennar");
        state.addChronicleChainOutcome(4L, ChainState.RESOLVED, ChronicleBand.INTIMATE,
                actor, target, market, industry, 20, 23);

        assertEquals("[INTIMATE/CONFIRMED] RESOLVED — House Korvath vs House Drennar"
                        + " — heavyindustry @ jangala — happened day 20, learned day 23",
                CampaignDebugIntel.chronicleSummary(state, 0));
    }

    @Test
    void summaryFallsBackForMissingMutableReferences() {
        CampaignState state = new CampaignState();
        state.addChronicleChainOutcome(4L, ChainState.FAILED, ChronicleBand.EPIC,
                90L, 91L, 6, 7, 20, 23);

        assertEquals("[EPIC/CONFIRMED] FAILED — house#90 vs house#91"
                        + " — industry#7 @ market#6 — happened day 20, learned day 23",
                CampaignDebugIntel.chronicleSummary(state, 0));
    }

    @Test
    void rumorSummaryStatesUncertaintyAndOngoingAction() {
        CampaignState state = new CampaignState();
        state.addChronicleChainRumor(4L, ChronicleBand.INTIMATE,
                90L, 91L, 6, 7, 20, 23);

        assertEquals("[INTIMATE/RUMOR] RUMOR — house#90 moving against house#91"
                        + " — industry#7 @ market#6 — happened day 20, learned day 23",
                CampaignDebugIntel.chronicleSummary(state, 0));
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }
}
