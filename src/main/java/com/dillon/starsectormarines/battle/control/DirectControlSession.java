package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;

/**
 * Battle-owned authority for one directly controlled Marine. UI publishes input;
 * the serial tick validates ownership, snapshots input and runs one movement and
 * primary intent pass. Ordinary unit dispatch skips only this identity. No health,
 * mission, damage, or equipment resource authority moves into the session.
 */
public final class DirectControlSession {
    private final BattleControl battle;
    private final UnitRosterService roster;
    private final LongPredicate unavailable;
    private final BooleanSupplier complete;
    private long unitId;
    private Squad controlledSquad;
    private ManualIntent intent = ManualIntent.NEUTRAL;

    public DirectControlSession(BattleControl battle, UnitRosterService roster,
                                LongPredicate unavailable, BooleanSupplier complete) {
        this.battle = battle;
        this.roster = roster;
        this.unavailable = unavailable;
        this.complete = complete;
    }

    public long activeUnitId() { return unitId; }
    public boolean active() { return unitId != 0L; }
    public boolean isControlling(long id) { return id != 0L && id == unitId; }
    public ManualIntent intent() { return intent; }

    /** Read-only entry check. Committed special actions finish under their original owner. */
    public boolean canEnter(long id) {
        if (!eligible(id)) return false;
        return ManualTerrainMotion.canStand(battle.getGrid(), roster.world().renderX(id),
                roster.world().renderY(id), battle.physicalRadius(id));
    }

    private boolean eligible(long id) {
        if (id == 0L || complete.getAsBoolean() || !roster.isAliveById(id)) return false;
        World world = roster.world();
        if (roster.identity().type(id) != UnitType.MARINE
                || roster.identity().faction(id) != Faction.MARINE
                || world.hp(id) <= 0f || !roster.movement().has(id)
                || !roster.combat().has(id) || !world.hasAiState(id)
                || unavailable.test(id) || world.fallbackTimer(id) > 0f) return false;
        // Mission kit carriers have an objective-owned channel, not a secondary
        // weapon clock. Their explicit interaction adapter is outside this primary-only slice.
        UnitRole role = roster.role().role(id);
        if (role == UnitRole.PLANTER || role == UnitRole.KIT_RETRIEVER) return false;
        if (world.hasSecondaryWeapon(id) && world.secondaryActionTimer(id) > 0f) return false;
        Squad squad = battle.squadOf(id);
        if (squad == null) return true;
        ObjectiveAssignment assignment = squad.assignedObjective;
        CommandDirective shelved = battle.getShelvedSquadDirective(squad.id);
        return (assignment == null || assignment.kind() != AssignmentKind.WITHDRAW)
                && (shelved == null || shelved.assignment().kind() != AssignmentKind.WITHDRAW)
                && !squad.moraleBroken && !squad.fireTeamBroken(battle.squad().fireTeamIndex(id));
    }

    /** UI and scripted requests enter on the simulation host thread, outside worker dispatch. */
    public boolean enter(long id) {
        if (active()) return unitId == id;
        if (!canEnter(id)) return false;
        unitId = id;
        intent = ManualIntent.NEUTRAL;
        controlledSquad = battle.squadOf(id);
        clearOwnedWork(id);
        roster.combat().setTargetId(id, 0L);
        if (controlledSquad != null) controlledSquad.setControlledMember(id, battle);
        return true;
    }

    public void submit(ManualIntent next) {
        if (active()) intent = next != null ? next : ManualIntent.NEUTRAL;
    }

    /** Chrome, focus, and pause can neutralize input even when no simulation tick runs. */
    public void suspendInput() {
        intent = intent.neutralized();
        if (active() && roster.isAliveById(unitId) && roster.combat().has(unitId)) {
            roster.combat().clearPrimaryFire(unitId);
        }
    }

    public void exit() {
        long previous = unitId;
        if (previous == 0L) return;
        unitId = 0L;
        intent = ManualIntent.NEUTRAL;
        clearOwnedWork(previous);
        Squad squad = controlledSquad;
        controlledSquad = null;
        // Keep the original squad reference: death/boarding can remove membership
        // before release, but that squad must still reallocate its retained plan.
        if (squad != null) squad.setControlledMember(0L, battle);
    }

    private void clearOwnedWork(long id) {
        if (!roster.isAliveById(id)) return;
        if (roster.movement().has(id)) battle.clearPath(id);
        if (roster.combat().has(id)) roster.combat().clearPrimaryFire(id);
    }

    /** Run before replanning and after lifecycle phases, including on paused advances. */
    public void validate() {
        if (active() && (!eligible(unitId) || controlledSquad != battle.squadOf(unitId))) exit();
    }

    /** Exactly once at UPDATE_UNITS, before autonomous workers are dispatched. */
    public void tick() {
        validate();
        if (!active()) return;
        long id = unitId;
        ManualIntent input = intent;
        InfantryUnitPrep.tickCooldowns(id, roster.world());
        roster.movement().moveDirect(id, battle.getGrid(), input.moveX(), input.moveY(),
                battle.physicalRadius(id), BattleSimulation.TICK_DT);
        if (input.firing()) {
            boolean moving = roster.movement().velX(id) != 0f || roster.movement().velY(id) != 0f;
            roster.combat().setPointFireIntent(id, new PointFireAim(input.aimX(), input.aimY()),
                    FireStance.stanceFor(moving));
        }
    }
}
