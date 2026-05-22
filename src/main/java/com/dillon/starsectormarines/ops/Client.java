package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.RepLevel;

/**
 * One row in the client list — a faction the player might contract with from
 * this planet, or a campaign-tier patron-house with outstanding offers at this
 * market. Pre-resolved by {@link MarineOpsContext} so panels don't reach into
 * Starsector's rep/faction APIs at render time.
 *
 * <p>{@link #locked} is the gating decision (hostile factions show but can't be
 * selected); {@link #lockReason} is an i18n key for the "why locked" tooltip.
 *
 * <p>{@link #patronHouseId} distinguishes the two flavors of client:
 * <ul>
 *   <li>{@code -1L} — faction-direct client (Hegemony, Independent, Pirates,
 *       co-resident faction). Missions come from the industry catalog.</li>
 *   <li>otherwise — a campaign-tier patron house. Missions come from
 *       {@code OFFERED} rows in the contracts table.</li>
 * </ul>
 */
public final class Client {

    public final String   factionId;
    public final String   displayName;
    public final String   crestPath;
    public final RepLevel repLevel;
    public final boolean  locked;
    public final String   lockReason;
    public final long     patronHouseId;

    /** Faction-direct constructor — equivalent to the patron-aware ctor with {@code patronHouseId = -1L}. */
    public Client(String factionId,
                  String displayName,
                  String crestPath,
                  RepLevel repLevel,
                  boolean locked,
                  String lockReason) {
        this(factionId, displayName, crestPath, repLevel, locked, lockReason, -1L);
    }

    public Client(String factionId,
                  String displayName,
                  String crestPath,
                  RepLevel repLevel,
                  boolean locked,
                  String lockReason,
                  long patronHouseId) {
        this.factionId     = factionId;
        this.displayName   = displayName;
        this.crestPath     = crestPath;
        this.repLevel      = repLevel;
        this.locked        = locked;
        this.lockReason    = lockReason;
        this.patronHouseId = patronHouseId;
    }

    /** Stable cache key — uniquely names a client across faction-direct and patron flavors. */
    public String identity() {
        return patronHouseId == -1L ? factionId : ("house:" + patronHouseId);
    }
}
