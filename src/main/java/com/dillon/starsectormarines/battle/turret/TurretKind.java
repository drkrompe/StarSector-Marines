package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;

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
 *       a rotated sprite traveling from→to over the authored shot lifetime.</li>
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
     * with a small AoE at its physical stop. Ground-deployed rounds use the
     * shared resolver, so wide/high/low fire interacts with cover and walls.
     * Same shape as the shuttle-mounted {@link #HEAVY_MG} but tighter
     * spread, smaller burst, faster cycle.
     */
    VULCAN       ("graphics/weapons/vulcan_cannon_turret_base.png",
                  "graphics/weapons/vulcan_cannon_turret_recoil.png",
                  "graphics/missiles/shell_small_yellow.png",
                  "vulcan_cannon_fire",
                  "Vulcan Cannon",
                  22f, 10.8f, 0.45f,  1.40f, 60f, 120f, 1.6f, 0.22f, TurretRole.A2G, 120,
                  /*burst*/ 6, 0.08f, /*aoe*/ 0.6f, /*wallDmg*/ 3, /*wallDmgRadius*/ 0f,
                  /*arc*/ 0f, /*flightSec*/ 0.14f, /*hitSpread*/ 1.3f,
                  /*minRange*/ 0f, /*smokeTrail*/ false),
    /** Mid-range autocannon — balanced workhorse. */
    ARBALEST     ("graphics/weapons/arbalest_turret_base.png",
                  "graphics/weapons/arbalest_turret_recoil.png",
                  "graphics/missiles/shell_large_green.png",
                  "autocannon_fire",
                  "Arbalest Autocannon",
                  30f, 45.0f, 0.50f,  1.50f, 70f,  90f, 1.8f, 0.30f, TurretRole.A2G,  60),
    /** Long-range high-damage — sniper turret. Slow turn rate so flanking matters. */
    HEAVY_MORTAR ("graphics/weapons/heavy_mortar_turret.png",
                  "graphics/weapons/heavy_mortar_turret_recoil.png",
                  "graphics/missiles/shell_round_lrg.png",
                  "heavy_mortar_fire",
                  "Heavy Mortar",
                  36f, 81.0f, 0.55f,  2.50f, 80f,  60f, 1.8f, 0.32f, TurretRole.A2G,  15,
                  /*burst*/ 1, 0f, /*aoe*/ 1.35f, /*wallDmg*/ 24, /*wallDmgRadius*/ 1.15f,
                  /*arc*/ 0f, /*flightSec*/ 0.60f, /*hitSpread*/ 0.18f,
                  /*minRange*/ 0f, /*smokeTrail*/ false),
    /** Rapid-fire flak — defensive area sweep. Wide turn rate, fast cooldown. */
    DUAL_FLAK    ("graphics/weapons/double_flak_cannon_turret_base.png",
                  "graphics/weapons/double_flak_cannon_turret_recoil.png",
                  "graphics/missiles/shell_large_blue.png",
                  "flak_fire",
                  "Dual Flak Cannon",
                  26f, 36.0f, 0.40f,  0.80f, 75f, 100f, 2.0f, 0.28f, TurretRole.A2G,  80),
    /** Slow medium-range cannon — high-penetration direct contact plus a lower-penetration HE blast. */
    HEPHAESTUS   ("graphics/weapons/hephaestus_turret_base.png",
                  "graphics/weapons/hephaestus_turret_recoil.png",
                  "graphics/missiles/shell_hephag.png",
                  "hephaestus_fire",
                  "Hephaestus Heavy Cannon",
                  32f, 45.0f, 0.65f,  4.50f, 90f,  75f, 2.2f, 0.35f, TurretRole.A2G,  50,
                  /*burst*/ 1, 0f, /*aoe*/ 1.6f, /*wallDmg*/ 30, /*wallDmgRadius*/ 1.25f,
                  /*arc*/ 0f, /*flightSec*/ 0.55f, /*hitSpread*/ 0.14f,
                  /*minRange*/ 0f, /*smokeTrail*/ false),
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
                  28f, 36.0f, 0.55f,  4.00f, 75f,  80f, 1.7f, 0.55f, TurretRole.A2G,  60,
                  /*burst*/ 4, 0.18f, /*aoe*/ 1.5f, /*wallDmg*/ 30, /*wallDmgRadius*/ 1.5f,
                  /*arc*/ 2.5f, /*flightSec*/ 0.65f, /*hitSpread*/ 0.6f,
                  /*minRange*/ 5f, /*smokeTrail*/ true),
    /**
     * Long-range rocket battery — defender-side indirect-fire area denial.
     * Each trigger pull rips an 8-rocket salvo at the locked target; rounds
     * fly dumb (the vanilla Locust's smart-tracking is dropped here) with
     * moderate scatter, smoke-trail the whole way, then detonate with a
     * small AoE at the landing cell. Long cooldown between salvos so one
     * launcher can't sustain fire — they're meant to break a push, not
     * grind one. Range 100 covers half of a 200-cell Conquest map —
     * fortress + kill zone + port + city, but not the beach LZ.
     *
     * <p><b>Indirect fire</b>: {@link #indirectFire} = {@code true}, so the
     * turret keeps the target locked when LoS breaks (the kremlin wall
     * blocks LoS to anything in the kill zone). Accuracy uses quadratic
     * distance falloff <em>and</em> a {@link #noLosAccuracyMult} when the
     * shot is fired blind — see {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#fireShotFrom}.
     *
     * <p>Sprite ported from the base-game Locust SRM Launcher (ship weapon),
     * reused as a planetary defense emplacement; no {@code _recoil} variant
     * ships with the source asset, so the base sprite stands in for both.
     */
    LOCUST       ("graphics/weapons/locust_turret.png",
                  "graphics/weapons/locust_turret.png",
                  "graphics/missiles/missile_locust.png",
                  "swarmer_fire",
                  "Locust Rocket Battery",
                  100f, 45.0f, 0.25f,  10.00f, 85f,  50f, 2.0f, 0.45f, TurretRole.A2G,  30,
                  /*burst*/ 8, 0.08f, /*aoe*/ 1.4f, /*wallDmg*/ 20, /*wallDmgRadius*/ 1.4f,
                  /*arc*/ 3.5f, /*flightSec*/ 1.50f, /*hitSpread*/ 8.0f,
                  /*minRange*/ 30f, /*smokeTrail*/ true,
                  /*indirectFire*/ true, /*noLosAccuracyMult*/ 0.55f),
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
                  24f, 22.5f, 0.50f,  2.20f, 70f, 110f, 1.6f, 0.22f, TurretRole.A2G, 200,
                  /*burst*/ 10, 0.07f, /*aoe*/ 0.8f, /*wallDmg*/ 5, /*wallDmgRadius*/ 0f,
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
    /** Exposed structure before the emplacement goes down. */
    public final float maxStructure;
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
     * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#fireShotFrom}
     * queues a {@link com.dillon.starsectormarines.battle.combat.PendingDetonation}
     * at the projectile's endpoint instead of resolving damage at fire time.
     */
    public final float aoeRadius;
    /**
     * Wall HP knocked off per wall cell touched by this kind's detonation —
     * the per-weapon "penetration" knob. Walls are hardened structural targets
     * with their own HP; this is what a single hit chips off. Whether a hit
     * reaches a given wall is governed by {@link #wallDamageRadius}.
     * {@code 0} = non-structural (round doesn't touch walls at all).
     */
    public final int wallDamage;
    /**
     * Radius (in cells) over which {@link #wallDamage} is applied around the
     * detonation endpoint. {@code 0} = endpoint-only (round chips the literal
     * wall it lands on, if any — almost never hits anything since the endpoint
     * is a unit's cell, which is walkable). {@code > 0} = HE-crater behavior:
     * every wall cell within radius of the detonation takes {@link #wallDamage}.
     *
     * <p>Set on kinds that conceptually crater the area on detonation (HE
     * artillery: locust, grenade launcher). Kinetic-splash kinds (vulcan,
     * heavy MG) leave it {@code 0} — a stray autocannon round catching two
     * marines is the AoE intent, not a wall-flattener.
     */
    public final float wallDamageRadius;
    /**
     * Visual parabola peak in cells. {@code > 0} draws the projectile arcing
     * above the straight-line lerp; the sim's hit roll is unchanged. Used by
     * {@link #GRENADE_LAUNCHER} to read as a lobbed grenade.
     */
    public final float arcHeight;
    /**
     * Maximum-range presentation timing. Legacy aerial/indirect paths use it
     * directly; modeled ground direct fire derives {@link #directRoundVelocity()}
     * so a nearer stop arrives sooner. {@code 0} uses the tracer/resolver default.
     */
    public final float flightSec;
    /**
     * Nominal spread in cells. Modeled direct fire feeds it into target-plane
     * lateral/elevation sampling; legacy aerial/indirect fire uses it as
     * endpoint scatter. Misses expand beyond this nominal pattern.
     */
    public final float hitSpread;
    /** Minimum engagement range in cells. Targets closer than this aren't acquired or kept locked — keeps lobbed-AoE weapons from dropping on top of friendlies. {@code 0} = no minimum. */
    public final float minRange;
    /** When true, projectiles in flight emit a small gray smoke puff per render frame at their tail. Used by the grenade launcher; reads as "smokes its way to the target." */
    public final boolean smokeTrail;
    /**
     * When {@code true}, this kind can engage targets it has no direct line of
     * sight to — artillery / indirect-fire weapons. The aim loop keeps the
     * target locked when LoS breaks; the fire path applies a quadratic
     * range-falloff on accuracy ({@code 1 - (d/range)²}) and multiplies by
     * {@link #noLosAccuracyMult} on shots fired blind.
     *
     * <p>Direct-fire kinds leave this {@code false} — they retain the existing
     * "drop target on LoS loss" behavior and use flat per-kind accuracy.
     */
    public final boolean indirectFire;
    /**
     * Accuracy multiplier applied to shots fired without direct line of sight,
     * when {@link #indirectFire} is {@code true}. Ignored for direct-fire
     * kinds. Mirrors {@link com.dillon.starsectormarines.battle.mech.MechWeapon#LRM_NO_LOS_ACC_MULT}
     * — "battery knows roughly where the target is via squad spotting / data
     * link, but each rocket flies wider without a direct sightline."
     */
    public final float noLosAccuracyMult;

    TurretKind(String spritePath, String recoilSpritePath, String projectileSpritePath, String fireSoundId,
               String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxStructure, float turnRateDegPerSec, float visualCells, float projectileVisualCells,
               TurretRole role, int startingAmmo) {
        this(spritePath, recoilSpritePath, projectileSpritePath, fireSoundId, displayName,
                range, damage, accuracy, cooldown, maxStructure, turnRateDegPerSec, visualCells, projectileVisualCells,
                role, startingAmmo,
                /*burstCount*/ 1, /*burstSpacing*/ 0f, /*aoeRadius*/ 0f, /*wallDamage*/ 0, /*wallDamageRadius*/ 0f,
                /*arcHeight*/ 0f, /*flightSec*/ 0f, /*hitSpread*/ 0f, /*minRange*/ 0f,
                /*smokeTrail*/ false,
                /*indirectFire*/ false, /*noLosAccuracyMult*/ 1.0f);
    }

    TurretKind(String spritePath, String recoilSpritePath, String projectileSpritePath, String fireSoundId,
               String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxStructure, float turnRateDegPerSec, float visualCells, float projectileVisualCells,
               TurretRole role, int startingAmmo,
               int burstCount, float burstSpacing, float aoeRadius, int wallDamage, float wallDamageRadius,
               float arcHeight, float flightSec, float hitSpread, float minRange,
               boolean smokeTrail) {
        this(spritePath, recoilSpritePath, projectileSpritePath, fireSoundId, displayName,
                range, damage, accuracy, cooldown, maxStructure, turnRateDegPerSec, visualCells, projectileVisualCells,
                role, startingAmmo,
                burstCount, burstSpacing, aoeRadius, wallDamage, wallDamageRadius,
                arcHeight, flightSec, hitSpread, minRange, smokeTrail,
                /*indirectFire*/ false, /*noLosAccuracyMult*/ 1.0f);
    }

    TurretKind(String spritePath, String recoilSpritePath, String projectileSpritePath, String fireSoundId,
               String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxStructure, float turnRateDegPerSec, float visualCells, float projectileVisualCells,
               TurretRole role, int startingAmmo,
               int burstCount, float burstSpacing, float aoeRadius, int wallDamage, float wallDamageRadius,
               float arcHeight, float flightSec, float hitSpread, float minRange,
               boolean smokeTrail, boolean indirectFire, float noLosAccuracyMult) {
        this.spritePath = spritePath;
        this.recoilSpritePath = recoilSpritePath;
        this.projectileSpritePath = projectileSpritePath;
        this.fireSoundId = fireSoundId;
        this.displayName = displayName;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.maxStructure = maxStructure;
        this.turnRateDegPerSec = turnRateDegPerSec;
        this.visualCells = visualCells;
        this.projectileVisualCells = projectileVisualCells;
        this.role = role;
        this.startingAmmo = startingAmmo;
        this.burstCount = burstCount;
        this.burstSpacing = burstSpacing;
        this.aoeRadius = aoeRadius;
        this.wallDamage = wallDamage;
        this.wallDamageRadius = wallDamageRadius;
        this.arcHeight = arcHeight;
        this.flightSec = flightSec;
        this.hitSpread = hitSpread;
        this.minRange = minRange;
        this.smokeTrail = smokeTrail;
        this.indirectFire = indirectFire;
        this.noLosAccuracyMult = noLosAccuracyMult;
    }

    /**
     * Projectile velocity in cells per sim-second. {@code > 0} flips the kind
     * onto the simulated-{@link com.dillon.starsectormarines.battle.combat.Projectile}
     * path — flight time becomes {@code dist / cellsPerSec} (close shots arrive
     * sooner than far ones, matching real rockets), and the shot is a real
     * entity in {@code BattleSimulation.activeProjectiles} that point defense
     * can later target. {@code 0} means the shot remains a tracer/event rather
     * than an independent projectile entity; ground direct-fire callers may
     * still derive their resolver velocity from {@link #flightSec}.
     *
     * <p>Tuning: LOCUST 70 (1.4s at max range 100; close shots arrive fast).
     * GRENADE_LAUNCHER 45 (lobbed shells, similar to legacy 0.65s @ 28-cell range).
     */
    public float cellsPerSec() {
        switch (this) {
            case LOCUST:           return 70f;
            case GRENADE_LAUNCHER: return 45f;
            default:               return 0f;
        }
    }

    /**
     * Modeled ground direct-fire velocity in cells/sec. Burst kinds retain
     * their pre-S4 maximum-range visual timing; zero-timing single-shot kinds
     * use the shared resolver default.
     */
    public float directRoundVelocity() {
        if (!(flightSec > 0f)) return 60f;
        return range / flightSec;
    }

    /** Actor-armor penetration for this transitional turret weapon catalog. */
    public float penetration() {
        return switch (this) {
            case VULCAN -> 3f;
            case ARBALEST -> 8f;
            case HEAVY_MORTAR -> 10f;
            case HEPHAESTUS -> 4f;
            case DUAL_FLAK -> 5f;
            case GRENADE_LAUNCHER -> 6f;
            case LOCUST -> 14f;
            case HEAVY_MG -> 4f;
        };
    }

    /**
     * Damage delivered only to the actor physically contacted by the round.
     * Zero means the weapon has no contact payload distinct from its ordinary
     * direct or area damage.
     */
    public float contactDamage() {
        return this == HEPHAESTUS ? 117f : 0f;
    }

    /** Armor penetration delivered only by {@link #contactDamage()}. */
    public float contactPenetration() {
        return this == HEPHAESTUS ? 24f : 0f;
    }

    /** Strongest penetration the targeting compatibility layer may consider. */
    public float targetAffinityPenetration() {
        return Math.max(penetration(), contactPenetration());
    }

    public float armorPool() {
        return switch (this) {
            case VULCAN -> 80f;
            case ARBALEST -> 110f;
            case HEAVY_MORTAR -> 130f;
            case DUAL_FLAK, GRENADE_LAUNCHER -> 120f;
            case HEPHAESTUS -> 145f;
            case LOCUST -> 135f;
            case HEAVY_MG -> 110f;
        };
    }

    public float armorRating() {
        return switch (this) {
            case VULCAN -> 8f;
            case ARBALEST, DUAL_FLAK, GRENADE_LAUNCHER -> 10f;
            case HEAVY_MORTAR, LOCUST -> 12f;
            case HEPHAESTUS -> 14f;
            case HEAVY_MG -> 10f;
        };
    }

    /**
     * True for rocket-class kinds whose projectile accelerates from rest —
     * applies the {@link com.dillon.starsectormarines.battle.combat.Projectile#applyBoostCurve}
     * boost-then-cruise visual curve in flight. False for chemical-charge
     * shells (grenades, mortars) which exit the tube already at terminal
     * velocity and travel at constant speed.
     */
    public boolean hasBoostRamp() {
        return this == LOCUST;
    }

    /**
     * True for kinds whose firing emits a SAM-site-style backblast plume —
     * smoke billows out the back of the launcher (opposite firing direction)
     * as the missile leaves the tube. Mirrors the vanilla locust
     * {@code .wpn}'s {@code smokeSpec.blowback*} fields. Renderer hooks this
     * during the fire-time FX pass in
     * {@code BattleScreen.spawnImpactFx}.
     */
    public boolean hasLaunchBackblast() {
        return this == LOCUST;
    }

    /** Visual impact profile for this kind — small spark for light weapons, kinetic flash for autocannons, rocket HE for launchers, and heavy cannon HE for the mortar/cannon pair. */
    public ImpactProfile impactProfile() {
        switch (this) {
            case GRENADE_LAUNCHER:
            case LOCUST:                             return ImpactProfile.HE;
            case HEAVY_MORTAR:
            case HEPHAESTUS:                         return ImpactProfile.CANNON_HE;
            case ARBALEST:
            case DUAL_FLAK:
            case HEAVY_MG:                           return ImpactProfile.KINETIC;
            case VULCAN:
            default:                                 return ImpactProfile.RIFLE;
        }
    }
}
