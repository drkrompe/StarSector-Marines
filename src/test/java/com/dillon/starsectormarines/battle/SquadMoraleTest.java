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

        // Drive morale below the broken threshold directly — the drain
        // cooldown deliberately caps how fast bursts can drain morale, so
        // accumulating "many hits worth" inside a single test tick goes
        // through the cooldown gate. The hysteresis math we're verifying
        // sits downstream of how morale got there.
        Unit b = sim.getUnits().get(1);
        sim.applyDamage(b, b.hp + 1000f, 1f); // 1 kill → 3-of-4 alive, cap=0.75
        sq.morale = 0.15f;
        sq.moraleDrainCooldown = 0f;

        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sq.moraleBroken, "morale below broken threshold → moraleBroken flips true");

        // Drive recovery ticks until morale climbs over the clear threshold.
        // Recovery rate scales with cap: 0.20 * 0.75 = 0.15/sec for a 3-of-4
        // squad. clear_at = 0.5 * 0.75 = 0.375. From 0.15 needs > 0.225 /
        // 0.15 = 1.5s. Drive 3 sim-seconds to give headroom.
        for (int i = 0; i < 90; i++) sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sq.morale > BattleSimulation.MORALE_CLEAR_THRESHOLD * 0.75f,
                "morale should have recovered past the (scaled) clear threshold within 3 sim-seconds");
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
    public void soloSurvivorCanRecoverPastClearThreshold() {
        // Pre-fix pathology: a 1-of-4 survivor had cap = 0.25 < absolute
        // clear threshold (0.5), so morale could never climb back above
        // clear and the squad stayed perma-broken. Under the cap-scaled
        // model the clear threshold for cap=0.25 is 0.125 — well below cap.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        // Three deaths → 1-of-4 alive, cap = 0.25.
        sim.getUnits().get(0).hp = 0f;
        sim.getUnits().get(1).hp = 0f;
        sim.getUnits().get(2).hp = 0f;
        sq.morale = 0f;
        sq.moraleBroken = true;

        // Drive long enough for recovery to saturate at cap. Solo recovery
        // rate scales: 0.20 * 0.25 = 0.05/sec → ~5 sim-seconds to reach
        // cap from 0. 200 ticks (~6.7s) gives comfortable headroom.
        for (int i = 0; i < 200; i++) sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0.25f, sq.morale, 1e-3f,
                "solo survivor's morale should pin at their cap (0.25), not climb past");
        assertFalse(sq.moraleBroken,
                "morale at cap (0.25) is well above scaled clear (0.125) — must clear");
    }

    @Test
    public void soloSurvivorFoldsOnSingleHit() {
        // With drain scaled by 1/cap, a solo survivor (cap = 0.25) takes a
        // 0.20 drain per hit. Sitting at cap, one hit drops morale to 0.05,
        // below the scaled broken threshold (0.075). Folds in one shot —
        // matches the "any incoming suppressing fire and they fold" intent.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        // Three kills → 1-of-4 alive.
        sim.getUnits().get(0).hp = 0f;
        sim.getUnits().get(1).hp = 0f;
        sim.getUnits().get(2).hp = 0f;
        // Recover the survivor to their cap.
        sq.morale = 0.25f;
        sq.moraleBroken = false;
        // One tick to settle moraleBroken at the cap value.
        sim.advance(BattleSimulation.TICK_DT);
        // The recovery tick may have just-barely tweaked morale; reset to
        // exactly cap so the assertion below pins on the drain math.
        sq.morale = 0.25f;
        sq.moraleBroken = false;

        Unit survivor = sim.getUnits().get(3);
        sim.applyDamage(survivor, 1f, 1f);
        assertTrue(survivor.isAlive(), "test prerequisite: 1 damage shouldn't kill");

        // Hit drain for cap=0.25 is 0.05/0.25 = 0.20 → morale = 0.05.
        assertEquals(0.05f, sq.morale, 1e-5f,
                "solo hit drain should scale to 0.05/cap = 0.20");
        // Now run one tick so updateSquadMorale notices the threshold cross.
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sq.moraleBroken,
                "morale 0.05 < scaled broken (0.075) — single incoming hit folds the solo");
    }

    @Test
    public void thresholdsScaleByCap() {
        // 2-of-4 squad (cap = 0.5). Scaled thresholds: broken < 0.15,
        // clear > 0.25. Sitting at 0.20 must hold its broken state through
        // the hysteresis band — same logic as the full-squad case but
        // scaled.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        // Two deaths.
        sim.getUnits().get(0).hp = 0f;
        sim.getUnits().get(1).hp = 0f;
        sq.morale = 0.20f;
        sq.moraleBroken = true;

        sim.advance(BattleSimulation.TICK_DT);

        // Morale ticked up slightly (~0.0067), still in the (0.15, 0.25)
        // hysteresis band for cap=0.5 — should NOT have cleared.
        assertTrue(sq.morale < 0.25f,
                "test prerequisite: morale stays in the scaled hysteresis band after one tick");
        assertTrue(sq.morale > 0.15f,
                "test prerequisite: morale stays above scaled broken threshold");
        assertTrue(sq.moraleBroken,
                "scaled-threshold hysteresis must hold broken flag inside the band");
    }

    @Test
    public void burstOfHitsInOneTickCountsAsOneDrainEvent() {
        // The drain cooldown caps how fast bursts can drain a squad. A full
        // squad eating 10 hits in one tick should only register one hit
        // worth of drain — the rest fall inside the cooldown window. This
        // is the guardrail against "hail of bullets insta-break."
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        Unit target = sim.getUnits().get(0);
        float startMorale = sq.morale;

        for (int i = 0; i < 10; i++) sim.applyDamage(target, 1f, 1f);

        assertEquals(startMorale - BattleSimulation.MORALE_DROP_ON_HIT, sq.morale, 1e-5f,
                "10 hits inside one cooldown window drain by exactly one hit");
        assertEquals(BattleSimulation.MORALE_DRAIN_COOLDOWN, sq.moraleDrainCooldown, 1e-5f,
                "cooldown set on the first hit and not refreshed by subsequent hits");
    }

    @Test
    public void cooldownLapsesBetweenSeparateHits() {
        // After the cooldown elapses, the next hit should register again.
        // We tick the sim past the cooldown duration so the gate reopens.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        Unit target = sim.getUnits().get(0);

        sim.applyDamage(target, 1f, 1f);
        float afterFirst = sq.morale;

        // Tick past the cooldown so the next hit can drain again. Out-of-
        // contact recovery climbs morale during this window — we subtract
        // it back out when checking the second drain.
        int cooldownTicks = (int) Math.ceil(
                BattleSimulation.MORALE_DRAIN_COOLDOWN / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < cooldownTicks; i++) sim.advance(BattleSimulation.TICK_DT);
        float moraleAfterCooldown = sq.morale;

        sim.applyDamage(target, 1f, 1f);

        assertEquals(moraleAfterCooldown - BattleSimulation.MORALE_DROP_ON_HIT,
                sq.morale, 1e-5f,
                "second hit after cooldown elapses must drain again");
    }

    @Test
    public void deathDrainBypassesCooldown() {
        // The hit component of a kill drain respects the cooldown, but the
        // death component itself (-0.30) is a discrete event that the model
        // should always reflect — even if a fellow squadmate just took a
        // hit a tick ago.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        Unit a = sim.getUnits().get(0);
        Unit b = sim.getUnits().get(1);

        // Burn the cooldown with a non-lethal hit.
        sim.applyDamage(a, 1f, 1f);
        float afterHit = sq.morale;
        assertTrue(sq.moraleDrainCooldown > 0f,
                "test prerequisite: first hit puts the cooldown on");

        // Kill b immediately — death drain should still apply.
        sim.applyDamage(b, b.hp + 1000f, 1f);

        assertEquals(afterHit - BattleSimulation.MORALE_DROP_ON_DEATH,
                sq.morale, 1e-5f,
                "death drain stacks regardless of the cooldown state");
    }

    @Test
    public void recoveryRateScalesWithCap() {
        // Scaled recovery: rate = MORALE_RECOVERY_RATE * cap. Verified by
        // running one tick on a 2-of-4 squad (cap = 0.5) and checking the
        // morale climb is exactly half the full-squad rate.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        hideDefender(sim);
        sim.getUnits().get(0).hp = 0f;
        sim.getUnits().get(1).hp = 0f;
        sq.morale = 0.10f;
        sq.moraleBroken = true;

        sim.advance(BattleSimulation.TICK_DT);

        float expected = 0.10f
                + BattleSimulation.MORALE_RECOVERY_RATE * 0.5f * BattleSimulation.TICK_DT;
        assertEquals(expected, sq.morale, 1e-5f,
                "recovery should be cap-scaled (rate × 0.5 for a 2-of-4 squad)");
    }

    @Test
    public void hitDrainScalesWithShooterMoraleImpact() {
        // The applyDamage moraleImpact param scales the hit drain — drives
        // the per-shooter intimidation factor (MILITIA = 0.4, HEAVY_MECH =
        // 1.5, etc.). A militia hit should bleed only 40% of a marine hit.
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        Unit target = sim.getUnits().get(0);
        float before = sq.morale;

        sim.applyDamage(target, 1f, 1f, UnitType.MILITIA.moraleImpact);

        float expected = before - BattleSimulation.MORALE_DROP_ON_HIT
                * UnitType.MILITIA.moraleImpact;
        assertEquals(expected, sq.morale, 1e-5f,
                "militia hit drain should scale by MILITIA.moraleImpact (0.4)");
    }

    @Test
    public void heavyMechHitDrainsMoreThanMarineHit() {
        // The mech intimidation factor (1.5) should rattle a marine squad
        // faster than the baseline (1.0). Same hit, same target, just two
        // different shooter intimidation values. We test directly on the
        // drain math rather than chaining two applyDamage calls (which
        // involves cooldowns and recovery).
        BattleSimulation sim = openSim();
        Squad sq = marineSquad(sim, 4);
        Unit a = sim.getUnits().get(0);

        sim.applyDamage(a, 1f, 1f, UnitType.MARINE.moraleImpact);
        float marineDrain = 1.0f - sq.morale;

        // Reset state and apply the heavy-mech hit identically.
        sq.morale = 1.0f;
        sq.moraleDrainCooldown = 0f;
        Unit b = sim.getUnits().get(1);
        sim.applyDamage(b, 1f, 1f, UnitType.HEAVY_MECH.moraleImpact);
        float mechDrain = 1.0f - sq.morale;

        assertTrue(mechDrain > marineDrain,
                "HEAVY_MECH (1.5) drain must exceed MARINE (1.0) drain on identical hits ("
                        + "mech=" + mechDrain + ", marine=" + marineDrain + ")");
        assertEquals(marineDrain * UnitType.HEAVY_MECH.moraleImpact, mechDrain, 1e-5f,
                "mech drain should be exactly marine drain × HEAVY_MECH.moraleImpact");
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
