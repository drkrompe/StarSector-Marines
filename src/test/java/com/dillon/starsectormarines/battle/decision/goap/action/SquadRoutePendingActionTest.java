package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.AttackerIndexService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real advance body and two-unit scorer; no whole-battle construction. */
class SquadRoutePendingActionTest {
    @Test void pendingPausesARepathWithoutChangingItsPathOrClockAndStillFires() {
        try (Fixture f = new Fixture()) {
            f.roster.movement().beginTick(1f);
            assertTrue(f.roster.movement().mayRepath(f.member));
            assertEquals(ActionStatus.RUNNING, f.execute());
            f.assertUntouched();
            assertTrue(f.roster.movement().mayRepath(f.member), "pending must not stamp the repath clock");
            assertEquals(f.enemy, f.roster.combat().fireTargetId(f.member));
            assertTrue(f.roster.movement().needsObjectiveRouteRefresh(f.member));
            assertEquals(1, f.profile.countOf(TickInnerProfile.Bucket.SQUAD_ROUTE_PENDING_CALL));
        }
    }

    @Test void pendingAlsoPausesTravelWhenRepathIsStillThrottled() {
        try (Fixture f = new Fixture()) {
            assertFalse(f.roster.movement().mayRepath(f.member));
            assertEquals(ActionStatus.RUNNING, f.execute());
            f.assertUntouched();
            assertFalse(f.roster.movement().mayRepath(f.member));
            assertEquals(1, f.pendingChecks);
            assertEquals(f.enemy, f.roster.combat().fireTargetId(f.member));
        }
    }

    @Test void admissionResumesNormalRequestAndTravelOnTheNextUpdate() {
        try (Fixture f = new Fixture()) {
            f.roster.movement().beginTick(1f);
            f.execute();
            f.assertUntouched();
            f.pending = false;
            f.tick++;
            f.roster.combat().clearPrimaryFire(f.member);
            assertEquals(ActionStatus.RUNNING, f.execute());
            assertEquals(1, f.requests);
            assertEquals(1, f.pathWrites);
            assertEquals(1, f.advances);
            assertSame(f.currentPath, f.roster.world().path(f.member));
            assertFalse(f.roster.movement().mayRepath(f.member), "real installation stamps the repath clock");
            assertEquals(f.enemy, f.roster.combat().fireTargetId(f.member));
            assertEquals(1, f.profile.countOf(TickInnerProfile.Bucket.SQUAD_ROUTE_PENDING_CALL));
            assertFalse(f.roster.movement().needsObjectiveRouteRefresh(f.member));
        }
    }

    @Test void newlyAdmittedIntentReplacesTheOldPathEvenBeforeTheRepathClockExpires() {
        try (Fixture f = new Fixture()) {
            assertFalse(f.roster.movement().mayRepath(f.member));
            assertFalse(f.roster.movement().needsObjectiveRouteRefresh(f.member), "fresh movement rows default clear");
            f.execute();
            f.assertUntouched();
            assertTrue(f.roster.movement().needsObjectiveRouteRefresh(f.member));
            f.pending = false;
            f.tick++;
            assertFalse(f.roster.movement().mayRepath(f.member));
            f.execute();
            assertEquals(1, f.requests, "ready field must be read before resuming a possibly unrelated old path");
            assertEquals(1, f.pathWrites);
            assertEquals(1, f.advances);
            assertSame(f.currentPath, f.roster.world().path(f.member));
            assertFalse(f.roster.movement().needsObjectiveRouteRefresh(f.member));
            f.execute();
            assertEquals(1, f.requests, "the one-off refresh must not defeat the ordinary throttle thereafter");
            assertEquals(2, f.advances);
        }
    }

