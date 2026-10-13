package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;

/** Castle's first-company lesson; all speech retains the shared Castle portrait. */
final class OfficerInductionGuide {
    private OfficerInductionGuide() { }

    static CompanyManagementGuide.Line line(Step step, String name) {
        return switch (step) {
            case SHIP -> new CompanyManagementGuide.Line("preparation-ship", Strings.get("openingOfficerInductionGuideAHomeForTheCompany"),
                    Strings.get("openingOfficerInductionGuideFirstCommanderChooseAShipWithQuarters"));
            case LIFT -> new CompanyManagementGuide.Line("preparation-lift", Strings.get("openingOfficerInductionGuideOurFirstLift"),
                    Strings.get("openingOfficerInductionGuideOurPeopleNeedAWayDownAnd"));
            case INTRODUCTION -> new CompanyManagementGuide.Line("officer-introduction", Strings.get("openingOfficerInductionGuideMeetYourFieldCommander"),
                    Strings.format("openingOfficerInductionGuideCommanderWillLeadOurFirstFormationYou", name));
            case SPECIALTY -> new CompanyManagementGuide.Line("officer-specialty", Strings.get("openingOfficerInductionGuideHerFirstSpecialty"),
                    Strings.get("openingOfficerInductionGuideSheHasOneTrainingPointCoverProtects"));
            case FORMATION -> new CompanyManagementGuide.Line("officer-formation", Strings.get("openingOfficerInductionGuidePeopleBeforeOrders"),
                    Strings.get("openingOfficerInductionGuideNowSheNeedsAFormationRaiseA"));
            case ASSIGNMENT -> new CompanyManagementGuide.Line("officer-assignment", Strings.get("openingOfficerInductionGuideGiveHerACommand"),
                    Strings.format("openingOfficerInductionGuideSelectTheSquadThenClickAssignSelected", name));
            case READY -> new CompanyManagementGuide.Line("officer-ready", Strings.get("openingOfficerInductionGuideACompanyTakingShape"),
                    Strings.get("openingOfficerInductionGuideGoodSheHasASpecialtyAndPeople"));
            case COMPLETE -> new CompanyManagementGuide.Line("officer-complete", Strings.get("openingOfficerInductionGuideReady"), "");
        };
    }
}
