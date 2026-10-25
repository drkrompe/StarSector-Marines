package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownBlockStageTest {

    private static final int W = 50, H = 30;

    @Test
    void twoTownLotsFacingAcrossOneMinorStreetBecomeOneBlock() {
        BlockLeaf home = new BlockLeaf(2, 2, 9, 10, false), shop = new BlockLeaf(13, 2, 20, 10, false);
        home.kind = BlockKind.BUILDING_RESIDENTIAL;
        shop.kind = BlockKind.BUILDING_COMMERCIAL;
        Bsp.Partition p = partition(home, shop);
        assertEquals(1, TownBlockStage.assemble(p, plan(p), leaf -> true));
        assertEquals(1, p.leaves.size());
        BlockLeaf block = p.leaves.get(0);
        assertEquals(BlockKind.BUILDING_COMMERCIAL, block.kind, "a shop lot and a home lot the same size make shops");
        assertEquals(2, block.left);
        assertEquals(20, block.right);
        for (int x = 10; x <= 12; x++) assertFalse(p.roadCells[x][5], "the street between them is still street");
        // The ring round the block is untouched, so every neighbour keeps its frontage.
        for (int x = 1; x <= 21; x++) assertTrue(p.roadCells[x][1] && p.roadCells[x][11]);
    }

    @Test
    void lotsThatDoNotShareTheirWholeSideKeepTheStreet() {
        // Offset lots: the street between them runs on past one of them, so removing it would leave a dead end.
        Bsp.Partition p = partition(new BlockLeaf(2, 2, 9, 10, false), new BlockLeaf(13, 4, 20, 12, false));
        assertEquals(0, TownBlockStage.assemble(p, plan(p), leaf -> true));
        assertEquals(2, p.leaves.size());
    }

    @Test
    void aTrunkBetweenTwoLotsIsNeverJoinedAcross() {
        Bsp.Partition p = partition(new BlockLeaf(2, 2, 9, 10, false), new BlockLeaf(13, 2, 20, 10, false));
        TrunkPlan.Plan plan = new TrunkPlan.Plan(p.roadCells, List.of(),
                List.of(new TrunkPlan.TrunkSegment(10, 0, 12, H - 1, TrunkKind.PRIMARY, false)), null, W, H);
        assertEquals(0, TownBlockStage.assemble(p, plan, leaf -> true));
    }

    @Test
    void onlyTownLotsJoinAndBlocksStopAtTheirSizeLimit() {
        Bsp.Partition p = partition(new BlockLeaf(2, 2, 9, 10, false), new BlockLeaf(13, 2, 20, 10, false));
        assertEquals(0, TownBlockStage.assemble(p, plan(p), leaf -> leaf.left == 2));
        // Three 12-wide lots in a row: the first two join to 27, a third would make 42 and stays apart.
        Bsp.Partition row = partition(new BlockLeaf(2, 2, 13, 10, false), new BlockLeaf(17, 2, 28, 10, false),
                new BlockLeaf(32, 2, 43, 10, false));
        assertEquals(1, TownBlockStage.assemble(row, plan(row), leaf -> true));
        for (BlockLeaf leaf : row.leaves) assertTrue(leaf.width() <= TownBlockStage.LONG_MAX);
    }

    /** A map that is all street but for the given lots. */
    private static Bsp.Partition partition(BlockLeaf... lots) {
        boolean[][] road = new boolean[W][H];
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) road[x][y] = true;
        List<BlockLeaf> leaves = new ArrayList<>();
        for (BlockLeaf lot : lots) {
            leaves.add(lot);
            for (int x = lot.left; x <= lot.right; x++) for (int y = lot.top; y <= lot.bottom; y++) road[x][y] = false;
        }
        return new Bsp.Partition(leaves, road, W, H);
    }

    private static TrunkPlan.Plan plan(Bsp.Partition p) {
        return new TrunkPlan.Plan(p.roadCells, List.of(), List.of(), null, W, H);
    }
}
