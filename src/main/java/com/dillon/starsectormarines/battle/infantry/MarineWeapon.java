package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.awt.Color;

/**
 * Primary handheld weapon catalog for marines. Each entry maps to a vanilla
 * Starsector weapon for free art / audio, but the stats are ground-combat
 * tuned (cells, not space-combat pixels). This enum is the weapon FAMILY:
 * firing pattern, projectile presentation, and baseline stats. Per-instance
 * {@link EquipmentGrade} and {@link SoldierProfile} modifiers are resolved
 * when the combatant is seeded, so tier variants do not duplicate enum values
 * or require different layered sprites. Loadout is per-marine, assigned at
 * deboard time via {@link MarineLoadout#primary}.
 *
 * <p><b>This enum is an id handle, not a data carrier.</b> Every stat lives in
 * {@code data/marines/marine-weapons.weapon.json} and is served by
 * {@link WeaponRegistry}; the accessors below delegate there. The constants
 * survive while code and persisted marine records still use enum identity;
 * W4 in {@code moddable-weapons-nouns.md} retires that compatibility handle.
 * Deliberately, the javadoc here explains *intent* and no longer restates
 * numbers: a figure written in two places drifts, and several of these
 * paragraphs had already gone stale against the balance pass that moved them.
 *
 * <p>Primaries render as either weapon-colored traveling bolt-family bodies or
 * explicit projectile sprites. The render-side effect recipe selects a pulse,
 * rail, or drone silhouette and tints it from {@link #tracerColor()};
 * shell-backed weapons use {@link #projectileSpritePath()}. Per-weapon fire
 * sound supplies the matching audio identity.
 *
 * <p>{@link #penetration()} is resolved against the target's current armor
 * rating by the shared combat-durability model.
 */
public enum MarineWeapon {
    /**
     * Cheap recruit issue — a heavy, slow ballistic rifle whose single shots
     * reward closing distance and taking a stable firing posture. It is
     * deliberately worse than the pulse rifle in cadence, accuracy, falloff
     * and burst pressure so the first energy-weapon upgrade is immediately
     * meaningful. Its one advantage is a heavier round: a single-shot weapon
     * that also loses on per-round damage cannot provide a useful upgrade
     * tradeoff against a burst rifle.
     */
    FIELD_RIFLE("weapon.field-rifle"),
    /**
     * Standard upgraded marine primary — vanilla pulse laser flavor, 3-round
     * burst (Halo BR-style tap-tap-tap). Sits between the single-shot DMR and
     * the sustained squad automatic.
     *
     * <p>Mild range falloff and a small spread keep the BR the "always
     * useful" baseline — not as punishing at range as the shredder, not as crisp
     * as the DMR. Each burst rolls accuracy independently per round, so
     * {@code P(any hit)} at long range is well above the single-shot
     * baseline. The three traveling bolts briefly overlap, which reads as a
     * burst without a bespoke projectile sprite.
     */
    PULSE_RIFLE("weapon.pulse-rifle"),
    /**
     * Close-range flechette cloud. The enum name and {@code weapon.smg} id are
     * retained for save compatibility, while catalog presentation and firing
     * behavior now express the shredder-carbine family.
     *
     * <p>Heavy range falloff plus a wide spread saturate the area near max
     * range — the design read is "devastating at door-breach distance, just
     * noise at the far end of the cone."
     */
    SMG("weapon.smg"),
    /**
     * Sustained chemical-slug support primary. Its long temporal burst makes
     * an automatic rifleman valuable while a fire team covers, without adding
     * a suppression status or a permanent marine role.
     */
    SQUAD_AUTOMATIC("weapon.squad-automatic"),
    /**
     * Long-range marksman rifle — heavier hit, slower cycle, mild AT bonus.
     * Vanilla railgun. Single long, fast traveling bolt.
     *
     * <p>Minimal range falloff and a tiny spread mean range is the DMR's
     * whole point: its accuracy curve stays almost flat across the band where
     * rifles and SMGs are dropping off hard.
     */
    DMR("weapon.dmr"),
    /**
     * Drone-mounted pulse laser — built-in armament for the autonomous
     * defender drones launched from a {@link com.dillon.starsectormarines.battle.drone.DroneHub}.
     * Light burst with a cyan tracer (visually reads as overhead laser fire).
     * Lower damage and shorter range than the marine pulse rifle — drones are
     * a screen, not a heavy hitter; sustained drone fire whittles marines
     * down rather than dropping them in one engagement.
     *
     * <p>The {@code Marine} prefix on this enum is a naming wart for now —
     * drones aren't marines, but the firing pipeline is shared, and adding a
     * parallel {@code DroneWeapon} enum just to host one entry isn't worth
     * the duplication. The wart disappears with the enum itself in W4, since
     * {@code weapon.drone-pulse} carries no such implication.
     */
    DRONE_PULSE("weapon.drone-pulse");

