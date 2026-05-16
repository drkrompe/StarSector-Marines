package com.dillon.starsectormarines.battle;

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
 * <p>Sprite path convention is {@code graphics/weapons/<weapon>_turret_base.png}
 * for everything except a handful (mortars, locust) that ship without the
 * {@code _base} suffix. Sprite barrel points UP (+Y) in the source PNG so
 * {@link com.fs.starfarer.api.graphics.SpriteAPI#setAngle setAngle(0)} reads as
 * north-facing — matches our shuttle convention.
 */
public enum TurretKind {
    /** Light rapid-fire — anti-personnel suppression. Short range, low damage per shot, fast cooldown. */
    VULCAN       ("graphics/weapons/vulcan_cannon_turret_base.png",  "Vulcan Cannon",
                  24f,  2.5f, 0.45f,  0.50f, 50f, 120f, 1.6f),
    /** Mid-range autocannon — balanced workhorse. */
    ARBALEST     ("graphics/weapons/arbalest_turret_base.png",       "Arbalest Autocannon",
                  30f,  5.0f, 0.50f,  1.50f, 65f,  90f, 1.8f),
    /** Long-range high-damage — sniper turret. Slow turn rate so flanking matters. */
    HEAVY_MORTAR ("graphics/weapons/heavy_mortar_turret.png",        "Heavy Mortar",
                  36f,  9.0f, 0.55f,  2.50f, 75f,  60f, 1.8f),
    /** Rapid-fire flak — defensive area sweep. Wide turn rate, fast cooldown. */
    DUAL_FLAK    ("graphics/weapons/dual_flak_cannon_turret_base.png","Dual Flak Cannon",
                  26f,  4.0f, 0.40f,  0.80f, 70f, 100f, 2.0f),
    /** Heavy assault — high DPS at medium range. */
    HEPHAESTUS   ("graphics/weapons/hephaestus_turret_base.png",     "Hephaestus Assault Gun",
                  32f,  6.5f, 0.50f,  1.20f, 85f,  75f, 2.2f);

    public final String spritePath;
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

    TurretKind(String spritePath, String displayName,
               float range, float damage, float accuracy, float cooldown,
               float maxHp, float turnRateDegPerSec, float visualCells) {
        this.spritePath = spritePath;
        this.displayName = displayName;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.maxHp = maxHp;
        this.turnRateDegPerSec = turnRateDegPerSec;
        this.visualCells = visualCells;
    }
}
