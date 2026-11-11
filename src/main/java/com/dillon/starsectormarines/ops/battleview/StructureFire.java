package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Arrays;

/**
 * A burning building's fire drawn on it as it stands ({@link WallDepth}; {@code fire-nouns.md}, Look): flame licking
 * up its faces and pouring out of their openings, laid on each face's own plane over the faces ({@link FaceFire}
 * decides how much), and, over the roofs, flame beds burning at the height of its roof and wall tops with smoke and
 * sparks rising off them. The flames up a face are cut from an upright sheet ({@link #TONGUE_SHEET_PATH}), each
 * fading from one frame of its loop into the next; the fire over the roofs is the ground fire's own top-down sheet.
 * Collected GL-free.
 */
final class StructureFire {

    /** The upright flame sheet: 8 x 4 frames of 32 x 64, rows a curtain's two loops then a plume's ({@code art-source/fx/fire-faces}). */
    static final String TONGUE_SHEET_PATH = "graphics/fx/fire_face_tongues.png";
    private static final int TONGUE_COLS = 8, TONGUE_W = 32, TONGUE_H = 64;
    private static final int CURTAIN_ROW = 0, PLUME_ROW = 2;
    /** Where in a plume's frame its opening's head sits, a share of the frame's height up from its foot. */
    private static final float PLUME_ROOT = .3f, PLUME_FOOT = .04f;
    /** The top-down sheet's columns and bed loop ({@link FireRenderSystem#FLAME_SHEET_PATH}). */
    private static final int BED_COLS = 4, BED_LOOP = 8;
    /** The smoke sheet's grid and its smoke frames (the upper two rows). */
    private static final int SMOKE_COLS = 4, SMOKE_ROWS = 4, SMOKE_FRAMES = 8;
    private static final float SMOKE_LIFE = 3.2f, SMOKE_GREY = .26f;
    /** How strongly a burning face is lit by its own fire. UNTUNED. */
    private static final float FACE_GLOW = .42f;

    /** A buffered flame: its corners on screen (foot t0, foot t1, top t1, top t0), sheet column, row and alpha. */
    private static final int FLAME = 11;
    private float[] flames = new float[FLAME * 64];
    private int flameCount;
    /** Flames rising over the walls' tops, drawn over the roofs ({@link #flushCrowns}); as {@link #flames}. */
    private float[] crowns = new float[FLAME * 32];
    private int crownCount;
    /** Whether {@link #flame} is laying a crown. */
    private boolean crowning;
    /** A buffered glow: centre, width, height, alpha. */
    private static final int GLOW = 5;
    private float[] glows = new float[GLOW * 32];
    private int glowCount;
    /** Patch arrays handed to the draw list, reused frame to frame (a frame's list is drained before the next). */
    private float[][] patches = new float[64][];
    private int patchesUsed;
    private final float[] corners = new float[8];

    private float clock;

    /** Starts a frame at {@code clock} seconds. */
    void begin(float clock) {
        this.clock = clock;
        flameCount = 0;
        crownCount = 0;
        glowCount = 0;
        patchesUsed = 0;
    }

    float clock() {
        return clock;
    }

