package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadTrafficSystemTest {
    private static final float DT = 1f / 30f;
    private static final int GOAL_X = 80, GOAL_Y = 10;

    @Test
    void preparationDoesNotMoveBodiesAndMatchingTravelConsumesTheHint() {
        Fixture f = new Fixture();
        f.prepare(0L);
        assertEquals(10.5f, f.world.x(f.first));
        assertEquals(10.5f, f.world.y(f.first));
        assertTrue(f.advance());
        assertTrue(f.world.x(f.first) > 10.5f);
        assertTrue(f.world.y(f.first) < 10.5f, "the first squad takes its stable left-hand preference");
    }

    @Test
    void pathReplacedDuringDispatchCannotConsumeThePreparedHint() {
        Fixture f = new Fixture();
        f.prepare(0L);
        f.repath(f.first);
        assertFalse(f.advance());
        assertEquals(10.5f, f.world.x(f.first));
        assertEquals(10.5f, f.world.y(f.first));
    }

    @Test
    void changedGoalStepAndEpochEachRejectTheSnapshot() {
        Fixture f = new Fixture();
        f.prepare(0L);
        assertFalse(f.traffic.advance(f.first, f.squad, GOAL_X - 1, GOAL_Y, DT));
        f.squad.routingEpoch++;
        assertFalse(f.advance());
        f.prepare(0L);
        f.squad.currentPlan = plan(f.first);
        assertFalse(f.advance());
        assertEquals(10.5f, f.world.x(f.first));
    }

    @Test
    void directControlExcludesTheExactMemberFromPreparationAndExecution() {
        Fixture f = new Fixture();
        f.prepare(f.first);
        assertFalse(f.advance());
        assertEquals(10.5f, f.world.x(f.first));
        assertEquals(10.5f, f.world.y(f.first));
    }

    @Test
    void directContactAndCommittedCombatRejectPreparedTravel() {
        Fixture f = new Fixture();
        f.prepare(0L);
        f.squad._engagedThisTick = true;
        assertFalse(f.advance());
        f.prepare(0L);
        f.squad._engagedThisTick = false;
        assertFalse(f.advance(), "a contact-excluded snapshot cannot become eligible midway through dispatch");
        f.prepare(0L);
        f.squad.advanceEngageCommitted = true;
        assertFalse(f.advance());
    }

    @Test
    void wallSeparatedSquadsDoNotAuthorTrafficForEachOther() {
        Fixture f = new Fixture();
        f.world.setPos(f.second, 10.5f, 11.5f);
        for (int x = 0; x < 100; x++) f.grid.blockSharedEdge(x, 10, Direction.N);
        f.prepare(0L);
        assertTrue(f.advance(), "the adapter may own an ordinary safe step without authoring a traffic hint");
        assertEquals(10.5f + f.movement.moveSpeed(f.first) * DT, f.world.x(f.first), 1e-5f);
        assertEquals(10.5f, f.world.y(f.first));
    }

    @Test
    void compressionKeepsSweptReturnUntilTheShiftedBodyIsSafe() {
        Fixture f = new Fixture();
        f.prepare(0L);
        assertTrue(f.advance(), "first establish that this participant received shaping");
        f.world.setPos(f.first, 10.5f, 11.1f);
        f.grid.setDoorway(10, 10, true);
        f.grid.blockSharedEdge(10, 10, Direction.N);
        f.prepare(0L);

        assertTrue(f.advance(), "zero offset must not hand an off-corridor actor to the unswept follower");
        assertEquals(10.5f, f.world.x(f.first));
        assertEquals(11.1f, f.world.y(f.first));
        assertEquals(0f, f.movement.velX(f.first));
        assertEquals(0f, f.movement.velY(f.first));
    }

    @Test
    void routineRouteRebasesDoNotAccumulateUnboundedLateralDrift() {
        Fixture f = new Fixture();
        float maximumDeparture = 0f;
        int trafficMoves = 0;
        for (int tick = 0; tick < 450; tick++) {
            // Keep one local pressure source beside the measured squad. This
            // isolates the lane bound instead of letting separation end the
            // pressure that would expose repeated path-origin accumulation.
            f.world.setPos(f.second, f.world.x(f.first), f.world.y(f.first) + 0.15f);
            if (tick % 11 == 0) {
                f.repath(f.first);
                f.repath(f.second);
            }
            f.prepare(0L);
            if (f.advance()) trafficMoves++;
            else f.movement.advanceAlongPath(f.world, f.first, DT);
            maximumDeparture = Math.max(maximumDeparture, Math.abs(f.world.y(f.first) - 10.5f));
        }
        assertTrue(trafficMoves > 300, "the bound must not pass by turning the feature off");
        assertTrue(f.world.x(f.first) > 25f, "traffic keeps making useful progress");
        assertTrue(maximumDeparture <= SquadTrafficSolver.MAX_LATERAL_OFFSET + 0.55f,
                "a new path origin cannot renew the entire lateral allowance: " + maximumDeparture);
        assertTrue(maximumDeparture > 1f, "the test must actually exercise spreading");
    }

    @Test
    void gradualHeadingChangeIsIndependentOfAbsoluteWorldCoordinates() {
        Fixture local = new Fixture(), translated = new Fixture(300, 200);
        for (Fixture f : List.of(local, translated)) {
            // The same path acquires a slight diagonal heading as its cursor
            // advances. There is no new route origin to rebase, and translating
            // this tiny local scene across Conquest must not invent a demand.
            int[] path = new int[34];
            for (int i = 0; i < 16; i++) {
                path[i * 2] = 2 + i + f.shiftX;
                path[i * 2 + 1] = 10 + i / 4 + f.shiftY;
            }
            path[32] = f.goalX;
            path[33] = f.goalY;
            for (long member : new long[]{f.first, f.second}) {
                f.world.setPos(member, 2.5f + f.shiftX, 10.5f + f.shiftY);
                f.movement.setPathRef(member, path);
                f.movement.setPathIdx(member, 0);
            }
            f.prepare(0L);
            assertTrue(f.advance());
            for (long member : new long[]{f.first, f.second}) {
                f.world.setPos(member, 3.5f + f.shiftX, 10.5f + f.shiftY);
                f.movement.setPathIdx(member, 2);
            }
            f.prepare(0L);
            assertTrue(f.advance());
        }
        assertEquals(local.world.x(local.first), translated.world.x(translated.first) - 300f, 5e-5f);
        assertEquals(local.world.y(local.first), translated.world.y(translated.first) - 200f, 5e-5f);
    }

    private static SquadPlan plan(long member) {
        return plan(member, GOAL_X, GOAL_Y);
    }

    private static SquadPlan plan(long member, int goalX, int goalY) {
        SquadPlan.Step step = new SquadPlan.Step(new AttackMove(goalX, goalY));
        step.assignments.put("travel", List.of(member));
        return new SquadPlan(List.of(step));
    }

    private static final class Fixture {
        final NavigationGrid grid;
        final UnitRosterService roster;
        final MovementService movement;
        final World world;
        final SquadTrafficSystem traffic;
        final BattleView view;
        final int shiftX, shiftY, goalX, goalY;
        final long first, second;
        final Squad squad;

        Fixture() { this(0, 0); }

        Fixture(int shiftX, int shiftY) {
            this.shiftX = shiftX;
            this.shiftY = shiftY;
            goalX = GOAL_X + shiftX;
            goalY = GOAL_Y + shiftY;
            grid = new NavigationGrid(100 + shiftX, 32 + shiftY);
            roster = new UnitRosterService(new UnitSpatialIndex(100 + shiftX, 32 + shiftY), null);
            movement = roster.movement();
            world = roster.world();
            traffic = new SquadTrafficSystem(grid, roster);
            view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getGrid" -> grid;
                    case "getSquads" -> roster.getSquads();
                    case "world" -> world;
                    case "isRiding" -> false;
                    default -> throw new AssertionError("Unexpected query: " + method.getName());
                });
            for (int y = shiftY; y < 32 + shiftY; y++) {
                for (int x = shiftX; x < 100 + shiftX; x++) grid.setWalkableFloor(x, y);
            }
            movement.setNavigationGrid(grid);
            first = member();
            second = member();
            squad = roster.getSquad(roster.squad().squadId(first));
        }

        long member() {
            long id = roster.spawn(new EntitySpec("member", Faction.MARINE, UnitType.MARINE_BLUE,
                    10 + shiftX, 10 + shiftY));
            int squadId = roster.mintSquad(Faction.MARINE, id);
            roster.squad().assignSquad(id, squadId);
            // Production awareness publishes this before traffic preparation.
            roster.getSquad(squadId).aliveMembers = 1;
            roster.getSquad(squadId).currentPlan = plan(id, goalX, goalY);
            repath(id);
            return id;
        }

        void repath(long id) {
            int cx = world.cellX(id), cy = world.cellY(id);
            int count = goalX - cx + 1 + Math.abs(goalY - cy);
            int[] path = new int[count * 2];
            int cursor = 0;
            for (int x = cx; x <= goalX; x++) {
                path[cursor++] = x;
                path[cursor++] = cy;
            }
            int step = Integer.compare(goalY, cy);
            for (int y = cy + step; step != 0 && y != goalY + step; y += step) {
                path[cursor++] = goalX;
                path[cursor++] = y;
            }
            movement.setPathRef(id, path);
            movement.setPathIdx(id, 0);
        }

        void prepare(long controlled) { traffic.prepare(view, controlled, DT); }

        boolean advance() { return traffic.advance(first, squad, goalX, goalY, DT); }
    }
}
