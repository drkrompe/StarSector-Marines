package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A biome is the ground a place is built on, not the ground a place is made of.
 *
 * <p>The beach override repaints outdoor ground as sand so a shore reads as one
 * continuous strand, which is right for a road, a park or a yard. It took an
 * airbase apron with it: the lot came out floored in beach with each berth's
 * painted outline eaten away in ragged patches, because the override ran after
 * the fill that laid them. Both halves were doing their job.
 *
 * <p>So a facility claims the surface it made, and terrain passes leave it
 * alone. Asserted on the stage rather than on a generated city, because the
 * question is entirely about this stage's decision.
 */
class MadeGroundSurvivesTerrainTest {

    private static final int W = 40;
    private static final int H = 40;

    /** A context whose whole grid is walkable street, with the beach biome bound. */
    private static GenContext beachContext() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(1L), W, H, 1L);
        ctx.put(BspKeys.BIOME_MAP,
                new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(1L)));
        // The override paints the world's own approach ground, so the fixture
        // has to say which world it is on. A living one, whose approach is sand.
        ctx.put(BspKeys.MARKET_PROFILE, new TargetProfile(5, 5, 1, 0, "independent",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.VERDANT, SettlementLink.ROAD));
        return ctx;
    }

    /** Any cell the beach band actually covers, so the test cannot pass vacuously. */
    private static int[] aBeachCell(GenContext ctx) {
        BiomeMap biomes = ctx.get(BspKeys.BIOME_MAP);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (biomes.biomeAt(x, y) == BiomeKind.BEACH) return new int[]{ x, y };
            }
        }
        return null;
    }

    @Test
    void theBeachTakesOrdinaryGround() {
        GenContext ctx = beachContext();
        int[] cell = aBeachCell(ctx);
        assertNotNull(cell, "no beach band on the map — the test proves nothing");

        new BiomeGroundOverrideStage().run(ctx);

        assertEquals(GroundKind.SAND, ctx.topology.getGroundKind(cell[0], cell[1]),
                "the beach override stopped repainting ordinary outdoor ground");
    }

    /** A world with no sea has no sand either: its approach is the ground it is made of. */
    @Test
    void aBarrenWorldsBeachTakesItsOwnGround() {
        GenContext ctx = beachContext();
        ctx.put(BspKeys.MARKET_PROFILE, new TargetProfile(5, 5, 1, 0, "independent",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD));
        int[] cell = aBeachCell(ctx);
        assertNotNull(cell, "no beach band on the map — the test proves nothing");

        new BiomeGroundOverrideStage().run(ctx);

        assertEquals(SurfacePalette.ROCK.approachGround(),
                ctx.topology.getGroundKind(cell[0], cell[1]),
                "a barren world's approach band should not be sand");
    }

    @Test
    void theBeachLeavesAFacilitysMadeGroundAlone() {
        GenContext ctx = beachContext();
        int[] cell = aBeachCell(ctx);
        assertNotNull(cell, "no beach band on the map — the test proves nothing");
        ctx.markMadeGround(cell[0], cell[1]);

        new BiomeGroundOverrideStage().run(ctx);

        assertEquals(GroundKind.STREET, ctx.topology.getGroundKind(cell[0], cell[1]),
                "the beach sanded an apron a facility had claimed as made ground");
    }
}
