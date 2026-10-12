package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.SecondhandWings;
import com.dillon.starsectormarines.ops.CompanyShipDesignation;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.ShipsBoatsAboard;
import com.fs.starfarer.api.Global;
import java.util.Objects;

/** Campaign adapter for the saved, hull-specific conversion and the fleet credit account. */
public final class SecondhandWingsCampaign {
    private SecondhandWingsCampaign() { }
    public record Offer(boolean visible, String refusal, int credits) { }

    public static boolean available(MarineRoster roster) {
        return roster != null && roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)
                && !roster.boatDeck().secondhandWingsPurchased();
    }

    public static boolean converted(String shipId) {
        if (Global.getSector() == null) return false;
        var script = MarineRosterScript.getInstance();
        return script != null && script.roster() != null
                && script.roster().boatDeck().hasAeroshuttleConversion(shipId);
    }

    public static Offer offer(MarineRoster roster) {
        if (roster == null || Global.getSector() == null || Global.getSector().getPlayerFleet() == null)
            return new Offer(false, "No company fleet.", 0);
        var fleet = Global.getSector().getPlayerFleet();
        var ship = CompanyShipDesignation.aboard();
        int credits = (int) fleet.getCargo().getCredits().get();
        boolean home = ship != null && ship.getId() != null
                && Objects.equals(ship.getId(), CerberusCompanyStart.startingShipId())
                && fleet.getFleetData().getMembersListCopy().contains(ship)
                && MercenaryCompanyConversion.installed(ship);
        String refusal = SecondhandWings.refusal(roster.boatDeck(),
                roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId), home,
                ship != null && CompanyBayRestoration.state(ship) == BoatBayState.RESTORED,
                ship != null && !ship.isMothballed(), CompanyYard.dockedMarket() != null, credits);
        return new Offer(available(roster), refusal, credits);
    }

    public static String purchase(MarineRoster roster, SecondhandWings.Review reviewed) {
        ShipsBoatsAboard.reconcile(roster.boatDeck());
        return SecondhandWings.purchase(roster.boatDeck(), reviewed, offer(roster).refusal(), cost -> {
            var credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
            if (credits.get() < cost) return false;
            credits.subtract(cost);
            return true;
        });
    }
}
