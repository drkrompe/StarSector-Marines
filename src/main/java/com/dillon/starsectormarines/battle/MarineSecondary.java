package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.fx.ImpactProfile;

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
    /** Annihilator-pattern unguided rockets. Three tubes per marine, long range, huge anti-emplacement payload. */
    ROCKET_LAUNCHER("Annihilator Rocket Launcher",
                    "annihilator_fire",
                    "graphics/missiles/missile_annihilator.png",
                    "marines_explosion",
                    "graphics/battle/marine-rocket.png",
                    32f, 18f, 0.85f, 3.0f, 3.50f, 3, 0.50f, 0.70f, 0.65f);

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

    MarineSecondary(String displayName, String fireSoundId, String projectileSpritePath, String impactSoundId,
                    String aimSpritePath,
                    float range, float damage, float accuracy, float cooldown, float vsTurretMult,
                    int startingAmmo, float projectileVisualCells, float flightSec, float aimDuration) {
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
    }

    /** Secondary shots are full detonations — fire burst + smoke + explosion sound. Same recipe the heavy mortar uses. */
    public ImpactProfile impactProfile() { return ImpactProfile.HE; }
}
