package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.MitigationSystem;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.infantry.SmokeTactics;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.vehicle.BicycleBody;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipPilot;
import com.dillon.starsectormarines.battle.warship.WarshipSystem;
import com.dillon.starsectormarines.marine.SpecialActivation;

import java.util.EnumMap;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;

/**
 * Battle-owned authority for one directly controlled ground body. UI publishes input;
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
    private boolean vehicleControl;
    private Squad controlledSquad;
    private ManualIntent intent = ManualIntent.NEUTRAL;
    private int selectedWeapon;
    private final EnumMap<DirectControlAbility, PointFireAim> pendingAbilities =
            new EnumMap<>(DirectControlAbility.class);
    private final MarineSprint marineSprint = new MarineSprint();
    /** Whether the right button was down on the previous Marine tick; a press, not a hold, uses equipment. */
    private boolean secondaryHeld;

    /** The side the player flies for. */
    private static final Faction PLAYER_SIDE = Faction.MARINE;
    /** What the four number keys do at the helm of a warship, in key order. */
    private static final WarshipPilot.Command[] WARSHIP_COMMANDS = {
            WarshipPilot.Command.HOLD_FIRE, WarshipPilot.Command.TOGGLE_DIRECTOR,
            WarshipPilot.Command.CYCLE_MISSILE_PRESET, WarshipPilot.Command.CYCLE_GROUND_FIRE};
    private final WarshipSystem warships;
    private BiPredicate<Integer, Integer> revealed;
    private Warship ship;

    public DirectControlSession(BattleControl battle, UnitRosterService roster,
                                LongPredicate unavailable, BooleanSupplier complete) {
        this(battle, roster, unavailable, complete, null, (x, y) -> true);
    }

    /**
     * As above, with the warships a player may take the helm of and the fog rule that decides
     * which ground a pilot may lay an area on.
     */
    public DirectControlSession(BattleControl battle, UnitRosterService roster,
                                LongPredicate unavailable, BooleanSupplier complete,
                                WarshipSystem warships, BiPredicate<Integer, Integer> revealed) {
        this.battle = battle;
        this.roster = roster;
        this.unavailable = unavailable;
        this.complete = complete;
        this.warships = warships;
        this.revealed = revealed;
    }

    public long activeUnitId() { return unitId; }
    /** Whether any carrier is under player control: a ground body or a warship. */
    public boolean active() { return unitId != 0L || ship != null; }
    /** Whether a ground body (Marine, Mech or vehicle) is the one under control. */
    public boolean unitActive() { return unitId != 0L; }
    /** Replaces the rule for which ground a pilot may lay an area on (the default is what the player has observed); for scenes and tests. */
    public void layableGround(BiPredicate<Integer, Integer> rule) { this.revealed = rule; }
    /** Whether a warship is the one under control. */
    public boolean warshipControl() { return ship != null; }
    /** The warship under control, or null. */
    public Warship controlledWarship() { return ship; }
    public boolean isControlling(long id) { return id != 0L && id == unitId; }
    public ManualIntent intent() { return intent; }
    /** 0 is all direct weapons; 1..3 name ARMS, LEFT_SHOULDER, and RIGHT_SHOULDER. */
    public int selectedWeapon() { return selectedWeapon; }

    /** Read-only status for the exact controlled Marine; other carriers have no stamina gauge. */
    public MarineSprint.Status sprintStatus() {
        return unitActive() && roster.isAliveById(unitId)
                && roster.identity().type(unitId) == UnitType.MARINE
                ? marineSprint.status(unitId) : MarineSprint.EMPTY;
    }

    public boolean canSelectWeapon(int selection) {
        // A warship has no weapon to choose: its four number keys are commands (see WARSHIP_COMMANDS).
        if (ship != null) return selection >= 0 && selection < WARSHIP_COMMANDS.length;
        if (!unitActive() || !roster.isAliveById(unitId)) return false;
        if (selection == 0) return true;
        if (selection < 1 || selection > MechMountSlot.values().length
                || !roster.world().hasMechLoadout(unitId)) return false;
        return HeavyWeapons.supportsPointFire(roster.world().mechLoadout(unitId)
                .mount(MechMountSlot.values()[selection - 1]));
    }

    /** Serialized player choice; switching releases queued rounds without changing installed equipment. */
    public boolean selectWeapon(int selection) {
        if (!canSelectWeapon(selection)) return false;
        if (ship != null) return WarshipPilot.command(ship, WARSHIP_COMMANDS[selection]);
        if (selection == selectedWeapon) return true;
        selectedWeapon = selection;
        releaseSelectedFire();
        return true;
    }

    private void releaseSelectedFire() {
        intent = new ManualIntent(intent.moveX(), intent.moveY(), intent.aimX(), intent.aimY(), false);
        if (roster.world().hasMechLoadout(unitId)) roster.world().mechLoadout(unitId).clearQueuedFire();
    }
    public long controlledMechId() {
        return unitActive() && roster.world().hasMechLoadout(unitId) ? unitId : 0L;
    }
    public long controlledVehicleId() { return vehicleControl ? unitId : 0L; }
    public PointFireAim pointAim() {
        return unitActive() && Float.isFinite(intent.aimX()) && Float.isFinite(intent.aimY())
                ? new PointFireAim(intent.aimX(), intent.aimY()) : null;
    }

    /** Availability does not spend equipment or change the actor's current channel. */
    public boolean canUseAbility(DirectControlAbility ability) {
        if (ship != null) return ability == DirectControlAbility.SHIELD && ship.hasShield();
        if (ability == null || !unitActive() || !eligible(unitId) || vehicleControl
                || roster.identity().type(unitId) != UnitType.MARINE
                || pendingAbilities.containsKey(ability)) return false;
        World world = roster.world();
        if (world.hasSecondaryWeapon(unitId) && world.secondaryActionTimer(unitId) > 0f) return false;
        return switch (ability) {
            case SHIELD -> {
                var spec = roster.integralSystems().spec(unitId);
                yield spec != null && spec.grantsMitigation() && roster.integralSystems().canActivate(unitId);
            }
            case SMOKE -> SmokeTactics.canThrow(unitId, battle);
        };
    }

    /** Bounded one-shot mailbox, consumed at the fixed simulation boundary before movement. */
    public boolean requestAbility(DirectControlAbility ability, PointFireAim aim) {
        // The ship's shield key is a toggle the pilot owns, not a charge: it takes effect at once.
        if (ship != null) {
            return canUseAbility(ability) && WarshipPilot.command(ship, WarshipPilot.Command.TOGGLE_SHIELD);
        }
        if (!canUseAbility(ability) || aim == null || !Float.isFinite(aim.x()) || !Float.isFinite(aim.y())) {
            return false;
        }
        pendingAbilities.put(ability, aim);
        return true;
    }

    private boolean ownsSmokeChannel(long id) {
        var commit = roster.world().smokeThrowCommit(id);
        return isControlling(id) && commit != null && commit.manual()
                && roster.world().specialEquipment(id).activation() == SpecialActivation.UTILITY_SMOKE;
    }

    /** Read-only entry check. Committed special actions finish under their original owner. */
    public boolean canEnter(long id) {
        if (!eligible(id)) return false;
        if (roster.convoy().isVehicle(id)) {
            GroundBody body = roster.convoy().body(id);
            VehicleType type = roster.convoy().vehicleType(id);
            return VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                    type.visualLengthCells, type.visualWidthCells, battle.getGrid());
        }
        return ManualTerrainMotion.canStand(battle.getGrid(), roster.world().renderX(id),
                roster.world().renderY(id), battle.physicalRadius(id),
                roster.identity().type(id).isMech());
    }

    private boolean eligible(long id) {
        if (id == 0L || complete.getAsBoolean() || !roster.isAliveById(id)) return false;
        World world = roster.world();
        if (roster.convoy().isVehicle(id)) {
            var convoy = roster.convoy();
            var mission = convoy.mission(id);
            return convoy.faction(id) == Faction.MARINE && world.hp(id) > 0f
                    && convoy.vehicleType(id) == VehicleType.HEAVY_APC
                    && convoy.body(id) instanceof BicycleBody && convoy.control(id) != null
                    && mission != null && mission.state == VehicleState.DEPLOYED
                    && !unavailable.test(id);
        }
        UnitType type = roster.identity().type(id);
        boolean mech = type.isMech();
        if ((type != UnitType.MARINE && !mech)
                || roster.identity().faction(id) != Faction.MARINE
                || world.hp(id) <= 0f || !roster.movement().has(id)
                || !roster.combat().has(id) || !world.hasAiState(id)
                || unavailable.test(id) || world.fallbackTimer(id) > 0f) return false;
        // Mission kit carriers have an objective-owned channel, not a secondary
        // weapon clock. Their explicit interaction adapter is outside this primary-only slice.
        UnitRole role = roster.role().role(id);
        if (role == UnitRole.PLANTER || role == UnitRole.KIT_RETRIEVER) return false;
        if (world.hasSecondaryWeapon(id) && world.secondaryActionTimer(id) > 0f && !ownsSmokeChannel(id)) return false;
        Squad squad = battle.squadOf(id);
        if (mech && (!world.hasMechLoadout(id) || squad == null
                || squad.faction != Faction.MARINE || !squad.isMechSquad()
                || squad.rescuePickupMech
                || !roster.entityWorld().has(id, roster.components().MECH_LOCOMOTION))) return false;
        if (squad == null) return true;
        ObjectiveAssignment assignment = squad.assignedObjective;
        CommandDirective shelved = battle.getShelvedSquadDirective(squad.id);
        return (assignment == null || assignment.kind() != AssignmentKind.WITHDRAW)
                && (shelved == null || shelved.assignment().kind() != AssignmentKind.WITHDRAW)
                && !squad.moraleBroken && !squad.fallbackInProgress
                && !squad.fireTeamBroken(battle.squad().fireTeamIndex(id));
    }

    /** UI and scripted requests enter on the simulation host thread, outside worker dispatch. */
    public boolean enter(long id) {
        if (active()) return unitId != 0L && unitId == id;
        if (!canEnter(id)) return false;
        boolean vehicle = roster.convoy().isVehicle(id);
        if (vehicle && !battle.beginVehicleDirectControl(id)) return false;
        unitId = id;
        selectedWeapon = 0;
        vehicleControl = vehicle;
        intent = ManualIntent.NEUTRAL;
        if (!vehicle && roster.identity().type(id) == UnitType.MARINE) marineSprint.releaseHold(id);
        pendingAbilities.clear();
        controlledSquad = vehicle ? null : battle.squadOf(id);
        clearOwnedWork(id);
        if (roster.combat().has(id)) roster.combat().setTargetId(id, 0L);
        if (controlledSquad != null) controlledSquad.setControlledMember(id, battle);
        return true;
    }

    /** Whether {@code candidate} could be piloted now: a live friendly hull over this battle that is not leaving. */
    public boolean canEnterWarship(Warship candidate) {
        return warships != null && candidate != null && !complete.getAsBoolean()
                && warships.ships().contains(candidate) && WarshipPilot.eligible(candidate, PLAYER_SIDE);
    }

    /** Takes the helm of a warship. Never while another carrier is under control. */
    public boolean enterWarship(Warship candidate) {
        if (active()) return ship != null && ship == candidate;
        if (!canEnterWarship(candidate)) return false;
        ship = candidate;
        selectedWeapon = 0;
        intent = ManualIntent.NEUTRAL;
        pendingAbilities.clear();
        candidate.takeHelm();
        return true;
    }

    public void submit(ManualIntent next) {
        if (!active()) return;
        intent = next != null ? next : ManualIntent.NEUTRAL;
    }

    /** A physical Shift release may arrive while chrome has suppressed simulation intent. */
    public void observeSprintRelease() {
        if (unitActive()) marineSprint.releaseHold(unitId);
    }

    /** Chrome, focus, and pause can neutralize input even when no simulation tick runs. */
    public void suspendInput() {
        pendingAbilities.clear();
        intent = intent.neutralized();
        if (ship != null) {
            ship.pilotMove(0f, 0f);
            return;
        }
        if (unitActive()) marineSprint.suspend(unitId);
        if (vehicleControl) battle.suspendVehicleDirectInput(unitId);
        if (unitActive()) clearMechDrive(unitId);
        if (unitActive() && roster.isAliveById(unitId) && roster.combat().has(unitId)) {
            roster.combat().clearPrimaryFire(unitId);
            if (roster.world().hasMechLoadout(unitId)) roster.world().mechLoadout(unitId).clearQueuedFire();
        }
    }

    public void exit() {
        secondaryHeld = false;
        if (ship != null) {
            Warship released = ship;
            ship = null;
            selectedWeapon = 0;
            pendingAbilities.clear();
            intent = ManualIntent.NEUTRAL;
            released.releaseHelm();
            return;
        }
        long previous = unitId;
        if (previous == 0L) return;
        boolean vehicle = vehicleControl;
        unitId = 0L;
        selectedWeapon = 0;
        pendingAbilities.clear();
        marineSprint.releaseHold(previous);
        vehicleControl = false;
        if (vehicle) battle.endVehicleDirectControl(previous);
        intent = ManualIntent.NEUTRAL;
        clearOwnedWork(previous);
        Squad squad = controlledSquad;
        controlledSquad = null;
        // Keep the original squad reference: death/boarding can remove membership
        // before release, but that squad must still reallocate its retained plan.
        if (squad != null) squad.setControlledMember(0L, battle);
    }

    private void clearMechDrive(long id) {
        if (roster.world().hasMechLoadout(id)) roster.world().mechLoadout(id).manualDrive.clear();
        if (roster.entityWorld().has(id, roster.components().MECH_LOCOMOTION)) {
            roster.entityWorld().setFloat(id, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY, 0f);
        }
    }

    private void clearOwnedWork(long id) {
        clearMechDrive(id);
        if (!roster.isAliveById(id)) return;
        if (roster.movement().has(id)) battle.clearPath(id);
        if (roster.combat().has(id)) roster.combat().clearPrimaryFire(id);
        if (roster.world().hasMechLoadout(id)) {
            battle.cancelMechMoveOrder(id);
            var loadout = roster.world().mechLoadout(id);
            loadout.clearQueuedFire();
            loadout.routeIntent.reset();
            loadout.collisionEscapeActive = false;
            loadout.collisionStallSeconds = 0f;
            loadout.collisionBestRemainingDistance = Float.POSITIVE_INFINITY;
            loadout.collisionProgressDestX = Integer.MIN_VALUE;
            loadout.collisionProgressDestY = Integer.MIN_VALUE;
            loadout.collisionProgressPointX = Float.NaN;
            loadout.collisionProgressPointY = Float.NaN;
        }
    }

    /** Run before replanning and after lifecycle phases, including on paused advances. */
    public void validate() {
        if (ship != null) {
            if (!canEnterWarship(ship)) exit();
            return;
        }
        if (active() && (!eligible(unitId) || (!vehicleControl && controlledSquad != battle.squadOf(unitId)))) exit();
        if (active() && !canSelectWeapon(selectedWeapon)) {
            selectedWeapon = 0;
            releaseSelectedFire();
        }
    }

    /**
     * One tick of the pilot's hands on the hull: the ship flies and lays ground from the
     * same snapshot a Marine would be driven by, and the warship system, later in the same
     * tick, does the moving and the firing.
     */
    private void tickWarship() {
        ManualIntent input = intent;
        WarshipPilot.apply(ship, new WarshipPilot.Hands(input.moveX(), input.moveY(),
                        input.aimX(), input.aimY(), input.firing(), input.secondary()),
                battle.getGrid().getWidth(), battle.getGrid().getHeight(), revealed);
    }

    /**
     * The right button is a Marine's secondary equipment: on the press it throws the carried smoke
     * at the cursor, and a Marine with no smoke raises a directional shield toward it. It is the
     * same one-shot request as the G and E keys, so ammunition, cooldowns and the committed throw
     * stay the equipment's own; holding the button never repeats it. A Marine with neither does nothing.
     */
    private void requestSecondaryEquipment(ManualIntent input) {
        boolean pressed = input.secondary() && !secondaryHeld;
        secondaryHeld = input.secondary();
        if (!pressed) return;
        PointFireAim aim = new PointFireAim(input.aimX(), input.aimY());
        if (canUseAbility(DirectControlAbility.SMOKE)) pendingAbilities.put(DirectControlAbility.SMOKE, aim);
        else if (canUseAbility(DirectControlAbility.SHIELD)) pendingAbilities.put(DirectControlAbility.SHIELD, aim);
    }

    /** Exactly once at UPDATE_UNITS, before autonomous workers are dispatched. */
    public void tick() {
        validate();
        long activeMarine = unitActive() && !vehicleControl
                && roster.identity().type(unitId) == UnitType.MARINE ? unitId : 0L;
        marineSprint.tickInactive(activeMarine, BattleSimulation.TICK_DT, roster::isAliveById);
        if (ship != null) {
            tickWarship();
            return;
        }
        if (!unitActive() || vehicleControl) { secondaryHeld = false; return; } // GroundSystem owns vehicle movement and turret clocks.
        long id = unitId;
        ManualIntent input = intent;
        if (roster.identity().type(id).isMech()) {
            secondaryHeld = false;
            roster.movement().moveDirectMech(id, battle.getGrid(), input.moveX(), input.moveY(),
                    battle.physicalRadius(id), BattleSimulation.TICK_DT, input.aimX(), input.aimY());
            return; // HeavyWeapons owns every mount clock and trigger in its serial pass.
        }
        InfantryUnitPrep.tickCooldowns(id, roster.world());
        requestSecondaryEquipment(input);
        PointFireAim shield = pendingAbilities.remove(DirectControlAbility.SHIELD);
        if (shield != null && roster.integralSystems().spec(id) != null
                && roster.integralSystems().spec(id).grantsMitigation()) {
            if (roster.integralSystems().activate(id)) {
                MitigationSystem.faceManual(id, roster.mitigations(), roster.world(), shield);
            }
        }
        PointFireAim smoke = pendingAbilities.remove(DirectControlAbility.SMOKE);
        if (smoke != null) SmokeTactics.beginThrow(id, smoke, true, battle);
        if (ownsSmokeChannel(id) && InfantryUnitPrep.tickAimAndShortCircuit(id, battle)) {
            marineSprint.finishStep(id, input.sprint(), 0f, roster.movement().moveSpeed(id),
                    BattleSimulation.TICK_DT);
            roster.combat().clearPrimaryFire(id);
            roster.movement().moveDirect(id, battle.getGrid(), 0f, 0f,
                    battle.physicalRadius(id), BattleSimulation.TICK_DT);
            MitigationSystem.faceManual(id, roster.mitigations(), roster.world(), pointAim());
            return;
        }
        float speed = roster.movement().moveSpeed(id);
        float scale = marineSprint.speedScale(id, input.sprint(), BattleSimulation.TICK_DT);
        ManualTerrainMotion.Result normal = scale > 1f
                ? roster.movement().previewDirect(id, battle.getGrid(), input.moveX(), input.moveY(),
                        battle.physicalRadius(id), BattleSimulation.TICK_DT) : null;
        ManualTerrainMotion.Result applied = roster.movement().moveDirectScaled(id, battle.getGrid(),
                input.moveX(), input.moveY(), battle.physicalRadius(id), BattleSimulation.TICK_DT, scale);
        float bonusDistance = normal == null ? 0f : Math.max(0f,
                (float) Math.hypot(applied.dx(), applied.dy())
                        - (float) Math.hypot(normal.dx(), normal.dy()));
        boolean sprinting = marineSprint.finishStep(id, input.sprint(), bonusDistance,
                speed, BattleSimulation.TICK_DT);
        if (sprinting) roster.combat().clearPrimaryFire(id);
        MitigationSystem.faceManual(id, roster.mitigations(), roster.world(), pointAim());
        if (input.firing() && !sprinting) {
            boolean moving = roster.movement().velX(id) != 0f || roster.movement().velY(id) != 0f;
            roster.combat().setPointFireIntent(id, new PointFireAim(input.aimX(), input.aimY()),
                    FireStance.stanceFor(moving));
        }
    }
}
