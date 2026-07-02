package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
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

        Entity leader = new Entity("L", Faction.DEFENDER, UnitType.MARINE, anchorX, anchorY);
        sim.addUnit(leader);
        squad.leaderId = leader.entityId;

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

        Entity leader = new Entity("L", Faction.DEFENDER, UnitType.MARINE, anchorX, anchorY);
        sim.addUnit(leader);
        squad.leaderId = leader.entityId;

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

    @Test
    public void engageInRangeVisibleAndWithinLeashWritesStancedIntent() {
        int anchorX = 20, anchorY = 20, radius = 8;
        BattleSimulation sim = openArena(40, 40);
        Squad squad = postedSquad(sim, anchorX, anchorY, radius);

        Entity member = new Entity("m", Faction.DEFENDER, UnitType.MARINE, anchorX, anchorY);
        sim.addUnit(member);
        squad.leaderId = member.entityId;
        sim.world().setAttackRange(member.entityId, 10f);
        sim.world().setCooldownTimer(member.entityId, 0.6f);

        Entity enemy = new Entity("e", Faction.MARINE, UnitType.MARINE, anchorX + 3, anchorY);
        sim.addUnit(enemy);

        GuardPostPatrol patrol = new GuardPostPatrol(anchorX, anchorY, radius);
        patrol.execute(member, squad, sim);

        assertEquals(0.6f, sim.world().cooldownTimer(member.entityId), 1e-6f,
                "engage() must not decrement cooldownTimer itself anymore — "
                        + "the local decrement was one of the epic's double-tick bugs");
        assertEquals(enemy.entityId, sim.combat().fireTargetId(member.entityId),
                "in range + LoS + inside the leash writes a fire intent for the target");
        assertEquals(FireStance.STANCED.ordinal(),
                sim.getRoster().entityWorld().getInt(member.entityId, sim.getRoster().components().COMBAT,
                        BattleComponents.COMBAT_FIRE_STANCE),
                "the authored intent carries the STANCED stance");
    }

    @Test
    public void engageOutsideLeashHoldsFireWhileKeepingTheTarget() {
        // Contract-(b) case: a member with LOS + range to its target but
        // standing outside the (odds-scaled, capped at the box radius) leash
        // must NOT author a fire intent — it gives ground toward the
        // strongpoint instead of trading shots out on the perimeter — while
        // still remembering the target it was engaging.
        int anchorX = 20, anchorY = 20, radius = 5;
        BattleSimulation sim = openArena(60, 40);
        Squad squad = postedSquad(sim, anchorX, anchorY, radius);

        // Member sits well outside the box (radius 5); leash can never exceed
        // the full box radius regardless of the odds tally, so any member
        // farther than that from the anchor is unconditionally outside it.
        Entity member = new Entity("m", Faction.DEFENDER, UnitType.MARINE, anchorX + 10, anchorY);
        sim.addUnit(member);
        squad.leaderId = member.entityId;
        sim.world().setAttackRange(member.entityId, 10f);

        Entity enemy = new Entity("e", Faction.MARINE, UnitType.MARINE, anchorX + 12, anchorY);
        sim.addUnit(enemy);

        GuardPostPatrol patrol = new GuardPostPatrol(anchorX, anchorY, radius);
        patrol.execute(member, squad, sim);

        assertEquals(0L, sim.combat().fireTargetId(member.entityId),
                "outside the leash — no fire intent even though in range + LoS");
        assertEquals(enemy.entityId, sim.world().targetId(member.entityId),
                "the engaged target is still retained for when the leash allows firing again");
    }
}
