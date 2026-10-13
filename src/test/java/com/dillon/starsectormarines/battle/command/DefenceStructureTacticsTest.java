package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Candidate;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Role;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Task;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.shield.ShieldGenerator;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure-work chooser as a unit: which structures are work for a side, which squad is sent, and what it is
 * told. The sides are written both ways round in the tests on purpose; nothing here is special to attacker or defender.
 */
class DefenceStructureTacticsTest {

    private static final int SIZE = 80;

    private static CommandTopology openTopology() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        return CommandTopology.freeze(new BattleSimulation(grid, new CellTopology(SIZE, SIZE)));
    }

    private static DefenceStructureFact structure(Kind kind, int x, int y, Faction holder, Faction original,
                                                  boolean threatened) {
        return new DefenceStructureFact(kind, x, y, holder, original, threatened, 6);
    }

    private static Candidate squad(int id, int x, int y) {
        return new Candidate(id, x, y, null);
    }

    @Test
    void anEnemyHeldStructureIsTakenByTheNearestSquadWithAnOrderToItsDoorstep() {
        CommandTopology topology = openTopology();
        DefenceStructureFact generator = structure(Kind.SHIELD_GENERATOR, 40, 40, Faction.DEFENDER, Faction.DEFENDER, false);

        List<Task> tasks = DefenceStructureTactics.plan(Faction.MARINE, List.of(generator),
                List.of(squad(1, 10, 10), squad(2, 30, 40), squad(3, 70, 70)), topology);

        assertEquals(1, tasks.size());
        Task task = tasks.get(0);
        assertEquals(2, task.squadId(), "the nearest squad goes");
        assertEquals(Role.TAKE, task.role());
        assertEquals(AssignmentKind.ATTACK_MOVE, task.assignment().kind());
        int gap = Math.max(Math.abs(task.assignment().targetCellX() - 40), Math.abs(task.assignment().targetCellY() - 40));
        assertTrue(gap >= 2 && gap <= 6, "an order to ground within the structure's reach, was " + gap);
    }

    @Test
    void theSameRulesSendTheDefendersToATakenGeneratorTheyBuiltAsARetake() {
        CommandTopology topology = openTopology();
        DefenceStructureFact taken = structure(Kind.SHIELD_GENERATOR, 40, 40, Faction.MARINE, Faction.DEFENDER, false);

        List<Task> tasks = DefenceStructureTactics.plan(Faction.DEFENDER, List.of(taken),
                List.of(squad(7, 20, 20)), topology);

        assertEquals(1, tasks.size());
        assertEquals(Role.RETAKE, tasks.get(0).role());
        assertEquals(AssignmentKind.ATTACK_MOVE, tasks.get(0).assignment().kind());
    }

    @Test
    void anOwnStructureIsLeftAloneUntilItIsPressedThenAnAreaDefenceIsOrdered() {
        CommandTopology topology = openTopology();
        DefenceStructureFact calm = structure(Kind.FIRE_CONTROL, 40, 40, Faction.DEFENDER, Faction.DEFENDER, false);
        DefenceStructureFact pressed = structure(Kind.FIRE_CONTROL, 40, 40, Faction.DEFENDER, Faction.DEFENDER, true);
        List<Candidate> squads = List.of(squad(1, 30, 30));

        assertTrue(DefenceStructureTactics.plan(Faction.DEFENDER, List.of(calm), squads, topology).isEmpty());
        List<Task> tasks = DefenceStructureTactics.plan(Faction.DEFENDER, List.of(pressed), squads, topology);

        assertEquals(1, tasks.size());
        assertEquals(Role.DEFEND, tasks.get(0).role());
        assertEquals(AssignmentKind.DEFEND_AREA, tasks.get(0).assignment().kind());
    }

    @Test
    void aSquadAlreadyOnTheJobKeepsItEvenWhenAnotherIsNearer() {
        CommandTopology topology = openTopology();
        DefenceStructureFact generator = structure(Kind.SHIELD_GENERATOR, 40, 40, Faction.DEFENDER, Faction.DEFENDER, false);
        ObjectiveAssignment held = ObjectiveAssignment.attackMove(5, 38, 38);

        List<Task> tasks = DefenceStructureTactics.plan(Faction.MARINE, List.of(generator),
                List.of(new Candidate(5, 10, 10, held), squad(6, 39, 39)), topology);

        assertEquals(1, tasks.size());
        assertEquals(5, tasks.get(0).squadId());
        assertEquals(held, tasks.get(0).assignment(), "the same order, so nothing churns");
    }

    @Test
    void workIsCappedPrioritisedAndGivenOneSquadEach() {
        CommandTopology topology = openTopology();
        List<DefenceStructureFact> structures = List.of(
                structure(Kind.FIRE_CONTROL, 10, 10, Faction.DEFENDER, Faction.DEFENDER, false),
                structure(Kind.FIRE_CONTROL, 30, 10, Faction.DEFENDER, Faction.DEFENDER, false),
                structure(Kind.SHIELD_GENERATOR, 50, 10, Faction.DEFENDER, Faction.DEFENDER, false),
                structure(Kind.SHIELD_GENERATOR, 70, 10, Faction.MARINE, Faction.MARINE, true));

        List<Task> tasks = DefenceStructureTactics.plan(Faction.MARINE, structures,
                List.of(squad(1, 12, 12), squad(2, 32, 12), squad(3, 52, 12), squad(4, 68, 12)), topology);

        assertEquals(DefenceStructureTactics.MAX_TASKS, tasks.size());
        assertEquals(Role.DEFEND, tasks.get(0).role(), "an own structure under pressure comes first");
        assertEquals(4, tasks.get(0).squadId());
        assertEquals(Role.TAKE, tasks.get(1).role());
        assertEquals(1, tasks.get(1).squadId(), "then the first enemy structure in map order");
        assertFalse(tasks.get(0).squadId() == tasks.get(1).squadId());
    }

    @Test
    void nothingIsPlannedWithoutStructuresOrSquadsAndAFriendlyHeldOneIsNotATarget() {
        CommandTopology topology = openTopology();
        DefenceStructureFact ours = structure(Kind.SHIELD_GENERATOR, 40, 40, Faction.MARINE, Faction.MARINE, false);
        assertTrue(DefenceStructureTactics.plan(Faction.MARINE, List.of(), List.of(squad(1, 1, 1)), topology).isEmpty());
        assertTrue(DefenceStructureTactics.plan(Faction.MARINE, List.of(ours), List.of(squad(1, 1, 1)), topology).isEmpty());
        assertTrue(DefenceStructureTactics.plan(Faction.MARINE,
                List.of(structure(Kind.SHIELD_GENERATOR, 40, 40, Faction.DEFENDER, Faction.DEFENDER, false)),
                List.of(), topology).isEmpty());
    }

    @Test
    void anOrderIsRecognisedAsStructureWorkOnlyNearAKnownStructure() {
        DefenceStructureFact generator = structure(Kind.SHIELD_GENERATOR, 40, 40, Faction.DEFENDER, Faction.DEFENDER, false);
        assertTrue(DefenceStructureTactics.isTask(ObjectiveAssignment.attackMove(1, 42, 41), List.of(generator)));
        assertFalse(DefenceStructureTactics.isTask(ObjectiveAssignment.attackMove(1, 70, 70), List.of(generator)));
        assertFalse(DefenceStructureTactics.isTask(ObjectiveAssignment.clearZone(1, 3), List.of(generator)));
        assertFalse(DefenceStructureTactics.isTask(null, List.of(generator)));
        assertNull(DefenceStructureTactics.plan(Faction.MARINE, List.of(generator), List.of(), openTopology())
                .stream().findFirst().orElse(null));
    }

    @Test
    void aCommandIsOnlyToldOfStructuresItBuiltHeldOrHasSeen() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(SIZE, SIZE));
        sim.spawn(ShieldGenerator.create("company-gen", Faction.MARINE, 20, 20));

        assertEquals(1, sim.defenceStructures().size());
        assertEquals(1, ConquestCommandFacts.freeze(sim, Faction.MARINE).structures().size(), "its own side's");
        assertTrue(ConquestCommandFacts.freeze(sim, Faction.DEFENDER).structures().isEmpty(),
                "the other side never built it and has not seen it, so it is not told");

        // The other side takes it the ordinary way: standing by it, uncontested, for the capture time.
        sim.spawn(new EntitySpec("raider", Faction.DEFENDER, UnitType.MARINE, 23, 20).moveSpeed(0f));
        sim.spawn(new EntitySpec("far-marine", Faction.MARINE, UnitType.MARINE, 2, 2).moveSpeed(0f));
        float seconds = TurretCatalogRegistry.requireStructure(ShieldGenerator.STRUCTURE_ID).dome.captureSeconds() * 1.5f;
        for (int i = 0, n = Math.round(seconds / BattleSimulation.TICK_DT); i < n; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertEquals(Faction.DEFENDER, sim.defenceStructures().get(0).holder());
        assertEquals(1, ConquestCommandFacts.freeze(sim, Faction.DEFENDER).structures().size(),
                "a side holding a structure knows it");
        assertEquals(1, ConquestCommandFacts.freeze(sim, Faction.MARINE).structures().size(),
                "and the side that built it keeps knowing where it is");
    }

    private static long firstGenerator(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().name(unit).equals("company-gen")) return unit;
        }
        throw new AssertionError("no generator");
    }
}
