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

    // UI minimum is Orbitron 20 — Victor 10 / similar small fonts are deliberately
    // not exposed here. Past playtest feedback: small fonts are unreadable in this
    // dense 3-column layout. Re-add only if a specific secondary-text need arises.
    public static final BitmapFont ORBITRON_20 = new BitmapFont("graphics/fonts/orbitron20aa.fnt");
    public static final BitmapFont ORBITRON_20_BOLD = new BitmapFont("graphics/fonts/orbitron20aabold.fnt");
    public static final BitmapFont ORBITRON_24_BOLD = new BitmapFont("graphics/fonts/orbitron24aabold.fnt");
    public static final BitmapFont INSIGNIA_LARGE = new BitmapFont("graphics/fonts/insignia21LTaa.fnt");

    /**
     * Debug-only smaller font. Reserved for the GOAP debug overlay
     * ({@code SquadPlanDebugPanel}'s filtered detail mode) where information
     * density beats readability — long predicate names + per-step assignments
     * blow the panel bounds at Orbitron 20. Do not use in gameplay UI; the
     * font-minimum rule above still stands for player-facing surfaces.
     */
    public static final BitmapFont INSIGNIA_15_AA = new BitmapFont("graphics/fonts/insignia15LTaa.fnt");
}
