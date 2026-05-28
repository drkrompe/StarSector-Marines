package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.mech.MechWeapon;

/**
 * Read + mutate window onto the battle, for code that runs during the
 * <b>serial unit-update pass</b> — GOAP {@code Action.execute} and the
 * behaviors it drives, which are free to move units, fire weapons, and spawn.
 * Extends {@link BattleView} with the mutators; {@link BattleSimulation}
 * implements this directly.
 *
 * <p>The {@code BattleView} / {@code BattleControl} split mirrors the GOAP
 * thread-safety contract: parallel-replan methods take {@link BattleView}
 * (queries only, can't compile a mutation), the serial {@code execute} takes
 * {@code BattleControl}. Part of the {@code drop-sim-facade-delegators}
 * migration; the mutator surface grows as {@code execute} consumers migrate
 * off the raw {@code BattleSimulation} parameter.
 */
public interface BattleControl extends BattleView {

    /** Replace a unit's path; queues the occupancy/destIndex delta. Pass an empty path (or {@link #clearPath}) to drop the current path. */
    void setPath(Unit u, int[] newPath);

    /** Drop the unit's path. */
    void clearPath(Unit u);

    /** Advance the unit one tick along its current path. */
    void advanceMovement(Unit u);

    /** Stanced-fire convenience (STANCED). */
    void fireShot(Unit shooter, Unit target);

    /** Stance-aware fire — MOVING halves the base accuracy roll. */
    void fireShot(Unit shooter, Unit target, FireStance stance);

    void fireSecondary(Unit shooter, Unit target);

    void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon);

    /** Mech fire with explicit accuracy multiplier (LRM indirect-fire path). */
    void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon, float accuracyMult);
}
