package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.WaylineNarration.Line;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.fs.starfarer.api.SoundAPI;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.*;

class StoryNarrationPlayerTest {
    @Test void terminalSubtitleStartsAtCompletionWithoutRequestingMissingAudio() {
        Line terminal = new Line("done", "chapter", "done", "Installation neutralized.",
                5, 100, 30, List.of(), "KORD", "", false, true);
        var player = new StoryNarrationPlayer(new WaylineNarration(List.of(line("start"), terminal)),
                "chapter", new NarrationPlayer(l -> { fail("Text-only dialogue has no sound lookup"); return null; }), l -> { });
        player.advance(0, List.of(event(1, "start"), event(2, "done")), true, true);
        assertEquals(terminal, player.current());
        player.advance(6, List.of(event(2, "done")), false, true);
        assertNull(player.current());
    }
    private final List<FakeSound> sounds = new ArrayList<>();
    private final NarrationPlayer audio = new NarrationPlayer(line -> {
        FakeSound sound = new FakeSound();
        sounds.add(sound);
        return sound;
    });

    private static Line line(String id, String... supersedes) {
        return new Line(id, "chapter", id, "Subtitle", 4, 10, 30, List.of(supersedes));
    }

    private static StoryBeatLog.Event event(int sequence, String id) {
        return new StoryBeatLog.Event(sequence, sequence, id, StoryBeatLog.Audience.PLAYER);
    }

    @Test void silentInstructionsYieldToRecordedCalls() {
        Line instruction = new Line("instruction", "chapter", "instruction", "Hold here.",
                18, 40, 40, List.of(), "CASTLE", "", false, false);
        Line inbound = line("inbound");
        var player = new StoryNarrationPlayer(new WaylineNarration(List.of(instruction, inbound)),
                "chapter", audio, next -> { });
        player.advance(0, List.of(event(1, "instruction")), false, false);
        player.advance(1, List.of(event(2, "inbound")), false, false);
        assertEquals(inbound, player.current());
        assertEquals(1, sounds.size());
        sounds.get(0).playing = false;
        player.advance(5, List.of(), false, false);
        assertEquals(instruction, player.current(), "Still-relevant reading returns after speech");
    }

    @Test void emergencyInterruptsRoutineSpeechButWaitsForAnotherEmergency() {
        Line instruction = line("instruction");
        Line warning = urgent("warning", 80), hit = urgent("hit", 81);
        var player = new StoryNarrationPlayer(new WaylineNarration(List.of(instruction, warning, hit)),
                "chapter", audio, next -> { });
        player.advance(0, List.of(event(1, "instruction")), false, false);
        player.advance(1, List.of(event(2, "warning")), false, false);
        assertEquals(warning, player.current());
        assertEquals(1, sounds.get(0).stops);
        player.advance(1, List.of(event(3, "hit")), false, false);
        assertEquals(warning, player.current());
        assertEquals(0, sounds.get(1).stops);
        sounds.get(1).playing = false;
        player.advance(4, List.of(), false, false);
        assertEquals(hit, player.current());
        sounds.get(2).playing = false;
        player.advance(5, List.of(), false, false);
        assertEquals(instruction, player.current());
    }

    @Test void shootdownReplacesObsoleteActiveCallWithoutReplayingIt() {
        Line hit = urgent("hit", 81), lost = urgent("lost", 85, "hit");
        var player = new StoryNarrationPlayer(new WaylineNarration(List.of(hit, lost)),
                "chapter", audio, next -> { });
        player.advance(0, List.of(event(1, "hit")), false, false);
        player.advance(1, List.of(event(2, "lost")), false, false);
        assertEquals(lost, player.current());
        assertEquals(1, sounds.get(0).stops);
        sounds.get(1).playing = false;
        player.advance(5, List.of(), false, false);
        assertNull(player.current());
    }

    @Test void interruptedInstructionDoesNotGetAnExtendedExpiry() {
        Line instruction = new Line("instruction", "chapter", "instruction", "Hold here.",
                18, 10, 3, List.of());
        Line warning = urgent("warning", 80);
        var player = new StoryNarrationPlayer(new WaylineNarration(List.of(instruction, warning)),
                "chapter", audio, next -> { });
        player.advance(0, List.of(event(1, "instruction")), false, false);
        player.advance(1, List.of(event(2, "warning")), false, false);
        sounds.get(1).playing = false;
        player.advance(5, List.of(), false, false);
        assertNull(player.current());
    }

    private static Line urgent(String id, int priority, String... supersedes) {
        return new Line(id, "chapter", id, "Emergency", 4, priority, 15, List.of(supersedes),
                "KESTREL", "", true, false, true);
    }

