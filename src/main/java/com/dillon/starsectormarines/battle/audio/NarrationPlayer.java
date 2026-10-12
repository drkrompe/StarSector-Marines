package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.WaylineNarration.Line;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;

import java.util.function.Function;

/** One owned sound handle with a subtitle lifetime even when audio is unavailable or muted. */
public final class NarrationPlayer {
    private final Function<Line, SoundAPI> play;
    private SoundAPI sound;
    private Line current;
    private float remaining;

    public NarrationPlayer() {
        this(line -> {
            try {
                return Global.getSoundPlayer().playUISound(line.id(), 1f, 1f);
            } catch (RuntimeException failure) {
                Global.getLogger(NarrationPlayer.class).warn("Cannot play narration " + line.id(), failure);
                return null;
            }
        });
    }

    public NarrationPlayer(Function<Line, SoundAPI> play) { this.play = play; }

    public void start(Line line) {
        stop();
        if (line == null) return;
        current = line;
        remaining = line.durationSeconds() + 0.4f;
        sound = line.voiced() ? play.apply(line) : null;
    }

    /** Audio is real-time even when the simulation is paused or accelerated. */
    public void advance(float dt) {
        if (current == null) return;
        remaining -= Math.max(0, dt);
        if (remaining <= 0 && (sound == null || !sound.isPlaying())) stop();
    }

    public boolean busy() { return current != null; }
    public Line current() { return current; }

    public void stop() {
        if (sound != null) sound.stop();
        sound = null;
        current = null;
        remaining = 0;
    }
}
