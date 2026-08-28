package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    private final Random rng;
    private final ArrayList<PendingPlacement> pending = new ArrayList<>();
    private final ArrayList<LiveEmplacement> active = new ArrayList<>();
    private final ArrayList<EngagementMark> marks = new ArrayList<>();
    private volatile List<EmplacementView> snapshot = List.of();
    private volatile List<EngagementView> markSnapshot = List.of();
    private long nextId = 1L;

    /** Takes the battle's seeded RNG so an engagement roll replays identically. */
    public PointDefenseService(Random rng) {
        this.rng = rng;
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
            if (engaged == null) continue;
            // An engagement is a burst fired at a moving warhead, not a
            // guaranteed deletion. It costs a round from the magazine and puts
            // the mount on its interval whether or not it connects, so "it
            // fired and lost" is a real, reachable, visible state.
            boolean stopped = rng.nextFloat() < pod.hitChance;
            if (stopped) engaged.intercepted = true;
            pod.engagementsRemaining--;
            pod.engagementTimer = pod.engagementInterval;
            sim.telemetry().recordOrdnanceEngaged(pod.carrierId);
            if (stopped) sim.telemetry().recordOrdnanceIntercepted(pod.carrierId);
            postEngagement(pod, engaged, stopped, sim);
        }
        ageMarks(dt);
        publishSnapshot();
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
     * Nearest hostile, not-yet-engaged, opted-in round physically inside the
     * radius. Nearest-first so a saturating salvo loses the round closest to
     * landing rather than an arbitrary one.
     */
    private Projectile pickEngageable(LiveEmplacement pod, List<Projectile> inFlight,
                                      BattleControl sim) {
        float podX = sim.world().x(pod.entityId);
        float podY = sim.world().y(pod.entityId);
        float reachSq = pod.interceptRadius * pod.interceptRadius;
        Projectile best = null;
        float bestDistanceSq = Float.MAX_VALUE;
        for (int i = 0, n = inFlight.size(); i < n; i++) {
            Projectile p = inFlight.get(i);
            if (!p.pointDefenseTarget || p.intercepted) continue;
            if (p.shooterFaction == pod.faction) continue;
            float dx = p.currentX() - podX;
            float dy = p.currentY() - podY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq > reachSq || distanceSq >= bestDistanceSq) continue;
            best = p;
            bestDistanceSq = distanceSq;
        }
        return best;
    }

    /**
     * Emits the visible burst for one engagement. Routed through the ordinary
     * shot pipeline so the mount's authored muzzle and impact effects, its
     * audio, and its noise footprint all apply — an emplacement the enemy can
     * hear and see firing is the point, not a hidden field that eats missiles.
     */
    private void postEngagement(LiveEmplacement pod, Projectile engaged,
                                boolean stopped, BattleControl sim) {
        float fromX = sim.world().x(pod.entityId);
        float fromY = sim.world().y(pod.entityId);
        float toX = engaged.currentX();
        float toY = engaged.currentY();
        // Point the mount at what it just shot, so a bystander can read which
        // way the pod is working rather than seeing a tracer leave a fixed
        // barrel. Snapped rather than slewed: the traverse on a cluster mount
        // is far faster than the tick, and a slew rate here would be a second
        // authority over the mount's authored turn rate.
        sim.turretState().setFacingDegrees(pod.entityId,
                (float) Math.toDegrees(Math.atan2(toY - fromY, toX - fromX)));
        sim.turretState().setRecoilTimer(pod.entityId, 0f);
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
        long id = sim.resolveUnit(pod.entityId);
        if (id == 0L) return;
        float remaining = sim.world().hp(id)
                + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
        sim.applyExternalDamage(id, remaining + 1f, Float.MAX_VALUE);
    }

    private void publishSnapshot() {
        ArrayList<EmplacementView> views = new ArrayList<>(active.size());
        for (LiveEmplacement pod : active) {
            views.add(new EmplacementView(pod.id, pod.entityId, pod.carrierId, pod.faction,
                    pod.spawnX, pod.spawnY, pod.interceptRadius, pod.remainingLifetime,
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

    private static final class LiveEmplacement {
        final long id;
        final long entityId;
        /** The marine who set it down. Interceptions are credited here, and the record survives the pod. */
        final long carrierId;
        final Faction faction;
        final StructureDef structure;
        final float interceptRadius;
        final float engagementInterval;
        /** The mount weapon's authored accuracy against a moving warhead. */
        final float hitChance;
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
            // Reach and rate come from the mount's gun, magazine from the
            // mount itself: one emplacement, one answer per question.
            this.interceptRadius = structure.mount.weapon.range;
            this.engagementInterval = structure.mount.weapon.cooldown;
            this.hitChance = structure.mount.weapon.accuracy;
            this.totalLifetime = spec.lifetimeSeconds();
            this.remainingLifetime = spec.lifetimeSeconds();
            this.engagementsRemaining = structure.mount.ammoCapacity;
            this.engagementTimer = 0f;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
        }
    }
}
