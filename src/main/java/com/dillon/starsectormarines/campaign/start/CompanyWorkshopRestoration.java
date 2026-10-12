package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.CompanyShipDesignation;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/** Hull-owned progression; paid stages and reclamation recheck the live campaign at commit. */
public final class CompanyWorkshopRestoration {
    private static final String KEY = "sm_workshop_restoration_";
    public static final int CLEARANCE_METALS = 10;
    public static final int RECLAIMED_METALS = 4;
    private CompanyWorkshopRestoration() { }

    public static WorkshopState state(FleetMemberAPI ship) {
        if (CompanyBayRestoration.state(ship) == BoatBayState.NONE) return WorkshopState.NONE;
        Object saved = Global.getSector().getPersistentData().get(KEY + ship.getId());
        if (saved instanceof String name) {
            try { return WorkshopState.valueOf(name); }
            catch (IllegalArgumentException ignored) { /* An unknown stage cannot award a benefit. */ }
        }
        return WorkshopState.BURIED;
    }
    public static boolean toolingAboard() {
        FleetMemberAPI ship = CompanyShipDesignation.aboard();
        return ship != null && !ship.isMothballed() && state(ship) == WorkshopState.EQUIPPED;
    }
    public record Offer(WorkshopState state, int credits, String refusal, String reclaimRefusal,
                        int machinery, int metals) { }

    public static String refusal(WorkshopState actual, WorkshopState reviewed, boolean ownedHome,
                                 boolean converted, boolean active, boolean completed, boolean yard,
                                 int credits, float space) {
        if (!ownedHome || actual == WorkshopState.NONE) return "House the company aboard Second Wind's original hull.";
        if (actual != reviewed) return "The workshop changed. Inspect its current stage before ordering.";
        if (!converted || !active) return "The converted company ship must be active.";
        if (!completed) return "Complete Hold Until Relieved before restoring the workshop.";
        if (actual == WorkshopState.EQUIPPED) return "Workshop fully equipped.";
        if (!yard) return "Dock at an accessible planetary spaceport for this work.";
        if (credits < actual.price()) return "Insufficient credits.";
        if (actual == WorkshopState.BURIED && space < CLEARANCE_METALS)
            return "Make room for 10 recovered metals in fleet cargo.";
        return null;
    }
    private static CargoAPI cargo() {
        return Global.getSector() == null || Global.getSector().getPlayerFleet() == null
                ? null : Global.getSector().getPlayerFleet().getCargo();
    }
    private static boolean ownedHome(FleetMemberAPI ship, MarineRoster roster) {
        return ship != null && Global.getSector() != null && Global.getSector().getPlayerFleet() != null
                && Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(ship)
                && roster != null && ship.getId().equals(roster.companyShipId());
    }
    public static Offer offer(FleetMemberAPI ship, MarineRoster roster) {
        WorkshopState state = state(ship);
        CargoAPI cargo = cargo();
        int credits = cargo == null ? 0 : (int) cargo.getCredits().get();
        boolean home = ownedHome(ship, roster);
        String refusal = refusal(state, state, home, MercenaryCompanyConversion.installed(ship),
                ship != null && !ship.isMothballed(), roster != null
                        && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId),
                CompanyYard.dockedMarket() != null, credits, cargo == null ? 0 : cargo.getSpaceLeft());
        int machinery = cargo == null ? 0 : (int) cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
        String reclaim = !home || ship.isMothballed() || !state.operational()
                ? "Repair this company's workshop to reclaim machinery."
                : machinery < 1 ? "Requires 1 heavy machinery in fleet cargo."
                : cargo.getSpaceLeft() < RECLAIMED_METALS - 1 ? "Make room for the reclaimed metals." : null;
        return new Offer(state, credits, refusal, reclaim, machinery,
                cargo == null ? 0 : (int) cargo.getCommodityQuantity(Commodities.METALS));
    }
    public static String advance(FleetMemberAPI ship, MarineRoster roster, WorkshopState reviewed) {
        Offer live = offer(ship, roster);
        if (live.state() != reviewed) return "The workshop changed. Inspect its current stage before ordering.";
        if (live.refusal() != null) return live.refusal();
        CargoAPI cargo = cargo();
        cargo.getCredits().subtract(reviewed.price());
        Global.getSector().getPersistentData().put(KEY + ship.getId(), reviewed.next().name());
        if (reviewed == WorkshopState.BURIED) cargo.addCommodity(Commodities.METALS, CLEARANCE_METALS);
        return null;
    }
    public static String reclaim(FleetMemberAPI ship, MarineRoster roster) {
        Offer live = offer(ship, roster);
        if (live.reclaimRefusal() != null) return live.reclaimRefusal();
        CargoAPI cargo = cargo();
        cargo.removeCommodity(Commodities.HEAVY_MACHINERY, 1);
        cargo.addCommodity(Commodities.METALS, RECLAIMED_METALS);
        return null;
    }
}
