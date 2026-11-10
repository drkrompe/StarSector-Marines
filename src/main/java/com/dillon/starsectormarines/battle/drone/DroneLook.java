package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;

/**
 * How a drone is drawn. Presentation only: stats, weapon, flight and behaviour
 * are the same whatever the look, and a drone's look is fixed when it is made.
 *
 * <p>It is carried on the drone's {@link AirBody#artVariant}, so the live pass
 * and the falling wreck both read it from the body they already draw.
 */
public enum DroneLook {

    /** The vanilla drone, drawn from {@link Drone#SPRITE_PATH}: what a drone made without a look wears, and the fallback when a look's art did not load. */
    STANDARD(Drone.SPRITE_PATH, Drone.VISUAL_CELLS),
    /** A tender's drone, drawn as the vanilla Shepherd's own wing draws it (the Borer). */
    TENDER(null, 0f),
    /** Tri-Tachyon's escort: a long pod built along its pulse emitter, held up by two hip nozzles. */
    TT_ESCORT("graphics/battle/drones/tt-escort.png", 1.05f),
    /** Tri-Tachyon's sensor drone: a teardrop pod whose front is one large aperture, swept antennae, twin tail nozzles. */
    TT_SENSOR("graphics/battle/drones/tt-sensor.png", 1f),
    /** Tri-Tachyon's point-defence drone: a broad octagonal pod with a twin-emitter turret and a vent at each corner. */
    TT_HALO("graphics/battle/drones/tt-halo.png", .85f),
    /** Tri-Tachyon's strike drone: a loitering missile, brass nose, swept tail fins and one rear nozzle. */
    TT_STRIKE("graphics/battle/drones/tt-strike.png", 1.05f),
    /**
     * A flock's swarm drone ({@link Flock}), a stamped dart on one rear nozzle, in Domain security's livery
     * ({@link FlockLivery#DOMAIN}): small, so a flock of sixteen reads as a body of little machines (`air-nouns.md`, Flocks).
     */
    SWARM("graphics/battle/drones/swarm-domain.png", .55f),
    /** The swarm drone in contingency security's livery ({@link FlockLivery#CONTINGENCY}). Appended: looks are ordinals. */
    SWARM_CONTINGENCY("graphics/battle/drones/swarm-contingency.png", .55f),
    /** A low-tech hub's drone: a riveted box with a stubby barrel, held up by two jet lift pods on outriggers. */
    HUB_LOW_TECH("graphics/battle/drones/hub-drone-low-tech.png", .9f),
    /** A midline hub's drone: an angular pod with a small turret forward and a belly thrust grille at each corner. */
    HUB_MIDLINE("graphics/battle/drones/hub-drone-midline.png", .9f),
    /** A high-tech hub's drone: a narrow lifting body with its emitter on the centreline. */
    HUB_HIGH_TECH("graphics/battle/drones/hub-drone-high-tech.png", .95f);

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

    /**
     * The look of a drone launched by a hub built in {@code style}: the drone is built in the hub's tech family
     * (`tech-families.md`), as its posts are. Derelict works and the pirates' patched ones are low-tech.
     */
    public static DroneLook forHub(EmplacementStyle style) {
        if (style == null) return HUB_LOW_TECH;
        return switch (style) {
            case MODULAR, BARRIER, EXPORT, BLOCKWORK -> HUB_MIDLINE;
            case COMPOSITE, DEPLOYED -> HUB_HIGH_TECH;
            case BRUTALIST, EARTHWORK, DERELICT, PATCHWORK -> HUB_LOW_TECH;
        };
    }

    /** The look a body wears; an unknown value is {@link #STANDARD}. */
    public static DroneLook of(AirBody body) {
        DroneLook[] all = values();
        return body == null || body.artVariant < 0 || body.artVariant >= all.length
                ? STANDARD : all[body.artVariant];
    }
}
