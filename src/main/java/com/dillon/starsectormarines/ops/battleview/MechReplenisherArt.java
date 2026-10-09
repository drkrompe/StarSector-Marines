package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

import java.awt.Color;

/** Vanilla autoforge symbols shared by every mini-fab equipment view. */
public final class MechReplenisherArt {
    private MechReplenisherArt() { }

    public static String icon(MissileReplenisherComponent component) {
        return MissileReplenisherComponent.ACCELERATED_FEED.equals(component)
                ? "graphics/icons/hullsys/heavy_missile_autoforge.png"
                : "graphics/icons/hullsys/missile_autoforge.png";
    }

    static void draw(CanvasContext context, MissileReplenisherComponent component,
                     float centerX, float centerY, float size) {
        context.sprite(icon(component), null, centerX, centerY, size, size, 0f,
                new Color(0xd3d0c5));
    }
}
