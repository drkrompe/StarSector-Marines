package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.fx.ImpactProfile;

/**
 * Catalog of static ground-defense turrets, each backed by a vanilla Starsector
 * weapon sprite. The mod surfaces a curated subset of {@code graphics/weapons/}
 * — top-down ship-mount art doubles as bunker-mounted planetary defense at the
 * ground combat scale.
 *
 * <p>Stats live here, not on a CSV — the vanilla {@code weapon_data.csv} numbers
 * are tuned for space combat (1000+ unit ranges, hull damage of hundreds), so we
 * pick our own ground-scale balance. The sprite is the reusable bit.
 *
 * <p>FX paths are vanilla:
 * <ul>
 *   <li>{@code spritePath} / {@code recoilSpritePath} — the {@code _base} +
 *       {@code _recoil} pair that ship every turret weapon. We swap to recoil
 *       briefly after each shot to read as a muzzle flash.</li>
 *   <li>{@code projectileSpritePath} — the {@code bulletSprite} from each
 *       weapon's {@code .proj} file in {@code data/weapons/proj/}. Rendered as
 *       a rotated sprite traveling from→to over {@code SHOT_LIFETIME}.</li>
 *   <li>{@code fireSoundId} — the {@code fireSoundTwo} id from each weapon's
 *       {@code .wpn} file. These ids are pre-registered in the core install's
 *       {@code sounds.json}; we can play them directly without our own
 *       declarations.</li>
 * </ul>
 *
 * <p>Sprite barrel points UP (+Y) in the source PNG so
 * {@link com.fs.starfarer.api.graphics.SpriteAPI#setAngle setAngle(0)} reads as
 * north-facing — matches our shuttle convention.
 */
