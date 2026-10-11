package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.WaylineNarration.Line;
import com.fs.starfarer.api.SoundAPI;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NarrationPlayerTest {
    private static final Line LINE = new Line("sound", "chapter", "event", "Text", 5, 10, 10, List.of());

    @Test
    void mutedOrMissingAudioRetainsSubtitleForItsRecordedDuration() {
        NarrationPlayer player = new NarrationPlayer(line -> null);
        player.start(LINE);
        player.advance(5);
        assertEquals(LINE, player.current());
        player.advance(1);
        assertFalse(player.busy());
    }

    @Test
    void replayAndLeavingScreenStopTheOwnedHandle() {
        FakeSound first = new FakeSound();
        FakeSound second = new FakeSound();
        int[] calls = {0};
        NarrationPlayer player = new NarrationPlayer(line -> calls[0]++ == 0 ? first : second);
        player.start(LINE);
        player.start(LINE);
        assertFalse(first.playing);
        assertTrue(second.playing);
        player.advance(10);
        assertTrue(player.busy()); // engine still owns a playing clip
        second.playing = false;
        player.advance(0);
        assertFalse(player.busy());
        player.start(LINE);
        player.stop();
        assertFalse(second.playing);
        assertNull(player.current());
    }

    private static final class FakeSound implements SoundAPI {
        boolean playing = true;
        public void stop() { playing = false; }
        public boolean isPlaying() { return playing; }
        public void setVolume(float volume) { }
        public void setPitch(float pitch) { }
        public void setLocation(float x, float y) { }
    }
}
