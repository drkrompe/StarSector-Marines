package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;

/**
 * What the company has to move, and what it has to pay the yard with.
 *
 * <p>One seam rather than three because they are asked together and answered
 * together: a transfer is priced from the company's own strength and paid for
 * out of the same purse the rest of the campaign spends from. Keeping it an
 * interface is what lets the transfer screen be shown a company of a stated
 * size with a stated balance, so the wording and the refusals can be seen
 * without a campaign underneath them.
 *
 * @see TransferCost
 */
public interface CompanyMeans {

    /** A company of nobody with nothing, which can afford nothing. */
    CompanyMeans NONE = of(0, 0, 0);

    /** How many marines would have to be moved. */
    int marines();

    /** How many machines would have to be moved. */
    int walkers();

    /** What is in the purse right now. */
    int credits();

    /** Take it out of the purse. Called only for a charge already afforded. */
    void charge(int credits);

    /** The player's own company and the player's own money. */
    static CompanyMeans ofPlayer() {
        return new CompanyMeans() {
            @Override
            public int marines() {
                return MarineOpsContext.companyMarines(roster()).size();
            }

            @Override
            public int walkers() {
                MarineRoster roster = roster();
                if (roster == null) return 0;
                int machines = 0;
                for (CampaignMechSquad squad : roster.mechBay().squads()) {
                    machines += squad.mechs().size();
                }
                return machines;
            }

            @Override
            public int credits() {
                CargoAPI hold = hold();
                return hold == null ? 0 : (int) hold.getCredits().get();
            }

            @Override
            public void charge(int credits) {
                CargoAPI hold = hold();
                if (hold != null && credits > 0) hold.getCredits().subtract(credits);
            }
        };
    }

    /** A stated company with a stated balance, for evidence and for tests. */
    static CompanyMeans of(int marines, int walkers, int credits) {
        return new CompanyMeans() {
            private int purse = credits;

            @Override
            public int marines() {
                return marines;
            }

            @Override
            public int walkers() {
                return walkers;
            }

            @Override
            public int credits() {
                return purse;
            }

            @Override
            public void charge(int amount) {
                purse -= amount;
            }
        };
    }

    private static MarineRoster roster() {
        if (Global.getSector() == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script == null ? null : script.roster();
    }

    private static CargoAPI hold() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet == null ? null : fleet.getCargo();
    }
}
