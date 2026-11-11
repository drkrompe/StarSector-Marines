package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctDefence;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stage scatters nests when a mission declares them and not otherwise, on a bare field. */
class DefenceNestStageTest {

    private static final int W = 240;
    private static final int H = 160;

    private static GenContext field(TargetProfile profile) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), W, H, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        if (profile != null) ctx.put(BspKeys.MARKET_PROFILE, profile);
        return ctx;
    }

    private static TargetProfile declared(int nests) {
        return TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(0, false, nests));
    }

    @Test
    void nothingIsPlacedUnlessTheMissionDeclaresNests() {
        GenContext none = field(null);
        new DefenceNestStage().run(none);
        assertTrue(none.defensePosts.isEmpty());

        GenContext marketOnly = field(TargetProfile.NEUTRAL.withDefences(true, true, true));
        new DefenceNestStage().run(marketOnly);
        assertTrue(marketOnly.defensePosts.isEmpty(), "the market's signals are inputs, not a declaration");

        GenContext batteriesOnly = field(TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(3, true)));
        new DefenceNestStage().run(batteriesOnly);
        assertTrue(batteriesOnly.defensePosts.isEmpty(), "declared batteries are not nests");
    }

    @Test
    void exactlyTheDeclaredCountStandsAlternatingCannonAndMachineGun() {
        GenContext ctx = field(declared(7));
        new DefenceNestStage().run(ctx);

        assertEquals(7, ctx.defensePosts.size());
        for (int i = 0; i < ctx.defensePosts.size(); i++) {
            DefensePost post = ctx.defensePosts.get(i);
            assertEquals(1, post.turrets.size());
            String expected = i % 2 == 0
                    ? TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID : TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID;
            assertEquals(expected, post.turrets.get(0).structureId);
            assertFalse(ctx.grid.isWalkable(post.anchorX, post.anchorY), "the mount is sealed");
        }
        assertTrue(ctx.tactical.isEmpty(), "nests are unmanned: no guard-post node");
    }

    @Test
    void nestsAreSpreadAndDeterministic() {
        GenContext a = field(declared(6));
        GenContext b = field(declared(6));
        new DefenceNestStage().run(a);
        new DefenceNestStage().run(b);
        assertEquals(a.defensePosts.size(), b.defensePosts.size());
        for (int i = 0; i < a.defensePosts.size(); i++) {
            assertEquals(a.defensePosts.get(i).anchorX, b.defensePosts.get(i).anchorX);
            assertEquals(a.defensePosts.get(i).anchorY, b.defensePosts.get(i).anchorY);
        }
        for (int i = 0; i < a.defensePosts.size(); i++) {
            for (int j = i + 1; j < a.defensePosts.size(); j++) {
                double d = Math.hypot(a.defensePosts.get(i).anchorX - a.defensePosts.get(j).anchorX,
                        a.defensePosts.get(i).anchorY - a.defensePosts.get(j).anchorY);
                assertTrue(d >= 20, "two nests " + d + " cells apart: a cluster, not a network");
            }
        }
    }

    /** Buildings in rows: walls every {@code pitch} columns, so no gun between two has ten open cells east or west. */
    private static void streets(GenContext ctx, int pitch) {
        for (int x = 0; x < W; x += pitch) {
            for (int y = 0; y < H; y++) {
                ctx.grid.setWalkable(x, y, false);
                ctx.topology.setWall(x, y, true);
            }
        }
    }

    @Test
    void aNestHardAgainstABuildingIsNotPlaced() {
        GenContext ctx = field(declared(6));
        ctx.put(BspKeys.AXIS, TraversalAxis.WEST_TO_EAST);
        streets(ctx, 6);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, TraversalAxis.WEST_TO_EAST));
        new DefenceNestStage().run(ctx);
        assertTrue(ctx.defensePosts.isEmpty(), "a nest stood facing the attacker with a building across its arc");
    }

    @Test
    void everyNestFacesTheAttackerOverOpenGround() {
        GenContext ctx = field(declared(6));
        ctx.put(BspKeys.AXIS, TraversalAxis.SOUTH_TO_NORTH);
        streets(ctx, 30);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, TraversalAxis.SOUTH_TO_NORTH));
        new DefenceNestStage().run(ctx);
        assertEquals(6, ctx.defensePosts.size());
        int[][] steps = {{0, -1}, {1, -1}, {-1, -1}};
        for (DefensePost post : ctx.defensePosts) {
            assertNotNull(post.restFacing, "the nest's guns are laid");
            int[] laid = null;
            for (int[] step : steps) {
                if (Math.abs(LayeredAppearance.facingDegrees(step[0], step[1]) - post.restFacing) < 0.01f) laid = step;
            }
            assertNotNull(laid, "nest at " + post.anchorX + "," + post.anchorY + " faces away from the attacker");
            assertTrue(PrecinctDefence.openArc(ctx, post.anchorX, post.anchorY, laid[0], laid[1], DefenceNestStage.ARC_FROM),
                    "nest at " + post.anchorX + "," + post.anchorY + " has a building across its arc");
        }
    }

    @Test
    void aGroundThatCannotHoldTheCountPlacesFewerRatherThanFailing() {
        GenContext ctx = field(declared(5000));
        new DefenceNestStage().run(ctx);
        assertTrue(ctx.defensePosts.size() > 0 && ctx.defensePosts.size() < 5000);
    }
}
