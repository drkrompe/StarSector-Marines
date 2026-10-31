package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The company's store: the technology a civilized market teaches for credits.
 *
 * <p>Loadout cards and mech patterns used to sit as special cargo on the market's open submarket,
 * where the player had to find them among the goods, buy them and right-click them. The store sells
 * the same things without the detour: the Kit Ladder alone decides what is on the shelf
 * ({@link StoreStock}: every card and pattern whose station the campaign has passed, at every store,
 * with warrants and story listings), a purchase pays from the company's purse and
 * teaches the {@link MarineArmory} or the {@link MechBay} directly, and nothing is left to carry.
 *
 * <p>This class is the rule; the Store page is its presentation. Cards that arrive some other way
 * (patron rewards, salvage, story rewards) are still cargo and are still learned by right-click.
 */
public final class EquipmentStore {

    /**
     * What a Common card costs before the market's tariff. It is the base price the card items
     * carried on the open market ({@code special_items.csv}), so a Common card costs what the shelf
     * charged; higher tiers are a multiple of it ({@link #tierMultiplier}). It is set against a basic
     * contract's pay of about 5,000: a Common card is half a job, an Advanced one about a whole job.
     */
    public static final int BASE_PRICE = 2000;

    /** The standing with a market's faction at which its licensed shelf opens. */
    public static final RepLevel MIN_LICENSE_RELATIONSHIP = RepLevel.FAVORABLE;

    public enum Kind { LOADOUT, MECH_PATTERN, BOAT_PART }

    /** Which shelf of the market an offer stands on; it is the reason shown for the offer. */
    public enum Shelf { LISTED, MARKET, LICENSE }

    /** One thing for sale. {@code id} is a loadout card id or a mech pattern id, by {@code kind}. */
    public record Offer(Kind kind, String id, Shelf shelf, long price) {
        public Offer {
            if (kind == null || id == null || shelf == null) {
                throw new IllegalArgumentException("An offer needs a kind, an id and a shelf");
            }
            if (price <= 0) throw new IllegalArgumentException("An offer has a positive price");
        }

        /** Where the store files it; null for an item this build no longer defines. */
        public StoreSlot slot() {
            return StoreSlot.of(kind, id);
        }
    }

    public enum Sale {
        SOLD,
        ALREADY_KNOWN,
        /** The card or pattern no longer exists in this build, or its owner (armory, bay) is absent. */
        UNAVAILABLE,
        NOT_AT_MARKET,
        CANNOT_AFFORD
    }

    /** What the company already holds: where a purchase is learned, and how it is known to be known. */
    public record Holdings(MarineArmory armory, MechBay bay, BoatDeck deck) {}

    /**
     * The market's side of the shelf; everything the policy reads about a place. Its size and id only seed and size
     * the boat part rotation; its faction and licence decide where Restricted cards stand.
     */
    public record Place(String factionId, String marketId, int size, float tariff, boolean licensed) {}

    private EquipmentStore() {}

    public static boolean hasLicenseAccess(RepLevel relationship) {
        return relationship != null && relationship.isAtWorst(MIN_LICENSE_RELATIONSHIP);
    }

    /**
     * How many base prices a card of {@code tier} costs: the scarcer the kit, the more the port asks
     * for teaching it. Restricted kit is only ever on a shelf through a warrant, and is priced as the
     * dearest of all.
     */
    public static int tierMultiplier(EquipmentAccessTier tier) {
        if (tier == null) return 1;
        return switch (tier) {
            case COMMON -> 1;
            case ADVANCED -> 2;
            case PRESTIGE -> 4;
            case RESTRICTED -> 6;
        };
    }

    /** The price of a {@code tier} card at a market charging {@code tariff} on top of the tier's price. */
    public static long price(EquipmentAccessTier tier, float tariff) {
        return Math.max(1L, Math.round(BASE_PRICE * (double) tierMultiplier(tier)
                * (1.0 + Math.max(0f, tariff))));
    }

