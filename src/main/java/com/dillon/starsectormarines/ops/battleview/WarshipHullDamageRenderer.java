package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipPlating;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws a damaged warship hull from its plating ({@code warship-hull-damage.md}): the game's own hull sprite cut on
 * the plating lattice into rows of source rectangles, the way {@link HullWreckRenderer} cuts a broken aircraft, so no
 * art the game owns is ever edited.
 *
 * <p>Standing cells draw as long runs, darkened as their plate goes; stripped plate takes the base game's own burn,
 * crack and hole decals; a breached plate cell is cut away, the same rectangle drawn beneath it charred and inset
 * so torn plating shows the frames behind it; a breached core cell is cut through and the ground shows. A fresh
 * breach glows and a breach that has opened burns. Every frame is a pure function of the plating and the clocks.
 */
final class WarshipHullDamageRenderer {

    private static final String[] BURNS = {"graphics/damage/damage_burns48_0_base.png",
            "graphics/damage/damage_burns48_1_base.png"};
    private static final String[] CRACKS = {"graphics/damage/damage_cracks48_0_base.png",
            "graphics/damage/damage_cracks48_1_base.png"};
    private static final String[] HOLES = {"graphics/damage/damage_holes48_0_base.png",
            "graphics/damage/damage_holes48_1_base.png"};
    private static final String GLOW = "graphics/fx/particlealpha64linear.png";
    /** Shade steps a run may be drawn at, so a gently worn side is still a few long runs and not a mosaic. */
    private static final int SHADES = 10;
    /** How long a fresh breach glows molten, in seconds. */
    static final float BREACH_GLOW_SECONDS = 3f;
    /** Plate below this share starts to show scorching. */
    private static final float SCORCH_FROM = 0.75f;

    private WarshipHullDamageRenderer() {}

    /**
     * Draws the hull from its plating, centred on the screen point of the sprite's centre and turned with the ship.
     * Returns false when there is nothing to cut (no plating, no hit yet, or a sprite whose size was not recorded),
     * so the caller draws the whole sprite as it always did.
     */
    static boolean draw(DrawList out, BattleCamera cam, BattleSprites sprites, ShuttleSpriteCache hull, Warship ship,
                        float hx, float hy, float pxW, float pxH, float alpha, float battleSeconds,
                        float animationSeconds) {
        return draw(out, RenderLayer.SHUTTLES, cam, sprites, hull, ship, hx, hy, pxW, pxH, alpha, 1f, battleSeconds,
                animationSeconds);
    }

