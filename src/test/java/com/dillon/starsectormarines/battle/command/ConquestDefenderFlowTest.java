package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam between GarrisonFlow and the defender commander on an open field with no prepared ground. With the track rally
 * and the flow unified, pressure on ground with nothing to man is answered by the rally alone, at its own band point and
 * under its own laws; nothing is staged for it. With the unification off (the same-build control) the earlier flow still
 * posts a bounded picket behind it. The area, demand and ordering arithmetic is {@link GarrisonFlowTest}'s.
 */
class ConquestDefenderFlowTest {

    private static final int W = 30;
    private static final int H = 120;

    @Test
    void aQuietFieldStagesNobody() {
        Assumptions.assumeTrue(flowEnabled(), "run without -Dbattle.garrison.flow=false");
        BattleSimulation sim = openSim();
        List<Squad> patrols = patrols(sim, 8);
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command(sim);
        CommanderService.runSingle(command, ConquestCommandDisclosure.INSTANCE, sim);

        for (Squad patrol : patrols) assertTrue(patrol.assignedObjective == null, "no pressure, no post");
    }

    @Test
    void pressureOnGroundWithNothingToManIsAnsweredByTheRallyAloneAtItsBandPoint() {
        Assumptions.assumeTrue(flowEnabled() && unifiedEnabled(), "run without -Dbattle.garrison.flow=false or unifiedRally=false");
        BattleSimulation sim = openSim();
        List<Squad> patrols = patrols(sim, 10);
        addDefender(sim, "reporter", 15, 50, UnitRole.PATROL);
        for (int i = 0; i < 4; i++) {
            sim.spawn(new EntitySpec("marine" + i, Faction.MARINE, UnitType.MARINE, 13 + i, 40)
                    .moveSpeed(0f).health(10_000f));
        }
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command(sim);
        for (int plan = 0; plan < 4; plan++) CommanderService.runSingle(command, ConquestCommandDisclosure.INSTANCE, sim);

        ConquestTrackLayout tracks = new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, W, H);
        int deepest = Math.round(tracks.forwardCoordinate(15, 40));
        int bandForward = deepest / ConquestDefenderCommand.COARSE_BAND_CELLS * ConquestDefenderCommand.COARSE_BAND_CELLS
                + ConquestDefenderCommand.COARSE_BAND_CELLS + ConquestDefenderCommand.RALLY_REAR_OFFSET_CELLS;
        int rallied = 0;
        for (Squad patrol : patrols) {
            ObjectiveAssignment order = patrol.assignedObjective;
            if (order == null) continue;
            assertEquals(AssignmentKind.DEFEND_TRACK, order.kind(), "nothing is staged where there is nothing to man");
            assertEquals(Math.round(tracks.forwardCoordinate(order.targetCellX(), order.targetCellY())), bandForward, 6,
                    "the rally keeps its band point");
            assertTrue(command.frontSnapshot().directiveFor(patrol.id).reason()
                    != ConquestFrontSnapshot.AssignmentReason.DEFENDER_GARRISONABLE_STAGE);
            rallied++;
        }
        assertTrue(rallied > 0, "the track is still answered");
        long free = patrols.stream().filter(p -> p.assignedObjective == null).count();
        assertTrue(free >= GarrisonDirector.RESERVE_FLOOR, "the reserve floor stands");
    }

    @Test
    void pressureStagesABoundedPicketBehindItAndLeavesAReserve() {
        Assumptions.assumeTrue(flowEnabled() && !unifiedEnabled(), "the earlier flow: run with -Dbattle.garrison.unifiedRally=false");
        BattleSimulation sim = openSim();
        List<Squad> patrols = patrols(sim, 10);
        addDefender(sim, "reporter", 15, 50, UnitRole.PATROL);
        for (int i = 0; i < 4; i++) {
            sim.spawn(new EntitySpec("marine" + i, Faction.MARINE, UnitType.MARINE, 13 + i, 40)
                    .moveSpeed(0f).health(10_000f));
        }
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command(sim);
        for (int plan = 0; plan < 4; plan++) CommanderService.runSingle(command, ConquestCommandDisclosure.INSTANCE, sim);

        List<Squad> staged = new ArrayList<>();
        for (Squad patrol : patrols) {
            if (patrol.assignedObjective != null && patrol.assignedObjective.kind() == AssignmentKind.DEFEND_AREA) {
                staged.add(patrol);
            }
        }
        assertTrue(!staged.isEmpty(), "pressure draws a patrol");
        assertTrue(staged.size() <= GarrisonFlow.MAX_PER_AREA, "one area asks for no more than its cap");
        for (Squad squad : staged) {
            assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_GARRISONABLE_STAGE,
                    command.frontSnapshot().directiveFor(squad.id).reason());
            assertTrue(squad.assignedObjective.targetCellY() > 40,
                    "the post is on the defender's side of the contact, further toward its rear");
            assertTrue(!squad.assignedObjective.isGarrison(), "staging is not manning");
        }
        long free = patrols.stream().filter(p -> p.assignedObjective == null).count();
        assertTrue(free >= GarrisonDirector.RESERVE_FLOOR, "a free patrol is left in reserve");
    }

    @Test
    void aPoolTooSmallToSpareAPatrolStagesNobody() {
        Assumptions.assumeTrue(flowEnabled(), "run without -Dbattle.garrison.flow=false");
        BattleSimulation sim = openSim();
        List<Squad> patrols = patrols(sim, 1);
        addDefender(sim, "reporter", 15, 50, UnitRole.GARRISON);
        for (int i = 0; i < 4; i++) {
            sim.spawn(new EntitySpec("marine" + i, Faction.MARINE, UnitType.MARINE, 13 + i, 40)
                    .moveSpeed(0f).health(10_000f));
        }
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command(sim);
        for (int plan = 0; plan < 3; plan++) CommanderService.runSingle(command, ConquestCommandDisclosure.INSTANCE, sim);

        for (Squad patrol : patrols) {
            assertTrue(command.frontSnapshot().directiveFor(patrol.id).reason()
                            != ConquestFrontSnapshot.AssignmentReason.DEFENDER_GARRISONABLE_STAGE,
                    "the only patrol is not spared for a post");
        }
    }

    private static boolean unifiedEnabled() {
        return !"false".equals(System.getProperty("battle.garrison.unifiedRally"));
    }

    private static boolean flowEnabled() {
        return !"false".equals(System.getProperty("battle.garrison.flow"))
                && !"false".equals(System.getProperty("battle.fort.stagedReserve"));
    }

    private static List<Squad> patrols(BattleSimulation sim, int count) {
        List<Squad> patrols = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            patrols.add(addDefender(sim, "patrol" + i, 3 + 2 * i, 115, UnitRole.PATROL));
        }
        return patrols;
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static ConquestDefenderCommand command(BattleSimulation sim) {
        ConquestTrackLayout tracks = new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, W, H);
        return new ConquestDefenderCommand(tracks, ConquestDefenderStartingForce.capture(sim, tracks));
    }

    private static Squad addDefender(BattleSimulation sim, String name, int x, int y, UnitRole role) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec(name, Faction.DEFENDER, UnitType.MILITIA, x, y)
                .role(role).squad(squadId).moveSpeed(0f));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }
}
