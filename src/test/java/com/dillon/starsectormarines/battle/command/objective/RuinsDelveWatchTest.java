package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.RuinsDelveWatch.Band;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective.LiftPoint;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsDelveWatchTest {

    private static final List<String> SIGNS = List.of("ruinsSurveyScavengersComms", "ruinsSurveyScavengersHeat",
            "ruinsSurveyScavengersBoat");

    @Test
    void aLayerReadsByItsHostilesAndTheVaultsLayerAlwaysReadsStrongest() {
        assertEquals(Band.QUIET, RuinsDelveWatch.band(0, false));
        assertEquals(Band.QUIET, RuinsDelveWatch.band(RuinsDelveWatch.QUIET_AT_MOST, false));
        assertEquals(Band.HELD, RuinsDelveWatch.band(RuinsDelveWatch.QUIET_AT_MOST + 1, false));
        assertEquals(Band.STRONGLY_HELD, RuinsDelveWatch.band(RuinsDelveWatch.HELD_AT_MOST + 1, false));
        assertEquals(Band.STRONGLY_HELD, RuinsDelveWatch.band(0, true));
    }

    @Test
    void aLayersSignIsOneOfThePoolsReadingsAndTheSameEachTime() {
        for (int layer = 0; layer < 6; layer++) {
            String sign = RuinsDelveWatch.sign(SIGNS, 77L, layer);
            assertTrue(SIGNS.contains(sign));
            assertEquals(sign, RuinsDelveWatch.sign(SIGNS, 77L, layer));
        }
    }

    @Test
    void theVaultsLayerReadsThePrizesSignAndEveryOtherTheOccupants() {
        assertEquals("ruinsInsideServerHall", RuinsDelveWatch.signAt(SIGNS, "ruinsInsideServerHall", 77L, 3, 4));
        for (int layer = 0; layer < 3; layer++) {
            assertEquals(RuinsDelveWatch.sign(SIGNS, 77L, layer),
                    RuinsDelveWatch.signAt(SIGNS, "ruinsInsideServerHall", 77L, layer, 4));
        }
        assertEquals(RuinsDelveWatch.sign(SIGNS, 77L, 3), RuinsDelveWatch.signAt(SIGNS, null, 77L, 3, 4));
    }

    @Test
    void takingTheEntryAndThenALayersJunctionAreStandsWithTheirTallies() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        try (BattleSimulation sim = new BattleSimulation(grid, new CellTopology(20, 20))) {
            // Two layers, outside (x < 10) and inside (x >= 10); the inside is the vault's, its junction at (12, 4).
            var watch = new RuinsDelveWatch(List.of(new RuinsDelve.Area(0, 0, 9, 19),
                    new RuinsDelve.Area(10, 0, 19, 19)), new int[][]{null, {12, 4}}, 1, SIGNS, 5L);
            var vault = new RuinsVaultObjective("R", "vault haul", "The vault", sim.getZoneGraph().zoneIdAt(10, 10),
                    100f, 10f, List.of(new LiftPoint("Landing area", true, new int[]{10, 10, 2, 2}),
                    new LiftPoint("Entrance", LiftPoint.Kind.ENTRANCE, new int[]{10, 10, 16, 16})));
            long pointman = sim.spawn(new EntitySpec("pointman", Faction.MARINE, UnitType.MARINE, 16, 16));
            long second = sim.spawn(new EntitySpec("second", Faction.MARINE, UnitType.MARINE, 2, 2));

            for (int i = 0; i < 200 && vault.entry() < 0; i++) {
                vault.tick(sim);
                watch.tick(sim, vault);
            }
            var entry = watch.picture(sim, vault).stand();
            assertEquals("Entry held", entry.where());
            assertEquals(0, entry.down());
            assertEquals("ruinsCloseStronglyHeld", entry.bandKey(), "the inside is the vault's layer");
            assertEquals(2, watch.picture(sim, vault).landed());

            sim.world().setCellPos(pointman, 12, 4);
            TestUnits.kill(sim, second);
            for (int i = 0; i < 200 && watch.picture(sim, vault).stand() == entry; i++) watch.tick(sim, vault);
            var layer = watch.picture(sim, vault).stand();
            assertEquals("Layer 2 held", layer.where());
            assertEquals(1, layer.down(), "the tally counts the losses since the last stand");
            assertNull(layer.bandKey(), "no layer lies past the vault's");
            assertEquals(1, watch.picture(sim, vault).effectives());
        }
    }
}
