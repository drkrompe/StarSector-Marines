package com.dillon.starsectormarines.marine;

/**
 * Buying one spare part into stores and breaking one down for credits, the same way in every fitting room
 * (mechs-nouns.md, stores; owner: share how equipment works). {@link MechWorkshop} and {@link BoatWorkshop} each name
 * the part, its price and whether the company knows its pattern, and read the answer in their own words; the checks
 * and their order are this one rule.
 *
 * <p><b>A refusal changes nothing.</b> Every check runs before anything moves, the purse pays before a spare is
 * stowed, and a recycled spare leaves the shelf only once its credits are in the purse.
 */
final class PartShop {

    /** What one order came to. */
    enum Outcome {
        BUILT,
        RECYCLED,
        /** The part has no price, so no workshop builds it. */
        NOT_FABRICABLE,
        /** The company has not learned the part's pattern. */
        NOT_LEARNED,
        /** Buying needs the fleet docked at a civilized market. */
        NO_MARKET,
        /** The purse is short of the price. */
        CANNOT_AFFORD,
        NO_SPARE,
        /** The machine shop's machinery is not running, so nothing can be broken down. */
        WORKSHOP_IDLE,
        /** Too little of the price would come back to be worth a credit. */
        NOTHING_TO_RECOVER,
        /** The purse would not take what came back. */
        NO_HOLD
    }

    private PartShop() { }

    /**
     * Builds one spare of {@code partId} into {@code stores}: a part with a price, whose pattern is known, bought at a
     * port, paid in full.
     *
     * @param price what building one costs now, already quoted by the workshop; null for a part with no price
     */
    static Outcome build(FabricationResources resources, PartStores stores, String partId, FabricationCost price,
                         boolean known) {
        if (price == null) return Outcome.NOT_FABRICABLE;
        if (!known) return Outcome.NOT_LEARNED;
        if (!resources.atMarket()) return Outcome.NO_MARKET;
        if (!resources.spend(price)) return Outcome.CANNOT_AFFORD;
        stores.stow(partId);
        return Outcome.BUILT;
    }

    /** What recycling one spare would pay back now: the machine shop's share of {@code price}; null for nothing. */
    static FabricationCost recycleQuote(FabricationResources resources, FabricationCost price) {
        return price == null ? null : resources.reclamation().recover(price);
    }

    /**
     * Breaks one spare of {@code partId} in {@code stores} down for credits: a spare held, a running machine shop and
     * something to come back. It needs no port: the machine shop aboard does the work.
     *
     * @param price what building one costs now, already quoted by the workshop; null for a part with no price
     */
    static Outcome recycle(FabricationResources resources, PartStores stores, String partId, FabricationCost price) {
        if (stores.count(partId) <= 0) return Outcome.NO_SPARE;
        if (!resources.reclamation().works()) return Outcome.WORKSHOP_IDLE;
        FabricationCost back = recycleQuote(resources, price);
        if (back == null) return Outcome.NOTHING_TO_RECOVER;
        if (!resources.credit(back)) return Outcome.NO_HOLD;
        if (!stores.take(partId)) throw new IllegalStateException("a counted spare could not leave stores: " + partId);
        return Outcome.RECYCLED;
    }
}
