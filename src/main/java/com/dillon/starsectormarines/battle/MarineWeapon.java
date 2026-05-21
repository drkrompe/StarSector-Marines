package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.turret.MapTurret;

import java.awt.Color;

/**
 * Primary handheld weapon catalog for marines. Each entry maps to a vanilla
 * Starsector weapon for free art / audio, but the stats are ground-combat
 * tuned (cells, not space-combat pixels). Loadout is per-marine, assigned at
 * deboard time in {@link BattleSimulation#tryDeboardMarine} via
 * {@link MarineLoadout#primary}.
 *
 * <p>Primaries render as colored line tracers (pulse-laser-style), not full
 * projectile sprites — vanilla pulse lasers have no bullet sprite anyway, and
 * a 35-pixel kinetic shell rendered at our cell scale would be invisible. The
 * tracer color and per-weapon fire sound do the visual differentiation work.
 *
 * <p>The {@link #vsTurretMult} multiplier is applied in
 * {@link BattleSimulation#fireShot} when the target is a {@link MapTurret} —
 * rifles plink emplacements at 0.3×, dedicated AT weapons land near 1.0×.
 */
public enum MarineWeapon {
    /**
     * Default marine primary — vanilla pulse laser flavor, 3-round burst
     * (Halo BR-style tap-tap-tap). Sits between the single-shot DMR and the
     * full-auto SMG to form a clean DMR / BR / AR triad.
     *
     * <p>Per-round damage 1.0 (max burst total 3.0); cooldown 1.0s between
     * trigger pulls; burst spacing 0.09s for a crisp three-flash cadence
     * just slower than the SMG's brrt. Tracer-style — at 0.15s tracer
     * lifetime + 0.09s spacing the three flashes briefly overlap, which
     * reads as a burst without needing a projectile sprite.
     *
     * <p>Mild range falloff (0.30 → 0.245 effective accuracy at d=24) and a
     * small 0.4-cell spread keep the BR the "always useful" baseline — not
     * as punishing at range as the SMG, not as crisp as the DMR. Each burst
     * rolls accuracy independently per round, so {@code P(any hit)} at long
     * range is well above the single-shot baseline.
     */
    PULSE_RIFLE("Pulse Rifle",
                "pulse_laser_fire",
                new Color(0x80, 0xFF, 0x80),
                24f, 1.0f, 0.35f, 1.0f, 0.30f,
                ImpactProfile.RIFLE,
                3, 0.09f, null, 0f, 0f,
                0.30f, 0.4f),
    /**
     * Close-range area-suppression — fast 3-round bursts of small bullet
     * sprites. Vanilla light MG. Per-shot damage is lighter (0.7) so a full
     * burst lands ~2.1, trading raw per-burst damage for the saturation
     * pattern and the snappier 0.07s cadence.
     *
     * <p>Heavy range falloff (0.60: 0.30 → 0.12 effective accuracy at d=16)
     * plus a wide 1.4-cell spread saturate the area near max range — the
     * design read is "devastating at door-breach distance, just noise at the
     * far end of the cone."
     */
    SMG        ("Light Machine Gun",
                "light_machinegun_fire",
                new Color(0xFF, 0xE8, 0xC0),
                16f, 0.7f, 0.30f, 0.50f, 0.30f,
                ImpactProfile.RIFLE,
                3, 0.07f, "graphics/missiles/shell_small_yellow.png", 0.15f, 0.10f,
                0.60f, 1.4f),
    /**
     * Long-range marksman rifle — heavier hit, slower cycle, mild AT bonus.
     * Vanilla railgun. Single shot, line tracer for now.
     *
     * <p>Minimal range falloff (0.10: 0.55 → 0.495 at d=32) and a tiny
     * 0.15-cell spread mean range is the DMR's whole point — the curve stays
     * almost flat across the band where rifles and SMGs are dropping off
     * hard.
     */
    DMR        ("Railgun",
                "railgun_fire",
                new Color(0xC8, 0xC8, 0xFF),
                32f, 4.0f, 0.55f, 1.80f, 0.40f,
                ImpactProfile.KINETIC,
                1, 0f, null, 0f, 0f,
                0.10f, 0.15f),
    /**
     * Drone-mounted pulse laser — built-in armament for the autonomous
     * defender drones launched from a {@link com.dillon.starsectormarines.battle.DroneHubUnit}.
     * Light 2-round burst with a cyan tracer (visually reads as overhead
     * laser fire). Lower damage and shorter range than the marine PULSE_RIFLE
     * — drones are a screen, not a heavy hitter; sustained drone fire whittles
     * marines down rather than dropping them in one engagement.
     *
     * <p>The {@code Marine} prefix on this enum is a naming wart for now —
     * drones aren't marines, but the firing pipeline is shared, and adding a
     * parallel {@code DroneWeapon} enum just to host one entry isn't worth
     * the duplication. Will be revisited when there's a second non-marine
     * weapon to host.
     */
    DRONE_PULSE("Drone Pulse Laser",
                "pulse_laser_fire",
                new Color(0x60, 0xCF, 0xFF),
                26f, 0.8f, 0.40f, 1.0f, 0.30f,
                ImpactProfile.RIFLE,
                2, 0.10f, null, 0f, 0f,
                0.35f, 0.5f);

