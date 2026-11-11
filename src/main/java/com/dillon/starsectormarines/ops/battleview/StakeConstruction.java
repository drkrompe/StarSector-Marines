package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.derelict.DerelictLook;
import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.FallenHullLook;
import com.dillon.starsectormarines.battle.derelict.StakeFootprint;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Map;

/**
 * A survey stake building up (`fallen-hull-nouns.md`, pick {@code stake-composed} (a)): its parts stay separate in
 * their slots ({@link StakeFootprint}), and each part of the coming stage builds in its slot while the stake's
 * {@link SurveyStake#progress} runs toward that stage, so when the rules stand the part it is already there. A part
 * builds in phases over its own share of the progress ({@link #share}): its foundation slab laid, a scaffold frame
 * going up, its plating closed over it from back to front with welding sparks along the seam, and its lamps coming on.
 * Builder drones flit between the spire and the part at work, and dust rises off the slab. The planted pylon drives
 * into the ground as the stake is planted, and the raised mast turns open out of it ({@link #spireMotion}). A
 * stranded stake's half-built parts stand where they stopped, dim and idle.
 *
 * <p>Presentation only: everything keys off the rules' stage and progress.
 */
final class StakeConstruction {

    /** Where each part builds in the progress toward its stage: from, to. */
    static final float[] CRADLE = {0.05f, 0.7f};
    static final float[] DRONE = {0.3f, 0.95f};
    static final float[] STORE = {0.05f, 0.75f};
    static final float[] POST_WEST = {0.25f, 0.85f};
    static final float[] POST_EAST = {0.35f, 0.95f};
    /** Within a part's own build: the slab, the frame, the plating, the lamps. */
    static final float SLAB_TO = 0.15f;
    static final float FRAME_TO = 0.45f;
    static final float PLATE_TO = 0.88f;
    /** The pylon driving in, and the mast turning open: their share of the stage's progress. */
    static final float DRIVE_SHARE = 0.06f;
    static final float UNFOLD_SHARE = 0.08f;
    /** The parts' drawn sizes, cells across: the cradle and store as their structures draw, the pad, a post. */
    static final float PAD_CELLS = 2.4f;
    static final float POST_CELLS = 1.4f;
    static final String PAD = "graphics/battle/fallen-hull/stake-pad.png";
    /** A finished part's slab, left under it. */
    static final float SLAB_ALPHA = 0.3f;

    private StakeConstruction() {
    }

    /**
     * How far {@code part} is built, 0 to 1, for a stake at {@code stage} and {@code progress} toward the next; -1 when it
     * is not built yet and not building. A part of a stage already reached is whole.
     */
    static float share(StakeFootprint.Part part, SurveyStake.Stage stage, float progress) {
        if (stage == SurveyStake.Stage.SCRAP) return -1f;
        if (StakeFootprint.parts(stage).contains(part)) return 1f;
        float[] window = window(part);
        if (window == null) return -1f;
        boolean nextStage = stage == SurveyStake.Stage.PLANTED
                ? StakeFootprint.parts(SurveyStake.Stage.RAISED).contains(part)
                : stage == SurveyStake.Stage.RAISED;
        if (!nextStage) return -1f;
        float q = (progress - window[0]) / (window[1] - window[0]);
        return q <= 0f ? -1f : Math.min(1f, q);
    }

    private static float[] window(StakeFootprint.Part part) {
        return switch (part) {
            case CRADLE -> CRADLE;
            case DRONE -> DRONE;
            case STORE -> STORE;
            case POST_WEST -> POST_WEST;
            case POST_EAST -> POST_EAST;
            case SPIRE -> null;
        };
    }

    /**
     * The spire's motion as its stage arrives: {scale, turn in degrees, dust share} for a stake at {@code stage} and
     * {@code progress}, or null once it stands still. The pylon drives in from above (drawn larger, nearer the camera);
     * the mast turns open out of it, growing.
     */
    static float[] spireMotion(SurveyStake.Stage stage, float progress) {
        if (stage == SurveyStake.Stage.PLANTED && progress < DRIVE_SHARE) {
            float t = SalvageWalkerArms.ease(progress / DRIVE_SHARE);
            return new float[] {1f + 0.6f * (1f - t), 0f, 1f - progress / DRIVE_SHARE};
        }
        if (stage == SurveyStake.Stage.RAISED && progress < UNFOLD_SHARE) {
            float t = SalvageWalkerArms.ease(progress / UNFOLD_SHARE);
            return new float[] {0.6f + 0.4f * t, 45f * (1f - t), 1f - progress / UNFOLD_SHARE};
        }
        return null;
    }

