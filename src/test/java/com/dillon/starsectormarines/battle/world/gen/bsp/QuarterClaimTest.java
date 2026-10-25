package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quarter claim ({@code set-piece-districts.md}) on small block plans with road everywhere no leaf stands:
 * two rows of leaves either side of a main cross street, with a cross street between neighbours in each row.
 */
class QuarterClaimTest {

    static final int W = 40, H = 40;
    static final QuarterClaim.Spec SPEC = new QuarterClaim.Spec(2, 3, 5, 7);

    static boolean[][] road(List<BlockLeaf> leaves) {
        boolean[][] road = new boolean[W][H];
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) road[x][y] = true;
        for (BlockLeaf leaf : leaves) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        return road;
    }

    static BlockLeaf leaf(int l, int t, int r, int b) { return new BlockLeaf(l, t, r, b, false); }

    final BlockLeaf a = leaf(3, 3, 12, 12), b = leaf(18, 3, 27, 12);
    final BlockLeaf c = leaf(3, 18, 12, 27), d = leaf(18, 18, 27, 27);

    @Test
    void claimsTwoRowsAndTheCrossStreetsBetweenThem() {
        List<BlockLeaf> leaves = List.of(a, b, c, d);
        QuarterClaim.Quarter q = QuarterClaim.find(leaves, road(leaves), null, leaf -> true, leaf -> 1, SPEC);
        assertNotNull(q);
        assertTrue(q.horizontal);
        assertEquals(List.of(List.of(a, b), List.of(c, d)), q.rows);
        assertArrayEquals(new int[]{3, 3, 27, 27}, q.bounds());
        assertEquals(new QuarterClaim.Street(3, 13, 27, 17), q.streets.get(0), "the main cross street, whole");
        assertEquals(new QuarterClaim.Street(13, 3, 17, 12), q.streets.get(1), "the first row's cross street");
        assertEquals(new QuarterClaim.Street(13, 18, 17, 27), q.streets.get(2), "the second row's");
        assertTrue(q.owns(15, 15) && q.owns(15, 5) && q.owns(5, 5));
        assertFalse(q.owns(2, 15), "the streets round the outside stay public");
    }

    @Test
    void rowsNeedNotLineUp() {
        BlockLeaf c1 = leaf(3, 18, 9, 27), c2 = leaf(13, 18, 19, 27), c3 = leaf(23, 18, 27, 27);
        List<BlockLeaf> leaves = List.of(a, b, c1, c2, c3);
        QuarterClaim.Quarter q = QuarterClaim.find(leaves, road(leaves), null, leaf -> true, leaf -> 1,
                new QuarterClaim.Spec(2, 3, 5, 5));
        assertNotNull(q);
        assertEquals(5, q.members().size(), "a row of two faces a row of three");
        assertEquals(4, q.streets.size());
    }

    @Test
    void findsAQuarterWhoseMainStreetRunsAlongY() {
        BlockLeaf a2 = leaf(3, 3, 12, 12), c2 = leaf(3, 18, 12, 27), b2 = leaf(17, 3, 27, 9), d2 = leaf(17, 13, 27, 27);
        // Down the middle the columns share a street; across, the rows do not line up.
        List<BlockLeaf> leaves = List.of(a2, b2, c2, d2);
        QuarterClaim.Quarter q = QuarterClaim.find(leaves, road(leaves), null, leaf -> true, leaf -> 1, SPEC);
        assertNotNull(q);
        assertFalse(q.horizontal);
        assertEquals(new QuarterClaim.Street(13, 3, 16, 27), q.streets.get(0));
    }

    @Test
    void refusesALeafThatDoesNotFitTheRow() {
        BlockLeaf shallow = leaf(18, 3, 27, 10);
        List<BlockLeaf> leaves = List.of(a, shallow, c, d);
        assertNull(QuarterClaim.find(leaves, road(leaves), null, leaf -> true, leaf -> 1, SPEC),
                "a member short of its row's depth leaves a street that is not the quarter's");
    }

    @Test
    void refusesIneligibleSmallOrUnpocketedMembers() {
        List<BlockLeaf> leaves = List.of(a, b, c, d);
        boolean[][] road = road(leaves);
        assertNull(QuarterClaim.find(leaves, road, null, leaf -> leaf != d, leaf -> 1, SPEC));
        assertNull(QuarterClaim.find(leaves, road, null, leaf -> true, leaf -> 1, new QuarterClaim.Spec(2, 3, 5, 11)));
        assertNull(QuarterClaim.find(leaves, road, null, leaf -> true, leaf -> leaf == a ? 3 : 0, SPEC),
                "three of four members outside the pocket");
        assertNotNull(QuarterClaim.find(leaves, road, null, leaf -> true, leaf -> leaf == d ? 0 : 2, SPEC),
                "one outside, on balance in");
    }

    @Test
    void neverOwnsATrunkOrAWideStreet() {
        List<BlockLeaf> leaves = List.of(a, b, c, d);
        boolean[][] road = road(leaves);
        boolean[][] trunk = new boolean[W][H];
        for (int x = 0; x < W; x++) for (int y = 13; y <= 17; y++) trunk[x][y] = true;
        assertNull(QuarterClaim.find(leaves, road, trunk, leaf -> true, leaf -> 1, SPEC));
        assertNull(QuarterClaim.find(leaves, road, null, leaf -> true, leaf -> 1, new QuarterClaim.Spec(2, 3, 4, 7)));
    }

    @Test
    void prefersTheDeeperPocketThenMoreMembers() {
        BlockLeaf e = leaf(32, 3, 38, 12), f = leaf(32, 18, 38, 27);
        // Cross streets of four between b and e, and d and f, so a quarter of six stands over all of them.
        List<BlockLeaf> leaves = List.of(a, b, c, d, e, f);
        boolean[][] road = road(leaves);
        QuarterClaim.Spec spec = new QuarterClaim.Spec(2, 3, 5, 5);
        QuarterClaim.Quarter even = QuarterClaim.find(leaves, road, null, leaf -> true,
                leaf -> leaf == e || leaf == f ? 0 : 2, spec);
        assertEquals(6, even.members().size(), "as deep in its pocket as the four without e and f, and larger");
        QuarterClaim.Quarter two = QuarterClaim.find(leaves, road, null, leaf -> true, leaf -> 1,
                new QuarterClaim.Spec(2, 2, 5, 5));
        assertEquals(4, two.members().size(), "two to a row at most");
        assertEquals(List.of(a, b), two.rows.get(0), "of two as deep, the larger ground");
    }
}
