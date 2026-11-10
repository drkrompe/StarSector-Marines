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
    /** How much larger a part draws per cell of height. */
    static final float HEIGHT_GROWTH = 0.10f;
    /** The torso's own growth per cell of rise: stronger than a leg's, so its bob reads from above. */
    static final float TORSO_HEIGHT_GROWTH = 0.45f;
    /** The knee joint drawn a little wider than the segments it joins, so it reads as the pivot. */
    static final float JOINT_SCALE = 1.15f;
    /** A lost leg's shin, darkened. */
    static final float LOST_SHADE = 0.55f;
    /** A lost knee's joint, burnt orange. */
    static final float[] LOST_KNEE = {1f, 0.48f, 0.24f};
    /** The LEGS marker's pips: size and gap in px, and how far above the body it stands, cells. */
    static final float PIP_PX = 14f;
    static final float PIP_GAP_PX = 5f;
    static final float MARKER_CELLS = 1.6f;

    private DerelictWalkerComposer() {
    }

    /**
     * @param cam         converts cells to screen px
     * @param renderScale the walker's render scale (the torso's size)
     */
    static void emit(LayeredUnitComposer.SpriteEmitter out, DerelictWalkerAssets art, DerelictLegRig rig,
                     BattleCamera cam, float renderScale, float alpha) {
        emitBody(out, art, rig, cam, TORSO_CELLS * renderScale, alpha);
    }

    /**
     * Any four-legged machine on this rig (the walker, Tri-Tachyon's crawler): its legs sized by the rig's
     * {@link DerelictLegRig.Build}, its torso drawn {@code torsoCells} wide at rest.
     */
    static void emitBody(LayeredUnitComposer.SpriteEmitter out, DerelictWalkerAssets art, DerelictLegRig rig,
                         BattleCamera cam, float torsoCells, float alpha) {
        DerelictLegRig.Build build = rig.build();
        float inset = build.jointInsetCells();
        float[] knee = new float[3];
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < rig.legs(); i++) {
                boolean lifted = rig.lift(i) > 0f;
                if (lifted != (pass == 1)) continue;
                float footHeight = (rig.onWall(i) ? build.wallFootCells() : 0f) + build.stepLiftCells() * rig.lift(i);
                float hx = rig.hipX(i);
                float hy = rig.hipY(i);
                float hipZ = build.hipHeightCells() + rig.bodyHeight();
                DerelictLegPose.knee(hx, hy, hipZ, rig.footX(i), rig.footY(i), footHeight, build.thighCells(),
                        build.shinCells(), knee);
                // A lost leg is scorched below its broken knee.
                float shade = rig.lost(i) ? LOST_SHADE : 1f;
                bone(out, art.shin, cam, knee[0], knee[1], rig.footX(i), rig.footY(i), (knee[2] + footHeight) / 2f,
                        inset, shade, alpha);
                bone(out, art.thigh, cam, hx, hy, knee[0], knee[1], (hipZ + knee[2]) / 2f, inset, 1f, alpha);
                float joint = art.joint.pxWidth / DerelictWalkerAssets.PX_PER_CELL * JOINT_SCALE * cam.cellPxSize()
                        * (1f + HEIGHT_GROWTH * knee[2]);
                float legAngle = (float) Math.toDegrees(Math.atan2(-(rig.footX(i) - hx), rig.footY(i) - hy));
                if (rig.lost(i)) {
                    out.add(art.joint, cam.cellToScreenX(knee[0]), cam.cellToScreenY(knee[1]), joint, joint, legAngle,
                            LOST_KNEE[0], LOST_KNEE[1], LOST_KNEE[2], alpha);
                } else {
                    out.add(art.joint, cam.cellToScreenX(knee[0]), cam.cellToScreenY(knee[1]), joint, joint, legAngle,
                            1f, 1f, 1f, alpha);
                }
            }
        }
        float ppc = cam.cellPxSize();
        // The body nearer the camera as it rises, further as it sinks onto its legs.
        float torsoW = torsoCells * ppc * (1f + TORSO_HEIGHT_GROWTH * rig.bodyHeight());
        float torsoH = torsoW * art.torso.pxHeight / Math.max(1f, art.torso.pxWidth);
        out.add(art.torso, cam.cellToScreenX(rig.bodyX()), cam.cellToScreenY(rig.bodyY()), torsoW, torsoH,
                rig.bodyFacing(), 1f, 1f, 1f, alpha);
    }

    /**
     * The LEGS marker over a walker that has lost a knee: a pip a leg, green while it stands and red once lost, in a
     * dark frame. Nothing over a whole walker.
     */
    static void marker(DrawList out, DerelictLegRig rig, BattleCamera cam, float alpha) {
        int lost = 0;
        for (int i = 0; i < rig.legs(); i++) if (rig.lost(i)) lost++;
        if (lost == 0) return;
        float cx = cam.cellToScreenX(rig.bodyX());
        // Screen y runs up.
        float cy = cam.cellToScreenY(rig.bodyY()) + MARKER_CELLS * cam.cellPxSize();
        float width = rig.legs() * PIP_PX + (rig.legs() - 1) * PIP_GAP_PX;
        float x = cx - width / 2f;
        out.addSolidRect(RenderLayer.UNITS, x - 3f, cy - 3f, x + width + 3f, cy + PIP_PX + 3f, 0.06f, 0.06f, 0.07f,
                0.85f * alpha);
        // Pips left to right: front left, rear left, rear right, front right, as the legs stand round the body.
        int[] order = {0, 1, 2, 3};
        for (int k = 0; k < order.length; k++) {
            boolean gone = rig.lost(order[k]);
            float px = x + k * (PIP_PX + PIP_GAP_PX);
            out.addSolidRect(RenderLayer.UNITS, px, cy, px + PIP_PX, cy + PIP_PX,
                    gone ? 0.92f : 0.30f, gone ? 0.25f : 0.82f, gone ? 0.20f : 0.38f, alpha);
        }
    }

    /**
     * One leg part from its root joint at (x0, y0) to its far end at (x1, y1), cells: its root joint centred on the
     * root, its length the seen distance (a part reaching up or down looks shorter from above), its width as authored.
     * It is never drawn longer than authored: a leg foreshortens, it does not stretch.
     */
    private static void bone(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache part, BattleCamera cam,
                             float x0, float y0, float x1, float y1, float height, float inset, float shade,
                             float alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float seen = (float) Math.sqrt(dx * dx + dy * dy);
        if (seen < 1e-4f) return;
        float ux = dx / seen;
        float uy = dy / seen;
        float grow = 1f + HEIGHT_GROWTH * height;
        float length = Math.min(seen + inset, part.pxHeight / DerelictWalkerAssets.PX_PER_CELL);
        float cx = x0 + ux * (length / 2f - inset);
        float cy = y0 + uy * (length / 2f - inset);
        float ppc = cam.cellPxSize();
        float width = part.pxWidth / DerelictWalkerAssets.PX_PER_CELL * ppc * grow;
        // Sprites are authored head-up: turned so the part's top lies along root to far end (0 = +y, counter-clockwise).
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
        out.add(part, cam.cellToScreenX(cx), cam.cellToScreenY(cy), width, length * ppc * grow, angle,
                shade, shade * 0.92f, shade * 0.88f, alpha);
    }
}
