package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.FleetFacilities;
import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Reclamation;
import com.dillon.starsectormarines.ops.CompanyShipDesignation;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/** Hull-owned progression; paid stages recheck the live campaign at commit. */
public final class CompanyWorkshopRestoration {
    private static final String KEY = "sm_workshop_restoration_";
    public static final int CLEARANCE_METALS = 10;
    private CompanyWorkshopRestoration() { }

    public static WorkshopState state(FleetMemberAPI ship) {
        if (CompanyBayRestoration.state(ship) == BoatBayState.NONE) return WorkshopState.NONE;
        Object saved = CampaignBackend.savedData().get(KEY + ship.getId());
        if (saved instanceof String name) {
            try { return WorkshopState.valueOf(name); }
            catch (IllegalArgumentException ignored) { /* An unknown stage cannot award a benefit. */ }
        }
        return WorkshopState.BURIED;
    }
    public static boolean toolingAboard() {
        if (FleetFacilities.inUse()) return fleetWorkshop().tooled();
        FleetMemberAPI ship = CompanyShipDesignation.aboard();
        return ship != null && !ship.isMothballed() && state(ship).tooled();
    }
    /** The home ship's machine shop as a recycler; a hull outside the restoration chain runs as built. */
    public static Reclamation reclamationAboard() {
        if (FleetFacilities.inUse()) return reclamation(fleetWorkshop());
        FleetMemberAPI ship = CompanyShipDesignation.aboard();
        return ship == null || ship.isMothballed() ? Reclamation.NONE : reclamation(state(ship));
    }

    /**
     * Fleet facilities' machine shop as the stage of the chain its fabrication band matches: restored
     * machinery, tooling, then the reclamation line (`fleet-facilities.md`).
     */
    static WorkshopState fleetWorkshop() { return workshop(FleetFacilities.condition().band(RefitTrack.FABRICATION)); }

    static WorkshopState workshop(RefitBand fabrication) {
        return switch (fabrication) {
            case DEFICIT, RESTORED -> WorkshopState.REPAIRED;
            case IMPROVED_I -> WorkshopState.EQUIPPED;
            case IMPROVED_II -> WorkshopState.RECLAIMING;
        };
    }

    /**
     * Machinery that runs recycles at half, installed tooling at three quarters, a reclamation
     * line at the whole bill; a buried or cleared shop not at all.
     */
    public static Reclamation reclamation(WorkshopState state) {
        return switch (state) {
            case NONE, REPAIRED -> Reclamation.MACHINERY;
            case EQUIPPED -> Reclamation.TOOLED;
            case RECLAIMING -> Reclamation.FULL;
            case BURIED, CLEARED -> Reclamation.NONE;
        };
    }
    public record Offer(WorkshopState state, int credits, String refusal) { }

    public static String refusal(WorkshopState actual, WorkshopState reviewed, boolean ownedHome,
                                 boolean converted, boolean active, boolean completed, boolean yard,
                                 int credits, float space) {
        if (!ownedHome || actual == WorkshopState.NONE) return Strings.get("openingCompanyWorkshopRestorationHouseTheCompanyAboardSecondWindS");
        if (actual != reviewed) return Strings.get("openingCompanyWorkshopRestorationTheWorkshopChangedInspectItsCurrentStage");
        if (!converted || !active) return Strings.get("openingCompanyWorkshopRestorationTheConvertedCompanyShipMustBeActive");
        if (!completed) return Strings.get("openingCompanyWorkshopRestorationCompleteHoldUntilRelievedBeforeRestoringThe");
        if (actual == WorkshopState.RECLAIMING) return Strings.get("openingCompanyWorkshopRestorationWorkshopFullyEquipped");
        if (!yard) return Strings.get("openingCompanyWorkshopRestorationDockAtAnAccessiblePlanetarySpaceportFor");
        if (credits < actual.price()) return Strings.get("openingCompanyWorkshopRestorationInsufficientCredits");
        if (actual == WorkshopState.BURIED && space < CLEARANCE_METALS)
            return Strings.get("openingCompanyWorkshopRestorationMakeRoomForRecoveredMetalsInFleet");
        return null;
    }
    private static CargoAPI cargo() {
        return CampaignBackend.playerFleet() == null
                ? null : CampaignBackend.playerFleet().getCargo();
    }
    private static boolean ownedHome(FleetMemberAPI ship, MarineRoster roster) {
        return ship != null && CampaignBackend.playerFleet() != null
                && CampaignBackend.playerFleet().getFleetData().getMembersListCopy().contains(ship)
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
        return new Offer(state, credits, refusal);
    }
    public static String advance(FleetMemberAPI ship, MarineRoster roster, WorkshopState reviewed) {
        Offer live = offer(ship, roster);
        if (live.state() != reviewed) return Strings.get("openingCompanyWorkshopRestorationTheWorkshopChangedInspectItsCurrentStage");
        if (live.refusal() != null) return live.refusal();
        CargoAPI cargo = cargo();
        cargo.getCredits().subtract(reviewed.price());
        CampaignBackend.savedData().put(KEY + ship.getId(), reviewed.next().name());
        if (reviewed == WorkshopState.BURIED) cargo.addCommodity(Commodities.METALS, CLEARANCE_METALS);
        return null;
    }
}
