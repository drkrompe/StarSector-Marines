package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code TacticalScoring.findFiringPosition} does not verify reachability —
 * its sibling {@code findReachableFiringPosition} exists because of that — so
 * a target walled off from the pursuer yields a cell on the wrong side of the
 * wall. Setting that empty path pins the member, and since {@code setPath}
 * stamps the repath throttle only on a non-empty assignment, the same
 * full-component search runs again on the next tick.
 *
 * <p>{@code ClearZone} already dropped the target on this case and documented
 * it as the SQ-96 garrison freeze. These are the pursuit paths that did not.
 */
public class PursuitReachabilityTest {

    private static final int W = 48;
    private static final int H = 24;
    private static final int WALL_X = 24;

    /** Two halves of open floor with no way between them. */
    private static BattleSimulation sealedSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
            grid.setWalkable(WALL_X, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void anUnreachableTargetIsDroppedRatherThanPinningThePursuer() {
        BattleSimulation sim = sealedSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("m0", Faction.MARINE,
                UnitType.MARINE, 18, 12).squad(squadId));
        long enemy = sim.spawn(new EntitySpec("t0", Faction.DEFENDER,
                UnitType.MARINE_RED, 30, 12));
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = 18.5f;
        squad.centroidY = 12.5f;
        sim.world().setTargetId(member, enemy);

        ActionStatus status = ApproachPosture.INSTANCE.execute(member, squad, sim);

        assertEquals(ActionStatus.RUNNING, status,
                "the squad plan stays alive; only this member's target is dropped");
        assertEquals(0L, sim.targetOf(member),
                "a target with no reachable firing position must be dropped so "
                        + "the next acquisition picks something engageable");
        assertTrue(Paths.isEmpty(sim.world().path(member)),
                "the member must not be left holding an empty path, which "
                        + "dodges the repath throttle and re-runs the search every tick");
    }

    @Test
    public void aReachableTargetIsStillPursued() {
        // Control: same geometry with both on one side of the wall. Without
        // this the test above would pass just as well on a change that dropped
        // every target.
        BattleSimulation sim = sealedSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("m0", Faction.MARINE,
                UnitType.MARINE, 4, 12).squad(squadId));
        long enemy = sim.spawn(new EntitySpec("t0", Faction.DEFENDER,
                UnitType.MARINE_RED, 20, 12));
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = 4.5f;
        squad.centroidY = 12.5f;
        sim.world().setTargetId(member, enemy);

        ApproachPosture.INSTANCE.execute(member, squad, sim);

        assertEquals(enemy, sim.targetOf(member),
                "a target the member can walk to must be kept");
    }
}