    private static EquipmentAccessTier tierOf(Kind kind, String id) {
        return switch (kind) {
            case LOADOUT -> {
                LoadoutCard card = LoadoutCard.parse(id);
                yield card == null ? null : card.accessTier();
            }
            case MECH_PATTERN -> {
                MechPattern pattern = MechPattern.parse(id);
                yield pattern == null ? null : pattern.accessTier();
            }
            case BOAT_PART -> BoatParts.accessTier(id);
        };
    }

    private static Offer offer(Kind kind, String id, Shelf shelf, float tariff) {
        return new Offer(kind, id, shelf, price(tierOf(kind, id), tariff));
    }

    /**
     * The shelf of one market ({@link StoreStock}): every loadout card and machine pattern the Kit Ladder has opened
     * and the company lacks, the market's listed cards among them, and the boat parts of this month's
     * {@code rotation}. In {@link #shelfOrder} with the ids standing in for names; the page sorts by display name.
     */
    public static List<Offer> shelf(Place place, long rotation,
                                    Set<String> unavailableCardIds, Set<String> unavailablePatternIds,
                                    Set<String> unavailablePartIds,
                                    EquipmentAcquisitionEligibility.Progress progress,
                                    List<String> listedCardIds) {
        StoreStock.StockPlan plan = StoreStock.cards(place.factionId(), place.licensed(),
                unavailableCardIds, progress, listedCardIds);
        List<Offer> offers = new ArrayList<>();
        for (String id : plan.listedCardIds()) offers.add(offer(Kind.LOADOUT, id, Shelf.LISTED, place.tariff()));
        for (String id : plan.marketCardIds()) offers.add(offer(Kind.LOADOUT, id, Shelf.MARKET, place.tariff()));
        for (String id : plan.licensedCardIds()) offers.add(offer(Kind.LOADOUT, id, Shelf.LICENSE, place.tariff()));
        for (String id : StoreStock.patterns(unavailablePatternIds, progress)) {
            offers.add(offer(Kind.MECH_PATTERN, id, Shelf.MARKET, place.tariff()));
        }
        Shelf partShelf = place.licensed() ? Shelf.LICENSE : Shelf.MARKET;
        for (String id : StoreStock.boatParts(place.factionId(), place.marketId(),
                place.size(), rotation, place.licensed(), unavailablePartIds, progress)) {
            offers.add(offer(Kind.BOAT_PART, id, partShelf, place.tariff()));
        }
        offers.sort(shelfOrder(Offer::id));
        return List.copyOf(offers);
    }

