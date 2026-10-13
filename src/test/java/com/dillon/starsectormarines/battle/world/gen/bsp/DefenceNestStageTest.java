package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void exactlyTheDeclaredCountStandsAlternatingFlakAndMachineGun() {
        GenContext ctx = field(declared(7));
        new DefenceNestStage().run(ctx);

        assertEquals(7, ctx.defensePosts.size());
        for (int i = 0; i < ctx.defensePosts.size(); i++) {
            DefensePost post = ctx.defensePosts.get(i);
            assertEquals(1, post.turrets.size());
            String expected = i % 2 == 0
                    ? TurretCatalogRegistry.DUAL_FLAK_STRUCTURE_ID : TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID;
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

    @Test
    void aGroundThatCannotHoldTheCountPlacesFewerRatherThanFailing() {
        GenContext ctx = field(declared(5000));
        new DefenceNestStage().run(ctx);
        assertTrue(ctx.defensePosts.size() > 0 && ctx.defensePosts.size() < 5000);
    }
}
