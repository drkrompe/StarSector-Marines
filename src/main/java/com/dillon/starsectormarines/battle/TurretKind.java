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
    /** Light rapid-fire — anti-personnel suppression. Short range, low damage per shot, fast cooldown. */
    VULCAN       ("graphics/weapons/vulcan_cannon_turret_base.png",
                  "graphics/weapons/vulcan_cannon_turret_recoil.png",
                  "graphics/missiles/shell_small_yellow.png",
                  "vulcan_cannon_fire",
                  "Vulcan Cannon",
                  24f, 2.5f, 0.45f,  0.50f, 50f, 120f, 1.6f, 0.22f, TurretRole.A2G, 120),
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
                  32f,  6.5f, 0.50f,  1.20f, 85f,  75f, 2.2f, 0.35f, TurretRole.A2G,  50);

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

    TurretKind(String spritePath, String recoilSpritePath, String projectileSpritePath, String fireSoundId,
               String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxHp, float turnRateDegPerSec, float visualCells, float projectileVisualCells,
               TurretRole role, int startingAmmo) {
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
    }

    /** Visual impact profile for this kind — small spark for the fast/light weapons, kinetic flash + smoke for the mid-weight shells, full HE burst for the mortar. */
    public ImpactProfile impactProfile() {
        switch (this) {
            case HEAVY_MORTAR:                       return ImpactProfile.HE;
            case ARBALEST:
            case DUAL_FLAK:
            case HEPHAESTUS:                         return ImpactProfile.KINETIC;
            case VULCAN:
            default:                                 return ImpactProfile.RIFLE;
        }
    }
}
