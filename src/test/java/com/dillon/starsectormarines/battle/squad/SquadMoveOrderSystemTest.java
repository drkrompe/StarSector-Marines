package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.AttackMoveGoal;
import com.dillon.starsectormarines.battle.command.DefendAssignedAreaGoal;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendArea;
import com.dillon.starsectormarines.battle.infantry.SecureCompoundGoal;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.mech.GoapMechBehavior;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveCaptureOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.highlight.SquadMoveOrderHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadMoveOrderSystemTest {

    @Test
    void uncapturedObjectiveRunsTheOrdinarySecureActionUntilCaptureCompletes() {
        BattleSimulation sim = partitionedSimulation();
        Squad squad = infantrySquad(sim, Faction.MARINE, 3, 5, 2);
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 7);
        squad.assignedObjective = mission;
        TacticalNode objective = compoundAt(13, 5);
        CompoundService.Record record = sim.getCompoundService().register(objective);

        sim.getSquadMoveOrderService().requestMove(squad.id, 13, 5);
        sim.getSquadMoveOrderSystem().tick(sim);

        ActiveOrder active = sim.getSquadMoveOrderService().activeOrder(squad.id);
        ActiveCaptureOrder capture = assertInstanceOf(ActiveCaptureOrder.class, active);
        int captureZone = sim.getCompoundService().captureZoneId(record, sim);
        assertEquals(objective, capture.targetNode());
        assertEquals(record.captureCellX, capture.destinationX());
        assertEquals(record.captureCellY, capture.destinationY());
        assertSame(mission, squad.assignedObjective);
        assertEquals(AssignmentKind.SECURE_COMPOUND,
                squad.assignmentForExecution().kind());
        assertEquals(captureZone, squad.assignmentForExecution().targetZoneId());
        assertSame(objective, squad.assignmentForExecution().targetNode());

        Selection selection = new Selection();
        selection.selectSquad(squad.id);
        HighlightOverlay overlay = new HighlightOverlay();
        SquadMoveOrderHighlightPublisher.publish(selection, sim, overlay);
        assertEquals(record.captureCellX, overlay.source(
                HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION).get(0).cellX);
        assertEquals(record.captureCellY, overlay.source(
                HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION).get(0).cellY);

        GoapInfantryBehavior.replanIfNeeded(squad, sim);
        assertSame(SecureCompoundGoal.INSTANCE, squad.currentGoal);
        assertInstanceOf(EnterZone.class, squad.currentPlan.currentStep().action,
                "the contextual order reuses the combat-aware compound approach");

        record.state = CompoundService.CompoundState.CONTESTED;
        sim.getSquadMoveOrderSystem().tick(sim);
        assertSame(active, sim.getSquadMoveOrderService().activeOrder(squad.id),
                "arrival and contested progress do not complete the action");

        record.state = CompoundService.CompoundState.MARINE_HELD;
        sim.getSquadMoveOrderSystem().tick(sim);
        assertNull(sim.getSquadMoveOrderService().activeOrder(squad.id));
        assertSame(mission, squad.assignmentForExecution());
        assertNull(squad.currentPlan,
                "completion hands back before the authoritative mission replans");
    }

    @Test
    void capturedObjectiveRemainsOrdinaryGroundForMoveOrders() {
        BattleSimulation sim = openSimulation(20, 12);
        Squad squad = infantrySquad(sim, Faction.MARINE, 3, 5, 1);
        TacticalNode objective = compoundAt(13, 5);
        CompoundService.Record record = sim.getCompoundService().register(objective);
        record.state = CompoundService.CompoundState.MARINE_HELD;

        sim.getSquadMoveOrderService().requestMove(squad.id, 13, 5);
        sim.getSquadMoveOrderSystem().tick(sim);

        assertInstanceOf(ActiveMoveOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squad.id));
        assertEquals(AssignmentKind.ATTACK_MOVE,
                squad.assignmentForExecution().kind());
    }

    @Test
    void unreachableObjectiveIsRejectedWithoutClearingTheCurrentOrder() {
        BattleSimulation sim = disconnectedSimulation();
        Squad squad = infantrySquad(sim, Faction.MARINE, 3, 5, 1);
        sim.getCompoundService().register(compoundAt(13, 5));

        sim.getSquadMoveOrderService().requestMove(squad.id, 6, 5);
        sim.getSquadMoveOrderSystem().tick(sim);
        ActiveOrder incumbent = sim.getSquadMoveOrderService().activeOrder(squad.id);
        assertInstanceOf(ActiveMoveOrder.class, incumbent);

        sim.getSquadMoveOrderService().requestMove(squad.id, 13, 5);
        sim.getSquadMoveOrderSystem().tick(sim);

        assertSame(incumbent,
                sim.getSquadMoveOrderService().activeOrder(squad.id));
        assertEquals(AssignmentKind.ATTACK_MOVE,
                squad.assignmentForExecution().kind());
    }

    @Test
    void commandSnapsToReachableGroundWithoutReplacingMissionAuthority() {
        BattleSimulation sim = openSimulation(16, 10);
        sim.getGrid().setWalkable(10, 5, false);
        Squad squad = infantrySquad(sim, Faction.MARINE, 3, 5, 2);
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 7);
        squad.assignedObjective = mission;
        sim.setPath(squad.leaderId, new int[]{3, 5, 8, 5});

        sim.getSquadMoveOrderService().requestMove(squad.id, 10, 5);
        sim.getSquadMoveOrderSystem().tick(sim);

        ActiveOrder order = sim.getSquadMoveOrderService().activeOrder(squad.id);
        assertNotNull(order);
        assertEquals(10, order.requestedX());
        assertEquals(5, order.requestedY());
        assertEquals(10, order.destinationX());
        assertEquals(4, order.destinationY());
        assertSame(mission, squad.assignedObjective);
        assertEquals(AssignmentKind.ATTACK_MOVE,
                squad.assignmentForExecution().kind());
        assertEquals(10, squad.assignmentForExecution().targetCellX());
        assertEquals(4, squad.assignmentForExecution().targetCellY());
        assertTrue(Paths.isEmpty(sim.world().path(squad.leaderId)));

        Selection selection = new Selection();
        selection.selectSquad(squad.id);
        HighlightOverlay overlay = new HighlightOverlay();
        SquadMoveOrderHighlightPublisher.publish(selection, sim, overlay);
        assertEquals(10, overlay.source(
                HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION).get(0).cellX);
        assertEquals(4, overlay.source(
                HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION).get(0).cellY);
    }

    @Test
    void tacticalAssignmentUsesTheOrdinaryMovingFireAttackMove() {
        BattleSimulation sim = openSimulation(20, 12);
        Squad squad = infantrySquad(sim, Faction.MARINE, 3, 5, 1);
        long enemy = sim.spawn(new EntitySpec(
                "enemy", Faction.DEFENDER, UnitType.MARINE, 1, 5));
        sim.world().setTargetId(squad.leaderId, enemy);

        sim.getSquadMoveOrderService().requestMove(squad.id, 14, 5);
        sim.getSquadMoveOrderSystem().tick(sim);
        GoapInfantryBehavior.replanIfNeeded(squad, sim);

        assertSame(AttackMoveGoal.INSTANCE, squad.currentGoal);
        assertInstanceOf(AttackMove.class, squad.currentPlan.currentStep().action);
        GoapInfantryBehavior.INSTANCE.update(squad.leaderId, sim);

        assertEquals(enemy, sim.combat().fireTargetId(squad.leaderId),
                "the attack-move action authors a moving shot without yielding its route");
        assertFalse(Paths.isEmpty(sim.world().path(squad.leaderId)));
        assertEquals(14, Paths.destX(sim.world().path(squad.leaderId)));
        assertEquals(5, Paths.destY(sim.world().path(squad.leaderId)));
    }

    @Test
    void defendAreaIsPersistentBoundedAndKeepsMissionAuthorityUnderneath() {
        BattleSimulation sim = openSimulation(64, 48);
        Squad squad = infantrySquad(sim, Faction.MARINE, 5, 24, 1);
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 7);
        squad.assignedObjective = mission;

        sim.getSquadMoveOrderService().requestDefendArea(squad.id, 32, 24);
        sim.getSquadMoveOrderSystem().tick(sim);

        ActiveDefendAreaOrder order = assertInstanceOf(ActiveDefendAreaOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squad.id));
        assertEquals(20, order.radiusCells());
        assertSame(mission, squad.assignedObjective);
        assertEquals(AssignmentKind.DEFEND_AREA,
                squad.assignmentForExecution().kind());
        assertEquals(20, squad.assignmentForExecution().targetRadiusCells());

        GoapInfantryBehavior.replanIfNeeded(squad, sim);
        assertSame(DefendAssignedAreaGoal.INSTANCE, squad.currentGoal);
        assertInstanceOf(DefendArea.class, squad.currentPlan.currentStep().action);

        squad.centroidX = order.destinationX() + 0.5f;
        squad.centroidY = order.destinationY() + 0.5f;
        sim.getSquadMoveOrderSystem().tick(sim);
        assertSame(order, sim.getSquadMoveOrderService().activeOrder(squad.id),
                "defense persists after arrival until superseded or withdrawn");

        Selection selection = new Selection();
        selection.selectSquad(squad.id);
        HighlightOverlay overlay = new HighlightOverlay();
        SquadMoveOrderHighlightPublisher.publish(selection, sim, overlay);
        assertTrue(overlay.source(HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION)
                .stream().anyMatch(cell -> cell.cellX == order.destinationX() + 20
                        && cell.cellY == order.destinationY()));
    }

    @Test
    void defendAreaAppliesToTheWholeMechLanceWhileRetainingCombatTargets() {
        BattleSimulation sim = openSimulation(80, 48);
        Squad lance = squad(sim, Faction.MARINE, UnitType.HEAVY_MECH,
                17, 20, 2);
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(lance.id, 9);
        lance.assignedObjective = mission;
        long enemy = sim.spawn(new EntitySpec("enemy", Faction.DEFENDER,
                UnitType.MARINE, 14, 20));
        for (int i = 0; i < sim.squadMemberCount(lance.id); i++) {
            sim.world().setTargetId(sim.squadMemberAt(lance.id, i), enemy);
        }

        sim.getSquadMoveOrderService().requestDefendArea(lance.id, 36, 20);
        sim.getSquadMoveOrderSystem().tick(sim);

        ActiveDefendAreaOrder order = assertInstanceOf(ActiveDefendAreaOrder.class,
                sim.getSquadMoveOrderService().activeOrder(lance.id));
        assertSame(mission, lance.assignedObjective);
        assertEquals(AssignmentKind.DEFEND_AREA,
                lance.assignmentForExecution().kind());
        assertEquals(20, order.radiusCells());

        GoapMechBehavior.replanIfNeeded(lance, sim);
        assertSame(DefendAssignedAreaGoal.INSTANCE, lance.currentGoal);
        assertInstanceOf(DefendArea.class, lance.currentPlan.currentStep().action);
        for (int i = 0; i < sim.squadMemberCount(lance.id); i++) {
            long mech = sim.squadMemberAt(lance.id, i);
            GoapMechBehavior.INSTANCE.update(mech, sim);
            assertEquals(enemy, sim.targetOf(mech),
                    "the area order must retain each chassis's combat target while positioning");
            if (!Paths.isEmpty(sim.world().path(mech))) {
                int dx = Paths.destX(sim.world().path(mech)) - order.destinationX();
                int dy = Paths.destY(sim.world().path(mech)) - order.destinationY();
                assertTrue(dx * dx + dy * dy
                                <= order.radiusCells() * order.radiusCells(),
                        "each chassis should take a position inside the lance's area");
            }
        }
    }

    @Test
    void arrivalHandsBackAndSurvivalSuspendsWhileWithdrawalCancels() {
        BattleSimulation sim = openSimulation(20, 12);
        Squad squad = infantrySquad(sim, Faction.MARINE, 3, 5, 2);
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 3);
        squad.assignedObjective = mission;
        sim.getSquadMoveOrderService().requestMove(squad.id, 14, 5);
        sim.getSquadMoveOrderSystem().tick(sim);

        squad.moraleBroken = true;
        sim.getSquadMoveOrderSystem().tick(sim);
        assertNotNull(sim.getSquadMoveOrderService().activeOrder(squad.id));
        assertEquals(AssignmentKind.ATTACK_MOVE,
                squad.assignmentForExecution().kind());

        squad.moraleBroken = false;
        squad.centroidX = 14.5f;
        squad.centroidY = 5.5f;
        sim.getSquadMoveOrderSystem().tick(sim);
        assertNull(sim.getSquadMoveOrderService().activeOrder(squad.id));
        assertSame(mission, squad.assignmentForExecution());
        assertNull(squad.currentPlan);

        sim.getSquadMoveOrderService().requestMove(squad.id, 12, 5);
        sim.getSquadMoveOrderSystem().tick(sim);
        squad.assignedObjective = ObjectiveAssignment.withdraw(squad.id, 0, 5);
        sim.getSquadMoveOrderSystem().tick(sim);
        assertNull(sim.getSquadMoveOrderService().activeOrder(squad.id));
        assertEquals(AssignmentKind.WITHDRAW,
                squad.assignmentForExecution().kind());
    }

    @Test
    void enemyMechNonSoldierRescueAndStaleSquadsAreRejected() {
        BattleSimulation sim = openSimulation(20, 12);
        Squad enemy = infantrySquad(sim, Faction.DEFENDER, 10, 5, 1);
        Squad mech = squad(sim, Faction.MARINE, UnitType.HEAVY_MECH, 4, 5, 1);
        Squad civilian = squad(sim, Faction.MARINE, UnitType.CIVILIAN, 5, 5, 1);
        Squad rescue = infantrySquad(sim, Faction.MARINE, 6, 5, 1);
        rescue.rescuePickupGuard = true;

        for (int id : new int[]{enemy.id, mech.id, civilian.id,
                rescue.id, Integer.MAX_VALUE}) {
            sim.getSquadMoveOrderService().requestMove(id, 12, 5);
        }
        sim.getSquadMoveOrderSystem().tick(sim);

        assertNull(sim.getSquadMoveOrderService().activeOrder(enemy.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(mech.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(civilian.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(rescue.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(Integer.MAX_VALUE));
    }

    @Test
    void defendAreaRejectsEnemyCivilianAndRescueFormations() {
        BattleSimulation sim = openSimulation(20, 12);
        Squad enemy = infantrySquad(sim, Faction.DEFENDER, 3, 5, 1);
        Squad civilian = squad(sim, Faction.MARINE, UnitType.CIVILIAN, 4, 5, 1);
        Squad rescueGuard = infantrySquad(sim, Faction.MARINE, 5, 5, 1);
        rescueGuard.rescuePickupGuard = true;
        Squad rescueMech = squad(sim, Faction.MARINE,
                UnitType.HEAVY_MECH, 6, 5, 1);
        rescueMech.rescuePickupMech = true;

        for (int id : new int[]{enemy.id, civilian.id, rescueGuard.id,
                rescueMech.id, Integer.MAX_VALUE}) {
            sim.getSquadMoveOrderService().requestDefendArea(id, 12, 5);
        }
        sim.getSquadMoveOrderSystem().tick(sim);

        assertNull(sim.getSquadMoveOrderService().activeOrder(enemy.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(civilian.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(rescueGuard.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(rescueMech.id));
        assertNull(sim.getSquadMoveOrderService().activeOrder(Integer.MAX_VALUE));
    }

    private static Squad infantrySquad(BattleSimulation sim, Faction faction,
                                        int x, int y, int count) {
        return squad(sim, faction, UnitType.MARINE, x, y, count);
    }

    private static Squad squad(BattleSimulation sim, Faction faction,
                               UnitType type, int x, int y, int count) {
        int squadId = sim.mintSquad(faction, type);
        Squad squad = sim.getSquad(squadId);
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < count; i++) {
            EntitySpec spec = new EntitySpec("member-" + squadId + "-" + i,
                    faction, type, x, y + i).squad(squadId);
            if (type.usesInfantryTraining()) {
                spec.primaryWeapon(WeaponRegistry.require(
                        WeaponRegistry.SQUAD_AUTOMATIC_ID));
            } else if (type.isMech()) {
                MechVariant.BULWARK.applyTo(spec);
            }
            long member = sim.spawn(spec);
            if (type.isMech()) {
                sim.world().attachMechLoadout(member,
                        MechVariant.BULWARK.createLoadout(null));
            }
            if (i == 0) squad.leaderId = member;
            sumX += sim.world().x(member);
            sumY += sim.world().y(member);
        }
        squad.aliveMembers = count;
        squad.originalSize = count;
        squad.centroidX = sumX / count;
        squad.centroidY = sumY / count;
        return squad;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static BattleSimulation partitionedSimulation() {
        int width = 20;
        int height = 12;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x != 8) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(8, 5);
        grid.setDoorway(8, 5, true);
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static BattleSimulation disconnectedSimulation() {
        int width = 20;
        int height = 12;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x != 8) grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static TacticalNode compoundAt(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.BARRACKS, x, y,
                x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 4);
    }
}
