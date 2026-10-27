package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.MitigationSystem;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.FlameJet;
import com.dillon.starsectormarines.battle.infantry.FragGrenadeTactics;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.infantry.SmokeTactics;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.battle.tender.TenderSystem;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.vehicle.BicycleBody;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleFireGroups;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipPilot;
import com.dillon.starsectormarines.battle.warship.WarshipSystem;
import com.dillon.starsectormarines.battle.weapon.MagazineDef;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.EnumMap;
import java.util.List;
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
    /** A tender's chosen build option, by number key; nothing else keeps a number-key choice. */
    private int selectedWeapon;
    /** What each of a mech's or a vehicle's triggers is on; a Marine's buttons keep their own picks below. */
    private TriggerPicks picks = TriggerPicks.LINKED;
    /** A Marine's right-button pick; null is its list's first item. */
    private MarineRightTrigger.Item marineRight;
    /**
     * Whether the controlled Marine's current grenade channel is one the player started. The battle drops the
     * throw's reservation at the release, so the channel's follow-through is known only here.
     */
    private boolean manualGrenade;
    /** A Marine's left button: the primary, or a direct-fire special under its manual contract. */
    private final MarineLeftTrigger marineLeft = new MarineLeftTrigger();
    private final EnumMap<DirectControlAbility, PointFireAim> pendingAbilities =
            new EnumMap<>(DirectControlAbility.class);
    private final MarineSprint marineSprint = new MarineSprint();
    /** Whether the right button was down on the previous Marine tick; a press, not a hold, uses equipment. */
    private boolean secondaryHeld;
    /** Whether the left button was down on the previous Marine tick; a semi-automatic primary fires on a press. */
    private boolean primaryHeld;

    /** The side the player flies for. */
    private static final Faction PLAYER_SIDE = Faction.MARINE;
    /** What the four number keys do at the helm of a warship, in key order. */
    private static final WarshipPilot.Command[] WARSHIP_COMMANDS = {
            WarshipPilot.Command.HOLD_FIRE, WarshipPilot.Command.TOGGLE_DIRECTOR,
            WarshipPilot.Command.CYCLE_MISSILE_PRESET, WarshipPilot.Command.CYCLE_GROUND_FIRE};
    private final WarshipSystem warships;
    private final TenderSystem tenders;
    private BiPredicate<Integer, Integer> revealed;
    private Warship ship;
    /** The tender whose hull is {@link #ship}, when the hull under control is one; its helm is the tender's. */
    private Tender tender;
    private final TenderPilot.Pad tenderPad = new TenderPilot.Pad();

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
        this(battle, roster, unavailable, complete, warships, null, revealed);
    }

    /** As above, with the tenders whose hulls are among {@code warships}: a tender hull is flown as a tender. */
    public DirectControlSession(BattleControl battle, UnitRosterService roster,
                                LongPredicate unavailable, BooleanSupplier complete,
                                WarshipSystem warships, TenderSystem tenders,
                                BiPredicate<Integer, Integer> revealed) {
        this.tenders = tenders;
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
    /** The tender under control, or null when nothing is, or the hull is an ordinary warship. */
    public Tender controlledTender() { return tender; }
    /** What a tender pilot's hands remember: the last placement notice. */
    public TenderPilot.Pad tenderPad() { return tenderPad; }
    /** The option the tender pilot has selected, or null when the chosen number key has no option. */
    public BuildOption selectedBuildOption() { return tender == null ? null : TenderPilot.option(selectedWeapon); }
    public boolean isControlling(long id) { return id != 0L && id == unitId; }
    public ManualIntent intent() { return intent; }
    /** A tender's selected number key; 0 for every other carrier. */
    public int selectedWeapon() { return selectedWeapon; }

    /** What each trigger of the controlled mech or vehicle is on; LINKED on both for every other carrier. */
    public TriggerPicks triggerPicks() { return picks; }

    /**
     * The controlled machine's weapon mounts ({@link FireGroups}): a mech's hardpoints or a hull's turrets. Null
     * for a Marine, a warship, or nothing under control.
     */
    private FireGroups.Mount[] machineMounts() {
        if (!unitActive() || !roster.isAliveById(unitId)) return null;
        if (vehicleControl) {
            return VehicleFireGroups.mounts(roster.convoy().vehicleType(unitId), roster.convoy().turret(unitId));
        }
        return roster.world().hasMechLoadout(unitId)
                ? MechFireGroups.mounts(roster.world().mechLoadout(unitId).mounts()) : null;
    }

    /** Changes whenever any trigger or number-key choice does, so the host knows to release held fire. */
    public int selectionKey() {
        return (((selectedWeapon * 31 + picks.left()) * 31 + picks.right()) * 31 + marineLeft.pick()) * 31
                + (marineRight == null ? 0 : marineRight.ordinal() + 1);
    }

    /** The controlled Marine's right-button list, empty for every other carrier. */
    public List<MarineRightTrigger.Item> marineRightMembers() {
        if (!unitActive() || vehicleControl || !roster.isAliveById(unitId)
                || roster.identity().type(unitId) != UnitType.MARINE) return List.of();
        World world = roster.world();
        return MarineRightTrigger.members(world.hasSecondaryWeapon(unitId) ? world.specialEquipment(unitId) : null,
                world.hasBelt(unitId) ? world.beltEquipment(unitId) : null, roster.integralSystems().spec(unitId));
    }

    /** What the controlled Marine's right button uses now, or null when it has nothing on it. */
    public MarineRightTrigger.Item marineRightPick() {
        return MarineRightTrigger.resolve(marineRightMembers(), marineRight);
    }

    /** What the controlled Marine's left button is on, its readying and its last round; primary otherwise. */
    public MarineLeftTrigger marineLeft() { return marineLeft; }

    /**
     * The direct-fire special the controlled Marine's left button is on, or null while it is on the
     * primary or nothing is a Marine.
     */
    public SpecialEquipmentDef leftSpecial() {
        if (!marineLeft.onSpecial()) return null;
        List<SpecialEquipmentDef> specials = marineSpecials();
        int index = marineLeft.pick() - 1;
        return index < specials.size() ? specials.get(index) : null;
    }

    /** The specials the controlled Marine's left button cycles through, after the primary. */
    public List<SpecialEquipmentDef> marineSpecials() {
        if (!controllingMarine()) return List.of();
        World world = roster.world();
        // Both slots, so a launcher swapped onto the belt for a belt throw stays on the list.
        return MarineLeftTrigger.specials(world.hasSecondaryWeapon(unitId) ? world.specialEquipment(unitId) : null,
                world.hasBelt(unitId) ? world.beltEquipment(unitId) : null);
    }

    /** Puts a Marine's left button on {@code member}: the primary (0), or a special, which it readies. */
    private boolean pickMarineLeft(int member) {
        List<SpecialEquipmentDef> specials = marineSpecials();
        int before = marineLeft.pick();
        float aim = member > 0 && member <= specials.size() ? specials.get(member - 1).aimDuration() : 0f;
        if (!marineLeft.pick(member, specials.size() + 1, aim)) return false;
        if (marineLeft.pick() != before) releaseSelectedFire();
        return true;
    }

    private boolean controllingMarine() {
        return unitActive() && !vehicleControl && roster.isAliveById(unitId)
                && roster.identity().type(unitId) == UnitType.MARINE;
    }

    /** Every Marine's battle stamina, which a running squad spends too ({@code SquadRunSystem}). */
    public MarineSprint marineStamina() {
        return marineSprint;
    }

    /** Read-only status for the exact controlled Marine; other carriers have no stamina gauge. */
    public MarineSprint.Status sprintStatus() {
        return unitActive() && roster.isAliveById(unitId)
                && roster.identity().type(unitId) == UnitType.MARINE
                ? marineSprint.status(unitId) : MarineSprint.EMPTY;
    }

    public boolean canSelectWeapon(int selection) {
        // A tender's number keys choose what to build; a slot with no option behind it is refused.
        if (tender != null) return TenderPilot.option(selection) != null;
        // A warship has no weapon to choose: its four number keys are commands (see WARSHIP_COMMANDS).
        if (ship != null) return selection >= 0 && selection < WARSHIP_COMMANDS.length;
        if (!unitActive() || !roster.isAliveById(unitId)) return false;
        // Key 1 is the left trigger's first member, LINKED; a single-member list has nothing else.
        if (selection == 0) return true;
        if (controllingMarine()) return selection > 0 && selection <= marineSpecials().size();
        FireGroups.Mount[] mounts = machineMounts();
        return mounts != null && FireGroups.member(mounts, FireTrigger.LEFT, selection) >= 0;
    }

    /**
     * Serialized player choice. On a ground body a number key jumps the left trigger to that member
     * of its list; switching releases queued rounds without changing installed equipment.
     */
    public boolean selectWeapon(int selection) {
        if (!canSelectWeapon(selection)) return false;
        if (tender != null) {
            selectedWeapon = selection;
            return true;
        }
        if (ship != null) return WarshipPilot.command(ship, WARSHIP_COMMANDS[selection]);
        if (controllingMarine()) return pickMarineLeft(selection);
        FireGroups.Mount[] mounts = machineMounts();
        if (mounts == null) return true;
        int member = FireGroups.member(mounts, FireTrigger.LEFT, selection);
        return member >= 0 && pick(FireTrigger.LEFT, member);
    }

    /**
     * Moves a trigger to the next member of its list (Q for the left button, E for the right).
     * Refused when the list has fewer than two members. A Marine's left list is the primary and then
     * its direct-fire specials.
     */
    public boolean cycle(FireTrigger trigger) {
        var marineItems = trigger == FireTrigger.RIGHT ? marineRightMembers() : List.<MarineRightTrigger.Item>of();
        if (!marineItems.isEmpty()) {
            if (marineItems.size() < 2) return false;
            marineRight = MarineRightTrigger.next(marineItems, marineRight);
            releaseSelectedFire();
            return true;
        }
        if (trigger == FireTrigger.LEFT && controllingMarine()) {
            List<SpecialEquipmentDef> specials = marineSpecials();
            if (specials.isEmpty()) return false;
            return pickMarineLeft((marineLeft.pick() + 1) % (specials.size() + 1));
        }
        FireGroups.Mount[] mounts = machineMounts();
        if (trigger == null || mounts == null || FireGroups.members(mounts, trigger).length < 2) return false;
        return pick(trigger, FireGroups.next(mounts, trigger, picks.pick(trigger)));
    }

    /**
     * Puts a trigger on one member of its list: LINKED, or a mount of its group: a mech hardpoint
     * ({@code slot.ordinal() + 1}) or a hull's mount ({@link VehicleFireGroups}).
     * A Marine's right button takes the index of an item in {@link #marineRightMembers()}.
     */
    public boolean pick(FireTrigger trigger, int member) {
        var marineItems = trigger == FireTrigger.RIGHT ? marineRightMembers() : List.<MarineRightTrigger.Item>of();
        if (!marineItems.isEmpty()) {
            if (member < 0 || member >= marineItems.size()) return false;
            if (marineRightPick() == marineItems.get(member)) return true;
            marineRight = marineItems.get(member);
            releaseSelectedFire();
            return true;
        }
        if (trigger == FireTrigger.LEFT && controllingMarine()) return pickMarineLeft(member);
        FireGroups.Mount[] mounts = machineMounts();
        if (trigger == null || mounts == null || !FireGroups.isMember(mounts, trigger, member)) return false;
        if (picks.pick(trigger) == member) return true;
        picks = picks.with(trigger, member);
        releaseSelectedFire();
        return true;
    }

    private void releaseSelectedFire() {
        intent = new ManualIntent(intent.moveX(), intent.moveY(), intent.aimX(), intent.aimY(), false);
        if (roster.world().hasMechLoadout(unitId)) roster.world().mechLoadout(unitId).clearQueuedFire();
        if (vehicleControl) battle.suspendVehicleDirectInput(unitId);
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
        if (ship != null) {
            return ability == DirectControlAbility.SHIELD && ship.hasShield()
                    || ability == DirectControlAbility.CANCEL_BUILDS && tender != null
                    || ability == DirectControlAbility.LOW_PASS && tender == null && !ship.lowPassing()
                            && !ship.departing();
        }
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
            case GRENADE -> FragGrenadeTactics.canThrow(unitId, battle);
            case RELOAD -> {
                CombatService combat = roster.combat();
                int size = combat.magazineSize(unitId);
                yield size > 0 && !combat.isReloading(unitId) && combat.roundsLoaded(unitId) < size;
            }
            case CANCEL_BUILDS, LOW_PASS -> false;
        };
    }

    /** Bounded one-shot mailbox, consumed at the fixed simulation boundary before movement. */
    public boolean requestAbility(DirectControlAbility ability, PointFireAim aim) {
        // The ship's shield key is a toggle the pilot owns, not a charge: it takes effect at once.
        if (ship != null) {
            if (!canUseAbility(ability)) return false;
            if (ability == DirectControlAbility.LOW_PASS) {
                return aim != null && WarshipPilot.lowPass(ship,
                        new WarshipPilot.Hands(0f, 0f, aim.x(), aim.y(), false, false),
                        battle.getGrid().getWidth(), battle.getGrid().getHeight(), revealed);
            }
            return ability == DirectControlAbility.CANCEL_BUILDS ? TenderPilot.cancelBuilds(tender)
                    : WarshipPilot.command(ship, WarshipPilot.Command.TOGGLE_SHIELD);
        }
        if (ability == DirectControlAbility.RELOAD) {
            if (!canUseAbility(ability)) return false;
            pendingAbilities.put(ability, null);
            return true;
        }
        if (!canUseAbility(ability) || aim == null || !Float.isFinite(aim.x()) || !Float.isFinite(aim.y())) {
            return false;
        }
        pendingAbilities.put(ability, aim);
        return true;
    }

    /** Whether the controlled Marine is in the committed channel of a throw the player started: smoke or a grenade. */
    private boolean ownsThrowChannel(long id) {
        if (!isControlling(id) || !roster.world().hasSecondaryWeapon(id)) return false;
        var special = roster.world().specialEquipment(id);
        if (special.activation() == SpecialActivation.UTILITY_SMOKE) {
            var commit = roster.world().smokeThrowCommit(id);
            return commit != null && commit.manual();
        }
        return manualGrenade && special.isThrownGrenade() && roster.world().secondaryActionTimer(id) > 0f;
    }

    /** Whether the controlled Marine is in the committed channel of a throw the player started. */
    public boolean manualThrowActive() { return unitActive() && ownsThrowChannel(unitId); }

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
                roster.world().renderY(id), battle.physicalRadius(id));
    }

    private boolean eligible(long id) {
        if (id == 0L || complete.getAsBoolean() || !roster.isAliveById(id)) return false;
        World world = roster.world();
        if (roster.convoy().isVehicle(id)) {
            var convoy = roster.convoy();
            var mission = convoy.mission(id);
            // Any hull the player owns, whatever its type: owning is the Marine faction. Every vehicle the
            // player's force puts on the field (a bay drop, a granted drop, a works the company runs) is
            // spawned Marine, and every hull it only escorts or fights beside is spawned Ally, enlisted or not.
            return convoy.faction(id) == Faction.MARINE && world.hp(id) > 0f
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
        if (world.hasSecondaryWeapon(id) && world.secondaryActionTimer(id) > 0f && !ownsThrowChannel(id)) return false;
        Squad squad = battle.squadOf(id);
        if (mech && (!world.hasMechLoadout(id) || squad == null
                || squad.faction != Faction.MARINE || !squad.isMechSquad()
                || squad.rescuePickupMech
                || !roster.entityWorld().has(id, roster.components().MECH_LOCOMOTION))) return false;
        if (squad == null) return true;
        ObjectiveAssignment assignment = squad.assignedObjective;
        CommandDirective shelved = battle.getShelvedSquadDirective(squad.id);
        return (assignment == null || assignment.kind() != AssignmentKind.WITHDRAW)
                && (shelved == null || shelved.assignment() == null
                        || shelved.assignment().kind() != AssignmentKind.WITHDRAW)
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
        picks = TriggerPicks.LINKED;
        marineRight = null;
        manualGrenade = false;
        marineLeft.reset();
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
        tender = tenders == null ? null : tenders.of(candidate);
        tenderPad.reset();
        selectedWeapon = 0;
        intent = ManualIntent.NEUTRAL;
        pendingAbilities.clear();
        if (tender != null) tender.takeHelm();
        else candidate.takeHelm();
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
        marineLeft.suspend();
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
        primaryHeld = false;
        if (ship != null) {
            Warship released = ship;
            Tender releasedTender = tender;
            ship = null;
            tender = null;
            tenderPad.reset();
            selectedWeapon = 0;
            pendingAbilities.clear();
            intent = ManualIntent.NEUTRAL;
            if (releasedTender != null) releasedTender.releaseHelm();
            else released.releaseHelm();
            return;
        }
        long previous = unitId;
        if (previous == 0L) return;
        boolean vehicle = vehicleControl;
        unitId = 0L;
        selectedWeapon = 0;
        picks = TriggerPicks.LINKED;
        marineRight = null;
        manualGrenade = false;
        marineLeft.reset();
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
        // A right-button item the Marine no longer carries returns the button to its first item.
        if (marineRight != null && !marineRightMembers().contains(marineRight)) {
            marineRight = null;
            releaseSelectedFire();
        }
        // A special no longer carried returns a Marine's left button to the primary.
        if (marineLeft.onSpecial() && leftSpecial() == null) {
            marineLeft.reset();
            releaseSelectedFire();
        }
        // A picked mount that was destroyed, or moved groups with it, returns its trigger to LINKED.
        FireGroups.Mount[] mounts = machineMounts();
        if (mounts != null) {
            for (FireTrigger trigger : FireTrigger.values()) {
                if (picks.pick(trigger) != FireGroups.LINKED
                        && !FireGroups.isMember(mounts, trigger, picks.pick(trigger))) {
                    picks = picks.with(trigger, FireGroups.LINKED);
                    releaseSelectedFire();
                }
            }
        }
    }

    /**
     * One tick of the pilot's hands on the hull: the ship flies and lays ground from the
     * same snapshot a Marine would be driven by, and the warship system, later in the same
     * tick, does the moving and the firing.
     */
    private void tickWarship() {
        ManualIntent input = intent;
        if (tender != null) {
            TenderPilot.apply(tender, new TenderPilot.Hands(input.moveX(), input.moveY(),
                            input.aimX(), input.aimY(), input.firing(), input.secondary()),
                    tenderPad, TenderPilot.option(selectedWeapon), battle.getGrid(), revealed);
            return;
        }
        WarshipPilot.apply(ship, new WarshipPilot.Hands(input.moveX(), input.moveY(),
                        input.aimX(), input.aimY(), input.firing(), input.secondary()),
                battle.getGrid().getWidth(), battle.getGrid().getHeight(), revealed);
    }

    /**
     * The right button uses the Marine's right-button pick (smoke, a hand grenade or the shield; E moves
     * it) on the press, at the cursor. It is the same one-shot request as the G and C keys, so ammunition,
     * cooldowns and the committed throw stay the equipment's own; holding the button never repeats it,
     * and a pick that cannot be used now does nothing rather than falling back to another item.
     */
    private void requestSecondaryEquipment(ManualIntent input) {
        boolean pressed = input.secondary() && !secondaryHeld;
        secondaryHeld = input.secondary();
        if (!pressed) return;
        MarineRightTrigger.Item item = marineRightPick();
        if (item != null && canUseAbility(item.ability)) {
            pendingAbilities.put(item.ability, new PointFireAim(input.aimX(), input.aimY()));
        }
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
        if (pendingAbilities.containsKey(DirectControlAbility.RELOAD)) {
            pendingAbilities.remove(DirectControlAbility.RELOAD);
            roster.combat().beginReload(id);
        }
        PointFireAim smoke = pendingAbilities.remove(DirectControlAbility.SMOKE);
        if (smoke != null) SmokeTactics.beginThrow(id, smoke, true, battle);
        PointFireAim grenade = pendingAbilities.remove(DirectControlAbility.GRENADE);
        if (grenade != null && FragGrenadeTactics.beginManualThrow(id, grenade, battle)) manualGrenade = true;
        if (ownsThrowChannel(id) && InfantryUnitPrep.tickAimAndShortCircuit(id, battle)) {
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
        if (marineLeft.onSpecial()) {
            fireLeftSpecial(id, input, sprinting);
            return;
        }
        if (primaryTrigger(id, input.firing()) && !sprinting) {
            boolean moving = roster.movement().velX(id) != 0f || roster.movement().velY(id) != 0f;
            roster.combat().setPointFireIntent(id, new PointFireAim(input.aimX(), input.aimY()),
                    FireStance.stanceFor(moving));
        }
    }

    /**
     * The left button on a special: a press fires one round at the cursor through the special's own
     * point-fire path once it is readied, off cooldown and loaded. The flamer is a held stream instead: while
     * the button is down it sends a charge toward the cursor, short of the first wall and no further than its
     * range, each time its cooldown allows. The primary holds meanwhile.
     */
    private void fireLeftSpecial(long id, ManualIntent input, boolean sprinting) {
        primaryHeld = input.firing();
        roster.combat().clearPrimaryFire(id);
        SpecialEquipmentDef special = leftSpecial();
        World world = roster.world();
        boolean canFire = special != null && world.specialEquipment(id) == special
                && world.secondaryCooldownTimer(id) <= 0f
                && world.secondaryAmmo(id) > 0 && Float.isFinite(input.aimX()) && Float.isFinite(input.aimY());
        boolean stream = special != null && special.isHeldStream();
        float[] jet = canFire && stream ? FlameJet.point(battle.getGrid(), world.renderX(id), world.renderY(id),
                input.aimX(), input.aimY(), special.range()) : null;
        if (stream) canFire &= jet != null;
        if (!marineLeft.tick(input.firing(), sprinting, canFire, stream, BattleSimulation.TICK_DT)) return;
        if (stream) {
            // The flamer's own release spends one charge of fuel; a held button sends the next on its cooldown.
            int fuel = world.secondaryAmmo(id);
            battle.throwFragmentationGrenade(id, jet[0], jet[1]);
            if (world.secondaryAmmo(id) >= fuel) return;
        } else if (!battle.fireSecondaryAtPoint(id, input.aimX(), input.aimY())) {
            return;
        }
        world.setSecondaryCooldownTimer(id, special.cooldown());
        marineLeft.fired();
    }

    /**
     * Whether the controlled Marine's primary trigger asks for a round this tick. A held trigger asks
     * every tick, and the firing gate paces it to the weapon's cycle. A semi-automatic magazine fires
     * once per press with no cycle between rounds (owner, 2026-10-23): a press with a round loaded
     * clears the action's cooldown, so the player's finger is the rate of fire. The magazine reloads
     * on its own only when it runs dry, or on the reload key.
     */
    private boolean primaryTrigger(long id, boolean firing) {
        boolean pressed = firing && !primaryHeld;
        primaryHeld = firing;
        CombatService combat = roster.combat();
        MagazineDef magazine = combat.primaryMagazine(id);
        if (magazine == null || magazine.trigger() != MagazineDef.Trigger.SEMI) return firing;
        if (!pressed || !combat.mayFirePrimary(id) || combat.burstRemaining(id) > 0) return false;
        combat.setCooldownTimer(id, 0f);
        return true;
    }
}
