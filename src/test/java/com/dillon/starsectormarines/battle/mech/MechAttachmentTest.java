package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The attachment rules: who may follow whom, what ends it, and where each doctrine stands. */
class MechAttachmentTest {

    // ---- the slot each doctrine takes ----

    @Test
    void inContactDoctrineDecidesWhereAFollowerStandsAlongTheSquadsAxis() {
        // Squad at (10,10) facing +X.
        float[] brawler = MechAttachmentSlot.point(MechRole.ASSAULT, 0, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        float[] tank = MechAttachmentSlot.point(MechRole.ARMORED_SUPPORT, 0, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        float[] balanced = MechAttachmentSlot.point(MechRole.BALANCED, 0, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        float[] longRange = MechAttachmentSlot.point(MechRole.LR_SUPPORT, 0, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);

        assertTrue(brawler[0] > tank[0] && tank[0] > 10f, "Brawler leads, Frontline Support screens just ahead");
        assertEquals(10f, brawler[1], 1e-4f);
        assertEquals(10f, balanced[0], 1e-4f, "Balanced is level with the squad");
        assertTrue(Math.abs(balanced[1] - 10f) >= 4f, "and beside it");
        assertTrue(longRange[0] < 10f - 5f, "Long-Range Support shadows from behind");
    }

    @Test
    void followersOfOneRoleSpreadAcrossTheAxis() {
        float[] first = MechAttachmentSlot.point(MechRole.ASSAULT, 0, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        float[] second = MechAttachmentSlot.point(MechRole.ASSAULT, 1, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        float[] third = MechAttachmentSlot.point(MechRole.ASSAULT, 2, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        assertEquals(first[0], second[0], 1e-4f, "same depth");
        assertTrue(second[1] != first[1] && third[1] != first[1] && third[1] != second[1]);
        assertTrue(Math.signum(second[1] - 10f) != Math.signum(third[1] - 10f), "files out to both sides");

        float[] left = MechAttachmentSlot.point(MechRole.BALANCED, 0, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        float[] right = MechAttachmentSlot.point(MechRole.BALANCED, 1, 0, true, 10f, 10f, 1f, 0f, 0f, 0f);
        assertTrue(Math.signum(left[1] - 10f) != Math.signum(right[1] - 10f), "Balanced pair flank both sides");
    }

    @Test
    void outOfContactEveryFollowerWalksWithTheSquadWhateverItsDoctrine() {
        float[] brawler = MechAttachmentSlot.point(MechRole.ASSAULT, 0, 0, false, 10f, 10f, 1f, 0f, 0f, 0f);
        float[] longRange = MechAttachmentSlot.point(MechRole.LR_SUPPORT, 0, 1, false, 10f, 10f, 1f, 0f, 0f, 0f);
        assertEquals(10f - MechAttachmentSlot.MARCH_DEPTH, brawler[0], 1e-4f, "a Brawler does not lead the march");
        assertEquals(brawler[0], longRange[0], 1e-4f, "every follower walks at the same depth");
        assertTrue(longRange[1] != brawler[1], "filed across the axis by its place among all followers");
    }

    @Test
    void withNoAxisAFollowerKeepsItsBearingAtItsRolesDistance() {
        float[] slot = MechAttachmentSlot.point(MechRole.ASSAULT, 0, 0, true, 10f, 10f, Float.NaN, Float.NaN, 10f, 30f);
        assertEquals(10f, slot[0], 1e-4f);
        assertEquals(10f + MechAttachmentSlot.LEAD_DEPTH, slot[1], 1e-4f);
    }

    // ---- the squad's axis ----

    @Test
    void theAxisIsTheThreatBeforeTheDestination() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 10);
        Squad squad = sim.getSquad(infantry);
        squad.assignedObjective = ObjectiveAssignment.attackMove(infantry, 30, 10);

        float[] toward = MechAttachmentSystem.axis(squad, sim);
        assertNotNull(toward);
        assertTrue(toward[0] > 0.99f, "toward the order's cell");

        squad.lastSeenEnemyX = 10;
        squad.lastSeenEnemyY = 30;
        squad.alertLevel = SquadAlertLevel.ENGAGED;
        float[] threat = MechAttachmentSystem.axis(squad, sim);
        assertTrue(threat[1] > 0.99f, "toward the believed threat once there is one");
    }

    // ---- who may attach, and what ends it ----

    @Test
    void aPlayerMechAttachesToAPlayerInfantrySquadOnly() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 10);
        int enemy = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        sim.spawn(new EntitySpec("e", Faction.DEFENDER, UnitType.MILITIA, 30, 30).squad(enemy));
        sim.getSquad(enemy).aliveMembers = 1;
        long mech = mechSquad(sim, 5, 5);
        int mechSquad = sim.squad().squadId(mech);
        long other = mechSquad(sim, 8, 5);
        int otherSquad = sim.squad().squadId(other);
        MechAttachmentService attachments = sim.getMechAttachmentService();

        attachments.requestAttach(mechSquad, enemy);
        attachments.requestAttach(mechSquad, otherSquad);
        sim.getMechAttachmentSystem().tick(sim);
        assertFalse(attachments.isAttached(mechSquad), "neither an enemy squad nor another mech is an anchor");

        attachments.requestAttach(mechSquad, infantry);
        attachments.requestAttach(otherSquad, infantry);
        sim.getMechAttachmentSystem().tick(sim);
        assertEquals(infantry, attachments.anchorOf(mechSquad));
        assertEquals(List.of(mechSquad, otherSquad), attachments.followersOf(infantry),
                "several mechs may follow one squad");
    }

    @Test
    void attachingReplacesTheMechsDirectOrder() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 10);
        long mech = mechSquad(sim, 5, 5);
        int mechSquad = sim.squad().squadId(mech);
        sim.getMechMoveOrderService().requestMove(mech, 30, 30);
        sim.getMechMoveOrderSystem().tick(sim);
        assertNotNull(sim.getMechMoveOrderService().activeOrder(mech));

        sim.getMechAttachmentService().requestAttach(mechSquad, infantry);
        sim.getMechAttachmentSystem().tick(sim);

        assertNull(sim.getMechMoveOrderService().activeOrder(mech));
        assertTrue(sim.getMechAttachmentService().isAttached(mechSquad));
        sim.getMechAttachmentSystem().tick(sim);
        assertTrue(sim.getMechAttachmentService().isAttached(mechSquad), "and stays attached");
    }

    @Test
    void aDirectOrderToTheMechDetachesIt() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 10);
        long mech = mechSquad(sim, 5, 5);
        int mechSquad = attached(sim, mech, infantry);

