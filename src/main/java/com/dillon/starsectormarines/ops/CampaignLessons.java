package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import java.util.ArrayList;
import java.util.List;

/** Resumable guidance derived from actual company work, independent of an entry screen. */
final class CampaignLessons {
    enum Step { NONE, BOAT_RECOVERY, FIRST_LIFT, REFIT, GANTRY, INVESTMENTS, FORMATIONS }
    private CampaignLessons() { }

    static Step next(MarineRoster roster) {
        if (roster == null || roster.officerInductionRequired()) return Step.NONE;
        if (roster.campaignLessons().boatRecoveryPending()) return Step.BOAT_RECOVERY;
        if (!roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)) return Step.NONE;
        if (firstLiftPending(roster)) return Step.FIRST_LIFT;
        if (FirstRefit.available(roster)) return Step.REFIT;
        if (!roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) return Step.NONE;
        if (roster.mechBay().mechById(WaylineHound.ID) != null
                && roster.mechBay().labLesson() > 0 && roster.mechBay().labLesson() < 5) return Step.GANTRY;
        if (!roster.campaignLessons().investmentsReviewed()) return Step.INVESTMENTS;
        if (roster.campaignLessons().junctionPrepared()) return Step.NONE;
        return roster.campaignLessons().formationsDeferred((int) formations(roster)) ? Step.NONE : Step.FORMATIONS;
    }

    /**
     * Relief needed no boat: the company rode up on Wayline's truck. The depot job is its first ferry battle, so
     * between the two the company repairs (or commissions) its first Hermes from the prepaid stores.
     */
    static boolean firstLiftPending(MarineRoster roster) {
        return roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)
                && !roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)
                && roster.boatDeck().flightReady().isEmpty();
    }

    static PreparationGuide.Lesson handoff(MarineRoster roster, MarineOpsContext context, ScreenId host) {
        if (context.getSelectedMission() != null && context.getSelectedMission().source.isDebug()) return null;
        return switch (next(roster)) {
            case BOAT_RECOVERY -> lesson(1, "boatLessonTitle", "boatLessonHandoff",
                    List.of("page-nav-boats"), "boatLessonVisit", () -> context.goTo(ScreenId.BOAT_DECK));
            case FIRST_LIFT -> host == ScreenId.BOAT_DECK ? null : lesson(1, "lessonFirstLiftTitle", "lessonFirstLiftHandoff",
                    List.of("page-nav-boats"), "boatLessonVisit", () -> context.goTo(ScreenId.BOAT_DECK));
            case REFIT -> lesson(0, "lessonRefitTitle", "lessonRefitHandoff", List.of("barracks-equipment"),
                    "lessonEquipment", () -> context.openFleetArmoryWorkspaceFrom(host));
            case GANTRY -> lesson(1, "lessonGantryTitle", "lessonGantryHandoff", List.of("page-nav-mech-lab"),
                    "lessonMechLab", () -> context.goTo(ScreenId.MECH_LAB));
            case INVESTMENTS -> lesson(2, "lessonInvestmentsTitle", "lessonInvestmentsCopy",
                    List.of("page-nav-boats", "page-nav-engineering"), "lessonContinue",
                    roster.campaignLessons()::reviewInvestments);
            case FORMATIONS -> host == ScreenId.BARRACKS ? null : later(lesson(3,
                    "lessonFormationTitle", "lessonFormationHandoff", List.of("page-nav-barracks"),
                    "lessonBarracks", () -> context.goTo(ScreenId.BARRACKS)), roster);
            default -> null;
        };
    }

    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model) {
        if (next(roster) == Step.FIRST_LIFT) return firstLift(roster, model);
        if (next(roster) != Step.BOAT_RECOVERY) return null;
        CampaignBoat boat = model.boat(roster.campaignLessons().damagedBoatId());
        if (boat == null) return lesson(1, "boatLessonTitle", "boatLessonGone", List.of("boat-list"),
                "lessonContinue", roster.campaignLessons()::reviewBoatRecovery);
        boolean selected = model.selectedBerthIndex() >= 0
                && roster.boatDeck().boats().get(model.selectedBerthIndex()) == boat;
        if (!selected) return lesson(1, "boatLessonTitle", "boatLessonInspect", List.of("boat-list"),
                "boatLessonInspectAction", () -> model.inspect(boat.id()));
        return lesson(1, "boatLessonTitle", boat.airworthy(model.day()) ? "boatLessonReady" : "boatLessonChoice",
                List.of("boat-deck-canvas", "boat-recovery", "boat-material-stock", "page-nav-return"),
                "boatLessonUnderstood", roster.campaignLessons()::reviewBoatRecovery);
    }

    /** The wreck in the first berth if Second Wind brought one, otherwise an empty berth to commission. */
    private static PreparationGuide.Lesson firstLift(MarineRoster roster, BoatDeckViewModel model) {
        boolean wreck = roster.boatDeck().wreckInBerth(0);
        boolean building = model.fabricationFocused();
        return lessonText(1, Strings.get(wreck ? "openingCompanyPreparationGuideRepairOurFirstHermes"
                        : "openingCompanyPreparationGuideCommissionOurFirstShuttle"),
                Strings.get(wreck ? "openingCompanyPreparationGuideSecondWindCameWithTwoWreckedHermes"
                        : "openingOfficerInductionGuideOurPeopleNeedAWayDownAnd")
                        + Strings.get(building ? "openingCompanyPreparationGuideCheckTheMaterialBillThenConfirmThe"
                        : "openingCompanyPreparationGuideSelectTheFirstBerthOnTheRight")
                        + (CerberusCompanyStart.depot() != null
                        ? Strings.get("openingCompanyPreparationGuideOurPrepaidStoresAreAvailableWhileDocked") : ""),
                building ? List.of("boat-fabrication-materials", "boat-fabricate", "boat-fabrication-back")
                        : List.of("boat-list"), null, null);
    }

    static long formations(MarineRoster roster) {
        return roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()).count();
    }

    static PreparationGuide.Lesson formations(MarineRoster roster, MarineOpsContext context) {
        if (next(roster) != Step.FORMATIONS) return null;
        if (formations(roster) < 2) return later(lesson(3, "lessonFormationTitle", "lessonFormationFound",
                List.of("barracks-raise-squad"), null, null), roster);
        boolean full = roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()
                && roster.readyCount(s) == MarineSquad.CAPACITY).count() >= 2;
        // Wounded marines keep their billets, so a squad short only by wounded has nothing for Fill Vacancies to
        // fill; sending the player to a disabled button is a dead end, not guidance.
        boolean vacant = roster.squads().stream().anyMatch(s -> !s.reserve() && !s.stationed()
                && roster.vacancies(s) > 0);
        String copy = full ? "lessonFormationAssign" : vacant ? "lessonFormationRecover" : "lessonFormationWounded";
        return later(lesson(3, "lessonFormationTitle", copy,
                vacant || full ? List.of("barracks-command-list", "barracks-formation-board", "barracks-fill-selected",
                        "barracks-missions")
                        : List.of("barracks-command-list", "barracks-formation-board", "barracks-missions"),
                switch (MissionReturn.of(context)) {
                    case MISSIONS -> "lessonHeadquarters";
                    case BRIEFING -> "lessonBriefing";
                    case DEBRIEF -> "lessonDebrief";
                },
                () -> {
                    MissionReturn back = MissionReturn.of(context);
                    context.goTo(back == MissionReturn.MISSIONS ? ScreenId.COMPANY_HQ : back.screen);
                }), roster);
    }

    /**
     * Founding is a full cargo bill, so a company that cannot yet pay it must be able to put the formation lesson
     * down and earn the stores first. The Junction briefing still states its own two-squad requirement.
     */
    private static PreparationGuide.Lesson later(PreparationGuide.Lesson lesson, MarineRoster roster) {
        return new PreparationGuide.Lesson(lesson.milestone(), lesson.title(), lesson.copy(), lesson.targets(),
                lesson.actionLabel(), lesson.action(), lesson.height(),
                () -> roster.campaignLessons().deferFormations((int) formations(roster)));
    }

    /**
     * Deploying on the Junction closes the formation lessons. The briefing itself says nothing: the warning
     * lamps beside Deploy already show a short or wounded force.
     */
    static void deployed(Mission mission, MarineRoster roster) {
        if (!SecondSquadPreparation.relevant(mission, roster) || next(roster) != Step.FORMATIONS) return;
        roster.campaignLessons().observePreparation(true, true);
        roster.acknowledgeSecondSquadPreparation();
    }

    static PreparationGuide.Lesson lesson(int step, String title, String copy, List<String> targets,
                                           String action, Runnable run) {
        return lessonText(step, LessonCopy.text(title), LessonCopy.text(copy), targets,
                action == null ? null : LessonCopy.text(action), run);
    }

    /** The same lesson in a callout {@code height} tall, for a screen with little room to spare. */
    static PreparationGuide.Lesson lesson(int step, String title, String copy, List<String> targets,
                                           String action, Runnable run, int height) {
        PreparationGuide.Lesson lesson = lesson(step, title, copy, targets, action, run);
        return new PreparationGuide.Lesson(lesson.milestone(), lesson.title(), lesson.copy(), lesson.targets(),
                lesson.actionLabel(), lesson.action(), height, lesson.later());
    }

    private static PreparationGuide.Lesson lessonText(int step, String title, String copy, List<String> targets,
                                                       String action, Runnable run) {
        var interactive = new ArrayList<>(targets);
        // Travel, resupply, other work and mission back remain available; leaving is not completion.
        interactive.addAll(List.of("marine-ops-page-nav", "mission-briefing-back", "mission-prepare-barracks",
                "mission-prepare-armory", "mission-assign", "mission-deploy", "company-hq-board", "barracks-flow-actions"));
        return new PreparationGuide.Lesson(step, title, copy, interactive, action, run, 280);
    }
}
