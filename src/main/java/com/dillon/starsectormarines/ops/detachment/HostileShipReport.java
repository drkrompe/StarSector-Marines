package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.warship.ShipSupportSource;

/**
 * What the launch found about the defender's ship: the frozen source it will field, or why it
 * will not. Plain data, no campaign objects, so the briefing reads the same thing the battle is
 * handed.
 *
 * @param outcome   what was found
 * @param source    the frozen ship, for {@link Outcome#COMING} only
 * @param shipName  the real ship's name (coming or held), else null
 * @param hullName  the hull's display name, else null
 * @param fleetName the fleet it sails with, else null (a declared ship has none)
 * @param held      for {@link Outcome#HELD}, why it is not free
 */
public record HostileShipReport(Outcome outcome, ShipSupportSource source, String shipName,
                                String hullName, String fleetName, HostileFleetStatus held) {

    public enum Outcome {
        /** The world is not defended enough to call a ship, or the mission declares none: nothing is said. */
        UNDEFENDED,
        /** No eligible hull in any fleet of the defending faction in the system. */
        ABSENT,
        /** The largest eligible hull is bogged down; no ship will come. */
        HELD,
        /** A ship will answer: {@link #source} is its frozen loadout. */
        COMING
    }

    public static final HostileShipReport UNDEFENDED =
            new HostileShipReport(Outcome.UNDEFENDED, null, null, null, null, null);
    public static final HostileShipReport ABSENT =
            new HostileShipReport(Outcome.ABSENT, null, null, null, null, null);

    /** A ship declared by the mission rather than found in a fleet. */
    public static HostileShipReport declared(ShipSupportSource source) {
        if (source == null) return UNDEFENDED;
        return new HostileShipReport(Outcome.COMING, source, source.shipName(), source.hullId(), null, null);
    }

    /** The frozen source the battle is handed, or null. */
    public ShipSupportSource sourceOrNull() { return outcome == Outcome.COMING ? source : null; }
}
