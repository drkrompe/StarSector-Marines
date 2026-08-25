package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;

/** Shared resolution-fit and user UI-scale policy for retained Marine Ops screens. */
public final class MarineOpsUiViewport {

    public static final float REFERENCE_WIDTH = 1744f;
    public static final float REFERENCE_HEIGHT = 938f;

    private MarineOpsUiViewport() {
    }

    public static UiViewport from(PositionAPI position) {
        if (position == null) throw new IllegalArgumentException("position is required");
        return UiViewport.relative(
                position.getX(), position.getY(),
                position.getWidth(), position.getHeight(),
                Global.getSettings().getScreenScaleMult(),
                REFERENCE_WIDTH, REFERENCE_HEIGHT);
    }
}
