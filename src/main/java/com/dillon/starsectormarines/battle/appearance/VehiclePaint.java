package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * Appearance-only paint for the Hegemony vehicle line ({@code hegemony-armor-line.md}, liveries): the APC and its
 * carriers, the medium tank, the Onager and the turret heads they turn in. The Hegemony's sand plate with orange
 * hazard panels is the master; a paint is the same sheet recoloured pixel for pixel
 * (art-source/vehicles/liveries/paint.py), so it never moves a frame and owns no geometry, stat or allegiance. A side
 * wears the paint of the faction its mechs wear the livery of ({@link #of}); a line outside the Hegemony one keeps its
 * own sheet whatever the side.
 */
public enum VehiclePaint {
    /** The masters: sand plate, olive faces, orange hazard panels. */
    HEGEMONY(null),
    /** Blue hazard panels on a faded grey plate. */
    INDEPENDENT("independent"),
    /** Red hazard panels on a dark, dirty brown plate, sooted, chipped and rusted. */
    PIRATES("pirates");

    private final String suffix;

    VehiclePaint(String suffix) {
        this.suffix = suffix;
    }

    /**
     * The paint a side whose mechs wear {@code livery} drives in: the Independent and pirate liveries have their own,
     * every other faction (the Hegemony's own, and those not yet painted) the Hegemony's.
     */
    public static VehiclePaint of(MechLivery livery) {
        if (livery == MechLivery.INDEPENDENT) return INDEPENDENT;
        if (livery == MechLivery.PIRATES) return PIRATES;
        return HEGEMONY;
    }

    /** Whether {@code type}'s sheet comes in every paint: the Hegemony line. */
    public static boolean paints(VehicleType type) {
        return switch (type) {
            case HEAVY_APC, FIRE_SUPPORT_APC, LRM_CARRIER, MEDIUM_TANK, BREAKTHROUGH_TANK -> true;
            default -> false;
        };
    }

    /** {@code path} in this paint: the master itself for the Hegemony's, else {@code <name>-<paint>.png} beside it. */
    public String path(String path) {
        if (suffix == null) return path;
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path + "-" + suffix : path.substring(0, dot) + "-" + suffix + path.substring(dot);
    }
}
