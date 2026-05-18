package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the squad-morale state machine — drain hooks ({@link
 * BattleSimulation#applyDamage}), the per-tick recovery + hysteresis pass
 * ({@code updateSquadMorale}), and the {@code aliveMembers / originalSize}
 * recovery cap. Drives the real {@link BattleSimulation} so the wiring path
 * (applyDamage → squad lookup → drain → recovery → hysteresis) gets exercised
 * end to end.
 */
public class SquadMoraleTest {

    private static final int W = 20;
    private static final int H = 12;

    /**
     * 20x12 floor with a wall at column 15 — splits the map into a marine
     * zone (left) and a defender hideout (right). Tests that need an
     * "out of contact" tick stash a defender beyond the wall via {@link
     * #hideDefender}: the LoS scan stays clean (so {@code _engagedThisTick}
     * is false and recovery fires) while {@link BattleSimulation#checkWinCondition}
     * still sees both factions alive so the sim doesn't auto-complete after
     * one tick.
     */
    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < H; y++) grid.setWalkable(15, y, false);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * Park a defender deep in the right hideout. They sit there forever —
     * never used in target tests, never seen by marines (wall in the way) —
     * but their existence keeps the auto-win check from completing the battle
     * after one tick. Tests that DO want LoS engagement put the defender on
     * the marine side of the wall instead.
     */
    private static void hideDefender(BattleSimulation sim) {
        Unit d = new Unit("d-hidden", Faction.DEFENDER, UnitType.MARINE, 18, 6);
        sim.addUnit(d);
    }

    /**
     * Hand-mint a marine squad of {@code size} units placed in a line. We
     * bypass the AirSystem deboard path so the test doesn't need to fly a
     * shuttle in; what we care about is the drain/recovery math, not the
     * spawn pipeline. {@code originalSize} is stamped explicitly to match.
     */
    private static Squad marineSquad(BattleSimulation sim, int size) {
        Unit first = new Unit("m0", Faction.MARINE, UnitType.MARINE, 1, 1);
        int squadId = sim.mintSquad(Faction.MARINE, first);
        first.squadId = squadId;
        sim.addUnit(first);
        for (int i = 1; i < size; i++) {
            Unit u = new Unit("m" + i, Faction.MARINE, UnitType.MARINE, 1 + i, 1);
            u.squadId = squadId;
            sim.addUnit(u);
        }
        Squad sq = sim.getSquad(squadId);
        sq.originalSize = size;
        return sq;
    }

    @Test
    public void hitDrainsByDropOnHit() {
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        Unit target = sim.getUnits().get(0);
        float startingHp = target.hp;
        // Damage low enough that the unit survives so we isolate the hit drain.
        sim.applyDamage(target, 1f, 1f);
        assertTrue(target.hp < startingHp, "test prerequisite: damage actually landed");
        assertTrue(target.isAlive(), "test prerequisite: target survived the hit");
        assertEquals(1.0f - BattleSimulation.MORALE_DROP_ON_HIT, sq.morale, 1e-5f,
                "single hit on a squadmate drops morale by exactly MORALE_DROP_ON_HIT");
    }

    @Test
    public void deathDrainsByHitPlusDeath() {
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        Unit target = sim.getUnits().get(0);
        // Overkill damage — guaranteed kill.
        sim.applyDamage(target, target.hp + 1000f, 1f);
        assertFalse(target.isAlive(), "test prerequisite: target died");
        float expected = 1.0f - BattleSimulation.MORALE_DROP_ON_HIT
                              - BattleSimulation.MORALE_DROP_ON_DEATH;
        assertEquals(expected, sq.morale, 1e-5f,
                "kill = hit drain + death drain stacked");
    }

    @Test
    public void recoveryFiresWhenOutOfContact() {
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        sq.morale = 0.2f;
        sq.moraleBroken = true;

        // Hidden defender keeps the battle from auto-completing; the wall
        // keeps LoS clean → _engagedThisTick stays false → recovery fires.
        sim.advance(BattleSimulation.TICK_DT);

        float expected = 0.2f + BattleSimulation.MORALE_RECOVERY_RATE * BattleSimulation.TICK_DT;
        assertEquals(expected, sq.morale, 1e-5f,
                "out-of-contact tick should grant exactly one TICK_DT of recovery");
    }

    @Test
    public void recoveryStopsWhenEngaged() {
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        sq.morale = 0.2f;

        // Put a defender in LoS of a squadmate — that flips _engagedThisTick
        // true during updateSquadAlertLevels and skips the recovery branch.
        Unit defender = new Unit("d", Faction.DEFENDER, UnitType.MARINE, 10, 1);
        sim.addUnit(defender);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0.2f, sq.morale, 1e-5f,
                "in-contact tick must not grant recovery — morale holds at the drained value");
    }

    @Test
    public void recoveryIsCappedByAliveOverOriginal() {
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        // Half the squad dead → cap = 2/4 = 0.5.
        sim.getUnits().get(0).hp = 0f;
        sim.getUnits().get(1).hp = 0f;
        sq.morale = 0.49f;

        // Drive enough ticks that uncapped recovery would push morale well above 0.5.
        for (int i = 0; i < 200; i++) sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0.5f, sq.morale, 1e-3f,
                "morale should pin at the alive/original ratio cap, not climb back to 1.0");
    }

    @Test
    public void hysteresisFlipsBrokenBelowThresholdAndClearsAboveClear() {
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);

        // Walk morale below the broken threshold via drain on hits.
        Unit a = sim.getUnits().get(0);
        Unit b = sim.getUnits().get(1);
        // 4 hits + 1 kill = 4*0.05 + 0.30 + 0.05 = 0.55 drain → morale 0.45.
        // Below clear (0.5) but above broken (0.3). Need more.
        for (int i = 0; i < 10; i++) sim.applyDamage(a, 1f, 1f);
        sim.applyDamage(b, b.hp + 1000f, 1f);
        // After: 10*0.05 + (0.05 + 0.30) = 0.85 drain → morale = 0.15.
        // Now drive one tick so updateSquadMorale notices the threshold cross.
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sq.moraleBroken, "morale below broken threshold → moraleBroken flips true");

        // Now drive recovery ticks until morale climbs over the clear threshold.
        // Recovery rate is 0.20/sec capped by alive/original = 3/4 = 0.75.
        // From ~0.15 to >0.5 needs > 0.35 / 0.20 = 1.75s of recovery.
        // Drive 3 sim-seconds to give headroom.
        for (int i = 0; i < 90; i++) sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sq.morale > BattleSimulation.MORALE_CLEAR_THRESHOLD,
                "morale should have recovered past the clear threshold within 3 sim-seconds");
        assertFalse(sq.moraleBroken,
                "above clear threshold → moraleBroken flips false (hysteresis cleared)");
    }

    @Test
    public void brokenDoesNotClearBetweenThresholds() {
        // A squad sitting in the (0.3, 0.5) band must keep its current
        // broken flag — that's the whole point of hysteresis. We test by
        // pre-setting the flag and letting the unit recover into the band
        // without crossing the clear threshold.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        sq.morale = 0.35f;
        sq.moraleBroken = true;

        // One tick → morale climbs by RATE * DT = ~0.0067, still well under 0.5.
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sq.morale < BattleSimulation.MORALE_CLEAR_THRESHOLD,
                "test prerequisite: morale stays in the hysteresis band after one tick");
        assertTrue(sq.moraleBroken,
                "hysteresis must hold broken flag while morale is in (broken, clear) gap");
    }

    @Test
    public void nonSquadHitDoesNotCrashOrDrainAnyone() {
        // applyDamage on a squad-less target (turret/civilian path) must not
        // touch any morale. Belt-and-braces — the guard in applyDamage is the
        // squadId != NO_SQUAD check; this pins it.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 2);
        float before = sq.morale;

        Unit civilian = new Unit("c", Faction.DEFENDER, UnitType.MARINE, 8, 8);
        civilian.squadId = Unit.NO_SQUAD;
        sim.addUnit(civilian);
        sim.applyDamage(civilian, civilian.hp + 1000f, 1f);

        assertEquals(before, sq.morale, 1e-6f,
                "killing a non-squad unit must not drain any squad's morale");
    }
}
