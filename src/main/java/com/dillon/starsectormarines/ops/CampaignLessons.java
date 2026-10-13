package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Resumable guidance derived from actual company work, independent of an entry screen. */
final class CampaignLessons {
    enum Step { NONE, BOAT_RECOVERY, REFIT, GANTRY, INVESTMENTS, FORMATIONS }
    private CampaignLessons() { }

    static Step next(MarineRoster roster) {
        if (roster == null || roster.officerInductionRequired()) return Step.NONE;
        if (roster.campaignLessons().boatRecoveryPending()) return Step.BOAT_RECOVERY;
        if (!roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)) return Step.NONE;
        if (FirstRefit.available(roster)) return Step.REFIT;
        if (!roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) return Step.NONE;
        if (roster.mechBay().mechById(WaylineHound.ID) != null
                && roster.mechBay().labLesson() > 0 && roster.mechBay().labLesson() < 5) return Step.GANTRY;
        if (!roster.campaignLessons().investmentsReviewed()) return Step.INVESTMENTS;
        if (roster.campaignLessons().junctionPrepared()) return Step.NONE;
        return roster.campaignLessons().formationsDeferred((int) formations(roster)) ? Step.NONE : Step.FORMATIONS;
    }

    static PreparationGuide.Lesson handoff(MarineRoster roster, MarineOpsContext context, ScreenId host) {
        if (context.getSelectedMission() != null && context.getSelectedMission().source.isDebug()) return null;
        return switch (next(roster)) {
            case BOAT_RECOVERY -> lesson(1, "boatLessonTitle", "boatLessonHandoff",
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

    static long formations(MarineRoster roster) {
        return roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()).count();
    }

    static PreparationGuide.Lesson formations(MarineRoster roster, MarineOpsContext context,
                                                boolean equipment, Runnable found) {
        if (next(roster) != Step.FORMATIONS) return null;
        if (formations(roster) < 2) return later(lesson(3, "lessonFormationTitle", "lessonFormationFound",
                List.of(equipment ? "found-squad" : "barracks-raise-squad"),
                equipment ? null : "lessonEquipment", equipment ? null : found), roster);
        if (equipment) return later(lesson(3, "lessonFormationTitle", "lessonFormationAssign",
                List.of("page-nav-barracks"), "lessonBarracks", () -> context.goTo(ScreenId.BARRACKS)), roster);
        boolean full = roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()
                && roster.readyCount(s) == MarineSquad.CAPACITY).count() >= 2;
        // Wounded marines keep their billets, so a squad short only by wounded has nothing for Fill Vacancies to
        // fill; sending the player to a disabled button is a dead end, not guidance.
        boolean vacant = roster.squads().stream().anyMatch(s -> !s.reserve() && !s.stationed()
                && roster.vacancies(s) > 0);
        String copy = full ? "lessonFormationAssign" : vacant ? "lessonFormationRecover" : "lessonFormationWounded";
        return later(lesson(3, "lessonFormationTitle", copy,
                vacant || full ? List.of("barracks-command-list", "barracks-formation-board", "barracks-reinforce",
                        "barracks-missions")
                        : List.of("barracks-command-list", "barracks-formation-board", "barracks-missions"),
                context.getSelectedMission() == null ? "lessonHeadquarters" : "lessonBriefing",
                () -> context.goTo(context.getSelectedMission() == null ? ScreenId.COMPANY_HQ : ScreenId.BRIEFING)), roster);
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

    static PreparationGuide.Lesson briefing(Mission mission, MarineRoster roster, Set<String> selected,
                                              boolean supportAvailable, boolean supportSelected) {
        if (!SecondSquadPreparation.relevant(mission, roster) || next(roster) != Step.FORMATIONS) return null;
        boolean ready = SupplyRouteCampaign.forceReady(roster, selected);
        String copy = !ready ? "lessonJunctionSquads" : supportAvailable && !supportSelected ? "lessonJunctionSupport"
                : supportAvailable ? "lessonJunctionReady" : "lessonJunctionWithoutMech";
        boolean prepared = ready && (!supportAvailable || supportSelected);
        return lesson(3, "lessonJunctionTitle", copy,
                List.of("mission-assign", "mission-power-list", "mission-transport-readout"),
                prepared ? "lessonReady" : null, prepared
                        ? () -> { roster.campaignLessons().observePreparation(true, true); roster.acknowledgeSecondSquadPreparation(); } : null);
    }

    static PreparationGuide.Lesson lesson(int step, String title, String copy, List<String> targets,
                                           String action, Runnable run) {
        var interactive = new ArrayList<>(targets);
        // Travel, resupply, other work and mission back remain available; leaving is not completion.
        interactive.addAll(List.of("marine-ops-page-nav", "mission-briefing-back", "mission-prepare-barracks",
                "mission-prepare-armory", "mission-assign", "mission-deploy", "company-hq-board", "barracks-flow-actions"));
        return new PreparationGuide.Lesson(step, LessonCopy.text(title), LessonCopy.text(copy),
                interactive, action == null ? null : LessonCopy.text(action), run, 280);
    }
}
