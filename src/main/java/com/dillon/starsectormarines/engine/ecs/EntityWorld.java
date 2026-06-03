package com.dillon.starsectormarines.engine.ecs;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The archetype-table entity world: mints entity ids, owns one
 * {@link ArchetypeTable} per archetype, and routes all reads / structural changes
 * by entity id. An entity is a bare {@code long}; its archetype is the set of
 * component columns its id lives in, identified by a {@code long} bitmask.
 *
 * <p>The location index ({@code entityId -> packed(tableIndex, row)}) and the
 * mask-&gt;table index are fastutil primitive maps (no boxing). A structural
 * change moves the entity's row between tables and fixes the location of both the
 * moved entity and the source tail that filled its hole. Serial-only — built for
 * the single-threaded sim tick.
 *
 * <p>Game-agnostic engine layer — see {@code roadmap/ecs-migration/archetype-storage.md}.
 */
public final class EntityWorld {

    private final Map<Integer, ComponentType> typeById = new HashMap<>();

    private final List<ArchetypeTable> tables = new ArrayList<>();
    private final Long2IntOpenHashMap tableIndexByMask = new Long2IntOpenHashMap();
    /** entityId -> {@code (tableIndex << 32) | row}. */
    private final Long2LongOpenHashMap location = new Long2LongOpenHashMap();
    private final CommandBuffer commands = new CommandBuffer();

    private long nextEntityId = 1L;
    private int tableVersion = 0;

    public EntityWorld() {
        tableIndexByMask.defaultReturnValue(-1);
        location.defaultReturnValue(-1L);
    }

    // ---- component-type registration (code-driven, no reflection) ----

    /** Register a component type; {@code fields} empty makes a pure-presence tag. */
    public ComponentType register(int id, String name, FieldKind... fields) {
        ComponentType ct = new ComponentType(id, name, fields);
        if (typeById.putIfAbsent(id, ct) != null) {
            throw new IllegalStateException("component id " + id + " already registered");
        }
        return ct;
    }

    // ---- entity lifecycle ----

    public long createEntity(ComponentType... comps) {
        long mask = maskOf(comps);
        int tableIdx = tableIndexForMask(mask);
        long id = nextEntityId++;
        int row = tables.get(tableIdx).append(id);
        location.put(id, pack(tableIdx, row));
        return id;
    }

    /**
     * Creates an entity under an <b>externally-minted</b> id — the adoption seam
     * for migration, where another authority (the transitional dense registry)
     * still mints game entity ids. The internal mint is bumped past {@code id}
     * so future {@link #createEntity(ComponentType...)} calls can never collide
     * with an adopted id. {@code id} must be positive ({@code 0} is the no-entity
     * sentinel) and not already alive.
     */
    public void createEntity(long id, ComponentType... comps) {
        if (id <= 0L) throw new IllegalArgumentException("adopted entity id must be positive: " + id);
        if (location.get(id) != -1L) throw new IllegalStateException("entity " + id + " already alive");
        long mask = maskOf(comps);
        int tableIdx = tableIndexForMask(mask);
        int row = tables.get(tableIdx).append(id);
        location.put(id, pack(tableIdx, row));
        nextEntityId = Math.max(nextEntityId, id + 1);
    }

    public boolean isAlive(long entity) { return location.get(entity) != -1L; }

    public void destroy(long entity) {
        long loc = location.remove(entity);
        if (loc == -1L) return;
        int tableIdx = tableIdx(loc);
        ArchetypeTable t = tables.get(tableIdx);
        long moved = t.swapPop(row(loc));
        if (moved != -1L) location.put(moved, pack(tableIdx, row(loc)));
        t.maybeShrink();
    }

    public void addComponent(long entity, ComponentType ct) {
        long loc = requireLoc(entity);
        ArchetypeTable src = tables.get(tableIdx(loc));
        long newMask = src.mask | ct.bit();
        if (newMask == src.mask) return;   // already has it
        transition(entity, loc, newMask);
    }

