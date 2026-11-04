package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRoster;
import java.util.ArrayList;
import java.util.List;

/** Resumable guidance derived from actual company work, independent of an entry screen. */
final class CampaignLessons {
    enum Step { NONE, FIRST_LIFT, REFIT, GANTRY }
    private CampaignLessons() { }

    static Step next(MarineRoster roster) {
        if (roster == null || roster.officerInductionRequired()) return Step.NONE;
        if (!roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)) return Step.NONE;
        if (firstLiftPending(roster)) return Step.FIRST_LIFT;
        if (FirstRefit.available(roster)) return Step.REFIT;
        if (!roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) return Step.NONE;
        return roster.mechBay().mechById(WaylineHound.ID) != null
                && roster.mechBay().labLesson() > 0 && roster.mechBay().labLesson() < 5 ? Step.GANTRY : Step.NONE;
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
            case REFIT -> lesson(0, "lessonRefitTitle", "lessonRefitHandoff", List.of("barracks-equipment"),
                    "lessonEquipment", () -> context.openFleetArmoryWorkspaceFrom(host));
            case GANTRY -> lesson(1, "lessonGantryTitle", "lessonGantryHandoff", List.of("page-nav-mech-lab"),
                    "lessonMechLab", () -> context.goTo(ScreenId.MECH_LAB));
            default -> null;
        };
    }

    static PreparationGuide.Lesson boats(MarineRoster roster, BoatDeckViewModel model) {
        return next(roster) == Step.FIRST_LIFT ? firstLift(roster, model) : null;
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
