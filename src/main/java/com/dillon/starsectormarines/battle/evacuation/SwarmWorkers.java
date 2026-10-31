package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.creep.CreepVegetation;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The nest's workers (`colony-breach-nouns.md`, Swarm strain: Skitterer): a living bore broods skitterers out of its
 * mouth under a cap of its own, and each one does the first job that applies, every one of them something the
 * company can see and stop by killing it.
 *
 * <ol>
 *   <li><b>Flee</b> from a marine or an ally near it, or from a hurt: drop the load and scatter.</li>
 *   <li><b>Haul</b> a loose body in its nest home, as a carrier ({@link SwarmHarvest}): only a share of a bore's
 *   brood at once, its own dead only from near the mouth, and never a body a skitterer just fled from.</li>
 *   <li><b>Repair</b> a wounded growth of its nest: its bore, colonies, outposts, ground colonies or spire.</li>
 *   <li><b>Tend the spire</b>: carry a feed from the mouth to its bore's brood spire, shortening its wait.</li>
 *   <li><b>Grow</b>: re-seed burnt ground its bore's creep claims, strip a creep-held tree to its death and bring
 *   the pulp home, stand on a pending outpost, or failing all that mill at the mouth feeding the creep.</li>
 * </ol>
 *
 * Skitterers are outside the director's population and every surge: the brood cap is their whole price.
 *
 * <p>Serial: runs once a tick after the packs, deciding and applying every effect here, so the parallel unit
 * update only reads a worker's {@link Job} and walks to it ({@link SwarmWorkerBehavior}). Deterministic: workers in
 * brood order, no random draw. {@code -Dbattle.swarm.workers=false} is the control: no skitterers.
 */
public final class SwarmWorkers {

    /** Control-run switch; bores brood skitterers unless it reads {@code false}. */
    public static final String PROPERTY = "battle.swarm.workers";
    /**
     * Experiment switch for sharing out the haul: a bore sends at most {@link #haulersFor} of its skitterers to
     * bodies, fetches its own dead only from {@link #RAV_HAUL_REACH} of its work range, and leaves a body a
     * skitterer fled from for {@link #SHUN_SECONDS}. {@code false} is the control: every skitterer hauls first.
     * Remove it with the losing arm once a Colony Breach {@code commanderEvidence} run with and without it reads
     * budding, pulp and skitterer losses.
     */
    public static final String HAUL_SHARE_PROPERTY = "battle.swarm.haulShare";

    /** Seconds between two skitterers out of one bore while it is under its brood. */
    public static final float BROOD_SECONDS = 12f;
    /** A marine or ally on the ground this near sends a skitterer running, cells. */
    public static final float THREAT_RANGE = 7f;
    /** A skitterer hurt this recently runs, seconds. */
    public static final float HURT_SECONDS = 3f;
    /** How long one scatter lasts once nothing is near, seconds, and how far it runs, cells. */
    public static final float FLEE_SECONDS = 4f, FLEE_CELLS = 8f;
    /** A bore with no creep keeps its workers within this of its mouth, cells. */
    public static final float BARE_WORK_RANGE = 16f;
    /** A growth below this share of its structure is mended. */
    public static final float REPAIR_BELOW = 0.9f;
    /** Structure one skitterer restores a second, and how many work on one body at once. */
    public static final float REPAIR_PER_SECOND = 8f;
    public static final int REPAIRERS_PER_BODY = 2;
    /** The share of its bore's work range within which a skitterer fetches one of the swarm's own dead. */
    public static final float RAV_HAUL_REACH = 0.5f;
    /** Seconds a body a skitterer fled from is left before the nest sends for it again. */
    public static final float SHUN_SECONDS = 15f;
    /** Seconds one skitterer's feed takes off its spire's wait, and how many carry feeds at once. */
    public static final float SPIRE_FEED_SECONDS = 8f;
    public static final int TENDERS_PER_SPIRE = 2;
    /** Seconds a skitterer works a burnt cell before its refusal ends, and the cells round it that it frees too. */
    public static final float REGROW_SECONDS = 4f, REGROW_RADIUS = 1.5f;
    /** Coverage a skitterer strips off a tree's remaining hold a second, and how many strip one tree. */
    public static final int STRIP_PER_SECOND = 3;
    public static final int STRIPPERS_PER_TREE = 2;
    /** A tree a skitterer saw die is carried home as this much biomass, in colonists. */
    public static final float TREE_PULP = 0.25f;
    /** Skitterers on one pending outpost. */
    public static final int TENDERS_PER_OUTPOST = 2;
    /** A skitterer milling at the mouth feeds its bore's creep a second of growth for every this many seconds. */
    public static final float MILL_FEED_EVERY_SECONDS = 10f;
    /** How far from the mouth a skitterer mills, cells. */
    public static final float MILL_RADIUS = 4f;
    /** A skitterer this near its work's cell is at it, cells. */
    public static final float ARRIVE = 1.3f;
    /** Ticks between two decisions of one worker. */
    static final int DECIDE_TICKS = 30;
    /** Ticks between two looks for a threat round one worker. */
    static final int THREAT_TICKS = 6;
    /** Ticks between two scans of a bore's burnt ground. */
    static final int BURN_SCAN_TICKS = 60;

