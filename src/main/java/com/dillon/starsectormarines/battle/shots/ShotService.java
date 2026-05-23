package com.dillon.starsectormarines.battle.shots;

import com.dillon.starsectormarines.battle.PendingDetonation;
import com.dillon.starsectormarines.battle.Projectile;
import com.dillon.starsectormarines.battle.ShotEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Owner of every in-flight bullet, tracer, and projectile the battle has
 * spawned: the {@link ShotEvent} list driving renderer tracers and audio,
 * the {@link Projectile} list driving simulated-flight rockets / missiles,
 * and the per-frame event drains the renderer pulls each frame.
 *
 * <p>Sibling slice to
 * {@link com.dillon.starsectormarines.battle.fx.EffectsService} and
 * {@link com.dillon.starsectormarines.battle.vision.VisionService}.
 * {@link com.dillon.starsectormarines.battle.BattleSimulation} owns one
 * instance and delegates the {@code postShot} / {@code queueProjectile} /
 * accessor surface here; the SHOTS and PROJECTILES tick phases call
 * {@link #tickShots(float)} and
 * {@link #tickProjectiles(float, ProjectileArrivalSink)}.
 *
 * <p>The projectile-arrival → detonation hand-off goes through the
 * {@link ProjectileArrivalSink} callback rather than a direct reference to
 * the weapons subsystem, so this class doesn't import the (deprecation-bound)
 * {@code WeaponSimContext} interface or take {@code Detonations} as a
 * dependency. {@code BattleSimulation} provides the sink as a lambda that
 * routes back to {@code detonations.detonateNow}.
 *
 * <p>Concurrency: {@link #postShot} and {@link #queueProjectile} are called
 * from the parallel UPDATE_UNITS dispatch. Both synchronize on
 * {@link #activeShots} (the same monitor covers the paired
 * {@link #shotsThisFrame} append in {@code postShot}) and
 * {@link #activeProjectiles} respectively. {@link #snapshotActiveShots} grabs
 * the same monitor so concurrent readers see a consistent list.
 */
public final class ShotService {

    /** Callback the projectile-arrival path uses to hand a {@link PendingDetonation} to the weapons subsystem. Functional interface so the BattleSimulation site is a lambda. */
    @FunctionalInterface
    public interface ProjectileArrivalSink {
        void detonate(PendingDetonation det);
    }

    private final List<ShotEvent> activeShots = new ArrayList<>();
    /** Shots fired during the last advance. Cleared at the top of each advance, populated per tick. Drives one-shot audio in the renderer. */
    private final List<ShotEvent> shotsThisFrame = new ArrayList<>();
    /** Shots whose lifetime ran out during the last advance — the "arrival" event for projectile-style shots. The renderer reads this to spawn impact FX at the endpoint when the projectile sprite actually reaches its target, rather than at launch time. */
    private final List<ShotEvent> shotsExpiredThisFrame = new ArrayList<>();
    /** In-flight {@link Projectile}s — slow-velocity AoE kinds. Advanced + detonated by {@link #tickProjectiles(float, ProjectileArrivalSink)} each tick. */
    private final List<Projectile> activeProjectiles = new ArrayList<>();
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
     * {@link com.dillon.starsectormarines.battle.BattleSimulation#advance(float)}
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
     * expired ones fire their {@link Projectile#onArrival} payload via the
     * supplied {@code sink} and land in {@link #projectilesArrivedThisFrame}
     * for the renderer's impact-FX dispatch. Reverse iteration for in-place
     * removal.
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
                sink.detonate(p.onArrival);
                projectilesArrivedThisFrame.add(p);
                activeProjectiles.remove(i);
            }
        }
    }
}