public enum TurretKind {
    /**
     * Light rapid-fire — anti-personnel area suppression. Rips a 6-round
     * burst at the locked target with wide scatter; each round detonates
     * with a small AoE at its landing cell. Ground-deployed only — rounds
     * raycast against walls so a stray spread can't reach marines behind
     * cover. Same shape as the shuttle-mounted {@link #HEAVY_MG} but tighter
     * spread, smaller burst, faster cycle.
     */
    VULCAN       ("graphics/weapons/vulcan_cannon_turret_base.png",
                  "graphics/weapons/vulcan_cannon_turret_recoil.png",
                  "graphics/missiles/shell_small_yellow.png",
                  "vulcan_cannon_fire",
                  "Vulcan Cannon",
                  22f, 1.2f, 0.45f,  1.40f, 50f, 120f, 1.6f, 0.22f, TurretRole.A2G, 120,
                  /*burst*/ 6, 0.08f, /*aoe*/ 0.6f, /*wallDmg*/ 3,
                  /*arc*/ 0f, /*flightSec*/ 0.14f, /*hitSpread*/ 1.3f,
                  /*minRange*/ 0f, /*smokeTrail*/ false, /*raycastShots*/ true),
    /** Mid-range autocannon — balanced workhorse. */
    ARBALEST     ("graphics/weapons/arbalest_turret_base.png",
                  "graphics/weapons/arbalest_turret_recoil.png",
                  "graphics/missiles/shell_large_green.png",
                  "autocannon_fire",
                  "Arbalest Autocannon",
                  30f,  5.0f, 0.50f,  1.50f, 65f,  90f, 1.8f, 0.30f, TurretRole.A2G,  60),
    /** Long-range high-damage — sniper turret. Slow turn rate so flanking matters. */
    HEAVY_MORTAR ("graphics/weapons/heavy_mortar_turret.png",
                  "graphics/weapons/heavy_mortar_turret_recoil.png",
                  "graphics/missiles/shell_round_lrg.png",
                  "heavy_mortar_fire",
                  "Heavy Mortar",
                  36f,  9.0f, 0.55f,  2.50f, 75f,  60f, 1.8f, 0.28f, TurretRole.A2G,  15),
    /** Rapid-fire flak — defensive area sweep. Wide turn rate, fast cooldown. */
    DUAL_FLAK    ("graphics/weapons/dual_flak_cannon_turret_base.png",
                  "graphics/weapons/dual_flak_cannon_turret_recoil.png",
                  "graphics/missiles/shell_large_blue.png",
                  "flak_fire",
                  "Dual Flak Cannon",
                  26f,  4.0f, 0.40f,  0.80f, 70f, 100f, 2.0f, 0.28f, TurretRole.A2G,  80),
    /** Heavy assault — high DPS at medium range. */
    HEPHAESTUS   ("graphics/weapons/hephaestus_turret_base.png",
                  "graphics/weapons/hephaestus_turret_recoil.png",
                  "graphics/missiles/shell_hephag.png",
                  "hephaestus_fire",
                  "Hephaestus Assault Gun",
                  32f,  6.5f, 0.50f,  1.20f, 85f,  75f, 2.2f, 0.35f, TurretRole.A2G,  50),
    /**
     * Burst-fire grenade launcher — shuttle-mounted indirect-fire pod that lobs
     * a 4-round salvo of arc'd grenades with a smoke trail, then waits out a
     * cooldown before the next burst. Each round detonates with a small AoE
     * and chips wall HP, so a sustained burst on a building flattens it across
     * a few salvos. Minimum-range gate keeps the launcher from dropping grenades
     * on top of the shuttle's own LZ when the squad pushes in close.
     */
    GRENADE_LAUNCHER ("graphics/weapons/light_mortar_turret_base.png",
                  "graphics/weapons/light_mortar_turret_recoil.png",
                  "graphics/missiles/mortar_round.png",
                  "light_mortar_fire",
                  "Grenade Launcher",
                  28f,  4.0f, 0.55f,  4.00f, 70f,  80f, 1.7f, 0.55f, TurretRole.A2G,  60,
                  /*burst*/ 4, 0.18f, /*aoe*/ 1.5f, /*wallDmg*/ 30,
                  /*arc*/ 2.5f, /*flightSec*/ 0.65f, /*hitSpread*/ 0.6f,
                  /*minRange*/ 5f, /*smokeTrail*/ true),
    /**
     * Heavy MG — wide-spread suppression. Each trigger pull rips a long
     * tracer burst toward the lock; rounds scatter across a wide pattern
     * and detonate with a small AoE at their landing cell, so a stray round
     * landing between two marines clips both. No arc, no smoke trail — this
     * is direct-fire area saturation, not artillery.
     */
    HEAVY_MG     ("graphics/weapons/vulcan_cannon_turret_base.png",
                  "graphics/weapons/vulcan_cannon_turret_recoil.png",
                  "graphics/missiles/shell_small_yellow.png",
                  "autocannon_fire",
                  "Heavy MG",
                  24f,  2.5f, 0.50f,  2.20f, 65f, 110f, 1.6f, 0.22f, TurretRole.A2G, 200,
                  /*burst*/ 10, 0.07f, /*aoe*/ 0.8f, /*wallDmg*/ 5,
                  /*arc*/ 0f, /*flightSec*/ 0.18f, /*hitSpread*/ 2.0f,
                  /*minRange*/ 3f, /*smokeTrail*/ false);

