package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The company's store: the technology a civilized market teaches for credits.
 *
 * <p>Loadout cards and mech patterns used to sit as special cargo on the market's open submarket,
 * where the player had to find them among the goods, buy them and right-click them. The store sells
 * the same things without the detour: what is on the shelf is chosen by exactly the policy the open
 * market used ({@link FactionEquipmentMarketStockPlanner}: size, standing, the company's victories
 * and reputation, warrants and story listings), a purchase pays from the company's purse and
 * teaches the {@link MarineArmory} or the {@link MechBay} directly, and nothing is left to carry.
 *
 * <p>This class is the rule; the Store page is its presentation. Cards that arrive some other way
 * (patron rewards, salvage, story rewards) are still cargo and are still learned by right-click.
 */
public final class EquipmentStore {

    /**
     * What one card costs before the market's tariff. It is the base price the card items carried
     * on the open market ({@code special_items.csv}), so the store charges what the shelf did.
     */
    public static final int BASE_PRICE = 5000;

    /** The standing with a market's faction at which its licensed shelf opens. */
    public static final RepLevel MIN_LICENSE_RELATIONSHIP = RepLevel.FAVORABLE;

    public enum Kind { LOADOUT, MECH_PATTERN }

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
    }

    public enum Sale {
        SOLD,
        ALREADY_KNOWN,
        /** The card or pattern no longer exists in this build, or its owner (armory, bay) is absent. */
        UNAVAILABLE,
        NOT_AT_MARKET,
        CANNOT_AFFORD
    }

    /** The market's side of the shelf plan; everything the policy reads about a place. */
    public record Place(String factionId, String marketId, int size, boolean military,
                        float tariff, boolean licensed) {}

    private EquipmentStore() {}

    public static boolean hasLicenseAccess(RepLevel relationship) {
        return relationship != null && relationship.isAtWorst(MIN_LICENSE_RELATIONSHIP);
    }

    /** The price at a market charging {@code tariff} on top of the base price. */
    public static long price(float tariff) {
        return Math.max(1L, Math.round(BASE_PRICE * (1.0 + Math.max(0f, tariff))));
    }

    /**
     * The shelf of one market for one monthly {@code rotation}: listed cards first, then the open
     * market's rotating cards, then the licensed ones, then the mech pattern slot. Deterministic for
     * the month, so reopening the page shows the same shelf.
     */
    public static List<Offer> shelf(Place place, long rotation,
                                    Set<String> unavailableCardIds, Set<String> unavailablePatternIds,
                                    EquipmentAcquisitionEligibility.Progress progress,
                                    List<String> listedCardIds) {
        long price = price(place.tariff());
        FactionEquipmentMarketStockPlanner.StockPlan plan = FactionEquipmentMarketStockPlanner.plan(
                place.factionId(), place.marketId(), place.size(), rotation, place.licensed(),
                unavailableCardIds, progress, listedCardIds);
        List<Offer> offers = new ArrayList<>();
        for (String id : plan.listedCardIds()) offers.add(new Offer(Kind.LOADOUT, id, Shelf.LISTED, price));
        for (String id : plan.marketCardIds()) offers.add(new Offer(Kind.LOADOUT, id, Shelf.MARKET, price));
        for (String id : plan.licensedCardIds()) offers.add(new Offer(Kind.LOADOUT, id, Shelf.LICENSE, price));
        for (String id : FactionEquipmentMarketStockPlanner.mechPattern(place.factionId(), place.marketId(),
                place.size(), place.military(), rotation, place.licensed(), unavailablePatternIds, progress)) {
            offers.add(new Offer(Kind.MECH_PATTERN, id, Shelf.MARKET, price));
        }
        return List.copyOf(offers);
    }

    /** Whether the company already has what the offer teaches. */
    public static boolean known(Offer offer, MarineArmory armory, MechBay bay) {
        return switch (offer.kind()) {
            case LOADOUT -> LoadoutCardLearning.status(armory, offer.id())
                    == LoadoutCardLearning.Result.ALREADY_KNOWN;
            case MECH_PATTERN -> MechPatternLearning.status(bay, offer.id())
                    == MechPatternLearning.Result.ALREADY_KNOWN;
        };
    }

    /**
     * Buys {@code offer}: pays the whole price and teaches what it names, or does neither. A sale
     * that cannot teach (an armory or bay that is not there, a card this build no longer defines)
     * is never charged.
     */
    public static Sale buy(Offer offer, MarineArmory armory, MechBay bay, FabricationResources purse) {
        if (offer == null || purse == null) return Sale.UNAVAILABLE;
        if (!purse.atMarket()) return Sale.NOT_AT_MARKET;
        switch (offer.kind()) {
            case LOADOUT -> {
                LoadoutCardLearning.Result status = LoadoutCardLearning.status(armory, offer.id());
                if (status == LoadoutCardLearning.Result.ALREADY_KNOWN) return Sale.ALREADY_KNOWN;
                if (status != LoadoutCardLearning.Result.LEARNED) return Sale.UNAVAILABLE;
            }
            case MECH_PATTERN -> {
                MechPatternLearning.Result status = MechPatternLearning.status(bay, offer.id());
                if (status == MechPatternLearning.Result.ALREADY_KNOWN) return Sale.ALREADY_KNOWN;
                if (status != MechPatternLearning.Result.LEARNED) return Sale.UNAVAILABLE;
            }
        }
        FabricationCost cost = FabricationCost.of(offer.price());
        if (!purse.canAfford(cost) || !purse.spend(cost)) return Sale.CANNOT_AFFORD;
        boolean learned = switch (offer.kind()) {
            case LOADOUT -> LoadoutCardLearning.learn(armory, offer.id()) == LoadoutCardLearning.Result.LEARNED;
            case MECH_PATTERN -> MechPatternLearning.learn(bay, offer.id()) == MechPatternLearning.Result.LEARNED;
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
        List<String> listed = new ArrayList<>();
        CampaignStateScript campaign = CampaignStateScript.getInstance();
        if (campaign != null) {
            for (MarketListings.Listing listing : MarketListings.at(
                    campaign.state(), market.getId(), market.getFactionId())) {
                listed.add(listing.cardId());
            }
        }
        SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
        boolean military = market.hasIndustry(Industries.MILITARYBASE)
                || market.hasIndustry(Industries.HIGHCOMMAND);
        Place place = new Place(market.getFactionId(), market.getId(), market.getSize(), military,
                open != null ? open.getTariff() : 0f, hasLicenseAccess(relationship));
        return shelf(place, rotation, unavailableCards, unavailablePatterns,
                EquipmentAcquisitionEligibility.currentProgress(), listed);
    }
}
