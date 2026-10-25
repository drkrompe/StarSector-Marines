package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBruteRig;
import com.dillon.starsectormarines.battle.appearance.RavBurst;

/**
 * Draws a Rav Bloom Brute from its skeleton, seen from above, as the approved motion study 3 draws it:
 * the hooves on the bottom layer, a shank from each hip and shoulder down to its hoof over them, the body
 * over both, and the head on top, riding on the shoulders. The body follows the axis from the hips to the
 * shoulders, so it turns with the sway; the head slides along it and scales about the neck as the charge
 * drops it and a blow rears and drives it. A dead Brute is drawn burst ({@link #emitDead}). Ported from
 * {@code art-source/rav-redesign/exploration/motion_brute.py}.
 */
final class RavBruteComposer {

    /** Sprite px per hero layer px, as {@code export_brute_rig.py} wrote them. */
    private static final float SCALE = 0.25f;
    private static final float U = RavBruteRig.U_LAYER;
    /** Layer rows: the canvas centre, the neck pivot, and the head sprite's centre (it is rows 0..448). */
    private static final float NECK_ROW = 384f, HEAD_CENTRE_ROW = 224f;
    /** Layer width of the front and hind shanks. */
    private static final float[] SHANK_W = {120f, 135f};
    /**
     * Shank roots sit this fraction of the way out from the spine to the leg's own root, well inside the body,
     * as the Matriarch's do. The rig's root is at the body's edge, where the shank's tapered top showed as a
     * neck between hip and hoof once the hoof swung away; from inside, the shank always leaves the body at full
     * width and is stretched to whatever length the pose needs.
     */
    private static final float LEG_ROOT_IN = 0.55f;
    /** The owner asked for bigger feet; a raised hoof is nearer the camera. */
    private static final float HOOF_SCALE = 1.3f, HOOF_LIFT = 2.5f;
    /** A reared forequarter is nearer the camera: the body widens and the head grows by this per cell of rear. */
    private static final float REAR_SCALE = 0.9f;
    /** Layer rows of the centres of the body's front half (rows 0..990) and rear half (rows 958..1536). */
    private static final float FRONT_CENTRE_ROW = 495f, REAR_CENTRE_ROW = 1247f;
    /** Layer row where the halves meet: the middle of the back, halfway between the shoulders and the hips. */
    private static final float SEAM_ROW = (RavBruteRig.SHOULDER_ROW + RavBruteRig.HIP_ROW) / 2;
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
        emit(units(out), art, rig, cx, cy, ppc, alpha, null);
    }

    /** As above, with its worn chitin laid on each half and its head as each is drawn ({@code marks} null for none). */
    static void emit(DrawList out, RavBruteAssets art, RavBruteRig rig, float cx, float cy, float ppc, float alpha,
                     RavWear.Marks marks) {
        emit(units(out), art, rig, cx, cy, ppc, alpha, marks);
    }

    /** The same composition into any emitter, so an art review draws exactly what the battle draws. */
    static void emit(LayeredUnitComposer.SpriteEmitter out, RavBruteAssets art, RavBruteRig rig, float cx, float cy,
                     float ppc, float alpha) {
        emit(out, art, rig, cx, cy, ppc, alpha, null);
    }

    static void emit(LayeredUnitComposer.SpriteEmitter out, RavBruteAssets art, RavBruteRig rig, float cx, float cy,
                     float ppc, float alpha, RavWear.Marks marks) {
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
            out.add(s, a[0], a[1], s.pxWidth * k, s.pxHeight * k, facing,
                    1f, 1f, 1f, alpha);
        }
        for (int leg : LEG_ORDER) {                    // shanks: from inside the body, stretched down to the hoof
            int start = RavBruteRig.LEG_START[leg];
            int hoof = start + RavBruteRig.LEG_POINTS[leg] - 1;
            boolean front = leg < 2;
            int joint = front ? RavBruteRig.SHOULDER : RavBruteRig.HIP;
            float rootX = rig.x(joint) + (rig.x(start) - rig.x(joint)) * LEG_ROOT_IN;
            toScreen(rootX, rig.y(start), cx, cy, ppc, fx, fy, a);
            screen(rig, hoof, cx, cy, ppc, fx, fy, b);
            LayeredSpriteCache s = art.shank[front ? 0 : 1][leg % 2];
            float width = SHANK_W[front ? 0 : 1] * ppc / U;
            float dx = a[0] - b[0], dy = a[1] - b[1];  // the image runs from the hoof (bottom) to the root (top)
            float ln = (float) Math.hypot(dx, dy);
            float angle = ln > 1e-3f ? (float) Math.toDegrees(Math.atan2(-dx, dy)) : facing;
            out.add(s, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2,
                    width, ln + width, angle, 1f, 1f, 1f, alpha);
        }

        // The body in two halves that bend at the middle of the back, each on its own axis: a layer row r of the
        // front half lies at shoulder + (shoulder - seam) * (SHOULDER_ROW - r) / span, the rear half's likewise
        // from the hip. Each half's length is its axis's, so the sprite's shoulders and hips stay on the joints
        // the legs hang from; a reared forequarter nearer the camera widens it, never lengthens it.
        float k = spritePx * (1f + REAR_SCALE * rig.rear());
        float halfSpan = RavBruteRig.HIP_ROW - SEAM_ROW;
        float hpX = rig.x(RavBruteRig.HIP), hpY = rig.y(RavBruteRig.HIP);
        float rX = (rig.x(RavBruteRig.SEAM_REAR) - hpX) / halfSpan, rY = (rig.y(RavBruteRig.SEAM_REAR) - hpY) / halfSpan;
        toScreen(hpX + rX * (RavBruteRig.HIP_ROW - REAR_CENTRE_ROW), hpY + rY * (RavBruteRig.HIP_ROW - REAR_CENTRE_ROW),
                cx, cy, ppc, fx, fy, a);
        out.add(art.bodyRear, a[0], a[1], art.bodyRear.pxWidth * k,
                art.bodyRear.pxHeight * spritePx * (float) Math.hypot(rX, rY) * U,
                facing + (float) Math.toDegrees(Math.atan2(-rX, rY)), 1f, 1f, 1f, alpha);
        if (marks != null) marks.part(art.bodyRear, RavWear.BRUTE_HALF, 0, a[0], a[1], art.bodyRear.pxWidth * k,
                art.bodyRear.pxHeight * spritePx * (float) Math.hypot(rX, rY) * U,
                facing + (float) Math.toDegrees(Math.atan2(-rX, rY)), alpha);
        float shX = rig.x(RavBruteRig.SHOULDER), shY = rig.y(RavBruteRig.SHOULDER);
        float frontSpan = SEAM_ROW - RavBruteRig.SHOULDER_ROW;
        float axX = (shX - rig.x(RavBruteRig.SEAM_FRONT)) / frontSpan;   // cells per layer row, toward the head
        float axY = (shY - rig.y(RavBruteRig.SEAM_FRONT)) / frontSpan;
        float bodyAngle = facing + (float) Math.toDegrees(Math.atan2(-axX, axY));
        rowPoint(shX, shY, axX, axY, FRONT_CENTRE_ROW, cx, cy, ppc, fx, fy, a);
        out.add(art.bodyFront, a[0], a[1], art.bodyFront.pxWidth * k,
                art.bodyFront.pxHeight * spritePx * (float) Math.hypot(axX, axY) * U,
                bodyAngle, 1f, 1f, 1f, alpha);
        if (marks != null) marks.part(art.bodyFront, RavWear.BRUTE_HALF, 1, a[0], a[1], art.bodyFront.pxWidth * k,
                art.bodyFront.pxHeight * spritePx * (float) Math.hypot(axX, axY) * U, bodyAngle, alpha);

        // the head: slides along the axis by its dy, scales about the neck, and turns about it
        float hk = rig.headK();
        float neckRow = NECK_ROW + rig.headDy();
        float centreRow = neckRow - (NECK_ROW - HEAD_CENTRE_ROW) * hk;
        rowPoint(shX, shY, axX, axY, neckRow, cx, cy, ppc, fx, fy, b);
        rowPoint(shX, shY, axX, axY, centreRow, cx, cy, ppc, fx, fy, a);
        double yaw = Math.toRadians(rig.headYaw());
        float ox = a[0] - b[0], oy = a[1] - b[1];
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        out.add(art.head, b[0] + ox * cos - oy * sin, b[1] + ox * sin + oy * cos,
                art.head.pxWidth * k * hk, art.head.pxHeight * k * hk, bodyAngle + rig.headYaw(),
                1f, 1f, 1f, alpha);
        if (marks != null) marks.part(art.head, RavWear.BRUTE_HEAD, 2, b[0] + ox * cos - oy * sin,
                b[1] + ox * sin + oy * cos, art.head.pxWidth * k * hk, art.head.pxHeight * k * hk,
                bodyAngle + rig.headYaw(), alpha);
    }

    /**
     * A dead Brute, burst: its ichor on the ground, then each leg (shank over hoof), the body's rear and front
     * halves and the head where the burst left them, then the droplets still in the air. The ichor is the
     * Runner's ({@code splat}, {@code drop}); without it the pieces are drawn bare.
     */
    static void emitDead(DrawList out, RavBruteAssets art, LayeredSpriteCache[] splat, LayeredSpriteCache drop,
                         RavBruteRig rig, float cx, float cy, float ppc, float alpha) {
        emitDead(units(out), art, splat, drop, rig, cx, cy, ppc, alpha);
    }

    static void emitDead(LayeredUnitComposer.SpriteEmitter out, RavBruteAssets art, LayeredSpriteCache[] splat,
                         LayeredSpriteCache drop, RavBruteRig rig, float cx, float cy, float ppc, float alpha) {
        RavBurst burst = rig.burst();
        float facing = rig.facingDegrees();
        double rad = Math.toRadians(facing);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float spritePx = ppc / (U * SCALE);
        RavIchor.Ground ground = (x, y, o) -> toScreen(x, y, cx, cy, ppc, fx, fy, o);
        if (splat != null) RavIchor.onGround(out, splat, burst, ground, ppc, facing, 1f, alpha);

        float[] d = new float[3], a = new float[2], b = new float[2];
        for (int leg : LEG_ORDER) {
            int piece = RavBruteRig.PIECE_LEG + leg;
            int start = RavBruteRig.LEG_START[leg];
            int hoof = start + RavBruteRig.LEG_POINTS[leg] - 1;
            float turn = facing + burst.pieceSpin(piece);
            burst.deadPoint(piece, hoof, 0f, 0f, d);
            toScreen(d[0], d[1], cx, cy, ppc, fx, fy, b);
            LayeredSpriteCache h = art.hoof[leg];
            float k = spritePx * HOOF_SCALE * (1f + HOOF_LIFT * d[2]);
            out.add(h, b[0], b[1], h.pxWidth * k, h.pxHeight * k, turn,
                    1f, 1f, 1f, alpha);
            burst.deadPoint(piece, start, 0f, 0f, d);
            toScreen(d[0], d[1], cx, cy, ppc, fx, fy, a);
            boolean front = leg < 2;
            LayeredSpriteCache s = art.shank[front ? 0 : 1][leg % 2];
            float width = SHANK_W[front ? 0 : 1] * ppc / U;
            float dx = a[0] - b[0], dy = a[1] - b[1];
            float ln = (float) Math.hypot(dx, dy);
            float angle = ln > 1e-3f ? (float) Math.toDegrees(Math.atan2(-dx, dy)) : turn;
            out.add(s, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2,
                    width, ln + width, angle, 1f, 1f, 1f, alpha);
        }
        deadPiece(out, art.bodyRear, burst, RavBruteRig.PIECE_REAR, RavBruteRig.SHOULDER,
                (RavBruteRig.SHOULDER_ROW - REAR_CENTRE_ROW) / U, cx, cy, ppc, fx, fy, facing, spritePx, alpha, d, a);
        deadPiece(out, art.bodyFront, burst, RavBruteRig.PIECE_FRONT, RavBruteRig.SHOULDER,
                (RavBruteRig.SHOULDER_ROW - FRONT_CENTRE_ROW) / U, cx, cy, ppc, fx, fy, facing, spritePx, alpha, d, a);
        deadPiece(out, art.head, burst, RavBruteRig.PIECE_HEAD, RavBruteRig.HEAD, 0f,
                cx, cy, ppc, fx, fy, facing, spritePx, alpha, d, a);
        if (drop != null) RavIchor.inFlight(out, drop, burst, ground, ppc, 1f, alpha);
    }

    /** One burst piece's sprite, whose centre lies {@code ahead} cells forward of {@code point} as it died. */
    private static void deadPiece(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache sprite, RavBurst burst, int piece, int point,
                                  float ahead, float cx, float cy, float ppc, float fx, float fy, float facing,
                                  float spritePx, float alpha, float[] d, float[] at) {
        burst.deadPoint(piece, point, 0f, ahead, d);
        toScreen(d[0], d[1], cx, cy, ppc, fx, fy, at);
        out.add(sprite, at[0], at[1], sprite.pxWidth * spritePx,
                sprite.pxHeight * spritePx, facing + burst.pieceSpin(piece), 1f, 1f, 1f, alpha);
    }

    private static LayeredUnitComposer.SpriteEmitter units(DrawList out) {
        return (layer, x, y, w, h, angle, r, g, b, a) -> out.addSprite(RenderLayer.UNITS, layer.sprite, x, y, w, h,
                angle, r, g, b, a);
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
