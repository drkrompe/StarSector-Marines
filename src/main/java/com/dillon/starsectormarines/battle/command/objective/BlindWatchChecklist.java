package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.setup.BlindWatchSequence;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Blind Watch's checklist row beside the three charge sites' own (S1 Mast, S2 Uplink, S3 Dish field): the uplink's
 * warning to Garnir, its bar the share sent, with the standing mast marked while it spots for the mortar. The riggers
 * and the false schedule are side objectives, listed in the Optional section by the toolkit.
 */
public final class BlindWatchChecklist {
    private BlindWatchChecklist() { }

    public static List<Item> items(BlindWatchSequence sequence) {
        return List.of(warning(sequence));
    }

    private static Item warning(BlindWatchSequence sequence) {
        BlindWatchProgress p = sequence.progress();
        String percent = percent(p.sentShare());
        String phase = switch (p.warning()) {
            case WAITING -> Strings.get("blindWatchHudWarningWaiting");
            case SENDING -> Strings.format("blindWatchHudWarningSending", percent);
            case SENT -> Strings.get("blindWatchHudWarningSent");
            case STOPPED -> p.sentShare() > 0f ? Strings.format("blindWatchHudWarningStopped", percent)
                    : Strings.get("blindWatchHudWarningSilenced");
            case FALSE_SCHEDULE -> Strings.get("blindWatchHudWarningFalse");
        };
        State state = switch (p.warning()) {
            case WAITING, SENDING -> State.ACTIVE;
            case SENT -> State.FAILED;
            case STOPPED, FALSE_SCHEDULE -> State.COMPLETE;
        };
        Cell uplink = sequence.sites().uplink();
        List<Marker> markers = new ArrayList<>();
        markers.add(new Marker("blind-watch-uplink", Strings.get("blindWatchHudUplink"), uplink.x(), uplink.y(), phase, state));
        if (sequence.mastUp()) {
            Cell mast = sequence.sites().mast();
            markers.add(new Marker("blind-watch-mast", Strings.get("blindWatchHudMast"), mast.x(), mast.y(),
                    Strings.get("blindWatchHudMastStatus"), State.ACTIVE));
        }
        String timer = p.warning() == BlindWatchProgress.Warning.SENDING ? clockText(p.warningRemaining()) : "";
        return new Item("blind-watch-1-warning", Strings.get("blindWatchHudWarningName"), state, phase,
                Strings.get("blindWatchHudWarningSteps"), Strings.get(state == State.ACTIVE
                        ? "blindWatchHudWarningInstruction" : "blindWatchHudWarningDone"),
                timer, p.sentShare(), "", markers, "blind-watch-uplink");
    }

    static String clockText(float seconds) {
        int whole = (int) Math.ceil(seconds);
        return Strings.format("blindWatchHudWarningTime", whole / 60, String.format(Locale.ROOT, "%02d", whole % 60));
    }

    private static String percent(float share) { return Math.round(Math.max(0f, Math.min(1f, share)) * 100f) + "%"; }
}
