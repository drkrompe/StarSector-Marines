package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.start.OpeningPort.Use;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Action;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Port;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.CompanyShipDesignation;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction.ShipSaleInfo;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Keeps the Second Wind: applies {@link SecondWindKeep} to the campaign.
 *
 * <p>She is the start's own ship ({@link CerberusCompanyStart#startingShipId()}), whichever hull the company
 * lives aboard now. Each frame she is in the fleet she is remembered; when she is not, this works out where she
 * went. Storage is the player's choice and is left alone. A sale is undone as it is reported. A loss in a fight
 * that nobody recovered sends her to a yard; anything else that took her out of the fleet (scuttling, a
 * respawn, a quest that took her) puts her straight back, and a scuttling's salvage is taken back out of the holds.
 *
 * <p>The game's {@code ship_can_not_scuttle} tag is set on her too, but 0.98a's fleet screen does not read it,
 * which is why scuttling is undone rather than refused. {@code always_recoverable} is read, and is what makes
 * recovery off a held field certain.
 *
 * <p>Transient: installed on every load by {@code StarsectorMarinesModPlugin}. Everything that must survive a
 * save lives in sector persistent data.
 */
public final class SecondWindKeeper extends BaseCampaignEventListener implements EveryFrameScript {

    /** The last fleet member seen as the Second Wind, so she can be put back after she is gone. */
    private static final String REMEMBERED_KEY = "sm_second_wind_member";
    /** Her {@link SecondWindYardOrder} while she is in a yard after a loss. */
    private static final String YARD_KEY = "sm_second_wind_yard";
    /** Seconds between checks; scans of storage only happen while she is missing. */
    private static final float CHECK_INTERVAL = 0.25f;
    private static final Logger LOG = Global.getLogger(SecondWindKeeper.class);

    /** Where she was last found in storage, so the next check looks there first. */
    private static SubmarketAPI storedAt;

    private float sinceCheck = CHECK_INTERVAL;
    private boolean casualty;
    private float suppliesSeen;
    private float fuelSeen;
    private float machinerySeen;
    /** The dialog Castle last spoke in about an unfinished yard job, so she says it once per docking. */
    private Object remindedIn;

    public SecondWindKeeper() {
        super(false);
    }

    /** Installs one keeper, as script and as event listener. Neither is saved. */
    public static void install(SectorAPI sector) {
        for (CampaignEventListener listener : new ArrayList<>(sector.getAllListeners())) {
            if (listener instanceof SecondWindKeeper) sector.removeListener(listener);
        }
        sector.removeScriptsOfClass(SecondWindKeeper.class);
        SecondWindKeeper keeper = new SecondWindKeeper();
        sector.addTransientListener(keeper);
        sector.addTransientScript(keeper);
        storedAt = null;
    }

