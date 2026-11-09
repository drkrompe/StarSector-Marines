package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegPose;
import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws the derelict security walker from its rig, seen from above. Each leg is a shin from a knee
 * {@link DerelictLegPose} raises to the foot, the thigh from the hip over it, and the knee joint over both; a part
 * higher off the floor draws a little larger, being nearer the camera. Planted legs first, stepping legs over them, the torso over all, turned to the
 * walker's facing.
 */
final class DerelictWalkerComposer {

    /** The torso's width, cells, at render scale one. */
    static final float TORSO_CELLS = 0.85f;
    /** How far a part's root joint centre sits in from its sprite's end, cells. */
    static final float JOINT_INSET_CELLS = 0.16f;
    /** How much larger a part draws per cell of height. */
    static final float HEIGHT_GROWTH = 0.10f;
    /** The knee joint drawn a little wider than the segments it joins, so it reads as the pivot. */
    static final float JOINT_SCALE = 1.15f;

    private DerelictWalkerComposer() {
    }

    /**
     * @param cam         converts cells to screen px
     * @param renderScale the walker's render scale (the torso's size)
     */
    static void emit(LayeredUnitComposer.SpriteEmitter out, DerelictWalkerAssets art, DerelictLegRig rig,
                     BattleCamera cam, float renderScale, float alpha) {
        float[] knee = new float[3];
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < rig.legs(); i++) {
                boolean lifted = rig.lift(i) > 0f;
                if (lifted != (pass == 1)) continue;
                float footHeight = (rig.onWall(i) ? DerelictLegPose.WALL_FOOT_HEIGHT_CELLS : 0f)
                        + DerelictLegPose.STEP_LIFT_CELLS * rig.lift(i);
                float hx = rig.hipX(i);
                float hy = rig.hipY(i);
                DerelictLegPose.knee(hx, hy, rig.footX(i), rig.footY(i), footHeight, knee);
                float hipZ = DerelictLegPose.HIP_HEIGHT_CELLS;
                bone(out, art.shin, cam, knee[0], knee[1], rig.footX(i), rig.footY(i), (knee[2] + footHeight) / 2f,
                        alpha);
                bone(out, art.thigh, cam, hx, hy, knee[0], knee[1], (hipZ + knee[2]) / 2f, alpha);
                float joint = art.joint.pxWidth / DerelictWalkerAssets.PX_PER_CELL * JOINT_SCALE * cam.cellPxSize()
                        * (1f + HEIGHT_GROWTH * knee[2]);
                float legAngle = (float) Math.toDegrees(Math.atan2(-(rig.footX(i) - hx), rig.footY(i) - hy));
                out.add(art.joint, cam.cellToScreenX(knee[0]), cam.cellToScreenY(knee[1]), joint, joint, legAngle,
                        1f, 1f, 1f, alpha);
            }
        }
        float ppc = cam.cellPxSize();
        float torsoW = TORSO_CELLS * renderScale * ppc;
        float torsoH = torsoW * art.torso.pxHeight / Math.max(1f, art.torso.pxWidth);
        out.add(art.torso, cam.cellToScreenX(rig.bodyX()), cam.cellToScreenY(rig.bodyY()), torsoW, torsoH,
                rig.facing(), 1f, 1f, 1f, alpha);
    }

    /**
     * One leg part from its root joint at (x0, y0) to its far end at (x1, y1), cells: its root joint centred on the
     * root, its length the seen distance (a part reaching up or down looks shorter from above), its width as authored.
     */
    private static void bone(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache part, BattleCamera cam,
                             float x0, float y0, float x1, float y1, float height, float alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float seen = (float) Math.sqrt(dx * dx + dy * dy);
        if (seen < 1e-4f) return;
        float ux = dx / seen;
        float uy = dy / seen;
        float grow = 1f + HEIGHT_GROWTH * height;
        float length = seen + JOINT_INSET_CELLS;
        float cx = x0 + ux * (seen - JOINT_INSET_CELLS) / 2f;
        float cy = y0 + uy * (seen - JOINT_INSET_CELLS) / 2f;
        float ppc = cam.cellPxSize();
        float width = part.pxWidth / DerelictWalkerAssets.PX_PER_CELL * ppc * grow;
        // Sprites are authored head-up: turned so the part's top lies along root to far end (0 = +y, counter-clockwise).
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
        out.add(part, cam.cellToScreenX(cx), cam.cellToScreenY(cy), width, length * ppc * grow, angle,
                1f, 1f, 1f, alpha);
    }
}
