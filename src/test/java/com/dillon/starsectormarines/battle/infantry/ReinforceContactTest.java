package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Entity leader = new Entity("d", Faction.DEFENDER, UnitType.MARINE,
                Math.round(cx), Math.round(cy));
        sim.addUnit(leader);
        int sid = sim.mintSquad(Faction.DEFENDER, leader);
        sim.squad().assignSquad(leader.entityId, sid);
        Squad squad = sim.getSquad(sid);
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        squad.centroidX = cx;
        squad.centroidY = cy;
        return squad;
    }

    private static Squad suspiciousPatrol(BattleSimulation sim) {
        Squad s = addDefenderSquad(sim, 35f, 5f);
        s.alertLevel = SquadAlertLevel.SUSPICIOUS;
        s.lastSeenEnemyX = 20;
        s.lastSeenEnemyY = 30;
        return s;
    }

    // ---- Relevance gates ----

    @Test
    public void priorityIsMission() {
        assertEquals(Goal.Priority.MISSION, ReinforceContact.INSTANCE.priority());
    }

    @Test
    public void relevanceZeroForMarines() {
        BattleSimulation sim = openSim();
        Entity leader = new Entity("m", Faction.MARINE, UnitType.MARINE, 35, 5);
        sim.addUnit(leader);
        int sid = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader.entityId, sid);
        Squad s = sim.getSquad(sid);
        s.alertLevel = SquadAlertLevel.SUSPICIOUS;
        s.lastSeenEnemyX = 20;
        s.lastSeenEnemyY = 30;
        s.centroidX = 35;
        s.centroidY = 5;
        s.aliveMembers = 4;
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(WorldState.EMPTY, s, sim));
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
        WorldState ws = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(ws, s, sim));
    }

    @Test
    public void relevanceZeroWhenAlreadyAtContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.centroidX = 20;
        s.centroidY = 30;
        assertEquals(0f, ReinforceContact.INSTANCE.relevance(WorldState.EMPTY, s, sim),
                "Squad centroid on top of contact should yield to EliminateEnemies");
    }

    @Test
    public void relevancePositiveWhenSuspiciousWithContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        assertTrue(ReinforceContact.INSTANCE.relevance(WorldState.EMPTY, s, sim) > 0f);
    }

    @Test
    public void relevancePositiveWhenEngagedWithContact() {
        BattleSimulation sim = openSim();
        Squad s = suspiciousPatrol(sim);
        s.alertLevel = SquadAlertLevel.ENGAGED;
        assertTrue(ReinforceContact.INSTANCE.relevance(WorldState.EMPTY, s, sim) > 0f,
                "ENGAGED patrol mid-flank should keep reinforcing until arrival");
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
}