    /** Whether this fleet member id is the Second Wind, which is never let go of. */
    public static boolean keeps(String memberId) {
        return memberId != null && memberId.equals(CerberusCompanyStart.startingShipId());
    }

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return true; }

    @Override
    public void advance(float amount) {
        sinceCheck += amount;
        if (sinceCheck < CHECK_INTERVAL) return;
        sinceCheck = 0f;
        SectorAPI sector = Global.getSector();
        String id = CerberusCompanyStart.startingShipId();
        CampaignFleetAPI fleet = sector == null ? null : sector.getPlayerFleet();
        if (id == null || fleet == null || fleet.getFleetData() == null) return;

        SecondWindYardOrder order = yardOrder();
        if (order != null) {
            tendYard(order, fleet);
            return;
        }
        FleetMemberAPI aboard = find(fleet.getFleetData().getMembersListCopy(), id);
        if (aboard != null) {
            remember(aboard, fleet.getCargo());
            return;
        }
        // Storage is scanned only once nothing on screen can still bring her back, which is also when it is cheap.
        boolean busy = uiBusy();
        Action action = SecondWindKeep.decide(false, !busy && stored(id) != null, false, casualty, busy);
        if (action == Action.SEND_TO_YARD) sendToYard(fleet);
        else if (action == Action.RETURN_TO_FLEET) returnToFleet(fleet);
    }

    // ---- what happens to her -------------------------------------------------------------------------------

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        String id = CerberusCompanyStart.startingShipId();
        if (result == null || id == null) return;
        if (casualtyIn(result.getWinnerResult(), id) || casualtyIn(result.getLoserResult(), id)) {
            casualty = true;
            LOG.info("Second Wind: disabled or destroyed in an engagement; she goes to a yard unless recovered");
        }
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        String id = CerberusCompanyStart.startingShipId();
        if (transaction == null || id == null || transaction.getShipsSold() == null) return;
        SubmarketAPI market = transaction.getSubmarket();
        if (market == null || market.getPlugin() == null || market.getPlugin().isFreeTransfer()) return;
        for (ShipSaleInfo sale : transaction.getShipsSold()) {
            if (sale == null || sale.getMember() == null || !id.equals(sale.getMember().getId())) continue;
            undoSale(market, sale);
        }
    }

    /** Takes her back off the market and the credits back out of the account. */
    private void undoSale(SubmarketAPI market, ShipSaleInfo sale) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;
        FleetMemberAPI ship = sale.getMember();
        CargoAPI cargo = market.getCargoNullOk();
        if (cargo != null) {
            FleetMemberAPI listed = find(cargo.getMothballedShips().getMembersListCopy(), ship.getId());
            if (listed != null) {
                cargo.getMothballedShips().removeFleetMember(listed);
                ship = listed;
            }
        }
        if (find(fleet.getFleetData().getMembersListCopy(), ship.getId()) == null)
            fleet.getFleetData().addFleetMember(ship);
        fleet.getCargo().getCredits().subtract(sale.getPrice());
        fleet.getFleetData().setSyncNeeded();
        LOG.info("Second Wind: sale at " + market.getSpecId() + " undone; " + Math.round(sale.getPrice()) + " credits returned");
        say(Strings.format("secondWindKeepSaleUndone", Misc.getDGSCredits(sale.getPrice())));
    }

    /** She left the fleet outside a fight: put her back, and take a scuttling's salvage back out. */
    private void returnToFleet(CampaignFleetAPI fleet) {
        FleetMemberAPI ship = remembered();
        if (ship == null) {
            LOG.warn("Second Wind: missing from the fleet with no record of her; nothing to put back");
            return;
        }
        CargoAPI cargo = fleet.getCargo();
        float supplies = SecondWindKeep.scuttleClawback(ship.getRepairTracker().getSuppliesFromScuttling(),
                suppliesSeen, cargo.getSupplies());
        float fuel = SecondWindKeep.scuttleClawback(ship.getRepairTracker().getFuelFromScuttling(),
                fuelSeen, cargo.getFuel());
        float machinery = SecondWindKeep.scuttleClawback(ship.getRepairTracker().getHeavyMachineryFromScuttling(),
                machinerySeen, cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY));
        cargo.removeSupplies(supplies);
        cargo.removeFuel(fuel);
        cargo.removeCommodity(Commodities.HEAVY_MACHINERY, machinery);
        restore(ship, fleet);
        boolean scuttled = supplies + fuel + machinery > 0f;
        LOG.info("Second Wind: left the fleet outside a fight; returned" + (scuttled ? " and salvage taken back" : ""));
        say(Strings.get(scuttled ? "secondWindKeepScuttleUndone" : "secondWindKeepReturned"));
    }

    /** Lost in a fight and not recovered: one more d-mod, a yard, a bill and three weeks. */
    private void sendToYard(CampaignFleetAPI fleet) {
        casualty = false;
        FleetMemberAPI ship = remembered();
        if (ship == null) {
            LOG.warn("Second Wind: lost with no record of her; she cannot be sent to a yard");
            return;
        }
        SectorAPI sector = Global.getSector();
        boolean damaged = addLossDMod(ship, new Random(ship.getId().hashCode() * 31L + sector.getClock().getTimestamp()));
        ship.getStatus().repairFully();
        if (ship.getCaptain() != null && ship.getCaptain() == sector.getPlayerPerson()) ship.setCaptain(null);
        ship.updateStats();

        if (isCompanyShip(ship)) CompanyShipDesignation.settleKeptLoss();

        MarketAPI yard = pickYard(fleet);
        if (yard == null) {
            // Nowhere will take her: she limps home under her own crew instead, d-mod and all.
            LOG.warn("Second Wind: lost with no open yard anywhere; she rejoins the fleet as she is");
            restore(ship, fleet);
            say(Strings.get("secondWindKeepNoYard"));
            return;
        }
        int bill = SecondWindKeep.yardBill(ship.getHullSpec() == null ? 0f : ship.getHullSpec().getBaseValue());
        sector.getPersistentData().put(YARD_KEY,
                new SecondWindYardOrder(ship, yard.getId(), sector.getClock().getTimestamp(), bill, damaged));
        LOG.info("Second Wind: lost; towed to " + yard.getId() + " for " + SecondWindKeep.YARD_DAYS
                + " days and " + bill + " credits" + (damaged ? ", one more d-mod" : ""));
        say(Strings.format(damaged ? "secondWindKeepToYard" : "secondWindKeepToYardNoDMod",
                yard.getName(), SecondWindKeep.YARD_DAYS, Misc.getDGSCredits(bill)));
    }

    /** While she is in the yard: follow a lost yard, and hand her over when docked there and she is ready. */
    private void tendYard(SecondWindYardOrder order, CampaignFleetAPI fleet) {
        SectorAPI sector = Global.getSector();
        MarketAPI yard = sector.getEconomy().getMarket(order.marketId());
        if (!OpeningPort.eligible(yard, sector.getPlayerFaction(), Use.YARD)) {
            MarketAPI moved = pickYard(fleet);
            if (moved == null) return;
            order.relocate(moved.getId());
            say(Strings.format("secondWindKeepYardMoved", moved.getName()));
            return;
        }
        MarketAPI docked = CompanyYard.dockedMarket();
        if (docked == null || !docked.getId().equals(order.marketId())) {
            remindedIn = null;
            return;
        }
        float days = sector.getClock().getElapsedDaysSince(order.since());
        if (!SecondWindKeep.ready(days)) {
            Object dialog = sector.getCampaignUI().getCurrentInteractionDialog();
            if (dialog != remindedIn) {
                remindedIn = dialog;
                say(Strings.format("secondWindKeepYardNotReady", SecondWindKeep.daysLeft(days)));
            }
            return;
        }
        FleetMemberAPI ship = order.ship();
        sector.getPersistentData().remove(YARD_KEY);
        restore(ship, fleet);
        ship.getRepairTracker().setCR(ship.getRepairTracker().getMaxCR());
        fleet.getCargo().getCredits().subtract(order.bill());
        LOG.info("Second Wind: collected from " + order.marketId() + "; " + order.bill() + " credits paid");
        say(Strings.format("secondWindKeepCollected", Misc.getDGSCredits(order.bill())));
    }

    // ---- the Marines button and the planet menu -------------------------------------------------------------

    /**
     * What to tell the player while the company's home is away, or null when it is not. Only the Second Wind's
     * absence counts, and only while the company lives aboard her (or has not yet chosen a ship).
     */
    public static String awayInstructions() {
        SectorAPI sector = Global.getSector();
        String id = CerberusCompanyStart.startingShipId();
        if (sector == null || id == null || sector.getPlayerFleet() == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        String home = script == null ? null : script.roster().companyShipId();
        if (home != null && !home.equals(id)) return null;
        if (find(sector.getPlayerFleet().getFleetData().getMembersListCopy(), id) != null) return null;

        SecondWindYardOrder order = yardOrder();
        if (order != null) {
            MarketAPI yard = sector.getEconomy().getMarket(order.marketId());
            String where = yard == null ? order.marketId() : yard.getName() + " (" + yard.getContainingLocation().getNameWithLowercaseTypeShort() + ")";
            int left = SecondWindKeep.daysLeft(sector.getClock().getElapsedDaysSince(order.since()));
            return left > 0
                    ? Strings.format("secondWindKeepAwayInYard", where, left, Misc.getDGSCredits(order.bill()))
                    : Strings.format("secondWindKeepAwayReady", where, Misc.getDGSCredits(order.bill()));
        }
        SubmarketAPI storage = stored(id);
        if (storage != null && storage.getMarket() != null)
            return Strings.format("secondWindKeepAwayInStorage", storage.getMarket().getName());
        return Strings.get("secondWindKeepAwayUnknown");
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private void remember(FleetMemberAPI ship, CargoAPI cargo) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        if (data.get(REMEMBERED_KEY) != ship) data.put(REMEMBERED_KEY, ship);
        tag(ship);
        casualty = false;
        storedAt = null;
        suppliesSeen = cargo.getSupplies();
        fuelSeen = cargo.getFuel();
        machinerySeen = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
    }

    /**
     * Recovery always offered, scuttling flagged. Tagged on her own variant, cloned off the shared stock variant
     * first if she still points at it, so no other Cerberus picks the tags up and older saves get them on load.
     */
    private static void tag(FleetMemberAPI ship) {
        ShipVariantAPI variant = ship.getVariant();
        if (variant == null || variant.hasTag(Tags.VARIANT_ALWAYS_RECOVERABLE) && variant.hasTag(Tags.SHIP_CAN_NOT_SCUTTLE)) return;
        if (variant.isStockVariant() || variant.getSource() != VariantSource.REFIT) {
            variant = variant.clone();
            variant.setSource(VariantSource.REFIT);
            ship.setVariant(variant, false, true);
        }
        variant.addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);
        variant.addTag(Tags.SHIP_CAN_NOT_SCUTTLE);
    }

    /** Adds the loss's d-mod when she has room for one; whether one was added. */
    private static boolean addLossDMod(FleetMemberAPI ship, Random random) {
        ShipVariantAPI variant = ship.getVariant();
        int before = DModManager.getNumDMods(variant);
        int add = SecondWindKeep.dModsToAdd(before);
        if (add <= 0) return false;
        DModManager.addDMods(ship, true, add, random);
        DModManager.setDHull(ship.getVariant());
        return DModManager.getNumDMods(ship.getVariant()) > before;
    }

    private static void restore(FleetMemberAPI ship, CampaignFleetAPI fleet) {
        if (find(fleet.getFleetData().getMembersListCopy(), ship.getId()) == null)
            fleet.getFleetData().addFleetMember(ship);
        ship.updateStats();
        fleet.getFleetData().setSyncNeeded();
    }

    /** The start port while it still has an open yard, else the nearest friendly one, else the nearest. */
    private static MarketAPI pickYard(CampaignFleetAPI fleet) {
        SectorAPI sector = Global.getSector();
        FactionAPI player = sector.getPlayerFaction();
        String startPort = CerberusCompanyStart.startPortMarketId();
        List<Port> ports = new ArrayList<>();
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (!OpeningPort.eligible(market, player, Use.YARD)) continue;
            FactionAPI owner = market.getFaction();
            boolean friendly = Factions.INDEPENDENT.equals(owner.getId()) || owner.isPlayerFaction()
                    || owner.isAtWorst(Factions.PLAYER, RepLevel.FAVORABLE);
            ports.add(new Port(market.getId(), market.getId().equals(startPort), friendly,
                    Misc.getDistanceLY(fleet, market.getPrimaryEntity())));
        }
        Port pick = SecondWindKeep.pickYard(ports);
        return pick == null ? null : sector.getEconomy().getMarket(pick.marketId());
    }

    private static boolean casualtyIn(EngagementResultForFleetAPI side, String id) {
        if (side == null || !side.isPlayer()) return false;
        return find(side.getDestroyed(), id) != null || find(side.getDisabled(), id) != null;
    }

    private static boolean isCompanyShip(FleetMemberAPI ship) {
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script == null ? null : script.roster();
        return roster != null && ship.getId().equals(roster.companyShipId());
    }

    /** A dialog, a core screen or the menu is up, any of which may still put her back. */
    private static boolean uiBusy() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        return ui == null || ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null;
    }

    /** The storage she is in, or null. The last place she was found is checked before every market is. */
    private static SubmarketAPI stored(String id) {
        if (holds(storedAt, id)) return storedAt;
        storedAt = null;
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (SectorEntityToken entity : location.getAllEntities()) {
                MarketAPI market = entity.getMarket();
                if (market == null || market.getSubmarketsCopy() == null) continue;
                for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                    if (submarket.getPlugin() == null || !submarket.getPlugin().isFreeTransfer()) continue;
                    if (holds(submarket, id)) return storedAt = submarket;
                }
            }
        }
        return null;
    }

    private static boolean holds(SubmarketAPI submarket, String id) {
        if (submarket == null) return false;
        CargoAPI cargo = submarket.getCargoNullOk();
        return cargo != null && cargo.getMothballedShips() != null
                && find(cargo.getMothballedShips().getMembersListCopy(), id) != null;
    }

    private static SecondWindYardOrder yardOrder() {
        Object order = Global.getSector().getPersistentData().get(YARD_KEY);
        return order instanceof SecondWindYardOrder value ? value : null;
    }

    private static FleetMemberAPI remembered() {
        Object ship = Global.getSector().getPersistentData().get(REMEMBERED_KEY);
        return ship instanceof FleetMemberAPI member ? member : null;
    }

    private static FleetMemberAPI find(List<FleetMemberAPI> members, String id) {
        if (members == null) return null;
        for (FleetMemberAPI member : members) if (member != null && id.equals(member.getId())) return member;
        return null;
    }

    /** Castle's line, on the campaign message feed. */
    private static void say(String line) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui != null) ui.addMessage(line, Misc.getHighlightColor());
    }
}
