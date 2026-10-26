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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefendAreaTest {

    @Test
    void aDefenderShootsWhatItCanHitFromItsGroundBeforeWhatItWouldMoveFor() {
        assertEquals(7L, DefendArea.fromItsGround(3L, 7L), "a runner in reach beats a flier beyond it");
        assertEquals(3L, DefendArea.fromItsGround(3L, 0L), "with nothing in reach it keeps the far target");
    }

    @Test
    void quietSquadFansOutInsteadOfPackingABlock() {
        int count = 12;
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float[] a = DefendArea.quietOffset(i, count, 20);
            assertTrue(Math.hypot(a[0], a[1]) <= 20 * 0.7f + 0.001f, "stays inside the circle");
            for (int j = i + 1; j < count; j++) {
                float[] b = DefendArea.quietOffset(j, count, 20);
                nearest = Math.min(nearest, (float) Math.hypot(a[0] - b[0], a[1] - b[1]));
            }
        }
        assertTrue(nearest >= 2.5f, "neighbours stand apart, was " + nearest);
        float[] alone = DefendArea.quietOffset(0, 1, 20);
        assertEquals(0f, alone[0]);
        assertEquals(0f, alone[1]);
    }

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

    /**
     * A target with no firing position inside the circle sends members to the
     * prepared line. That fallback used to pass no squad, so every member was
     * ordinal 0 of 1 and the whole squad walked to one cell.
     */
    @Test
    void squadWithATargetButNoFiringPositionStaysSpreadAlongThePreparedLine() {
        BattleSimulation sim = openSimulation(120, 48);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        int count = 6;
        long[] members = new long[count];
        for (int i = 0; i < count; i++) {
            members[i] = sim.spawn(new EntitySpec("marine" + i, Faction.MARINE,
                    UnitType.MARINE, 28 + i, 20).squad(squadId).primaryWeapon(
                    WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID)));
        }
        squad.leaderId = members[0];
        squad.aliveMembers = count;
        squad.originalSize = count;
        squad.assignedObjective = ObjectiveAssignment.defendArea(squadId, 30, 20, 10);
        long enemy = sim.spawn(new EntitySpec("enemy", Faction.DEFENDER,
                UnitType.MARINE, 100, 20));
        DefendArea action = new DefendArea(30, 20, 10);

        for (long member : members) {
            sim.world().setTargetId(member, enemy);
            action.execute(member, squad, sim);
        }

        for (long member : members) {
            assertEquals(enemy, sim.targetOf(member), "premise: the target is kept");
        }
        Set<Long> destinations = new HashSet<>();
        for (long member : members) {
            int[] path = sim.world().path(member);
            assertTrue(!Paths.isEmpty(path), "every member is walking somewhere");
            destinations.add(((long) Paths.destX(path) << 32) | Paths.destY(path));
        }
        assertTrue(destinations.size() >= count - 1,
                "members take separate places on the line, got " + destinations.size()
                        + " distinct destinations for " + count);
    }

    /**
     * One strong cover cell inside reach of several members' line slots used to be every one of
     * their stands: they all walked to its centre, pushed each other off it, and none of them
     * ever counted as arrived. The squad's stands are now settled together, one cell each.
     */
    @Test
    void oneCoverCellNearSeveralSlotsIsNotEveryonesStand() {
        BattleSimulation sim = openSimulation(120, 48);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        int count = 6;
        long[] members = new long[count];
        for (int i = 0; i < count; i++) {
            members[i] = sim.spawn(new EntitySpec("marine" + i, Faction.MARINE,
                    UnitType.MARINE, 28 + i, 20).squad(squadId).primaryWeapon(
                    WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID)));
        }
        squad.leaderId = members[0];
        squad.aliveMembers = count;
        squad.originalSize = count;
        squad.assignedObjective = ObjectiveAssignment.defendArea(squadId, 30, 20, 10);
        sim.getGrid().setCoverAtFacing(27, 20, NavigationGrid.FACING_E, 3);
        squad.contactPicture = contactAt(100, 20);
        DefendArea action = new DefendArea(30, 20, 10);

        for (long member : members) action.execute(member, squad, sim);

        Set<Long> destinations = new HashSet<>();
        boolean coverTaken = false;
        for (long member : members) {
            int[] path = sim.world().path(member);
            assertTrue(!Paths.isEmpty(path), "every member is walking somewhere");
            destinations.add(((long) Paths.destX(path) << 32) | Paths.destY(path));
            coverTaken |= Paths.destX(path) == 27 && Paths.destY(path) == 20;
        }
        assertTrue(coverTaken, "premise: someone takes the cover");
        assertEquals(count, destinations.size(), "one stand per member");
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
