package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import java.util.List;
import java.util.Objects;

/** Read-only conversations selected from the settled mission, never reward or completion authority. */
final class MissionDebriefConversation {
    private MissionDebriefConversation() { }

    static boolean coldAccount(MissionOutcome outcome) {
        return outcome != null && outcome.victory
                && (outcome.missionSource == MissionSource.STORY
                    && CorvusWaylineProgress.forMission(outcome.missionId) == CorvusChapter.COLD_ACCOUNT
                || DebugMissionCatalog.scenario(outcome.missionId, outcome.missionSource)
                    == DebugMissionCatalog.Scenario.COLD_ACCOUNT);
    }

    static boolean blindWatch(MissionOutcome outcome) {
        return outcome != null && outcome.victory && outcome.missionSource == MissionSource.STORY
                && CorvusWaylineProgress.forMission(outcome.missionId) == CorvusChapter.BLIND_WATCH;
    }

    static String title(MissionOutcome outcome) {
        return coldAccount(outcome) || blindWatch(outcome) ? "AFTER ACTION / CASTLE & MARA" : "RECOVERY AGREEMENT / TRANSCRIPT";
    }

    static List<WaylineNarration.Line> lines(MissionOutcome outcome, WaylineNarration catalog) {
        if (blindWatch(outcome)) return List.of("initiative", "ready")
                .stream().map(beat -> catalog.find("story_corvus_blind_watch", "debrief.blind_watch." + beat))
                .filter(Objects::nonNull).toList();
        if (!coldAccount(outcome)) return HoundSalvageDebrief.lines(outcome, catalog);
        return List.of("orders", "consideration", "one_ship", "payroll", "way_out", "debt")
                .stream().map(beat -> catalog.find("story_corvus_cold_account", "debrief.cold_account." + beat))
                .filter(Objects::nonNull).toList();
    }
}
