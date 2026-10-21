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
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
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
 */
public final class BruteChargeSystem {

    /** Where a Brute is in its charge. {@code IDLE} is walking, possibly still on cooldown. */
    public enum Phase { IDLE, TELL, RUN, PULL_UP }

    /** One thing a charge did, kept for evidence. */
    public record Event(int tick, long brute, String what, long target, int cellX, int cellY) {}

    private record Request(long brute, long target, float aimX, float aimY) {}

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
    private final Long2ObjectOpenHashMap<Charge> charges = new Long2ObjectOpenHashMap<>();
    private final List<Request> drain = new ArrayList<>();
    private final LongArrayList order = new LongArrayList();
    private final LongBucket nearby = new LongBucket();
    private final LongBucket fear = new LongBucket();
    private final List<Event> events = new ArrayList<>();

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

    // ---- presentation and evidence ----

    public Phase phase(long brute) {
        Charge c = charges.get(brute);
        return c == null ? Phase.IDLE : c.phase;
    }

    /** The heading a charge runs on, in degrees, and how far it can go: the tell's ground wedge. */
    public float headingDegrees(long brute) {
        Charge c = charges.get(brute);
        return c == null ? 0f : c.heading;
    }

    public float reach(long brute) {
        Charge c = charges.get(brute);
        return c == null ? 0f : c.reach;
    }

    /** Ticks a tell lasts, for the presentation's fade. */
    public int tellTicks() { return BruteChargeRules.ticks(BruteChargeRules.TELL); }

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

    /** What charges have done this battle, oldest first (capped). */
    public List<Event> events() { return events; }

    // ---- serial ----

