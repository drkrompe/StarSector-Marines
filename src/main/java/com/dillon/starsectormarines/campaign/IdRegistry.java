package com.dillon.starsectormarines.campaign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interns vanilla string ids (faction id, industry id) into stable {@code int}
 * slots so {@link CampaignState}'s SoA tables can keep them as primitives.
 *
 * <p>Insertion-order preserved across save/load: ids are persisted via xstream
 * as part of {@link CampaignState}, so a slot assigned on first encounter at
 * game-init keeps the same value forever. Newly-discovered ids (e.g. a mod
 * adds a faction between save and load) get appended; existing slots never
 * shift.
 */
public final class IdRegistry implements Serializable {

    private final List<String> idsByIndex = new ArrayList<>();
    private final Map<String, Integer> indexByid = new HashMap<>();

    /** Returns the existing slot for {@code id}, or assigns a new one. {@code null} → -1. */
    public int intern(String id) {
        if (id == null) return -1;
        Integer existing = indexByid.get(id);
        if (existing != null) return existing;
        int slot = idsByIndex.size();
        idsByIndex.add(id);
        indexByid.put(id, slot);
        return slot;
    }

    /** Returns the string for {@code slot}, or {@code null} if out of range / {@code -1}. */
    public String get(int slot) {
        if (slot < 0 || slot >= idsByIndex.size()) return null;
        return idsByIndex.get(slot);
    }

    public int size() {
        return idsByIndex.size();
    }

    public List<String> all() {
        return Collections.unmodifiableList(idsByIndex);
    }
}
