package com.dillon.starsectormarines.ops.battleview;

import java.awt.Color;

/**
 * What a {@link ReviewAnnotation} is for, and the colour that says so.
 *
 * <p>The palette deliberately avoids every hue the frame already spends:
 * cyan, red and yellow are marine, defender and civilian bodies, and orange
 * is ordnance in flight and burning wreckage. An annotation that borrowed one
 * of those would read as a very large unit. What is left — magenta, violet,
 * green and white — is also what stands off generated ground art, which is
 * mostly grey pavement, brown earth and dark green vegetation.
 *
 * <p>Emphasis is a stroke weight rather than a second colour: the keep and the
 * approach are drawn heavier than the compounds around them, so the two things
 * a reader looks for first are the two things that are thickest.
 */
public enum ReviewStyle {

    /** The defender's command post — the place a Conquest is decided on. */
    KEEP(new Color(255, 92, 236), 2.4f),
    /** A compound that is not the keep: a barracks, an armory. */
    OBJECTIVE(new Color(178, 140, 255), 1.6f),
    /** Ground the marines came ashore on. */
    LANDING(new Color(86, 255, 170), 1.8f),
    /** The line from the beachhead to what it was sent at. */
    APPROACH(new Color(240, 240, 255), 2.4f),
    /** Anything else worth writing on the map. */
    NOTE(new Color(205, 205, 215), 1.4f);

    /** Outline and text colour. Text always sits on a dark plate, so this is legible over any ground. */
    public final Color color;
    /** Stroke width in pixels at the frame's own scale. */
    public final float strokeWidth;

    ReviewStyle(Color color, float strokeWidth) {
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    /** The same colour at {@code alpha}, for a box's interior wash. */
    public Color at(int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
