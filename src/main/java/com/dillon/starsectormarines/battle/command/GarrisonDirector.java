package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The defender commander's reactive manning of {@link GarrisonableRegistry garrisonables}: which prepared-ground
 * places believed pressure has reached, which squads of the mobile pool to send, and when a manned place has cooled
 * enough to give its squads back. Pure planning over frozen inputs; the caller turns the answers into orders.
 *
 * <p><b>Cost.</b> Nothing here runs per tick: a defender plan calls {@link #evaluate} and {@link #assign} once per
 * command pulse. Work follows the fight, not the map. The places considered are those woken by a believed contact
 * (the registry's block lookup answers, per contact block, which places lie within reach) plus the places already
 * manned; each is priced by one array read per covered influence block. A map with a thousand garrisonables and a
 * quiet front visits none of them.
 *
 * <p><b>Hysteresis.</b> A place starts being manned when its pressure reaches {@link #START_PRESSURE}. Its squads are
 * given back only after the pressure has stayed below {@link #RELEASE_PRESSURE}, which is lower, for
 * {@link #DWELL_PULSES} consecutive pulses; anything in between holds them. Between the two lines nothing changes, so
 * a squad is not marched in and out of a trench by pressure that wavers around one level.
 *
 * <p><b>Pickets before massing.</b> Every hot place gets one squad before any gets a second, most pressed first, and
 * a bounded number are sent per pulse. At least {@link #RESERVE_FLOOR} free patrol is left unsent: the roving
 * reserve the defender's other laws rely on.
 *
 * <p>State is per commander (so per simulation) and exists only for places already manned or ordered:
 * the consecutive-cool count per garrisonable, and the squads ordered but not yet seen carrying the order, which the
 * assignment ledger may hold back for a pulse.
 */
final class GarrisonDirector {

    /** Believed hostile pressure on a place at or above which it is manned. UNTUNED: roughly a squad of riflemen about two blocks off. */
    static final float START_PRESSURE = 3f;
    /** Pressure below which a manned place counts as cool. Lower than {@link #START_PRESSURE}. UNTUNED. */
    static final float RELEASE_PRESSURE = 1f;
    /** Consecutive cool pulses (2.5 s each) before a manned place releases its squads. UNTUNED. */
    static final int DWELL_PULSES = 3;
    /** Most squads newly sent to man places in one pulse. UNTUNED. */
    static final int MAX_NEW_PER_PULSE = 2;
    /** Pressure at which a place wants a second squad, given room for one. UNTUNED. */
    static final float SECOND_SQUAD_PRESSURE = 8f;
    /** A place smaller than this holds one squad. */
    static final int SECOND_SQUAD_MIN_CELLS = 24;
    /** Free patrols never sent: the reserve stays at least this large. */
    static final int RESERVE_FLOOR = 1;
    /** Pulses an unadopted order is kept before the squad is treated as an ordinary patrol again. */
    static final int PENDING_PULSES = 4;
    /** A believed contact lighter than this (confidence times strength) wakes nothing: a works crew, a fading report. */
    static final float MIN_CONTACT_WEIGHT = 0.5f;

    /** Believed hostile pressure at an influence block. */
    @FunctionalInterface
    interface Pressure {
        float hostileAt(int blockX, int blockY);
    }

    /** Whether a free squad may be sent to a garrisonable (reachable, in reach of its track). */
    @FunctionalInterface
    interface Eligibility {
        boolean test(int squadId, int garrisonable);
    }

    /** A squad manning, or ordered to man, a garrisonable; {@code releasable} is false while it is in a fight. */
    record Garrison(int squadId, int garrisonable, boolean releasable) { }

    /** A free patrol and where it stands. */
    record Free(int squadId, int x, int y) { }

    /** A squad to send to a garrisonable. */
    record Man(int squadId, int garrisonable) { }

    private record Pending(int cellX, int cellY, int pulsesLeft) { }

    private final Map<Integer, Integer> cool = new HashMap<>();
    private final Map<Integer, Pending> pending = new HashMap<>();

    private GarrisonableRegistry bound;
    private int[] placeStamp = new int[0];
    private int[] pressureStamp = new int[0];
    private float[] pressureOf = new float[0];
    private int[] blockStamp = new int[0];
    private int generation;

    // Between evaluate and assign within one pulse.
    private GarrisonableRegistry pulseRegistry;
    private Pressure pulseField;
    private int[] hot = new int[0];
    private int hotCount;
    private final Map<Integer, Integer> have = new HashMap<>();
    private int considered;
    private int blockReads;

    /** The garrisonable cell a squad was ordered to man and has not yet been seen carrying, or {@code null}. */
    int[] pendingCell(int squadId) {
        Pending p = pending.get(squadId);
        return p == null ? null : new int[]{p.cellX, p.cellY};
    }

    /** The squad now carries its order; stop tracking it as unadopted. */
    void adopted(int squadId) {
        pending.remove(squadId);
    }

    /** The squad stands down (released, lost, or dead). */
    void forget(int squadId) {
        pending.remove(squadId);
    }

    /** How many orders await adoption; for tests and evidence. */
    int pendingCount() {
        return pending.size();
    }

    /**
     * Prices the places woken by {@code contactXY} (packed cell pairs, {@code contactCount} of them) and those already
     * manned, advances the cooling count of each manned place, and returns the squads to give back. Call once per pulse,
     * before {@link #assign}.
     *
     * @param held every squad manning or ordered to man a place, grouped by nothing in particular
     * @return ids of the squads to release, ascending
     */
    List<Integer> evaluate(GarrisonableRegistry registry, Pressure field, int[] contactXY, int contactCount,
                           List<Garrison> held) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile != null ? System.nanoTime() : 0L;
        bind(registry);
        generation++;
        considered = 0;
        blockReads = 0;
        hotCount = 0;
        have.clear();
        pulseRegistry = registry;
        pulseField = field;
        ageOrders();

        int contactBlocks = 0;
        int bw = registry.blockWidth();
        int bh = registry.blockHeight();
        int size = registry.blockSize();
        for (int i = 0; i < contactCount; i++) {
            int bx = Math.min(bw - 1, Math.max(0, contactXY[2 * i] / size));
            int by = Math.min(bh - 1, Math.max(0, contactXY[2 * i + 1] / size));
            int block = by * bw + bx;
            if (blockStamp[block] == generation) continue;
            blockStamp[block] = generation;
            contactBlocks++;
            for (int g : registry.near(bx, by)) {
                if (placeStamp[g] == generation) continue;
                placeStamp[g] = generation;
                if (pressure(g) >= START_PRESSURE) {
                    if (hotCount == hot.length) hot = Arrays.copyOf(hot, Math.max(4, hot.length * 2));
                    hot[hotCount++] = g;
                }
            }
        }

        List<Garrison> ordered = new ArrayList<>(held);
        ordered.sort(Comparator.comparingInt(Garrison::garrisonable).thenComparingInt(Garrison::squadId));
        List<Integer> release = new ArrayList<>();
        for (int from = 0; from < ordered.size(); ) {
            int g = ordered.get(from).garrisonable();
            int to = from;
            while (to < ordered.size() && ordered.get(to).garrisonable() == g) to++;
            float p = pressure(g);
            int stays = to - from;
            if (p >= RELEASE_PRESSURE) {
                cool.remove(g);
            } else {
                int n = cool.merge(g, 1, Integer::sum);
                if (n >= DWELL_PULSES) {
                    stays = 0;
                    for (int i = from; i < to; i++) {
                        Garrison squad = ordered.get(i);
                        if (squad.releasable()) release.add(squad.squadId());
                        else stays++;
                    }
                    if (stays == 0) cool.remove(g);
                }
            }
            if (stays > 0) have.put(g, stays);
            from = to;
        }
        release.sort(Integer::compare);

        sortHot();
        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_CONTACT_BLOCK, contactBlocks);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_RELEASE, release.size());
            profile.record(TickInnerProfile.Bucket.GARRISON_EVAL, System.nanoTime() - started);
        }
        return release;
    }

    /**
     * Sends free patrols to the hot places {@link #evaluate} found. Every hot place without a squad gets one before any
     * gets a second; the number sent is bounded per pulse and the reserve is never emptied.
     *
     * @param free free patrols, in a stable order; none is already manned or ordered
     */
    List<Man> assign(List<Free> free, Eligibility eligible) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile != null ? System.nanoTime() : 0L;
        List<Man> out = new ArrayList<>();
        if (hotCount > 0 && pulseRegistry != null) {
            boolean[] used = new boolean[free.size()];
            int left = free.size();
            boolean capped = false;
            for (int i = 0; i < hotCount; i++) {
                int g = hot[i];
                if (have.getOrDefault(g, 0) > 0) continue;
                if (out.size() >= MAX_NEW_PER_PULSE) {
                    capped = true;
                    break;
                }
                if (left <= RESERVE_FLOOR) break;
                int pick = nearest(free, used, g, eligible);
                if (pick < 0) continue;
                used[pick] = true;
                left--;
                have.put(g, 1);
                out.add(new Man(free.get(pick).squadId(), g));
            }
            if (!capped) {
                for (int i = 0; i < hotCount && out.size() < MAX_NEW_PER_PULSE && left > RESERVE_FLOOR; i++) {
                    int g = hot[i];
                    if (have.getOrDefault(g, 0) != 1 || !wantsSecond(g)) continue;
                    int pick = nearest(free, used, g, eligible);
                    if (pick < 0) continue;
                    used[pick] = true;
                    left--;
                    have.put(g, 2);
                    out.add(new Man(free.get(pick).squadId(), g));
                }
            }
        }
        for (Man man : out) {
            pending.put(man.squadId(), new Pending(pulseRegistry.centreX(man.garrisonable()),
                    pulseRegistry.centreY(man.garrisonable()), PENDING_PULSES));
        }
        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_CANDIDATE, considered);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_BLOCK_READ, blockReads);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_MAN, out.size());
            profile.record(TickInnerProfile.Bucket.GARRISON_EVAL, System.nanoTime() - started);
        }
        pulseRegistry = null;
        pulseField = null;
        return out;
    }

    private boolean wantsSecond(int g) {
        return pressure(g) >= SECOND_SQUAD_PRESSURE && pulseRegistry.size(g) >= SECOND_SQUAD_MIN_CELLS;
    }

    /** Index in {@code free} of the nearest unused eligible patrol, ties to the lower squad id, or -1. */
    private int nearest(List<Free> free, boolean[] used, int g, Eligibility eligible) {
        int cx = pulseRegistry.centreX(g);
        int cy = pulseRegistry.centreY(g);
        Integer[] order = new Integer[free.size()];
        int n = 0;
        for (int i = 0; i < used.length; i++) if (!used[i]) order[n++] = i;
        Integer[] candidates = Arrays.copyOf(order, n);
        Arrays.sort(candidates, Comparator
                .<Integer>comparingLong(i -> distanceSq(free.get(i), cx, cy))
                .thenComparingInt(i -> free.get(i).squadId()));
        for (int i : candidates) {
            if (eligible.test(free.get(i).squadId(), g)) return i;
        }
        return -1;
    }

    private static long distanceSq(Free f, int cx, int cy) {
        long dx = f.x() - cx;
        long dy = f.y() - cy;
        return dx * dx + dy * dy;
    }

    /** Hot places by pressure descending, then id. */
    private void sortHot() {
        Integer[] boxed = new Integer[hotCount];
        for (int i = 0; i < hotCount; i++) boxed[i] = hot[i];
        Arrays.sort(boxed, Comparator.<Integer>comparingDouble(g -> -pressureOf[g]).thenComparingInt(g -> g));
        for (int i = 0; i < hotCount; i++) hot[i] = boxed[i];
    }

    /** The place's believed pressure: the highest of its covered blocks, read once per pulse. */
    private float pressure(int g) {
        if (pressureStamp[g] == generation) return pressureOf[g];
        considered++;
        float best = 0f;
        int bw = pulseRegistry.blockWidth();
        for (int block : pulseRegistry.coveredBlocks(g)) {
            blockReads++;
            best = Math.max(best, pulseField.hostileAt(block % bw, block / bw));
        }
        pressureStamp[g] = generation;
        pressureOf[g] = best;
        return best;
    }

    private void bind(GarrisonableRegistry registry) {
        if (bound == registry) return;
        bound = registry;
        cool.clear();
        placeStamp = new int[registry.count()];
        pressureStamp = new int[registry.count()];
        pressureOf = new float[registry.count()];
        blockStamp = new int[registry.blockWidth() * registry.blockHeight()];
        generation = 0;
    }

    private void ageOrders() {
        for (Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Pending> entry = it.next();
            Pending p = entry.getValue();
            if (p.pulsesLeft <= 1) it.remove();
            else entry.setValue(new Pending(p.cellX, p.cellY, p.pulsesLeft - 1));
        }
    }
}
