package com.dillon.starsectormarines.marine;

/**
 * Atomic campaign authority for fitting a ship's boat.
 *
 * <p>The Boat Deck's counterpart to {@link MechWorkshop}, and deliberately the
 * simpler of the two: a fitting is installed work rather than a spare, so there
 * is no stock to check, nothing to return, and only one question to ask — can
 * the fleet pay for it. The outgoing fitting is scrapped, which is what
 * happens to plate somebody cuts off a hull.
 *
 * <p><b>A failed fit changes nothing.</b> Affordability is rechecked at the
 * moment of the commit and the whole bill is spent before anything is
 * installed, so a spend that fails leaves both the boat and the hold exactly
 * as they were. The order matters: installing first and paying afterwards
 * would hand the player a better boat on a transaction that did not complete.
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

    public enum Status {
        UNKNOWN_BOAT,
        UNKNOWN_FITTING,
        ALREADY_FITTED,
        CANNOT_AFFORD,
        FITTED
    }

    public record Result(Status status, CampaignBoat boat) {
        private static Result of(Status status) { return new Result(status, null); }

        public boolean succeeded() {
            return status == Status.FITTED || status == Status.ALREADY_FITTED;
        }
    }
}
