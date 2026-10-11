package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.WaylineNarration.Line;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;

import java.util.List;
import java.util.function.Consumer;

/** Mission speech finishes each started line and takes precedence over incidental radio chatter. */
public final class StoryNarrationPlayer {
    private final StoryNarrationQueue queue;
    private final NarrationPlayer player;
    private final Consumer<Line> beforeStart;

    public StoryNarrationPlayer(WaylineNarration catalog, String missionId, Consumer<Line> beforeStart) {
        this(catalog, missionId, new NarrationPlayer(), beforeStart);
    }

    StoryNarrationPlayer(WaylineNarration catalog, String missionId,
                         NarrationPlayer player, Consumer<Line> beforeStart) {
        queue = new StoryNarrationQueue(catalog, missionId);
        this.player = player;
        this.beforeStart = beforeStart;
    }

    public int cursor() { return queue.cursor(); }
    public Line current() { return player.current(); }
    public boolean busy() { return player.busy(); }

    public void advance(float realSeconds, List<StoryBeatLog.Event> events, boolean paused, boolean complete) {
        player.advance(realSeconds);
        queue.advance(realSeconds, events, complete);
        // Supersession and completion affect only unspoken instructions. Never stop a sentence here.
        if (paused || complete || player.busy()) return;
        Line next = queue.poll();
        if (next == null) return;
        beforeStart.accept(next);
        player.start(next);
    }

    /** Leaving the battle explicitly ends owned playback and discards queued speech. */
    public void stop() {
        player.stop();
        queue.clearPending();
    }
}
