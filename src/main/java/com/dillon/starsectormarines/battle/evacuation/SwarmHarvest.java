package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.FireTeamMorale;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.ArmorClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;

/**
 * The swarm's harvest (`colony-breach-nouns.md`, Harvest): a Runner that brings
 * down a colonist seizes the body and drags it to the nearest living bore,
 * where it is fed in through {@link SwarmBores#feed}; and now and then a
 * Runner's blow impales a living body instead and drags it off alive
 * (`colony-breach-nouns.md`, Impale).
 *
 * <p><b>A carrier is a Runner with a load.</b> It moves at
 * {@link #CARRIER_SPEED} of its own pace down the {@link SwarmGoal#BORE} field
 * and does not strike ({@link SwarmPressureBehavior}). Its load is the body as
 * itself: a corpse is an entity with its own position, so dragging it is
 * writing that position each tick a little behind the carrier, and the body
 * draws where it is like any other. A living load is dragged the same way.
 *
 * <p><b>Killing the carrier drops the body</b> where it fell. A dropped corpse
 * is loose, and the next Runner to come within {@link #SEIZE_RADIUS} of it
 * takes it up again. A dropped living body is <b>freed</b>: alive, a wound in
 * each leg, back to what it was doing after {@link #RECOVERY_SECONDS}. A body
 * that reaches the bore is consumed: a living one is <b>taken</b> (killed
 * there), and either way it stops being drawn and counts as one colonist's
 * biomass in the bore it went into.
 *
 * <p><b>A delivered body goes down the hole</b> (owner, 2026-10-25): it counts the moment its carrier reaches the
 * mouth and lets go, then slides from where it was let go into the mouth over {@link #FEED_SLIDE_SECONDS} and sinks
 * out of sight by {@link #FEED_SECONDS} ({@link #feeding}, {@link #sunk}), so the haul is seen dropped in the hole
 * rather than vanishing beside it. Nothing reads a feeding body: it is nobody's load, never loose, never prey.
 *
 * <p><b>What can be harvested</b> is a body the swarm hunts: a colonist, a
 * militia fighter or a Marine on foot. Never a power suit, a machine or a
 * structure, and never one of the cohort while a Marine or a militia fighter
 * is near enough to be its escort.
 *
 * <p><b>A held body</b> ({@link #isCarried} and alive) does nothing and is
 * nobody's prey: the unit update skips it, the swarm ignores it, direct fire
 * passes it ({@code BallisticResolver}) and a blast reaches it only while
 * {@link #areaHitsHeld()}.
 *
 * <p><b>Threading.</b> Runners strike on the parallel unit update, so
 * {@link #struck} and {@link #impale} are the calls made there, and each keeps
 * the lowest Runner id per target so the result does not depend on which
 * worker ran first. Every other change is made serially: deaths arrive through
 * the death drain and {@link #tick} runs after it. The parallel update only
 * reads, and nothing writes while it runs.
 *
 * <p><b>The swarm recycles its own dead.</b> A dead Runner lies loose like a
 * dropped body, and a Runner with nothing to fight goes to the nearest within
 * {@link #SCAVENGE_RADIUS} ({@link #nearestRecyclable}), takes it up and drags it to
 * the bore, where it is worth {@link #RAV_BODY_BIOMASS} of a colonist. A Runner
 * still fighting leaves its dead where they fell, and a recycled body is counted
 * apart from the harvest ({@link #recycled}): it is not a harvest beat. Since
 * the dead lie where the fighting is, a bore also sends out scavenger packs
 * ({@link #markScavenger}): a scavenger fetches the nearest of the dead anywhere
 * on the field, fighting only what is already within its reach, and attacks like
 * any Runner once nothing is left to fetch.
 *
 * <p><b>Harriers carry too</b> (`colony-breach-nouns.md`, Harrier, Carrying feed): a body a Harrier's bite killed
 * lies loose ({@link #airStruck}), and a Harrier sent off its pack takes a loose body up
 * ({@link #takeUpByFlier}) and flies it to the mouth, dragged and delivered as any load is.
 *
 * <p>{@code -Dbattle.colonyBreach.harvest=false} is the control: Runners leave
 * the dead where they fall, as they did before harvest.
 * {@code -Dbattle.colonyBreach.impale=false} keeps harvest and turns impales
 * off: every strike resolves as an ordinary blow.
 */
public final class SwarmHarvest {

