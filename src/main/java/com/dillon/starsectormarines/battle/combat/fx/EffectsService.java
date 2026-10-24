package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.world.model.PropDamage;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Owner of every transient visual side-effect the battle accumulates:
 * persistent ground decals (bullet holes, craters, rubble, shell casings),
 * smoking wrecks parked on destroyed turrets / hubs / drone-crash sites,
 * lingering HE smoke plumes, and the per-frame puff / fire-burst / wall-dust
 * event queues the renderer drains each frame.
 *
 * <p>First slice of the services refactor: this class owns the state and the
 * tick logic; {@link com.dillon.starsectormarines.battle.sim.BattleSimulation}
 * keeps a single instance and delegates the matching accessors.
 */
public final class EffectsService {

    // ---- Wreck timing constants ----

    /** Total seconds a wreck stays alive after destruction. Burn phase up front, then a longer smoke-only tail so the player can still read "dead turret" minutes later. */
    public static final float WRECK_LIFETIME = 30f;
    /** Seconds at the start of the wreck's life during which it emits fire bursts in addition to smoke. After this, fire stops and only smoke continues for the remainder. */
    public static final float WRECK_BURN_DURATION = 12f;
    /** Tail of the burn phase over which fire-burst emit probability tapers from 1 to 0. RNG-gated so the taper actually drops emissions. */
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
    /** Opening burn on a cannon impact plume before it settles into smoke only. */
    private static final float CANNON_PLUME_FIRE_DURATION = 1.25f;

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
    private final List<RollingBlast> rollingBlastsThisFrame = new ArrayList<>();
    /** Identity membership keeps the ordinary plume data object and smoke-only API unchanged. */
    private final Set<SmokePlume> cannonPlumes = Collections.newSetFromMap(new IdentityHashMap<>());

