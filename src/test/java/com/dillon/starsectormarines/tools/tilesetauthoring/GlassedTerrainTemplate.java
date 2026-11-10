package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.nio.file.Path;

/**
 * The glassed escarpment's nine-swatch guide, on {@link IceTerrainTemplate}'s registration: a bleached
 * summit crust, pale ash talus, then seven scorched-to-bone face values. Flat luminance only; the brief
 * owns the texture, since drawn marks become repeated motifs.
 */
public final class GlassedTerrainTemplate {
    public static final IceTerrainTemplate.Spec GLASSED = new IceTerrainTemplate.Spec("glassed-terrain", "terrain.glassed",
            i -> i==0?new Color(198,195,180):i==1?new Color(170,167,157):warmGrey(52+(i-2)*24),
            null,
            "GlassedTerrainTemplate: bleached summit crust with fused glass patches, ash-and-glass talus, then seven heat-crazed face values dark scorched to pale bone. Inset material cuts; runtime contour owns all geometry.");

    private static Color warmGrey(int light) {
        return new Color(Math.min(255,light+5),Math.min(255,light+2),Math.max(0,light-7));
    }

    public static void main(String[] args) throws Exception { IceTerrainTemplate.write(Path.of(args[0]), GLASSED); }
}
