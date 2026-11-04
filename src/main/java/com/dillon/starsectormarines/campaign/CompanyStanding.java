package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What shape the company is in right now, flattened for the company view's standing
 * pane.
 *
 * <p>Derived on demand and never persisted. Financial figures come straight from
 * {@link OfficerMoodReader.Snapshot} rather than being reassembled here, so the number
 * the pane shows is the number the officer's mood is reacting to.
 *
 * <p>The headline is {@link OfficerMoodReader.Snapshot#runwayMonths()} — months of
 * payroll on hand. It governs every other decision the screen offers: whether a
 * retainer at a poor rate is worth taking, whether a squad can stay stationed, whether
 * the company can absorb a lean month.
 */
public final class CompanyStanding {

    /** One employer the player has a track record with. */
    public static final class Employer {
        public final long houseId;
        public final String name;
        public final int reputation;
        public final int contractsCompleted;
        public final int contractsFailed;

        Employer(long houseId, String name, int reputation,
                 int contractsCompleted, int contractsFailed) {
            this.houseId = houseId;
            this.name = name;
            this.reputation = reputation;
            this.contractsCompleted = contractsCompleted;
            this.contractsFailed = contractsFailed;
        }
    }

    public final OfficerMoodReader.Snapshot finances;
    /** Retainer credits per in-game month across every live stationing assignment. */
    public final int retainerPerMonth;
    /** Live stationing assignments producing that retainer. */
    public final int stationingContracts;
    /** Living marines on the books. */
    public final int strength;
    /** Marines that could deploy today: ACTIVE, in a line squad, not stationed. */
    public final int available;
    /**
     * Living but not deployable today: away on a stationing assignment, or
     * sitting in the reserve pool awaiting transfer into a line squad. Deliberately the
     * whole gap rather than just casualties — the player's question is "how many can I
     * actually send", and every one of these is a reason the answer is smaller.
     */
    public final int unavailable;
    public final int stationed;
    /** Line squads — the reserve pool is a holding area, not a formation. */
    public final int lineSquads;
    /** Line squads currently away on a stationing assignment. */
    public final int stationedSquads;
    /** Employers with a record, strongest relationship first. */
    public final List<Employer> employers;

    CompanyStanding(OfficerMoodReader.Snapshot finances, int retainerPerMonth,
                    int stationingContracts, int strength, int available,
                    int unavailable, int stationed,
                    int lineSquads, int stationedSquads,
                    List<Employer> employers) {
        this.finances = finances;
        this.retainerPerMonth = retainerPerMonth;
        this.stationingContracts = stationingContracts;
        this.strength = strength;
        this.available = available;
        this.unavailable = unavailable;
        this.stationed = stationed;
        this.lineSquads = lineSquads;
        this.stationedSquads = stationedSquads;
        this.employers = Collections.unmodifiableList(new ArrayList<>(employers));
    }

    public OfficerMood mood() {
        return finances.mood();
    }

    /**
     * Retainer income across every stationing assignment currently being paid.
     *
     * <p>Only ACTIVE and IN_PROGRESS rows count. An OFFERED row has not been accepted
     * and pays nothing; a terminal row has stopped paying. Counting either would tell
     * the player they have income they do not have — the one direction this number must
     * never err in, because it feeds the runway the screen leads with.
     */
    public static int retainerPerMonth(CampaignState state) {
        if (state == null) return 0;
        long total = 0;
        for (int row = 0; row < state.contractCount; row++) {
            if (!payingStationingRow(state, row)) continue;
            total += Math.max(0, state.contractRetainerPerMonth[row]);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static int stationingContracts(CampaignState state) {
        if (state == null) return 0;
        int count = 0;
        for (int row = 0; row < state.contractCount; row++) {
            if (payingStationingRow(state, row)) count++;
        }
        return count;
    }

    private static boolean payingStationingRow(CampaignState state, int row) {
        if (!ContractType.fromByte(state.contractType[row]).isStationing()) return false;
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        return contractState == ContractState.ACTIVE
                || contractState == ContractState.IN_PROGRESS;
    }

    /**
     * Employers the player has actually worked for, best relationship first, house id
     * as a deterministic tiebreak so the list does not reshuffle between frames.
     * Houses with no name in the registry are skipped rather than rendered as an id.
     */
    public static List<Employer> employers(CampaignState state, int limit) {
        List<Employer> result = new ArrayList<>();
        if (state == null || limit <= 0) return result;
        for (int row = 0; row < state.repCount; row++) {
            long houseId = state.repHouseId[row];
            int houseRow = state.houseIndex(houseId);
            if (houseRow < 0) continue;
            String name = state.houseDisplayName[houseRow];
            if (name == null) continue;
            result.add(new Employer(houseId, name, state.repValue[row],
                    state.repContractsCompleted[row], state.repContractsFailed[row]));
        }
        result.sort(Comparator.comparingInt((Employer e) -> -e.reputation)
                .thenComparingLong(e -> e.houseId));
        return result.size() <= limit ? result : new ArrayList<>(result.subList(0, limit));
    }

    /**
     * Living marines on the books. The gap between this and {@link #available} is the
     * number worth looking at.
     */
    public static int strength(MarineRoster roster) {
        if (roster == null) return 0;
        int count = 0;
        for (MarineSoldier soldier : roster.soldiers()) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) count++;
        }
        return count;
    }

    /** Marines who could go today — the roster's own definition of a line-ready seat. */
    public static int available(MarineRoster roster) {
        return roster == null ? 0 : roster.lineReadySoldiers().size();
    }

    /** Living marines bound to a stationing assignment. */
    public static int stationed(MarineRoster roster) {
        if (roster == null) return 0;
        int count = 0;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve() || !squad.stationed()) continue;
            for (MarineSoldier soldier : roster.squadMembers(squad)) {
                if (soldier.status() == MarineSoldierStatus.ACTIVE) count++;
            }
        }
        return count;
    }

    /**
     * Squads that are actual formations. The reserve pool is a {@link MarineSquad} too,
     * but it is a holding area for marines awaiting transfer rather than something that
     * deploys, so counting it would overstate the company by one every time.
     */
    public static int lineSquads(MarineRoster roster) {
        if (roster == null) return 0;
        int count = 0;
        for (MarineSquad squad : roster.squads()) {
            if (!squad.reserve()) count++;
        }
        return count;
    }

    public static int stationedSquads(MarineRoster roster) {
        if (roster == null) return 0;
        int count = 0;
        for (MarineSquad squad : roster.squads()) {
            if (!squad.reserve() && squad.stationed()) count++;
        }
        return count;
    }

    /**
     * Assembles a standing from already-read state. Split from the live reader so the
     * whole derivation is testable without a Sector.
     */
    public static CompanyStanding of(OfficerMoodReader.Snapshot finances,
                                     CampaignState state, MarineRoster roster,
                                     int employerLimit) {
        int strength = strength(roster);
        int available = available(roster);
        int stationed = stationed(roster);
        return new CompanyStanding(finances,
                retainerPerMonth(state), stationingContracts(state),
                strength, available, Math.max(0, strength - available),
                stationed, lineSquads(roster), stationedSquads(roster),
                employers(state, employerLimit));
    }

    /** Reads the live campaign. Every lookup is guarded; absent state reads as zeroes. */
    public static CompanyStanding current(int employerLimit) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        return of(OfficerMoodReader.read(),
                script != null ? script.state() : null,
                rosterScript != null ? rosterScript.roster() : null,
                employerLimit);
    }
}