    /** Registry id this constant resolves through. The handle that outlives the enum. */
    public final String id;

    MarineWeapon(String id) {
        this.id = id;
    }

    public static MarineWeapon fromId(String id) {
        for (MarineWeapon weapon : values()) {
            if (weapon.id.equals(id)) return weapon;
        }
        throw new IllegalArgumentException("Unknown marine-primary weapon id '" + id + "'");
    }

    /**
     * The backing definition. A plain map lookup on a handful of entries —
     * not cached, deliberately, so installing a registry is never subtly
     * order-dependent. Throws when the registry is missing; see
     * {@link WeaponRegistry#require}.
     */
    public WeaponDef def() {
        return WeaponRegistry.require(id);
    }

    public String displayName() { return def().displayName; }
    public String catalogRole() { return def().catalogRole; }
    public String catalogDescription() { return def().catalogDescription; }
    /** Vanilla fire sound id ({@code fireSoundTwo} from the source {@code .wpn}); mono, pre-registered by the core install. */
    public String fireSoundId() { return def().fireSoundId; }
    /** Traveling-body tint. Distinct per weapon so the player can identify fire at a glance. */
    public Color tracerColor() { return def().tracerColor; }
    public float range() { return def().range; }
    public float damage() { return def().damage; }
    public float accuracy() { return def().accuracy; }
    public float cooldown() { return def().cooldown; }
    public float penetration() { return def().penetration; }
    /** Visual character of the impact at endpoint. */
    public ImpactProfile impactProfile() { return def().impactProfile; }
    /** Rounds per fire decision. 1 = single shot. &gt;1 = burst: the AI fires the first round and {@code InfantryWeapons.tick} emits the remainder at {@link #burstSpacing()} intervals. */
    public int burstCount() { return def().burstCount; }
    /** Sim-seconds between burst rounds. Ignored when {@link #burstCount()} == 1. */
    public float burstSpacing() { return def().burstSpacing; }
    /** Simultaneous projectiles released by each burst round. */
    public int projectilesPerShot() { return def().projectilesPerShot; }
    /** Optional projectile sprite. When non-null, shots render as that rotated traveling sprite instead of the shared tinted bolt. */
    public String projectileSpritePath() { return def().projectileSpritePath; }
    /** Projectile sprite visual size in cells (long axis). Ignored when {@link #projectileSpritePath()} is null. */
    public float projectileVisualCells() { return def().projectileVisualCells; }
    /**
     * Fraction of base {@link #accuracy()} lost at {@link #range()} cells.
     * 0 = no falloff, 0.5 = halve accuracy at max range. Applied via
     * {@link com.dillon.starsectormarines.battle.combat.RangeFalloff#accuracy}
     * in {@code InfantryWeapons.fireShot} — compounds multiplicatively with
     * the {@code FireStance} multiplier so a moving marine at long range is
     * doubly inaccurate.
     */
    public float accuracyFalloff() { return def().accuracyFalloff; }
    /**
     * Lateral target-plane spread in cells at {@link #range()}, scaling
     * linearly with distance via
     * {@link com.dillon.starsectormarines.battle.combat.RangeFalloff#spread}.
     * The ballistic resolver commits this offset to the physical ground ray;
     * the intended target is not automatically damaged, and the resulting
     * ray may contact another body or miss its silhouette. The same physical
     * endpoint drives the visible round. Mirrors
     * {@link com.dillon.starsectormarines.battle.mech.MechWeapon#hitSpread}.
     */
    public float hitSpread() { return def().hitSpread; }
    /**
     * Round speed in cells/sec, fed to
     * {@link com.dillon.starsectormarines.battle.combat.BallisticResolver#resolve}
     * as {@code roundVelocity}. Used whenever &gt; 0;
     * {@code InfantryWeapons.fireShot} falls back to
     * {@link com.dillon.starsectormarines.battle.combat.BallisticResolver#DEFAULT_ROUND_VELOCITY}
     * for the null-weapon militia/alien/turret callers.
     */
    public float roundVelocity() { return def().roundVelocity; }

    /** Fleet catalog designation shown alongside the weapon's in-universe model name. */
    public String designation(EquipmentGrade grade) {
        return def().designation(grade != null ? grade.tier : 1);
    }

    public String modelName() {
        return def().modelName;
    }

    public String catalogName(EquipmentGrade grade) {
        return def().catalogName(grade != null ? grade.tier : 1);
    }
}
