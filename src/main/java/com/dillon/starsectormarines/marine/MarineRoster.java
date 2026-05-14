package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin collection wrapper around the player's captains. Held by {@link MarineRosterScript}
 * which xstream persists with the campaign.
 */
public class MarineRoster implements Serializable {

    /** Hardcoded cap for phase 2. Phase 2.5 will scale this with player level. */
    private static final int DEFAULT_CAPACITY = 10;

    private final List<MarineCaptain> captains = new ArrayList<>();
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
}
