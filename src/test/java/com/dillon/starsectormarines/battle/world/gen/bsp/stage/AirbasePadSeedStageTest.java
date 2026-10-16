package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The airbase seed promotes exactly one block: the largest ordinary one that can
 * hold the smallest lot. Asked of hand-cut leaves, since the rule is about the
 * leaves and not about a city; that a whole generated city then ends up with one
 * whole airbase is {@code CityLandingSiteEvidence}.
 */
class AirbasePadSeedStageTest {

    private static BlockLeaf leaf(int left, int top, int width, int height, BlockKind kind) {
        BlockLeaf leaf = new BlockLeaf(left, top, left + width - 1, top + height - 1, false);
        leaf.kind = kind;
        return leaf;
    }

    private static List<BlockLeaf> run(BlockLeaf... leaves) {
        List<BlockLeaf> list = new ArrayList<>(List.of(leaves));
        GenContext ctx = new GenContext(new NavigationGrid(80, 80), new CellTopology(80, 80),
                new Random(1L), 80, 80, 1L);
        ctx.put(BspKeys.PARTITION, new Bsp.Partition(list, new boolean[80][80], 80, 80));
        new AirbasePadSeedStage().run(ctx);
        return list;
    }

    private static long compounds(List<BlockLeaf> leaves) {
        return leaves.stream().filter(l -> l.kind == BlockKind.AIRBASE_COMPOUND).count();
    }

    @Test
    void theLargestBlockThatCanHoldALotIsPromotedAndOnlyThat() {
        BlockLeaf small = leaf(0, 0, 16, 14, BlockKind.BUILDING_INDUSTRIAL);
        BlockLeaf large = leaf(20, 0, 20, 18, BlockKind.BUILDING_COMMERCIAL);
        BlockLeaf medium = leaf(45, 0, 18, 16, BlockKind.BUILDING_RESIDENTIAL);

        List<BlockLeaf> leaves = run(small, large, medium);

        assertEquals(1, compounds(leaves), "a landmark appears once");
        assertEquals(BlockKind.AIRBASE_COMPOUND, large.kind);
    }

    @Test
    void aBlockTooSmallForTheSmallestLotIsNeverPromoted() {
        BlockLeaf tiny = leaf(0, 0, 8, 8, BlockKind.BUILDING_COMMERCIAL);
        BlockLeaf narrow = leaf(10, 0, 30, 5, BlockKind.BUILDING_COMMERCIAL);

        assertEquals(0, compounds(run(tiny, narrow)));
    }

    @Test
    void compoundsAndNaturalGroundAreNotCityBlocksToReplace() {
        BlockLeaf base = leaf(0, 0, 30, 30, BlockKind.MILITARY_BASE);
        BlockLeaf park = leaf(35, 0, 30, 30, BlockKind.PARK);
        BlockLeaf ordinary = leaf(0, 35, 16, 14, BlockKind.BUILDING_CIVIC);

        run(base, park, ordinary);

        assertEquals(BlockKind.MILITARY_BASE, base.kind);
        assertEquals(BlockKind.PARK, park.kind);
        assertEquals(BlockKind.AIRBASE_COMPOUND, ordinary.kind);
    }

    @Test
    void aCityThatAlreadyHasAnAirbaseGetsNoSecondSeed() {
        BlockLeaf existing = leaf(0, 0, 16, 14, BlockKind.AIRBASE_PAD);
        BlockLeaf candidate = leaf(20, 0, 20, 18, BlockKind.BUILDING_COMMERCIAL);

        run(existing, candidate);

        assertEquals(BlockKind.BUILDING_COMMERCIAL, candidate.kind);
    }
}
