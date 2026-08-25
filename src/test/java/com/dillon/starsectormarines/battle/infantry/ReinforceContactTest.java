package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link ReinforceContact} — Story D patrol intercept. Verifies
 * relevance gates, the RoutinePatrol handoff, and the flanking waypoint
 * geometry.
 */
public class ReinforceContactTest {

    private static final int W = 40;
    private static final int H = 40;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad addDefenderSquad(BattleSimulation sim, float cx, float cy) {
        long leader = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE,
                Math.round(cx), Math.round(cy)));
        int sid = sim.mintSquad(Faction.DEFENDER, leader);
        sim.squad().assignSquad(leader, sid);
        Squad squad = sim.getSquad(sid);
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        // Centroids are center-based true positions — a squad "at cell
        // (cx, cy)" has its centroid on that cell's center.
        squad.centroidX = Math.round(cx) + 0.5f;
        squad.centroidY = Math.round(cy) + 0.5f;
        return squad;
    }

    private static Squad suspiciousPatrol(BattleSimulation sim) {
        Squad s = addDefenderSquad(sim, 35f, 5f);
        s.alertLevel = SquadAlertLevel.SUSPICIOUS;
        s.lastSeenEnemyX = 20;
        s.lastSeenEnemyY = 30;
        return s;
    }

    private static WorldState contactState() {
        return WorldState.EMPTY.with(Predicate.HAS_TARGET, true);
    }

    // ---- Relevance gates ----

    @Test
    public void priorityIsEngagementSoSurvivalCanPreempt() {
        assertEquals(Goal.Priority.ENGAGEMENT, ReinforceContact.INSTANCE.priority());
    }

    @Test
    public void relevancePositiveForMarinesSoTheyCanFixAndFlank() {
        BattleSimulation sim = openSim();
        long leader = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 35, 5));
        int sid = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader, sid);
        Squad s = sim.getSquad(sid);
        s.alertLevel = SquadAlertLevel.SUSPICIOUS;
        s.lastSeenEnemyX = 20;
        s.lastSeenEnemyY = 30;
        s.centroidX = 35;
        s.centroidY = 5;
        s.aliveMembers = 4;
        assertTrue(ReinforceContact.INSTANCE.relevance(contactState(), s, sim) > 0f);
    }

    @Test
    public void relevanceZeroForGarrisons() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.holdsFireUntilKillZone = true;
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(WorldState.EMPTY, s, sim));
    }

    @Test
    public void relevanceZeroWhenUnaware() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.alertLevel = SquadAlertLevel.UNAWARE;
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(WorldState.EMPTY, s, sim));
    }

    @Test
    public void relevanceZeroWhenNoContactPoint() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.lastSeenEnemyX = -1;
        s.lastSeenEnemyY = -1;
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(WorldState.EMPTY, s, sim));
    }

    @Test
    public void relevanceZeroWhenMoraleBroken() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        WorldState ws = contactState().with(Predicate.MORALE_BROKEN, true);
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(ws, s, sim));
    }

    @Test
    public void relevanceZeroWhenAlreadyAtContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        sim.world().setCellPos(s.leaderId, 20, 30);
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(contactState(), s, sim),
                "a live fireteam member at contact should yield to EliminateEnemies");
    }

    @Test
    public void relevancePositiveWhenSuspiciousWithContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        assertTrue(ReinforceContact.INSTANCE.relevance(contactState(), s, sim) > 0f);
    }

    @Test
    public void relevancePositiveWhenEngagedWithContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.alertLevel = SquadAlertLevel.ENGAGED;
        s.currentGoal = ReinforceContact.INSTANCE;
        assertTrue(ReinforceContact.INSTANCE.relevance(contactState(), s, sim) > 0f,
                "ENGAGED patrol mid-flank should keep reinforcing until arrival");
    }

    @Test
    public void engagedSquadDoesNotReenterFlankAfterContactHandoff() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.alertLevel = SquadAlertLevel.ENGAGED;
        s.currentGoal = EliminateEnemiesGoal.INSTANCE;

        assertEquals(0f, ReinforceContact.INSTANCE.relevance(
                contactState(), s, sim));
    }

    @Test
    public void deadRememberedIdentityCannotRestartReinforcement() {
        BattleSimulation sim = openSim();
        Squad squad = addDefenderSquad(sim, 35f, 5f);
        long enemy = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 20, 5));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(WorldStateBuilder.build(squad, sim).get(Predicate.HAS_TARGET));
        assertTrue(TacticalScoring.cellDistance(squad.centroidX,
                squad.centroidY, 20.5f, 5.5f)
                > ReinforceContact.ALREADY_AT_CONTACT_RADIUS);

        sim.getRoster().release(enemy);
        squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
        WorldState stale = WorldStateBuilder.build(squad, sim);

        assertFalse(stale.get(Predicate.HAS_TARGET));
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(stale, squad, sim));
    }

    // ---- RoutinePatrol handoff ----

    @Test
    public void routinePatrolYieldsOnSuspiciousWithContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        assertEquals(0f, RoutinePatrol.INSTANCE.relevance(WorldState.EMPTY, s, sim),
                "RoutinePatrol must yield when SUSPICIOUS + valid lastSeenEnemy");
    }

    @Test
    public void routinePatrolStaysOnSuspiciousWithoutContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.lastSeenEnemyX = -1;
        s.lastSeenEnemyY = -1;
        assertTrue(RoutinePatrol.INSTANCE.relevance(WorldState.EMPTY, s, sim) > 0f,
                "RoutinePatrol should stay active when SUSPICIOUS but no contact point");
    }

    // ---- Custom plan ----

    @Test
    public void customPlanEmitsFlankApproach() {
        BattleSimulation sim = openSim();
        Squad patrol = suspiciousPatrol(sim);

        SquadPlan plan = ReinforceContact.INSTANCE.customPlan(patrol, sim);
        assertNotNull(plan);
        assertEquals(1, plan.stepCount());
        assertTrue(plan.steps().get(0).action instanceof FlankApproach);
    }

    @Test
    public void flankApproachKeepsOneTeamFixingWhileSiblingManeuvers() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            members.add(sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 5, 5 + i).squad(squadId).fireTeam(i / 4)));
        }
        squad.aliveMembers = 8;
        FlankApproach action = new FlankApproach(20, 20);

        var roles = action.assignRoles(squad, sim, members);

        assertEquals(2, roles.size());
        assertEquals(4, roles.entrySet().stream()
                .filter(e -> e.getKey().startsWith(FlankApproach.FIX))
                .findFirst().orElseThrow().getValue().size());
        assertEquals(4, roles.entrySet().stream()
                .filter(e -> e.getKey().startsWith(FlankApproach.FLANK))
                .findFirst().orElseThrow().getValue().size());
    }

    @Test
    public void fixingTeamClosesToReachableSupportLineOnDirectContact() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int x = i < 4 ? 5 : 25;
            long member = sim.spawn(new EntitySpec("d" + i, Faction.DEFENDER,
                    UnitType.MARINE, x, 10 + i % 4).squad(squadId)
                    .fireTeam(i / 4));
            members.add(member);
            if (i == 0) squad.leaderId = member;
        }
        long enemy = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 30, 11));
        sim.world().setMaxHp(enemy, 1_000f);
        sim.world().setHp(enemy, 1_000f);
        sim.advance(BattleSimulation.TICK_DT);

        FlankApproach action = new FlankApproach(30, 25);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.putAll(action.assignRoles(squad, sim, members));
        squad.currentPlan = new SquadPlan(List.of(step));
        long fixer = step.assignments.entrySet().stream()
                .filter(e -> e.getKey().startsWith(FlankApproach.FIX))
                .findFirst().orElseThrow().getValue().get(0);

        assertEquals(ActionStatus.RUNNING, action.execute(fixer, squad, sim));

        int[] path = sim.world().path(fixer);
        assertFalse(Paths.isEmpty(path));
        int destX = Paths.destX(path);
        int destY = Paths.destY(path);
        assertTrue(sim.getGrid().hasLineOfSight(destX, destY,
                sim.world().cellX(enemy), sim.world().cellY(enemy)));
        assertTrue(TacticalScoring.cellDistance(destX + 0.5f, destY + 0.5f,
                sim.world().x(enemy), sim.world().y(enemy))
                <= sim.world().attackRange(fixer));
    }

    @Test
    public void structurallyUnreachableFlankCompletesForOrdinaryHandoff() {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (x != 9 && x != 10) grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("flanker", Faction.DEFENDER,
                UnitType.MARINE, 5, 5).squad(squadId));
        FlankApproach action = new FlankApproach(15, 5);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put("flank:0", List.of(member));
        squad.currentPlan = new SquadPlan(List.of(step));

        assertEquals(ActionStatus.SUCCESS, action.execute(member, squad, sim));
    }

    // ---- Flanking geometry ----

    @Test
    public void flankWaypointIsNotColinearWithGarrison() {
        BattleSimulation sim = openSim();

        Squad garrison = addDefenderSquad(sim, 5f, 10f);
        garrison.alertLevel = SquadAlertLevel.ENGAGED;

        Squad patrol = addDefenderSquad(sim, 35f, 5f);
        patrol.alertLevel = SquadAlertLevel.SUSPICIOUS;
        patrol.lastSeenEnemyX = 20;
        patrol.lastSeenEnemyY = 30;

        int[] wp = ReinforceContact.computeFlankWaypoint(patrol, sim);

        float gAxisX = 20 - 5f;
        float gAxisY = 30 - 10f;
        float gLen = (float) Math.sqrt(gAxisX * gAxisX + gAxisY * gAxisY);
        gAxisX /= gLen;
        gAxisY /= gLen;

        float wpAxisX = 20 - wp[0];
        float wpAxisY = 30 - wp[1];
        float wpLen = (float) Math.sqrt(wpAxisX * wpAxisX + wpAxisY * wpAxisY);
        if (wpLen > 0.01f) {
            wpAxisX /= wpLen;
            wpAxisY /= wpLen;
        }

        float dot = gAxisX * wpAxisX + gAxisY * wpAxisY;
        float angleDeg = (float) Math.toDegrees(Math.acos(Math.min(1f, Math.abs(dot))));

        assertTrue(angleDeg >= 30f,
                "Flanking waypoint should be at least 30° off the garrison's axis, was " + angleDeg + "°");
    }

    @Test
    public void flankWaypointFallbackWhenNoEngagedFriendly() {
        BattleSimulation sim = openSim();
        Squad patrol = suspiciousPatrol(sim);

        int[] wp = ReinforceContact.computeFlankWaypoint(patrol, sim);
        assertNotNull(wp);
        assertTrue(sim.getGrid().inBounds(wp[0], wp[1]) && sim.getGrid().isWalkable(wp[0], wp[1]),
                "Waypoint must be walkable even without an engaged friendly");
    }

    @Test
    public void snapToWalkableFindsNearbyCell() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        // Make everything walkable EXCEPT (10, 10)
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                if (x == 10 && y == 10) continue;
                grid.setWalkableFloor(x, y);
            }
        }

        int[] result = ReinforceContact.snapToWalkable(10, 10, grid, 5, 5);
        assertTrue(grid.isWalkable(result[0], result[1]),
                "Snap must find a walkable cell near the target");
        int dx = Math.abs(result[0] - 10);
        int dy = Math.abs(result[1] - 10);
        assertTrue(dx <= 1 && dy <= 1,
                "Snapped cell should be adjacent to the original target");
    }

    @Test
    public void flankWaypointFallsBackWhenStructureMakesItAnExtremeDetour() {
        int width = 30;
        int height = 20;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (x != 15) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(15, 1);
        grid.setDoorway(15, 1, true);
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, height));
        long leader = sim.spawn(new EntitySpec("leader", Faction.MARINE,
                UnitType.MARINE, 10, 15));
        int squadId = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.centroidX = 10.5f;
        squad.centroidY = 15.5f;

        int[] waypoint = ReinforceContact.snapToReachable(20, 15, squad, sim);

        assertEquals(10, waypoint[0]);
        assertEquals(15, waypoint[1]);
    }
}
