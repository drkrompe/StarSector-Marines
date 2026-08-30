package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a field decides to fly.
 *
 * <p>Everything under this — the lot, the strip, the berths, the ground
 * procedure — worked without anything ever asking for a sortie, so a station's
 * fighters sat in their hangars for whole battles. These pin the asking.
 */
class AirStrikeSystemTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);

    private static BattleSimulation openSim(boolean withStrip) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        if (withStrip) sim.getAirfieldService().installRunway(STRIP);
        return sim;
    }

    private static AirfieldService.Berth shed(BattleSimulation sim, int x, int y) {
        return sim.getAirfieldService().addShelterBerth(
                new Gantry(x, y, 2, 2, Gantry.Facing.SOUTH), FighterProfile.BROADSWORD);
    }

    /** A concentration worth attacking: a squad's worth of marines together. */
    private static void massMarines(BattleSimulation sim, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    x + (i % 3), y + (i / 3)));
        }
    }

    private static ShuttleMission strikeOut(BattleSimulation sim) {
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = sim.world().mission(id);
            if (m != null && m.strikeSortie) return m;
        }
        return null;
    }

    private static void advance(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    /** A massed enemy on a field's doorstep gets an aircraft sent at it. */
    @Test
    void aFieldFliesAStrikeAtAMassedEnemy() {
        BattleSimulation sim = openSim(true);
        AirfieldService.Berth shed = shed(sim, 25, 20);
        massMarines(sim, 45, 30, 6);

        // Past the opening interval.
        advance(sim, 60 * 30);

        ShuttleMission strike = strikeOut(sim);
        assertNotNull(strike, "the field never flew anything");
        assertTrue(strike.usesRunway, "a strike that did not use the strip");
        assertEquals(shed, strike.homeBerth, "the sortie does not know its own shed");
        assertEquals(AirfieldService.BerthState.AWAY, shed.state,
                "flew and the shed still says it has the aircraft");
        assertTrue(strike.lzX > 40f, "sent somewhere other than the concentration");
    }

    /**
     * One at a time. A garrison that launched its whole air arm at first
     * contact would have nothing left for the assault it exists to answer.
     */
    @Test
    void aFieldFliesOneStrikeAtATime() {
        BattleSimulation sim = openSim(true);
        shed(sim, 25, 20);
        shed(sim, 29, 20);
        shed(sim, 33, 20);
        massMarines(sim, 45, 30, 6);

        // Watched across the whole window rather than sampled at the end: a
        // sortie completes and goes home, so the instant a test happens to look
        // says nothing about whether two were ever up together.
        int mostAtOnce = 0;
        for (int t = 0; t < 120 * 30; t++) {
            sim.advance(BattleSimulation.TICK_DT);
            int out = 0;
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission m = sim.world().mission(id);
                if (m != null && m.strikeSortie) out++;
            }
            mostAtOnce = Math.max(mostAtOnce, out);
        }
        assertEquals(1, mostAtOnce, "the field scrambled everything it had");
    }

    /** Scattered enemies are not worth a sortie. */
    @Test
    void aScatteredEnemyIsNotWorthASortie() {
        BattleSimulation sim = openSim(true);
        shed(sim, 25, 20);
        // Far enough apart that nobody has friends within the cluster radius.
        for (int i = 0; i < 6; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    4 + i * 9, 30));
        }

        advance(sim, 120 * 30);

        assertNull(strikeOut(sim), "flew a sortie at nothing in particular");
    }

    /**
     * A field with no strip flies nothing, however many aircraft are in its
     * sheds — they cannot get off the ground.
     */
    @Test
    void aFieldWithNoStripFliesNothing() {
        BattleSimulation sim = openSim(false);
        shed(sim, 25, 20);
        massMarines(sim, 45, 30, 6);

        advance(sim, 120 * 30);

        assertNull(strikeOut(sim), "flew off a field with no runway");
    }

    /** A field whose sheds are empty flies nothing either. */
    @Test
    void aFieldWithNothingAirworthyFliesNothing() {
        BattleSimulation sim = openSim(true);
        AirfieldService.Berth shed = shed(sim, 25, 20);
        sim.getAirfieldService().destroyed(shed);
        massMarines(sim, 45, 30, 6);

        advance(sim, 120 * 30);

        assertNull(strikeOut(sim), "flew an aircraft that had been destroyed");
    }
}
