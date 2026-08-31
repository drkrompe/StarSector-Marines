package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The stage is asked directly, on a handful of leaves. What it decides is a
 * property of the partition, so nothing here generates a world.
 */
class SettlementLandingLinkStageTest {

    private static final int W = 40;
    private static final int H = 40;

    @Test
    void promotesTheLargestOrdinaryBlockWhenNothingCanBeLandedOn() {
        BlockLeaf small = leaf(1, 1, 8, 8, BlockKind.BUILDING_RESIDENTIAL);
        BlockLeaf big = leaf(10, 10, 25, 25, BlockKind.BUILDING_COMMERCIAL);
        run(small, big);
        assertEquals(BlockKind.LANDING_ZONE, big.kind, "the largest block should carry the lifeline");
        assertEquals(BlockKind.BUILDING_RESIDENTIAL, small.kind, "only one pad is needed");
    }

    @Test
    void leavesTheSettlementAloneWhenItAlreadyHasSomewhereToLand() {
        BlockLeaf pad = leaf(1, 1, 8, 8, BlockKind.LANDING_ZONE);
        BlockLeaf big = leaf(10, 10, 30, 30, BlockKind.BUILDING_COMMERCIAL);
        run(pad, big);
        assertEquals(BlockKind.BUILDING_COMMERCIAL, big.kind, "a second pad is not a link, it is clutter");
    }

    /**
     * A landing zone below the filler's minimum publishes no pad at all, so a
     * leaf merely carrying the label does not count as a link.
     */
    @Test
    void aLandingZoneTooSmallToPublishAPadIsNotALink() {
        BlockLeaf token = leaf(1, 1, 4, 4, BlockKind.LANDING_ZONE);
        BlockLeaf big = leaf(10, 10, 25, 25, BlockKind.BUILDING_COMMERCIAL);
        run(token, big);
        assertEquals(BlockKind.LANDING_ZONE, big.kind, "a 4x4 zone publishes nothing and must not satisfy the link");
    }

    @Test
    void anAirbaseAlreadyCountsAsTheLink() {
        BlockLeaf airbase = leaf(1, 1, 20, 20, BlockKind.AIRBASE_PAD);
        BlockLeaf other = leaf(22, 22, 35, 35, BlockKind.BUILDING_COMMERCIAL);
        run(airbase, other);
        assertEquals(BlockKind.BUILDING_COMMERCIAL, other.kind);
    }

    /** Compound seeds own their neighbours and natural ground is not city; neither may be paved. */
    @Test
    void refusesToPaveACompoundSeedOrOpenGround() {
        BlockLeaf compound = leaf(1, 1, 30, 30, BlockKind.MILITARY_BASE);
        BlockLeaf wild = leaf(31, 1, 39, 30, BlockKind.NATURE_GRASSLAND);
        run(compound, wild);
        assertSame(BlockKind.MILITARY_BASE, compound.kind);
        assertSame(BlockKind.NATURE_GRASSLAND, wild.kind);
    }

    @Test
    void refusesAnyBlockTooSmallToPublishAPad() {
        BlockLeaf tiny = leaf(1, 1, 4, 4, BlockKind.BUILDING_COMMERCIAL);
        run(tiny);
        assertEquals(BlockKind.BUILDING_COMMERCIAL, tiny.kind, "promoting a 4x4 would look linked and not be");
    }

    private static BlockLeaf leaf(int l, int t, int r, int b, BlockKind kind) {
        BlockLeaf leaf = new BlockLeaf(l, t, r, b, false);
        leaf.kind = kind;
        return leaf;
    }

    private static void run(BlockLeaf... leaves) {
        GenContext ctx = new GenContext(new NavigationGrid(W, H), new CellTopology(W, H),
                new Random(1), W, H, 1L);
        ctx.put(BspKeys.PARTITION,
                new Bsp.Partition(List.of(leaves), new boolean[W][H], W, H));
        new SettlementLandingLinkStage().run(ctx);
    }
}
