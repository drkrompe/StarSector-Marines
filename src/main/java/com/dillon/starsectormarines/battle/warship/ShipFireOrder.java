package com.dillon.starsectormarines.battle.warship;

/**
 * Whether a warship's mounts may fire on hostile hulls, the ship-versus-ship sibling of {@link AirFireOrder}.
 *
 * <p>{@code AUTO} is the default: hostile hulls over any battle fight each other, in an ordinary or generated mission
 * as much as a story chapter ({@code warship-behaviours.md}, Decided: ship against ship). A mission that wants the
 * guns quiet until a moment of its own sets a {@link HullFireHold}, which {@code AUTO} obeys. {@code ALL} is an
 * explicit order from a mission or a script that no hold delays, and {@code OFF} keeps a ship off hulls entirely.
 */
public enum ShipFireOrder {
    /** Never fire at a hull. */
    OFF,
    /**
     * Fight any hostile hull, once no mission hold is pending, if the ship carries a weapon that may fire at a ship.
     * The default.
     */
    AUTO,
    /** Fire at any hostile hull in reach with every weapon that may fire at a ship, whatever a mission holds. */
    ALL
}
