package com.dillon.starsectormarines.battle.fx;

import com.dillon.starsectormarines.ops.battleview.BattleCamera;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ground-combat impact FX engine. Owns a particle list, lazy-loads the shared
 * smoke / fire / alpha-glow sprites, and exposes high-level spawn helpers
 * keyed by weapon type. {@link com.dillon.starsectormarines.ops.BattleScreen}
 * holds one instance per screen lifetime.
 *
 * <p>Parallel to the particle subsystem inside
 * {@code com.dillon.starsectormarines.battle.flyby.FlybyOverlay} — same sheets,
 * same flipbook UV math, separate state. Splitting keeps the working flyby FX
 * untouched while we iterate on ground impacts; if the two grow into one
 * shared engine later, the rendering math here is the obvious extraction
 * point.
 *
 * <p>Recipes per weapon class:
 * <ul>
 *   <li>Rifle (null kind, marines / militia / aliens) — one small spark,
 *       one tiny dust puff. Fast, brief, no smoke.</li>
 *   <li>Vulcan — same shape as rifle but slightly punchier; still no smoke.</li>
 *   <li>Arbalest / Dual Flak / Hephaestus (kinetic shells) — spark + a small
 *       smoke puff. Reads as a real impact, not a flash.</li>
 *   <li>Heavy Mortar (HE) — a fire burst plus 2-3 smoke puffs. Reads as a
 *       detonation, and the caller layers an explosion clip on top.</li>
 * </ul>
 */
public final class ImpactFx {

    private static final Logger LOG = Global.getLogger(ImpactFx.class);

    /** Mod-shipped 4×4 sheet of 16px frames: top 2 rows = smoke (8 frames), bottom 2 rows = fire (8 frames). Same asset FlybyOverlay uses. */
    private static final String SPRITE_PARTICLE_SHEET = "graphics/particle/smokeAndFire.png";
    /** Soft radial alpha — sparks + glow flashes. Same alpha-only texture FlybyOverlay uses for muzzle/impact flashes. */
    private static final String SPRITE_GLOW           = "graphics/fx/particlealpha64linear.png";

    private static final int PARTICLE_SHEET_COLS = 4;
    private static final int SMOKE_FIRST_FRAME   = 0;
    private static final int SMOKE_FRAME_COUNT   = 8;
    private static final int FIRE_FIRST_FRAME    = 8;
    private static final int FIRE_FRAME_COUNT    = 8;

    /** Light kick-up of dust on floor impacts; muted yellow-brown. */
    private static final Color FLOOR_DUST_COLOR  = new Color(0xB4, 0xA0, 0x70);
    /** Cooler chip dust on wall impacts; reads as concrete / metal flake. */
    private static final Color WALL_CHIP_COLOR   = new Color(0xA0, 0xA0, 0xA0);
    /** Hot, bright spark — close to white with a yellow lean. */
    private static final Color SPARK_COLOR       = new Color(0xFF, 0xE0, 0x80);
    /** Tracer-yellow hot puff used as a small additive flash at the impact point on every kinetic round. */
    private static final Color KINETIC_FLASH_COLOR = new Color(0xFF, 0xC8, 0x60);

    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();
    private SpriteAPI particleSheetSprite;
    private SpriteAPI glowSprite;
    private boolean spritesLoadAttempted;

    /** Lazy-load both shared sheets. Safe to call repeatedly; only attempts loads on the first call. Failed loads log + are tolerated — affected spawn paths no-op when their sprite is null. */
    public void ensureSprites() {
        if (spritesLoadAttempted) return;
        spritesLoadAttempted = true;
        particleSheetSprite = loadSpriteOrNull(SPRITE_PARTICLE_SHEET);
        glowSprite          = loadSpriteOrNull(SPRITE_GLOW);
    }

