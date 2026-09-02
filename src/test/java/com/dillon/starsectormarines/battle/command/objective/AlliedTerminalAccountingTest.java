package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A terminal check names the sides it counts, and the two statements that
 * follow from the marine side counting {@code DEFENDER} and the defender side
 * counting {@code MARINE}: an allied wipe never ends the battle, and a marine
 * wipe with allies still standing is the player's defeat.
 *
 * <p>The second is the one worth writing down. It is tempting to let a
 * surviving ally keep the player's battle alive — the field is not lost, after
 * all — but the player's company being gone <em>is</em> the player's mission
 * ending, and a battle that runs on with nobody the player can command is a
 * battle with no way out of it.
 */
class AlliedTerminalAccountingTest {

    @Test
    void anAlliedWipeDoesNotEndTheBattle() {
        BattleSimulation sim = openSim();
        sim.spawn(unit("marine", Faction.MARINE, UnitType.MARINE, 2, 2));
        sim.spawn(unit("defender", Faction.DEFENDER, UnitType.MILITIA, 12, 12));
        long ally = sim.spawn(unit("ally", Faction.ALLY, UnitType.MILITIA, 3, 3));

        sim.advance(BattleSimulation.TICK_DT);
        assertFalse(sim.isComplete(), "both counted sides are still in play");

        sim.releaseFromRegistry(ally);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sim.isComplete(),
                "no objective counts the allied side, so losing it decides"
                        + " nothing");
        assertNull(sim.getWinner());
    }

    @Test
    void aMarineWipeIsDefeatEvenWithAlliesStillStanding() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(unit("marine", Faction.MARINE, UnitType.MARINE, 2, 2));
        sim.spawn(unit("defender", Faction.DEFENDER, UnitType.MILITIA, 12, 12));
        sim.spawn(unit("ally", Faction.ALLY, UnitType.MILITIA, 3, 3));

        sim.advance(BattleSimulation.TICK_DT);
        assertFalse(sim.isComplete());

        sim.releaseFromRegistry(marine);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.isComplete(),
                "the defender's objective counts MARINE and nothing else");
        assertEquals(Faction.DEFENDER, sim.getWinner(),
                "an ally left on the field does not keep the player's battle"
                        + " alive");
    }

    @Test
    void theBackstopPairCountsOnlyTheOtherSide() {
        ObjectivesService objectives = new ObjectivesService();
        objectives.installEliminationBackstopIfEmpty(
                Faction.MARINE, EnumSet.of(Faction.DEFENDER),
                Faction.DEFENDER, EnumSet.of(Faction.MARINE));

        assertEquals(2, objectives.getObjectives().size());
        EliminateFactionObjective marineSide =
                (EliminateFactionObjective) objectives.getObjectives().get(0);
        EliminateFactionObjective defenderSide =
                (EliminateFactionObjective) objectives.getObjectives().get(1);

        assertEquals(Set.of(Faction.DEFENDER),
                marineSide.countedFactions());
        assertEquals(Set.of(Faction.MARINE),
                defenderSide.countedFactions());
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(16, 16));
        sim.setMissionCompletionEnabled(true);
        return sim;
    }

    private static EntitySpec unit(String id, Faction faction, UnitType type,
                                   int x, int y) {
        return new EntitySpec(id, faction, type, x, y);
    }
}