    public void removeComponent(long entity, ComponentType ct) {
        long loc = requireLoc(entity);
        ArchetypeTable src = tables.get(tableIdx(loc));
        long newMask = src.mask & ~ct.bit();
        if (newMask == src.mask) return;   // doesn't have it
        transition(entity, loc, newMask);
    }

    /**
     * Applies several component adds/removes as <b>one</b> row-move to the
     * combined target archetype (the Artemis {@code EntityTransmuter} analogue) —
     * a multi-component change via chained {@link #addComponent}/{@link
     * #removeComponent} would pay one row-move each and register every
     * intermediate archetype as a permanent table. No-op when the mask doesn't
     * change, which also makes a repeated transmute (e.g. a double death event)
     * idempotent. Either array may be null/empty.
     */
    public void transmute(long entity, ComponentType[] add, ComponentType[] remove) {
        long loc = requireLoc(entity);
        long mask = tables.get(tableIdx(loc)).mask;
        long newMask = mask;
        if (add != null) for (ComponentType ct : add) newMask |= ct.bit();
        if (remove != null) for (ComponentType ct : remove) newMask &= ~ct.bit();
        if (newMask == mask) return;
        transition(entity, loc, newMask);
    }

    public boolean has(long entity, ComponentType ct) {
        long loc = location.get(entity);
        return loc != -1L && (tables.get(tableIdx(loc)).mask & ct.bit()) != 0;
    }

    // ---- random-access field get/set (off-hot-path; systems iterate columns instead) ----

    public int    getInt(long e, ComponentType ct, int f)    { long l = requireLoc(e); return tables.get(tableIdx(l)).ints(ct, f).get(row(l)); }
    public void   setInt(long e, ComponentType ct, int f, int v)    { long l = requireLoc(e); tables.get(tableIdx(l)).ints(ct, f).set(row(l), v); }
    public long   getLong(long e, ComponentType ct, int f)   { long l = requireLoc(e); return tables.get(tableIdx(l)).longs(ct, f).get(row(l)); }
    public void   setLong(long e, ComponentType ct, int f, long v)  { long l = requireLoc(e); tables.get(tableIdx(l)).longs(ct, f).set(row(l), v); }
    public float  getFloat(long e, ComponentType ct, int f)  { long l = requireLoc(e); return tables.get(tableIdx(l)).floats(ct, f).get(row(l)); }
    /** Tolerant read: {@code orElse} when {@code e} is missing or lacks {@code ct} — one location probe, for liveness-style checks over maybe-dead ids. */
    public float  getFloat(long e, ComponentType ct, int f, float orElse) {
        long l = location.get(e);
        if (l == -1L) return orElse;
        ArchetypeTable t = tables.get(tableIdx(l));
        return (t.mask & ct.bit()) == 0L ? orElse : t.floats(ct, f).get(row(l));
    }
    public void   setFloat(long e, ComponentType ct, int f, float v){ long l = requireLoc(e); tables.get(tableIdx(l)).floats(ct, f).set(row(l), v); }
    public Object getObject(long e, ComponentType ct, int f) { long l = requireLoc(e); return tables.get(tableIdx(l)).objects(ct, f).get(row(l)); }
    public void   setObject(long e, ComponentType ct, int f, Object v) { long l = requireLoc(e); tables.get(tableIdx(l)).objects(ct, f).set(row(l), v); }

    // ---- queries ----

    public Query query(ComponentType[] required, ComponentType[] excluded) {
        return new Query(maskOf(required), excluded == null ? 0L : maskOf(excluded));
    }

