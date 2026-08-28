package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Watches for the company's ship failing to come home from a battle.
 *
 * <p>Losing the ship and letting her go are the same fact to a fleet roster —
 * she is simply not in it any more — and completely different to a company. One
 * is a decision the player made and the other is something that happened to
 * them, and a screen that greeted both with the same sentence would be telling
 * the player their transport was sold when it was shot out from under them.
 *
 * <p><b>Evidence, not a verdict.</b> A ship that is disabled in an engagement
 * may well be recovered off the field afterwards, so this only records that she
 * was among the casualties. Whether the company actually lost their home is
 * settled later by whether she is in the fleet at all — see
 * {@link CompanyShipDesignation#aboard()}.
 */
public final class CompanyShipLossListener extends BaseCampaignEventListener {

    private static final Logger LOG = Global.getLogger(CompanyShipLossListener.class);

    public CompanyShipLossListener() {
        super(false);
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        if (result == null) return;
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script == null ? null : script.roster();
        String home = roster == null ? null : roster.companyShipId();
        if (home == null) return;

        if (!wasCasualty(result.getWinnerResult(), home)
                && !wasCasualty(result.getLoserResult(), home)) {
            return;
        }
        // Whether the player still owned the field is recorded with the
        // casualty because it decides who gets picked up, and nobody can ask
        // the battle about it again once it is over.
        boolean heldTheField = result.getWinnerResult() != null
                && result.getWinnerResult().isPlayer();
        LOG.info("CompanyShipLossListener: the company ship (" + home
                + ") did not come through the engagement; field held: " + heldTheField);
        roster.reportCompanyShipCasualty(heldTheField);
    }

    /** Whether the player's own side lost this ship in the engagement. */
    private static boolean wasCasualty(EngagementResultForFleetAPI side, String shipId) {
        if (side == null || !side.isPlayer()) return false;
        return names(side.getDestroyed(), shipId) || names(side.getDisabled(), shipId);
    }

    private static boolean names(List<FleetMemberAPI> members, String shipId) {
        if (members == null) return false;
        for (FleetMemberAPI member : members) {
            if (member != null && shipId.equals(member.getId())) return true;
        }
        return false;
    }
}
