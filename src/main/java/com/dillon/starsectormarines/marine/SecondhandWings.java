package com.dillon.starsectormarines.marine;

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
        return new Review(deck.shipId(), deck.pattern(), deck.berths(), deck.airworthy().stream()
                .map(boat -> new Trade(boat.id(), boat.displayName(), boat.plating().id(), boat.drive().id())).toList());
    }

    public static String refusal(BoatDeck deck, boolean counterattackComplete, boolean ownedHome,
                                 boolean bayRestored, boolean active, boolean docked, int credits) {
        if (deck.secondhandWingsPurchased()) return "Secondhand Wings has already been purchased.";
        if (!counterattackComplete) return "Complete Counterattack to hear Mara's offer.";
        if (!ownedHome) return "Quarter the company aboard Second Wind's original hull.";
        if (!bayRestored || deck.berths() < 2) return "Restore the second bay first: a separate 8,000-credit yard order.";
        if (deck.pattern() != ShuttleType.HERMES) return "This offer replaces Second Wind's Hermes complement.";
        if (!active) return "Reactivate Second Wind before ordering yard work.";
        if (!docked) return "Dock at an accessible planetary spaceport for delivery and fitting.";
        if (credits < CREDIT_COST) return "Insufficient credits: the pair costs 5,000 credits.";
        return null;
    }

    /** Caller supplies current campaign eligibility; both it and the exact trade are rechecked. */
    public static String purchase(BoatDeck deck, Review reviewed, String liveRefusal, IntPredicate spend) {
        if (liveRefusal != null) return liveRefusal;
        if (deck.secondhandWingsPurchased()) return "Secondhand Wings has already been purchased.";
        if (deck.berths() < 2 || deck.pattern() != ShuttleType.HERMES
                || !Objects.equals(reviewed, review(deck))) return "The boat deck changed. Review the offer again.";
        if (!spend.test(CREDIT_COST)) return "The purchase could not be paid; nothing aboard was changed.";
        deck.acceptSecondhandWings();
        return null;
    }
}
