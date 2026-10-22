package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Locale;

/**
 * The fire field drawn (`fire-nouns.md`, Look): scorch on the ground under
 * everything that stands on it, and over the bodies a warm glow, flames from
 * the smoke-and-fire sheet sized and paced by intensity, and a few embers over
 * the hottest cells. Visits only the change-tracking tiles the fire has
 * touched that lie in view, so a battle that never burns collects nothing and
 * a burning one collects what is on screen.
 *
 * <p>Two passes because a system emits into one layer: {@link #scorch()} on
 * {@link RenderLayer#DECALS}, {@link #flames()} on {@link RenderLayer#HAZARDS}.
 * {@code -Dbattle.render.fire=false} is the control; {@code
 * -Dbattle.render.fireScorch=char|ash|ember} and {@code
 * -Dbattle.render.fireFlames=bold|restrained} choose the look.
 */
public final class FireRenderSystem {

    public static final String PROPERTY = "battle.render.fire";
    public static final String SCORCH_PROPERTY = "battle.render.fireScorch";
    public static final String FLAMES_PROPERTY = "battle.render.fireFlames";
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** How burnt ground reads. */
    public enum Scorch { CHAR, ASH, EMBER }

    /** How big the flames stand. */
    public enum Flames {
        /** Full-size tongues: a crown fire hides its tree. */
        BOLD(1f, 1f),
        /** Smaller, fainter tongues over a stronger glow: the tree and the ground read through the fire. */
        RESTRAINED(0.62f, 0.75f);

        final float size, alpha;

        Flames(float size, float alpha) { this.size = size; this.alpha = alpha; }
    }

    /** Sheet columns and rows; the fire frames are the lower two rows. */
    private static final int SHEET_COLS = 4, SHEET_ROWS = 4, FIRE_FIRST_ROW = 2, FIRE_FRAMES = 8;
    private static final float FLAME_FPS = 10f;
    /** Cell size in pixels below which a fire is drawn as glow and one tongue, no embers. */
    private static final float DETAIL_CELL_PX = 10f;

    private final BattleSprites sprites;
    private final Scorch scorchStyle = parse(Scorch.class, SCORCH_PROPERTY, Scorch.CHAR);
    private final Flames flameStyle = parse(Flames.class, FLAMES_PROPERTY, Flames.BOLD);
    private final RenderSystem scorch = RenderSystem.of(RenderLayer.DECALS, this::collectScorch);
    private final RenderSystem flames = RenderSystem.of(RenderLayer.HAZARDS, this::collectFlames);
    private float clock;

    public FireRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    public static boolean enabled() { return ENABLED; }

    public RenderSystem scorch() { return scorch; }

    public RenderSystem flames() { return flames; }

    // ---- Scorch -----------------------------------------------------------

    private void collectScorch(RenderContext ctx, DrawList out) {
        FireField field = ENABLED ? ctx.sim.fireFieldIfPresent() : null;
        if (field == null || field.revision() == 0) return;
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        VisibleCellRect view = camera.visibleCells(1, field.width(), field.height());
        int w = field.width();
        for (int ty = view.minY() / FireField.TILE; ty <= view.maxY() / FireField.TILE; ty++) {
            for (int tx = view.minX() / FireField.TILE; tx <= view.maxX() / FireField.TILE; tx++) {
                if (field.tileRevision(tx, ty) == 0) continue;
                int y0 = Math.max(view.minY(), ty * FireField.TILE), y1 = Math.min(view.maxY(), ty * FireField.TILE + FireField.TILE - 1);
                int x0 = Math.max(view.minX(), tx * FireField.TILE), x1 = Math.min(view.maxX(), tx * FireField.TILE + FireField.TILE - 1);
                for (int y = y0; y <= y1; y++) {
                    // Runs of one shade along a row are one rectangle.
                    int runStart = -1, runShade = -1;
                    for (int x = x0; x <= x1 + 1; x++) {
                        int shade = x <= x1 ? shade(field, y * w + x) : -1;
                        if (shade == runShade) continue;
                        if (runShade > 0) emitScorch(out, camera, cellPx, runStart, x, y, runShade, ctx.alphaMult);
                        runStart = x;
                        runShade = shade;
                    }
                }
            }
        }
        if (scorchStyle == Scorch.EMBER && cellPx >= DETAIL_CELL_PX) collectEmberSpeckle(ctx, out, field, view);
    }

