package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

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
 * Atmosphere layer that flies vanilla fighter sprites across the battle map,
 * lets them strafe targets of opportunity on the ground, and pulls them into
 * brief dogfight stand-offs when they cross paths. Purely a visual + audio
 * layer with one narrow coupling to the sim: {@link BattleSimulation#applyExternalDamage}
 * for connecting strafe rounds.
 *
 * <p>Coordinate system matches units + shuttles — fighters live in <em>cell</em>
 * coords; the layout maps to pixels at draw time. Sprite paths reference the
 * core install directly (the resource loader resolves against core + enabled
 * mods, so no redistribution is needed).
 *
 * <p>Lifecycle ownership: created once per {@code BattleScreen.attach} and
 * thrown away on detach — the singleton SpriteAPIs the cache holds belong to
 * the engine and persist; we just reset their mutable state (size, angle,
 * color, alpha) before each draw.
 */
public final class FlybyOverlay {

    private static final Logger LOG = Global.getLogger(FlybyOverlay.class);

    // ---- Sound ids exposed for FighterProfile (declared in mod/data/config/sounds.json). ----
    public static final String SFX_GUN_HEAVY  = "marines_flyby_gun_heavy";
    public static final String SFX_GUN_LIGHT  = "marines_flyby_gun_light";
    public static final String SFX_GUN_ENERGY = "marines_flyby_gun_energy";
    public static final String SFX_IMPACT     = "marines_flyby_impact";

    // ---- Shared FX sprite paths — soft particles re-used for shadows / flashes / hits. ----
    private static final String SPRITE_SHADOW       = "graphics/fx/particlealpha64linear.png";
    private static final String SPRITE_GLOW         = "graphics/fx/glow64.png";
    private static final String SPRITE_ENGINE_GLOW  = "graphics/fx/engineglow32.png";

    // ---- Spawn pacing ---------------------------------------------------------
    /** Min/max sim-seconds between spawns. Re-rolled after each spawn. */
    private static final float SPAWN_GAP_MIN = 4f;
    private static final float SPAWN_GAP_MAX = 10f;
    /** First spawn delay — long enough that the player sees the ground action before fighters appear. */
    private static final float SPAWN_INITIAL_DELAY = 3f;
    /** Hard cap on simultaneous flybys. 4+ starts to feel cluttered over a 96x48 grid. */
    private static final int MAX_CONCURRENT = 4;

    /** Cells of off-map slack a fighter spawns / despawns past. Above the grid edge so its entry/exit reads as smooth. */
    private static final float OFFMAP_PAD = 8f;
    /** Fighter speed range, cells/sec. ~6-10 = comfortably above marine 2 cell/sec, reads as overhead. */
    private static final float SPEED_MIN = 6f;
    private static final float SPEED_MAX = 10f;

    // ---- Dogfight gating ------------------------------------------------------
    /** Cell radius below which two opposing fighters are considered to be tangling. */
    private static final float DOGFIGHT_RADIUS = 6f;
    /** Sim-seconds of aggro applied per tick of proximity. Refreshed each tick they're close, so they're locked in until separation. */
    private static final float DOGFIGHT_AGGRO_REFRESH = 2.5f;
    /** Cosmetic facing wobble while dogfighting — sells the chase even though paths stay straight. */
    private static final float DOGFIGHT_WOBBLE_DEG = 25f;
    private static final float DOGFIGHT_WOBBLE_HZ  = 1.8f;

    // ---- Strafe parameters ----------------------------------------------------
    /** Forward cone half-angle (degrees) for target acquisition. ~40 covers a generous strafe lane. */
    private static final float STRAFE_CONE_HALF_DEG = 40f;
    /** Max target range (cells) along the strafe lane. */
    private static final float STRAFE_RANGE_CELLS   = 14f;
    /** Long cooldown after a burst finishes before another strafe is even considered. */
    private static final float STRAFE_REARM_SEC     = 3.5f;
    /** Per-tracer pitch jitter (±) — same trick as the rifle pool, gives audio variety from a small clip pool. */
    private static final float SFX_PITCH_JITTER     = 0.05f;

    // ---- Visual feel ----------------------------------------------------------
    /** Drop-shadow offset (cells, downward in screen space) — sells altitude. */
    private static final float SHADOW_Y_OFFSET = -0.7f;
    /** Shadow base alpha. */
    private static final float SHADOW_ALPHA    = 0.45f;
    /** Engine glow tail length factor relative to fighter visual length. */
    private static final float ENGINE_GLOW_LEN_MULT = 0.7f;
    private static final float MUZZLE_FLASH_LIFETIME = 0.08f;
    private static final float IMPACT_FLASH_LIFETIME = 0.22f;

    // ---- Live state -----------------------------------------------------------
    private final List<Fighter> fighters = new ArrayList<>();
    private final List<Tracer> tracers = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    /** Per-profile sprite cache (singleton SpriteAPIs reused across fighters of the same type). */
    private final EnumMap<FighterProfile, SpriteAPI> sprites = new EnumMap<>(FighterProfile.class);
    private SpriteAPI shadowSprite;
    private SpriteAPI glowSprite;
    private SpriteAPI engineSprite;
    private boolean spritesLoadAttempted;

    /** Real-time seed; flyby visuals are intentionally non-deterministic. */
    private final Random rng = new Random();
    private float spawnTimer = SPAWN_INITIAL_DELAY;

    /**
     * Drives the overlay one sim-time step. Pass the same {@code dt} you feed
     * the simulation (already scaled for pause / 1x / 2x / 4x). The sim is used
     * for target acquisition + the strafe damage hook — null is fine and
     * disables those features without stopping the visual.
     */
    public void advance(float dt, BattleSimulation sim, BattleLayout layout) {
        if (dt <= 0f || layout == null) return;

        spawnTimer -= dt;
        if (spawnTimer <= 0f && fighters.size() < MAX_CONCURRENT) {
            spawnFighter(layout);
            spawnTimer = SPAWN_GAP_MIN + rng.nextFloat() * (SPAWN_GAP_MAX - SPAWN_GAP_MIN);
        }

        // 1. Apply dogfight aggro from proximity. Refresh both sides on contact so
        //    they stay tangled while they're close, and clean up naturally on separation.
        applyDogfightAggro();

        // 2. Tick each fighter — movement, aggro decay, burst state, strafe attempts.
        for (int i = fighters.size() - 1; i >= 0; i--) {
            Fighter f = fighters.get(i);
            tickFighter(f, dt, sim, layout);
            if (isOffMap(f, layout)) fighters.remove(i);
        }

        // 3. Age active tracers + particles in place.
        for (int i = tracers.size() - 1; i >= 0; i--) {
            Tracer t = tracers.get(i);
            t.lifetimeRemaining -= dt;
            if (t.lifetimeRemaining <= 0f) tracers.remove(i);
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.lifetimeRemaining -= dt;
            if (p.lifetimeRemaining <= 0f) particles.remove(i);
        }
    }

    private void tickFighter(Fighter f, float dt, BattleSimulation sim, BattleLayout layout) {
        // Move.
        f.worldX += f.vx * dt;
        f.worldY += f.vy * dt;

        // Aggro decays; while aggro'd, layer in a cosmetic facing wobble.
        f.aggroTimer = Math.max(0f, f.aggroTimer - dt);
        f.wobblePhase += dt * 2f * (float) Math.PI * DOGFIGHT_WOBBLE_HZ;
        float baseFacing = facingDeg(f.vx, f.vy);
        f.facingDeg = (f.aggroTimer > 0f)
                ? baseFacing + (float) Math.sin(f.wobblePhase) * DOGFIGHT_WOBBLE_DEG
                : baseFacing;

        f.strafeRearmTimer = Math.max(0f, f.strafeRearmTimer - dt);

        // Drive an active burst, or look for a new target if we're rearmed + not aggro'd.
        if (f.burstRemaining > 0) {
            f.burstNextFireIn -= dt;
            while (f.burstNextFireIn <= 0f && f.burstRemaining > 0) {
                fireOneTracer(f, sim);
                f.burstNextFireIn += f.profile.burstInterval;
                f.burstRemaining--;
            }
            if (f.burstRemaining == 0) {
                f.strafeRearmTimer = STRAFE_REARM_SEC;
                f.burstTarget = null;
            }
        } else if (f.aggroTimer <= 0f && f.strafeRearmTimer <= 0f && sim != null) {
            Unit target = acquireStrafeTarget(f, sim);
            if (target != null) {
                f.burstTarget = target;
                f.burstRemaining = f.profile.burstSize;
                f.burstNextFireIn = 0f; // fire the first round next tick
            }
        }
    }

    /** Forward-cone search for an opposing-faction live unit, weighted by distance. */
    private Unit acquireStrafeTarget(Fighter f, BattleSimulation sim) {
        Faction enemy = (f.side == Faction.MARINE) ? Faction.DEFENDER : Faction.MARINE;
        float cosThreshold = (float) Math.cos(Math.toRadians(STRAFE_CONE_HALF_DEG));
        float dirLen = (float) Math.sqrt(f.vx * f.vx + f.vy * f.vy);
        if (dirLen < 0.0001f) return null;
        float dx = f.vx / dirLen, dy = f.vy / dirLen;

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
     * Emits one tracer with scatter, plays the fire sound, applies damage to
     * the held burst target. Damage applies instantly on fire — same convention
     * as {@link BattleSimulation#fireShot}; the visual tracer carries the read,
     * not the physics. A hit-glow particle pops at the target if damage lands.
     */
    private void fireOneTracer(Fighter f, BattleSimulation sim) {
        if (f.burstTarget == null) return;
        // Apply burst spread to the actual fire direction. Bigger spread =
        // shorter dwell + more "spray-and-pray" feel; small spread = surgical.
        float spreadRad = (float) Math.toRadians((rng.nextFloat() * 2f - 1f) * f.profile.burstSpreadDeg);

        float tx = f.burstTarget.renderX + 0.5f;
        float ty = f.burstTarget.renderY + 0.5f;
        float dx = tx - f.worldX;
        float dy = ty - f.worldY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float cos = (float) Math.cos(spreadRad), sin = (float) Math.sin(spreadRad);
        float ndx = (dx * cos - dy * sin) / len;
        float ndy = (dx * sin + dy * cos) / len;
        float endX = f.worldX + ndx * len;
        float endY = f.worldY + ndy * len;

        Tracer t = new Tracer();
        t.profile = f.profile;
        t.fromX = f.worldX;
        t.fromY = f.worldY;
        t.toX = endX;
        t.toY = endY;
        t.lifetimeRemaining = f.profile.tracerLifetime;
        tracers.add(t);

        // Muzzle flash at the fighter nose.
        Particle muzzle = new Particle();
        muzzle.x = f.worldX;
        muzzle.y = f.worldY;
        muzzle.lifetimeRemaining = MUZZLE_FLASH_LIFETIME;
        muzzle.lifetimeMax = MUZZLE_FLASH_LIFETIME;
        muzzle.radiusCells = 0.5f;
        muzzle.color = f.profile.tracerColor;
        particles.add(muzzle);

        // Apply damage + impact glow only if the round actually lands (spread=0 hits target cell-center).
        // Within burstSpreadDeg the target is "in the cone"; we apply damage to model the strafe pinning.
        if (sim != null) {
            sim.applyExternalDamage(f.burstTarget, f.profile.perTracerDamage);
            Particle impact = new Particle();
            impact.x = endX;
            impact.y = endY;
            impact.lifetimeRemaining = IMPACT_FLASH_LIFETIME;
            impact.lifetimeMax = IMPACT_FLASH_LIFETIME;
            impact.radiusCells = 0.6f;
            impact.color = f.profile.tracerColor;
            particles.add(impact);
        }

        // Audio — fire + a quieter impact tick so the strafe reads aurally.
        float pitch = f.profile.fireSoundPitch
                + (rng.nextFloat() * 2f - 1f) * SFX_PITCH_JITTER;
        Global.getSoundPlayer().playUISound(f.profile.fireSoundId, pitch, f.profile.fireSoundVolume);
    }

    /**
     * Pairwise opposing-fighter proximity check — both fighters get their aggro
     * timer refreshed when they're within {@link #DOGFIGHT_RADIUS}. O(n²) but
     * n ≤ {@link #MAX_CONCURRENT}, so cheap.
     */
    private void applyDogfightAggro() {
        for (int i = 0; i < fighters.size(); i++) {
            Fighter a = fighters.get(i);
            for (int j = i + 1; j < fighters.size(); j++) {
                Fighter b = fighters.get(j);
                if (a.side == b.side) continue;
                float dx = a.worldX - b.worldX;
                float dy = a.worldY - b.worldY;
                float distSq = dx * dx + dy * dy;
                if (distSq < DOGFIGHT_RADIUS * DOGFIGHT_RADIUS) {
                    a.aggroTimer = DOGFIGHT_AGGRO_REFRESH;
                    b.aggroTimer = DOGFIGHT_AGGRO_REFRESH;
                }
            }
        }
    }

    /**
     * Spawns a fighter at a random map edge with a velocity toward the opposing
     * edge. We pick the entry side, then derive the destination side as the
     * opposite, then jitter both endpoints within the grid for variety.
     */
    private void spawnFighter(BattleLayout layout) {
        int gridCellsW = (int) (layout.gridW / Math.max(0.001f, layout.cellSize));
        int gridCellsH = (int) (layout.gridH / Math.max(0.001f, layout.cellSize));
        if (gridCellsW <= 0 || gridCellsH <= 0) return;

        int side = rng.nextInt(4); // 0=top, 1=right, 2=bottom, 3=left
        float sx, sy, ex, ey;
        switch (side) {
            case 0:  // top → bottom
                sx = rng.nextFloat() * gridCellsW;       sy = gridCellsH + OFFMAP_PAD;
                ex = rng.nextFloat() * gridCellsW;       ey = -OFFMAP_PAD;
                break;
            case 1:  // right → left
                sx = gridCellsW + OFFMAP_PAD;            sy = rng.nextFloat() * gridCellsH;
                ex = -OFFMAP_PAD;                        ey = rng.nextFloat() * gridCellsH;
                break;
            case 2:  // bottom → top
                sx = rng.nextFloat() * gridCellsW;       sy = -OFFMAP_PAD;
                ex = rng.nextFloat() * gridCellsW;       ey = gridCellsH + OFFMAP_PAD;
                break;
            default: // left → right
                sx = -OFFMAP_PAD;                        sy = rng.nextFloat() * gridCellsH;
                ex = gridCellsW + OFFMAP_PAD;            ey = rng.nextFloat() * gridCellsH;
                break;
        }
        float dx = ex - sx, dy = ey - sy;
        float chord = (float) Math.sqrt(dx * dx + dy * dy);
        if (chord < 0.001f) return;
        float speed = SPEED_MIN + rng.nextFloat() * (SPEED_MAX - SPEED_MIN);

        Fighter f = new Fighter();
        f.profile = FighterProfile.values()[rng.nextInt(FighterProfile.values().length)];
        f.side = rng.nextBoolean() ? Faction.MARINE : Faction.DEFENDER;
        f.worldX = sx; f.worldY = sy;
        f.vx = dx / chord * speed;
        f.vy = dy / chord * speed;
        f.facingDeg = facingDeg(f.vx, f.vy);
        fighters.add(f);
    }

    private static boolean isOffMap(Fighter f, BattleLayout layout) {
        float gridCellsW = layout.gridW / Math.max(0.001f, layout.cellSize);
        float gridCellsH = layout.gridH / Math.max(0.001f, layout.cellSize);
        float pad = OFFMAP_PAD + 2f; // a touch of hysteresis past spawn pad
        return f.worldX < -pad || f.worldX > gridCellsW + pad
                || f.worldY < -pad || f.worldY > gridCellsH + pad;
    }

    /** Starsector-convention sprite facing: 0 = +Y (up), increasing CCW. */
    private static float facingDeg(float vx, float vy) {
        return (float) Math.toDegrees(Math.atan2(vy, vx)) - 90f;
    }

    // ---- Rendering ------------------------------------------------------------

    /**
     * Draws shadows → engine glows → fighters → tracers → impact flashes in
     * that order so additive bits land on top of the unit layer. Caller is
     * expected to render this AFTER ground units / shots / shuttles but
     * BEFORE the victory banner.
     */
    public void render(BattleLayout layout, float alphaMult) {
        if (layout == null) return;
        ensureSprites();

        // 1. Drop shadows — normal blend at low alpha, low on the stack so
        //    everything else punches over them.
        if (shadowSprite != null) {
            for (Fighter f : fighters) {
                drawShadow(f, layout, alphaMult);
            }
        }

        // 2. Engine glows — additive, behind the hull.
        if (engineSprite != null) {
            for (Fighter f : fighters) {
                drawEngineGlow(f, layout, alphaMult);
            }
        }

        // 3. Fighter hulls — per-profile shared sprite, rotated to facing.
        for (Fighter f : fighters) {
            SpriteAPI sprite = sprites.get(f.profile);
            if (sprite == null) continue;
            float pxLen = f.profile.visualLengthCells * layout.cellSize;
            // Preserve native aspect: width = pxLen * (w/h).
            float texW = sprite.getWidth();
            float texH = sprite.getHeight();
            float aspect = (texH > 0f) ? texW / texH : 1f;
            sprite.setSize(pxLen * aspect, pxLen);
            sprite.setAngle(f.facingDeg);
            sprite.setAlphaMult(alphaMult);
            sprite.setColor(Color.WHITE);
            sprite.setNormalBlend();
            float px = layout.gridX + f.worldX * layout.cellSize;
            float py = layout.gridY + f.worldY * layout.cellSize;
            sprite.renderAtCenter(px, py);
        }
        // Reset shared sprite state — singletons leak rotation otherwise.
        for (SpriteAPI s : sprites.values()) {
            if (s != null) s.setAngle(0f);
        }

        // 4. Tracers — additive colored quads, no texture. Lifetime drives alpha falloff.
        if (!tracers.isEmpty()) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            glBegin(GL_QUADS);
            for (Tracer t : tracers) {
                drawTracerQuad(t, layout, alphaMult);
            }
            glEnd();
            // Restore normal blend so we don't poison subsequent draws.
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }

        // 5. Particles (muzzle flashes + impacts) — additive glow sprites.
        if (glowSprite != null) {
            for (Particle p : particles) {
                drawParticle(p, layout, alphaMult);
            }
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
        float glowLenCells = f.profile.visualLengthCells * ENGINE_GLOW_LEN_MULT;
        // Position the glow behind the fighter relative to its velocity.
        float speed = (float) Math.sqrt(f.vx * f.vx + f.vy * f.vy);
        if (speed < 0.001f) return;
        float nx = f.vx / speed, ny = f.vy / speed;
        float backOffset = f.profile.visualLengthCells * 0.45f;
        float gx = f.worldX - nx * backOffset;
        float gy = f.worldY - ny * backOffset;
        engineSprite.setSize(glowLenCells * 0.6f * layout.cellSize, glowLenCells * layout.cellSize);
        engineSprite.setAngle(f.facingDeg);
        engineSprite.setAlphaMult(alphaMult * 0.9f);
        engineSprite.setColor(new Color(0xFF, 0xC8, 0x80));
        engineSprite.setAdditiveBlend();
        engineSprite.renderAtCenter(
                layout.gridX + gx * layout.cellSize,
                layout.gridY + gy * layout.cellSize);
        engineSprite.setAngle(0f);
    }

    /** Draws one tracer as a rotated rectangle in additive color. Lifetime ratio drives alpha. */
    private void drawTracerQuad(Tracer t, BattleLayout layout, float alphaMult) {
        float fromPx = layout.gridX + t.fromX * layout.cellSize;
        float fromPy = layout.gridY + t.fromY * layout.cellSize;
        float toPx   = layout.gridX + t.toX   * layout.cellSize;
        float toPy   = layout.gridY + t.toY   * layout.cellSize;

        // Tracer is drawn as a fixed-length segment from origin along the
        // direction to target, regardless of target distance — gives the
        // "muzzle-velocity round whipping out" read instead of stretching to
        // the impact point. Length & thickness scale with cellSize so the
        // tracer reads at any zoom.
        float dx = toPx - fromPx, dy = toPy - fromPy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001f) return;
        float nx = dx / dist, ny = dy / dist;
        float scale = layout.cellSize / 32f; // normalize sprite-px sizes to current zoom
        float tracerLen = t.profile.tracerPxLen * scale;
        float half = (t.profile.tracerPxThick * scale) * 0.5f;
        // Anchor the trailing end at the muzzle, head leads ahead toward target.
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

    private static final class Fighter {
        FighterProfile profile;
        Faction side;
        float worldX, worldY;
        float vx, vy;
        float facingDeg;
        /** Sim-seconds remaining of dogfight aggro — while >0, the fighter skips strafe attempts. */
        float aggroTimer;
        /** Sin phase for cosmetic wobble while aggro'd. */
        float wobblePhase;
        /** Active strafe burst — when >0, drives fireOneTracer on each tick. */
        int burstRemaining;
        float burstNextFireIn;
        Unit burstTarget;
        /** Cooldown between successive strafe attempts (set after a burst completes). */
        float strafeRearmTimer;
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
