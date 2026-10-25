package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spine claim and the street rule ({@code set-piece-districts.md}) on one small block plan: a street four
 * wide along x, three leaves above it and two below, cross streets between them, and road all round.
 */
class SpineClaimTest {

    static final int W = 36, H = 28;
    static final SpineClaim.Spec SPEC = new SpineClaim.Spec(2, 3, 24, 7, 6, 5);

    final BlockLeaf a1 = new BlockLeaf(1, 3, 8, 11, false);
    final BlockLeaf a2 = new BlockLeaf(12, 3, 19, 11, false);
    final BlockLeaf a3 = new BlockLeaf(23, 3, 30, 11, false);
    final BlockLeaf b1 = new BlockLeaf(1, 16, 10, 24, false);
    final BlockLeaf b2 = new BlockLeaf(15, 16, 30, 24, false);
    final List<BlockLeaf> leaves = List.of(a1, a2, a3, b1, b2);

    static boolean[][] road(List<BlockLeaf> leaves) {
        boolean[][] road = new boolean[W][H];
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) road[x][y] = true;
        for (BlockLeaf leaf : leaves) {
            for (int x = leaf.left; x <= leaf.right; x++) for (int y = leaf.top; y <= leaf.bottom; y++) road[x][y] = false;
        }
        return road;
    }

    static boolean[][] copy(boolean[][] road) {
        boolean[][] out = new boolean[road.length][];
        for (int x = 0; x < road.length; x++) out[x] = road[x].clone();
        return out;
    }

    SpineClaim.Spine spine() {
        return SpineClaim.find(leaves, road(leaves), leaf -> true, leaf -> 1, SPEC);
    }

    @Test
    void claimsTheStreetWithEveryLeafFrontingIt() {
        SpineClaim.Spine spine = spine();
        assertNotNull(spine);
        assertTrue(spine.horizontal);
        assertEquals(1, spine.from);
        assertEquals(30, spine.to);
        assertEquals(12, spine.lo);
        assertEquals(15, spine.hi);
        assertEquals(List.of(a1, a2, a3), spine.near);
        assertEquals(List.of(b1, b2), spine.far);
        assertEquals(3, spine.gaps.size());
        SpineClaim.Gap wide = spine.gaps.get(2);
        assertFalse(wide.near());
        assertEquals(4, wide.width());
        assertTrue(spine.owns(13, 20), "the cross street between the two leaves below is the spine's");
        assertFalse(spine.owns(13, 26), "the back street is not");
        assertFalse(spine.owns(32, 13), "nor is the street beyond the last member");
    }

    @Test
    void aLeafThatMayNotJoinBreaksTheStretchItFronts() {
        assertNull(SpineClaim.find(leaves, road(leaves), leaf -> leaf != a2, leaf -> 1, SPEC));
    }

    @Test
    void standsOnlyInItsPocketOnBalance() {
        assertNull(SpineClaim.find(leaves, road(leaves), leaf -> true, leaf -> 0, SPEC));
        // One member deep in the pocket carries one outside it.
        assertNotNull(SpineClaim.find(leaves, road(leaves), leaf -> true,
                leaf -> leaf == b2 || leaf == a2 ? 2 : leaf == a1 ? 1 : 0, SPEC));
    }

    @Test
    void narrowingACrossStreetKeepsEveryWayIn() {
        SpineClaim.Spine spine = spine();
        boolean[][] before = road(leaves), after = copy(before);
        for (int y = 16; y <= 24; y++) after[14][y] = false;
        assertTrue(DistrictStreets.keepsEntries(spine, before, after));
    }

    @Test
    void closingACrossStreetLosesAWayIn() {
        SpineClaim.Spine spine = spine();
        boolean[][] before = road(leaves), after = copy(before);
        for (int x = 9; x <= 11; x++) for (int y = 3; y <= 11; y++) after[x][y] = false;
        assertFalse(DistrictStreets.keepsEntries(spine, before, after));
    }

    @Test
    void cuttingTheStreetPartsWaysInThatWereJoined() {
        SpineClaim.Spine spine = spine();
        boolean[][] before = road(leaves), after = copy(before);
        for (int y = 12; y <= 15; y++) after[17][y] = false;
        assertFalse(DistrictStreets.keepsEntries(spine, before, after));
    }
}
