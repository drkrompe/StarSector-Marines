package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Resumable guidance derived from actual company work, independent of an entry screen. */
final class CampaignLessons {
    enum Step { NONE, REFIT, GANTRY, INVESTMENTS, FORMATIONS }
    private CampaignLessons() { }

    static Step next(MarineRoster roster) {
        if (roster == null || roster.officerInductionRequired()
                || !roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)) return Step.NONE;
        if (FirstRefit.available(roster)) return Step.REFIT;
        if (!roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) return Step.NONE;
        if (roster.mechBay().mechById(WaylineHound.ID) != null
                && roster.mechBay().labLesson() > 0 && roster.mechBay().labLesson() < 5) return Step.GANTRY;
        if (!roster.campaignLessons().investmentsReviewed()) return Step.INVESTMENTS;
        return roster.campaignLessons().junctionPrepared() ? Step.NONE : Step.FORMATIONS;
    }

    static PreparationGuide.Lesson handoff(MarineRoster roster, MarineOpsContext context, ScreenId host) {
        if (context.getSelectedMission() != null && context.getSelectedMission().source.isDebug()) return null;
        return switch (next(roster)) {
            case REFIT -> lesson(0, "lessonRefitTitle", "lessonRefitHandoff", List.of("barracks-equipment"),
                    "lessonEquipment", () -> context.openFleetArmoryWorkspaceFrom(host));
            case GANTRY -> lesson(1, "lessonGantryTitle", "lessonGantryHandoff", List.of("page-nav-mech-lab"),
                    "lessonMechLab", () -> context.goTo(ScreenId.MECH_LAB));
            case INVESTMENTS -> lesson(2, "lessonInvestmentsTitle", "lessonInvestmentsCopy",
                    List.of("page-nav-boats", "page-nav-engineering"), "lessonContinue",
                    roster.campaignLessons()::reviewInvestments);
            case FORMATIONS -> host == ScreenId.BARRACKS ? null : lesson(3,
                    "lessonFormationTitle", "lessonFormationHandoff", List.of("page-nav-barracks"),
                    "lessonBarracks", () -> context.goTo(ScreenId.BARRACKS));
            default -> null;
        };
    }

    static long formations(MarineRoster roster) {
        return roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()).count();
    }

    static PreparationGuide.Lesson formations(MarineRoster roster, MarineOpsContext context,
                                                boolean equipment, Runnable found) {
        if (next(roster) != Step.FORMATIONS) return null;
        if (formations(roster) < 2) return lesson(3, "lessonFormationTitle", "lessonFormationFound",
                List.of(equipment ? "found-squad" : "barracks-raise-squad"),
                equipment ? null : "lessonEquipment", equipment ? null : found);
        if (equipment) return lesson(3, "lessonFormationTitle", "lessonFormationAssign",
                List.of("page-nav-barracks"), "lessonBarracks", () -> context.goTo(ScreenId.BARRACKS));
        boolean full = roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()
                && roster.readyCount(s) == MarineSquad.CAPACITY).count() >= 2;
        return lesson(3, "lessonFormationTitle", full ? "lessonFormationAssign" : "lessonFormationRecover",
                List.of("barracks-command-list", "barracks-formation-board", "barracks-reinforce", "barracks-missions"),
                context.getSelectedMission() == null ? "lessonHeadquarters" : "lessonBriefing",
                () -> context.goTo(context.getSelectedMission() == null ? ScreenId.COMPANY_HQ : ScreenId.BRIEFING));
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
