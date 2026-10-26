package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBombardRig;
import com.dillon.starsectormarines.battle.appearance.RavBurst;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Draws a Bombard from its parts, seen from above: each leg turned about its hip to its claw, stretched to reach it
 * and drawn larger while lifted in a step, the shell over them, and the
 * tail's slices strung between the points its rig lays out, root under the shell's rear to the sling. A slice standing
 * up off the ground draws larger and shortened, so the tail arching over the back reads as rising toward the camera.
 * Laid out behind, the tail is drawn under the shell; arched over the back to throw, it is drawn over it, the glob
 * glowing in its sling. The shell sits lower and higher on its legs, lurches and rocks as the rig's springs say. The
 * sac glows amber ({@link #emitGlow}). A dead Bombard is drawn fallen apart, its shell in two halves ({@link #emitDead}).
 */
final class RavBombardComposer {

    /** Sprite px per canvas px, as {@code export_bombard_rig.py} wrote them. */
    private static final float SCALE = 1f;
    private static final float U = RavBombardRig.U_LAYER;
    private static final float ANCHOR = RavBombardRig.ANCHOR_ROW, CENTRE = RavBombardRig.CENTRE_COL;
    /** The curl past which the tail is over the shell rather than under it. */
    private static final float OVER_BACK = 0.3f;
    /** A tail slice's length, cells, and the sling's from its hinge to its centre. */
    private static final float SLICE = RavBombardRig.SLICE_PX / U;
    private static final float SLING_REACH = (RavBombardRig.SLING_ROW - RavBombardRig.TAIL_ROOT_ROW
            - RavBombardRig.TAIL_ART_SLICES * RavBombardRig.SLICE_PX) / U;
    /** A slice seen end on still shows this much of its length: its cross-section. */
    private static final float MIN_STRETCH = 0.4f;
    /** The hips follow this share of the body's rock: their feet are on the ground. */
    private static final float LEG_ROCK = 0.4f;
    /** The glob in the sling, its long axis in cells. */
    private static final float GLOB_CELLS = 0.3f;
    /** The sac's centre, canvas px. */
    private static final float SAC_ROW = 420f;

    private RavBombardComposer() {
    }

    static void emit(DrawList out, RavBombardAssets art, RavBombardRig rig, float cx, float cy, float ppc, float alpha,
                     RavWear.Marks marks) {
        emit(units(out), art, rig, cx, cy, ppc, alpha, marks);
    }

    /**
     * The same composition into any emitter, so an art review draws exactly what the battle draws.
     *
     * @param cx  screen position of the rig's body
     * @param cy  screen position of the rig's body
     * @param ppc screen px per cell
     */
    static void emit(LayeredUnitComposer.SpriteEmitter out, RavBombardAssets art, RavBombardRig rig, float cx,
                     float cy, float ppc, float alpha, RavWear.Marks marks) {
        float facing = rig.facingDegrees();
        float body = facing + rig.rock();
        float spritePx = ppc / (U * SCALE);
        float[] p = new float[2];
        rotate(0f, rig.shift(), facing, p);
        float ax = cx + p[0] * ppc, ay = cy + p[1] * ppc;

        // a lifted leg draws over its planted neighbours
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < RavBombardRig.LEGS; i++) {
                if ((rig.legLift(i) > 0f) != (pass == 1)) continue;
                leg(out, art.legs[i], i, rig, ax, ay, ppc, spritePx, facing, alpha);
            }
        }
        // the root slice lies under the shell's rear either way; the rest crosses over it once arched
        boolean over = rig.curl() > OVER_BACK;
        tail(out, art, rig, ax, ay, ppc, spritePx, body, alpha, 0, over ? 1 : RavBombardRig.TAIL_SEGMENTS);
        float lift = rig.lift();
        if (rig.dead()) {
            float apart = rig.crackCells() * U, turn = rig.crackDegrees();
            shellHalf(out, art.shellL, RavBombardRig.SHELL_L_COL - apart, body + turn, lift, ax, ay, ppc, spritePx,
                    alpha);
            shellHalf(out, art.shellR, RavBombardRig.SHELL_R_COL + apart, body - turn, lift, ax, ay, ppc, spritePx,
                    alpha);
        } else {
            point(CENTRE, RavBombardRig.BODY_ROW, body, ax, ay, ppc, p);
            float bw = art.body.pxWidth * spritePx * lift * rig.squeeze(), bh = art.body.pxHeight * spritePx * lift;
            out.add(art.body, p[0], p[1], bw, bh, body, 1f, 1f, 1f, alpha);
            if (marks != null) marks.part(art.body, RavWear.BOMBARD_BODY, 0, p[0], p[1], bw, bh, body, alpha);
        }
        if (over) tail(out, art, rig, ax, ay, ppc, spritePx, body, alpha, 1, RavBombardRig.TAIL_SEGMENTS);
    }

    /** One half of the cracked shell, its centre at canvas column {@code col}, turned to {@code angle}. */
    private static void shellHalf(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache s, float col, float angle,
                                  float lift, float ax, float ay, float ppc, float spritePx, float alpha) {
        float[] p = new float[2];
        rotate((col - CENTRE) / U * lift, (ANCHOR - RavBombardRig.BODY_ROW) / U * lift, angle, p);
        out.add(s, ax + p[0] * ppc, ay + p[1] * ppc, s.pxWidth * spritePx * lift, s.pxHeight * spritePx * lift,
                angle, 1f, 1f, 1f, alpha);
    }

    /** Leg {@code i}, turned about its hip, stretched to its claw and splayed, larger while lifted in a step. */
    private static void leg(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache s, int i, RavBombardRig rig,
                            float ax, float ay, float ppc, float spritePx, float facing, float alpha) {
        // the splay was a side's stretch; a single leg takes half of it, as a longer reach
        float splay = 1f + (rig.legSplay(i < RavBombardRig.LEGS_PER_SIDE) - 1f) * 0.5f;
        float grow = rig.legReach(i) * splay * RavBombardRig.liftScale(rig.legLift(i));
        float turn = facing + rig.rock() * LEG_ROCK;
        float swing = turn + rig.legAngle(i);
        float[] hip = new float[2];
        point(RavBombardRig.HIP_COL[i], RavBombardRig.HIP_ROW[i], turn, ax, ay, ppc, hip);
        float[] o = new float[2];
        rotate((RavBombardRig.LEG_COL[i] - RavBombardRig.HIP_COL[i]) / U * grow,
                (RavBombardRig.HIP_ROW[i] - RavBombardRig.LEG_ROW[i]) / U * grow, swing, o);
        out.add(s, hip[0] + o[0] * ppc, hip[1] + o[1] * ppc, s.pxWidth * spritePx * grow, s.pxHeight * spritePx * grow,
                swing, 1f, 1f, 1f, alpha);
    }

    /**
     * The tail's slices from joint {@code from} up to {@code to}, each strung between the rig's points and drawn larger
     * the higher it stands and shorter the steeper; the sling as the last, and the glob in it while one is loaded.
     */
    private static void tail(LayeredUnitComposer.SpriteEmitter out, RavBombardAssets art, RavBombardRig rig,
                             float ax, float ay, float ppc, float spritePx, float body, float alpha, int from, int to) {
        float[] a = new float[2], b = new float[2];
        for (int i = from; i < to; i++) {
            rotate(rig.tailX(i), rig.tailY(i), body, a);
            rotate(rig.tailX(i + 1), rig.tailY(i + 1), body, b);
            float k = RavBombardRig.rise((rig.tailZ(i) + rig.tailZ(i + 1)) * 0.5f);
            float dx = (b[0] - a[0]) * ppc, dy = (b[1] - a[1]) * ppc;
            float shown = (float) Math.hypot(dx, dy);
            // seen end on, a slice has no heading of its own: it keeps the joint's
            float angle = shown > 0.05f * ppc * SLICE ? (float) Math.toDegrees(Math.atan2(dx, -dy))
                    : body + rig.tailYaw(i);
            if (i == RavBombardRig.TAIL_ART_SLICES) {
                float stretch = Math.max(MIN_STRETCH * k, shown / (SLING_REACH * ppc));
                float sx = ax + b[0] * ppc, sy = ay + b[1] * ppc;
                out.add(art.sling, sx, sy, art.sling.pxWidth * spritePx * k, art.sling.pxHeight * spritePx * stretch,
                        angle, 1f, 1f, 1f, alpha);
                if (rig.globLoaded()) {
                    float gh = GLOB_CELLS * ppc * k;
                    float gw = gh * art.glob.pxWidth / Math.max(1f, art.glob.pxHeight);
                    out.add(art.glob, sx, sy, gw, gh, angle + 180f, 1f, 1f, 1f, alpha);
                }
                continue;
            }
            float stretch = Math.max(MIN_STRETCH * k, shown / (SLICE * ppc));
            LayeredSpriteCache s = art.tail[i];
            out.add(s, ax + (a[0] + b[0]) * 0.5f * ppc, ay + (a[1] + b[1]) * 0.5f * ppc, s.pxWidth * spritePx * k,
                    s.pxHeight * spritePx * stretch, angle, 1f, 1f, 1f, alpha);
        }
    }

    /** The sac's glow: its halo's and core's widths, cells per cell of body, and alphas at full glow. */
    private static final float GLOW_HALO = 1.3f, GLOW_CORE = 0.55f, GLOW_HALO_ALPHA = 0.45f, GLOW_CORE_ALPHA = 0.28f;
    /** The glow's amber, halo then core. */
    private static final float[] GLOW_HALO_TINT = {1f, 0.55f, 0.12f}, GLOW_CORE_TINT = {1f, 0.8f, 0.4f};

    /**
     * The amber light of the sac through its split shell: a slow pulse that swells through the wind-up and flashes as
     * the glob is flung, so the lob is read off the creature itself. Additive, over the body.
     */
    static void emitGlow(DrawList out, SpriteAPI glow, RavBombardRig rig, float cx, float cy, float ppc, float alpha) {
        float g = rig.glow();
        if (glow == null || g <= 0f) return;
        float[] p = new float[2];
        rotate(0f, rig.shift(), rig.facingDegrees(), p);
        float[] sac = new float[2];
        point(CENTRE, SAC_ROW, rig.facingDegrees() + rig.rock(), cx + p[0] * ppc, cy + p[1] * ppc, ppc, sac);
        out.addAdditiveSprite(RenderLayer.UNITS, glow, sac[0], sac[1], ppc * GLOW_HALO, ppc * GLOW_HALO * 1.5f,
                rig.facingDegrees(), GLOW_HALO_TINT[0], GLOW_HALO_TINT[1], GLOW_HALO_TINT[2],
                GLOW_HALO_ALPHA * g * alpha);
        out.addAdditiveSprite(RenderLayer.UNITS, glow, sac[0], sac[1], ppc * GLOW_CORE, ppc * GLOW_CORE * 1.6f,
                rig.facingDegrees(), GLOW_CORE_TINT[0], GLOW_CORE_TINT[1], GLOW_CORE_TINT[2],
                GLOW_CORE_ALPHA * g * alpha);
    }

    static void emitDead(DrawList out, RavBombardAssets art, LayeredSpriteCache[] splat, LayeredSpriteCache drop,
                         RavBombardRig rig, float cx, float cy, float ppc, float alpha) {
        emitDead(units(out), art, splat, drop, rig, cx, cy, ppc, alpha);
    }

    /**
     * A dead Bombard, fallen apart: its blood on the ground, then its buckled legs, its collapsed tail and its cracked
     * shell as {@link #emit} draws them, then the droplets still in the air. The blood is the Runner's.
     */
    static void emitDead(LayeredUnitComposer.SpriteEmitter out, RavBombardAssets art, LayeredSpriteCache[] splat,
                         LayeredSpriteCache drop, RavBombardRig rig, float cx, float cy, float ppc, float alpha) {
        RavBurst burst = rig.burst();
        float facing = rig.facingDegrees();
        RavIchor.Ground ground = (x, y, o) -> {
            rotate(x, y, facing, o);
            o[0] = cx + o[0] * ppc;
            o[1] = cy + o[1] * ppc;
        };
        if (splat != null) RavIchor.onGround(out, splat, burst, ground, ppc, facing, 1f, alpha);
        emit(out, art, rig, cx, cy, ppc, alpha, null);
        if (drop != null) RavIchor.inFlight(out, drop, burst, ground, ppc, 1f, alpha);
    }

    /** The screen point of canvas pixel {@code (col, row)} on a body anchored at (ax, ay) and turned to angle. */
    private static void point(float col, float row, float angle, float ax, float ay, float ppc, float[] out) {
        rotate((col - CENTRE) / U, (ANCHOR - row) / U, angle, out);
        out[0] = ax + out[0] * ppc;
        out[1] = ay + out[1] * ppc;
    }

    /** A body-frame offset (x right, y forward, cells) turned to the facing {@code angle} (sprite degrees). */
    private static void rotate(float x, float y, float angle, float[] out) {
        double rad = Math.toRadians(angle);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        out[0] = x * fy + y * fx;
        out[1] = -x * fx + y * fy;
    }

    private static LayeredUnitComposer.SpriteEmitter units(DrawList out) {
        return (layer, x, y, w, h, angle, r, g, b, a) -> out.addSprite(RenderLayer.UNITS, layer.sprite, x, y, w, h,
                angle, r, g, b, a);
    }
}
