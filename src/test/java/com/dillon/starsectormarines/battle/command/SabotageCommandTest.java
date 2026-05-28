package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Coverage for {@link SabotageCommand}'s assignment policy. The map is a
 * trivial open grid — no walls/doorways needed because the commander only
 * picks the closest unfinished site by squad-centroid distance and reads
 * the zone id at the site cell.
 */
public class SabotageCommandTest {

    private static final int W = 20;
    private static final int H = 10;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * Mint a squad through the public sim API so it shows up in
     * {@code sim.getSquads()} (the commander reads from there). Centroid +
     * {@code aliveMembers} are normally refreshed by
     * {@code BattleSimulation.updateSquadAlertLevels} on the regular tick
     * path; we set them by hand here since the test calls
     * {@code SabotageCommand.tick} directly without driving a sim tick.
     */
    private static Squad addSquad(BattleSimulation sim, float centroidX, float centroidY) {
        Unit leader = new Unit("m", Faction.MARINE, UnitType.MARINE,
                Math.round(centroidX), Math.round(centroidY));
        sim.addUnit(leader);
        int sid = sim.mintSquad(Faction.MARINE, leader);
        leader.squadId = sid;
        Squad squad = sim.getSquad(sid);
        squad.aliveMembers = 1;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        return squad;
    }

    @Test
    public void assignsNonPlanterSquadToNearestUnfinishedSite() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective near = new ChargeSiteObjective(4, 4, 5f, "near");
        ChargeSiteObjective far  = new ChargeSiteObjective(18, 4, 5f, "far");
        sim.addObjective(near);
        sim.addObjective(far);

        Squad squad = addSquad(sim, 2f, 4f);

        new SabotageCommand().tick(sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "non-planter squad should receive an assignment");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
        int expectedZone = sim.getZoneGraph().zoneIdAt(4, 4);
        assertEquals(expectedZone, a.targetZoneId(),
                "should be routed to the near charge site, not the far one");
    }

    @Test
    public void planterEquippedSquadIsNotOverridden() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(10, 4, 5f, "site");
        sim.addObjective(site);

        Squad squad = addSquad(sim, 2f, 4f);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 2, 4);
        planter.squadId = squad.id;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = site;
        sim.addUnit(planter);

        new SabotageCommand().tick(sim);

        assertNull(squad.assignedObjective,
                "squad with a live planter on an unfinished site should be left for SecureObjectiveZone");
    }

    @Test
    public void completedSitesAreSkippedWhenChoosingTarget() {
        BattleSimulation sim = openSim();
        // The near site is "complete" — commander should pick the far one instead.
        ChargeSiteObjective near = completedSite(sim, 4, 4, "near");
        ChargeSiteObjective far  = new ChargeSiteObjective(18, 4, 5f, "far");
        sim.addObjective(near);
        sim.addObjective(far);

        Squad squad = addSquad(sim, 2f, 4f);

        new SabotageCommand().tick(sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a);
        int farZone = sim.getZoneGraph().zoneIdAt(18, 4);
        assertEquals(farZone, a.targetZoneId(), "completed site should be ignored");
    }

    @Test
    public void allSitesCompleteClearsAssignments() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective only = completedSite(sim, 4, 4, "only");
        sim.addObjective(only);

        Squad squad = addSquad(sim, 2f, 4f);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, 0);

        new SabotageCommand().tick(sim);

        assertNull(squad.assignedObjective,
                "no unfinished sites → commander clears stale assignments");
    }

    @Test
    public void idempotentReassignmentDoesNotChurnRecord() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(4, 4, 5f, "site");
        sim.addObjective(site);
        Squad squad = addSquad(sim, 2f, 4f);

        SabotageCommand cmd = new SabotageCommand();
        cmd.tick(sim);
        ObjectiveAssignment first = squad.assignedObjective;
        cmd.tick(sim);
        ObjectiveAssignment second = squad.assignedObjective;

        assertNotNull(first);
        // Same record instance — commander short-circuits when the chosen
        // zone matches the existing assignment, so an in-flight squad plan
        // doesn't get invalidated by re-allocation churn.
        assertEquals(first, second,
                "stable inputs should produce the same assignment record across ticks");
    }

    private static ChargeSiteObjective completedSite(BattleSimulation sim, int x, int y, String name) {
        ChargeSiteObjective cs = new ChargeSiteObjective(x, y, 0.0001f, name);
        // Plant a planter on top + tick once so isComplete() flips true
        // without needing the rest of the sim to be set up properly.
        Unit p = new Unit("complete-" + name, Faction.MARINE, UnitType.MARINE, x, y);
        p.role = UnitRole.PLANTER;
        p.assignedObjective = cs;
        p.setMoveProgress(0f);
        sim.addUnit(p);
        cs.tick(sim);
        return cs;
    }
}
