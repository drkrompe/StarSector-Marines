package com.dillon.starsectormarines.marine;

/**
 * External company-purse authority used by workshop previews and atomic commits.
 *
 * <p>Every room that buys pays from the company's credits, so this is one authority the Mech Lab
 * and the Boat Deck share rather than two views of the same account that can disagree about what
 * is in it. Buying needs a civilized port as well as the money ({@link #atMarket}); fitting,
 * stripping and moving what is already in stores need neither.
 */
public interface FabricationResources {

    FabricationResources NONE = new FabricationResources() {
        @Override public long credits() { return 0; }
        @Override public boolean atMarket() { return false; }
        @Override public boolean spend(FabricationCost cost) { return false; }
    };

    /** Credits the company can spend on a workshop job now. */
    long credits();

    /**
     * Whether the fleet is docked at a civilized market, where parts, hulls and yard time can be
     * bought. Away from one every purchase is refused and a screen says why.
     */
    boolean atMarket();

    /** Current installed-workshop price; both previews and commits ask here. */
    default FabricationCost quote(FabricationCost base) { return base; }

    /** Rechecks and pays the whole price, or pays nothing. It does not ask {@link #atMarket}; the workshops do. */
    boolean spend(FabricationCost cost);

    /** Pays a recycled part's credits into the purse; false where there is no purse to take them. */
    default boolean credit(FabricationCost refund) { return false; }

    /** How much the ship's machine shop gets back from a recycled spare now; previews and commits both ask. */
    default Reclamation reclamation() { return Reclamation.NONE; }

    default boolean canAfford(FabricationCost cost) {
        return cost != null && credits() >= cost.credits();
    }

    /** Both halves of a purchase: the port and the money. */
    default boolean canBuy(FabricationCost cost) {
        return atMarket() && canAfford(cost);
    }
}
