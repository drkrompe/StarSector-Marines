package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBruteRig;

/**
 * Draws a Rav Bloom Brute from its skeleton, seen from above, as the approved motion study 3 draws it:
 * the hooves on the bottom layer, a shank from each hip and shoulder down to its hoof over them, the body
 * over both, and the head on top, riding on the shoulders. The body follows the axis from the hips to the
 * shoulders, so it turns with the sway; the head slides along it and scales about the neck as the charge
 * drops it and a blow rears and drives it. Ported from
 * {@code art-source/rav-redesign/exploration/motion_brute.py}.
 */
final class RavBruteComposer {

    /** Sprite px per hero layer px, as {@code export_brute_rig.py} wrote them. */
    private static final float SCALE = 0.25f;
    private static final float U = RavBruteRig.U_LAYER;
    /** Layer rows: the canvas centre, the neck pivot, and the head sprite's centre (it is rows 0..448). */
    private static final float CANVAS_CENTRE_ROW = 768f, NECK_ROW = 384f, HEAD_CENTRE_ROW = 224f;
    /** Layer width of the front and hind shanks. */
    private static final float[] SHANK_W = {120f, 135f};
    /** The owner asked for bigger feet; a raised hoof is nearer the camera. */
    private static final float HOOF_SCALE = 1.3f, HOOF_LIFT = 2.5f;
    /** Hind legs under the front ones, as the study layers them. */
    private static final int[] LEG_ORDER = {2, 3, 0, 1};

    private RavBruteComposer() {
    }

    /**
     * @param cx  screen position of the rig's body
     * @param cy  screen position of the rig's body
     * @param ppc screen px per cell
     */
    static void emit(DrawList out, RavBruteAssets art, RavBruteRig rig, float cx, float cy, float ppc, float alpha) {
        float facing = rig.facingDegrees();
        double rad = Math.toRadians(facing);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float spritePx = ppc / (U * SCALE);           // screen px per sprite px
        float[] a = new float[2], b = new float[2];

        for (int leg : LEG_ORDER) {                    // hooves: the bottom layer
            int hoof = RavBruteRig.LEG_START[leg] + RavBruteRig.LEG_POINTS[leg] - 1;
            screen(rig, hoof, cx, cy, ppc, fx, fy, a);
            LayeredSpriteCache s = art.hoof[leg];
            float k = spritePx * HOOF_SCALE * (1f + HOOF_LIFT * rig.z(hoof));
            out.addSprite(RenderLayer.UNITS, s.sprite, a[0], a[1], s.pxWidth * k, s.pxHeight * k, facing,
                    1f, 1f, 1f, alpha);
        }
        for (int leg : LEG_ORDER) {                    // shanks: from the root down to the hoof
            int start = RavBruteRig.LEG_START[leg];
            int hoof = start + RavBruteRig.LEG_POINTS[leg] - 1;
            screen(rig, start, cx, cy, ppc, fx, fy, a);
            screen(rig, hoof, cx, cy, ppc, fx, fy, b);
            boolean front = leg < 2;
            LayeredSpriteCache s = art.shank[front ? 0 : 1][leg % 2];
            float width = SHANK_W[front ? 0 : 1] * ppc / U;
            float dx = a[0] - b[0], dy = a[1] - b[1];  // the image runs from the hoof (bottom) to the root (top)
            float ln = (float) Math.hypot(dx, dy);
            float angle = ln > 1e-3f ? (float) Math.toDegrees(Math.atan2(-dx, dy)) : facing;
            out.addSprite(RenderLayer.UNITS, s.sprite, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2,
                    width, ln + width, angle, 1f, 1f, 1f, alpha);
        }

        // the body axis: a layer row r lies at shoulder + (shoulder - hip) * (SHOULDER_ROW - r) / span
        float shX = rig.x(RavBruteRig.SHOULDER), shY = rig.y(RavBruteRig.SHOULDER);
        float hpX = rig.x(RavBruteRig.HIP), hpY = rig.y(RavBruteRig.HIP);
        float span = RavBruteRig.HIP_ROW - RavBruteRig.SHOULDER_ROW;
        float axX = (shX - hpX) / span, axY = (shY - hpY) / span;     // cells per layer row, toward the head
        float bodyAngle = facing + (float) Math.toDegrees(Math.atan2(-axX, axY));
        float k = spritePx;
        rowPoint(shX, shY, axX, axY, CANVAS_CENTRE_ROW, cx, cy, ppc, fx, fy, a);
        out.addSprite(RenderLayer.UNITS, art.body.sprite, a[0], a[1], art.body.pxWidth * k, art.body.pxHeight * k,
                bodyAngle, 1f, 1f, 1f, alpha);

        // the head: slides along the axis by its dy, and scales about the neck
        float hk = rig.headK();
        float neckRow = NECK_ROW + rig.headDy();
        float centreRow = neckRow - (NECK_ROW - HEAD_CENTRE_ROW) * hk;
        rowPoint(shX, shY, axX, axY, centreRow, cx, cy, ppc, fx, fy, a);
        out.addSprite(RenderLayer.UNITS, art.head.sprite, a[0], a[1], art.head.pxWidth * k * hk,
                art.head.pxHeight * k * hk, bodyAngle, 1f, 1f, 1f, alpha);
    }

    /** The screen point of layer row {@code row} on the body axis. */
    private static void rowPoint(float shX, float shY, float axX, float axY, float row, float cx, float cy, float ppc,
                                 float fx, float fy, float[] out) {
        float t = RavBruteRig.SHOULDER_ROW - row;
        toScreen(shX + axX * t, shY + axY * t, cx, cy, ppc, fx, fy, out);
    }

    private static void screen(RavBruteRig rig, int point, float cx, float cy, float ppc, float fx, float fy,
                               float[] out) {
        toScreen(rig.x(point), rig.y(point), cx, cy, ppc, fx, fy, out);
    }

    /** Rig frame (x right, y forward) onto the screen at the facing; right is forward turned clockwise. */
    private static void toScreen(float x, float y, float cx, float cy, float ppc, float fx, float fy, float[] out) {
        out[0] = cx + (x * fy + y * fx) * ppc;
        out[1] = cy + (-x * fx + y * fy) * ppc;
    }
}
