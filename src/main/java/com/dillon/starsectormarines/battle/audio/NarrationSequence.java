package com.dillon.starsectormarines.battle.audio;

import java.util.List;

/** An explicitly replayable conversation; each recorded sentence finishes before the next starts. */
public final class NarrationSequence {
    private final NarrationPlayer player;
    private List<WaylineNarration.Line> lines = List.of();
    private int next;
    private boolean running;

    public NarrationSequence() { this(new NarrationPlayer()); }
    NarrationSequence(NarrationPlayer player) { this.player = player; }

    public void start(List<WaylineNarration.Line> lines) {
        stop();
        this.lines = List.copyOf(lines);
        running = !lines.isEmpty();
    }

    public void advance(float dt) {
        player.advance(dt);
        if (!running || player.busy()) return;
        if (next < lines.size()) player.start(lines.get(next++));
        else running = false;
    }

    public WaylineNarration.Line current() { return player.current(); }
    public boolean busy() { return running; }
    public void stop() {
        player.stop();
        running = false;
        next = 0;
    }
}
