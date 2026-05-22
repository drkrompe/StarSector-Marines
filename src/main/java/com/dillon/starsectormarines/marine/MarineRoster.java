package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thin collection wrapper around the player's captains. Held by {@link MarineRosterScript}
 * which xstream persists with the campaign.
 *
 * <p>Also tracks {@link #completedStoryIds} — the set of one-shot story mission ids the
 * player has already cleared on this save. Lives here (rather than a new top-level
 * script) because xstream already walks the roster graph for the captain list; adding
 * one Set ride-shares for free.
 */
public class MarineRoster implements Serializable {

    /** Hardcoded cap for phase 2. Phase 2.5 will scale this with player level. */
    private static final int DEFAULT_CAPACITY = 10;

    private final List<MarineCaptain> captains = new ArrayList<>();
    // Non-final so xstream's readResolve can backfill on legacy saves that
    // predate this field (xstream bypasses the constructor on deserialization,
    // so the inline initializer doesn't run for old save streams).
    private Set<String> completedStoryIds = new HashSet<>();
    private int capacity = DEFAULT_CAPACITY;

    public void add(MarineCaptain captain) {
        captains.add(captain);
    }

    public boolean removeById(String id) {
        return captains.removeIf(c -> c.id().equals(id));
    }

    public MarineCaptain byId(String id) {
        for (MarineCaptain c : captains) {
            if (c.id().equals(id)) return c;
        }
        return null;
    }

    public List<MarineCaptain> all() {
        return Collections.unmodifiableList(captains);
    }

    public List<MarineCaptain> active() {
        List<MarineCaptain> result = new ArrayList<>();
        for (MarineCaptain c : captains) {
            if (c.status() == Status.ACTIVE) result.add(c);
        }
        return result;
    }

    /**
     * Same predicate as {@link #active()} but counts in place without
     * allocating an intermediate list. Used by per-frame readers
     * (e.g. {@code OfficerMoodReader.currentMood}) where the list itself
     * isn't needed.
     */
    public int activeCount() {
        int n = 0;
        for (MarineCaptain c : captains) {
            if (c.status() == Status.ACTIVE) n++;
        }
        return n;
    }

    public int size() {
        return captains.size();
    }

    public int capacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean hasRoom() {
        return captains.size() < capacity;
    }

    public boolean hasCompletedStory(String storyId) {
        return completedStoryIds.contains(storyId);
    }

    public void markStoryComplete(String storyId) {
        if (storyId != null) completedStoryIds.add(storyId);
    }

    public Set<String> completedStoryIds() {
        return Collections.unmodifiableSet(completedStoryIds);
    }

    /**
     * Backfill for saves created before {@link #completedStoryIds} existed —
     * xstream calls readResolve after building the object graph; an unset
     * Set field arrives as null and would NPE on first use.
     */
    private Object readResolve() {
        if (completedStoryIds == null) completedStoryIds = new HashSet<>();
        return this;
    }
}
