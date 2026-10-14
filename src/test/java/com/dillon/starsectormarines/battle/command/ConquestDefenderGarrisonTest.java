package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.BeltGarrisonMode;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam between {@link GarrisonDirector} and the defender commander: that a trench under believed pressure is
 * manned from the free patrols with a run-scoped area order, that a quiet trench is not, and that the response
 * to the same threat still leaves a reserve. The pressure arithmetic, hysteresis and ordering are the director's own
 * tests.
 */
class ConquestDefenderGarrisonTest {

    private static final int W = 30;
    private static final int H = 60;
    private static final int TRENCH_Y = 30;

    @BeforeEach
    void reactiveModeIsTheDefault() {
        Assumptions.assumeTrue(BeltGarrisonMode.current().reactiveManning(),
                "run without -Dbattle.fort.beltGarrison overriding the default");
    }

    @Test
    void aQuietTrenchIsNotManned() {
        BattleSimulation sim = openSimWithTrench();
        List<Squad> patrols = patrols(sim, 8);
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command(sim);
        tick(command, sim);

        for (Squad patrol : patrols) {
            assertTrue(patrol.assignedObjective == null || !patrol.assignedObjective.isGarrison(),
                    "no believed pressure, no garrison order");
        }
    }

    @Test
    void aTrenchUnderBelievedPressureIsMannedFromTheReserveWithARunScopedOrder() {
        BattleSimulation sim = openSimWithTrench();
        FightingPositionIndex index = sim.fightingPositions();
        int run = index.runIdAt(14, TRENCH_Y);
        assertTrue(run >= 0, "the trench is a fighting position");
        List<Squad> patrols = patrols(sim, 8);
        addDefender(sim, "reporter", 15, 33, UnitRole.PATROL);
        for (int i = 0; i < 4; i++) {
            sim.spawn(new EntitySpec("marine" + i, Faction.MARINE, UnitType.MARINE,
                    13 + i, 26).moveSpeed(0f).health(10_000f));
        }
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command(sim);
        tick(command, sim);

        List<Squad> manning = new ArrayList<>();
        for (Squad patrol : patrols) {
            if (patrol.assignedObjective != null && patrol.assignedObjective.isGarrison()) manning.add(patrol);
        }
        assertEquals(1, manning.size(), "one picket for the one pressed place");
        ObjectiveAssignment order = manning.get(0).assignedObjective;
        assertEquals(AssignmentKind.DEFEND_AREA, order.kind());
        assertEquals(run, order.occupyRunId());
        assertTrue(index.runIdAt(order.targetCellX(), order.targetCellY()) == run,
                "the circle is centred on the run it is scoped to");
        assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_GARRISONABLE_MAN,
                command.frontSnapshot().directiveFor(manning.get(0).id).reason());
        long reserve = patrols.stream().filter(p -> p.assignedObjective == null).count();
        assertTrue(reserve >= GarrisonDirector.RESERVE_FLOOR, "a free patrol is left in reserve");
    }

    private static List<Squad> patrols(BattleSimulation sim, int count) {
        List<Squad> patrols = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            patrols.add(addDefender(sim, "patrol" + i, 3 + 3 * i, 58, UnitRole.PATROL));
        }
        return patrols;
    }

    private static BattleSimulation openSimWithTrench() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        TileRegistry registry = TileRegistry.installed();
        int x0 = 8;
        int x1 = 21;
        for (int x = x0; x <= x1; x++) {
            int mask = (x < x1 ? 2 : 0) | (x > x0 ? 8 : 0);
            DoodadDef def = registry.doodad("doodad.fort-trench-" + mask);
            if (def == null) throw new IllegalStateException("no trench piece for mask " + mask);
            sim.addDoodad(new Doodad(x, TRENCH_Y, def));
        }
        return sim;
    }

    private static ConquestDefenderCommand command(BattleSimulation sim) {
        ConquestTrackLayout tracks = new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, W, H);
        return new ConquestDefenderCommand(tracks, ConquestDefenderStartingForce.capture(sim, tracks));
    }

    private static void tick(ConquestDefenderCommand command, BattleSimulation sim) {
        CommanderService.runSingle(command, ConquestCommandDisclosure.INSTANCE, sim);
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
