package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipPlating;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws a damaged warship hull from its plating ({@code warship-hull-damage.md}): the game's own hull sprite cut on
 * the plating lattice into rows of source rectangles, the way {@link HullWreckRenderer} cuts a broken aircraft, so no
 * art the game owns is ever edited.
 *
 * <p>Standing cells draw as long runs, shaded gently as their plate goes so a battered hull still reads as the ship;
 * stripped plate takes the base game's own burn, crack and hole decals. A breached cell shows the ship's insides —
 * torn frames behind the plating, gutted compartments in the core — from the mod's seamless interior sheets, a
 * hashed window at a hashed turn per cell, tinted to the builder's metal; its rim is torn plate lip laid over the
 * standing edge, so a hole has a ragged outline rather than a cell boundary. Nothing is cut through to the ground:
 * that belongs to a hull breaking apart. A fresh breach glows and a breach that has opened burns. Every frame is a pure function of the plating and the clocks.
 */
final class WarshipHullDamageRenderer {

    private static final String[] BURNS = {"graphics/damage/damage_burns48_0_base.png",
            "graphics/damage/damage_burns48_1_base.png"};
    private static final String[] CRACKS = {"graphics/damage/damage_cracks48_0_base.png",
            "graphics/damage/damage_cracks48_1_base.png"};
    private static final String[] HOLES = {"graphics/damage/damage_holes48_0_base.png",
            "graphics/damage/damage_holes48_1_base.png"};
    private static final String GLOW = "graphics/fx/particlealpha64linear.png";
    /** Torn frames, ribs and decks behind a stripped side: what a breached plate cell shows. */
    static final String INTERIOR_FRAMES = "graphics/warship-damage/hull-interior-frames.png";
    /** Gutted compartments, twisted conduit and scorched bulkheads: what a breached core cell shows. */
    static final String INTERIOR_CORE = "graphics/warship-damage/hull-interior-core.png";
    /** Four bands of torn plate lip, the lip pointing up, laid along the rim of a breach. */
    static final String TORN_EDGES = "graphics/warship-damage/hull-torn-edges.png";
    /** Interior sheets are seamless tiles; one cell samples this share of the sheet, at a hashed spot and turn. */
    private static final float INTERIOR_CELL_SHARE = 0.11f;
    /** Cells across one interior hunk: one window and one turn of the sheet. */
    private static final int HUNK = 3;
    /** The torn lip's rows within one band of the edge sheet: lip above, a little plate below. */
    private static final float LIP_TOP = 28f / 128f;
    private static final float LIP_BOTTOM = 104f / 128f;
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

