package com.dillon.starsectormarines.battle.setup;

/** The declared maintenance-road departure gate, reached through ordinary infantry travel. */
public final class WaterUnderSealRoadExit {
    public static final int X = 173, Y = 88;
    private WaterUnderSealRoadExit() {}
    public static boolean withdraws(int living) { return living > 0 && living <= 2; }
    public static boolean atExit(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && x >= 169 && x < 176 && y >= 83 && y <= 96;
    }
}
