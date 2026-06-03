package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.mech.MechLoadoutState;
import com.dillon.starsectormarines.battle.mech.MechRole;

import com.dillon.starsectormarines.battle.mech.GoapMechBehavior;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.mech.MechSurviveContact;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the Stage 2 mech morale model — HP-threshold drain in
 * {@link BattleSimulation#applyDamage}, per-mech recovery + hysteresis +
 * armor-gone cap in {@code updateMechSquadMorale}, and the squad-level
 * aggregation that lights up {@link MechSurviveContact}. Sister of
 * {@link SquadMoraleTest} but for the per-chassis path.
 */
public class MechMoraleTest {

    private static final int W = 20;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < H; y++) grid.setWalkable(15, y, false);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static void hideEnemy(BattleSimulation sim) {
        Unit d = new Unit("e-hidden", Faction.MARINE, UnitType.MARINE, 18, 6);
        sim.addUnit(d);
    }

    private static Squad mechSquad(BattleSimulation sim, int size, MechRole role) {
        Unit first = new Unit("d0", Faction.DEFENDER, UnitType.HEAVY_MECH, 1, 1);
        first.mech = MechLoadoutState.defaultLoadout(role);
        int squadId = sim.mintSquad(Faction.DEFENDER, first);
        first.squadId = squadId;
        sim.addUnit(first);
        for (int i = 1; i < size; i++) {
            Unit u = new Unit("d" + i, Faction.DEFENDER, UnitType.HEAVY_MECH, 1 + i, 1);
            u.mech = MechLoadoutState.defaultLoadout(role);
            u.squadId = squadId;
            sim.addUnit(u);
        }
        Squad sq = sim.getSquad(squadId);
        sq.originalSize = size;
        return sq;
    }

    @Test
    public void crossingFirstHpThresholdDrainsOnePerThreshold() {
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 1, MechRole.ARMORED_SUPPORT);
        Unit mech = sim.liveUnitAt(0);
        float starting = mech.mech.morale;

        // Damage to 70% HP — crosses the 0.75 threshold once.
        sim.applyDamage(mech, sim.world().maxHp(mech.entityId) * 0.30f, 1f);

        assertEquals(1, mech.mech.hpThresholdsCrossed,
                "70% HP should have crossed the 0.75 threshold exactly once");
        assertEquals(starting - SquadMoraleSystem.MECH_MORALE_DROP_PER_THRESHOLD,
                mech.mech.morale, 1e-5f,
                "single threshold crossing drains exactly MECH_MORALE_DROP_PER_THRESHOLD");
    }

    @Test
    public void multipleThresholdsInOneHitDrainMultipleSteps() {
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 1, MechRole.ARMORED_SUPPORT);
        Unit mech = sim.liveUnitAt(0);

        // Damage straight to ~5% HP — crosses 0.75, 0.50, 0.25, 0.10 (4 thresholds).
        sim.applyDamage(mech, sim.world().maxHp(mech.entityId) * 0.95f, 1f);

        assertEquals(4, mech.mech.hpThresholdsCrossed,
                "5% HP should have crossed all four thresholds");
        assertEquals(Math.max(0f, 1.0f - 4 * SquadMoraleSystem.MECH_MORALE_DROP_PER_THRESHOLD),
                mech.mech.morale, 1e-5f,
                "4 crossings drains 4× MECH_MORALE_DROP_PER_THRESHOLD (clamped at 0)");
    }

    @Test
    public void thresholdsAreMonotonic_reDamageBelowDoesNotDrainAgain() {
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 1, MechRole.ARMORED_SUPPORT);
        Unit mech = sim.liveUnitAt(0);

        // First hit: cross 0.75 (1 drain).
        sim.applyDamage(mech, sim.world().maxHp(mech.entityId) * 0.30f, 1f);
        float afterFirst = mech.mech.morale;

        // Second hit: still above 0.50 — no new threshold crossed.
        sim.applyDamage(mech, sim.world().maxHp(mech.entityId) * 0.05f, 1f);

        assertEquals(1, mech.mech.hpThresholdsCrossed,
                "no new threshold crossed → counter stays at 1");
        assertEquals(afterFirst, mech.mech.morale, 1e-5f,
                "no new threshold crossed → morale unchanged by second hit");
    }

    @Test
    public void mechSquadDoesNotConsumeSquadLevelMoraleDrain() {
        // Per-roadmap: mech squads use HP-threshold drain, not the infantry
        // per-hit/per-death squad-level drain. The squad.morale float should
        // stay at its initial 1.0 even after a mech takes a hit.
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 4, MechRole.ARMORED_SUPPORT);
        Unit mech = sim.liveUnitAt(0);

        sim.applyDamage(mech, sim.world().maxHp(mech.entityId) * 0.30f, 1f);

        assertEquals(1.0f, sq.morale, 1e-5f,
                "mech squad's squad.morale must NOT drain on a mech hit");
    }

    @Test
    public void armorGoneCapClampsMoraleBelowHalfHp() {
        // Below 50% HP, the mech's morale ceiling drops to MECH_MORALE_ARMOR_GONE_CAP.
        // Verify by setting morale high and ticking — recovery should cap there.
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 1, MechRole.ARMORED_SUPPORT);
        hideEnemy(sim);
        Unit mech = sim.liveUnitAt(0);
        sim.world().setHp(mech.entityId, sim.world().maxHp(mech.entityId) * 0.40f); // below the 0.50 gate
        mech.mech.morale = 1.0f; // overshoot — caller forced past cap, recovery clamps
        mech.mech.timeSinceUnderFire = 10f; // out of under-fire window

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(SquadMoraleSystem.MECH_MORALE_ARMOR_GONE_CAP, mech.mech.morale, 1e-5f,
                "morale must clamp to armor-gone cap once HP drops below MECH_MORALE_ARMOR_GONE_HP_FRAC");
    }

    @Test
    public void hysteresisFlipsBrokenBelowMechThresholds() {
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 1, MechRole.ARMORED_SUPPORT);
        hideEnemy(sim);
        Unit mech = sim.liveUnitAt(0);
        mech.mech.morale = 0.10f; // below 0.60 broken threshold (cap=1.0)
        mech.mech.timeSinceUnderFire = 0f; // in under-fire — no recovery

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(mech.mech.moraleBroken,
                "morale 0.10 < MECH_MORALE_BROKEN_THRESHOLD (0.60) → moraleBroken trips");
        assertTrue(sq.moraleBroken,
                "all-broken single-member mech squad → squad.moraleBroken trips");
    }

    @Test
    public void hysteresisHoldsBrokenBetweenThresholds() {
        // morale in (0.60, 0.85) band must hold its current broken state —
        // hysteresis prevents flicker.
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 1, MechRole.ARMORED_SUPPORT);
        hideEnemy(sim);
        Unit mech = sim.liveUnitAt(0);
        mech.mech.morale = 0.70f;
        mech.mech.moraleBroken = true;
        mech.mech.timeSinceUnderFire = 10f;

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(mech.mech.moraleBroken,
                "morale between 0.60 and 0.85 must hold the broken flag");
    }

    @Test
    public void squadAggregatesMajorityBroken() {
        // 4-mech squad. 2 broken → squad.moraleBroken should trip (2*2 >= 4).
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 4, MechRole.ARMORED_SUPPORT);
        hideEnemy(sim);
        sim.liveUnitAt(0).mech.morale = 0.05f;
        sim.liveUnitAt(1).mech.morale = 0.05f;
        sim.liveUnitAt(2).mech.morale = 1.0f;
        sim.liveUnitAt(3).mech.morale = 1.0f;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            Unit u = sim.liveUnitAt(i);
            if (u.mech != null) u.mech.timeSinceUnderFire = 0f;
        }

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sq.moraleBroken,
                "2-of-4 mechs broken (majority via doubled count) → squad flag trips");
    }

    @Test
    public void squadHoldsUnbrokenWithMinorityBroken() {
        // 4-mech squad. 1 broken → 1*2 < 4 → squad.moraleBroken stays false.
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 4, MechRole.ARMORED_SUPPORT);
        hideEnemy(sim);
        sim.liveUnitAt(0).mech.morale = 0.05f;
        for (int i = 1; i < 4; i++) sim.liveUnitAt(i).mech.morale = 1.0f;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            Unit u = sim.liveUnitAt(i);
            if (u.mech != null) u.mech.timeSinceUnderFire = 0f;
        }

        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sq.moraleBroken,
                "1-of-4 mechs broken → squad flag stays false (minority)");
    }

    @Test
    public void surviveContactWinsGoalSelectionWhenMoraleBroken() {
        // Once MORALE_BROKEN trips, MechSurviveContact (SURVIVAL) should
        // outrank MechEliminateEnemies (ENGAGEMENT). The MISSION-tier role
        // goals carve themselves out on MORALE_BROKEN, so SURVIVAL wins.
        BattleSimulation sim = openSim();
        Squad sq = mechSquad(sim, 1, MechRole.ARMORED_SUPPORT);
        sq.moraleBroken = true;
        // Need an enemy so HAS_TARGET=true (otherwise MechEliminate also
        // returns 0.1 floor — surface a positive ENGAGEMENT alternative).
        hideEnemy(sim);

        WorldState state = WorldStateBuilder.build(sq, sim);
        Goal picked = Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS, state, sq, sim);

        assertNotNull(picked, "MORALE_BROKEN squad should not produce a null goal pick");
        assertSame(MechSurviveContact.INSTANCE, picked,
                "MORALE_BROKEN trips → MechSurviveContact wins over engagement floor");
    }
}