    public List<ArchetypeTable> matched(Query q) {
        if (q.seenVersion != tableVersion) {
            q.matched.clear();
            for (ArchetypeTable t : tables) {
                if ((t.mask & q.required) == q.required && (t.mask & q.excluded) == 0L) {
                    q.matched.add(t);
                }
            }
            q.matched.sort((a, b) -> Long.compareUnsigned(a.mask, b.mask));   // deterministic walk order
            q.seenVersion = tableVersion;
        }
        return q.matched;
    }

    public int entityCount() { return location.size(); }

    // ---- deferred structural change (safe mutation during a query walk) ----

    /**
     * The deferred structural-change buffer. Queue {@code destroy} / {@code add} /
     * {@code remove} here during a {@link Query} walk — applying them immediately
     * would swap-pop rows out from under the iterator — then {@link #flush()} at a
     * tick barrier. Creates are not buffered ({@link #createEntity} is walk-safe).
     */
    public CommandBuffer cmd() { return commands; }

    /**
     * Apply every queued structural change in FIFO order, then clear the buffer.
     * Call at a tick barrier, never mid-walk. Add/remove on an entity destroyed
     * earlier in the same flush is skipped; double-destroy is a no-op.
     */
    public void flush() {
        int n = commands.size();
        for (int i = 0; i < n; i++) {
            long e = commands.entityAt(i);
            switch (commands.kindAt(i)) {
                case CommandBuffer.DESTROY -> destroy(e);
                case CommandBuffer.ADD    -> { if (isAlive(e)) addComponent(e, typeById.get(commands.componentIdAt(i))); }
                case CommandBuffer.REMOVE -> { if (isAlive(e)) removeComponent(e, typeById.get(commands.componentIdAt(i))); }
                default -> throw new IllegalStateException("unknown command kind");
            }
        }
        commands.clear();
    }

    // ---- internals ----

    /** Package-private for tests: the table currently holding {@code entity}. */
    ArchetypeTable tableOf(long entity) { return tables.get(tableIdx(requireLoc(entity))); }

    private void transition(long entity, long loc, long newMask) {
        int srcIdx = tableIdx(loc);
        int srcRow = row(loc);
        int dstIdx = tableIndexForMask(newMask);   // may create a table (bumps tableVersion)
        ArchetypeTable src = tables.get(srcIdx);
        ArchetypeTable dst = tables.get(dstIdx);

        int dstRow = dst.append(entity);
        dst.copySharedFrom(src, srcRow, dstRow);

        long moved = src.swapPop(srcRow);
        if (moved != -1L) location.put(moved, pack(srcIdx, srcRow));
        location.put(entity, pack(dstIdx, dstRow));
        src.maybeShrink();
    }

    private int tableIndexForMask(long mask) {
        int idx = tableIndexByMask.get(mask);
        if (idx >= 0) return idx;
        ArchetypeTable t = new ArchetypeTable(mask, componentsForMask(mask));
        idx = tables.size();
        tables.add(t);
        tableIndexByMask.put(mask, idx);
        tableVersion++;
        return idx;
    }

    private ComponentType[] componentsForMask(long mask) {
        List<ComponentType> cs = new ArrayList<>();
        for (int id = 0; id < 64; id++) {
            if ((mask & (1L << id)) != 0L) {
                ComponentType ct = typeById.get(id);
                if (ct == null) throw new IllegalStateException("no component registered for id " + id);
                cs.add(ct);
            }
        }
        return cs.toArray(new ComponentType[0]);   // ascending id order
    }

    private long requireLoc(long entity) {
        long loc = location.get(entity);
        if (loc == -1L) throw new IllegalArgumentException("no such live entity: " + entity);
        return loc;
    }

    private static long maskOf(ComponentType[] comps) {
        long m = 0L;
        for (ComponentType c : comps) m |= c.bit();
        return m;
    }

    private static long pack(int tableIdx, int row) { return ((long) tableIdx << 32) | (row & 0xFFFFFFFFL); }
    private static int tableIdx(long packed) { return (int) (packed >>> 32); }
    private static int row(long packed) { return (int) packed; }
}
