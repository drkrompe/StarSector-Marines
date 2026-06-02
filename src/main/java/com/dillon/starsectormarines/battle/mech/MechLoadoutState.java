package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;

/**
 * Per-unit mutable state for the three-weapon mech loadout. Set on
 * {@link Unit#mech} when a mech-class unit spawns; null on every other unit.
 * Holds the three {@link MechWeapon} slot references plus per-slot
 * ammo / cooldown / salvo trackers — concurrent fire across all three tracks
 * is the whole point of the mech, so each weapon's state is independent.
 *
 * <p>Each weapon track keeps its own cooldown + burst/salvo trackers on this
 * class ({@link #chaingunCooldown} / {@link #chaingunBurstRemaining} /
 * {@link #chaingunBurstTimer} / {@link #chaingunBurstTargetId}, and the SRM
 * salvo equivalents below) rather than borrowing the {@link Unit}-level
 * primary-weapon cooldown/burst state — the three tracks fire concurrently,
 * so shared fields would collide. Mechs don't carry a {@link com.dillon.starsectormarines.battle.infantry.MarineWeapon}
 * either, so there's no marine fire path to reuse.
 */
public final class MechLoadoutState {

    public final MechWeapon chaingun;
    public final MechWeapon srmPod;
    public final MechWeapon lrmArtillery;

    /**
     * Doctrine slot for this chassis. Set at spawn time by
     * {@link BattleSetup}'s defender cluster mint; read by
     * {@code GoapMechBehavior} goal-relevance scoring to pick which mech
     * goal (overwatch / backstop / etc.) the planner pursues. Mutable so
     * the commander tier (future) can re-assign without re-allocating the
     * loadout state.
     */
    public MechRole role;

    /** Sim-seconds until CHAINGUN can fire another burst. */
    public float chaingunCooldown = 0f;
    /** Rounds left in the current chaingun burst. 0 = no active burst. */
    public int chaingunBurstRemaining = 0;
    /** Sim-seconds until the next chaingun round emits. Ignored when {@link #chaingunBurstRemaining} == 0. */
    public float chaingunBurstTimer = 0f;
    /**
     * Entity id of the target locked at burst start — burst keeps firing here
     * even if the mech's primary target drifts mid-stream. {@code 0L} = no
     * locked target. Held as an id (not a {@link Unit} ref) so a target killed
     * mid-burst resolves cleanly to {@code null} via {@code registry.getOrNull}
     * instead of dangling — see {@code entity-id-handle} story. Resolved in the
     * {@code HeavyWeapons} continuation pass; written by {@code MechCombatantBehavior}.
     */
    public long chaingunBurstTargetId;

    /** Sim-seconds until SRM_POD can launch another salvo. Decremented in the per-tick mech-fire pass. */
    public float srmCooldown = 0f;
    /** Salvos remaining for the SRM_POD. One salvo emits {@link MechWeapon#burstCount} rockets. */
    public int srmAmmoSalvos;
    /** Rockets left to emit in the current SRM salvo. 0 = no active salvo. */
    public int srmSalvoRemaining = 0;
    /** Sim-seconds until the next rocket in the current salvo launches. Ignored when {@link #srmSalvoRemaining} == 0. */
    public float srmSalvoTimer = 0f;
    /** Entity id of the target locked at salvo start, held until the salvo is exhausted so it doesn't smear across enemies. {@code 0L} = none; resolved via {@code registry.getOrNull} (dangling-safe). */
    public long srmSalvoTargetId;

    /** Sim-seconds until LRM_ARTILLERY can fire another salvo. */
    public float lrmCooldown = 0f;
    /** Salvos remaining for LRM_ARTILLERY. One salvo emits {@link MechWeapon#burstCount} rockets. */
    public int lrmAmmoSalvos;
    /** Rockets left in the current LRM salvo. 0 = no active salvo. */
    public int lrmSalvoRemaining = 0;
    /** Sim-seconds until the next rocket in the current LRM salvo launches. Ignored when {@link #lrmSalvoRemaining} == 0. */
    public float lrmSalvoTimer = 0f;
    /** Entity id of the target locked at salvo start. {@code 0L} = none; resolved via {@code registry.getOrNull} (dangling-safe). */
    public long lrmSalvoTargetId;

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

    public MechLoadoutState(MechWeapon chaingun, MechWeapon srmPod, MechWeapon lrmArtillery,
                            int srmAmmoSalvos, int lrmAmmoSalvos, MechRole role) {
        this.chaingun = chaingun;
        this.srmPod = srmPod;
        this.lrmArtillery = lrmArtillery;
        this.srmAmmoSalvos = srmAmmoSalvos;
        this.lrmAmmoSalvos = lrmAmmoSalvos;
        this.role = role;
    }

    /** Default chassis loadout for a stock HEAVY_MECH — chainguns + SRM pod (6 salvos) + LRM artillery (3 salvos × 5 rockets = 15 rockets). Role is the doctrine slot the planner reads to pick mech goals. */
    public static MechLoadoutState defaultLoadout(MechRole role) {
        return new MechLoadoutState(
                MechWeapon.CHAINGUN,
                MechWeapon.SRM_POD,
                MechWeapon.LRM_ARTILLERY,
                6, 3, role);
    }
}
