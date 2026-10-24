package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import com.dillon.starsectormarines.battle.fire.FireSmoke;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * The fire field drawn over the bodies (`fire-nouns.md`, Look): a warm glow,
 * flames seen from straight above (beds of flame turned each to its own angle,
 * large ones for a crown, small tufts for grass) sized and paced by intensity,
 * a few embers over the hottest cells, and smoke off brush and crown fires.
 * Burnt ground under everything is {@link ScorchLayer}'s. Visits only the cells
 * burning in view, so a battle that never burns collects nothing.
 * {@code -Dbattle.render.fire=false} is the control for fire's whole look.
 */
public final class FireRenderSystem {

    public static final String PROPERTY = "battle.render.fire";
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** The glow's opacity under a burning cell. */
    private static final float GLOW_ALPHA = 0.34f;
    /** The glow disc's width in cells, and how much stronger its middle is drawn, since its edge fades to nothing. */
    private static final float GLOW_CELLS = 1.8f, GLOW_DISC_ALPHA = 1.2f;

    /** The smoke-and-fire sheet's columns and rows; the smoke frames are its upper two rows. */
    private static final int SHEET_COLS = 4, SHEET_ROWS = 4, SMOKE_FRAMES = 8;
    /** Seconds a smoke puff takes to rise and fade. */
    private static final float SMOKE_LIFE = 2.4f;
    /** Cell size in pixels below which a fire is drawn as glow and one tongue, no embers. */
    private static final float DETAIL_CELL_PX = 10f;

    /** The top-down flame sheet, a bed of flame (its source is in {@code art-source/fx/fire-topdown}). */
    static final String FLAME_SHEET_PATH = "graphics/fx/fire_topdown_a-flame-bed.png";
    /** The flame sheet's grid: cells 0-7 a bed's loop, 8-15 a grass tuft's. */
    private static final int FLAME_COLS = 4, FLAME_LOOP = 8, TUFT_FIRST = 8;
    /** How thick fire smoke is drawn, against a faint grey at 1: it sets a puff's size and opacity. */
    private static final float SMOKE_DENSITY = 3f;
    /** Smoke's grey, dark enough to stand off flame and creep. */
    private static final float SMOKE_GREY = 0.30f;
    /** A low fire's haze against a brush fire's smoke. */
    private static final float HAZE_ALPHA = 0.35f;
    /**
     * A cell's fire mass ({@link FireField#massAt}) at or below which it draws as a small fire, small flame tufts and
     * embers, and at or above which as a big one, full flame beds; between, it grows from the one into the other.
     */
    static final int SMALL_MASS = 22, BIG_MASS = 42;
    private static final float TOP_FPS = 7f, TURN_DEG_PER_SEC = 6f, FLAME_ALPHA_TOP = 0.9f, INTERIOR_ALPHA = 0.55f;

    private final BattleSprites sprites;
    private final RenderSystem flames = RenderSystem.of(RenderLayer.HAZARDS, this::collectFlames);
    private float clock;

    public FireRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    public static boolean enabled() { return ENABLED; }

    public RenderSystem flames() { return flames; }

    // ---- Flames -----------------------------------------------------------