    /** The spire's motion for structure {@code id}, when it is a stake's pylon or mast; null otherwise. */
    static float[] spireMotion(DerelictSecurityDirector director, long id) {
        FallenHull hull = director == null ? null : director.hull();
        if (hull == null) return null;
        for (Map.Entry<Integer, SurveyStake> e : hull.stakes().entrySet()) {
            SurveyStake stake = e.getValue();
            Long pylon = hull.pylons().get(e.getKey());
            Long node = director.nodes().get(FallenHull.zoneOf(e.getKey()));
            boolean match = stake.stage() == SurveyStake.Stage.PLANTED ? pylon != null && pylon == id
                    : stake.stage() == SurveyStake.Stage.RAISED && node != null && node == id;
            if (match) return spireMotion(stake.stage(), stake.progress());
        }
        return null;
    }

    /** Every stake of the hull building up, and the pads and slabs under its finished parts. */
    static void draw(DrawList out, BattleCamera cam, BattleSprites sprites, DerelictSecurityDirector director,
                     ViewCull view, float time, float alpha) {
        FallenHull hull = director.hull();
        if (hull == null) return;
        SpriteAPI glow = sprites.engineGlowSprite();
        DerelictLook look = director.look();
        String cradlePath = (look == DerelictLook.CONTINGENCY ? FlockLivery.CONTINGENCY : FlockLivery.DOMAIN)
                .cradleSpritePath();
        StructureLookPaths posts = new StructureLookPaths(look.postLook(true).basePath(),
                look.postLook(true).spritePath());
        for (Map.Entry<Integer, SurveyStake> e : hull.stakes().entrySet()) {
            int[] seat = hull.seatOf(e.getKey());
            if (seat == null) continue;
            float half = StakeFootprint.HALF + 1f;
            if (!view.visible(seat[0] + 0.5f, seat[1] + 0.5f, 2f * half)) continue;
            SurveyStake stake = e.getValue();
            boolean working = stake.builds();
            float[] spire = spireMotion(stake.stage(), stake.progress());
            if (spire != null && working) {
                dust(out, cam, glow, seat[0] + 0.5f, seat[1] + 0.5f, 2.2f, spire[2], time, e.getKey(), alpha);
            }
            int seed = e.getKey() * 7919;
            for (StakeFootprint.Slot slot : StakeFootprint.SLOTS) {
                if (slot.part() == StakeFootprint.Part.SPIRE) continue;
                float q = share(slot.part(), stake.stage(), stake.progress());
                if (q < 0f) continue;
                int[] c = slot.centre(seat[0], seat[1]);
                float cx = c[0] + 0.5f;
                float cy = c[1] + 0.5f;
                float span = switch (slot.part()) {
                    case CRADLE -> FlockLivery.CRADLE_VISUAL_CELLS;
                    case DRONE -> PAD_CELLS;
                    case STORE -> FallenHullLook.STAKE_STORE.cells();
                    default -> POST_CELLS;
                } * 0.85f;
                // Darker while it is a building site, settling to a faint footing once the part stands.
                float slabAlpha = q < 1f ? 1.6f * SLAB_ALPHA : SLAB_ALPHA;
                slab(out, cam, cx, cy, span, Math.min(1f, q / SLAB_TO) * slabAlpha * (working ? 1f : 0.7f) * alpha);
                if (slot.part() == StakeFootprint.Part.DRONE) {
                    // The pad is the stake's own: it stays once built, under the drone it serves.
                    part(out, cam, sprites.spriteByPath(PAD), null, cx, cy, PAD_CELLS, q, working, glow, time,
                            seed + 1, alpha);
                } else if (q < 1f) {
                    String path = switch (slot.part()) {
                        case CRADLE -> cradlePath;
                        case STORE -> FallenHullLook.STAKE_STORE.path();
                        default -> posts.base;
                    };
                    float cells = switch (slot.part()) {
                        case CRADLE -> FlockLivery.CRADLE_VISUAL_CELLS;
                        case STORE -> FallenHullLook.STAKE_STORE.cells();
                        default -> POST_CELLS;
                    };
                    ShuttleSpriteCache top = slot.part() == StakeFootprint.Part.POST_WEST
                            || slot.part() == StakeFootprint.Part.POST_EAST ? sprites.spriteByPath(posts.head) : null;
                    part(out, cam, sprites.spriteByPath(path), top, cx, cy, cells, q, working, glow, time,
                            seed + slot.part().ordinal(), alpha);
                    if (working && q > SLAB_TO && q < PLATE_TO) {
                        drones(out, cam, glow, seat[0] + 0.5f, seat[1] + 0.5f, cx, cy, time,
                                seed + slot.part().ordinal(), alpha);
                    }
                    if (working && q < FRAME_TO) dust(out, cam, glow, cx, cy, span * 0.5f, 0.6f, time, seed, alpha);
                }
            }
        }
    }

