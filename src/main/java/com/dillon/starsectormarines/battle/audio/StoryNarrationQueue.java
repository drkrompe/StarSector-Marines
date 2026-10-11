package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.audio.WaylineNarration.Line;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Real-time presentation queue; it has no simulation or sound-engine dependencies. */
public final class StoryNarrationQueue {
    private final WaylineNarration catalog;
    private final String missionId;
    private final List<Pending> pending = new ArrayList<>();
    private final Set<String> superseded = new HashSet<>();
    private int cursor;
    private float clock;
    private boolean ended;

    private record Pending(Line line, float expiresAt) {}

    public StoryNarrationQueue(WaylineNarration catalog, String missionId) {
        this.catalog = catalog;
        this.missionId = missionId;
    }

    public int cursor() { return cursor; }

    public void advance(float realSeconds, List<StoryBeatLog.Event> events, boolean complete) {
        clock += Math.max(0, realSeconds);
        ended |= complete;
        for (StoryBeatLog.Event event : events) {
            if (event.sequence() <= cursor) continue;
            cursor = event.sequence();
            if (ended || event.audience() != StoryBeatLog.Audience.PLAYER || missionId == null) continue;
            Line line = catalog.find(missionId, event.id());
            if (line == null) continue;
            superseded.addAll(line.supersedes());
            if (!superseded.contains(line.event()))
                pending.add(new Pending(line, clock + line.expiresSeconds()));
        }
        pending.removeIf(p -> ended || p.expiresAt() <= clock || superseded.contains(p.line().event()));
    }

    public void clearPending() { pending.clear(); }

    public boolean hasPending() { return !pending.isEmpty(); }

    /** Caller waits for the current mission line to finish; squad chatter cannot delay mission speech. */
    public Line poll() {
        Pending next = null;
        for (Pending candidate : pending)
            if (next == null || candidate.line().priority() > next.line().priority()) next = candidate;
        if (next == null) return null;
        pending.remove(next);
        return next.line();
    }
}
