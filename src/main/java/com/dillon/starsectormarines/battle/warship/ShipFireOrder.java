package com.dillon.starsectormarines.battle.warship;

/**
 * Whether a warship's mounts may fire on hostile hulls, the ship-versus-ship sibling of {@link AirFireOrder}.
 *
 * <p>{@code OFF} is the default, so a ship no mission has told to fight ships behaves exactly as it always has.
 * {@code ALL} is an explicit order from a mission or a script. {@code AUTO} follows the ship's posture and is
 * reserved for a duel doctrine the owner has not decided on ({@code warship-behaviours.md}, open questions: ship
 * against ship); until then it engages nothing.
 */
public enum ShipFireOrder {
    /** Never fire at a hull. The default. */
    OFF,
    /** Follow the posture; engages nothing until the duel doctrine decides when it should. */
    AUTO,
    /** Fire at any hostile hull in reach with every weapon that may fire at a ship. */
    ALL
}
