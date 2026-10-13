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
    STANDARD,
    /** A tender's drone, drawn as the vanilla Shepherd's own wing draws it (the Borer). */
    TENDER;

    /** The look a body wears; an unknown value is {@link #STANDARD}. */
    public static DroneLook of(AirBody body) {
        DroneLook[] all = values();
        return body == null || body.artVariant < 0 || body.artVariant >= all.length
                ? STANDARD : all[body.artVariant];
    }
}
