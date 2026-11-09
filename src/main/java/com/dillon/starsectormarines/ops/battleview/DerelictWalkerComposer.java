package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws the derelict security walker from its rig, seen from above: each leg a bone from its hip to its foot, the
 * planted feet, the lifted legs over the planted ones and drawn a little larger, then the torso over all of them,
 * turned to the walker's facing.
 */
final class DerelictWalkerComposer {

    /** The torso plate's width, cells, at render scale one. */
    static final float TORSO_CELLS = 0.75f;
    static final float LEG_WIDTH_CELLS = 0.22f;
    static final float FOOT_CELLS = 0.36f;
    /** How much larger a leg at the top of its step draws: it is nearer the camera. */
    static final float LIFT_GROWTH = 0.25f;

    private DerelictWalkerComposer() {
    }

    /**
     * @param cam         converts cells to screen px
     * @param renderScale the walker's render scale (the torso's size)
     */
    static void emit(LayeredUnitComposer.SpriteEmitter out, DerelictWalkerAssets art, DerelictLegRig rig,
                     BattleCamera cam, float renderScale, float alpha) {
        float ppc = cam.cellPxSize();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < rig.legs(); i++) {
                boolean lifted = rig.lift(i) > 0f;
                if (lifted != (pass == 1)) continue;
                float grow = 1f + LIFT_GROWTH * rig.lift(i);
                float hx = cam.cellToScreenX(rig.hipX(i));
                float hy = cam.cellToScreenY(rig.hipY(i));
                float fx = cam.cellToScreenX(rig.footX(i));
                float fy = cam.cellToScreenY(rig.footY(i));
                float dx = fx - hx;
                float dy = fy - hy;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                // The bone sprite is authored head-up: turned so its length lies along hip to foot.
                float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
                out.add(art.leg, (hx + fx) / 2f, (hy + fy) / 2f, LEG_WIDTH_CELLS * ppc * grow, length, angle,
                        1f, 1f, 1f, alpha);
                float foot = FOOT_CELLS * ppc * grow;
                out.add(art.foot, fx, fy, foot, foot, angle + 180f, 1f, 1f, 1f, alpha);
            }
        }
        float torso = TORSO_CELLS * renderScale * ppc;
        out.add(art.torso, cam.cellToScreenX(rig.bodyX()), cam.cellToScreenY(rig.bodyY()), torso, torso,
                rig.facing(), 1f, 1f, 1f, alpha);
    }
}
