package com.dillon.starsectormarines.ops.battleview;

/**
 * One dome's frozen presentation for a frame: where it is on screen, how it
 * looks, and the impacts currently crossing it. A plain bag of numbers so the
 * collector can build it without GL and both the shader and its CPU model read
 * the same thing.
 *
 * <p>Impacts are packed four floats each: position in dome-local units (the dome
 * radius is {@code 1}, screen orientation), age in seconds, and intensity in
 * {@code [0, 1]}.
 */
final class ShieldDomeDraw {

    /** Bound on concurrent impacts the shader takes; equals the service's per-dome cap. */
    static final int MAX_IMPACTS = 24;

    float sx;
    float sy;
    float radiusPx;
    float radiusCells;
    final float[] tint = new float[3];
    float level;
    boolean overloaded;
    /** Share of the down time still to run, {@code [0, 1]}. */
    float recovery;
    float hitFlash;
    float dropFlash;
    float alpha = 1f;
    /** Colour the field strains toward as it fills and shatters in when it overloads; a dome's is amber. */
    final float[] collapse = ShieldDomeShading.AMBER.clone();
    /**
     * The arc the shield covers, for a directional shield: the unit direction it faces in the local frame, and the
     * cosine of half its width. Below {@code -1} (the default) the whole circle is drawn, as a dome is.
     */
    float arcDirX = 1f;
    float arcDirY;
    float arcCos = -2f;
    final float[] impacts = new float[MAX_IMPACTS * 4];
    int impactCount;
}
