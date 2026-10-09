package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyHqClientCrestTest {
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

}
