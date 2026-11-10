package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws a sliding leaf door's leaves the way {@link RoomDoorRenderSystem} draws a ship's: two leaves part from a seam
 * and withdraw into their jambs, and only the part of each still in the opening is drawn, cut from the art's meeting
 * end at the art's own density. Shared by every leaf door: a ship's compartment doors, the locked doors
 * ({@link LockedDoorRenderSystem}) and the ruins' hall and cargo doors ({@link RuinsLeafDoorRenderSystem}).
 *
 * <p>Art comes two ways. A <b>single</b> leaf is authored along x, its jamb end left and its meeting edge right; the
 * high leaf is the same art turned a half. A <b>pair</b> is a shut door's sprite, both leaves side by side with the
 * seam in the middle: the low leaf is its left half, the high leaf its right half, each drawn unturned.
 */
final class LeafDoorArt {

    /** How far a torn leaf lies from the door's line, in cells, and how far it is skewed off square. */
    static final float TORN_OFFSET_CELLS = 1.2f, TORN_SKEW_DEGREES = 24f;

    private LeafDoorArt() { }

    /** A door across {@code [min, max]} along x (or y when {@code !spanX}), its leaves on the line at {@code across}. */
    record Span(boolean spanX, float min, float max, float across) {
        float half() { return (max - min) * 0.5f; }
    }

    /**
     * The leaves of one door at {@code travel} (0 shut, 1 withdrawn): {@code perSide} leaves each way, telescoping;
     * {@code artCells} is how long one leaf of the art is in cells, {@code depth} how thick a leaf stands through the
     * wall. With {@code tornSide} -1 or 1 the low or high leaf is gone from its track.
     */
    static void leaves(BattleCamera cam, DrawList out, ShuttleSpriteCache art, boolean pair, float artCells, Span s,
                       float depth, float travel, int perSide, int tornSide, float tint, float alpha) {
        float reach = s.half() * (1f - travel);
        if (reach < 0.02f) return;
        float px = cam.cellPxSize();
        int leafW = pair ? art.pxW / 2 : art.pxW;
        float seg = reach / perSide;
        int srcW = Math.max(1, Math.min(leafW, Math.round(leafW * seg / artCells)));
        for (int side = -1; side <= 1; side += 2) {
            if (side == tornSide) continue;
            float jamb = side < 0 ? s.min() : s.max();
            for (int k = 0; k < perSide; k++) {
                float along = jamb - side * (k * seg + seg * 0.5f);
                int srcX;
                float angle = s.spanX() ? 0f : 90f;
                if (pair) srcX = side < 0 ? leafW - srcW : leafW;
                else {
                    srcX = art.pxW - srcW;
                    if (side > 0) angle += 180f;
                }
                out.addImageRegion(RenderLayer.DOODADS, art.sprite, art.pxW, art.pxH, srcX, 0, srcW, art.pxH,
                        screenX(cam, s, along, s.across()), screenY(cam, s, along, s.across()), seg * px, depth * px,
                        angle, tint, tint, tint, alpha);
            }
        }
    }

    /**
     * A leaf torn from its track lying on the floor beside the door, on the side {@code floorSide} (-1 below or left of
     * the door's line, 1 above or right), skewed off square.
     */
    static void torn(BattleCamera cam, DrawList out, ShuttleSpriteCache art, boolean pair, Span s, float depth,
                     int tornSide, int floorSide, float alpha) {
        float px = cam.cellPxSize();
        int leafW = pair ? art.pxW / 2 : art.pxW;
        float length = s.half();
        float along = tornSide < 0 ? s.min() + length * 0.5f : s.max() - length * 0.5f;
        float across = s.across() + floorSide * TORN_OFFSET_CELLS;
        float angle = (s.spanX() ? 0f : 90f) + TORN_SKEW_DEGREES * tornSide * floorSide;
        int srcX = pair && tornSide > 0 ? leafW : 0;
        out.addImageRegion(RenderLayer.DOODADS, art.sprite, art.pxW, art.pxH, srcX, 0, leafW, art.pxH,
                screenX(cam, s, along, across), screenY(cam, s, along, across), length * px, depth * px, angle,
                .78f, .78f, .78f, alpha);
    }

