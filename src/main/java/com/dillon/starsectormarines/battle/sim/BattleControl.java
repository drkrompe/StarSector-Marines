package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.SquadDirectiveControl;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderSystem;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.nav.AsyncDefendTrackRoutes;
import com.dillon.starsectormarines.battle.nav.PathRequestStatus;

/**
 * Read + mutate window onto the battle, for code that runs during the
 * <b>serial unit-update pass</b> — GOAP {@code Action.execute} and the
 * behaviors it drives, which are free to move units, fire weapons, and spawn.
 * Extends {@link BattleView} with the mutators; {@link BattleSimulation}
 * implements this directly.
 *
 * <p>The {@code BattleView} / {@code BattleControl} split mirrors the GOAP
 * thread-safety contract: parallel-replan methods take {@link BattleView}
 * (queries only, can't compile a mutation), while serial {@code execute} takes
 * {@code BattleControl}. See {@code ecs-nouns.md}.
 */
public interface BattleControl extends BattleView, SquadDirectiveControl {

    /** Optional battle-owned async routing service; null for the synchronous control. */
    default AsyncDefendTrackRoutes asyncDefendTrackRoutes() { return null; }

    /** Resolve an externally delivered blast through the shared AoE/structure pipeline. */
    void detonateNow(PendingDetonation detonation);

    /** Queue the view/audio event paired with an externally delivered heavy blast. */
    void spawnHeavyImpact(float x, float y, float radius);

    /**
     * Apply damage from a source that is not an entity's weapon — a strafing
     * run, or a placed emplacement burning out. Credits no attacker, so nobody
     * collects a kill for it.
     */
    void applyExternalDamage(long target, float damage, float penetration);

    /** Publish a visible/audible shot event without resolving a round through the weapon pipeline. */
    void postShot(ShotEvent shot);

    /** Replace a unit's path; queues the occupancy/destIndex delta. Pass an empty path (or {@link #clearPath}) to drop the current path. */
    void setPath(long u, int[] newPath);

    /** Request terrain-proven movement; PENDING retains this intent instead of selecting a fallback. */
    default PathRequestStatus requestPath(long unit, int goalX, int goalY) {
        return PathRequestStatus.FAILED;
    }

    /** Drop the unit's path. */
    void clearPath(long u);

    /** Advance the unit (by entity id) one tick along its current path. */
    void advanceMovement(long u);

    /** Stanced-fire convenience (STANCED). */
    void fireShot(long shooter, long target);

    /** Stance-aware fire — MOVING halves the base accuracy roll. */
    void fireShot(long shooter, long target, FireStance stance);

    /** Commit a direct primary round along a world bearing without a target lock. */
    void firePointShot(long shooter, PointFireAim aim, FireStance stance);

    void fireSecondary(long shooter, long target);

    /** Consume and lob a fragmentation grenade toward a world-space landing point. */
    void throwFragmentationGrenade(long carrier, float targetX, float targetY);

    /** Consume a smoke grenade and lob it toward a world-space cell center. */
    void throwSmoke(long carrier, float targetX, float targetY);

    /** Complete a reserved contact-demolition plant and begin its fuse. */
    boolean plantSatchel(long carrier, long target);

    /**
     * Apply a committed close-contact payload to an adjacent actor. Damage and
     * penetration come from the item's weapon definition and resolve through
     * the shared durability calculation; there is no travelling round and no
     * area blast. Returns {@code false} when the contact is no longer legal.
     */
    boolean applyContactStrike(long carrier, long target);

    /**
     * Apply a committed breaching cutter's weapon-owned wall damage to one
     * authored breach point. Returns {@code false} for any cell that is not
     * still an authored, uncut breach point beside the carrier.
     */
    boolean applyContactBreach(long carrier, int cellX, int cellY);

    void fireMechWeapon(long shooter, long target, WeaponDef weapon);

    /** Mech fire with explicit accuracy multiplier (LRM indirect-fire path). */
    void fireMechWeapon(long shooter, long target, WeaponDef weapon, float accuracyMult);

    /** Fires one installed mount from its carrier-owned posed hardpoint. */
    default void fireMechWeapon(long shooter, long target, MechWeaponMount mount,
                                float accuracyMult) {
        fireMechWeapon(shooter, target, mount.weaponDef(), accuracyMult);
    }

    /** Mint a new squad for {@code faction} led by an existing unit {@code leaderId} ({@code 0L} for a leaderless squad); returns the new squad id. */
    int mintSquad(Faction faction, long leaderId);

    /**
     * Mint a new (as-yet-leaderless) squad for {@code faction}, denormalizing
     * {@code mechSquad} from {@code type}; returns the new squad id. The pre-spawn
     * mint the spec-based construction path uses — the caller holds an
     * {@link EntitySpec}, not a live {@code Entity}, so leadership (if any) is set
     * after the members spawn.
     */
    int mintSquad(Faction faction, UnitType type);

    /** Spawn a ground-roster unit from a spec (immediate adopt); returns the minted id. The spec-based construction path (identity-collapse Phase C). */
    long spawn(EntitySpec spec);

    /** Queue a spec spawn for the serial spawn-flush; returns the minted id, or {@code 0L} in the parallel path (the id is minted at the drain, so none is available yet). */
    long queueSpawn(EntitySpec spec);

    /**
     * Take a unit off the map without killing it — no death event, no corpse,
     * no wreck. The registry contract holds afterwards: a released id resolves
     * to null and reads as not alive.
     *
     * <p>This is not a kill and must not be used as one. It exists for a unit
     * that has genuinely left the battlefield alive — an airframe leaving its
     * hardstand to become an air entity is the case it was added for.
     * Anything that should read as a death goes through {@code applyDamage},
     * which leaves a body behind on purpose.
     */
    void takeOffTheField(long entityId);

    /**
     * Spawn a shuttle into the air system (shuttle reinforcement / garrison drop) and
     * return its world entity id. Configure the rest by id — {@code world().mission(id)}
     * for the mission bag (cycles, loadouts, garrison node), {@code attachAirTurrets} for
     * the turret kit.
     */
    long spawnShuttle(ShuttleType type, Faction faction,
                      float lzX, float lzY, float entryX, float entryY,
                      float exitX, float exitY, float pendingDelay);

    /** Spawn a convoy vehicle (convoy reinforcement): builds its world entity from the
     *  variant + faction, seeding the caller-built {@link VehicleMission} as its
     *  {@code VEHICLE_MISSION} column. Configure the mission (route inputs, loadout) before calling. */
    void addConvoyVehicle(VehicleType type, Faction faction, VehicleMission mission);

    /**
     * The player's per-chassis mech move orders. Declared on the mutate window
     * rather than only on {@code BattleSimulation} because it is consumed as a
     * {@code battle.decision.Reflex} — the lance's one interrupt ahead of its
     * doctrine step — and a reflex is handed this interface.
     */
    MechMoveOrderSystem getMechMoveOrderSystem();

    /** Discard this chassis's queued and active one-shot moves at an ownership boundary. */
    void cancelMechMoveOrder(long member);

    /** The posed barrel must reach open space before a mount spends its trigger. */
    boolean canFireMechMount(long shooter, MechWeaponMount mount);
}
