package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** A convoy body can share ownership without acquiring infantry capabilities or clocks. */
class VehicleDirectControlSessionTest {
    private final NavigationGrid grid = new NavigationGrid(32, 24);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(32, 24), null);
    private boolean complete, unavailable, accept = true;
    private int entries, exits, suspensions;
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "beginVehicleDirectControl" -> { entries++; yield accept; }
                case "endVehicleDirectControl" -> { exits++; yield null; }
                case "suspendVehicleDirectInput" -> { suspensions++; yield null; }
                default -> throw new AssertionError("Unexpected infantry/battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster,
            id -> unavailable, () -> complete);

    VehicleDirectControlSessionTest() {
        for (int y = 0; y < 24; y++) for (int x = 0; x < 32; x++) grid.setWalkableFloor(x, y);
        roster.setNavigationGrid(grid);
    }

    @Test void onlyFriendlyDeployedLiveChassisEnters() {
        long id = spawn(Faction.MARINE);
        var mission = roster.convoy().mission(id);
        for (VehicleState state : VehicleState.values()) {
            mission.state = state;
            assertEquals(state == VehicleState.DEPLOYED, session.canEnter(id), state.name());
        }
        mission.state = VehicleState.DEPLOYED;
        assertFalse(session.enter(spawn(Faction.DEFENDER)));
        accept = false;
        assertFalse(session.enter(id));
        assertFalse(session.active());
        accept = true;
        assertTrue(session.enter(id));
        assertEquals(id, session.controlledVehicleId());
        assertEquals(0L, session.controlledMechId());
        assertFalse(roster.movement().has(id));
        assertFalse(roster.combat().has(id));
        assertFalse(roster.world().hasAiState(id));
        assertFalse(roster.entityWorld().has(id, roster.components().POSITION));
    }

    @Test void fullFootprintMustFitBeforeEntry() {
        long id = spawn(Faction.MARINE);
        GroundBody body = roster.convoy().body(id);
        body.x = .25f;
        assertFalse(session.canEnter(id));
        body.x = 10f;
        grid.setWalkable(10, 11, false);
        assertFalse(session.canEnter(id));
        grid.setWalkableFloor(10, 11);
        assertTrue(session.canEnter(id));
    }

    @Test void sessionDoesNotAdvanceMotionAmmoOrTurretClock() {
        long id = spawn(Faction.MARINE);
        var turret = roster.convoy().turret(id);
        var body = roster.convoy().body(id);
        var mission = roster.convoy().mission(id);
        mission.marinesRemaining = 3;
        assertTrue(session.enter(id));
        turret.cooldownTimer = 2f;
        int ammo = turret.ammo;
        session.submit(new ManualIntent(1f, 1f, 20f, 20f, true));
        session.tick();
        assertEquals(10f, body.x);
        assertEquals(10f, body.y);
        assertEquals(2f, turret.cooldownTimer);
        assertEquals(ammo, turret.ammo);
        assertEquals(3, mission.marinesRemaining);
        session.suspendInput();
        assertEquals(1, suspensions);
        assertEquals(0f, session.intent().moveX());
        assertFalse(session.intent().firing());
        session.exit();
        session.exit();
        assertEquals(1, entries);
        assertEquals(1, exits);
        assertEquals(0L, session.controlledVehicleId());
    }

    @Test void lifecycleLossReleasesEvenWhenEntityHasAlreadyBeenRemoved() {
        long id = spawn(Faction.MARINE);
        assertTrue(session.enter(id));
        roster.convoy().mission(id).state = VehicleState.DEPARTING;
        session.validate();
        assertFalse(session.active());
        roster.convoy().mission(id).state = VehicleState.DEPLOYED;
        assertTrue(session.enter(id));
        unavailable = true;
        session.validate();
        assertFalse(session.active());
        unavailable = false;
        assertTrue(session.enter(id));
        complete = true;
        session.validate();
        assertFalse(session.active());
        complete = false;
        assertTrue(session.enter(id));
        roster.convoy().despawn(id);
        session.validate();
        assertFalse(session.active());
        assertEquals(4, exits);
    }

    private long spawn(Faction faction) {
        long id = roster.convoy().spawn(VehicleType.HEAVY_APC, faction, VehicleMission.deployed(10f, 10f));
        roster.convoy().body(id).facingDegrees = 0f;
        return id;
    }
}
