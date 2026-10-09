package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ChronicleBand;
import com.dillon.starsectormarines.campaign.ChronicleConfidence;
import com.dillon.starsectormarines.campaign.ChronicleEventType;
import com.dillon.starsectormarines.campaign.CompanyNews;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyHqIntakeTest {
    @Test
    void clientCrestUsesPersistentAffiliationEvenWhenTheHouseNameChanges() {
        CampaignState state = new CampaignState();
        int faction = state.factionRegistry.intern("persean");
        long house = state.addHouse(0, faction, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "House with an unrelated name");
        AtomicReference<String> requested = new AtomicReference<>();
        assertEquals("actual-crest.png", CompanyHqViewModel.employerCrest(state, house, id -> {
            requested.set(id);
            return "actual-crest.png";
        }));
        assertEquals("persean", requested.get());
        assertEquals("", CompanyHqViewModel.employerCrest(state, house + 100,
                id -> { throw new AssertionError("Unknown house must not borrow a faction"); }));
        assertEquals("", CompanyHqViewModel.employerCrest(state, house, id -> null));
    }

    @Test
    void confidencePartitionsOnlyTheDisplayedLearnedPosts() {
        List<CompanyNews.Entry> posts = List.of(post(ChronicleConfidence.CONFIRMED),
                post(ChronicleConfidence.RUMOR), post(ChronicleConfidence.CONFIRMED));
        assertEquals(new CompanyHqViewModel.Intake(2, 2, 1), CompanyHqViewModel.intake(2, posts));
        assertEquals(new CompanyHqViewModel.Intake(0, 0, 0), CompanyHqViewModel.intake(0, List.of()));
    }

    private static CompanyNews.Entry post(ChronicleConfidence confidence) {
        return new CompanyNews.Entry(1, ChronicleEventType.SILENT_COLONY, confidence,
                ChronicleBand.INTIMATE, null, null, "Colony", ChainState.FAILED,
                AbandonedColonyArchiveOutcome.NONE, 0, 0, 1, 1);
    }
}
