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
    private Pending started;

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
            if (event.audience() != StoryBeatLog.Audience.PLAYER || missionId == null) continue;
            Line line = catalog.find(missionId, event.id());
            if (line == null || ended && !line.terminal()) continue;
            superseded.addAll(line.supersedes());
            if (!superseded.contains(line.event()))
                pending.add(new Pending(line, clock + line.expiresSeconds()));
        }
        pending.removeIf(p -> ended && !p.line().terminal() || p.expiresAt() <= clock || superseded.contains(p.line().event()));
    }

    public void clearPending() { pending.clear(); started = null; }

    public boolean hasPending() { return !pending.isEmpty(); }

    /** Caller waits for the current mission line to finish; squad chatter cannot delay mission speech. */
    public Line poll() {
        return poll(null);
    }

    /** Emergency speech may break routine traffic; silent reading yields to recorded speech. */
    public Line poll(Line current) {
        Pending next = null;
        for (Pending candidate : pending) {
            Line line = candidate.line();
            if (current != null && !(line.voiced() && (!current.voiced()
                    || line.interrupts() && line.priority() > current.priority()
                    && (!current.interrupts() || line.supersedes().contains(current.event()))))) continue;
            if (next == null || candidate.line().priority() > next.line().priority()) next = candidate;
        }
        if (next == null) return null;
        pending.remove(next);
        // Resume an interrupted instruction only within its original relevance window.
        if (current != null && started != null && started.line() == current
                && started.expiresAt() > clock && !superseded.contains(current.event())
                && (!ended || current.terminal())) pending.add(started);
        started = next;
        return next.line();
    }
}
