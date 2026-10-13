package com.dillon.starsectormarines.battle.story;

/** A finite road party can be refused by a sustained physical boundary screen. */
public final class ColdStartRoadEntry {
    public static final long SCREEN_TICKS = 240;
    private long screenedSince = -1;
    public boolean denied(long tick, boolean visible, boolean friendlyScreen) {
        if (!visible || !friendlyScreen) { screenedSince = -1; return false; }
        if (screenedSince < 0) screenedSince = tick;
        return tick - screenedSince >= SCREEN_TICKS;
    }
}
