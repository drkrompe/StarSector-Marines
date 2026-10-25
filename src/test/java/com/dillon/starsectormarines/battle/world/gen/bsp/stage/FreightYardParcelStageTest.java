package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreightYardParcelStageTest {

    /** Leaves on an all-road grid, each leaf's own cells cleared of road. */
    private static GenContext fixture(BlockLeaf... leaves) {
        int w = 64, h = 34;
        boolean[][] roads = new boolean[w][h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) roads[x][y] = true;
        for (BlockLeaf leaf : leaves) {
            for (int y = leaf.top; y <= leaf.bottom; y++) for (int x = leaf.left; x <= leaf.right; x++) roads[x][y] = false;
        }
        GenContext c = new GenContext(new NavigationGrid(w, h), new CellTopology(w, h), new Random(7), w, h, 7);
        c.put(BspKeys.PARTITION, new Bsp.Partition(new ArrayList<>(List.of(leaves)), roads, w, h));
        c.put(BspKeys.TRUNK_PLAN, new TrunkPlan.Plan(roads, List.of(), List.of(), null, w, h));
        return c;
    }

    private static BlockLeaf leaf(int left, int top, int right, int bottom, BlockKind kind) {
        BlockLeaf leaf = new BlockLeaf(left, top, right, bottom, false);
        leaf.kind = kind;
        return leaf;
    }

    @Test
    void twoParcelsTooNarrowForAnyYardAreJoinedAcrossTheirStreetForTheTwoBayYard() {
        GenContext c = fixture(leaf(2, 2, 13, 27, BlockKind.BUILDING_INDUSTRIAL),
                leaf(17, 2, 28, 27, BlockKind.INDUSTRIAL_YARD));
        new FreightYardParcelStage().run(c);
        Bsp.Partition p = c.get(BspKeys.PARTITION);
        assertEquals(1, p.leaves.size());
        BlockLeaf yard = p.leaves.get(0);
        assertEquals(BlockKind.FREIGHT_YARD, yard.kind);
        assertEquals(27, yard.width());
        assertFalse(p.roadCells[15][10], "the street between them is the yard's now");
        assertTrue(p.roadCells[15][1] && p.roadCells[29][10], "the ring round it stays street");
        assertEquals(new Random(7).nextLong(), c.rng.nextLong(), "planning draws nothing");
    }

    @Test
    void workingGroundIsTakenBeforeSpareGroundAndHomesNever() {
        BlockLeaf homes = leaf(2, 2, 25, 31, BlockKind.BUILDING_RESIDENTIAL);
        BlockLeaf shops = leaf(28, 2, 49, 31, BlockKind.BUILDING_COMMERCIAL);
        BlockLeaf works = leaf(52, 2, 61, 21, BlockKind.BUILDING_INDUSTRIAL);
        GenContext c = fixture(homes, shops, works);
        new FreightYardParcelStage().run(c);
        List<BlockLeaf> leaves = c.get(BspKeys.PARTITION).leaves;
        assertTrue(leaves.contains(homes) && homes.kind == BlockKind.BUILDING_RESIDENTIAL, "homes are never a yard");
        assertFalse(leaves.contains(works), "a works too narrow alone is joined to the shops beside it");
        assertTrue(leaves.stream().anyMatch(l -> l.kind == BlockKind.FREIGHT_YARD && l.width() == 34));

        BlockLeaf narrowWorks = leaf(52, 2, 61, 21, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf moreWorks = leaf(2, 2, 17, 21, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf bigShops = leaf(28, 2, 49, 31, BlockKind.BUILDING_COMMERCIAL);
        new FreightYardParcelStage().run(fixture(narrowWorks, moreWorks, bigShops));
        assertEquals(BlockKind.FREIGHT_YARD, moreWorks.kind, "a works that holds the standard yard wins");
        assertEquals(BlockKind.BUILDING_COMMERCIAL, bigShops.kind);
    }
}
