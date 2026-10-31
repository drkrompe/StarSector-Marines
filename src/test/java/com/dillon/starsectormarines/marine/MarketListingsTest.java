package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A story beat keeps a named card on a named shelf until the company has it. */
class MarketListingsTest {

    /** Placed at Counterattack: listed, it is shown first once the campaign has passed Counterattack, and not before. */
    private static final String TRENCH = "loadout-card:weapon:weapons:trench-clearing";

    @Test
    void aListingHasAShelfOfItsOwnAndIsNeverOfferedTwice() {
        StoreStock.StockPlan with = plan(List.of(TRENCH), Set.of());

        assertEquals(List.of(TRENCH), with.listedCardIds());
        assertFalse(with.marketCardIds().contains(TRENCH), "never offered a second time");
        assertEquals(TRENCH, with.allCardIds().get(0));
    }

    @Test
    void aListingIsWithdrawnOnceTheCompanyHasTheCardAndReturnsIfItGoesAgain() {
        assertTrue(plan(List.of(TRENCH), Set.of(TRENCH)).listedCardIds().isEmpty());
        assertEquals(List.of(TRENCH), plan(List.of(TRENCH), Set.of()).listedCardIds());
    }

    @Test
    void aListingStandsWhereItWasPlacedAndNowhereElse() {
        CampaignState state = new CampaignState();
        MarketListings.list(state, TRENCH, MarketListings.Scope.MARKET, "jangala", "reason", 4);
        MarketListings.list(state, TRENCH, MarketListings.Scope.FACTION, "hegemony", "reason", 4);

        assertEquals(2, MarketListings.at(state, "jangala", "hegemony").size());
        assertEquals(1, MarketListings.at(state, "chicomoztoc", "hegemony").size());
        assertEquals(1, MarketListings.at(state, "jangala", "independent").size());
        assertTrue(MarketListings.at(state, "sindria", "sindrian_diktat").isEmpty());
        assertFalse(MarketListings.list(state, TRENCH, MarketListings.Scope.MARKET, "jangala", "reason", 9),
                "listing the same card at the same place twice changes nothing");
    }

    @Test
    void aListingWaitsOnItsCardsStation() {
        assertTrue(StoreStock.cards(Set.of(), EquipmentAcquisitionEligibility.Progress.OPENING, List.of(TRENCH))
                .allCardIds().stream().noneMatch(TRENCH::equals));
    }

    @Test
    void aListingNeverShowsACardTheLadderLeavesOnNone() {
        String none = "loadout-card:weapon:weapons:assault";
        assertEquals(UnlockStation.NONE, KitLadder.station(none));
        assertTrue(StoreStock.cards(Set.of(), EquipmentAcquisitionEligibility.Progress.complete(), List.of(none))
                .allCardIds().stream().noneMatch(none::equals));
    }

    @Test
    void listingsSurviveALoadAndASaveFromBeforeThemRestoresEmpty() throws Exception {
        CampaignState state = new CampaignState();
        MarketListings.list(state, TRENCH, MarketListings.Scope.MARKET, "jangala", "reason", 4);
        readResolve(state);
        assertEquals(1, MarketListings.all(state).size());

        CampaignState legacy = new CampaignState();
        legacy.marketListingCardId = null;
        legacy.marketListingScopeId = null;
        legacy.marketListingReason = null;
        readResolve(legacy);
        assertEquals(0, legacy.marketListingCount);
        assertTrue(MarketListings.list(legacy, TRENCH, MarketListings.Scope.MARKET, "jangala", "reason", 4));
    }

    private static StoreStock.StockPlan plan(List<String> listed, Set<String> unavailable) {
        return StoreStock.cards(unavailable, EquipmentAcquisitionEligibility.Progress.complete(), listed);
    }

    private static void readResolve(CampaignState state) throws Exception {
        Method method = CampaignState.class.getDeclaredMethod("readResolve");
        method.setAccessible(true);
        method.invoke(state);
    }
}