    public final String spritePath;
    /** Base sprite swap shown for {@code RECOIL_DURATION} after each shot — the muzzle-flash variant that ships next to the base sprite in {@code graphics/weapons/}. */
    public final String recoilSpritePath;
    /** Bullet sprite from this weapon's vanilla {@code .proj} file. Rendered rotated along the travel vector. */
    public final String projectileSpritePath;
    /** Vanilla fire sound id ({@code fireSoundTwo} field in the {@code .wpn}). Pre-registered by the core install (and mono, like all vanilla weapon SFX) — playable via positional {@code playSound} without our own sounds.json entry. */
    public final String fireSoundId;
    public final String displayName;
    /** Engagement range in cells. */
    public final float range;
    /** Damage per shot before cover reduction (cover at the target cell still applies via {@link BattleSimulation#fireShot}). */
    public final float damage;
    /** Per-shot hit chance. Mounted turrets sit higher than handheld marines, so accuracy reads higher across the board. */
    public final float accuracy;
    /** Sim-seconds between shots. */
    public final float cooldown;
    /** HP before the mount goes down. */
    public final float maxHp;
    /** How fast the turret can rotate, in degrees per sim-second. Slower turrets reward flanking. */
    public final float turnRateDegPerSec;
    /** Visual sprite size in cells (long axis). Aspect comes from the loaded PNG. Slightly larger than 1 cell so the turret reads as a real emplacement, not a floor decal. */
    public final float visualCells;
    /** Projectile visual size in cells (long axis). Aspect comes from the loaded PNG. Tuned per kind so a vulcan reads as a small zipping round and a mortar as a fat shell. */
    public final float projectileVisualCells;
    /** Target class this kind is allowed to shoot at. Static {@link MapTurret}s default to {@link TurretRole#A2G}; mounted shuttle turrets honor the role for target filtering. */
    public final TurretRole role;
    /**
     * Rounds in the magazine when this kind is mounted on a shuttle. Static
     * {@link MapTurret}s ignore this — bolted-down defenses don't run dry —
     * but a shuttle turret tracks ammo down and triggers the hover-loiter
     * exit when its mounts go empty across the board.
     */
    public final int startingAmmo;

    /**
     * Rounds per trigger pull. {@code 1} = single shot (every existing turret);
     * {@code >1} = burst — the mount fires {@code burstCount} rounds at
     * {@link #burstSpacing} sim-second intervals, then enters {@link #cooldown}.
     */
    public final int burstCount;
    /** Sim-seconds between rounds within a burst. Ignored when {@link #burstCount} == 1. */
    public final float burstSpacing;
    /**
     * Splash radius in cells. {@code > 0} swings the kind onto the AoE path —
     * {@link com.dillon.starsectormarines.battle.BattleSimulation#fireShotFrom}
     * queues a {@link com.dillon.starsectormarines.battle.PendingDetonation}
     * at the projectile's endpoint instead of resolving damage at fire time.
     */
    public final float aoeRadius;
    /** Wall HP knocked off the endpoint cell on detonation. {@code 0} = non-structural. */
    public final int wallDamage;
    /**
     * Visual parabola peak in cells. {@code > 0} draws the projectile arcing
     * above the straight-line lerp; the sim's hit roll is unchanged. Used by
     * {@link #GRENADE_LAUNCHER} to read as a lobbed grenade.
     */
    public final float arcHeight;
    /**
     * Sim-seconds the projectile is visible in flight. Drives both the
     * {@link com.dillon.starsectormarines.battle.ShotEvent} lifetime and the
     * paired {@link com.dillon.starsectormarines.battle.PendingDetonation}
     * timer so the explosion lines up with the projectile arriving. {@code 0}
     * falls back to the legacy {@code SHOT_LIFETIME} tracer flash.
     */
    public final float flightSec;
    /**
     * Endpoint scatter on a hit, in cells. The hit/miss roll still resolves
     * against the locked target; this just nudges WHERE the impact lands so a
     * burst of grenades sprays the cell cluster instead of stacking on one.
     */
    public final float hitSpread;
    /** Minimum engagement range in cells. Targets closer than this aren't acquired or kept locked — keeps lobbed-AoE weapons from dropping on top of friendlies. {@code 0} = no minimum. */
    public final float minRange;
    /** When true, projectiles in flight emit a small gray smoke puff per render frame at their tail. Used by the grenade launcher; reads as "smokes its way to the target." */
    public final boolean smokeTrail;
    /**
     * When {@code true}, each scattered round raycasts from origin to endpoint
     * through the nav grid; if a wall sits in the path, the endpoint snaps to
     * that wall cell (the round "hits" the wall instead of passing through).
     * Used by ground-deployed area-spread weapons so wide scatter can't pepper
     * units behind cover. Air-mounted variants leave this off — they're
     * elevated above the buildings they fire over.
     */
    public final boolean raycastShots;

