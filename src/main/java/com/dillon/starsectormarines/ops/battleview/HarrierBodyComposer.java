package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierBodyRig;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;

/**
 * Lays a live Harrier out from its parts: the frame (the wings and the thorax between them) at the body's anchor,
 * the abdomen turned about the waist, each tail segment about its joint on the one before, and the head about the
 * neck, by the angles its rig holds ({@link HarrierBodyRig}), the whole pumped with the stroke. Back to front:
 * the tail's tip first, the head last. The flier and its shadow are both drawn from this.
 */
final class HarrierBodyComposer {

    /** Where one part goes: its sprite, its centre in cells, its turn and its size in cells. */
    interface Sink {
        void part(ShuttleSpriteCache sprite, float x, float y, float degrees, float w, float h);
    }

    private HarrierBodyComposer() {}

    /** Composes {@code m} with its frame drawing {@code frame}, anchored at (x, y) cells, {@code scale} its size. */
    static void compose(BattleSprites sprites, HarrierFlight m, String frame, float x, float y, float scale,
                        Sink sink) {
        HarrierLook look = m.look;
        HarrierBodyRig rig = m.rig;
        float k = scale * rig.pump / HarrierLook.PX_PER_CELL;
        float t0 = m.body.facingDegrees;
        float[] tailPx = HarrierLook.TAIL_PX;

        // The spine from the waist back: each joint lies along the segment before it.
        float wx = x - fx(t0) * HarrierLook.WAIST_PX * k, wy = y - fy(t0) * HarrierLook.WAIST_PX * k;
        float t1 = t0 + rig.waist;
        float[] jx = new float[HarrierBodyRig.TAIL_SEGMENTS], jy = new float[HarrierBodyRig.TAIL_SEGMENTS];
        float[] heading = new float[HarrierBodyRig.TAIL_SEGMENTS];
        float px = wx, py = wy, prevPx = HarrierLook.WAIST_PX, h = t1;
        for (int i = 0; i < HarrierBodyRig.TAIL_SEGMENTS; i++) {
            px -= fx(h) * (tailPx[i] - prevPx) * k;
            py -= fy(h) * (tailPx[i] - prevPx) * k;
            jx[i] = px;
            jy[i] = py;
            h += rig.tail[i];
            heading[i] = h;
            prevPx = tailPx[i];
        }
        for (int i = HarrierBodyRig.TAIL_SEGMENTS - 1; i >= 0; i--) {
            part(sprites, look.tail(i + 1), jx[i], jy[i], heading[i], tailPx[i], k, sink);
        }
        part(sprites, look.abdomen(), wx, wy, t1, HarrierLook.WAIST_PX, k, sink);
        part(sprites, frame, x, y, t0, 0f, k, sink);
        float nx = x - fx(t0) * HarrierLook.NECK_PX * k, ny = y - fy(t0) * HarrierLook.NECK_PX * k;
        part(sprites, look.head(), nx, ny, t0 + rig.head, HarrierLook.NECK_PX, k, sink);
    }

    /** A part hinged at (jx, jy), turned to {@code degrees}: its centre lies {@code behindPx} ahead of the hinge. */
    private static void part(BattleSprites sprites, String path, float jx, float jy, float degrees, float behindPx,
                             float k, Sink sink) {
        ShuttleSpriteCache cache = sprites.spriteByPath(path);
        if (cache == null) return;
        float cx = jx + fx(degrees) * behindPx * k, cy = jy + fy(degrees) * behindPx * k;
        sink.part(cache, cx, cy, degrees, cache.pxW * k, cache.pxH * k);
    }

    /** The forward direction of a facing, in cells. */
    private static float fx(float degrees) { return (float) -Math.sin(Math.toRadians(degrees)); }

    private static float fy(float degrees) { return (float) Math.cos(Math.toRadians(degrees)); }
}
