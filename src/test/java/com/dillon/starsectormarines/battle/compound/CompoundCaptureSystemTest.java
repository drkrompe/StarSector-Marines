package com.dillon.starsectormarines.battle.compound;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Synthetic-grid coverage for {@link CompoundCaptureSystem}. Each test
 * builds a small open grid, registers a single compound on its only zone,
 * and walks the system through ticks to assert the state machine. The
 * system is stateless w.r.t. game state, so per-test {@code new}
 * construction is safe — the only shared structure is the
 * {@link CompoundService} record list, which is per-test as well.
 */
public class CompoundCaptureSystemTest {

    private static final int W = 10;
    private static final int H = 10;

    /** 10x10 single open zone — every cell walkable, no walls. Compound anchor at (5,5) sits inside this zone. */
    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** A BARRACKS compound anchored at {@code (x, y)} with a 3x3 bbox. Suits the open-sim layout where any anchor inside the bounds resolves to the single zone. */
    private static TacticalNode barracksAt(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.BARRACKS, x, y,
                x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 4);
    }

    /** Drives the system for exactly {@code count} cadence ticks. */
    private static void tickN(CompoundCaptureSystem system, BattleSimulation sim,
                              CompoundService service, int count) {
        for (int i = 0; i < count; i++) {
            system.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
    }

    @Test
    public void capturesAfterMarineHoldTime() {
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode node = barracksAt(5, 5);
        service.register(node);

        // No marines yet: DEFENDER_HELD persists.
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.DEFENDER_HELD,
                service.getRecord(node).state);

        // Marine walks in. One tick later → CONTESTED.
        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(node).state);

        // Marines alone hold for MARINE_HOLD_TIME → MARINE_HELD.
        int holdTicks = (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        tickN(system, sim, service, holdTicks);
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(node).state);
        // Terminal state — capture-progress represents in-flight transition,
        // not "captured." 0 here so the renderer doesn't paint the arc
        // forever over the captured ring.
        assertEquals(0f, service.getRecord(node).captureProgress, 0.001f);
    }

    @Test
    public void contestedFreezesWhenBothPresent() {
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode node = barracksAt(5, 5);
        service.register(node);

        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
        sim.addUnit(new Unit("d1", Faction.DEFENDER, UnitType.MILITIA, 5, 5));

        // First tick: DEFENDER_HELD → CONTESTED (marine present trips the flip).
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(node).state);

        // Subsequent ticks: both present → progress frozen, no decay.
        float progressBefore = service.getRecord(node).captureProgress;
        tickN(system, sim, service, 5);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(node).state);
        assertEquals(progressBefore, service.getRecord(node).captureProgress, 0.001f);
    }

    @Test
    public void contestedRecoversWhenDefendersAlone() {
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode node = barracksAt(5, 5);
        service.register(node);

        Unit marine = new Unit("m1", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);

        // Push to CONTESTED.
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(node).state);

        // Marine dies, defender moves in.
        TestUnits.kill(sim, marine);
        sim.addUnit(new Unit("d1", Faction.DEFENDER, UnitType.MILITIA, 5, 5));

        int holdTicks = (int) Math.ceil(
                CompoundService.DEFENDER_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        tickN(system, sim, service, holdTicks);
        assertEquals(CompoundService.CompoundState.DEFENDER_HELD,
                service.getRecord(node).state);
        assertEquals(0f, service.getRecord(node).captureProgress, 0.001f);
    }

    @Test
    public void marineHeldFlipsToContestedOnDefenderEntry() {
        // V2 reverse path is wired but dormant in V1 — no production trigger
        // drops defenders into a marine-held zone. This test simulates a
        // future AutoGarrisonTrigger directly (synthetic defender unit) and
        // pins that the state machine flips correctly. V1 production code
        // shouldn't ever drive this branch.
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode node = barracksAt(5, 5);
        service.register(node);

        // Walk to MARINE_HELD.
        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        tickN(system, sim, service, ticks);
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(node).state);

        // Synthetic v2-style defender ingress.
        sim.addUnit(new Unit("d1", Faction.DEFENDER, UnitType.MILITIA, 5, 5));
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(node).state);
    }

    @Test
    public void hasAliveCompoundReadsDefenderSupplyState() {
        // Slice 3 trigger/means gates read this. Defender-side read is true
        // while at least one compound of the kind is still defender-held or
        // contested; marine-side read is true once a compound of the kind
        // has flipped to marine-held.
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode barracks = barracksAt(5, 5);
        service.register(barracks);

        // Start state: defender side has supply, marine side doesn't.
        org.junit.jupiter.api.Assertions.assertTrue(
                service.hasAliveCompound(TacticalNode.Kind.BARRACKS, Faction.DEFENDER));
        org.junit.jupiter.api.Assertions.assertFalse(
                service.hasAliveCompound(TacticalNode.Kind.BARRACKS, Faction.MARINE));

        // Walk to MARINE_HELD.
        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        tickN(system, sim, service, ticks);

        org.junit.jupiter.api.Assertions.assertFalse(
                service.hasAliveCompound(TacticalNode.Kind.BARRACKS, Faction.DEFENDER),
                "defender side reads no-supply once the only BARRACKS flips to marine-held");
        org.junit.jupiter.api.Assertions.assertTrue(
                service.hasAliveCompound(TacticalNode.Kind.BARRACKS, Faction.MARINE),
                "marine side reads supply once the BARRACKS flips to marine-held");
    }
}
