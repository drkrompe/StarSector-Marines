package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.CompanyPreparation;
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
        MarineRosterScript script = Global.getSector() == null ? null : MarineRosterScript.getInstance();
        return script == null ? null : script.roster();
    }

    /** Leaving the planet closes access to its stores, not the saved consignment or lesson. */
    static PreparationGuide.Lesson withDepotAccess(MarineRoster roster, PreparationGuide.Lesson lesson,
                                                  Runnable leave) {
        if (lesson == null || !CompanyPreparation.required(roster)) return lesson;
        Step step = roster.officerInduction().step(roster);
        if (step != Step.SHIP && step != Step.LIFT && step != Step.FORMATION) return lesson;
        var depot = CerberusCompanyStart.depot();
        if (depot == null || CerberusCompanyStart.accessibleDepot() != null) return lesson;
        var resources = CampaignFabricationResources.forPreparation();
        boolean needsLocalStores = CompanyPreparation.remainingBill(roster, ShuttleType.HERMES).stream()
                .anyMatch(line -> resources.available(line.commodityId()) < line.quantity()
                        && depot.available(line.commodityId()) > 0);
        if (!needsLocalStores) return lesson;
        var destination = CerberusCompanyStart.destination();
        String port = destination == null ? depot.marketId() : destination.getName();
        return new PreparationGuide.Lesson(lesson.milestone(), Strings.get("openingCompanyPreparationGuideOurStoresAreStillReserved"),
                Strings.format("openingCompanyPreparationGuideNothingWasSpentByLeavingCommanderOur", port),
                List.of("page-nav-return"), destination == null ? Strings.get("openingCompanyPreparationGuideReturn") : Strings.get("openingCompanyPreparationGuideReturnToDepot"), () -> {
                    if (destination != null && Global.getSector() != null)
                        Global.getSector().getCampaignUI().layInCourseForNextStep(destination);
                    leave.run();
                }, 320);
    }

    static PreparationGuide.Lesson ship(ShipTransferViewModel model, MarineOpsContext context) {
        return ship(roster(), model, context);
    }

    static PreparationGuide.Lesson ship(MarineRoster roster, ShipTransferViewModel model, MarineOpsContext context) {
        if (!CompanyPreparation.required(roster)) return null;
        if (roster.companyShipId() != null) return handoff(roster, context);
        if (CerberusCompanyStart.pendingDesignation()) return foundingLesson(context, model.preparationBill());
        return new PreparationGuide.Lesson(0, Strings.get("openingCompanyPreparationGuideChooseOurCompanyShip"),
                Strings.format("openingCompanyPreparationGuideCommanderChooseAHomeWithQuartersAnd", model.preparationBill()),
                List.of("transfer-commit", "transfer-fleet-list"), null, null);
    }

    static PreparationGuide.Lesson foundingLesson(MarineOpsContext context, String bill) {
        boolean installed = MercenaryCompanyConversion.installed(CerberusCompanyStart.startingShip());
        return new PreparationGuide.Lesson(0, installed ? Strings.get("openingCompanyPreparationGuideOurCompanyCommandVessel") : Strings.get("openingCompanyPreparationGuideMercenaryCompanyConversion"),
                CerberusCompanyStart.instructions() + (installed && bill != null ? "\n\n" + bill : ""),
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
        if (step == Step.SHIP && CerberusCompanyStart.pendingDesignation()) return foundingLesson(context, null);
        if (step == Step.SHIP) return new PreparationGuide.Lesson(0, Strings.get("openingCompanyPreparationGuideAHomeForTheCompany"),
                OfficerInductionGuide.line(Step.SHIP, "Mira Hale").text(),
                List.of("company-hq-ship"), Strings.get("openingCompanyPreparationGuideChooseShip"), () -> context.goTo(ScreenId.SHIP_TRANSFER));
        boolean waiting = context.shipGettingReady();
        return new PreparationGuide.Lesson(step == Step.LIFT ? 1 : 2, waiting ? Strings.get("openingCompanyPreparationGuidePreparingOurShip") : Strings.get("openingCompanyPreparationGuideLetSGetTheCompanyReady"),
                waiting ? Strings.get("openingCompanyPreparationGuideTheCrewIsPreparingOurQuartersAnd")
                        : step == Step.LIFT ? Strings.get("openingCompanyPreparationGuideOurHomeIsReadyComeDownTo")
                        : Strings.get("openingCompanyPreparationGuideOurLiftIsAboardComeToBarracks"),
                List.of(step == Step.LIFT ? "page-nav-boats" : "page-nav-barracks"),
                step == Step.LIFT ? Strings.get("openingCompanyPreparationGuideBoatDeck") : Strings.get("openingCompanyPreparationGuideMeetMira"), waiting ? null : context::openOfficerInduction);
    }

    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model, MarineOpsContext context) {
        return boats(roster, model, context::openOfficerInduction);
    }

    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model, Runnable next) {
        if (!CompanyPreparation.required(roster)) return null;
        if (roster.officerInduction().step(roster) == Step.LIFT) {
            boolean building = model.fabricationFocused();
            return new PreparationGuide.Lesson(1, roster.boatDeck().wreckInBerth(0) ? Strings.get("openingCompanyPreparationGuideRepairOurFirstHermes") : Strings.get("openingCompanyPreparationGuideCommissionOurFirstShuttle"),
                    (roster.boatDeck().wreckInBerth(0) ? Strings.get("openingCompanyPreparationGuideSecondWindCameWithTwoWreckedHermes") : OfficerInductionGuide.line(Step.LIFT, "Mira Hale").text())
                            + (building ? Strings.get("openingCompanyPreparationGuideCheckTheMaterialBillThenConfirmThe") : Strings.get("openingCompanyPreparationGuideSelectTheFirstBerthOnTheRight"))
                            + (CerberusCompanyStart.depot() != null ? Strings.get("openingCompanyPreparationGuideOurPrepaidStoresAreAvailableWhileDocked") : ""),
                    building ? List.of("boat-fabrication-materials", "boat-fabricate", "boat-fabrication-back")
                            : List.of("boat-list"), null, null);
        }
        return new PreparationGuide.Lesson(1, Strings.get("openingCompanyPreparationGuideLiftReady"),
                Strings.get("openingCompanyPreparationGuideGoodThatBoatIsOursWithA"),
                List.of("page-nav-barracks"), Strings.get("openingCompanyPreparationGuideMeetMira"), next);
    }

    static PreparationGuide.Lesson armory(MarineRoster roster, MarineOpsContext context) {
        return armory(roster, context::openOfficerInduction);
    }

    static PreparationGuide.Lesson armory(MarineRoster roster, Runnable next) {
        if (!CompanyPreparation.required(roster)) return null;
        Step step = roster.officerInduction().step(roster);
        if (step == Step.SHIP || step == Step.LIFT || step == Step.INTRODUCTION || step == Step.SPECIALTY)
            return new PreparationGuide.Lesson(step == Step.SHIP ? 0 : step == Step.LIFT ? 1 : 2,
                    Strings.get("openingCompanyPreparationGuideOneStepAtATime"), Strings.get("openingCompanyPreparationGuideWeWillMusterTheSquadOnceOur"),
                    List.of(), Strings.get("openingCompanyPreparationGuideContinue"), next);
        if (step == Step.FORMATION) return new PreparationGuide.Lesson(3, Strings.get("openingCompanyPreparationGuideMusterOurFirstSquad"),
                Strings.get("openingCompanyPreparationGuideTwelveMarinesThirtySixSuppliesAndTwenty"),
                List.of("found-squad"), null, null);
        return new PreparationGuide.Lesson(3, Strings.get("openingCompanyPreparationGuideAFormationNeedsItsOfficer"),
                Strings.get("openingCompanyPreparationGuideTheSquadAndItsFieldKitAre"),
                List.of("page-nav-barracks"), Strings.get("openingCompanyPreparationGuideBarracks"), next);
    }
}
