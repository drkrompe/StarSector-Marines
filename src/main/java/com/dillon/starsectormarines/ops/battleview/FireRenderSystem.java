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
 * restrained flames from the smoke-and-fire sheet sized and paced by intensity,
 * a few embers over the hottest cells, and smoke off brush and crown fires.
 * Burnt ground under everything is {@link ScorchLayer}'s. Visits only the cells
 * burning in view, so a battle that never burns collects nothing.
 * {@code -Dbattle.render.fire=false} is the control for fire's whole look.
 */
public final class FireRenderSystem {

    public static final String PROPERTY = "battle.render.fire";
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Flame tongues' size and opacity against a full sheet frame, and the glow under them. */
    private static final float FLAME_SIZE = 0.62f, FLAME_ALPHA = 0.75f, GLOW_ALPHA = 0.34f;

    /** Sheet columns and rows; the smoke frames are the upper two rows, the fire frames the lower two. */
    private static final int SHEET_COLS = 4, SHEET_ROWS = 4, FIRE_FIRST_ROW = 2, FIRE_FRAMES = 8, SMOKE_FRAMES = 8;
    /** Seconds a smoke puff takes to rise and fade. */
    private static final float SMOKE_LIFE = 2.4f;
    private static final float FLAME_FPS = 10f;
    /** Cell size in pixels below which a fire is drawn as glow and one tongue, no embers. */
    private static final float DETAIL_CELL_PX = 10f;

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
        // Glow first, under every flame of the frame.
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int x = idx % w, y = idx / w;
            if (x < view.minX() || x > view.maxX() || y < view.minY() || y > view.maxY()) continue;
            float heat = field.intensityAt(idx) / 255f;
            out.addSolidRect(RenderLayer.HAZARDS, camera.cellToScreenX(x), camera.cellToScreenY(y),
                    camera.cellToScreenX(x + 1f), camera.cellToScreenY(y + 1f),
                    1f, 0.42f, 0.10f, GLOW_ALPHA * heat * ctx.alphaMult);
        }
        if (sheet == null || framePxW <= 0) return;
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int x = idx % w, y = idx / w;
            if (x < view.minX() || x > view.maxX() || y < view.minY() || y > view.maxY()) continue;
            int intensity = field.intensityAt(idx);
            float heat = intensity / 255f;
            int mask = field.burnedMaskAt(idx);
            boolean crown = FireFuel.CROWN.in(mask);
            int tongues = !detail ? 1 : crown ? 3 : 2;
            for (int k = 0; k < tongues; k++) {
                int h = hash(idx, 0xF1A + k * 977);
                float jx = ((h & 0xFF) / 255f - 0.5f) * 0.7f, jy = (((h >>> 8) & 0xFF) / 255f - 0.5f) * 0.7f;
                float rate = FLAME_FPS * (0.8f + 0.4f * (((h >>> 16) & 0xFF) / 255f));
                int frame = (int) (clock * rate + (h >>> 24)) % FIRE_FRAMES;
                // Flame frames run big to small: start each tongue's loop at its fullest and let heat pick the size.
                float size = cellPx * (crown ? 1.6f : 1.05f) * (0.55f + 0.6f * heat) * FLAME_SIZE;
                float cx = camera.cellToScreenX(x + 0.5f + jx), cy = camera.cellToScreenY(y + 0.5f + jy) + size * 0.18f;
                out.addSheetQuad(RenderLayer.HAZARDS, sheet,
                        (frame % SHEET_COLS) * framePxW, (FIRE_FIRST_ROW + frame / SHEET_COLS) * framePxH,
                        framePxW, framePxH, cx, cy, size, size,
                        1f, 0.85f + 0.15f * heat, 0.75f + 0.25f * heat, (0.55f + 0.45f * heat) * FLAME_ALPHA * ctx.alphaMult);
            }
            if (detail && intensity > 200) {
                int h = hash(idx, 0xE1B);
                float life = (clock * 0.9f + (h & 0xFF) / 255f) % 1f;
                float ex = x + 0.5f + (((h >>> 8) & 0xFF) / 255f - 0.5f) * 0.9f;
                float ey = y + 0.5f + (((h >>> 16) & 0xFF) / 255f - 0.5f) * 0.6f;
                float s = cellPx * 0.07f;
                float sx = camera.cellToScreenX(ex), sy = camera.cellToScreenY(ey) + life * cellPx * 1.2f;
                out.addSolidRect(RenderLayer.HAZARDS, sx - s, sy - s, sx + s, sy + s,
                        1f, 0.75f, 0.30f, (1f - life) * ctx.alphaMult);
            }
        }
        if (!detail) return;
        // Smoke last, over every flame, so a burning crown's screen reads as one.
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int x = idx % w, y = idx / w;
            if (x < view.minX() || x > view.maxX() || y < view.minY() || y > view.maxY()) continue;
            if (FireSmoke.smokes(field.burnedMaskAt(idx))) emitSmoke(out, camera, sheet, framePxW, framePxH, field, idx, x, y, cellPx, ctx.alphaMult);
        }
    }

    /**
     * A puff of dark smoke rising off a brush or crown fire and leaning
     * downwind: the picture of the screen {@link FireSmoke} holds there.
     */
    private void emitSmoke(DrawList out, BattleCamera camera, SpriteAPI sheet, int framePxW, int framePxH,
                           FireField field, int idx, int x, int y, float cellPx, float alphaMult) {
        int h = hash(idx, 0x5A0C);
        float life = (clock / SMOKE_LIFE + (h & 0xFF) / 255f) % 1f;
        int frame = (h >>> 8) % SMOKE_FRAMES;
        float drift = life * 1.2f;
        float px = x + 0.5f + field.windX() * field.windStrength() * drift;
        float py = y + 0.5f + field.windY() * field.windStrength() * drift;
        float size = cellPx * (1.1f + 1.3f * life);
        float cx = camera.cellToScreenX(px), cy = camera.cellToScreenY(py) + life * cellPx * 1.4f;
        float a = 0.6f * (1f - life) * Math.min(1f, life * 5f);
        out.addSheetQuad(RenderLayer.HAZARDS, sheet,
                (frame % SHEET_COLS) * framePxW, (frame / SHEET_COLS) * framePxH,
                framePxW, framePxH, cx, cy, size, size,
                0.46f, 0.44f, 0.42f, a * alphaMult);
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
