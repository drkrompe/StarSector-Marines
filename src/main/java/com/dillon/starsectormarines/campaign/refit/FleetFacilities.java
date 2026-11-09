package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.FleetRooms;
import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import java.util.Map;

/**
 * The refit tree of a company with no company ship: its upgrades belong to the fleet, not to
 * any hull, so selling, losing or replacing ships never takes one away. Every company but the
 * Second Wind start's runs on these (`fleet-facilities.md`).
 *
 * <p>Every track starts at its {@link RefitTrack#fleetStart() neutral} band, the figures
 * missions and evidence are tuned at, so a fresh company is exactly as strong as one on no
 * tree at all and only buys past it. The fabrication core is the fleet's machine shop, sold as
 * tiers of the tree rather than as the Second Wind's workshop chain.
 */
public final class FleetFacilities {
    /** Receipts are saved under this prefix and the order's own receipt suffix. */
    static final String RECEIPTS = "sm_fleet_facilities_";

    private FleetFacilities() { }

    /** The id the fleet's standard deck and its boat berths are kept under, in place of a hull's. */
    public static final String HOME_ID = "sm_fleet_facilities";

    /** The sizes {@code condition} has bought for the mock rooms. */
    public static FleetRooms rooms(ShipCondition condition) {
        return new FleetRooms(condition.mechGantries(), condition.boatBerths(), condition.bunkrooms());
    }

    /** The fleet's standard deck as bought now: what the Mech Lab, boat bay and barracks are cropped from. */
    public static CompanyShip ship() { return CompanyShip.fleet(rooms(condition())); }

    /**
     * The marines the company can billet, which bounds how many squads it raises: the fleet's
     * bunkrooms for fleet facilities, and no bound here for the Second Wind, whose deck has its own.
     */
    public static int billets() {
        return inUse() ? condition().bunkrooms() * RoomRecipe.TROOP_BERTHING.provides() : Integer.MAX_VALUE;
    }

    /** Whether the campaign's company runs on fleet facilities: any campaign but the Second Wind start's. */
    public static boolean inUse() {
        return CampaignBackend.savedData() != null && CerberusCompanyStart.startingShipId() == null;
    }

    /** The fleet's condition now; neutral outside a campaign. */
    public static ShipCondition condition() {
        Map<String, Object> data = CampaignBackend.savedData();
        return read(data == null ? Map.of() : data);
    }

    /** A fleet that has bought nothing: every track at its neutral start. */
    public static ShipCondition fresh() { return read(Map.of()); }

    /** The condition {@code data}'s receipts give, every track counted on from its fleet start. */
    static ShipCondition read(Map<String, Object> data) {
        return ShipRefits.read(data, RECEIPTS, RefitTrack::fleetStart, null, true);
    }

    /**
     * A review of {@code order} for the fleet. There is no hull to be aboard or to keep active,
     * and no story gate: only the tree's own order, the dock, credits and cores refuse.
     */
    public static ShipRefits.Offer offer(RefitOrder order) {
        return ShipRefits.quote(condition(), order, true, true, true);
    }

    /** Rechecks the review at payment, then takes the bill and saves the fleet's receipt. */
    public static String purchase(RefitOrder order) {
        var offer = offer(order);
        if (offer.refusal() != null) return offer.refusal();
        ShipRefits.pay(order, RECEIPTS);
        return null;
    }
}
