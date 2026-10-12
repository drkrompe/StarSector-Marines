package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.CompanyPreparation;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
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

    static PreparationGuide.Lesson ship(ShipTransferViewModel model, MarineOpsContext context) {
        return ship(roster(), model, context);
    }

    static PreparationGuide.Lesson ship(MarineRoster roster, ShipTransferViewModel model, MarineOpsContext context) {
        if (!CompanyPreparation.required(roster)) return null;
        if (roster.companyShipId() != null) return handoff(roster, context);
        if (CerberusCompanyStart.pendingDesignation()) return foundingLesson(context, model.preparationBill());
        return new PreparationGuide.Lesson(0, "CHOOSE OUR COMPANY SHIP",
                "Commander, choose a home with quarters and a boat bay. These stores cover one reusable shuttle and a fully equipped squad. Mira already has her appointment and first training point.\n\n"
                        + model.preparationBill(),
                List.of("transfer-commit", "transfer-fleet-list"), null, null);
    }

    static PreparationGuide.Lesson foundingLesson(MarineOpsContext context, String bill) {
        boolean installed = MercenaryCompanyConversion.installed(CerberusCompanyStart.startingShip());
        return new PreparationGuide.Lesson(0, installed ? "OUR COMPANY COMMAND VESSEL" : "MERCENARY COMPANY CONVERSION",
                CerberusCompanyStart.instructions() + (installed && bill != null ? "\n\n" + bill : ""),
                installed ? List.of("company-hq-ship", "transfer-fleet-list", "transfer-commit")
                        : List.of("page-nav-return"),
                installed ? "CHOOSE SHIP" : null,
                installed ? () -> context.goTo(ScreenId.SHIP_TRANSFER) : null);
    }

    static PreparationGuide.Lesson headquarters(MarineOpsContext context) {
        MarineRoster roster = roster();
        if (!CompanyPreparation.required(roster)) return null;
        return handoff(roster, context);
    }

    private static PreparationGuide.Lesson handoff(MarineRoster roster, MarineOpsContext context) {
        Step step = roster.officerInduction().step(roster);
        if (step == Step.SHIP && CerberusCompanyStart.pendingDesignation()) return foundingLesson(context, null);
        if (step == Step.SHIP) return new PreparationGuide.Lesson(0, "A HOME FOR THE COMPANY",
                OfficerInductionGuide.line(Step.SHIP, "Mira Hale").text(),
                List.of("company-hq-ship"), "CHOOSE SHIP", () -> context.goTo(ScreenId.SHIP_TRANSFER));
        boolean waiting = context.shipGettingReady();
        return new PreparationGuide.Lesson(step == Step.LIFT ? 1 : 2, waiting ? "PREPARING OUR SHIP" : "LET'S GET THE COMPANY READY",
                waiting ? "The crew is preparing our quarters and hangar. Once the deck is ready, we will commission our lift, meet Mira and muster her squad."
                        : step == Step.LIFT ? "Our home is ready. Come down to the Boat Deck and we will commission our first shuttle."
                        : "Our lift is aboard. Come to Barracks; Mira is waiting to take charge of our first formation.",
                List.of(step == Step.LIFT ? "page-nav-boats" : "page-nav-barracks"),
                step == Step.LIFT ? "BOAT DECK" : "MEET MIRA", waiting ? null : context::openOfficerInduction);
    }

    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model, MarineOpsContext context) {
        return boats(roster, model, context::openOfficerInduction);
    }

    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model, Runnable next) {
        if (!CompanyPreparation.required(roster)) return null;
        if (roster.officerInduction().step(roster) == Step.LIFT) {
            boolean building = model.fabricationFocused();
            return new PreparationGuide.Lesson(1, roster.boatDeck().wreckInBerth(0) ? "REPAIR OUR FIRST HERMES" : "COMMISSION OUR FIRST SHUTTLE",
                    (roster.boatDeck().wreckInBerth(0) ? "Second Wind came with two wrecked Hermes. This one is accessible; the other is behind the sealed second bay. Let us patch this one for our opening contracts." : OfficerInductionGuide.line(Step.LIFT, "Mira Hale").text())
                            + (building ? " Check the material bill, then confirm the work." : " Select the first berth on the right.")
                            + (CerberusCompanyStart.depot() != null ? " Our prepaid stores are available while docked at the conversion planet." : ""),
                    building ? List.of("boat-fabrication-materials", "boat-fabricate", "boat-fabrication-back")
                            : List.of("boat-list"), null, null);
        }
        return new PreparationGuide.Lesson(1, "LIFT READY",
                "Good. That boat is ours, with a place on the deck and a bill paid. It can make repeated trips to carry the squad. Plating and drive upgrades can wait. Now let us give Mira her first command.",
                List.of("page-nav-barracks"), "MEET MIRA", next);
    }

    static PreparationGuide.Lesson armory(MarineRoster roster, MarineOpsContext context) {
        return armory(roster, context::openOfficerInduction);
    }

    static PreparationGuide.Lesson armory(MarineRoster roster, Runnable next) {
        if (!CompanyPreparation.required(roster)) return null;
        Step step = roster.officerInduction().step(roster);
        if (step == Step.SHIP || step == Step.LIFT || step == Step.INTRODUCTION || step == Step.SPECIALTY)
            return new PreparationGuide.Lesson(step == Step.SHIP ? 0 : step == Step.LIFT ? 1 : 2,
                    "ONE STEP AT A TIME", "We will muster the squad once our ship, lift and field commander are ready. Let us finish that preparation first.",
                    List.of(), "CONTINUE", next);
        if (step == Step.FORMATION) return new PreparationGuide.Lesson(3, "MUSTER OUR FIRST SQUAD",
                "Twelve Marines, thirty-six supplies and twenty-four food. This order gives them their baseline field kit and stores as well as their places on the roll. Check the highlighted bill and Found Line Squad; then we will assign them to Mira.",
                List.of("found-squad"), null, null);
        return new PreparationGuide.Lesson(3, "A FORMATION NEEDS ITS OFFICER",
                "The squad and its field kit are on the roll. Return to Barracks and give Mira her standing command. Assigning the squad costs no further stores.",
                List.of("page-nav-barracks"), "BARRACKS", next);
    }
}
