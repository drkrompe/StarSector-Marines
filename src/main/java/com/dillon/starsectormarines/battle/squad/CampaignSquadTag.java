package com.dillon.starsectormarines.battle.squad;

/**
 * The campaign squad one deploying marine came from, frozen at deploy time.
 *
 * <p>Plain values, deliberately: the battle tier never resolves a campaign
 * object and has no roster access, so the display name is copied across the
 * seam rather than looked up. A rename back home mid-battle therefore does not
 * change what the HUD says, which is the behaviour we want.
 *
 * <p>Null on every generated spawn — defenders, militia, employer personnel,
 * debug fixtures — which is what keeps those paths on the old per-shuttle
 * squad minting.
 */
public final class CampaignSquadTag {

    /** Stable campaign squad id; the key battle squads are grouped by. */
    public final String squadId;

    /** Display name frozen at deploy time, e.g. "Squad 01". */
    public final String label;

    /** True for the one seat holding this squad's NCO, who leads it on the ground. */
    public final boolean leader;

    public CampaignSquadTag(String squadId, String label, boolean leader) {
        this.squadId = squadId;
        this.label = label;
        this.leader = leader;
    }
}
