package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.CompanyShipDesignation;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The founding hull's refit receipts and the yard orders that write them. A
 * receipt is saved under the hull's id, beside the original restoration
 * receipts, so it survives saves and company transfers and stays with her.
 */
public final class ShipRefits {
    private static final String KEY = "sm_ship_restoration_";
    private ShipRefits() { }

    /** Only the founding hull carries the tree; every other hull is neutral. */
    public static ShipCondition condition(FleetMemberAPI ship) {
        if (ship == null || CompanyBayRestoration.state(ship) == BoatBayState.NONE) return ShipCondition.NONE;
        return read(CampaignBackend.savedData(), ship.getId(),
                ShipCondition.fabrication(CompanyWorkshopRestoration.state(ship)));
    }

    /** Receipts count only while contiguous from the track's start, so no tier is ever skipped. */
    static ShipCondition read(Map<String, Object> data, String shipId, RefitBand fabrication) {
        return read(data, KEY + shipId, RefitTrack::start, fabrication, false);
    }

    /** The same reading for receipts saved under {@code prefix}, each track counted on from {@code start}. */
    static ShipCondition read(Map<String, Object> data, String prefix, Function<RefitTrack, RefitBand> start,
                              RefitBand fabrication, boolean fleet) {
        var bands = new EnumMap<RefitTrack, RefitBand>(RefitTrack.class);
        for (RefitTrack track : RefitTrack.values()) {
            RefitBand band = start.apply(track);
            for (RefitOrder order = RefitOrder.after(track, band, fleet); order != null
                    && Boolean.TRUE.equals(data.get(prefix + order.receipt)); order = RefitOrder.after(track, band, fleet))
                band = order.band;
            bands.put(track, band);
        }
        // A hull's fabrication is its workshop chain's; the fleet's is counted from its own receipts.
        if (fabrication != null) bands.put(RefitTrack.FABRICATION, fabrication);
        return new ShipCondition(true, bands, fleet);
    }

    /**
     * The current company's condition, for a launch to freeze: the fleet's facilities for a
     * company with no founding hull, otherwise the active founding hull's, and neutral off her.
     */
    public static ShipCondition aboard() {
        if (FleetFacilities.inUse()) return FleetFacilities.condition();
        var ship = CompanyShipDesignation.aboard();
        if (ship == null || ship.isMothballed() || !MercenaryCompanyConversion.installed(ship)) return ShipCondition.NONE;
        return condition(ship);
    }

    /** What a review shows: the condition, the credits and cargo on hand, and the first reason to refuse. */
    public record Offer(ShipCondition condition, int credits, Map<String, Integer> cargo, String refusal) {
        public Offer(ShipCondition condition, int credits, String refusal) { this(condition, credits, Map.of(), refusal); }
        public int held(String commodity) { return cargo.getOrDefault(commodity, 0); }
    }

    /**
     * The first reason an order cannot be bought now. AI cores are checked
     * after credits; {@code names} names a short core.
     */
    public static String refusal(RefitOrder order, ShipCondition condition, boolean home, boolean active,
                                 boolean reliefComplete, boolean docked, int credits,
                                 Map<String, Integer> cargo, Function<String, String> names) {
        if (!home || !condition.applies()) return Strings.get("openingCompanyShipRestorationHouseTheCompanyAboardSecondWindS");
        if (condition.band(order.track).atLeast(order.band)) return Strings.get("openingCompanyShipRestorationThisWorkIsAlreadyComplete");
        if (!active) return Strings.get("openingCompanyShipRestorationTheConvertedCompanyShipMustBeActive");
        if (!reliefComplete) return Strings.get("openingCompanyShipRestorationCompleteHoldUntilRelievedBeforeOrderingThis");
        if (condition.next(order.track) != order)
            return Strings.format("openingRefitPreviousTierFirst", RefitText.tier(order.track, condition.band(order.track).next()));
        var missing = condition.missing(order);
        if (missing != null) return RefitText.lock(missing.getKey(), missing.getValue());
        if (!docked) return Strings.get("openingCompanyShipRestorationDockAtAnAccessiblePlanetarySpaceportFor");
        if (credits < order.price) return Strings.get("openingCompanyShipRestorationInsufficientCredits");
        for (var need : order.cores().entrySet())
            if (cargo.getOrDefault(need.getKey(), 0) < need.getValue())
                return Strings.format("openingRefitShortOfMaterial", names.apply(need.getKey()),
                        need.getValue() - cargo.getOrDefault(need.getKey(), 0));
        return null;
    }

    public static Offer offer(FleetMemberAPI ship, MarineRoster roster, RefitOrder order) {
        var fleet = CampaignBackend.playerFleet();
        boolean home = fleet != null && ship != null && roster != null && ship.getId().equals(roster.companyShipId())
                && fleet.getFleetData().getMembersListCopy().contains(ship);
        return quote(condition(ship), order, home,
                ship != null && !ship.isMothballed() && MercenaryCompanyConversion.installed(ship),
                roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId));
    }

    /** A review of {@code order} against {@code condition}, reading the fleet's credits, cores and dock. */
    static Offer quote(ShipCondition condition, RefitOrder order, boolean home, boolean active, boolean reliefComplete) {
        var fleet = CampaignBackend.playerFleet();
        int credits = fleet == null ? 0 : (int) fleet.getCargo().getCredits().get();
        var cargo = new LinkedHashMap<String, Integer>();
        for (String commodity : order.cores().keySet())
            cargo.put(commodity, fleet == null ? 0 : (int) fleet.getCargo().getCommodityQuantity(commodity));
        return new Offer(condition, credits, Map.copyOf(cargo), refusal(order, condition, home, active, reliefComplete,
                CompanyYard.dockedMarket() != null, credits, cargo, CampaignCommodityPresentation.INSTANCE::commodityName));
    }

    /** Rechecks everything at payment; the hull's stats refresh so a combat tier shows at once. */
    public static String purchase(FleetMemberAPI ship, MarineRoster roster, RefitOrder order) {
        var offer = offer(ship, roster, order);
        if (offer.refusal() != null) return offer.refusal();
        pay(order, KEY + ship.getId());
        ship.updateStats();
        return null;
    }

    /** Takes the order's credits and cores from the fleet and saves its receipt under {@code prefix}. */
    static void pay(RefitOrder order, String prefix) {
        var fleetCargo = CampaignBackend.playerFleet().getCargo();
        fleetCargo.getCredits().subtract(order.price);
        for (var need : order.cores().entrySet()) fleetCargo.removeCommodity(need.getKey(), need.getValue());
        CampaignBackend.savedData().put(prefix + order.receipt, true);
    }
}
