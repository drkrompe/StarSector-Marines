package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.mech.MechAttachmentService;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay.LineMark;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders.Recipient;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders.RecipientKind;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Group orders over mixed selections, and the attach click. */
class GroupOrderTest {

    private static final int W = 80;
    private static final int H = 80;

    // ---- the spread of a group click ----

    @Test
    void aGroupClickSpreadsRoundTheClickKeepingTheGroupsShape() {
        float[][] positions = {{10f, 30f}, {30f, 30f}, {20f, 30f}};
        int[][] slots = SelectionOrders.spreadSlots(positions, 50, 50, null);

        Set<Long> distinct = new HashSet<>();
        for (int[] slot : slots) {
            distinct.add(((long) slot[0] << 32) | slot[1]);
            assertTrue(Math.hypot(slot[0] - 50, slot[1] - 50) <= SelectionOrders.GROUP_SPACING_CELLS + 1,
                    "a compact cluster round the click");
        }
        assertEquals(3, distinct.size(), "nobody stacks on one cell");
        assertTrue(slots[0][0] < slots[1][0], "the westmost stays west of the eastmost");
        assertEquals(1, SelectionOrders.spreadSlots(new float[][]{{1f, 1f}}, 7, 8, null).length);
        assertEquals(7, SelectionOrders.spreadSlots(new float[][]{{1f, 1f}}, 7, 8, null)[0][0],
                "a lone recipient goes to the clicked cell");
    }

