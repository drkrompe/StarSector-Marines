package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-freeze coverage for {@link PatrolMotion} — the two QUIET-patrol stalls
 * behind the SQ-96 garrison freeze: a dwell timer gated on a dead/unset leader
 * (never counts down) and a waypoint the pathfinder can't reach (parked on
 * forever because the round-robin only rolls over on arrival).
 */
public class PatrolMotionTest {

    private static final int W = 12;
    private static final int H = 10;

    /** A keep-current waypoint source — exercises advance() without a strategy. */
    private static final PatrolMotion.WaypointSource KEEP = (m, s, sim) -> null;

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    /** Two rooms split by a solid wall column at x=6, NO doorway → disconnected. */
    private static BattleSimulation walledRooms() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == 6) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    private static Entity add(BattleSimulation sim, String id, int x, int y) {
        Entity u = new Entity(id, Faction.MARINE, UnitType.MARINE, x, y);
        sim.addUnit(u);
        return u;
    }

    @Test
    public void dwellNeverDrainsWhileLeaderTicksAreSkipped() {
        // Control: with a live leader, a non-leader member must NOT drain the
        // shared dwell — that's the once-per-tick guarantee the gate exists for.
        BattleSimulation sim = openArena();
        Entity leader = add(sim, "L", 3, 3);
        Entity member = add(sim, "m", 4, 3);
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 2;
        squad.leaderId = leader.entityId;
        squad.patrolDwellTimer = 4.0f;
        squad.patrolWaypointX = 3; squad.patrolWaypointY = 3;

        PatrolMotion.advance(member, squad, sim, KEEP, false);
        assertEquals(4.0f, squad.patrolDwellTimer, 1e-6,
                "a non-leader must not drain the dwell while the leader is alive");

        PatrolMotion.advance(leader, squad, sim, KEEP, false);
        assertTrue(squad.patrolDwellTimer < 4.0f,
                "the live leader drains the dwell as before");
    }

    @Test
    public void dwellStillDrainsWhenLeaderIsUnset() {
        // leaderId == 0L (wipe sentinel) with members still alive: no member
        // matches the leader gate, so without the fallback the dwell would never
        // expire and the squad would park in onHold forever (the SQ-96 stall).
        BattleSimulation sim = openArena();
        Entity member = add(sim, "m", 4, 3);
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;
        squad.leaderId = 0L;
        squad.patrolDwellTimer = 4.0f;
        squad.patrolWaypointX = 3; squad.patrolWaypointY = 3;

        PatrolMotion.advance(member, squad, sim, KEEP, false);
        assertTrue(squad.patrolDwellTimer < 4.0f,
                "a leaderless squad must still drain its dwell so patrol never freezes");
    }

    @Test
    public void dwellStillDrainsWhenLeaderIdIsStale() {
        // leaderId points at a unit that isn't live (a death path that didn't
        // promote). resolveUnit returns null → same fallback as the 0L case.
        BattleSimulation sim = openArena();
        Entity member = add(sim, "m", 4, 3);
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;
        squad.leaderId = 999_999L;   // never-registered id → resolves to null
        squad.patrolDwellTimer = 4.0f;
        squad.patrolWaypointX = 3; squad.patrolWaypointY = 3;

        PatrolMotion.advance(member, squad, sim, KEEP, false);
        assertTrue(squad.patrolDwellTimer < 4.0f,
                "a stale (dead) leaderId must still drain the dwell, not freeze the patrol");
    }

    @Test
    public void unreachableWaypointGetsReRolled() {
        // Member in the west room, waypoint in the walled-off east room. The
        // pathfinder finds no route, so moveToward parks — and advance must
        // invalidate the waypoint so needsNew re-picks next tick rather than
        // leaving the squad parked on an unreachable cell forever.
        BattleSimulation sim = walledRooms();
        Entity member = add(sim, "m", 2, 4);
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;
        squad.leaderId = member.entityId;
        squad.patrolDwellTimer = 0f;          // skip the dwell → hit the move branch
        squad.patrolWaypointX = 9; squad.patrolWaypointY = 4;  // east room — unreachable
        squad.centroidX = 2; squad.centroidY = 4;              // far from waypoint → not "arrived"

        PatrolMotion.advance(member, squad, sim, KEEP, false);

        assertTrue(squad.patrolWaypointX < 0 && squad.patrolWaypointY < 0,
                "an unreachable waypoint must be invalidated so the patrol re-rolls instead of freezing");
    }

    @Test
    public void reachableWaypointIsKept() {
        // Same setup but the waypoint is in the SAME room — reachable. The
        // waypoint must survive (no spurious re-roll) and the member moves.
        BattleSimulation sim = walledRooms();
        Entity member = add(sim, "m", 2, 4);
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;
        squad.leaderId = member.entityId;
        squad.patrolDwellTimer = 0f;
        squad.patrolWaypointX = 4; squad.patrolWaypointY = 7;  // west room — reachable
        squad.centroidX = 2; squad.centroidY = 4;

        PatrolMotion.advance(member, squad, sim, KEEP, false);

        assertEquals(4, squad.patrolWaypointX, "a reachable waypoint must not be re-rolled");
        assertEquals(7, squad.patrolWaypointY, "a reachable waypoint must not be re-rolled");
    }

    @Test
    public void fireIfAbleWritesMovingIntentWithoutTouchingCooldown() {
        // FiringSystem sweep: fireIfAble now authors a fire intent instead of
        // firing inline, and no longer touches cooldownTimer itself (the old
        // inline decrement was one of the epic's double-tick bugs).
        BattleSimulation sim = openArena();
        Entity marine = add(sim, "m", 3, 3);
        Entity enemy = new Entity("e", Faction.DEFENDER, UnitType.MARINE, 5, 3);
        sim.addUnit(enemy);
        sim.world().setAttackRange(marine.entityId, 10f);
        sim.world().setCooldownTimer(marine.entityId, 0.6f);

        PatrolMotion.fireIfAble(marine, sim);

        assertEquals(0.6f, sim.world().cooldownTimer(marine.entityId), 1e-6f,
                "fireIfAble must not decrement cooldownTimer itself anymore");
        assertEquals(enemy.entityId, sim.combat().fireTargetId(marine.entityId),
                "an in-range, visible enemy gets a fire intent written");
        assertEquals(FireStance.MOVING.ordinal(),
                sim.getRoster().entityWorld().getInt(marine.entityId, sim.getRoster().components().COMBAT,
                        BattleComponents.COMBAT_FIRE_STANCE),
                "the authored intent carries the MOVING stance");
    }

    @Test
    public void fireIfAbleHoldsIntentWhenOutOfRange() {
        BattleSimulation sim = openArena();
        Entity marine = add(sim, "m", 3, 3);
        Entity enemy = new Entity("e", Faction.DEFENDER, UnitType.MARINE, 9, 3);
        sim.addUnit(enemy);
        sim.world().setAttackRange(marine.entityId, 2f); // enemy is 6 cells away

        PatrolMotion.fireIfAble(marine, sim);

        assertEquals(0L, sim.combat().fireTargetId(marine.entityId),
                "an out-of-range enemy must not get a fire intent");
    }
}
