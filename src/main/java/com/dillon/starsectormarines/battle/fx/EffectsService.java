package com.dillon.starsectormarines.battle.fx;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.Decal;
import com.dillon.starsectormarines.battle.SmokePlume;
import com.dillon.starsectormarines.battle.SmokingWreck;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Owner of every transient visual side-effect the battle accumulates:
 * persistent ground decals (bullet holes, craters, rubble, shell casings),
 * smoking wrecks parked on destroyed turrets / hubs / drone-crash sites,
 * lingering HE smoke plumes, and the per-frame puff / fire-burst / wall-dust
 * event queues the renderer drains each frame.
 *
 * <p>First slice of the services refactor: this class owns the state and the
 * tick logic; {@link com.dillon.starsectormarines.battle.BattleSimulation}
 * keeps a single instance and delegates the matching accessors. The
 * {@link com.dillon.starsectormarines.battle.weapons.WeaponSimContext}
 * spawn methods on the sim forward here too — once that context interface is
 * deprecated, weapon subsystems will hold an {@link EffectsService} directly.
 */
public final class EffectsService {

    // ---- Wreck timing constants ----

    /** Total seconds a wreck stays alive after destruction. Burn phase up front, then a longer smoke-only tail so the player can still read "dead turret" minutes later. */
    public static final float WRECK_LIFETIME = 30f;
    /** Seconds at the start of the wreck's life during which it emits fire bursts in addition to smoke. After this, fire stops and only smoke continues for the remainder. Public so the screen-side lightmap pump can mirror this window for persistent wreck-fire lights. */
    public static final float WRECK_BURN_DURATION = 12f;
    /** Tail of the burn phase over which fire-burst emit probability tapers from 1 to 0. RNG-gated so the taper actually drops emissions. Public for the lightmap pump's intensity ramp. */
    public static final float WRECK_FIRE_FADE_DURATION = 2f;
    /** Min/max sim-seconds between smoke puffs on a single wreck. Jittered per emission so wrecks don't sync up. */
    private static final float WRECK_PUFF_MIN_GAP = 0.45f;
    private static final float WRECK_PUFF_MAX_GAP = 0.85f;
    /** Min/max sim-seconds between fire bursts on a single wreck. Tighter than smoke — fire is the more active, frequent emission during the burn phase. */
    private static final float WRECK_FIRE_MIN_GAP = 0.25f;
    private static final float WRECK_FIRE_MAX_GAP = 0.50f;

    // ---- Plume timing constants ----

    /** Total sim-seconds an HE impact plume keeps emitting. Long enough to read as a lingering column rising off the impact site, short enough that overlapping rocket salvos don't pile into permanent smoke. */
    private static final float PLUME_LIFETIME = 5.0f;
    /** Min/max sim-seconds between puff emissions on a single plume. Tighter than wreck cadence — impact smoke is denser per-second during its brief life. */
    private static final float PLUME_PUFF_MIN_GAP = 0.18f;
    private static final float PLUME_PUFF_MAX_GAP = 0.32f;

    private final Random rng;

    /** Persistent visual decals (bullet holes, craters, rubble) accumulated over the battle. Bounded by {@link DevConfig#DECAL_SOURCE_CAP} with FIFO eviction — O(1) head removal via {@link ArrayDeque#pollFirst}. */
    private final ArrayDeque<Decal> decals = new ArrayDeque<>();
    /**
     * Monotonic count of decals ever added to {@link #decals}. Lets the render
     * layer's accumulator know how many new decals were spawned since it last
     * stamped, even when {@link #decals} has saturated at the cap and FIFO
     * eviction keeps {@code decals.size()} pinned — without this counter the
     * accumulator can't distinguish "no new decals" from "new decals arrived
     * but the head was evicted to make room."
     */
    private long decalsEverAdded = 0L;

    private final List<SmokingWreck> smokingWrecks = new ArrayList<>();
    private final List<SmokePlume> smokePlumes = new ArrayList<>();

    private final List<float[]> smokePuffsThisFrame = new ArrayList<>();
    private final List<float[]> fireBurstsThisFrame = new ArrayList<>();
    private final List<float[]> wallDustsThisFrame = new ArrayList<>();

    public EffectsService(Random rng) {
        this.rng = rng;
    }

    // ---- Decals ----

    public void addDecal(Decal d) {
        decals.addLast(d);
        if (decals.size() > DevConfig.DECAL_SOURCE_CAP) decals.pollFirst();
        decalsEverAdded++;
    }

    public Collection<Decal> getDecals() { return decals; }

    public long getDecalsEverAdded() { return decalsEverAdded; }

    // ---- Wrecks ----

    public void spawnSmokingWreck(int x, int y) {
        smokingWrecks.add(new SmokingWreck(x, y, WRECK_LIFETIME,
                0.05f + rng.nextFloat() * 0.10f));
    }

    /** Live smoking wrecks. Read-only — callers iterate; mutation happens through {@link #spawnSmokingWreck} and {@link #tickWrecks(float)}. */
    public List<SmokingWreck> getSmokingWrecks() {
        return Collections.unmodifiableList(smokingWrecks);
    }

    // ---- Plumes ----

