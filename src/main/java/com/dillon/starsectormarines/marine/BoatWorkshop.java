package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.ArrayList;
import java.util.List;

/**
 * Atomic campaign authority for fitting and building a ship's boat.
 *
 * <p>The Boat Deck's counterpart to {@link MechWorkshop}, and it keeps parts
 * the same way: a part is built from its bill into stores
 * ({@link #fabricatePart}), a fit takes a spare out of stores, and the part it
 * replaces goes back in as a spare. Taking a part off ({@link #strip}) returns
 * it to stores and leaves the socket empty. The stock parts a boat is built with
 * are parts like any other: they come off into stores and go back on from there.
 * A boat left without a required part cannot fly until one is fitted
 * ({@link CampaignBoat#missingParts}).
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

    /** Builds one spare part into stores: a fitting or a door gun, by id. */
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
     * What recycling one spare of {@code partId} would put back in the hold now: the machine
     * shop's share of what building one costs. Null for a part with no bill, or a shop that
     * cannot recycle. It does not ask whether a spare is in stores; {@link #recycle} does.
     */
    public FabricationCost recycleQuote(String partId) {
        FabricationCost bill = billOf(partId);
        return bill == null ? null : resources.reclamation().recover(resources.quote(bill));
    }

    /**
     * Breaks one spare in stores back down into materials. The hold is credited before the
     * spare leaves stores, so a fleet with no hold to take them keeps its spare.
     */
    public Result recycle(String partId) {
        if (BoatFitting.findById(partId) == null && BoatGun.findById(partId) == null) {
            return Result.of(Status.UNKNOWN_FITTING);
        }
        if (billOf(partId) == null) return Result.of(Status.NOT_FABRICABLE);
        if (deck.spares(partId) <= 0) return Result.of(Status.NO_SPARE);
        if (!resources.reclamation().works()) return Result.of(Status.WORKSHOP_IDLE);
        FabricationCost back = recycleQuote(partId);
        if (back == null) return Result.of(Status.NOTHING_TO_RECOVER);
        if (!resources.credit(back)) return Result.of(Status.NO_HOLD);
        deck.take(partId);
        return Result.of(Status.RECYCLED);
    }

    /**
     * What scrapping one boat would put back in the hold now: the machine shop's share of
     * her pattern's quote, less the stock parts a new boat of that pattern is built with.
     * Whatever is fitted to her goes to stores on its own, so the hull never pays for those
     * parts twice. Null when nothing would come back.
     */
    public FabricationCost scrapQuote(String boatId) {
        CampaignBoat boat = deck.boatById(boatId);
        FabricationCost price = boat == null ? null : fabricationQuote(boat.pattern());
        if (price == null) return null;
        return resources.reclamation().recover(Reclamation.hull(price, stockQuotes(boat.pattern())));
    }

    /**
     * What building a boat of {@code pattern} costs now, or null for a pattern with no recipe:
     * the workshop's quote of her bill, never less than her stock parts' quotes together.
     * Tooling rounds each part up on its own, so the boat's quote is covered again here.
     */
    public FabricationCost fabricationQuote(ShuttleType pattern) {
        BoatFabricationCatalog.Recipe recipe = pattern == null ? null : BoatFabricationCatalog.recipe(pattern);
        return recipe == null ? null : BoatFabricationCatalog.covering(resources.quote(recipe.bill()), stockQuotes(pattern));
    }

    private List<FabricationCost> stockQuotes(ShuttleType pattern) {
        List<FabricationCost> quotes = new ArrayList<>();
        for (FabricationCost part : BoatFabricationCatalog.stockParts(pattern)) quotes.add(resources.quote(part));
        return quotes;
    }

    /**
     * Breaks one boat up and leaves her berth vacant: every fitting and gun on her is stripped
     * to stores, then the shop's share of the hull goes to the hold. A shop that cannot
     * recycle still breaks her up, for her parts alone. The hold is checked before anything
     * moves, so a refusal leaves her standing as she was.
     */
    public Result scrap(String boatId) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        FabricationCost back = scrapQuote(boatId);
        if (back != null && !resources.credit(back)) return new Result(Status.NO_HOLD, boat);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) strip(boatId, slot);
        for (int hardpoint = 0; hardpoint < boat.pattern().hardpoints(); hardpoint++) stripGun(boatId, hardpoint);
        if (!deck.scrap(boatId)) throw new IllegalStateException("a boat found on the deck could not leave her berth");
        return new Result(Status.SCRAPPED, boat);
    }

    private static FabricationCost billOf(String partId) {
        BoatFitting fitting = BoatFitting.findById(partId);
        if (fitting != null) return fitting.bill();
        BoatGun gun = BoatGun.findById(partId);
        return gun == null ? null : gun.bill();
    }

    /**
     * Fits one catalog fitting to one boat from a spare, and puts what came off,
     * if anything, into stores.
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
        if (outgoing != null && fitting.id().equals(outgoing.id())) return new Result(Status.ALREADY_FITTED, boat);
        if (!fitting.supports(boat.pattern())) return new Result(Status.INCOMPATIBLE_FITTING, boat);
        if (!BoatFrame.admits(boat, BoatFrame.weight(boat), BoatFrame.weightWith(boat, fitting))) {
            return new Result(Status.OVER_FRAME, boat);
        }
        if (!deck.take(fitting.id())) return new Result(Status.NO_SPARE, boat);
        boat.install(fitting);
        if (outgoing != null) deck.stow(outgoing.id());
        return new Result(Status.FITTED, boat);
    }

    /**
     * Takes the fitting out of one slot into stores and leaves the slot empty. A
     * required part may come off: the boat then stays off the lift until one is fitted.
     */
    public Result strip(String boatId, BoatFittingSlot slot) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (slot == null) return new Result(Status.NOTHING_TO_STRIP, boat);
        BoatFitting outgoing = boat.fittingIn(slot);
        if (outgoing == null) return new Result(Status.NOTHING_TO_STRIP, boat);
        boat.clear(slot);
        deck.stow(outgoing.id());
        return new Result(Status.STRIPPED, boat);
    }

    /**
     * Puts one gun on one of a boat's hardpoints from a spare. The gun that came
     * off, if any, goes into stores.
     */
    public Result fitGun(String boatId, int hardpoint, String gunId) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        BoatGun gun = BoatGun.findById(gunId);
        if (gun == null) return Result.of(Status.UNKNOWN_FITTING);
        if (!boat.hasHardpoint(hardpoint)) return new Result(Status.UNKNOWN_HARDPOINT, boat);
        BoatGun current = boat.gunOn(hardpoint);
        if (current == gun) return new Result(Status.ALREADY_FITTED, boat);
        if (!gun.supports(boat.pattern())) return new Result(Status.INCOMPATIBLE_FITTING, boat);
        if (!BoatFrame.admits(boat, BoatFrame.weight(boat), BoatFrame.weightWithGun(boat, hardpoint, gun))) {
            return new Result(Status.OVER_FRAME, boat);
        }
        if (!deck.take(gun.id())) return new Result(Status.NO_SPARE, boat);
        boat.installGun(hardpoint, gun);
        if (current != null) deck.stow(current.id());
        return new Result(Status.FITTED, boat);
    }

    /** Takes a gun off into stores and leaves the hardpoint bare. */
    public Result stripGun(String boatId, int hardpoint) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (!boat.hasHardpoint(hardpoint)) return new Result(Status.UNKNOWN_HARDPOINT, boat);
        BoatGun current = boat.gunOn(hardpoint);
        if (current == null) return new Result(Status.NOTHING_TO_STRIP, boat);
        boat.installGun(hardpoint, null);
        deck.stow(current.id());
        return new Result(Status.STRIPPED, boat);
    }

    /**
     * Moves a gun from one hardpoint to another on the same boat, with no trip
     * through stores: the source is left bare, and whatever the target held goes
     * into stores. A bare target simply takes the gun.
     */
    public Result moveGun(String boatId, int from, int to) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (!boat.hasHardpoint(from) || !boat.hasHardpoint(to)) return new Result(Status.UNKNOWN_HARDPOINT, boat);
        BoatGun gun = boat.gunOn(from);
        BoatGun displaced = boat.gunOn(to);
        if (gun == null) return new Result(Status.NOTHING_TO_STRIP, boat);
        if (from == to || gun == displaced) return new Result(Status.ALREADY_FITTED, boat);
        int before = BoatFrame.weight(boat);
        // The source goes bare and the target takes this one: both changes at once.
        int after = BoatFrame.weightWithGun(boat, from, null) + BoatFrame.weightWithGun(boat, to, gun) - before;
        if (!BoatFrame.admits(boat, before, after)) return new Result(Status.OVER_FRAME, boat);
        boat.installGun(from, null);
        boat.installGun(to, gun);
        if (displaced != null) deck.stow(displaced.id());
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
        FabricationCost price = fabricationQuote(pattern);
        if (price == null || !resources.spend(price)) {
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
        RECYCLED,
        /** A boat was broken up: her parts are in stores and her berth stands vacant. */
        SCRAPPED,
        /** The machine shop's machinery is not running, so nothing can be broken down. */
        WORKSHOP_IDLE,
        /** Too little of the bill would come back to be worth a line. */
        NOTHING_TO_RECOVER,
        NO_HOLD,
        NO_SUCH_BERTH,
        BERTH_OCCUPIED,
        FABRICATED
    }

    public record Result(Status status, CampaignBoat boat) {
        private static Result of(Status status) { return new Result(status, null); }

        public boolean succeeded() {
            return status == Status.FITTED || status == Status.ALREADY_FITTED
                    || status == Status.STRIPPED || status == Status.PART_FABRICATED
                    || status == Status.RECYCLED || status == Status.SCRAPPED
                    || status == Status.FABRICATED || status == Status.REPAIRED;
        }
    }
}
