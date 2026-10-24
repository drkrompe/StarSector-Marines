package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavSpitterRig;

/**
 * Draws a Spitter from its parts, seen from above: the tail's slices coiled out from under the haunch, the arms
 * about their shoulder sockets, the back over them, and the head on top, turned about its neck, with its
 * hood folded or popped open as the rig fires (the flared hood grows out from the folded one). A dead Spitter lies
 * dulled in a pool of its own acid ({@link #emitDead}).
 */
final class RavSpitterComposer {

    /** Sprite px per canvas px, as {@code export_spitter_rig.py} wrote them. */
    private static final float SCALE = 0.25f;
    private static final float U = RavSpitterRig.U_LAYER;
    private static final float ANCHOR = RavSpitterRig.ANCHOR_ROW, CENTRE = RavSpitterRig.CENTRE_COL;
    /** Canvas row of each sprite's centre (the export boxes): the back, and the head in either hood state. */
    private static final float BACK_ROW = 565f, HEAD_ROW = 250f;
    /**
     * The left arm on its own canvas: its box's centre, its shoulder ball (the pivot set on the back's socket), the
     * share it is drawn at, and its rest swing off the drawn pose, raised forward (degrees).
     */
    private static final float ARM_COL = 360f, ARM_ROW = 602.5f, ARM_PIVOT_COL = 536f, ARM_PIVOT_ROW = 517f;
    private static final float ARM_SCALE = 0.75f, ARM_REST = -25f;
    /** The flared hood opens out from this share of its width. */
    private static final float HOOD_FROM = 0.55f;
    /** The canvas centre row of the corpse, the corpse's pool across, and the corpse drawn a little flattened. */
    private static final float CANVAS_CENTRE_ROW = 768f, POOL_CELLS = 1.25f, DEAD_SCALE = 0.97f;

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

        // the tail, chained from its root under the chest; drawn tip first so each segment lies over the one behind it
        float[][] root = new float[RavSpitterRig.TAIL_SEGMENTS][2];
        float[] angle = new float[RavSpitterRig.TAIL_SEGMENTS];
        point(CENTRE, RavSpitterRig.COIL_ROOT_ROW, chest, ax, ay, ppc, p);
        float jx = p[0], jy = p[1], a = chest;
        for (int i = 0; i < RavSpitterRig.TAIL_SEGMENTS; i++) {
            a += rig.tailAngle(i);
            angle[i] = a;
            root[i][0] = jx;
            root[i][1] = jy;
            float length = (RavSpitterRig.TAIL_ROWS[i + 1] - RavSpitterRig.TAIL_ROWS[i]) / U;
            rotate(0f, -length, a, p);
            jx += p[0] * ppc;
            jy += p[1] * ppc;
        }
        for (int i = RavSpitterRig.TAIL_SEGMENTS - 1; i >= 0; i--) {
            float back = (RavSpitterRig.TAIL_ROWS[i + 1] - RavSpitterRig.TAIL_ROWS[i]) / 2f / U;
            rotate(0f, -back, angle[i], p);
            LayeredSpriteCache s = art.tail[i];
            out.add(s, root[i][0] + p[0] * ppc, root[i][1] + p[1] * ppc, s.pxWidth * spritePx,
                    s.pxHeight * spritePx, angle[i], 1f, 1f, 1f, alpha);
        }

        for (int side = 0; side < 2; side++) {        // arms about their shoulder sockets
            float sign = side == RavSpitterRig.LEFT ? 1f : -1f;
            float shoulderCol = CENTRE - sign * (CENTRE - RavSpitterRig.SHOULDER_COL);
            point(shoulderCol, RavSpitterRig.SHOULDER_ROW, chest, ax, ay, ppc, p);
            // the left swings back counter-clockwise, the right clockwise
            float swing = sign * (ARM_REST + rig.clawAngle(side));
            float ox = sign * (ARM_COL - ARM_PIVOT_COL) * ARM_SCALE / U;
            float oy = (ARM_PIVOT_ROW - ARM_ROW) * ARM_SCALE / U;
            float[] o = new float[2];
            rotate(ox, oy, chest + swing, o);
            LayeredSpriteCache s = art.arm[side];
            out.add(s, p[0] + o[0] * ppc, p[1] + o[1] * ppc, s.pxWidth * spritePx * ARM_SCALE,
                    s.pxHeight * spritePx * ARM_SCALE, chest + swing, 1f, 1f, 1f, alpha);
        }

        point(CENTRE, BACK_ROW, chest, ax, ay, ppc, p);
        out.add(art.back, p[0], p[1], art.back.pxWidth * spritePx, art.back.pxHeight * spritePx, chest,
                1f, 1f, 1f, alpha);

        // the head about its neck, its hood folded under the flared one as it pops
        float[] neck = new float[2];
        point(CENTRE, RavSpitterRig.NECK_ROW, chest, ax, ay, ppc, neck);
        float head = chest + rig.headYaw();
        float k = rig.headScale();
        float hood = rig.hood();
        if (hood < 0.98f) headSprite(out, art.headShut, HEAD_ROW, neck, head, k, 1f, ppc, spritePx, alpha);
        if (hood > 0.02f) {
            headSprite(out, art.headOpen, HEAD_ROW, neck, head, k, HOOD_FROM + (1f - HOOD_FROM) * hood, ppc,
                    spritePx, alpha * Math.min(1f, hood * 2.5f));
        }
    }

    private static void headSprite(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache s, float centreRow,
                                   float[] neck, float head, float k, float widen, float ppc, float spritePx,
                                   float alpha) {
        float[] o = new float[2];
        rotate(0f, (RavSpitterRig.NECK_ROW - centreRow) / U * k, head, o);
        out.add(s, neck[0] + o[0] * ppc, neck[1] + o[1] * ppc, s.pxWidth * spritePx * k * widen,
                s.pxHeight * spritePx * k, head, 1f, 1f, 1f, alpha);
    }

    static void emitDead(DrawList out, RavSpitterAssets art, float cx, float cy, float facing, float ppc,
                         float alpha) {
        emitDead(units(out), art, cx, cy, facing, ppc, alpha);
    }

    /** A dead Spitter: dulled and lying where it fell, in the acid it spilled. */
    static void emitDead(LayeredUnitComposer.SpriteEmitter out, RavSpitterAssets art, float cx, float cy,
                         float facing, float ppc, float alpha) {
        float pool = POOL_CELLS * ppc;
        out.add(art.pool, cx, cy, pool, pool * art.pool.pxHeight / art.pool.pxWidth, facing + 37f,
                1f, 1f, 1f, alpha);
        float spritePx = ppc / (U * SCALE) * DEAD_SCALE;
        float[] p = new float[2];
        point(CENTRE, CANVAS_CENTRE_ROW, facing, cx, cy, ppc, p);
        out.add(art.dead, p[0], p[1], art.dead.pxWidth * spritePx, art.dead.pxHeight * spritePx, facing,
                1f, 1f, 1f, alpha);
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
