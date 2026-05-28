package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.combat.Projectile;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.turret.MapTurret;

/**
 * Optional second weapon a marine can carry on top of their {@link MarineWeapon}.
 * Limited ammo (rocket launchers don't get topped up mid-mission), big single-
 * shot damage, heavy multiplier against hardened targets. AI in
 * {@link com.dillon.starsectormarines.battle.ai.CombatantBehavior} prefers the
 * secondary against {@link MapTurret} targets while ammo remains, and falls
 * back to primary fire once the tube is dry.
 *
 * <p>Secondary shots render with real projectile sprites + HE impact (mirroring
 * the heavy mortar pipeline), since a rocket flying visibly is more readable
 * than a colored line.
 */
public enum MarineSecondary {
    /** Annihilator-pattern unguided rockets. Three tubes per marine, long range, huge anti-emplacement payload. Splashes 1.5 cells on detonation (friendly fire ON) and chews ~50 HP of wall per impact within the same 1.5-cell radius — a rocket lands in the open and still cracks walls in the splash, two hits drop a standard wall. */
    ROCKET_LAUNCHER("Annihilator Rocket Launcher",
                    "annihilator_fire",
                    "graphics/missiles/missile_annihilator.png",
                    "marines_explosion",
                    "graphics/battle/marine-rocket.png",
                    32f, 18f, 0.85f, 3.0f, 3.50f, 3, 0.50f, 0.70f, 0.65f,
                    1.5f, 50, 1.5f);

    public final String displayName;
    /** Vanilla fire sound id from the source {@code .wpn}. */
    public final String fireSoundId;
    /** Vanilla projectile sprite. Rendered rotated along the travel vector during flight. */
    public final String projectileSpritePath;
    /** Sound id played at impact arrival — the pooled mod explosion clip for the rocket's detonation. */
    public final String impactSoundId;
    /** Mod sprite sheet (7-frame WNES + weapon-up convention, same as the regular marine sheets) drawn for the marine while they're inside the {@link #aimDuration} window of this weapon. */
    public final String aimSpritePath;
    public final float range;
    public final float damage;
    public final float accuracy;
    public final float cooldown;
    /** Multiplier on damage when the target is a {@link MapTurret}. Rockets land near 3.5× — destroys a Vulcan in one hit, brings down a Hephaestus in two. */
    public final float vsTurretMult;
    /** Tubes loaded at mission start. Once exhausted the marine reverts to primary fire. */
    public final int startingAmmo;
    /** Projectile visual size in cells (long axis). Aspect comes from the loaded PNG. */
    public final float projectileVisualCells;
    /** Sim-seconds the projectile spends in flight (and visible) before reaching its endpoint. Longer than the default {@code SHOT_LIFETIME} so a rocket reads as a slow heavy munition rather than a flash. */
    public final float flightSec;
    /** Sim-seconds the marine is frozen in the aim pose before the shot launches. The actual fire happens at the midpoint of this window — first half is the aim-up, second half is the launcher held out as the rocket departs. */
    public final float aimDuration;
    /** Splash radius in cells on detonation. Every unit within this radius of the impact endpoint takes {@link #damage}, modified by cover. Friendly fire is ON — the squad pays the price for clustering near a rocket impact. */
    public final float aoeRadius;
    /**
     * Wall HP knocked off per wall cell touched by the rocket's detonation —
     * the rocket's penetration value against walls (which are hardened
     * structural targets with their own HP). Walls are 100 HP default
     * (150 in fortified bases); 50/hit means two rockets drop a standard
     * wall, three drop a fortified one.
     */
    public final int wallDamage;
    /**
     * Radius (in cells) over which {@link #wallDamage} is applied around the
     * detonation endpoint. Set to {@link #aoeRadius} so the rocket cracks
     * every wall cell the blast reaches — matches the player intuition that
     * an HE rocket landing in the open between two buildings damages both
     * walls, not just the dirt at the impact point.
     */
    public final float wallDamageRadius;

    MarineSecondary(String displayName, String fireSoundId, String projectileSpritePath, String impactSoundId,
                    String aimSpritePath,
                    float range, float damage, float accuracy, float cooldown, float vsTurretMult,
                    int startingAmmo, float projectileVisualCells, float flightSec, float aimDuration,
                    float aoeRadius, int wallDamage, float wallDamageRadius) {
        this.displayName = displayName;
        this.fireSoundId = fireSoundId;
        this.projectileSpritePath = projectileSpritePath;
        this.impactSoundId = impactSoundId;
        this.aimSpritePath = aimSpritePath;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.vsTurretMult = vsTurretMult;
        this.startingAmmo = startingAmmo;
        this.projectileVisualCells = projectileVisualCells;
        this.flightSec = flightSec;
        this.aimDuration = aimDuration;
        this.aoeRadius = aoeRadius;
        this.wallDamage = wallDamage;
        this.wallDamageRadius = wallDamageRadius;
    }

    /** Secondary shots are full detonations — fire burst + smoke + explosion sound. Same recipe the heavy mortar uses. */
    public ImpactProfile impactProfile() { return ImpactProfile.HE; }
}
