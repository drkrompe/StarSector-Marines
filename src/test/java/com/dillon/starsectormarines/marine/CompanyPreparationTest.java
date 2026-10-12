package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanyPreparationTest {
    @Test void theCompleteBillFundsTheRealBoatAndSquadOrdersExactly() throws Exception {
        MarineRoster roster = new MarineRoster();
        MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0);
        roster.add(mira);
        roster.beginCompanyPreparation(mira);
        OfficerInduction induction = roster.officerInduction();
        assertEquals(Step.SHIP, induction.step(roster));
        Hold hold = new Hold();
        for (var line : CompanyPreparation.remainingBill(roster, ShuttleType.AEROSHUTTLE))
            hold.stock.put(line.commodityId(), line.quantity());
        assertEquals(Map.of(Commodities.METALS, 60, Commodities.HEAVY_MACHINERY, 15,
                Commodities.SUPPLIES, 76, Commodities.MARINES, 12, Commodities.FOOD, 24), hold.stock);
        assertFalse(CompanyPreparation.canPrepare(roster, ShuttleType.AEROSHUTTLE, 0, hold));
        hold.stock.put(Commodities.SUPPLIES, 75);
        assertFalse(CompanyPreparation.canPrepare(roster, ShuttleType.AEROSHUTTLE, 2, hold));
        hold.stock.put(Commodities.SUPPLIES, 76);
        assertTrue(CompanyPreparation.canPrepare(roster, ShuttleType.AEROSHUTTLE, 2, hold));
        roster.setCompanyShip("home", "Home", 200);
        roster.boatDeck().reconcile("home", ShuttleType.AEROSHUTTLE, 2);
        assertTrue(roster.boatDeck().airworthy().isEmpty());
        assertEquals(Step.LIFT, induction.step(roster));
        assertFalse(induction.finish(roster));
        assertTrue(new BoatWorkshop(roster.boatDeck(), hold).fabricate(0).succeeded());
        assertEquals(Step.INTRODUCTION, induction.step(roster));
        induction.introduce(mira);
        assertEquals(Step.SPECIALTY, induction.step(roster));
        assertTrue(mira.train(OfficerSpecialty.TACTICAL_COVER));
        induction = roundTrip(induction);
        assertTrue(induction.companyPreparation());
        assertEquals(Step.FORMATION, induction.step(roster));
        var squad = new SquadFoundingWorkshop(roster, hold).foundSquad().squad();
        assertNotNull(squad);
        assertTrue(hold.stock.values().stream().allMatch(n -> n == 0));
        assertTrue(CompanyPreparation.remainingBill(roster, ShuttleType.AEROSHUTTLE).isEmpty());
        assertEquals(Step.ASSIGNMENT, induction.step(roster));
        roster.assignCaptainToSquad(mira.id(), squad.id());
        assertEquals(Step.READY, induction.step(roster));
        assertTrue(induction.finish(roster));
        assertTrue(roundTrip(induction).complete());
    }

    @Test void understrengthAssignedSquadCannotFinishAndTransferDoesNotMintBoats() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0);
        roster.add(mira); roster.beginCompanyPreparation(mira);
        roster.setCompanyShip("first", "First", 200);
        BoatDeck deck = roster.boatDeck();
        deck.reconcile("first", ShuttleType.HERMES, 2);
        CampaignBoat boat = deck.fabricate(0);
        roster.officerInduction().introduce(mira); mira.train(OfficerSpecialty.RECOVERY_PLANNING);
        MarineSquad squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 11);
        roster.assignCaptainToSquad(mira.id(), squad.id());
        assertEquals(Step.FORMATION, roster.officerInduction().step(roster));
        deck.reconcile("second", ShuttleType.HERMES, 4);
        assertEquals(1, deck.airworthy().size());
        assertSame(boat, deck.airworthy().get(0));
        deck.lose(List.of(boat.id()));
        deck.reconcile("first", ShuttleType.HERMES, 2);
        assertTrue(deck.airworthy().isEmpty());
    }

    @Test void legacyOfficerLessonsAreNotRestartedOrStrippedOfBoats() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0);
        roster.add(mira); roster.beginOfficerInduction(mira);
        roster.boatDeck().reconcile("home", ShuttleType.HERMES, 2);
        roster.beginCompanyPreparation(mira);
        assertFalse(CompanyPreparation.required(roster));
        assertEquals(Step.INTRODUCTION, roster.officerInduction().step(roster));
        assertEquals(2, roster.boatDeck().airworthy().size());
    }

    private static OfficerInduction roundTrip(OfficerInduction state) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(state); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OfficerInduction) in.readObject();
        }
    }

    private static final class Hold implements FabricationResources, SquadFoundingResources {
        final Map<String, Integer> stock = new HashMap<>();
        @Override public int available(String id) { return stock.getOrDefault(id, 0); }
        @Override public String commodityName(String id) { return id; }
        @Override public String commodityIcon(String id) { return ""; }
        @Override public boolean spend(FabricationCost cost) {
            if (!canAfford(cost)) return false;
            cost.lines().forEach(l -> stock.merge(l.commodityId(), -l.quantity(), Integer::sum));
            return true;
        }
        @Override public boolean spend(SquadFoundingCost cost) {
            return spend(new FabricationCost(cost.lines().stream()
                    .map(l -> new FabricationCost.Line(l.commodityId(), l.quantity())).toList()));
        }
    }
}