    @Test void thunderAmbushCallsReachTheChannelDuringDispatchInstructions() throws Exception {
        var catalog = WaylineNarration.parse(new JSONObject(
                Files.readString(Path.of("mod", WaylineNarration.PATH))));
        String chapter = "story_supply_route_thunder_run", prefix = "supply_route.thunder_run.";
        var player = new StoryNarrationPlayer(catalog, chapter, new NarrationPlayer(line -> null), next -> { });
        player.advance(0, List.of(event(1, prefix + "defend_dispatch"),
                event(2, prefix + "gunship_inbound")), false, false);
        assertEquals(prefix + "gunship_inbound", player.current().event());
        player.advance(6, List.of(), false, false);
        assertEquals(prefix + "defend_dispatch", player.current().event());
        player.advance(2, List.of(event(3, prefix + "gunship_pass_committed")), false, false);
        player.advance(9, List.of(event(4, prefix + "gunship_missile_warning")), false, false);
        assertEquals(prefix + "gunship_missile_warning", player.current().event());
        player.advance(2, List.of(event(5, prefix + "gunship_hit")), false, false);
        assertEquals(prefix + "gunship_missile_warning", player.current().event());
        player.advance(1.3f, List.of(), false, false);
        assertEquals(prefix + "gunship_hit", player.current().event());
        player.advance(1, List.of(event(6, prefix + "gunship_lost")), false, false);
        assertEquals(prefix + "gunship_lost", player.current().event());
        player.advance(6, List.of(), false, false);
        assertEquals(prefix + "defend_dispatch", player.current().event());
    }

    @Test
    void landingWaitsForTheStartedInstructionToFinishWithoutCuttingItOff() {
        Line start = line("start"), landed = line("landed", "start");
        StoryNarrationPlayer player = new StoryNarrationPlayer(
                new WaylineNarration(List.of(start, landed)), "chapter", audio, next -> { });
        player.advance(0, List.of(event(1, "start")), false, false);
        player.advance(1, List.of(event(2, "landed")), false, false);
        assertEquals(start, player.current());
        assertEquals(0, sounds.get(0).stops);
        player.advance(4, List.of(), false, false);
        assertEquals(start, player.current(), "Engine playback outliving the declared duration must finish too");
        assertEquals(1, sounds.size());
        sounds.get(0).playing = false;
        player.advance(0, List.of(), false, false);
        assertEquals(landed, player.current());
        assertEquals(2, sounds.size());
    }

    @Test
    void missionSpeechTakesOverChatterEvenWhileItsCooldownIsClosed() {
        BattleRadioChatter chatter = new BattleRadioChatter();
        chatter.reserveVoice(60);
        FakeSound squad = new FakeSound();
        Line start = line("start");
        StoryNarrationPlayer player = new StoryNarrationPlayer(
                new WaylineNarration(List.of(start)), "chapter", new NarrationPlayer(line -> {
                    assertFalse(squad.playing, "Chatter must stop before mission playback starts");
                    return new FakeSound();
                }), next -> squad.stop());
        assertFalse(chatter.voiceAvailable());
        player.advance(0, List.of(event(1, "start")), false, false);
        assertEquals(start, player.current());
        assertEquals(1, squad.stops);
        assertFalse(chatter.voiceAvailable());
    }

    @Test
    void completionKeepsTheCurrentSentenceAndDiscardsPendingInstructions() {
        Line start = line("start"), next = line("next");
        StoryNarrationPlayer player = new StoryNarrationPlayer(
                new WaylineNarration(List.of(start, next)), "chapter", audio, line -> { });
        player.advance(0, List.of(event(1, "start")), false, false);
        player.advance(1, List.of(event(2, "next")), false, false);
        player.advance(1, List.of(), false, true);
        assertEquals(start, player.current());
        assertEquals(0, sounds.get(0).stops);
        sounds.get(0).playing = false;
        player.advance(3, List.of(), false, true);
        assertFalse(player.busy());
        assertEquals(1, sounds.size());
    }

    @Test
    void pauseLetsActiveAudioFinishAndHoldsTheNextLineUntilResume() {
        Line start = line("start"), next = line("next");
        StoryNarrationPlayer player = new StoryNarrationPlayer(
                new WaylineNarration(List.of(start, next)), "chapter", audio, line -> { });
        player.advance(0, List.of(event(1, "start")), true, false);
        assertFalse(player.busy());
        player.advance(0, List.of(), false, false);
        player.advance(1, List.of(event(2, "next")), true, false);
        assertEquals(0, sounds.get(0).stops);
        sounds.get(0).playing = false;
        player.advance(4, List.of(), true, false);
        assertFalse(player.busy());
        player.advance(0, List.of(), false, false);
        assertEquals(next, player.current());
    }

    @Test
    void leavingTheScreenStillStopsSpeechAndDoesNotReplayConsumedEvents() {
        Line start = line("start"), next = line("next");
        StoryNarrationPlayer player = new StoryNarrationPlayer(
                new WaylineNarration(List.of(start, next)), "chapter", audio, line -> { });
        List<StoryBeatLog.Event> events = List.of(event(1, "start"), event(2, "next"));
        player.advance(0, events, false, false);
        player.stop();
        assertEquals(1, sounds.get(0).stops);
        assertFalse(player.busy());
        player.advance(0, events, false, false);
        assertFalse(player.busy());
        assertEquals(1, sounds.size());
    }

    private static final class FakeSound implements SoundAPI {
        boolean playing = true;
        int stops;
        public void stop() { playing = false; stops++; }
        public boolean isPlaying() { return playing; }
        public void setVolume(float volume) { }
        public void setPitch(float pitch) { }
        public void setLocation(float x, float y) { }
    }
}
