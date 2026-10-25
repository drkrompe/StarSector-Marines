package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavMatriarchRig;

/**
 * Draws a Matriarch from its skeleton, seen from above, as the approved motion study 4 draws it: a shank
 * from inside the body down to each foot, the feet over them, the body over both along the line from the
 * hips to the shoulders, the scythes about their pivots under the crown's rim (long enough that the reap
 * reads past the crown), and the head on top. A scythe swung forward past the crown's rim crosses over the
 * head. Ported from
 * {@code art-source/rav-redesign/exploration/motion_matriarch.py}.
 */
final class RavMatriarchComposer {

    /** Sprite px per hero layer px, as {@code export_matriarch_rig.py} wrote them. */
    private static final float SCALE = 0.4f;
    private static final float U = RavMatriarchRig.U_LAYER;
    /** Layer rows: the canvas centre, the neck pivot, and the head sprites' centre (they are rows 0..672). */
    private static final float CANVAS_CENTRE_ROW = 768f, NECK_ROW = 470f, HEAD_CENTRE_ROW = 336f;
    private static final float CENTRE_COL = 512f;
    /** Each scythe's pivot, layer px, left then right, mirrored about the centre line; its sprite is centred on it. */
    private static final float[][] PIVOT = {{319f, 300f}, {705f, 300f}};
    /** Past this swing a scythe has crossed the crown's rim and is drawn over the head. */
    private static final float OVER_HEAD_DEGREES = 70f;
    /** The arm reaches forward this many times its outward reach as it swings. */
    private static final float FORWARD_REACH = 2.2f;
    /** Layer width of the front and hind shanks. */
    private static final float[] SHANK_W = {175f, 185f};
    /** A leg's root sits this fraction of the way out to its foot, well inside the body. */
    private static final float LEG_ROOT_IN = 0.55f;
    /** A raised foot is nearer the camera. */
    private static final float FOOT_SCALE = 1.1f, FOOT_LIFT = 1.2f;
    /** Hind legs under the front ones. */
    private static final int[] LEG_ORDER = {2, 3, 0, 1};

    private RavMatriarchComposer() {
    }

    /**
     * @param cx  screen position of the rig's body
     * @param cy  screen position of the rig's body
     * @param ppc screen px per cell
     */
    static void emit(DrawList out, RavMatriarchAssets art, RavMatriarchRig rig, float cx, float cy, float ppc,
                     float alpha) {
        emit(out, art, rig, cx, cy, ppc, alpha, null);
    }

