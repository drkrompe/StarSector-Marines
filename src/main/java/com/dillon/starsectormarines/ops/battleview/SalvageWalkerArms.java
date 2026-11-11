package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegPose;
import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.battle.derelict.HaulerWork;
import com.dillon.starsectormarines.battle.derelict.Salvage;
import com.dillon.starsectormarines.battle.derelict.StripperWork;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.List;

/**
 * The salvage walkers' work arms and what their work throws off (`fallen-hull-nouns.md`, Stripper and Hauler), drawn
 * over the walker {@link DerelictWalkerComposer} has drawn. The arms unfold while the rules have the walker at work and
 * tuck against its body while it walks, easing between over {@link #UNFOLD_SECONDS}; everything keys off the rules'
 * goal ({@link StripperWork#goal()}, {@link HaulerWork#goal()}) and load, never off what the walker seems to be doing.
 *
 * <ul>
 *   <li>The stripper's two saw booms telescope out to the salvage it cuts, the saws turning, with a cutting glow,
 *   sparks, metal chips and dust where the saw meets the metal; the pile it cuts is drawn shrinking
 *   ({@link #piles}).</li>
 *   <li>The hauler's two grab arms reach to the stripper it takes from, clamp with a clank of debris, and swing a clump
 *   into the bed, which fills a grab at a time; at a store they push the load out over the bed's tail, spilling it.</li>
 * </ul>
 *
 * <p>The rules hand a stripper's whole load over in one pulse; the bed shows it arriving a grab at a time
 * ({@link State}), so the screen runs a little behind the rules and never ahead.
 */
final class SalvageWalkerArms {

    /** Folded to working, or back, seconds. */
    static final float UNFOLD_SECONDS = 0.6f;
    /** One grab: reach, clamp, swing to the bed, release, back. */
    static final float GRAB_SECONDS = 1.3f;
    /** Where in a grab the clamp shuts (the clank) and where the clump is let go into the bed. */
    static final float CLAMP_AT = 0.45f;
    static final float RELEASE_AT = 0.8f;
    /** The share of the bed one grab carries. */
    static final float GRAB_SHARE = 0.2f;
    /** A full bed pushed out, seconds. */
    static final float DUMP_SECONDS = 1.6f;
    /** How long a clank's puff hangs, seconds. */
    static final float PUFF_SECONDS = 0.7f;
    private static final float EPS = 0.005f;

    /** The stripper's shoulders (work_parts.py), as shares of its torso image across and down; the left one. */
    static final float SHOULDER_U = 96f / 307f;
    static final float SHOULDER_V = 150f / 297f;
    /** Shoulder to saw hub, folded, in the torso image's px. */
    static final float HUB_PX = 103f;
    /** The furthest the booms telescope past their own length, cells. */
    static final float MAX_EXTENSION_CELLS = 2.4f;
    /**
     * How far a working boom turns across the body's front from its own side, degrees; to its own side it turns all the
     * way round, so a stripper cutting what lies behind it reaches back. And where a folded one lies.
     */
    static final float WORK_SWING = 45f;
    static final float FOLD_SWING = 160f;
    static final float SAW_DEGREES_PER_SECOND = 900f;
    /** The booms and saws drawn larger than the torso's own cut of them, so they read off the body. */
    static final float STRIPPER_ARM_DRAW = 1.5f;

    /** The hauler's arm mounts at the bed's front corners, and the bed's inner box, as shares of its torso image. */
    static final float MOUNT_U = 40f / 261f;
    static final float MOUNT_V = 105f / 253f;
    static final float BED_U0 = 60f / 261f;
    static final float BED_V0 = 121f / 253f;
    static final float BED_U1 = 202f / 261f;
    static final float BED_V1 = 201f / 253f;
    /** The hauler's arms are its own leg parts at this share of their size. */
    static final float ARM_SCALE = 0.42f;
    /** The hauler's arm segments: the stripper's boom at this share of its authored size. */
    static final float HAULER_BOOM_SCALE = 1.25f;
    static final float MOUNT_HEIGHT = 0.45f;

    /** A salvage heap at full metal, cells across. */
    static final float PILE_CELLS = 1.5f;

    private SalvageWalkerArms() {
    }

