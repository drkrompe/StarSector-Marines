package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The allied side's on-map cap (`allied-command.md`, The allied cap): how many allied fighting bodies a mission lets
 * stand on the map at once, in two pools, infantry and machines. Mission-neutral and unlimited until a mission declares
 * a limit. It counts only what a spawn path enlisted ({@link #enlist}): waves, observer teams, armour, mech lances and
 * fire sections a mission brings on during the battle, and the machines it sets down at the opening. Seated
 * emplacements and their crews, which never spawn again, are outside it, and aircraft are bounded by their bays.
 *
 * <p>A spawn path asks before it spawns ({@link #mayGo}) with a key of its own and the bodies it would bring. At the
 * cap a request <b>waits</b>: it takes its place in its pool's queue and goes, first come first served, once the pool
 * has room for it, so losses are replaced rather than piling up. A key not asked again for {@link #EXPIRE_TICKS} has
 * stopped wanting and leaves the queue, so it never holds the line. Serial and deterministic: the queue is the order
 * the paths asked in.
 */
public final class AlliedCap {

    /** What a body counts against. */
    public enum Pool {
        /** Militia teams, observer teams: soldiers. */
        INFANTRY,
        /** Hulls and mechs, a fire section's carriers included. */
        MACHINES
    }

    /** Ticks a waiting request may go unasked before it leaves the queue. */
    public static final int EXPIRE_TICKS = 180;

    private static final class Ticket {
        final Object key;
        final int size;
        int lastAsk;
        boolean waited;

        Ticket(Object key, int size, int tick) {
            this.key = key;
            this.size = size;
            this.lastAsk = tick;
        }
    }

    private final Map<Pool, Integer> limits = new EnumMap<>(Pool.class);
    private final Map<Pool, Set<Long>> enlisted = new EnumMap<>(Pool.class);
    private final Map<Pool, List<Ticket>> queues = new EnumMap<>(Pool.class);
    private final Map<Pool, Integer> peaks = new EnumMap<>(Pool.class);
    private final Map<Pool, Integer> deferred = new EnumMap<>(Pool.class);
    /** Each pool's standing bodies at each {@link #sample}, in pool order: the reading over time. */
    private final List<int[]> samples = new ArrayList<>();

    public AlliedCap() {
        for (Pool pool : Pool.values()) {
            enlisted.put(pool, new LinkedHashSet<>());
            queues.put(pool, new ArrayList<>());
            peaks.put(pool, 0);
            deferred.put(pool, 0);
        }
    }

    /** Declares the pool's limit: at most {@code bodies} enlisted bodies standing at once. */
    public AlliedCap limit(Pool pool, int bodies) {
        limits.put(pool, Math.max(0, bodies));
        return this;
    }

    /** The pool's limit, or {@link Integer#MAX_VALUE} when the mission declared none. */
    public int limit(Pool pool) {
        return limits.getOrDefault(pool, Integer.MAX_VALUE);
    }

    /** Whether the mission declared any limit. */
    public boolean declared() {
        return !limits.isEmpty();
    }

    /**
     * Whether the path keyed {@code key} may bring {@code size} bodies of {@code pool} on now. True when the pool is
     * unlimited, or the request is first in its pool's queue and the pool has room; otherwise it waits in the queue.
     */
    public boolean mayGo(BattleSimulation sim, Object key, Pool pool, int size) {
        if (!limits.containsKey(pool)) return true;
        return mayGo(key, pool, size, live(sim, pool), sim.getSimTickIndex());
    }

    /** The rule, with the pool's standing bodies given: what {@link #mayGo(BattleSimulation, Object, Pool, int)} decides. */
    boolean mayGo(Object key, Pool pool, int size, int standing, int tick) {
        int limit = limit(pool);
        List<Ticket> queue = queues.get(pool);
        queue.removeIf(t -> tick - t.lastAsk > EXPIRE_TICKS);
        Ticket mine = null;
        for (Ticket t : queue) if (t.key.equals(key)) mine = t;
        if (mine == null) {
            mine = new Ticket(key, size, tick);
            queue.add(mine);
        }
        mine.lastAsk = tick;
        if (queue.get(0) == mine && standing + size <= limit) {
            queue.remove(0);
            return true;
        }
        if (!mine.waited) {
            mine.waited = true;
            deferred.merge(pool, 1, Integer::sum);
        }
        return false;
    }

    /** Counts a body a spawn path brought on against its pool. */
    public void enlist(BattleSimulation sim, Pool pool, long id) {
        if (id == 0L) return;
        enlisted.get(pool).add(id);
        if (sim != null) live(sim, pool);
    }

    /** Enlisted bodies of the pool standing now; the dead are forgotten, and the pool's peak is kept. */
    public int live(BattleSimulation sim, Pool pool) {
        Set<Long> ids = enlisted.get(pool);
        for (Iterator<Long> it = ids.iterator(); it.hasNext(); ) if (!standing(sim, it.next())) it.remove();
        int n = ids.size();
        if (n > peaks.get(pool)) peaks.put(pool, n);
        return n;
    }

    private static boolean standing(BattleSimulation sim, long id) {
        if (sim.convoy().isVehicle(id)) return sim.convoy().structure(id) > 0f;
        return sim.resolveUnit(id) != 0L && sim.world().isAlive(id);
    }

    /** The most of the pool that stood at once, as counted. */
    public int peak(Pool pool) {
        return peaks.get(pool);
    }

    /** Spawns of the pool that had to wait for room at least once. */
    public int deferred(Pool pool) {
        return deferred.get(pool);
    }

    /** Requests of the pool waiting now. */
    public int waiting(Pool pool) {
        return queues.get(pool).size();
    }

    /** Records each pool's standing bodies now: the evidence's reading over time. */
    public void sample(BattleSimulation sim) {
        int[] row = new int[Pool.values().length];
        for (Pool pool : Pool.values()) row[pool.ordinal()] = live(sim, pool);
        samples.add(row);
    }

    /** One line for the evidence: each pool's limit, standing, peak and deferred spawns, then the samples taken. */
    public String line(BattleSimulation sim) {
        StringBuilder out = new StringBuilder();
        for (Pool pool : Pool.values()) {
            if (out.length() > 0) out.append("; ");
            int limit = limit(pool);
            out.append(pool.name().toLowerCase(Locale.ROOT)).append(' ').append(live(sim, pool)).append(" standing of ")
                    .append(limit == Integer.MAX_VALUE ? "no cap" : String.valueOf(limit)).append(", peak ")
                    .append(peak(pool)).append(", spawns deferred ").append(deferred(pool))
                    .append(", waiting ").append(waiting(pool));
        }
        if (!samples.isEmpty()) {
            out.append("; standing by sample (infantry/machines)");
            for (int[] row : samples) out.append(' ').append(row[0]).append('/').append(row[1]);
        }
        return out.toString();
    }
}
