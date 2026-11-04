package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.CompanyPreparation;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.CampaignSquadFoundingResources;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.Global;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;

import java.util.List;

/** Presentation routes over the single saved induction and existing room orders. */
final class CompanyPreparationGuide {
    private CompanyPreparationGuide() { }

    static MarineRoster roster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script == null ? null : script.roster();
    }

    /**
     * Leaving the planet closes access to its stores, not the saved consignment or lesson. The induction draws the
     * squad's kit there; the first-lift lesson after Relief draws the yard credit held for the Hermes.
     */
    static PreparationGuide.Lesson withDepotAccess(MarineRoster roster, PreparationGuide.Lesson lesson,
                                                  Runnable leave) {
        if (lesson == null) return lesson;
        int boat;
        List<SquadFoundingCost.Line> squad;
        String copy;
        if (CompanyPreparation.required(roster)) {
            Step step = roster.officerInduction().step(roster);
            if (step == Step.SHIP) {
                boat = CompanyPreparation.boatPrice(roster, ShuttleType.HERMES);
                squad = CompanyPreparation.squadBill(roster);
            } else if (step == Step.FORMATION) {
                boat = 0;
                squad = CompanyPreparation.squadBill(roster);
            } else return lesson;
            copy = "openingCompanyPreparationGuideNothingWasSpentByLeavingCommanderOur";
        } else if (CampaignLessons.firstLiftPending(roster)) {
            boat = CompanyPreparation.boatPrice(roster, ShuttleType.HERMES);
            squad = List.of();
            copy = "openingCompanyPreparationGuideTheHermesPartsAreStillWaitingAt";
        } else return lesson;
        var depot = CerberusCompanyStart.depot();
        if (depot == null || CerberusCompanyStart.accessibleDepot() != null) return lesson;
        var credits = CampaignFabricationResources.forPreparation();
        var stores = CampaignSquadFoundingResources.forPreparation();
        long price = (long) boat + (squad.isEmpty() ? 0 : CompanyPreparation.squadPrice(roster));
        boolean needsLocalStores = price > 0 && credits.credits() < price && depot.credits() > 0
                || squad.stream().anyMatch(line -> stores.available(line.commodityId()) < line.quantity()
                        && depot.available(line.commodityId()) > 0);
        if (!needsLocalStores) return lesson;
        var destination = CerberusCompanyStart.destination();
        String port = destination == null ? depot.marketId() : destination.getName();
        return new PreparationGuide.Lesson(lesson.milestone(), Strings.get("openingCompanyPreparationGuideOurStoresAreStillReserved"),
                Strings.format(copy, port),
                List.of("page-nav-return"), destination == null ? Strings.get("openingCompanyPreparationGuideReturn") : Strings.get("openingCompanyPreparationGuideReturnToDepot"), () -> {
                    if (destination != null && Global.getSector() != null)
                        Global.getSector().getCampaignUI().layInCourseForNextStep(destination);
                    leave.run();
                }, 320);
    }

    static PreparationGuide.Lesson ship(MarineOpsContext context) {
        return ship(roster(), context);
    }

    static PreparationGuide.Lesson ship(MarineRoster roster, MarineOpsContext context) {
        if (!CompanyPreparation.required(roster)) return null;
        if (roster.companyShipId() != null) return handoff(roster, context);
        if (CerberusCompanyStart.pendingDesignation()) return foundingLesson(context);
        return new PreparationGuide.Lesson(0, Strings.get("openingCompanyPreparationGuideChooseOurCompanyShip"),
                Strings.get("openingCompanyPreparationGuideCommanderChooseAHomeWithQuartersAnd"),
                List.of("transfer-commit", "transfer-fleet-list"), null, null);
    }

    static PreparationGuide.Lesson foundingLesson(MarineOpsContext context) {
        boolean installed = MercenaryCompanyConversion.installed(CerberusCompanyStart.startingShip());
        return new PreparationGuide.Lesson(0, installed ? Strings.get("openingCompanyPreparationGuideOurCompanyCommandVessel") : Strings.get("openingCompanyPreparationGuideMercenaryCompanyConversion"),
                CerberusCompanyStart.instructions(),
                installed ? List.of("company-hq-ship", "transfer-fleet-list", "transfer-commit")
                        : List.of("page-nav-return"),
                installed ? Strings.get("openingCompanyPreparationGuideChooseShip") : null,
                installed ? () -> context.goTo(ScreenId.SHIP_TRANSFER) : null);
    }

    static PreparationGuide.Lesson headquarters(MarineOpsContext context) {
        MarineRoster roster = roster();
        if (!CompanyPreparation.required(roster)) return CampaignLessons.handoff(roster, context, ScreenId.COMPANY_HQ);
        return handoff(roster, context);
    }

    private static PreparationGuide.Lesson handoff(MarineRoster roster, MarineOpsContext context) {
        Step step = roster.officerInduction().step(roster);
        if (step == Step.SHIP && CerberusCompanyStart.pendingDesignation()) return foundingLesson(context);
        if (step == Step.SHIP) return new PreparationGuide.Lesson(0, Strings.get("openingCompanyPreparationGuideAHomeForTheCompany"),
                OfficerInductionGuide.line(Step.SHIP, "Morgan Hale").text(),
                List.of("company-hq-ship"), Strings.get("openingCompanyPreparationGuideChooseShip"), () -> context.goTo(ScreenId.SHIP_TRANSFER));
        boolean waiting = context.shipGettingReady();
        return new PreparationGuide.Lesson(1, waiting ? Strings.get("openingCompanyPreparationGuidePreparingOurShip") : Strings.get("openingCompanyPreparationGuideLetSGetTheCompanyReady"),
                waiting ? Strings.get("openingCompanyPreparationGuideTheCrewIsPreparingOurQuartersAnd")
                        : Strings.get("openingCompanyPreparationGuideOurLiftIsAboardComeToBarracks"),
                List.of("page-nav-barracks"), Strings.get("openingCompanyPreparationGuideMeetSabine"),
                waiting ? null : context::openOfficerInduction);
    }

    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model, MarineOpsContext context) {
        if (!CompanyPreparation.required(roster)) return CampaignLessons.boats(roster, model);
        return boats(roster, model, context::openOfficerInduction);
    }

    /** The boats wait for the depot job; Relief's company rides up on Wayline's truck. */
    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model, Runnable next) {
        if (!CompanyPreparation.required(roster)) return null;
        return new PreparationGuide.Lesson(1, Strings.get("openingCompanyPreparationGuideOneStepAtATime"),
                Strings.get("openingCompanyPreparationGuideTheBoatsCanWait"),
                List.of("page-nav-barracks"), Strings.get("openingCompanyPreparationGuideMeetSabine"), next);
    }
}