    /**
     * {@link #draw(DrawList, BattleCamera, BattleSprites, ShuttleSpriteCache, Warship, float, float, float, float,
     * float, float, float)} on {@code layer}, every standing run's shade multiplied by {@code charScale} (a wreck is
     * drawn burnt).
     */
    static boolean draw(DrawList out, RenderLayer layer, BattleCamera cam, BattleSprites sprites,
                        ShuttleSpriteCache hull, Warship ship, float hx, float hy, float pxW, float pxH, float alpha,
                        float charScale, float battleSeconds, float animationSeconds) {
        WarshipPlating p = ship.plating();
        if (p == null || p.revision() == 0 || hull.pxW <= 0 || hull.pxH <= 0) return false;
        float sx = cam.cellToScreenX(hx);
        float sy = cam.cellToScreenY(hy);
        double rad = Math.toRadians(ship.facingDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        Frame f = new Frame(out, layer, hull, p, sx, sy, pxW, pxH, cos, sin, ship.facingDegrees, alpha);

        // Beneath: what a cut-away plate cell shows, the frames behind it, charred. Drawn as runs, so a torn side is
        // one dark gash and not a grid of tiles.
        for (int r = 0; r < p.rows; r++) {
            int start = -1;
            for (int c = 0; c <= p.cols; c++) {
                boolean frames = c < p.cols && p.isHull(c, r) && p.breached(c, r) && p.isPlated(c, r);
                if (frames && start < 0) start = c;
                if (!frames && start >= 0) {
                    float s = 0.16f + 0.05f * (hash(start, r) % 3) * charScale;
                    f.quad(start, r, c - start, 1f, s, s * 0.8f, s * 0.68f, 0.97f);
                    start = -1;
                }
            }
        }
        // The hull that still stands, run by run.
        for (int r = 0; r < p.rows; r++) {
            int runStart = -1;
            int runShade = -1;
            for (int c = 0; c <= p.cols; c++) {
                int shade = c < p.cols ? shade(p, c, r) : -1;
                if (shade == runShade) continue;
                if (runShade >= 0) {
                    float s = (0.42f + 0.58f * runShade / (SHADES - 1f)) * charScale;
                    f.quad(runStart, r, c - runStart, 1f, s, s * 0.96f, s * 0.92f, 1f);
                }
                runStart = c;
                runShade = shade;
            }
        }
        decals(f, sprites, p);
        glows(out, layer, cam, sprites, ship, p, alpha, battleSeconds, animationSeconds);
        return true;
    }

    /** A standing cell's shade step, or -1 for a cell that is not drawn (a breach). */
    private static int shade(WarshipPlating p, int c, int r) {
        if (!p.isHull(c, r)) return SHADES - 1;
        if (p.breached(c, r)) return -1;
        float s = p.isPlated(c, r) ? 0.25f + 0.75f * p.plate(c, r) : 0.55f + 0.45f * p.structure(c, r);
        if (nextToBreach(p, c, r)) s *= 0.6f;
        return Math.max(0, Math.min(SHADES - 1, Math.round(s * (SHADES - 1))));
    }

    private static boolean nextToBreach(WarshipPlating p, int c, int r) {
        return breachedAt(p, c - 1, r) || breachedAt(p, c + 1, r) || breachedAt(p, c, r - 1) || breachedAt(p, c, r + 1);
    }

    private static boolean breachedAt(WarshipPlating p, int c, int r) {
        return c >= 0 && r >= 0 && c < p.cols && r < p.rows && p.isHull(c, r) && p.breached(c, r);
    }

    /** The base game's burn, crack and hole decals over stripped plate, chosen and turned by a hash of the cell. */
    private static void decals(Frame f, BattleSprites sprites, WarshipPlating p) {
        for (int r = 0; r < p.rows; r++) {
            for (int c = 0; c < p.cols; c++) {
                if (!p.isHull(c, r) || !p.isPlated(c, r) || p.breached(c, r)) continue;
                float plate = p.plate(c, r);
                if (plate >= SCORCH_FROM) continue;
                int h = hash(c, r);
                String[] family = plate > 0.45f ? BURNS : plate > 0.15f ? CRACKS : HOLES;
                ShuttleSpriteCache decal = sprites.spriteByPath(family[h & 1]);
                if (decal == null || decal.pxW <= 0) continue;
                // One quadrant of the sheet, so a cell carries one mark and not the sheet's whole cluster.
                int qw = decal.pxW / 2;
                int qh = decal.pxH / 2;
                int qx = ((h >> 1) & 1) * qw;
                int qy = ((h >> 2) & 1) * qh;
                float strength = Math.min(1f, (SCORCH_FROM - plate) / 0.5f);
                float spin = (h >> 3) % 360;
                f.decal(decal, qx, qy, qw, qh, c, r, 1.9f, spin, strength);
            }
        }
    }

    /** A fresh breach glows molten and fades; an open breach burns with a flicker. Additive, over the hull. */
    private static void glows(DrawList out, RenderLayer layer, BattleCamera cam, BattleSprites sprites, Warship ship,
                              WarshipPlating p, float alpha, float now, float anim) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        float cellPx = cam.cellPxSize() * ship.lengthCells / p.rows;
        for (int r = 0; r < p.rows; r++) {
            for (int c = 0; c < p.cols; c++) {
                if (!p.isHull(c, r) || !p.breached(c, r)) continue;
                int cell = r * p.cols + c;
                float x = cam.cellToScreenX(ship.cellX(cell));
                float y = cam.cellToScreenY(ship.cellY(cell));
                float age = now - p.breachedAt(c, r);
                if (age >= 0f && age < BREACH_GLOW_SECONDS) {
                    float heat = 1f - age / BREACH_GLOW_SECONDS;
                    out.addAdditiveSprite(layer, glow.sprite, x, y, cellPx * 2.6f, cellPx * 2.6f, 0f,
                            1f, 0.55f + 0.3f * heat, 0.2f, alpha * 0.85f * heat);
                }
                int h = hash(c, r);
                if (h % 3 == 0 && nextToStanding(p, c, r)) {
                    float flicker = 0.65f + 0.2f * (float) Math.sin(anim * 9f + h)
                            + 0.15f * (float) Math.sin(anim * 23f + h * 0.37f);
                    out.addAdditiveSprite(layer, glow.sprite, x, y, cellPx * 1.8f, cellPx * 1.8f, 0f,
                            1f, 0.45f, 0.12f, alpha * 0.55f * flicker);
                }
            }
        }
    }

