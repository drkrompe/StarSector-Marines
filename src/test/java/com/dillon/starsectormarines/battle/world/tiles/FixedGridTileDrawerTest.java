package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FixedGridTileDrawerTest {

    @Test
    void drawSpanUsesTheWholeContiguousSourceFootprint() {
        FixedGridTileDrawer drawer = new FixedGridTileDrawer(32);
        float[] destination = new float[5];
        int[] source = new int[4];
        TileSink sink = (srcX, srcY, srcW, srcH, dstCx, dstCy, dstW, dstH, alpha) -> {
            source[0] = srcX;
            source[1] = srcY;
            source[2] = srcW;
            source[3] = srcH;
            destination[0] = dstCx;
            destination[1] = dstCy;
            destination[2] = dstW;
            destination[3] = dstH;
            destination[4] = alpha;
        };

        drawer.drawSpan(sink, new TileManifest.TileFrame(2, 3),
                2, 1, 96f, 48f, 64f, 32f, 0.75f,
                FixedGridTileDrawer.OVERLAY_INSET_PX);

        assertArrayEquals(new int[]{64, 96, 64, 32}, source);
        assertArrayEquals(new float[]{96f, 48f, 64f, 32f, 0.75f}, destination);
    }
}
