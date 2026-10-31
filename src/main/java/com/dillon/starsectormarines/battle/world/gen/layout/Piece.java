package com.dillon.starsectormarines.battle.world.gen.layout;

/**
 * One named thing a {@link MissionLayout} lays. A required piece that cannot be
 * laid makes the map unfit for its mission; an optional one is reported and
 * the map stands.
 */
public sealed interface Piece permits Line, Site, Deferred, Road, Structure, Block, Ridge, Props, Worked, Airfield, Paving {

    /** Unique within its layout; the name the laid result is reported under. */
    String name();

    boolean required();
}