    public final String displayName;
    /** Vanilla fire sound id ({@code fireSoundTwo} from the source {@code .wpn}); mono, pre-registered by the core install. */
    public final String fireSoundId;
    /** Line-tracer color. Distinct per weapon so the player can pick out which marine is firing which gun at a glance. */
    public final Color tracerColor;
    public final float range;
    public final float damage;
    public final float accuracy;
    public final float cooldown;
    /** Multiplier on damage when the target is a {@link MapTurret}. Rifles 0.3×, DMRs 0.4× — fine against infantry, anemic against emplacements. */
    public final float vsTurretMult;
    /** Visual character of the impact at endpoint. RIFLE for the small-arms entries; the DMR sits in KINETIC since a railgun strike should kick more material than a pulse-laser bolt. */
    public final ImpactProfile impactProfile;
    /** Rounds per fire decision. 1 = single shot (default). &gt;1 = burst: the AI fires the first round and {@code InfantryWeapons.tick} emits the remainder at {@link #burstSpacing} intervals. SMG = 3. */
    public final int burstCount;
    /** Sim-seconds between burst rounds. Ignored when {@link #burstCount} == 1. */
    public final float burstSpacing;
    /** Optional projectile sprite. When non-null, shots render as a rotated traveling sprite (like turret kinetics + the rocket) instead of a line tracer. Used by SMG to launch visible bullets. */
    public final String projectileSpritePath;
    /** Projectile sprite visual size in cells (long axis). Ignored when {@link #projectileSpritePath} is null. */
    public final float projectileVisualCells;
    /** Sim-seconds the projectile is visible in flight. Ignored when {@link #projectileSpritePath} is null. */
    public final float flightSec;
    /**
     * Fraction of base {@link #accuracy} lost at {@link #range} cells.
     * 0 = no falloff (legacy flat behavior), 0.5 = halve accuracy at max
     * range. Applied via {@link com.dillon.starsectormarines.battle.weapons.RangeFalloff#accuracy}
     * in {@code InfantryWeapons.fireShot} — compounds multiplicatively with
     * the {@code FireStance} multiplier so a moving marine at long range is
     * doubly inaccurate.
     */
    public final float accuracyFalloff;
    /**
     * Lateral scatter radius in cells at {@link #range}, scaling linearly
     * with distance via {@link com.dillon.starsectormarines.battle.weapons.RangeFalloff#spread}.
     * Applied to both hit-endpoint jitter (the round still hits the locked
     * target for damage purposes, but the tracer endpoint scatters
     * visually) and to miss-scatter as an additive on top of the baseline
     * near-miss ring. Mirrors {@link MechWeapon#hitSpread}.
     */
    public final float hitSpread;

    MarineWeapon(String displayName, String fireSoundId, Color tracerColor,
                 float range, float damage, float accuracy, float cooldown, float vsTurretMult,
                 ImpactProfile impactProfile,
                 int burstCount, float burstSpacing, String projectileSpritePath,
                 float projectileVisualCells, float flightSec,
                 float accuracyFalloff, float hitSpread) {
        this.displayName = displayName;
        this.fireSoundId = fireSoundId;
        this.tracerColor = tracerColor;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.vsTurretMult = vsTurretMult;
        this.impactProfile = impactProfile;
        this.burstCount = burstCount;
        this.burstSpacing = burstSpacing;
        this.projectileSpritePath = projectileSpritePath;
        this.projectileVisualCells = projectileVisualCells;
        this.flightSec = flightSec;
        this.accuracyFalloff = accuracyFalloff;
        this.hitSpread = hitSpread;
    }
}
