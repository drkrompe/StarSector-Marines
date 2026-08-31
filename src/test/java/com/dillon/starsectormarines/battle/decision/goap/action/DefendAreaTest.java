package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefendAreaTest {

    @Test
    void believedThreatMovementRotatesThePreparedCoverLine() {
        BattleSimulation sim = openSimulation(64, 48);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 30, 20).squad(squadId).primaryWeapon(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID)));
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.assignedObjective = ObjectiveAssignment.defendArea(
                squadId, 30, 20, 20);
        DefendArea action = new DefendArea(30, 20, 20);

        sim.getGrid().setCoverAtFacing(27, 20,
                NavigationGrid.FACING_E, 3);
        squad.contactPicture = contactAt(50, 20);
        action.execute(member, squad, sim);
        assertEquals(27, Paths.destX(sim.world().path(member)));
        assertEquals(20, Paths.destY(sim.world().path(member)));

        sim.getGrid().setCoverAtFacing(33, 20,
                NavigationGrid.FACING_W, 3);
        squad.contactPicture = contactAt(10, 20);
        action.execute(member, squad, sim);
        assertEquals(33, Paths.destX(sim.world().path(member)));
        assertEquals(20, Paths.destY(sim.world().path(member)));
        assertTrue(distanceSquared(30, 20, 33, 20) <= 20 * 20);
    }

    @Test
    void squadFiresWhileMovingBackInsideItsDefenseCircle() {
        BattleSimulation sim = openSimulation(80, 48);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 5, 20).squad(squadId).primaryWeapon(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID)));
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = sim.world().x(member);
        squad.centroidY = sim.world().y(member);
        squad.assignedObjective = ObjectiveAssignment.defendArea(
                squadId, 30, 20, 20);
        long enemy = sim.spawn(new EntitySpec("enemy", Faction.DEFENDER,
                UnitType.MARINE, 14, 20));
        sim.world().setTargetId(member, enemy);

        GoapInfantryBehavior.replanIfNeeded(squad, sim);
        GoapInfantryBehavior.INSTANCE.update(member, sim);

        assertEquals(enemy, sim.combat().fireTargetId(member));
        int destinationX = Paths.destX(sim.world().path(member));
        int destinationY = Paths.destY(sim.world().path(member));
        assertTrue(distanceSquared(30, 20, destinationX, destinationY)
                <= 20 * 20);
    }

    private static SquadContactPicture contactAt(int x, int y) {
        return new SquadContactPicture(1,
                SquadContactPicture.Posture.DEFENDING, 1f, 0f,
                1, 0, 1f, 1,
                SquadContactPicture.ForceBalance.EVEN,
                SquadContactPicture.Sector.FRONT,
                SquadContactPicture.Motion.LATERAL,
                0L, x, y, 0.7f,
                SquadContactPicture.Doctrine.HOLD,
                0, 1, 0, 1,
                SquadContactPicture.ContactInitiative.RECEIVE);
    }

    private static int distanceSquared(int x0, int y0, int x1, int y1) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        return dx * dx + dy * dy;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
