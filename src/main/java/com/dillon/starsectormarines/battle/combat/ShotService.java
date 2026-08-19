package com.dillon.starsectormarines.battle.combat;


import com.dillon.starsectormarines.battle.vision.FogOfWarService;

import java.util.ArrayList;
import java.util.List;

/**
 * Owner of every in-flight bullet, tracer, and projectile the battle has
 * spawned: the {@link ShotEvent} list driving renderer tracers and audio,
 * the {@link Projectile} list driving simulated-flight rockets / missiles,
 * the {@link PendingImpact} list driving delayed ballistic-round damage, and
 * the per-frame event drains the renderer pulls each frame.
 *
 * <p>Sibling slice to
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService} and
 * {@link FogOfWarService}.
 * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation} owns one
 * instance and delegates the {@code postShot} / {@code queueProjectile} /
 * {@code queueImpact} / accessor surface here; the SHOTS and PROJECTILES tick
 * phases call {@link #tickShots(float)} /
 * {@link #tickImpacts(float, ImpactSink)} and
 * {@link #tickProjectiles(float, ProjectileArrivalSink)}.
 *
 * <p>The projectile-arrival → detonation and impact-arrival → damage hand-offs
 * both go through callback interfaces ({@link ProjectileArrivalSink},
 * {@link ImpactSink}) rather than a direct reference to the weapons
 * subsystem, so this class doesn't import the (deprecation-bound)
 * {@code WeaponSimContext} interface or take {@code Detonations} /
 * {@code DamageService} as a dependency. {@code BattleSimulation} provides
 * each sink as a lambda that routes back to {@code detonations.detonateNow}
 * / {@code damageService.applyDamage} + {@code HitResponseSystem}.
 *
 * <p>Concurrency: {@link #postShot}, {@link #queueProjectile}, and
 * {@link #queueImpact} are called from the parallel UPDATE_UNITS dispatch.
 * Each synchronizes on its own list monitor ({@link #activeShots} — which
 * also covers the paired {@link #shotsThisFrame} append in {@code postShot} —
 * {@link #activeProjectiles}, {@link #activeImpacts}). {@link #snapshotActiveShots}
 * grabs the same monitor so concurrent readers see a consistent list.
 */
public final class ShotService {

    /** Callback the projectile-arrival path uses to hand a {@link PendingDetonation} to the weapons subsystem. Functional interface so the BattleSimulation site is a lambda. */
    @FunctionalInterface
    public interface ProjectileArrivalSink {
        void detonate(PendingDetonation det);
    }

    /** Callback the impact-arrival path uses to hand an expired {@link PendingImpact} to the weapons subsystem for damage + hit-response application. Functional interface so the BattleSimulation site is a lambda. */
    @FunctionalInterface
    public interface ImpactSink {
        void apply(PendingImpact impact);
    }

    /**
     * A ballistic round's damage/hit-response payload, scheduled to apply
     * when its flight clock ({@link #remainingTime}) reaches zero — the
     * {@link BallisticResolver} sibling to {@link PendingDetonation}. Queued
     * by {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#fireShot}
     * at fire time (parallel-safe via {@link #queueImpact}) and drained by
     * {@link #tickImpacts(float, ImpactSink)} in the serial SHOTS phase. The
     * sink re-guards {@code roster.isAliveById(victimId)} before applying —
     * a victim who died mid-flight (e.g. to a faster round, or a detonation)
     * takes no further damage, mirroring {@code DamageResolver}'s released-
     * target guard pattern.
     */
    public static final class PendingImpact {
        public final long victimId;
        public final long shooterId;
        /** Sim-seconds until the round's flight completes and damage applies. Decremented per tick by {@link #tickImpacts(float, ImpactSink)}. */
        public float remainingTime;
        /** Pre-multiplied by {@code BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT} at queue time when {@link #friendly} — the queue carries the final applied damage, not a raw base value. */
        public final float damage;
        public final float vsTurretMult;
        public final float moraleImpact;
        /** True when the victim shares the shooter's faction. Damage is already reduced accordingly; kept for FX/log — hit-response rolls still apply, since being shot by your own side is still getting shot. */
        public final boolean friendly;

        public PendingImpact(long victimId, long shooterId, float remainingTime,
                             float damage, float vsTurretMult, float moraleImpact,
                             boolean friendly) {
            this.victimId = victimId;
            this.shooterId = shooterId;
            this.remainingTime = remainingTime;
            this.damage = damage;
            this.vsTurretMult = vsTurretMult;
            this.moraleImpact = moraleImpact;
            this.friendly = friendly;
        }
    }

    private final List<ShotEvent> activeShots = new ArrayList<>();
    /** Shots fired during the last advance. Cleared at the top of each advance, populated per tick. Drives one-shot audio in the renderer. */
    private final List<ShotEvent> shotsThisFrame = new ArrayList<>();
    /** Shots whose lifetime ran out during the last advance — the "arrival" event for projectile-style shots. The renderer reads this to spawn impact FX at the endpoint when the projectile sprite actually reaches its target, rather than at launch time. */
    private final List<ShotEvent> shotsExpiredThisFrame = new ArrayList<>();
    /** In-flight {@link Projectile}s — slow-velocity AoE kinds. Advanced + detonated by {@link #tickProjectiles(float, ProjectileArrivalSink)} each tick. */
    private final List<Projectile> activeProjectiles = new ArrayList<>();
    /** In-flight {@link PendingImpact}s — one per resolved ballistic round with a victim, queued at fire time by {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#fireShot}. Advanced + applied by {@link #tickImpacts(float, ImpactSink)} each tick. */
    private final List<PendingImpact> activeImpacts = new ArrayList<>();
    /** Projectiles that arrived this tick — parallel to {@link #shotsExpiredThisFrame} for the impact-FX dispatch in the renderer. Cleared each tick. */
    private final List<Projectile> projectilesArrivedThisFrame = new ArrayList<>();