    /** A post's base and its folded head. */
    private record StructureLookPaths(String base, String head) { }

    /** The foundation slab: dark plate, a little past the slot's edge. */
    private static void slab(DrawList out, BattleCamera cam, float cx, float cy, float span, float a) {
        if (a <= 0f) return;
        float h = span * 0.5f;
        out.addSolidRect(RenderLayer.DOODADS, cam.cellToScreenX(cx - h), cam.cellToScreenY(cy - h),
                cam.cellToScreenX(cx + h), cam.cellToScreenY(cy + h), 0.17f, 0.16f, 0.15f, a);
    }

    /**
     * A part {@code q} built: its frame going up, its plating closing over it from the back, its lamps coming on.
     * {@code top} is a second layer over the first (a post's head), or null.
     */
    private static void part(DrawList out, BattleCamera cam, ShuttleSpriteCache art, ShuttleSpriteCache top, float cx,
                             float cy, float cells, float q, boolean working, SpriteAPI glow, float time, int seed,
                             float alpha) {
        float ppc = cam.cellPxSize();
        float w = cells * ppc;
        float h = art == null ? w : w / Math.max(0.01f, art.aspect);
        float sx = cam.cellToScreenX(cx);
        float sy = cam.cellToScreenY(cy);
        float dim = working ? 1f : 0.7f;
        // The frame: its members going up one by one, each growing from its foot.
        float frame = Math.max(0f, Math.min(1f, (q - SLAB_TO) / (FRAME_TO - SLAB_TO)));
        if (frame > 0f) {
            float x0 = sx - w * 0.42f;
            float x1 = sx + w * 0.42f;
            float y0 = sy - h * 0.42f;
            float y1 = sy + h * 0.42f;
            float[][] members = {
                    {x0, y0, x0, y1}, {x1, y0, x1, y1}, {x0, y0, x1, y0}, {x0, y1, x1, y1},
                    {x0, y0, x1, y1}, {x1, y0, x0, y1}, {sx, y0, sx, y1}, {x0, sy, x1, sy}};
            float fade = q < PLATE_TO ? 1f : Math.max(0f, 1f - (q - PLATE_TO) / (1f - PLATE_TO));
            for (int k = 0; k < members.length; k++) {
                float g = Math.max(0f, Math.min(1f, frame * members.length - k));
                if (g <= 0f) break;
                float[] m = members[k];
                out.addLine(RenderLayer.UNITS, m[0], m[1], m[0] + (m[2] - m[0]) * g, m[1] + (m[3] - m[1]) * g,
                        Math.max(1.5f, ppc * 0.05f), 0.55f * dim, 0.53f * dim, 0.5f * dim, 0.9f * fade * alpha);
            }
        }
        // The plating: the part's own image closed over the frame from its back (north) edge toward its front.
        float plate = Math.max(0f, Math.min(1f, (q - FRAME_TO) / (PLATE_TO - FRAME_TO)));
        if (plate > 0f && art != null) {
            float shade = (0.55f + 0.45f * plate) * dim;
            reveal(out, art, sx, sy, w, h, plate, shade, alpha);
            if (top != null) {
                float th = w / Math.max(0.01f, top.aspect);
                reveal(out, top, sx, sy, w, th, plate, shade, alpha);
            }
            if (working && plate < 1f && glow != null) {
                // Welding along the seam where the plating meets the frame.
                float seamY = sy + h / 2f - plate * h;
                for (int j = 0; j < 3; j++) {
                    float px = sx + (SalvageWalkerArms.hash(seed, (int) (time * 3f) * 7 + j) - 0.5f) * w * 0.8f;
                    float flick = SalvageWalkerArms.hash(seed + j, (int) (time * 20f));
                    float s = (0.18f + 0.12f * flick) * ppc;
                    out.addAdditiveSprite(RenderLayer.UNITS, glow, px, seamY, s, s, 0f, 0.7f, 0.85f, 1f,
                            (0.6f + 0.4f * flick) * alpha);
                    sparks(out, ppc, px, seamY, time, seed * 31 + j, alpha);
                }
            }
        }
        // The lamps: on at the end, blinking a while.
        float lamps = Math.max(0f, (q - PLATE_TO) / (1f - PLATE_TO));
        if (lamps > 0f && q < 1f && working && glow != null) {
            for (int k = 0; k < 4; k++) {
                float lx = sx + (k % 2 == 0 ? -1f : 1f) * w * 0.38f;
                float ly = sy + (k < 2 ? -1f : 1f) * h * 0.38f;
                boolean on = SalvageWalkerArms.hash(seed + k, (int) (time * 4f)) < 0.3f + 0.7f * lamps;
                if (!on) continue;
                float s = 0.22f * ppc;
                out.addAdditiveSprite(RenderLayer.UNITS, glow, lx, ly, s, s, 0f, 1f, 0.75f, 0.3f, 0.85f * alpha);
            }
        }
    }

