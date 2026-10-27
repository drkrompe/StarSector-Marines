package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanyPreparationTest {
    @Test void theCompleteBillFundsTheRealBoatAndSquadOrdersExactly() throws Exception {
        MarineRoster roster = new MarineRoster();
        MarineCaptain sabine = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0);
        roster.add(sabine);
        roster.beginCompanyPreparation(sabine);
        OfficerInduction induction = roster.officerInduction();
        assertEquals(Step.SHIP, induction.step(roster));
        // The purse pays the boat and the squad's stores together; the hold supplies the marines.
        TestStores hold = TestStores.forSquads(1);
        int price = CompanyPreparation.boatPrice(roster, ShuttleType.AEROSHUTTLE) + CompanyPreparation.squadPrice(roster);
        assertTrue(CompanyPreparation.boatPrice(roster, ShuttleType.AEROSHUTTLE) > 0);
        TestPurse purse = new TestPurse(price - 1);
        assertFalse(CompanyPreparation.canPrepare(roster, ShuttleType.AEROSHUTTLE, 0, purse, hold));
        assertFalse(CompanyPreparation.canPrepare(roster, ShuttleType.AEROSHUTTLE, 2, purse, hold));
        purse.credits = price;
        hold.stock.merge(Commodities.MARINES, -1, Integer::sum);
        assertFalse(CompanyPreparation.canPrepare(roster, ShuttleType.AEROSHUTTLE, 2, purse, hold));
        hold.stock.merge(Commodities.MARINES, 1, Integer::sum);
        assertTrue(CompanyPreparation.canPrepare(roster, ShuttleType.AEROSHUTTLE, 2, purse, hold));
        roster.setCompanyShip("home", "Home", 200);
        roster.boatDeck().reconcile("home", ShuttleType.AEROSHUTTLE, 2);
        // The company rides up to Relief on Wayline's truck: Morgan comes straight after the ship.
        assertTrue(roster.boatDeck().flightReady().isEmpty());
        assertEquals(Step.INTRODUCTION, induction.step(roster));
        assertFalse(induction.finish(roster));
        induction.introduce(sabine);
        assertEquals(Step.SPECIALTY, induction.step(roster));
        assertTrue(sabine.train(OfficerSpecialty.TACTICAL_COVER));
        induction = roundTrip(induction);
        assertTrue(induction.companyPreparation());
        assertEquals(Step.FORMATION, induction.step(roster));
        // The game draws both from one purse; here the hold pays the squad's price, so that share leaves the purse.
        purse.credits -= SquadFoundingCost.STANDARD.credits();
        var squad = new SquadFoundingWorkshop(roster, hold).foundSquad().squad();
        assertNotNull(squad);
        assertTrue(CompanyPreparation.squadBill(roster).isEmpty());

        assertEquals(Step.ASSIGNMENT, induction.step(roster));
        roster.assignCaptainToSquad(sabine.id(), squad.id());
        assertEquals(Step.READY, induction.step(roster));
        assertTrue(induction.finish(roster));
        assertTrue(roundTrip(induction).complete());
        // The boat's share of the bill waits for the first-lift lesson after Relief, and funds it exactly.
        assertTrue(new BoatWorkshop(roster.boatDeck(), purse).fabricate(0).succeeded());
        assertEquals(0, purse.credits);
        assertTrue(hold.stock.values().stream().allMatch(n -> n == 0));
        assertEquals(0, hold.credits);
        assertEquals(0, CompanyPreparation.boatPrice(roster, ShuttleType.AEROSHUTTLE));
    }

    @Test void understrengthAssignedSquadCannotFinishAndTransferDoesNotMintBoats() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain sabine = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0);
        roster.add(sabine); roster.beginCompanyPreparation(sabine);
        roster.setCompanyShip("first", "First", 200);
        BoatDeck deck = roster.boatDeck();
        deck.reconcile("first", ShuttleType.HERMES, 2);
        CampaignBoat boat = deck.fabricate(0);
        roster.officerInduction().introduce(sabine); sabine.train(OfficerSpecialty.RECOVERY_PLANNING);
        MarineSquad squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 11);
        roster.assignCaptainToSquad(sabine.id(), squad.id());
        assertEquals(Step.FORMATION, roster.officerInduction().step(roster));
        deck.reconcile("second", ShuttleType.HERMES, 4);
        assertEquals(1, deck.flightReady().size());
        assertSame(boat, deck.flightReady().get(0));
        deck.lose(List.of(boat.id()));
        deck.reconcile("first", ShuttleType.HERMES, 2);
        assertTrue(deck.flightReady().isEmpty());
    }

    @Test void legacyOfficerLessonsAreNotRestartedOrStrippedOfBoats() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain sabine = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0);
        roster.add(sabine); roster.beginOfficerInduction(sabine);
        roster.boatDeck().reconcile("home", ShuttleType.HERMES, 2);
        roster.beginCompanyPreparation(sabine);
        assertFalse(CompanyPreparation.required(roster));
        assertEquals(Step.INTRODUCTION, roster.officerInduction().step(roster));
        assertEquals(2, roster.boatDeck().flightReady().size());
    }

    private static OfficerInduction roundTrip(OfficerInduction state) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(state); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OfficerInduction) in.readObject();
        }
    }
}
