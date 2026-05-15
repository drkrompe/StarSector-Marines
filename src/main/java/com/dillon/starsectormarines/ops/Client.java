package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.RepLevel;

/**
 * One row in the client list — a faction the player might contract with from
 * this planet. Pre-resolved by {@link MarineOpsContext} so panels don't reach
 * into Starsector's rep/faction APIs at render time.
 *
 * <p>{@link #locked} is the gating decision (hostile factions show but can't be
 * selected); {@link #lockReason} is an i18n key for the "why locked" tooltip.
 */
public final class Client {

    public final String   factionId;
    public final String   displayName;
    public final String   crestPath;
    public final RepLevel repLevel;
    public final boolean  locked;
    public final String   lockReason;

    public Client(String factionId,
                  String displayName,
                  String crestPath,
                  RepLevel repLevel,
                  boolean locked,
                  String lockReason) {
        this.factionId   = factionId;
        this.displayName = displayName;
        this.crestPath   = crestPath;
        this.repLevel    = repLevel;
        this.locked      = locked;
        this.lockReason  = lockReason;
    }
}