    /** {@code art} shown from its top (north) edge down to {@code share} of its height. */
    private static void reveal(DrawList out, ShuttleSpriteCache art, float sx, float sy, float w, float h, float share,
                               float shade, float alpha) {
        int rows = Math.max(1, Math.round(art.pxH * share));
        float shownH = h * rows / (float) art.pxH;
        out.addImageRegion(RenderLayer.UNITS, art.sprite, art.pxW, art.pxH, 0, 0, art.pxW, rows, sx,
                sy + h / 2f - shownH / 2f, w, shownH, 0f, shade, shade, shade * 0.97f, alpha);
    }

    /** A welder's sparks at (x, y), screen px. */
    private static void sparks(DrawList out, float ppc, float x, float y, float time, int seed, float alpha) {
        for (int j = 0; j < 5; j++) {
            float period = 0.25f + 0.15f * SalvageWalkerArms.hash(seed, 900 + j);
            float ph = time / period + SalvageWalkerArms.hash(seed, 500 + j);
            int cycle = (int) Math.floor(ph);
            float p = ph - cycle;
            double heading = SalvageWalkerArms.hash(seed * 13 + j, cycle) * Math.PI * 2;
            float d = (0.15f + 0.4f * SalvageWalkerArms.hash(seed * 17 + j, cycle)) * p * ppc;
            float dx = (float) Math.cos(heading);
            float dy = (float) Math.sin(heading);
            float tail = Math.max(0f, d - 0.08f * ppc);
            out.addLine(RenderLayer.UNITS, x + dx * tail, y + dy * tail, x + dx * d, y + dy * d, 1.5f, 1f,
                    0.85f - 0.4f * p, 0.45f - 0.3f * p, (1f - p) * alpha);
        }
    }

    /** Two builder drones flitting between the spire at (ax, ay) and the part at (bx, by), cells. */
    private static void drones(DrawList out, BattleCamera cam, SpriteAPI glow, float ax, float ay, float bx, float by,
                               float time, int seed, float alpha) {
        if (glow == null) return;
        float ppc = cam.cellPxSize();
        for (int k = 0; k < 2; k++) {
            float period = 2.6f + 0.8f * k;
            float ph = time / period + 0.5f * k + SalvageWalkerArms.hash(seed, k);
            float t = 0.5f - 0.5f * (float) Math.cos(ph * Math.PI * 2);
            float x = ax + (bx - ax) * (0.25f + 0.7f * t) + 0.35f * (float) Math.sin(time * 3.1f + k * 2f + seed);
            float y = ay + (by - ay) * (0.25f + 0.7f * t) + 0.35f * (float) Math.cos(time * 2.7f + k * 3f + seed);
            float sx = cam.cellToScreenX(x);
            float sy = cam.cellToScreenY(y);
            // Its shadow on the ground, its body, its lamp.
            float body = 0.22f * ppc;
            out.addSprite(RenderLayer.UNITS, glow, sx + 0.25f * ppc, sy - 0.3f * ppc, body * 1.4f, body * 1.4f, 0f,
                    0f, 0f, 0f, 0.35f * alpha);
            out.addSolidRect(RenderLayer.UNITS, sx - body / 2f, sy - body / 2f, sx + body / 2f, sy + body / 2f, 0.3f,
                    0.31f, 0.33f, alpha);
            float lamp = 0.3f * ppc;
            out.addAdditiveSprite(RenderLayer.UNITS, glow, sx, sy, lamp, lamp, 0f, 0.5f, 0.85f, 1f, 0.8f * alpha);
        }
    }

    /** Dust rising round (x, y), cells, {@code radius} across, at {@code amount}. */
    private static void dust(DrawList out, BattleCamera cam, SpriteAPI glow, float x, float y, float radius,
                             float amount, float time, int seed, float alpha) {
        if (glow == null || amount <= 0f) return;
        float ppc = cam.cellPxSize();
        for (int j = 0; j < 5; j++) {
            float ph = time / 1.6f + j / 5f;
            int cycle = (int) Math.floor(ph);
            float p = ph - cycle;
            double heading = SalvageWalkerArms.hash(seed * 11 + j, cycle) * Math.PI * 2;
            float d = radius * (0.4f + 0.6f * p);
            float size = (0.6f + 1.2f * p) * ppc;
            out.addSprite(RenderLayer.UNITS, glow, cam.cellToScreenX(x + (float) Math.cos(heading) * d),
                    cam.cellToScreenY(y + (float) Math.sin(heading) * d), size, size, 0f, 0.46f, 0.41f, 0.34f,
                    0.35f * amount * (1f - p) * alpha);
        }
    }
}