    /** One walker's arms between frames: how far unfolded, what the bed shows, where a grab or tip stands. */
    static final class State {
        /** 0 tucked, 1 working. */
        float unfold;
        /** The bed's shown share of a full load. */
        float shown;
        /** Where in a grab, or -1 between grabs. */
        float grab = -1f;
        /** The shown share when the present tip began, or 0. */
        float dumpFrom;
        float spin;
        float time;
        /** The battle tick it was last drawn at, or -1. */
        int lastTick = -1;
        /** Where the arms reach for a grab, cells: the stripper the rules last had it take from. */
        float grabX;
        float grabY;
        boolean hasGrab;
        /** Seconds since the last clamp shut, and where. */
        float puffAge = Float.MAX_VALUE;
        float puffX;
        float puffY;

        /** A stripper's frame: at work while the rules have it cutting. */
        void stripper(float dt, boolean cutting) {
            time += dt;
            unfold = toward(unfold, cutting ? 1f : 0f, dt / UNFOLD_SECONDS);
            spin += dt * SAW_DEGREES_PER_SECOND * (cutting ? unfold : 0f);
        }

        /**
         * A hauler's frame.
         *
         * @param kind  the rules' goal
         * @param share its load as a share of {@link HaulerWork#CAPACITY}
         */
        void hauler(float dt, HaulerWork.Kind kind, float goalX, float goalY, float share) {
            time += dt;
            puffAge += dt;
            if (kind == HaulerWork.Kind.TAKE) {
                grabX = goalX;
                grabY = goalY;
                hasGrab = true;
            }
            if (Math.abs(share - shown) <= EPS) shown = share;
            boolean filling = share > shown + EPS;
            boolean dumping = share < shown - EPS;
            if (filling && !hasGrab) {
                // Loaded with nothing seen to load it: shown as it is.
                shown = share;
                filling = false;
            }
            boolean busy = grab >= 0f || filling || dumping || kind == HaulerWork.Kind.TAKE
                    || kind == HaulerWork.Kind.UNLOAD;
            unfold = toward(unfold, busy ? 1f : 0f, dt / UNFOLD_SECONDS);
            if (!dumping) dumpFrom = 0f;
            if (unfold < 1f) return;
            if (grab < 0f && filling) grab = 0f;
            if (grab >= 0f) {
                float before = grab;
                grab += dt / GRAB_SECONDS;
                if (before < CLAMP_AT && grab >= CLAMP_AT) {
                    puffAge = 0f;
                    puffX = grabX;
                    puffY = grabY;
                }
                if (before < RELEASE_AT && grab >= RELEASE_AT) shown = Math.min(share, shown + GRAB_SHARE);
                if (grab >= 1f) grab = -1f;
            } else if (dumping) {
                if (dumpFrom <= 0f) dumpFrom = shown;
                shown = Math.max(share, shown - dt / DUMP_SECONDS);
            }
        }

        /** Whether the bed is being tipped out now. */
        boolean dumping() {
            return dumpFrom > 0f;
        }
    }

    /** {@code from} moved toward {@code to} by at most {@code step}. */
    static float toward(float from, float to, float step) {
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
    }

    /** The eased pose for an unfold share: slow out of either rest. */
    static float ease(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return c * c * (3f - 2f * c);
    }

    // ---- the stripper ----

