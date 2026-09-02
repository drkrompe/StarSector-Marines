package com.dillon.starsectormarines.ui.retained;

/**
 * CSS {@code pointer-events}: whether an element and its descendants can be the
 * geometric target of a pointer.
 *
 * <p>{@link #NONE} is the documented exception to design law 6. Painted content
 * is normally hit-tested, but an overlay opened <em>by</em> a hover must not be
 * able to take that hover away from the element it describes, which is exactly
 * the flicker CSS invented this property for.
 */
public enum PointerEvents {
    AUTO,
    NONE
}
