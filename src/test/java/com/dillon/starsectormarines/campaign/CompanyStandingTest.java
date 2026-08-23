package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyStandingTest {

    // ---------- retainer income ----------

    @Test
    void retainerCountsOnlyAssignmentsThatArePaying() {
        CampaignState state = new CampaignState();
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, 1_000);
        addStationing(state, ContractType.CADRE, ContractState.IN_PROGRESS, 400);
        // Not yet accepted, already ended, and walked away from: none of these pay.
        addStationing(state, ContractType.GARRISON, ContractState.OFFERED, 9_000);
        addStationing(state, ContractType.GARRISON, ContractState.COMPLETED, 9_000);
        addStationing(state, ContractType.CADRE, ContractState.DEFAULTED, 9_000);

        assertEquals(1_400, CompanyStanding.retainerPerMonth(state));
        assertEquals(2, CompanyStanding.stationingContracts(state));
    }

    @Test
    void missionModeContractsNeverContributeRetainer() {
        CampaignState state = new CampaignState();
        state.addContract(1L, 2L, -1L, ContractType.STRIKE,
                ContractState.IN_PROGRESS, 10, 500, -1, (byte) 1,
                -1, 12, -1, 25_000, 7_777, (byte) 60, (byte) 60, (byte) 100);

        assertEquals(0, CompanyStanding.retainerPerMonth(state));
        assertEquals(0, CompanyStanding.stationingContracts(state));
    }

    @Test
    void anEmptyOrAbsentStatePaysNothing() {
        assertEquals(0, CompanyStanding.retainerPerMonth(new CampaignState()));
        assertEquals(0, CompanyStanding.retainerPerMonth(null));
        assertEquals(0, CompanyStanding.stationingContracts(null));
    }

    // ---------- employers ----------

    @Test
    void employersAreRankedByStandingWithADeterministicTiebreak() {
        CampaignState state = new CampaignState();
        long kazeron = seedHouse(state, "Kazeron");
        long sindria = seedHouse(state, "Sindria");
        long umbra = seedHouse(state, "Umbra");
        rep(state, kazeron, 25);
        rep(state, sindria, 60);
        rep(state, umbra, 25);

        List<CompanyStanding.Employer> employers = CompanyStanding.employers(state, 5);

        assertEquals(3, employers.size());
        assertEquals("Sindria", employers.get(0).name);
        // Equal standing: lower house id first, so the list cannot reshuffle per frame.
        assertEquals(kazeron, employers.get(1).houseId);
        assertEquals(umbra, employers.get(2).houseId);
        assertTrue(kazeron < umbra);
    }

    @Test
    void theEmployerListIsCappedButTheRestAreNotInvented() {
        CampaignState state = new CampaignState();
        for (int i = 1; i <= 8; i++) {
            rep(state, seedHouse(state, "House " + i), i);
        }

        assertEquals(3, CompanyStanding.employers(state, 3).size());
        assertEquals(8, CompanyStanding.employers(state, 99).size());
        assertTrue(CompanyStanding.employers(state, 0).isEmpty());
    }

    @Test
    void aReputationRowWithNoNamedHouseIsSkippedRatherThanShownAsAnId() {
        CampaignState state = new CampaignState();
        rep(state, seedHouse(state, "Kazeron"), 30);
        rep(state, 999L, 80);

        List<CompanyStanding.Employer> employers = CompanyStanding.employers(state, 5);

        assertEquals(1, employers.size());
        assertEquals("Kazeron", employers.get(0).name);
    }

    // ---------- personnel ----------

    @Test
    void strengthCountsTheLivingAndAvailableCountsOnlyThoseWhoCouldGo() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        Map<String, MarineSoldierStatus> outcomes = new HashMap<>();
        outcomes.put(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA);
        outcomes.put(roster.soldiers().get(1).id(), MarineSoldierStatus.KIA);
        outcomes.put(roster.soldiers().get(2).id(), MarineSoldierStatus.MIA);
        roster.applySoldierOutcome(outcomes, 0, 10f, 7f);

        int living = CompanyStanding.strength(roster);
        assertEquals(1, CompanyStanding.wounded(roster));
        // KIA/MIA leave the books entirely; the wounded marine stays on them.
        assertTrue(living < 12);
        // ...but cannot deploy, so available is strictly below strength.
        assertEquals(living - 1, CompanyStanding.available(roster));
    }

    @Test
    void aStationedSquadLeavesTheAvailablePoolWithoutLeavingTheBooks() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Lead", null, Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(12);
        MarineSquad squad = roster.squads().get(0);
        int squadSize = roster.squadMembers(squad).size();
        int strengthBefore = CompanyStanding.strength(roster);
        int availableBefore = CompanyStanding.available(roster);

        assertTrue(roster.bindStationing(1L, captain.id(), List.of(squad.id())));

        assertEquals(strengthBefore, CompanyStanding.strength(roster));
        assertEquals(squadSize, CompanyStanding.stationed(roster));
        assertEquals(availableBefore - squadSize, CompanyStanding.available(roster));
    }

    @Test
    void anAbsentRosterReadsAsZeroesRatherThanThrowing() {
        assertEquals(0, CompanyStanding.strength(null));
        assertEquals(0, CompanyStanding.available(null));
        assertEquals(0, CompanyStanding.stationed(null));
        assertEquals(0, CompanyStanding.wounded(null));
    }

    // ---------- formation and recovery ----------

    @Test
    void theReservePoolIsNotAFormation() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        int lineSquads = CompanyStanding.lineSquads(roster);
        assertEquals(roster.squads().size(), lineSquads);

        // The reserve pool is a MarineSquad too — a holding area for marines awaiting
        // transfer rather than a formation that deploys. It is created on demand, so
        // the count must not move when it appears.
        roster.reserveSquad();

        assertEquals(lineSquads + 1, roster.squads().size());
        assertEquals(lineSquads, CompanyStanding.lineSquads(roster));
    }

    @Test
    void stationedSquadsAreCountedSeparatelyFromTheLineTotal() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Lead", null, Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(12);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(0, CompanyStanding.stationedSquads(roster));
        assertTrue(roster.bindStationing(1L, captain.id(), List.of(squad.id())));

        assertEquals(1, CompanyStanding.stationedSquads(roster));
        // Away, not gone: it is still one of the company's formations.
        assertTrue(CompanyStanding.lineSquads(roster) >= 1);
    }

    @Test
    void theNextRecoveryIsTheSoonestOneNotTheLast() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        roster.applySoldierOutcome(
                Map.of(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA),
                0, 100f, 20f);
        roster.applySoldierOutcome(
                Map.of(roster.soldiers().get(1).id(), MarineSoldierStatus.WIA),
                0, 100f, 5f);

        assertEquals(2, CompanyStanding.wounded(roster));
        assertEquals(105f, CompanyStanding.nextRecoveryDay(roster), 0.001f);
    }

    @Test
    void aCompanyWithNobodyRecoveringHasNoNextRecoveryDay() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);

        // -1 is the caller's signal to omit the line, not a day to render.
        assertEquals(-1f, CompanyStanding.nextRecoveryDay(roster), 0.001f);
        assertEquals(-1f, CompanyStanding.nextRecoveryDay(null), 0.001f);
        assertEquals(0, CompanyStanding.lineSquads(null));
        assertEquals(0, CompanyStanding.stationedSquads(null));
    }

    // ---------- assembly ----------

    @Test
    void theUnavailableGapIsEverythingBetweenTheBooksAndTheReadyPool() {
        CampaignState state = new CampaignState();
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, 1_000);
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        roster.applySoldierOutcome(
                Map.of(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA),
                0, 10f, 7f);

        CompanyStanding standing = CompanyStanding.of(
                finances(120_000f, 20_000f), state, roster, 5);

        assertEquals(standing.strength - standing.available, standing.unavailable);
        assertEquals(1, standing.wounded);
        assertEquals(1_000, standing.retainerPerMonth);
        assertEquals(1, standing.stationingContracts);
    }

    @Test
    void runwayIsCreditsOverLastMonthsUpkeep() {
        CompanyStanding standing = CompanyStanding.of(
                finances(120_000f, 20_000f), new CampaignState(), null, 5);

        assertEquals(6f, standing.finances.runwayMonths(), 0.001f);
    }

    @Test
    void runwayIsUnknownRatherThanInfiniteWhenThereIsNoUpkeepYet() {
        CompanyStanding standing = CompanyStanding.of(
                finances(120_000f, 0f), new CampaignState(), null, 5);

        // A first-month campaign has no monthly report. -1 is the caller's signal to
        // say so, not a number to render.
        assertEquals(-1f, standing.finances.runwayMonths(), 0.001f);
    }

    @Test
    void theRunwayBandsAgreeWithTheMoodTheyGate() {
        // A company below the DESPERATE floor must both read as low runway and bucket
        // DESPERATE — the pane's colour and the officer's tone come from the same
        // numbers, so they can never disagree on screen.
        CompanyStanding broke = CompanyStanding.of(
                finances(10_000f, 20_000f), new CampaignState(), null, 5);

        assertTrue(broke.finances.runwayMonths()
                < OfficerMoodReader.DESPERATE_RUNWAY_MONTHS);
        assertEquals(OfficerMood.DESPERATE, broke.mood());
    }


    // ---------- the pre-report window ----------

    @Test
    void beforeTheFirstMonthlyReportTheFiguresAreUnknownRatherThanZero() {
        // Vanilla only writes a monthly report at the first rollover. Until then every
        // figure derived from it is absent, and a surface that renders "upkeep Cr. 0"
        // states something false about the company.
        OfficerMoodReader.Snapshot fresh =
                new OfficerMoodReader.Snapshot(200_000f, 0f, 0f, 0, 0, 1, 1, 0);

        assertFalse(fresh.hasMonthlyReport);
        assertEquals(-1f, fresh.runwayMonths(), 0.001f);
    }

    @Test
    void aReportWithRealUpkeepIsRecognisedAsPresent() {
        OfficerMoodReader.Snapshot settled =
                new OfficerMoodReader.Snapshot(200_000f, 1_000f, 20_000f, 0, 0, 1, 1, 0);

        assertTrue(settled.hasMonthlyReport);
        assertEquals(10f, settled.runwayMonths(), 0.001f);
    }

    // ---------- fixtures ----------

    /** Same package as {@link OfficerMoodReader}, so the snapshot needs no live Sector. */
    private static OfficerMoodReader.Snapshot finances(float credits, float upkeep) {
        return new OfficerMoodReader.Snapshot(credits, 0f, upkeep, 0, 0, 1, 1, 0);
    }

    private static void addStationing(CampaignState state, ContractType type,
                                      ContractState contractState, int retainer) {
        state.addContract(1L, -1L, -1L, type, contractState, 10, 500, -1, (byte) 0,
                -1, 12, -1, 0, retainer, (byte) 25, (byte) 25, (byte) 100);
    }

    private static long seedHouse(CampaignState state, String name) {
        return state.addHouse(0, 0, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, name);
    }

    private static void rep(CampaignState state, long houseId, int value) {
        state.repValue[state.ensureRepRow(houseId)] = value;
    }
}
