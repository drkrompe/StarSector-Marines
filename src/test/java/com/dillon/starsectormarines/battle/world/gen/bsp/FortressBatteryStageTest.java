package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
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

        GenContext groundOnly = field(TargetProfile.NEUTRAL.withDefences(true, false, true));
        new FortressBatteryStage().run(groundOnly);
        assertTrue(groundOnly.defensePosts.isEmpty(), "ground defences and a shield are not heavy batteries");
    }

    @Test
    void aDeclaredBatteryStandsSealedOnOpenGroundWithAClearApron() {
        GenContext ctx = field(TargetProfile.NEUTRAL.withDefences(true, true, false));
        new FortressBatteryStage().run(ctx);

        assertEquals(FortressBatteryStage.batteryCount(W, H), ctx.defensePosts.size());
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
        GenContext a = field(TargetProfile.NEUTRAL.withDefences(true, true, false));
        GenContext b = field(TargetProfile.NEUTRAL.withDefences(true, true, false));
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
    void aSmallMapCarriesOneAndALargeOneNoMoreThanFour() {
        assertEquals(1, FortressBatteryStage.batteryCount(60, 40));
        assertEquals(4, FortressBatteryStage.batteryCount(560, 336));
    }
}