    /** What a skitterer is doing. */
    public enum Job { FLEE, HAUL, REPAIR, SPIRE_FETCH, SPIRE, REGROW, STRIP, PULP, OUTPOST, MILL }

    /** One skitterer: its bore, its job and the cell it walks to. Written serially; read by the unit update. */
    public static final class Worker {
        public final long id;
        SwarmBore bore;
        Job job = Job.MILL;
        int targetX, targetY;
        /** The structure, body or tree index the job is about. */
        long subject;
        int workTicks;
        int fleeUntil = Integer.MIN_VALUE / 2;
        int hurtTick = Integer.MIN_VALUE / 2;
        int nextDecide;
        float lastHp;
        int millTicks;
        /** Whether it stood at its work this tick: what draws it head-down over the work. */
        boolean atWork;

        Worker(long id, SwarmBore bore, float hp, int tick) {
            this.id = id;
            this.bore = bore;
            this.lastHp = hp;
            this.nextDecide = tick;
        }

        public Job job() { return job; }
        public int targetX() { return targetX; }
        public int targetY() { return targetY; }
        public SwarmBore bore() { return bore; }
    }

    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    private final boolean haulShare = !"false".equalsIgnoreCase(System.getProperty(HAUL_SHARE_PROPERTY, "true"));
    private final LinkedHashMap<Long, Worker> workers = new LinkedHashMap<>();
    /** Bodies a skitterer fled from, and the tick until which the nest leaves each. */
    private final Map<Long, Integer> shunned = new HashMap<>();
    private final Map<Long, Integer> nextBrood = new HashMap<>();
    private final Map<Long, int[]> burnt = new HashMap<>();
    private int nextBurnScan;
    private int brooded, fled, spireFeeds, cellsRegrown, outpostTicks, treesStripped, pulpDelivered, millFeeds;
    private float repaired;

    public boolean enabled() { return enabled; }

    /** The skitterer with body {@code id}, or {@code null}. Read-only from the parallel update. */
    public Worker of(long id) { return workers.get(id); }

    /** Every living skitterer, in brood order. */
    public Collection<Worker> workers() { return Collections.unmodifiableCollection(workers.values()); }

    /** What a skitterer's body shows of its job: nothing, head-down over the work, or a load in its mandibles. */
    public enum Show { NONE, WORKING, FEED, PULP }

    /**
     * What body {@code id} shows: head-down while it stands mending, regrowing, stripping or tending an outpost; a
     * feed in its mandibles on its way to the spire; pulp on its way home from a tree. Presentation only.
     */
    public Show show(long id) {
        Worker w = workers.get(id);
        if (w == null) return Show.NONE;
        return switch (w.job) {
            case SPIRE -> Show.FEED;
            case PULP -> Show.PULP;
            case REPAIR, REGROW, STRIP, OUTPOST -> w.atWork ? Show.WORKING : Show.NONE;
            default -> Show.NONE;
        };
    }

    /** Whether a skitterer may take up a loose body: it is hauling, and not running. */
    public boolean mayHaul(long id) {
        Worker w = workers.get(id);
        return w != null && w.job == Job.HAUL;
    }

    /** Skitterers a bore of {@code tier} holds round it: one at fissure, two at bore, three at maw, two more for a mother. */
    public static int broodFor(BoreTier tier, boolean mother) {
        int base = switch (tier) {
            case FISSURE -> 1;
            case BORE -> 2;
            case MAW -> 3;
        };
        return mother ? base + 2 : base;
    }

    /**
     * Whether a skitterer runs: a marine or ally on the ground within {@link #THREAT_RANGE}
     * ({@code threatDistance}, or a negative value for none), or a hurt of its own within {@link #HURT_SECONDS}.
     */
    public static boolean flees(float threatDistance, int ticksSinceHurt) {
        return threatDistance >= 0f && threatDistance <= THREAT_RANGE
                || ticksSinceHurt >= 0 && ticksSinceHurt < Math.round(HURT_SECONDS / BattleSimulation.TICK_DT);
    }

