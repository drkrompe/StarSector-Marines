package com.dillon.starsectormarines.campaign.backend;

import com.dillon.starsectormarines.campaign.CivilizedPort;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

import java.util.List;
import java.util.Map;

/**
 * Where the mod's screens and rules reach the running campaign, so that one seam can answer them without a game, as
 * the battle renderer drains into a window, a snapshot or nothing. {@code playBattle} plays the company's screens over a
 * stand-in backend; the shipped mod only ever uses the game's.
 *
 * <p>One small interface per family of question, in the mod's own terms where it can be. Each family moves here whole
 * in its own commit, and {@code CampaignBackendBoundaryTest} keeps it moved ({@code campaign-backend.md}).
 *
 * <p><b>Held statically and never saved.</b> Nothing reachable from a saved object may keep a backend in a field: the
 * game serializes those, and the answers belong to whichever backend is installed now. Read it here each time.
 */
public final class CampaignBackend {

    /** The save's own key-value store. */
    public interface SavedData {
        /** The store, or null when no campaign is running. */
        Map<String, Object> data();
    }

    /**
     * The fleet the company travels with, whose members are its ships and whose cargo is its purse and stores. The
     * game's own type: 45 files read members and cargo through it, and tests already stand in for it.
     */
    public interface CompanyFleet {
        /** The fleet, or null when no campaign is running. */
        CampaignFleetAPI fleet();
    }

    /** The sector's running scripts, which is where the roster and the campaign's state live. */
    public interface SectorScripts {
        /** The scripts; empty when no campaign is running. */
        List<EveryFrameScript> scripts();
    }

    /** The port the fleet is docked at, where the company can buy and fabricate. */
    public interface DockedPort {
        /** The civilized market the fleet could trade with now, or null. */
        MarketAPI market();
    }

    /** The planet the fleet is docked at, where a yard can take the company's ship in hand. */
    public interface YardPort {
        /** The market of the planet the fleet is at now, or null. Whether its yard will serve the company is not asked here. */
        MarketAPI market();
    }

    /** Who is who: the sector's factions and the player's own. */
    public interface Factions {
        /** The player's faction, or null when no campaign is running. */
        FactionAPI player();

        /** The faction, or null when it is unknown or no campaign is running. */
        FactionAPI byId(String id);
    }

    /** The sector's markets. */
    public interface Markets {
        /** Whether there is an economy to ask: a world still loading has none, which says nothing of any one market. */
        boolean running();

        /** Every market; empty when no campaign is running. */
        List<MarketAPI> all();

        /** The market, or null when it is unknown or no campaign is running. */
        MarketAPI byId(String id);
    }

    /** The game's own factions, read afresh each time. */
    private static final Factions GAME_FACTIONS = new Factions() {
        @Override public FactionAPI player() {
            return Global.getSector() == null ? null : Global.getSector().getPlayerFaction();
        }

        @Override public FactionAPI byId(String id) {
            return Global.getSector() == null ? null : Global.getSector().getFaction(id);
        }
    };

    /** The game's own markets, read afresh each time. */
    private static final Markets GAME_MARKETS = new Markets() {
        @Override public boolean running() {
            return Global.getSector() != null && Global.getSector().getEconomy() != null;
        }

        @Override public List<MarketAPI> all() {
            return Global.getSector() == null || Global.getSector().getEconomy() == null
                    ? List.of() : Global.getSector().getEconomy().getMarketsCopy();
        }

        @Override public MarketAPI byId(String id) {
            return Global.getSector() == null || Global.getSector().getEconomy() == null
                    ? null : Global.getSector().getEconomy().getMarket(id);
        }
    };

    /** The sector's star systems, where the story's worlds are found by name. */
    public interface StarSystems {
        /** The system, or null when it is unknown or no campaign is running. */
        StarSystemAPI byName(String name);
    }

    /** Where a ship the company is awarded comes from. */
    public interface ShipYard {
        FleetMemberAPI build(String variantId);
    }

    /** The game's answers, read afresh each time since a sector comes and goes with each save. */
    private static final SavedData GAME_SAVED_DATA =
            () -> Global.getSector() == null ? null : Global.getSector().getPersistentData();

