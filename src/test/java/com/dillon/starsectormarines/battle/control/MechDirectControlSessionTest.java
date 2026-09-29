package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Session ownership on bare chassis components, independent of full battle scheduling. */
class MechDirectControlSessionTest {
    private final NavigationGrid grid = new NavigationGrid(24, 16);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(24, 16), null);
    private final int squadId = roster.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
    private final Squad squad = roster.getSquad(squadId);
    private boolean unavailable, complete, detached;
    private int cancelledMoves;
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "physicalRadius" -> roster.radius((long) args[0]);
                case "getShelvedSquadDirective" -> null;
                case "squadOf" -> detached ? null : roster.getSquad(roster.squad().squadId((long) args[0]));
                case "squad" -> roster.squad();
                case "world" -> roster.world();
                case "resolveUnit" -> roster.isAliveById((long) args[0]) ? args[0] : 0L;
                case "squadMemberCount" -> roster.squadMemberCount((int) args[0]);
                case "squadMemberAt" -> roster.squadMemberArray((int) args[0])[(int) args[1]];
                case "isRiding" -> roster.isRiding((long) args[0]);
                case "cancelMechMoveOrder" -> { cancelledMoves++; yield null; }
                case "clearPath" -> {
                    long id = (long) args[0];
                    roster.movement().setPathRef(id, new int[0]);
                    roster.movement().setPathIdx(id, 0);
                    yield null;
                }
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster,
            id -> unavailable, () -> complete);

    MechDirectControlSessionTest() {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        roster.setNavigationGrid(grid);
    }

    @Test
    void combatChassisEntersAndHandsBackWithoutChangingItsLanceMission() {
        long mech = spawn();
        squad.leaderId = mech;
        var assignment = ObjectiveAssignment.attackMove(squadId, 20, 5);
        squad.assignedObjective = assignment;
        roster.world().mechLoadout(mech).collisionEscapeActive = true;
        assertTrue(session.enter(mech));
        assertFalse(roster.world().mechLoadout(mech).collisionEscapeActive);
        assertEquals(mech, session.controlledMechId());
        assertEquals(mech, squad.controlledMemberId());
        assertEquals(mech, squad.leaderId);
        assertEquals(1, squad.aliveMembers);
        assertEquals(1, cancelledMoves);
        session.exit();
        assertEquals(0L, squad.controlledMemberId());
        assertSame(assignment, squad.assignedObjective);
        assertEquals(2, cancelledMoves);
    }

    @Test
    void absentLoadoutRescueAndIllegalBodyCannotEnter() {
        long mech = spawn();
        squad.rescuePickupMech = true;
        assertFalse(session.enter(mech));
        squad.rescuePickupMech = false;
        roster.world().setPos(mech, .5f, 5.5f);
        assertFalse(session.enter(mech), "Bulwark extends beyond the map");
        roster.world().setPos(mech, 4.5f, 5.5f);
        assertTrue(session.canEnter(mech));
        roster.world().removeMechLoadout(mech);
        assertFalse(session.enter(mech));
        long frame = roster.spawn(new EntitySpec("frame", Faction.MARINE, UnitType.MACHINE_FRAME, 8, 5)
                .mechVariant(MechVariant.BULWARK));
        assertFalse(session.enter(frame));
    }

    @Test
    void inputSuspensionAndHandbackCancelRoundsButPreserveMountResources() {
        long mech = spawn();
        var loadout = roster.world().mechLoadout(mech);
        var mount = loadout.mounts()[0];
        assertTrue(session.enter(mech));
        mount.cooldown = 3f;
        mount.replenishmentProgressSeconds = 1.2f;
        int ammo = mount.ammo;
        mount.burstRemaining = 2;
        mount.burstPointAim = new PointFireAim(20, 5);
        session.submit(new ManualIntent(1, 0, 20, 5, true, true));
        session.tick();
        assertEquals(MarineSprint.EMPTY, session.sprintStatus(), "Mechs do not acquire Marine stamina");
        assertEquals(3f, mount.cooldown, "session cannot tick the heavy-weapon clock");
        assertEquals(ammo, mount.ammo, "session cannot spend a mount trigger");
        session.suspendInput();
        assertEquals(0, mount.burstRemaining);
        assertNull(mount.burstPointAim);
        assertEquals(3f, mount.cooldown);
        assertEquals(1.2f, mount.replenishmentProgressSeconds);
        mount.burstRemaining = 1;
        mount.burstPointAim = new PointFireAim(18, 5);
        session.exit();
        assertEquals(0, mount.burstRemaining);
        assertNull(session.pointAim());
        assertEquals(ammo, mount.ammo);
    }

    @Test
    void existingSurvivalAndLifecycleAuthorityReleaseTheChassis() {
        long mech = spawn();
        assertTrue(session.enter(mech));
        squad.moraleBroken = true;
        session.validate();
        assertFalse(session.active());
        squad.moraleBroken = false;
        assertTrue(session.enter(mech));
        squad.assignedObjective = ObjectiveAssignment.withdraw(squadId, 1, 5);
        session.validate();
        assertFalse(session.active());
        squad.assignedObjective = null;
        assertTrue(session.enter(mech));
        squad.fallbackInProgress = true;
        session.validate();
        assertFalse(session.active());
        squad.fallbackInProgress = false;
        assertTrue(session.enter(mech));
        unavailable = true;
        session.validate();
        assertFalse(session.active());
        unavailable = false;
        assertTrue(session.enter(mech));
        detached = true;
        session.validate();
        assertFalse(session.active());
        assertEquals(0L, squad.controlledMemberId(), "original lance is released after membership changes");
    }

    @Test
    void personalMoraleStillContributesThroughTheSharedSquadSurvivalRule() {
        long mech = spawn();
        roster.world().mechLoadout(mech).moraleBroken = true;
        assertTrue(session.canEnter(mech), "personal morale alone is not the AI survival trigger");
        squad.moraleBroken = true;
        assertFalse(session.canEnter(mech));
    }

    @Test
    void selectorValidatesDirectEquipmentAndCancelsBurstsWithoutChangingResourcesOrMovement() {
        long mech = spawn();
        var loadout = new MechLoadoutComponent(MechVariant.BULWARK, MechWeaponComponent.DUAL_CHAINGUNS,
                MechWeaponComponent.SRM_5, MechWeaponComponent.LRM_5, MechRole.BALANCED);
        roster.world().attachMechLoadout(mech, loadout);
        assertFalse(session.selectWeapon(1), "inactive session cannot retain a choice");
        assertTrue(session.enter(mech));
        assertEquals(0, session.selectedWeapon());
        var arms = loadout.mount(MechMountSlot.ARMS);
        var srm = loadout.mount(MechMountSlot.LEFT_SHOULDER);
        arms.burstRemaining = 3;
        arms.burstPointAim = new PointFireAim(20, 5);
        srm.cooldown = 3f;
        srm.ammo = 0;
        srm.replenishmentProgressSeconds = 1.2f;
        srm.burstRemaining = 1;
        srm.burstPointAim = new PointFireAim(20, 5);
        session.submit(new ManualIntent(1, -1, 20, 5, true));
        assertTrue(session.canSelectWeapon(2), "an empty rack remains selectable");
        assertTrue(session.selectWeapon(2));
        assertEquals(2, session.selectedWeapon());
        assertEquals(0, arms.burstRemaining);
        assertEquals(0, srm.burstRemaining);
        assertEquals(3f, srm.cooldown);
        assertEquals(0, srm.ammo);
        assertEquals(1.2f, srm.replenishmentProgressSeconds);
        assertEquals(1f, session.intent().moveX());
        assertEquals(-1f, session.intent().moveY());
        assertEquals(20f, session.intent().aimX());
        assertFalse(session.intent().firing());
        for (int invalid : new int[]{-1, 3, 4}) assertFalse(session.selectWeapon(invalid));
        assertEquals(2, session.selectedWeapon(), "indirect and invalid choices cannot change selection");
        session.suspendInput();
        assertEquals(2, session.selectedWeapon(), "pause and chrome suspension retain equipment choice");
        session.exit();
        assertEquals(0, session.selectedWeapon());
        assertTrue(session.enter(mech));
        assertEquals(0, session.selectedWeapon());
    }

    @Test
    void lostSelectedHardpointReturnsToAllAndReleasesFireAtValidationBoundary() {
        long mech = spawn();
        assertTrue(session.enter(mech));
        assertTrue(session.selectWeapon(1));
        var replacement = new MechLoadoutComponent(MechVariant.BULWARK, null,
                MechWeaponComponent.SRM_5, null, MechRole.BALANCED);
        roster.world().attachMechLoadout(mech, replacement);
        replacement.mount(MechMountSlot.LEFT_SHOULDER).burstRemaining = 1;
        session.submit(new ManualIntent(1, 0, 20, 5, true));
        session.validate();
        assertTrue(session.active());
        assertEquals(0, session.selectedWeapon());
        assertFalse(session.intent().firing());
        assertEquals(0, replacement.mount(MechMountSlot.LEFT_SHOULDER).burstRemaining);
        assertFalse(session.canSelectWeapon(1));
        assertTrue(session.canSelectWeapon(2));
        assertFalse(session.canSelectWeapon(3));
    }

    private long spawn() {
        long id = roster.spawn(MechVariant.BULWARK.applyTo(new EntitySpec(
                "Bulwark", Faction.MARINE, UnitType.HEAVY_MECH, 4, 5).squad(squadId)));
        roster.world().attachMechLoadout(id, MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
        squad.aliveMembers++;
        return id;
    }
}
