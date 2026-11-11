package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.PlanetSkin.FieldUse;
import com.dillon.starsectormarines.battle.world.gen.nature.Flora;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
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
    void townsAreSealedWhereverTheHabitatIsEnclosedAndBareRockIsAirless() {
        for (SurfacePalette palette : SurfacePalette.values()) {
            boolean enclosed = palette == SurfacePalette.ROCK || palette == SurfacePalette.IRRADIATED
                    || palette == SurfacePalette.TOXIC;
            assertEquals(enclosed, PlanetSkin.sealsTowns(world(palette)), palette.toString());
        }
        assertTrue(PlanetSkin.sealsTowns(world(SurfacePalette.ARID)
                .withClimate(new Climate(EnumSet.of(Climate.Hazard.TOXIC)))),
                "a desert under toxic air is sealed");
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
    void aSealedWorldKeepsNoLawnEvenOnItsHomePalette() {
        assertEquals(GroundKind.SNOW, PlanetSkin.ground(GroundKind.GRASS, SurfacePalette.FROZEN, SurfacePalette.FROZEN, true));
        assertEquals(GroundKind.GRASS, PlanetSkin.ground(GroundKind.GRASS, SurfacePalette.FROZEN, SurfacePalette.FROZEN, false));
        assertEquals(GroundKind.DIRT, PlanetSkin.ground(GroundKind.DIRT, SurfacePalette.FROZEN, SurfacePalette.FROZEN, true));
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
            assertTrue(farm.stream().anyMatch(p -> p.piece() == PlanetSkin.SHED));
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
    void aPlotTooShortForALongShedTakesShortOnes() {
        List<PlanetSkin.Placed> farm = PlanetSkin.covered(
                new RuralLayout.Plot(0, 0, 6, 9, RuralLayout.Use.ROW_CROP, true, false));
        assertTrue(!farm.isEmpty() && farm.stream().allMatch(p -> p.piece() == PlanetSkin.SHED_SHORT));
    }

    @Test
    void aShedsGrowLightRunsDownItsMiddleEndToEndAndAPumpHouseHasNone() {
        DoodadDef shed = def(PlanetSkin.SHED), pump = def(PlanetSkin.PUMPHOUSE);
        List<NightLighting.Lamp> along = PlanetSkin.growLights(List.of(new Doodad(10, 20, shed, 0),
                new Doodad(30, 20, pump, 0)));
        assertEquals(3, along.size());
        for (NightLighting.Lamp lamp : along) {
            assertEquals(21.5f, lamp.y(), 1e-4f);
            assertTrue(lamp.x() > 10 && lamp.x() < 16);
            assertSame(NightLighting.Fixture.GLOW, lamp.fixture());
        }
        List<NightLighting.Lamp> turned = PlanetSkin.growLights(List.of(new Doodad(10, 20, shed, 1)));
        assertEquals(3, turned.size());
        for (NightLighting.Lamp lamp : turned) assertEquals(11.5f, lamp.x(), 1e-4f);
        NightLighting night = PlanetSkin.lit(new NightLighting(0.78f, List.of(), Set.of()), along);
        assertTrue(night.lamps().stream().allMatch(night::powered));
    }

    @Test
    void aSunkenShedIsAnEarthMoundWalkedOverWithLowCoverOnItsBankAndNoWall() {
        int w = 12, h = 8;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkable(x, y, true);
        DoodadService doodads = new DoodadService(grid);
        PlanetSkin.Placed placed = new PlanetSkin.Placed(PlanetSkin.SHED, 3, 2, 0);
        doodads.addDoodad(PlanetSkin.shed(placed, def(PlanetSkin.SHED)));
        int light = PlanetSkin.BANK.level();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            assertTrue(grid.isWalkable(x, y), x + "," + y);
            assertTrue(!grid.blocksLineOfSight(x, y), x + "," + y);
        }
        for (int y = 2; y < 5; y++) for (int x = 3; x < 9; x++) {
            assertEquals(light, doodads.getDoodadLevelOnCell(x, y), x + "," + y);
            assertEquals(0, doodads.getDoodadLevelOnCell(x, y, 0.5f), "a chest-high round passes over the bank");
        }
        assertEquals(light, doodads.getDoodadCoverAt(3, 1, 0, 1), "a man lying north of the bank has it as cover");
        assertTrue(grid.hasLineOfSight(0, 3, 11, 3), "the mound hides no one");
        assertEquals(0, doodads.getDoodadLevelOnCell(3, 1));
        assertTrue(light < DoodadCover.MED.level() && PlanetSkin.BANK_HALF_HEIGHT < 0.4f, "lower than a parapet");
        PlanetSkin.Placed pump = new PlanetSkin.Placed(PlanetSkin.PUMPHOUSE, 0, 0, 0);
        assertEquals(0, PlanetSkin.shed(pump, def(PlanetSkin.PUMPHOUSE)).cover);
    }

    @Test
    void theLandingFieldIsLeftBareSoItsFieldOfFireStaysClear() {
        FieldOfFire field = new FieldOfFire(20, 20, 5);
        assertTrue(PlanetSkin.onLanding(new PlanetSkin.Placed(PlanetSkin.SHED, 22, 19, 0), field));
        assertTrue(PlanetSkin.onLanding(new PlanetSkin.Placed(PlanetSkin.SHED, 10, 19, 0), field), "reaching in");
        assertTrue(!PlanetSkin.onLanding(new PlanetSkin.Placed(PlanetSkin.SHED, 30, 19, 0), field));
        assertTrue(!PlanetSkin.onLanding(new PlanetSkin.Placed(PlanetSkin.SHED, 22, 19, 0), null));
    }

    private static DoodadDef def(PlanetSkin.Piece piece) {
        return new DoodadDef(piece.id(), "graphics/doodads/hydro-sheds.png", 0, 0, DoodadCover.NONE, 0.3f,
                piece.along(), piece.across());
    }
}
