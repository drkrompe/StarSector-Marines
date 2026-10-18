package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirHandling;

/**
 * What a tethered drone is for, which decides where its hull's wing sends it and how it is drawn. Every role is an
 * ordinary {@code battle.drone} unit flown by the shared swarm action; a role differs only in its station, its
 * handling, whether it may fire, and what it carries (see {@link DroneWing}).
 *
 * <p>The roles are the Sector's own drone systems brought to ground scale, remote-controlled from a crewed hull as
 * the AI law requires: the sensor drone, the defensive laser drone, the strike drone used as a guided munition, and
 * the Terminator-style escort.
 */
public enum DroneRole {

    /** Flies the escort formation round the hull and engages what comes into its reach. */
    ESCORT(DroneLook.TT_ESCORT, true, Drone.HANDLING),
    /** Holds station ahead of the hull, where its sight reports to fog and command, and stings what it meets. */
    SENSOR(DroneLook.TT_SENSOR, true, DroneWing.FAST_HANDLING),
    /** Orbits tight round the hull with a short point-defence laser that engages incoming rounds. */
    HALO(DroneLook.TT_HALO, true, DroneWing.FAST_HANDLING),
    /** Waits in the bay for a target, then flies into it and detonates; the bay rebuilds it. */
    STRIKE(DroneLook.TT_STRIKE, false, DroneWing.STRIKE_HANDLING);

    private final DroneLook look;
    private final boolean fires;
    private final AirHandling handling;

    DroneRole(DroneLook look, boolean fires, AirHandling handling) {
        this.look = look;
        this.fires = fires;
        this.handling = handling;
    }

    public DroneLook look() { return look; }

    /** Whether a drone in this role shoots its gun; a strike drone is the munition and never does. */
    public boolean fires() { return fires; }

    public AirHandling handling() { return handling; }
}
