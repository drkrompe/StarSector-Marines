package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the {@link BattleSimulation#rollFallbackOnHit} gate that
 * skips GOAP-driven targets (squad-member infantry). The Story B follow-up
 * routes infantry retreat through {@code SurviveContact}/{@code BreakContact}
 * instead of the per-unit timer; civilians and mechs keep the legacy roll
 * until their own substitutes land.
 *
 * <p>Drives the real {@link BattleSimulation} rather than mocking so any
 * future refactor of the gate (faction-based vs. role-based vs. behavior-
 * based) lights up here regardless of which knob we change.
 */
public class RollFallbackGateTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        // A wall column to give findFallbackPosition something to hide behind,
        // so the roll has a non-trivial candidate cell to pick.
        for (int y = 2; y < 10; y++) grid.setWalkable(7, y, false);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void squadMemberInfantrySkipsLegacyFallback() {
        BattleSimulation sim = openSim();
        Unit marine = new Unit("m0", Faction.MARINE, UnitType.MARINE, 3, 5);
        int sid = sim.mintSquad(Faction.MARINE, marine);
        marine.squadId = sid;
        sim.addUnit(marine);

        // Force the roll to "succeed" via many calls — if the gate is off,
        // statistical chance (FALLBACK_CHANCE = 0.25 per call) of all 100
        // calls failing is vanishingly small. The gate is correct iff
        // fallbackTimer stays at zero.
        for (int i = 0; i < 100; i++) sim.rollFallbackOnHit(marine);

        assertEquals(0f, marine.fallbackTimer, 1e-6f,
                "GOAP-driven infantry must never enter the legacy fall-back state — morale owns retreat");
    }

    @Test
    public void mechKeepsLegacyFallback() {
        BattleSimulation sim = openSim();
        Unit mech = new Unit("mech0", Faction.DEFENDER, UnitType.HEAVY_MECH, 9, 5);
        mech.mech = MechLoadoutState.defaultLoadout();
        // Mechs in the current setup carry squadId from BattleSetup's defender
        // cluster — but the gate is mech-aware, so even with a squad they
        // should still roll the legacy fall-back until their GOAP lands.
        int sid = sim.mintSquad(Faction.DEFENDER, mech);
        mech.squadId = sid;
        sim.addUnit(mech);
        // findFallbackPosition needs an enemy in LoS of the mech's cell so its
        // own cell isn't a valid hide spot — otherwise the scorer's
        // distance-zero pick is the mech's own cell, the function bails, and
        // we can't tell whether the gate skipped or the picker just gave up.
        sim.addUnit(new Unit("opp", Faction.MARINE, UnitType.MARINE, 11, 5));

        boolean rolledAtLeastOnce = false;
        for (int i = 0; i < 100; i++) {
            mech.fallbackTimer = 0f; // reset each iteration so we can keep rolling
            sim.rollFallbackOnHit(mech);
            if (mech.fallbackTimer > 0f) {
                rolledAtLeastOnce = true;
                break;
            }
        }
        assertTrue(rolledAtLeastOnce,
                "mechs (no GOAP yet) must still roll the legacy fall-back");
    }

    @Test
    public void civilianKeepsLegacyFallback() {
        BattleSimulation sim = openSim();
        Unit civilian = new Unit("c0", Faction.DEFENDER, UnitType.MARINE, 3, 8);
        // No squad — civilians have squadId == NO_SQUAD by default.
        civilian.squadId = Unit.NO_SQUAD;
        sim.addUnit(civilian);
        // Enemy in LoS so the civilian's own cell fails the hidden-from-enemies
        // test in findFallbackPosition — see the mech case above for the
        // reason this setup is required.
        sim.addUnit(new Unit("opp", Faction.MARINE, UnitType.MARINE, 5, 8));

        boolean rolledAtLeastOnce = false;
        for (int i = 0; i < 100; i++) {
            civilian.fallbackTimer = 0f;
            sim.rollFallbackOnHit(civilian);
            if (civilian.fallbackTimer > 0f) {
                rolledAtLeastOnce = true;
                break;
            }
        }
        assertTrue(rolledAtLeastOnce,
                "non-squad units must still roll the legacy fall-back (FleeBehavior depends on it)");
    }
}
