package com.dillon.starsectormarines.battle.mech.components;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.setup.BattleSetup;

/**
 * Per-unit mutable state for a component-built mech loadout. Held in the world's
 * {@code MECH_LOADOUT} OBJECT column (an optional archetype-presence component)
 * when a mech-class unit spawns; absent on every other unit — a mech is an entity
 * that <em>has</em> this component, not one with a non-null {@code mech} field.
 * (Was a {@code ComponentStore<MechLoadoutComponent>} before that transitional
 * store was folded into the archetype world and deleted.)
 * Each physical hardpoint carries an optional {@link MechWeaponMount}; the
 * installed {@link MechWeaponComponent} is immutable while cooldown, ammunition
 * and burst continuation remain per-mount. Independent mounts may fire on the
 * same tick. Mechs do not borrow the infantry primary-weapon state.
 */
public final class MechLoadoutComponent {

    public static final int DEFAULT_SRM_AMMO_SALVOS = MechWeaponComponent.SRM_15.ammoCapacity;
    public static final int DEFAULT_LRM_AMMO_SALVOS = MechWeaponComponent.LRM_15.ammoCapacity;
    /** Default upper-torso traverse speed, in sprite degrees per sim-second. */
    public static final float DEFAULT_TORSO_TURN_RATE_DEGREES = 120f;

    public final MechVariant variant;
    private final MechWeaponMount[] mounts = new MechWeaponMount[MechMountSlot.values().length];

    /** Current upper-torso bearing in sprite degrees; independent of the hips. */
    public float torsoFacingDegrees = 180f;
    /** Maximum upper-torso traverse speed, in degrees per sim-second. */
    public float torsoTurnRateDegrees = DEFAULT_TORSO_TURN_RATE_DEGREES;
    /** Target this torso state was last evaluated against. {@code 0L} means neutral/no target. */
    public long torsoAimTargetId;
    /** True only when the upper torso is physically aligned enough to fire at {@link #torsoAimTargetId}. */
    public boolean torsoOnTarget;

    /** Seconds without meaningful progress toward the current path destination. */
    public float collisionStallSeconds;
    /** Best remaining straight-line distance reached for the current path destination. */
    public float collisionBestRemainingDistance = Float.POSITIVE_INFINITY;
    /** Destination used to establish {@link #collisionBestRemainingDistance}. */
    public int collisionProgressDestX = Integer.MIN_VALUE;
    /** Destination used to establish {@link #collisionBestRemainingDistance}. */
    public int collisionProgressDestY = Integer.MIN_VALUE;
    /** True while a stalled mech may pass through soft separation from other mechs. */
    public boolean collisionEscapeActive;

    /** Whether this state permits firing at {@code target}. */
    public boolean isAimedAt(long target) {
        return torsoOnTarget && torsoAimTargetId == target;
    }

    /**
     * Doctrine slot for this chassis. Set at spawn time by
     * {@link BattleSetup}'s defender cluster mint; read by
     * {@code GoapMechBehavior} goal-relevance scoring to pick which mech
     * goal (overwatch / backstop / etc.) the planner pursues. Mutable so
     * the commander tier (future) can re-assign without re-allocating the
     * loadout state.
     */
    public MechRole role;

    /** Latched true once the sim has emitted a smoking-wreck for this mech's death. Prevents re-spawn across ticks if the death-scan pass runs again with the mech still in the units list. */
    public boolean wreckSpawned = false;

    // ---- LR Support overwatch cell cache ----
    //
    // Stage 1's OverwatchKillZone action picks an LR-band cover cell once per
    // threat-axis shift (not per tick). These fields hold the picked cell
    // and the squad's lastSeenEnemy at pick time; when the lastSeenEnemy
    // shifts, the action re-picks. -1 sentinel = no pick yet / no contact yet.

    /** Picked overwatch cell X. -1 = no pick yet. */
    public int overwatchCellX = -1;
    /** Picked overwatch cell Y. -1 = no pick yet. */
    public int overwatchCellY = -1;
    /** Squad's lastSeenEnemyX at the moment the overwatch cell was picked. Drives re-pick when the threat axis shifts. */
    public int overwatchAxisX = -1;
    /** Squad's lastSeenEnemyY at the moment the overwatch cell was picked. */
    public int overwatchAxisY = -1;

    // ---- Armored Support backstop assignment ----
    //
    // Stage 1's BackstopAssignedSquad action paces a designated friendly
    // infantry squad. Picked lazily at the first execute tick that finds a
    // candidate (nearest same-side infantry squad); cached here so the pick
    // is stable across replans. Cleared back to -1 when the backed squad is
    // wiped, so the next replan re-picks. The commander tier (future) will
    // overwrite this with explicit assignments.

