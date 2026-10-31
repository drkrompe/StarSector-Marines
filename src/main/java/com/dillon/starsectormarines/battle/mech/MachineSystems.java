package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.drone.DroneRole;

import java.util.List;

/**
 * What the internals in a machine's auxiliary compartment make of it at the drop, beyond its frame values and its
 * loader: the one record a mech's frame ({@link MechFrame#systems}) and a vehicle's spawn read alike, so a part does
 * the same thing on every machine (mechs-nouns.md, one machine, one part catalog). Battle reads only these numbers;
 * the parts themselves stay campaign authority.
 *
 * @param structureFactor  what the machine's structure is multiplied by (a spall liner's fifth more)
 * @param ammoFactor       what every finite magazine, rack and belt it carries is multiplied by (a ready rack's half
 *                         again)
 * @param speedFactor      what its top speed is multiplied by (active protection's launchers and their power)
 * @param droneBay         the drones its compartment adds to what it launches, in launch order (a sensor pair's two)
 * @param activeProtection it carries active protection: a point-defence mount riding the machine that takes the
 *                         arcing, top-attack rounds coming down on it ({@link #activeProtectionStructureId})
 */
public record MachineSystems(float structureFactor, float ammoFactor, float speedFactor, List<DroneRole> droneBay,
                             boolean activeProtection) {

    /** {@code false} is the control: active protection mounts nothing. */
    public static final String ACTIVE_PROTECTION_PROPERTY = "battle.vehicle.activeProtection";

    /** Active protection's turret-catalog structure, or null under the control. */
    private static final String ACTIVE_PROTECTION_STRUCTURE =
            Boolean.parseBoolean(System.getProperty(ACTIVE_PROTECTION_PROPERTY, "true")) ? "structure.tank-aps" : null;

    /** A compartment that changes none of these. */
    public static final MachineSystems NONE = new MachineSystems(1f, 1f, 1f, List.of(), false);

    public MachineSystems {
        if (!positive(structureFactor) || !positive(ammoFactor) || !positive(speedFactor)) {
            throw new IllegalArgumentException("Machine system factors must be finite and positive");
        }
        droneBay = droneBay == null ? List.of() : List.copyOf(droneBay);
    }

    /** A compartment that changes only structure and rounds. */
    public MachineSystems(float structureFactor, float ammoFactor) {
        this(structureFactor, ammoFactor, 1f, List.of(), false);
    }

    private static boolean positive(float value) {
        return value > 0f && Float.isFinite(value);
    }

    /** {@code rounds} of a finite magazine, rack or belt as this machine carries them: never fewer than one. */
    public int rounds(int rounds) {
        return rounds <= 0 || ammoFactor == 1f ? rounds : Math.max(1, Math.round(rounds * ammoFactor));
    }

    /**
     * The turret-catalog structure of the point-defence mount this machine carries, or null for one that carries none
     * or under the control ({@code -Dbattle.vehicle.activeProtection=false}). The mount
     * ({@code PointDefenseService#mountMachine}) engages hostile arcing, top-attack rounds coming down on the machine,
     * inside its reach, at its rate, from a finite magazine; flat rockets, gun rounds and rounds falling on anybody else
     * are left alone.
     */
    public String activeProtectionStructureId() {
        return activeProtection ? ACTIVE_PROTECTION_STRUCTURE : null;
    }
}
