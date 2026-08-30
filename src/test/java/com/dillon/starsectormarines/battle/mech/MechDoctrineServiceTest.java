package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechDoctrineServiceTest {

    @Test
    void loadoutKeepsDeploymentBaselineAndNormalizesReset() {
        MechLoadoutComponent loadout =
                MechVariant.HOUND.createLoadout(MechRole.ASSAULT);

        assertEquals(MechRole.ASSAULT, loadout.deployedRole());
        assertNull(loadout.battleOverride());
        assertEquals(MechRole.ASSAULT, loadout.effectiveRole());

        assertTrue(loadout.applyBattleOverride(MechRole.LR_SUPPORT));
        assertEquals(MechRole.LR_SUPPORT, loadout.battleOverride());
        assertEquals(MechRole.LR_SUPPORT, loadout.effectiveRole());

        assertTrue(loadout.applyBattleOverride(MechRole.ASSAULT));
        assertNull(loadout.battleOverride(),
                "selecting the deployed doctrine is the canonical reset state");
        assertEquals(MechRole.ASSAULT, loadout.effectiveRole());
        assertFalse(loadout.applyBattleOverride(null));
    }

    @Test
    void lanceOrderDefaultsToFormationAndAppliesAtomically() {
        BattleSimulation sim = openSimulation(12, 8);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);

        assertEquals(MechLanceOrder.FORM_ON_LEAD, squad.lanceOrder());
        assertTrue(squad.applyLanceOrder(MechLanceOrder.FREE_REIGN));
        assertEquals(MechLanceOrder.FREE_REIGN, squad.lanceOrder());
        assertFalse(squad.applyLanceOrder(MechLanceOrder.FREE_REIGN));
    }

    @Test
    void commandChangesOnlySelectedFriendlyMechAndInvalidatesSharedPlan() {
        BattleSimulation sim = openSimulation(24, 12);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long selected = spawnMech(sim, squadId, Faction.MARINE,
                MechVariant.HOUND, MechRole.ASSAULT, 3, 4);
        long sibling = spawnMech(sim, squadId, Faction.MARINE,
                MechVariant.BULWARK, MechRole.ARMORED_SUPPORT, 4, 4);
        long enemy = sim.spawn(new EntitySpec(
                "enemy", Faction.DEFENDER, UnitType.MARINE, 18, 4));
        Squad squad = finishSquad(sim, squadId, selected, 2, 3, 4);
        ObjectiveAssignment assignment = ObjectiveAssignment.attackMove(squadId, 20, 4);
        squad.assignedObjective = assignment;
        squad.morale = 0.73f;
        squad.currentPlan = new SquadPlan(List.of(
                new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
        squad.currentGoal = MechEliminateEnemiesGoal.INSTANCE;

        MechLoadoutComponent selectedLoadout = sim.world().mechLoadout(selected);
        MechLoadoutComponent siblingLoadout = sim.world().mechLoadout(sibling);
        selectedLoadout.overwatchCellX = 16;
        selectedLoadout.overwatchCellY = 4;
        selectedLoadout.assignedSquadId = 42;
        selectedLoadout.assignmentBoundaryKind = AssignmentKind.ATTACK_MOVE;
        selectedLoadout.assignmentBoundaryCellX = 20;
        selectedLoadout.assignmentBoundaryCellY = 4;
        selectedLoadout.assignmentBoundaryReached = true;
        selectedLoadout.assignmentBoundaryBestDistance = 3f;
        siblingLoadout.overwatchCellX = 9;
        siblingLoadout.assignedSquadId = 7;
        sim.setPath(selected, new int[]{3, 4, 8, 4});
        sim.setPath(sibling, new int[]{4, 4, 9, 4});
        sim.world().setTargetId(selected, enemy);
        sim.world().setTargetId(sibling, enemy);
        MechWeaponMount selectedSrm = selectedLoadout.mount(MechMountSlot.LEFT_SHOULDER);
        selectedSrm.ammo = 2;
        selectedSrm.cooldown = 1.25f;

        sim.getMechDoctrineService().requestOverride(selected, MechRole.BALANCED);
        new MechDoctrineSystem(sim.getMechDoctrineService()).tick(sim);

        assertEquals(MechRole.ASSAULT, selectedLoadout.deployedRole());
        assertEquals(MechRole.BALANCED, selectedLoadout.battleOverride());
        assertEquals(MechRole.BALANCED, selectedLoadout.effectiveRole());
        assertEquals(-1, selectedLoadout.overwatchCellX);
        assertEquals(-1, selectedLoadout.assignedSquadId);
        assertEquals(AssignmentKind.ATTACK_MOVE,
                selectedLoadout.assignmentBoundaryKind,
                "a doctrine-only interrupt must not create a new mission generation");
        assertTrue(selectedLoadout.assignmentBoundaryReached);
        assertEquals(3f, selectedLoadout.assignmentBoundaryBestDistance);
        assertTrue(Paths.isEmpty(sim.world().path(selected)));
        assertEquals(enemy, sim.world().targetId(selected),
                "a legal combat target survives the posture change and is revalidated by the new action");
        assertNull(squad.currentPlan);
        assertNull(squad.currentGoal);
        assertSame(assignment, squad.assignedObjective);
        assertEquals(0.73f, squad.morale);
        assertEquals(2, selectedSrm.ammo);
        assertEquals(1.25f, selectedSrm.cooldown);

        assertEquals(MechRole.ARMORED_SUPPORT, siblingLoadout.effectiveRole());
        assertEquals(9, siblingLoadout.overwatchCellX);
        assertEquals(7, siblingLoadout.assignedSquadId);
        assertFalse(Paths.isEmpty(sim.world().path(sibling)));
        assertEquals(enemy, sim.world().targetId(sibling));
    }

    @Test
    void invalidSelectionsAreIgnoredSafely() {
        BattleSimulation sim = openSimulation(20, 12);
        int enemySquadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long enemyMech = spawnMech(sim, enemySquadId, Faction.DEFENDER,
                MechVariant.HOUND, MechRole.ASSAULT, 12, 5);
        finishSquad(sim, enemySquadId, enemyMech, 1, 12, 5);

        int rescueSquadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long rescueMech = spawnMech(sim, rescueSquadId, Faction.MARINE,
                MechVariant.BULWARK, MechRole.ARMORED_SUPPORT, 4, 5);
        Squad rescue = finishSquad(sim, rescueSquadId, rescueMech, 1, 4, 5);
        rescue.rescuePickupMech = true;
        long infantry = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, 3, 3));

        MechDoctrineService service = sim.getMechDoctrineService();
        service.requestOverride(enemyMech, MechRole.BALANCED);
        service.requestOverride(rescueMech, MechRole.LR_SUPPORT);
        service.requestOverride(infantry, MechRole.ASSAULT);
        service.requestOverride(Long.MAX_VALUE, null);
        service.requestLanceOrder(enemyMech, MechLanceOrder.FREE_REIGN);
        service.requestLanceOrder(rescueMech, MechLanceOrder.FREE_REIGN);
        service.requestLanceOrder(infantry, MechLanceOrder.FREE_REIGN);
        service.requestLanceOrder(Long.MAX_VALUE, MechLanceOrder.FREE_REIGN);
        new MechDoctrineSystem(service).tick(sim);

        assertEquals(MechRole.ASSAULT,
                sim.world().mechLoadout(enemyMech).effectiveRole());
        assertEquals(MechRole.ARMORED_SUPPORT,
                sim.world().mechLoadout(rescueMech).effectiveRole());
        assertEquals(MechLanceOrder.FORM_ON_LEAD,
                sim.getSquad(enemySquadId).lanceOrder());
        assertEquals(MechLanceOrder.FORM_ON_LEAD, rescue.lanceOrder());
    }

    @Test
    void exactMechRequestChangesEntireLanceAndOnlyOnceInvalidatesMovement() {
        BattleSimulation sim = openSimulation(24, 12);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long selected = spawnMech(sim, squadId, Faction.MARINE,
                MechVariant.HOUND, MechRole.ASSAULT, 3, 4);
        long sibling = spawnMech(sim, squadId, Faction.MARINE,
                MechVariant.BULWARK, MechRole.ARMORED_SUPPORT, 4, 4);
        long infantry = sim.spawn(new EntitySpec(
                "attached-infantry", Faction.MARINE, UnitType.MARINE, 5, 4)
                .squad(squadId));
        Squad squad = finishSquad(sim, squadId, selected, 3, 4, 4);
        ObjectiveAssignment assignment = ObjectiveAssignment.attackMove(
                squadId, 20, 4);
        squad.assignedObjective = assignment;
        squad.morale = 0.61f;
        squad.currentPlan = new SquadPlan(List.of(
                new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
        squad.currentGoal = MechEliminateEnemiesGoal.INSTANCE;
        long enemy = sim.spawn(new EntitySpec(
                "order-contact", Faction.DEFENDER, UnitType.MARINE, 18, 4));
        sim.world().setTargetId(selected, enemy);
        sim.world().setTargetId(sibling, enemy);
        MechWeaponMount selectedSrm = sim.world().mechLoadout(selected)
                .mount(MechMountSlot.LEFT_SHOULDER);
        selectedSrm.ammo = 3;
        selectedSrm.cooldown = 0.75f;
        sim.setPath(selected, new int[]{3, 4, 12, 4});
        sim.setPath(sibling, new int[]{4, 4, 12, 4});
        sim.setPath(infantry, new int[]{5, 4, 12, 4});

        sim.getMechDoctrineService().requestLanceOrder(
                selected, MechLanceOrder.FREE_REIGN);
        new MechDoctrineSystem(sim.getMechDoctrineService()).tick(sim);

        assertEquals(MechLanceOrder.FREE_REIGN, squad.lanceOrder());
        assertTrue(Paths.isEmpty(sim.world().path(selected)));
        assertTrue(Paths.isEmpty(sim.world().path(sibling)));
        assertFalse(Paths.isEmpty(sim.world().path(infantry)),
                "the lance command clears only live Mech movement");
        assertNull(squad.currentPlan);
        assertNull(squad.currentGoal);
        assertSame(assignment, squad.assignedObjective);
        assertEquals(0.61f, squad.morale);
        assertEquals(enemy, sim.world().targetId(selected));
        assertEquals(enemy, sim.world().targetId(sibling));
        assertEquals(3, selectedSrm.ammo);
        assertEquals(0.75f, selectedSrm.cooldown);
        assertEquals(MechRole.ASSAULT,
                sim.world().mechLoadout(selected).effectiveRole());
        assertEquals(MechRole.ARMORED_SUPPORT,
                sim.world().mechLoadout(sibling).effectiveRole());

        sim.setPath(selected, new int[]{3, 4, 14, 4});
        sim.setPath(sibling, new int[]{4, 4, 14, 4});
        SquadPlan replacement = new SquadPlan(List.of(
                new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
        squad.currentPlan = replacement;
        squad.currentGoal = MechEliminateEnemiesGoal.INSTANCE;
        sim.getMechDoctrineService().requestLanceOrder(
                sibling, MechLanceOrder.FREE_REIGN);
        new MechDoctrineSystem(sim.getMechDoctrineService()).tick(sim);

        assertFalse(Paths.isEmpty(sim.world().path(selected)),
                "reissuing the effective order must not restart the lance");
        assertFalse(Paths.isEmpty(sim.world().path(sibling)));
        assertSame(replacement, squad.currentPlan);
        assertSame(MechEliminateEnemiesGoal.INSTANCE, squad.currentGoal);
    }

    @Test
    void productionCommandPhaseAppliesOrdersBeforeSameTickReplan() {
        BattleSimulation sim = openSimulation(20, 12);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = spawnMech(sim, squadId, Faction.MARINE,
                MechVariant.HOUND, MechRole.ASSAULT, 3, 4);
        Squad squad = finishSquad(sim, squadId, mech, 1, 3, 4);
        squad.currentPlan = new SquadPlan(List.of(
                new SquadPlan.Step(BreachAndAssault.INSTANCE)));

        sim.getMechDoctrineService().requestOverride(mech, MechRole.BALANCED);
        sim.getMechDoctrineService().requestLanceOrder(
                mech, MechLanceOrder.FREE_REIGN);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(MechRole.BALANCED,
                sim.world().mechLoadout(mech).effectiveRole());
        assertEquals(MechLanceOrder.FREE_REIGN, squad.lanceOrder());
        assertSame(MechEliminateEnemiesGoal.INSTANCE, squad.currentGoal);
        assertSame(ExecuteMechDoctrine.INSTANCE,
                squad.currentPlan.currentStep().action);
    }

    private static long spawnMech(BattleSimulation sim, int squadId,
                                  Faction faction, MechVariant variant,
                                  MechRole role, int x, int y) {
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                variant.id + "-" + sim.liveUnitCount(), faction,
                UnitType.HEAVY_MECH, x, y).squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(role));
        return mech;
    }

    private static Squad finishSquad(BattleSimulation sim, int squadId,
                                     long leader, int alive, int x, int y) {
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = leader;
        squad.aliveMembers = alive;
        squad.originalSize = alive;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