    /**
     * The stripper's two saw booms over its drawn body.
     *
     * @param goal its goal, or null
     * @param glow a soft round light for the cut, or null
     */
    static void stripper(DrawList out, DerelictWalkerAssets art, DerelictLegRig rig, BattleCamera cam,
                         float renderScale, State st, StripperWork.Goal goal, SpriteAPI glow, float alpha) {
        if (art.arm == null || art.saw == null) return;
        float e = ease(st.unfold);
        float f = rig.bodyFacing();
        float k = torsoCells(rig, renderScale) / art.torso.pxWidth;
        float ka = k * STRIPPER_ARM_DRAW;
        float ppc = cam.cellPxSize();
        boolean cutting = goal != null && goal.kind() == StripperWork.Kind.CUT;
        for (int side = -1; side <= 1; side += 2) {
            float lx = side * (0.5f - SHOULDER_U) * art.torso.pxWidth * k;
            float ly = (0.5f - SHOULDER_V) * art.torso.pxHeight * k;
            float sx = worldX(rig, f, lx, ly);
            float sy = worldY(rig, f, lx, ly);
            float base = HUB_PX * ka;
            float work = 0f;
            float ext = 0f;
            if (cutting) {
                float rel = wrap(bearing(goal.x() - sx, goal.y() - sy) - f);
                // The left boom (side -1) turns counter-clockwise, to its own side, as far as straight back.
                work = side < 0 ? Math.max(-WORK_SWING, rel < -WORK_SWING ? rel + 360f : rel)
                        : Math.min(WORK_SWING, rel > WORK_SWING ? rel - 360f : rel);
                work = side < 0 ? Math.min(work, 180f + WORK_SWING) : Math.max(work, -180f - WORK_SWING);
                float d = (float) Math.hypot(goal.x() - sx, goal.y() - sy);
                ext = Math.max(0f, Math.min(MAX_EXTENSION_CELLS, d - base - 0.5f * art.saw.pxWidth * ka));
            }
            float fold = side < 0 ? FOLD_SWING : -FOLD_SWING;
            float rel = fold + (work - fold) * e;
            float reach = base + ext * e;
            float a = f + rel;
            float dx = dirX(a);
            float dy = dirY(a);
            // The boom telescopes: a sleeve at its own length, thinner tubes sliding out of it to the saw's mount.
            float boomLen = art.arm.pxHeight * ka;
            float boomW = art.arm.pxWidth * ka;
            float tip = reach - (HUB_PX - art.arm.pxHeight) * ka;
            int tubes = Math.max(1, (int) Math.ceil(tip / boomLen - 1e-3f));
            for (int i = tubes - 1; i >= 0; i--) {
                float start = tubes == 1 ? 0f : i * (tip - boomLen) / (tubes - 1);
                float c = start + boomLen / 2f;
                float shade = (float) Math.pow(0.86f, i);
                out.addSprite(RenderLayer.UNITS, art.arm.sprite, cam.cellToScreenX(sx + dx * c),
                        cam.cellToScreenY(sy + dy * c), boomW * (float) Math.pow(0.78f, i) * ppc, boomLen * ppc, a,
                        shade, shade, shade, alpha);
            }
            float hx = sx + dx * reach;
            float hy = sy + dy * reach;
            float saw = art.saw.pxWidth * ka;
            out.addSprite(RenderLayer.UNITS, art.saw.sprite, cam.cellToScreenX(hx), cam.cellToScreenY(hy), saw * ppc,
                    saw * ppc, a + side * st.spin, 1f, 1f, 1f, alpha);
            if (cutting && e > 0.9f) {
                // Where the saw's rim meets the metal: toward the salvage from the hub.
                float tx = goal.x() - hx;
                float ty = goal.y() - hy;
                float tl = (float) Math.hypot(tx, ty);
                if (tl < 1e-3f) {
                    tx = dx;
                    ty = dy;
                    tl = 1f;
                }
                tx /= tl;
                ty /= tl;
                float rim = 0.42f * saw;
                // Sparks fly off the turning rim, along it.
                float spray = side;
                cut(out, cam, glow, hx + tx * rim, hy + ty * rim, -ty * spray, tx * spray, goal.x(), goal.y(),
                        st.time, (int) (goal.salvageId() * 7 + side), alpha);
            }
        }
    }