    public void spawnSmokePlume(float x, float y) {
        smokePlumes.add(new SmokePlume(x, y, PLUME_LIFETIME));
    }

    // ---- Dust ----

    /** Queues a wall-collapse dust-burst event at world cell-center {@code (cellX, cellY)}. Drained by the renderer (today: {@code FlybyOverlay}) each frame. */
    public void spawnDustBurst(float cellX, float cellY) {
        wallDustsThisFrame.add(new float[]{cellX, cellY});
    }

    // ---- Per-frame event drains ----

    public List<float[]> getSmokePuffsThisFrame() { return smokePuffsThisFrame; }
    public List<float[]> getFireBurstsThisFrame() { return fireBurstsThisFrame; }
    public List<float[]> getWallDustsThisFrame()  { return wallDustsThisFrame; }

    /**
     * Clears the per-frame event lists. Called from
     * {@link com.dillon.starsectormarines.battle.BattleSimulation#advance(float)}
     * at the top of each advance so a paused caller doesn't keep replaying the
     * previous frame's events.
     */
    public void beginFrame() {
        smokePuffsThisFrame.clear();
        fireBurstsThisFrame.clear();
        wallDustsThisFrame.clear();
    }

    // ---- Tick passes ----

    /**
     * Ages each smoking wreck and emits smoke/fire events on independent
     * jittered timers. Two-phase lifecycle:
     * <ul>
     *   <li>Burn phase (first {@link #WRECK_BURN_DURATION}s): fire bursts on
     *       tight cadence in addition to smoke. Emit probability tapers to 0
     *       over the trailing {@link #WRECK_FIRE_FADE_DURATION}s so the fire
     *       crossfades out cleanly rather than cutting.</li>
     *   <li>Smoke phase (remainder): smoke continues at its full cadence.</li>
     * </ul>
     * Separate timers per emitter so plumes interleave naturally instead of
     * spawning as paired emissions on the same frame.
     */
    public void tickWrecks(float dt) {
        for (int i = smokingWrecks.size() - 1; i >= 0; i--) {
            SmokingWreck w = smokingWrecks.get(i);
            w.remainingLifetime -= dt;
            if (w.remainingLifetime <= 0f) {
                smokingWrecks.remove(i);
                continue;
            }
            float age = w.totalLifetime - w.remainingLifetime;

            w.nextPuffTimer -= dt;
            if (w.nextPuffTimer <= 0f) {
                float cooledFrac = Math.max(0.15f, w.remainingLifetime / w.totalLifetime);
                float radius = 0.40f + cooledFrac * 0.45f;
                smokePuffsThisFrame.add(new float[]{w.cellX + 0.5f, w.cellY + 0.5f, radius});
                w.nextPuffTimer = WRECK_PUFF_MIN_GAP
                        + rng.nextFloat() * (WRECK_PUFF_MAX_GAP - WRECK_PUFF_MIN_GAP);
            }

            if (age < WRECK_BURN_DURATION) {
                w.nextFireTimer -= dt;
                if (w.nextFireTimer <= 0f) {
                    float burnRemaining = WRECK_BURN_DURATION - age;
                    float intensity = (burnRemaining < WRECK_FIRE_FADE_DURATION)
                            ? burnRemaining / WRECK_FIRE_FADE_DURATION
                            : 1f;
                    if (rng.nextFloat() < intensity) {
                        float fireRadius = 0.40f + rng.nextFloat() * 0.30f;
                        fireBurstsThisFrame.add(new float[]{w.cellX + 0.5f, w.cellY + 0.5f, fireRadius});
                    }
                    w.nextFireTimer = WRECK_FIRE_MIN_GAP
                            + rng.nextFloat() * (WRECK_FIRE_MAX_GAP - WRECK_FIRE_MIN_GAP);
                }
            }
        }
    }

    /**
     * Ages each smoke plume and emits puff events on a jittered timer. Per-puff
     * radius scales with the remaining-lifetime fraction so the plume billows
     * hard at impact and thins as it rises. Reuses the shared
     * {@link #smokePuffsThisFrame} drain that wrecks emit into — the renderer
     * already pulls from that list each frame.
     */
    public void tickPlumes(float dt) {
        for (int i = smokePlumes.size() - 1; i >= 0; i--) {
            SmokePlume p = smokePlumes.get(i);
            p.remainingLifetime -= dt;
            if (p.remainingLifetime <= 0f) {
                smokePlumes.remove(i);
                continue;
            }
            p.nextPuffTimer -= dt;
            if (p.nextPuffTimer <= 0f) {
                float lifeFrac = p.remainingLifetime / p.totalLifetime;
                // Bigger puffs early (impact bloom), tightening as the column
                // rises. Floor keeps the tail-end column readable rather than
                // shrinking to invisible.
                float radius = 0.45f + Math.max(0.20f, lifeFrac) * 0.55f;
                smokePuffsThisFrame.add(new float[]{p.x, p.y, radius});
                p.nextPuffTimer = PLUME_PUFF_MIN_GAP
                        + rng.nextFloat() * (PLUME_PUFF_MAX_GAP - PLUME_PUFF_MIN_GAP);
            }
        }
    }
}
