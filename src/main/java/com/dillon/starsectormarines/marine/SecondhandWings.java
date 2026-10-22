package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * A one-time, reviewed purchase. A hull whose second bay is still sealed takes the
 * yard's bay restoration in the same order at the combined price, so the pair is never
 * refused for want of a separate job the player has to find first.
 */
public final class SecondhandWings {
    public static final int CREDIT_COST = 5_000;
    /** The second bay's restoration, as {@code CompanyBayRestoration} prices it on its own. */
    public static final int BAY_COST = 8_000;
    private SecondhandWings() { }

    /** What the order costs: the pair alone, or the pair with the sealed bay opened. */
    public static int price(boolean bayRestored) { return bayRestored ? CREDIT_COST : CREDIT_COST + BAY_COST; }

    /** Whether this deck's order carries the bay: the deck stands one berth short of the pair. */
    public static boolean bundlesBay(BoatDeck deck) { return deck.berths() < 2; }

    public record Trade(String id, String name, String plating, String drive) { }
    public record Review(String shipId, ShuttleType pattern, int berths, List<Trade> outgoing) { }

    public static Review review(BoatDeck deck) {
        return new Review(deck.shipId(), deck.pattern(), deck.berths(), deck.airworthy().stream()
                .map(boat -> new Trade(boat.id(), boat.displayName(), boat.plating().id(), boat.drive().id())).toList());
    }

    public static String refusal(BoatDeck deck, boolean counterattackComplete, boolean ownedHome,
                                 boolean bayRestored, boolean active, boolean docked, int credits) {
        if (deck.secondhandWingsPurchased()) return Strings.get("openingSecondhandWingsSecondhandWingsHasAlreadyBeenPurchased");
        if (!counterattackComplete) return Strings.get("openingSecondhandWingsCompleteCounterattackToHearMaraSOffer");
        if (!ownedHome) return Strings.get("openingSecondhandWingsQuarterTheCompanyAboardSecondWindS");
        if (deck.pattern() != ShuttleType.HERMES) return Strings.get("openingSecondhandWingsThisOfferReplacesSecondWindSHermes");
        if (!active) return Strings.get("openingSecondhandWingsReactivateSecondWindBeforeOrderingYardWork");
        if (!docked) return Strings.get("openingSecondhandWingsDockAtAnAccessiblePlanetarySpaceportFor");
        int price = price(bayRestored && !bundlesBay(deck));
        if (credits < price) return Strings.format("openingSecondhandWingsInsufficientCreditsThePairCostsCredits", price);
        return null;
    }

    /** Caller supplies current campaign eligibility; both it and the exact trade are rechecked. */
    public static String purchase(BoatDeck deck, Review reviewed, String liveRefusal, IntPredicate spend) {
        return purchase(deck, reviewed, liveRefusal, spend, () -> { });
    }

    /**
     * As above; a deck one berth short pays the combined price and {@code restoreBay} opens
     * the second berth after payment and before delivery, so both boats have somewhere to stand.
     */
    public static String purchase(BoatDeck deck, Review reviewed, String liveRefusal, IntPredicate spend,
                                  Runnable restoreBay) {
        if (liveRefusal != null) return liveRefusal;
        if (deck.secondhandWingsPurchased()) return Strings.get("openingSecondhandWingsSecondhandWingsHasAlreadyBeenPurchased");
        if (deck.pattern() != ShuttleType.HERMES
                || !Objects.equals(reviewed, review(deck))) return Strings.get("openingSecondhandWingsTheBoatDeckChangedReviewTheOffer");
        boolean bundled = bundlesBay(deck);
        if (!spend.test(price(!bundled))) return Strings.get("openingSecondhandWingsThePurchaseCouldNotBePaidNothing");
        if (bundled) restoreBay.run();
        deck.acceptSecondhandWings();
        return null;
    }
}
