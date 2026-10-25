package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.intel.CompanyFoundingIntel;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.dillon.starsectormarines.campaign.start.OpeningPort.Use;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.tutorial.GalatianAcademyStipend;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/** Runs once, after vanilla's ordinary skip-tutorial setup, only for this selected start. */
public final class CerberusCompanyStart implements Script {
    public static final String SHIP_KEY = "sm_cerberus_start_ship";
    /**
     * The ship both start paths hand the player: a Cerberus (D) the mine stripped for parts, carrying the d-mods of
     * those years and only the two Vulcans nobody took ({@code mod/data/variants/sm_second_wind_start.variant}).
     */
    public static final String START_VARIANT_ID = "sm_second_wind_start";
    private static final String DEPOT_KEY = "sm_preparation_depot";
    private static final String DEPOT_ISSUED = "sm_preparation_depot_issued";
    /** The start port, chosen once by {@link OpeningPort} when the start runs and kept. */
    private static final String START_PORT_KEY = "sm_start_port_market";
    /** The start port's planet, which outlives its market, so its system is known after the port is lost. */
    private static final String START_PLANET_KEY = "sm_start_port_planet";
    /** The replacement port the last crossing fuel was granted for, so each re-pick grants once. */
    private static final String CROSSING_FUEL_KEY = "sm_start_port_crossing_fuel_for";
    private static final Logger LOG = Global.getLogger(CerberusCompanyStart.class);
    /** The pooled ship's account: about twenty-two days of board and berth at Kilo Nine. */
    public static final int STARTING_CREDITS = 473;
    public static final int TRAVEL_SUPPLIES = 25;
    /**
     * One unit of fuel (owner, 2026-10-25; the tank started empty). Fuel burns only in hyperspace. The start sets
     * the fleet down in its port's own system, and the yard, Mara Venn's office and the depot all start there; a
     * re-pick after that port is lost stays in that system while any qualifying port remains in it
     * ({@link OpeningPort}). Fuel is bought at the port when the company leaves.
     * Nothing aboard to sell is also why a second squad is out of reach before Relief
     * ({@code SecondSquadAffordabilityTest}).
     */
    public static final int STARTING_FUEL = 1;
    /** Hands aboard beyond the ship's minimum crew. */
    public static final int SURPLUS_CREW = 5;

    @Override public void run() {
        var sector = Global.getSector();
        if (sector.getPersistentData().containsKey(SHIP_KEY)) return;
        var fleet = sector.getPlayerFleet();
        FleetMemberAPI ship = startingCerberus(fleet);
        if (ship == null) return;
        fleet.getFleetData().setFlagship(ship);
        ship.setCaptain(sector.getPlayerPerson());
        ship.setShipName("Second Wind");
        ship.updateStats();
        ship.getRepairTracker().setCR(ship.getRepairTracker().getMaxCR());
        var cargo = fleet.getCargo();
        cargo.clear();
        cargo.getCredits().set(STARTING_CREDITS);
        cargo.addCrew((int) Math.ceil(ship.getMinCrew()) + SURPLUS_CREW);
        cargo.addSupplies(TRAVEL_SUPPLIES);
        if (STARTING_FUEL > 0) cargo.addFuel(Math.min(STARTING_FUEL, cargo.getMaxFuel()));
        sector.getPersistentData().put(SHIP_KEY, ship.getId());
        FoundingReserve.issue(sector.getPersistentData());
        MarketAPI port = OpeningPort.resolve(sector, Use.YARD);
        if (port != null) {
            sector.getPersistentData().put(START_PORT_KEY, port.getId());
            sector.getPersistentData().put(START_PLANET_KEY, port.getPlanetEntity().getId());
        }
        else LOG.warn("Second Wind start: no planetary port with a spaceport is open to the player; the fleet stays put");
        CompanyPrologue.begin(sector.getPersistentData());
        approachDestination(fleet);
        fleet.setTransponderOn(true);
        fleet.getFleetData().setSyncNeeded();
        sector.getIntelManager().addIntel(new CompanyFoundingIntel());
        CompanyProloguePresenter.install();
    }

