package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.CompanyPreparation;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
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
                List.of("page-nav-barracks"), Strings.get("openingCompanyPreparationGuideMeetMorgan"),
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
                List.of("page-nav-barracks"), Strings.get("openingCompanyPreparationGuideMeetMorgan"), next);
    }
}
