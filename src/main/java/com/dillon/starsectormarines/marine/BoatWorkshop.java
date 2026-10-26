package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

/**
 * Atomic campaign authority for fitting and building a ship's boat.
 *
 * <p>The Boat Deck's counterpart to {@link MechWorkshop}, and it keeps parts
 * the same way: a part is built from its bill into stores
 * ({@link #fabricatePart}), a fit takes a spare out of stores, and the part it
 * replaces goes back in as a spare. Taking a part off ({@link #strip}) returns
 * it to stores and leaves the socket on its yard-standard part. A yard-standard
 * part is never stored and never runs out: every socket has its own, so
 * putting it back is free and taking it off makes no spare.
 *
 * <p>A fit is weighed against the boat's frame ({@link BoatFrame}): one that
 * would take her over the rating is refused, unless it leaves her no heavier
 * than she is.
 *
 * <p><b>A failed commit changes nothing.</b> Every check runs before anything
 * moves, and a build spends its whole bill before the spare is stowed, so a
 * refusal or a short hold leaves the boat, the stores and the hold exactly as
 * they were.
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
     * Builds one spare part into stores: a fitting or a door gun, by id. A
     * yard-standard fitting has no bill and is never stored, so it is refused.
     */
    public Result fabricatePart(String partId) {
        BoatFitting fitting = BoatFitting.findById(partId);
        BoatGun gun = fitting == null ? BoatGun.findById(partId) : null;
        if (fitting == null && gun == null) return Result.of(Status.UNKNOWN_FITTING);
        FabricationCost bill = fitting != null ? fitting.bill() : gun.bill();
        if (bill == null) return Result.of(Status.NOT_FABRICABLE);
        if (!resources.spend(resources.quote(bill))) return Result.of(Status.CANNOT_AFFORD);
        deck.stow(partId);
        return Result.of(Status.PART_FABRICATED);
    }

    /**
     * Fits one catalog fitting to one boat, from a spare unless it is the
     * yard-standard one, and puts what came off into stores.
     *
     * <p>A fitting knows its own slot, so there is no wrong-slot outcome to
     * report: naming the fitting names where it goes.
     */
    public Result fit(String boatId, String fittingId) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        BoatFitting fitting = BoatFitting.findById(fittingId);
        if (fitting == null) return Result.of(Status.UNKNOWN_FITTING);
        BoatFitting outgoing = boat.fittingIn(fitting.slot());
        if (fitting.id().equals(outgoing.id())) return new Result(Status.ALREADY_FITTED, boat);
        if (!fitting.supports(boat.pattern())) return new Result(Status.INCOMPATIBLE_FITTING, boat);
        if (!BoatFrame.admits(boat, BoatFrame.weight(boat), BoatFrame.weightWith(boat, fitting))) {
            return new Result(Status.OVER_FRAME, boat);
        }
        if (!fitting.standard() && !deck.take(fitting.id())) return new Result(Status.NO_SPARE, boat);
        boat.install(fitting);
        if (!outgoing.standard()) deck.stow(outgoing.id());
        return new Result(Status.FITTED, boat);
    }

    /** Takes the fitting out of one slot into stores; the slot goes back to its yard-standard part. */
    public Result strip(String boatId, BoatFittingSlot slot) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (slot == null) return new Result(Status.NOTHING_TO_STRIP, boat);
        BoatFitting outgoing = boat.fittingIn(slot);
        if (outgoing.standard()) return new Result(Status.NOTHING_TO_STRIP, boat);
        boat.install(BoatFitting.standard(slot));
        deck.stow(outgoing.id());
        return new Result(Status.STRIPPED, boat);
    }

    /**
     * Puts one door gun on one of a boat's hardpoints: the hardpoint's own yard
     * gun for nothing, any other from a spare. The gun that came off goes into
     * stores unless it was that hardpoint's yard gun.
     */
    public Result fitGun(String boatId, int hardpoint, String gunId) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        BoatGun gun = BoatGun.findById(gunId);
        if (gun == null) return Result.of(Status.UNKNOWN_FITTING);
        BoatGun current = boat.gunOn(hardpoint);
        if (current == null) return new Result(Status.UNKNOWN_HARDPOINT, boat);
        if (current == gun) return new Result(Status.ALREADY_FITTED, boat);
        if (!gun.supports(boat.pattern())) return new Result(Status.INCOMPATIBLE_FITTING, boat);
        if (!BoatFrame.admits(boat, BoatFrame.weight(boat), BoatFrame.weightWithGun(boat, hardpoint, gun))) {
            return new Result(Status.OVER_FRAME, boat);
        }
        if (!gun.standardOn(boat.pattern(), hardpoint) && !deck.take(gun.id())) {
            return new Result(Status.NO_SPARE, boat);
        }
        boat.installGun(hardpoint, gun);
        if (!current.standardOn(boat.pattern(), hardpoint)) deck.stow(current.id());
        return new Result(Status.FITTED, boat);
    }

    /** Takes a door gun off into stores; the hardpoint goes back to its yard gun. */
    public Result stripGun(String boatId, int hardpoint) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        BoatGun current = boat.gunOn(hardpoint);
        if (current == null) return new Result(Status.UNKNOWN_HARDPOINT, boat);
        BoatGun yard = BoatGun.standardFor(boat.pattern(), hardpoint);
        if (yard == null || current == yard) return new Result(Status.NOTHING_TO_STRIP, boat);
        boat.installGun(hardpoint, yard);
        deck.stow(current.id());
        return new Result(Status.STRIPPED, boat);
    }

    /**
     * Moves a door gun from one hardpoint to another on the same boat, with no
     * trip through stores: the source goes back to its yard gun, and whatever
     * the target held goes into stores unless it was the target's yard gun. A
     * yard gun is not moved; it is every hardpoint's for nothing already.
     */
    public Result moveGun(String boatId, int from, int to) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        BoatGun gun = boat.gunOn(from);
        BoatGun displaced = boat.gunOn(to);
        if (gun == null || displaced == null) return new Result(Status.UNKNOWN_HARDPOINT, boat);
        BoatGun yard = BoatGun.standardFor(boat.pattern(), from);
        if (yard == null || gun == yard) return new Result(Status.NOTHING_TO_STRIP, boat);
        if (from == to || gun == displaced) return new Result(Status.ALREADY_FITTED, boat);
        int before = BoatFrame.weight(boat);
        // The source goes back to its yard gun and the target takes this one: both changes at once.
        int after = BoatFrame.weightWithGun(boat, from, yard) + BoatFrame.weightWithGun(boat, to, gun) - before;
        if (!BoatFrame.admits(boat, before, after)) return new Result(Status.OVER_FRAME, boat);
        boat.installGun(from, yard);
        boat.installGun(to, gun);
        if (!displaced.standardOn(boat.pattern(), to)) deck.stow(displaced.id());
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
        if (recipe == null || !resources.spend(resources.quote(recipe.bill()))) {
            return Result.of(Status.CANNOT_AFFORD);
        }
        CampaignBoat built = deck.fabricate(berthIndex);
        if (built == null) {
            throw new IllegalStateException("validated berth refused the boat built for it");
        }
        return new Result(Status.FABRICATED, built);
    }

    /** Half a replacement bill at maximum damage; the remaining work reduces it further. */
    public RepairQuote repairQuote(String boatId, float day) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null || boat.airworthy(day)) return null;
        float fraction = Math.min(1f, boat.repairDaysRemaining(day) / 2f);
        FabricationCost full = BoatFabricationCatalog.recipe(boat.pattern()).bill();
        FabricationCost bill = new FabricationCost(full.lines().stream().map(line ->
                new FabricationCost.Line(line.commodityId(),
                        Math.max(1, (int) Math.ceil(line.quantity() * .5f * fraction)))).toList());
        return new RepairQuote(boatId, boat.repairUntilDay(), resources.quote(bill));
    }

    /** A reviewed order may get cheaper as time passes, but may never spend more. */
    public Result expedite(RepairQuote reviewed, float day) {
        if (reviewed == null) return Result.of(Status.STALE_REPAIR);
        CampaignBoat boat = deck.boatById(reviewed.boatId());
        RepairQuote live = repairQuote(reviewed.boatId(), day);
        if (boat == null || live == null || live.untilDay() != reviewed.untilDay())
            return Result.of(Status.STALE_REPAIR);
        for (FabricationCost.Line line : live.bill().lines()) {
            int approved = reviewed.bill().lines().stream()
                    .filter(old -> old.commodityId().equals(line.commodityId()))
                    .mapToInt(FabricationCost.Line::quantity).sum();
            if (line.quantity() > approved) return Result.of(Status.STALE_REPAIR);
        }
        if (!resources.spend(live.bill())) return new Result(Status.CANNOT_AFFORD, boat);
        boat.finishRepairs();
        return new Result(Status.REPAIRED, boat);
    }

    public record RepairQuote(String boatId, float untilDay, FabricationCost bill) { }

    public enum Status {
        REPAIRED,
        STALE_REPAIR,
        UNKNOWN_BOAT,
        UNKNOWN_FITTING,
        UNKNOWN_HARDPOINT,
        INCOMPATIBLE_FITTING,
        OVER_FRAME,
        ALREADY_FITTED,
        CANNOT_AFFORD,
        NO_SPARE,
        NOT_FABRICABLE,
        NOTHING_TO_STRIP,
        FITTED,
        STRIPPED,
        PART_FABRICATED,
        NO_SUCH_BERTH,
        BERTH_OCCUPIED,
        FABRICATED
    }

    public record Result(Status status, CampaignBoat boat) {
        private static Result of(Status status) { return new Result(status, null); }

        public boolean succeeded() {
            return status == Status.FITTED || status == Status.ALREADY_FITTED
                    || status == Status.STRIPPED || status == Status.PART_FABRICATED
                    || status == Status.FABRICATED || status == Status.REPAIRED;
        }
    }
}