    /** The cut at (x, y): a glow, sparks sprayed along (sx, sy), and chips and dust off the salvage at (px, py). */
    private static void cut(DrawList out, BattleCamera cam, SpriteAPI glow, float x, float y, float sx, float sy,
                            float px, float py, float time, int seed, float alpha) {
        float ppc = cam.cellPxSize();
        float ex = cam.cellToScreenX(x);
        float ey = cam.cellToScreenY(y);
        float flicker = hash(seed, (int) (time * 24f));
        if (glow != null) {
            float g = (0.5f + 0.25f * flicker) * ppc;
            out.addAdditiveSprite(RenderLayer.UNITS, glow, ex, ey, g, g, 0f, 1f, 0.55f, 0.2f,
                    (0.55f + 0.35f * flicker) * alpha);
            float core = 0.16f * ppc;
            out.addAdditiveSprite(RenderLayer.UNITS, glow, ex, ey, core, core, 0f, 1f, 0.95f, 0.8f, 0.9f * alpha);
        }
        for (int j = 0; j < 14; j++) {
            float period = 0.28f + 0.14f * hash(seed, 900 + j);
            float ph = time / period + hash(seed, 500 + j);
            int cycle = (int) Math.floor(ph);
            float p = ph - cycle;
            float turn = (hash(seed * 31 + j, cycle) - 0.5f) * 1.5f;
            float speed = 1.4f + 2.2f * hash(seed * 17 + j, cycle);
            float cs = (float) Math.cos(turn);
            float sn = (float) Math.sin(turn);
            float vx = sx * cs - sy * sn;
            float vy = sx * sn + sy * cs;
            float d = speed * p * period;
            float tail = Math.max(0f, d - 0.14f);
            out.addLine(RenderLayer.UNITS, ex + vx * tail * ppc, ey + vy * tail * ppc, ex + vx * d * ppc,
                    ey + vy * d * ppc, 2f, 1f, 0.85f - 0.45f * p, 0.4f - 0.3f * p, (1f - p) * alpha);
        }
        float ox = cam.cellToScreenX(px);
        float oy = cam.cellToScreenY(py);
        for (int j = 0; j < 7; j++) {
            float period = 0.7f + 0.3f * hash(seed, 300 + j);
            float ph = time / period + hash(seed, 700 + j);
            int cycle = (int) Math.floor(ph);
            float p = ph - cycle;
            double heading = hash(seed * 13 + j, cycle) * Math.PI * 2;
            float d = (0.25f + 0.5f * hash(seed * 5 + j, cycle)) * (1f - (1f - p) * (1f - p)) * ppc;
            float cx = ox + (float) Math.cos(heading) * d;
            float cy = oy + (float) Math.sin(heading) * d;
            float s = 2.5f + 2.5f * hash(seed * 3 + j, cycle);
            float tone = 0.3f + 0.25f * hash(seed + j, cycle);
            out.addSolidRect(RenderLayer.UNITS, cx - s, cy - s, cx + s, cy + s, tone, tone * 0.93f, tone * 0.85f,
                    (1f - p * p) * alpha);
        }
        if (glow == null) return;
        for (int j = 0; j < 3; j++) {
            float period = 1.4f;
            float ph = time / period + j / 3f;
            int cycle = (int) Math.floor(ph);
            float p = ph - cycle;
            double heading = hash(seed * 11 + j, cycle) * Math.PI * 2;
            float d = 0.35f * p * ppc;
            float size = (0.45f + 0.7f * p) * ppc;
            out.addSprite(RenderLayer.UNITS, glow, ox + (float) Math.cos(heading) * d,
                    oy + (float) Math.sin(heading) * d, size, size, 0f, 0.42f, 0.38f, 0.32f,
                    0.38f * (1f - p) * alpha);
        }
    }

    // ---- the hauler ----