    /**
     * The fleet's Cerberus, or the start's own Second Wind added to it when another mod's setup took it away.
     * Null only when even that cannot be made, in which case the start is logged and left undone.
     */
    private static FleetMemberAPI startingCerberus(CampaignFleetAPI fleet) {
        FleetMemberAPI ship = fleet.getFleetData().getMembersListCopy().stream()
                .filter(MercenaryCompanyConversion::compatible).findFirst().orElse(null);
        if (ship != null) return ship;
        LOG.warn("Second Wind start: no Cerberus in the starting fleet; adding " + START_VARIANT_ID);
        try {
            ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, START_VARIANT_ID);
            fleet.getFleetData().addFleetMember(ship);
            return ship;
        } catch (RuntimeException e) {
            LOG.error("Second Wind start: could not create " + START_VARIANT_ID + "; the start is not applied", e);
            return null;
        }
    }

    /**
     * Vanilla's skip-tutorial setup, which this start runs first, also starts the Galatia Academy stipend:
     * 15,000 credits a month for three years. Second Wind has no patron and 473 credits, and a month of that
     * stipend would buy the second squad the story Relief assumes it cannot afford. Its only reader in vanilla
     * rules is Baird's "ask about the stipend" option, which goes with it.
     */
    static void withdrawAcademyStipend() {
        var sector = Global.getSector();
        sector.getListenerManager().removeListenerOfClass(GalatianAcademyStipend.class);
        sector.getMemoryWithoutUpdate().unset("$playerReceivingGAStipend");
    }

    /** Puts the fleet a short flight out from the yard it must reach first, with the course laid in. */
    public static void approachDestination(CampaignFleetAPI fleet) {
        PlanetAPI planet = destination();
        if (planet == null) return;
        if (fleet.getContainingLocation() != planet.getContainingLocation()) {
            fleet.getContainingLocation().removeEntity(fleet);
            planet.getContainingLocation().addEntity(fleet);
        }
        fleet.setLocation(planet.getLocation().x + planet.getRadius() + 1800, planet.getLocation().y);
        Global.getSector().getCampaignUI().layInCourseForNextStep(planet);
    }

    public static String startingShipId() {
        if (Global.getSector() == null) return null;
        Object id = Global.getSector().getPersistentData().get(SHIP_KEY);
        return id instanceof String value ? value : null;
    }
    /** The start port's market id, locked when the start ran, or null for an ordinary start or an older save. */
    public static String startPortMarketId() {
        if (Global.getSector() == null) return null;
        return Global.getSector().getPersistentData().get(START_PORT_KEY) instanceof String id ? id : null;
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
    /**
     * The depot while docked at its port. A depot whose port is lost to the company (gone, decivilized, hidden or
     * hostile) follows it: the stores come aboard at the next accessible yard it docks at, and stay there.
     */
    public static PreparationDepot accessibleDepot() {
        PreparationDepot depot = depot();
        MarketAPI market = CompanyYard.dockedMarket();
        if (depot == null || market == null) return null;
        if (depot.marketId().equals(market.getId())) return depot;
        if (!depotLost()) return null;
        LOG.info("Second Wind depot at " + depot.marketId() + " is lost; moving its stores to " + market.getId());
        depot.relocate(market.getId());
        return depot;
    }
    /** Whether the depot's port can no longer be reached. */
    public static boolean depotLost() {
        PreparationDepot depot = depot();
        if (depot == null || Global.getSector().getEconomy() == null) return false;
        return lost(Global.getSector().getEconomy().getMarket(depot.marketId()), Global.getSector().getPlayerFaction());
    }
    /** A port is lost when it is gone, decivilized, hidden or hostile; a disrupted spaceport is only closed. */
    static boolean lost(MarketAPI market, FactionAPI player) {
        return market == null || market.getPlanetEntity() == null || market.isPlanetConditionMarketOnly()
                || market.isHidden() || market.getFaction() == null
                || player != null && market.getFaction().isHostileTo(player);
    }
    /** The planet the depot's port is on, or null when there is no depot or its port is lost. */
    public static PlanetAPI depotPlanet() {
        PreparationDepot depot = depot();
        if (depot == null || depotLost()) return null;
        return Global.getSector().getEconomy().getMarket(depot.marketId()).getPlanetEntity();
    }
    public static void establishDepot(FleetMemberAPI member, MarketAPI market) {
        if (!member.getId().equals(startingShipId())) return;
        var data = Global.getSector().getPersistentData();
        if (data.containsKey(DEPOT_ISSUED)) return;
        data.put(DEPOT_KEY, new PreparationDepot(market.getId(), FoundingReserve.protectedStart(data)));
        data.put(DEPOT_ISSUED, true);
    }
    /**
     * Where the company goes next: the depot's port, else the start port locked when the start ran, else (that port
     * lost, or a save older than the lock) the {@link OpeningPort} order's yard. A lost depot points there too.
     */
    public static PlanetAPI destination() {
        var sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) return null;
        PlanetAPI depot = depotPlanet();
        if (depot != null) return depot;
        Object locked = sector.getPersistentData().get(START_PORT_KEY);
        if (locked instanceof String id) {
            MarketAPI market = sector.getEconomy().getMarket(id);
            if (OpeningPort.eligible(market, sector.getPlayerFaction(), Use.YARD)) {
                // A lost depot sends the company back to the start port, which may be a system away.
                if (depot() != null) provisionCrossing(market);
                return market.getPlanetEntity();
            }
        }
        MarketAPI market = OpeningPort.resolve(sector, Use.YARD, homeSystem());
        if (market == null) return null;
        if (locked != null) provisionCrossing(market);
        return market.getPlanetEntity();
    }
    /**
     * The start carries a single unit of fuel, which is safe while the company's port is in the system it stands in. When the
     * locked port is lost during the opening and the replacement lies across hyperspace, Castle tops the tank up to
     * exactly what the fleet burns to reach it, once per replacement, and says so.
     */
    private static void provisionCrossing(MarketAPI market) {
        var sector = Global.getSector();
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        PlanetAPI planet = market.getPlanetEntity();
        if (fleet == null || planet == null || !openingUnderway()
                || fleet.getContainingLocation() == planet.getContainingLocation()
                || market.getId().equals(sector.getPersistentData().get(CROSSING_FUEL_KEY))) return;
        sector.getPersistentData().put(CROSSING_FUEL_KEY, market.getId());
        var cargo = fleet.getCargo();
        int grant = crossingFuel(Misc.getDistanceLY(fleet, planet), fleet.getLogistics().getFuelCostPerLightYear(),
                cargo.getFuel(), cargo.getMaxFuel());
        if (grant <= 0) return;
        cargo.addFuel(grant);
        LOG.info("Second Wind start: port re-picked to " + market.getId() + " across hyperspace; granted " + grant + " fuel");
        if (sector.getCampaignUI() != null)
            sector.getCampaignUI().addMessage(Strings.format("openingCerberusCompanyStartCrossingFuel", planet.getName(), grant));
    }
    /** Whole fuel units the fleet still needs to cross {@code distanceLy}, within the space its tanks have left. */
    static int crossingFuel(float distanceLy, float fuelPerLy, float aboard, float capacity) {
        int needed = (int) Math.ceil(Math.max(0f, distanceLy) * Math.max(0f, fuelPerLy));
        int room = (int) Math.floor(Math.max(0f, capacity - aboard));
        return Math.max(0, Math.min(room, needed - (int) Math.floor(Math.max(0f, aboard))));
    }
    /** Until the conversion is paid and the depot's stores are drawn, the opening still has somewhere to send the company. */
    private static boolean openingUnderway() {
        if (conversionRequired()) return true;
        PreparationDepot depot = depot();
        return depot != null && depot.stock().values().stream().anyMatch(quantity -> quantity > 0);
    }
    /** The start port's star system, or null before the start has locked one (or for an ordinary start). */
    public static StarSystemAPI homeSystem() {
        var sector = Global.getSector();
        if (sector == null || !(sector.getPersistentData().get(START_PLANET_KEY) instanceof String id)) return null;
        SectorEntityToken planet = sector.getEntityById(id);
        return planet == null ? null : planet.getStarSystem();
    }
    public static String instructions() {
        var script = Global.getSector() == null ? null : MarineRosterScript.getInstance();
        if (script != null && script.roster().companyShipId() != null) {
            if (!script.roster().officerInductionRequired())
                return Strings.get("openingCerberusCompanyStartYourCompanyIsReadyCommanderTheFirst");
            return Strings.get("openingCerberusCompanyStartOurShipIsHomeNowStayDocked");
        }
        PlanetAPI planet = destination();
        String destination = planet == null ? Strings.get("openingCerberusCompanyStartAnAccessiblePlanetarySpaceport") : planet.getName();
        if (!MercenaryCompanyConversion.installed(startingShip()))
            return Strings.format("openingCerberusCompanyStartFirstWeNeedMercenaryCompanyConversionOur", (CompanyYard.reservedCredits(startingShip()) > 0
                    ? Strings.get("openingCerberusCompanyStartIHaveKeptThatPaymentSeparateFrom") : ""), destination);
        return Strings.format("openingCerberusCompanyStartTheConversionIsInstalledStayDockedAt", destination);
    }
}
