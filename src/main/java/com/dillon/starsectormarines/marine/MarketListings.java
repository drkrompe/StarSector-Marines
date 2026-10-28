package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Market listings: a named loadout card a story beat has put on sale at one named market,
 * or at every open market of one faction, until the company knows or carries it
 * ({@code progression-nouns.md}, Kit and armory).
 *
 * <p>A listing is not a fifth acquisition channel. It is authored stock that the market
 * channel displays: it bypasses the reputation and victory thresholds because the story is
 * the permission, but it never bypasses the Restricted tier. Listing Restricted kit needs a
 * military warrant naming every Restricted template on the card, so a listing can never be
 * a back door around a warrant.
 *
 * <p>Settlement code owns once-only; {@link #list} is idempotent per card and scope so a
 * repeated call is harmless either way.
 */
public final class MarketListings {

    /** Where a listing stands. Stored as {@link #code}. */
    public enum Scope {
        /** One named market's open market. */
        MARKET((byte) 0),
        /** Every open market of one faction. */
        FACTION((byte) 1);

        public final byte code;

        Scope(byte code) {
            this.code = code;
        }

        static Scope fromCode(byte code) {
            for (Scope scope : values()) {
                if (scope.code == code) return scope;
            }
            return null;
        }
    }

    /** One live listing. */
    public record Listing(String cardId, Scope scope, String scopeId, String reasonKey, int day) {}

    private MarketListings() {}

    /**
     * Puts {@code cardId} on sale under {@code scope}.
     *
     * @param reasonKey strings key naming the event that stocked it, shown on the card
     * @return true when this call created the listing; false when it already existed
     * @throws IllegalArgumentException when the card is unknown, or is Restricted and no
     *         held military warrant names it
     */
    public static boolean list(CampaignState state, String cardId, Scope scope, String scopeId,
                               String reasonKey, int day) {
        if (state == null || scope == null || scopeId == null || scopeId.isBlank()
                || reasonKey == null || reasonKey.isBlank()) {
            throw new IllegalArgumentException("A listing needs a state, a scope and a reason");
        }
        LoadoutCard card = LoadoutCard.parse(cardId);
        if (card == null) throw new IllegalArgumentException("Unknown loadout card: " + cardId);
        if (card.accessTier() == EquipmentAccessTier.RESTRICTED
                && !MilitaryWarrants.warrantedCardIds(state).contains(card.id())) {
            throw new IllegalArgumentException("Listing Restricted card " + cardId
                    + " without a military warrant naming it");
        }
        return state.recordMarketListing(card.id(), scope.code, scopeId, reasonKey, day) >= 0;
    }

    /** Every listing standing at {@code marketId}, whose owner is {@code factionId}, in listing order. */
    public static List<Listing> at(CampaignState state, String marketId, String factionId) {
        List<Listing> listings = new ArrayList<>();
        for (Listing listing : all(state)) {
            boolean here = listing.scope() == Scope.MARKET
                    ? listing.scopeId().equals(marketId)
                    : listing.scopeId().equals(factionId);
            if (here) listings.add(listing);
        }
        return listings;
    }

    /** The listing that put {@code cardId} on sale first, anywhere; null when it is not listed. */
    public static Listing firstFor(CampaignState state, String cardId) {
        for (Listing listing : all(state)) {
            if (listing.cardId().equals(cardId)) return listing;
        }
        return null;
    }

    /** Every listing, skipping cards this build no longer defines. */
    public static List<Listing> all(CampaignState state) {
        List<Listing> listings = new ArrayList<>();
        if (state == null || state.marketListingCardId == null) return listings;
        for (int i = 0; i < state.marketListingCount; i++) {
            Scope scope = Scope.fromCode(state.marketListingScopeKind[i]);
            String cardId = state.marketListingCardId[i];
            if (scope == null || state.marketListingScopeId[i] == null || LoadoutCard.parse(cardId) == null) {
                continue;
            }
            listings.add(new Listing(cardId, scope, state.marketListingScopeId[i],
                    state.marketListingReason[i], state.marketListingDay[i]));
        }
        return listings;
    }
}
