package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.LandingPad;

import java.util.List;

/**
 * Who comes off the field unhurt when the company withdraws.
 *
 * <p>A marine inside the extraction circle around a pad the company landed on
 * boards and leaves. Everyone else still on the ground fights their way back
 * and comes home wounded. A battle with no recorded pads extracts nobody
 * cleanly. The battle draws the same circles while Retreat is being confirmed,
 * so what the player sees is exactly what is tested here.
 */
final class WithdrawalExtraction {

    /** Cells beyond a pad's footprint that still count as at the landing zone. */
    static final int EXTRACTION_MARGIN_CELLS = 10;

    private WithdrawalExtraction() {
    }

    /** The circle's centre in continuous cell coordinates: the middle of the pad's centre cell. */
    static float centreX(LandingPad pad) {
        return pad.centerX + 0.5f;
    }

    static float centreY(LandingPad pad) {
        return pad.centerY + 0.5f;
    }

    /** Radius in cells: the pad's larger half-extent, out to its cell edge, plus the margin. */
    static float radius(LandingPad pad) {
        return Math.max(pad.halfWidth, pad.halfHeight) + 0.5f + EXTRACTION_MARGIN_CELLS;
    }

    static boolean extracts(List<LandingPad> pads, float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return false;
        for (LandingPad pad : pads) {
            float dx = x - centreX(pad);
            float dy = y - centreY(pad);
            float r = radius(pad);
            if (dx * dx + dy * dy <= r * r) return true;
        }
        return false;
    }
}
