package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.WaylineNarration.Line;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.fs.starfarer.api.SoundAPI;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoryNarrationPlayerTest {
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