        // Beneath the standing hull: what a breach shows, the mangled frames and decks behind the plating.
        interior(f, sprites, ship, p, charScale);
        // The hull that still stands, run by run.
        for (int r = 0; r < p.rows; r++) {
            int runStart = -1;
            int runShade = -1;
            for (int c = 0; c <= p.cols; c++) {
                int shade = c < p.cols ? shade(p, c, r) : -1;
                if (shade == runShade) continue;
                if (runShade >= 0) {
                    float s = (0.55f + 0.45f * runShade / (SHADES - 1f)) * charScale;
                    f.quad(runStart, r, c - runStart, 1f, s, s * 0.96f, s * 0.92f, 1f);
                }
                runStart = c;
                runShade = shade;
            }
        }
        decals(f, sprites, p);
        tornEdges(f, sprites, ship, p, charScale);
        glows(out, layer, cam, sprites, ship, p, alpha, battleSeconds, animationSeconds);
        return true;
    }

    /** A standing cell's shade step, or -1 for a cell that is not drawn (a breach). */
    private static int shade(WarshipPlating p, int c, int r) {
        if (!p.isHull(c, r)) return SHADES - 1;
        if (p.breached(c, r)) return -1;
        float s = p.isPlated(c, r) ? 0.6f + 0.4f * p.plate(c, r) : 0.8f + 0.2f * p.structure(c, r);
        if (nextToBreach(p, c, r)) s *= 0.85f;
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

    /**
     * Breached cells draw the ship's insides, not the ground: plate cells the torn frames sheet, core cells the gutted
     * compartments sheet, each a hashed window of the seamless sheet at a hashed quarter turn, drawn a little
     * oversize so the hunks overlap and no lattice shows. A breached cell on the hull's own outline draws its piece
     * of the hull sprite darkened instead, so the ship's silhouette stays the sprite's and never squares off.
     */
    private static void interior(Frame f, BattleSprites sprites, Warship ship, WarshipPlating p, float charScale) {
        ShuttleSpriteCache frames = sprites.spriteByPath(INTERIOR_FRAMES);
        ShuttleSpriteCache core = sprites.spriteByPath(INTERIOR_CORE);
        float[] tint = palette(ship.techStyle());
        float bright = 0.75f * Math.min(1f, charScale);
        int salt = p.cols * 131 + p.rows * 7;
        for (int r = 0; r < p.rows; r++) {
            for (int c = 0; c < p.cols; c++) {
                if (!p.isHull(c, r) || !p.breached(c, r)) continue;
                ShuttleSpriteCache sheet = p.isPlated(c, r) ? frames : core;
                if (sheet == null || sheet.pxW <= 0 || !surrounded(p, c, r)) {
                    // On the outline: the sprite's own piece, burnt and holed, so the silhouette stays the ship's.
                    float s = 0.5f * Math.min(1f, charScale * 1.4f);
                    f.quad(c, r, 1, 1f, s * tint[0], s * tint[1] * 0.85f, s * tint[2] * 0.75f, 1f);
                    int eh = hash(c, r + salt);
                    ShuttleSpriteCache hole = sprites.spriteByPath(HOLES[eh & 1]);
                    if (hole != null && hole.pxW > 0) {
                        int qw = hole.pxW / 2;
                        int qh = hole.pxH / 2;
                        f.decal(hole, ((eh >> 1) & 1) * qw, ((eh >> 2) & 1) * qh, qw, qh, c, r, 1f,
                                (eh >> 3) % 360, 0.9f);
                    }
                    continue;
                }
                // A hunk is a block of cells sharing one hashed window of the sheet at one hashed turn, so a breach
                // shows ribs and decks that carry across cells, and the next hunk over is a different piece.
                int bc = Math.floorDiv(c + (salt % HUNK), HUNK);
                int br = Math.floorDiv(r, HUNK);
                int h = hash(bc + salt, br);
                int turn = h & 3;
                int win = Math.max(8, Math.round(sheet.pxW * INTERIOR_CELL_SHARE));
                int span = win * HUNK;
                int originX = (h >>> 3) % Math.max(1, sheet.pxW - span);
                int originY = (h >>> 13) % Math.max(1, sheet.pxH - span);
                // The cell's place in its hunk, about the hunk's centre with y towards the nose, turned back by the
                // hunk's turn so the turned quads assemble into one turned picture.
                float x = (c + (salt % HUNK)) - bc * HUNK - (HUNK - 1) * 0.5f;
                float y = -(r - br * HUNK - (HUNK - 1) * 0.5f);
                for (int k = 0; k < turn; k++) {
                    float t = x;
                    x = y;
                    y = -t;
                }
                int srcX = originX + Math.round((x + (HUNK - 1) * 0.5f) * win);
                int srcY = originY + Math.round((-y + (HUNK - 1) * 0.5f) * win);
                float jitter = 0.88f + 0.12f * ((h >>> 21) % 8) / 7f;
                f.patch(sheet, srcX, srcY, win, win, c, r, 1.06f, 90f * turn,
                        tint[0] * bright * jitter, tint[1] * bright * jitter, tint[2] * bright * jitter, 1f);
            }
        }
    }

    /**
     * Torn plate along every side where a breach meets standing hull: a strip of ragged lip laid on the standing cell's
     * edge with the lip reaching into the breach, so the hole's outline is torn metal and not a cell boundary.
     */
    private static void tornEdges(Frame f, BattleSprites sprites, Warship ship, WarshipPlating p, float charScale) {
        ShuttleSpriteCache edges = sprites.spriteByPath(TORN_EDGES);
        if (edges == null || edges.pxW <= 0) return;
        float[] tint = palette(ship.techStyle());
        int bandH = edges.pxH / 4;
        int stripW = edges.pxW / 4;
        int salt = p.cols * 977 + p.rows * 13;
        for (int r = 0; r < p.rows; r++) {
            for (int c = 0; c < p.cols; c++) {
                if (!standing(p, c, r)) continue;
                float s = (0.55f + 0.45f * shade(p, c, r) / (SHADES - 1f)) * charScale;
                // A breach towards the nose takes the strip unturned; the other sides turn from it.
                edge(f, edges, p, c, r, 0, -1, 0f, bandH, stripW, salt, tint, s);
                edge(f, edges, p, c, r, 0, 1, 180f, bandH, stripW, salt, tint, s);
                edge(f, edges, p, c, r, 1, 0, -90f, bandH, stripW, salt, tint, s);
                edge(f, edges, p, c, r, -1, 0, 90f, bandH, stripW, salt, tint, s);
            }
        }
    }

    private static void edge(Frame f, ShuttleSpriteCache edges, WarshipPlating p, int c, int r, int dc, int dr,
                             float turn, int bandH, int stripW, int salt, float[] tint, float s) {
        if (!breachedAt(p, c + dc, r + dr)) return;
        int h = hash(c * 2 + dc + salt, r * 2 + dr);
        int band = h & 3;
        int srcX = (h >>> 2) % Math.max(1, edges.pxW - stripW);
        // Centred on the shared side: the plate half lies over the standing cell, the torn lip reaches into the breach.
        float cu = c + 0.5f + dc * 0.5f;
        float cv = r + 0.5f + dr * 0.5f;
        float along = 1.4f + 0.4f * ((h >>> 4) % 4) / 3f;
        int top = Math.round(band * bandH + LIP_TOP * bandH);
        int bottom = Math.round(band * bandH + LIP_BOTTOM * bandH);
        f.strip(edges, srcX, top, stripW, bottom - top, cu, cv, along, 1.05f, turn,
                tint[0] * s, tint[1] * s, tint[2] * s, 1f);
    }

    /** True when every neighbour of the cell, diagonals too, is hull: the cell lies inside the silhouette. */
    private static boolean surrounded(WarshipPlating p, int c, int r) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nc = c + dc;
                int nr = r + dr;
                if (nc < 0 || nr < 0 || nc >= p.cols || nr >= p.rows || !p.isHull(nc, nr)) return false;
            }
        }
        return true;
    }

    /** The hull's metal, by its builder: warm and rusted low tech, grey-green midline, cool high tech. */
    static float[] palette(String techStyle) {
        String t = techStyle == null ? "" : techStyle.toLowerCase();
        if (t.contains("low")) return new float[] {1f, 0.86f, 0.72f};
        if (t.contains("high")) return new float[] {0.82f, 0.9f, 1f};
        if (t.contains("mid")) return new float[] {0.92f, 0.93f, 0.86f};
        return new float[] {0.95f, 0.9f, 0.85f};
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

        /** A window of a sheet laid over cell {@code (c, r)}, {@code size} cells across, turned by {@code spin}. */
        void patch(ShuttleSpriteCache sheet, int srcX, int srcY, int srcW, int srcH, int c, int r, float size,
                   float spin, float red, float green, float blue, float a) {
            float lx = ((c + 0.5f) / p.cols - 0.5f) * pxW;
            float ly = (0.5f - (r + 0.5f) / p.rows) * pxH;
            out.addSheetQuad(layer, sheet.sprite, srcX, srcY, srcW, srcH,
                    sx + lx * cos - ly * sin, sy + lx * sin + ly * cos, pxW / p.cols * size, pxH / p.rows * size,
                    facing + spin, red, green, blue, a * alpha);
        }

        /** A strip of a sheet centred at lattice point {@code (cu, cv)}, {@code along} by {@code across} cells. */
        void strip(ShuttleSpriteCache sheet, int srcX, int srcY, int srcW, int srcH, float cu, float cv, float along,
                   float across, float turn, float red, float green, float blue, float a) {
            float lx = (cu / p.cols - 0.5f) * pxW;
            float ly = (0.5f - cv / p.rows) * pxH;
            float cell = pxH / p.rows;
            out.addSheetQuad(layer, sheet.sprite, srcX, srcY, srcW, srcH,
                    sx + lx * cos - ly * sin, sy + lx * sin + ly * cos, cell * along, cell * across, facing + turn,
                    red, green, blue, a * alpha);
        }
    }
}