    @Test
    void twoMechsClickedAwayDoNotShareADestination() {
        BattleSimulation sim = arena();
        long a = mech(sim, 20, 20);
        long b = mech(sim, 24, 20);
        Selection selection = new Selection();
        selection.selectSquads(List.of(squadOf(sim, a), squadOf(sim, b)), squadOf(sim, a), a, false);

        SelectionOrders.requestMove(sim, selection, 50, 50);
        sim.getMechMoveOrderSystem().tick(sim);

        MechMoveOrderService.ActiveOrder first = sim.getMechMoveOrderService().activeOrder(a);
        MechMoveOrderService.ActiveOrder second = sim.getMechMoveOrderService().activeOrder(b);
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.destinationX() == second.destinationX() && first.destinationY() == second.destinationY());
        assertTrue(first.destinationX() < second.destinationX(), "the western mech keeps the western slot");
    }

    // ---- recipients ----

    @Test
    void anAttachedMechFollowsItsSelectedSquadRatherThanTakingTheOrder() {
        BattleSimulation sim = arena();
        int infantry = infantry(sim, 30, 30);
        long mech = mech(sim, 25, 30);
        int mechSquad = squadOf(sim, mech);
        sim.getMechAttachmentService().requestAttach(mechSquad, infantry);
        sim.getMechAttachmentSystem().tick(sim);

        Selection selection = new Selection();
        selection.selectSquads(List.of(infantry, mechSquad), infantry, 0L, false);
        List<Recipient> recipients = SelectionOrders.recipients(sim, selection);
        assertEquals(1, recipients.size(), "the follower is carried by its squad");
        assertEquals(RecipientKind.INFANTRY, recipients.get(0).kind());

        selection.selectUnit(mechSquad, mech);
        assertEquals(RecipientKind.MECH, SelectionOrders.recipients(sim, selection).get(0).kind(),
                "selected without its squad, the mech takes orders itself");
        SelectionOrders.requestMove(sim, selection, 60, 60);
        sim.getMechMoveOrderSystem().tick(sim);
        sim.getMechAttachmentSystem().tick(sim);
        assertFalse(sim.getMechAttachmentService().isAttached(mechSquad), "and that order detaches it");
    }

    @Test
    void onlyALoneMechOrVehicleTakesAHeading() {
        BattleSimulation sim = arena();
        long a = mech(sim, 20, 20);
        long b = mech(sim, 24, 20);
        Selection selection = new Selection();
        selection.selectUnit(squadOf(sim, a), a);
        assertTrue(SelectionOrders.takesFacing(sim, selection));
        selection.selectSquads(List.of(squadOf(sim, a), squadOf(sim, b)), squadOf(sim, a), a, false);
        assertFalse(SelectionOrders.takesFacing(sim, selection), "two mechs share a line instead");
    }

    // ---- the attach click ----

    @Test
    void aMechsClickOnAFriendlySquadReadsAttachAndTheClickAttaches() {
        BattleSimulation sim = arena();
        int infantry = infantry(sim, 30, 30);
        long mech = mech(sim, 20, 20);
        int mechSquad = squadOf(sim, mech);
        Selection selection = new Selection();
        selection.selectUnit(mechSquad, mech);

        assertEquals(TacticalOrderIntent.ATTACH, TacticalOrderIntent.resolve(sim, selection, 30, 30));
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, selection, 50, 50));

        SelectionOrders.requestMove(sim, selection, 30, 30);
        sim.getMechAttachmentSystem().tick(sim);
        assertEquals(infantry, sim.getMechAttachmentService().anchorOf(mechSquad));

        selection.selectSquad(infantry);
        assertTrue(TacticalOrderIntent.resolve(sim, selection, 30, 30) != TacticalOrderIntent.ATTACH,
                "infantry do not attach");
    }

    // ---- the group line ----

    @Test
    void aMixedDragLaysEveryRecipientAlongTheLineInStretches() {
        BattleSimulation sim = arena();
        int infantry = infantry(sim, 20, 30);
        long mech = mech(sim, 20, 40);
        long apc = apc(sim, 20.5f, 50.5f);
        Selection selection = new Selection();
        selection.selectGroup(List.of(infantry, squadOf(sim, mech)), List.of(apc), infantry, 0L, 0L, false);
        assertEquals(3, SelectionOrders.recipients(sim, selection).size());

        List<LineMark> marks = SelectionOrders.previewMarks(sim, selection, 40, 25, 40, 55);
        assertEquals(3, marks.size(), "every recipient's stretch is previewed");
        assertEquals(1, marks.get(1).places().size(), "the mech's slot");
        assertEquals(1, marks.get(2).places().size(), "the vehicle's slot");
        FrontageLine mechLine = marks.get(1).line();
        assertTrue(mechLine.length() < marks.get(0).line().length(),
                "a mech is one unit: its stretch is shorter than a squad's");
        assertEquals(mechLine.length(), marks.get(2).line().length(), 1.01f, "and as long as a vehicle's");

        SelectionOrders.requestLine(sim, selection, 40, 25, 40, 55);
        sim.getSquadMoveOrderSystem().tick(sim);
        sim.getMechMoveOrderSystem().tick(sim);

        FrontageLine held = assertInstanceOf(ActiveDefendAreaOrder.class,
                sim.getSquadMoveOrderService().activeOrder(infantry)).line();
        assertEquals(25, held.y0(), "the infantry hold the first stretch");
        assertEquals(38, held.y1(), "three marines' worth of a line shared with two machines");
        MechMoveOrderService.ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
        assertNotNull(order);
        assertEquals(39, order.requestedX(), "the mech stands a marine's setback behind its stretch's middle");
        assertEquals(42, order.requestedY());
        assertEquals(SelectionOrders.facingOf(mechLine.frontX(), mechLine.frontY()), order.facingDegrees(), 1e-4f,
                "facing its stretch's front");
        assertTrue(mechLine.frontX() > 0.9f, "away from where it stood, as an infantry line faces");
        assertTrue(sim.getVehicleMoveOrderService().pending(apc), "the vehicle is sent to its stretch");
    }

    @Test
    void anAttachedMechTakesAPostOnItsSquadsStretchAndFollowsAgainOnTheNextOrder() {
        BattleSimulation sim = arena();
        int infantry = infantry(sim, 20, 30);
        long mech = mech(sim, 20, 40);
        int mechSquad = squadOf(sim, mech);
        MechAttachmentService attachments = sim.getMechAttachmentService();
        attachments.requestAttach(mechSquad, infantry);
        sim.getMechAttachmentSystem().tick(sim);
        Selection selection = new Selection();
        selection.selectSquad(infantry);
        assertEquals(1, SelectionOrders.recipients(sim, selection).size(), "the follower is no recipient of its own");

        List<LineMark> marks = SelectionOrders.previewMarks(sim, selection, 40, 25, 40, 55);
        assertEquals(1, marks.size());
        assertEquals(4, marks.get(0).places().size(), "three marines and the mech on the squad's stretch");
        assertTrue(marks.get(0).places().stream().filter(place -> place.member() != mech)
                .anyMatch(place -> place.y() < 40) && marks.get(0).places().stream()
                .filter(place -> place.member() != mech).anyMatch(place -> place.y() > 40),
                "marines either side of the mech's post");

        SelectionOrders.requestLine(sim, selection, 40, 25, 40, 55);
        sim.getSquadMoveOrderSystem().tick(sim);
        sim.getMechAttachmentSystem().tick(sim);

        FrontageLine held = assertInstanceOf(ActiveDefendAreaOrder.class,
                sim.getSquadMoveOrderService().activeOrder(infantry)).line();
        assertEquals(25, held.y0(), "the marines hold the whole stretch");
        assertEquals(55, held.y1());
        assertEquals(1, held.machines(), "with the mech standing within it");
        MechAttachmentService.Post post = attachments.postOf(mechSquad);
        assertNotNull(post, "the mech holds a post on its squad's line");
        assertEquals(39, post.x(), "a marine's setback behind the line");
        assertEquals(40, post.y(), "at its middle, the marines either side");
        assertTrue(attachments.isAttached(mechSquad), "and is still attached");

        SelectionOrders.requestMove(sim, selection, 60, 30);
        sim.getSquadMoveOrderSystem().tick(sim);
        sim.getMechAttachmentSystem().tick(sim);
        assertNull(attachments.postOf(mechSquad), "a new order to the squad sends the mech back to following");
        assertTrue(attachments.isAttached(mechSquad));
    }

    // ---- fixtures ----

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 5; y <= 75; y++) {
            for (int x = 5; x <= 75; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static int squadOf(BattleSimulation sim, long unit) {
        return sim.squad().squadId(unit);
    }

    private static int infantry(BattleSimulation sim, int x, int y) {
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

    private static long mech(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        MechVariant variant = MechVariant.BULWARK;
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                "mech-" + sim.liveUnitCount(), Faction.MARINE, UnitType.HEAVY_MECH, x, y).squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(MechRole.BALANCED));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.centroidX = sim.world().x(mech);
        squad.centroidY = sim.world().y(mech);
        return mech;
    }

    private static long apc(BattleSimulation sim, float x, float y) {
        long id = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE, VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, 0f);
        return id;
    }
}
