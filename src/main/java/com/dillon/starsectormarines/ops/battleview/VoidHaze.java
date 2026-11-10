package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Air lying low in a natural drop (`voids.md`, natural styles): a glacial crevasse's cold mist, a canyon's dust.
 * Soft puffs from vanilla's fog sheet on a grid drifting with the wind, a few metres over the floor, thick where a
 * slow noise says so, standing only over the floor and never under a bridge's deck. Drawn in the drop's painter's
 * order, composited, right after the floor; nothing of it stands over the ground tiles, where a composited piece
 * would thin the ground target's alpha and read as shade.
 */
final class VoidHaze {

    /** Vanilla's fog sheet, four puffs across and down, as the city drop's fog and the lava rift's smoke. */
    static final String SHEET = MoltenVoidRenderSystem.SMOKE_SHEET;
    static final int SHEET_CELLS = MoltenVoidRenderSystem.SMOKE_SHEET_CELLS;

    private VoidHaze() { }

    /** Field cells over the floor and off every bridge: where haze may stand. */
    static boolean[] overFloor(NaturalVoidField field) {
        int n = field.steps();
        boolean[] over = new boolean[field.w * field.h];
        for (int y = 0; y < field.h; y++) {
            for (int x = 0; x < field.w; x++) {
                int i = y * field.w + x;
                over[i] = field.open[i] && !field.bridge[i] && field.sample(field.reach[n], x + .5f, y + .5f) > 0f;
            }
        }
        return over;
    }

    /**
     * A deck of haze {@code above} metres over the floor of {@code drop}'s field.
     *
     * @param over      {@link #overFloor} of the field
     * @param puffCells a puff's size in cells
     * @param thick     a puff's alpha where the haze is thickest
     * @param windX     the wind's bearing, which the deck drifts along at {@code drift} cells a second
     */
    static void draw(NaturalVoidRenderSystem drop, BattleSprites sprites, boolean[] over, float above,
                     float puffCells, float thick, float windX, float windY, float drift, float r, float g, float b,
                     float time) {
        ShuttleSpriteCache sheet = sprites.spriteByPath(SHEET);
        if (sheet == null || sheet.pxW <= 0 || thick <= 0f) return;
        NaturalVoidField field = drop.field();
        BattleCamera cam = drop.camera();
        int n = field.steps();
        // The grid reaches as far as the deepest of the deck would show.
        float s = drop.scale(field.depth[n] * field.mostGain - above);
        float cellPx = cam.cellPxSize();
        float step = puffCells * .55f;
        float dx = windX * drift * time, dy = windY * drift * time;
        float ccx = cam.panCellX() + NaturalVoidRenderSystem.MARGIN, ccy = cam.panCellY() + NaturalVoidRenderSystem.MARGIN;
        float half = cam.vpW() / cellPx / s * .5f + puffCells, halfY = cam.vpH() / cellPx / s * .5f + puffCells;
        int i0 = (int) Math.floor((ccx - half - dx) / step), i1 = (int) Math.ceil((ccx + half - dx) / step);
        int j0 = (int) Math.floor((ccy - halfY - dy) / step), j1 = (int) Math.ceil((ccy + halfY - dy) / step);
        if ((long) (i1 - i0) * (j1 - j0) > 8000) return;
        int cells = SHEET_CELLS, cw = sheet.pxW / cells, ch = sheet.pxH / cells;
        int seed = drop.seed() * 17;
        float alpha = drop.alpha();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int hsh = hash(i, j, seed);
                float x = i * step + dx + ((hsh & 255) / 255f - .5f) * step;
                float y = j * step + dy + (((hsh >> 8) & 255) / 255f - .5f) * step;
                int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
                if (ix < 0 || iy < 0 || ix >= field.w || iy >= field.h || !over[iy * field.w + ix]) continue;
                if (field.nearBridge(x, y, puffCells * .75f)) continue;
                float dense = .5f + .5f * NaturalVoidField.Noise.fbm(i * .19f, j * .19f, seed);
                if (dense < .34f) continue;
                float ps = drop.scale(field.stepDepth(n, x, y) - above);
                float size = puffCells * (.85f + ((hsh >> 16) & 255) / 255f * .6f) * cellPx * ps;
                float a = thick * Math.min(1f, (dense - .34f) / .3f) * alpha;
                int cell = (hsh >>> 24) % (cells * cells);
                drop.drawList().addImageRegion(RenderLayer.GROUND, sheet.sprite, sheet.pxW, sheet.pxH,
                        cell % cells * cw, cell / cells * ch, cw, ch, drop.sx(x, s), drop.sy(y, s), size, size,
                        (hsh >> 4 & 1023) * .35f + time * (hsh % 2 == 0 ? 1f : -1f), r, g, b, a);
            }
        }
    }

    static int hash(int i, int j, int k) {
        int h = i * 374761393 + j * 668265263 + k * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        return h ^ (h >>> 16);
    }
}
