package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which ship in the player's fleet the company lives aboard.
 *
 * <p>The company's home is one hull the player owns, not a vessel the mod
 * conjured. That is what puts the ship and the rooms on one axis: a bigger or
 * more specialised hull is more interior, and it is bought the way any ship is
 * bought. See {@code company-ship.md}.
 *
 * <p><b>The designation is a campaign fact and the deck is derived from it.</b>
 * Only the choice is recorded — an id on the roster — because everything about
 * the interior follows from the ship and re-deriving it is cheaper and more
 * honest than persisting a second copy that can fall out of step with the
 * refits and damage the base game applies.
 *
 * <p><b>A designation that no longer names a ship is replaced, not followed.</b>
 * Ships are sold, mothballed, and lost, and a company pointing at a hull that is
 * no longer in the fleet has to end up somewhere rather than nowhere. The
 * fallback is the fleet's best remaining candidate, which is also what a company
 * that has never chosen gets — so the operations screens work before the choice
 * exists and keep working after the chosen ship is gone.
 */
public final class CompanyShipDesignation {

    private static final Logger LOG = Global.getLogger(CompanyShipDesignation.class);

    /** The layout a company gets with no campaign to read their own seed from. */
    private static final long FOUNDING_DECK_SEED = 0x5AFE_DECEL;

    private CompanyShipDesignation() { }

    /**
     * The ship the company is aboard, recording the answer when it had to be
     * chosen. Null outside campaign, or for a fleet with nothing to live on.
     */
    public static FleetMemberAPI aboard() {
        MarineRoster roster = roster();
        List<FleetMemberAPI> fleet = candidates();
        // A company whose every hull is laid up still sleeps somewhere. Being
        // shut down makes a ship a poor thing to offer the player, not a
        // reason for the company to have no home at all.
        if (fleet.isEmpty()) fleet = allShips();
        if (fleet.isEmpty()) return null;

        String chosen = roster == null ? null : roster.companyShipId();
        if (chosen != null) {
            for (FleetMemberAPI member : fleet) {
                if (chosen.equals(member.getId())) return member;
            }
            LOG.info("CompanyShipDesignation: the company's ship (" + chosen
                    + ") is no longer in the fleet; moving them to the best remaining hull");
        }
        FleetMemberAPI best = fleet.get(0);
        if (roster != null) roster.setCompanyShipId(best.getId());
        return best;
    }

    /**
     * Whether the player owns anything the company could be moved to.
     *
     * <p>Distinct from having a ship: a company always ends up somewhere while
     * the player has hulls at all, but a fleet of nothing but laid-up ships has
     * no candidate worth offering.
     */
    public static boolean canChoose() {
        return !candidates().isEmpty();
    }

    /**
     * Move the company to a ship they own. Returns whether the ship was one
     * they could move to.
     */
    public static boolean designate(FleetMemberAPI member) {
        MarineRoster roster = roster();
        if (roster == null || member == null) return false;
        if (!candidates().contains(member)) return false;
        roster.setCompanyShipId(member.getId());
        LOG.info("CompanyShipDesignation: the company now lives aboard "
                + member.getShipName() + " (" + member.getHullId() + ")");
        return true;
    }

    /**
     * Every ship the company could live aboard, best first.
     *
     * <p>Ranked by <b>lift</b> — the hands a hull can carry beyond the ones
     * needed to fly her — because that is the space the company actually gets,
     * and it is the one measure that separates a transport from a warship of the
     * same tonnage. Hull class breaks the tie, since a larger hull has more deck
     * to lay the same program on.
     *
     * <p>A mothballed ship is not a candidate: she is shut down with nobody
     * aboard, which is the one state that makes a hull genuinely uninhabitable
     * rather than merely a poor choice. Everything else the player owns stays on
     * the list, including hulls that would be bad homes — what a candidate
     * <em>loses</em> is the interesting half of the comparison, and it cannot be
     * shown for a candidate that was filtered out before the player saw it.
     */
    public static List<FleetMemberAPI> candidates() {
        return ranked(false);
    }

    /** Every hull the player owns, laid-up ones included. @see #aboard() */
    private static List<FleetMemberAPI> allShips() {
        return ranked(true);
    }

    private static List<FleetMemberAPI> ranked(boolean includeLaidUp) {
        if (Global.getSector() == null) return List.of();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return List.of();
        List<FleetMemberAPI> found = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null || member.getHullSpec() == null) continue;
            if (!includeLaidUp && member.isMothballed()) continue;
            found.add(member);
        }
        found.sort(Comparator
                .comparingDouble(CompanyShipDesignation::lift)
                .thenComparingInt(member -> member.getHullSpec().getHullSize().ordinal())
                .reversed());
        return found;
    }

    /**
     * The layout seed for one hull: the company's own seed mixed with the ship
     * it is generating for.
     *
     * <p>Per ship rather than per company, so transferring produces a genuinely
     * different interior, and stable per ship, so a hull the company moves back
     * to is the one they remember rather than a fresh draw. Shared with the
     * transfer preview, because a preview generated from a different seed is of
     * a different ship.
     */
    public static long deckSeedFor(String hull) {
        MarineRoster roster = roster();
        long company = roster == null ? FOUNDING_DECK_SEED : roster.deckSeed();
        return hull == null ? company : company * 31L + hull.hashCode();
    }

    /** Everyone a hull can carry who is not needed to work her. */
    private static float lift(FleetMemberAPI member) {
        return Math.max(0f, member.getMaxCrew() - member.getMinCrew());
    }

    /**
     * The company's own state, or null outside a campaign — which a headless
     * caller asking what a hull's deck would look like legitimately is.
     */
    private static MarineRoster roster() {
        if (Global.getSector() == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script == null ? null : script.roster();
    }
}
