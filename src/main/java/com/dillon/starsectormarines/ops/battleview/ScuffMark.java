package com.dillon.starsectormarines.ops.battleview;

/**
 * Lays the marks {@link VehicleScuffs#place} hands over onto one drawn image ({@code body-scuffs.md}): a hull
 * frame, a turret or a mech part, centred on a screen point and turned with it. Source pixels map to screen pixels
 * by the image's own scales; each mark is one cell of the scuff sheet, turned by its own spin on top of the image's
 * angle. Reused: {@link #begin} aims it at the next image.
 */
final class ScuffMark implements VehicleScuffs.Sink {

    DrawList out;
    private RenderLayer layer;
    private BattleSprites sprites;
    private float centreX;
    private float centreY;
    private int imageW;
    private int imageH;
    private float sx;
    private float sy;
    private float angleDeg;
    private float cos;
    private float sin;
    private float sizePx;
    private float alpha;

    /**
     * Aims at an {@code imageW x imageH} image drawn centred on screen point {@code (centreX, centreY)}, a source
     * pixel {@code sx} by {@code sy} screen pixels, turned {@code angleDeg}; each mark {@code sizePx} across.
     */
    void begin(DrawList out, RenderLayer layer, BattleSprites sprites, float centreX, float centreY,
               int imageW, int imageH, float sx, float sy, float angleDeg, float sizePx, float alpha) {
        this.out = out;
        this.layer = layer;
        this.sprites = sprites;
        this.centreX = centreX;
        this.centreY = centreY;
        this.imageW = imageW;
        this.imageH = imageH;
        this.sx = sx;
        this.sy = sy;
        this.angleDeg = angleDeg;
        double rad = Math.toRadians(angleDeg);
        this.cos = (float) Math.cos(rad);
        this.sin = (float) Math.sin(rad);
        this.sizePx = sizePx;
        this.alpha = alpha;
    }

    @Override
    public void mark(float x, float y, int kind, int variant, float spinDegrees, float strength) {
        ShuttleSpriteCache sheet = sprites.spriteByPath(VehicleScuffs.SHEET);
        if (sheet == null || sheet.pxW <= 0) return;
        float lx = (x - imageW * 0.5f) * sx;
        float ly = (imageH * 0.5f - y) * sy;
        out.addImageRegion(layer, sheet.sprite, sheet.pxW, sheet.pxH,
                variant * VehicleScuffs.SHEET_CELL, kind * VehicleScuffs.SHEET_CELL,
                VehicleScuffs.SHEET_CELL, VehicleScuffs.SHEET_CELL,
                centreX + lx * cos - ly * sin, centreY + lx * sin + ly * cos, sizePx, sizePx,
                angleDeg + spinDegrees, 1f, 1f, 1f, strength * alpha);
    }
}