    /** The ruins' door furniture ({@code ruins-doors}): jamb, its lamp's lens in the jamb art, rubble, blast clamp. */
    static final String RUINS_JAMB = "graphics/doors/ruins-door-jamb.png",
            RUINS_RUBBLE = "graphics/doors/ruins-door-rubble.png",
            RUINS_CLAMP = "graphics/doors/ruins-blast-clamp.png";
    /** A ruins jamb's length along the door and depth through the wall, in cells (80 x 96 px at 160 a cell). */
    static final float JAMB_CELLS = .5f, JAMB_DEPTH = .6f;
    /** The jamb lamp's plain lens within the jamb art, px: left, top, size. Drawn again tinted to light it. */
    private static final int LENS_X = 4, LENS_Y = 3, LENS_PX = 25;
    /** A lamp's colours: answering or open, powered but held, locked, dead. */
    static final float[] LAMP_OPEN = {.45f, 1f, .55f}, LAMP_READY = {1f, .72f, .25f}, LAMP_LOCKED = {1f, .3f, .25f},
            LAMP_DEAD = {.35f, .33f, .3f};

    /**
     * A jamb at each end of the door, set into the wall just outside its span, the lamp's lens lit {@code lamp}. The
     * high jamb is the low one turned a half.
     */
    static void jambs(BattleCamera cam, DrawList out, ShuttleSpriteCache jamb, Span s, float[] lamp, float alpha) {
        if (jamb == null) return;
        float px = cam.cellPxSize();
        for (int side = -1; side <= 1; side += 2) {
            float along = side < 0 ? s.min() - JAMB_CELLS * .5f : s.max() + JAMB_CELLS * .5f;
            float angle = (s.spanX() ? 0f : 90f) + (side > 0 ? 180f : 0f);
            float sx = screenX(cam, s, along, s.across()), sy = screenY(cam, s, along, s.across());
            out.addSprite(RenderLayer.DOODADS, jamb.sprite, sx, sy, JAMB_CELLS * px, JAMB_DEPTH * px, angle,
                    1f, 1f, 1f, alpha);
            // The lens, lit: its offset from the jamb's middle in art px, turned with the jamb.
            float scale = JAMB_CELLS * px / jamb.pxW;
            float ox = (LENS_X + LENS_PX * .5f - jamb.pxW * .5f) * scale;
            float oy = (jamb.pxH * .5f - LENS_Y - LENS_PX * .5f) * scale;
            double r = Math.toRadians(angle);
            float lx = sx + (float) (ox * Math.cos(r) - oy * Math.sin(r));
            float ly = sy + (float) (ox * Math.sin(r) + oy * Math.cos(r));
            out.addImageRegion(RenderLayer.DOODADS, jamb.sprite, jamb.pxW, jamb.pxH, LENS_X, LENS_Y, LENS_PX, LENS_PX,
                    lx, ly, LENS_PX * scale, LENS_PX * scale, angle, lamp[0], lamp[1], lamp[2], alpha);
        }
    }

    /** Rubble a cell across, on the floor where a torn leaf fell ({@link #torn}'s place for it). */
    static void rubble(BattleCamera cam, DrawList out, ShuttleSpriteCache rubble, Span s, int tornSide, int floorSide,
                       float alpha) {
        if (rubble == null) return;
        float px = cam.cellPxSize();
        float along = tornSide < 0 ? s.min() + .5f : s.max() - .5f;
        float across = s.across() + floorSide * .7f;
        out.addSprite(RenderLayer.DOODADS, rubble.sprite, screenX(cam, s, along, across),
                screenY(cam, s, along, across), px, px, s.spanX() ? 0f : 90f, 1f, 1f, 1f, alpha);
    }

    /** A blast door's seal clamp across its seam, {@code length} by {@code depth} cells. */
    static void clamp(BattleCamera cam, DrawList out, ShuttleSpriteCache clamp, Span s, float length, float depth,
                      float alpha) {
        if (clamp == null) return;
        float px = cam.cellPxSize(), seam = (s.min() + s.max()) * .5f;
        out.addSprite(RenderLayer.DOODADS, clamp.sprite, screenX(cam, s, seam, s.across()),
                screenY(cam, s, seam, s.across()), length * px, depth * px, s.spanX() ? 0f : 90f, 1f, 1f, 1f, alpha);
    }

    private static float screenX(BattleCamera cam, Span s, float along, float across) {
        return cam.cellToScreenX(s.spanX() ? along : across);
    }

    private static float screenY(BattleCamera cam, Span s, float along, float across) {
        return cam.cellToScreenY(s.spanX() ? across : along);
    }
}
