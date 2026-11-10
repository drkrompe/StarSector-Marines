package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.PlanetSkin.FieldUse;
import com.dillon.starsectormarines.battle.world.gen.nature.Flora;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetSkinTest {

    private static TargetProfile world(SurfacePalette surface) {
        return new TargetProfile(0, 0, 0, 0, "", Set.of(), surface, SettlementLink.ROAD);
    }

    @Test
    void openFieldsOnlyWhereThingsGrowUnderAirThatLetsThem() {
        assertEquals(FieldUse.CROPS, PlanetSkin.fieldsOn(SurfacePalette.VERDANT, Climate.NONE));
        assertEquals(FieldUse.CROPS, PlanetSkin.fieldsOn(SurfacePalette.ARID, Climate.NONE));
        for (SurfacePalette p : EnumSet.of(SurfacePalette.FROZEN, SurfacePalette.ROCK, SurfacePalette.VOLCANIC,
                SurfacePalette.IRRADIATED, SurfacePalette.TOXIC)) {
            assertEquals(FieldUse.BARE, PlanetSkin.fieldsOn(p, Climate.NONE), p.name());
        }
        assertEquals(FieldUse.BARE, PlanetSkin.fieldsOn(SurfacePalette.VERDANT,
                new Climate(EnumSet.of(Climate.Hazard.TOXIC))));
    }

    @Test
    void bareRockIsAirlessUnlessTheProfileSaysOtherwise() {
        assertEquals(Set.of(Climate.Hazard.AIRLESS), PlanetSkin.climateOf(world(SurfacePalette.ROCK)).hazards());
        assertEquals(Climate.NONE, PlanetSkin.climateOf(world(SurfacePalette.FROZEN)));
        Climate toxic = new Climate(EnumSet.of(Climate.Hazard.TOXIC));
        assertEquals(toxic, PlanetSkin.climateOf(world(SurfacePalette.ROCK).withClimate(toxic)));
    }

    @Test
    void theHomesWildGroundAndLawnBecomeTheWorldsWildGroundAndBareGroundStays() {
        assertEquals(GroundKind.SNOW, PlanetSkin.ground(GroundKind.SAND, SurfacePalette.ARID, SurfacePalette.FROZEN));
        assertEquals(GroundKind.STONE, PlanetSkin.ground(GroundKind.GRASS, SurfacePalette.ARID, SurfacePalette.ROCK));
        assertEquals(GroundKind.GRASS, PlanetSkin.ground(GroundKind.SAND, SurfacePalette.ARID, SurfacePalette.VERDANT));
        assertEquals(GroundKind.DIRT, PlanetSkin.ground(GroundKind.DIRT, SurfacePalette.ARID, SurfacePalette.FROZEN));
        assertEquals(GroundKind.GRASS, PlanetSkin.ground(GroundKind.GRASS, SurfacePalette.ARID, SurfacePalette.ARID));
    }

    @Test
    void woodsTakeTheWorldsFloraAndDeadScrubWhereNothingLives() {
        assertSame(Flora.TEMPERATE, PlanetSkin.flora(Flora.TEMPERATE, SurfacePalette.ARID, SurfacePalette.ARID, null));
        assertSame(Flora.TAIGA, PlanetSkin.flora(Flora.TEMPERATE, SurfacePalette.ARID, SurfacePalette.FROZEN, null));
        assertSame(Flora.JUNGLE, PlanetSkin.flora(Flora.DRY, SurfacePalette.ARID, SurfacePalette.VERDANT,
                LivingWorld.JUNGLE));
        assertSame(Flora.DRY, PlanetSkin.flora(Flora.TEMPERATE, SurfacePalette.ARID, SurfacePalette.TOXIC, null));
    }

    @Test
    void stonesAndCliffsKeepTheirPartInTheWorldsSet() {
        String sandstone = RockDamage.Biome.SANDSTONE.stone(RockDamage.GROUPS.get(0));
        assertEquals(RockDamage.Biome.FROST.stone(RockDamage.GROUPS.get(0)),
                PlanetSkin.stone(sandstone, RockDamage.Biome.FROST));
        assertNull(PlanetSkin.stone("forest.oak", RockDamage.Biome.FROST));
        assertEquals("face.3", PlanetSkin.cliffPart("terrain.face.3", TerrainPlan.Finish.ROCK));
        assertNull(PlanetSkin.cliffPart("terrain.ice.face.3", TerrainPlan.Finish.ROCK));
    }

    @Test
    void aSkinMovesNoCellOfTheLayoutAndClearsFieldsWhereNothingGrowsInTheOpen() {
        int w = 8, h = 6;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) topology.setGroundKind(x, y, GroundKind.SAND);
        grid.setWalkable(7, 5, false);
        grid.setSeeThrough(7, 5, false);
        RuralLayout.Plot plot = new RuralLayout.Plot(0, 0, 4, 4, RuralLayout.Use.ROW_CROP, true, false);
        for (int y = 0; y <= 4; y++) for (int x = 0; x <= 4; x++) topology.setGroundKind(x, y, GroundKind.GRASS);
        MapResult map = new MapResult(grid, topology, 0, 0, w - 1, h - 1, new ArrayList<>(), new ArrayList<>())
                .withFields(List.of(plot));
        boolean[] walk = new boolean[w * h], see = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            walk[y * w + x] = grid.isWalkable(x, y);
            see[y * w + x] = grid.isSeeThrough(x, y);
        }
        PlanetSkin.apply(map, SurfacePalette.ARID, world(SurfacePalette.FROZEN));
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            assertEquals(walk[y * w + x], grid.isWalkable(x, y), x + "," + y);
            assertEquals(see[y * w + x], grid.isSeeThrough(x, y), x + "," + y);
        }
        assertEquals(SurfacePalette.FROZEN, topology.groundPalette());
        assertEquals(GroundKind.SNOW, topology.getGroundKind(6, 5));
        assertEquals(GroundKind.SNOW, topology.getGroundKind(0, 0));
        assertEquals(GroundKind.SNOW, topology.getGroundKind(2, 1));
    }

    @Test
    void aWorldOfTheHomePaletteKeepsItsFieldsAndGround() {
        int w = 4, h = 4;
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) topology.setGroundKind(x, y, GroundKind.GRASS);
        MapResult map = new MapResult(new NavigationGrid(w, h), topology, 0, 0, 3, 3, new ArrayList<>(),
                new ArrayList<>()).withFields(List.of(new RuralLayout.Plot(0, 0, 3, 3, RuralLayout.Use.PASTURE,
                true, false)));
        PlanetSkin.apply(map, SurfacePalette.ARID, world(SurfacePalette.ARID));
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) assertEquals(GroundKind.GRASS, topology.getGroundKind(x, y));
    }

    @Test
    void theCoveredFarmStandsInsideItsHeadlandInRowsWithALaneRoundEveryPiece() {
        for (boolean alongX : new boolean[]{true, false}) {
            RuralLayout.Plot plot = alongX ? new RuralLayout.Plot(10, 20, 40, 33, RuralLayout.Use.ROW_CROP, true, false)
                    : new RuralLayout.Plot(10, 20, 23, 50, RuralLayout.Use.GRAIN, false, false);
            List<PlanetSkin.Placed> farm = PlanetSkin.covered(plot);
            assertTrue(farm.stream().anyMatch(p -> p.piece() == PlanetSkin.Cover.TUNNEL));
            for (PlanetSkin.Placed p : farm) {
                assertEquals(alongX ? 0 : 1, p.turns());
                assertTrue(p.x() > plot.x0() && p.y() > plot.y0()
                        && p.x() + p.width() - 1 < plot.x1() && p.y() + p.height() - 1 < plot.y1(), p.toString());
                for (PlanetSkin.Placed q : farm) {
                    if (p == q) continue;
                    boolean apart = p.x() + p.width() + PlanetSkin.LANE <= q.x() || q.x() + q.width() + PlanetSkin.LANE <= p.x()
                            || p.y() + p.height() + PlanetSkin.LANE <= q.y() || q.y() + q.height() + PlanetSkin.LANE <= p.y();
                    assertTrue(apart, p + " and " + q);
                }
            }
        }
    }

    @Test
    void aPlotTooShortForATunnelTakesGrowDomes() {
        List<PlanetSkin.Placed> farm = PlanetSkin.covered(
                new RuralLayout.Plot(0, 0, 5, 9, RuralLayout.Use.ROW_CROP, true, false));
        assertTrue(!farm.isEmpty() && farm.stream().allMatch(p -> p.piece() == PlanetSkin.Cover.DOME));
    }
}

