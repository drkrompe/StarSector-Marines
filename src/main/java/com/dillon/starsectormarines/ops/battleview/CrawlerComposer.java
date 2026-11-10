package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws Tri-Tachyon's light crawler from above (`mech-lines.md`, high-tech light): its carapace and four short legs as
 * the derelict walker's are drawn ({@link DerelictWalkerComposer#emitBody}), from its own parts and its own rig build
 * ({@link DerelictLegRig#CRAWLER}), then its one gun over the carapace. The hull is drawn bare with an empty socket at
 * the front (law 4): the gun is a layer of its own, any arm gun the mount holds, standing on the socket and turned to
 * the mech's aim while the body walks to its own heading.
 */
final class CrawlerComposer {

    /** The front socket's centre ahead of the torso's, as a share of the torso drawing's height (crawler-master.png). */
    static final float MOUNT_FORWARD_SHARE = 0.20f;
    /** The gun drawn at this share of the size it has on a mech of the crawler's render scale. UNTUNED. */
    static final float GUN_SCALE = 1.0f;
    /** How much of the gun's length sits behind the socket's centre, on its ring. */
    static final float GUN_SEAT = 0.35f;

    private CrawlerComposer() {
    }

    /** The carapace's drawn width at render scale one, cells: the parts' own authored density. */
    static float torsoCells(DerelictWalkerAssets art) {
        return art.torso.pxWidth / DerelictWalkerAssets.PX_PER_CELL;
    }

    /**
     * @param guns        the mech weapon layers, or null to draw the body alone
     * @param gun         the front mount's arm-gun selector ({@link LayeredMechAppearance}), 0 for an empty socket
     * @param aimDegrees  the mech's upper-body bearing, the gun's
     */
    static void emit(DrawList out, DerelictWalkerAssets art, LayeredMechAssets guns, DerelictLegRig rig,
                     BattleCamera cam, float renderScale, float aimDegrees, int gun, float kick, boolean flash,
                     float alpha) {
        float torsoCells = torsoCells(art) * renderScale;
        DerelictWalkerComposer.emitBody((layer, x, y, w, h, angle, cr, cg, cb, ca) -> out.addSprite(
                RenderLayer.UNITS, layer.sprite, x, y, w, h, angle, cr, cg, cb, ca), art, rig, cam, torsoCells, alpha);
        if (guns == null || gun == 0) return;
        float[] mount = mount(art, rig, renderScale);
        float hullWidth = LayeredMechAppearance.hullWidthCells(renderScale) * GUN_SCALE * cam.cellPxSize();
        LayeredMechComposer.emitMountedGun(out, guns, gun, cam.cellToScreenX(mount[0]), cam.cellToScreenY(mount[1]),
                hullWidth, aimDegrees, GUN_SEAT, kick, flash, alpha);
    }

    /** The front socket's centre in cells, riding the drawn body: ahead of it along the body's heading. */
    static float[] mount(DerelictWalkerAssets art, DerelictLegRig rig, float renderScale) {
        float torsoHeight = torsoCells(art) * renderScale * art.torso.pxHeight / Math.max(1f, art.torso.pxWidth)
                * (1f + DerelictWalkerComposer.TORSO_HEIGHT_GROWTH * rig.bodyHeight());
        float forward = MOUNT_FORWARD_SHARE * torsoHeight;
        double heading = Math.toRadians(rig.facing());
        // Sprite degrees: 0 is +y, counter-clockwise positive.
        return new float[] {rig.bodyX() - (float) Math.sin(heading) * forward,
                rig.bodyY() + (float) Math.cos(heading) * forward};
    }
}