    // ---- Append entry points (parallel-safe) ----

    /**
     * Adds a {@link ShotEvent} to the active + this-frame lists. Active drives
     * the renderer's projectile lerp + impact-on-expire path; this-frame drives
     * one-shot audio so the fire SFX plays exactly once per round even though
     * the event lives for its full flight time.
     */
    public void postShot(ShotEvent shot) {
        // activeShots + shotsThisFrame are always written together; one
        // monitor (activeShots) covers both for the parallel UPDATE_UNITS
        // dispatch path.
        synchronized (activeShots) {
            activeShots.add(shot);
            shotsThisFrame.add(shot);
        }
    }

    public void queueProjectile(Projectile p) {
        synchronized (activeProjectiles) {
            activeProjectiles.add(p);
        }
    }

    /**
     * Queues a resolved round's damage/hit-response payload for delayed
     * application at {@link PendingImpact#remainingTime}. Called from the
     * parallel UPDATE_UNITS dispatch — same monitor-per-list discipline as
     * {@link #postShot} / {@link #queueProjectile}.
     */
    public void queueImpact(PendingImpact p) {
        synchronized (activeImpacts) {
            activeImpacts.add(p);
        }
    }

    // ---- Read accessors ----

    public List<ShotEvent> getActiveShots() { return activeShots; }
    public List<ShotEvent> getShotsThisFrame() { return shotsThisFrame; }
    public List<ShotEvent> getShotsExpiredThisFrame() { return shotsExpiredThisFrame; }
    public List<Projectile> getActiveProjectiles() { return activeProjectiles; }
    public List<Projectile> getProjectilesArrivedThisFrame() { return projectilesArrivedThisFrame; }

    /**
     * Thread-safe snapshot of {@link #activeShots} for callers iterating during
     * the parallel UPDATE_UNITS dispatch. A concurrent {@link #postShot} append
     * would otherwise {@code CME} a plain iterator. Allocates one ArrayList per
     * call (small — typically &lt; 50 shots in-flight); pool later if a profile
     * shows it matters.
     */
    public List<ShotEvent> snapshotActiveShots() {
        synchronized (activeShots) {
            return new ArrayList<>(activeShots);
        }
    }

    /**
     * Thread-safe snapshot of {@link #activeProjectiles} — same justification
     * as {@link #snapshotActiveShots}. Used by squad-coordination scorers that
     * run during the parallel UPDATE_UNITS dispatch (today:
     * {@code TacticalScoring.projectedRocketDamageOnTurret} while another
     * worker may concurrently {@link #queueProjectile} a freshly-fired marine
     * rocket).
     */
    public List<Projectile> snapshotActiveProjectiles() {
        synchronized (activeProjectiles) {
            return new ArrayList<>(activeProjectiles);
        }
    }

    // ---- Per-frame drains ----

    /**
     * Clears the per-frame event lists. Called from
     * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#advance(float)}
     * at the top of each advance.
     */
    public void beginFrame() {
        shotsThisFrame.clear();
        shotsExpiredThisFrame.clear();
        projectilesArrivedThisFrame.clear();
    }

    // ---- Tick passes ----

    /** Ages every active shot by one tick and drops expired ones into {@link #shotsExpiredThisFrame}. Reverse iteration for in-place removal. */
    public void tickShots(float dt) {
        for (int i = activeShots.size() - 1; i >= 0; i--) {
            ShotEvent s = activeShots.get(i);
            s.lifetime -= dt;
            if (s.lifetime <= 0f) {
                shotsExpiredThisFrame.add(s);
                activeShots.remove(i);
            }
        }
    }

    /**
     * Advances every in-flight {@link Projectile} by {@code dt}. Intercepted
     * projectiles (point-defense future hook) are removed without detonating;
     * expired ones with a non-null {@link Projectile#onArrival} payload fire
     * it via the supplied {@code sink} and land in
     * {@link #projectilesArrivedThisFrame} for renderer impact FX. Payloadless
     * direct-fire overshoots simply expire. Reverse iteration for in-place removal.
     */
    public void tickProjectiles(float dt, ProjectileArrivalSink sink) {
        for (int i = activeProjectiles.size() - 1; i >= 0; i--) {
            Projectile p = activeProjectiles.get(i);
            if (p.intercepted) {
                // Future: spawn intercept FX here. For now, just remove.
                activeProjectiles.remove(i);
                continue;
            }
            p.remainingTime -= dt;
            if (p.remainingTime <= 0f) {
                if (p.onArrival != null) {
                    sink.detonate(p.onArrival);
                    projectilesArrivedThisFrame.add(p);
                }
                activeProjectiles.remove(i);
            }
        }
    }

    /**
     * Advances every in-flight {@link PendingImpact} by {@code dt}; expired
     * impacts fire through {@code sink} (damage + hit-response application —
     * see {@link ImpactSink}) and are removed. Called from the serial SHOTS
     * phase, right alongside {@link #tickShots(float)} — outside the
     * parallel UPDATE_UNITS dispatch and the FIRING deferral window, so the
     * sink's {@code DamageService.applyDamage} call resolves inline rather
     * than re-queuing. Reverse iteration for in-place removal.
     */
    public void tickImpacts(float dt, ImpactSink sink) {
        for (int i = activeImpacts.size() - 1; i >= 0; i--) {
            PendingImpact impact = activeImpacts.get(i);
            impact.remainingTime -= dt;
            if (impact.remainingTime <= 0f) {
                sink.apply(impact);
                activeImpacts.remove(i);
            }
        }
    }
}
