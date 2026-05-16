package com.dillon.starsectormarines.battle.flyby;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/**
 * Per-fighter visual + audio loadout for the flyby overlay. All sprite paths
 * resolve against the vanilla install — Starsector's resource loader walks core
 * + enabled mods, so {@code "graphics/ships/wasp_ftr.png"} from a mod jar pulls
 * the core file. No redistribution required.
 *
 * <p>Tracer / burst params encode the "feel" of each fighter's primary armament
 * — broadswords spray a long chaingun burst of yellow shells, thunders snap off
 * a couple of cyan energy bolts. The numbers don't have to match vanilla DPS;
 * they just have to read distinctly per fighter type.
 *
 * <p>{@link #weaponClass} picks the fire-resolution path. TRACER profiles use
 * the tracer / burst block. PROJECTILE profiles use the projectile block
 * (homing speed, AoE radius, fuse) — the tracer fields are unused for them.
 */
public enum FighterProfile {

    /** Talon — light autocannon, fast and twitchy. */
    TALON("graphics/ships/talon/talon.png", 1.5f,
            WeaponClass.TRACER,
            new Color(0xFF, 0xE0, 0x70), 22f, 2.5f, 0.06f,
            7, 0.06f, 0.8f, 1.0f, 30, 0.09f,
            0f, 0f, 0f, 0f, 0f,
            FlybyOverlay.SFX_GUN_LIGHT, 0.9f, 1.0f, null),

    /** Wasp — small drone with a pulse laser. */
    WASP("graphics/ships/wasp_ftr.png", 1.3f,
            WeaponClass.TRACER,
            new Color(0x88, 0xFF, 0xFF), 18f, 3.0f, 0.10f,
            4, 0.10f, 1.2f, 1.5f, 35, 0.09f,
            0f, 0f, 0f, 0f, 0f,
            FlybyOverlay.SFX_GUN_ENERGY, 1.1f, 0.9f, null),

    /** Broadsword — heavy fighter, dual chaingun. The strafe of choice. */
    BROADSWORD("graphics/ships/broadsword.png", 2.0f,
            WeaponClass.TRACER,
            new Color(0xFF, 0xE0, 0x70), 30f, 3.5f, 0.08f,
            10, 0.05f, 0.6f, 1.5f, 45, 0.09f,
            0f, 0f, 0f, 0f, 0f,
            FlybyOverlay.SFX_GUN_HEAVY, 1.0f, 1.1f, null),

    /** Thunder — interceptor with twin ion bolts. */
    THUNDER("graphics/ships/thunder.png", 1.7f,
            WeaponClass.TRACER,
            new Color(0x70, 0xC8, 0xFF), 24f, 3.0f, 0.10f,
            6, 0.08f, 0.9f, 1.4f, 90, 0.09f,
            0f, 0f, 0f, 0f, 0f,
            FlybyOverlay.SFX_GUN_ENERGY, 1.0f, 1.0f, null),

    /** Dagger — Tri-Tachyon torpedo bomber; one Reaper per shot. AoE detonation flattens walls and chews into clusters. */
    DAGGER("graphics/ships/dagger_trp.png", 1.9f,
            WeaponClass.PROJECTILE,
            new Color(0xFF, 0xB0, 0x60), 0f, 0f, 0f,
            1, 0f, 0f, 0f, 150, 0.7f,
            14f, 90f, 4.0f, 3.0f, 5.0f,
            FlybyOverlay.SFX_MISSILE_LAUNCH, 1.0f, 1.1f, "graphics/missiles/missile_harpoon.png");

    /** Vanilla sprite path. Lazy-loaded once per overlay. */
    public final String spritePath;
    /** Drawn length in cells (sprite's longer axis). Smaller = "higher altitude"; we lean on shadow offset + tint for the rest. */
    public final float visualLengthCells;

    /** Delivery model — picks which fire-resolution path the overlay takes when this profile fires. */
    public final WeaponClass weaponClass;

    // ---- Tracer / burst tuning (TRACER class) ----------------------------------
    /** Tracer color (and tinted muzzle flash). RGB; alpha is applied per-particle. PROJECTILE profiles still use this for the engine-trail / detonation tint. */
    public final Color tracerColor;
    /** Tracer length in pixels at default cellSize. Scaled with cellSize at draw time. */
    public final float tracerPxLen;
    /** Tracer thickness in pixels. */
    public final float tracerPxThick;
    /** Tracer lifetime in seconds. Short = whippy; long = beam-like. */
    public final float tracerLifetime;

    /** Number of shots per burst (tracers for TRACER, missiles for PROJECTILE). PROJECTILE usually fires 1 per commit. */
    public final int burstSize;
    /** Sim-seconds between successive shots within a single burst. Ignored when {@link #burstSize} is 1. */
    public final float burstInterval;
    /** Burst spread half-angle in degrees — random scatter per tracer. */
    public final float burstSpreadDeg;
    /** Damage applied per tracer that connects with its target. Tiny values; strafes shouldn't insta-kill. */
    public final float perTracerDamage;
    /** Wall HP a single tracer chips off when its endpoint lands on a wall. Wall HP = 100 (UrbanMapGenerator.WALL_HP_DEFAULT); higher values = fewer hits to flatten. Ballistic light = chips, heavy = ~3 hits, hi-tech energy/missile = one- or two-shot. PROJECTILE wall damage is applied per cell inside the AoE on detonation. */
    public final int wallDamage;
    /** Sim-seconds between RUN-phase shots. 0.09 for chainguns / autocannons; 0.6+ for missile bombers — missiles aren't sprayed. */
    public final float runFireInterval;

