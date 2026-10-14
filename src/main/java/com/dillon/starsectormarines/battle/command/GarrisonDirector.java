package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The defender commander's reactive manning of {@link GarrisonableRegistry garrisonables}: which prepared-ground
 * places believed pressure has reached, which squads of the mobile pool to send, and when a manned place has cooled
 * enough to give its squads back. Pure planning over frozen inputs; the caller turns the answers into orders.
 *
 * <p><b>Cost.</b> Nothing here runs per tick: a defender plan calls {@link #evaluate} and {@link #assign} once per
 * command pulse. Work follows the fight, not the map. The places considered are those woken by a believed contact
 * (the registry's block lookup answers, per contact block, which places lie within reach) plus the places already
 * manned; each is priced by one array read per covered influence block. A map with a thousand garrisonables and a
 * quiet front visits none of them. Pricing a march is arithmetic on two cells; the breach check reads nine cells per
 * believed contact and only while some place is manned.
 *
 * <p><b>Hysteresis.</b> A place starts being manned when its pressure reaches {@link #START_PRESSURE}. Its squads are
 * given back only after the pressure has stayed below {@link #RELEASE_PRESSURE}, which is lower, for
 * {@link #DWELL_PULSES} consecutive pulses; anything in between holds them. Between the two lines nothing changes, so
 * a squad is not marched in and out of a trench by pressure that wavers around one level.
 *
 * <p><b>Pickets before massing.</b> Every hot place gets one squad before any gets a second, most pressed first, and
 * a bounded number are sent per pulse. At least {@link #RESERVE_FLOOR} roving patrol is left unsent: the reserve
 * the defender's other laws rely on. A patrol staged at a fort's works does not count toward it.
 *
 * <p><b>Arrival.</b> A march across the map outlasts the pressure that called it, so a squad is sent only if it can
 * arrive soon enough to matter: {@link #etaTicks} prices the march from the squad's cell to the place by octile
 * distance over marching speed, with no search, and a squad beyond {@link #MAX_ETA_TICKS} is not an option. When no
 * free squad is close enough the place stays empty. A squad still closing on its place is not given back for a lull in
 * pressure until {@link #EN_ROUTE_DWELL_PULSES} cool pulses, since a march abandoned halfway is wasted; one that has
 * been closing for {@link #MAX_EN_ROUTE_PULSES} pulses without arriving is given back regardless.
 *
 * <p><b>Fall back.</b> A squad on its place when an enemy is believed inside the run, or that has fallen below
 * {@link #MIN_USEFUL_MEMBERS}, is reported by {@link #fallingBack} for the caller to withdraw to the works (or, with
 * none standing, to release). A squad that has not arrived at a breached place is simply given back. A breached place
 * is not manned again for {@link #BREACH_LOCKOUT_PULSES} pulses.
 *
 * <p>State is per commander (so per simulation) and exists only for places already manned or ordered:
 * the consecutive-cool count per garrisonable, the squads ordered but not yet seen carrying the order, which the
 * assignment ledger may hold back for a pulse, the pulses each squad has spent closing, and the breached places.
 */
final class GarrisonDirector {

    /** Believed hostile pressure on a place at or above which it is manned. UNTUNED: roughly a squad of riflemen about two blocks off. */
    static final float START_PRESSURE = 3f;
    /** Believed pressure at or above which an area is warm enough for the pool to start flowing toward it. UNTUNED. */
    static final float FLOW_PRESSURE = 1f;
    /** Pressure below which a manned place counts as cool. Lower than {@link #START_PRESSURE}. UNTUNED. */
    static final float RELEASE_PRESSURE = 1f;
    /** Consecutive cool pulses (2.5 s each) before a manned place releases the squads standing on it. UNTUNED. */
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
    /** Longest march, in ticks, for which a squad is sent to a place: 40 s. UNTUNED; a squad needing longer is not an option. */
    static final int MAX_ETA_TICKS = 1200;
    /** Route length over octile distance assumed when pricing a march without a search; matches the reinforcement means. */
    static final float MARCH_DETOUR = 1.35f;
    /** Simulation ticks per second, to turn the marine's cells per second into cells per tick. */
    static final float TICKS_PER_SECOND = 30f;
    /** Cool pulses before a squad still closing on its place is given back: as long as {@link #MAX_ETA_TICKS} takes. UNTUNED. */
    static final int EN_ROUTE_DWELL_PULSES = 16;
    /** Pulses a squad may spend closing on its place before it is treated as stuck and given back. UNTUNED. */
    static final int MAX_EN_ROUTE_PULSES = 40;
    /** A manning squad with fewer living members than this is no longer a garrison. UNTUNED. */
    static final int MIN_USEFUL_MEMBERS = 3;
    /** Pulses a breached place is left unmanned. UNTUNED. */
    static final int BREACH_LOCKOUT_PULSES = 16;
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

    /**
     * A squad manning, or ordered to man, a garrisonable. {@code releasable} is false while it is in a fight;
     * {@code arrived} is whether it stands on its place yet; {@code aliveMembers} is its living strength.
     */
    record Garrison(int squadId, int garrisonable, boolean releasable, boolean arrived, int aliveMembers) {
        /** A squad on its place at full strength. */
        Garrison(int squadId, int garrisonable, boolean releasable) {
            this(squadId, garrisonable, releasable, true, Integer.MAX_VALUE);
        }
    }

    /**
     * A free patrol and where it stands. A {@code staged} one waits at a fort's works for exactly this call: it is sent
     * without regard to the reserve floor, which protects the roving pool.
     */
    record Free(int squadId, int x, int y, boolean staged) {
        /** A roving patrol. */
        Free(int squadId, int x, int y) {
            this(squadId, x, y, false);
        }
    }

    /** A squad to send to a garrisonable. */
    record Man(int squadId, int garrisonable) { }

    private record Pending(int cellX, int cellY, int pulsesLeft) { }

    private final Map<Integer, Integer> cool = new HashMap<>();
    private final Map<Integer, Pending> pending = new HashMap<>();
    /** Pulses each squad has spent closing on its place; only squads not yet arrived appear. */
    private final Map<Integer, Integer> closing = new HashMap<>();
    /** Breached places (by packed centre cell) and the pulses left before they may be manned again. */
    private final Map<Long, Integer> lockout = new HashMap<>();
    private final List<Integer> fallBack = new ArrayList<>();

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
    private int[] warm = new int[0];
    private int warmCount;
    private final Map<Integer, Integer> have = new HashMap<>();
    private int considered;
    private int blockReads;
    private int etaEvals;

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
        closing.remove(squadId);
    }

    /** How many orders await adoption; for tests and evidence. */
    int pendingCount() {
        return pending.size();
    }

    /** Squads {@link #evaluate} found on a breached or depleted place: withdraw them to the works, or release them with none. */
    List<Integer> fallingBack() {
        return fallBack;
    }

    /**
     * The places the last {@link #evaluate} found under at least {@link #FLOW_PRESSURE} of believed pressure (a superset
     * of the hot ones), for the caller to decide where the pool should be gathering. Valid until the next evaluate.
     */
    int[] warmPlaces() {
        return Arrays.copyOf(warm, warmCount);
    }

    /** Whether the place with this centre cell is held unmanned after a breach; for tests and evidence. */
    boolean lockedOut(int cellX, int cellY) {
        return lockout.containsKey(pack(cellX, cellY));
    }

    /**
     * Ticks to march from a cell to a place's centre, priced without a search: octile distance, scaled by the detour
     * the reinforcement means assume, over a marine's walking speed.
     */
    static int etaTicks(int fromX, int fromY, int toX, int toY) {
        int dx = Math.abs(fromX - toX);
        int dy = Math.abs(fromY - toY);
        float octile = Math.max(dx, dy) + 0.41f * Math.min(dx, dy);
        float cellsPerTick = UnitType.MARINE.moveSpeed / TICKS_PER_SECOND;
        return (int) Math.ceil(octile * MARCH_DETOUR / cellsPerTick);
    }

    /**
     * Prices the places woken by {@code contactXY} (packed cell pairs, {@code contactCount} of them) and those already
     * manned, advances the cooling count of each manned place, and returns the squads to give back. Squads to withdraw
     * rather than release are left in {@link #fallingBack}. Call once per pulse, before {@link #assign}.
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
        etaEvals = 0;
        hotCount = 0;
        warmCount = 0;
        have.clear();
        fallBack.clear();
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
                float price = pressure(g);
                if (price >= FLOW_PRESSURE) {
                    if (warmCount == warm.length) warm = Arrays.copyOf(warm, Math.max(4, warm.length * 2));
                    warm[warmCount++] = g;
                }
                if (price >= START_PRESSURE) {
                    if (hotCount == hot.length) hot = Arrays.copyOf(hot, Math.max(4, hot.length * 2));
                    hot[hotCount++] = g;
                }
            }
        }

        int breachReads = 0;
        Set<Integer> breached = Set.of();
        if (!held.isEmpty() && contactCount > 0) {
            breached = new HashSet<>();
            for (int i = 0; i < contactCount; i++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        breachReads++;
                        int run = registry.idAt(contactXY[2 * i] + dx, contactXY[2 * i + 1] + dy);
                        if (run >= 0) breached.add(run);
                    }
                }
            }
        }

        Set<Integer> present = new HashSet<>();
        List<Garrison> ordered = new ArrayList<>(held);
        ordered.sort(Comparator.comparingInt(Garrison::garrisonable).thenComparingInt(Garrison::squadId));
        List<Integer> release = new ArrayList<>();
        int deferred = 0;
        for (int from = 0; from < ordered.size(); ) {
            int g = ordered.get(from).garrisonable();
            int to = from;
            while (to < ordered.size() && ordered.get(to).garrisonable() == g) to++;
            boolean overrun = breached.contains(g);
            if (overrun) lockout.put(pack(registry.centreX(g), registry.centreY(g)), BREACH_LOCKOUT_PULSES);
            int cooled = 0;
            if (pressure(g) >= RELEASE_PRESSURE) {
                cool.remove(g);
            } else {
                cooled = cool.merge(g, 1, Integer::sum);
            }
            int stays = to - from;
            for (int i = from; i < to; i++) {
                Garrison squad = ordered.get(i);
                present.add(squad.squadId());
                boolean lost = overrun || squad.aliveMembers() < MIN_USEFUL_MEMBERS;
                if (squad.arrived()) {
                    closing.remove(squad.squadId());
                    if (lost) {
                        fallBack.add(squad.squadId());
                        stays--;
                    } else if (cooled >= DWELL_PULSES && squad.releasable()) {
                        release.add(squad.squadId());
                        stays--;
                    }
                    continue;
                }
                boolean stuck = closing.merge(squad.squadId(), 1, Integer::sum) > MAX_EN_ROUTE_PULSES;
                if (!squad.releasable()) continue;
                if (lost || stuck || cooled >= EN_ROUTE_DWELL_PULSES) {
                    release.add(squad.squadId());
                    closing.remove(squad.squadId());
                    stays--;
                } else if (cooled >= DWELL_PULSES) {
                    deferred++;
                }
            }
            if (stays == 0) cool.remove(g);
            if (stays > 0) have.put(g, stays);
            from = to;
        }
        closing.keySet().retainAll(present);
        release.sort(Integer::compare);
        fallBack.sort(Integer::compare);

        sortHot();
        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_CONTACT_BLOCK, contactBlocks);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_RELEASE, release.size());
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_FALL_BACK, fallBack.size());
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_HOLD_EN_ROUTE, deferred);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_BREACH_READ, breachReads);
            profile.record(TickInnerProfile.Bucket.GARRISON_EVAL, System.nanoTime() - started);
        }
        return release;
    }

    /**
     * Sends free patrols to the hot places {@link #evaluate} found. Every hot place without a squad gets one before any
     * gets a second; the number sent is bounded per pulse and the reserve is never emptied. A place is only given a
     * squad that can reach it within {@link #MAX_ETA_TICKS}; where none can, it stays empty. A breached place waits out
     * its lockout.
     *
     * @param free free patrols, in a stable order; none is already manned or ordered
     */
    List<Man> assign(List<Free> free, Eligibility eligible) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile != null ? System.nanoTime() : 0L;
        List<Man> out = new ArrayList<>();
        int refused = 0;
        if (hotCount > 0 && pulseRegistry != null) {
            boolean[] used = new boolean[free.size()];
            int left = 0;
            for (Free squad : free) if (!squad.staged()) left++;
            boolean capped = false;
            for (int i = 0; i < hotCount; i++) {
                int g = hot[i];
                if (have.getOrDefault(g, 0) > 0 || lockedOut(pulseRegistry.centreX(g), pulseRegistry.centreY(g))) continue;
                if (out.size() >= MAX_NEW_PER_PULSE) {
                    capped = true;
                    break;
                }
                int pick = nearest(free, used, g, eligible, left > RESERVE_FLOOR);
                if (pick < 0) {
                    refused++;
                    continue;
                }
                used[pick] = true;
                if (!free.get(pick).staged()) left--;
                have.put(g, 1);
                out.add(new Man(free.get(pick).squadId(), g));
            }
            if (!capped) {
                for (int i = 0; i < hotCount && out.size() < MAX_NEW_PER_PULSE; i++) {
                    int g = hot[i];
                    if (have.getOrDefault(g, 0) != 1 || !wantsSecond(g)
                            || lockedOut(pulseRegistry.centreX(g), pulseRegistry.centreY(g))) continue;
                    int pick = nearest(free, used, g, eligible, left > RESERVE_FLOOR);
                    if (pick < 0) continue;
                    used[pick] = true;
                    if (!free.get(pick).staged()) left--;
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
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_ETA_EVAL, etaEvals);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_ETA_REFUSED, refused);
            profile.record(TickInnerProfile.Bucket.GARRISON_EVAL, System.nanoTime() - started);
        }
        pulseRegistry = null;
        pulseField = null;
        return out;
    }

    private boolean wantsSecond(int g) {
        return pressure(g) >= SECOND_SQUAD_PRESSURE && pulseRegistry.size(g) >= SECOND_SQUAD_MIN_CELLS;
    }

    /**
     * Index in {@code free} of the nearest unused eligible patrol that can arrive within {@link #MAX_ETA_TICKS}, ties
     * to the lower squad id, or -1. Distance is the only ordering, so the first squad found past the bound ends the
     * search: nothing after it is closer.
     */
    private int nearest(List<Free> free, boolean[] used, int g, Eligibility eligible, boolean mayTakeRoving) {
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
            Free squad = free.get(i);
            if (!squad.staged() && !mayTakeRoving) continue;
            etaEvals++;
            if (etaTicks(squad.x(), squad.y(), cx, cy) > MAX_ETA_TICKS) return -1;
            if (eligible.test(squad.squadId(), g)) return i;
        }
        return -1;
    }

    private static long distanceSq(Free f, int cx, int cy) {
        long dx = f.x() - cx;
        long dy = f.y() - cy;
        return dx * dx + dy * dy;
    }

    private static long pack(int x, int y) {
        return ((long) y << 32) | (x & 0xffffffffL);
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
        closing.clear();
        lockout.clear();
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
        for (Iterator<Map.Entry<Long, Integer>> it = lockout.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Integer> entry = it.next();
            if (entry.getValue() <= 1) it.remove();
            else entry.setValue(entry.getValue() - 1);
        }
    }
}
