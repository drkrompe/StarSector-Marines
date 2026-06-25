package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Renders one {@link TileDef} through a {@link TileSink}. Owns the
 * frame-index lookup and the ground-vs-overlay edge-inset rule so both
 * the preview test and the in-game renderer execute the same source-rect
 * computation — only the pixel-push backend behind the sink differs.
 *
 * <p>Edge inset is applied to {@link TileLayer#GROUND} tiles only.
 * Overlay tiles (plants, rocks — the doodad-style layer) pass through
 * with their full sliced bbox because cropping a standalone sprite's
 * edge would visibly chop the art. This matches the rule the in-game
 * urban/floors/water renderer uses (ground autotiles inset; doodads /
 * DOOR_OPEN at inset=0).
 *
 * <p>The drawer doesn't know what device units the sink uses (screen
 * pixels in-game, image pixels in tests). It only forwards source-rect
 * math and lets the caller decide where in the destination to place each
 * tile.
 */
public final class SlicedTileDrawer {

    /**
     * Default inset applied to ground tiles. 2px confirmed via visual A/B
     * on the nature sheet — eliminates the bilinear-sampler seam without
     * cropping perceptible content out of the source frame. Bump only if a
     * re-export reintroduces the halo; drop only to verify the artifact.
     */
    public static final int DEFAULT_GROUND_INSET_PX = 2;

    private final SpriteSheetFrames frames;
    private final int groundInsetPx;

    public SlicedTileDrawer(SpriteSheetFrames frames) {
        this(frames, DEFAULT_GROUND_INSET_PX);
    }

    public SlicedTileDrawer(SpriteSheetFrames frames, int groundInsetPx) {
        this.frames = frames;
        this.groundInsetPx = groundInsetPx;
    }

    /**
     * Draw {@code tile} centered at {@code (dstCx, dstCy)} sized
     * {@code (dstW, dstH)}. No-op if {@code tile} is null or its frame
     * index falls outside the slicer's detected range — keeps callers
     * from having to guard for a sheet/registry drift.
     */
    public void draw(TileSink sink, TileDef tile,
                     float dstCx, float dstCy, float dstW, float dstH,
                     float alphaMult) {
        if (sink == null || tile == null) return;
        int idx = tile.frame;
        if (idx < 0 || idx >= frames.frames.length) return;
        SpriteSheetFrames.Frame f = frames.frames[idx];
        int inset = tile.isGround() ? groundInsetPx : 0;
        int srcX = f.x + inset;
        int srcY = f.y + inset;
        int srcW = Math.max(1, f.w - 2 * inset);
        int srcH = Math.max(1, f.h - 2 * inset);
        sink.drawSlice(srcX, srcY, srcW, srcH, dstCx, dstCy, dstW, dstH, alphaMult);
    }

    public SpriteSheetFrames frames() { return frames; }
    public int groundInsetPx() { return groundInsetPx; }
}