    /** 0 for unscorched; 1..4 darkening as a cell burns and once it is burnt, varied by a seeded hash. */
    private static int shade(FireField field, int idx) {
        byte s = field.stateAt(idx);
        if (s == FireField.UNBURNT) return 0;
        if (s == FireField.BURNING) return 1 + Math.min(2, (255 - field.intensityAt(idx)) / 90);
        return 4;
    }

    private void emitScorch(DrawList out, BattleCamera camera, float cellPx, int x0, int x1, int y, int shade, float alpha) {
        float a;
        float r, g, b;
        switch (scorchStyle) {
            case ASH -> { r = 0.30f; g = 0.29f; b = 0.27f; a = 0.18f + 0.13f * shade; }
            default -> { r = 0.07f; g = 0.06f; b = 0.05f; a = 0.16f + 0.15f * shade; }
        }
        float sx0 = camera.cellToScreenX(x0), sx1 = camera.cellToScreenX(x1);
        float sy0 = camera.cellToScreenY(y), sy1 = camera.cellToScreenY(y + 1);
        out.addSolidRect(RenderLayer.DECALS, sx0, sy0, sx1, sy1, r, g, b, a * alpha);
    }

    /** Young burnt ground still glows: a speckle of dull embers for the first half-minute. */
    private void collectEmberSpeckle(RenderContext ctx, DrawList out, FireField field, VisibleCellRect view) {
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        int w = field.width();
        for (int ty = view.minY() / FireField.TILE; ty <= view.maxY() / FireField.TILE; ty++) {
            for (int tx = view.minX() / FireField.TILE; tx <= view.maxX() / FireField.TILE; tx++) {
                if (field.tileRevision(tx, ty) == 0) continue;
                for (int y = Math.max(view.minY(), ty * FireField.TILE); y <= Math.min(view.maxY(), ty * FireField.TILE + FireField.TILE - 1); y++) {
                    for (int x = Math.max(view.minX(), tx * FireField.TILE); x <= Math.min(view.maxX(), tx * FireField.TILE + FireField.TILE - 1); x++) {
                        int idx = y * w + x;
                        int age = field.burntAge(idx);
                        if (age < 0 || age > 900) continue;
                        float life = 1f - age / 900f;
                        int h = hash(idx, 0xE3B);
                        float px = x + 0.2f + 0.6f * ((h & 0xFF) / 255f), py = y + 0.2f + 0.6f * (((h >>> 8) & 0xFF) / 255f);
                        float s = cellPx * 0.14f;
                        float cx = camera.cellToScreenX(px), cy = camera.cellToScreenY(py);
                        out.addSolidRect(RenderLayer.DECALS, cx - s, cy - s, cx + s, cy + s,
                                0.85f, 0.30f, 0.08f, 0.55f * life * ctx.alphaMult);
                    }
                }
            }
        }
    }

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
                    1f, 0.42f, 0.10f, (flameStyle == Flames.BOLD ? 0.22f : 0.34f) * heat * ctx.alphaMult);
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
                float size = cellPx * (crown ? 1.6f : 1.05f) * (0.55f + 0.6f * heat) * flameStyle.size;
                float cx = camera.cellToScreenX(x + 0.5f + jx), cy = camera.cellToScreenY(y + 0.5f + jy) + size * 0.18f;
                out.addSheetQuad(RenderLayer.HAZARDS, sheet,
                        (frame % SHEET_COLS) * framePxW, (FIRE_FIRST_ROW + frame / SHEET_COLS) * framePxH,
                        framePxW, framePxH, cx, cy, size, size,
                        1f, 0.85f + 0.15f * heat, 0.75f + 0.25f * heat, (0.55f + 0.45f * heat) * flameStyle.alpha * ctx.alphaMult);
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

    private static <E extends Enum<E>> E parse(Class<E> type, String property, E fallback) {
        String v = System.getProperty(property);
        if (v == null) return fallback;
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
