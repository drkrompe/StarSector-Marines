package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.ArrayList;
import java.util.List;

/**
 * Atomic campaign authority for fitting and building a ship's boat.
 *
 * <p>The Boat Deck's counterpart to {@link MechWorkshop}, and it keeps parts
 * the same way: a part is bought for its price into stores
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
 * moves, and a build pays its whole price before the spare is stowed, so a
 * refusal or a short purse leaves the boat, the stores and the purse exactly as
 * they were.
 *
 * <p><b>Buying needs a port.</b> Building a part or a boat and expediting a
 * repair are refused ({@link Status#NO_MARKET}) unless the fleet is docked at a
 * civilized market; fitting, stripping, moving and recycling what is already
 * aboard work anywhere.
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
        if (!deck.knowsPart(partId)) return Result.of(Status.NOT_LEARNED);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        if (!resources.spend(resources.quote(bill))) return Result.of(Status.CANNOT_AFFORD);
        deck.stow(partId);
        return Result.of(Status.PART_FABRICATED);
    }

    /**
     * What recycling one spare of {@code partId} would pay back now: the machine
     * shop's share of what building one costs. Null for a part with no bill, or a shop that
     * cannot recycle. It does not ask whether a spare is in stores; {@link #recycle} does.
     */
    public FabricationCost recycleQuote(String partId) {
        FabricationCost bill = billOf(partId);
        return bill == null ? null : resources.reclamation().recover(resources.quote(bill));
    }

    /**
     * Breaks one spare in stores down for credits. The purse is paid before the spare leaves
     * stores, so a fleet with no purse to take them keeps its spare. It needs no port: the
     * machine shop aboard does the work.
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
     * What scrapping one boat would pay back now: the machine shop's share of
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
     * to stores, then the shop's share of the hull goes to the purse. A shop that cannot
     * recycle still breaks her up, for her parts alone. The purse is checked before anything
     * moves, so a refusal leaves her standing as she was.
     */
    public Result scrap(String boatId) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (!scrappable(boat.pattern())) return new Result(Status.NOT_SCRAPPABLE, boat);
        FabricationCost back = scrapQuote(boatId);
        if (back != null && !resources.credit(back)) return new Result(Status.NO_HOLD, boat);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) strip(boatId, slot);
        for (int hardpoint = 0; hardpoint < boat.pattern().hardpoints(); hardpoint++) stripGun(boatId, hardpoint);
        if (!deck.scrap(boatId)) throw new IllegalStateException("a boat found on the deck could not leave her berth");
        return new Result(Status.SCRAPPED, boat);
    }

    /**
     * Whether a boat of {@code pattern} may be broken up. The Hermes may not, for now: a company
     * that scraps its gig and cannot pay for another has no lift left (owner, 2026-10-27).
     */
    public static boolean scrappable(ShuttleType pattern) {
        return pattern != null && pattern != ShuttleType.HERMES;
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

    /** Puts one gun in the top position of a hardpoint; see {@link #fitGun(String, int, int, String)}. */
    public Result fitGun(String boatId, int hardpoint, String gunId) {
        return fitGun(boatId, hardpoint, BoatHardpoint.TOP, gunId);
    }

    /**
     * Puts one gun in one position of a boat's hardpoint from a spare. A 2x2 gun takes the
     * whole hardpoint; a 2x1 gun takes its position, beside any 2x1 gun in the other
     * ({@link BoatHardpoint#placed}). Whatever comes off goes into stores.
     */
    public Result fitGun(String boatId, int hardpoint, int position, String gunId) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        BoatGun gun = BoatGun.findById(gunId);
        if (gun == null) return Result.of(Status.UNKNOWN_FITTING);
        if (!boat.hasHardpoint(hardpoint) || !BoatHardpoint.valid(position)) {
            return new Result(Status.UNKNOWN_HARDPOINT, boat);
        }
        BoatGun[] before = boat.gunsAt(hardpoint);
        if (BoatHardpoint.occupant(before, position) == gun) return new Result(Status.ALREADY_FITTED, boat);
        if (!gun.supports(boat.pattern())) return new Result(Status.INCOMPATIBLE_FITTING, boat);
        BoatGun[] after = BoatHardpoint.placed(before, position, gun);
        if (!BoatFrame.admits(boat, BoatFrame.weight(boat), BoatFrame.weightWithGuns(boat, hardpoint, after))) {
            return new Result(Status.OVER_FRAME, boat);
        }
        if (!deck.take(gun.id())) return new Result(Status.NO_SPARE, boat);
        boat.installGuns(hardpoint, after);
        for (BoatGun off : BoatHardpoint.displaced(before, after)) deck.stow(off.id());
        return new Result(Status.FITTED, boat);
    }

    /** Takes every gun on a hardpoint off into stores and leaves it bare. */
    public Result stripGun(String boatId, int hardpoint) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (!boat.hasHardpoint(hardpoint)) return new Result(Status.UNKNOWN_HARDPOINT, boat);
        List<BoatGun> guns = boat.gunsOn(hardpoint);
        if (guns.isEmpty()) return new Result(Status.NOTHING_TO_STRIP, boat);
        boat.installGuns(hardpoint, new BoatGun[BoatHardpoint.POSITIONS]);
        for (BoatGun gun : guns) deck.stow(gun.id());
        return new Result(Status.STRIPPED, boat);
    }

    /** Takes the gun filling one position of a hardpoint off into stores; the other position keeps its gun. */
    public Result stripGun(String boatId, int hardpoint, int position) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (!boat.hasHardpoint(hardpoint) || !BoatHardpoint.valid(position)) {
            return new Result(Status.UNKNOWN_HARDPOINT, boat);
        }
        BoatGun[] before = boat.gunsAt(hardpoint);
        if (BoatHardpoint.occupant(before, position) == null) return new Result(Status.NOTHING_TO_STRIP, boat);
        BoatGun[] after = BoatHardpoint.placed(before, position, null);
        boat.installGuns(hardpoint, after);
        for (BoatGun off : BoatHardpoint.displaced(before, after)) deck.stow(off.id());
        return new Result(Status.STRIPPED, boat);
    }

    /** Moves the top gun of one hardpoint to the top of another; see {@link #moveGun(String, int, int, int, int)}. */
    public Result moveGun(String boatId, int from, int to) {
        return moveGun(boatId, from, BoatHardpoint.TOP, to, BoatHardpoint.TOP);
    }

    /**
     * Moves the gun filling one hardpoint position to another position on the same boat, the
     * same hardpoint's other one included, with no trip through stores: the source position is
     * left empty, and whatever the gun displaces at the target goes into stores
     * ({@link BoatHardpoint#placed}).
     */
    public Result moveGun(String boatId, int from, int fromPosition, int to, int toPosition) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null) return Result.of(Status.UNKNOWN_BOAT);
        if (!boat.hasHardpoint(from) || !boat.hasHardpoint(to)
                || !BoatHardpoint.valid(fromPosition) || !BoatHardpoint.valid(toPosition)) {
            return new Result(Status.UNKNOWN_HARDPOINT, boat);
        }
        BoatGun gun = BoatHardpoint.occupant(boat.gunsAt(from), fromPosition);
        if (gun == null) return new Result(Status.NOTHING_TO_STRIP, boat);
        if (from == to && (fromPosition == toPosition || !gun.half())) return new Result(Status.ALREADY_FITTED, boat);
        BoatGun[] source = BoatHardpoint.placed(boat.gunsAt(from), fromPosition, null);
        BoatGun[] target = from == to ? source : boat.gunsAt(to);
        if (BoatHardpoint.occupant(target, toPosition) == gun) return new Result(Status.ALREADY_FITTED, boat);
        BoatGun[] landed = BoatHardpoint.placed(target, toPosition, gun);
        int before = BoatFrame.weight(boat);
        // The source loses the gun and the target takes it: both changes at once.
        int after = from == to ? BoatFrame.weightWithGuns(boat, to, landed)
                : BoatFrame.weightWithGuns(boat, from, source) + BoatFrame.weightWithGuns(boat, to, landed) - before;
        if (!BoatFrame.admits(boat, before, after)) return new Result(Status.OVER_FRAME, boat);
        if (from != to) boat.installGuns(from, source);
        boat.installGuns(to, landed);
        for (BoatGun off : BoatHardpoint.displaced(target, landed)) deck.stow(off.id());
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
     * <p>The same order as a fit, for the same reason: the price is paid in
     * full before anything is stood up, so a failed spend leaves the deck and
     * the purse exactly as they were.
     */
    public Result fabricate(int berthIndex) {
        ShuttleType pattern = deck.pattern();
        if (pattern == null || berthIndex < 0 || berthIndex >= deck.berths()) {
            return Result.of(Status.NO_SUCH_BERTH);
        }
        CampaignBoat standing = deck.boats().get(berthIndex);
        if (standing != null) return new Result(Status.BERTH_OCCUPIED, standing);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
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

    /** Half a replacement's price at maximum damage; the remaining work reduces it further. */
    public RepairQuote repairQuote(String boatId, float day) {
        CampaignBoat boat = deck.boatById(boatId);
        if (boat == null || boat.airworthy(day)) return null;
        float fraction = Math.min(1f, boat.repairDaysRemaining(day) / 2f);
        FabricationCost bill = BoatFabricationCatalog.recipe(boat.pattern()).bill().scaled(.5f * fraction);
        return new RepairQuote(boatId, boat.repairUntilDay(), resources.quote(bill));
    }

    /** A reviewed order may get cheaper as time passes, but may never spend more. */
    public Result expedite(RepairQuote reviewed, float day) {
        if (reviewed == null) return Result.of(Status.STALE_REPAIR);
        CampaignBoat boat = deck.boatById(reviewed.boatId());
        RepairQuote live = repairQuote(reviewed.boatId(), day);
        if (boat == null || live == null || live.untilDay() != reviewed.untilDay())
            return Result.of(Status.STALE_REPAIR);
        if (live.bill().credits() > reviewed.bill().credits()) return Result.of(Status.STALE_REPAIR);
        if (!resources.atMarket()) return new Result(Status.NO_MARKET, boat);
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
        /** Buying needs the fleet docked at a civilized market, and it is not. */
        NO_MARKET,
        NO_SPARE,
        NOT_FABRICABLE,
        /** The company has not learned this part; the company store sells the pattern. */
        NOT_LEARNED,
        NOTHING_TO_STRIP,
        FITTED,
        STRIPPED,
        PART_FABRICATED,
        RECYCLED,
        /** A boat was broken up: her parts are in stores and her berth stands vacant. */
        SCRAPPED,
        /** Her pattern is kept from the breakers ({@link BoatWorkshop#scrappable}). */
        NOT_SCRAPPABLE,
        /** The machine shop's machinery is not running, so nothing can be broken down. */
        WORKSHOP_IDLE,
        /** Too little of the price would come back to be worth a credit. */
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