    private static boolean nextToStanding(WarshipPlating p, int c, int r) {
        return standing(p, c - 1, r) || standing(p, c + 1, r) || standing(p, c, r - 1) || standing(p, c, r + 1);
    }

    private static boolean standing(WarshipPlating p, int c, int r) {
        return c >= 0 && r >= 0 && c < p.cols && r < p.rows && p.isHull(c, r) && !p.breached(c, r);
    }

    private static int hash(int c, int r) {
        int h = c * 73856093 ^ r * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** One hull's draw: the lattice mapped onto its sprite and turned with the ship. */
    private record Frame(DrawList out, RenderLayer layer, ShuttleSpriteCache hull, WarshipPlating p, float sx, float sy, float pxW,
                         float pxH, float cos, float sin, float facing, float alpha) {

        /** A run of {@code count} cells from {@code (c, r)}, scaled about its own centre and tinted. */
        void quad(int c, int r, int count, float scale, float red, float green, float blue, float a) {
            int srcX = Math.round(c * hull.pxW / (float) p.cols);
            int srcRight = Math.round((c + count) * hull.pxW / (float) p.cols);
            int srcY = Math.round(r * hull.pxH / (float) p.rows);
            int srcBottom = Math.round((r + 1) * hull.pxH / (float) p.rows);
            if (srcRight <= srcX || srcBottom <= srcY) return;
            float u0 = srcX / (float) hull.pxW;
            float u1 = srcRight / (float) hull.pxW;
            float v0 = srcY / (float) hull.pxH;
            float v1 = srcBottom / (float) hull.pxH;
            float lx = ((u0 + u1) * 0.5f - 0.5f) * pxW;
            float ly = (0.5f - (v0 + v1) * 0.5f) * pxH;
            float w = (u1 - u0) * pxW;
            float h = (v1 - v0) * pxH;
            // A little bleed so neighbouring runs meet without a hairline seam.
            float bleed = scale >= 1f ? 1f : 0f;
            out.addSheetQuad(layer, hull.sprite, srcX, srcY, srcRight - srcX, srcBottom - srcY,
                    sx + lx * cos - ly * sin, sy + lx * sin + ly * cos, w * scale + bleed, h * scale + bleed, facing,
                    red, green, blue, a * alpha);
        }

        /** A decal's sub-rectangle laid over cell {@code (c, r)}, {@code size} cells across, turned by {@code spin}. */
        void decal(ShuttleSpriteCache decal, int qx, int qy, int qw, int qh, int c, int r, float size, float spin,
                   float a) {
            float lx = ((c + 0.5f) / p.cols - 0.5f) * pxW;
            float ly = (0.5f - (r + 0.5f) / p.rows) * pxH;
            float cell = pxH / p.rows;
            out.addSheetQuad(layer, decal.sprite, qx, qy, qw, qh,
                    sx + lx * cos - ly * sin, sy + lx * sin + ly * cos, cell * size, cell * size, facing + spin,
                    1f, 1f, 1f, a * alpha);
        }
    }
}
