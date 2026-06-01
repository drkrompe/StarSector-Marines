package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the turret-emplacement patrol: {@link GuardPost} routes a squad
 * with a live {@link DefensePost} to {@link GuardPostPatrol} (box wander) and a
 * released / non-turret squad to {@link HoldPost} (static hold); and the QUIET
 * wander keeps its squad-scoped waypoint inside the post's bounding box.
 */
public class GuardPostPatrolTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(w, h));
    }

    private static Squad postedSquad(BattleSimulation sim, int anchorX, int anchorY, int radius) {
        DefensePost post = new DefensePost(DefensePostKind.LARGE, anchorX, anchorY,
                List.of(new DefensePost.TurretSpec(TurretKind.VULCAN, anchorX, anchorY)));
        Squad squad = new Squad(1, Faction.DEFENDER);
        squad.holdsFireUntilKillZone = true;
        squad.defensePost = post;
        squad.patrolRadius = radius;
        squad.aliveMembers = 1;
        squad.centroidX = anchorX;
        squad.centroidY = anchorY;
        return squad;
    }

    @Test
    public void livePostRoutesToBoxPatrolAndReleasedPostToStaticHold() {
        BattleSimulation sim = openArena(40, 40);
        Squad squad = postedSquad(sim, 20, 20, 8);

        SquadPlan patrolPlan = GuardPost.INSTANCE.customPlan(squad, sim);
        assertInstanceOf(GuardPostPatrol.class, patrolPlan.currentStep().action,
                "a squad on a live turret post patrols its box");

        // TurretDemolitionSystem clears the post link once every turret is down.
        squad.defensePost = null;
        SquadPlan heldPlan = GuardPost.INSTANCE.customPlan(squad, sim);
        assertEquals(HoldPost.INSTANCE, heldPlan.currentStep().action,
                "a released (or non-turret) guard squad falls back to the static hold");
    }

    @Test
    public void quietWanderKeepsWaypointInsideTheBox() {
        int anchorX = 20, anchorY = 20, radius = 6;
        BattleSimulation sim = openArena(40, 40);
        Squad squad = postedSquad(sim, anchorX, anchorY, radius);

        Unit leader = new Unit("L", Faction.DEFENDER, UnitType.MARINE, anchorX, anchorY);
        sim.addUnit(leader);
        squad.leader = leader;

        GuardPostPatrol patrol = new GuardPostPatrol(anchorX, anchorY, radius);

        // Drive many waypoint advances: each iteration expires the dwell and
        // parks the squad on its current waypoint so squadHasArrived → a fresh
        // roll. Every rolled waypoint must land inside the anchor±radius box.
        patrol.execute(leader, squad, sim);
        for (int i = 0; i < 200; i++) {
            squad.patrolDwellTimer = 0f;
            squad.centroidX = squad.patrolWaypointX;
            squad.centroidY = squad.patrolWaypointY;
            patrol.execute(leader, squad, sim);
            assertTrue(Math.abs(squad.patrolWaypointX - anchorX) <= radius
                            && Math.abs(squad.patrolWaypointY - anchorY) <= radius,
                    "waypoint (" + squad.patrolWaypointX + "," + squad.patrolWaypointY
                            + ") escaped the patrol box");
        }
    }

    @Test
    public void inheritedOutOfBoxWaypointIsRerolledNotWalkedTo() {
        int anchorX = 20, anchorY = 20, radius = 4;
        BattleSimulation sim = openArena(40, 40);
        Squad squad = postedSquad(sim, anchorX, anchorY, radius);

        Unit leader = new Unit("L", Faction.DEFENDER, UnitType.MARINE, anchorX, anchorY);
        sim.addUnit(leader);
        squad.leader = leader;

        // Simulate a posture switch: the squad carries a waypoint left by a
        // previous (wider) patrol, well outside this post's box. The squad is
        // NOT arrived at it (centroid sits on the anchor) and the dwell has
        // expired — the pre-fix code would have walked toward the stale cell.
        squad.patrolWaypointX = anchorX + 15;
        squad.patrolWaypointY = anchorY + 15;
        squad.patrolDwellTimer = 0f;

        GuardPostPatrol patrol = new GuardPostPatrol(anchorX, anchorY, radius);
        patrol.execute(leader, squad, sim);

        assertTrue(Math.abs(squad.patrolWaypointX - anchorX) <= radius
                        && Math.abs(squad.patrolWaypointY - anchorY) <= radius,
                "an inherited out-of-box waypoint must be re-rolled into the box, not walked to");
    }
}
