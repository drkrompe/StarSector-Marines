package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;

/** Shared full-screen request for every Marine Ops takeover entry path. */
public final class MarineOpsDialogSize {

    private MarineOpsDialogSize() {
    }

    public static float requestedWidth() {
        return Global.getSettings().getScreenWidth();
    }

    public static float requestedHeight() {
        return Global.getSettings().getScreenHeight();
    }
}
