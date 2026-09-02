package com.dillon.starsectormarines.campaign.systems;

/**
 * What vanilla has done so far with the raid a Garrison defence was armed from. Read by
 * the lapse path for a defence the player never answered; nothing persists it.
 */
public enum RaidStatus {
    /** Still coming. The defence has not been decided either way yet. */
    LIVE,
    /** The raiders reached the ground. Vanilla resolved it; the outcome is gradeable. */
    LANDED,
    /** Gone, without ever landing on the defended market. */
    REPELLED
}
