package com.dillon.starsectormarines.ui;

/**
 * Lazy-loaded handles to Starsector's built-in bitmap fonts. The first
 * {@link BitmapFont#drawString} or {@link BitmapFont#measureWidth} call
 * triggers font + atlas load — safe to construct without a GL context.
 *
 * <p>We resolve fonts by their vanilla paths because Starsector ships the
 * atlas image alongside the .fnt manifest. Mods can add their own
 * {@link BitmapFont} instances pointing at their own .fnt files the same way.
 */
public final class Fonts {

    private Fonts() {}

    public static final BitmapFont ORBITRON_20 = new BitmapFont("graphics/fonts/orbitron20aa.fnt");
    public static final BitmapFont ORBITRON_20_BOLD = new BitmapFont("graphics/fonts/orbitron20aabold.fnt");
    public static final BitmapFont ORBITRON_24_BOLD = new BitmapFont("graphics/fonts/orbitron24aabold.fnt");
    public static final BitmapFont VICTOR_10 = new BitmapFont("graphics/fonts/victor10.fnt");
    public static final BitmapFont INSIGNIA_LARGE = new BitmapFont("graphics/fonts/insignia21LTaa.fnt");
}