    private void collectFlames(RenderContext ctx, DrawList out) {
        FireField field = ENABLED ? ctx.sim.fireFieldIfPresent() : null;
        if (field == null || field.burningCount() == 0) return;
        clock = Float.isFinite(ctx.animationTimeSeconds) ? ctx.animationTimeSeconds : clock + Math.max(0f, ctx.realDt);
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        boolean detail = cellPx >= DETAIL_CELL_PX;
        VisibleCellRect view = camera.visibleCells(2, field.width(), field.height());
        SpriteAPI sheet = sprites == null ? null : sprites.fireSheet();
        int framePxW = 0, framePxH = 0;
        if (sheet != null) {
            framePxW = (int) (sheet.getWidth() / SHEET_COLS);
            framePxH = (int) (sheet.getHeight() / SHEET_ROWS);
        }
        int w = field.width();
        // Glow first, under every flame of the frame: a soft disc a little wider than the cell, so a small fire's
        // ground glows round rather than in squares.
        SpriteAPI blob = sprites == null ? null : sprites.shadowBlobSprite();
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int x = idx % w, y = idx / w;
            if (x < view.minX() || x > view.maxX() || y < view.minY() || y > view.maxY()) continue;
            float heat = field.intensityAt(idx) / 255f;
            float a = GLOW_ALPHA * heat * ctx.alphaMult;
            if (blob != null) {
                float size = cellPx * GLOW_CELLS;
                out.addSprite(RenderLayer.HAZARDS, blob, camera.cellToScreenX(x + 0.5f), camera.cellToScreenY(y + 0.5f),
                        size, size, 0f, 1f, 0.42f, 0.10f, a * GLOW_DISC_ALPHA);
            } else {
                out.addSolidRect(RenderLayer.HAZARDS, camera.cellToScreenX(x), camera.cellToScreenY(y),
                        camera.cellToScreenX(x + 1f), camera.cellToScreenY(y + 1f), 1f, 0.42f, 0.10f, a);
            }
        }
        SpriteAPI flameSheet = sprites == null ? null : sprites.flameSheet();
        if (flameSheet != null) collectTopDown(ctx, out, field, flameSheet, view, cellPx, detail);
        if (!detail || sheet == null || framePxW <= 0) return;
        // Smoke last, over every flame, so a burning crown's screen reads as one.
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int x = idx % w, y = idx / w;
            if (x < view.minX() || x > view.maxX() || y < view.minY() || y > view.maxY()) continue;
            int mask = field.burnedMaskAt(idx);
            if (FireSmoke.smokes(mask)) {
                emitSmoke(out, camera, sheet, framePxW, framePxH, field, idx, x, y, cellPx, ctx.alphaMult, false);
            } else if (FireSmoke.hazes(mask) && (hash(idx, 0x4A2E) & 1) == 0) {
                // A low fire's haze: every other cell, thin and pale, so a burning field reads hazed, not screened.
                emitSmoke(out, camera, sheet, framePxW, framePxH, field, idx, x, y, cellPx, ctx.alphaMult, true);
            }
        }
    }

    /**
     * Flames from straight above. Each burning cell is a few flame beds, by its fuel: a grass fire a few small
     * flame islands, brush, creep and debris a bed, a building's walls and rooms two, a crown three large ones. Every bed
     * is turned to its own angle, starts its loop at its own frame and turns slowly as it burns; at play zoom and
     * closer it fades from one frame into the next, so the eight-frame loop never shows. Heat sets size and
     * strength, and a cell with fire on all four sides draws fewer, smaller and fainter beds.
     *
     * <p>How big the fire round a cell is ({@link FireField#massAt}) sets which flames it draws: a small fire, a
     * flamer's patch or a smouldering edge, is one small tuft and a scatter of embers; the large beds are a big
     * fire's, and a cell grows from the one into the other as more burns round it ({@link #bigness}).
     */
    private void collectTopDown(RenderContext ctx, DrawList out, FireField field, SpriteAPI sheet,
                                VisibleCellRect view, float cellPx, boolean detail) {
        BattleCamera camera = ctx.camera;
        int w = field.width();
        int framePx = (int) (sheet.getWidth() / FLAME_COLS);
        if (framePx <= 0) return;
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int x = idx % w, y = idx / w;
            if (x < view.minX() || x > view.maxX() || y < view.minY() || y > view.maxY()) continue;
            int intensity = field.intensityAt(idx);
            float heat = intensity / 255f;
            int mask = field.burnedMaskAt(idx);
            boolean crown = FireFuel.CROWN.in(mask);
            boolean building = FireFuel.STRUCTURE.in(mask) || FireFuel.FURNISHING.in(mask);
            boolean grass = !crown && !building && (mask & ~FireFuel.GRASS.bit) == 0;
            int beds = !detail ? 1 : crown ? 3 : building ? 2 : 1;
            // Inside a burn the flame thins and the ground shows: a fire's edge burns brightest.
            boolean interior = field.isBurning(x - 1, y) && field.isBurning(x + 1, y)
                    && field.isBurning(x, y - 1) && field.isBurning(x, y + 1);
            if (interior) beds = Math.max(1, beds - 1);
            float big = bigness(field.massAt(idx));
            boolean small = big < 0.5f;
            if (small) beds = 1;
            float scale = (crown ? 2.3f : building ? 2.0f : grass ? 1.9f : 1.8f) * (0.55f + 0.45f * big);
            int first = grass || small ? TUFT_FIRST : 0;
            for (int k = 0; k < beds; k++) {
                int h = hash(idx, 0x70D + k * 977);
                float spread = beds == 1 ? 0.25f : 0.6f;
                float jx = ((h & 0xFF) / 255f - 0.5f) * spread, jy = (((h >>> 8) & 0xFF) / 255f - 0.5f) * spread;
                float rate = TOP_FPS * (0.75f + 0.5f * (((h >>> 16) & 0xFF) / 255f));
                float turn = ((h >>> 24) & 1) == 0 ? TURN_DEG_PER_SEC : -TURN_DEG_PER_SEC;
                float angle = ((h >>> 4) & 0x3FF) * (360f / 1024f) + clock * turn;
                float t = clock * rate + ((h >>> 20) & 0xF);
                int frame = (int) t % FLAME_LOOP;
                float blend = detail ? t - (float) Math.floor(t) : 0f;
                float size = cellPx * scale * (0.7f + 0.4f * heat) * (beds > 1 ? 0.8f : 1f) * (interior ? 0.8f : 1f);
                float a = (0.6f + 0.4f * heat) * FLAME_ALPHA_TOP * (interior ? INTERIOR_ALPHA : 1f) * ctx.alphaMult;
                float cx = camera.cellToScreenX(x + 0.5f + jx), cy = camera.cellToScreenY(y + 0.5f + jy);
                emitFlame(out, sheet, framePx, first + frame, cx, cy, size, angle, a * (1f - blend));
                if (blend > 0f) emitFlame(out, sheet, framePx, first + (frame + 1) % FLAME_LOOP, cx, cy, size, angle, a * blend);
            }
            if (detail && (intensity > 200 || small)) emitEmber(out, camera, idx, 0xE1B, x, y, cellPx, ctx.alphaMult);
            if (detail && small) emitEmber(out, camera, idx, 0x5E1B, x, y, cellPx, ctx.alphaMult);
        }
    }

    private static void emitFlame(DrawList out, SpriteAPI sheet, int framePx, int cell, float cx, float cy,
                                  float size, float angle, float alpha) {
        if (alpha <= 0.004f) return;
        out.addSheetQuad(RenderLayer.HAZARDS, sheet, (cell % FLAME_COLS) * framePx, (cell / FLAME_COLS) * framePx,
                framePx, framePx, cx, cy, size, size, angle, 1f, 1f, 1f, alpha);
    }

    /** 0 for a small fire's cell, 1 for a big one's: its {@link FireField#massAt mass} between {@link #SMALL_MASS} and {@link #BIG_MASS}. */
    static float bigness(int mass) {
        return Math.max(0f, Math.min(1f, (mass - SMALL_MASS) / (float) (BIG_MASS - SMALL_MASS)));
    }

    /** A spark lifting off a hot cell. */
    private void emitEmber(DrawList out, BattleCamera camera, int idx, int salt, int x, int y, float cellPx,
                           float alphaMult) {
        int h = hash(idx, salt);
        float life = (clock * 0.9f + (h & 0xFF) / 255f) % 1f;
        float ex = x + 0.5f + (((h >>> 8) & 0xFF) / 255f - 0.5f) * 0.9f;
        float ey = y + 0.5f + (((h >>> 16) & 0xFF) / 255f - 0.5f) * 0.6f;
        float s = cellPx * 0.07f;
        float sx = camera.cellToScreenX(ex), sy = camera.cellToScreenY(ey) + life * cellPx * 1.2f;
        SpriteAPI blob = sprites == null ? null : sprites.shadowBlobSprite();
        if (blob != null) {
            // A glowing mote rather than a square: twice the size, since the disc fades out toward its edge.
            out.addAdditiveSprite(RenderLayer.HAZARDS, blob, sx, sy, s * 4f, s * 4f, 0f, 1f, 0.70f, 0.28f,
                    (1f - life) * alphaMult);
            return;
        }
        out.addSolidRect(RenderLayer.HAZARDS, sx - s, sy - s, sx + s, sy + s,
                1f, 0.75f, 0.30f, (1f - life) * alphaMult);
    }

    /**
     * A puff of dark smoke rising off a brush or crown fire and leaning
     * downwind: the picture of the screen {@link FireSmoke} holds there.
     */
    private void emitSmoke(DrawList out, BattleCamera camera, SpriteAPI sheet, int framePxW, int framePxH,
                           FireField field, int idx, int x, int y, float cellPx, float alphaMult, boolean haze) {
        int h = hash(idx, 0x5A0C);
        float life = (clock / SMOKE_LIFE + (h & 0xFF) / 255f) % 1f;
        int frame = (h >>> 8) % SMOKE_FRAMES;
        float drift = life * 1.2f;
        float px = x + 0.5f + field.windX() * field.windStrength() * drift;
        float py = y + 0.5f + field.windY() * field.windStrength() * drift;
        float size = cellPx * (1.1f + 1.3f * life) * (1f + 0.25f * (SMOKE_DENSITY - 1f));
        float cx = camera.cellToScreenX(px), cy = camera.cellToScreenY(py) + life * cellPx * 1.4f;
        float a = Math.min(0.9f, 0.6f * SMOKE_DENSITY) * (1f - life) * Math.min(1f, life * 5f);
        out.addSheetQuad(RenderLayer.HAZARDS, sheet,
                (frame % SHEET_COLS) * framePxW, (frame / SHEET_COLS) * framePxH,
                framePxW, framePxH, cx, cy, size, size,
                haze ? 0.62f : SMOKE_GREY, haze ? 0.60f : SMOKE_GREY - 0.02f, haze ? 0.57f : SMOKE_GREY - 0.04f,
                a * alphaMult * (haze ? HAZE_ALPHA : 1f));
    }

    /**
     * How dark a tree is drawn, 1 for untouched: a tree whose middle cell is
     * burning darkens toward char, and one standing (or felled) on burnt ground
     * is char. Trees only; everything else on the map keeps its own art.
     */
    static float treeShade(FireField field, Doodad d) {
        String id = d.definitionId();
        if (id == null || !id.startsWith("forest.")) return 1f;
        int cx = d.cellX + d.footprintCellsX / 2, cy = d.cellY + d.footprintCellsY / 2;
        if (cx < 0 || cy < 0 || cx >= field.width() || cy >= field.height()) return 1f;
        int idx = cy * field.width() + cx;
        byte s = field.stateAt(idx);
        if (s == FireField.BURNT) return CHAR_SHADE;
        if (s == FireField.BURNING) return 1f - (1f - CHAR_SHADE) * Math.min(1f, (field.intensityAt(idx) / 255f) * 0.6f + 0.2f);
        return 1f;
    }

    /** The brightness a burnt tree is drawn at. */
    static final float CHAR_SHADE = 0.22f;

    private static int hash(int idx, int salt) {
        int h = idx * 0x9E3779B1 ^ salt * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        return h ^ (h >>> 15);
    }
}
