package com.dillon.starsectormarines.battle.command.compound;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
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
        sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
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

        sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MILITIA, 5, 5));

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

        long marine = sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 5, 5));

        // Push to CONTESTED.
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(node).state);

        // Marine dies, defender moves in.
        TestUnits.kill(sim, marine);
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MILITIA, 5, 5));

        int holdTicks = (int) Math.ceil(
                CompoundService.DEFENDER_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        tickN(system, sim, service, holdTicks);
        assertEquals(CompoundService.CompoundState.DEFENDER_HELD,
                service.getRecord(node).state);
        assertEquals(0f, service.getRecord(node).captureProgress, 0.001f);
    }

    @Test
    public void marineHeldFlipsToContestedOnDefenderEntry() {
        // Synthetic defender ingress covers the recapture path after a
        // marine garrison or defender reinforcement reaches the compound.
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode node = barracksAt(5, 5);
        service.register(node);

        // Walk to MARINE_HELD.
        sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        tickN(system, sim, service, ticks);
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(node).state);

        // Synthetic defender ingress.
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MILITIA, 5, 5));
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
        sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 5, 5));
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

    @Test
    public void capturesThroughAnAnchorCellThatIsNotWalkable() {
        // A node anchor is the compound's stable identity, not a promise of a
        // standable cell: map generation is free to leave it on a wall, and a
        // furnishing pass can drop a crate on an anchor that was clear when it
        // was chosen. Such a cell belongs to no zone, so reading the capture
        // zone straight off the anchor stalls the compound at DEFENDER_HELD
        // forever — and on Conquest, where every compound must flip, that makes
        // the mission unwinnable.
        BattleSimulation sim = openSim();
        sim.getGrid().setWalkable(5, 5, false);
        sim.getZoneGraph().rebuild();

        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode node = barracksAt(5, 5);
        service.register(node);

        // Marine stands in the room, one cell over from the blocked anchor.
        sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 4, 5));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        tickN(system, sim, service, ticks);

        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(node).state,
                "a compound anchored on a blocked cell still captures in its own room");
    }

    /**
     * An open compound is taken by standing on it, not by sharing the outdoors
     * with it.
     *
     * <p>An airfield has no walls, so the zone its apron belongs to is the
     * whole outdoors — on a generated ward, a 216-cell apron resolving to a
     * 1341-cell zone. Reading presence from the zone alone made the field
     * contested by any marine anywhere outside a building, and then froze it
     * there, because every defender outdoors was equally "present" and a
     * two-sided zone pauses the timer. The field showed permanently
     * mid-capture and could neither fall nor be held.
     *
     * <p>This is the whole grid as one zone with a 3x3 compound in the middle
     * of it, which is that map in miniature.
     */
    @Test
    public void openCompoundIgnoresTheRestOfItsZone() {
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode field = new TacticalNode(TacticalNode.Kind.AIRBASE, 5, 5,
                4, 4, 6, 6, Faction.DEFENDER, 65, 3);
        service.register(field);

        // Marines across the same open ground, well clear of the compound.
        sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 0, 0));
        sim.spawn(new EntitySpec("m2", Faction.MARINE, UnitType.MARINE, 9, 9));
        tickN(system, sim, service, 3);
        assertEquals(CompoundService.CompoundState.DEFENDER_HELD,
                service.getRecord(field).state,
                "sharing the outdoors with a field is not being on it");

        // One of them walks onto the apron itself, and it is contested.
        sim.spawn(new EntitySpec("m3", Faction.MARINE, UnitType.MARINE, 5, 5));
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(field).state);

        // And with nobody defending it, holding it takes it.
        tickN(system, sim, service,
                (int) Math.ceil(CompoundService.MARINE_HOLD_TIME
                        / CompoundCaptureSystem.CAPTURE_TICK_PERIOD));
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(field).state);
    }

    /**
     * The beachhead starts taken, and can be lost.
     *
     * <p>A compound's opening state is its node's own default guard, which is
     * what makes the ground the marines came ashore on theirs from tick zero
     * without a special case anywhere in the state machine. The rest of the
     * machine then runs unchanged in the direction it was already able to run:
     * a defender standing on the beachhead alone contests it and, given the
     * defender hold time, takes it.
     */
    @Test
    public void theBeachheadStartsHeldByTheMarinesAndCanBeTaken() {
        BattleSimulation sim = openSim();
        CompoundService service = new CompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode beachhead = new TacticalNode(TacticalNode.Kind.BEACHHEAD, 5, 5,
                4, 4, 6, 6, Faction.MARINE, 75, 0);
        service.register(beachhead);
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(beachhead).state,
                "the marines do not start holding the ground they landed on");

        // Nobody on it leaves it held: an empty beachhead is still the
        // attacker's, the way an empty barracks is still the defender's.
        sim.spawn(new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, 0, 0));
        tickN(system, sim, service, 3);
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(beachhead).state);

        // A defender walks onto it, and the same recapture path runs.
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5));
        tickN(system, sim, service, 1);
        assertEquals(CompoundService.CompoundState.CONTESTED,
                service.getRecord(beachhead).state);
        tickN(system, sim, service,
                (int) Math.ceil(CompoundService.DEFENDER_HOLD_TIME
                        / CompoundCaptureSystem.CAPTURE_TICK_PERIOD));
        assertEquals(CompoundService.CompoundState.DEFENDER_HELD,
                service.getRecord(beachhead).state,
                "a defender standing alone on the beachhead does not take it");
    }
}
