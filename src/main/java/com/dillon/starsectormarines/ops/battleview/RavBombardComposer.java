package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBombardRig;
import com.dillon.starsectormarines.battle.appearance.RavBurst;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Draws a Bombard from its parts, seen from above: each side's legs swung about its hip, the shell over them, and the
 * tail's slices chained from its root under the shell's rear to the sling. Laid out behind, the tail is drawn under the
 * shell; curled up over the back to throw, it is drawn over it, the glob glowing in its sling. The sac glows amber
 * ({@link #emitGlow}). A dead Bombard is drawn burst, as a Spitter is ({@link #emitDead}).
 */
final class RavBombardComposer {

    /** Sprite px per canvas px, as {@code export_bombard_rig.py} wrote them. */
    private static final float SCALE = 1f;
    private static final float U = RavBombardRig.U_LAYER;
    private static final float ANCHOR = RavBombardRig.ANCHOR_ROW, CENTRE = RavBombardRig.CENTRE_COL;
    /** The curl past which the tail is over the shell rather than under it. */
    private static final float OVER_BACK = 0.3f;
    /** The sling's centre past its hinge, and the glob's cup past it, cells. */
    private static final float SLING_REACH = (RavBombardRig.SLING_ROW - RavBombardRig.TAIL_ROOT_ROW
            - RavBombardRig.TAIL_ART_SLICES * RavBombardRig.SLICE_PX) / U;
    private static final float CUP_REACH = (RavBombardRig.CUP_ROW - RavBombardRig.TAIL_ROOT_ROW
            - RavBombardRig.TAIL_ART_SLICES * RavBombardRig.SLICE_PX) / U;
    /** A tail slice's length, cells. */
    private static final float SLICE = RavBombardRig.SLICE_PX / U;
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
        float spritePx = ppc / (U * SCALE);
        float[] p = new float[2];
        rotate(0f, rig.bob(), facing, p);
        float ax = cx + p[0] * ppc, ay = cy + p[1] * ppc;

        legs(out, art.legsL, RavBombardRig.LEGS_L_COL, RavBombardRig.HIP_L_COL, rig.legSwing(), rig, ax, ay, ppc,
                spritePx, facing, alpha);
        legs(out, art.legsR, RavBombardRig.LEGS_R_COL, RavBombardRig.HIP_R_COL, -rig.legSwing(), rig, ax, ay, ppc,
                spritePx, facing, alpha);
        boolean over = rig.curl() > OVER_BACK;
        if (!over) tail(out, art, rig, ax, ay, ppc, spritePx, facing, alpha);
        point(CENTRE, RavBombardRig.BODY_ROW, facing, ax, ay, ppc, p);
        float bw = art.body.pxWidth * spritePx, bh = art.body.pxHeight * spritePx;
        out.add(art.body, p[0], p[1], bw, bh, facing, 1f, 1f, 1f, alpha);
        if (marks != null) marks.part(art.body, RavWear.BOMBARD_BODY, 0, p[0], p[1], bw, bh, facing, alpha);
        if (over) tail(out, art, rig, ax, ay, ppc, spritePx, facing, alpha);
    }

    /** One side's legs, swung about its hip and splayed out from it. */
    private static void legs(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache s, float col, float hipCol,
                             float swing, RavBombardRig rig, float ax, float ay, float ppc, float spritePx,
                             float facing, float alpha) {
        float splay = rig.legSplay();
        float[] hip = new float[2];
        point(hipCol, RavBombardRig.HIP_ROW, facing, ax, ay, ppc, hip);
        float[] o = new float[2];
        rotate((col - hipCol) / U * splay, (RavBombardRig.HIP_ROW - RavBombardRig.LEGS_ROW) / U, facing + swing, o);
        out.add(s, hip[0] + o[0] * ppc, hip[1] + o[1] * ppc, s.pxWidth * spritePx * splay, s.pxHeight * spritePx,
                facing + swing, 1f, 1f, 1f, alpha);
    }

    /** The tail's slices root to sling, then the sling, and the glob in it while one is loaded. */
    private static void tail(LayeredUnitComposer.SpriteEmitter out, RavBombardAssets art, RavBombardRig rig,
                             float ax, float ay, float ppc, float spritePx, float facing, float alpha) {
        float[] p = new float[2];
        float[] j = new float[2];
        point(CENTRE, RavBombardRig.TAIL_ROOT_ROW, facing, ax, ay, ppc, j);
        float a = facing;
        for (int i = 0; i < RavBombardRig.TAIL_ART_SLICES; i++) {
            a += rig.tailAngle(i);
            float k = rig.tailRise(i);
            rotate(0f, -SLICE * k, a, p);
            float nx = j[0] + p[0] * ppc, ny = j[1] + p[1] * ppc;
            LayeredSpriteCache s = art.tail[i];
            out.add(s, (j[0] + nx) / 2f, (j[1] + ny) / 2f, s.pxWidth * spritePx * k, s.pxHeight * spritePx * k, a,
                    1f, 1f, 1f, alpha);
            j[0] = nx;
            j[1] = ny;
        }
        a += rig.tailAngle(RavBombardRig.TAIL_ART_SLICES);
        float k = rig.tailRise(RavBombardRig.TAIL_ART_SLICES);
        rotate(0f, -SLING_REACH * k, a, p);
        out.add(art.sling, j[0] + p[0] * ppc, j[1] + p[1] * ppc, art.sling.pxWidth * spritePx * k,
                art.sling.pxHeight * spritePx * k, a, 1f, 1f, 1f, alpha);
        if (rig.globLoaded()) {
            rotate(0f, -CUP_REACH * k, a, p);
            float gh = GLOB_CELLS * ppc * k;
            float gw = gh * art.glob.pxWidth / Math.max(1f, art.glob.pxHeight);
            out.add(art.glob, j[0] + p[0] * ppc, j[1] + p[1] * ppc, gw, gh, a + 180f, 1f, 1f, 1f, alpha);
        }
    }

    /** The sac's glow: its halo's and core's widths, cells per cell of body, and alphas at full glow. */
    private static final float GLOW_HALO = 1.3f, GLOW_CORE = 0.55f, GLOW_HALO_ALPHA = 0.55f, GLOW_CORE_ALPHA = 0.7f;
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
        rotate(0f, rig.bob(), rig.facingDegrees(), p);
        float[] sac = new float[2];
        point(CENTRE, SAC_ROW, rig.facingDegrees(), cx + p[0] * ppc, cy + p[1] * ppc, ppc, sac);
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
     * A dead Bombard, burst: its blood on the ground, then the tail's slices and the sling where the burst left them,
     * the legs and the shell together, then the droplets still in the air. The blood is the Runner's.
     */
    static void emitDead(LayeredUnitComposer.SpriteEmitter out, RavBombardAssets art, LayeredSpriteCache[] splat,
                         LayeredSpriteCache drop, RavBombardRig rig, float cx, float cy, float ppc, float alpha) {
        RavBurst burst = rig.burst();
        float facing = rig.facingDegrees();
        float spritePx = ppc / (U * SCALE);
        RavIchor.Ground ground = (x, y, o) -> {
            rotate(x, y, facing, o);
            o[0] = cx + o[0] * ppc;
            o[1] = cy + o[1] * ppc;
        };
        if (splat != null) RavIchor.onGround(out, splat, burst, ground, ppc, facing, 1f, alpha);
        float[] d = new float[3], a = new float[2], b = new float[2];
        for (int i = RavBombardRig.TAIL_SEGMENTS - 1; i >= 0; i--) {
            int piece = RavBombardRig.tailPieceOf(i);
            burst.deadPoint(piece, i, 0f, 0f, d);
            ground.toScreen(d[0], d[1], a);
            burst.deadPoint(piece, i + 1, 0f, 0f, d);
            ground.toScreen(d[0], d[1], b);
            float angle = (float) Math.toDegrees(Math.atan2(b[0] - a[0], -(b[1] - a[1])));
            if (i == RavBombardRig.TAIL_ART_SLICES) {
                out.add(art.sling, (a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, art.sling.pxWidth * spritePx,
                        art.sling.pxHeight * spritePx, angle, 1f, 1f, 1f, alpha);
                continue;
            }
            float stretch = (float) Math.hypot(b[0] - a[0], b[1] - a[1]) / (SLICE * ppc);
            LayeredSpriteCache s = art.tail[i];
            out.add(s, (a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, s.pxWidth * spritePx, s.pxHeight * spritePx * stretch,
                    angle, 1f, 1f, 1f, alpha);
        }
        burst.deadPoint(RavBombardRig.PIECE_BODY, RavBombardRig.BODY_POINT, 0f, 0f, d);
        ground.toScreen(d[0], d[1], a);
        float spin = facing + burst.pieceSpin(RavBombardRig.PIECE_BODY);
        float[] o = new float[2];
        for (LayeredSpriteCache legs : new LayeredSpriteCache[]{art.legsL, art.legsR}) {
            float col = legs == art.legsL ? RavBombardRig.LEGS_L_COL : RavBombardRig.LEGS_R_COL;
            rotate((col - CENTRE) / U, (RavBombardRig.BODY_ROW - RavBombardRig.LEGS_ROW) / U, spin, o);
            out.add(legs, a[0] + o[0] * ppc, a[1] + o[1] * ppc, legs.pxWidth * spritePx, legs.pxHeight * spritePx,
                    spin, 1f, 1f, 1f, alpha);
        }
        out.add(art.body, a[0], a[1], art.body.pxWidth * spritePx, art.body.pxHeight * spritePx, spin,
                1f, 1f, 1f, alpha);
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
