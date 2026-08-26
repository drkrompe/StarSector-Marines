package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabotageDefenderCommandTest {
    private static final int W = 24;
    private static final int H = 12;

    @Test
    void routineCoverageSpreadsBeforeDoublingAndHoldsReserve() {
        BattleSimulation sim = openSim();
        addSites(sim);
        Squad a = addDefender(sim, 1, 2, UnitRole.PATROL);
        Squad b = addDefender(sim, 1, 5, UnitRole.PATROL);
        Squad c = addDefender(sim, 1, 8, UnitRole.PATROL);
        Squad reserve = addDefender(sim, 2, 5, UnitRole.PATROL);
        SabotageDefenderCommand command = new SabotageDefenderCommand(
                Set.of(a.id, b.id, c.id, reserve.id));

        tick(command, sim);

        SabotageDefenseSnapshot snapshot = command.defenseSnapshot();
        assertEquals(3, snapshot.directives().stream()
                .filter(row -> row.role()
                        == SabotageDefenseSnapshot.Role.ROUTINE_SECURITY)
                .map(SabotageDefenseSnapshot.SquadDirective::siteIndex)
                .collect(Collectors.toSet()).size());
        assertEquals(1, snapshot.reserveCount());
        int reserveId = snapshot.directives().stream()
                .filter(row -> row.role() == SabotageDefenseSnapshot.Role.RESERVE)
                .findFirst().orElseThrow().squadId();
        assertNull(sim.getSquad(reserveId).assignedObjective);
    }

    @Test
    void activeAlarmMobilizesOnlyHeldReserveToKnownSite() {
        BattleSimulation sim = openSim();
        List<ChargeSiteObjective> sites = addSites(sim);
        Squad a = addDefender(sim, 1, 2, UnitRole.PATROL);
        Squad b = addDefender(sim, 1, 5, UnitRole.PATROL);
        Squad c = addDefender(sim, 1, 8, UnitRole.PATROL);
        Squad reserve = addDefender(sim, 2, 5, UnitRole.PATROL);
        SabotageDefenderCommand command = new SabotageDefenderCommand(
                Set.of(a.id, b.id, c.id, reserve.id));
        tick(command, sim);
        int heldReserveId = command.defenseSnapshot().directives().stream()
                .filter(row -> row.role() == SabotageDefenseSnapshot.Role.RESERVE)
                .findFirst().orElseThrow().squadId();

        ChargeSiteObjective alarmed = sites.get(1);
        sim.spawn(new EntitySpec("hidden-planter", Faction.MARINE,
                UnitType.MARINE, alarmed.cellX(), alarmed.cellY())
                .role(UnitRole.PLANTER).assignedObjective(alarmed));
        alarmed.tick(sim);
        tick(command, sim);

        SabotageDefenseSnapshot snapshot = command.defenseSnapshot();
        SabotageDefenseSnapshot.SquadDirective response =
                snapshot.directiveFor(heldReserveId);
        assertEquals(SabotageDefenseSnapshot.Role.ALARM_RESPONDER,
                response.role());
        assertEquals(1, response.siteIndex());
        assertTrue(Math.abs(alarmed.cellX() - response.markerCellX()) <= 3);
        assertTrue(Math.abs(alarmed.cellY() - response.markerCellY()) <= 3);
        assertFalse(alarmed.cellX() == response.markerCellX()
                && alarmed.cellY() == response.markerCellY(),
                "alarm response should use installation perimeter, not plant cell");
        assertEquals(0, snapshot.reserveCount());
        assertEquals(1, snapshot.site(1).respondingSquads());
    }

    @Test
    void authoredGarrisonAndLaterPatrolStayExternal() {
        BattleSimulation sim = openSim();
        addSites(sim);
        Squad mobile = addDefender(sim, 1, 2, UnitRole.PATROL);
        Squad garrison = addDefender(sim, 6, 2, UnitRole.GARRISON);
        Squad laterPatrol = addDefender(sim, 2, 8, UnitRole.PATROL);
        SabotageDefenderCommand command = new SabotageDefenderCommand(
                Set.of(mobile.id));

        tick(command, sim);

        assertEquals(SabotageDefenseSnapshot.Role.AUTHORED_POST,
                command.defenseSnapshot().directiveFor(garrison.id).role());
        assertEquals(SabotageDefenseSnapshot.Role.EXTERNAL,
                command.defenseSnapshot().directiveFor(laterPatrol.id).role());
        assertNull(garrison.assignedObjective);
        assertNull(laterPatrol.assignedObjective);
    }

    @Test
    void multipleAlarmsReceiveOneResponderBeforeAnySiteDoubles() {
        BattleSimulation sim = openSim();
        List<ChargeSiteObjective> sites = addSites(sim);
        Set<Integer> mobile = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 5; i++) {
            mobile.add(addDefender(sim, 1, 1 + i * 2,
                    UnitRole.PATROL).id);
        }
        SabotageDefenderCommand command = new SabotageDefenderCommand(mobile);
        tick(command, sim);
        for (int i = 0; i < 2; i++) {
            ChargeSiteObjective site = sites.get(i);
            sim.spawn(new EntitySpec("planter-" + i, Faction.MARINE,
                    UnitType.MARINE, site.cellX(), site.cellY())
                    .role(UnitRole.PLANTER).assignedObjective(site));
            site.tick(sim);
        }

        tick(command, sim);

        assertEquals(1, command.defenseSnapshot().site(0).respondingSquads());
        assertEquals(1, command.defenseSnapshot().site(1).respondingSquads());
    }

    @Test
    void expiredAlarmReturnsOnlyResponderToReserve() {
        BattleSimulation sim = openSim();
        List<ChargeSiteObjective> sites = addSites(sim);
        Set<Integer> mobile = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 4; i++) {
            mobile.add(addDefender(sim, 1, 2 + i * 2,
                    UnitRole.PATROL).id);
        }
        SabotageDefenderCommand command = new SabotageDefenderCommand(mobile);
        tick(command, sim);
        ChargeSiteObjective site = sites.get(1);
        long planter = sim.spawn(new EntitySpec("planter-expiry", Faction.MARINE,
                UnitType.MARINE, site.cellX(), site.cellY())
                .role(UnitRole.PLANTER).assignedObjective(site));
        site.tick(sim);
        tick(command, sim);
        assertEquals(1, command.defenseSnapshot().site(1).respondingSquads());
        int expires = site.defenderAlarm(sim.getSimTickIndex()).expiresTick();

        sim.role().setRole(planter, UnitRole.COMBATANT);
        while (sim.getSimTickIndex() <= expires) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        tick(command, sim);

        assertFalse(command.defenseSnapshot().site(1).alarmActive());
        assertEquals(0, command.defenseSnapshot().site(1).respondingSquads());
        assertEquals(1, command.defenseSnapshot().reserveCount());
        assertEquals(3, command.defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == SabotageDefenseSnapshot.Role.ROUTINE_SECURITY)
                .count());
    }

    @Test
    void completedSiteRedistributesOnlyMobileSecurity() {
        BattleSimulation sim = openSim();
        List<ChargeSiteObjective> sites = addSites(sim);
        Set<Integer> mobile = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 4; i++) {
            mobile.add(addDefender(sim, 1, 2 + i * 2,
                    UnitRole.PATROL).id);
        }
        Squad garrison = addDefender(sim, 6, 2, UnitRole.GARRISON);
        SabotageDefenderCommand command = new SabotageDefenderCommand(mobile);
        tick(command, sim);
        ChargeSiteObjective completed = sites.get(0);
        long planter = sim.spawn(new EntitySpec("fast-planter", Faction.MARINE,
                UnitType.MARINE, completed.cellX(), completed.cellY())
                .role(UnitRole.PLANTER).assignedObjective(completed));
        for (int i = 0; i < 200 && !completed.isComplete(); i++) {
            completed.tick(sim);
        }
        assertTrue(completed.isComplete());

        tick(command, sim);

        assertTrue(command.defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == SabotageDefenseSnapshot.Role.ROUTINE_SECURITY)
                .noneMatch(row -> row.siteIndex() == 0));
        assertEquals(SabotageDefenseSnapshot.Role.AUTHORED_POST,
                command.defenseSnapshot().directiveFor(garrison.id).role());
        assertNull(garrison.assignedObjective);
    }

    @Test
    void defenderFactsContainNoAttackerTaskOrProgressComponents() {
        Set<String> forbidden = Set.of("progress", "plantDuration",
                "planterOnSite", "planterSquadIds", "retrieverSquadIds",
                "activeKitDrops", "unclaimedKitDrops", "unitId", "squadId");
        Set<String> names = Arrays.stream(
                        SabotageDefenderCommandFacts.Site.class
                                .getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertTrue(names.stream().noneMatch(forbidden::contains));
        assertEquals(Set.of("active", "raisedTick", "expiresTick"),
                Arrays.stream(SabotageDefenderCommandFacts.Alarm.class
                                .getRecordComponents())
                        .map(RecordComponent::getName).collect(Collectors.toSet()));
    }

    @Test
    void disclosuresRejectWrongPerspective() {
        BattleSimulation sim = openSim();
        CommandTopology topology = CommandTopology.freeze(sim);
        CommandAssignmentSnapshot assignments =
                new CommandAssignmentSnapshot(java.util.Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> SabotageCommandDisclosure.INSTANCE.freeze(sim,
                        Faction.DEFENDER, topology, assignments));
        assertThrows(IllegalArgumentException.class,
                () -> SabotageDefenderCommandDisclosure.INSTANCE.freeze(sim,
                        Faction.MARINE, topology, assignments));
    }

    @Test
    void alarmHoldsAfterInterruptionAndExpiresDeterministically() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(8, 5, 100f,
                "SAB-01", "relay");
        sim.addObjective(site);
        long planter = sim.spawn(new EntitySpec("planter", Faction.MARINE,
                UnitType.MARINE, 8, 5).role(UnitRole.PLANTER)
                .assignedObjective(site));
        site.tick(sim);
        int expires = site.defenderAlarm(sim.getSimTickIndex()).expiresTick();
        assertTrue(site.defenderAlarm(sim.getSimTickIndex()).active());

        sim.role().setRole(planter, UnitRole.COMBATANT);
        while (sim.getSimTickIndex() <= expires) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertFalse(site.defenderAlarm(sim.getSimTickIndex()).active());
        assertEquals(-1, site.defenderAlarm(sim.getSimTickIndex()).raisedTick());
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static List<ChargeSiteObjective> addSites(BattleSimulation sim) {
        List<ChargeSiteObjective> sites = List.of(
                new ChargeSiteObjective(6, 2, 5f, "SAB-01", "one"),
                new ChargeSiteObjective(12, 5, 5f, "SAB-02", "two"),
                new ChargeSiteObjective(20, 8, 5f, "SAB-03", "three"));
        sites.forEach(sim::addObjective);
        return sites;
    }

    private static Squad addDefender(BattleSimulation sim, int x, int y,
                                     UnitRole role) {
        long leader = sim.spawn(new EntitySpec("d-" + x + "-" + y,
                Faction.DEFENDER, UnitType.MILITIA, x, y).role(role));
        int squadId = sim.mintSquad(Faction.DEFENDER, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }

    private static void tick(SabotageDefenderCommand command,
                             BattleSimulation sim) {
        CommanderService.runSingle(command,
                SabotageDefenderCommandDisclosure.INSTANCE, sim);
    }
}
