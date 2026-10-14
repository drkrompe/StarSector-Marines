package com.dillon.starsectormarines.battle.airsupport;

/**
 * One kind's stock in a side's air pool: how many airframes it starts with, the most it will ever hold (counting the
 * ones out on a sortie), and how long it takes the side to replace one that is missing.
 *
 * @param initial        airframes available when the battle begins, never above {@code cap}
 * @param cap            the establishment: airframes available plus airframes out flying never exceed it
 * @param regenSeconds   sim-seconds to bring one airframe back while below the establishment; non-positive means the
 *                       side never replaces a loss
 */
public record AirPoolStock(int initial, int cap, float regenSeconds) {

    public static final AirPoolStock NONE = new AirPoolStock(0, 0, 0f);

    public AirPoolStock {
        if (cap < 0) cap = 0;
        initial = Math.max(0, Math.min(initial, cap));
        if (!(regenSeconds > 0f)) regenSeconds = 0f;
    }

    /** Whether this stock holds nothing and will never hold anything. */
    public boolean isNone() {
        return cap == 0;
    }
}
