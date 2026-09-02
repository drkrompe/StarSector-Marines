package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Role;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionDefenderCommandTest {

    @Test
    void holdsMobileReserveUntilPublicAlarmMobilizesBoundedResponse() {
        try (BattleSimulation sim = simulation()) {
            int[] route = {15, 8, 14, 8, 13, 8, 12, 8, 11, 8,
                    10, 8, 9, 8, 8, 8, 7, 8, 6, 8, 5, 8, 4, 8};
            ExtractionObjective objective = new ExtractionObjective(
                    "EXTRACTION-01", "recovery package",
                    sim.getZoneGraph().zoneIdAt(15, 8), route);
            sim.addObjective(objective);
            Set<Integer> mobile = new TreeSet<>();
            for (int i = 0; i < 4; i++) {
                mobile.add(squad(sim, 20, 3 + i * 2));
            }
            ExtractionDefenderCommand command =
                    new ExtractionDefenderCommand(mobile);

            CommanderService.runSingle(command,
                    ExtractionDefenderCommandDisclosure.INSTANCE, sim);
            ExtractionDefenseSnapshot quiet = command.defenseSnapshot();
            assertEquals(ExtractionDefenseSnapshot.Phase.ROUTINE_SECURITY,
                    quiet.phase());
            assertEquals(1, assigned(sim, mobile));
            assertEquals(3, quiet.reserveCount());
            assertEquals(1, quiet.squadIntents().stream()
                    .filter(intent -> intent.role() == Role.SOURCE_GUARD)
                    .count());
            assertFalse(quiet.alarmActive());

            sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 15, 8));
            objective.tick(sim);
            CommanderService.runSingle(command,
                    ExtractionDefenderCommandDisclosure.INSTANCE, sim);
            ExtractionDefenseSnapshot alarm = command.defenseSnapshot();

            assertTrue(alarm.alarmActive());
            assertEquals(ExtractionDefenseSnapshot.Phase.ALARM_INTERDICTION,
                    alarm.phase());
            assertEquals(ExtractionDefenderCommand.ALARM_RESPONSE_LIMIT,
                    assigned(sim, mobile));
            assertEquals(1, alarm.reserveCount());
            assertEquals(-1, ExtractionDefenderCommandFacts.freeze(sim)
                    .payload().egressCellX());
            assertEquals(-1, ExtractionDefenderCommandFacts.freeze(sim)
                    .payload().payloadCellX());
            assertEquals(0f, ExtractionDefenderCommandFacts.freeze(sim)
                    .payload().progress());
        }
    }

    @Test
    void onlyABeliefNewerThanOneCommandPulseIsSomewhereToSendAResponder() {
        int tick = 1_000;
        int bound = ExtractionDefenderCommand.ACTIONABLE_CONTACT_TICKS;
        CommanderContact seenNow = contact(1L, tick);
        CommanderContact onTheBound = contact(2L, tick - bound);
        CommanderContact justPast = contact(3L, tick - bound - 1);
        CommanderContact remembered = contact(4L, tick - 4 * bound);

        assertEquals(List.of(seenNow, onTheBound),
                ExtractionDefenderCommand.actionable(
                        List.of(seenNow, onTheBound, justPast, remembered),
                        tick));
        assertEquals(List.of(), ExtractionDefenderCommand.actionable(
                List.of(justPast, remembered), tick));
        assertEquals(List.of(), ExtractionDefenderCommand.actionable(
                List.of(), tick));
    }

    @Test
    void respondersWithNothingToInterdictTakeSeparateSourcePerimeterCells() {
        try (BattleSimulation sim = simulation()) {
            int[] route = {15, 8, 14, 8, 13, 8, 12, 8, 11, 8,
                    10, 8, 9, 8, 8, 8, 7, 8, 6, 8, 5, 8, 4, 8};
            ExtractionObjective objective = new ExtractionObjective(
                    "EXTRACTION-01", "recovery package",
                    sim.getZoneGraph().zoneIdAt(15, 8), route);
            sim.addObjective(objective);
            Set<Integer> mobile = new TreeSet<>();
            for (int i = 0; i < 4; i++) mobile.add(squad(sim, 20, 3 + i * 2));
            ExtractionDefenderCommand command =
                    new ExtractionDefenderCommand(mobile);

            sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 15, 8));
            objective.tick(sim);
            CommanderService.runSingle(command,
                    ExtractionDefenderCommandDisclosure.INSTANCE, sim);
            ExtractionDefenseSnapshot alarm = command.defenseSnapshot();

            assertTrue(alarm.alarmActive());
            assertEquals(0, alarm.knownContactCount());
            List<SquadIntent> responders = alarm.squadIntents().stream()
                    .filter(intent -> intent.role() == Role.ALARM_RESPONDER)
                    .toList();
            assertEquals(ExtractionDefenderCommand.ALARM_RESPONSE_LIMIT - 1,
                    responders.size());
            for (SquadIntent responder : responders) {
                assertEquals("SOURCE_ALARM_RESPONSE", responder.reason());
                assertTrue(Math.abs(responder.targetCellX() - 15) <= 4
                                && Math.abs(responder.targetCellY() - 8) <= 4,
                        "responder left the source perimeter: " + responder);
            }
            assertEquals(responders.size(), responders.stream()
                    .map(intent -> intent.targetCellX() + ","
                            + intent.targetCellY())
                    .distinct().count(),
                    "responders were stacked on one cell");
        }
    }

    @Test
    void anInterdictionSearchWillNotOfferTheCellTheSourceGuardAlreadyHolds() {
        try (BattleSimulation sim = simulation()) {
            int[] route = {15, 8, 14, 8, 13, 8, 12, 8, 11, 8,
                    10, 8, 9, 8, 8, 8, 7, 8, 6, 8, 5, 8, 4, 8};
            ExtractionObjective objective = new ExtractionObjective(
                    "EXTRACTION-01", "recovery package",
                    sim.getZoneGraph().zoneIdAt(15, 8), route);
            sim.addObjective(objective);
            Set<Integer> mobile = new TreeSet<>();
            for (int i = 0; i < 4; i++) mobile.add(squad(sim, 20, 3 + i * 2));
            ExtractionDefenderCommand command =
                    new ExtractionDefenderCommand(mobile);

            still(sim, "escort", 15, 8);
            // Four cells west of the guard's own post. The interdiction search
            // opens on this contact plus (4, 0), which is the cell the source
            // guard took first.
            still(sim, "seen", 11, 4);
            sim.advance(BattleSimulation.TICK_DT);
            objective.tick(sim);
            CommanderService.runSingle(command,
                    ExtractionDefenderCommandDisclosure.INSTANCE, sim);
            ExtractionDefenseSnapshot alarm = command.defenseSnapshot();

            assertTrue(alarm.alarmActive());
            assertTrue(sim.getCommanderInfluence(Faction.DEFENDER).contacts()
                            .stream().anyMatch(seen -> seen.cellX() == 11
                                    && seen.cellY() == 4),
                    "the collision this test is named for needs that contact");
            SquadIntent guard = alarm.squadIntents().stream()
                    .filter(intent -> intent.role() == Role.SOURCE_GUARD)
                    .findFirst().orElseThrow();
            assertEquals(15, guard.targetCellX());
            assertEquals(4, guard.targetCellY());

            List<SquadIntent> interdiction = alarm.squadIntents().stream()
                    .filter(intent -> intent.role() == Role.INTERDICTION)
                    .toList();
            assertFalse(interdiction.isEmpty(),
                    "no interdiction squad was published to measure");
            for (SquadIntent squad : interdiction) {
                assertFalse(squad.targetCellX() == guard.targetCellX()
                                && squad.targetCellY() == guard.targetCellY(),
                        "interdiction was sent onto the guard's cell: " + squad);
            }
            List<SquadIntent> rallied = alarm.squadIntents().stream()
                    .filter(intent -> intent.targetCellX() >= 0)
                    .toList();
            assertEquals(rallied.size(), rallied.stream()
                            .map(intent -> intent.targetCellX() + ","
                                    + intent.targetCellY())
                            .distinct().count(),
                    "two squads of one pulse rallied on one cell");
        }
    }

    private static void still(BattleSimulation sim, String name, int x, int y) {
        EntitySpec spec = new EntitySpec(name, Faction.MARINE,
                UnitType.MARINE, x, y);
        spec.moveSpeed = 0f;
        sim.spawn(spec);
    }

    private static CommanderContact contact(long unitId, int observedTick) {
        return new CommanderContact(unitId, 4, 4, observedTick, 1f, 1f,
                BeliefSource.DIRECT, 0);
    }

    private static long assigned(BattleSimulation sim, Set<Integer> squads) {
        return squads.stream().filter(id ->
                sim.getSquad(id).assignedObjective != null).count();
    }

    private static int squad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec("d" + squadId,
                Faction.DEFENDER, UnitType.MILITIA, x, y).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squadId;
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(24, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(24, 16));
    }
}
