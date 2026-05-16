package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Atmosphere layer — vanilla fighter sprites cruise across the battle map on
 * gentle weaving curves, occasionally bank around to commit to a deliberate
 * strafing run on a cluster of opposing units, and lock into brief dogfight
 * stand-offs when they cross paths. Purely visual + audio with one narrow
 * coupling to the sim: {@link BattleSimulation#applyExternalDamage} for hits.
 *
 * <p>Movement is heading-based with a clamped turn rate, so paths naturally
 * arc instead of snapping along straight chords. Cruise direction is a
 * randomized weave (sinusoidal offset on top of a base heading); strafing
 * runs override the weave with explicit run-in / run-out waypoints, which
 * the steering turns toward at the same max rate — producing a continuous
 * banked loop rather than a teleport.
 *
 * <p>Coordinate system matches units + shuttles. Sprite paths resolve against
 * the vanilla install at runtime (no redistribution). Singleton SpriteAPIs are
 * reused across fighters of the same profile; mutable state is reset between
 * fighters.
 */
public final class FlybyOverlay {

    private static final Logger LOG = Global.getLogger(FlybyOverlay.class);

    // ---- Sound ids (declared in mod/data/config/sounds.json). -----------------
    public static final String SFX_GUN_HEAVY  = "marines_flyby_gun_heavy";
    public static final String SFX_GUN_LIGHT  = "marines_flyby_gun_light";
    public static final String SFX_GUN_ENERGY = "marines_flyby_gun_energy";
    public static final String SFX_IMPACT     = "marines_flyby_impact";

    // ---- Shared FX sprite paths (vanilla particles). --------------------------
    private static final String SPRITE_SHADOW       = "graphics/fx/particlealpha64linear.png";
    private static final String SPRITE_GLOW         = "graphics/fx/glow64.png";
    private static final String SPRITE_ENGINE_GLOW  = "graphics/fx/engineglow32.png";

    // ---- Spawn pacing ---------------------------------------------------------
    /** Safety cap on simultaneous flybys. The roster's own schedule normally keeps things sane; this protects against an over-eager mission roll. */
    private static final int MAX_CONCURRENT = 6;
    /** Cells of off-map slack a fighter spawns / despawns past. */
    private static final float OFFMAP_PAD = 8f;

    // ---- Motion ---------------------------------------------------------------
    /** Speed bounds (cells/sec). 1.5x the pre-curve numbers so fighters read as atmospheric jets, not patrolling drones. */
    private static final float SPEED_MIN = 9f;
    private static final float SPEED_MAX = 15f;
    /** Max yaw rate (degrees/sec). Caps how tight a fighter can bank — keeps strafing loops feeling like real arcs, not pivots in place. */
    private static final float TURN_RATE_DEG_PER_SEC = 80f;
    /** Weave parameters during CRUISE — sinusoid added to base heading, so the path snakes lazily instead of running straight. */
    private static final float WEAVE_FREQ_HZ_MIN  = 0.15f;
    private static final float WEAVE_FREQ_HZ_MAX  = 0.45f;
    private static final float WEAVE_AMP_DEG_MIN  = 12f;
    private static final float WEAVE_AMP_DEG_MAX  = 35f;

    // ---- Dogfight gating ------------------------------------------------------
    private static final float DOGFIGHT_RADIUS = 6f;
    private static final float DOGFIGHT_AGGRO_REFRESH = 2.5f;
    private static final float DOGFIGHT_WOBBLE_DEG = 25f;
    private static final float DOGFIGHT_WOBBLE_HZ  = 1.8f;

    // ---- Opportunistic strafe (single-target during CRUISE) -------------------
    private static final float STRAFE_CONE_HALF_DEG = 40f;
    private static final float STRAFE_RANGE_CELLS   = 14f;
    private static final float STRAFE_REARM_SEC     = 3.5f;
    /** Per-shot pitch jitter (±). Wide because the sound system folds identical-pitch repeats into a single voice; varying the pitch keeps each round audible. */
    private static final float SFX_PITCH_JITTER     = 0.15f;
    /** Cells → OpenAL world units, for positional fire/listener placement. 30 puts a typical battlefield (~24 cells) at ~720 units — vanilla-combat-ish scale where the engine's distance attenuation reads cleanly. */
    private static final float AUDIO_WORLD_UNITS_PER_CELL = 30f;

    // ---- Deliberate strafing run (multi-target AoE) ---------------------------
    /** Sim-seconds between cluster scans during CRUISE. Cheap loop, but no need to run it every tick. */
    private static final float RUN_TRIGGER_INTERVAL_SEC = 1.5f;
    /** Probability a viable cluster gets a commit, rolled at each scan. <1 keeps runs feeling like choices, not reflexes. */
    private static final float RUN_TRIGGER_PROBABILITY = 0.45f;
    /** Minimum opposing-faction units inside {@link #CLUSTER_RADIUS_CELLS} of each other to count as a strafe-worthy cluster. */
    private static final int CLUSTER_MIN_UNITS = 3;
    private static final float CLUSTER_RADIUS_CELLS = 8f;
    /** Distance from the cluster centroid the run-in / run-out waypoints sit on. */
    private static final float RUN_WAYPOINT_DISTANCE = 18f;
    /** Sim-seconds the fighter spends in RUN state laying down fire. After this it returns to CRUISE regardless of waypoint progress. */
    private static final float RUN_DURATION_SEC = 2.8f;
    /** Cell distance from waypoint that counts as "arrived." Loose because turn rate keeps the fighter from snapping to it. */
    private static final float RUN_WAYPOINT_ARRIVAL = 4f;
    /** Failsafe — if BANK_BACK takes longer than this, give up and start the run from current heading. */
    private static final float RUN_BANK_BACK_TIMEOUT = 6f;
    /** Cooldown between successive runs from the same fighter, so a single fighter doesn't loop forever. */
    private static final float RUN_REARM_COOLDOWN = 9f;
    private static final float RUN_FIRE_INTERVAL_SEC = 0.09f;
    /** Spread half-angle (degrees) of run fire. Wide on purpose — most rounds go errant. */
    private static final float RUN_SPREAD_DEG = 22f;
    /** Visual tracer reach for RUN tracers — they don't have a specific target, so this fixes how far ahead they paint. */
    private static final float RUN_TRACER_RANGE_CELLS = 16f;
    /** Cell radius around each tracer endpoint that catches "hit" units. */
    private static final float RUN_AOE_RADIUS_CELLS = 1.6f;
    /** Damage applied to each unit caught in a tracer's AoE. Small — runs spam many tracers, so total damage scales with hits, not per-round. */
    private static final float RUN_DAMAGE_PER_HIT = 0.9f;
    /** Wall HP a single RUN tracer chips off if its endpoint lands on a wall cell. Walls start at 100 (UrbanMapGenerator.WALL_HP_DEFAULT) — ~5 connecting tracers flatten a wall, so a sustained strafing run reshapes buildings. */
    private static final int RUN_WALL_DAMAGE = 20;
    /** Wall HP a CRUISE opportunistic round chips. Lower than RUN damage — the burst is aimed at a unit, so wall hits are spillover. */
    private static final int CRUISE_WALL_DAMAGE = 8;
    /** Tint for the dust burst when a wall collapses. Cooler grey so it reads distinct from the warm muzzle/impact glows. */
    private static final Color WALL_RUBBLE_DUST = new Color(0xB0, 0xA8, 0x98);
    /** Small dust speck spawned when a tracer chips a wall but doesn't collapse it. Lighter than {@link #WALL_RUBBLE_DUST} so chip vs. collapse reads at a glance. */
    private static final Color WALL_CHIP_DUST   = new Color(0xD0, 0xC4, 0xB0);
    /** Small dirt kick spawned when a tracer hits walkable ground. Warm brown so it differs from the cool wall dust. */
    private static final Color FLOOR_KICK_DUST  = new Color(0x88, 0x6E, 0x50);

    // ---- Visual feel ----------------------------------------------------------
    private static final float SHADOW_Y_OFFSET = -0.7f;
    private static final float SHADOW_ALPHA    = 0.45f;
    private static final float ENGINE_GLOW_LEN_MULT = 0.7f;
    private static final float MUZZLE_FLASH_LIFETIME = 0.08f;
    private static final float IMPACT_FLASH_LIFETIME = 0.22f;

    // ---- Live state -----------------------------------------------------------
    private final List<Fighter> fighters = new ArrayList<>();
    private final List<Tracer> tracers = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final EnumMap<FighterProfile, SpriteAPI> sprites = new EnumMap<>(FighterProfile.class);
    private SpriteAPI shadowSprite;
    private SpriteAPI glowSprite;
    private SpriteAPI engineSprite;
    private boolean spritesLoadAttempted;

    private final Random rng = new Random();

    /** Sim-seconds since the current battle started — drives roster spawn scheduling. Reset when the sim instance changes. */
    private float simTime = 0f;
    /** Identity of the last sim we ticked; on change we treat it as a new battle and reset spawn tracking + visual state. */
    private BattleSimulation lastSim;
    /** Roster currently being driven; cached for change-detection. */
    private FlybyRoster lastRoster;
    /** Sortie counter per wing in {@link #lastRoster}, parallel to its wings list. */
    private int[] sortiesSpawned = new int[0];

    /**
     * Drives the overlay one sim-time step. Pass the same {@code dt} you feed
     * the simulation (already scaled for pause / 1x / 2x / 4x). Null sim
     * disables targeting + damage but the visual layer keeps running.
     */
    public void advance(float dt, BattleSimulation sim, BattleLayout layout) {
        if (dt <= 0f || layout == null) return;
        // Park the OpenAL listener at the battlefield center so positional fire
        // sounds pan / attenuate around a fixed observer. setListenerPosOverrideOneFrame
        // is a one-frame override, so we re-arm it every advance — same pattern as playUILoop.
        // Outside a CombatEngine the listener has no other driver, so this is the only place
        // it gets set; if we later add a scrolling camera, swap this for camera-center.
        float gridCellsW = layout.gridW / Math.max(0.001f, layout.cellSize);
        float gridCellsH = layout.gridH / Math.max(0.001f, layout.cellSize);
        Global.getSoundPlayer().setListenerPosOverrideOneFrame(new Vector2f(
                (gridCellsW * 0.5f) * AUDIO_WORLD_UNITS_PER_CELL,
                (gridCellsH * 0.5f) * AUDIO_WORLD_UNITS_PER_CELL));
        // New battle? Wipe state — between-battle leftovers shouldn't bleed into the next mission.
        if (sim != lastSim) {
            lastSim = sim;
            resetForNewBattle();
        }
        // Freeze the overlay once the sim resolves — no new spawns, no fire,
        // no damage. Existing fighters and particles stop in place; the
        // victory / defeat banner reads cleanly without flyby losses
        // continuing to mutate the casualty count.
        if (sim != null && sim.isComplete()) return;

        simTime += dt;
        maybeSpawnFromRoster(sim, layout);

        applyDogfightAggro();

        for (int i = fighters.size() - 1; i >= 0; i--) {
            Fighter f = fighters.get(i);
            tickFighter(f, dt, sim, layout);
            if (isOffMap(f, layout)) fighters.remove(i);
        }

        for (int i = tracers.size() - 1; i >= 0; i--) {
            if ((tracers.get(i).lifetimeRemaining -= dt) <= 0f) tracers.remove(i);
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            if ((particles.get(i).lifetimeRemaining -= dt) <= 0f) particles.remove(i);
        }
    }

    /**
     * Per-tick fighter update. Steers heading toward the state-driven target
     * heading at a clamped rate (so paths arc, never snap), advances position
     * along the new heading, then runs state-specific firing logic.
     */
    private void tickFighter(Fighter f, float dt, BattleSimulation sim, BattleLayout layout) {
        // 1. Determine target heading based on current state.
        float targetHeading = pickTargetHeading(f, dt);

        // 2. Steer toward it at the clamped rate. Wobble overlay (aggro) is added
        //    AFTER steering so it doesn't fight the heading lock-in for state goals.
        float diff = wrapAngleDiffDeg(targetHeading - f.headingDeg);
        float maxStep = TURN_RATE_DEG_PER_SEC * dt;
        f.headingDeg += clamp(diff, -maxStep, maxStep);
        f.headingDeg = wrap360(f.headingDeg);

        // 3. Cosmetic wobble while dogfighting — affects facing only, not motion.
        f.wobblePhase += dt * 2f * (float) Math.PI * DOGFIGHT_WOBBLE_HZ;
        float drawnFacing = f.headingDeg
                + (f.aggroTimer > 0f ? (float) Math.sin(f.wobblePhase) * DOGFIGHT_WOBBLE_DEG : 0f);
        f.facingDeg = drawnFacing;

        // 4. Aggro decay — and aggro cancels any active strafing run (you don't strafe while being chased).
        if (f.aggroTimer > 0f) {
            f.aggroTimer = Math.max(0f, f.aggroTimer - dt);
            if (f.runState != RunState.NONE) {
                f.runState = RunState.NONE;
                f.runRearmTimer = RUN_REARM_COOLDOWN;
            }
        }

        // 5. Move along heading.
        float rad = (float) Math.toRadians(f.headingDeg);
        f.vx = (float) Math.cos(rad) * f.speed;
        f.vy = (float) Math.sin(rad) * f.speed;
        f.worldX += f.vx * dt;
        f.worldY += f.vy * dt;

        // 6. State-specific firing + transitions.
        f.runRearmTimer = Math.max(0f, f.runRearmTimer - dt);
        switch (f.runState) {
            case NONE:
                tickCruise(f, dt, sim);
                break;
            case BANK_BACK:
                tickBankBack(f, dt);
                break;
            case RUN:
                tickRun(f, dt, sim);
                break;
        }
    }

    /**
     * In CRUISE: weave the base heading via sinusoid (lazy snake). In BANK_BACK / RUN:
     * point at the active waypoint. Aggro doesn't override — the wobble is layered
     * onto facing for visual chase only.
     */
    private float pickTargetHeading(Fighter f, float dt) {
        switch (f.runState) {
            case BANK_BACK:
                return headingTo(f.worldX, f.worldY, f.runInX, f.runInY);
            case RUN:
                return headingTo(f.worldX, f.worldY, f.runOutX, f.runOutY);
            case NONE:
            default:
                f.weavePhase += dt * 2f * (float) Math.PI * f.weaveFreq;
                return f.baseHeadingDeg + (float) Math.sin(f.weavePhase) * f.weaveAmpDeg;
        }
    }

    /**
     * Cruise behavior: opportunistic single-target bursts (existing) plus a
     * periodic cluster scan that can commit the fighter to a deliberate
     * strafing run. Aggro and run-rearm both gate the strafing-run check.
     */
    private void tickCruise(Fighter f, float dt, BattleSimulation sim) {
        // Drive any active opportunistic burst — fire each scheduled round, then enter rearm cooldown.
        if (f.burstRemaining > 0) {
            f.burstNextFireIn -= dt;
            while (f.burstNextFireIn <= 0f && f.burstRemaining > 0) {
                fireOneTracerAt(f, f.burstTarget, sim);
                f.burstNextFireIn += f.profile.burstInterval;
                f.burstRemaining--;
            }
            if (f.burstRemaining == 0) {
                f.strafeRearmTimer = STRAFE_REARM_SEC;
                f.burstTarget = null;
            }
            return; // bursts and cluster scans are mutually exclusive — finish what you started
        }

        f.strafeRearmTimer = Math.max(0f, f.strafeRearmTimer - dt);

        // Cluster scan for strafing-run commit, rate-limited.
        f.runScanTimer -= dt;
        if (f.runScanTimer <= 0f) {
            f.runScanTimer = RUN_TRIGGER_INTERVAL_SEC;
            if (sim != null && f.aggroTimer <= 0f && f.runRearmTimer <= 0f
                    && rng.nextFloat() < RUN_TRIGGER_PROBABILITY
                    && tryPlanStrafingRun(f, sim)) {
                f.runState = RunState.BANK_BACK;
                f.runStateTimer = 0f;
                return;
            }
        }

        // Otherwise, opportunistic single-target strafe (small tight burst).
        if (f.aggroTimer <= 0f && f.strafeRearmTimer <= 0f && sim != null) {
            Unit target = acquireStrafeTarget(f, sim);
            if (target != null) {
                f.burstTarget = target;
                f.burstRemaining = f.profile.burstSize;
                f.burstNextFireIn = 0f;
            }
        }
    }

    /**
     * BANK_BACK turns the fighter toward its run-in waypoint. We arrive when
     * we're within {@link #RUN_WAYPOINT_ARRIVAL} cells, OR after the timeout —
     * the timeout exists because tight turn rates can leave a fighter circling
     * a waypoint it can't quite reach.
     */
    private void tickBankBack(Fighter f, float dt) {
        f.runStateTimer += dt;
        float dx = f.runInX - f.worldX, dy = f.runInY - f.worldY;
        float distSq = dx * dx + dy * dy;
        if (distSq <= RUN_WAYPOINT_ARRIVAL * RUN_WAYPOINT_ARRIVAL
                || f.runStateTimer >= RUN_BANK_BACK_TIMEOUT) {
            f.runState = RunState.RUN;
            f.runStateTimer = 0f;
            f.runFireAccumulator = 0f;
        }
    }

    /**
     * RUN ticks the run duration timer, sprays wide-spread tracers on the
     * fire interval, and lands AoE damage on any opposing units within
     * {@link #RUN_AOE_RADIUS_CELLS} of each tracer's endpoint. Most tracers
     * miss entirely — that's the point.
     */
    private void tickRun(Fighter f, float dt, BattleSimulation sim) {
        f.runStateTimer += dt;
        f.runFireAccumulator -= dt;
        while (f.runFireAccumulator <= 0f) {
            fireRunTracer(f, sim);
            f.runFireAccumulator += RUN_FIRE_INTERVAL_SEC;
        }
        // Exit run on duration or on passing the run-out waypoint.
        float dx = f.runOutX - f.worldX, dy = f.runOutY - f.worldY;
        boolean passedWaypoint = dx * dx + dy * dy <= RUN_WAYPOINT_ARRIVAL * RUN_WAYPOINT_ARRIVAL;
        if (passedWaypoint || f.runStateTimer >= RUN_DURATION_SEC) {
            f.runState = RunState.NONE;
            f.runRearmTimer = RUN_REARM_COOLDOWN;
            // Snap base heading back toward original exit so cruise weave doesn't
            // immediately yank the fighter back over the cluster.
            f.baseHeadingDeg = headingTo(f.worldX, f.worldY, f.exitX, f.exitY);
        }
    }

    /**
     * Finds a cluster of opposing units and plans run-in / run-out waypoints.
     * Cluster definition: any opposing unit with ≥ {@code CLUSTER_MIN_UNITS - 1}
     * other opposing units within {@code CLUSTER_RADIUS_CELLS}. Centroid is the
     * average position of the cluster members. Run line passes through the
     * centroid, oriented toward the fighter's current heading so the bank-back
     * arc is a reasonable U-turn rather than a full 360°.
     */
    private boolean tryPlanStrafingRun(Fighter f, BattleSimulation sim) {
        Faction enemy = (f.side == Faction.MARINE) ? Faction.DEFENDER : Faction.MARINE;
        float clusterR2 = CLUSTER_RADIUS_CELLS * CLUSTER_RADIUS_CELLS;
        List<Unit> enemies = new ArrayList<>();
        for (Unit u : sim.getUnits()) {
            if (u.isAlive() && u.faction == enemy) enemies.add(u);
        }
        if (enemies.size() < CLUSTER_MIN_UNITS) return false;

        // For each enemy, count neighbors within cluster radius. The best
        // anchor is the one with the most neighbors — its neighbors form the
        // cluster we strafe.
        Unit bestAnchor = null;
        int bestCount = 0;
        List<Unit> bestNeighbors = new ArrayList<>();
        for (Unit a : enemies) {
            List<Unit> neighbors = new ArrayList<>();
            for (Unit b : enemies) {
                float dx = (b.renderX + 0.5f) - (a.renderX + 0.5f);
                float dy = (b.renderY + 0.5f) - (a.renderY + 0.5f);
                if (dx * dx + dy * dy <= clusterR2) neighbors.add(b);
            }
            if (neighbors.size() > bestCount) {
                bestCount = neighbors.size();
                bestAnchor = a;
                bestNeighbors = neighbors;
            }
        }
        if (bestCount < CLUSTER_MIN_UNITS || bestAnchor == null) return false;

        // Centroid of the cluster.
        float cx = 0f, cy = 0f;
        for (Unit u : bestNeighbors) { cx += u.renderX + 0.5f; cy += u.renderY + 0.5f; }
        cx /= bestNeighbors.size();
        cy /= bestNeighbors.size();

        // Run-line: aligned with the fighter's current heading so the bank-back
        // arc is a reasonable U-turn. Run-in waypoint sits behind the fighter
        // (opposite its current heading offset from the centroid), run-out
        // waypoint sits ahead of the centroid in the same direction.
        float headRad = (float) Math.toRadians(f.headingDeg);
        float runDirX = (float) Math.cos(headRad);
        float runDirY = (float) Math.sin(headRad);
        f.runInX  = cx - runDirX * RUN_WAYPOINT_DISTANCE;
        f.runInY  = cy - runDirY * RUN_WAYPOINT_DISTANCE;
        f.runOutX = cx + runDirX * RUN_WAYPOINT_DISTANCE;
        f.runOutY = cy + runDirY * RUN_WAYPOINT_DISTANCE;
        return true;
    }

    /** Forward-cone search used by opportunistic CRUISE strafe (single target). */
    private Unit acquireStrafeTarget(Fighter f, BattleSimulation sim) {
        Faction enemy = (f.side == Faction.MARINE) ? Faction.DEFENDER : Faction.MARINE;
        float cosThreshold = (float) Math.cos(Math.toRadians(STRAFE_CONE_HALF_DEG));
        float rad = (float) Math.toRadians(f.headingDeg);
        float dx = (float) Math.cos(rad), dy = (float) Math.sin(rad);

        Unit best = null;
        float bestDist = STRAFE_RANGE_CELLS;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.faction != enemy) continue;
            float ux = (u.renderX + 0.5f) - f.worldX;
            float uy = (u.renderY + 0.5f) - f.worldY;
            float dist = (float) Math.sqrt(ux * ux + uy * uy);
            if (dist < 0.001f || dist > bestDist) continue;
            float dot = (ux * dx + uy * dy) / dist;
            if (dot < cosThreshold) continue;
            best = u;
            bestDist = dist;
        }
        return best;
    }

    /**
     * Fires one round at a specific target with the profile's burst spread.
     * Used by CRUISE opportunistic bursts — applies damage on hit, with a
     * muzzle flash + impact glow.
     */
    private void fireOneTracerAt(Fighter f, Unit target, BattleSimulation sim) {
        if (target == null) return;
        float spreadRad = (float) Math.toRadians((rng.nextFloat() * 2f - 1f) * f.profile.burstSpreadDeg);
        float tx = target.renderX + 0.5f;
        float ty = target.renderY + 0.5f;
        float dx = tx - f.worldX, dy = ty - f.worldY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float cos = (float) Math.cos(spreadRad), sin = (float) Math.sin(spreadRad);
        float ndx = (dx * cos - dy * sin) / len;
        float ndy = (dx * sin + dy * cos) / len;
        float endX = f.worldX + ndx * len;
        float endY = f.worldY + ndy * len;
        spawnTracer(f, endX, endY);
        spawnMuzzleFlash(f);
        if (sim != null) {
            sim.applyExternalDamage(target, f.profile.perTracerDamage);
            spawnImpact(endX, endY, f.profile.tracerColor);
            // Spread that carried the round onto a wall instead chips the wall —
            // emergent collateral, no extra branching needed.
            damageWallAtEndpoint(sim, endX, endY, CRUISE_WALL_DAMAGE);
        }
        playFireSound(f);
    }

    /**
     * Fires one tracer during a RUN — direction is current heading ± wide
     * spread, range is fixed (the run isn't aiming at a single unit). AoE
     * damage lands on every opposing unit within
     * {@link #RUN_AOE_RADIUS_CELLS} of the tracer endpoint, so a single
     * lucky tracer can clip multiple units in a cluster.
     */
    private void fireRunTracer(Fighter f, BattleSimulation sim) {
        float spreadRad = (float) Math.toRadians((rng.nextFloat() * 2f - 1f) * RUN_SPREAD_DEG);
        float headRad = (float) Math.toRadians(f.headingDeg) + spreadRad;
        float ndx = (float) Math.cos(headRad);
        float ndy = (float) Math.sin(headRad);
        float endX = f.worldX + ndx * RUN_TRACER_RANGE_CELLS;
        float endY = f.worldY + ndy * RUN_TRACER_RANGE_CELLS;
        spawnTracer(f, endX, endY);
        spawnMuzzleFlash(f);

        // AoE damage check — anyone close to the tracer endpoint catches a round.
        boolean anyHit = false;
        if (sim != null) {
            Faction enemy = (f.side == Faction.MARINE) ? Faction.DEFENDER : Faction.MARINE;
            float r2 = RUN_AOE_RADIUS_CELLS * RUN_AOE_RADIUS_CELLS;
            for (Unit u : sim.getUnits()) {
                if (!u.isAlive() || u.faction != enemy) continue;
                float ux = (u.renderX + 0.5f) - endX;
                float uy = (u.renderY + 0.5f) - endY;
                if (ux * ux + uy * uy <= r2) {
                    sim.applyExternalDamage(u, RUN_DAMAGE_PER_HIT);
                    anyHit = true;
                }
            }
        }
        if (anyHit) {
            // Bright tracer-color glow on a connecting hit — same read as the
            // CRUISE opportunistic burst.
            spawnImpact(endX, endY, f.profile.tracerColor);
        } else {
            // Most RUN tracers miss; show where they landed so the player can
            // see the strafe pattern punching the ground / walls instead of
            // just disappearing into the void.
            spawnTerrainImpact(sim, endX, endY, f.profile.tracerColor);
        }
        // Wide-spread runs frequently spray walls — chip them. A sustained
        // run can flatten a wall, opening line-of-sight + new paths for the
        // sim's pathfinder to discover on its next re-route. The dust on
        // collapse is louder than the chip dust above so the destroy event reads.
        if (sim != null) damageWallAtEndpoint(sim, endX, endY, RUN_WALL_DAMAGE);

        // Audio plays per-tracer — the wider SFX_PITCH_JITTER above is what
        // keeps the sound system from collapsing repeated same-id voices.
        playFireSound(f);
    }

    /**
     * Spawns a small spark + dust speck at the tracer endpoint when it didn't
     * connect with a unit. Differentiates wall vs. open ground via the grid;
     * gives the strafing run a visual touchdown on every round even when the
     * fire is going wide (which is most of it).
     */
    private void spawnTerrainImpact(BattleSimulation sim, float endX, float endY, Color tracerColor) {
        // Always show a small tracer-color spark — round visibly hit *something*.
        Particle spark = new Particle();
        spark.x = endX;
        spark.y = endY;
        spark.lifetimeRemaining = 0.16f;
        spark.lifetimeMax = 0.16f;
        spark.radiusCells = 0.32f;
        spark.color = tracerColor;
        particles.add(spark);

        if (sim == null) return;
        int cellX = (int) Math.floor(endX);
        int cellY = (int) Math.floor(endY);
        if (!sim.getGrid().inBounds(cellX, cellY)) return;
        boolean isWall = !sim.getGrid().isWalkable(cellX, cellY);
        Particle dust = new Particle();
        dust.x = endX;
        dust.y = endY;
        dust.lifetimeRemaining = isWall ? 0.32f : 0.24f;
        dust.lifetimeMax = dust.lifetimeRemaining;
        dust.radiusCells = isWall ? 0.45f : 0.30f;
        dust.color = isWall ? WALL_CHIP_DUST : FLOOR_KICK_DUST;
        particles.add(dust);
    }

    /**
     * Forwards wall damage to {@link BattleSimulation#damageCell} — that path
     * pipes through to the grid AND triggers a ZoneGraph rebuild on collapse,
     * which keeps the AI's zone vocabulary in sync. Spawns a dust burst only on
     * the collapse frame; chip damage gets the lighter chip dust spawned by
     * {@link #spawnTerrainImpact}, so chip vs. destroy reads at a glance.
     */
    private void damageWallAtEndpoint(BattleSimulation sim, float endX, float endY, int amount) {
        int cellX = (int) Math.floor(endX);
        int cellY = (int) Math.floor(endY);
        if (sim.damageCell(cellX, cellY, amount)) {
            spawnDustBurst(cellX + 0.5f, cellY + 0.5f);
        }
    }

    private void spawnDustBurst(float x, float y) {
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.lifetimeRemaining = 0.45f;
        p.lifetimeMax = 0.45f;
        p.radiusCells = 1.2f;
        p.color = WALL_RUBBLE_DUST;
        particles.add(p);
    }

    private void spawnTracer(Fighter f, float endX, float endY) {
        Tracer t = new Tracer();
        t.profile = f.profile;
        t.fromX = f.worldX;
        t.fromY = f.worldY;
        t.toX = endX;
        t.toY = endY;
        t.lifetimeRemaining = f.profile.tracerLifetime;
        tracers.add(t);
    }

    private void spawnMuzzleFlash(Fighter f) {
        Particle p = new Particle();
        p.x = f.worldX;
        p.y = f.worldY;
        p.lifetimeRemaining = MUZZLE_FLASH_LIFETIME;
        p.lifetimeMax = MUZZLE_FLASH_LIFETIME;
        p.radiusCells = 0.5f;
        p.color = f.profile.tracerColor;
        particles.add(p);
    }

    private void spawnImpact(float x, float y, Color color) {
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.lifetimeRemaining = IMPACT_FLASH_LIFETIME;
        p.lifetimeMax = IMPACT_FLASH_LIFETIME;
        p.radiusCells = 0.6f;
        p.color = color;
        particles.add(p);
    }

    private void playFireSound(Fighter f) {
        float pitch = f.profile.fireSoundPitch
                + (rng.nextFloat() * 2f - 1f) * SFX_PITCH_JITTER;
        // playSound (not playUISound) — vanilla weapon SFX are mono, which is what
        // the positional pipeline requires; UI-routed mono buffers play silently.
        // Location + velocity feed OpenAL spatialization + Doppler for fast banking jets.
        Vector2f loc = new Vector2f(f.worldX * AUDIO_WORLD_UNITS_PER_CELL,
                                    f.worldY * AUDIO_WORLD_UNITS_PER_CELL);
        Vector2f vel = new Vector2f(f.vx * AUDIO_WORLD_UNITS_PER_CELL,
                                    f.vy * AUDIO_WORLD_UNITS_PER_CELL);
        Global.getSoundPlayer().playSound(f.profile.fireSoundId, pitch, f.profile.fireSoundVolume, loc, vel);
    }

    /** Pairwise proximity check for dogfight aggro. O(n²) but n ≤ MAX_CONCURRENT. */
    private void applyDogfightAggro() {
        for (int i = 0; i < fighters.size(); i++) {
            Fighter a = fighters.get(i);
            for (int j = i + 1; j < fighters.size(); j++) {
                Fighter b = fighters.get(j);
                if (a.side == b.side) continue;
                float dx = a.worldX - b.worldX;
                float dy = a.worldY - b.worldY;
                if (dx * dx + dy * dy < DOGFIGHT_RADIUS * DOGFIGHT_RADIUS) {
                    a.aggroTimer = DOGFIGHT_AGGRO_REFRESH;
                    b.aggroTimer = DOGFIGHT_AGGRO_REFRESH;
                }
            }
        }
    }

    /**
     * Walks the active roster's wings and spawns any sortie whose schedule has
     * come due. Sortie {@code k} of a wing fires at {@code firstArrivalSec + k * spawnIntervalSec}.
     * {@link #MAX_CONCURRENT} acts as a backstop for pathological rosters; the
     * generator normally schedules wings far enough apart that we never hit it.
     */
    private void maybeSpawnFromRoster(BattleSimulation sim, BattleLayout layout) {
        if (sim == null) return;
        FlybyRoster roster = sim.getFlybyRoster();
        if (roster == null || roster.isEmpty()) return;

        // Re-arm sortie tracking if the roster object changed under us — e.g. a
        // re-attach with a different mission while the FlybyOverlay instance is reused.
        if (roster != lastRoster) {
            lastRoster = roster;
            sortiesSpawned = new int[roster.wings.size()];
        }

        for (int i = 0; i < roster.wings.size(); i++) {
            if (fighters.size() >= MAX_CONCURRENT) return;
            FighterWing wing = roster.wings.get(i);
            if (sortiesSpawned[i] >= wing.sortieCount) continue;
            float nextSpawnAt = wing.firstArrivalSec + sortiesSpawned[i] * wing.spawnIntervalSec;
            if (simTime < nextSpawnAt) continue;
            spawnFromWing(wing, layout);
            sortiesSpawned[i]++;
        }
    }

    /**
     * Spawns one fighter from a wing — wing dictates profile + side, the rest
     * (entry edge, exit, speed, weave params) is rolled per-sortie so a wing's
     * multiple sorties don't trace identical paths.
     */
    private void spawnFromWing(FighterWing wing, BattleLayout layout) {
        int gridCellsW = (int) (layout.gridW / Math.max(0.001f, layout.cellSize));
        int gridCellsH = (int) (layout.gridH / Math.max(0.001f, layout.cellSize));
        if (gridCellsW <= 0 || gridCellsH <= 0) return;

        int side = rng.nextInt(4);
        float sx, sy, ex, ey;
        switch (side) {
            case 0:  sx = rng.nextFloat() * gridCellsW; sy = gridCellsH + OFFMAP_PAD;
                     ex = rng.nextFloat() * gridCellsW; ey = -OFFMAP_PAD; break;
            case 1:  sx = gridCellsW + OFFMAP_PAD; sy = rng.nextFloat() * gridCellsH;
                     ex = -OFFMAP_PAD;             ey = rng.nextFloat() * gridCellsH; break;
            case 2:  sx = rng.nextFloat() * gridCellsW; sy = -OFFMAP_PAD;
                     ex = rng.nextFloat() * gridCellsW; ey = gridCellsH + OFFMAP_PAD; break;
            default: sx = -OFFMAP_PAD;             sy = rng.nextFloat() * gridCellsH;
                     ex = gridCellsW + OFFMAP_PAD; ey = rng.nextFloat() * gridCellsH; break;
        }

        Fighter f = new Fighter();
        f.profile = wing.profile;
        f.side = wing.side;
        f.worldX = sx; f.worldY = sy;
        f.exitX = ex;  f.exitY = ey;
        f.speed = SPEED_MIN + rng.nextFloat() * (SPEED_MAX - SPEED_MIN);
        f.baseHeadingDeg = headingTo(sx, sy, ex, ey);
        f.headingDeg = f.baseHeadingDeg;
        f.facingDeg = f.baseHeadingDeg;
        f.weavePhase = rng.nextFloat() * (float) (2 * Math.PI);
        f.weaveFreq = WEAVE_FREQ_HZ_MIN + rng.nextFloat() * (WEAVE_FREQ_HZ_MAX - WEAVE_FREQ_HZ_MIN);
        f.weaveAmpDeg = WEAVE_AMP_DEG_MIN + rng.nextFloat() * (WEAVE_AMP_DEG_MAX - WEAVE_AMP_DEG_MIN);
        f.runScanTimer = RUN_TRIGGER_INTERVAL_SEC;
        fighters.add(f);
    }

    /** Clears all transient overlay state. Called when the sim instance changes — leftover fighters / sortie counters from the prior battle would otherwise bleed across. */
    private void resetForNewBattle() {
        fighters.clear();
        tracers.clear();
        particles.clear();
        simTime = 0f;
        lastRoster = null;
        sortiesSpawned = new int[0];
    }

    private static boolean isOffMap(Fighter f, BattleLayout layout) {
        float gridCellsW = layout.gridW / Math.max(0.001f, layout.cellSize);
        float gridCellsH = layout.gridH / Math.max(0.001f, layout.cellSize);
        float pad = OFFMAP_PAD + 2f;
        return f.worldX < -pad || f.worldX > gridCellsW + pad
                || f.worldY < -pad || f.worldY > gridCellsH + pad;
    }

    /** Math-standard angle (0=+X, CCW positive) in degrees. */
    private static float headingTo(float fromX, float fromY, float toX, float toY) {
        return (float) Math.toDegrees(Math.atan2(toY - fromY, toX - fromX));
    }

    /** Folds an angle delta into [-180, 180] so steering takes the short way around. */
    private static float wrapAngleDiffDeg(float deg) {
        return ((deg + 540f) % 360f) - 180f;
    }

    private static float wrap360(float deg) {
        float r = deg % 360f;
        return r < 0f ? r + 360f : r;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---- Rendering ------------------------------------------------------------

    /**
     * Draws shadows → engine glows → fighters → tracers → impact flashes. Caller
     * is expected to invoke this AFTER ground units / shots / shuttles but BEFORE
     * the victory banner. Sprite facing converts heading (math-standard, 0=+X)
     * to Starsector convention (0=+Y) by subtracting 90°.
     */
    public void render(BattleLayout layout, float alphaMult) {
        if (layout == null) return;
        ensureSprites();

        if (shadowSprite != null) {
            for (Fighter f : fighters) drawShadow(f, layout, alphaMult);
        }
        if (engineSprite != null) {
            for (Fighter f : fighters) drawEngineGlow(f, layout, alphaMult);
        }
        for (Fighter f : fighters) {
            SpriteAPI sprite = sprites.get(f.profile);
            if (sprite == null) continue;
            float pxLen = f.profile.visualLengthCells * layout.cellSize;
            float texW = sprite.getWidth();
            float texH = sprite.getHeight();
            float aspect = (texH > 0f) ? texW / texH : 1f;
            sprite.setSize(pxLen * aspect, pxLen);
            sprite.setAngle(f.facingDeg - 90f);
            sprite.setAlphaMult(alphaMult);
            sprite.setColor(Color.WHITE);
            sprite.setNormalBlend();
            float px = layout.gridX + f.worldX * layout.cellSize;
            float py = layout.gridY + f.worldY * layout.cellSize;
            sprite.renderAtCenter(px, py);
        }
        for (SpriteAPI s : sprites.values()) {
            if (s != null) s.setAngle(0f);
        }

        if (!tracers.isEmpty()) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            glBegin(GL_QUADS);
            for (Tracer t : tracers) drawTracerQuad(t, layout, alphaMult);
            glEnd();
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }

        if (glowSprite != null) {
            for (Particle p : particles) drawParticle(p, layout, alphaMult);
        }
    }

    private void drawShadow(Fighter f, BattleLayout layout, float alphaMult) {
        float sizeCells = f.profile.visualLengthCells * 1.1f;
        float px = layout.gridX + f.worldX * layout.cellSize;
        float py = layout.gridY + (f.worldY + SHADOW_Y_OFFSET) * layout.cellSize;
        shadowSprite.setSize(sizeCells * layout.cellSize, sizeCells * 0.5f * layout.cellSize);
        shadowSprite.setAngle(0f);
        shadowSprite.setAlphaMult(alphaMult * SHADOW_ALPHA);
        shadowSprite.setColor(Color.BLACK);
        shadowSprite.setNormalBlend();
        shadowSprite.renderAtCenter(px, py);
    }

    private void drawEngineGlow(Fighter f, BattleLayout layout, float alphaMult) {
        float speed = (float) Math.sqrt(f.vx * f.vx + f.vy * f.vy);
        if (speed < 0.001f) return;
        float nx = f.vx / speed, ny = f.vy / speed;
        float backOffset = f.profile.visualLengthCells * 0.45f;
        float gx = f.worldX - nx * backOffset;
        float gy = f.worldY - ny * backOffset;
        float glowLenCells = f.profile.visualLengthCells * ENGINE_GLOW_LEN_MULT;
        engineSprite.setSize(glowLenCells * 0.6f * layout.cellSize, glowLenCells * layout.cellSize);
        engineSprite.setAngle(f.facingDeg - 90f);
        engineSprite.setAlphaMult(alphaMult * 0.9f);
        engineSprite.setColor(new Color(0xFF, 0xC8, 0x80));
        engineSprite.setAdditiveBlend();
        engineSprite.renderAtCenter(
                layout.gridX + gx * layout.cellSize,
                layout.gridY + gy * layout.cellSize);
        engineSprite.setAngle(0f);
    }

    private void drawTracerQuad(Tracer t, BattleLayout layout, float alphaMult) {
        float fromPx = layout.gridX + t.fromX * layout.cellSize;
        float fromPy = layout.gridY + t.fromY * layout.cellSize;
        float toPx   = layout.gridX + t.toX   * layout.cellSize;
        float toPy   = layout.gridY + t.toY   * layout.cellSize;
        float dx = toPx - fromPx, dy = toPy - fromPy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001f) return;
        float nx = dx / dist, ny = dy / dist;
        float scale = layout.cellSize / 32f;
        float tracerLen = t.profile.tracerPxLen * scale;
        float half = (t.profile.tracerPxThick * scale) * 0.5f;
        float headX = fromPx + nx * tracerLen;
        float headY = fromPy + ny * tracerLen;
        float perpX = -ny * half, perpY = nx * half;

        float lifeFrac = Math.max(0f, t.lifetimeRemaining / Math.max(0.001f, t.profile.tracerLifetime));
        float a = alphaMult * lifeFrac;
        Color c = t.profile.tracerColor;
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);

        glVertex2f(fromPx - perpX, fromPy - perpY);
        glVertex2f(fromPx + perpX, fromPy + perpY);
        glVertex2f(headX + perpX, headY + perpY);
        glVertex2f(headX - perpX, headY - perpY);
    }

    private void drawParticle(Particle p, BattleLayout layout, float alphaMult) {
        float lifeFrac = Math.max(0f, p.lifetimeRemaining / Math.max(0.001f, p.lifetimeMax));
        float radiusPx = p.radiusCells * layout.cellSize;
        glowSprite.setSize(radiusPx * 2f, radiusPx * 2f);
        glowSprite.setAngle(0f);
        glowSprite.setAlphaMult(alphaMult * lifeFrac);
        glowSprite.setColor(p.color);
        glowSprite.setAdditiveBlend();
        glowSprite.renderAtCenter(
                layout.gridX + p.x * layout.cellSize,
                layout.gridY + p.y * layout.cellSize);
    }

    // ---- Sprite loading -------------------------------------------------------

    private void ensureSprites() {
        if (spritesLoadAttempted) return;
        spritesLoadAttempted = true;
        for (FighterProfile profile : FighterProfile.values()) {
            SpriteAPI sprite = loadSpriteOrNull(profile.spritePath);
            if (sprite != null) sprites.put(profile, sprite);
        }
        shadowSprite = loadSpriteOrNull(SPRITE_SHADOW);
        glowSprite   = loadSpriteOrNull(SPRITE_GLOW);
        engineSprite = loadSpriteOrNull(SPRITE_ENGINE_GLOW);
    }

    private SpriteAPI loadSpriteOrNull(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("FlybyOverlay: getSprite returned null for " + path);
                return null;
            }
            LOG.info("FlybyOverlay: loaded " + path);
            return sprite;
        } catch (Exception e) {
            LOG.error("FlybyOverlay: failed to load " + path, e);
            return null;
        }
    }

    // ---- Inner data types -----------------------------------------------------

    private enum RunState { NONE, BANK_BACK, RUN }

    private static final class Fighter {
        FighterProfile profile;
        Faction side;

        // Position + motion. Heading is math-standard (0=+X, CCW); we steer it
        // toward state-dependent targets at TURN_RATE_DEG_PER_SEC.
        float worldX, worldY;
        float vx, vy;          // derived from heading + speed each tick; cached for engine-glow render
        float speed;
        float headingDeg;
        float facingDeg;       // headingDeg + cosmetic wobble; what the sprite draws at
        float exitX, exitY;    // original exit waypoint (for re-anchoring base heading after a RUN)

        // Cruise weave — sin-wave offset to base heading.
        float baseHeadingDeg;
        float weavePhase;
        float weaveFreq;
        float weaveAmpDeg;

        // Dogfight aggro.
        float aggroTimer;
        float wobblePhase;

        // Opportunistic CRUISE burst (single target).
        int burstRemaining;
        float burstNextFireIn;
        Unit burstTarget;
        float strafeRearmTimer;

        // Strafing run state machine.
        RunState runState = RunState.NONE;
        float runStateTimer;
        float runScanTimer;
        float runRearmTimer;
        float runInX, runInY;
        float runOutX, runOutY;
        float runFireAccumulator;
    }

    private static final class Tracer {
        FighterProfile profile;
        float fromX, fromY;
        float toX, toY;
        float lifetimeRemaining;
    }

    private static final class Particle {
        float x, y;
        float lifetimeRemaining;
        float lifetimeMax;
        float radiusCells;
        Color color;
    }
}