    /** Serial: starts posted charges and advances every charge one tick. */
    public void tick(BattleSimulation sim) {
        int now = sim.getSimTickIndex();
        StaggerService stagger = sim.stagger();

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
                        log(now, brute, "run", c.target, -1, -1);
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
        sim.swarmCues().post(SwarmCues.Kind.CHARGE, r.brute(), sim);
        log(now, r.brute(), "tell", r.target(), -1, -1);
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
        float walk = sim.identity().type(brute).moveSpeed;
        float scale = BruteChargeRules.CHARGE_SPEED * c.pace / walk;
        float intended = Math.max(0f, roster.movement().moveSpeed(brute)) * dt * scale;
        ManualTerrainMotion.Result moved = roster.movement().moveDirectScaled(brute, grid, hx, hy, radius, dt, scale);
        float applied = (float) Math.hypot(moved.dx(), moved.dy());
        c.run += applied;

        if (hitBodies(brute, c, sim, now, hx, hy)) return;
        if (applied < intended * .3f && terrain(brute, c, sim, now, hx, hy)) return;
        if (c.run >= BruteChargeRules.MAX_RUN) {
            log(now, brute, "spent", 0L, -1, -1);
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
        Outcome outcome = BruteChargeRules.outcome(kind);
        c.struck.add(target);
        UnitType type = sim.identity().type(brute);
        float damage = sim.combat().attackDamage(brute) * BruteChargeRules.DAMAGE_SCALE;
        if (outcome.legs()) {
            roster.damageService().applyDamage(target, brute, damage, SwarmBruteBehavior.MELEE_PENETRATION,
                    type.moraleImpact, BodyPart.LEGS);
        } else {
            sim.applyDamage(target, brute, damage, SwarmBruteBehavior.MELEE_PENETRATION, type.moraleImpact);
        }
        log(now, brute, kind.name().toLowerCase(), target, -1, -1);
        boolean alive = roster.isAliveById(target);
        if (alive && outcome.throwCells() > 0f) throwBody(target, brute, outcome.throwCells(), sim, hx, hy);
        if (alive && outcome.shoveCells() > 0f) shove(target, outcome.shoveCells(), sim, hx, hy);
        if (alive && outcome.holdSeconds() > 0f) {
            sim.stagger().stagger(target, BruteChargeRules.ticks(outcome.holdSeconds()),
                    BruteChargeRules.holdCause(kind));
        }
        if (outcome.bruteStunSeconds() > 0f) {
            sim.stagger().stagger(brute, BruteChargeRules.ticks(outcome.bruteStunSeconds()), Cause.STUNNED);
            log(now, brute, "stunned", target, -1, -1);
        }
        if (outcome.goesOn() && ++c.bodies <= BruteChargeRules.MAX_BODIES) {
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
        ManualTerrainMotion.Result landed = ManualTerrainMotion.move(sim.getGrid(), bx, by,
                dir[0] * distance, dir[1] * distance, sim.getRoster().radius(body));
        sim.world().setPos(body, landed.x(), landed.y());
        sim.clearPath(body);
        frighten(body, landed.x(), landed.y(), brute, sim);
    }

    private void shove(long hull, float distance, BattleSimulation sim, float hx, float hy) {
        GroundBody body = sim.convoy().body(hull);
        if (body == null) return;
        ManualTerrainMotion.Result moved = ManualTerrainMotion.move(sim.getGrid(), body.x, body.y,
                hx * distance, hy * distance, sim.getRoster().radius(hull));
        body.teleport(moved.x(), moved.y(), body.facingDegrees);
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
            if (light && sim.isBarricadeAt(cx, cy)) {
                sim.mapEditor().damageWall(cx, cy, hp);
                c.pace *= BruteChargeRules.BODY_SLOW;
                log(now, brute, "prop", 0L, cx, cy);
                return false;
            }
            if (light && (topology.isWall(cx, cy) || topology.isWindow(cx, cy))) {
                boolean broke = sim.mapEditor().damageWall(cx, cy, BruteChargeRules.WALL_BLOW);
                sim.swarmCues().postAt(broke ? SwarmCues.Kind.WALL_BREAK : SwarmCues.Kind.WALL_BLOW, brute, cx, cy, sim);
                log(now, brute, "ram", 0L, cx, cy);
            } else if (hp > 0 || topology.isWall(cx, cy) || topology.isWindow(cx, cy)) {
                sim.stagger().stagger(brute, BruteChargeRules.ticks(BruteChargeRules.STUN), Cause.STUNNED);
                log(now, brute, "stunned", 0L, cx, cy);
            } else {
                log(now, brute, "blocked", 0L, cx, cy);
            }
            pullUp(c);
            return true;
        }
        Direction step = cardinal(cx - ox, cy - oy);
        if (step != null) {
            SharedEdgeBarrier barrier = grid.getEdgeBarrier(ox, oy, step);
            if (barrier != null && barrier.kind().blocksMovement()) {
                if (barrier.kind() == SharedEdgeBarrier.Kind.WINDOW) {
                    boolean broke = sim.mapEditor().damageEdgeBarrier(ox, oy, step, BruteChargeRules.WALL_BLOW);
                    sim.swarmCues().postAt(broke ? SwarmCues.Kind.WALL_BREAK : SwarmCues.Kind.WALL_BLOW, brute, ox, oy, sim);
                    log(now, brute, "ram", 0L, ox, oy);
                } else {
                    sim.stagger().stagger(brute, BruteChargeRules.ticks(BruteChargeRules.STUN), Cause.STUNNED);
                    log(now, brute, "stunned", 0L, ox, oy);
                }
                pullUp(c);
                return true;
            }
        }
        log(now, brute, "blocked", 0L, cx, cy);
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

    /** How far open ground runs along {@code heading} from a point, up to {@code max}: the tell's wedge. */
    static float openReach(NavigationGrid grid, float x, float y, float heading, float max) {
        float hx = (float) Math.cos(Math.toRadians(heading)), hy = (float) Math.sin(Math.toRadians(heading));
        for (float d = .25f; d <= max; d += .25f) {
            int cx = (int) Math.floor(x + hx * d), cy = (int) Math.floor(y + hy * d);
            if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return d;
        }
        return max;
    }

    private void log(int tick, long brute, String what, long target, int cellX, int cellY) {
        if (events.size() < EVENT_CAP) events.add(new Event(tick, brute, what, target, cellX, cellY));
    }
}
