package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.intel.CompanyFoundingIntel;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.Comparator;

/** Runs once, after vanilla's ordinary skip-tutorial setup, only for this selected start. */
public final class CerberusCompanyStart implements Script {
    public static final String SHIP_KEY = "sm_cerberus_start_ship";
    private static final String DEPOT_KEY = "sm_preparation_depot";
    private static final String DEPOT_ISSUED = "sm_preparation_depot_issued";
    public static final int STARTING_CREDITS = 15000;
    public static final int TRAVEL_SUPPLIES = 25;

    @Override public void run() {
        var sector = Global.getSector();
        if (sector.getPersistentData().containsKey(SHIP_KEY)) return;
        var fleet = sector.getPlayerFleet();
        FleetMemberAPI ship = fleet.getFleetData().getMembersListCopy().stream()
                .filter(MercenaryCompanyConversion::compatible).findFirst().orElseThrow();
        fleet.getFleetData().setFlagship(ship);
        ship.setCaptain(sector.getPlayerPerson());
        ship.setShipName("Second Wind");
        ship.updateStats();
        ship.getRepairTracker().setCR(ship.getRepairTracker().getMaxCR());
        var cargo = fleet.getCargo();
        cargo.clear();
        cargo.getCredits().set(STARTING_CREDITS);
        cargo.addCrew((int) Math.ceil(ship.getMinCrew()) + 5);
        cargo.addSupplies(TRAVEL_SUPPLIES);
        cargo.addFuel(Math.min(40, cargo.getMaxFuel()));
        sector.getPersistentData().put(SHIP_KEY, ship.getId());
        PlanetAPI planet = destination();
        if (planet != null) {
            if (fleet.getContainingLocation() != planet.getContainingLocation()) {
                fleet.getContainingLocation().removeEntity(fleet);
                planet.getContainingLocation().addEntity(fleet);
            }
            fleet.setLocation(planet.getLocation().x + planet.getRadius() + 1800, planet.getLocation().y);
            sector.getCampaignUI().layInCourseForNextStep(planet);
        }
        fleet.setTransponderOn(true);
        fleet.getFleetData().setSyncNeeded();
        sector.getIntelManager().addIntel(new CompanyFoundingIntel());
    }

    public static String startingShipId() {
        if (Global.getSector() == null) return null;
        Object id = Global.getSector().getPersistentData().get(SHIP_KEY);
        return id instanceof String value ? value : null;
    }
    public static boolean pendingDesignation() {
        if (startingShipId() == null) return false;
        var script = MarineRosterScript.getInstance();
        return script == null || (script.roster().companyShipId() == null
                && script.roster().officerInductionRequired());
    }
    /** Founding conversion is mandatory; completed companies never regain this gate. */
    public static boolean conversionRequired() {
        return startingShipId() != null
                && !Global.getSector().getPersistentData().containsKey(DEPOT_ISSUED)
                && !MercenaryCompanyConversion.installed(startingShip());
    }
    public static FleetMemberAPI startingShip() {
        String id = startingShipId();
        if (id == null || Global.getSector().getPlayerFleet() == null) return null;
        return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().stream()
                .filter(m -> id.equals(m.getId())).findFirst().orElse(null);
    }
    public static PreparationDepot depot() {
        if (Global.getSector() == null) return null;
        Object depot = Global.getSector().getPersistentData().get(DEPOT_KEY);
        return depot instanceof PreparationDepot value ? value : null;
    }
    public static PreparationDepot accessibleDepot() {
        PreparationDepot depot = depot();
        MarketAPI market = CompanyYard.dockedMarket();
        return depot != null && market != null && depot.marketId().equals(market.getId()) ? depot : null;
    }
    public static void establishDepot(FleetMemberAPI member, MarketAPI market) {
        if (!member.getId().equals(startingShipId())) return;
        var data = Global.getSector().getPersistentData();
        if (data.containsKey(DEPOT_ISSUED)) return;
        data.put(DEPOT_KEY, new PreparationDepot(market.getId()));
        data.put(DEPOT_ISSUED, true);
    }
    public static PlanetAPI destination() {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return null;
        PreparationDepot depot = depot();
        if (depot != null) {
            MarketAPI market = Global.getSector().getEconomy().getMarket(depot.marketId());
            return market == null ? null : market.getPlanetEntity();
        }
        return Global.getSector().getEconomy().getMarketsCopy().stream()
                .filter(m -> !m.isHidden() && CompanyYard.accessible(m))
                .min(Comparator.comparingInt((MarketAPI m) -> "jangala".equals(m.getPlanetEntity().getId()) ? 0 : 1)
                        .thenComparing(MarketAPI::getId))
                .map(MarketAPI::getPlanetEntity).orElse(null);
    }
    public static String instructions() {
        var script = Global.getSector() == null ? null : MarineRosterScript.getInstance();
        if (script != null && script.roster().companyShipId() != null) {
            if (!script.roster().officerInductionRequired())
                return "Your company is ready, Commander. The first story contract on Jangala is waiting in Headquarters. Any unused depot stores remain here for our return.";
            return "Our ship is home now. Stay docked here while we use the prepaid stores: commission our Hermes shuttle on the Boat Deck, meet Mira in Barracks and choose her first specialty, then muster a squad in the Armory and assign it to her. I will walk you through each order.";
        }
        PlanetAPI planet = destination();
        String destination = planet == null ? "an accessible planetary spaceport" : planet.getName();
        if (!MercenaryCompanyConversion.installed(startingShip()))
            return "First we need Mercenary Company Conversion: our permanent barracks and workshop, costing 5,000 credits. "
                    + "Choose Return, dock at " + destination + ", then choose Marine operations in the planet menu. "
                    + "I will bring up the yard order for you. This is not Additional Berthing or an item in the standard Refit picker. "
                    + "We must finish the conversion before designating our command vessel; our prepaid preparation stores will wait in the planetary depot.";
        return "The conversion is installed. Stay docked at " + destination
                + " and open Marine operations, then choose our Cerberus as the company command vessel. "
                + "The preparation bill includes our finite depot stock while we are docked here. Then we will commission the shuttle, meet Mira and muster her squad.";
    }
}
