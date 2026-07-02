package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime coverage for the COMBAT membership narrowing: a non-combatant
 * (civilian / engineer / scientist; {@code UnitType.combatant == false}) carries
 * no COMBAT component, so "has COMBAT" defines a combatant — the same
 * presence-is-the-capability shape as MOVEMENT/AI_STATE
 * ({@link StaticEmplacementMembershipTest}).
 *
 * <p>Ambient civilians DO spawn (~8 per battle, via {@code BattleSetup}) and they
 * ARE mobile (they flee), so the dangerous case is an all-live-units reader that
 * touches a COMBAT column without a {@code u.type.combatant} gate. The full-tick
 * test drives the two such readers — {@code AttackerIndexService.rebuild} and
 * {@code InfantryWeapons.tick} (both walk the whole roster every tick) — so a
 * missing gate surfaces as a fail-loud crash here rather than only in-game.
 */
public class CivilianCombatMembershipTest {

    private static final int W = 24;
    private static final int H = 24;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void combatantsHaveCombatNonCombatantsDoNot() {
        BattleSimulation sim = openSim();
        Entity marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 2, 2));
        Entity civilian = sim.spawn(new EntitySpec("c", Faction.CIVILIAN, UnitType.CIVILIAN, 4, 4));
        Entity engineer = sim.spawn(new EntitySpec("e", Faction.CIVILIAN, UnitType.ENGINEER, 5, 5));
        Entity scientist = sim.spawn(new EntitySpec("s", Faction.CIVILIAN, UnitType.SCIENTIST, 6, 6));

        // A combatant carries COMBAT; the three non-combatant types do not.
        assertTrue(sim.world().hasCombat(marine.entityId));
        assertFalse(sim.world().hasCombat(civilian.entityId));
        assertFalse(sim.world().hasCombat(engineer.entityId));
        assertFalse(sim.world().hasCombat(scientist.entityId));

        // Narrowing dropped ONLY COMBAT — civilians are still movers and thinkers
        // (they flee), so MOVEMENT + AI_STATE remain.
        assertTrue(sim.world().hasMovement(civilian.entityId));
        assertTrue(sim.world().hasAiState(civilian.entityId));
    }

    @Test
    public void fullTickWithCiviliansPresentDoesNotFailLoud() {
        BattleSimulation sim = openSim();
        int sid = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Entity marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 2, 2).squad(sid));
        Entity civilian = sim.spawn(new EntitySpec("c", Faction.CIVILIAN, UnitType.CIVILIAN, 10, 10));
        Entity engineer = sim.spawn(new EntitySpec("e", Faction.CIVILIAN, UnitType.ENGINEER, 11, 11));
        Entity scientist = sim.spawn(new EntitySpec("s", Faction.CIVILIAN, UnitType.SCIENTIST, 12, 12));

        // Each advance() drives AttackerIndexService.rebuild + InfantryWeapons.tick
        // — both all-live-units walks that read a COMBAT column. The component-less
        // civilians are included; an ungated reader would fail-loud on their missing
        // COMBAT. Spaced apart so nothing dies, keeping the assertions meaningful.
        for (int i = 0; i < 15; i++) sim.advance(BattleSimulation.TICK_DT);

        // Civilians were never accidentally granted COMBAT, and the marine kept it.
        assertTrue(sim.world().hasCombat(marine.entityId));
        assertFalse(sim.world().hasCombat(civilian.entityId));
        assertFalse(sim.world().hasCombat(engineer.entityId));
        assertFalse(sim.world().hasCombat(scientist.entityId));
    }
}