    /** As above, with her worn chitin laid on her body and head as each is drawn ({@code marks} null for none). */
    static void emit(DrawList out, RavMatriarchAssets art, RavMatriarchRig rig, float cx, float cy, float ppc,
                     float alpha, RavWear.Marks marks) {
        float facing = rig.facingDegrees();
        double rad = Math.toRadians(facing);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float spritePx = ppc / (U * SCALE);           // screen px per sprite px
        float[] a = new float[2], b = new float[2];

        for (int leg : LEG_ORDER) {                    // shanks: from inside the body down past the foot
            boolean front = leg < 2;
            float rootX = rig.footX(leg) * LEG_ROOT_IN + (front ? rig.shoulderX() : rig.hipX());
            float rootY = front ? rig.shoulderY() : rig.hipY();
            toScreen(rootX, rootY, cx, cy, ppc, fx, fy, a);
            toScreen(rig.footX(leg), rig.footY(leg), cx, cy, ppc, fx, fy, b);
            LayeredSpriteCache s = art.shank[front ? 0 : 1][leg % 2];
            float width = SHANK_W[front ? 0 : 1] * ppc / U;
            float dx = a[0] - b[0], dy = a[1] - b[1];  // the image runs from the foot (bottom) to the root (top)
            float ln = (float) Math.hypot(dx, dy);
            float angle = ln > 1e-3f ? (float) Math.toDegrees(Math.atan2(-dx, dy)) : facing;
            out.addSprite(RenderLayer.UNITS, s.sprite, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2,
                    width, ln * 1.25f + width * 0.3f, angle, 1f, 1f, 1f, alpha);
        }
        for (int leg : LEG_ORDER) {                    // feet over the shanks
            toScreen(rig.footX(leg), rig.footY(leg), cx, cy, ppc, fx, fy, a);
            LayeredSpriteCache s = art.foot[leg];
            float k = spritePx * FOOT_SCALE * (1f + FOOT_LIFT * rig.footZ(leg));
            out.addSprite(RenderLayer.UNITS, s.sprite, a[0], a[1], s.pxWidth * k, s.pxHeight * k, facing,
                    1f, 1f, 1f, alpha);
        }

        // the body axis: a layer row r lies at shoulder + (shoulder - hip) * (SHOULDER_ROW - r) / span
        float shX = rig.shoulderX(), shY = rig.shoulderY();
        float span = RavMatriarchRig.HIP_ROW - RavMatriarchRig.SHOULDER_ROW;
        float axX = (shX - rig.hipX()) / span, axY = (shY - rig.hipY()) / span;   // cells per row, forward
        float local = (float) Math.toDegrees(Math.atan2(-axX, axY));
        float bodyAngle = facing + local;
        float lc = (float) Math.cos(Math.toRadians(local)), ls = (float) Math.sin(Math.toRadians(local));
        layerPoint(CENTRE_COL, CANVAS_CENTRE_ROW, shX, shY, axX, axY, lc, ls, cx, cy, ppc, fx, fy, a);
        out.addSprite(RenderLayer.UNITS, art.body.sprite, a[0], a[1], art.body.pxWidth * spritePx,
                art.body.pxHeight * spritePx, bodyAngle, 1f, 1f, 1f, alpha);
        if (marks != null) marks.part(art.body, RavWear.MATRIARCH_BODY, 0, a[0], a[1], art.body.pxWidth * spritePx,
                art.body.pxHeight * spritePx, bodyAngle, alpha);

        boolean[] over = new boolean[2];
        for (int side = 0; side < 2; side++) {
            over[side] = rig.bladeAngle(side) > OVER_HEAD_DEGREES;
            if (!over[side]) scythe(out, art, rig, side, shX, shY, axX, axY, lc, ls, bodyAngle,
                    cx, cy, ppc, fx, fy, spritePx, alpha);
        }

        // the head: slides along the axis by its dy, scales about the neck, and turns about it
        float hk = rig.headK();
        float neckRow = NECK_ROW + rig.headDy();
        float centreRow = neckRow - (NECK_ROW - HEAD_CENTRE_ROW) * hk;
        layerPoint(CENTRE_COL, neckRow, shX, shY, axX, axY, lc, ls, cx, cy, ppc, fx, fy, b);
        layerPoint(CENTRE_COL, centreRow, shX, shY, axX, axY, lc, ls, cx, cy, ppc, fx, fy, a);
        float turn = -rig.headYaw();                  // + yaw is toward its right: clockwise from above
        double yaw = Math.toRadians(turn);
        float ox = a[0] - b[0], oy = a[1] - b[1];
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        LayeredSpriteCache head = rig.gaping() ? art.gape : art.head;
        out.addSprite(RenderLayer.UNITS, head.sprite, b[0] + ox * cos - oy * sin, b[1] + ox * sin + oy * cos,
                head.pxWidth * spritePx * hk, head.pxHeight * spritePx * hk, bodyAngle + turn,
                1f, 1f, 1f, alpha);
        if (marks != null) marks.part(head, RavWear.MATRIARCH_HEAD, 1, b[0] + ox * cos - oy * sin,
                b[1] + ox * sin + oy * cos, head.pxWidth * spritePx * hk, head.pxHeight * spritePx * hk,
                bodyAngle + turn, alpha);

        for (int side = 0; side < 2; side++) {
            if (over[side]) scythe(out, art, rig, side, shX, shY, axX, axY, lc, ls, bodyAngle,
                    cx, cy, ppc, fx, fy, spritePx, alpha);
        }
    }

    /** One scythe, turned about its pivot and reaching out and forward with its swing. */
    private static void scythe(DrawList out, RavMatriarchAssets art, RavMatriarchRig rig, int side,
                               float shX, float shY, float axX, float axY, float lc, float ls, float bodyAngle,
                               float cx, float cy, float ppc, float fx, float fy, float spritePx, float alpha) {
        float sign = side == RavMatriarchRig.RIGHT ? 1f : -1f;
        float swing = sign * rig.bladeAngle(side);      // the right turns counter-clockwise, the left clockwise
        float reach = rig.bladeReach(side);
        float[] p = new float[2];
        layerPoint(PIVOT[side][0], PIVOT[side][1], shX + sign * reach, shY + FORWARD_REACH * reach, axX, axY,
                lc, ls, cx, cy, ppc, fx, fy, p);
        LayeredSpriteCache blade = art.scythe[side];
        out.addSprite(RenderLayer.UNITS, blade.sprite, p[0], p[1],
                blade.pxWidth * spritePx, blade.pxHeight * spritePx, bodyAngle + swing, 1f, 1f, 1f, alpha);
    }

    /**
     * The screen point of layer pixel {@code (col, row)}: down the body axis to the row, then across it by
     * the column's offset from the centre line, turned with the body.
     */
    private static void layerPoint(float col, float row, float shX, float shY, float axX, float axY,
                                   float lc, float ls, float cx, float cy, float ppc, float fx, float fy,
                                   float[] out) {
        float t = RavMatriarchRig.SHOULDER_ROW - row;
        float across = (col - CENTRE_COL) / U;
        toScreen(shX + axX * t + across * lc, shY + axY * t + across * ls, cx, cy, ppc, fx, fy, out);
    }

    /** Rig frame (x right, y forward) onto the screen at the facing; right is forward turned clockwise. */
    private static void toScreen(float x, float y, float cx, float cy, float ppc, float fx, float fy, float[] out) {
        out[0] = cx + (x * fy + y * fx) * ppc;
        out[1] = cy + (-x * fx + y * fy) * ppc;
    }
}