    private static volatile SavedData savedData = GAME_SAVED_DATA;
    private static volatile CompanyFleet companyFleet =
            () -> Global.getSector() == null ? null : Global.getSector().getPlayerFleet();

    private static volatile SectorScripts sectorScripts =
            () -> Global.getSector() == null ? List.of() : Global.getSector().getScripts();

    private static volatile StarSystems starSystems =
            name -> Global.getSector() == null ? null : Global.getSector().getStarSystem(name);
    private static volatile ShipYard shipYard =
            variantId -> Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
    private static volatile DockedPort dockedPort = CivilizedPort::dockedInDialog;
    private static volatile YardPort yardPort = CompanyYard::planetInDialog;
    private static volatile Factions factions = GAME_FACTIONS;
    private static volatile Markets markets = GAME_MARKETS;

    private CampaignBackend() {
    }

    /** {@link SavedData#data} of the installed backend. */
    public static Map<String, Object> savedData() {
        return savedData.data();
    }

    /** Answers saved data from {@code backend} from now on, for a host with no game running. */
    public static void useSavedData(SavedData backend) {
        savedData = backend;
    }

    /** {@link CompanyFleet#fleet} of the installed backend. */
    public static CampaignFleetAPI playerFleet() {
        return companyFleet.fleet();
    }

    /** Answers the company's fleet from {@code backend} from now on, for a host with no game running. */
    public static void useCompanyFleet(CompanyFleet backend) {
        companyFleet = backend;
    }

    /** {@link SectorScripts#scripts} of the installed backend. */
    public static List<EveryFrameScript> sectorScripts() {
        return sectorScripts.scripts();
    }

    /** Answers the sector's scripts from {@code backend} from now on, for a host with no game running. */
    public static void useSectorScripts(SectorScripts backend) {
        sectorScripts = backend;
    }

    /** {@link DockedPort#market} of the installed backend. */
    public static MarketAPI dockedPort() {
        return dockedPort.market();
    }

    /** Answers the docked port from {@code backend} from now on, for a host with no game running. */
    public static void useDockedPort(DockedPort backend) {
        dockedPort = backend;
    }

    /** {@link YardPort#market} of the installed backend. */
    public static MarketAPI yardPort() {
        return yardPort.market();
    }

    /** Answers the yard's planet from {@code backend} from now on, for a host with no game running. */
    public static void useYardPort(YardPort backend) {
        yardPort = backend;
    }

    /** {@link StarSystems#byName} of the installed backend. */
    public static StarSystemAPI starSystem(String name) {
        return starSystems.byName(name);
    }

    /** Answers star systems from {@code backend} from now on, for a host with no game running. */
    public static void useStarSystems(StarSystems backend) {
        starSystems = backend;
    }

    /** {@link ShipYard#build} of the installed backend. */
    public static FleetMemberAPI newShip(String variantId) {
        return shipYard.build(variantId);
    }

    /** Builds ships from {@code backend} from now on, for a host with no game running. */
    public static void useShipYard(ShipYard backend) {
        shipYard = backend;
    }

    /** {@link Factions#player} of the installed backend. */
    public static FactionAPI playerFaction() {
        return factions.player();
    }

    /** {@link Factions#byId} of the installed backend. */
    public static FactionAPI faction(String id) {
        return factions.byId(id);
    }

    /** Answers factions from {@code backend} from now on, for a host with no game running. */
    public static void useFactions(Factions backend) {
        factions = backend;
    }

    /** {@link Markets#running} of the installed backend. */
    public static boolean economyRunning() {
        return markets.running();
    }

    /** {@link Markets#all} of the installed backend. */
    public static List<MarketAPI> markets() {
        return markets.all();
    }

    /** {@link Markets#byId} of the installed backend. */
    public static MarketAPI market(String id) {
        return markets.byId(id);
    }

    /** Answers markets from {@code backend} from now on, for a host with no game running. */
    public static void useMarkets(Markets backend) {
        markets = backend;
    }
}
