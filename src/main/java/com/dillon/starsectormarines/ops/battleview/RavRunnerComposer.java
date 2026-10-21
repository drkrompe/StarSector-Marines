package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavRunnerRig;

/**
 * Draws a Rav Bloom Runner from its skeleton, seen from above: every bone sprite is turned toward
 * the bone as projected onto the ground and shortened to its projected length, so a leg swinging
 * under the body foreshortens instead of sliding. Legs go under the body and the spike arms over
 * it. Ported from {@code art-source/rav-redesign/exploration/render_runner.py}.
 */
final class RavRunnerComposer {

    /** Each sprite's bone length in sprite px: the layer row tables times the export scale (0.2). */
    private static final float TRUNK_HALF_PX = 41.7f, FORE_PX = 17.4f;
    private static final float[] FRONT_PX = {116f, 120f};
    private static final float[] HIND_PX = {70f, 84f, 86f};
    private static final float[] ARM_PX = {118f, 88f, 45.8f};
    /** The body layer's scale: layer px between the shoulder and hip rows per cell of spine. */
    private static final float BODY_LAYER_PX_PER_CELL = (958f - 541f) / 0.5f;
    /** The head pivots at the neck at its drawn length; it never stretches. */
    private static final float FORE_CELLS = (420f - 333f) / BODY_LAYER_PX_PER_CELL;
    /** The neck root sits this far ahead of the shoulders. */
    private static final float NECK_ROOT_CELLS = (541f - 420f) / BODY_LAYER_PX_PER_CELL;
    private static final float LEG_WIDTH = 1.25f, ARM_WIDTH = 1.1f;
    /** How bright a dead Runner is drawn: the carapace dulls once it lies still. */
    static final float DEAD_SHADE = 0.62f;
    /** Hind legs under the front ones. */
    private static final int[] LEG_ORDER = {2, 3, 0, 1};

    private RavRunnerComposer() {
    }

    /**
     * @param cx  screen position of the rig's body
     * @param cy  screen position of the rig's body
     * @param ppc   screen px per cell
     * @param shade brightness the sprites are drawn at, 1 for a living Runner
     */
    static void emit(DrawList out, RavRunnerAssets art, RavRunnerRig rig,
                     float cx, float cy, float ppc, float shade, float alpha) {
        Frame f = new Frame(rig, cx, cy, ppc);
        float[] p0 = new float[2], p1 = new float[2];
        for (int leg : LEG_ORDER) {
            boolean front = leg < 2;
            int side = leg % 2;
            int start = RavRunnerRig.LEG_START[leg];
            int bones = RavRunnerRig.LEG_POINTS[leg] - 1;
            float lift = 1f + 1.5f * rig.z(start + bones);
            for (int i = 0; i < bones; i++) {
                LayeredSpriteCache sprite = (front ? art.front : art.hind)[i][side];
                float px = (front ? FRONT_PX : HIND_PX)[i];
                f.screen(start + i, p0);
                f.screen(start + i + 1, p1);
                bone(out, sprite, px, p0, p1, dist3(rig, start + i, start + i + 1) * ppc,
                        LEG_WIDTH, i == bones - 1 ? lift : 1f, f.facing, shade, alpha);
            }
        }
        // trunk, in two halves from mid-spine so it bends through a turn: the rear half (tail and all)
        // out to the hips, then the front half out to the shoulders over it
        float halfSpine = RavRunnerRig.SPINE / 2 * ppc;
        f.screen(RavRunnerRig.MID, p0);
        f.screen(RavRunnerRig.HIP, p1);
        bone(out, art.trunkRear, TRUNK_HALF_PX, p0, p1, halfSpine, 1f, 1f, f.facing + 180f, shade, alpha);
        f.screen(RavRunnerRig.MID, p0);
        f.screen(RavRunnerRig.SHOULDER, p1);
        bone(out, art.trunkFront, TRUNK_HALF_PX, p0, p1, halfSpine, 1f, 1f, f.facing, shade, alpha);
        // head and neck: pivots at the neck root toward the skeleton's head
        f.screen(RavRunnerRig.SHOULDER, p0, 0f, NECK_ROOT_CELLS);
        f.screen(RavRunnerRig.HEAD, p1);
        float dx = p1[0] - p0[0], dy = p1[1] - p0[1];
        float d = (float) Math.hypot(dx, dy);
        float full = FORE_CELLS * ppc;
        if (d > 1e-4f) {
            p1[0] = p0[0] + dx / d * full;
            p1[1] = p0[1] + dy / d * full;
        }
        bone(out, art.fore, FORE_PX, p0, p1, full, 1f, 1f, f.facing, shade, alpha);
        // spike arms on top
        for (int a = 0; a < 2; a++) {
            int start = RavRunnerRig.ARM_START[a];
            for (int i = 0; i < 3; i++) {
                f.screen(start + i, p0);
                f.screen(start + i + 1, p1);
                bone(out, art.arm[i][a], ARM_PX[i], p0, p1,
                        dist3(rig, start + i, start + i + 1) * ppc, ARM_WIDTH, 1f, f.facing, shade, alpha);
            }
        }
    }

    /**
     * One bone from p0 toward p1. full is the bone's unforeshortened length on screen: it sets
     * the sprite's scale across, and the projected length its scale along (never under a fifth).
     */
    static void bone(DrawList out, LayeredSpriteCache sprite, float bonePx, float[] p0, float[] p1,
                     float full, float widthMul, float lift, float fallbackFacing, float shade,
                     float alpha) {
        float dx = p1[0] - p0[0], dy = p1[1] - p0[1];
        float length = (float) Math.hypot(dx, dy);
        float angle, ux, uy;
        if (length > 1e-4f) {
            angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
            ux = dx / length;
            uy = dy / length;
        } else {
            angle = fallbackFacing;
            double rad = Math.toRadians(angle);
            ux = (float) -Math.sin(rad);
            uy = (float) Math.cos(rad);
        }
        float across = full / bonePx * widthMul;
        float along = Math.max(0.2f * full, length) / bonePx * widthMul;
        float toCentre = bonePx / 2f * along;
        out.addSprite(RenderLayer.UNITS, sprite.sprite, p0[0] + ux * toCentre, p0[1] + uy * toCentre,
                sprite.pxWidth * across * lift, sprite.pxHeight * along, angle, shade, shade, shade, alpha);
    }

    private static float dist3(RavRunnerRig rig, int a, int b) {
        float dx = rig.x(b) - rig.x(a), dy = rig.y(b) - rig.y(a), dz = rig.z(b) - rig.z(a);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The rig frame (x right, y forward) laid onto the screen at the rig's facing. */
    private static final class Frame {
        final RavRunnerRig rig;
        final float cx, cy, ppc, facing;
        final float fx, fy;

        Frame(RavRunnerRig rig, float cx, float cy, float ppc) {
            this.rig = rig;
            this.cx = cx;
            this.cy = cy;
            this.ppc = ppc;
            this.facing = rig.facingDegrees();
            double rad = Math.toRadians(facing);
            this.fx = (float) -Math.sin(rad);
            this.fy = (float) Math.cos(rad);
        }

        void screen(int point, float[] out) {
            screen(point, out, 0f, 0f);
        }

        void screen(int point, float[] out, float addX, float addY) {
            float x = rig.x(point) + addX, y = rig.y(point) + addY + rig.drawOffset();
            // right is forward turned clockwise: (fy, -fx)
            out[0] = cx + (x * fy + y * fx) * ppc;
            out[1] = cy + (-x * fx + y * fy) * ppc;
        }
    }
}
