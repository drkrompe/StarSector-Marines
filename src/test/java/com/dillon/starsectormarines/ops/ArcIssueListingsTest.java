package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.KitLadder;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.LoadoutCards;
import com.dillon.starsectormarines.marine.MarketListings;
import com.dillon.starsectormarines.marine.UnlockStation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each early arc beat stocks the card that names its lesson, once, where it happened. */
class ArcIssueListingsTest {

    @Test
    void eachEarnedBeatListsItsOwnCardExactlyOnce() {
        for (ArcIssueListings.Beat beat : ArcIssueListings.Beat.values()) {
            CampaignState state = new CampaignState();
            assertTrue(ArcIssueListings.after(state, beat, true, "jangala", 3), beat.name());
            assertFalse(ArcIssueListings.after(state, beat, true, "jangala", 9),
                    beat + ": a repeated settlement creates nothing");

            List<MarketListings.Listing> listings = MarketListings.all(state);
            assertEquals(1, listings.size(), beat.name());
            MarketListings.Listing listing = listings.get(0);
            assertEquals(beat.cardId(), listing.cardId());
            assertEquals(beat.scope, listing.scope());
            assertEquals(beat.reasonKey, listing.reasonKey());
            assertEquals(beat == ArcIssueListings.Beat.BLIND_WATCH ? "hegemony" : "jangala", listing.scopeId());
        }
    }

    @Test
    void anUnearnedBeatOrAMarketThatCannotBeFoundListsNothing() {
        CampaignState state = new CampaignState();
        assertFalse(ArcIssueListings.after(state, ArcIssueListings.Beat.THUNDER_RUN, false, "jangala", 0));
        assertFalse(ArcIssueListings.after(state, ArcIssueListings.Beat.WORKING_PASSAGE, true, null, 0));
        assertTrue(MarketListings.all(state).isEmpty());
    }

    @Test
    void eachBeatIsItsCardsKitLadderStation() {
        for (ArcIssueListings.Beat beat : ArcIssueListings.Beat.values()) {
            assertEquals(UnlockStation.byId(beat.name().toLowerCase(Locale.ROOT).replace('_', '-')), KitLadder.station(beat.cardId()), beat.name());
        }
    }

    @Test
    void theCardsAreDistinctBuyableAndNeedNoWarrant() {
        Set<String> circulating = Set.copyOf(LoadoutCards.all().stream().map(LoadoutCard::id).toList());
        Set<String> seen = new HashSet<>();
        for (ArcIssueListings.Beat beat : ArcIssueListings.Beat.values()) {
            LoadoutCard card = LoadoutCard.parse(beat.cardId());
            assertNotNull(card, beat.name());
            assertTrue(seen.add(card.id()));
            assertTrue(circulating.contains(card.id()), beat + " circulates outside its listing too");
            for (String templateId : card.templateIds()) {
                assertNotEquals(EquipmentAccessTier.RESTRICTED, EquipmentTemplateCatalog.require(templateId).accessTier());
            }
        }
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            assertNotEquals(ArcIssueListings.Beat.RELIEF.doctrineId, path.weapons,
                    "Checkpoint Issue is bought beside A Proper Issue's free choice, never one of it");
        }
    }
}
