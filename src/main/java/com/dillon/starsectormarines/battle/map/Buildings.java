package com.dillon.starsectormarines.battle.map;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Map-scoped registry of {@link Building}s, owned by {@code MapResult} and
 * populated by {@link com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill}
 * during gen. Carries both an id→building lookup (fastutil primitive-keyed map
 * for the hot per-cell roof-render and visibility loops) and a stable
 * iteration list so render order is deterministic across frames.
 *
 * <p>Empty for generators that don't run the flood-fill pass yet
 * (wilderness / spacehulk / etc.) — consumers must tolerate an empty
 * registry, which simply means "no buildings on this map, no roofs to draw."
 */
public final class Buildings {

    public static final Buildings EMPTY = new Buildings();

    private final Int2ObjectOpenHashMap<Building> byId = new Int2ObjectOpenHashMap<>();
    private final List<Building> ordered = new ArrayList<>();

    public void add(Building b) {
        byId.put(b.id, b);
        ordered.add(b);
    }

    public Building get(int id) {
        return byId.get(id);
    }

    public Collection<Building> all() {
        return Collections.unmodifiableList(ordered);
    }

    public int size() {
        return ordered.size();
    }

    public boolean isEmpty() {
        return ordered.isEmpty();
    }
}