    TurretKind(String spritePath, String recoilSpritePath, String projectileSpritePath, String fireSoundId,
               String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxHp, float turnRateDegPerSec, float visualCells, float projectileVisualCells,
               TurretRole role, int startingAmmo) {
        this(spritePath, recoilSpritePath, projectileSpritePath, fireSoundId, displayName,
                range, damage, accuracy, cooldown, maxHp, turnRateDegPerSec, visualCells, projectileVisualCells,
                role, startingAmmo,
                /*burstCount*/ 1, /*burstSpacing*/ 0f, /*aoeRadius*/ 0f, /*wallDamage*/ 0,
                /*arcHeight*/ 0f, /*flightSec*/ 0f, /*hitSpread*/ 0f, /*minRange*/ 0f,
                /*smokeTrail*/ false, /*raycastShots*/ false);
    }

    TurretKind(String spritePath, String recoilSpritePath, String projectileSpritePath, String fireSoundId,
               String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxHp, float turnRateDegPerSec, float visualCells, float projectileVisualCells,
               TurretRole role, int startingAmmo,
               int burstCount, float burstSpacing, float aoeRadius, int wallDamage,
               float arcHeight, float flightSec, float hitSpread, float minRange,
               boolean smokeTrail) {
        this(spritePath, recoilSpritePath, projectileSpritePath, fireSoundId, displayName,
                range, damage, accuracy, cooldown, maxHp, turnRateDegPerSec, visualCells, projectileVisualCells,
                role, startingAmmo,
                burstCount, burstSpacing, aoeRadius, wallDamage,
                arcHeight, flightSec, hitSpread, minRange, smokeTrail,
                /*raycastShots*/ false);
    }

    TurretKind(String spritePath, String recoilSpritePath, String projectileSpritePath, String fireSoundId,
               String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxHp, float turnRateDegPerSec, float visualCells, float projectileVisualCells,
               TurretRole role, int startingAmmo,
               int burstCount, float burstSpacing, float aoeRadius, int wallDamage,
               float arcHeight, float flightSec, float hitSpread, float minRange,
               boolean smokeTrail, boolean raycastShots) {
        this.spritePath = spritePath;
        this.recoilSpritePath = recoilSpritePath;
        this.projectileSpritePath = projectileSpritePath;
        this.fireSoundId = fireSoundId;
        this.displayName = displayName;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.maxHp = maxHp;
        this.turnRateDegPerSec = turnRateDegPerSec;
        this.visualCells = visualCells;
        this.projectileVisualCells = projectileVisualCells;
        this.role = role;
        this.startingAmmo = startingAmmo;
        this.burstCount = burstCount;
        this.burstSpacing = burstSpacing;
        this.aoeRadius = aoeRadius;
        this.wallDamage = wallDamage;
        this.arcHeight = arcHeight;
        this.flightSec = flightSec;
        this.hitSpread = hitSpread;
        this.minRange = minRange;
        this.smokeTrail = smokeTrail;
        this.raycastShots = raycastShots;
    }

    /** Visual impact profile for this kind — small spark for the fast/light weapons, kinetic flash + smoke for the mid-weight shells, full HE burst for the mortar. */
    public ImpactProfile impactProfile() {
        switch (this) {
            case HEAVY_MORTAR:
            case GRENADE_LAUNCHER:                   return ImpactProfile.HE;
            case ARBALEST:
            case DUAL_FLAK:
            case HEPHAESTUS:
            case HEAVY_MG:                           return ImpactProfile.KINETIC;
            case VULCAN:
            default:                                 return ImpactProfile.RIFLE;
        }
    }
}
