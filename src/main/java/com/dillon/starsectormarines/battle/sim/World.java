package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;

import java.util.Map;

/**
 * Entity-access facade — the artemis-shaped read layer over the two stores the
 * sim already keeps: the dense SoA {@link UnitRegistry} (universal hot columns)
 * and the sparse {@link ComponentStore}s (optional capabilities). The entity is
 * its {@code long} id; you reach its state <em>by id</em> through one receiver,
 * instead of holding a {@code Unit} object that self-routes. This is the access
 * half of the {@code world-facade} endgame (see
 * {@code roadmap/ecs-migration/stories/world-facade.md}); as call sites migrate
 * onto it, {@code Unit} loses its {@code registry} back-pointer and shrinks to a
 * bare id + immutable archetype ({@code Unit} → {@code Entity}).
 *
 * <p><b>Two faces, deliberately split</b> — the split is what preserves the
 * ECS primitive / cache-locality win while adding the ergonomic by-id access:
 *
 * <ul>
 *   <li><b>Hot face — primitive accessors</b> ({@link #hp}, {@code setHp}, and
 *       one method per dense column as groups migrate). Backed directly by the
 *       SoA arrays via {@link UnitRegistry}'s {@code *ById} accessors — one map
 *       probe + array read, <b>zero object construction</b>. Mandatory columns
 *       (every live entity has hp/cell/position) live here. Per-tick <b>bulk</b>
 *       systems do not even use this — they iterate the dense arrays over
 *       {@code [0, liveCount())}; the primitive face is the random-access / held-
 *       ref path.</li>
 *   <li><b>Cold face — projected components</b> ({@code id(id).getOrNull(Cmp.class)}).
 *       Opt-in convenience for optional capabilities, debug/UI, cross-cutting
 *       reads. Today every projected component is a <b>sparse store lookup</b>
 *       (zero construction — presence <em>is</em> the data, the real artemis
 *       {@code ComponentMapper.get}); a dense-column group projected into a view
 *       object would allocate, so such a projection must stay off hot paths.
 *       <b>Never materialize a component inside a per-tick bulk loop.</b></li>
 * </ul>
 *
 * <p>Serial-only, mirroring {@link ComponentStore} — built for the
 * single-threaded tick + render read.
 */
public final class World {

    private final UnitRegistry registry;
    /**
     * Component class → its sparse store, for the cold projection face. Wired
     * once at sim construction with the stores that exist today
     * ({@code Crashing}, {@code DeadBody}); a group decomposed out of the dense
     * table registers here as it lands.
     */
    private final Map<Class<?>, ComponentStore<?>> stores;

    public World(UnitRegistry registry, Map<Class<?>, ComponentStore<?>> stores) {
        this.registry = registry;
        this.stores = stores;
    }

    // ---- hot face: primitive by-id accessors over the dense SoA ----

    /** Current hp of the live entity {@code id}. Fail-loud on a dead/unknown id (see {@link UnitRegistry#hpById}). */
    public float hp(long id) { return registry.hpById(id); }

    /** Sets the hp of the live entity {@code id}. Fail-loud on a dead/unknown id. */
    public void setHp(long id, float v) { registry.setHpById(id, v); }

    // ---- cold face: projected component access ----

    /**
     * An entity handle bound to {@code id} for the cold projection face. A small
     * allocation per call — acceptable because this face is opt-in and off the
     * hot path (see class doc). If a projected component ever gets hot, the fix
     * is a hot-face primitive, not a flyweight band-aid.
     */
    public EntityHandle id(long id) { return new EntityHandle(id, this); }

    /** Resolves {@code id}'s component of {@code type}, or null if it has none / the type has no store. */
    <T> T componentOrNull(long id, Class<T> type) {
        ComponentStore<?> store = stores.get(type);
        return store == null ? null : type.cast(store.get(id));
    }

    /**
     * Cold-face handle: {@code world.id(entityId).getOrNull(Cmp.class)}. Bound to
     * one entity id; obtain a fresh one per access via {@link World#id(long)}.
     */
    public record EntityHandle(long id, World world) {
        /** This entity's {@code type} component, or null if it doesn't carry one. */
        public <T> T getOrNull(Class<T> type) { return world.componentOrNull(id, type); }
    }
}
