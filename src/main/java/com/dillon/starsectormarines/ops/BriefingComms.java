package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.NarrationPlayer;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import com.dillon.starsectormarines.ui.retained.UiElement;

/** Briefing-owned conversation state, independent of document lifetime. */
final class BriefingComms {
    record Turn(String key, String speaker, String text, String portrait, WaylineNarration.Line recording) { }
    private final NarrationPlayer player;
    private List<Turn> turns = List.of();
    private int index;
    private boolean visible;
    private boolean pending;
    private boolean started;
    private boolean playbackStarted;
    private String missionId;
    private boolean entered;
    private UiElement mountedDialog;
    private UiElement dialogParent;
    private long turnRevision;
    private long displayedRevision = -1;
    BriefingComms() { this(new NarrationPlayer()); }
    BriefingComms(NarrationPlayer player) { this.player = player; }

    void enter(Mission mission, List<WaylineNarration.Line> recordings) {
        String selectedId = mission == null ? null : mission.id;
        List<Turn> refreshed = conversation(mission, recordings);
        if (entered && Objects.equals(missionId, selectedId)) {
            if (turns.equals(refreshed)) return;
            player.stop(); started = false; playbackStarted = false;
            turns = refreshed;
            index = Math.min(index, Math.max(0, turns.size() - 1));
            turnRevision++;
            if (turns.isEmpty()) stop();
            return;
        }
        entered = true;
        missionId = selectedId;
        stop();
        turns = refreshed;
        turnRevision++;
        pending = !turns.isEmpty();
        index = 0;
    }
    static List<Turn> conversation(Mission mission, List<WaylineNarration.Line> recordings) {
        List<MissionDialogue.Turn> authored = MissionDialogue.turns(mission);
        var result = new ArrayList<Turn>();
        // A legacy whole-briefing recording retains its original text and identity.
        if (recordings.size() == 1 && !recordings.get(0).event().contains(".")) {
            WaylineNarration.Line line = recordings.get(0);
            String key = MissionDialogue.say(line.speaker(), line.text()).speakerKey();
            result.add(new Turn(key, line.speaker(), line.text(), line.portrait(), line));
        } else if (!authored.isEmpty()) {
            for (int i = 0; i < authored.size(); i++) {
                MissionDialogue.Turn turn = authored.get(i);
                WaylineNarration.Line recording = turn.narrationId().isBlank()
                        ? i < recordings.size() ? recordings.get(i) : null
                        : recordings.stream().filter(line -> line.id().equals(turn.narrationId())).findFirst().orElse(null);
                if (turn.narrationId().isBlank() && recording != null
                        && !Strings.resolve(recording.text()).equals(Strings.resolve(turn.text()))) recording = null;
                result.add(new Turn(turn.speakerKey(), turn.speaker(), turn.text(),
                        recording != null && !recording.portrait().isBlank() ? recording.portrait() : MissionDialogue.speakerPortrait(turn.speakerKey()), recording));
            }
        } else for (WaylineNarration.Line line : recordings) {
            String key = MissionDialogue.say(line.speaker(), line.text()).speakerKey();
            result.add(new Turn(key, line.speaker(), line.text(), line.portrait(), line));
        }
        return List.copyOf(result);
    }
    void replay() { turnRevision++; player.stop(); index = 0; started = false; playbackStarted = false; pending = !turns.isEmpty(); visible = false; }
    void advance(float dt, boolean tutorialVisible) {
        if (tutorialVisible) {
            if (visible) { player.stop(); started = false; playbackStarted = false; visible = false; pending = true; }
            return;
        }
        if (pending) { pending = false; visible = true; }
        if (!visible) return;
        Turn turn = current();
        if (!started) {
            started = true;
            if (turn.recording() != null && turn.recording().voiced()) {
                player.start(turn.recording());
                playbackStarted = player.hasSound();
                if (!playbackStarted) player.stop();
            }
        }
        player.advance(dt);
        if (playbackStarted && !player.busy()) next();
    }
    void next() {
        if (!visible) return;
        turnRevision++;
        player.stop(); started = false; playbackStarted = false;
        if (++index >= turns.size()) stop();
    }
    void stop() { player.stop(); visible = false; pending = false; started = false; playbackStarted = false; }
    boolean visible() { return visible; }
    boolean available() { return !turns.isEmpty(); }
    boolean busy() { return player.busy(); }
    Turn current() { return available() ? turns.get(Math.min(index, turns.size() - 1)) : null; }
    WaylineNarration.Line contact() {
        Turn turn = current();
        return turn == null ? null : new WaylineNarration.Line("briefing-contact", "briefing", "briefing", Strings.resolve(turn.text()),
                1, 0, 1, List.of(), turn.speaker(), turn.portrait(), false, false);
    }
    void put(Map<String, Object> props) {
        Turn turn = current();
        props.put("commsVisible", visible);
        props.put("commsText", turn == null ? "" : Strings.resolve(turn.text()));
        props.put("commsProgress", turn == null ? "" : Strings.format("briefingCommsProgress", Math.min(index + 1, turns.size()), turns.size()));
        props.put("commsNextLabel", Strings.get(index + 1 >= turns.size() ? "briefingCommsFinish" : "briefingCommsNext"));
        props.put("commsNextAction", (Runnable) this::next);
        props.put("commsSkipAction", (Runnable) this::stop);
    }
    void update(MarkupInstance markup) {
        var props = new LinkedHashMap<String, Object>();
        put(props);
        UiElement dialog = markup.requireElement("mission-comms-dialog");
        if (dialog != mountedDialog) { mountedDialog = dialog; dialogParent = dialog.parent(); }
        if (!visible && dialog.parent() != null) dialog.parent().remove(dialog);
        if (visible && dialog.parent() == null && dialogParent != null) dialogParent.child(dialog);
        dialog.classed("comms-hidden", !visible);
        Turn turn = current();
        if (turn != null && markup.requireElement("mission-briefing-speaker").parent() != null) {
            markup.requireElement("mission-briefing-speaker-name").text(MissionDialogue.speakerName(turn.speaker()))
                    .style(MissionDialogue.speakerStyle(turn.key()));
            markup.requireElement("mission-briefing-speaker-role").text(MissionDialogue.speakerRole(turn.speaker()));
            markup.requireElement("mission-briefing-speaker-portrait").imageSource(turn.portrait());
        }
        markup.requireElement("mission-comms-text").text((String) props.get("commsText"));
        if (displayedRevision != turnRevision) {
            markup.requireElement("mission-comms-transcript").scrollTop(0);
            displayedRevision = turnRevision;
        }
        markup.requireElement("mission-comms-status").text((String) props.get("commsProgress"));
        markup.requireElement("mission-comms-next").text((String) props.get("commsNextLabel"));
    }
}
