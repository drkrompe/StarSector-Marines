package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipAtmosphere;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WolfStrikeTest {

    @Test
    void stationTimeStaysBetweenTenAndThirtySeconds() {
        Random random = new Random(7L);
        float least = Float.MAX_VALUE;
        float most = 0f;
        for (int i = 0; i < 1000; i++) {
            float seconds = WolfStrike.stationSeconds(random);
            least = Math.min(least, seconds);
            most = Math.max(most, seconds);
        }
        assertTrue(least >= WolfStrike.MIN_STATION_SECONDS && least < 11f, "least " + least);
        assertTrue(most < WolfStrike.MAX_STATION_SECONDS && most > 29f, "most " + most);
    }

    @Test
    void aStrikeHoldsWhereTheWolfDropsForAShortStayAndWritesNothingBack() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        NavigationGrid grid = new NavigationGrid(120, 68);
        for (int y = 0; y < 68; y++) {
            for (int x = 0; x < 120; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(120, 68));
        sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 30, 34));
        sim.setCommandPowers(List.of(new WolfStrike()));
        CommandPowerSystem system = new CommandPowerSystem(sim.getCommandPowerService(), sim);
        for (int i = 0; i < 10; i++) sim.advance(BattleSimulation.TICK_DT);

        sim.getCommandPowerService().requestActivation(WolfStrike.ID, 34, 34);
        system.tick(BattleSimulation.TICK_DT);

        assertEquals(1, sim.warships().ships().size());
        Warship wolf = sim.warships().ships().get(0);
        assertEquals(Faction.MARINE, wolf.faction);
        float live = wolf.stationSecondsRemaining() - WarshipAtmosphere.DESCENT_SECONDS;
        assertTrue(live >= WolfStrike.MIN_STATION_SECONDS && live < WolfStrike.MAX_STATION_SECONDS, "live " + live);
        assertEquals(wolf.x, wolf.stationX(), "it holds where it drops");
        assertEquals(null, WolfStrike.source().memberId());
    }
}
