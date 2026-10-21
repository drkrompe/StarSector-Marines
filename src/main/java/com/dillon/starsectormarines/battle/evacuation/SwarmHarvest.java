package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.ArmorClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The swarm's harvest (`colony-breach-nouns.md`, Harvest): a Runner that brings
 * down a colonist seizes the body and drags it to the nearest living bore,
 * where it is fed in through {@link SwarmBores#feed}.
 *
 * <p><b>A carrier is a Runner with a load.</b> It moves at
 * {@link #CARRIER_SPEED} of its own pace down the {@link SwarmGoal#BORE} field
 * and does not strike ({@link SwarmPressureBehavior}). Its load is the body as
 * itself: a corpse is an entity with its own position, so dragging it is
 * writing that position each tick a little behind the carrier, and the body
 * draws where it is like any other.
 *
 * <p><b>Killing the carrier drops the body</b> where it fell. A dropped body is
 * loose, and the next Runner to come within {@link #SEIZE_RADIUS} of it takes
 * it up again. A delivered body is consumed: it stops being drawn and counts
 * as one colonist's biomass in the bore it went into.
 *
 * <p><b>What can be harvested</b> is a body the swarm hunts that a Runner
 * brought down: a colonist, a militia fighter or a Marine on foot. Never a
 * power suit, a machine or a structure, and never one of the cohort while a
 * Marine or a militia fighter is near enough to be its escort.
 *
 * <p><b>Threading.</b> Runners strike on the parallel unit update, so
 * {@link #struck} is the one call made there, and it keeps the lowest Runner id
 * per target so the result does not depend on which worker ran first. Every
 * other change is made serially: deaths arrive through the death drain and
 * {@link #tick} runs after it. The parallel update only reads
 * {@link #isOpen} and {@link #isCarrier}, which nothing writes while it runs.
 *
 * <p>{@code -Dbattle.colonyBreach.harvest=false} is the control: Runners leave
 * the dead where they fall, as they did before harvest.
 */
public final class SwarmHarvest {

    /** A carrier's pace as a share of its own. Slower than a walking Marine, so a squad sent after one catches it. */
    public static final float CARRIER_SPEED = 0.4f;
    /** How far behind its carrier a dragged body trails, in cells. */
    public static final float TRAIL_CELLS = 0.55f;
    /** A carrier this close to a living bore's centre has reached its mouth. */
    public static final float DELIVER_RADIUS = 1.6f;
    /** A loose body is taken up by a Runner this close to it. */
    public static final float SEIZE_RADIUS = 1.25f;
    /** A cohort evacuee with a Marine or militia fighter this close is under escort and not harvested. */
    public static final float ESCORT_RADIUS = 8f;

    /** Whether harvest runs. {@code -Dbattle.colonyBreach.harvest=false} is the control. */
    public static boolean enabledByDefault() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.harvest", "true"));
    }

    private final boolean enabled;
    /** Target to striking Runner, for this tick's strikes; lowest Runner id wins. */
    private final ConcurrentHashMap<Long, Long> strikes = new ConcurrentHashMap<>();
    /** Bodies a Runner struck that died this tick, in death order. */
    private final List<Long> fallen = new ArrayList<>();
    /** Carrier to load, in the order they were seized. */
    private final LinkedHashMap<Long, Long> loads = new LinkedHashMap<>();
    /** Load to carrier. */
    private final Map<Long, Long> carriers = new HashMap<>();
    /** Bodies dropped by a dead carrier, waiting for another, in the order they fell. */
    private final List<Long> loose = new ArrayList<>();
    private boolean open;
    private int seized;
    private int delivered;
    private int dropped;
    private int firstHarvestTick = -1;

    public SwarmHarvest(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    /** Whether a Runner may harvest now: harvest is on and a bore lives to take the body to. */
    public boolean isOpen() { return open; }

    /** Whether {@code runner} is dragging a body. */
    public boolean isCarrier(long runner) { return loads.containsKey(runner); }

    /** Whether {@code body} is being dragged. */
    public boolean isCarried(long body) { return carriers.containsKey(body); }

    /** The body {@code carrier} drags, or {@code 0}. */
    public long loadOf(long carrier) {
        Long load = loads.get(carrier);
        return load == null ? 0L : load;
    }

    /** Every carrier and its load, in the order they were seized. Read-only; serial phases only. */
    public Map<Long, Long> loads() { return Collections.unmodifiableMap(loads); }

    /** Whether {@code body} was dropped and is waiting for another Runner. */
    public boolean isLoose(long body) { return loose.contains(body); }

    /** Bodies seized over the battle, re-seizures of a dropped body included. */
    public int seized() { return seized; }

    /** Bodies delivered into a bore. */
    public int delivered() { return delivered; }

    /** Bodies dropped because their carrier died or had nowhere left to go. */
    public int dropped() { return dropped; }

    /** The tick the first body was seized, or {@code -1}. */
    public int firstHarvestTick() { return firstHarvestTick; }

    /** How many are being dragged right now. */
    public int carrying() { return loads.size(); }

    /**
     * A Runner landed a blow on {@code target}. Called from the parallel unit
     * update; if the blow kills, the death drain finds it here.
     */
    public void struck(long target, long runner) {
        if (!open) return;
        strikes.merge(target, runner, Math::min);
    }

    /** Death drain subscriber: a body a Runner struck this tick has died. */
    public void onDeath(DeathEvent event) {
        if (strikes.containsKey(event.unitId())) fallen.add(event.unitId());
    }

    /**
     * Once per tick, serially, after the death drain and the bores: seize the
     * bodies Runners brought down, drag every load, deliver what reached a
     * bore, and drop what a dead carrier was holding.
     */
    public void tick(BattleSimulation sim) {
        SwarmBores bores = sim.swarmBores();
        boolean boreLives = bores.livingCount() > 0;
        if (enabled) {
            carry(sim, bores, boreLives);
            if (boreLives) {
                for (long body : fallen) {
                    Long runner = strikes.get(body);
                    if (runner != null && mayCarry(sim, runner) && harvestable(sim, body)) seize(sim, runner, body);
                }
                takeUpLoose(sim);
            } else {
                loose.removeIf(body -> !sim.world().hasPosition(body));
            }
        }
        strikes.clear();
        fallen.clear();
        open = enabled && boreLives;
    }

    /** The carrier slow-down, as a stat modifier on the carrier. */
    public UnitModifier carrierModifier() {
        return new UnitModifier() {
            @Override public String name(long unit) { return "Dragging a body"; }
            @Override public Set<UnitStat> stats() { return Set.of(UnitStat.MOVE_SPEED); }
            @Override public boolean appliesTo(long unit) { return loads.containsKey(unit); }
            @Override public float multiplier(long unit, UnitStat stat) { return CARRIER_SPEED; }
        };
    }

    /** Whether {@code body}, dead at a Runner's hands, is one the swarm takes. */
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

    private boolean mayCarry(BattleSimulation sim, long runner) {
        return sim.resolveUnit(runner) != 0L && sim.world().isAlive(runner)
                && sim.identity().type(runner) == UnitType.SWARM_RUNNER
                && !loads.containsKey(runner);
    }

    private void seize(BattleSimulation sim, long runner, long body) {
        loads.put(runner, body);
        carriers.put(body, runner);
        loose.remove(body);
        sim.clearPath(runner);
        sim.combat().setTargetId(runner, 0L);
        trail(sim, body, sim.world().x(runner), sim.world().y(runner));
        seized++;
        if (firstHarvestTick < 0) firstHarvestTick = sim.getSimTickIndex();
    }

    /** Drags every load, delivers what reached a bore, drops what lost its carrier. */
    private void carry(BattleSimulation sim, SwarmBores bores, boolean boreLives) {
        if (loads.isEmpty()) return;
        for (Map.Entry<Long, Long> entry : new ArrayList<>(loads.entrySet())) {
            long carrier = entry.getKey(), body = entry.getValue();
            if (!sim.world().hasPosition(body)) {
                release(carrier, body);
                continue;
            }
            boolean carrierLives = sim.resolveUnit(carrier) != 0L && sim.world().isAlive(carrier);
            if (!carrierLives || !boreLives) {
                release(carrier, body);
                loose.add(body);
                dropped++;
                continue;
            }
            float cx = sim.world().x(carrier), cy = sim.world().y(carrier);
            trail(sim, body, cx, cy);
            SwarmBore bore = bores.nearestLiving(cx, cy);
            float dx = bore.x() + 0.5f - cx, dy = bore.y() + 0.5f - cy;
            if (dx * dx + dy * dy <= DELIVER_RADIUS * DELIVER_RADIUS && bores.feed(sim, bore, 1f)) {
                consume(sim, body, bore);
                release(carrier, body);
                delivered++;
            }
        }
    }

    private void release(long carrier, long body) {
        loads.remove(carrier);
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
            long best = 0L;
            float bestDistance = SEIZE_RADIUS * SEIZE_RADIUS;
            for (int i = 0; i < near.size; i++) {
                long runner = near.ids[i];
                if (!mayCarry(sim, runner)) continue;
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
    private static void consume(BattleSimulation sim, long body, SwarmBore bore) {
        sim.world().setPos(body, bore.x() + 0.5f, bore.y() + 0.5f);
        BattleComponents c = sim.getBattleComponents();
        EntityWorld world = sim.getEntityWorld();
        if (world.has(body, c.SPRITE)) world.setInt(body, c.SPRITE, BattleComponents.SPRITE_INDEX, -1);
    }
}
