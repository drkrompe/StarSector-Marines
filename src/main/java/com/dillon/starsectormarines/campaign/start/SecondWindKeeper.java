package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.start.OpeningPort.Use;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Action;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Cause;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Gate;
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
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction.ShipSaleInfo;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Keeps the Second Wind: applies {@link SecondWindKeep} to the campaign.
 *
 * <p>She is the start's own ship ({@link CerberusCompanyStart#startingShipId()}). Each check she is in the fleet
 * she is remembered; when she is not, this works out where she went. Storage is the player's choice and is left
 * alone. Anything else - a fight nobody recovered her from, a sale, a scuttling - is a loss: she leaves the
 * world one d-mod worse, and the buy-back screen ({@code SecondWindBuyBackScreen}) offers her back at any port
 * with a yard until she is home. A sold hull is taken off the market so the ordinary ship market never sells
 * her back.
 *
 * <p>{@code always_recoverable} on her variant makes recovery off a held field certain. {@code
 * ship_can_not_scuttle} is set too, but 0.98a's fleet screen does not read it.
 *
 * <p>Transient: installed on every load by {@code StarsectorMarinesModPlugin}. Everything that must survive a
 * save lives in sector persistent data.
 */
public final class SecondWindKeeper extends BaseCampaignEventListener implements EveryFrameScript {

    /** The last fleet member seen as the Second Wind, so she can be offered back after she is gone. */
    private static final String REMEMBERED_KEY = "sm_second_wind_member";
    /** Her {@link SecondWindLoss} while she is gone. */
    private static final String LOSS_KEY = "sm_second_wind_loss";
    /** The 2026-10-20 yard build's order, migrated to a {@link SecondWindLoss} on load. */
    private static final String LEGACY_YARD_KEY = "sm_second_wind_yard";
    /** Seconds between checks; storage is scanned only while she is missing. */
    private static final float CHECK_INTERVAL = 0.25f;
    private static final Logger LOG = Global.getLogger(SecondWindKeeper.class);

    /** Where she was last found in storage, so the next check looks there first. */
    private static SubmarketAPI storedAt;

