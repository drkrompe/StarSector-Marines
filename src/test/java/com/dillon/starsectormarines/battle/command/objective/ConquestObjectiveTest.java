package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-4 coverage: {@link ConquestObjective} latches only when every
 * defender compound has flipped to MARINE_HELD and at least one marine
 * remains in play. Same synthetic open-grid pattern the slice-1 and
 * slice-3 tests use.
 */
public class ConquestObjectiveTest {

    private static final int W = 10;
    private static final int H = 10;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * 10x10 grid split by a wall at column 5 with a doorway at (5, 5). The
     * two halves are distinct zones, so a marine at (3,5) sits in the
     * left-half zone and the right-half zone reads as marines-absent. Used
     * by tests that want partial captures.
     */
    private static BattleSimulation splitSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == 5) continue; // wall column
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(5, 5);
        grid.setDoorway(5, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode compoundAt(TacticalNode.Kind kind, int x, int y) {
        return new TacticalNode(kind, x, y,
                x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 4);
    }

    /** Walks every compound's zone toward MARINE_HELD by parking a marine in each anchor cell and ticking the system enough times. */
    private static void captureAll(BattleSimulation sim, CompoundService service,
                                   CompoundCaptureSystem system, int... anchorCells) {
        for (int i = 0; i < anchorCells.length; i += 2) {
            sim.addUnit(new Unit("cap-m-" + i, Faction.MARINE, UnitType.MARINE,
                    anchorCells[i], anchorCells[i + 1]));
        }
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        for (int i = 0; i < ticks; i++) {
            system.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
    }

    @Test
    public void failsWhenNoCompoundsRegistered() {
        // Map-gen bug scenario: Conquest battle starts with no compounds.
        // Marines can never satisfy the win condition; the battle would
        // otherwise stall forever until every marine dies. Fail-closed:
        // the marine objective flips to isFailed, the defender wins via
        // the standard objective-failure path, the player sees the loss
        // screen + the warning lands in the log for diagnosis.
        BattleSimulation sim = openSim();
        ConquestObjective obj = new ConquestObjective(sim.getCompoundService());
        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, 5, 5));

        obj.tick(sim);
        assertFalse(obj.isComplete(),
                "empty compound layer must not satisfy the conquest objective");
        assertTrue(obj.isFailed(),
                "empty compound layer must fail the conquest objective so the battle terminates");
    }

    @Test
    public void incompleteWhilePartialCaptures() {
        // Split-zone sim so a marine in one half doesn't propagate
        // capture-presence to the other compound. Without the wall the
        // open-grid helper has one zone covering every cell, and a single
        // marine reads as "captured" everywhere.
        BattleSimulation sim = splitSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        ConquestObjective obj = new ConquestObjective(service);
        service.register(compoundAt(TacticalNode.Kind.BARRACKS, 3, 5));
        service.register(compoundAt(TacticalNode.Kind.ARMORY, 7, 5));

        // Capture only the BARRACKS (left half).
        captureAll(sim, service, system, 3, 5);

        obj.tick(sim);
        assertFalse(obj.isComplete(),
                "objective must not complete while any defender compound is still in play");
    }

    @Test
    public void completeWhenAllCompoundsCapturedWithLiveMarine() {
        BattleSimulation sim = openSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        ConquestObjective obj = new ConquestObjective(service);
        service.register(compoundAt(TacticalNode.Kind.BARRACKS, 3, 5));
        service.register(compoundAt(TacticalNode.Kind.ARMORY, 7, 5));

        captureAll(sim, service, system, 3, 5, 7, 5);

        obj.tick(sim);
        assertTrue(obj.isComplete(),
                "objective must complete when all compounds are MARINE_HELD and a marine is alive");
    }

    @Test
    public void incompleteWhenLastMarineJustDied() {
        // The "marines stormed the keep but everyone died" edge case.
        // Without the alive-marine precondition, ConquestObjective would
        // complete the same tick the defender's EliminateFactionObjective
        // completes → mutual win = draw. With it, the marine objective
        // doesn't complete → defender wins via elimination.
        BattleSimulation sim = openSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        ConquestObjective obj = new ConquestObjective(service);
        service.register(compoundAt(TacticalNode.Kind.BARRACKS, 5, 5));

        // Use a held marine reference so we can kill it after the
        // capture-loop adds its own (and that one too).
        captureAll(sim, service, system, 5, 5);
        for (Unit u : sim.getUnits()) {
            if (u.faction == Faction.MARINE) TestUnits.kill(sim, u);
        }

        obj.tick(sim);
        assertFalse(obj.isComplete(),
                "no live marine → objective must not complete; defender wins via elimination");
    }

    @Test
    public void winCheckResolvesToDefenderWhenLastMarineDiesOnCaptureTick() {
        // The central same-tick race the alive-marine precondition exists
        // to win. Conquest registers a marine-side ConquestObjective and a
        // defender-side EliminateFactionObjective(DEFENDER, MARINE). If
        // both completed the same tick, WinCheckSystem would return
        // winner=null (mutual-victory draw). Deferring marine completion
        // by one tick lets the defender's elimination latch first and
        // win. This pins that behaviour through the actual WinCheckSystem
        // — the hand-proof in slice-4's commit message is load-bearing,
        // worth an explicit assertion.
        BattleSimulation sim = openSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        ConquestObjective marineObj = new ConquestObjective(service);
        com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective defenderObj =
                new com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective(
                        Faction.DEFENDER, Faction.MARINE);
        service.register(compoundAt(TacticalNode.Kind.BARRACKS, 5, 5));

        // Drive to MARINE_HELD + everyone dies in the same finishing
        // assault. captureAll adds a marine and ticks the capture system;
        // we then kill every marine to simulate the "stormed the keep but
        // got wiped" tick.
        captureAll(sim, service, system, 5, 5);
        for (Unit u : sim.getUnits()) {
            if (u.faction == Faction.MARINE) TestUnits.kill(sim, u);
        }

        marineObj.tick(sim);
        defenderObj.tick(sim);

        java.util.List<com.dillon.starsectormarines.battle.command.objective.Objective> objectives =
                java.util.List.of(marineObj, defenderObj);
        com.dillon.starsectormarines.battle.command.objective.WinCheckSystem winCheck =
                new com.dillon.starsectormarines.battle.command.objective.WinCheckSystem();
        com.dillon.starsectormarines.battle.command.objective.WinCheckSystem.WinResult result =
                winCheck.tick(objectives);

        org.junit.jupiter.api.Assertions.assertTrue(result.complete(),
                "battle must terminate this tick — defender's elimination objective has completed");
        org.junit.jupiter.api.Assertions.assertEquals(Faction.DEFENDER, result.winner(),
                "marine cannot win without a live marine; defender wins via elimination, not a draw");
    }

    @Test
    public void displayNameTracksCaptureCount() {
        // Split-sim so capturing one compound doesn't bleed into the other.
        BattleSimulation sim = splitSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        ConquestObjective obj = new ConquestObjective(service);
        service.register(compoundAt(TacticalNode.Kind.BARRACKS, 3, 5));
        service.register(compoundAt(TacticalNode.Kind.ARMORY, 7, 5));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Capture supply hubs (0 / 2)", obj.displayName());

        captureAll(sim, service, system, 3, 5);
        org.junit.jupiter.api.Assertions.assertEquals(
                "Capture supply hubs (1 / 2)", obj.displayName());
    }
}
