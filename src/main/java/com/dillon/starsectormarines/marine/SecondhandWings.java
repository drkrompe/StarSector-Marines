package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/** A one-time, reviewed purchase. Structural bay restoration remains a separate order. */
public final class SecondhandWings {
    public static final int CREDIT_COST = 5_000;
    private SecondhandWings() { }

    public record Trade(String id, String name, String plating, String drive) { }
    public record Review(String shipId, ShuttleType pattern, int berths, List<Trade> outgoing) { }

    public static Review review(BoatDeck deck) {
        return new Review(deck.shipId(), deck.pattern(), deck.berths(), deck.boats().stream().filter(Objects::nonNull)
                .map(boat -> new Trade(boat.id(), boat.displayName(), boat.plating().id(), boat.drive().id())).toList());
    }

    public static String refusal(BoatDeck deck, boolean counterattackComplete, boolean ownedHome,
                                 boolean bayRestored, boolean active, boolean docked, int credits) {
        if (deck.secondhandWingsPurchased()) return Strings.get("openingSecondhandWingsSecondhandWingsHasAlreadyBeenPurchased");
        if (!counterattackComplete) return Strings.get("openingSecondhandWingsCompleteCounterattackToHearMaraSOffer");
        if (!ownedHome) return Strings.get("openingSecondhandWingsQuarterTheCompanyAboardSecondWindS");
        if (!bayRestored || deck.berths() < 2) return Strings.get("openingSecondhandWingsRestoreTheSecondBayFirstASeparate");
        if (deck.pattern() != ShuttleType.HERMES) return Strings.get("openingSecondhandWingsThisOfferReplacesSecondWindSHermes");
        if (!active) return Strings.get("openingSecondhandWingsReactivateSecondWindBeforeOrderingYardWork");
        if (!docked) return Strings.get("openingSecondhandWingsDockAtAnAccessiblePlanetarySpaceportFor");
        if (credits < CREDIT_COST) return Strings.get("openingSecondhandWingsInsufficientCreditsThePairCostsCredits");
        return null;
    }

    /** Caller supplies current campaign eligibility; both it and the exact trade are rechecked. */
    public static String purchase(BoatDeck deck, Review reviewed, String liveRefusal, IntPredicate spend) {
        if (liveRefusal != null) return liveRefusal;
        if (deck.secondhandWingsPurchased()) return Strings.get("openingSecondhandWingsSecondhandWingsHasAlreadyBeenPurchased");
        if (deck.berths() < 2 || deck.pattern() != ShuttleType.HERMES
                || !Objects.equals(reviewed, review(deck))) return Strings.get("openingSecondhandWingsTheBoatDeckChangedReviewTheOffer");
        if (!spend.test(CREDIT_COST)) return Strings.get("openingSecondhandWingsThePurchaseCouldNotBePaidNothing");
        deck.acceptSecondhandWings();
        return null;
    }
}
