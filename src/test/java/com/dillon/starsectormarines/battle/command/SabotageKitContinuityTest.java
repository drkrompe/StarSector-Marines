package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end transition coverage for a Sabotage charge kit after its original
 * planter is lost. These tests intentionally drive the production damage and
 * per-tick equipment systems so a passing assertion proves continuity through
 * the same seams used by a live battle.
 */
public class SabotageKitContinuityTest {

    private static final int WIDTH = 24;
    private static final int HEIGHT = 12;
    private static final int SITE_X = 5;
    private static final int SITE_Y = 5;

    @Test
    public void planterDeathEmitsOneDropForTheExactChargeSite() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = site();
        sim.addObjective(site);
        long planter = sim.spawn(new EntitySpec("planter", Faction.MARINE,
                UnitType.MARINE, SITE_X, SITE_Y)
                .role(UnitRole.PLANTER).assignedObjective(site));

        kill(sim, planter);

        assertFalse(sim.getRoster().isAliveById(planter));
        assertEquals(1, sim.getEquipmentDrops().size());
        EquipmentDrop drop = sim.getEquipmentDrops().get(0);
        assertEquals(SITE_X, drop.cellX);
        assertEquals(SITE_Y, drop.cellY);
        assertSame(site, drop.objective,
                "death must preserve the exact named-site objective on the kit");
        assertFalse(drop.consumed);
    }

    @Test
    public void droppedKitRecruitsNearestFreeSquadAndCommanderYields() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = site();
        sim.addObjective(site);
        Squad nearest = addSquad(sim, 8, SITE_Y);
        Squad farther = addSquad(sim, 16, SITE_Y);
        SabotageCommand command = new SabotageCommand();
        tick(command, sim);
        assertTrue(nearest.assignedObjective != null,
                "ordinary security begins under commander control");

        EquipmentDrop drop = new EquipmentDrop(SITE_X, SITE_Y, site);
        sim.getEquipmentDrops().add(drop);
        sim.advance(BattleSimulation.TICK_DT);

        long nearestMarine = sim.resolveUnit(nearest.leaderId);
        long fartherMarine = sim.resolveUnit(farther.leaderId);
        assertEquals(UnitRole.KIT_RETRIEVER, sim.role().role(nearestMarine));
        assertSame(drop, sim.task().equipmentDropTarget(nearestMarine));
        assertEquals(UnitRole.COMBATANT, sim.role().role(fartherMarine));

        tick(command, sim);

        assertNull(nearest.assignedObjective,
                "commander must release its zone order while retrieval owns the squad");
        SabotageSiteSnapshot.SquadDirective directive =
                command.siteSnapshot().directiveFor(nearest.id);
        assertEquals(SabotageSiteSnapshot.GroupRole.KIT_RETRIEVER,
                directive.groupRole());
        assertEquals(SabotageSiteSnapshot.AssignmentReason.KIT_RECOVERY_PRESERVED,
                directive.reason());
        assertEquals(0, directive.siteIndex());
        assertSame(site, drop.objective);
    }

    @Test
    public void retrieverDeathLeavesSingleKitAndRecruitsReplacement() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = site();
        sim.addObjective(site);
        Squad firstSquad = addSquad(sim, 8, SITE_Y);
        Squad replacementSquad = addSquad(sim, 12, SITE_Y);
        EquipmentDrop drop = new EquipmentDrop(SITE_X, SITE_Y, site);
        sim.getEquipmentDrops().add(drop);
        long first = sim.resolveUnit(firstSquad.leaderId);
        sim.role().setRole(first, UnitRole.KIT_RETRIEVER);
        sim.task().setEquipmentDropTarget(first, drop);

        kill(sim, first);

        assertEquals(1, sim.getEquipmentDrops().size(),
                "a retriever dies before pickup, so the existing ground kit remains");
        assertSame(drop, sim.getEquipmentDrops().get(0),
                "retriever death must not duplicate or replace the kit object");
        assertSame(site, drop.objective);

        sim.advance(BattleSimulation.TICK_DT);

        long replacement = sim.resolveUnit(replacementSquad.leaderId);
        assertEquals(UnitRole.KIT_RETRIEVER, sim.role().role(replacement));
        assertSame(drop, sim.task().equipmentDropTarget(replacement));
        assertFalse(drop.consumed);
    }

    @Test
    public void successfulPickupRestoresPlanterForSameSiteWithoutCommanderContention() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = site();
        sim.addObjective(site);
        Squad squad = addSquad(sim, SITE_X, SITE_Y);
        long marine = sim.resolveUnit(squad.leaderId);
        EquipmentDrop drop = new EquipmentDrop(SITE_X, SITE_Y, site);
        sim.getEquipmentDrops().add(drop);
        sim.role().setRole(marine, UnitRole.KIT_RETRIEVER);
        sim.task().setEquipmentDropTarget(marine, drop);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id,
                sim.getZoneGraph().zoneIdAt(SITE_X, SITE_Y));
        sim.clearPath(marine);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(UnitRole.PLANTER, sim.role().role(marine));
        assertSame(site, sim.task().assignedObjective(marine),
                "pickup must restore the exact original site, not choose a new one");
        assertNull(sim.task().equipmentDropTarget(marine));
        assertTrue(drop.consumed);
        assertTrue(sim.getEquipmentDrops().isEmpty());

        SabotageCommand command = new SabotageCommand();
        float progressBeforeCommand = site.progress();
        tick(command, sim);

        assertNull(squad.assignedObjective,
                "the planter's unit-level objective exclusively owns its squad");
        assertEquals(UnitRole.PLANTER, sim.role().role(marine));
        assertSame(site, sim.task().assignedObjective(marine));
        site.tick(sim);
        assertTrue(site.progress() > progressBeforeCommand,
                "commander handoff must leave the planter free to continue channeling");
        SabotageSiteSnapshot.SquadDirective directive =
                command.siteSnapshot().directiveFor(squad.id);
        assertEquals(SabotageSiteSnapshot.GroupRole.PLANTER,
                directive.groupRole());
        assertEquals(SabotageSiteSnapshot.AssignmentReason.PLANTER_OBJECTIVE_PRESERVED,
                directive.reason());
        assertEquals(0, directive.siteIndex());
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT));
    }

    private static ChargeSiteObjective site() {
        return new ChargeSiteObjective(SITE_X, SITE_Y, 5f,
                "SAB-01", "generator controls");
    }

    private static Squad addSquad(BattleSimulation sim, int x, int y) {
        long marine = sim.spawn(new EntitySpec("marine-" + x, Faction.MARINE,
                UnitType.MARINE, x, y));
        int squadId = sim.mintSquad(Faction.MARINE, marine);
        sim.squad().assignSquad(marine, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }

    private static void kill(BattleSimulation sim, long unit) {
        sim.applyDamage(unit, 100_000f, 100f, 0f);
    }

    private static void tick(SabotageCommand command, BattleSimulation sim) {
        CommanderService.runSingle(command, SabotageCommandDisclosure.INSTANCE, sim);
    }
}
