package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.mech.GoapMechBehavior;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.infantry.SweepAssignedSectorGoal;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultCommandTest {

    @Test
    void emptyBeliefStartsSearchAndHiddenDefenderDoesNotChangeOrder() {
        BattleSimulation empty = openSim(60, 30);
        BattleSimulation hidden = openSim(60, 30);
        Squad emptySquad = addSquad(empty, Faction.MARINE, UnitType.MARINE, 3, 3);
        Squad hiddenSquad = addSquad(hidden, Faction.MARINE, UnitType.MARINE, 3, 3);
        hidden.spawn(new EntitySpec("hidden", Faction.DEFENDER,
                UnitType.MARINE_RED, 57, 27).health(10_000f));

        AssaultCommand emptyCommand = new AssaultCommand();
        AssaultCommand hiddenCommand = new AssaultCommand();
        tick(emptyCommand, empty);
        tick(hiddenCommand, hidden);

        assertEquals(emptySquad.assignedObjective, hiddenSquad.assignedObjective,
                "unseen live occupancy must not alter a search order");
        assertEquals(AssignmentKind.SWEEP_SECTOR,
                hiddenSquad.assignedObjective.kind());
        assertEquals(List.of(AssaultSearchSnapshot.SectorStatus.SEARCHING),
                hiddenCommand.searchSnapshot().sectors().stream()
                        .map(AssaultSearchSnapshot.SectorState::status).distinct()
                        .toList());
        assertNotEquals(57, hiddenSquad.assignedObjective.targetCellX(),
                "the order is a public search leg, not the hidden hostile cell");
    }

    @Test
    void initialAllocationSpreadsBeforeSurplusReinforces() {
        BattleSimulation sim = openSim(60, 30);
        AssaultCommand command = new AssaultCommand();
        for (int i = 0; i < 4; i++) {
            addSquad(sim, Faction.MARINE, UnitType.MARINE, 2 + i, 2);
        }

        tick(command, sim);

        Set<Integer> sectors = new HashSet<>();
        for (AssaultSearchSnapshot.SquadDirective directive
                : command.searchSnapshot().directives()) {
            sectors.add(directive.sectorIndex());
        }
        assertEquals(4, sectors.size(),
                "each reachable sector receives one squad before doubling");
    }

    @Test
    void freshBeliefActivatesSectorAndDrawsOnlySurplusReinforcement() {
        BattleSimulation sim = openSim(60, 30);
        for (int i = 0; i < 5; i++) {
            addSquad(sim, Faction.MARINE, UnitType.MARINE, 3 + i, 3);
        }
        sim.spawn(new EntitySpec("reported", Faction.DEFENDER,
                UnitType.MARINE_RED, 25, 5).health(10_000f).moveSpeed(0f));
        sim.advance(BattleSimulation.TICK_DT);
        AssaultCommand command = new AssaultCommand();

        tick(command, sim);

        AssaultSearchSnapshot.SectorState active = command.searchSnapshot()
                .sectors().stream()
                .filter(sector -> sector.status()
                        == AssaultSearchSnapshot.SectorStatus.ACTIVE)
                .findFirst().orElseThrow();
        assertEquals(2, active.assignedSquads());
        assertEquals(1, command.searchSnapshot().directives().stream()
                .filter(row -> row.reason() == AssaultSearchSnapshot.AssignmentReason
                        .ACTIVE_CONTACT_REINFORCEMENT).count());
        assertEquals(4, command.searchSnapshot().sectors().stream()
                .filter(sector -> sector.assignedSquads() > 0).count(),
                "reported contact does not consume squads needed for first coverage");
    }

    @Test
    void indirectReportMakesSectorSuspectedWithoutExactHostileBriefing() {
        BattleSimulation sim = openSim(60, 30);
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(15, y, false);
        }
        addSquad(sim, Faction.MARINE, UnitType.MARINE, 5, 5);
        long source = sim.spawn(new EntitySpec("heard", Faction.DEFENDER,
                UnitType.MARINE_RED, 20, 5).health(10_000f).moveSpeed(0f));
        sim.postShot(new ShotEvent(source, 20.5f, 5.5f, 5.5f, 5.5f,
                false, Faction.DEFENDER, 0.1f));
        sim.advance(BattleSimulation.TICK_DT);
        AssaultCommand command = new AssaultCommand();

        tick(command, sim);

        AssaultSearchSnapshot.SectorState suspected = command.searchSnapshot()
                .sectors().stream()
                .filter(sector -> sector.status()
                        == AssaultSearchSnapshot.SectorStatus.SUSPECTED)
                .findFirst().orElseThrow();
        assertEquals(1, suspected.believedContacts());
        assertNotEquals(20, command.searchSnapshot().directives().get(0)
                .targetCellX(), "other squads receive a sector leg, not the report cell");
    }

    @Test
    void reachingCommittedLegPersistsCoverageAndAdvancesOrder() {
        BattleSimulation sim = openSim(60, 30);
        Squad squad = addSquad(sim, Faction.MARINE, UnitType.MARINE, 3, 3);
        AssaultCommand command = new AssaultCommand();
        tick(command, sim);
        ObjectiveAssignment first = squad.assignedObjective;
        AssaultSearchSnapshot.SquadDirective firstDirective =
                command.searchSnapshot().directiveFor(squad.id);
        squad.centroidX = first.targetCellX() + 0.5f;
        squad.centroidY = first.targetCellY() + 0.5f;

        tick(command, sim);

        AssaultSearchSnapshot.SectorState sector = command.searchSnapshot()
                .sector(firstDirective.sectorIndex());
        assertEquals(1, sector.visitedLegs());
        assertNotEquals(first, squad.assignedObjective);
        GoapInfantryBehavior.replanIfNeeded(squad, sim);
        assertNotNull(squad.currentPlan);
        assertEquals("SweepAssignedSector", squad.currentGoal.name());
    }

    @Test
    void completedFirstPassKeepsBoundedRecheckOrdersActive() {
        BattleSimulation sim = openSim(60, 30);
        for (int i = 0; i < 4; i++) {
            addSquad(sim, Faction.MARINE, UnitType.MARINE, 2 + i, 2);
        }
        AssaultCommand command = new AssaultCommand();
        tick(command, sim);
        for (int round = 0; round < 12
                && command.searchSnapshot().phase()
                != AssaultSearchSnapshot.Phase.RECHECK; round++) {
            for (Squad squad : sim.getSquads()) {
                ObjectiveAssignment assignment = squad.assignedObjective;
                if (squad.faction != Faction.MARINE || assignment == null) continue;
                squad.centroidX = assignment.targetCellX() + 0.5f;
                squad.centroidY = assignment.targetCellY() + 0.5f;
            }
            tick(command, sim);
            assertDirectiveTargetsInsideAssignedSectors(command);
        }

        assertEquals(AssaultSearchSnapshot.Phase.RECHECK,
                command.searchSnapshot().phase());
        assertEquals(2, command.searchSnapshot().searchPass());
        assertEquals(4, command.searchSnapshot().directives().stream()
                .filter(row -> row.assignmentKind() == AssignmentKind.SWEEP_SECTOR)
                .count(), "hidden survivors cannot leave the completed search idle");
    }

    @Test
    void indoorZoneAddsARequiredSearchLegWithoutDisclosingOccupancy() {
        BattleSimulation sim = roomPlusExteriorSim();
        addSquad(sim, Faction.MARINE, UnitType.MARINE, 5, 3);
        AssaultCommand command = new AssaultCommand();
        tick(command, sim);

        assertTrue(command.sweepLegsInSector(0).stream()
                        .anyMatch(cell -> cell[0] <= 2 && cell[1] <= 2),
                "the enclosed navigation zone contributes a deterministic leg");
    }

    @Test
    void strongerOrEqualExternalOwnershipAndMechExecutionArePreserved() {
        BattleSimulation sim = openSim(60, 30);
        Squad payload = addSquad(sim, Faction.MARINE, UnitType.MARINE, 3, 3);
        ObjectiveAssignment escort = ObjectiveAssignment.escort(payload.id, 5, 5);
        sim.assignSquadCommand(escort, CommandAuthority.PAYLOAD,
                "payload", "escort payload");
        Squad otherMission = addSquad(sim, Faction.MARINE, UnitType.MARINE, 7, 3);
        ObjectiveAssignment equal = ObjectiveAssignment.sweepSector(
                otherMission.id, 9, 3);
        sim.assignSquadCommand(equal, CommandAuthority.MISSION_COMMAND,
                "other-mission", "scripted mission ownership");
        AssaultCommand command = new AssaultCommand();
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(30, y, false);
        }
        sim.spawn(new EntitySpec("hidden", Faction.DEFENDER,
                UnitType.MARINE_RED, 55, 25).health(10_000f));
        sim.setAutonomousCommander(Faction.MARINE, command,
                AssaultCommandDisclosure.INSTANCE);

        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        assertEquals(escort, payload.assignedObjective);
        assertEquals(AssaultSearchSnapshot.AssignmentReason
                        .EXTERNAL_OWNERSHIP_PRESERVED,
                command.searchSnapshot().directiveFor(payload.id).reason());
        assertEquals(equal, otherMission.assignedObjective);
        assertEquals(AssaultSearchSnapshot.AssignmentReason
                        .EXTERNAL_OWNERSHIP_PRESERVED,
                command.searchSnapshot().directiveFor(otherMission.id).reason());
        assertTrue(GoapMechBehavior.MECH_GOALS.contains(
                SweepAssignedSectorGoal.INSTANCE));
    }

    private static BattleSimulation openSim(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static BattleSimulation roomPlusExteriorSim() {
        BattleSimulation sim = openSim(30, 10);
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(3, 0, false);
        grid.setWalkable(3, 2, false);
        grid.setWalkable(0, 3, false);
        grid.setWalkable(1, 3, false);
        grid.setWalkable(2, 3, false);
        grid.setWalkable(3, 3, false);
        grid.setDoorway(3, 1, true);
        return sim;
    }

    private static Squad addSquad(BattleSimulation sim, Faction faction,
                                  UnitType type, int x, int y) {
        long leader = sim.spawn(new EntitySpec("squad-" + sim.getSquads().size(),
                faction, type, x, y).moveSpeed(0f));
        int squadId = sim.mintSquad(faction, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }

    private static void assertDirectiveTargetsInsideAssignedSectors(
            AssaultCommand command) {
        for (AssaultSearchSnapshot.SquadDirective directive
                : command.searchSnapshot().directives()) {
            AssaultSearchSnapshot.SectorState sector = command.searchSnapshot()
                    .sector(directive.sectorIndex());
            assertNotNull(sector);
            assertTrue(directive.targetCellX() >= sector.minCellX()
                            && directive.targetCellX()
                            < sector.minCellX() + sector.width()
                            && directive.targetCellY() >= sector.minCellY()
                            && directive.targetCellY()
                            < sector.minCellY() + sector.height(),
                    "directive target must belong to its reported search sector");
        }
    }

    private static void tick(AssaultCommand command, BattleSimulation sim) {
        CommanderService.runSingle(command, AssaultCommandDisclosure.INSTANCE, sim);
    }
}