    /**
     * An adult carrier's pace as a share of its own. Slower than a walking Marine, so a squad sent after one
     * catches it. A whelp drags slower and an elder faster ({@link SwarmStageStats#dragPace}).
     */
    public static final float CARRIER_SPEED = 0.4f;
    /** How far behind its carrier a dragged body trails, in cells. */
    public static final float TRAIL_CELLS = 0.55f;
    /** A carrier this close to a living bore's centre has reached its mouth. */
    public static final float DELIVER_RADIUS = 1.6f;
    /** How long a delivered body takes to slide from where it was let go into the mouth. */
    public static final float FEED_SLIDE_SECONDS = 0.4f;
    /** How long a delivered body takes to go into the mouth and sink out of sight, its slide included. */
    public static final float FEED_SECONDS = 1.2f;
    /** A loose body is taken up by a Runner this close to it. */
    public static final float SEIZE_RADIUS = 1.25f;
    /** A cohort evacuee with a Marine or militia fighter this close is under escort and not harvested. */
    public static final float ESCORT_RADIUS = 8f;
    /** The chance an adult Runner's landed blow impales its target instead of wounding it; by stage, {@link SwarmStageStats#impaleChance}. */
    public static final float IMPALE_CHANCE = 0.05f;
    /** How long a freed body lies recovering before it acts again. */
    public static final float RECOVERY_SECONDS = 3f;
    /** Damage that takes a held body at the bore's mouth. */
    private static final float TAKING_DAMAGE = 1_000_000f;
    /** A Runner's own body fed back into a bore, in colonists' worth of biomass. */
    public static final float RAV_BODY_BIOMASS = 0.5f;
    /** How far a Runner with nothing to fight goes to fetch one of the swarm's dead, cells. */
    public static final float SCAVENGE_RADIUS = 8f;
    /** A bore sends a scavenger pack only while at least this many of the swarm's dead lie loose. */
    public static final int SCAVENGER_PACK_MIN_DEAD = 3;
    /** A skitterer's pace hauling, as a share of its own: it is built for loads (`colony-breach-nouns.md`, Skitterer). */
    public static final float WORKER_DRAG_PACE = 0.7f;

