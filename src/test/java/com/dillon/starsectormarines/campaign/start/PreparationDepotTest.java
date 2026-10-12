package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.*;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreparationDepotTest {
    @Test void realPreparationOrdersConsumeTheConsignmentOnceAndPreserveTravelSupplies() throws Exception {
        PreparationDepot depot = new PreparationDepot("jangala");
        Map<String, Float> cargo = new HashMap<>(Map.of(Commodities.SUPPLIES, 25f));
        Resources resources = new Resources(new WorkshopStores(cargo(cargo), depot));
        MarineRoster roster = new MarineRoster();
        MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0);
        roster.add(mira); roster.beginCompanyPreparation(mira);
        assertTrue(CompanyPreparation.canPrepare(roster, ShuttleType.HERMES, 1, resources));
        roster.setCompanyShip("cerberus", "Second Wind", 75);
        roster.boatDeck().reconcile("cerberus", ShuttleType.HERMES, 1);
        assertTrue(new BoatWorkshop(roster.boatDeck(), resources).fabricate(0).succeeded());
        assertFalse(new BoatWorkshop(roster.boatDeck(), resources).fabricate(0).succeeded());
        assertEquals(36, depot.available(Commodities.SUPPLIES));
        depot = roundTrip(depot);
        resources = new Resources(new WorkshopStores(cargo(cargo), depot));
        roster.officerInduction().introduce(mira);
        assertTrue(mira.train(OfficerSpecialty.TACTICAL_COVER));
        var squad = new SquadFoundingWorkshop(roster, resources).foundSquad().squad();
        assertNotNull(squad);
        roster.assignCaptainToSquad(mira.id(), squad.id());
        assertTrue(roster.officerInduction().finish(roster));
        assertTrue(depot.stock().values().stream().allMatch(n -> n == 0));
        assertEquals(Map.of(Commodities.SUPPLIES, 25f), cargo);
        assertFalse(new SquadFoundingWorkshop(roster, resources).foundSquad().succeeded());
        assertEquals("jangala", roundTrip(depot).marketId());
        assertTrue(roundTrip(depot).stock().values().stream().allMatch(n -> n == 0));
    }

    @Test void unavailableDepotAndShortBillsCannotSpendAnyStores() {
        PreparationDepot depot = new PreparationDepot("jangala");
        Map<String, Integer> initial = depot.stock();
        Map<String, Float> cargo = new HashMap<>(Map.of(Commodities.SUPPLIES, 25f));
        WorkshopStores away = new WorkshopStores(cargo(cargo), null);
        assertFalse(away.spend(initial));
        WorkshopStores docked = new WorkshopStores(cargo(cargo), depot);
        Map<String, Integer> shortBill = new LinkedHashMap<>(initial);
        shortBill.put(Commodities.METALS, 31);
        assertFalse(docked.spend(shortBill));
        assertEquals(initial, depot.stock());
        assertEquals(25f, cargo.get(Commodities.SUPPLIES));
        assertTrue(docked.spend(Map.of(Commodities.SUPPLIES, 60)));
        assertEquals(0, depot.available(Commodities.SUPPLIES));
        assertEquals(21f, cargo.get(Commodities.SUPPLIES));
    }

    @Test void yardRefusesEveryInvalidOrderBeforeMutating() {
        assertNull(CompanyYard.refusal(true, true, false, false, true, 5000));
        assertNotNull(CompanyYard.refusal(false, true, false, false, true, 5000));
        assertNotNull(CompanyYard.refusal(true, false, false, false, true, 5000));
        assertNotNull(CompanyYard.refusal(true, true, true, false, true, 5000));
        assertNotNull(CompanyYard.refusal(true, true, false, true, true, 5000));
        assertNotNull(CompanyYard.refusal(true, true, false, false, false, 5000));
        assertNotNull(CompanyYard.refusal(true, true, false, false, true, 4999));
    }

    private static CargoAPI cargo(Map<String, Float> stock) {
        return (CargoAPI) Proxy.newProxyInstance(CargoAPI.class.getClassLoader(), new Class<?>[]{CargoAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCommodityQuantity" -> stock.getOrDefault(args[0], 0f);
                    case "removeCommodity" -> { stock.merge((String) args[0], -((Number) args[1]).floatValue(), Float::sum); yield null; }
                    default -> throw new AssertionError(method.getName());
                });
    }
    private static PreparationDepot roundTrip(PreparationDepot depot) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(depot); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (PreparationDepot) in.readObject();
        }
    }
    private record Resources(WorkshopStores stores) implements FabricationResources, SquadFoundingResources {
        @Override public int available(String id) { return stores.available(id); }
        @Override public String commodityName(String id) { return id; }
        @Override public String commodityIcon(String id) { return ""; }
        @Override public boolean spend(FabricationCost cost) {
            Map<String, Integer> bill = new LinkedHashMap<>();
            cost.lines().forEach(l -> bill.merge(l.commodityId(), l.quantity(), Integer::sum));
            return stores.spend(bill);
        }
        @Override public boolean spend(SquadFoundingCost cost) {
            return spend(new FabricationCost(cost.lines().stream()
                    .map(l -> new FabricationCost.Line(l.commodityId(), l.quantity())).toList()));
        }
    }
}