    /** Squad id this Armored Support mech is currently backing. -1 = no assignment yet (re-pick on next execute). */
    public int assignedSquadId = -1;

    // ---- Per-mech morale (Stage 2) ----
    //
    // A mech's morale is a chassis property, not a squad aggregate — playtest
    // dump squad_0 showed that the infantry-shape squad-level morale drains
    // off-puzzle for mechs (a fresh full-strength squad of 4 mechs can break
    // collectively before any individual is hurt enough to flinch). Per-mech
    // morale instead drains at chassis-HP threshold crossings and recovers
    // out of LoS, with a hard cap once the chassis crosses
    // {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MECH_MORALE_ARMOR_GONE_HP_FRAC} HP.
    //
    // The squad-level {@link Squad#moraleBroken} flag is still what the
    // GOAP predicate reads — {@code SquadMoraleSystem.updateMechSquadMorale}
    // aggregates these per-mech flags up (majority-broken trips the squad).
    // Infantry squads continue to use the squad-level drain in
    // {@code SquadMoraleSystem.tick}.

    /** Mech-side morale, [0, 1]. Drains on HP-threshold crossings, recovers passively out of fire. Capped by {@link #moraleCap()}. */
    public float morale = 1.0f;
    /** Hysteresis flag for {@link #morale}. Trips below {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MECH_MORALE_BROKEN_THRESHOLD} × cap, clears above {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MECH_MORALE_CLEAR_THRESHOLD} × cap. */
    public boolean moraleBroken = false;
    /** Sim-seconds since the last hit on this mech. Gates morale recovery — see {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MORALE_RECOVER_AFTER_FIRE_SECONDS}. */
    public float timeSinceUnderFire = Float.MAX_VALUE / 2f;
    /** Number of HP thresholds in {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MECH_HP_DRAIN_THRESHOLDS} this mech has already drained at. Monotonic — a healed mech doesn't refund drains. */
    public int hpThresholdsCrossed = 0;

    public MechLoadoutComponent(MechVariant variant, MechRole role) {
        this(variant, variant != null ? variant.arms : null,
                variant != null ? variant.leftShoulder : null,
                variant != null ? variant.rightShoulder : null, role);
    }

    /** Builds an authored/custom loadout from independently swappable hardpoint components. */
    public MechLoadoutComponent(MechVariant variant, MechWeaponComponent arms,
                                MechWeaponComponent leftShoulder,
                                MechWeaponComponent rightShoulder, MechRole role) {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        this.variant = variant;
        this.role = role;
        install(MechMountSlot.ARMS, arms);
        install(MechMountSlot.LEFT_SHOULDER, leftShoulder);
        install(MechMountSlot.RIGHT_SHOULDER, rightShoulder);
    }

    private void install(MechMountSlot slot, MechWeaponComponent component) {
        if (component != null) mounts[slot.ordinal()] = new MechWeaponMount(slot, component);
    }

    public MechWeaponMount mount(MechMountSlot slot) {
        return mounts[slot.ordinal()];
    }

    public int appearanceSelector(MechMountSlot slot) {
        MechWeaponMount mount = mount(slot);
        return mount != null ? mount.component.appearanceSelector : 0;
    }

    public MechWeaponMount[] mounts() {
        return mounts;
    }

    public boolean hasWeapon(MechWeapon weapon) {
        for (MechWeaponMount mount : mounts) {
            if (mount != null && mount.weapon() == weapon) return true;
        }
        return false;
    }

    /** Supplied SRM band when present, otherwise the longest direct-fire band. */
    public float preferredDirectRange() {
        float missileRange = 0f;
        float range = 0f;
        for (MechWeaponMount mount : mounts) {
            if (mount != null && mount.hasAmmo() && mount.weapon() != MechWeapon.LRM_ARTILLERY) {
                range = Math.max(range, mount.weapon().range);
                if (mount.weapon() == MechWeapon.SRM_POD) {
                    missileRange = Math.max(missileRange, mount.weapon().range);
                }
            }
        }
        return missileRange > 0f ? missileRange : range;
    }

    public boolean needsSupply() {
        for (MechWeaponMount mount : mounts) {
            if (mount != null && mount.needsSupply()) return true;
        }
        return false;
    }

    /** Supplies long-range racks first, then the remaining physical slot order. */
    public boolean resupplyOne() {
        for (MechWeaponMount mount : mounts) {
            if (mount != null && mount.weapon() == MechWeapon.LRM_ARTILLERY
                    && mount.resupplyOne()) return true;
        }
        for (MechWeaponMount mount : mounts) {
            if (mount != null && mount.resupplyOne()) return true;
        }
        return false;
    }

    /** Default stock-heavy compatibility loadout. */
    public static MechLoadoutComponent defaultLoadout(MechRole role) {
        return MechVariant.BULWARK.createLoadout(role);
    }
}
