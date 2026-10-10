package com.dillon.starsectormarines.battle.story;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Battle-local, append-only facts for presentation and evidence. No callbacks run
 * on the simulation thread. Readers retain their own sequence cursor and reset
 * it for a new battle. Audio selection, queueing and playback belong to the host.
 */
public final class StoryBeatLog {
    public enum Audience { PLAYER, OBSERVER_ONLY }
    public record Event(int sequence, int tick, String id, Audience audience) {}
    private final Set<String> fired = new HashSet<>();
    private final List<Event> events = new ArrayList<>();
    private volatile List<Event> published = List.of();

    /** An authored key fires at most once in this attempt, in producer order. */
    public void emitOnce(int tick, String id, Audience audience) {
        if (tick < 0 || id == null || id.isBlank() || audience == null)
            throw new IllegalArgumentException("A story beat needs a tick, stable key and audience");
        if (!events.isEmpty() && tick < events.get(events.size() - 1).tick())
            throw new IllegalArgumentException("Story beats cannot precede already published facts");
        if (!fired.add(id)) return;
        events.add(new Event(events.size() + 1, tick, id, audience));
        published = List.copyOf(events);
    }

    /** Immutable neutral-observer history; never feed this into a faction planner. */
    public List<Event> events() { return published; }

    /**
     * Non-destructive player-facing read. Advance the cursor to the last returned
     * sequence, not by counting events: private events leave sequence gaps.
     */
    public List<Event> playerEventsAfter(int sequence) {
        if (sequence < 0) throw new IllegalArgumentException("Negative story cursor");
        return published.stream().filter(e -> e.sequence() > sequence && e.audience() == Audience.PLAYER).toList();
    }
}