    /**
     * The hauler's bed load and two grab arms over its drawn body.
     *
     * @param partScale its leg parts' size against the heavy's ({@link DerelictWalkerComposer#emit})
     */
    static void hauler(DrawList out, DerelictWalkerAssets art, DerelictLegRig rig, BattleCamera cam,
                       float renderScale, float partScale, State st, SpriteAPI glow, float alpha) {
        float f = rig.bodyFacing();
        float k = torsoCells(rig, renderScale) / art.torso.pxWidth;
        float w = art.torso.pxWidth * k;
        float h = art.torso.pxHeight * k;
        float ppc = cam.cellPxSize();
        float bedX = ((BED_U0 + BED_U1) / 2f - 0.5f) * w;
        float bedY = (0.5f - (BED_V0 + BED_V1) / 2f) * h;
        float bedW = (BED_U1 - BED_U0) * w;
        float bedH = (BED_V1 - BED_V0) * h;
        float rearY = (0.5f - BED_V1) * h;
        if (art.loads != null) {
            float s = st.shown * DerelictWalkerAssets.LOAD_STAGES;
            int full = (int) Math.floor(s + 1e-4f);
            float part = s - full;
            // Tipping out, the load slides back over the tail.
            float slide = st.dumping() ? (1f - st.shown / st.dumpFrom) * 0.25f * bedH : 0f;
            float cx = worldX(rig, f, bedX, bedY - slide);
            float cy = worldY(rig, f, bedX, bedY - slide);
            if (full >= 1) {
                LayeredSpriteCache l = art.loads[Math.min(full, DerelictWalkerAssets.LOAD_STAGES) - 1];
                out.addSprite(RenderLayer.UNITS, l.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy), bedW * ppc,
                        bedH * ppc, f, 1f, 1f, 1f, alpha);
            }
            if (full < DerelictWalkerAssets.LOAD_STAGES && part > 0.01f) {
                LayeredSpriteCache l = art.loads[full];
                out.addSprite(RenderLayer.UNITS, l.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy), bedW * ppc,
                        bedH * ppc, f, 1f, 1f, 1f, part * alpha);
            }
        }
        float e = ease(st.unfold);
        // Each arm two boom segments (the leg parts, smaller, where the boom is missing).
        LayeredSpriteCache upper = art.arm != null ? art.arm : art.thigh;
        LayeredSpriteCache fore = art.arm != null ? art.arm : art.shin;
        float scale = (art.arm != null ? HAULER_BOOM_SCALE : ARM_SCALE) * partScale;
        float thigh = upper.pxHeight / DerelictWalkerAssets.PX_PER_CELL * scale;
        float shin = fore.pxHeight / DerelictWalkerAssets.PX_PER_CELL * scale;
        float reach = 0.92f * (thigh + shin);
        float[] knee = new float[3];
        for (int side = -1; side <= 1; side += 2) {
            float mx = side * (0.5f - MOUNT_U) * w;
            float my = (0.5f - MOUNT_V) * h;
            float wx = worldX(rig, f, mx, my);
            float wy = worldY(rig, f, mx, my);
            // Tucked: the hand drawn in over the bed's front corner, the arm folded up off the deck.
            float tx = worldX(rig, f, mx * 0.55f, my - 0.1f);
            float ty = worldY(rig, f, mx * 0.55f, my - 0.1f);
            float tz = MOUNT_HEIGHT + 0.25f;
            // At rest at work: out ahead of the mount, low.
            float rx = worldX(rig, f, mx * 1.2f, my + 0.55f * (thigh + shin));
            float ry = worldY(rig, f, mx * 1.2f, my + 0.55f * (thigh + shin));
            float hx = rx;
            float hy = ry;
            float hz = 0.15f;
            boolean carrying = false;
            float bx = worldX(rig, f, bedX + side * 0.15f * bedW, bedY);
            float by = worldY(rig, f, bedX + side * 0.15f * bedW, bedY);
            if (st.grab >= 0f) {
                // The stripper, each hand to its own side of it, no further than the arm reaches.
                float px = st.grabX + dirX(f - 90f) * side * 0.12f;
                float py = st.grabY + dirY(f - 90f) * side * 0.12f;
                float d = (float) Math.hypot(px - wx, py - wy);
                if (d > reach) {
                    px = wx + (px - wx) * reach / d;
                    py = wy + (py - wy) * reach / d;
                }
                float g = st.grab;
                if (g < 0.4f) {
                    float t = ease(g / 0.4f);
                    hx = rx + (px - rx) * t;
                    hy = ry + (py - ry) * t;
                    hz = 0.15f - 0.05f * t;
                } else if (g < 0.5f) {
                    hx = px;
                    hy = py;
                    hz = 0.1f;
                } else if (g < RELEASE_AT) {
                    float t = ease((g - 0.5f) / (RELEASE_AT - 0.5f));
                    hx = px + (bx - px) * t;
                    hy = py + (by - py) * t;
                    hz = 0.1f + 0.5f * t;
                    carrying = true;
                } else {
                    float t = ease((g - RELEASE_AT) / (1f - RELEASE_AT));
                    hx = bx + (rx - bx) * t;
                    hy = by + (ry - by) * t;
                    hz = 0.6f - 0.45f * t;
                }
            } else if (st.dumping()) {
                // Pushing the load out over the tail.
                float p = 1f - st.shown / st.dumpFrom;
                float lift = 0.5f * (1f - p);
                hx = worldX(rig, f, mx * 0.5f, bedY + (rearY - bedY) * p);
                hy = worldY(rig, f, mx * 0.5f, bedY + (rearY - bedY) * p);
                hz = 0.55f + lift * 0.2f;
            }
            float fx = tx + (hx - tx) * e;
            float fy = ty + (hy - ty) * e;
            float fz = tz + (hz - tz) * e;
            DerelictLegPose.knee(wx, wy, MOUNT_HEIGHT, fx, fy, fz, thigh, shin, knee);
            LayeredUnitComposer.SpriteEmitter emit = (layer, x, y, ww, hh, angle, cr, cg, cb, ca) -> out.addSprite(
                    RenderLayer.UNITS, layer.sprite, x, y, ww, hh, angle, cr, cg, cb, ca);
            DerelictWalkerComposer.bone(emit, fore, cam, knee[0], knee[1], fx, fy, (knee[2] + fz) / 2f, 0f, 0.92f,
                    scale, alpha);
            DerelictWalkerComposer.bone(emit, upper, cam, wx, wy, knee[0], knee[1], (MOUNT_HEIGHT + knee[2]) / 2f,
                    0f, 1f, scale, alpha);
            float joint = art.joint.pxWidth / DerelictWalkerAssets.PX_PER_CELL * ARM_SCALE * 1.4f * partScale * ppc;
            out.addSprite(RenderLayer.UNITS, art.joint.sprite, cam.cellToScreenX(knee[0]), cam.cellToScreenY(knee[1]),
                    joint, joint, f, 1f, 1f, 1f, alpha);
            // The clamp: the joint's plate again at the hand, a clump of scrap in it on the swing to the bed.
            float clamp = joint * 1.25f;
            out.addSprite(RenderLayer.UNITS, art.joint.sprite, cam.cellToScreenX(fx), cam.cellToScreenY(fy), clamp,
                    clamp, f + 45f, 0.8f, 0.78f, 0.74f, alpha);
            if (carrying && art.loads != null) {
                LayeredSpriteCache l = art.loads[DerelictWalkerAssets.LOAD_STAGES - 1];
                float c = 0.32f * ppc;
                out.addSprite(RenderLayer.UNITS, l.sprite, cam.cellToScreenX(fx), cam.cellToScreenY(fy), c,
                        c * 0.7f, f + side * 30f, 1f, 1f, 1f, alpha);
            }
        }
        if (st.puffAge < PUFF_SECONDS) {
            puff(out, cam, glow, st.puffX, st.puffY, st.puffAge / PUFF_SECONDS, (int) (st.puffX * 31 + st.puffY * 17),
                    alpha);
        }
        if (st.dumping()) {
            // The load spilling off the tail: debris and dust behind the bed.
            float ox = worldX(rig, f, bedX, rearY);
            float oy = worldY(rig, f, bedX, rearY);
            spill(out, cam, glow, ox, oy, dirX(f + 180f), dirY(f + 180f), bedW,
                    dirX(f - 90f), dirY(f - 90f), st.time, alpha);
        }
    }

    /** A clank's puff at (x, y), {@code p} of the way through: a flash, dust, and a few flakes knocked off. */
    private static void puff(DrawList out, BattleCamera cam, SpriteAPI glow, float x, float y, float p, int seed,
                             float alpha) {
        float ppc = cam.cellPxSize();
        float ex = cam.cellToScreenX(x);
        float ey = cam.cellToScreenY(y);
        if (glow != null) {
            if (p < 0.15f) {
                float s = 0.35f * ppc;
                out.addAdditiveSprite(RenderLayer.UNITS, glow, ex, ey, s, s, 0f, 1f, 0.8f, 0.5f,
                        (1f - p / 0.15f) * 0.8f * alpha);
            }
            for (int j = 0; j < 4; j++) {
                double heading = hash(seed, j) * Math.PI * 2;
                float d = 0.3f * p * ppc;
                float size = (0.35f + 0.6f * p) * ppc;
                out.addSprite(RenderLayer.UNITS, glow, ex + (float) Math.cos(heading) * d,
                        ey + (float) Math.sin(heading) * d, size, size, 0f, 0.45f, 0.4f, 0.34f,
                        0.45f * (1f - p) * alpha);
            }
        }
        for (int j = 0; j < 6; j++) {
            double heading = hash(seed, 50 + j) * Math.PI * 2;
            float d = (0.2f + 0.35f * hash(seed, 80 + j)) * (1f - (1f - p) * (1f - p)) * ppc;
            float s = 2f + 2f * hash(seed, 90 + j);
            float cx = ex + (float) Math.cos(heading) * d;
            float cy = ey + (float) Math.sin(heading) * d;
            out.addSolidRect(RenderLayer.UNITS, cx - s, cy - s, cx + s, cy + s, 0.36f, 0.33f, 0.3f, (1f - p) * alpha);
        }
    }

    /** Scrap spilling off a bed's tail at (x, y), thrown along (bx, by), the tail {@code width} across (wx, wy). */
    private static void spill(DrawList out, BattleCamera cam, SpriteAPI glow, float x, float y, float bx, float by,
                              float width, float wx, float wy, float time, float alpha) {
        float ppc = cam.cellPxSize();
        for (int j = 0; j < 16; j++) {
            float period = 0.55f + 0.3f * hash(77, j);
            float ph = time / period + hash(78, j);
            int cycle = (int) Math.floor(ph);
            float p = ph - cycle;
            float across = (hash(79 + j, cycle) - 0.5f) * width;
            float out1 = (0.1f + 0.6f * hash(80 + j, cycle)) * (1f - (1f - p) * (1f - p));
            float cx = cam.cellToScreenX(x + wx * across + bx * out1);
            float cy = cam.cellToScreenY(y + wy * across + by * out1);
            float s = 2.5f + 3f * hash(81 + j, cycle);
            float tone = 0.28f + 0.3f * hash(82 + j, cycle);
            out.addSolidRect(RenderLayer.UNITS, cx - s, cy - s, cx + s, cy + s, tone * 1.05f, tone * 0.92f,
                    tone * 0.8f, (1f - p * p) * alpha);
        }
        if (glow == null) return;
        for (int j = 0; j < 4; j++) {
            float ph = time / 1.2f + j / 4f;
            int cycle = (int) Math.floor(ph);
            float p = ph - cycle;
            float across = (hash(90 + j, cycle) - 0.5f) * width;
            float size = (0.5f + 0.8f * p) * ppc;
            out.addSprite(RenderLayer.UNITS, glow, cam.cellToScreenX(x + wx * across + bx * 0.4f * p),
                    cam.cellToScreenY(y + wy * across + by * 0.4f * p), size, size, 0f, 0.44f, 0.4f, 0.33f,
                    0.4f * (1f - p) * alpha);
        }
    }

    // ---- the salvage ----

    /** The claim's laid salvage as scrap heaps, each shrinking toward nothing as it is cut. Wrecks draw as wrecks. */
    static void piles(DrawList out, BattleCamera cam, List<Salvage> salvage, LayeredSpriteCache pile, ViewCull view,
                      float alpha) {
        if (pile == null) return;
        float ppc = cam.cellPxSize();
        for (Salvage s : salvage) {
            if (s.kind != Salvage.Kind.STRUCTURE || s.initialMetal <= 0f || s.metal() <= 0f) continue;
            if (s.x < view.cullMinX(PILE_CELLS) || s.x > view.cullMaxX(PILE_CELLS) || s.y < view.cullMinY(PILE_CELLS)
                    || s.y > view.cullMaxY(PILE_CELLS)) continue;
            float share = s.metal() / s.initialMetal;
            float size = PILE_CELLS * (0.3f + 0.7f * (float) Math.sqrt(share)) * ppc;
            out.addSprite(RenderLayer.DOODADS, pile.sprite, cam.cellToScreenX(s.x), cam.cellToScreenY(s.y), size,
                    size * pile.pxHeight / Math.max(1f, pile.pxWidth), hash((int) s.id, 1) * 360f, 1f, 1f, 1f,
                    alpha);
        }
    }

    // ---- shared ----

    /** The torso's drawn width, cells, as {@link DerelictWalkerComposer} draws it. */
    private static float torsoCells(DerelictLegRig rig, float renderScale) {
        return DerelictWalkerComposer.TORSO_CELLS * renderScale
                * (1f + DerelictWalkerComposer.TORSO_HEIGHT_GROWTH * rig.bodyHeight());
    }

    /** A point {@code lx} to the body's right and {@code ly} ahead of its centre, facing {@code f}, in cells. */
    private static float worldX(DerelictLegRig rig, float f, float lx, float ly) {
        return rig.bodyX() + lx * dirY(f) + ly * dirX(f);
    }

    private static float worldY(DerelictLegRig rig, float f, float lx, float ly) {
        return rig.bodyY() - lx * dirX(f) + ly * dirY(f);
    }

    /** The cell direction of a sprite bearing (0 = +y, counter-clockwise). */
    static float dirX(float degrees) {
        return (float) -Math.sin(Math.toRadians(degrees));
    }

    static float dirY(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }

    /** The sprite bearing of (dx, dy): degrees counter-clockwise from +y. */
    private static float bearing(float dx, float dy) {
        return (float) Math.toDegrees(Math.atan2(-dx, dy));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** A steady value in [0, 1) for a pair of ints. */
    static float hash(int a, int b) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h >>> 8) / (float) (1 << 24);
    }
}
