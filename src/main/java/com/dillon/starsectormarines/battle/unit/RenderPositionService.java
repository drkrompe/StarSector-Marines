package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.unit.components.RenderPositionComponent;

/**
 * Smooth render position (sub-cell {@code x,y}) for every entity, keyed by
 * monotonic {@link Entity#entityId} — the first column pulled out of the
 * {@link UnitRegistry} kitchen-sink into a standalone per-component service.
 *
 * <h2>Why entity-id-keyed, not dense</h2>
 * <p>The dense {@link UnitRegistry} table swap-and-pops dead entities out, so
 * its columns are <b>live-only</b>. Render position is the one mandatory column
 * that (a) is never iterated densely — every reader goes through the
 * {@link Entity#getRenderX()} / {@link Entity#getRenderY()} accessor, never a bulk
 * {@code renderXArray()} sweep — and (b) must <b>survive into the death drain</b>:
 * {@code DeadBodySystem} snapshots the entry into the corpse entity's own
 * {@code RENDER_POSITION} columns (the corpse render no longer reads this store
 * after that). Keying by {@code entityId} instead of dense index gives both for
 * free — the entry simply isn't removed on release, so a released {@link Entity}
 * still resolves its last render position for any remaining legacy reader. No
 * perf cost (no hot dense loop reads this) and no behavior change for the living.
 *
 * <h2>A real component, wrapped</h2>
 * <p>This service is a thin float-typed API over a
 * {@link ComponentStore}{@code <}{@link RenderPositionComponent}{@code >} — render
 * position is a genuine composable component (the same presence mechanism the
 * drone {@code CrashingComponent} fall uses), so "where do I draw" is shared across
 * entity categories instead of redefined per type. The wrapper keeps
 * {@link Entity}'s accessors returning primitive {@code float} without exposing
 * the component object. A corpse is an entity <em>present</em> in this store
 * but <em>absent</em> from the live registry's health/AI columns.
 *
 * <p>The store grows with total spawns and never shrinks for the battle's
 * lifetime — same memory profile as the legacy units list that retains dead
 * entries, GC'd whole when the ephemeral battle ends.
 *
 * <h2>Thread safety</h2>
 * <p>Same single-writer / multi-reader contract as {@link UnitRegistry}:
 * seeded serially in {@link UnitRegistry#allocate}, written from
 * {@link Entity#setRenderPos} in serial movement phases; the parallel
 * UPDATE_UNITS dispatch reads but does not mutate.
 */
public final class RenderPositionService {

    private final ComponentStore<RenderPositionComponent> store = new ComponentStore<>();

    /**
     * Sets both render axes for {@code entityId} — the paired write every
     * {@code setRenderPos} caller makes. Mutates the existing component in
     * place (no churn) or attaches one on first write.
     */
    public void set(long entityId, float renderX, float renderY) {
        RenderPositionComponent p = store.get(entityId);
        if (p == null) {
            store.add(entityId, new RenderPositionComponent(renderX, renderY));
        } else {
            p.x = renderX;
            p.y = renderY;
        }
    }

    /**
     * Sets only the render X axis (keeps Y); for the rare single-axis writer.
     * <b>Invariant:</b> an entity's <em>first</em> render write must be the
     * paired {@link #set} (which {@link UnitRegistry#allocate} guarantees by
     * seeding at spawn) — a single-axis setter on a never-seeded entity zeros
     * the other axis. The absent-entity {@code add} here is a defensive seed,
     * not a supported first-write path.
     */
    public void setX(long entityId, float renderX) {
        RenderPositionComponent p = store.get(entityId);
        if (p == null) store.add(entityId, new RenderPositionComponent(renderX, 0f));
        else p.x = renderX;
    }

    /** Sets only the render Y axis (keeps X). Same first-write invariant as {@link #setX}. */
    public void setY(long entityId, float renderY) {
        RenderPositionComponent p = store.get(entityId);
        if (p == null) store.add(entityId, new RenderPositionComponent(0f, renderY));
        else p.y = renderY;
    }

    /** Render X for {@code entityId}; {@code 0f} if the entity has no render position (never seeded). */
    public float getX(long entityId) {
        RenderPositionComponent p = store.get(entityId);
        return p == null ? 0f : p.x;
    }

    /** Render Y for {@code entityId}; {@code 0f} if the entity has no render position. */
    public float getY(long entityId) {
        RenderPositionComponent p = store.get(entityId);
        return p == null ? 0f : p.y;
    }

    /** True iff {@code entityId} has a render position — including released corpses (entries survive release). */
    public boolean has(long entityId) { return store.has(entityId); }

    /**
     * Drops the render position for {@code entityId}. <b>Not</b> called on
     * registry release (render must survive for the corpse) — reserved for a
     * future true corpse-removal lifecycle (medic revive consuming the body,
     * end-of-battle teardown).
     */
    public void remove(long entityId) { store.remove(entityId); }

    /** Number of entities with a render position (live + retained corpses). */
    public int size() { return store.size(); }
}
