package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.infantry.InfantryCohesion;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.infantry.BreachAndAdvance;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Availability and handback on bare roster data; no battle construction or simulation tick. */
class SquadDirectControlTest {
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(96, 32), null);
    private final int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
    private final Squad squad = roster.getSquad(squadId);
    private final BattleControl view = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "resolveUnit" -> roster.isAliveById((long) args[0]) ? args[0] : 0L;
                case "squadMemberCount" -> roster.squadMemberCount((int) args[0]);
                case "squadMemberAt" -> roster.squadMemberArray((int) args[0])[(int) args[1]];
                case "world" -> roster.world();
                case "movement" -> roster.movement();
                case "liveUnitCount" -> roster.liveCount();
                case "liveUnitAt" -> roster.denseArray()[(int) args[0]];
                case "clearPath" -> {
                    roster.movement().setPathRef((long) args[0], new int[0]);
                    yield null;
                }
                case "squad" -> roster.squad();
                case "squadOf" -> squad;
                case "isRiding" -> roster.isRiding((long) args[0]);
                default -> throw new AssertionError("Unexpected query: " + method.getName());
            });

    @Test
    void controlledLeaderKeepsIdentityAndStrengthButDoesNotAnchorSquadmates() {
        long leader = spawn(70);
        long first = spawn(10);
        long second = spawn(11);
        squad.leaderId = leader;
        squad.setControlledMember(leader, view);

        assertEquals(leader, squad.leaderId);
        assertEquals(3, squad.aliveMembers);
        assertEquals(3, roster.squadMemberCount(squad.id));
        assertEquals(first, squad.autonomousLeader(view));
        assertEquals(2, squad.autonomousMemberCount(view));
        assertFalse(squad.participatesInPlan(leader));
        assertTrue(squad.participatesInPlan(first));
        assertEquals(11f, squad.centroidX, 1e-5f);
        assertTrue(InfantryCohesion.withinCohesion(first, view));
        assertTrue(InfantryCohesion.withinCohesion(second, view));
    }

    @Test
    void entryAndHandbackInvalidateEveryRetainedRoleWithoutReplacingTheOrder() {
        long leader = spawn(30);
        long follower = spawn(10);
        squad.leaderId = leader;
        ObjectiveAssignment order = new ObjectiveAssignment(squadId, AssignmentKind.ATTACK_MOVE,
                -1, null, -1, 60, 5);
        squad.assignedObjective = order;
        SquadPlan.Step first = step(leader, follower);
        SquadPlan.Step later = step(leader, follower);
        squad.currentPlan = new SquadPlan(List.of(first, later));
        squad.boundingActive = true;
        squad.boundingMemberIds = new long[]{leader, follower};
        squad.breachStackupTimer = 1f;
        squad.setControlledMember(leader, view);
        assertTrue(first.assignments.isEmpty());
        assertTrue(later.assignments.isEmpty());
        assertNull(squad.currentPlan);
        assertFalse(squad.boundingActive);
        assertEquals(0, squad.boundingMemberIds.length);
        assertEquals(0f, squad.breachStackupTimer);
        assertSame(order, squad.assignedObjective);

        SquadPlan.Step autonomous = step(follower);
        squad.currentPlan = new SquadPlan(List.of(autonomous));
        squad.setControlledMember(0L, view);
        assertTrue(autonomous.assignments.isEmpty());
        assertNull(squad.currentPlan);
        assertEquals(leader, squad.autonomousLeader(view));
        assertTrue(squad.participatesInPlan(leader));
        assertSame(order, squad.assignedObjective);
        assertEquals(20.5f, squad.centroidX, 1e-5f);
    }

    @Test
    void controlledStragglerCannotHoldAnAttackMoveOpen() {
        long manual = spawn(70);
        spawn(10);
        squad.setControlledMember(manual, view);
        assertTrue(AttackMove.squadHasArrived(squad, 10, 5, view));
        squad.setControlledMember(0L, view);
        assertFalse(AttackMove.squadHasArrived(squad, 10, 5, view));
    }

    @Test
    void soloControlHasNoAutonomousWriterOrFalseArrivalAndKeepsFiniteCentroid() {
        long manual = spawn(10);
        squad.leaderId = manual;
        squad.setControlledMember(manual, view);
        assertEquals(0L, squad.autonomousLeader(view));
        assertEquals(0, squad.autonomousMemberCount(view));
        assertEquals(manual, squad.leaderId);
        assertEquals(1, squad.aliveMembers);
        assertTrue(Float.isFinite(squad.centroidX));
        assertTrue(Float.isFinite(squad.centroidY));
        assertFalse(AttackMove.squadHasArrived(squad, 10, 5, view));
        squad.setControlledMember(0L, view);
        assertEquals(manual, squad.autonomousLeader(view));
    }

    @Test
    void returningADestroyedBodyStillReleasesTheSquad() {
        long manual = spawn(70);
        long follower = spawn(10);
        squad.leaderId = manual;
        squad.setControlledMember(manual, view);
        roster.world().setHp(manual, 0f);
        roster.release(manual);
        squad.setControlledMember(0L, view);
        assertEquals(0L, squad.controlledMemberId());
        assertEquals(follower, squad.autonomousLeader(view));
        assertEquals(10.5f, squad.centroidX, 1e-5f);
    }

    @Test
    void actingLeaderMustBeAvailableToExecuteSharedTimers() {
        long manual = spawn(70);
        long rejoining = spawn(40);
        long available = spawn(10);
        squad.leaderId = manual;
        squad.markRejoining(rejoining);
        squad.setControlledMember(manual, view);
        assertEquals(available, squad.autonomousLeader(view));
        assertEquals(1, squad.autonomousMemberCount(view));
    }

    @Test
    void patrolDwellAdvancesOnceWhenTheOrganizationalLeaderIsControlled() {
        long manual = spawn(70);
        long first = spawn(10);
        long second = spawn(11);
        squad.leaderId = manual;
        squad.setControlledMember(manual, view);
        squad.patrolDwellTimer = 4f;
        PatrolMotion.WaypointSource keep = (member, group, battle) -> null;
        PatrolMotion.advance(first, squad, view, keep, false);
        PatrolMotion.advance(second, squad, view, keep, false);
        assertEquals(4f - BattleSimulation.TICK_DT, squad.patrolDwellTimer, 1e-6f);
    }

    @Test
    void breachStackupAndCompletionCountOnlyTheAutonomousMember() {
        long manual = spawn(70);
        long breacher = spawn(10);
        squad.leaderId = manual;
        squad.setControlledMember(manual, view);
        BreachAndAdvance breach = new BreachAndAdvance(1,
                new int[]{10}, new int[]{5}, new int[]{10}, new int[]{5});
        SquadPlan.Step step = new SquadPlan.Step(breach);
        step.assignments.put("breacher:0", List.of(breacher));
        squad.currentPlan = new SquadPlan(List.of(step));
        assertEquals(ActionStatus.SUCCESS, breach.execute(breacher, squad, view));
    }

    private long spawn(int x) {
        long member = roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE,
                x, 5).squad(squadId));
        squad.aliveMembers++;
        return member;
    }

    private static SquadPlan.Step step(long... members) {
        SquadPlan.Step step = new SquadPlan.Step(new AttackMove(60, 5));
        step.assignments.put("any", Arrays.stream(members).boxed().toList());
        return step;
    }
}
