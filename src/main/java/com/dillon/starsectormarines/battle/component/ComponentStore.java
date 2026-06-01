package com.dillon.starsectormarines.battle.component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Presence-based component store: a sparse map from entity id to a component of
 * type {@code T}. An entity <em>has</em> the component or it doesn't — there is
 * no row for an absent entity, so a system processes exactly the entities that
 * carry the capability by iterating {@link #entries()}, with no per-unit
 * null-check and no branch on {@code role}/{@code type}.
 *
 * <p>This is the composition primitive of the ECS migration's component-model
 * phase: optional capabilities (a falling-crash, on-fire smoke, a vehicle body)
 * become components in their own store instead of nullable fields every unit
 * carries. Universal/hot columns stay on the dense {@code UnitRegistry} table;
 * sparse/optional ones live here.
 *
 * <p>Keyed by {@code long} entity id (monotonic, never recycled — so no
 * generation bits needed) rather than dense index, so a component survives its
 * entity's release from the live registry: a corpse / crashing wreck keeps its
 * components after it leaves {@code UnitRegistry}.
 *
 * <p>Iteration order is insertion order ({@link LinkedHashMap}), so systems run
 * deterministically. <b>Serial-only</b> — built for the single-threaded sim
 * tick + render read, no synchronization. Remove-during-iteration is not
 * supported: a system that retires entries collects their ids while iterating
 * {@link #entries()} and calls {@link #remove(long)} after the loop.
 */
public final class ComponentStore<T> {

    private final Map<Long, T> components = new LinkedHashMap<>();

    /** Attaches (or replaces) the component for {@code entityId}. */
    public void add(long entityId, T component) {
        components.put(entityId, component);
    }

    /** The component for {@code entityId}, or {@code null} if the entity doesn't have one. */
    public T get(long entityId) {
        return components.get(entityId);
    }

    /** Whether {@code entityId} currently carries this component. */
    public boolean has(long entityId) {
        return components.containsKey(entityId);
    }

    /** Detaches the component from {@code entityId}; no-op if absent. */
    public void remove(long entityId) {
        components.remove(entityId);
    }

    /** Number of entities currently carrying this component. */
    public int size() {
        return components.size();
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    /**
     * The (entity id → component) entries, in insertion order, for a system to
     * iterate. Do not structurally modify the store while iterating this — to
     * retire entries, collect their ids during the loop and {@link #remove(long)}
     * them afterward.
     */
    public Iterable<Map.Entry<Long, T>> entries() {
        return components.entrySet();
    }
}
