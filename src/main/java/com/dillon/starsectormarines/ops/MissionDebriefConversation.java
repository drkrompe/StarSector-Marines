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
        if (firstContract(outcome)) return "FIRST CONTRACT / CASTLE & MARA";
        return coldAccount(outcome) || blindWatch(outcome) ? "AFTER ACTION / CASTLE & MARA" : "RECOVERY AGREEMENT / TRANSCRIPT";
    }

    static List<WaylineNarration.Line> lines(MissionOutcome outcome, WaylineNarration catalog) {
        if (firstContract(outcome)) return List.of(
                firstContractLine("receipt", true, "Payment's in. I checked twice. Our first Wayline contract, Captain. Someone's paid us to do what we said we would."),
                firstContractLine("names", false, "The drivers asked who'd answered the call. I gave them your company's name. They'll remember it."),
                firstContractLine("cost", true, outcome.marinesLost > 0
                        ? "We've got names to take off the roll. I'll see to those before I start counting what we can afford."
                        : "I'll put crew pay and supplies first. It's good to have a receipt that isn't for something we had to sell."),
                firstContractLine("next", false, "Put your people in order. When you're ready, come back and we'll talk about the depot. I'd like this to be the start of something."));
        if (blindWatch(outcome)) return List.of("initiative", "ready")
                .stream().map(beat -> catalog.find("story_corvus_blind_watch", "debrief.blind_watch." + beat))
                .filter(Objects::nonNull).toList();
        if (!coldAccount(outcome)) return HoundSalvageDebrief.lines(outcome, catalog);
        return List.of("orders", "consideration", "one_ship", "payroll", "way_out", "debt")
                .stream().map(beat -> catalog.find("story_corvus_cold_account", "debrief.cold_account." + beat))
                .filter(Objects::nonNull).toList();
    }

    private static boolean firstContract(MissionOutcome outcome) {
        return outcome != null && outcome.victory && WaylineArc.chapter(outcome) == OpeningOperationKind.RELIEF;
    }

    /** Written correspondence has no recording; keep it readable without offering silent playback. */
    private static WaylineNarration.Line firstContractLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("first-contract-" + beat, OpeningOperationKind.RELIEF.missionId,
                "debrief.relief." + beat, text, 10f, 10, 15f, List.of(),
                castle ? "CASTLE / COMPANY OPERATIONS" : "MARA VENN / WAYLINE",
                castle ? CompanyManagementGuide.portrait() : OpeningBroker.PORTRAIT, false, false);
    }

    static List<WaylineNarration.Line> recordings(MissionOutcome outcome, WaylineNarration catalog) {
        return lines(outcome, catalog).stream().filter(WaylineNarration.Line::voiced).toList();
    }
}
