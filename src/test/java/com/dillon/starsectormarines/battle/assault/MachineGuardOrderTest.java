package com.dillon.starsectormarines.battle.assault;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefs;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.TacticalOrderIntent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The machine guard's click, cursor, threat axis and cell choice ({@link MachineGuards}), on a small arena with one held
 * building (walls round a 40..50 box, a door on the east) and one hand-laid trench. Beliefs are planted with
 * {@link SquadBeliefs#sighted}; the garrison is a marine standing inside.
 */
class MachineGuardOrderTest {

    private static final int W = 96;
    private static final int H = 96;
    private static final int TRENCH_Y = 80;
    private static final TacticalNode PLACE = new TacticalNode(TacticalNode.Kind.INNER_POSITION,
            45, 45, 40, 40, 50, 50, Faction.ALLY, HeldPlaces.PRIORITY, 0);

    private boolean previouslyEnabled;
    private BattleSimulation sim;
    private int lanceId;
    private long mech;
    private int infantryId;
    private int enemyId;

    @BeforeEach
    void setUp() {
        previouslyEnabled = DigInSystem.isEnabled();
        DigInSystem.setEnabledForEvidence(true);
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 2; y < H - 2; y++) {
            for (int x = 2; x < W - 2; x++) grid.setWalkableFloor(x, y);
        }
        for (int i = PLACE.left; i <= PLACE.right; i++) {
            wall(grid, i, PLACE.top);
            wall(grid, i, PLACE.bottom);
            wall(grid, PLACE.left, i);
            if (i != 45) wall(grid, PLACE.right, i);
        }
        sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setTacticalMap(new TacticalMap(List.of(PLACE)));
        DoodadDef trench = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = 30; x <= 41; x++) sim.addDoodad(new Doodad(x, TRENCH_Y, trench));

        lanceId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        MechVariant variant = MechVariant.BULWARK;
        mech = sim.spawn(variant.applyTo(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 10, 45).squad(lanceId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(MechRole.BALANCED));
        Squad lance = sim.getSquad(lanceId);
        lance.leaderId = mech;
        lance.aliveMembers = 1;
        lance.originalSize = 1;

        infantryId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        enemyId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
    }

    @AfterEach
    void tearDown() {
        DigInSystem.setEnabledForEvidence(previouslyEnabled);
    }

    private static void wall(NavigationGrid grid, int x, int y) {
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, 100);
    }

    private long garrisonAt(int x, int y) {
        return sim.spawn(new EntitySpec("g" + x + "," + y, Faction.MARINE, UnitType.MARINE, x, y).squad(infantryId));
    }

    private void reportedHostile(int x, int y) {
        long hostile = sim.spawn(new EntitySpec("h" + x + "," + y, Faction.DEFENDER, UnitType.MILITIA, x, y)
                .squad(enemyId));
        SquadBeliefs.sighted(sim.getSquad(infantryId), hostile, x, y, sim.getSimTickIndex());
    }

    private Selection mechSelected() {
        Selection selection = new Selection();
        selection.selectUnit(lanceId, mech);
        return selection;
    }

    @Test
    void aMachineOverAPlaceItsSideMansReadsGuardAndOtherwiseMove() {
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, mechSelected(), 45, 45),
                "nobody mans the building: an ordinary move");
        garrisonAt(45, 45);
        TacticalOrderIntent intent = TacticalOrderIntent.resolve(sim, mechSelected(), 45, 45);
        assertEquals(TacticalOrderIntent.GUARD, intent);
        assertTrue(intent.guard() && !intent.assault() && intent.actionable(), "announced in the friendly colour");

        Selection lance = new Selection();
        lance.selectSquad(lanceId);
        assertEquals(TacticalOrderIntent.GUARD, TacticalOrderIntent.resolve(sim, lance, 45, 45));

        reportedHostile(44, 44);
        assertEquals(TacticalOrderIntent.ASSAULT, TacticalOrderIntent.resolve(sim, mechSelected(), 45, 45),
                "an enemy believed inside makes it an assault, whoever else is there");
    }

    @Test
    void aHostileOnlyHeardInAMannedPlaceDoesNotBarItsGuard() {
        garrisonAt(45, 45);
        long hostile = sim.spawn(new EntitySpec("heard", Faction.DEFENDER, UnitType.MILITIA, 60, 45).squad(enemyId));
        SquadBeliefs.heard(sim.getSquad(infantryId), hostile, 44, 44, sim.getSimTickIndex(), 0.3f);
        assertNotNull(MachineGuards.targetUnderClick(sim, Faction.MARINE, 45, 45),
                "a report localised by sound onto the place is not the enemy holding it");

        SquadBeliefs.sighted(sim.getSquad(infantryId), hostile, 44, 44, sim.getSimTickIndex());
        assertNull(MachineGuards.targetUnderClick(sim, Faction.MARINE, 45, 45), "one seen inside is");
    }

    @Test
    void aSquadSentToATrenchMansItBeforeItArrives() {
        AssaultTarget run = AssaultTarget.underClick(sim.fightingPositions(), true, sim.getTacticalMap(), 35, TRENCH_Y);
        assertNotNull(run);
        assertFalse(MachineGuards.manned(sim, Faction.MARINE, run));
        garrisonAt(10, 10);
        Squad squad = sim.getSquad(infantryId);
        squad.aliveMembers = 1;
        squad.assignedObjective = ObjectiveAssignment.defendRun(infantryId, 35, TRENCH_Y, 6, run.runId());
        assertTrue(MachineGuards.sentTo(sim, Faction.MARINE, run), "an order to man the run counts as manning it");
        assertEquals(TacticalOrderIntent.GUARD, TacticalOrderIntent.resolve(sim, mechSelected(), 38, TRENCH_Y));
    }

    @Test
    void aCommandersOrderToManTheBuildingByACopyOfItsNodeMansIt() {
        AssaultTarget place = AssaultTarget.place(PLACE);
        garrisonAt(10, 10);
        Squad squad = sim.getSquad(infantryId);
        squad.aliveMembers = 1;
        TacticalNode copy = new TacticalNode(PLACE.kind, PLACE.anchorX, PLACE.anchorY, PLACE.left, PLACE.top,
                PLACE.right, PLACE.bottom, Faction.ALLY, HeldPlaces.PRIORITY, 0);
        squad.assignedObjective = ObjectiveAssignment.holdEnvelope(infantryId, copy, 45, 41, 6);
        assertTrue(MachineGuards.sentTo(sim, Faction.MARINE, place),
                "a commander's facts carry a copy of the node; the same footprint is the same building");
        assertEquals(TacticalOrderIntent.GUARD, TacticalOrderIntent.resolve(sim, mechSelected(), 45, 45));
    }

    @Test
    void anArmedVehicleGuardsAndAnUnarmedOneOnlyMoves() {
        garrisonAt(45, 45);
        long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE, VehicleMission.deployed(20.5f, 20.5f));
        long truck = sim.convoy().spawn(VehicleType.SUPPLY_TRUCK, Faction.MARINE, VehicleMission.deployed(20.5f, 70.5f));
        Selection selection = new Selection();
        selection.selectVehicle(tank);
        assertEquals(TacticalOrderIntent.GUARD, TacticalOrderIntent.resolve(sim, selection, 45, 45));
        assertNotNull(MachineGuards.planVehicle(sim, tank, 45, 45));
        selection.selectVehicle(truck);
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, selection, 45, 45));
        assertNull(MachineGuards.planVehicle(sim, truck, 45, 45));
    }

    @Test
    void theThreatAxisPointsAtBelievedContactsAndElseAwayFromTheMachine() {
        AssaultTarget place = AssaultTarget.place(PLACE);
        float[] fallback = MachineGuards.threatAxis(sim, Faction.MARINE, place, 10.5f, 45.5f);
        assertTrue(fallback[0] > 0.99f, "with nothing believed, the threat is beyond the place from the machine");
        assertNull(MachineGuards.believedAxis(sim, Faction.MARINE, place));

        reportedHostile(46, 80);
        float[] axis = MachineGuards.threatAxis(sim, Faction.MARINE, place, 10.5f, 45.5f);
        assertTrue(axis[1] > 0.95f, "a hostile believed south sets a southward axis: " + axis[0] + "," + axis[1]);
    }

    @Test
    void theGuardCellIsOutsideThePlaceAndOffTheGarrisonsLanes() {
        AssaultTarget place = AssaultTarget.place(PLACE);
        // Threat due east: the lanes are the band east of the building, rows 40..50 plus the margin.
        int[] cell = MachineGuards.chooseGuardCell(sim, place, 10, 45, 30f, 1f, 0f, List.of());
        assertNotNull(cell);
        int gap = place.gap(cell[0], cell[1]);
        assertTrue(gap >= MachineGuards.GUARD_NEAR_CELLS && gap <= MachineGuards.GUARD_FAR_CELLS,
                "beside the place, not in it and not off on its own: gap " + gap);
        float rx = cell[0] + .5f - MachineGuards.centreX(place), ry = cell[1] + .5f - MachineGuards.centreY(place);
        assertFalse(MachineGuards.inLane(rx, Math.abs(ry), 5.5f), "not in front of the garrison: " + cell[0] + "," + cell[1]);
        assertTrue(cell[1] < PLACE.top - MachineGuards.LANE_MARGIN_CELLS || cell[1] > PLACE.bottom + MachineGuards.LANE_MARGIN_CELLS,
                "on a flank: " + cell[0] + "," + cell[1]);
        assertTrue(cell[0] >= PLACE.right - 2, "abreast of the front edge, to see past the place: " + cell[0]);
        assertTrue(sim.getGrid().hasLineOfFire(cell[0] + .5f, cell[1] + .5f, 70f, 45.5f), "it watches down the axis");
    }

    @Test
    void theLanesAreTheBandInFrontOfThePlace() {
        assertTrue(MachineGuards.inLane(8f, 0f, 5.5f));
        assertTrue(MachineGuards.inLane(8f, 7f, 5.5f), "within the margin of the frontage");
        assertFalse(MachineGuards.inLane(8f, 8f, 5.5f), "past the margin");
        assertFalse(MachineGuards.inLane(-8f, 0f, 5.5f), "behind the place fires over nobody");
    }

    @Test
    void lanceMatesStandApart() {
        AssaultTarget place = AssaultTarget.place(PLACE);
        int[] first = MachineGuards.chooseGuardCell(sim, place, 10, 45, 30f, 1f, 0f, List.of());
        int[] second = MachineGuards.chooseGuardCell(sim, place, 10, 45, 30f, 1f, 0f, List.of(first));
        assertNotNull(second);
        assertTrue(Math.max(Math.abs(first[0] - second[0]), Math.abs(first[1] - second[1]))
                >= MachineGuards.GUARD_SPACING_CELLS);
    }

    @Test
    void theClickBecomesAGuardBesideTheBuilding() {
        garrisonAt(45, 45);
        reportedHostile(80, 45);
        sim.getMechMoveOrderService().requestMove(mech, 45, 45);
        sim.getMechMoveOrderSystem().tick(sim);
        ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
        assertNotNull(order);
        assertNull(order.assault());
        assertNotNull(order.guard(), "the click is a guard, not a move");
        assertFalse(order.commanded());
        assertTrue(order.guard().target().gap(order.destinationX(), order.destinationY()) >= MachineGuards.GUARD_NEAR_CELLS);
        assertTrue(order.guard().axisX() > 0.99f, "faces the believed threat in the east");
    }

    @Test
    void aCommandersGuardNeverDisplacesAPlayersOrderAndStandsDownAlone() {
        garrisonAt(45, 45);
        sim.getMechMoveOrderService().requestGuard(mech, 45, 45);
        sim.getMechMoveOrderSystem().tick(sim);
        ActiveOrder commanded = sim.getMechMoveOrderService().activeOrder(mech);
        assertNotNull(commanded);
        assertTrue(commanded.commanded() && commanded.guard() != null);

        sim.getMechMoveOrderService().requestMove(mech, 20, 20);
        sim.getMechMoveOrderSystem().tick(sim);
        ActiveOrder player = sim.getMechMoveOrderService().activeOrder(mech);
        assertFalse(player.commanded(), "the player's move replaces the commander's guard");

        sim.getMechMoveOrderService().requestGuard(mech, 45, 45);
        sim.getMechMoveOrderSystem().tick(sim);
        assertFalse(sim.getMechMoveOrderService().activeOrder(mech).commanded(), "and a commander's guard waits on it");
        sim.getMechMoveOrderService().standDownGuard(mech);
        assertNotNull(sim.getMechMoveOrderService().activeOrder(mech), "standing down leaves a player's order alone");
    }

    @Test
    void theGuardStandsWhileMannedAndEndsAfterTheGarrisonLeaves() {
        AssaultTarget target = AssaultTarget.place(PLACE);
        int tick = sim.getSimTickIndex();
        MachineGuard left = new MachineGuard(target, 52, 36, 1f, 0f, tick - MachineGuards.RELEASE_TICKS);
        assertFalse(MachineGuards.stillStanding(left, sim, Faction.MARINE), "nobody in it for five seconds");
        MachineGuard recent = new MachineGuard(target, 52, 36, 1f, 0f, tick - MachineGuards.RELEASE_TICKS + 1);
        assertTrue(MachineGuards.stillStanding(recent, sim, Faction.MARINE), "inside the window it stands");
        garrisonAt(45, 45);
        assertTrue(MachineGuards.stillStanding(left, sim, Faction.MARINE));
        assertEquals(tick, left.lastMannedTick(), "a garrison refreshes the clock");
    }

    @Test
    void anAxisThatSwingsRoundMovesTheGuard() {
        MachineGuard guard = new MachineGuard(AssaultTarget.place(PLACE), 52, 36, 1f, 0f, 0);
        assertFalse(MachineGuards.axisTurned(guard, 0.9f, 0.4359f), "a small swing keeps the cell");
        assertTrue(MachineGuards.axisTurned(guard, 0f, 1f), "a quarter turn chooses again");
    }
}