        sim.getMechMoveOrderService().requestMove(mech, 30, 30);
        sim.getMechMoveOrderSystem().tick(sim);
        sim.getMechAttachmentSystem().tick(sim);

        assertFalse(sim.getMechAttachmentService().isAttached(mechSquad));
        assertNotNull(sim.getMechMoveOrderService().activeOrder(mech), "the order itself stands");
    }

    @Test
    void aDefendAreaOnTheMechDetachesIt() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 10);
        long mech = mechSquad(sim, 5, 5);
        int mechSquad = attached(sim, mech, infantry);

        sim.getSquadMoveOrderService().requestDefendArea(mechSquad, 20, 20);
        sim.getSquadMoveOrderSystem().tick(sim);
        sim.getMechAttachmentSystem().tick(sim);

        assertFalse(sim.getMechAttachmentService().isAttached(mechSquad));
    }

    @Test
    void theAnchorsLossOrDirectControlEndsTheAttachment() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 10);
        long mech = mechSquad(sim, 5, 5);
        int mechSquad = attached(sim, mech, infantry);

        sim.getSquad(mechSquad).setControlledMember(mech, sim);
        sim.getMechAttachmentSystem().tick(sim);
        assertFalse(sim.getMechAttachmentService().isAttached(mechSquad), "driving the mech detaches it");
        sim.getSquad(mechSquad).setControlledMember(0L, sim);

        attached(sim, mech, infantry);
        sim.getSquad(infantry).aliveMembers = 0;
        sim.getMechAttachmentSystem().tick(sim);
        assertFalse(sim.getMechAttachmentService().isAttached(mechSquad), "a destroyed squad releases its mechs");
    }

    @Test
    void aWithdrawingAnchorReleasesItsMechs() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 10);
        long mech = mechSquad(sim, 5, 5);
        int mechSquad = attached(sim, mech, infantry);

        sim.getSquad(infantry).assignedObjective = ObjectiveAssignment.withdraw(infantry, 1, 1);
        sim.getMechAttachmentSystem().tick(sim);

        assertFalse(sim.getMechAttachmentService().isAttached(mechSquad));
    }

    @Test
    void inContactAnAttachedBrawlerWalksToItsSlotAheadOfTheSquad() {
        BattleSimulation sim = openSimulation(40, 40);
        int infantry = infantrySquad(sim, 10, 20);
        Squad squad = sim.getSquad(infantry);
        squad.assignedObjective = ObjectiveAssignment.attackMove(infantry, 35, 20);
        long mech = mechSquad(sim, 10, 10);
        sim.world().mechLoadout(mech).applyBattleOverride(MechRole.ASSAULT);
        int mechSquad = attached(sim, mech, infantry);
        squad._engagedThisTick = true;

        assertTrue(sim.getMechAttachmentSystem().executeIfAttached(mech, sim.getSquad(mechSquad), sim));
        int[] slot = sim.getMechAttachmentService().slotOf(mech);
        assertNotNull(slot);
        assertTrue(slot[0] >= 10 + 4, "a Brawler's slot is ahead of the squad, toward its order: " + slot[0]);
        assertTrue(Math.abs(slot[1] - 20) <= 1);

        sim.getMechAttachmentService().requestDetach(mechSquad);
        sim.getMechAttachmentSystem().tick(sim);
        assertFalse(sim.getMechAttachmentSystem().executeIfAttached(mech, sim.getSquad(mechSquad), sim),
                "a detached mech goes back to its plan step");
    }

    // ---- fixtures ----

    private static int attached(BattleSimulation sim, long mech, int infantry) {
        int mechSquad = sim.squad().squadId(mech);
        sim.getMechAttachmentService().requestAttach(mechSquad, infantry);
        sim.getMechAttachmentSystem().tick(sim);
        assertTrue(sim.getMechAttachmentService().isAttached(mechSquad));
        return mechSquad;
    }

    private static int infantrySquad(BattleSimulation sim, int x, int y) {
        int id = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long leader = 0L;
        for (int i = 0; i < 3; i++) {
            long m = sim.spawn(new EntitySpec("m" + id + "-" + i, Faction.MARINE, UnitType.MARINE, x + i, y).squad(id));
            if (leader == 0L) leader = m;
        }
        Squad squad = sim.getSquad(id);
        squad.leaderId = leader;
        squad.aliveMembers = 3;
        squad.centroidX = x + 1.5f;
        squad.centroidY = y + .5f;
        return id;
    }

    private static long mechSquad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        MechVariant variant = MechVariant.BULWARK;
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                "mech-" + sim.liveUnitCount(), Faction.MARINE, UnitType.HEAVY_MECH, x, y).squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(MechRole.BALANCED));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = sim.world().x(mech);
        squad.centroidY = sim.world().y(mech);
        return mech;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
