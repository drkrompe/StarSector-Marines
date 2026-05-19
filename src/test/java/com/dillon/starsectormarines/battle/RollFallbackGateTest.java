package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the {@link BattleSimulation#rollFallbackOnHit} gate that
 * skips GOAP-driven targets — every squad member, infantry or mech.
 * Infantry retreats via {@code SurviveContact}/{@code BreakContact} (Story
 * B); mech squads run implacable in Stage 1 with no flinch (see
 * {@code roadmap/ai/14-mech-stage1.md} "Mech survival"). Civilians
 * (no-squad units) keep the legacy roll — {@code FleeBehavior} depends
 * on it.
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
    public void squadMemberMechSkipsLegacyFallback() {
        BattleSimulation sim = openSim();
        Unit mech = new Unit("mech0", Faction.DEFENDER, UnitType.HEAVY_MECH, 9, 5);
        mech.mech = MechLoadoutState.defaultLoadout(MechRole.ARMORED_SUPPORT);
        int sid = sim.mintSquad(Faction.DEFENDER, mech);
        mech.squadId = sid;
        sim.addUnit(mech);
        // Enemy in LoS so findFallbackPosition would otherwise have a valid
        // hide cell — if the gate is off, the roll has every chance to fire
        // and the fallbackTimer would flip non-zero across 100 attempts.
        sim.addUnit(new Unit("opp", Faction.MARINE, UnitType.MARINE, 11, 5));

        for (int i = 0; i < 100; i++) sim.rollFallbackOnHit(mech);

        assertEquals(0f, mech.fallbackTimer, 1e-6f,
                "mech-squad members must never enter the legacy fall-back — Stage 1 mechs are implacable");
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
