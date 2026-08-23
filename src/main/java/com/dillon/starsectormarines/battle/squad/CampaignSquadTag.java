package com.dillon.starsectormarines.battle.squad;

/**
 * The campaign squad one deploying marine came from, frozen at deploy time.
 *
 * <p>Plain values, deliberately: the battle tier never resolves a campaign
 * object and has no roster access, so the display name is copied across the
 * seam rather than looked up. A rename back home mid-battle therefore does not
 * change what the HUD says, which is the behaviour we want.
 *
 * <p>Null on scenario-generated spawns such as defenders, militia, and employer
 * personnel, which keeps those paths on the per-sortie squad-minting fallback.
 * Debug-company personnel intentionally carry tags because that fixture builds
 * and freezes a real, detached campaign roster.
 */
public final class CampaignSquadTag {

    /** Stable campaign squad id; the key battle squads are grouped by. */
    public final String squadId;

    /** Display name frozen at deploy time, e.g. "Squad 01". */
    public final String label;

    /** True for the one seat holding this squad's NCO, who leads it on the ground. */
    public final boolean leader;

    /**
     * Seats this squad holds in the whole manifest — the strength it is
     * assembling toward once it starts landing. Lets the battle tier tell
     * "still arriving" from "already mauled" without knowing anything about
     * the campaign.
     */
    public final int strength;

    /** Stable zero-based fire-team billet derived from the campaign squad roster. */
    public final int fireTeamIndex;

    public CampaignSquadTag(String squadId, String label, boolean leader, int strength) {
        this(squadId, label, leader, strength, Squad.NO_FIRE_TEAM);
    }

    public CampaignSquadTag(String squadId, String label, boolean leader, int strength,
                            int fireTeamIndex) {
        this.squadId = squadId;
        this.label = label;
        this.leader = leader;
        this.strength = strength;
        this.fireTeamIndex = fireTeamIndex;
    }
}