    // ---- Projectile tuning (PROJECTILE class) ----------------------------------
    /** Launch speed in cells/sec. 0 for TRACER profiles. */
    public final float projectileSpeed;
    /** Homing turn rate in degrees/sec — how tight the missile can lock onto a moving target. Lower = easier for a fast unit to dodge. */
    public final float projectileTurnRateDegPerSec;
    /** Fuse in sim-seconds — auto-detonate at current position if the missile hasn't impacted within this. */
    public final float projectileFuseSec;
    /** AoE radius in cells for detonation damage. */
    public final float projectileAoeRadiusCells;
    /** Damage applied to each opposing unit inside the AoE on detonation. */
    public final float projectileAoeDamage;

    // ---- Audio -----------------------------------------------------------------
    /** Sound id (declared in mod/data/config/sounds.json) for one shot in the burst. */
    public final String fireSoundId;
    /** Pitch + volume for the fire sound. Pitch jittered ±5% at play time. */
    public final float fireSoundPitch;
    public final float fireSoundVolume;

    // ---- Projectile sprite (PROJECTILE class only; null for TRACER) ------------
    /** Sprite path for the in-flight missile body. null for TRACER profiles. */
    public final String projectileSpritePath;

    FighterProfile(String spritePath, float visualLengthCells,
                   WeaponClass weaponClass,
                   Color tracerColor, float tracerPxLen, float tracerPxThick, float tracerLifetime,
                   int burstSize, float burstInterval, float burstSpreadDeg, float perTracerDamage, int wallDamage,
                   float runFireInterval,
                   float projectileSpeed, float projectileTurnRateDegPerSec, float projectileFuseSec,
                   float projectileAoeRadiusCells, float projectileAoeDamage,
                   String fireSoundId, float fireSoundPitch, float fireSoundVolume,
                   String projectileSpritePath) {
        this.spritePath = spritePath;
        this.visualLengthCells = visualLengthCells;
        this.weaponClass = weaponClass;
        this.tracerColor = tracerColor;
        this.tracerPxLen = tracerPxLen;
        this.tracerPxThick = tracerPxThick;
        this.tracerLifetime = tracerLifetime;
        this.burstSize = burstSize;
        this.burstInterval = burstInterval;
        this.burstSpreadDeg = burstSpreadDeg;
        this.perTracerDamage = perTracerDamage;
        this.wallDamage = wallDamage;
        this.runFireInterval = runFireInterval;
        this.projectileSpeed = projectileSpeed;
        this.projectileTurnRateDegPerSec = projectileTurnRateDegPerSec;
        this.projectileFuseSec = projectileFuseSec;
        this.projectileAoeRadiusCells = projectileAoeRadiusCells;
        this.projectileAoeDamage = projectileAoeDamage;
        this.fireSoundId = fireSoundId;
        this.fireSoundPitch = fireSoundPitch;
        this.fireSoundVolume = fireSoundVolume;
        this.projectileSpritePath = projectileSpritePath;
    }

    // ---- Faction → profile pool ----------------------------------------------

    private static final List<FighterProfile> LOWTECH  = Arrays.asList(BROADSWORD, TALON, DAGGER);
    private static final List<FighterProfile> HIGHTECH = Arrays.asList(WASP, THUNDER, DAGGER);
    private static final List<FighterProfile> MIDLINE  = Arrays.asList(TALON, BROADSWORD, THUNDER, DAGGER);
    private static final List<FighterProfile> MIXED    = Arrays.asList(values()); // all profiles

    /**
     * Returns the fighter profiles a given faction would plausibly field, used
     * by mission generation to pick faction-appropriate wings. Mapping leans on
     * vanilla aesthetic — Hegemony / Luddic / Pirates run ballistic kit;
     * Tri-Tachyon / Remnant run energy; everyone else gets a mid-line mix.
     * Unknown faction ids fall back to the full pool so modded factions still
     * get some support. Dagger (torpedo bomber) shows up in every pool — every
     * faction fields some flavor of missile boat, and the AoE keeps it from
     * being lost in a swarm of chaingun fighters.
     */
    public static List<FighterProfile> poolForFaction(String factionId) {
        if (factionId == null) return MIXED;
        switch (factionId) {
            case "hegemony":
            case "luddic_church":
            case "luddic_path":
            case "knights_of_ludd":
            case "pirates":
                return LOWTECH;
            case "tritachyon":
            case "remnant":
                return HIGHTECH;
            case "persean":
            case "diktat":
            case "independent":
                return MIDLINE;
            default:
                return MIXED;
        }
    }
}
