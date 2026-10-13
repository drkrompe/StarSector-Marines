package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A squad whose every member the ambient work service holds is a works crew at its bench, and the unit dispatcher
 * does not run it, so an order to it is never carried out. The command frame says so, and a commander offers it no
 * structure work: a lone relief technician once held the one take-the-bunker task for twenty-two thousand ticks while
 * the order sat unexecuted.
 */
class CommandFrameCrewTest {

    private static BattleSimulation sim() {
        NavigationGrid grid = new NavigationGrid(24, 24);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(24, 24), 7L);
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static AmbientTaskRoute atTheBench() {
        return new AmbientTaskRoute("bench", 0f, 1f, 0f, AmbientThreatPolicy.NONE, List.of(
                new AmbientTaskRoute.Stop(4.5f, 5.5f, 1f, AmbientActivity.WORKING, 4.5f, 9.5f)));
    }

    private static long member(BattleSimulation sim, int squadId, int x, int y) {
        return sim.spawn(new EntitySpec("m" + x + "-" + y, Faction.MARINE, UnitType.MARINE, x, y).squad(squadId));
    }

    @Test
    void aSquadEveryMemberOfWhichIsAtItsBenchIsACrewAndNotAnyOtherSquad() {
        BattleSimulation sim = sim();
        int crew = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long a = member(sim, crew, 3, 3);
        long b = member(sim, crew, 3, 4);
        sim.ambientTasks().assign(a, atTheBench());
        sim.ambientTasks().assign(b, atTheBench());
        int mixed = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long worker = member(sim, mixed, 9, 3);
        member(sim, mixed, 9, 4);
        sim.ambientTasks().assign(worker, atTheBench());
        int riflemen = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        member(sim, riflemen, 15, 3);
        for (int id : new int[] {crew, mixed, riflemen}) sim.getSquad(id).aliveMembers = 2;
        sim.getSquad(riflemen).aliveMembers = 1;

        CommandFrame frame = CommandFrame.freeze(sim, Faction.MARINE, CommandTopology.freeze(sim),
                new CommandAssignmentSnapshot(Map.of()));

        assertTrue(frame.squad(crew).crew(), "both members are at their bench");
        assertFalse(frame.squad(mixed).crew(), "one rifleman among them is a fighting squad");
        assertFalse(frame.squad(riflemen).crew());
    }

    @Test
    void aCrewStoodDownByTroubleIsStillACrewBecauseItIsDueBack() {
        BattleSimulation sim = sim();
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long worker = member(sim, squad, 3, 3);
        sim.getSquad(squad).aliveMembers = 1;
        sim.ambientTasks().assign(worker, new AmbientTaskRoute("bench", 0f, 1f, 6f,
                AmbientThreatPolicy.ANY_COMBATANT, List.of(
                        new AmbientTaskRoute.Stop(4.5f, 5.5f, 1f, AmbientActivity.WORKING, 4.5f, 9.5f))));
        sim.spawn(new EntitySpec("stranger", Faction.DEFENDER, UnitType.MARINE, 5, 3));
        sim.ambientTasks().advance(0f);
        assertTrue(sim.ambientTasks().isStoodDown(worker), "the stranger stood the work down");

        CommandFrame frame = CommandFrame.freeze(sim, Faction.MARINE, CommandTopology.freeze(sim),
                new CommandAssignmentSnapshot(Map.of()));

        assertTrue(frame.squad(squad).crew(),
                "a stood-down crew is due back at its bench, so it is not a squad on strength either");
    }
}
