package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

/**
 * Atomic campaign authority for fitting and building a ship's boat.
 *
 * <p>The Boat Deck's counterpart to {@link MechWorkshop}, and deliberately the
 * simpler of the two: a fitting is installed work rather than a spare, so there
 * is no stock to check, nothing to return, and only one question to ask — can
 * the fleet pay for it. The outgoing fitting is scrapped, which is what
 * happens to plate somebody cuts off a hull.
 *
 * <p><b>A failed commit changes nothing.</b> Affordability is rechecked at the
 * moment of the commit and the whole bill is spent before anything is
 * installed or stood up, so a spend that fails leaves the boat, the deck and
 * the hold exactly as they were. The order matters: installing first and
 * paying afterwards would hand the player a better boat on a transaction that
 * did not complete.
 */
public final class BoatWorkshop {

    private final BoatDeck deck;
    private final FabricationResources resources;

    public BoatWorkshop(BoatDeck deck, FabricationResources resources) {
        if (deck == null || resources == null) {
            throw new IllegalArgumentException("boat deck and fabrication resources are required");
        }
        this.deck = deck;
        this.resources = resources;
    }

    /**
     * Fits one catalog fitting to one boat.
     *
     * <p>A fitting knows its own slot, so there is no wrong-slot outcome to
     * report: naming the fitting names where it goes.
     */
    public Result fit(String boatId, String fittingId) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        BoatFitting fitting = BoatFitting.findById(fittingId);
        if (fitting == null) return Result.of(Status.UNKNOWN_FITTING);
        if (fitting.id().equals(boat.fittingIn(fitting.slot()).id())) {
            return new Result(Status.ALREADY_FITTED, boat);
        }
        FabricationCost bill = fitting.bill();
        if (bill != null && !resources.spend(bill)) {
            return new Result(Status.CANNOT_AFFORD, boat);
        }
        boat.install(fitting);
        return new Result(Status.FITTED, boat);
    }

    /**
     * Builds the hull's own pattern into one empty berth, at standard fit.
     *
     * <p>What the deck carries is not the player's choice — it is what her bays
     * hold — so this names a berth rather than a pattern. A deck that has never
     * been reconciled has no berths and no pattern to build, and reports the
     * berth as missing, which is what it is.
     *
     * <p>The same order as a fit, for the same reason: the bill is spent in
     * full before anything is stood up, so a failed spend leaves the deck and
     * the hold exactly as they were.
     */
    public Result fabricate(int berthIndex) {
        ShuttleType pattern = deck.pattern();
        if (pattern == null || berthIndex < 0 || berthIndex >= deck.berths()) {
            return Result.of(Status.NO_SUCH_BERTH);
        }
        CampaignBoat standing = deck.boats().get(berthIndex);
        if (standing != null) return new Result(Status.BERTH_OCCUPIED, standing);
        BoatFabricationCatalog.Recipe recipe = BoatFabricationCatalog.recipe(pattern);
        if (recipe == null || !resources.spend(recipe.bill())) {
            return Result.of(Status.CANNOT_AFFORD);
        }
        CampaignBoat built = deck.fabricate(berthIndex);
        if (built == null) {
            throw new IllegalStateException("validated berth refused the boat built for it");
        }
        return new Result(Status.FABRICATED, built);
    }

    public enum Status {
        UNKNOWN_BOAT,
        UNKNOWN_FITTING,
        ALREADY_FITTED,
        CANNOT_AFFORD,
        FITTED,
        NO_SUCH_BERTH,
        BERTH_OCCUPIED,
        FABRICATED
    }

    public record Result(Status status, CampaignBoat boat) {
        private static Result of(Status status) { return new Result(status, null); }

        public boolean succeeded() {
            return status == Status.FITTED || status == Status.ALREADY_FITTED
                    || status == Status.FABRICATED;
        }
    }
}