    private static final class ProbeAction extends AbstractZoneAction {
        ProbeAction() { super(-1); }
        @Override public String name() { return "PendingProbe"; }
        @Override public ActionStatus execute(long member, Squad squad, BattleControl sim) {
            advanceIntoZone(member, squad, sim, 13, 2, false);
            return ActionStatus.RUNNING;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(16, 6);
        final NavigationService nav;
        final UnitRosterService roster;
        final TickInnerProfile profile = new TickInnerProfile();
        final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();
        final Squad squad;
        final long member, enemy;
        final ProbeAction action = new ProbeAction();
        final SquadPlan.Step step = new SquadPlan.Step(action);
        final int[] oldPath = {2, 2, 3, 2, 4, 2};
        final int[] currentPath = {2, 2, 3, 2, 4, 2, 5, 2, 6, 2, 7, 2,
                8, 2, 9, 2, 10, 2, 11, 2, 12, 2, 13, 2};
        final BattleControl sim;
        boolean pending = true;
        int tick = 100, pendingChecks, requests, pathWrites, advances;

        Fixture() {
            for (int y = 0; y < 6; y++) for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
            nav = new NavigationService(grid, new CellTopology(16, 6), false);
            roster = new UnitRosterService(nav.getUnitIndex(), null);
            nav.setRoster(roster);
            roster.setNavigationGrid(grid);
            int id = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            squad = roster.getSquad(id);
            member = roster.spawn(new EntitySpec("member", Faction.MARINE, UnitType.MARINE, 2, 2).squad(id));
            enemy = roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 6, 2));
            nav.setOccupancyDeltaSink((unit, oldX, oldY, newX, newY) -> {
                assertEquals(member, unit);
                assertEquals(4, oldX);
                assertEquals(2, oldY);
                assertEquals(13, newX);
                assertEquals(2, newY);
            });
            roster.world().setAttackRange(member, 10f);
            roster.world().setTargetId(member, enemy);
            roster.movement().setPathRef(member, oldPath);
            roster.movement().setPathIdx(member, 0);
            roster.movement().markRepath(member);
            squad.currentPlan = new SquadPlan(List.of(step));
            squad.routingEpoch = 27L;
            nav.getUnitIndex().rebuild(roster);
            TacticalScoring scoring = new TacticalScoring(nav, roster, new AttackerIndexService(roster),
                    null, new DoodadService(grid));
            int routedPopulation = Math.max(1, SharedGoalPolicy.configuredMinimumSharedGoalUnits());
            assertTrue(SharedGoalPolicy.usesSquadRouteCorridors(routedPopulation));
            sim = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "world" -> roster.world();
                        case "combat" -> roster.combat();
                        case "movement" -> roster.movement();
                        case "targetOf" -> roster.combat().targetId((long) args[0]);
                        case "getTacticalScoring" -> scoring;
                        case "getSimTickIndex" -> tick;
                        case "liveUnitCount" -> routedPopulation;
                        case "getRouteCostField" -> null;
                        case "isSquadRoutePending" -> {
                            pendingChecks++;
                            assertEquals(squad.id, args[0]);
                            assertEquals(squad.routingEpoch, args[1]);
                            assertSame(step, args[2]);
                            assertEquals(13, args[3]);
                            assertEquals(2, args[4]);
                            yield pending;
                        }
                        case "findSquadPathToGoal" -> {
                            requests++;
                            assertEquals(13, args[5]);
                            assertEquals(2, args[6]);
                            yield currentPath;
                        }
                        case "setPath" -> {
                            assertTrue(roster.movement().needsObjectiveRouteRefresh(member),
                                    "handback marker must survive until route installation");
                            assertSame(currentPath, args[1]);
                            nav.setPath((long) args[0], (int[]) args[1]);
                            pathWrites++;
                            assertSame(currentPath, roster.world().path(member));
                            assertTrue(roster.movement().needsObjectiveRouteRefresh(member),
                                    "installation does not clear the action-owned handback marker");
                            yield null;
                        }
                        case "clearPath" -> throw new AssertionError("pending must not clear a path");
                        case "advanceSquadTravel" -> {
                            assertEquals(1, pathWrites, "current route must be installed before travel");
                            assertSame(currentPath, roster.world().path(member));
                            assertNotSame(oldPath, roster.world().path(member));
                            assertEquals(1, roster.world().pathIdx(member));
                            assertFalse(roster.movement().needsObjectiveRouteRefresh(member),
                                    "handback marker must clear before travel resumes");
                            assertFalse(roster.movement().mayRepath(member),
                                    "real installation must have stamped the repath clock");
                            advances++;
                            yield null;
                        }
                        case "advanceMovement" -> throw new AssertionError("unexpected combat movement");
                        default -> throw new AssertionError("Unexpected battle dependency: " + method.getName());
                    });
            TickInnerProfile.setCurrent(profile);
        }

        ActionStatus execute() { return action.execute(member, squad, sim); }

        void assertUntouched() {
            assertEquals(0, requests);
            assertEquals(0, pathWrites);
            assertEquals(0, advances);
            assertSame(oldPath, roster.world().path(member));
            assertEquals(0, roster.world().pathIdx(member));
            assertEquals(2.5f, roster.world().x(member));
            assertEquals(2.5f, roster.world().y(member));
        }

        @Override public void close() {
            TickInnerProfile.setCurrent(previousProfile);
            nav.close();
        }
    }
}
