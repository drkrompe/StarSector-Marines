package com.dillon.starsectormarines.battle.air;

/**
 * Cargo a shuttle physically delivers when it lands. Payloads own only the
 * deployment recipe; the shared shuttle state machine still owns flight,
 * touchdown, AA exposure, retries when the LZ is blocked, and departure.
 */
public interface AirDeliveryPayload {

    /** Number of payload units loaded for each sortie. */
    int unitsPerSortie(ShuttleType carrier);

    /**
     * Try to deploy one payload unit. Returns false when the landing area is
     * temporarily blocked so the shuttle can retry on its next unload tick.
     */
    boolean tryDeploy(AirDeliveryContext context);

    /**
     * Whether what this payload unloads fights: soldiers, vehicles or machines. A side with
     * such a payload still aboard or still callable has not lost its battle; a supply crate
     * keeps nobody in it.
     */
    default boolean fieldsForces() { return true; }
}
