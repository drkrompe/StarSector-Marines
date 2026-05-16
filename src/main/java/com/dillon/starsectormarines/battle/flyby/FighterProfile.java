package com.dillon.starsectormarines.battle.flyby;

import java.awt.Color;

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
 */
public enum FighterProfile {

    /** Talon — light autocannon, fast and twitchy. */
    TALON("graphics/ships/talon/talon.png", 1.5f,
            new Color(0xFF, 0xE0, 0x70), 22f, 2.5f, 0.06f,
            7, 0.06f, 0.8f, 1.0f,
            FlybyOverlay.SFX_GUN_LIGHT, 0.9f, 0.7f),

    /** Wasp — small drone with a pulse laser. */
    WASP("graphics/ships/wasp_ftr.png", 1.3f,
            new Color(0x88, 0xFF, 0xFF), 18f, 3.0f, 0.10f,
            4, 0.10f, 1.2f, 1.5f,
            FlybyOverlay.SFX_GUN_ENERGY, 1.1f, 0.6f),

    /** Broadsword — heavy fighter, dual chaingun. The strafe of choice. */
    BROADSWORD("graphics/ships/broadsword.png", 2.0f,
            new Color(0xFF, 0xE0, 0x70), 30f, 3.5f, 0.08f,
            10, 0.05f, 0.6f, 1.5f,
            FlybyOverlay.SFX_GUN_HEAVY, 1.0f, 0.8f),

    /** Thunder — interceptor with twin ion bolts. */
    THUNDER("graphics/ships/thunder.png", 1.7f,
            new Color(0x70, 0xC8, 0xFF), 24f, 3.0f, 0.10f,
            6, 0.08f, 0.9f, 1.4f,
            FlybyOverlay.SFX_GUN_ENERGY, 1.0f, 0.7f);

    /** Vanilla sprite path. Lazy-loaded once per overlay. */
    public final String spritePath;
    /** Drawn length in cells (sprite's longer axis). Smaller = "higher altitude"; we lean on shadow offset + tint for the rest. */
    public final float visualLengthCells;

    // ---- Tracer / burst tuning -------------------------------------------------
    /** Tracer color (and tinted muzzle flash). RGB; alpha is applied per-particle. */
    public final Color tracerColor;
    /** Tracer length in pixels at default cellSize. Scaled with cellSize at draw time. */
    public final float tracerPxLen;
    /** Tracer thickness in pixels. */
    public final float tracerPxThick;
    /** Tracer lifetime in seconds. Short = whippy; long = beam-like. */
    public final float tracerLifetime;

    /** Number of tracers per burst. */
    public final int burstSize;
    /** Sim-seconds between successive tracers within a single burst. */
    public final float burstInterval;
    /** Burst spread half-angle in degrees — random scatter per tracer. */
    public final float burstSpreadDeg;
    /** Damage applied per tracer that connects with its target. Tiny values; strafes shouldn't insta-kill. */
    public final float perTracerDamage;

    // ---- Audio -----------------------------------------------------------------
    /** Sound id (declared in mod/data/config/sounds.json) for one shot in the burst. */
    public final String fireSoundId;
    /** Pitch + volume for the fire sound. Pitch jittered ±5% at play time. */
    public final float fireSoundPitch;
    public final float fireSoundVolume;

    FighterProfile(String spritePath, float visualLengthCells,
                   Color tracerColor, float tracerPxLen, float tracerPxThick, float tracerLifetime,
                   int burstSize, float burstInterval, float burstSpreadDeg, float perTracerDamage,
                   String fireSoundId, float fireSoundPitch, float fireSoundVolume) {
        this.spritePath = spritePath;
        this.visualLengthCells = visualLengthCells;
        this.tracerColor = tracerColor;
        this.tracerPxLen = tracerPxLen;
        this.tracerPxThick = tracerPxThick;
        this.tracerLifetime = tracerLifetime;
        this.burstSize = burstSize;
        this.burstInterval = burstInterval;
        this.burstSpreadDeg = burstSpreadDeg;
        this.perTracerDamage = perTracerDamage;
        this.fireSoundId = fireSoundId;
        this.fireSoundPitch = fireSoundPitch;
        this.fireSoundVolume = fireSoundVolume;
    }
}
