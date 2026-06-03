package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.vehicle.Vehicle;

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
    void setPath(Entity u, int[] newPath);

    /** Drop the unit's path. */
    void clearPath(Entity u);

    /** Advance the unit one tick along its current path. */
    void advanceMovement(Entity u);

    /** Stanced-fire convenience (STANCED). */
    void fireShot(Entity shooter, Entity target);

    /** Stance-aware fire — MOVING halves the base accuracy roll. */
    void fireShot(Entity shooter, Entity target, FireStance stance);

    void fireSecondary(Entity shooter, Entity target);

    void fireMechWeapon(Entity shooter, Entity target, MechWeapon weapon);

    /** Mech fire with explicit accuracy multiplier (LRM indirect-fire path). */
    void fireMechWeapon(Entity shooter, Entity target, MechWeapon weapon, float accuracyMult);

    /** Queue a unit spawn for the serial spawn-flush (drone-hub / reinforcement spawns). */
    void queueSpawn(Entity u);

    /** Mint a new squad for {@code faction} with an optional {@code leader}; returns the new squad id. */
    int mintSquad(Faction faction, Entity leader);

    /** Add a freshly spawned unit to the roster (walk-in reinforcement). */
    void addUnit(Entity u);

    /** Add a shuttle to the air system (shuttle reinforcement / garrison drop). */
    void addShuttle(Shuttle s);

    /** Add a convoy vehicle to the ground system (convoy reinforcement). */
    void addConvoyVehicle(Vehicle v);
}
