package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
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
 * <p><b>Never chosen and no longer there are different states.</b> A company
 * that has not picked a ship has no home, and the operations screens say so by
 * being unavailable until the player picks one — quartering them somewhere on
 * their behalf would make the first real decision about what the company is for
 * into a default they never saw.
 *
 * <p>A company whose ship is gone is displaced rather than re-homed, for the
 * same reason. Losing a home is not being handed one, and moving them to the
 * next-best hull on their behalf would turn the loss into a shrug — the player
 * would find out their transport had burned by noticing the room looked
 * different. They are put back to choosing, out of whatever is left.
 *
 * <p><b>Confirming she is gone is where a loss is settled.</b> The engagement
 * she failed to come home from is only evidence — a disabled ship can be
 * recovered off the field — so what she took down with her is not counted until
 * she is actually missing from the fleet. See {@link ShipLossSettlement}.
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
        String chosen = roster == null ? null : roster.companyShipId();
        if (chosen == null) return null;

        // Laid-up hulls count here. Being shut down makes a ship a poor thing
        // to offer the player, not a reason to declare the company's own home
        // missing while it is sitting in the fleet mothballed.
        for (FleetMemberAPI member : allShips()) {
            if (chosen.equals(member.getId())) return member;
        }
        LOG.info("CompanyShipDesignation: the company's ship ("
                + roster.companyShipName() + ") is gone; they have nowhere to live");
        roster.reportCompanyShipGone(settle(roster));
        return null;
    }

    /**
     * Count what went down with her, once and only once.
     *
     * <p>A ship the player sold takes nothing with her; only a casualty does.
     * The roll is seeded from the company and her name so reloading the save
     * cannot buy a kinder one — a loss the player can re-roll is not a loss.
     *
     * @return how many named marines were lost
     */
    private static int settle(MarineRoster roster) {
        if (!roster.companyShipCasualty()) return 0;
        ShipLossSettlement.Toll toll = ShipLossSettlement.settle(roster, holds(),
                roster.companyShipHold(), roster.companyShipCasualtyHeldField(),
                roster.deckSeed() * 31L + String.valueOf(roster.companyShipName()).hashCode());
        announce(roster.companyShipName(), toll);
        return toll.marinesLost();
    }

    /**
     * Say it plainly and once, where the player is already looking. A
     * catastrophe the player discovers by noticing a screen looks different is
     * the failure this whole path exists to avoid.
     */
    private static void announce(String lost, ShipLossSettlement.Toll toll) {
        if (Global.getSector() == null || !toll.anything()) return;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.getMessageDisplay() == null) return;
        StringBuilder message = new StringBuilder(String.valueOf(lost))
                .append(" is lost. ");
        if (toll.marinesLost() > 0) {
            message.append(toll.marinesLost()).append(" marines went down with her; ")
                    .append(toll.marinesSurvived()).append(" were picked up. ");
        }
        message.append("The armory, the bay's spares and everything in her holds "
                + "are gone. The company needs somewhere to live.");
        ui.getMessageDisplay().addMessage(message.toString());
    }

    private static CargoAPI holds() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet == null ? null : fleet.getCargo();
    }

    /**
     * Where the company stands on having a home, for the screen that offers
     * them one.
     *
     * @param shipId the ship they live aboard, or null for a company with none
     * @param formerShipName the ship they lost, when they had one
     * @param lostInAction whether she was lost rather than let go
     * @param marinesLost how many of them went down with her
     */
    public record Home(String shipId, String formerShipName, boolean lostInAction,
                       int marinesLost) {

        public static final Home NONE = new Home(null, null, false, 0);

        /** Whether the company has never had a ship, as against having lost one. */
        public boolean founding() {
            return shipId == null && formerShipName == null;
        }

        /** Whether the company had a home and no longer does. */
        public boolean displaced() {
            return shipId == null && formerShipName != null;
        }
    }

    /** The company's standing with respect to quarters. Never null. */
    public static Home home() {
        FleetMemberAPI aboard = aboard();
        MarineRoster roster = roster();
        if (roster == null) return Home.NONE;
        return new Home(aboard == null ? null : aboard.getId(),
                roster.formerShipName(), roster.formerShipLostInAction(),
                roster.formerShipMarinesLost());
    }

    /**
     * Whether the company has yet been given a ship to live aboard.
     *
     * <p>False only before the founding choice. A company whose ship was sold
     * or lost still has one, because losing a home is not the same as never
     * having picked one.
     */
    public static boolean quartered() {
        return aboard() != null;
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
        roster.setCompanyShip(member.getId(), shipName(member),
                Math.round(member.getCargoCapacity()));
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

    /** What to call her, falling back to her hull when she is unnamed. */
    static String shipName(FleetMemberAPI member) {
        String named = member.getShipName();
        return named == null || named.isBlank() ? member.getHullId() : named;
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