    /**
     * The order a shelf is read in: listed cards first, then by store slot (the category the page browses), then by
     * Kit Ladder station, then by {@code name}.
     */
    public static Comparator<Offer> shelfOrder(Function<Offer, String> name) {
        return Comparator.comparingInt((Offer offer) -> offer.shelf() == Shelf.LISTED ? 0 : 1)
                .thenComparingInt(offer -> {
                    StoreSlot slot = offer.slot();
                    return slot == null ? Integer.MAX_VALUE : slot.ordinal();
                })
                .thenComparing(offer -> KitLadder.station(offer.id()))
                .thenComparing(name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    /** Whether the company already has what the offer teaches. */
    public static boolean known(Offer offer, Holdings held) {
        return switch (offer.kind()) {
            case LOADOUT -> LoadoutCardLearning.status(held.armory(), offer.id())
                    == LoadoutCardLearning.Result.ALREADY_KNOWN;
            case MECH_PATTERN -> MechPatternLearning.status(held.bay(), offer.id())
                    == MechPatternLearning.Result.ALREADY_KNOWN;
            case BOAT_PART -> BoatPartLearning.status(held.deck(), offer.id())
                    == BoatPartLearning.Result.ALREADY_KNOWN;
        };
    }

    /**
     * Buys {@code offer}: pays the whole price and teaches what it names, or does neither. A sale
     * that cannot teach (an armory or bay that is not there, a card this build no longer defines)
     * is never charged.
     */
    public static Sale buy(Offer offer, Holdings held, FabricationResources purse) {
        if (offer == null || purse == null) return Sale.UNAVAILABLE;
        if (!purse.atMarket()) return Sale.NOT_AT_MARKET;
        switch (offer.kind()) {
            case LOADOUT -> {
                LoadoutCardLearning.Result status = LoadoutCardLearning.status(held.armory(), offer.id());
                if (status == LoadoutCardLearning.Result.ALREADY_KNOWN) return Sale.ALREADY_KNOWN;
                if (status != LoadoutCardLearning.Result.LEARNED) return Sale.UNAVAILABLE;
            }
            case MECH_PATTERN -> {
                MechPatternLearning.Result status = MechPatternLearning.status(held.bay(), offer.id());
                if (status == MechPatternLearning.Result.ALREADY_KNOWN) return Sale.ALREADY_KNOWN;
                if (status != MechPatternLearning.Result.LEARNED) return Sale.UNAVAILABLE;
            }
            case BOAT_PART -> {
                BoatPartLearning.Result status = BoatPartLearning.status(held.deck(), offer.id());
                if (status == BoatPartLearning.Result.ALREADY_KNOWN) return Sale.ALREADY_KNOWN;
                if (status != BoatPartLearning.Result.LEARNED) return Sale.UNAVAILABLE;
            }
        }
        FabricationCost cost = FabricationCost.of(offer.price());
        if (!purse.canAfford(cost) || !purse.spend(cost)) return Sale.CANNOT_AFFORD;
        boolean learned = switch (offer.kind()) {
            case LOADOUT -> LoadoutCardLearning.learn(held.armory(), offer.id())
                    == LoadoutCardLearning.Result.LEARNED;
            case MECH_PATTERN -> MechPatternLearning.learn(held.bay(), offer.id())
                    == MechPatternLearning.Result.LEARNED;
            case BOAT_PART -> BoatPartLearning.learn(held.deck(), offer.id())
                    == BoatPartLearning.Result.LEARNED;
        };
        if (!learned) {
            purse.credit(cost);
            return Sale.UNAVAILABLE;
        }
        return Sale.SOLD;
    }

    /**
     * The shelf of {@code market} in the running campaign, or empty with no campaign. Cards the
     * company already knows or carries are not offered again.
     */
    public static List<Offer> shelfAt(MarketAPI market) {
        SectorAPI sector = Global.getSector();
        if (sector == null || market == null) return List.of();
        CampaignClockAPI clock = sector.getClock();
        long rotation = clock != null ? (long) clock.getCycle() * 12L + clock.getMonth() : 0L;
        FactionAPI playerFaction = sector.getPlayerFaction();
        RepLevel relationship = playerFaction != null
                ? playerFaction.getRelationshipLevel(market.getFactionId()) : null;

        Set<String> unavailableCards = LoadoutCardInventory.playerUnavailableCardIds();
        if (unavailableCards == null) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            unavailableCards = script != null
                    ? LoadoutCardInventory.knownCardIds(script.roster().armory()) : Set.of();
        }
        Set<String> unavailablePatterns = MechPatternInventory.playerUnavailablePatternIds();
        if (unavailablePatterns == null) {
            unavailablePatterns = MechPatternInventory.knownPatternIds(MechPatternInventory.playerBay());
        }
        MarineRosterScript roster = MarineRosterScript.getInstance();
        BoatDeck deck = roster != null ? roster.roster().boatDeck() : null;
        Set<String> unavailableParts = new HashSet<>(BoatParts.starterIds());
        if (deck != null) unavailableParts.addAll(deck.knownPartIds());
        List<String> listed = new ArrayList<>();
        CampaignStateScript campaign = CampaignStateScript.getInstance();
        if (campaign != null) {
            for (MarketListings.Listing listing : MarketListings.at(
                    campaign.state(), market.getId(), market.getFactionId())) {
                listed.add(listing.cardId());
            }
        }
        SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
        Place place = new Place(market.getFactionId(), market.getId(), market.getSize(),
                open != null ? open.getTariff() : 0f, hasLicenseAccess(relationship));
        return shelf(place, rotation, unavailableCards, unavailablePatterns, unavailableParts,
                EquipmentAcquisitionEligibility.currentProgress(), listed);
    }
}