    private final List<float[]> smokePuffsThisFrame = new ArrayList<>();
    /** Ground dust kicked up by moving hulls this tick, as {@code {x, y, vx, vy, radius, kind}}; see {@link #emitGroundDust}. */
    private final List<float[]> groundDustThisFrame = new ArrayList<>();
    private final List<float[]> fireBurstsThisFrame = new ArrayList<>();
    private final List<float[]> wallDustsThisFrame = new ArrayList<>();
    private final List<float[]> heavyImpactsThisFrame = new ArrayList<>();
    private final List<float[]> clawStrikesThisFrame = new ArrayList<>();
    /**
     * Rounds released onto the ground this frame. Unlike the {@code float[]}
     * queues above, a delivery carries a {@link OrdnanceDelivery} as well as a
     * position, because a shell, a beam and a bomb are three different events
     * to watch rather than one event at three sizes.
     */
    private final List<OrdnanceRelease> ordnanceReleasesThisFrame = new ArrayList<>();

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
        if (wreckSink != null) wreckSink.wreck(x + .5f, y + .5f, 1f);
        smokingWrecks.add(new SmokingWreck(x, y, WRECK_LIFETIME,
                0.05f + rng.nextFloat() * 0.10f));
    }

    /** Told of every burning wreck as it starts: where, and its rough radius in cells. */
    @FunctionalInterface
    public interface WreckSink { void wreck(float x, float y, float radiusCells); }

    private WreckSink wreckSink;

    /** Sets who is told when a wreck starts burning (the battle's fire); presentation is unchanged. */
    public void setWreckSink(WreckSink sink) { this.wreckSink = sink; }

    /** A hull-sized, purely visual cook-off followed by three minutes of cooling smoke. */
    public void spawnVehicleWreck(float x, float y, float length, float width) {
        float size = (float) Math.sqrt(length * width);
        if (wreckSink != null) wreckSink.wreck(x, y, Math.max(1f, size * .5f));
        float scale = Math.max(1f, Math.min(Math.max(2.5f, size * .1f), size * .65f));
        spawnHeavyImpact(x, y, scale);
        smokingWrecks.add(new SmokingWreck((int) Math.floor(x), (int) Math.floor(y),
                180f, .05f, scale, 24f));
    }

    /** One bounded tank cook-off, a brief roof fire and a longer rear-engine burn. No damage. */
    public void spawnTankWreck(float x, float y, float facingDeg) {
        double angle = Math.toRadians(facingDeg);
        float engineX = x + (float) Math.sin(angle) * 1.1f;
        float engineY = y - (float) Math.cos(angle) * 1.1f;
        spawnHeavyImpact(x, y, 1.6f);
        fireBurstsThisFrame.add(new float[]{engineX, engineY, .65f});
        smokingWrecks.add(new SmokingWreck(x, y, 35f, .05f, .8f, 6f));
        smokingWrecks.add(new SmokingWreck(engineX, engineY, 90f, .12f, 1.15f, 18f));
    }

    /** Lighter carrier damage: brief roof flash and a contained rear compartment burn. */
    public void spawnApcWreck(float x, float y, float facingDeg, float hullFrameCells) {
        float scale = hullFrameCells / 2.4f;
        double angle = Math.toRadians(facingDeg);
        // Registered APC roof bearing is forward; the breached troop roof is aft.
        float roofX = x - (float) Math.sin(angle) * .15208333f * scale;
        float roofY = y + (float) Math.cos(angle) * .15208333f * scale;
        float rearX = x + (float) Math.sin(angle) * .55f * scale;
        float rearY = y - (float) Math.cos(angle) * .55f * scale;
        spawnHeavyImpact(x, y, .72f * scale);
        fireBurstsThisFrame.add(new float[]{rearX, rearY, .3f * scale});
        smokingWrecks.add(new SmokingWreck(roofX, roofY, 20f, .05f, .4f * scale, 3f));
        smokingWrecks.add(new SmokingWreck(rearX, rearY, 55f, .12f, .6f * scale, 10f));
    }

    /** Thin-skinned pickup: a small blast, front-engine fire and brief pintle/ammo flash. */
    public void spawnTechnicalWreck(float x, float y, float facingDeg) {
        double angle = Math.toRadians(facingDeg);
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        spawnHeavyImpact(x, y, .58f);
        float engineX = x - sin * .95f, engineY = y + cos * .95f;
        fireBurstsThisFrame.add(new float[]{engineX, engineY, .24f});
        smokingWrecks.add(new SmokingWreck(engineX, engineY, 35f, .05f, .48f, 7f));
        smokingWrecks.add(new SmokingWreck(x + sin * .9875f, y - cos * .9875f,
                12f, .12f, .22f, 2f));
    }

    /**
     * A hover hull: a small, sharp blast on its composite shell and a short burn in the lift fans aft, which is where
     * the power is. Light plate holds little to burn, so it cools quickly. No damage.
     */
    public void spawnHoverWreck(float x, float y, float facingDeg, float hullFrameCells) {
        double angle = Math.toRadians(facingDeg);
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        float aft = hoverFanAftCells(hullFrameCells);
        float fanX = x + sin * aft, fanY = y - cos * aft;
        spawnHeavyImpact(x, y, .5f + .1f * hullFrameCells);
        fireBurstsThisFrame.add(new float[]{fanX, fanY, .26f});
        smokingWrecks.add(new SmokingWreck(x, y, 14f, .05f, .3f, 2f));
        smokingWrecks.add(new SmokingWreck(fanX, fanY, 40f, .12f, .5f, 7f));
    }

    /** How far aft of a hover hull's centre its lift fans burn, in cells: they sit in the rear quarters. */
    public static float hoverFanAftCells(float hullFrameCells) {
        return .35f * hullFrameCells;
    }

    /** Freight loss: modest blast, cab fire and a longer contained cargo burn; no chain explosions. */
    public void spawnHaulerWreck(float x, float y, float facingDeg) {
        double angle = Math.toRadians(facingDeg);
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        spawnHeavyImpact(x, y, 1.1f);
        float cargoX = x + sin * .75f, cargoY = y - cos * .75f;
        fireBurstsThisFrame.add(new float[]{cargoX, cargoY, .42f});
        smokingWrecks.add(new SmokingWreck(x - sin * 1.55f, y + cos * 1.55f,
                45f, .05f, .55f, 9f));
        smokingWrecks.add(new SmokingWreck(cargoX, cargoY, 90f, .12f, .9f, 18f));
    }

    /** Emit a filled, irregular fan from the hull's current ground contact, including its centre. */
    public void spawnCrashBlastFan(float x, float y, float vx, float vy, float hullRadius) {
        float speed = (float) Math.hypot(vx, vy);
        if (speed < .5f) return;
        float forwardX = vx / speed, forwardY = vy / speed;
        // An airframe's fan tops out at 2.5; a hull wreck tens of cells long earns a fan to match.
        float scale = Math.max(1.1f, Math.min(Math.max(2.5f, hullRadius * .2f), hullRadius * .65f));
        // A hot core follows the actual sliding hull; the other lobes fill the cone, not its outline.
        rollingBlastsThisFrame.add(new RollingBlast(x, y, scale * 1.25f, vx * .8f, vy * .8f));
        for (int lobe = 0; lobe < 4; lobe++) {
            float angle = (lobe + rng.nextFloat()) / 4f * 1.9f - .95f;
            float forward = (rng.nextFloat() - .5f) * hullRadius;
            float sideways = (rng.nextFloat() - .5f) * hullRadius * 1.4f;
            float travel = Math.max(4f, speed * (.6f + rng.nextFloat() * .35f));
            float along = (float) Math.cos(angle) * travel;
            float across = (float) Math.sin(angle) * travel;
            rollingBlastsThisFrame.add(new RollingBlast(
                    x + forwardX * forward - forwardY * sideways,
                    y + forwardY * forward + forwardX * sideways,
                    scale * (.65f + rng.nextFloat() * .55f),
                    forwardX * along - forwardY * across,
                    forwardY * along + forwardX * across));
        }
    }

    public List<RollingBlast> getRollingBlastsThisFrame() { return rollingBlastsThisFrame; }
    /** Live smoking wrecks. Read-only — callers iterate; mutation happens through {@link #spawnSmokingWreck} and {@link #tickWrecks(float)}. */
    public List<SmokingWreck> getSmokingWrecks() {
        return Collections.unmodifiableList(smokingWrecks);
    }

    // ---- Plumes ----

    public void spawnSmokePlume(float x, float y) {
        smokePlumes.add(new SmokePlume(x, y, PLUME_LIFETIME));
    }

    /** Compact, finite equipment cues sized from the prop's smaller footprint dimension. */
    public void spawnPropDamage(float x, float y, PropDamage.Effect effect, float footprintSize) {
        if (effect == PropDamage.Effect.NONE) return;
        if (effect == PropDamage.Effect.DUST) {
            wallDustsThisFrame.add(new float[]{x, y, .25f * footprintSize});
            return;
        }
        SmokePlume plume = new SmokePlume(x, y, 3f, .18f * footprintSize);
        smokePlumes.add(plume);
        if (effect == PropDamage.Effect.FIRE) {
            fireBurstsThisFrame.add(new float[]{x, y, .22f * footprintSize});
            cannonPlumes.add(plume);
        }
    }

    /**
     * Spawns a heavy gun impact plume whose opening smoke puffs carry a brief
     * flame burst before the ordinary smoke-only tail. Both layers use the
     * existing per-frame drains, so every presentation host gets the same
     * effect without a cannon-specific render path.
     */
    public void spawnBurningSmokePlume(float x, float y) {
        SmokePlume plume = new SmokePlume(x, y, PLUME_LIFETIME);
        smokePlumes.add(plume);
        cannonPlumes.add(plume);
    }

    // ---- Single emissions ----

    /**
     * Queues one smoke puff this frame, for an emitter that keeps its own cadence (a crippled
     * mech's legs). Draws nothing from the simulation's random stream.
     */
    public void emitSmokePuff(float x, float y, float radius) {
        smokePuffsThisFrame.add(new float[]{x, y, radius});
    }

    /** Ground dust off a wheel or track on loose ground. */
    public static final int DUST_WHEEL = 0;
    /** The haze a hover skirt blows out over dry ground. */
    public static final int DUST_SKIRT = 1;
    /** The spray a hover skirt blows off water. */
    public static final int DUST_SPRAY = 2;

    /**
     * Queues one puff of ground dust this frame, drifting at ({@code vx},{@code vy}) cells per second. Kind is one of
     * {@link #DUST_WHEEL}, {@link #DUST_SKIRT} and {@link #DUST_SPRAY}. Presentation only: draws nothing from the
     * simulation's random stream, so a battle replays the same with or without anybody drawing it.
     */
    public void emitGroundDust(float x, float y, float vx, float vy, float radius, int kind) {
        groundDustThisFrame.add(new float[]{x, y, vx, vy, radius, kind});
    }

    /** Queues one fire burst this frame; the fire counterpart of {@link #emitSmokePuff}. */
    public void emitFireBurst(float x, float y, float radius) {
        fireBurstsThisFrame.add(new float[]{x, y, radius});
    }

    // ---- Dust ----

    /** Queues a wall-collapse dust-burst event at world cell-center {@code (cellX, cellY)}. Drained by the renderer into {@code ImpactFx.spawnWallCollapse} each frame. */
    public void spawnDustBurst(float cellX, float cellY) {
        wallDustsThisFrame.add(new float[]{cellX, cellY});
    }

    /** Queues a presentation event for an externally delivered heavy blast. */
    public void spawnHeavyImpact(float x, float y, float radius) {
        heavyImpactsThisFrame.add(new float[]{x, y, radius});
    }

    /**
     * Queues a heavy blast in the colour of the ordnance that made it: the event
     * carries {@code tint} as three further floats (0..1), which a host that
     * understands them draws the blast with and which one that does not can
     * ignore, since the first three entries are unchanged.
     */
    public void spawnHeavyImpact(float x, float y, float radius, Color tint) {
        if (tint == null) {
            spawnHeavyImpact(x, y, radius);
            return;
        }
        heavyImpactsThisFrame.add(new float[]{x, y, radius,
                tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f});
    }

    // ---- Delivered ordnance ----

    /**
     * Queues one round leaving its carrier for a point on the ground. The
     * simulation has already resolved the delivery by the time this is called;
     * the record exists so a host drawing the battle can show the round going
     * out and arriving, and hear the weapon.
     */
    public void spawnOrdnanceRelease(OrdnanceRelease release) {
        ordnanceReleasesThisFrame.add(release);
    }

    /** Rounds released during the last advance. Drained by the renderer per frame. */
    public List<OrdnanceRelease> getOrdnanceReleasesThisFrame() {
        return ordnanceReleasesThisFrame;
    }

    /**
     * A claw or bite raked across a body at ({@code x},{@code y}), travelling along ({@code dirX},{@code dirY}) (a unit
     * vector): dust kicked off the ground, sparks off its plate and blood thrown on, {@code blood} heavy (0 for a
     * body that does not bleed, 1 for the plain spray). Presentation only; the hit itself already resolved.
     */
    public void emitClawStrike(float x, float y, float dirX, float dirY, float blood) {
        clawStrikesThisFrame.add(new float[]{x, y, dirX, dirY, blood});
    }

    // ---- Per-frame event drains ----

    /** Claw strikes queued this tick, each {x, y, dirX, dirY, blood}; see {@link #emitClawStrike}. */
    public List<float[]> getClawStrikesThisFrame() { return clawStrikesThisFrame; }

    public List<float[]> getSmokePuffsThisFrame() { return smokePuffsThisFrame; }
    public List<float[]> getGroundDustThisFrame() { return groundDustThisFrame; }
    public List<float[]> getFireBurstsThisFrame() { return fireBurstsThisFrame; }
    public List<float[]> getWallDustsThisFrame()  { return wallDustsThisFrame; }
    public List<float[]> getHeavyImpactsThisFrame() { return heavyImpactsThisFrame; }

    /**
     * Clears the per-frame event lists. Called from
     * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#advance(float)}
     * at the top of each advance so a paused caller doesn't keep replaying the
     * previous frame's events.
     */
    public void beginFrame() {
        smokePuffsThisFrame.clear();
        groundDustThisFrame.clear();
        fireBurstsThisFrame.clear();
        wallDustsThisFrame.clear();
        heavyImpactsThisFrame.clear();
        clawStrikesThisFrame.clear();
        rollingBlastsThisFrame.clear();
        ordnanceReleasesThisFrame.clear();
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
                float radius = (0.40f + cooledFrac * 0.45f) * w.effectScale;
                smokePuffsThisFrame.add(new float[]{w.x, w.y, radius});
                w.nextPuffTimer = WRECK_PUFF_MIN_GAP
                        + rng.nextFloat() * (WRECK_PUFF_MAX_GAP - WRECK_PUFF_MIN_GAP);
            }

            if (age < w.burnDuration) {
                w.nextFireTimer -= dt;
                if (w.nextFireTimer <= 0f) {
                    float burnRemaining = w.burnDuration - age;
                    float intensity = (burnRemaining < WRECK_FIRE_FADE_DURATION)
                            ? burnRemaining / WRECK_FIRE_FADE_DURATION
                            : 1f;
                    if (rng.nextFloat() < intensity) {
                        float fireRadius = (0.40f + rng.nextFloat() * 0.30f) * w.effectScale;
                        fireBurstsThisFrame.add(new float[]{w.x, w.y, fireRadius});
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
                cannonPlumes.remove(p);
                continue;
            }
            p.nextPuffTimer -= dt;
            if (p.nextPuffTimer <= 0f) {
                float lifeFrac = p.remainingLifetime / p.totalLifetime;
                // Bigger puffs early (impact bloom), tightening as the column
                // rises. Floor keeps the tail-end column readable rather than
                // shrinking to invisible.
                float radius = (0.45f + Math.max(0.20f, lifeFrac) * 0.55f) * p.radiusScale;
                smokePuffsThisFrame.add(new float[]{p.x, p.y, radius});
                float age = p.totalLifetime - p.remainingLifetime;
                if (cannonPlumes.contains(p) && age < CANNON_PLUME_FIRE_DURATION) {
                    float fireRadius = (0.35f + lifeFrac * 0.35f) * p.radiusScale;
                    fireBurstsThisFrame.add(new float[]{p.x, p.y, fireRadius});
                }
                p.nextPuffTimer = PLUME_PUFF_MIN_GAP
                        + rng.nextFloat() * (PLUME_PUFF_MAX_GAP - PLUME_PUFF_MIN_GAP);
            }
        }
    }
}
