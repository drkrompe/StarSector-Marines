package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.combat.Interceptable;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Data owner for carrier-placed point-defence emplacements: the queue of
 * completed placements waiting to be minted, the live emplacements, and each
 * one's remaining lifetime and engagement budget.
 *
 * <p>Sibling to {@code SatchelChargeService} in shape and in threading: the
 * carrier's executor runs inside the parallel {@code UPDATE_UNITS} dispatch and
 * only enqueues, while {@link #tick} runs serially and does every world
 * mutation — minting the entity, engaging ordnance, retiring the burnt-out pod.
 *
 * <h2>What an engagement is</h2>
 * The pass walks {@code ShotService}'s in-flight {@link Projectile} list and
 * flips {@link Projectile#intercepted} on one hostile round per engagement. The
 * marked round is dropped by {@code ShotService.tickProjectiles} later in the
 * same tick without firing its payload, so <b>interception is not damage</b>: an
 * engaged round never reaches the detonation sink, credits no damage to anyone,
 * and is counted as its own telemetry quantity rather than as damage prevented.
 *
 * <p>A round is engageable only when its weapon data declares
 * {@code sim.pointDefenseTarget} (carried on the projectile as
 * {@link Projectile#pointDefenseTarget}). Nothing here consults a hard-coded
 * list of weapon ids, so a warhead authored tomorrow is engageable the moment
 * its data opts in.
 *
 * <h2>The three bounds</h2>
 * An emplacement is bounded on every axis, so it degrades a weapon class rather
 * than banning one:
 * <ul>
 *   <li><b>Radius</b> — only rounds physically inside
 *       {@code interceptRadius} of the mount are engageable. This is a
 *       geometric test on where the round <em>is</em>, never a belief query, so
 *       an emplacement gets no omniscient view of ordnance it could not
 *       physically reach.</li>
 *   <li><b>Rate</b> — one engagement per {@code engagementInterval}. A salvo
 *       arriving faster than that saturates the mount and the surplus rounds
 *       land.</li>
 *   <li><b>Capacity and lifetime</b> — a finite total number of engagements and
 *       a finite time on the field, whichever runs out first. It is also an
 *       ordinary damageable entity, so it can simply be shot.</li>
 * </ul>
 */
public final class PointDefenseService {

    /** Visible tracer duration for one engagement, in sim-seconds. Presentation only. */
    private static final float ENGAGEMENT_TRACER_SECONDS = 0.12f;
    /**
     * How long an engagement mark stays readable after the burst, in
     * sim-seconds. Long enough that a reviewer sampling frames a third of a
     * second apart still sees which rounds the mount stopped and which it
     * missed, rather than a projectile that silently ceased to exist.
     */
    private static final float ENGAGEMENT_MARK_SECONDS = 0.9f;
    /** Slack an interceptor needs over its own flight before the round would land. */
    private static final float LAUNCH_MARGIN_SECONDS = 0.05f;
    /** How far past the ground it covers a launcher reaches out to meet a round, as a multiple of its radius. */
    private static final float LAUNCH_REACH_MULT = 2f;

    private final Random rng;
    private final ArrayList<PendingPlacement> pending = new ArrayList<>();
    private final ArrayList<LiveEmplacement> active = new ArrayList<>();
    private final ArrayList<EngagementMark> marks = new ArrayList<>();
    /** Interceptor missiles in the air, each flying to where its round will be. */
    private final ArrayList<Interceptor> interceptors = new ArrayList<>();
    private final ArrayList<Projectile> launches = new ArrayList<>();
    private volatile List<EmplacementView> snapshot = List.of();
    private volatile List<EngagementView> markSnapshot = List.of();
    private long nextId = 1L;
    /** Cells from a hull within which an arcing round must be coming down for the hull's mount to take it. */
    static final float HULL_COVER_CELLS = 3f;
    /** Every hull that has been given its mount, with the rounds it engaged and stopped; a spent mount is not re-armed. */
    private final Map<Long, int[]> hullMounts = new HashMap<>();

    /** Takes the battle's seeded RNG so an engagement roll replays identically. */
    public PointDefenseService(Random rng) {
        this.rng = rng;
    }

    /**
     * Gives hull {@code hullId} its active protection: a carried mount whose carrier is the hull, read off
     * {@code body} for its position, running for the battle until its magazine is spent or the hull is gone. It
     * engages only arcing rounds coming down on the hull ({@link #comingDownOn}). A hull is mounted once; calling
     * again for the same hull, even after its mount is spent, does nothing. Serial only.
     */
    public synchronized void mountHull(long hullId, Faction faction, String structureId, GroundBody body) {
        if (hullMounts.containsKey(hullId)) return;
        hullMounts.put(hullId, new int[2]);
        StructureDef structure = DeployedEmplacement.requireBounded(structureId);
        DeployableEmplacementSpec spec = new DeployableEmplacementSpec(structureId, 0f,
                Float.POSITIVE_INFINITY, 0f, 0f, true);
        LiveEmplacement pod = new LiveEmplacement(nextId++, hullId, hullId, faction, structure, spec, body.x, body.y);
        pod.hull = body;
        active.add(pod);
    }

    /**
     * Stands a fixed mount at battle setup: an emplacement nobody carried, credited to itself, that runs
     * until its magazine is spent or it is destroyed. Serial only. Returns the emplacement's entity.
     */
    public synchronized long placeFixed(BattleControl sim, Faction faction, int cellX, int cellY, String structureId) {
        StructureDef structure = DeployedEmplacement.requireBounded(structureId);
        long entityId = sim.spawn(DeployedEmplacement.create("emplacement-" + nextId, faction, structureId, cellX, cellY));
        DeployableEmplacementSpec spec = new DeployableEmplacementSpec(structureId, 0f, Float.POSITIVE_INFINITY);
        active.add(new LiveEmplacement(nextId++, entityId, entityId, faction, structure, spec,
                cellX + 0.5f, cellY + 0.5f));
        return entityId;
    }

    /** Rounds hull {@code hullId}'s active protection engaged and stopped, as {@code {engaged, stopped}}. */
    public synchronized int[] hullInterceptions(long hullId) {
        int[] counts = hullMounts.get(hullId);
        return counts != null ? counts.clone() : new int[2];
    }

    /**
     * Records a completed placement channel. Called from the parallel dispatch,
     * so it only enqueues; the entity is minted at the next serial {@link #tick}.
     */
    public synchronized void queuePlacement(long carrierId, Faction faction,
                                            int cellX, int cellY,
                                            DeployableEmplacementSpec spec) {
        pending.add(new PendingPlacement(carrierId, faction, cellX, cellY, spec));
    }

    /**
     * Mints queued placements, ages every live emplacement, engages at most one
     * hostile round per emplacement whose engagement timer has come due, and
     * retires emplacements that ran out of time, magazine, or structure.
     *
     * <p>Must run before {@code ShotService.tickProjectiles} so a round engaged
     * this tick is dropped this tick, on the same beat it would otherwise have
     * detonated.
     */
    public synchronized void tick(float dt, BattleControl sim) {
        mintPending(sim);
        arriveInterceptors(dt, sim);
        List<Projectile> inFlight = sim.getActiveProjectiles();
        for (Iterator<LiveEmplacement> it = active.iterator(); it.hasNext(); ) {
            LiveEmplacement pod = it.next();
            if (sim.resolveUnit(pod.entityId) == 0L) {
                it.remove();
                continue;
            }
            pod.remainingLifetime -= dt;
            if (pod.remainingLifetime <= 0f || pod.engagementsRemaining <= 0) {
                burnOut(pod, sim);
                it.remove();
                continue;
            }
            pod.engagementTimer -= dt;
            if (pod.engagementTimer > 0f) continue;
            Projectile engaged = pickEngageable(pod, inFlight, sim);
            Interceptable body = pickBody(pod, sim, engaged);
            if (engaged == null && body == null) continue;
            if (body == null && pod.launchSpeed > 0f) {
                launch(pod, engaged, sim);
                continue;
            }
            // An engagement is a burst fired at a moving warhead, not a
            // guaranteed deletion. It costs a round from the magazine and puts
            // the mount on its interval whether or not it connects, so "it
            // fired and lost" is a real, reachable, visible state.
            // A warship's missile that is nearer than any round is the one engaged; a tough one is harder to stop.
            float chance = body != null ? pod.hitChance * Interceptable.toughnessFactor(body.hitpoints()) : pod.hitChance;
            boolean stopped = rng.nextFloat() < chance;
            if (stopped) {
                if (body != null) body.intercept();
                else engaged.intercepted = true;
            }
            pod.engagementsRemaining--;
            pod.engagementTimer = pod.engagementInterval;
            sim.telemetry().recordOrdnanceEngaged(pod.carrierId);
            if (stopped) sim.telemetry().recordOrdnanceIntercepted(pod.carrierId);
            if (pod.hull != null) {
                int[] counts = hullMounts.get(pod.entityId);
                counts[0]++;
                if (stopped) counts[1]++;
            }
            if (body != null) postEngagement(pod, body.x(), body.y(), stopped, sim);
            else postEngagement(pod, engaged.currentX(), engaged.currentY(), stopped, sim);
        }
        // Queued after the walk: the list being walked is the one they join.
        for (Projectile missile : launches) sim.queueProjectile(missile);
        launches.clear();
        ageMarks(dt);
        publishSnapshot(sim);
    }

    /**
     * Engagement marks left by the last second of shooting, for the renderer.
     * Both outcomes are published: a miss that is drawn differently from a kill
     * is what separates "the emplacement lost that one" from "the missile was
     * never there".
     */
    public List<EngagementView> recentEngagements() { return markSnapshot; }

    private void ageMarks(float dt) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            EngagementMark mark = marks.get(i);
            mark.remaining -= dt;
            if (mark.remaining <= 0f) marks.remove(i);
        }
        ArrayList<EngagementView> views = new ArrayList<>(marks.size());
        for (EngagementMark mark : marks) {
            views.add(new EngagementView(mark.faction, mark.x, mark.y, mark.stopped,
                    Math.max(0f, mark.remaining / ENGAGEMENT_MARK_SECONDS)));
        }
        markSnapshot = List.copyOf(views);
    }

    /** Live emplacements, for the renderer and for the carrier's do-not-stack gate. */
    public List<EmplacementView> activeEmplacements() { return snapshot; }

    /**
     * True when {@code faction} already has a live emplacement whose radius
     * covers {@code (x, y)}. The carrier's opportunity gate reads this so a
     * squad does not stack four pods on one spot.
     */
    public boolean isCovered(Faction faction, float x, float y) {
        for (EmplacementView pod : snapshot) {
            if (pod.faction() != faction) continue;
            float dx = pod.x() - x;
            float dy = pod.y() - y;
            if (dx * dx + dy * dy <= pod.interceptRadius() * pod.interceptRadius()) return true;
        }
        return false;
    }

    private void mintPending(BattleControl sim) {
        if (pending.isEmpty()) return;
        for (PendingPlacement placement : pending) {
            DeployableEmplacementSpec spec = placement.spec;
            if (spec.carried()) {
                mintCarried(placement, sim);
                continue;
            }
            long entityId = sim.spawn(DeployedEmplacement.create(
                    "emplacement-" + nextId, placement.faction, spec.structureId(),
                    placement.cellX, placement.cellY));
            StructureDef structure = DeployedEmplacement.requireBounded(spec.structureId());
            active.add(new LiveEmplacement(nextId++, entityId, placement.carrierId,
                    placement.faction, structure, spec,
                    placement.cellX + 0.5f, placement.cellY + 0.5f));
        }
        pending.clear();
    }

    /**
     * A carried mount is the carrier: it mints no entity, reads its position off the marine
     * wearing it each tick, and ends when they fall. One per carrier, so a second switch-on
     * while the first still runs adds nothing.
     */
    private void mintCarried(PendingPlacement placement, BattleControl sim) {
        long carrier = sim.resolveUnit(placement.carrierId);
        if (carrier == 0L) return;
        for (LiveEmplacement live : active) {
            if (live.carried && live.entityId == carrier) return;
        }
        StructureDef structure = DeployedEmplacement.requireBounded(placement.spec.structureId());
        active.add(new LiveEmplacement(nextId++, carrier, placement.carrierId, placement.faction,
                structure, placement.spec, sim.world().x(carrier), sim.world().y(carrier)));
    }

    /**
     * Nearest hostile, not-yet-engaged, opted-in round physically inside the
     * radius. Nearest-first so a saturating salvo loses the round closest to
     * landing rather than an arbitrary one.
     */
    /**
     * The nearest hostile warship missile or warhead physically inside the radius, if it is nearer
     * than the round already picked (or none was); null otherwise.
     */
    private Interceptable pickBody(LiveEmplacement pod, BattleControl sim, Projectile engaged) {
        List<Interceptable> bodies = sim.interceptableOrdnance();
        if (bodies.isEmpty()) return null;
        float podX = x(pod, sim);
        float podY = y(pod, sim);
        float bestDistanceSq = pod.interceptRadius * pod.interceptRadius;
        if (engaged != null) {
            float dx = engaged.currentX() - podX;
            float dy = engaged.currentY() - podY;
            bestDistanceSq = Math.min(bestDistanceSq, dx * dx + dy * dy);
        }
        Interceptable best = null;
        for (int i = 0, n = bodies.size(); i < n; i++) {
            Interceptable b = bodies.get(i);
            if (b.isIntercepted() || !b.canBeIntercepted() || !pod.faction.hostileTo(b.faction())) continue;
            float dx = b.x() - podX;
            float dy = b.y() - podY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq >= bestDistanceSq) continue;
            best = b;
            bestDistanceSq = distanceSq;
        }
        return best;
    }

    private Projectile pickEngageable(LiveEmplacement pod, List<Projectile> inFlight,
                                      BattleControl sim) {
        float podX = x(pod, sim);
        float podY = y(pod, sim);
        float reachSq = pod.interceptRadius * pod.interceptRadius;
        Projectile best = null;
        float bestDistanceSq = Float.MAX_VALUE;
        for (int i = 0, n = inFlight.size(); i < n; i++) {
            Projectile p = inFlight.get(i);
            if (!p.pointDefenseTarget || p.intercepted) continue;
            if (pod.hull != null && !comingDownOn(p, podX, podY)) continue;
            if (!pod.faction.hostileTo(p.shooterFaction)) continue;
            float dx = p.currentX() - podX;
            float dy = p.currentY() - podY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq >= bestDistanceSq) continue;
            if (pod.launchSpeed > 0f) {
                // A launcher covers ground, not airspace: it takes a round coming down within its radius
                // and goes out to meet it, as far as LAUNCH_REACH_MULT times that radius, because a round
                // that close to its own landing is already past catching.
                float ldx = p.toX - podX;
                float ldy = p.toY - podY;
                if (ldx * ldx + ldy * ldy > reachSq) continue;
                if (distanceSq > reachSq * LAUNCH_REACH_MULT * LAUNCH_REACH_MULT) continue;
                // Only at a round it can reach before the round lands, and one interceptor per round.
                float reach = pod.coastSeconds + (float) Math.sqrt(distanceSq) / pod.launchSpeed;
                if (p.remainingTime <= reach + LAUNCH_MARGIN_SECONDS) continue;
                if (isChased(p)) continue;
            } else if (distanceSq > reachSq) {
                continue;
            }
            best = p;
            bestDistanceSq = distanceSq;
        }
        return best;
    }

    /**
     * Fires an interceptor missile at where {@code round} will be when the missile gets there,
     * one step like a led gun: the time to the round now, then the round's own flight extrapolated
     * that far. A soft-launched interceptor coasts out of its tube before its motor lights, and that
     * coast is part of its flight: it is aimed further along the round's path and arrives that much
     * later. Whether it connects is rolled now, so the battle replays identically, and applied
     * when it arrives; the magazine and the interval are spent at launch, as a burst's are.
     */
    private void launch(LiveEmplacement pod, Projectile round, BattleControl sim) {
        WeaponDef weapon = pod.structure.mount.weapon;
        float fromX = x(pod, sim);
        float fromY = y(pod, sim);
        float reach = pod.coastSeconds
                + (float) Math.hypot(round.currentX() - fromX, round.currentY() - fromY) / pod.launchSpeed;
        float aimX = round.xAfter(reach);
        float aimY = round.yAfter(reach);
        float flight = Math.max(BattleSimulation.TICK_DT,
                pod.coastSeconds + (float) Math.hypot(aimX - fromX, aimY - fromY) / pod.launchSpeed);
        boolean stopped = rng.nextFloat() < pod.hitChance;
        pod.engagementsRemaining--;
        pod.engagementTimer = pod.engagementInterval;
        sim.telemetry().recordOrdnanceEngaged(pod.carrierId);
        launches.add(new Projectile(fromX, fromY, aimX, aimY, weapon.boostRamp, weapon.arcHeight,
                pod.faction, /*aerialDelivery*/ false, flight, /*onArrival*/ null, weapon.id,
                /*pointDefenseTarget*/ false));
        sim.postShot(new ShotEvent(pod.entityId, fromX, fromY, aimX, aimY, stopped, pod.faction,
                flight, pod.structure));
        interceptors.add(new Interceptor(pod.carrierId, pod.faction, round, stopped, flight, aimX, aimY));
    }

    /** Lands interceptors whose flight is over: a hit takes the round out of the air there, a miss lets it fly on. */
    private void arriveInterceptors(float dt, BattleControl sim) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            Interceptor missile = interceptors.get(i);
            missile.remaining -= dt;
            if (missile.remaining > 0f) continue;
            interceptors.remove(i);
            if (missile.round.intercepted || missile.round.isExpired()) continue;
            if (missile.stopped) {
                missile.round.intercepted = true;
                sim.telemetry().recordOrdnanceIntercepted(missile.carrierId);
            }
            marks.add(new EngagementMark(missile.faction, missile.x, missile.y, missile.stopped,
                    ENGAGEMENT_MARK_SECONDS));
        }
    }

    private boolean isChased(Projectile round) {
        for (Interceptor missile : interceptors) {
            if (missile.round == round) return true;
        }
        return false;
    }

    /**
     * Emits the visible burst for one engagement. Routed through the ordinary
     * shot pipeline so the mount's authored muzzle and impact effects, its
     * audio, and its noise footprint all apply — an emplacement the enemy can
     * hear and see firing is the point, not a hidden field that eats missiles.
     */
    /**
     * Whether {@code round} is one a hull's active protection takes: an arcing (top-attack) round that will come down
     * within {@link #HULL_COVER_CELLS} of the hull at ({@code hullX}, {@code hullY}). A flat rocket, such as an SRM,
     * is left to the plate, and so is a round falling on somebody else.
     */
    static boolean comingDownOn(Projectile round, float hullX, float hullY) {
        if (round.arcHeight <= 0f) return false;
        float dx = round.toX - hullX;
        float dy = round.toY - hullY;
        return dx * dx + dy * dy <= HULL_COVER_CELLS * HULL_COVER_CELLS;
    }

    /** Where the mount stands: its hull's body, its carrier, or the entity it minted. */
    private static float x(LiveEmplacement pod, BattleControl sim) {
        return pod.hull != null ? pod.hull.x : sim.world().x(pod.entityId);
    }

    private static float y(LiveEmplacement pod, BattleControl sim) {
        return pod.hull != null ? pod.hull.y : sim.world().y(pod.entityId);
    }

    private void postEngagement(LiveEmplacement pod, float toX, float toY,
                                boolean stopped, BattleControl sim) {
        float fromX = x(pod, sim);
        float fromY = y(pod, sim);
        // Point the mount at what it just shot, so a bystander can read which
        // way the pod is working rather than seeing a tracer leave a fixed
        // barrel. Snapped rather than slewed: the traverse on a cluster mount
        // is far faster than the tick, and a slew rate here would be a second
        // authority over the mount's authored turn rate. A carried mount has
        // no turret of its own to point; the burst leaves its carrier.
        if (!pod.carried) {
            sim.turretState().setFacingDegrees(pod.entityId,
                    (float) Math.toDegrees(Math.atan2(toY - fromY, toX - fromX)));
            sim.turretState().setRecoilTimer(pod.entityId, 0f);
        }
        sim.postShot(new ShotEvent(pod.entityId, fromX, fromY, toX, toY,
                stopped, pod.faction, ENGAGEMENT_TRACER_SECONDS, pod.structure));
        marks.add(new EngagementMark(pod.faction, toX, toY, stopped,
                ENGAGEMENT_MARK_SECONDS));
    }

    /**
     * Retires a spent emplacement. Routed through the ordinary damage path with
     * no attacker so the death cascade (wreck, corpse cleanup, roster release)
     * runs exactly as it would for one the enemy shot, and nobody is credited
     * with a kill for a pod that simply ran down.
     */
    private void burnOut(LiveEmplacement pod, BattleControl sim) {
        // A spent pack is simply empty; the marine wearing it is not.
        if (pod.carried) return;
        long id = sim.resolveUnit(pod.entityId);
        if (id == 0L) return;
        float remaining = sim.world().hp(id)
                + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
        sim.applyExternalDamage(id, remaining + 1f, Float.MAX_VALUE);
    }

    private void publishSnapshot(BattleControl sim) {
        ArrayList<EmplacementView> views = new ArrayList<>(active.size());
        for (LiveEmplacement pod : active) {
            // A carried mount covers wherever its carrier now stands.
            float x = pod.carried ? x(pod, sim) : pod.spawnX;
            float y = pod.carried ? y(pod, sim) : pod.spawnY;
            views.add(new EmplacementView(pod.id, pod.entityId, pod.carrierId, pod.faction,
                    x, y, pod.interceptRadius, pod.remainingLifetime,
                    pod.totalLifetime, pod.engagementsRemaining));
        }
        snapshot = List.copyOf(views);
    }

    /** Read-only view of one live emplacement. */
    public record EmplacementView(long id, long entityId, long carrierId, Faction faction,
                                  float x, float y, float interceptRadius,
                                  float remainingLifetime, float totalLifetime,
                                  int engagementsRemaining) {
    }

    /** One engagement the mount just made, and whether it connected. */
    public record EngagementView(Faction faction, float x, float y, boolean stopped,
                                 float freshness) {
    }

    private static final class EngagementMark {
        final Faction faction;
        final float x;
        final float y;
        final boolean stopped;
        float remaining;

        EngagementMark(Faction faction, float x, float y, boolean stopped, float remaining) {
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.stopped = stopped;
            this.remaining = remaining;
        }
    }

    private record PendingPlacement(long carrierId, Faction faction, int cellX, int cellY,
                                    DeployableEmplacementSpec spec) {
    }

    private static final class Interceptor {
        final long carrierId;
        final Faction faction;
        final Projectile round;
        final boolean stopped;
        final float x;
        final float y;
        float remaining;

        Interceptor(long carrierId, Faction faction, Projectile round, boolean stopped,
                    float flight, float x, float y) {
            this.carrierId = carrierId;
            this.faction = faction;
            this.round = round;
            this.stopped = stopped;
            this.remaining = flight;
            this.x = x;
            this.y = y;
        }
    }

    private static final class LiveEmplacement {
        final long id;
        final long entityId;
        /** The marine who set it down. Interceptions are credited here, and the record survives the pod. */
        final long carrierId;
        final Faction faction;
        final StructureDef structure;
        /** Worn rather than set down: {@link #entityId} is the carrier. */
        final boolean carried;
        /** A hull's own mount: its position is this body's, and {@link #entityId} is the hull. */
        GroundBody hull;
        final float interceptRadius;
        final float engagementInterval;
        /** The mount weapon's authored accuracy against a moving warhead. */
        final float hitChance;
        /**
         * Speed of the interceptor missile it launches, cells/sec; zero for a gun whose burst
         * resolves on the spot. Read from the mount weapon's authored round velocity.
         */
        final float launchSpeed;
        /** Seconds a soft-launched interceptor coasts out of its tube before its motor lights; zero otherwise. */
        final float coastSeconds;
        final float totalLifetime;
        final float spawnX;
        final float spawnY;
        float remainingLifetime;
        float engagementTimer;
        int engagementsRemaining;

        LiveEmplacement(long id, long entityId, long carrierId, Faction faction,
                        StructureDef structure, DeployableEmplacementSpec spec,
                        float spawnX, float spawnY) {
            this.id = id;
            this.entityId = entityId;
            this.carrierId = carrierId;
            this.faction = faction;
            this.structure = structure;
            this.carried = spec.carried();
            // Reach and rate come from the mount's gun, magazine from the
            // mount itself: one emplacement, one answer per question.
            this.interceptRadius = structure.mount.weapon.range;
            this.engagementInterval = structure.mount.weapon.cooldown;
            this.hitChance = structure.mount.weapon.accuracy;
            this.launchSpeed = structure.mount.weapon.roundVelocity;
            this.coastSeconds = structure.mount.weapon.softLaunch != null
                    ? structure.mount.weapon.softLaunch.seconds() : 0f;
            this.totalLifetime = spec.lifetimeSeconds();
            this.remainingLifetime = spec.lifetimeSeconds();
            this.engagementsRemaining = structure.mount.ammoCapacity;
            this.engagementTimer = 0f;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
        }
    }
}
