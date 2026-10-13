package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** Saved interior condition belongs to the original hull, independent of vanilla hull repairs. */
public final class CompanyBayRestoration {
    public static final int CREDIT_COST = 8_000;
    private static final String KEY_PREFIX = "sm_restored_boat_bay_";
    private CompanyBayRestoration() { }

    public static BoatBayState state(FleetMemberAPI ship) {
        if (!MercenaryCompanyConversion.compatible(ship) || ship.getId() == null
                || !ship.getId().equals(CerberusCompanyStart.startingShipId())) return BoatBayState.NONE;
        return Boolean.TRUE.equals(Global.getSector().getPersistentData().get(KEY_PREFIX + ship.getId()))
                ? BoatBayState.RESTORED : BoatBayState.DAMAGED;
    }

    public record Offer(boolean visible, int credits, String refusal) { }

    public static String refusal(boolean owned, BoatBayState state, boolean converted,
                                 boolean firstMissionComplete, boolean mothballed, boolean docked, int credits) {
        if (!owned || state == BoatBayState.NONE) return Strings.get("openingCompanyBayRestorationSelectSecondWindSOriginalHullFor");
        if (state == BoatBayState.RESTORED) return Strings.get("openingCompanyBayRestorationTheSecondBayIsAlreadyRestored");
        if (!converted) return Strings.get("openingCompanyBayRestorationCompleteTheMercenaryCompanyConversionFirst");
        if (!firstMissionComplete) return Strings.get("openingCompanyBayRestorationCompleteHoldUntilRelievedToUnlockThis");
        if (mothballed) return Strings.get("openingCompanyBayRestorationReactivateTheShipBeforeOrderingYardWork");
        if (!docked) return Strings.get("openingCompanyBayRestorationDockAtAnAccessiblePlanetarySpaceportFor");
        if (credits < CREDIT_COST) return Strings.get("openingCompanyBayRestorationInsufficientCreditsForTheRepair");
        return null;
    }

    public static Offer offer(FleetMemberAPI ship, MarineRoster roster) {
        BoatBayState state = state(ship);
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null)
            return new Offer(false, 0, Strings.get("openingCompanyBayRestorationNoPlayerFleet"));
        var fleet = Global.getSector().getPlayerFleet();
        int credits = (int) fleet.getCargo().getCredits().get();
        boolean owned = ship != null && fleet.getFleetData().getMembersListCopy().contains(ship);
        return new Offer(state == BoatBayState.DAMAGED, credits, refusal(owned, state,
                MercenaryCompanyConversion.installed(ship),
                roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId),
                ship != null && ship.isMothballed(), CompanyYard.dockedMarket() != null, credits));
    }

    /** Rechecks the reviewed hull and all prerequisites; success never awards a boat or depot stock. */
    public static String repair(FleetMemberAPI ship, MarineRoster roster) {
        String refused = offer(ship, roster).refusal();
        if (refused != null) return refused;
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(CREDIT_COST);
        Global.getSector().getPersistentData().put(KEY_PREFIX + ship.getId(), true);
        return null;
    }
}
