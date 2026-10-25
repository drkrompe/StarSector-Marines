package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBurst;
import com.dillon.starsectormarines.battle.appearance.RavSpitterRig;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Draws a Spitter from its parts, seen from above: the tail's slices chained from the body's centre (coiled round the
 * body standing, trailing behind on the move), the back over them, and the head on top, turned about its
 * neck, closed or spitting as the rig fires, its snout glowing with acid as it braces ({@link #emitGlow}). A dead
 * Spitter is drawn burst, as a Brute is ({@link #emitDead}).
 */
final class RavSpitterComposer {

    /** Sprite px per canvas px, as {@code export_spitter_rig.py} wrote them. */
    private static final float SCALE = 0.25f;
    private static final float U = RavSpitterRig.U_LAYER;
    private static final float ANCHOR = RavSpitterRig.ANCHOR_ROW, CENTRE = RavSpitterRig.CENTRE_COL;
    private static final float BACK_ROW = RavSpitterRig.BACK_ROW, HEAD_ROW = RavSpitterRig.HEAD_ROW;
    /** The flared hood opens out from this share of its width. */
    private static final float HOOD_FROM = 0.9f;
    /** The hood share past which the spitting head replaces the closed one. */
    private static final float HOOD_SWAP = 0.5f;
    /** Tail slices from the root that bend to keep the planted tail joined to the moving body. */
    private static final int HOOKUP_SLICES = 2;
    /** A drawn tail slice's length, cells. */
    private static final float SLICE = RavSpitterRig.SLICE_PX / RavSpitterRig.U_LAYER;

    private RavSpitterComposer() {
    }

    static void emit(DrawList out, RavSpitterAssets art, RavSpitterRig rig, float cx, float cy, float ppc,
                     float alpha) {
        emit(units(out), art, rig, cx, cy, ppc, alpha);
    }

    /**
     * The same composition into any emitter, so an art review draws exactly what the battle draws.
     *
     * @param cx  screen position of the rig's body
     * @param cy  screen position of the rig's body
     * @param ppc screen px per cell
     */
    static void emit(LayeredUnitComposer.SpriteEmitter out, RavSpitterAssets art, RavSpitterRig rig, float cx,
                     float cy, float ppc, float alpha) {
        float facing = rig.facingDegrees();
        float spritePx = ppc / (U * SCALE);           // screen px per sprite px
        float chest = facing + rig.bodyAngle();
        // the chest's frame: its anchor carried by the rig's shift, turned by its roll
        float ax = cx, ay = cy;
        float[] p = new float[2];
        rotate(rig.shiftX(), rig.shiftY(), facing, p);
        ax += p[0] * ppc;
        ay += p[1] * ppc;

        // the tail, chained from the body's centre. It is planted: it hangs off the sim position and the facing, so the
        // chest's rear, lunge and roll leave it where it lies; only its hookup follows the body
        int n = RavSpitterRig.TAIL_SEGMENTS;
        float[][] joint = new float[n + 1][2];
        float tailPx = spritePx * RavSpitterRig.TAIL_SCALE;
        float jx = cx, jy = cy, a = facing;
        for (int i = 0; i <= n; i++) {
            joint[i][0] = jx;
            joint[i][1] = jy;
            if (i == n) break;
            a += rig.tailAngle(i);
            rotate(0f, -SLICE, a, p);
            jx += p[0] * ppc;
            jy += p[1] * ppc;
        }
        // the root rides with the body, and the first slices bend and stretch to keep it joined to the planted rest
        float dx = ax - cx, dy = ay - cy;
        for (int k = 0; k < HOOKUP_SLICES; k++) {
            float follow = 1f - (float) k / HOOKUP_SLICES;
            joint[k][0] += dx * follow;
            joint[k][1] += dy * follow;
        }
        for (int i = n - 1; i >= 0; i--) {             // tip first, so each slice lies over the one behind it
            float vx = joint[i + 1][0] - joint[i][0], vy = joint[i + 1][1] - joint[i][1];
            float stretch = (float) Math.hypot(vx, vy) / (SLICE * ppc);
            float sliceAngle = (float) Math.toDegrees(Math.atan2(vx, -vy));
            LayeredSpriteCache s = art.tail[RavSpitterRig.TAIL_ART[i]];
            out.add(s, (joint[i][0] + joint[i + 1][0]) / 2f, (joint[i][1] + joint[i + 1][1]) / 2f,
                    s.pxWidth * tailPx * RavSpitterRig.tailGirth(i), s.pxHeight * tailPx * stretch, sliceAngle,
                    1f, 1f, 1f, alpha);
        }

        point(CENTRE, BACK_ROW, chest, ax, ay, ppc, p);
        out.add(art.back, p[0], p[1], art.back.pxWidth * spritePx, art.back.pxHeight * spritePx, chest,
                1f, 1f, 1f, alpha);

        // the head about its neck, closed or spitting
        float[] neck = new float[2];
        point(CENTRE, RavSpitterRig.NECK_ROW, chest, ax, ay, ppc, neck);
        float head = chest + rig.headYaw();
        float k = rig.headScale();
        float hood = rig.hood();
        // one head at a time: the two poses' silhouettes differ, so any overlap reads as two heads
        if (hood < HOOD_SWAP) headSprite(out, art.headShut, HEAD_ROW, neck, head, k, 1f, ppc, spritePx, alpha);
        else {
            headSprite(out, art.headOpen, HEAD_ROW, neck, head, k, HOOD_FROM + (1f - HOOD_FROM) * hood, ppc,
                    spritePx, alpha);
        }
    }

    /** The snout's acid glow at full brace: its halo's and core's widths, cells per cell of body, and alphas. */
    private static final float GLOW_HALO = 1.7f, GLOW_CORE = 0.65f, GLOW_HALO_ALPHA = 0.85f, GLOW_CORE_ALPHA = 0.95f;
    /** The glow's acid green, halo then core. */
    private static final float[] GLOW_HALO_TINT = {0.55f, 1f, 0.15f}, GLOW_CORE_TINT = {0.85f, 1f, 0.55f};
    /** How far ahead of the head sprite's centre the snout's tip lies, as a share of the head's length. */
    private static final float SNOUT_AHEAD = 0.40f;

    /**
     * The acid at the snout while the Spitter braces to lob (owner, 2026-10-25): a soft green light that swells with
     * the brace and goes out as it lobs, so the lob is read off the creature itself. Additive, over the head; nothing
     * when it is not bracing.
     */
    static void emitGlow(DrawList out, SpriteAPI glow, RavSpitterAssets art, RavSpitterRig rig, float cx, float cy,
                         float ppc, float alpha) {
        float brace = rig.brace();
        if (glow == null || brace <= 0f) return;
        float[] tip = snout(art, rig, cx, cy, ppc);
        out.addAdditiveSprite(RenderLayer.UNITS, glow, tip[0], tip[1], ppc * GLOW_HALO, ppc * GLOW_HALO, 0f,
                GLOW_HALO_TINT[0], GLOW_HALO_TINT[1], GLOW_HALO_TINT[2], GLOW_HALO_ALPHA * brace * alpha);
        out.addAdditiveSprite(RenderLayer.UNITS, glow, tip[0], tip[1], ppc * GLOW_CORE, ppc * GLOW_CORE, 0f,
                GLOW_CORE_TINT[0], GLOW_CORE_TINT[1], GLOW_CORE_TINT[2], GLOW_CORE_ALPHA * brace * alpha);
    }

    /** The snout's tip on screen: the head turned about its neck as {@link #emit} draws it, then its length ahead. */
    static float[] snout(RavSpitterAssets art, RavSpitterRig rig, float cx, float cy, float ppc) {
        float facing = rig.facingDegrees();
        float chest = facing + rig.bodyAngle();
        float[] p = new float[2];
        rotate(rig.shiftX(), rig.shiftY(), facing, p);
        float ax = cx + p[0] * ppc, ay = cy + p[1] * ppc;
        float[] neck = new float[2];
        point(CENTRE, RavSpitterRig.NECK_ROW, chest, ax, ay, ppc, neck);
        float k = rig.headScale();
        float ahead = ((RavSpitterRig.NECK_ROW - HEAD_ROW) / U + art.headOpen.pxHeight * SCALE / U * SNOUT_AHEAD) * k;
        rotate(0f, ahead, chest + rig.headYaw(), p);
        return new float[]{neck[0] + p[0] * ppc, neck[1] + p[1] * ppc};
    }

    private static void headSprite(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache s, float centreRow,
                                   float[] neck, float head, float k, float widen, float ppc, float spritePx,
                                   float alpha) {
        float[] o = new float[2];
        rotate(0f, (RavSpitterRig.NECK_ROW - centreRow) / U * k, head, o);
        out.add(s, neck[0] + o[0] * ppc, neck[1] + o[1] * ppc, s.pxWidth * spritePx * k * widen,
                s.pxHeight * spritePx * k, head, 1f, 1f, 1f, alpha);
    }

    /**
     * A dead Spitter, burst: its blood on the ground, then the tail's slices, the body and the head where the burst
     * left them, then the droplets still in the air. The blood is the Runner's ({@code splat}, {@code drop}); without
     * it the pieces are drawn bare.
     */
    static void emitDead(DrawList out, RavSpitterAssets art, LayeredSpriteCache[] splat, LayeredSpriteCache drop,
                         RavSpitterRig rig, float cx, float cy, float ppc, float alpha) {
        emitDead(units(out), art, splat, drop, rig, cx, cy, ppc, alpha);
    }

    static void emitDead(LayeredUnitComposer.SpriteEmitter out, RavSpitterAssets art, LayeredSpriteCache[] splat,
                         LayeredSpriteCache drop, RavSpitterRig rig, float cx, float cy, float ppc, float alpha) {
        RavBurst burst = rig.burst();
        float facing = rig.facingDegrees();
        float spritePx = ppc / (U * SCALE);
        float[] p = new float[2];
        RavIchor.Ground ground = (x, y, o) -> {
            rotate(x, y, facing, o);
            o[0] = cx + o[0] * ppc;
            o[1] = cy + o[1] * ppc;
        };
        if (splat != null) RavIchor.onGround(out, splat, burst, ground, ppc, facing, 1f, alpha);

        float[] d = new float[3], a = new float[2], b = new float[2];
        float tailPx = spritePx * RavSpitterRig.TAIL_SCALE;
        for (int i = RavSpitterRig.TAIL_SEGMENTS - 1; i >= 0; i--) {
            int piece = RavSpitterRig.tailPieceOf(i);
            burst.deadPoint(piece, i, 0f, 0f, d);
            ground.toScreen(d[0], d[1], a);
            burst.deadPoint(piece, i + 1, 0f, 0f, d);
            ground.toScreen(d[0], d[1], b);
            float vx = b[0] - a[0], vy = b[1] - a[1];
            float stretch = (float) Math.hypot(vx, vy) / (SLICE * ppc);
            float sliceAngle = (float) Math.toDegrees(Math.atan2(vx, -vy));
            LayeredSpriteCache s = art.tail[RavSpitterRig.TAIL_ART[i]];
            out.add(s, (a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, s.pxWidth * tailPx * RavSpitterRig.tailGirth(i),
                    s.pxHeight * tailPx * stretch, sliceAngle, 1f, 1f, 1f, alpha);
        }
        deadPiece(out, art.back, burst, RavSpitterRig.PIECE_BODY, RavSpitterRig.BACK_POINT, ground, facing,
                spritePx, alpha, d, p);
        deadPiece(out, art.headShut, burst, RavSpitterRig.PIECE_HEAD, RavSpitterRig.HEAD_POINT, ground, facing,
                spritePx, alpha, d, p);
        if (drop != null) RavIchor.inFlight(out, drop, burst, ground, ppc, 1f, alpha);
    }

    /** One burst piece's sprite, centred on its pose point. */
    private static void deadPiece(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache sprite, RavBurst burst,
                                  int piece, int point, RavIchor.Ground ground, float facing, float spritePx,
                                  float alpha, float[] d, float[] at) {
        burst.deadPoint(piece, point, 0f, 0f, d);
        ground.toScreen(d[0], d[1], at);
        out.add(sprite, at[0], at[1], sprite.pxWidth * spritePx, sprite.pxHeight * spritePx,
                facing + burst.pieceSpin(piece), 1f, 1f, 1f, alpha);
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