    /**
     * How many of a bore's {@code living} skitterers may haul at once: about a third, never none. Bodies lie where
     * the fighting is, so the rest stay home at the nest's other work.
     */
    public static int haulersFor(int living) {
        return Math.max(1, (living + 1) / 3);
    }

    /**
     * Whether a skitterer goes for a body {@code distance} from its bore's mouth: prey anywhere in its
     * {@code workRange}, one of the swarm's own dead (worth far less) only from the nearer {@link #RAV_HAUL_REACH}.
     */
    public static boolean fetches(boolean ravBody, float distance, float workRange) {
        return distance <= (ravBody ? workRange * RAV_HAUL_REACH : workRange);
    }

    /** A body's structure share after {@code seconds} of {@code repairers} at work: never past full. */
    public static float mended(float hp, float maxHp, int repairers, float seconds) {
        return Math.min(maxHp, hp + REPAIR_PER_SECOND * Math.max(0, repairers) * Math.max(0f, seconds));
    }

    /**
     * An old bloom's nests are full: once the creep is matured at setup, each living bore stands with its whole brood
     * round its mouth.
     */
    public void populate(BattleSimulation sim) {
        if (!enabled) return;
        int tick = sim.getSimTickIndex();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed()) continue;
            int want = broodFor(bore.tier(), bore.mother()) - living(bore);
            for (int i = 0; i < want; i++) brood(sim, bore, tick, true);
            nextBrood.put(bore.entityId(), tick + broodTicks());
        }
    }

    /** Once a tick, serially, after the packs: brood, decide, and do each worker's work. */
    public void tick(BattleSimulation sim) {
        if (!enabled) return;
        int tick = sim.getSimTickIndex();
        prune(sim);
        shunned.values().removeIf(until -> until <= tick);
        broodAll(sim, tick);
        if (tick >= nextBurnScan) {
            nextBurnScan = tick + BURN_SCAN_TICKS;
            scanBurns(sim);
        }
        for (Worker w : new ArrayList<>(workers.values())) step(sim, w, tick);
    }

    /** One line of what the workers did, for evidence. */
    public String summary(BattleSimulation sim) {
        return String.format(Locale.ROOT,
                "skitterers brooded=%d alive=%d fled=%d hauled=%d repaired=%.0f spireFeeds=%d regrown=%d "
                        + "outpostSeconds=%.0f treesStripped=%d pulp=%d millFeeds=%d",
                brooded, workers.size(), fled, sim.swarmHarvest().hauledByWorkers(), repaired, spireFeeds,
                cellsRegrown, outpostTicks * BattleSimulation.TICK_DT, treesStripped, pulpDelivered, millFeeds);
    }

    public int brooded() { return brooded; }
    public int fled() { return fled; }
    public float repaired() { return repaired; }
    public int spireFeeds() { return spireFeeds; }
    public int cellsRegrown() { return cellsRegrown; }
    public int outpostTicks() { return outpostTicks; }
    public int treesStripped() { return treesStripped; }
    public int pulpDelivered() { return pulpDelivered; }
    public int millFeeds() { return millFeeds; }

    // ---- brood -------------------------------------------------------------------------------

    private static int broodTicks() { return Math.round(BROOD_SECONDS / BattleSimulation.TICK_DT); }

    private void broodAll(BattleSimulation sim, int tick) {
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed()) continue;
            // the clock starts with the bore's first surge, so a fresh bore's first act is to fight; a sleeping one broods
            if (bore.surges() == 0 && !bore.dormant()) continue;
            Integer next = nextBrood.get(bore.entityId());
            if (next == null) {
                nextBrood.put(bore.entityId(), tick + broodTicks());
                continue;
            }
            if (tick < next) continue;
            if (living(bore) < broodFor(bore.tier(), bore.mother()) && brood(sim, bore, tick, false)) {
                nextBrood.put(bore.entityId(), tick + broodTicks());
            }
        }
    }

    /** One skitterer out of {@code bore}'s mouth onto a free mouth cell; {@code spread} lets setup use cells round it. */
    private boolean brood(BattleSimulation sim, SwarmBore bore, int tick, boolean spread) {
        int[] cell = freeCell(sim, bore, spread);
        if (cell == null) return false;
        String name = (bore.mother() ? "Mother Bore" : "Bore " + bore.beatKey()) + " Skitterer " + (brooded + 1);
        long id = SwarmStrains.spawn(sim, name, UnitType.SWARM_WORKER, cell[0], cell[1]);
        workers.put(id, new Worker(id, bore, sim.world().hp(id), tick + (int) (id % DECIDE_TICKS)));
        // Brooded in the field it climbs out of the hole; a nest full at setup stands with its brood already out.
        if (!spread) sim.swarmEmergence().begin(sim, id, bore.x() + 0.5f, bore.y() + 0.5f);
        brooded++;
        return true;
    }

    private static int[] freeCell(BattleSimulation sim, SwarmBore bore, boolean spread) {
        NavigationGrid grid = sim.getGrid();
        byte[] occupancy = sim.getOccupancyMap();
        for (int[] c : SwarmBores.mouthCells(grid, bore)) {
            if ((occupancy[grid.index(c[0], c[1])] & 0xFF) == 0 && !SwarmBores.nearBody(sim, c[0], c[1], 0.6f)) return c;
        }
        if (!spread) return null;
        for (int r = SwarmBores.MOUTH_RADIUS + 1; r <= SwarmBores.MOUTH_RADIUS + 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = bore.x() + dx, y = bore.y() + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                    if ((occupancy[grid.index(x, y)] & 0xFF) != 0 || SwarmBores.nearBody(sim, x, y, 0.6f)) continue;
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private int living(SwarmBore bore) {
        int n = 0;
        for (Worker w : workers.values()) if (w.bore == bore) n++;
        return n;
    }

    private void prune(BattleSimulation sim) {
        workers.values().removeIf(w -> sim.resolveUnit(w.id) == 0L || !sim.world().isAlive(w.id));
    }

    // ---- one worker --------------------------------------------------------------------------

    private void step(BattleSimulation sim, Worker w, int tick) {
        float hp = sim.world().hp(w.id);
        if (hp < w.lastHp) w.hurtTick = tick;
        w.lastHp = hp;
        if (w.bore.collapsed()) {
            SwarmBore next = sim.swarmBores().nearestLiving(sim.world().x(w.id), sim.world().y(w.id));
            if (next != null) w.bore = next;
        }
        if ((tick + w.id) % THREAT_TICKS == 0 || tick - w.hurtTick == 0) {
            long threat = nearestThreat(sim, w);
            float distance = threat == 0L ? -1f : distance(sim, w.id, sim.world().x(threat), sim.world().y(threat));
            if (flees(distance, tick - w.hurtTick)) {
                scatter(sim, w, threat, tick);
                return;
            }
        }
        if (w.job == Job.FLEE) {
            if (tick < w.fleeUntil) return;
            w.nextDecide = tick;
        }
        if (sim.swarmHarvest().isCarrier(w.id)) return;
        if (w.job == Job.STRIP && sim.creepVegetation().dead((int) w.subject)) {
            // the tree it worked has died: if it was at it, it carries the pulp home
            if (distance(sim, w.id, w.targetX + 0.5f, w.targetY + 0.5f) <= ARRIVE) {
                treesStripped++;
                assign(w, Job.PULP, 0L, w.bore.x(), w.bore.y());
            }
        }
        if (tick >= w.nextDecide || !valid(sim, w, tick)) decide(sim, w, tick);
        work(sim, w, tick);
    }

    private static long nearestThreat(BattleSimulation sim, Worker w) {
        float x = sim.world().x(w.id), y = sim.world().y(w.id);
        LongBucket near = new LongBucket();
        long best = 0L;
        float bestDistance = THREAT_RANGE * THREAT_RANGE;
        for (Faction side : new Faction[]{Faction.MARINE, Faction.ALLY}) {
            sim.getUnitIndex().gatherFaction(x, y, THREAT_RANGE, side, near);
            for (int i = 0; i < near.size; i++) {
                long u = near.ids[i];
                if (sim.resolveUnit(u) == 0L || !sim.world().isAlive(u) || !sim.world().hasPosition(u)) continue;
                if (sim.identity().type(u).isFlier()) continue;
                float dx = sim.world().x(u) - x, dy = sim.world().y(u) - y;
                float d = dx * dx + dy * dy;
                if (d < bestDistance || d == bestDistance && u < best) {
                    best = u;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    /** Drop what it hauls and run straight away from the threat, or from where it was hurt, for a few seconds. */
    private void scatter(BattleSimulation sim, Worker w, long threat, int tick) {
        if (w.job != Job.FLEE) fled++;
        // the body it was going for or dragging lies in the fight: the nest leaves it for a while
        if (haulShare && w.job == Job.HAUL) shunned.put(w.subject, tick + Math.round(SHUN_SECONDS / BattleSimulation.TICK_DT));
        sim.swarmHarvest().drop(sim, w.id);
        float x = sim.world().x(w.id), y = sim.world().y(w.id);
        float ax, ay;
        if (threat != 0L) {
            ax = x - sim.world().x(threat);
            ay = y - sim.world().y(threat);
        } else {
            ax = x - (w.bore.x() + 0.5f);
            ay = y - (w.bore.y() + 0.5f);
            // hurt by something unseen: away from where it was working, back toward the nest's far side
            ax = -ax;
            ay = -ay;
        }
        float length = (float) Math.sqrt(ax * ax + ay * ay);
        if (length < 1e-3f) {
            ax = 1f;
            ay = 0f;
            length = 1f;
        }
        int[] cell = walkableNear(sim, x + ax / length * FLEE_CELLS, y + ay / length * FLEE_CELLS);
        w.job = Job.FLEE;
        w.subject = 0L;
        w.targetX = cell != null ? cell[0] : sim.world().cellX(w.id);
        w.targetY = cell != null ? cell[1] : sim.world().cellY(w.id);
        w.fleeUntil = tick + Math.round(FLEE_SECONDS / BattleSimulation.TICK_DT);
    }

    /** Whether the worker's job still makes sense; a job that stopped is decided again at once. */
    private boolean valid(BattleSimulation sim, Worker w, int tick) {
        switch (w.job) {
            case HAUL: return sim.swarmHarvest().isLoose(w.subject) && sim.world().hasPosition(w.subject);
            case REPAIR: return standing(sim, w.subject)
                    && sim.world().hp(w.subject) < sim.world().maxHp(w.subject);
            case SPIRE_FETCH:
            case SPIRE: {
                BroodSpires.Spire s = sim.broodSpires().byEntity(w.subject);
                return s != null && standing(sim, s.entityId) && BroodSpires.waiting(s, tick);
            }
            case REGROW: {
                FireField fire = sim.fireFieldIfPresent();
                return fire != null && fire.refusesCreep((int) w.subject);
            }
            case STRIP: return !sim.creepVegetation().dead((int) w.subject);
            case OUTPOST: {
                int[] cell = sim.creepOutposts().pendingCell(w.bore.entityId());
                return cell != null && cell[0] == w.targetX && cell[1] == w.targetY;
            }
            default: return true;
        }
    }

    private void decide(BattleSimulation sim, Worker w, int tick) {
        w.nextDecide = tick + DECIDE_TICKS;
        Job before = w.job;
        long subjectBefore = w.subject;
        if (!w.bore.collapsed() && (decideHaul(sim, w) || decideRepair(sim, w) || decideSpire(sim, w, tick)
                || decideRegrow(sim, w) || decideStrip(sim, w) || decideOutpost(sim, w))) {
            if (w.job != before || w.subject != subjectBefore) w.workTicks = 0;
            return;
        }
        if (w.job == Job.PULP) return;
        mill(sim, w);
        if (before != Job.MILL) w.workTicks = 0;
    }

    private float workRange(SwarmBore bore) {
        return Math.max(BARE_WORK_RANGE, CreepGrowth.forTier(bore.tier().ordinal()).maxReachCells());
    }

    private boolean decideHaul(BattleSimulation sim, Worker w) {
        if (w.job == Job.PULP) return false;
        SwarmBore bore = w.bore;
        float mouthX = bore.x() + 0.5f, mouthY = bore.y() + 0.5f, range = workRange(bore);
        if (haulShare) {
            int haulers = 0;
            for (Worker o : workers.values()) if (o != w && o.bore == bore && o.job == Job.HAUL) haulers++;
            if (haulers >= haulersFor(living(bore))) return false;
        }
        int tick = sim.getSimTickIndex();
        long body = sim.swarmHarvest().nearestLoose(sim, mouthX, mouthY, range,
                b -> b != w.subject && claimedBy(Job.HAUL, b) > 0
                        || haulShare && (shunned.getOrDefault(b, Integer.MIN_VALUE) > tick
                        || !fetches(SwarmHarvest.isRavBody(sim, b),
                                distance(sim, b, mouthX, mouthY), range)));
        if (body == 0L) return false;
        assign(w, Job.HAUL, body, sim.world().cellX(body), sim.world().cellY(body));
        return true;
    }

    private boolean decideRepair(BattleSimulation sim, Worker w) {
        if (w.job == Job.PULP) return false;
        long best = 0L;
        float bestShare = REPAIR_BELOW;
        for (long id : nest(sim, w.bore)) {
            if (!standing(sim, id)) continue;
            float max = sim.world().maxHp(id);
            if (max <= 0f) continue;
            float share = sim.world().hp(id) / max;
            if (share >= bestShare) continue;
            int working = claimedBy(Job.REPAIR, id) - (w.job == Job.REPAIR && w.subject == id ? 1 : 0);
            if (working >= REPAIRERS_PER_BODY) continue;
            best = id;
            bestShare = share;
        }
        if (best == 0L) return false;
        int[] cell = beside(sim, best, w.id);
        if (cell == null) return false;
        assign(w, Job.REPAIR, best, cell[0], cell[1]);
        return true;
    }

    /** Every growth of a bore's nest: its body, its colonies and outposts, the ground colonies it grew, its spire. */
    private static List<Long> nest(BattleSimulation sim, SwarmBore bore) {
        List<Long> out = new ArrayList<>();
        out.add(bore.entityId());
        out.addAll(bore.colonies());
        out.addAll(bore.outposts());
        out.addAll(sim.groundColonies().of(bore.entityId()));
        for (BroodSpires.Spire s : sim.broodSpires().spires()) if (s.bore == bore) out.add(s.entityId);
        return out;
    }

    private boolean decideSpire(BattleSimulation sim, Worker w, int tick) {
        if (w.job == Job.PULP) return false;
        if ((w.job == Job.SPIRE_FETCH || w.job == Job.SPIRE) && valid(sim, w, tick)) return true;
        for (BroodSpires.Spire s : sim.broodSpires().spires()) {
            if (s.bore != w.bore || !standing(sim, s.entityId) || !BroodSpires.waiting(s, tick)) continue;
            if (claimedBy(Job.SPIRE_FETCH, s.entityId) + claimedBy(Job.SPIRE, s.entityId) >= TENDERS_PER_SPIRE) continue;
            assign(w, Job.SPIRE_FETCH, s.entityId, w.bore.x(), w.bore.y());
            return true;
        }
        return false;
    }

    private boolean decideRegrow(BattleSimulation sim, Worker w) {
        if (w.job == Job.PULP) return false;
        if (w.job == Job.REGROW && valid(sim, w, sim.getSimTickIndex())) return true;
        int[] cells = burnt.get(w.bore.entityId());
        FireField fire = sim.fireFieldIfPresent();
        if (cells == null || fire == null) return false;
        int width = sim.getGrid().getWidth();
        for (int idx : cells) {
            if (!fire.refusesCreep(idx) || nearFire(sim, fire, idx)) continue;
            if (takenNear(Job.REGROW, idx, width)) continue;
            assign(w, Job.REGROW, idx, idx % width, idx / width);
            return true;
        }
        return false;
    }

    private boolean decideStrip(BattleSimulation sim, Worker w) {
        if (w.job == Job.PULP) return true;
        if (w.job == Job.STRIP && valid(sim, w, sim.getSimTickIndex())) return true;
        SwarmBore bore = w.bore;
        int[] tree = sim.creepVegetation().nearestStrippable(sim, bore.x() + 0.5f, bore.y() + 0.5f, workRange(bore));
        if (tree == null || claimedBy(Job.STRIP, tree[0]) >= STRIPPERS_PER_TREE) return false;
        int[] cell = toward(sim, tree[1], tree[2], bore.x(), bore.y());
        if (cell == null) return false;
        assign(w, Job.STRIP, tree[0], cell[0], cell[1]);
        return true;
    }

    private boolean decideOutpost(BattleSimulation sim, Worker w) {
        int[] cell = sim.creepOutposts().pendingCell(w.bore.entityId());
        if (cell == null) return false;
        int working = claimedBy(Job.OUTPOST, w.bore.entityId()) - (w.job == Job.OUTPOST ? 1 : 0);
        if (working >= TENDERS_PER_OUTPOST) return false;
        assign(w, Job.OUTPOST, w.bore.entityId(), cell[0], cell[1]);
        return true;
    }

    /** Wander a few cells round the mouth, a new spot every decision. */
    private void mill(BattleSimulation sim, Worker w) {
        int tick = sim.getSimTickIndex();
        float cx = w.bore.collapsed() ? sim.world().x(w.id) : w.bore.x() + 0.5f;
        float cy = w.bore.collapsed() ? sim.world().y(w.id) : w.bore.y() + 0.5f;
        long h = SwarmBores.mix(w.id * 0x9E3779B97F4A7C15L ^ (tick / DECIDE_TICKS));
        double angle = 2.0 * Math.PI * SwarmBores.unit(h);
        float r = SwarmBores.MOUTH_RADIUS + (MILL_RADIUS - SwarmBores.MOUTH_RADIUS) * SwarmBores.unit(SwarmBores.mix(h));
        int[] cell = walkableNear(sim, cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r);
        w.job = Job.MILL;
        w.subject = 0L;
        w.targetX = cell != null ? cell[0] : sim.world().cellX(w.id);
        w.targetY = cell != null ? cell[1] : sim.world().cellY(w.id);
    }

    private static void assign(Worker w, Job job, long subject, int x, int y) {
        w.job = job;
        w.subject = subject;
        w.targetX = x;
        w.targetY = y;
    }

    /** How many workers hold {@code job} on {@code subject}. */
    private int claimedBy(Job job, long subject) {
        int n = 0;
        for (Worker w : workers.values()) if (w.job == job && w.subject == subject) n++;
        return n;
    }

    /** Whether another worker already works a burnt cell within two cells of {@code idx}. */
    private boolean takenNear(Job job, int idx, int width) {
        int x = idx % width, y = idx / width;
        for (Worker w : workers.values()) {
            if (w.job != job) continue;
            if (Math.abs(w.targetX - x) <= 2 && Math.abs(w.targetY - y) <= 2) return true;
        }
        return false;
    }

    // ---- work --------------------------------------------------------------------------------

    private void work(BattleSimulation sim, Worker w, int tick) {
        boolean there = distance(sim, w.id, w.targetX + 0.5f, w.targetY + 0.5f) <= ARRIVE;
        w.atWork = there;
        switch (w.job) {
            case REPAIR -> {
                if (!there) return;
                float hp = sim.world().hp(w.subject), max = sim.world().maxHp(w.subject);
                float next = mended(hp, max, 1, BattleSimulation.TICK_DT);
                sim.world().setHp(w.subject, next);
                repaired += next - hp;
            }
            case SPIRE_FETCH -> {
                // within the mouth's reach it has its feed; then it carries it to the spire
                if (distance(sim, w.id, w.bore.x() + 0.5f, w.bore.y() + 0.5f) > SwarmBores.MOUTH_RADIUS + ARRIVE) return;
                BroodSpires.Spire s = sim.broodSpires().byEntity(w.subject);
                int[] cell = s != null ? beside(sim, s.entityId, w.id) : null;
                if (cell != null) assign(w, Job.SPIRE, s.entityId, cell[0], cell[1]);
            }
            case SPIRE -> {
                if (!there) return;
                BroodSpires.Spire s = sim.broodSpires().byEntity(w.subject);
                if (s != null) sim.broodSpires().tend(s, SPIRE_FEED_SECONDS);
                spireFeeds++;
                w.nextDecide = tick;
                mill(sim, w);
            }
            case REGROW -> {
                if (!there) return;
                if (++w.workTicks < Math.round(REGROW_SECONDS / BattleSimulation.TICK_DT)) return;
                regrow(sim, (int) w.subject);
                w.workTicks = 0;
                w.nextDecide = tick;
            }
            case STRIP -> {
                if (!there) return;
                CreepVegetation trees = sim.creepVegetation();
                if (++w.workTicks % Math.round(1f / BattleSimulation.TICK_DT) == 0) {
                    trees.strip((int) w.subject, STRIP_PER_SECOND);
                }
            }
            case PULP -> {
                if (distance(sim, w.id, w.bore.x() + 0.5f, w.bore.y() + 0.5f) > SwarmHarvest.DELIVER_RADIUS) return;
                if (sim.swarmBores().feed(sim, w.bore, TREE_PULP)) pulpDelivered++;
                w.nextDecide = tick;
                mill(sim, w);
            }
            case OUTPOST -> {
                if (!there) return;
                sim.creepOutposts().tend(w.bore.entityId(), 1);
                outpostTicks++;
            }
            case MILL -> {
                if (w.bore.collapsed()) return;
                if (distance(sim, w.id, w.bore.x() + 0.5f, w.bore.y() + 0.5f) > MILL_RADIUS + ARRIVE) return;
                if (++w.millTicks >= Math.round(MILL_FEED_EVERY_SECONDS / BattleSimulation.TICK_DT)) {
                    w.millTicks = 0;
                    CreepField field = sim.creepFieldIfPresent();
                    if (field != null) {
                        field.feed(w.bore.entityId(), 1f);
                        millFeeds++;
                    }
                }
            }
            default -> { }
        }
    }

    /** Ends the refusal on {@code idx} and the burnt cells round it its bore's creep still claims. */
    private void regrow(BattleSimulation sim, int idx) {
        FireField fire = sim.fireFieldIfPresent();
        CreepField field = sim.creepFieldIfPresent();
        if (fire == null || field == null) return;
        NavigationGrid grid = sim.getGrid();
        int width = grid.getWidth(), x0 = idx % width, y0 = idx / width;
        int r = (int) Math.ceil(REGROW_RADIUS);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > REGROW_RADIUS * REGROW_RADIUS) continue;
                int x = x0 + dx, y = y0 + dy;
                if (!grid.inBounds(x, y)) continue;
                int i = y * width + x;
                if (field.claimed(i) && fire.regrow(i)) cellsRegrown++;
            }
        }
    }

    /**
     * Each living bore's burnt ground its creep still claims, nearest the mouth first: what its growers re-seed. Kept
     * empty while nothing has ever burnt.
     */
    private void scanBurns(BattleSimulation sim) {
        burnt.clear();
        FireField fire = sim.fireFieldIfPresent();
        CreepField field = sim.creepFieldIfPresent();
        if (fire == null || field == null || fire.burntCount() == 0) return;
        NavigationGrid grid = sim.getGrid();
        int width = grid.getWidth();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed()) continue;
            int range = (int) workRange(bore);
            List<int[]> found = new ArrayList<>();
            for (int dy = -range; dy <= range; dy++) {
                for (int dx = -range; dx <= range; dx++) {
                    int d2 = dx * dx + dy * dy;
                    if (d2 > range * range) continue;
                    int x = bore.x() + dx, y = bore.y() + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                    int idx = y * width + x;
                    if (fire.refusesCreep(idx) && field.claimed(idx)) found.add(new int[]{d2, idx});
                }
            }
            if (found.isEmpty()) continue;
            found.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
            int[] cells = new int[found.size()];
            for (int i = 0; i < cells.length; i++) cells[i] = found.get(i)[1];
            burnt.put(bore.entityId(), cells);
        }
    }

    /** Whether a cell beside {@code idx} is burning: a skitterer never steps into a fire. */
    private static boolean nearFire(BattleSimulation sim, FireField fire, int idx) {
        NavigationGrid grid = sim.getGrid();
        int width = grid.getWidth(), x0 = idx % width, y0 = idx / width;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = x0 + dx, y = y0 + dy;
                if (!grid.inBounds(x, y)) continue;
                int i = y * width + x;
                if (fire.holdsCreepBack(i) && !fire.refusesCreep(i)) return true;
            }
        }
        return false;
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** A walkable cell a step off {@code body} on {@code worker}'s side, where it stands to work on it. */
    private static int[] beside(BattleSimulation sim, long body, long worker) {
        float bx = sim.world().x(body), by = sim.world().y(body);
        float dx = sim.world().x(worker) - bx, dy = sim.world().y(worker) - by;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3f) return walkableNear(sim, bx + 1.5f, by);
        return walkableNear(sim, bx + dx / length * 1.5f, by + dy / length * 1.5f);
    }

    private static boolean standing(BattleSimulation sim, long id) {
        return id != 0L && sim.resolveUnit(id) != 0L && sim.world().isAlive(id);
    }

    private static float distance(BattleSimulation sim, long id, float x, float y) {
        float dx = sim.world().x(id) - x, dy = sim.world().y(id) - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** The first walkable cell from {@code (x, y)} stepping toward {@code (towardX, towardY)}: a tree's edge on the nest's side. */
    private static int[] toward(BattleSimulation sim, int x, int y, int towardX, int towardY) {
        NavigationGrid grid = sim.getGrid();
        float dx = towardX - x, dy = towardY - y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3f) return walkableNear(sim, x + 0.5f, y + 0.5f);
        for (int step = 0; step <= 8; step++) {
            int gx = (int) Math.floor(x + 0.5f + dx / length * step);
            int gy = (int) Math.floor(y + 0.5f + dy / length * step);
            if (grid.inBounds(gx, gy) && grid.isWalkable(gx, gy)) return new int[]{gx, gy};
        }
        return null;
    }

    /** The walkable cell nearest {@code (x, y)} within a few cells, or {@code null}. */
    private static int[] walkableNear(BattleSimulation sim, float x, float y) {
        NavigationGrid grid = sim.getGrid();
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        for (int r = 0; r <= 3; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int gx = cx + dx, gy = cy + dy;
                    if (grid.inBounds(gx, gy) && grid.isWalkable(gx, gy)) return new int[]{gx, gy};
                }
            }
        }
        return null;
    }
}
