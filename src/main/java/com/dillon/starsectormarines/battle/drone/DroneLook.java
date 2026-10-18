package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * How a drone is drawn. Presentation only: stats, weapon, flight and behaviour
 * are the same whatever the look, and a drone's look is fixed when it is made.
 *
 * <p>It is carried on the drone's {@link AirBody#artVariant}, so the live pass
 * and the falling wreck both read it from the body they already draw.
 */
public enum DroneLook {

    /** The shipped drone (a hub's), drawn from {@link Drone#SPRITE_PATH}. */
    STANDARD(Drone.SPRITE_PATH, Drone.VISUAL_CELLS),
    /** A tender's drone, drawn as the vanilla Shepherd's own wing draws it (the Borer). */
    TENDER(null, 0f),
    /** Tri-Tachyon's ducted-fan escort: a body slung between two lift fans, a pulse emitter at the nose. */
    TT_ESCORT("graphics/battle/drones/tt-escort.png", .95f),
    /** Tri-Tachyon's sensor drone: one large lift fan and a sensor head on a forward boom. */
    TT_SENSOR("graphics/battle/drones/tt-sensor.png", 1.05f),
    /** Tri-Tachyon's point-defence drone: one lift fan ringed by three small laser turrets. */
    TT_HALO("graphics/battle/drones/tt-halo.png", .85f),
    /** Tri-Tachyon's strike drone: a dart-shaped warhead pushed by a tail fan. */
    TT_STRIKE("graphics/battle/drones/tt-strike.png", 1.1f);

    private final String spritePath;
    private final float heightCells;

    DroneLook(String spritePath, float heightCells) {
        this.spritePath = spritePath;
        this.heightCells = heightCells;
    }

    /** The sprite this look is drawn with, nose up, or null for a look whose art is resolved elsewhere. */
    public String spritePath() { return spritePath; }

    /** The sprite's drawn height (nose to tail) in cells; its width follows the art's aspect. */
    public float heightCells() { return heightCells; }

    /** The look a body wears; an unknown value is {@link #STANDARD}. */
    public static DroneLook of(AirBody body) {
        DroneLook[] all = values();
        return body == null || body.artVariant < 0 || body.artVariant >= all.length
                ? STANDARD : all[body.artVariant];
    }
}
