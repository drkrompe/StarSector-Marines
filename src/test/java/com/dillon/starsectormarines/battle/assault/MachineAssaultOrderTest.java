package com.dillon.starsectormarines.battle.assault;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The machine assault's click, cursor and fire choices, on a small arena with one held building
 * (walls round a 40..50 box, open inside) and one hand-laid trench. Every belief is planted with
 * {@link SquadBeliefs#sighted}, so nothing here depends on who could have seen whom.
 */
class MachineAssaultOrderTest {

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
        // The building's shell: a ring of wall round the place's footprint, with a door on the east.
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
        sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10).squad(infantryId));
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

    /** A defender at the cell, reported by the infantry squad: the side's belief, not the lance's own. */
    private long reportedOccupant(int x, int y) {
        long occupant = sim.spawn(new EntitySpec("d" + x + "," + y, Faction.DEFENDER, UnitType.MILITIA, x, y)
                .squad(enemyId));
        SquadBeliefs.sighted(sim.getSquad(infantryId), occupant, x, y, sim.getSimTickIndex());
        return occupant;
    }

    private Selection mechSelected() {
        Selection selection = new Selection();
        selection.selectUnit(lanceId, mech);
        return selection;
    }

    @Test
    void aMechOverABelievedHeldBuildingReadsAssaultAndOtherwiseMove() {
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, mechSelected(), 45, 45),
                "nobody reported inside: an ordinary move");
        reportedOccupant(44, 44);

        TacticalOrderIntent intent = TacticalOrderIntent.resolve(sim, mechSelected(), 45, 45);
        assertEquals(TacticalOrderIntent.ASSAULT, intent);
        assertTrue(intent.assault() && intent.actionable(), "announced in the hostile colour");
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, mechSelected(), 30, 45),
                "plain ground beside it is still a move");

        Selection lance = new Selection();
        lance.selectSquad(lanceId);
        assertEquals(TacticalOrderIntent.ASSAULT, TacticalOrderIntent.resolve(sim, lance, 45, 45),
                "the lance reads the same as its member");
    }

    @Test
    void aBelievedHeldTrenchIsAssaultedToo() {
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, mechSelected(), 35, TRENCH_Y));
        reportedOccupant(33, TRENCH_Y);
        assertEquals(TacticalOrderIntent.ASSAULT, TacticalOrderIntent.resolve(sim, mechSelected(), 38, TRENCH_Y));
        AssaultTarget run = MachineAssaults.targetUnderClick(sim, Faction.MARINE, 38, TRENCH_Y);
        assertNotNull(run);
        assertEquals(AssaultTarget.Kind.FIGHTING_POSITION, run.kind());
        assertFalse(run.ownsWall(35, TRENCH_Y - 1), "a trench has no walls to bring down");
    }

    @Test
    void anArmedVehicleAssaultsAndAnUnarmedOneOnlyMoves() {
        reportedOccupant(44, 44);
        long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE, VehicleMission.deployed(20.5f, 20.5f));
        long truck = sim.convoy().spawn(VehicleType.SUPPLY_TRUCK, Faction.MARINE, VehicleMission.deployed(20.5f, 70.5f));
        Selection selection = new Selection();
        selection.selectVehicle(tank);
        assertEquals(TacticalOrderIntent.ASSAULT, TacticalOrderIntent.resolve(sim, selection, 45, 45));
        selection.selectVehicle(truck);
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, selection, 45, 45));
    }

    @Test
    void theClickBecomesAnAssaultFromAStandOffOutsideTheBuilding() {
        reportedOccupant(44, 44);
        sim.getMechMoveOrderService().requestMove(mech, 45, 45);
        sim.getMechMoveOrderSystem().tick(sim);

        ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
        assertNotNull(order);
        assertNotNull(order.assault(), "the click is an assault, not a move");
        AssaultTarget target = order.assault().target();
        assertTrue(target.gap(order.destinationX(), order.destinationY()) >= MachineAssaults.STANDOFF_MARGIN_CELLS,
                "the stand-off is outside the footprint: " + order.destinationX() + "," + order.destinationY());
        assertTrue(sim.getGrid().isWalkable(order.destinationX(), order.destinationY()));
    }

    @Test
    void theStandOffPrefersALineOfFireToTheOccupant() {
        // Occupant in the open doorway on the east; only cells east of the building see him.
        reportedOccupant(50, 45);
        AssaultTarget target = AssaultTarget.place(PLACE);
        int[] cell = MachineAssaults.chooseStandoff(sim, target, Faction.MARINE, 10, 45, 30f, List.of());
        assertNotNull(cell);
        assertTrue(sim.getGrid().hasLineOfFire(cell[0] + .5f, cell[1] + .5f, 50.5f, 45.5f),
                "the chosen cell has a line to the occupant: " + cell[0] + "," + cell[1]);
        assertTrue(target.gap(cell[0], cell[1]) >= MachineAssaults.STANDOFF_MARGIN_CELLS);
    }

    @Test
    void aLanceMateKeepsItsDistance() {
        reportedOccupant(50, 45);
        AssaultTarget target = AssaultTarget.place(PLACE);
        int[] first = MachineAssaults.chooseStandoff(sim, target, Faction.MARINE, 10, 45, 30f, List.of());
        int[] second = MachineAssaults.chooseStandoff(sim, target, Faction.MARINE, 10, 45, 30f, List.of(first));
        assertNotNull(second);
        assertTrue(Math.max(Math.abs(first[0] - second[0]), Math.abs(first[1] - second[1]))
                >= MachineAssaults.STANDOFF_SPACING_CELLS);
    }

    @Test
    void theShelteringWallIsTheTargetsOwnFirstWall() {
        AssaultTarget target = AssaultTarget.place(PLACE);
        assertArrayEquals(new int[] {40, 45},
                MachineAssaults.shelteringWall(sim.getGrid(), target, 20.5f, 45.5f, 44, 45));
        // Another wall in the way is not this building's to bring down.
        wall(sim.getGrid(), 30, 45);
        assertNull(MachineAssaults.shelteringWall(sim.getGrid(), target, 20.5f, 45.5f, 44, 45));
        assertNull(MachineAssaults.shelteringWall(sim.getGrid(), target, 60.5f, 45.5f, 49, 45),
                "through the open door there is no wall at all");
    }

    @Test
    void fireGoesToAVisibleOccupantThenToHisWallAndNeverDangerClose() {
        long inside = reportedOccupant(44, 45);
        AssaultTarget target = AssaultTarget.place(PLACE);
        MachineAssault assault = new MachineAssault(target, 25, 45, sim.getSimTickIndex());

        MachineAssaults.aim(assault, sim, mech, Faction.MARINE, 25.5f, 45.5f, 30f, 0f, true);
        assertEquals(0L, assault.occupantId(), "the wall hides him");
        assertTrue(assault.hasWall());
        assertEquals(40, assault.wallX());
        assertEquals(45, assault.wallY());

        MachineAssaults.aim(assault, sim, mech, Faction.MARINE, 25.5f, 45.5f, 30f, 0f, false);
        assertFalse(assault.hasWall(), "a machine without wall damage leaves the wall alone");

        sim.spawn(new EntitySpec("friend", Faction.MARINE, UnitType.MARINE, 38, 45).squad(infantryId));
        MachineAssaults.aim(assault, sim, mech, Faction.MARINE, 25.5f, 45.5f, 30f, 0f, true);
        assertFalse(assault.hasWall(), "a friendly at the wall holds the wall fire");

        // From the doorway side he is in plain view.
        MachineAssaults.aim(assault, sim, mech, Faction.MARINE, 62.5f, 45.5f, 30f, 0f, true);
        assertEquals(inside, assault.occupantId());
        assertFalse(assault.hasWall());
    }

    @Test
    void theOrderStandsWhileBelievedAndEndsAfterTheQuiet() {
        AssaultTarget target = AssaultTarget.place(PLACE);
        int tick = sim.getSimTickIndex();
        MachineAssault quiet = new MachineAssault(target, 25, 45, tick - MachineAssaults.RELEASE_TICKS);
        assertFalse(MachineAssaults.stillStanding(quiet, sim, Faction.MARINE), "nobody believed inside for five seconds");

        MachineAssault recent = new MachineAssault(target, 25, 45, tick - MachineAssaults.RELEASE_TICKS + 1);
        assertTrue(MachineAssaults.stillStanding(recent, sim, Faction.MARINE), "inside the quiet window it stands");

        reportedOccupant(44, 44);
        assertTrue(MachineAssaults.stillStanding(quiet, sim, Faction.MARINE));
        assertEquals(tick, quiet.lastBelievedTick(), "a belief refreshes the clock");
    }
}