    /**
     * The fire on the face {@code paint} holds ({@link FacePainter#wall}), cell {@code cell}: a lick of {@code lick}
     * reaching {@code reach} of its height from its foot, and fire of {@code pour} out of each of the openings the
     * painter laid ({@link FacePainter#openings}), and fire of {@code crown} rising {@code crownReach} of its height over
     * its top. {@code standing} is how much of the face stands.
     */
    void face(FacePainter paint, int cell, float lick, float reach, float pour, float crown, float crownReach,
              float standing, float alpha) {
        if (lick > 0f && reach > 0f) {
            // Two overlapping curtains across the cell, each its own loop and row, so neighbours never repeat.
            for (int k = 0; k < 2; k++) {
                int seed = cell * 5 + k;
                float t0 = k == 0 ? -.2f : .35f, t1 = t0 + .85f;
                float r = Math.min(standing + .3f, reach * (.8f + .35f * BuildingFaces.hash(seed, 72)));
                paint.corners(t0, t1, 0f, r, corners);
                int row = CURTAIN_ROW + (BuildingFaces.hash(seed, 73) < .5f ? 0 : 1);
                loop(seed, row, alpha * Math.min(1f, .45f + lick));
            }
            paint.corners(0f, 1f, 0f, Math.min(standing, reach), corners);
            glow(alpha * FACE_GLOW * lick);
        }
        if (pour > 0f) {
            float[] o = paint.openings();
            for (int i = 0, n = paint.openingCount(); i < n; i++) {
                float t0 = o[i * 4], t1 = o[i * 4 + 1], z0 = o[i * 4 + 2], z1 = o[i * 4 + 3];
                if (z0 >= standing) continue;
                int seed = cell * 13 + i;
                // The opening's head at the plume's root; as wide as the opening and a little more, and taller
                // the harder it burns.
                float frame = (z1 - z0) / (PLUME_ROOT - PLUME_FOOT) * (.75f + .6f * pour);
                float foot = z0 - frame * PLUME_FOOT;
                float mid = (t0 + t1) / 2f, half = Math.max(.2f, (t1 - t0) * .85f);
                paint.corners(mid - half, mid + half, foot, foot + frame, corners);
                int row = PLUME_ROW + (BuildingFaces.hash(seed, 74) < .5f ? 0 : 1);
                loop(seed, row, alpha * Math.min(1f, .4f + pour));
                paint.corners(t0, t1, z0, Math.min(standing, z0 + frame), corners);
                glow(alpha * FACE_GLOW * pour * .8f);
            }
            if (paint.openingCount() == 0) {
                // A blind wall with the room burning behind it: only its heat shows, a glow up its face.
                paint.corners(0f, 1f, standing * .3f, standing, corners);
                glow(alpha * FACE_GLOW * pour * .5f);
            }
        }
        if (crown > 0f && crownReach > 0f && standing > .3f) {
            // Over the top: flame standing on the wall's broken top, rising from just below it, over the roofs.
            crowning = true;
            for (int k = 0; k < 2; k++) {
                int seed = cell * 7 + k + 3;
                float t0 = k == 0 ? -.15f : .3f, t1 = t0 + .85f;
                float up = crownReach * (.7f + .5f * BuildingFaces.hash(seed, 84));
                paint.corners(t0, t1, standing - up * .25f, standing + up, corners);
                int row = CURTAIN_ROW + (BuildingFaces.hash(seed, 85) < .5f ? 0 : 1);
                loop(seed, row, alpha * Math.min(1f, .4f + crown));
            }
            crowning = false;
        }
    }

    /** A flame in {@link #corners} fading from its loop frame into the next. */
    private void loop(int seed, int row, float alpha) {
        int frame = FaceFire.frame(clock, seed);
        float blend = FaceFire.blend(clock, seed);
        flame(frame, row, alpha * (1f - blend));
        if (blend > .02f) flame((frame + 1) % FaceFire.LOOP, row, alpha * blend);
    }

    private void flame(int col, int row, float alpha) {
        if (alpha <= .01f) return;
        float[] into;
        int o;
        if (crowning) {
            if ((crownCount + 1) * FLAME > crowns.length) crowns = Arrays.copyOf(crowns, crowns.length * 2);
            into = crowns;
            o = crownCount++ * FLAME;
        } else {
            if ((flameCount + 1) * FLAME > flames.length) flames = Arrays.copyOf(flames, flames.length * 2);
            into = flames;
            o = flameCount++ * FLAME;
        }
        System.arraycopy(corners, 0, into, o, 8);
        into[o + 8] = col;
        into[o + 9] = row;
        into[o + 10] = alpha;
    }