    /** Whether harvest runs. {@code -Dbattle.colonyBreach.harvest=false} is the control. */
    public static boolean enabledByDefault() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.harvest", "true"));
    }

    /** Whether Runners impale. {@code -Dbattle.colonyBreach.impale=false} is the control. */
    public static boolean impaleByDefault() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.impale", "true"));
    }

    /** Whether a blast reaches a held body. {@code -Dbattle.colonyBreach.impale.areaHitsHeld=false} spares it. */
    public static boolean areaHitsHeldByDefault() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.impale.areaHitsHeld", "true"));
    }

    /** How a held body's hold ended, or began. */
    public enum EventKind { IMPALED, FREED, TAKEN }

    /** One fact about a living body a Runner took, for the beats and the snapshot. */
    public record Event(int tick, EventKind kind, long body, Faction faction) { }

    /** A body going down a bore's mouth: where it was let go, the mouth's centre, and the tick it was let go. */
    public record Feed(long body, float fromX, float fromY, float mouthX, float mouthY, int tick) { }

    private final boolean enabled;
    private final boolean impaleOn;
    private final boolean areaHitsHeld;
    /** Target to striking Runner, for this tick's strikes; lowest Runner id wins. */
    private final ConcurrentHashMap<Long, Long> strikes = new ConcurrentHashMap<>();
    /** Target to impaling Runner, for this tick's impales; lowest Runner id wins. */
    private final ConcurrentHashMap<Long, Long> impales = new ConcurrentHashMap<>();
    /** Bodies a Runner struck that died this tick, in death order. */
    private final List<Long> fallen = new ArrayList<>();
    /** Runners that died this tick: their bodies go loose for the swarm to recycle. */
    private final List<Long> fallenRunners = new ArrayList<>();
    /** Bodies a Harrier bit since the last tick, and those of them that died: a flier's kill lies loose for the nest. */
    private final Set<Long> airStrikes = new HashSet<>();
    private final List<Long> airFallen = new ArrayList<>();
    /** Runners of a scavenger pack: they fetch the swarm's dead before they fight. */
    private final Set<Long> scavengers = new HashSet<>();
    /** Carrier to load, in the order they were seized. */
    private final LinkedHashMap<Long, Long> loads = new LinkedHashMap<>();
    /** Carrier to the share of its own pace it drags at, by its stage. */
    private final Map<Long, Float> carrierPace = new HashMap<>();
    /** Load to carrier. */
    private final Map<Long, Long> carriers = new HashMap<>();
    /** Bodies dropped by a dead carrier, waiting for another, in the order they fell. */
    private final List<Long> loose = new ArrayList<>();
    /** Freed bodies to the tick they act again. */
    private final Map<Long, Integer> recovering = new HashMap<>();
    /** Bodies delivered alive whose death has not been drained yet; hidden when it is. */
    private final Set<Long> consumed = new HashSet<>();
    /** Bodies going down a mouth, in the order they were let go. */
    private final LinkedHashMap<Long, Feed> feeding = new LinkedHashMap<>();
    /** Marines delivered alive into a bore, in the order they were taken. */
    private final Set<Long> takenMarines = new LinkedHashSet<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Event> eventView = Collections.unmodifiableList(events);
    private boolean open;
    private int seized;
    private int recycled;
    private int delivered;
    private int dropped;
    private int impaled;
    private int freed;
    private int taken;
    private int firstHarvestTick = -1;
    private int hauledByWorkers;
    private int hauledByFliers;

    public SwarmHarvest(boolean enabled) {
        this(enabled, impaleByDefault(), areaHitsHeldByDefault());
    }

    public SwarmHarvest(boolean enabled, boolean impales, boolean areaHitsHeld) {
        this.enabled = enabled;
        this.impaleOn = enabled && impales;
        this.areaHitsHeld = areaHitsHeld;
    }

    public boolean enabled() { return enabled; }

    /** Whether a blast reaches a held body. Direct fire never does. */
    public boolean areaHitsHeld() { return areaHitsHeld; }

    /** Whether a Runner may harvest now: harvest is on and a bore lives to take the body to. */
    public boolean isOpen() { return open; }

    /** Whether {@code runner} is dragging a body. */
    public boolean isCarrier(long runner) { return loads.containsKey(runner); }

    /** Whether {@code body} is being dragged, alive or dead. */
    public boolean isCarried(long body) { return carriers.containsKey(body); }

    /** Whether {@code body} neither moves nor acts: held by a carrier, or still recovering from being freed. */
    public boolean holdsStill(long body) {
        return carriers.containsKey(body) || recovering.containsKey(body);
    }

    /** The Runner dragging {@code body}, or {@code 0}. */
    public long carrierOf(long body) {
        Long carrier = carriers.get(body);
        return carrier == null ? 0L : carrier;
    }

    /** The body {@code carrier} drags, or {@code 0}. */
    public long loadOf(long carrier) {
        Long load = loads.get(carrier);
        return load == null ? 0L : load;
    }

    /** Every carrier and its load, in the order they were seized. Read-only; serial phases only. */
    public Map<Long, Long> loads() { return Collections.unmodifiableMap(loads); }

    /** Whether {@code body} was dropped and is waiting for another Runner. */
    public boolean isLoose(long body) { return loose.contains(body); }

    /** A body gone from the field some other way (the body pile cap): no Runner goes to fetch it. */
    public void forget(long body) { loose.remove(Long.valueOf(body)); }

    /** Living bodies impaled, freed and taken, in the order it happened; readers keep their own cursor. */
    public List<Event> events() { return eventView; }

    /** Marines taken into a bore alive, or still held when the battle ends: lost without a roll. */
    public Set<Long> takenMarines(BattleSimulation sim) {
        Set<Long> out = new LinkedHashSet<>(takenMarines);
        for (long body : loads.values()) {
            if (sim.world().isAlive(body) && sim.identity().faction(body) == Faction.MARINE) out.add(body);
        }
        return out;
    }

    /** Bodies seized over the battle, re-seizures of a dropped body and impales included. */
    public int seized() { return seized; }

    /** Bodies delivered into a bore, alive or dead. */
    public int delivered() { return delivered; }

    /** Corpses dropped because their carrier died or had nowhere left to go. */
    public int dropped() { return dropped; }

    /** Living bodies impaled. */
    public int impaled() { return impaled; }

    /** Living bodies freed when their carrier died. */
    public int freed() { return freed; }

    /** Living bodies taken into a bore. */
    public int taken() { return taken; }

    /** The tick the first body was seized, or {@code -1}. */
    public int firstHarvestTick() { return firstHarvestTick; }

    /** How many are being dragged right now. */
    public int carrying() { return loads.size(); }

    /**
     * The nearest of the swarm's own dead lying loose within {@code radius} of (x, y), lowest id on
     * a tie, or 0. Read from the parallel unit update, which only reads harvest state.
     */
    public long nearestRecyclable(BattleSimulation sim, float x, float y, float radius) {
        if (!open || loose.isEmpty()) return 0L;
        long best = 0L;
        float bestDistance = radius * radius;
        for (int i = 0, n = loose.size(); i < n; i++) {
            long body = loose.get(i);
            if (!sim.world().hasPosition(body) || !isRavBody(sim, body)) continue;
            float dx = sim.world().x(body) - x, dy = sim.world().y(body) - y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance || distance == bestDistance && body < best) {
                best = body;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The nearest loose body of any kind (one of the swarm's dead or a load a carrier dropped) within
     * {@code radius} of (x, y), lowest id on a tie, or 0: what a skitterer hauls. Serial.
     */
    public long nearestLoose(BattleSimulation sim, float x, float y, float radius) {
        return nearestLoose(sim, x, y, radius, body -> false);
    }

    /** As {@link #nearestLoose(BattleSimulation, float, float, float)}, passing over every body {@code skip} names. */
    public long nearestLoose(BattleSimulation sim, float x, float y, float radius, LongPredicate skip) {
        if (!open || loose.isEmpty()) return 0L;
        long best = 0L;
        float bestDistance = radius * radius;
        for (int i = 0, n = loose.size(); i < n; i++) {
            long body = loose.get(i);
            if (!sim.world().hasPosition(body) || skip.test(body)) continue;
            float dx = sim.world().x(body) - x, dy = sim.world().y(body) - y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance || distance == bestDistance && body < best) {
                best = body;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** How many of the swarm's dead lie loose for recycling now. */
    public int recyclableCount(BattleSimulation sim) {
        int count = 0;
        for (long body : loose) if (sim.world().hasPosition(body) && isRavBody(sim, body)) count++;
        return count;
    }

    /** Marks a Runner as one of a scavenger pack. Called serially, as a surge releases it. */
    public void markScavenger(long runner) { scavengers.add(runner); }

    /** Whether this Runner came out of its bore in a scavenger pack. */
    public boolean isScavenger(long runner) { return scavengers.contains(runner); }

    /** The swarm's own dead dragged back into a bore. */
    public int recycled() { return recycled; }

    /** Bodies, recycled or delivered, that a skitterer rather than a Runner brought into a bore. */
    public int hauledByWorkers() { return hauledByWorkers; }

    /** Bodies, recycled or delivered, that a Harrier flew into a bore. */
    public int hauledByFliers() { return hauledByFliers; }

    /**
     * A Harrier bit {@code target}. Serial, from the fliers' tick; if the bite kills, the body lies loose
     * ({@link #isLoose}) for anything of the nest's to fetch, as a dropped load does.
     */
    public void airStruck(long target) {
        if (open) airStrikes.add(target);
    }

    /**
     * Serial: a Harrier ({@code flier}) takes up the loose {@code body} to fly it to a bore. Refused (false) while
     * harvest is shut, or when the body is not loose or already held, or the flier already carries.
     */
    public boolean takeUpByFlier(BattleSimulation sim, long flier, long body) {
        if (!open || !loose.contains(body) || carriers.containsKey(body) || loads.containsKey(flier)) return false;
        if (!sim.world().hasPosition(body) || sim.resolveUnit(flier) == 0L || !sim.world().isAlive(flier)) return false;
        loads.put(flier, body);
        carrierPace.put(flier, 1f);
        carriers.put(body, flier);
        loose.remove(body);
        if (!isRavBody(sim, body)) {
            seized++;
            if (firstHarvestTick < 0) firstHarvestTick = sim.getSimTickIndex();
        }
        return true;
    }

    /** The bodies going down a mouth now, in the order they were let go. Presentation reads it. */
    public Collection<Feed> feeding() { return Collections.unmodifiableCollection(feeding.values()); }

    /** How far {@code body} has sunk into its mouth at {@code tick}: 0 while it is not going down or still sliding, 1 gone. */
    public float sunk(long body, int tick) {
        Feed feed = feeding.get(body);
        return feed == null ? 0f : sunk(feed, tick);
    }

    /** How far a fed body has sunk at {@code tick}: 0 through its slide, rising to 1 at {@link #FEED_SECONDS}. */
    static float sunk(Feed feed, int tick) {
        float seconds = (tick - feed.tick()) * BattleSimulation.TICK_DT;
        return Math.max(0f, Math.min(1f, (seconds - FEED_SLIDE_SECONDS) / (FEED_SECONDS - FEED_SLIDE_SECONDS)));
    }

    /** Where a fed body lies at {@code tick}: from where it was let go into the mouth, eased out, then at the mouth. */
    static float[] fedAt(Feed feed, int tick) {
        float t = Math.max(0f, Math.min(1f, (tick - feed.tick()) * BattleSimulation.TICK_DT / FEED_SLIDE_SECONDS));
        float eased = 1f - (1f - t) * (1f - t);
        return new float[] {feed.fromX() + (feed.mouthX() - feed.fromX()) * eased,
                feed.fromY() + (feed.mouthY() - feed.fromY()) * eased};
    }

    /**
     * A Runner landed a blow on {@code target}. Called from the parallel unit
     * update; if the blow kills, the death drain finds it here.
     */
    public void struck(long target, long runner) {
        if (!open) return;
        strikes.merge(target, runner, Math::min);
    }

    /**
     * Whether {@code runner}'s landed blow on {@code target} impales it instead
     * of wounding it: the target is a living body harvest may take, the Runner
     * is free, a bore is open, and the strike's own draw comes up. Read-only,
     * for the parallel update.
     */
    public boolean impales(long runner, long target, BattleSimulation sim) {
        if (!impaleOn || !open || loads.containsKey(runner) || carriers.containsKey(target)) return false;
        if (sim.identity().type(runner) != UnitType.SWARM_RUNNER) return false;
        if (sim.resolveUnit(target) == 0L || !sim.world().isAlive(target)) return false;
        if (!harvestable(sim, target)) return false;
        return impaleDraw(sim.getSimTickIndex(), runner, target)
                < SwarmStageStats.impaleChance(sim.identity().swarmStage(runner));
    }

    /** {@code runner} impaled {@code target} this tick. Called from the parallel update; {@link #tick} takes it up. */
    public void impale(long target, long runner) {
        impales.merge(target, runner, Math::min);
    }

    /**
     * The strike's own draw in {@code [0, 1)}: keyed by tick, Runner and
     * target, never the battle's shared stream, so impales change no other
     * roll and switching them off changes nothing else.
     */
    static float impaleDraw(int tick, long runner, long target) {
        long h = SwarmBores.mix(tick * 0x9E3779B97F4A7C15L ^ SwarmBores.mix(runner * 0xBF58476D1CE4E5B9L ^ target));
        return SwarmBores.unit(h);
    }

    /** Death drain subscriber: a body a Runner struck this tick has died. */
    public void onDeath(DeathEvent event) {
        long id = event.unitId();
        if (strikes.containsKey(id)) fallen.add(id);
        else if (airStrikes.contains(id)) airFallen.add(id);
        else fallenRunners.add(id);
    }

    /**
     * Once per tick, serially, after the death drain and the bores: seize the
     * bodies Runners brought down or impaled, drag every load, deliver what
     * reached a bore, and drop or free what a dead carrier was holding.
     */
    public void tick(BattleSimulation sim) {
        SwarmBores bores = sim.swarmBores();
        boolean boreLives = bores.livingCount() > 0;
        int tick = sim.getSimTickIndex();
        sink(sim, tick);
        hideConsumed(sim);
        recovering.values().removeIf(until -> tick >= until);
        if (enabled) {
            carry(sim, bores, boreLives, tick);
            if (boreLives) {
                for (long body : fallen) {
                    Long runner = strikes.get(body);
                    if (runner != null && mayCarry(sim, runner) && harvestable(sim, body)) seize(sim, runner, body);
                }
                takeImpaled(sim, tick);
                for (long body : fallenRunners) {
                    if (isRavBody(sim, body) && sim.world().hasPosition(body) && !loose.contains(body)) loose.add(body);
                }
                for (long body : airFallen) {
                    if (harvestable(sim, body) && !carriers.containsKey(body) && !loose.contains(body)) loose.add(body);
                }
                takeUpLoose(sim);
            } else {
                loose.removeIf(body -> !sim.world().hasPosition(body));
            }
        }
        strikes.clear();
        impales.clear();
        fallen.clear();
        fallenRunners.clear();
        airStrikes.clear();
        airFallen.clear();
        open = enabled && boreLives;
    }

    /** The carrier slow-down, as a stat modifier on the carrier. */
    public UnitModifier carrierModifier() {
        return new UnitModifier() {
            @Override public String name(long unit) { return "Dragging a body"; }
            @Override public Set<UnitStat> stats() { return Set.of(UnitStat.MOVE_SPEED); }
            @Override public boolean appliesTo(long unit) { return loads.containsKey(unit); }
            @Override public float multiplier(long unit, UnitStat stat) {
                return carrierPace.getOrDefault(unit, CARRIER_SPEED);
            }
        };
    }

    /** Whether {@code body} is one the swarm takes, dead at a Runner's hands or alive on its blades. */
    static boolean harvestable(BattleSimulation sim, long body) {
        if (!sim.world().hasPosition(body)) return false;
        UnitType type = sim.identity().type(body);
        if (!isInfantry(type) || sim.identity().armorClass(body) == ArmorClass.POWER_SUIT) return false;
        if (sim.identity().faction(body) == Faction.CIVILIAN
                && sim.getCivilianEvacuationTracker().state(body) != null) {
            return !escorted(sim, body);
        }
        return true;
    }

    /** Bodies on foot: soldiers, militia, technicians and colonists. */
    static boolean isInfantry(UnitType type) {
        if (type == null) return false;
        return switch (type) {
            case MARINE, MARINE_BLUE, MARINE_RED, MILITIA, TECHNICIAN, CIVILIAN, ENGINEER, SCIENTIST -> true;
            default -> false;
        };
    }

    private static boolean escorted(BattleSimulation sim, long body) {
        float x = sim.world().x(body), y = sim.world().y(body);
        LongBucket near = new LongBucket();
        for (Faction escort : new Faction[]{Faction.MARINE, Faction.ALLY}) {
            sim.getUnitIndex().gatherFaction(x, y, ESCORT_RADIUS, escort, near);
            for (int i = 0; i < near.size; i++) {
                long unit = near.ids[i];
                if (sim.resolveUnit(unit) == 0L || !sim.identity().type(unit).combatant) continue;
                float dx = sim.world().x(unit) - x, dy = sim.world().y(unit) - y;
                if (dx * dx + dy * dy <= ESCORT_RADIUS * ESCORT_RADIUS) return true;
            }
        }
        return false;
    }

    /** A living Runner, or a skitterer free to haul, with nothing already on its back. */
    private boolean mayCarry(BattleSimulation sim, long runner) {
        if (sim.resolveUnit(runner) == 0L || !sim.world().isAlive(runner) || loads.containsKey(runner)) return false;
        UnitType type = sim.identity().type(runner);
        return type == UnitType.SWARM_RUNNER
                || type == UnitType.SWARM_WORKER && sim.swarmWorkers().mayHaul(runner);
    }

    /** A dead Runner: the one body the swarm takes back from its own. */
    static boolean isRavBody(BattleSimulation sim, long body) {
        return sim.identity().type(body) == UnitType.SWARM_RUNNER;
    }

    private void seize(BattleSimulation sim, long runner, long body) {
        loads.put(runner, body);
        carrierPace.put(runner, sim.identity().type(runner) == UnitType.SWARM_WORKER ? WORKER_DRAG_PACE
                : SwarmStageStats.dragPace(sim.identity().swarmStage(runner)));
        carriers.put(body, runner);
        loose.remove(body);
        sim.clearPath(runner);
        sim.combat().setTargetId(runner, 0L);
        trail(sim, body, sim.world().x(runner), sim.world().y(runner));
        if (isRavBody(sim, body)) return;
        sim.swarmCues().post(SwarmCues.Kind.DRAG, runner, sim);
        seized++;
        if (firstHarvestTick < 0) firstHarvestTick = sim.getSimTickIndex();
    }

    /** This tick's impales, in target order: each living body goes onto its Runner's blades. */
    private void takeImpaled(BattleSimulation sim, int tick) {
        if (impales.isEmpty()) return;
        for (Map.Entry<Long, Long> entry : new TreeMap<>(impales).entrySet()) {
            long body = entry.getKey(), runner = entry.getValue();
            if (sim.resolveUnit(body) == 0L || !sim.world().isAlive(body) || carriers.containsKey(body)) continue;
            if (!mayCarry(sim, runner)) continue;
            recovering.remove(body);
            if (sim.movement().has(body)) sim.clearPath(body);
            if (sim.combat().has(body)) {
                sim.combat().setTargetId(body, 0L);
                sim.combat().setBurstRemaining(body, 0);
            }
            seize(sim, runner, body);
            sim.swarmCues().post(SwarmCues.Kind.IMPALE, runner, sim);
            impaled++;
            Faction faction = sim.identity().faction(body);
            events.add(new Event(tick, EventKind.IMPALED, body, faction));
            if (faction == Faction.MARINE) shake(sim, body);
        }
    }

    /** Impaling a Marine costs their fire team what a death would, once. */
    private static void shake(BattleSimulation sim, long body) {
        Squad squad = sim.squadOf(body);
        if (squad == null) return;
        FireTeamMorale team = squad.fireTeamMorale(sim.squad().fireTeamIndex(body));
        team.morale = Math.max(0f, team.morale - SquadMoraleSystem.MORALE_DROP_ON_DEATH);
        team.timeSinceUnderFire = 0f;
    }

    /** Drags every load, delivers what reached a bore, drops or frees what lost its carrier. */
    private void carry(BattleSimulation sim, SwarmBores bores, boolean boreLives, int tick) {
        if (loads.isEmpty()) return;
        for (Map.Entry<Long, Long> entry : new ArrayList<>(loads.entrySet())) {
            long carrier = entry.getKey(), body = entry.getValue();
            if (!sim.world().hasPosition(body)) {
                release(carrier, body);
                continue;
            }
            boolean alive = sim.resolveUnit(body) != 0L && sim.world().isAlive(body);
            boolean carrierLives = sim.resolveUnit(carrier) != 0L && sim.world().isAlive(carrier);
            if (!carrierLives || !boreLives) {
                release(carrier, body);
                if (alive) {
                    free(sim, body, tick);
                } else {
                    loose.add(body);
                    if (!isRavBody(sim, body)) dropped++;
                }
                continue;
            }
            float cx = sim.world().x(carrier), cy = sim.world().y(carrier);
            trail(sim, body, cx, cy);
            SwarmBore bore = bores.nearestLiving(cx, cy);
            float dx = bore.x() + 0.5f - cx, dy = bore.y() + 0.5f - cy;
            boolean rav = !alive && isRavBody(sim, body);
            if (dx * dx + dy * dy <= DELIVER_RADIUS * DELIVER_RADIUS
                    && bores.feed(sim, bore, rav ? RAV_BODY_BIOMASS : 1f)) {
                release(carrier, body);
                if (alive) take(sim, carrier, body, tick);
                feed(sim, body, bore, tick);
                if (rav) recycled++;
                else delivered++;
                if (sim.identity().type(carrier) == UnitType.SWARM_WORKER) hauledByWorkers++;
                if (sim.identity().type(carrier) == UnitType.SWARM_HARRIER) hauledByFliers++;
            }
        }
    }

    /** A living body whose carrier died: alive where it fell, both legs run through, acting again after a moment. */
    private void free(BattleSimulation sim, long body, int tick) {
        sim.wounds().wound(body, BodyPart.LEGS);
        sim.wounds().wound(body, BodyPart.LEGS);
        recovering.put(body, tick + Math.round(RECOVERY_SECONDS / BattleSimulation.TICK_DT));
        freed++;
        events.add(new Event(tick, EventKind.FREED, body, sim.identity().faction(body)));
    }

    /** A living body at the bore's mouth is taken: killed there, with no morale cost beyond the impale's. */
    private void take(BattleSimulation sim, long carrier, long body, int tick) {
        Faction faction = sim.identity().faction(body);
        sim.applyDamage(body, carrier, TAKING_DAMAGE, TAKING_DAMAGE, 0f);
        consumed.add(body);
        if (faction == Faction.MARINE) takenMarines.add(body);
        taken++;
        events.add(new Event(tick, EventKind.TAKEN, body, faction));
    }

    /**
     * Serial: {@code carrier} lets go of its load where it stands, as a whelp
     * does when it bolts. A living load is freed; a dead one lies loose for
     * another carrier.
     */
    public void drop(BattleSimulation sim, long carrier) {
        Long body = loads.get(carrier);
        if (body == null) return;
        release(carrier, body);
        if (sim.resolveUnit(body) != 0L && sim.world().isAlive(body)) {
            free(sim, body, sim.getSimTickIndex());
        } else {
            loose.add(body);
            dropped++;
        }
    }

    private void release(long carrier, long body) {
        loads.remove(carrier);
        carrierPace.remove(carrier);
        carriers.remove(body);
    }

    /** Loose bodies go to the nearest Runner within reach that is free to carry. */
    private void takeUpLoose(BattleSimulation sim) {
        if (loose.isEmpty()) return;
        LongBucket near = new LongBucket();
        for (long body : new ArrayList<>(loose)) {
            if (!sim.world().hasPosition(body)) {
                loose.remove(body);
                continue;
            }
            float x = sim.world().x(body), y = sim.world().y(body);
            sim.getUnitIndex().gatherFaction(x, y, SEIZE_RADIUS + 1f, Faction.DEFENDER, near);
            boolean rav = isRavBody(sim, body);
            long best = 0L;
            float bestDistance = SEIZE_RADIUS * SEIZE_RADIUS;
            for (int i = 0; i < near.size; i++) {
                long runner = near.ids[i];
                if (!mayCarry(sim, runner)) continue;
                // the swarm's own dead are taken up only by a Runner with nothing to fight
                if (rav && sim.combat().targetId(runner) != 0L) continue;
                float dx = sim.world().x(runner) - x, dy = sim.world().y(runner) - y;
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance || distance == bestDistance && runner < best) {
                    best = runner;
                    bestDistance = distance;
                }
            }
            if (best != 0L) seize(sim, best, body);
        }
    }

    /** Puts the body a little behind its carrier, lying along the drag. */
    private static void trail(BattleSimulation sim, long body, float cx, float cy) {
        float bx = sim.world().x(body), by = sim.world().y(body);
        float dx = bx - cx, dy = by - cy;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3f) return;
        float scale = Math.min(1f, TRAIL_CELLS / length);
        sim.world().setPos(body, cx + dx * scale, cy + dy * scale);
        BattleComponents c = sim.getBattleComponents();
        EntityWorld world = sim.getEntityWorld();
        if (world.has(body, c.CORPSE)) {
            float facing = (float) Math.toDegrees(Math.atan2(-dy, -dx));
            world.setFloat(body, c.CORPSE, BattleComponents.CORPSE_FACING_DEGREES, facing);
            // A body on the move has not lain anywhere long enough to pool: its stain starts again where it is dropped.
            world.setInt(body, c.CORPSE, BattleComponents.CORPSE_DEATH_TICK, sim.getSimTickIndex());
        }
    }

    /** A delivered body goes into the hole: moved onto it and no longer drawn. */
    /** A delivered body starts down the mouth from where it was let go. */
    private void feed(BattleSimulation sim, long body, SwarmBore bore, int tick) {
        feeding.put(body, new Feed(body, sim.world().x(body), sim.world().y(body), bore.x() + 0.5f, bore.y() + 0.5f,
                tick));
    }

    /** Moves every body going down a mouth, and hides those that have sunk out of sight. */
    private void sink(BattleSimulation sim, int tick) {
        if (feeding.isEmpty()) return;
        feeding.values().removeIf(feed -> {
            if (!sim.world().hasPosition(feed.body())) return true;
            float[] at = fedAt(feed, tick);
            sim.world().setPos(feed.body(), at[0], at[1]);
            if (sunk(feed, tick) < 1f) return false;
            // one taken alive is hidden once its death has drained (hideConsumed)
            if (!consumed.contains(feed.body())) hide(sim, feed.body());
            return true;
        });
    }

    /** A body taken alive becomes a corpse only once its death is drained; hide that corpse too, once it has sunk. */
    private void hideConsumed(BattleSimulation sim) {
        if (consumed.isEmpty()) return;
        BattleComponents c = sim.getBattleComponents();
        consumed.removeIf(body -> {
            if (feeding.containsKey(body) || !sim.getEntityWorld().has(body, c.CORPSE)) return false;
            hide(sim, body);
            return true;
        });
    }

    private static void hide(BattleSimulation sim, long body) {
        BattleComponents c = sim.getBattleComponents();
        EntityWorld world = sim.getEntityWorld();
        if (world.has(body, c.SPRITE)) world.setInt(body, c.SPRITE, BattleComponents.SPRITE_INDEX, -1);
    }
}
