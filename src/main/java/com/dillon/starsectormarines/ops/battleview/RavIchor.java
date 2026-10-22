package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBurst;

/**
 * The ichor of a Rav Bloom body that has burst ({@link RavBurst}), shared by every rig composer: the pool
 * spreading under the middle, a smear under each piece that came away and came to rest, the droplets that
 * have landed as splats, and those still in the air, drawn larger the higher they are. The ground part goes
 * under the pieces and the droplets in flight over them.
 */
final class RavIchor {

    /** An ichor droplet in flight, cells across at ground level. */
    private static final float DROP_CELLS = 0.07f;

    /** Lays a point of the rig frame (x right, y forward, cells) onto the screen. */
    interface Ground {
        void toScreen(float x, float y, float[] out);
    }

    private RavIchor() {
    }

    static void onGround(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache[] splat, RavBurst burst,
                         Ground ground, float ppc, float facing, float shade, float alpha) {
        float[] at = new float[2];
        float pool = burst.poolCells() * ppc;
        if (pool > 0.5f) {
            ground.toScreen(burst.poolX(), burst.poolY(), at);
            splat(out, splat[burst.poolVariant()], at, pool, facing + burst.poolAngle(), shade, alpha);
        }
        // a wet smear where each piece came down
        for (int i = 0; i < burst.pieceCount(); i++) {
            if (!burst.pieceResting(i)) continue;
            ground.toScreen(burst.pieceX(i), burst.pieceY(i), at);
            splat(out, splat[(i + burst.poolVariant()) % splat.length], at,
                    (burst.trunk(i) ? 0.5f : 0.3f) * ppc, facing + i * 77f, shade, alpha);
        }
        for (int i = 0; i < burst.dropCount(); i++) {
            if (!burst.dropLanded(i)) continue;
            ground.toScreen(burst.dropX(i), burst.dropY(i), at);
            splat(out, splat[burst.dropVariant(i)], at, burst.dropSize(i) * ppc,
                    facing + burst.dropAngle(i), shade, alpha);
        }
    }

    static void inFlight(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache drop, RavBurst burst,
                         Ground ground, float ppc, float shade, float alpha) {
        float[] at = new float[2];
        for (int i = 0; i < burst.dropCount(); i++) {
            if (burst.dropLanded(i)) continue;
            ground.toScreen(burst.dropX(i), burst.dropY(i), at);
            splat(out, drop, at, DROP_CELLS * (1f + 1.5f * burst.dropZ(i)) * ppc, 0f, shade, alpha);
        }
    }

    private static void splat(LayeredUnitComposer.SpriteEmitter out, LayeredSpriteCache sprite, float[] at,
                              float size, float angle, float shade, float alpha) {
        out.add(sprite, at[0], at[1], size, size * sprite.pxHeight / sprite.pxWidth, angle,
                shade, shade, shade, alpha);
    }
}
