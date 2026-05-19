package com.dillon.starsectormarines.battle;

/**
 * Per-unit mutable state for the three-weapon mech loadout. Set on
 * {@link Unit#mech} when a mech-class unit spawns; null on every other unit.
 * Holds the three {@link MechWeapon} slot references plus per-slot
 * ammo / cooldown / salvo trackers — concurrent fire across all three tracks
 * is the whole point of the mech, so each weapon's state is independent.
 *
 * <p>Chaingun fire reuses {@link Unit#cooldownTimer} / {@link Unit#burstRemaining}
 * / {@link Unit#burstTimer} / {@link Unit#burstTarget} — those fields are
 * unused on mechs (mechs don't carry a {@link MarineWeapon}), so we piggyback
 * cleanly. SRM salvo state is local here because it can run concurrently with
 * a chaingun burst, and reusing the same burst fields would collide.
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
    /** Target locked at burst start — burst keeps firing here even if {@link Unit#target} drifts mid-stream. */
    public Unit chaingunBurstTarget;

    /** Sim-seconds until SRM_POD can launch another salvo. Decremented in the per-tick mech-fire pass. */
    public float srmCooldown = 0f;
    /** Salvos remaining for the SRM_POD. One salvo emits {@link MechWeapon#burstCount} rockets. */
    public int srmAmmoSalvos;
    /** Rockets left to emit in the current SRM salvo. 0 = no active salvo. */
    public int srmSalvoRemaining = 0;
    /** Sim-seconds until the next rocket in the current salvo launches. Ignored when {@link #srmSalvoRemaining} == 0. */
    public float srmSalvoTimer = 0f;
    /** Target locked at salvo start. Held until exhausted so the salvo doesn't smear across multiple enemies. */
    public Unit srmSalvoTarget;

    /** Sim-seconds until LRM_ARTILLERY can fire another salvo. */
    public float lrmCooldown = 0f;
    /** Salvos remaining for LRM_ARTILLERY. One salvo emits {@link MechWeapon#burstCount} rockets. */
    public int lrmAmmoSalvos;
    /** Rockets left in the current LRM salvo. 0 = no active salvo. */
    public int lrmSalvoRemaining = 0;
    /** Sim-seconds until the next rocket in the current LRM salvo launches. Ignored when {@link #lrmSalvoRemaining} == 0. */
    public float lrmSalvoTimer = 0f;
    /** Target locked at salvo start. */
    public Unit lrmSalvoTarget;

    /** Latched true once the sim has emitted a smoking-wreck for this mech's death. Prevents re-spawn across ticks if the death-scan pass runs again with the mech still in the units list. */
    public boolean wreckSpawned = false;

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
