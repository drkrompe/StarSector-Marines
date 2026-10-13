package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipRestoration;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** Hull repairs own payment; mission two supplies Mech Lab restoration with Wayward's transfer. */
public final class CompanyShipRestoration {
    public enum Work {
        BILLETS(4_000), POWER(3_000);
        public final int price;
        Work(int price) { this.price = price; }
    }
    private static final String KEY = "sm_ship_restoration_";
    private CompanyShipRestoration() { }
    public static ShipRestoration state(FleetMemberAPI ship) {
        if (CompanyBayRestoration.state(ship) == BoatBayState.NONE) return ShipRestoration.NONE;
        var script = MarineRosterScript.getInstance();
        var roster = script == null ? null : script.roster();
        var data = Global.getSector().getPersistentData();
        return new ShipRestoration(true, mechBayRestored(roster),
                Boolean.TRUE.equals(data.get(KEY + ship.getId() + Work.BILLETS)),
                Boolean.TRUE.equals(data.get(KEY + ship.getId() + Work.POWER)));
    }
    public static boolean mechBayRestored(MarineRoster roster) {
        return roster != null && roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId);
    }
    public record Offer(ShipRestoration state, int credits, String refusal) { }
    public static String refusal(Work work, ShipRestoration state, boolean home, boolean active,
                                 boolean complete, boolean docked, int credits) {
        if (!home || !state.applies()) return "House the company aboard Second Wind's original hull.";
        if (work == Work.BILLETS ? state.billets() : state.power()) return "This work is already complete.";
        if (!active) return "The converted company ship must be active.";
        if (!complete) return "Complete Hold Until Relieved before ordering this work.";
        if (!docked) return "Dock at an accessible planetary spaceport for yard work.";
        if (credits < work.price) return "Insufficient credits.";
        return null;
    }
    public static Offer offer(FleetMemberAPI ship, MarineRoster roster, Work work) {
        var state = state(ship);
        var fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        int credits = fleet == null ? 0 : (int) fleet.getCargo().getCredits().get();
        boolean home = fleet != null && ship != null && roster != null && ship.getId().equals(roster.companyShipId())
                && fleet.getFleetData().getMembersListCopy().contains(ship);
        return new Offer(state, credits, refusal(work, state, home,
                ship != null && !ship.isMothballed() && MercenaryCompanyConversion.installed(ship),
                roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId),
                CompanyYard.dockedMarket() != null, credits));
    }
    public static String repair(FleetMemberAPI ship, MarineRoster roster, Work work) {
        var offer = offer(ship, roster, work);
        if (offer.refusal() != null) return offer.refusal();
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(work.price);
        Global.getSector().getPersistentData().put(KEY + ship.getId() + work, true);
        return null;
    }
}