    private float sinceCheck = CHECK_INTERVAL;
    private boolean casualty;
    /** The market and price of a reported sale of her, until the loss is recorded. */
    private SubmarketAPI soldAt;
    private float salePrice;
    private float suppliesSeen;
    private float fuelSeen;
    private float machinerySeen;

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
        migrateYardOrder(sector.getPersistentData());
    }

    /** Whether this fleet member id is the Second Wind, which is never let go of for good. */
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

        FleetMemberAPI aboard = find(fleet.getFleetData().getMembersListCopy(), id);
        if (aboard != null) {
            // Back by any route (bought back, or some other mod's doing): the loss is over.
            sector.getPersistentData().remove(LOSS_KEY);
            remember(aboard, fleet.getCargo());
            return;
        }
        // Storage is scanned only once nothing on screen can still bring her back, which is also when it is cheap.
        boolean busy = uiBusy();
        boolean lost = loss() != null;
        Action action = SecondWindKeep.decide(false, !busy && !lost && stored(id) != null, lost, busy);
        if (action == Action.LOSE) lose(fleet);
    }

    // ---- what happens to her -------------------------------------------------------------------------------

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        String id = CerberusCompanyStart.startingShipId();
        if (result == null || id == null) return;
        if (casualtyIn(result.getWinnerResult(), id) || casualtyIn(result.getLoserResult(), id)) {
            casualty = true;
            LOG.info("Second Wind: disabled or destroyed in an engagement; lost unless recovered");
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
            soldAt = market;
            salePrice = sale.getPrice();
            LOG.info("Second Wind: sold at " + market.getSpecId() + " for " + Math.round(salePrice));
        }
    }

    /** She is gone: one more d-mod, the bill set, and Castle's word on what happened. */
    private void lose(CampaignFleetAPI fleet) {
        FleetMemberAPI ship = remembered();
        if (ship == null) {
            LOG.warn("Second Wind: missing from the fleet with no record of her; nothing to offer back");
            return;
        }
        SectorAPI sector = Global.getSector();
        CargoAPI cargo = fleet.getCargo();
        float supplies = SecondWindKeep.salvageGained(ship.getRepairTracker().getSuppliesFromScuttling(),
                suppliesSeen, cargo.getSupplies());
        float fuel = SecondWindKeep.salvageGained(ship.getRepairTracker().getFuelFromScuttling(),
                fuelSeen, cargo.getFuel());
        float machinery = SecondWindKeep.salvageGained(ship.getRepairTracker().getHeavyMachineryFromScuttling(),
                machinerySeen, cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY));
        Cause cause = SecondWindKeep.cause(soldAt != null, casualty, supplies + fuel + machinery > 0f);
        float proceeds = cause == Cause.SOLD ? salePrice
                : cause == Cause.SCUTTLED ? worth(Commodities.SUPPLIES, supplies) + worth(Commodities.FUEL, fuel)
                        + worth(Commodities.HEAVY_MACHINERY, machinery) : 0f;
        if (cause == Cause.SOLD) takeOffTheMarket(soldAt, ship.getId());
        if (cause == Cause.BATTLE && isCompanyShip(ship)) CompanyShipDesignation.settleKeptLoss();
        casualty = false;
        soldAt = null;
        salePrice = 0f;

        String dMod = addLossDMod(ship, new Random(ship.getId().hashCode() * 31L + sector.getClock().getTimestamp()));
        ship.getStatus().repairFully();
        if (ship.getCaptain() != null && ship.getCaptain() == sector.getPlayerPerson()) ship.setCaptain(null);
        ship.updateStats();
        int bill = SecondWindKeep.bill(ship.getHullSpec() == null ? 0f : ship.getHullSpec().getBaseValue(), proceeds);
        sector.getPersistentData().put(LOSS_KEY, new SecondWindLoss(ship, cause, bill, dMod));
        LOG.info("Second Wind: lost (" + cause + "); buy-back " + bill + " credits" + (dMod != null ? ", d-mod " + dMod : ""));
        say(Strings.get(cause.onTheMarket() ? "secondWindKeepLostMarket" : "secondWindKeepLostBattle"));
    }

    /**
     * Buy her back at this port: she rejoins the fleet repaired and ready, and the bill comes out of the account,
     * into debt if it has to. Returns whether there was anything to buy.
     */
    public static boolean buyBack() {
        SectorAPI sector = Global.getSector();
        SecondWindLoss loss = loss();
        CampaignFleetAPI fleet = sector == null ? null : sector.getPlayerFleet();
        if (loss == null || fleet == null || loss.ship() == null) return false;
        sector.getPersistentData().remove(LOSS_KEY);
        FleetMemberAPI ship = loss.ship();
        if (find(fleet.getFleetData().getMembersListCopy(), ship.getId()) == null)
            fleet.getFleetData().addFleetMember(ship);
        ship.updateStats();
        ship.getRepairTracker().setCR(ship.getRepairTracker().getMaxCR());
        fleet.getFleetData().setSyncNeeded();
        fleet.getCargo().getCredits().subtract(loss.bill());
        LOG.info("Second Wind: bought back for " + loss.bill() + " credits");
        return true;
    }

    // ---- what the rest of the mod asks ----------------------------------------------------------------------

    /** Her loss while she is gone and waiting to be bought back, else null. */
    public static SecondWindLoss loss() {
        if (Global.getSector() == null) return null;
        Object loss = Global.getSector().getPersistentData().get(LOSS_KEY);
        return loss instanceof SecondWindLoss value ? value : null;
    }

    /** Whether this port can sell her back: she is lost, and it has a yard open to the company. */
    public static boolean buyBackOffered(MarketAPI market) {
        return loss() != null && Global.getSector() != null
                && OpeningPort.eligible(market, Global.getSector().getPlayerFaction(), Use.YARD);
    }

    /** Whether campaign missions can be flown now; see {@link SecondWindKeep#gate}. */
    public static Gate gate() {
        SectorAPI sector = Global.getSector();
        String id = CerberusCompanyStart.startingShipId();
        if (sector == null || id == null || sector.getPlayerFleet() == null) return Gate.OPEN;
        MarineRosterScript script = MarineRosterScript.getInstance();
        String home = script == null ? null : script.roster().companyShipId();
        boolean inFleet = find(sector.getPlayerFleet().getFleetData().getMembersListCopy(), id) != null;
        return SecondWindKeep.gate(true, inFleet, loss() != null, id.equals(home));
    }

    /** Why campaign missions cannot be flown, or null when they can. */
    public static String missionBlocker() {
        return switch (gate()) {
            case OPEN -> null;
            case LOST -> Strings.get("secondWindKeepMissionLost");
            case STORED -> Strings.get("secondWindKeepMissionStored");
            case NOT_ABOARD -> Strings.get("secondWindKeepMissionNotAboard");
        };
    }

    /**
     * What to tell the player while the company's home is away, or null when it is not: she is gone or stored
     * while the company lives aboard her (or has not chosen a ship). A company aboard another hull has a home.
     */
    public static String awayInstructions() {
        SectorAPI sector = Global.getSector();
        String id = CerberusCompanyStart.startingShipId();
        if (sector == null || id == null || sector.getPlayerFleet() == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        String home = script == null ? null : script.roster().companyShipId();
        if (home != null && !home.equals(id)) return null;
        if (find(sector.getPlayerFleet().getFleetData().getMembersListCopy(), id) != null) return null;
        SecondWindLoss loss = loss();
        if (loss != null) return Strings.format("secondWindKeepAwayLost", Misc.getDGSCredits(loss.bill()));
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
        soldAt = null;
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

    /** Adds the loss's d-mod when she has room for one; its display name, or null when none was added. */
    private static String addLossDMod(FleetMemberAPI ship, Random random) {
        ShipVariantAPI variant = ship.getVariant();
        if (SecondWindKeep.dModsToAdd(DModManager.getNumDMods(variant)) <= 0) return null;
        Set<String> before = new HashSet<>(variant.getPermaMods());
        DModManager.addDMods(ship, true, SecondWindKeep.D_MODS_PER_LOSS, random);
        DModManager.setDHull(ship.getVariant());
        for (String mod : ship.getVariant().getPermaMods()) {
            if (before.contains(mod)) continue;
            var spec = Global.getSettings().getHullModSpec(mod);
            return spec == null ? mod : spec.getDisplayName();
        }
        return null;
    }

    /** A sold hull is held by the buy-back, not by the market's ordinary ship list. */
    private static void takeOffTheMarket(SubmarketAPI market, String id) {
        CargoAPI cargo = market == null ? null : market.getCargoNullOk();
        if (cargo == null || cargo.getMothballedShips() == null) return;
        FleetMemberAPI listed = find(cargo.getMothballedShips().getMembersListCopy(), id);
        if (listed != null) cargo.getMothballedShips().removeFleetMember(listed);
    }

    private static float worth(String commodity, float quantity) {
        var spec = Global.getSettings().getCommoditySpec(commodity);
        return spec == null ? 0f : spec.getBasePrice() * quantity;
    }

    /** Carries a loss recorded by the 2026-10-20 yard build over to the buy-back. */
    private static void migrateYardOrder(Map<String, Object> data) {
        if (!(data.get(LEGACY_YARD_KEY) instanceof SecondWindYardOrder order)) return;
        data.remove(LEGACY_YARD_KEY);
        if (order.ship() != null && !data.containsKey(LOSS_KEY))
            data.put(LOSS_KEY, new SecondWindLoss(order.ship(), Cause.BATTLE, order.bill(), null));
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
