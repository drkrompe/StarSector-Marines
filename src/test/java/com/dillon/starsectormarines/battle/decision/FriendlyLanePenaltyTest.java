package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.FiringLane;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what a shooter does about one of its own standing in the lane.
 *
 * <p>{@code BallisticResolver} has always modelled this from the round's side —
 * a friendly met before the intended target catches it with a probability
 * scaled by muzzle distance, and stops it there at half damage — while every
 * decision-side line test bottomed out in the navigation grid, which holds
 * terrain and no units. So a marine with a squadmate directly in front read its
 * lane as clear and fired into their back, indefinitely, and nothing in the
 * picking or the fire gate ever noticed.
 *
 * <p>The lane test is built and the preference on top of it is switched off,
 * because measuring it said so: see {@code TacticalScoring.FRIENDLY_LANE_COST}.
 * The geometry is tested on its own account, and the last test pins what the
 * picker actually does today.
 */
public class FriendlyLanePenaltyTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final int ROW = 20;
    private static final int SHOOTER_X = 10;

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static long marine(BattleSimulation sim, int squadId, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.MARINE, x, y)
                .squad(squadId));
    }

    private static long enemy(BattleSimulation sim, String name, int x, int y) {
        EntitySpec spec = new EntitySpec(name, Faction.DEFENDER, UnitType.MARINE, x, y);
        spec.moveSpeed = 0f;
        return sim.spawn(spec);
    }

    // -- the geometry, asked directly ------------------------------------

    private static FiringLane.Friendlies at(float x, float y, float radius) {
        FiringLane.Friendlies allies = new FiringLane.Friendlies();
        allies.add(x, y, radius);
        return allies;
    }

    @Test
    public void somebodyStandingOnTheLineIsInTheWay() {
        assertTrue(FiringLane.blocked(at(20f, 20f, 0.4f), 10f, 20f, 30f, 20f));
    }

    @Test
    public void somebodyStandingWellOffTheLineIsNot() {
        assertFalse(FiringLane.blocked(at(20f, 24f, 0.4f), 10f, 20f, 30f, 20f),
                "four cells to the side of a lane a cell and a half wide");
    }

    /**
     * The one boundary shared with the ballistic model rather than restated: a
     * body inside the muzzle's zero-catch distance cannot take the round, so it
     * is not in the way however exactly it stands on the line.
     */
    @Test
    public void somebodyAtTheMuzzleIsNotInTheWayBecauseTheRoundCannotCatchThem() {
        float justInside = SHOOTER_X + BallisticResolver.PROXIMITY_CATCH_ZERO_DISTANCE - 0.5f;
        assertFalse(FiringLane.blocked(at(justInside, 20f, 0.4f), 10f, 20f, 30f, 20f));
        float justOutside = SHOOTER_X + BallisticResolver.PROXIMITY_CATCH_ZERO_DISTANCE + 0.5f;
        assertTrue(FiringLane.blocked(at(justOutside, 20f, 0.4f), 10f, 20f, 30f, 20f));
    }

    @Test
    public void somebodyBeyondTheTargetIsNotBetweenAnything() {
        assertFalse(FiringLane.blocked(at(40f, 20f, 0.4f), 10f, 20f, 30f, 20f));
    }

    // -- what the picker does with it ------------------------------------

    /**
     * What the picker does today, which is nothing about lanes.
     *
     * <p>The preference is built and switched off: measured against a control
     * on the same tree it cost the reinforced-south fixture four of its
     * fourteen captures and four of its eleven held compounds while killing 56
     * fewer defenders, because a blocked lane usually means a squadmate is
     * between this marine and the enemy the squad is already engaging, and
     * moving somebody off that target breaks up the concentrated fire that
     * does the killing. Reducing it to a pure tiebreak reproduced almost the
     * whole loss, so the weight was never the problem.
     *
     * <p>This test pins the shipped answer rather than the switched-on one,
     * for the same reason there is no test of the contact drill: a static
     * default cannot be toggled per test, and the behaviour that ships is the
     * one worth guarding. The geometry above is what the sidestep attempt was
     * built on — see {@code LaneSidestepTest} for what a marine does about a
     * blocked lane instead — and it is tested on its own account.
     */
    @Test
    public void theShippedPickerTakesTheNearestTargetEvenThroughItsOwnMan() {
        BattleSimulation sim = arena();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long shooter = marine(sim, squadId, "m0", SHOOTER_X, ROW);
        marine(sim, squadId, "m1", 20, ROW);
        long blockedEnemy = enemy(sim, "blocked", 30, ROW);
        enemy(sim, "clear", 30, ROW + 6);
        sim.advance(BattleSimulation.TICK_DT);

        FiringLane.Friendlies allies = at(20f, ROW + 0.5f, 0.4f);
        assertTrue(FiringLane.blocked(allies, SHOOTER_X + 0.5f, ROW + 0.5f,
                        30.5f, ROW + 0.5f),
                "the near lane really is blocked, or this test asserts nothing");
        assertEquals(blockedEnemy, sim.getTacticalScoring().findBestTarget(shooter),
                "and the shipped picker takes it anyway: distance decides, and the "
                        + "squad keeps its fire on one enemy");
    }
}
