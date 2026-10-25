package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavRunnerRig;
import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * Draws a Rav Bloom Runner from its skeleton, seen from above: every bone sprite is turned toward
 * the bone as projected onto the ground and shortened to its projected length, so a leg swinging
 * under the body foreshortens instead of sliding. Legs go under the body and the spike arms over
 * it. Ported from {@code art-source/rav-redesign/exploration/render_runner.py}. A dead Runner is
 * drawn burst: its pieces where the rig flung them, over the ichor it sprayed. A whelp's spikes
 * are nubs and an elder's longer than its size alone makes them ({@link #spikeScale}).
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
    /** Hind legs under the front ones. */
    private static final int[] LEG_ORDER = {2, 3, 0, 1};
    /** The spike's length against the body's, by growth stage: whelp nubs, an elder's grown out. */
    private static final float WHELP_SPIKE = 0.55f, ELDER_SPIKE = 1.30f;
    /**
     * A skitterer's head down over its work: how much shorter it draws as it pitches toward the ground, and how much
     * darker in the body's shadow, at full dip.
     */
    private static final float DIP_SHORTEN = 0.35f, DIP_SHADE = 0.18f;
    /** What it carries, cells across, held this far out past the head's tip. */
    private static final float LOAD_CELLS = 0.2f, LOAD_REACH_CELLS = 0.03f;

    private RavRunnerComposer() {
    }

    /**
     * @param cx  screen position of the rig's body
     * @param cy  screen position of the rig's body
     * @param ppc   screen px per cell
     * @param shade brightness the sprites are drawn at
     */
    static void emit(DrawList out, RavRunnerAssets art, RavRunnerRig rig,
                     float cx, float cy, float ppc, float shade, float alpha) {
        emit((layer, x, y, w, h, angle, r, g, b, a) -> out.addSprite(RenderLayer.UNITS, layer.sprite,
                x, y, w, h, angle, r, g, b, a), art, rig, cx, cy, ppc, shade, alpha);
    }

    /** The same composition into any emitter, so an art review draws exactly what the battle draws. */
    static void emit(LayeredUnitComposer.SpriteEmitter out, RavRunnerAssets art, RavRunnerRig rig,
                     float cx, float cy, float ppc, float shade, float alpha) {
        Frame f = new Frame(rig, cx, cy, ppc);
        float[] p0 = new float[2], p1 = new float[2];
        if (rig.dead()) {
            ichorOnGround(out, art, f, shade, alpha);
        }
        // a burst body's bones are drawn at their full length once its pieces lie flat
        float minAlong = rig.dead() ? 0.2f + 0.8f * rig.flat() : 0.2f;
        for (int leg : LEG_ORDER) {
            f.piece = RavRunnerRig.PIECE_LEG + leg;
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
                        LEG_WIDTH, i == bones - 1 ? lift : 1f, f.facing, shade, alpha, minAlong);
            }
        }
        // trunk, in two halves from mid-spine so it bends through a turn: the rear half (tail and all)
        // out to the hips, then the front half out to the shoulders over it
        float halfSpine = RavRunnerRig.SPINE / 2 * ppc;
        f.piece = RavRunnerRig.PIECE_REAR;
        f.screen(RavRunnerRig.MID, p0);
        f.screen(RavRunnerRig.HIP, p1);
        bone(out, art.trunkRear, TRUNK_HALF_PX, p0, p1, halfSpine, 1f, 1f, f.facing + 180f, shade, alpha, minAlong);
        f.piece = RavRunnerRig.PIECE_FRONT;
        f.screen(RavRunnerRig.MID, p0);
        f.screen(RavRunnerRig.SHOULDER, p1);
        bone(out, art.trunkFront, TRUNK_HALF_PX, p0, p1, halfSpine, 1f, 1f, f.facing, shade, alpha, minAlong);
        // head and neck: pivots at the neck root toward the skeleton's head
        f.piece = RavRunnerRig.PIECE_HEAD;
        f.screen(RavRunnerRig.SHOULDER, p0, 0f, NECK_ROOT_CELLS);
        f.screen(RavRunnerRig.HEAD, p1);
        float dx = p1[0] - p0[0], dy = p1[1] - p0[1];
        float d = (float) Math.hypot(dx, dy);
        float full = FORE_CELLS * ppc;
        float dip = rig.headDip(), drawn = full * (1f - DIP_SHORTEN * dip);
        float ux = d > 1e-4f ? dx / d : (float) -Math.sin(Math.toRadians(f.facing));
        float uy = d > 1e-4f ? dy / d : (float) Math.cos(Math.toRadians(f.facing));
        p1[0] = p0[0] + ux * drawn;
        p1[1] = p0[1] + uy * drawn;
        bone(out, art.fore, FORE_PX, p0, p1, full, 1f, 1f, f.facing, shade * (1f - DIP_SHADE * dip), alpha, 0.2f);
        int held = rig.held();
        LayeredSpriteCache load = held == RavRunnerRig.WORK_FEED ? art.loadFeed
                : held == RavRunnerRig.WORK_PULP ? art.loadPulp : null;
        if (load != null) {
            float size = LOAD_CELLS * ppc, reach = LOAD_REACH_CELLS * ppc;
            out.add(load, p1[0] + ux * reach, p1[1] + uy * reach, size, size,
                    (float) Math.toDegrees(Math.atan2(-ux, uy)), shade, shade, shade, alpha);
        }
        // spike arms on top; a skitterer has none, its mandibles are in its head
        for (int a = 0; a < 2 && art.arm[0][0] != null; a++) {
            int start = RavRunnerRig.ARM_START[a];
            f.piece = RavRunnerRig.PIECE_ARM + a;
            for (int i = 0; i < 3; i++) {
                f.screen(start + i, p0);
                f.screen(start + i + 1, p1);
                float grow = i == 2 ? spikeScale(rig.stage()) : 1f;
                p1[0] = p0[0] + (p1[0] - p0[0]) * grow;
                p1[1] = p0[1] + (p1[1] - p0[1]) * grow;
                bone(out, art.arm[i][a], ARM_PX[i], p0, p1,
                        dist3(rig, start + i, start + i + 1) * ppc * grow, ARM_WIDTH, 1f, f.facing, shade, alpha,
                        minAlong);
            }
        }
        if (rig.dead()) {
            ichorInFlight(out, art, f, shade, alpha);
        }
    }

    /** The spike bone's length against the adult's at the same body size. */
    static float spikeScale(SwarmStage stage) {
        return stage == SwarmStage.WHELP ? WHELP_SPIKE : stage == SwarmStage.ELDER ? ELDER_SPIKE : 1f;
    }

    private static void ichorOnGround(LayeredUnitComposer.SpriteEmitter out, RavRunnerAssets art, Frame f,
                                      float shade, float alpha) {
        RavIchor.onGround(out, art.splat, f.rig.burst(), f::rigToScreen, f.ppc, f.facing, shade, alpha);
    }

    private static void ichorInFlight(LayeredUnitComposer.SpriteEmitter out, RavRunnerAssets art, Frame f,
                                      float shade, float alpha) {
        RavIchor.inFlight(out, art.drop, f.rig.burst(), f::rigToScreen, f.ppc, shade, alpha);
    }

    /**
     * One bone from p0 toward p1. full is the bone's unforeshortened length on screen: it sets
     * the sprite's scale across, and the projected length its scale along (never under minAlong
     * of full).
     */
    static void bone(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache sprite, float bonePx, float[] p0, float[] p1,
                     float full, float widthMul, float lift, float fallbackFacing, float shade,
                     float alpha, float minAlong) {
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
        float along = Math.max(minAlong * full, length) / bonePx * widthMul;
        float toCentre = bonePx / 2f * along;
        out.add(sprite, p0[0] + ux * toCentre, p0[1] + uy * toCentre,
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
        /** The burst piece the points being drawn belong to, once the Runner is dead. */
        int piece;
        private final float[] dead = new float[3];

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
            if (rig.dead()) {
                rig.deadPoint(piece, point, addX, addY, dead);
                rigToScreen(dead[0], dead[1], out);
                return;
            }
            rigToScreen(rig.x(point) + addX, rig.y(point) + addY + rig.drawOffset(), out);
        }

        void rigToScreen(float x, float y, float[] out) {
            // right is forward turned clockwise: (fy, -fx)
            out[0] = cx + (x * fy + y * fx) * ppc;
            out[1] = cy + (-x * fx + y * fy) * ppc;
        }
    }
}
