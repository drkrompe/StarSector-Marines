package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the port pocket lands. Asked of the placement directly, on a hand-built
 * partition, because the question is which district block it chooses and not
 * what a generated city looks like.
 */
class PortPocketPlacementTest {

    private static final int W = 80;
    private static final int H = 80;

    /**
     * A row of touching pad-sized leaves in one corner and a lone leaf in
     * another. The pocket belongs on the row.
     */
    @Test
    void thePocketLandsWhereTheConnectedLeavesAre() {
        List<BlockLeaf> leaves = new ArrayList<>();
        addRow(leaves, 44, 44, 4);   // four in a row, bottom-right region
        leaves.add(leaf(2, 2, 12, 12));  // one lonely leaf, top-left
        DistrictMap map = plainMap();

        ZoningOverlayStage.placePortPocket(map, partition(leaves), W, H);

        assertEquals(MapDistrictTheme.HARBOR_PORT, map.themeAt(46, 46),
                "the pocket should cover the connected row");
        assertTrue(map.themeAt(4, 4) != MapDistrictTheme.HARBOR_PORT,
                "the lone leaf's district should not have been chosen");
    }

    /**
     * The coast keeps its theme, so leaves inside it can never become port
     * candidates. Scoring a block on all its leaves promised a campus the
     * pocket could not deliver; the placement must prefer a smaller group it
     * can actually reserve.
     */
    @Test
    void aWaterfrontBlockIsNotChosenEvenWhenItHoldsMoreLeaves() {
        List<BlockLeaf> leaves = new ArrayList<>();
        addRow(leaves, 4, 4, 5);     // the bigger group, top-left
        addRow(leaves, 44, 44, 3);   // the smaller group, bottom-right
        DistrictMap map = plainMap();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                map.forceThemeAt(dx * map.districtCellWidth(), dy * map.districtCellHeight(),
                        MapDistrictTheme.WATERFRONT);
            }
        }

        ZoningOverlayStage.placePortPocket(map, partition(leaves), W, H);

        assertEquals(MapDistrictTheme.WATERFRONT, map.themeAt(4, 4),
                "the coast keeps its theme");
        assertEquals(MapDistrictTheme.HARBOR_PORT, map.themeAt(46, 46),
                "the pocket should fall back to the group it can actually reserve");
    }

    /** Leaves too small to hold a pad are not what a port campus is made of. */
    @Test
    void undersizedLeavesDoNotAttractThePocket() {
        List<BlockLeaf> leaves = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int x = 4 + i * 4;
            leaves.add(leaf(x, 4, x + 3, 7));   // 4x4, below the pad minimum
        }
        addRow(leaves, 44, 44, 2);
        DistrictMap map = plainMap();

        ZoningOverlayStage.placePortPocket(map, partition(leaves), W, H);

        assertEquals(MapDistrictTheme.HARBOR_PORT, map.themeAt(46, 46),
                "two pad-sized leaves beat six that cannot hold a pad");
    }

    private static DistrictMap plainMap() {
        DistrictMap map = new DistrictMap(W, H, new Random(1));
        for (int x = 0; x < W; x += map.districtCellWidth()) {
            for (int y = 0; y < H; y += map.districtCellHeight()) {
                map.forceThemeAt(x, y, MapDistrictTheme.RESIDENTIAL);
            }
        }
        return map;
    }

    /** {@code count} pad-sized leaves in a touching row starting at (x, y). */
    private static void addRow(List<BlockLeaf> leaves, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            int left = x + i * 7;
            leaves.add(leaf(left, y, left + 6, y + 6));
        }
    }

    private static BlockLeaf leaf(int l, int t, int r, int b) {
        return new BlockLeaf(l, t, r, b, false);
    }

    private static Bsp.Partition partition(List<BlockLeaf> leaves) {
        return new Bsp.Partition(leaves, new boolean[W][H], W, H);
    }
}
