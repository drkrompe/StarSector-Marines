package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.combat.StaggerService;
import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.evacuation.BruteChargeRules.Kind;
import com.dillon.starsectormarines.battle.evacuation.BruteChargeRules.Outcome;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.FireTeamMorale;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullImpact;
import com.dillon.starsectormarines.battle.vehicle.HullSlide;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Brute's charge ({@code colony-breach-nouns.md}, Brute): the tell, the straight run at charge speed, and what the
 * run does to what it hits.
 *
 * <p>{@link SwarmBruteBehavior} decides, in the parallel unit update, that a charge should begin and
 * {@linkplain #request posts} it. Everything after that happens here in the serial pass, because a
 * charge moves other bodies, breaks walls and lands damage, none of which the parallel update may do.
 * Requests and charges are walked in Brute id order, so a replay runs the same charges the same way.
 *
 * <p>While a charge lasts (tell, run, pull-up) the Brute's behaviour only holds still; this system is
 * what moves it.
 *
 * <p>It also owns what follows a hit through. A body thrown or bowled, and a Brute rebounding off a hull,
 * <em>slide</em>: they leave at the speed that carries them their distance and are slowed to a stop over the
 * next ticks, so nothing jumps. A Brute's ordinary blow on infantry is a <em>trample</em>, posted by its
 * behaviour and landed here: the body under it is knocked down and bowled, and the bodies beside it take part
 * of the blow and go down too.
 */
public final class BruteChargeSystem {

    /** Where a Brute is in its charge. {@code IDLE} is walking, possibly still on cooldown. */
    public enum Phase { IDLE, TELL, RUN, PULL_UP }

    /** One thing a charge did, kept for evidence. */
    public record Event(int tick, long brute, String what, long target, int cellX, int cellY) {}

    private record Request(long brute, long target, float aimX, float aimY) {}

    /** A body sliding to rest: its velocity, cells a second, and the deceleration that stops it. */
    private static final class Slide {
        float vx, vy;
        final float decel;

        Slide(float vx, float vy, float decel) {
            this.vx = vx;
            this.vy = vy;
            this.decel = decel;
        }
    }

    /** A blow posted by the behaviour: a trample on infantry, or the push behind a blow on anything else. */
    private record Trample(long brute, long target, boolean trample) {}

    /** A body a Matriarch's scythe caught, thrown along {@code (dx, dy)} (a unit vector). */
    private record Toss(long attacker, long body, float dx, float dy) {}

    private static final class Charge {
        Phase phase = Phase.IDLE;
        int phaseTicks;
        int readyTick;
        long target;
        float aimX, aimY;
        float heading;
        float originX, originY;
        float reach;
        float run;
        float pace = 1f;
        int bodies;
        final LongOpenHashSet struck = new LongOpenHashSet();
    }

    private static final int EVENT_CAP = 4096;

    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Trample> tramples = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Toss> tosses = new ConcurrentLinkedQueue<>();
    private final List<Toss> tossDrain = new ArrayList<>();
    private final List<Trample> trampleDrain = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Slide> slides = new Long2ObjectOpenHashMap<>();
    private final LongArrayList slideOrder = new LongArrayList();
    private final Long2ObjectOpenHashMap<Charge> charges = new Long2ObjectOpenHashMap<>();
    private final List<Request> drain = new ArrayList<>();
    private final LongArrayList order = new LongArrayList();
    private final LongBucket nearby = new LongBucket();
    private final LongBucket fear = new LongBucket();
    private final List<Event> events = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Float> impulses = new Long2ObjectOpenHashMap<>();

    // ---- read by the parallel update; written only in tick ----

    /** Whether {@code brute} is in a charge (tell, run or pull-up) and its behaviour should hold. */
    public boolean busy(long brute) {
        Charge c = charges.get(brute);
        return c != null && c.phase != Phase.IDLE;
    }

    /** Whether {@code brute}'s charge is off cooldown at tick {@code now}. */
    public boolean ready(long brute, int now) {
        Charge c = charges.get(brute);
        return c == null || (c.phase == Phase.IDLE && now >= c.readyTick);
    }

    /** Thread-safe; posted by the Brute's behaviour. Lands next serial pass, if still legal then. */
    public void request(long brute, long target, float aimX, float aimY) {
        requests.add(new Request(brute, target, aimX, aimY));
    }

    /**
     * Thread-safe; posted by the Brute's behaviour in place of an ordinary blow when the body it strikes is
     * infantry. Lands next serial pass.
     */
    public void trample(long brute, long target) {
        tramples.add(new Trample(brute, target, true));
    }

    /**
     * Thread-safe; posted by the Brute's behaviour after an ordinary blow, whose damage it has already dealt.
     * Lands next serial pass: the Brute's weight behind the blow pushes a hull or a mech.
     */
    public void blow(long brute, long target) {
        tramples.add(new Trample(brute, target, false));
    }

    // ---- presentation and evidence ----

    public Phase phase(long brute) {
        Charge c = charges.get(brute);
        return c == null ? Phase.IDLE : c.phase;
    }

    /** The heading a charge runs on, in degrees, and how far it can go. */
    public float headingDegrees(long brute) {
        Charge c = charges.get(brute);
        return c == null ? 0f : c.heading;
    }

    public float reach(long brute) {
        Charge c = charges.get(brute);
        return c == null ? 0f : c.reach;
    }

    /** Ticks left in the current phase. */
    public int phaseTicks(long brute) {
        Charge c = charges.get(brute);
        return c == null ? 0 : c.phaseTicks;
    }

    /** Every Brute with a charge record, for presentation; in id order. */
    public long[] tracked() {
        long[] ids = charges.keySet().toLongArray();
        Arrays.sort(ids);
        return ids;
    }

    /** The impulse (tonne-cells a second) of the last charge that struck {@code hull}, or 0: evidence. */
    public float lastImpulse(long hull) {
        Float impulse = impulses.get(hull);
        return impulse == null ? 0f : impulse;
    }

    /** What charges have done this battle, oldest first (capped). */
    public List<Event> events() { return events; }

    // ---- serial ----

    /** Serial: starts posted charges and advances every charge one tick. */
    public void tick(BattleSimulation sim) {
        int now = sim.getSimTickIndex();
        StaggerService stagger = sim.stagger();
        landTramples(sim, now);
        landTosses(sim);

        drain.clear();
        for (Request r; (r = requests.poll()) != null; ) drain.add(r);
        drain.sort(Comparator.comparingLong(Request::brute));
        for (Request r : drain) {
            if (!sim.getRoster().isAliveById(r.brute()) || stagger.isStaggered(r.brute())) continue;
            if (busy(r.brute()) || !ready(r.brute(), now)) continue;
            begin(r, sim, now);
        }

        order.clear();
        for (long id : charges.keySet()) order.add(id);
        order.sort(null);
        for (int i = 0; i < order.size(); i++) {
            long brute = order.getLong(i);
            Charge c = charges.get(brute);
            if (!sim.getRoster().isAliveById(brute)) {
                charges.remove(brute);
                continue;
            }
            switch (c.phase) {
                case TELL -> {
                    if (stagger.isStaggered(brute)) pullUp(c);
                    else if (--c.phaseTicks <= 0) {
                        c.phase = Phase.RUN;
                        log(sim, now, brute, "run", c.target, -1, -1);
                    }
                }
                case RUN -> {
                    if (stagger.isStaggered(brute)) pullUp(c);
                    else run(brute, c, sim, now);
                }
                case PULL_UP -> {
                    if (--c.phaseTicks <= 0) {
                        c.phase = Phase.IDLE;
                        c.readyTick = now + BruteChargeRules.ticks(BruteChargeRules.COOLDOWN);
                    }
                }
                case IDLE -> { }
            }
        }
        slide(sim);
    }

    /** Moves every sliding body one tick, in id order, and drops the ones at rest. */
    private void slide(BattleSimulation sim) {
        if (slides.isEmpty()) return;
        slideOrder.clear();
        for (long id : slides.keySet()) slideOrder.add(id);
        slideOrder.sort(null);
        float dt = BattleSimulation.TICK_DT;
        for (int i = 0; i < slideOrder.size(); i++) {
            long id = slideOrder.getLong(i);
            Slide s = slides.get(id);
            if (!sim.getRoster().isAliveById(id)) {
                slides.remove(id);
                continue;
            }
            float x = sim.world().x(id), y = sim.world().y(id);
            float sx = s.vx * dt, sy = s.vy * dt;
            ManualTerrainMotion.Result moved = ManualTerrainMotion.move(sim.getGrid(), x, y, sx, sy,
                    sim.getRoster().radius(id));
            sim.world().setPos(id, moved.x(), moved.y());
            float speed = (float) Math.hypot(s.vx, s.vy);
            float left = Math.max(0f, speed - s.decel * dt);
            boolean stopped = Math.hypot(moved.dx(), moved.dy()) < Math.hypot(sx, sy) * .3f;
            if (left <= 0f || stopped) {
                slides.remove(id);
            } else {
                s.vx *= left / speed;
                s.vy *= left / speed;
            }
        }
    }

    /** Starts {@code body} sliding {@code cells} along the unit direction {@code (dx, dy)}. */
    private void launch(long body, float dx, float dy, float cells, float decel, BattleSimulation sim) {
        float v = BruteChargeRules.launchSpeed(cells, decel);
        slides.put(body, new Slide(dx * v, dy * v, decel));
        sim.clearPath(body);
    }

    /**
     * Thread-safe; posted by a Matriarch's reap for each body it cut, and by a Harrier for a body it threw as
     * that body comes down ({@code HarrierLift}). Lands next serial pass: a body that
     * lived through the cut is knocked down for {@code holdSeconds} and thrown {@code cells} along
     * {@code (dx, dy)}, sliding to rest as a trampled one does.
     */
    public void toss(long attacker, long body, float dx, float dy) {
        tosses.add(new Toss(attacker, body, dx, dy));
    }

    /** How far a reap throws a body, cells, and how long it lies. */
    static final float TOSS_CELLS = 1.6f, TOSS_HOLD = .8f;

    private void landTosses(BattleSimulation sim) {
        tossDrain.clear();
        for (Toss t; (t = tosses.poll()) != null; ) tossDrain.add(t);
        if (tossDrain.isEmpty()) return;
        tossDrain.sort(Comparator.comparingLong(Toss::attacker).thenComparingLong(Toss::body));
        for (Toss t : tossDrain) {
            if (!sim.getRoster().isAliveById(t.body())) continue;
            sim.stagger().stagger(t.body(), BruteChargeRules.ticks(TOSS_HOLD), Cause.KNOCKED_DOWN);
            launch(t.body(), t.dx(), t.dy(), TOSS_CELLS, BruteChargeRules.BODY_SLIDE, sim);
            sim.swarmCues().post(SwarmCues.Kind.THROWN, t.body(), sim);
        }
    }

    /** Whether {@code body} is still sliding from a hit. */
    public boolean sliding(long body) { return slides.containsKey(body); }

    /** Lands the posted tramples, in Brute id order. */
    private void landTramples(BattleSimulation sim, int now) {
        trampleDrain.clear();
        for (Trample tr; (tr = tramples.poll()) != null; ) trampleDrain.add(tr);
        if (trampleDrain.isEmpty()) return;
        trampleDrain.sort(Comparator.comparingLong(Trample::brute));
        UnitRosterService roster = sim.getRoster();
        for (Trample tr : trampleDrain) {
            long brute = tr.brute();
            if (!roster.isAliveById(brute) || !roster.isAliveById(tr.target())) continue;
            float bx = sim.world().x(brute), by = sim.world().y(brute);
            float tx = sim.world().x(tr.target()), ty = sim.world().y(tr.target());
            float d = (float) Math.hypot(tx - bx, ty - by);
            float hx = d > 1e-4f ? (tx - bx) / d : 1f, hy = d > 1e-4f ? (ty - by) / d : 0f;
            if (!tr.trample()) {
                log(sim, now, brute, "blow", tr.target(), -1, -1);
                push(brute, tr.target(), BruteChargeRules.BLOW_SPEED, false, sim, now, hx, hy);
                continue;
            }
            float damage = sim.combat().attackDamage(brute);
            UnitType type = sim.identity().type(brute);
            log(sim, now, brute, "trample", tr.target(), -1, -1);
            // The hooves come down on the ground the body stood on: whoever else is under them goes down too.
            Faction side = sim.identity().faction(brute);
            nearby.clear();
            sim.getUnitIndex().gather(tx, ty, BruteChargeRules.TRAMPLE_RADIUS, nearby);
            long[] under = Arrays.copyOf(nearby.ids, nearby.size);
            Arrays.sort(under);
            trampleOne(brute, tr.target(), damage, type, hx, hy, sim);
            for (long id : under) {
                if (id == brute || id == tr.target() || !roster.isAliveById(id)) continue;
                Faction other = sim.identity().faction(id);
                if (other == side || other.friendlyTo(side) || kind(id, sim) != Kind.INFANTRY) continue;
                float rx = sim.world().x(id) - bx, ry = sim.world().y(id) - by;
                float rl = (float) Math.hypot(rx, ry);
                trampleOne(brute, id, damage * BruteChargeRules.TRAMPLE_SPLASH, type,
                        rl > 1e-4f ? rx / rl : hx, rl > 1e-4f ? ry / rl : hy, sim);
            }
        }
    }

    private void trampleOne(long brute, long body, float damage, UnitType type, float dx, float dy,
                            BattleSimulation sim) {
        sim.applyDamage(body, brute, damage, SwarmBruteBehavior.MELEE_PENETRATION, type.moraleImpact);
        if (!sim.getRoster().isAliveById(body)) return;
        sim.stagger().stagger(body, BruteChargeRules.ticks(BruteChargeRules.TRAMPLE_HOLD), Cause.KNOCKED_DOWN);
        launch(body, dx, dy, BruteChargeRules.TRAMPLE_BOWL, BruteChargeRules.BODY_SLIDE, sim);
        sim.swarmCues().post(SwarmCues.Kind.THROWN, body, sim);
    }

    private void begin(Request r, BattleSimulation sim, int now) {
        Charge c = charges.computeIfAbsent(r.brute(), id -> new Charge());
        float x = sim.world().x(r.brute()), y = sim.world().y(r.brute());
        c.phase = Phase.TELL;
        c.phaseTicks = Math.max(1, BruteChargeRules.ticks(BruteChargeRules.TELL));
        c.target = r.target();
        c.aimX = r.aimX();
        c.aimY = r.aimY();
        c.heading = (float) Math.toDegrees(Math.atan2(r.aimY() - y, r.aimX() - x));
        c.originX = x;
        c.originY = y;
        c.run = 0f;
        c.pace = 1f;
        c.bodies = 0;
        c.struck.clear();
        c.reach = openReach(sim.getGrid(), x, y, c.heading, BruteChargeRules.MAX_RUN);
        sim.clearPath(r.brute());
        log(sim, now, r.brute(), "tell", r.target(), -1, -1);
    }

    private void run(long brute, Charge c, BattleSimulation sim, int now) {
        UnitRosterService roster = sim.getRoster();
        NavigationGrid grid = sim.getGrid();
        float dt = BattleSimulation.TICK_DT;
        float x = sim.world().x(brute), y = sim.world().y(brute);
        if (c.target != 0L && roster.isAliveById(c.target)) {
            c.aimX = sim.world().x(c.target);
            c.aimY = sim.world().y(c.target);
        }
        float desired = (float) Math.toDegrees(Math.atan2(c.aimY - y, c.aimX - x));
        c.heading = BruteChargeRules.turnToward(c.heading, desired, BruteChargeRules.TURN_RATE * dt);
        float hx = (float) Math.cos(Math.toRadians(c.heading));
        float hy = (float) Math.sin(Math.toRadians(c.heading));
        float radius = roster.radius(brute);
        SwarmStage stage = sim.identity().swarmStage(brute);
        float walk = SwarmStageStats.of(UnitType.SWARM_BRUTE, stage).speed();
        float scale = SwarmStageStats.bruteChargeSpeed(stage) * c.pace / walk;
        float intended = Math.max(0f, roster.movement().moveSpeed(brute)) * dt * scale;
        ManualTerrainMotion.Result moved = roster.movement().moveDirectScaled(brute, grid, hx, hy, radius, dt, scale);
        float applied = (float) Math.hypot(moved.dx(), moved.dy());
        c.run += applied;

        if (hitBodies(brute, c, sim, now, hx, hy)) return;
        if (applied < intended * .3f && terrain(brute, c, sim, now, hx, hy)) return;
        if (c.run >= BruteChargeRules.MAX_RUN) {
            log(sim, now, brute, "spent", 0L, -1, -1);
            pullUp(c);
        }
    }

    /** Lands the charge on whatever it has reached. True when the charge ended. */
    private boolean hitBodies(long brute, Charge c, BattleSimulation sim, int now, float hx, float hy) {
        UnitRosterService roster = sim.getRoster();
        float x = sim.world().x(brute), y = sim.world().y(brute);
        float radius = roster.radius(brute);
        Faction side = sim.identity().faction(brute);
        nearby.clear();
        sim.getUnitIndex().gather(x, y, radius + 1.5f, nearby);
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < nearby.size; i++) {
            long id = nearby.ids[i];
            if (id == brute || c.struck.contains(id) || !roster.isAliveById(id)) continue;
            Faction other = sim.identity().faction(id);
            if (other == side || other.friendlyTo(side)) continue;
            if (kind(id, sim) == null) continue;
            float rx = sim.world().x(id) - x, ry = sim.world().y(id) - y;
            if (rx * hx + ry * hy <= 0f) continue;
            float d = (float) Math.hypot(rx, ry);
            if (d > radius + roster.radius(id) + .05f) continue;
            if (d < bestDistance || (d == bestDistance && id < best)) {
                best = id;
                bestDistance = d;
            }
        }
        if (best == 0L) return false;
        return strike(brute, best, c, sim, now, hx, hy);
    }

    /** What {@code id} is to a charge, or null for something it does not hit (fliers, drones, swarm). */
    /** Whether {@code id} is a body on foot, which a blow knocks down and a toss throws. */
    public static boolean isInfantry(long id, BattleSimulation sim) { return kind(id, sim) == Kind.INFANTRY; }

    static Kind kind(long id, BattleSimulation sim) {
        if (sim.convoy().isVehicle(id)) return BruteChargeRules.vehicleKind(sim.convoy().vehicleType(id).weight());
        UnitType type = sim.identity().type(id);
        if (type.isSwarm() || type.isBasedAircraft() || type.isDrone()) return null;
        if (type.isMech()) {
            var loadout = sim.world().mechLoadout(id);
            return BruteChargeRules.mechKind(loadout == null ? 1f : loadout.variant.relativeMass);
        }
        if (type.isStatic()) return Kind.EMPLACEMENT;
        if (type.isGroundVehicle()) return null;
        return Kind.INFANTRY;
    }

    private boolean strike(long brute, long target, Charge c, BattleSimulation sim, int now, float hx, float hy) {
        UnitRosterService roster = sim.getRoster();
        Kind kind = kind(target, sim);
        SwarmStage stage = sim.identity().swarmStage(brute);
        Outcome outcome = BruteChargeRules.outcome(kind, stage);
        c.struck.add(target);
        UnitType type = sim.identity().type(brute);
        float damage = sim.combat().attackDamage(brute) * BruteChargeRules.DAMAGE_SCALE;
        if (outcome.legs()) {
            roster.damageService().applyDamage(target, brute, damage, SwarmBruteBehavior.MELEE_PENETRATION,
                    type.moraleImpact, BodyPart.LEGS);
        } else {
            sim.applyDamage(target, brute, damage, SwarmBruteBehavior.MELEE_PENETRATION, type.moraleImpact);
        }
        log(sim, now, brute, kind.name().toLowerCase(), target, -1, -1);
        // Head and hooves into it: the rig plays the blow off the cooldown, and the next one waits on it.
        sim.combat().setCooldownTimer(brute, sim.combat().attackCooldown(brute));
        boolean alive = roster.isAliveById(target);
        if (alive && outcome.throwCells() > 0f) throwBody(target, brute, outcome.throwCells(), sim, hx, hy);
        push(brute, target, SwarmStageStats.bruteChargeSpeed(stage) * c.pace, true, sim, now, hx, hy);
        if (alive && outcome.holdSeconds() > 0f) {
            sim.stagger().stagger(target, BruteChargeRules.ticks(outcome.holdSeconds()),
                    BruteChargeRules.holdCause(kind));
        }
        if (outcome.bruteStunSeconds() > 0f) {
            sim.stagger().stagger(brute, BruteChargeRules.ticks(outcome.bruteStunSeconds()), Cause.STUNNED);
            log(sim, now, brute, "stunned", target, -1, -1);
        }
        if (outcome.goesOn() && ++c.bodies <= BruteChargeRules.maxBodies(stage)) {
            c.pace *= BruteChargeRules.BODY_SLOW;
            // Through it and on along the line: nothing left to steer at.
            if (target == c.target) {
                c.target = 0L;
                c.aimX = sim.world().x(brute) + hx * BruteChargeRules.MAX_RUN;
                c.aimY = sim.world().y(brute) + hy * BruteChargeRules.MAX_RUN;
            }
            return false;
        }
        pullUp(c);
        return true;
    }

    private void throwBody(long body, long brute, float distance, BattleSimulation sim, float hx, float hy) {
        float bx = sim.world().x(body), by = sim.world().y(body);
        float[] dir = BruteChargeRules.throwDirection(hx, hy,
                bx - sim.world().x(brute), by - sim.world().y(brute));
        launch(body, dir[0], dir[1], distance, BruteChargeRules.BODY_SLIDE, sim);
        frighten(body, bx, by, brute, sim);
    }

    /** A hull's bulk is not a blade: what the Brute takes from one goes through its plate. */
    private static final float RECOIL_PENETRATION = 30f;

    /**
     * The Brute's weight, closing at {@code speed} along {@code (hx, hy)}, lands on {@code target}: a hull through
     * the contact ({@link #collide}), a mech as one body against another, pushed off its feet a little way.
     * Infantry and emplacements take nothing here (infantry is thrown or trampled; an emplacement is fixed).
     */
    private void push(long brute, long target, float speed, boolean charge, BattleSimulation sim, int now,
                      float hx, float hy) {
        if (!sim.getRoster().isAliveById(target)) return;
        if (sim.convoy().isVehicle(target)) {
            collide(brute, target, speed, charge, sim, now, hx, hy);
            return;
        }
        if (!sim.identity().type(target).isMech()) return;
        var loadout = sim.world().mechLoadout(target);
        float mass = BruteChargeRules.MECH_TONNES * (loadout == null ? 1f : loadout.variant.relativeMass);
        float dv = BruteChargeRules.pointPush(speed,
                SwarmStageStats.bruteMass(sim.identity().swarmStage(brute)), mass);
        float cells = dv * dv / (2f * BruteChargeRules.MECH_SLIDE);
        if (cells > 1e-3f) launch(target, hx, hy, cells, BruteChargeRules.MECH_SLIDE, sim);
    }

    /**
     * The Brute and a hull exchange momentum where they meet ({@link HullImpact}): the hull slides and turns
     * on its tires or tracks and rocks on its suspension ({@link HullSlide}), and the Brute rebounds with what
     * the hull did not take. A charge also hurts the Brute by whatever impulse its plate could not shed; a
     * blow it chose to throw does not.
     */
    private void collide(long brute, long hull, float speed, boolean charge, BattleSimulation sim, int now,
                         float hx, float hy) {
        GroundBody body = sim.convoy().body(hull);
        if (body == null) return;
        VehicleType type = sim.convoy().vehicleType(hull);
        double rad = Math.toRadians(body.facingDegrees);
        float hvx = (float) -Math.sin(rad) * body.speed + body.knockVx;
        float hvy = (float) Math.cos(rad) * body.speed + body.knockVy;
        HullImpact.Result hit = HullImpact.strike(body.x, body.y, body.facingDegrees,
                type.visualLengthCells / 2f, type.visualWidthCells / 2f, type.massTonnes(),
                hvx, hvy, body.knockOmega, sim.world().x(brute), sim.world().y(brute), hx * speed, hy * speed,
                SwarmStageStats.bruteMass(sim.identity().swarmStage(brute)), BruteChargeRules.RESTITUTION);
        if (hit.impulse() <= 0f) return;
        HullSlide.apply(body, hit);
        impulses.put(hull, Float.valueOf(hit.impulse()));
        float rvx = hx * speed + hit.strikerVx(), rvy = hy * speed + hit.strikerVy();
        if (Math.hypot(rvx, rvy) > 1e-3f) slides.put(brute, new Slide(rvx, rvy, BruteChargeRules.BRUTE_SLIDE));
        float recoil = charge ? BruteChargeRules.recoilDamage(hit.impulse()) : 0f;
        if (recoil > 0f) {
            sim.applyDamage(brute, hull, recoil, RECOIL_PENETRATION, 0f);
            log(sim, now, brute, "recoil", hull, -1, -1);
        }
    }

    /** Every squad mate within reach of a thrown body takes a near-miss drain. */
    private void frighten(long thrown, float x, float y, long brute, BattleSimulation sim) {
        UnitRosterService roster = sim.getRoster();
        Faction side = sim.identity().faction(thrown);
        float impact = sim.identity().type(brute).moraleImpact;
        fear.clear();
        sim.getUnitIndex().gatherFaction(x, y, BruteChargeRules.FEAR_RADIUS, side, fear);
        for (int i = 0; i < fear.size; i++) {
            long member = fear.ids[i];
            if (member == thrown || !roster.isAliveById(member) || !roster.squad().hasSquad(member)) continue;
            Squad squad = roster.getSquad(roster.squad().squadId(member));
            if (squad == null || squad.isMechSquad()) continue;
            FireTeamMorale team = squad.fireTeamMorale(roster.squad().fireTeamIndex(member));
            team.timeSinceUnderFire = 0f;
            if (team.drainCooldown > 0f) continue;
            team.morale = Math.max(0f, team.morale - SquadMoraleSystem.nearMissDrain(team.cap(), impact,
                    roster.combat().moraleDrainMultiplier(member)));
            team.drainCooldown = SquadMoraleSystem.MORALE_DRAIN_COOLDOWN;
        }
    }

    /** The run has stalled against the ground ahead. True when that ended the charge. */
    private boolean terrain(long brute, Charge c, BattleSimulation sim, int now, float hx, float hy) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        float x = sim.world().x(brute), y = sim.world().y(brute);
        float probe = sim.getRoster().radius(brute) + .35f;
        int ox = (int) Math.floor(x), oy = (int) Math.floor(y);
        int cx = (int) Math.floor(x + hx * probe), cy = (int) Math.floor(y + hy * probe);
        if (grid.inBounds(cx, cy) && !grid.isWalkable(cx, cy)) {
            int hp = grid.getWallHp(cx, cy);
            boolean light = hp > 0 && hp <= SwarmBruteBehavior.LIGHT_WALL_HP;
            // A barricade on a fort's or an objective's ground is rammed like its wall, never bowled through.
            boolean works = FortifiedWorks.isWorks(grid, cx, cy, null);
            boolean barricade = light && sim.isBarricadeAt(cx, cy);
            if (barricade && !works) {
                sim.mapEditor().damageWall(cx, cy, hp);
                c.pace *= BruteChargeRules.BODY_SLOW;
                log(sim, now, brute, "prop", 0L, cx, cy);
                return false;
            }
            if (barricade || light && (topology.isWall(cx, cy) || topology.isWindow(cx, cy))) {
                sim.mapEditor().damageWall(cx, cy, FortifiedWorks.swarmDamage(grid, cx, cy, null,
                        BruteChargeRules.wallBlow(sim.identity().swarmStage(brute))));
                log(sim, now, brute, "ram", 0L, cx, cy);
            } else if (hp > 0 || topology.isWall(cx, cy) || topology.isWindow(cx, cy)) {
                sim.stagger().stagger(brute, BruteChargeRules.ticks(BruteChargeRules.STUN), Cause.STUNNED);
                log(sim, now, brute, "stunned", 0L, cx, cy);
            } else {
                log(sim, now, brute, "blocked", 0L, cx, cy);
            }
            pullUp(c);
            return true;
        }
        Direction step = cardinal(cx - ox, cy - oy);
        if (step != null) {
            SharedEdgeBarrier barrier = grid.getEdgeBarrier(ox, oy, step);
            if (barrier != null && barrier.kind().blocksMovement()) {
                if (barrier.kind() == SharedEdgeBarrier.Kind.WINDOW) {
                    sim.mapEditor().damageEdgeBarrier(ox, oy, step, FortifiedWorks.swarmDamage(grid, ox, oy, step,
                            BruteChargeRules.wallBlow(sim.identity().swarmStage(brute))));
                    log(sim, now, brute, "ram", 0L, ox, oy);
                } else {
                    sim.stagger().stagger(brute, BruteChargeRules.ticks(BruteChargeRules.STUN), Cause.STUNNED);
                    log(sim, now, brute, "stunned", 0L, ox, oy);
                }
                pullUp(c);
                return true;
            }
        }
        log(sim, now, brute, "blocked", 0L, cx, cy);
        pullUp(c);
        return true;
    }

    private static Direction cardinal(int dx, int dy) {
        for (Direction d : Direction.CARDINALS) if (d.dx == dx && d.dy == dy) return d;
        return null;
    }

    private static void pullUp(Charge c) {
        c.phase = Phase.PULL_UP;
        c.phaseTicks = Math.max(1, BruteChargeRules.ticks(BruteChargeRules.PULL_UP));
    }

    /** How far open ground runs along {@code heading} from a point, up to {@code max}: the charge's reach. */
    static float openReach(NavigationGrid grid, float x, float y, float heading, float max) {
        float hx = (float) Math.cos(Math.toRadians(heading)), hy = (float) Math.sin(Math.toRadians(heading));
        for (float d = .25f; d <= max; d += .25f) {
            int cx = (int) Math.floor(x + hx * d), cy = (int) Math.floor(y + hy * d);
            if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return d;
        }
        return max;
    }

    private void log(BattleSimulation sim, int tick, long brute, String what, long target, int cellX, int cellY) {
        if (events.size() < EVENT_CAP) events.add(new Event(tick, brute, what, target, cellX, cellY));
        // A stun with no target is a heavy wall: the boom, then the groan.
        if (what.equals("stunned") && target == 0L) cue(sim, SwarmCues.Kind.CHARGE_HIT_WALL, brute, cellX, cellY);
        SwarmCues.Kind kind = cueKind(what);
        if (kind != null) cue(sim, kind, brute, cellX, cellY);
    }

    /** The sound a logged charge event asks for (`colony-breach-nouns.md`, Sound), or null for a silent one. */
    static SwarmCues.Kind cueKind(String what) {
        return switch (what) {
            case "tell" -> SwarmCues.Kind.CHARGE_TELL;
            case "run" -> SwarmCues.Kind.CHARGE_RUN;
            case "infantry" -> SwarmCues.Kind.CHARGE_HIT_BODY;
            case "ram" -> SwarmCues.Kind.CHARGE_HIT_WALL;
            case "prop" -> SwarmCues.Kind.WALL_BREAK;
            case "stunned" -> SwarmCues.Kind.STUNNED;
            case "emplacement", "light_vehicle", "medium_vehicle", "heavy_vehicle",
                 "light_mech", "medium_mech", "heavy_mech" -> SwarmCues.Kind.CHARGE_HIT_VEHICLE;
            default -> null;
        };
    }

    private static void cue(BattleSimulation sim, SwarmCues.Kind kind, long brute, int cellX, int cellY) {
        if (cellX >= 0) sim.swarmCues().postAt(kind, brute, cellX, cellY, sim);
        else sim.swarmCues().post(kind, brute, sim);
    }
}
