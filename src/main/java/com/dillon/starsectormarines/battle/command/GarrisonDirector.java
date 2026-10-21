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
 * <p><b>Stretches.</b> A garrisonable is a stretch of a run ({@link GarrisonableRegistry}). Where runs are cut, a place
 * is priced by belief only while a believed contact is within the registry's reach of it (it was woken this pulse):
 * the influence field spreads a company's weight across most of a fort, and a stretch nobody is near reads that spread
 * rather than an attack. A woken stretch reads belief over its own blocks and its neighbours', so the stretch an attack
 * is coming along the trench toward is manned before the attack is on it; of stretches pressed alike, the one whose own
 * blocks are pressed most is served first. A shift is read on own blocks alone: the squad follows the attack onto the
 * stretch it now faces. A fort's alarm floor holds the stretches of a fort only while belief has woken none of them:
 * an unseen attack keeps what the fort holds, and once the attack is seen anywhere on the fort, belief says which
 * stretches it presses. A squad arrived on a stretch that has fallen below the manning level, beside an unmanned
 * stretch of the same run pressed to it, {@link #shifting shifts} onto the neighbour instead of waiting out the dwell
 * and being given back: squad-tier movement under the assignment it holds, not a new send, and not counted against the
 * per-pulse budget. With stretches switched off every run is one place and the pricing is the old one, block by block
 * over belief and floor alike.
 *
 * <p><b>Fall back.</b> A squad on its place when an enemy is believed inside the run within
 * {@link #BREACH_RANGE_CELLS} of it (a report at the far end of a long run is somebody else's stretch, and a cell
 * beside the run is not inside it), or that is no longer at {@link #usefulFloor useful strength}, is reported by
 * {@link #fallingBack} for the caller to withdraw to the works (or, with none standing, to release). A squad that has
 * not arrived at a breached place is simply given back. A breached place is not manned again for
 * {@link #BREACH_LOCKOUT_PULSES} pulses. A patrol already below useful strength is never sent: it would be
 * given back the next pulse and sent again, one man and release per pulse for no garrison.
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
    /** Fewest living members that are ever a garrison, however small the squad started: one marine is a lookout. */
    static final int MIN_GARRISON_MEMBERS = 2;
    /** Pulses a breached place is left unmanned. UNTUNED. */
    static final int BREACH_LOCKOUT_PULSES = 16;
    /**
     * How near (Chebyshev cells) to an arrived squad a believed contact inside its run must be to count as the squad
     * being overrun: a run can be dozens of cells long, and a report at its far end is a flank threat, not a breach of
     * the stretch this squad holds. UNTUNED.
     */
    static final int BREACH_RANGE_CELLS = 16;
    /** A position that is not known; such a squad is breached by any contact inside its run. */
    static final int NO_POSITION = Integer.MIN_VALUE;
    /** A believed contact lighter than this (confidence times strength) wakes nothing: a works crew, a fading report. */
    static final float MIN_CONTACT_WEIGHT = 0.5f;
    /** Pulses after a shift before the same squad may shift again, so a wavering attack does not walk it to and fro. UNTUNED. */
    static final int SHIFT_COOLDOWN_PULSES = 4;

    /**
     * Fewest living members at which a squad raised at {@code originalMembers} is still a garrison: half its starting
     * strength, rounded up, and never fewer than {@link #MIN_GARRISON_MEMBERS}. A four-man section holds on with two, a
     * twelve-man squad with six. An unknown start ({@code 0}) reads as its present strength. UNTUNED.
     */
    static int usefulFloor(int originalMembers, int aliveMembers) {
        int established = Math.max(originalMembers, aliveMembers);
        return Math.max(MIN_GARRISON_MEMBERS, established - established / 2);
    }

    /**
     * Believed hostile pressure at an influence block, and the floor the side's own alarms keep under it. Where runs are
     * cut into stretches the two are read apart (see the class comment); otherwise a block reads the greater of them.
     */
    @FunctionalInterface
    interface Pressure {
        float hostileAt(int blockX, int blockY);

        /** The alarm floor on the block; none by default. */
        default float floorAt(int blockX, int blockY) {
            return 0f;
        }
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
    record Garrison(int squadId, int garrisonable, boolean releasable, boolean arrived, int aliveMembers,
                    int cellX, int cellY, int originalMembers) {
        /** Whether it is still strong enough to be a garrison. */
        boolean useful() { return aliveMembers >= usefulFloor(originalMembers, aliveMembers); }


        /** A squad of unknown position. */
        /** A squad whose starting strength is not known. */
        Garrison(int squadId, int garrisonable, boolean releasable, boolean arrived, int aliveMembers, int cellX, int cellY) {
            this(squadId, garrisonable, releasable, arrived, aliveMembers, cellX, cellY, 0);
        }

        Garrison(int squadId, int garrisonable, boolean releasable, boolean arrived, int aliveMembers) {
            this(squadId, garrisonable, releasable, arrived, aliveMembers, NO_POSITION, NO_POSITION);
        }

        /** A squad on its place at full strength. */
        Garrison(int squadId, int garrisonable, boolean releasable) {
            this(squadId, garrisonable, releasable, true, Integer.MAX_VALUE);
        }
    }

    /**
     * A free patrol and where it stands. A {@code staged} one waits at a fort's works for exactly this call: it is sent
     * without regard to the reserve floor, which protects the roving pool.
     */
    record Free(int squadId, int x, int y, boolean staged, int aliveMembers, int originalMembers) {
        /** A patrol whose starting strength is not known. */
        Free(int squadId, int x, int y, boolean staged, int aliveMembers) {
            this(squadId, x, y, staged, aliveMembers, 0);
        }

        /** Whether it is strong enough to be sent as a garrison. */
        boolean useful() { return aliveMembers >= usefulFloor(originalMembers, aliveMembers); }


        /** A patrol of full strength. */
        Free(int squadId, int x, int y, boolean staged) {
            this(squadId, x, y, staged, Integer.MAX_VALUE);
        }

        /** A roving patrol. */
        Free(int squadId, int x, int y) {
            this(squadId, x, y, false);
        }
    }

    /** A squad to send to a garrisonable. */
    record Man(int squadId, int garrisonable) { }

    /** A garrison moving from its stretch to the neighbouring one of the same run the attack has moved onto. */
    record Shift(int squadId, int from, int to) { }

    private record Pending(int cellX, int cellY, int pulsesLeft) { }

    private final Map<Integer, Integer> cool = new HashMap<>();
    private final Map<Integer, Pending> pending = new HashMap<>();
    /** Pulses each squad has spent closing on its place; only squads not yet arrived appear. */
    private final Map<Integer, Integer> closing = new HashMap<>();
    /** Breached places (by packed centre cell) and the pulses left before they may be manned again. */
    private final Map<Long, Integer> lockout = new HashMap<>();
    private final List<Integer> fallBack = new ArrayList<>();
    private final List<Shift> shifts = new ArrayList<>();
    /** The pulse ({@link #generation}) each squad last shifted on; only squads that have shifted appear. */
    private final Map<Integer, Integer> shiftedAt = new HashMap<>();
    /** Forts (by id) and fortless runs (by {@code -1 - run}) with a place woken this pulse. */
    private final Set<Integer> wokenGroups = new HashSet<>();

    private GarrisonableRegistry bound;
    private int[] placeStamp = new int[0];
    private int[] pressureStamp = new int[0];
    private float[] pressureOf = new float[0];
    /** Belief on a stretch's own blocks alone, beside {@link #pressureOf}: which of several equally pressed stretches faces the attack. */
    private float[] ownOf = new float[0];
    private int[] blockStamp = new int[0];
    private int generation;

    // Between evaluate and assign within one pulse.
    private GarrisonableRegistry pulseRegistry;
    private Pressure pulseField;
    private int[] hot = new int[0];
    private int hotCount;
    private int[] warm = new int[0];
    private int warmCount;
    private int[] woken = new int[0];
    private int wokenCount;
    private final Map<Integer, Integer> have = new HashMap<>();
    private int considered;
    private int blockReads;
    private int etaEvals;
    /** Whether the last {@link #nearest} skipped a roving patrol only because taking it would empty the reserve. */
    private boolean barredByFloor;
    /** The area-reservation preference of the {@link #assign} in progress, or {@code null}. */
    private Eligibility pulseReserved;

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
        shiftedAt.remove(squadId);
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
     * Garrisons {@link #evaluate} moved onto a neighbouring stretch: give each squad the order for its new stretch. They
     * are neither released nor falling back, and they count as manning the new stretch from this pulse.
     */
    List<Shift> shifting() {
        return shifts;
    }

    /**
     * The places the last {@link #evaluate} found under at least {@link #FLOW_PRESSURE} of believed pressure (a superset
     * of the hot ones), for the caller to decide where the pool should be gathering. Valid until the next evaluate.
     */
    int[] warmPlaces() {
        return Arrays.copyOf(warm, warmCount);
    }

    /** The places the last {@link #evaluate} found at or above {@link #START_PRESSURE}, most pressed first. Valid until the next evaluate. */
    int[] hotPlaces() {
        return Arrays.copyOf(hot, hotCount);
    }

    /** Whether any squad manned or was ordered to man this place as of the last {@link #evaluate} (and since, via {@link #assign}). */
    boolean isManned(int garrisonable) {
        return have.getOrDefault(garrisonable, 0) > 0;
    }

    /**
     * Counts each of these places as manned for the rest of this pulse: buildings their own garrison still stands in.
     * Call after {@link #evaluate} and before {@link #assign}. Pickets before massing then sends such a place a squad
     * only as its second, under the pressure that wants one, and a building whose garrison is gone is manned like any
     * other place.
     */
    void heldByOwnGarrison(Iterable<Integer> places) {
        for (int place : places) have.merge(place, 1, Integer::sum);
    }

    /** Whether the place with this centre cell is held unmanned after a breach; for tests and evidence. */
    boolean lockedOut(int cellX, int cellY) {
        return lockout.containsKey(pack(cellX, cellY));
    }

    /**
     * The order that mans a garrisonable: for a stretch, an area defence on its circle with its claims kept to the
     * stretch's run; for a building's envelope, a hold of the building that carries the envelope's centre and circle, so
     * the squad stands to at its windows and doors by the frontage laws and walks its rooms when they are quiet or
     * entered.
     */
    static ObjectiveAssignment orderFor(int squadId, int place, GarrisonableRegistry places) {
        if (places.isEnvelope(place)) {
            return ObjectiveAssignment.holdEnvelope(squadId, places.building(place), places.centreX(place),
                    places.centreY(place), places.radius(place));
        }
        return ObjectiveAssignment.defendRun(squadId, places.centreX(place), places.centreY(place),
                places.radius(place), places.runOf(place));
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
        shifts.clear();
        wokenGroups.clear();
        pulseRegistry = registry;
        pulseField = field;
        ageOrders();

        int contactBlocks = 0;
        wokenCount = 0;
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
                wokenGroups.add(group(registry, g));
                if (wokenCount == woken.length) woken = Arrays.copyOf(woken, Math.max(4, woken.length * 2));
                woken[wokenCount++] = g;
            }
        }
        // Priced only once every woken place is known: whether a fort's alarm floor stands depends on all of them.
        for (int i = 0; i < wokenCount; i++) {
            int g = woken[i];
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

        int breachReads = 0;
        // Believed contacts standing in a manned run, by run. One cell read per contact, and only while some place is held.
        Map<Integer, List<Integer>> inRun = Map.of();
        if (!held.isEmpty() && contactCount > 0) {
            // By run, not by stretch: an enemy in the trench a few cells along, past the stretch's end, is still in it.
            Set<Integer> heldRuns = new HashSet<>();
            for (Garrison squad : held) heldRuns.add(registry.runOf(squad.garrisonable()));
            inRun = new HashMap<>();
            for (int i = 0; i < contactCount; i++) {
                breachReads++;
                int place = registry.idAt(contactXY[2 * i], contactXY[2 * i + 1]);
                int run = place < 0 ? -1 : registry.runOf(place);
                if (run >= 0 && heldRuns.contains(run)) inRun.computeIfAbsent(run, k -> new ArrayList<>()).add(i);
            }
        }

        Set<Integer> present = new HashSet<>();
        Set<Integer> manned = new HashSet<>();
        for (Garrison squad : held) manned.add(squad.garrisonable());
        List<Garrison> ordered = new ArrayList<>(held);
        ordered.sort(Comparator.comparingInt(Garrison::garrisonable).thenComparingInt(Garrison::squadId));
        List<Integer> release = new ArrayList<>();
        int deferred = 0;
        for (int from = 0; from < ordered.size(); ) {
            int g = ordered.get(from).garrisonable();
            int to = from;
            while (to < ordered.size() && ordered.get(to).garrisonable() == g) to++;
            List<Integer> inside = inRun.get(registry.runOf(g));
            boolean lockPlace = false;
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
                boolean overrun = inside != null && overrunBy(squad, inside, contactXY, registry);
                if (overrun) lockPlace = true;
                boolean lost = overrun || !squad.useful();
                if (squad.arrived()) {
                    closing.remove(squad.squadId());
                    int onto = lost || !squad.releasable() ? -1 : shiftTarget(squad.squadId(), g, manned);
                    if (lost) {
                        fallBack.add(squad.squadId());
                        stays--;
                    } else if (onto >= 0) {
                        shifts.add(new Shift(squad.squadId(), g, onto));
                        shiftedAt.put(squad.squadId(), generation);
                        manned.add(onto);
                        have.merge(onto, 1, Integer::sum);
                        cool.remove(onto);
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
            if (lockPlace) lockout.put(pack(registry.centreX(g), registry.centreY(g)), BREACH_LOCKOUT_PULSES);
            if (stays == 0) cool.remove(g);
            if (stays > 0) have.merge(g, stays, Integer::sum);
            from = to;
        }
        closing.keySet().retainAll(present);
        shiftedAt.keySet().retainAll(present);
        release.sort(Integer::compare);
        fallBack.sort(Integer::compare);

        sortHot();
        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_CONTACT_BLOCK, contactBlocks);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_RELEASE, release.size());
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_FALL_BACK, fallBack.size());
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_SHIFT, shifts.size());
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
        return assign(free, eligible, null);
    }

    /**
     * As {@link #assign(List, Eligibility)}, but a squad the {@code reserved} test names for a place is tried before any
     * other, nearest first within each group: manning draws on the squads already reserved to the place's own area
     * before it reaches for anybody else. {@code null} means no preference.
     */
    List<Man> assign(List<Free> free, Eligibility eligible, Eligibility reserved) {
        return assign(free, eligible, reserved, MAX_NEW_PER_PULSE);
    }

    /**
     * As {@link #assign(List, Eligibility, Eligibility)}, sending at most {@code budget} squads this pulse (never more
     * than {@link #MAX_NEW_PER_PULSE}): a caller that may only spend part of its force on holding ground says how much
     * is left of that share. Pickets before massing still holds within the budget.
     */
    List<Man> assign(List<Free> free, Eligibility eligible, Eligibility reserved, int budget) {
        int most = Math.max(0, Math.min(MAX_NEW_PER_PULSE, budget));
        this.pulseReserved = reserved;
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile != null ? System.nanoTime() : 0L;
        List<Man> out = new ArrayList<>();
        int refused = 0;
        int floorRefused = 0;
        if (hotCount > 0 && pulseRegistry != null) {
            boolean[] used = new boolean[free.size()];
            int left = 0;
            for (Free squad : free) if (!squad.staged()) left++;
            boolean capped = false;
            for (int i = 0; i < hotCount; i++) {
                int g = hot[i];
                if (have.getOrDefault(g, 0) > 0 || lockedOut(pulseRegistry.centreX(g), pulseRegistry.centreY(g))) continue;
                if (out.size() >= most) {
                    capped = true;
                    break;
                }
                int pick = nearest(free, used, g, eligible, left > RESERVE_FLOOR);
                if (pick < 0) {
                    refused++;
                    if (barredByFloor) floorRefused++;
                    continue;
                }
                used[pick] = true;
                if (!free.get(pick).staged()) left--;
                have.put(g, 1);
                out.add(new Man(free.get(pick).squadId(), g));
            }
            if (!capped) {
                for (int i = 0; i < hotCount && out.size() < most; i++) {
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
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_FLOOR_REFUSED, floorRefused);
            profile.record(TickInnerProfile.Bucket.GARRISON_EVAL, System.nanoTime() - started);
        }
        pulseRegistry = null;
        pulseField = null;
        pulseReserved = null;
        return out;
    }

    /**
     * Whether this squad is overrun: an arrived one by a contact in its run near it; one still closing by any contact in
     * its run, or, where runs are cut, only by one in the stretch it is bound for or a neighbour of it, since an enemy
     * elsewhere on a long belt is pressing somebody else's stretch.
     */
    private static boolean overrunBy(Garrison squad, List<Integer> inside, int[] contactXY, GarrisonableRegistry registry) {
        if (squad.arrived() && squad.cellX() != NO_POSITION) {
            for (int ci : inside) if (near(squad, contactXY[2 * ci], contactXY[2 * ci + 1])) return true;
            return false;
        }
        if (!registry.stretched()) return true;
        int g = squad.garrisonable();
        for (int ci : inside) {
            int at = registry.idAt(contactXY[2 * ci], contactXY[2 * ci + 1]);
            if (at == g) return true;
            for (int n : registry.neighbours(g)) if (at == n) return true;
        }
        return false;
    }

    private static boolean near(Garrison squad, int x, int y) {
        return squad.cellX() == NO_POSITION
                || Math.max(Math.abs(squad.cellX() - x), Math.abs(squad.cellY() - y)) <= BREACH_RANGE_CELLS;
    }

    /**
     * Believed pressure on a place as this pulse prices it, for an {@link Eligibility} test during {@link #assign}; zero
     * outside a pulse.
     */
    float pressureOn(int g) {
        return pulseRegistry == null ? 0f : pressure(g);
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
        barredByFloor = false;
        int cx = pulseRegistry.centreX(g);
        int cy = pulseRegistry.centreY(g);
        Integer[] order = new Integer[free.size()];
        int n = 0;
        for (int i = 0; i < used.length; i++) if (!used[i]) order[n++] = i;
        Integer[] candidates = Arrays.copyOf(order, n);
        Eligibility reserved = pulseReserved;
        boolean[] ownSquad = null;
        if (reserved != null) {
            ownSquad = new boolean[free.size()];
            for (int i : candidates) ownSquad[i] = reserved.test(free.get(i).squadId(), g);
        }
        final boolean[] own = ownSquad;
        Comparator<Integer> byDistance = Comparator
                .<Integer>comparingLong(i -> distanceSq(free.get(i), cx, cy))
                .thenComparingInt(i -> free.get(i).squadId());
        Arrays.sort(candidates, own == null ? byDistance
                : Comparator.<Integer>comparingInt(i -> own[i] ? 0 : 1).thenComparing(byDistance));
        boolean ownOutOfReach = false;
        for (int i : candidates) {
            Free squad = free.get(i);
            if (!squad.useful()) continue;
            boolean isOwn = own != null && own[i];
            if (isOwn && ownOutOfReach) continue;
            if (!squad.staged() && !mayTakeRoving) {
                barredByFloor = true;
                continue;
            }
            etaEvals++;
            if (etaTicks(squad.x(), squad.y(), cx, cy) > MAX_ETA_TICKS) {
                // Sorted by distance within a group, so everyone after this one in the group is farther still.
                if (isOwn) {
                    ownOutOfReach = true;
                    continue;
                }
                return -1;
            }
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

    /**
     * Hot places by pressure descending, then (a stretch reads its neighbours' belief, so several along a run can tie) by
     * pressure on its own blocks descending, the one facing the attack first, then id.
     */
    private void sortHot() {
        Integer[] boxed = new Integer[hotCount];
        for (int i = 0; i < hotCount; i++) boxed[i] = hot[i];
        Arrays.sort(boxed, Comparator.<Integer>comparingDouble(g -> -pressureOf[g])
                .thenComparingDouble(g -> -ownOf[g]).thenComparingInt(g -> g));
        for (int i = 0; i < hotCount; i++) hot[i] = boxed[i];
    }

    /**
     * The place's pressure, read once per pulse; what presses the place's own blocks is left in {@link #ownOf}. Over runs
     * the two are one: the highest of its covered blocks, each the greater of belief and floor. Over stretches: its own
     * reading is its belief, only if it was woken this pulse, and its floor, only if nothing of its fort (or, outside a
     * fort, of its run) was; its pressure is that or belief over its neighbours' blocks (the stretch either side along
     * the run), so the stretch an attack is coming along the trench toward is manned before the attack is on it.
     */
    private float pressure(int g) {
        if (pressureStamp[g] == generation) return pressureOf[g];
        considered++;
        float best = 0f;
        float own = 0f;
        int bw = pulseRegistry.blockWidth();
        if (!pulseRegistry.stretched()) {
            for (int block : pulseRegistry.coveredBlocks(g)) {
                blockReads++;
                int bx = block % bw;
                int by = block / bw;
                best = Math.max(best, Math.max(pulseField.hostileAt(bx, by), pulseField.floorAt(bx, by)));
            }
            own = best;
        } else {
            boolean believed = placeStamp[g] == generation;
            boolean floored = !wokenGroups.contains(group(pulseRegistry, g));
            for (int block : pulseRegistry.coveredBlocks(g)) {
                if (!believed && !floored) break;
                blockReads++;
                int bx = block % bw;
                int by = block / bw;
                if (believed) best = Math.max(best, pulseField.hostileAt(bx, by));
                if (floored) best = Math.max(best, pulseField.floorAt(bx, by));
            }
            own = best;
            // The attack along the trench: belief on the neighbouring stretches presses this one too.
            if (believed) {
                for (int n : pulseRegistry.neighbours(g)) {
                    for (int block : pulseRegistry.coveredBlocks(n)) {
                        blockReads++;
                        best = Math.max(best, pulseField.hostileAt(block % bw, block / bw));
                    }
                }
            }
        }
        pressureStamp[g] = generation;
        pressureOf[g] = best;
        ownOf[g] = own;
        return best;
    }

    /** What a place's alarm floor is decided over: its fort, or, outside one, its own run. */
    private static int group(GarrisonableRegistry registry, int g) {
        int fort = registry.fortId(g);
        return fort >= 0 ? fort : -1 - registry.runOf(g);
    }

    /**
     * The neighbouring stretch a squad arrived on {@code g} should shift onto this pulse, or -1: only where runs are cut,
     * {@code g} is below the manning level, the neighbour is at it, nobody mans or is bound for it, it is not locked out
     * after a breach, and the squad has not shifted in the last {@link #SHIFT_COOLDOWN_PULSES}. Both read on their own
     * blocks: the squad follows the attack onto the stretch it now faces. The most pressed neighbour, ties to the lower id.
     */
    private int shiftTarget(int squadId, int g, Set<Integer> manned) {
        if (!pulseRegistry.stretched()) return -1;
        pressure(g);
        if (ownOf[g] >= START_PRESSURE) return -1;
        Integer last = shiftedAt.get(squadId);
        if (last != null && generation - last < SHIFT_COOLDOWN_PULSES) return -1;
        int best = -1;
        float bestPrice = 0f;
        for (int t : pulseRegistry.neighbours(g)) {
            if (manned.contains(t) || lockedOut(pulseRegistry.centreX(t), pulseRegistry.centreY(t))) continue;
            pressure(t);
            float price = ownOf[t];
            if (price < START_PRESSURE) continue;
            if (best < 0 || price > bestPrice) {
                best = t;
                bestPrice = price;
            }
        }
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
        ownOf = new float[registry.count()];
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