    /** Advances every particle by {@code dt} sim-seconds and drops expired entries. Reverse iteration for in-place removal. */
    public void advance(float dt) {
        if (dt <= 0f) return;
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.lifetimeRemaining -= dt;
            if (p.lifetimeRemaining <= 0f) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.radiusCells += p.radiusGrowthPerSec * dt;
        }
    }

    /** Draws every live particle. Iteration order = spawn order, so later spawns layer on top of earlier ones. */
    public void render(BattleCamera camera, float alphaMult) {
        if (particles.isEmpty() || camera == null) return;
        float cellPx = camera.cellPxSize();
        for (Particle p : particles) {
            drawParticle(p, camera, cellPx, alphaMult);
        }
    }

    /**
     * Routes the impact to the right particle recipe based on its visual
     * profile. Callers map their weapon catalog (turret kinds, marine
     * primaries, marine secondaries) onto a profile so the FX engine doesn't
     * need to know about every source enum.
     */
    public void spawnImpact(ImpactProfile profile, float x, float y, boolean isWall) {
        if (profile == null) {
            spawnRifleImpact(x, y, isWall);
            return;
        }
        switch (profile) {
            case KINETIC:  spawnKineticImpact(x, y, isWall); break;
            case HE:       spawnHeImpact(x, y, isWall); break;
            case RIFLE:
            default:       spawnRifleImpact(x, y, isWall); break;
        }
    }

    // ---- Recipes --------------------------------------------------------------

    /** Small spark + tiny dust puff. Rifle / vulcan profile — should read as quick and incidental. */
    private void spawnRifleImpact(float x, float y, boolean isWall) {
        spawnSparkFlash(x, y, 0.28f, 0.10f, SPARK_COLOR);
        spawnDust(x, y, isWall, 0.22f, 0.18f);
    }

    /** Punchier flash + a small smoke puff so the impact lingers a beat. */
    private void spawnKineticImpact(float x, float y, boolean isWall) {
        spawnSparkFlash(x, y, 0.42f, 0.14f, KINETIC_FLASH_COLOR);
        spawnDust(x, y, isWall, 0.32f, 0.28f);
        spawnSmokePuff(x, y, 0.35f, 0.70f);
    }

    /** Fire burst + 2-3 smoke puffs. The HE detonation read. Caller pairs this with an explosion clip. */
    private void spawnHeImpact(float x, float y, boolean isWall) {
        spawnSparkFlash(x, y, 0.70f, 0.16f, SPARK_COLOR);
        spawnFireBurst(x, y, 0.55f, 0.45f);
        int puffs = 2 + rng.nextInt(2);
        for (int i = 0; i < puffs; i++) {
            float jx = x + (rng.nextFloat() * 2f - 1f) * 0.45f;
            float jy = y + (rng.nextFloat() * 2f - 1f) * 0.45f;
            spawnSmokePuff(jx, jy, 0.55f + rng.nextFloat() * 0.25f, 1.10f + rng.nextFloat() * 0.4f);
        }
        spawnDust(x, y, isWall, 0.55f, 0.32f);
    }

    // ---- Primitive spawn helpers ---------------------------------------------

    /** Bright additive flash using the radial alpha sprite. Parked, brief, no growth. */
    private void spawnSparkFlash(float x, float y, float radiusCells, float lifetime, Color color) {
        if (glowSprite == null) return;
        Particle p = new Particle();
        p.x = x; p.y = y;
        p.lifetimeRemaining = lifetime;
        p.lifetimeMax = lifetime;
        p.radiusCells = radiusCells;
        p.color = color;
        p.sprite = glowSprite;
        p.additive = true;
        particles.add(p);
    }

    /** Small dust puff using the radial alpha sprite. Slight outward growth, normal-alpha so it occludes rather than glows. */
    private void spawnDust(float x, float y, boolean isWall, float radiusCells, float lifetime) {
        if (glowSprite == null) return;
        Particle p = new Particle();
        p.x = x; p.y = y;
        p.lifetimeRemaining = lifetime;
        p.lifetimeMax = lifetime;
        p.radiusCells = radiusCells;
        p.radiusGrowthPerSec = 0.4f;
        p.color = isWall ? WALL_CHIP_COLOR : FLOOR_DUST_COLOR;
        p.sprite = glowSprite;
        p.additive = false;
        particles.add(p);
    }

    /**
     * Spawns a single smoke puff at (x, y). For driving continuous emitters
     * (smoking wrecks, smoldering rubble) where the caller picks the cadence
     * and the puff size. Lifetime is fixed at a value that pairs naturally
     * with the puff radius — bigger smoke takes longer to dissipate.
     */
    public void spawnAmbientSmoke(float x, float y, float radiusCells) {
        spawnSmokePuff(x, y, radiusCells, 0.9f + radiusCells * 0.8f);
    }

    /**
     * Single fire burst at (x, y) for continuous wreck-burn emitters. Shorter
     * lifetime than {@link #spawnAmbientSmoke} because fire reads as a more
     * dynamic, flickering presence than smoke.
     */
    public void spawnAmbientFire(float x, float y, float radiusCells) {
        spawnFireBurst(x, y, radiusCells, 0.5f + radiusCells * 0.4f);
    }

    /**
     * Emits a small additive glow particle as a rocket-engine puff at (x, y).
     * Drives the lit-engine trail behind in-flight rockets — callers spawn
     * one per render frame at the rocket's tail position so successive
     * particles overlap into a fading streak. Short lifetime keeps the trail
     * tight; hot-orange tint sells it as rocket exhaust.
     */
    public void spawnEngineTrail(float x, float y, float radiusCells) {
        spawnSparkFlash(x, y, radiusCells, 0.25f, ENGINE_TRAIL_COLOR);
    }

    /**
     * Emits a small gray smoke puff at (x, y) for a ballistic round in flight.
     * Like {@link #spawnEngineTrail} but normal-blend gray instead of additive
     * orange — reads as gunpowder smoke trailing a lobbed grenade, not engine
     * exhaust. Callers spawn one per render frame at the round's tail; short
     * lifetime + slight drift gives the streak a "tighter than wreck smoke"
     * read while still dispersing realistically.
     */
    public void spawnSmokeTrail(float x, float y, float radiusCells) {
        if (glowSprite == null) return;
        Particle p = new Particle();
        p.x = x; p.y = y;
        // A gentle drift downward off the moving round so the trail hangs
        // behind it rather than tracking with the projectile.
        p.vx = (rng.nextFloat() * 2f - 1f) * 0.15f;
        p.vy = (rng.nextFloat() * 2f - 1f) * 0.15f;
        p.radiusGrowthPerSec = 0.30f;
        p.lifetimeRemaining = 0.45f;
        p.lifetimeMax = 0.45f;
        p.radiusCells = radiusCells;
        p.color = SMOKE_TRAIL_COLOR;
        p.sprite = glowSprite;
        p.additive = false;
        particles.add(p);
    }

    /** Smoke-trail tint — mid gray with a slight warm cast so it reads as gunpowder smoke against the ground palette. */
    private static final Color SMOKE_TRAIL_COLOR = new Color(0xB0, 0xA8, 0xA0);

    /**
     * Bright muzzle-flash variant exposed publicly so the battle renderer
     * can pin a flash at the firing unit's position when a chaingun round
     * goes out. Same primitive as the internal spark-flash recipe — a hot
     * additive glow with no growth — but the cell radius is bigger so the
     * flash reads at a unit's muzzle rather than at an impact point.
     */
    public void spawnMuzzleFlash(float x, float y, float radiusCells, float lifetime) {
        spawnSparkFlash(x, y, radiusCells, lifetime, SPARK_COLOR);
    }

    /** Engine-trail tint — hot orange so it reads as exhaust against the muted ground palette. */
    private static final Color ENGINE_TRAIL_COLOR = new Color(0xFF, 0x90, 0x40);

    /** Smoke puff from the flipbook sheet. Light upward drift, random rotation, normal-alpha. Tuned smaller than the FlybyOverlay variant since ground impacts are point events, not aerial bursts. */
    private void spawnSmokePuff(float x, float y, float radiusCells, float lifetime) {
        if (particleSheetSprite == null) return;
        Particle p = new Particle();
        p.x = x; p.y = y;
        p.vx = (rng.nextFloat() * 2f - 1f) * 0.25f;
        p.vy = 0.35f + rng.nextFloat() * 0.35f;
        p.radiusGrowthPerSec = 0.35f + rng.nextFloat() * 0.25f;
        p.lifetimeRemaining = lifetime;
        p.lifetimeMax = lifetime;
        p.radiusCells = radiusCells;
        p.color = new Color(0xC8, 0xC8, 0xC8);
        p.sprite = particleSheetSprite;
        p.firstFrame = SMOKE_FIRST_FRAME;
        p.frameCount = SMOKE_FRAME_COUNT;
        p.additive = false;
        p.angleDeg = rng.nextFloat() * 360f;
        particles.add(p);
    }

    /** Fire burst from the flipbook sheet. Small upward kick, additive blend so it reads as hot incandescence. */
    private void spawnFireBurst(float x, float y, float radiusCells, float lifetime) {
        if (particleSheetSprite == null) return;
        Particle p = new Particle();
        p.x = x; p.y = y;
        p.vx = (rng.nextFloat() * 2f - 1f) * 0.3f;
        p.vy = 0.15f + rng.nextFloat() * 0.25f;
        p.radiusGrowthPerSec = 0.20f + rng.nextFloat() * 0.20f;
        p.lifetimeRemaining = lifetime;
        p.lifetimeMax = lifetime;
        p.radiusCells = radiusCells;
        p.color = Color.WHITE;
        p.sprite = particleSheetSprite;
        p.firstFrame = FIRE_FIRST_FRAME;
        p.frameCount = FIRE_FRAME_COUNT;
        p.additive = true;
        p.angleDeg = rng.nextFloat() * 360f;
        particles.add(p);
    }

    // ---- Rendering ------------------------------------------------------------

    private void drawParticle(Particle p, BattleCamera camera, float cellPx, float alphaMult) {
        float lifeFrac = Math.max(0f, p.lifetimeRemaining / Math.max(0.001f, p.lifetimeMax));
        float radiusPx = p.radiusCells * cellPx;
        SpriteAPI s = p.sprite;
        if (s == null) return;
        s.setSize(radiusPx * 2f, radiusPx * 2f);
        s.setAngle(p.angleDeg);
        s.setAlphaMult(alphaMult * lifeFrac);
        s.setColor(p.color);
        if (p.additive) s.setAdditiveBlend(); else s.setNormalBlend();
        if (p.frameCount > 0) {
            int playFrame = (int) ((1f - lifeFrac) * p.frameCount);
            if (playFrame >= p.frameCount) playFrame = p.frameCount - 1;
            int frameIdx = p.firstFrame + playFrame;
            // 4 rows in the smokeAndFire sheet (smoke top 2, fire bottom 2).
            int rows = (FIRE_FIRST_FRAME + FIRE_FRAME_COUNT) / PARTICLE_SHEET_COLS;
            float cellW = s.getTextureWidth() / PARTICLE_SHEET_COLS;
            float cellH = s.getTextureHeight() / rows;
            int col = frameIdx % PARTICLE_SHEET_COLS;
            int row = frameIdx / PARTICLE_SHEET_COLS;
            s.setTexX(col * cellW);
            s.setTexY(row * cellH);
            s.setTexWidth(cellW);
            s.setTexHeight(cellH);
        }
        s.renderAtCenter(camera.cellToScreenX(p.x), camera.cellToScreenY(p.y));
        // Reset UVs on the shared sprite so the next particle that uses it
        // doesn't inherit a frame index. Cheap — four setters.
        if (p.frameCount > 0) {
            s.setTexX(0f);
            s.setTexY(0f);
            s.setTexWidth(s.getTextureWidth());
            s.setTexHeight(s.getTextureHeight());
        }
    }

    private static SpriteAPI loadSpriteOrNull(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("ImpactFx: getSprite returned null for " + path);
                return null;
            }
            LOG.info("ImpactFx: loaded " + path);
            return sprite;
        } catch (Exception e) {
            LOG.error("ImpactFx: failed to load " + path, e);
            return null;
        }
    }
}
