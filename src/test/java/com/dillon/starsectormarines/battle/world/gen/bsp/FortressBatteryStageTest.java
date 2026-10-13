package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
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

/**
 * The stage places batteries when a mission declares them and not otherwise. Run on a bare field, not a generated
 * map: the question is about the stage's rule, and a generator would only add inputs.
 */
class FortressBatteryStageTest {

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

    @Test
    void nothingIsPlacedUnlessTheMissionDeclaresBatteries() {
        GenContext none = field(null);
        new FortressBatteryStage().run(none);
        assertTrue(none.defensePosts.isEmpty());

        GenContext marketOnly = field(TargetProfile.NEUTRAL.withDefences(true, true, true));
        new FortressBatteryStage().run(marketOnly);
        assertTrue(marketOnly.defensePosts.isEmpty(), "the market's signals are inputs, not a declaration");

        GenContext shieldOnly = field(TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(0, true)));
        new FortressBatteryStage().run(shieldOnly);
        assertTrue(shieldOnly.defensePosts.isEmpty(), "a declared shield is not a battery");
    }

    @Test
    void aDeclaredBatteryStandsSealedOnOpenGroundWithAClearApron() {
        GenContext ctx = field(declared(3));
        new FortressBatteryStage().run(ctx);

        assertEquals(3, ctx.defensePosts.size(), "exactly the declared count");
        StructureDef def = TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID);
        for (DefensePost post : ctx.defensePosts) {
            assertEquals(def.id, post.turrets.get(0).structureId);
            int x0 = StructureFootprint.minX(def, post.anchorX);
            int y0 = StructureFootprint.minY(def, post.anchorY);
            for (int dy = -1; dy <= def.footprintCellsY; dy++) {
                for (int dx = -1; dx <= def.footprintCellsX; dx++) {
                    boolean inside = dx >= 0 && dy >= 0 && dx < def.footprintCellsX && dy < def.footprintCellsY;
                    assertEquals(!inside, ctx.grid.isWalkable(x0 + dx, y0 + dy),
                            "cell " + (x0 + dx) + "," + (y0 + dy));
                }
            }
        }
    }

    @Test
    void placementIsDeterministicAndKeepsBatteriesApart() {
        GenContext a = field(declared(3));
        GenContext b = field(declared(3));
        new FortressBatteryStage().run(a);
        new FortressBatteryStage().run(b);
        assertEquals(a.defensePosts.size(), b.defensePosts.size());
        for (int i = 0; i < a.defensePosts.size(); i++) {
            assertEquals(a.defensePosts.get(i).anchorX, b.defensePosts.get(i).anchorX);
            assertEquals(a.defensePosts.get(i).anchorY, b.defensePosts.get(i).anchorY);
        }
        for (int i = 0; i < a.defensePosts.size(); i++) {
            for (int j = i + 1; j < a.defensePosts.size(); j++) {
                double d = Math.hypot(a.defensePosts.get(i).anchorX - a.defensePosts.get(j).anchorX,
                        a.defensePosts.get(i).anchorY - a.defensePosts.get(j).anchorY);
                assertFalse(d < 40, "two batteries " + d + " cells apart");
            }
        }
    }

    @Test
    void theDeclaredCountIsHonouredWhateverTheMapSize() {
        GenContext one = field(declared(1));
        new FortressBatteryStage().run(one);
        assertEquals(1, one.defensePosts.size());
    }

    private static TargetProfile declared(int batteries) {
        return TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(batteries, false));
    }
}
