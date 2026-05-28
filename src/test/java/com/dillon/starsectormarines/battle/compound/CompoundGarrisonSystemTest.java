package com.dillon.starsectormarines.battle.compound;

import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link CompoundGarrisonSystem} — the marine-side garrison
 * shuttle drop that activates the v2 tug-of-war. Same synthetic open-grid
 * pattern as {@link CompoundCaptureSystemTest}.
 */
public class CompoundGarrisonSystemTest {

    private static final int W = 10;
    private static final int H = 10;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode barracksAt(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.BARRACKS, x, y,
                x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 4);
    }

    private static void captureCompound(BattleSimulation sim, CompoundService service,
                                         CompoundCaptureSystem capture) {
        sim.addUnit(new Unit("cap-m", Faction.MARINE, UnitType.MARINE, 5, 5));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        for (int i = 0; i < ticks; i++) {
            capture.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
    }

    private static void tickGarrison(CompoundGarrisonSystem garrison,
                                      BattleSimulation sim, CompoundService service, int count) {
        for (int i = 0; i < count; i++) {
            garrison.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
    }

    @Test
    public void dispatchesShuttleOnCapture() {
        BattleSimulation sim = openSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem capture = new CompoundCaptureSystem();
        CompoundGarrisonSystem garrison = new CompoundGarrisonSystem(TraversalAxis.SOUTH_TO_NORTH);
        service.register(barracksAt(5, 5));

        captureCompound(sim, service, capture);

        int shuttlesBefore = sim.getShuttles().size();
        tickGarrison(garrison, sim, service, 1);
        int shuttlesAfter = sim.getShuttles().size();

        assertEquals(shuttlesBefore + 1, shuttlesAfter,
                "garrison system should spawn one shuttle on compound capture");
        Shuttle last = sim.getShuttles().get(sim.getShuttles().size() - 1);
        assertEquals(Faction.MARINE, last.faction,
                "garrison shuttle must be marine-faction");
    }

    @Test
    public void noShuttleWhileDefenderHeld() {
        BattleSimulation sim = openSim();
        CompoundService service = sim.getCompoundService();
        CompoundGarrisonSystem garrison = new CompoundGarrisonSystem(TraversalAxis.SOUTH_TO_NORTH);
        service.register(barracksAt(5, 5));

        int shuttlesBefore = sim.getShuttles().size();
        tickGarrison(garrison, sim, service, 3);
        assertEquals(shuttlesBefore, sim.getShuttles().size(),
                "no shuttle while compound is still DEFENDER_HELD");
    }

    @Test
    public void lzPrefersBboxExteriorOver3x3() {
        // 20x20 grid with a walled compound: interior is 3x3 (too tight
        // for a 3x3 LZ when the center is the only walkable cell), but
        // the parade ground outside the building bbox is wide open.
        int size = 20;
        NavigationGrid grid = new NavigationGrid(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) grid.setWalkableFloor(x, y);
        }
        // Wall off a 5x5 building shell at (8,8)-(12,12), leaving only
        // the anchor cell (10,10) and a 1-cell doorway at (8,10) walkable
        // inside. The exterior parade ground is fully open.
        for (int y = 8; y <= 12; y++) {
            for (int x = 8; x <= 12; x++) {
                if (x == 10 && y == 10) continue; // anchor stays walkable
                if (x == 8 && y == 10) continue;  // doorway
                if (x > 8 && x < 12 && y > 8 && y < 12) continue; // interior
                grid.setWalkable(x, y, false);
            }
        }

        TacticalNode node = new TacticalNode(TacticalNode.Kind.BARRACKS,
                10, 10, 8, 8, 12, 12, Faction.DEFENDER, 50, 4);

        int[] lz = CompoundGarrisonSystem.findCompoundLz(grid, node);
        assertNotNull(lz, "should find an LZ");
        // The LZ should be OUTSIDE the building bbox — on the parade ground,
        // not inside the tight interior.
        boolean outsideBbox = lz[0] < 8 || lz[0] > 12 || lz[1] < 8 || lz[1] > 12;
        assertTrue(outsideBbox,
                "LZ should be on the parade ground outside the building bbox, got ("
                        + lz[0] + "," + lz[1] + ")");
    }

    @Test
    public void lzFallsBackToSingleCellWhenNo3x3Available() {
        // Tiny 5x5 grid — no room for a 3x3 clear patch near the edges.
        // Fallback should still find a single walkable cell.
        NavigationGrid grid = new NavigationGrid(5, 5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) grid.setWalkableFloor(x, y);
        }
        // Wall off most of it, leaving only (2,2) and (2,3) walkable.
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                if ((x == 2 && y == 2) || (x == 2 && y == 3)) continue;
                grid.setWalkable(x, y, false);
            }
        }
        TacticalNode node = new TacticalNode(TacticalNode.Kind.BARRACKS,
                2, 2, 1, 1, 3, 3, Faction.DEFENDER, 50, 4);

        int[] lz = CompoundGarrisonSystem.findCompoundLz(grid, node);
        assertNotNull(lz, "fallback should find a single walkable cell even without 3x3");
        assertTrue(grid.isWalkable(lz[0], lz[1]));
    }

    @Test
    public void rearmsAfterDefenderRecapture() {
        BattleSimulation sim = openSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem capture = new CompoundCaptureSystem();
        CompoundGarrisonSystem garrison = new CompoundGarrisonSystem(TraversalAxis.SOUTH_TO_NORTH);
        TacticalNode node = barracksAt(5, 5);
        service.register(node);

        // First capture → garrison dispatched.
        captureCompound(sim, service, capture);
        tickGarrison(garrison, sim, service, 1);
        int afterFirstCapture = sim.getShuttles().size();
        assertTrue(afterFirstCapture > 0, "first garrison shuttle should have spawned");

        // Second tick while still MARINE_HELD → no duplicate.
        tickGarrison(garrison, sim, service, 1);
        assertEquals(afterFirstCapture, sim.getShuttles().size(),
                "no duplicate shuttle while compound stays MARINE_HELD");

        // Defender recaptures: kill the marine, add a defender, tick capture system.
        for (Unit u : sim.getUnits()) {
            if (u.faction == Faction.MARINE) TestUnits.kill(sim, u);
        }
        sim.addUnit(new Unit("def-1", Faction.DEFENDER, UnitType.MILITIA, 5, 5));
        // MARINE_HELD → CONTESTED → DEFENDER_HELD
        int recapTicks = 2 + (int) Math.ceil(
                CompoundService.DEFENDER_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        // First tick: MARINE_HELD → CONTESTED (defender enters)
        capture.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        // Remaining ticks: CONTESTED → DEFENDER_HELD
        for (int i = 0; i < recapTicks; i++) {
            capture.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
        assertEquals(CompoundService.CompoundState.DEFENDER_HELD,
                service.getRecord(node).state, "compound should be back to DEFENDER_HELD");

        // Garrison system sees DEFENDER_HELD → re-arms.
        tickGarrison(garrison, sim, service, 1);

        // Second marine capture → second garrison shuttle.
        for (Unit u : sim.getUnits()) {
            if (u.faction == Faction.DEFENDER) TestUnits.kill(sim, u);
        }
        sim.addUnit(new Unit("cap-m2", Faction.MARINE, UnitType.MARINE, 5, 5));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        for (int i = 0; i < ticks; i++) {
            capture.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(node).state, "compound should be MARINE_HELD again");

        tickGarrison(garrison, sim, service, 1);
        assertEquals(afterFirstCapture + 1, sim.getShuttles().size(),
                "re-armed garrison should dispatch a second shuttle on recapture");
    }
}
