package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.LowPass;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WolfStrikeTest {

    /** Open ground with one marine at (30, 34) who sees the ground around it. */
    private static BattleSimulation sim() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        NavigationGrid grid = new NavigationGrid(160, 68);
        for (int y = 0; y < 68; y++) {
            for (int x = 0; x < 160; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(160, 68));
        sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 30, 34));
        sim.setCommandPowers(List.of(new WolfStrike()));
        for (int i = 0; i < 10; i++) sim.advance(BattleSimulation.TICK_DT);
        return sim;
    }

    @Test
    void aDrawnLineSendsTheStockWolfDownItFromBehindTheStartAndOutThroughTheDeck() {
        BattleSimulation sim = sim();
        CommandPowerSystem system = new CommandPowerSystem(sim.getCommandPowerService(), sim);

        sim.getCommandPowerService().requestLineActivation(WolfStrike.ID, 32, 34, 62, 34);
        system.tick(BattleSimulation.TICK_DT);

        assertEquals(1, sim.warships().ships().size());
        Warship wolf = sim.warships().ships().get(0);
        LowPass pass = wolf.lowPass();
        assertNotNull(pass);
        assertEquals(62.5f, pass.endX(), 1e-3f);
        assertTrue(wolf.x < 32.5f, "it breaks out of the deck behind the line's start, at " + wolf.x);
        assertTrue(wolf.climbsAfterPass());
        assertTrue(wolf.descending());
        assertNull(WolfStrike.source().memberId());
    }

    @Test
    void aLineTooShortOrTooLongIsRefusedAndNothingIsSpent() {
        BattleSimulation sim = sim();
        WolfStrike power = new WolfStrike();
        assertFalse(power.canTargetLine(32, 34, 40, 34, sim), "shorter than a pass");
        assertFalse(power.canTargetLine(32, 34, 32 + (int) WolfStrike.MAX_LINE_CELLS + 10, 34, sim), "longer than a run");
        assertTrue(power.canTargetLine(32, 34, 62, 34, sim));
    }

    @Test
    void theRunUpCoversTheDescentFromRest() {
        // 2 cells/s at the pass, reached after a second at 2 cells/s/s: 1 cell of ramp, then 5 s at 2.
        float runUp = WolfStrike.runUpCells(2f / LowPass.SPEED_FACTOR, 2f / LowPass.HANDLING_FACTOR);
        assertEquals(11f, runUp, 1e-3f);
    }
}