    /** The fire's light on the part of the face in {@link #corners}. */
    private void glow(float alpha) {
        if (alpha <= .01f) return;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int k = 0; k < 8; k += 2) {
            minX = Math.min(minX, corners[k]); maxX = Math.max(maxX, corners[k]);
            minY = Math.min(minY, corners[k + 1]); maxY = Math.max(maxY, corners[k + 1]);
        }
        if ((glowCount + 1) * GLOW > glows.length) glows = Arrays.copyOf(glows, glows.length * 2);
        int o = glowCount++ * GLOW;
        glows[o] = (minX + maxX) / 2f;
        glows[o + 1] = (minY + maxY) / 2f;
        glows[o + 2] = (maxX - minX) * 1.6f;
        glows[o + 3] = (maxY - minY) * 1.6f;
        glows[o + 4] = alpha;
    }

    /** Draws the faces' fire gathered this frame, over the faces and under the roofs. */
    void flushFaces(DrawList out, SpriteAPI tongues, SpriteAPI blob) {
        if (blob != null) {
            for (int i = 0; i < glowCount; i++) {
                int o = i * GLOW;
                out.addAdditiveSprite(RenderLayer.ROOFS, blob, glows[o], glows[o + 1], glows[o + 2], glows[o + 3], 0f,
                        1f, .45f, .12f, glows[o + 4]);
            }
        }
        emit(out, tongues, flames, flameCount);
    }

    /** Draws the flames rising over the walls' tops gathered this frame, over the roofs. */
    void flushCrowns(DrawList out, SpriteAPI tongues) {
        emit(out, tongues, crowns, crownCount);
    }

    private void emit(DrawList out, SpriteAPI tongues, float[] flames, int count) {
        if (tongues == null) return;
        for (int i = 0; i < count; i++) {
            int o = i * FLAME;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int k = 0; k < 8; k += 2) {
                minX = Math.min(minX, flames[o + k]); maxX = Math.max(maxX, flames[o + k]);
                minY = Math.min(minY, flames[o + k + 1]); maxY = Math.max(maxY, flames[o + k + 1]);
            }
            float w = Math.max(maxX - minX, 1e-3f), h = Math.max(maxY - minY, 1e-3f);
            float[] p = patch();
            for (int k = 0; k < 4; k++) {
                p[k * 2] = (flames[o + k * 2] - minX) / w;
                p[k * 2 + 1] = (flames[o + k * 2 + 1] - minY) / h;
                p[8 + k * 2] = k == 1 || k == 2 ? 1f : 0f;
                p[8 + k * 2 + 1] = k >= 2 ? 1f : 0f;
            }
            int col = (int) flames[o + 8], row = (int) flames[o + 9];
            out.addSheetPatch(RenderLayer.ROOFS, tongues, col * TONGUE_W, row * TONGUE_H, TONGUE_W, TONGUE_H,
                    (minX + maxX) / 2f, (minY + maxY) / 2f, w, h, flames[o + 10], p);
        }
    }

    private float[] patch() {
        if (patchesUsed == patches.length) patches = Arrays.copyOf(patches, patches.length * 2);
        float[] p = patches[patchesUsed];
        if (p == null) patches[patchesUsed] = p = new float[16];
        patchesUsed++;
        return p;
    }

    /**
     * The fire of burning building cell {@code idx} over the roofs: a flame bed at the height its roof or wall top
     * stood, centred on screen at {@code (x, y)}, {@code cellPx} wide there; then smoke and a spark rising off it
     * ({@code smoke} false leaves them to a neighbour). {@code (windX, windY)} is the wind on screen, a cell's drift.
     */
    void overRoof(DrawList out, FireField field, int idx, float x, float y, float cellPx, float alpha, boolean smoke,
                  float windX, float windY, SpriteAPI beds, SpriteAPI smokeSheet, SpriteAPI blob) {
        float heat = FaceFire.heat(field, idx);
        if (heat <= 0f) return;
        float big = FireRenderSystem.bigness(field.massAt(idx));
        if (beds != null) {
            int framePx = (int) (beds.getWidth() / BED_COLS);
            if (framePx > 0) {
                // Two beds a cell, each set off its middle, turned and sized its own way, so a burning roof's cells
                // run together into one ragged fire rather than a grid.
                for (int k = 0; k < 2; k++) {
                    int seed = idx * 3 + k;
                    float ox = (BuildingFaces.hash(seed, 86) - .5f) * .7f * cellPx;
                    float oy = (BuildingFaces.hash(seed, 87) - .5f) * .7f * cellPx;
                    float size = cellPx * (.95f + .55f * big + .5f * BuildingFaces.hash(seed, 88)) * (.75f + .4f * heat);
                    float angle = BuildingFaces.hash(seed, 75) * 360f + clock * (k == 0 ? 5f : -4f);
                    float t = clock * 7f + BuildingFaces.hash(seed, 76) * BED_LOOP;
                    int frame = Math.floorMod((int) Math.floor(t), BED_LOOP);
                    float blend = t - (float) Math.floor(t);
                    float a = alpha * Math.min(.85f, .35f + .55f * heat) * (k == 0 ? 1f : .7f);
                    bed(out, beds, framePx, frame, x + ox, y + oy, size, angle, a * (1f - blend));
                    bed(out, beds, framePx, (frame + 1) % BED_LOOP, x + ox, y + oy, size, angle, a * blend);
                }
            }
        }
        if (blob != null) {
            float s = cellPx * 2.2f;
            out.addAdditiveSprite(RenderLayer.ROOFS, blob, x, y, s, s, 0f, 1f, .42f, .1f, alpha * .3f * heat);
        }
        if (!smoke) return;
        if (smokeSheet != null) {
            int fw = (int) (smokeSheet.getWidth() / SMOKE_COLS), fh = (int) (smokeSheet.getHeight() / SMOKE_ROWS);
            if (fw > 0 && fh > 0) {
                // Two puffs a cell, half a life apart, rising and leaning downwind as they spread and thin.
                for (int k = 0; k < 2; k++) {
                    float life = (clock / SMOKE_LIFE + BuildingFaces.hash(idx, 77 + k) + k * .5f) % 1f;
                    int frame = (int) (BuildingFaces.hash(idx, 79 + k) * SMOKE_FRAMES) % SMOKE_FRAMES;
                    float drift = life * 1.6f;
                    float px = x + windX * drift;
                    float py = y + windY * drift + life * cellPx * 2.2f;
                    float size = cellPx * (1.2f + 1.8f * life) * (.8f + .4f * heat);
                    float a = alpha * .8f * (1f - life) * Math.min(1f, life * 6f) * (.5f + .5f * heat);
                    out.addSheetQuad(RenderLayer.ROOFS, smokeSheet, (frame % SMOKE_COLS) * fw,
                            (frame / SMOKE_COLS) * fh, fw, fh, px, py, size, size, life * 40f, SMOKE_GREY,
                            SMOKE_GREY - .02f, SMOKE_GREY - .03f, a);
                }
            }
        }
        if (blob != null && heat > .35f) {
            // A spark lifting off the hottest of it.
            float life = (clock * .8f + BuildingFaces.hash(idx, 81)) % 1f;
            float ex = x + (BuildingFaces.hash(idx, 82) - .5f) * cellPx * .9f + life * cellPx * .3f;
            float ey = y + life * cellPx * 2.4f;
            float s = cellPx * .28f;
            out.addAdditiveSprite(RenderLayer.ROOFS, blob, ex, ey, s, s, 0f, 1f, .7f, .28f, alpha * (1f - life));
        }
    }

    private static void bed(DrawList out, SpriteAPI sheet, int framePx, int frame, float x, float y, float size,
                            float angle, float alpha) {
        if (alpha <= .01f) return;
        out.addSheetQuad(RenderLayer.ROOFS, sheet, (frame % BED_COLS) * framePx, (frame / BED_COLS) * framePx,
                framePx, framePx, x, y, size, size, angle, 1f, 1f, 1f, alpha);
    }
}
