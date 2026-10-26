package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.*;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.MutableValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.dillon.starsectormarines.campaign.start.PreparationDepot.Purpose;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreparationDepotTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realPreparationOrdersConsumeTheConsignmentOnceAndPreserveTravelSupplies(boolean protectedStart) throws Exception {
        PreparationDepot depot = new PreparationDepot("jangala", protectedStart);
        Map<String, Float> cargo = new HashMap<>(protectedStart ? Map.of() : Map.of(Commodities.SUPPLIES, 25f));
        MutableValue purse = new MutableValue(0);
        Resources resources = new Resources(new WorkshopStores(cargo(cargo, purse), depot, Purpose.PREFLIGHT));
        MarineRoster roster = new MarineRoster();
        MarineCaptain sabine = new MarineCaptain("Sabine Hale", null, Rank.LIEUTENANT, 0);
        roster.add(sabine); roster.beginCompanyPreparation(sabine);
        assertTrue(CompanyPreparation.canPrepare(roster, ShuttleType.HERMES, 1, resources, resources));
        resources = new Resources(new WorkshopStores(cargo(cargo, purse), depot, Purpose.BOAT));
        roster.setCompanyShip("cerberus", "Second Wind", 75);
        roster.boatDeck().reconcile("cerberus", ShuttleType.HERMES, 1);
        assertTrue(new BoatWorkshop(roster.boatDeck(), resources).fabricate(0).succeeded());
        assertFalse(new BoatWorkshop(roster.boatDeck(), resources).fabricate(0).succeeded());
        assertEquals(0, depot.credits(), "the prepaid yard credit paid for the boat");
        assertEquals(0f, purse.get(), "and the company's own credits were never touched");
        assertEquals(36, depot.available(Commodities.SUPPLIES));
        depot = roundTrip(depot);
        resources = new Resources(new WorkshopStores(cargo(cargo, purse), depot, Purpose.SQUAD));
        roster.officerInduction().introduce(sabine);
        assertTrue(sabine.train(OfficerSpecialty.TACTICAL_COVER));
        var squad = new SquadFoundingWorkshop(roster, resources).foundSquad().squad();
        assertNotNull(squad);
        roster.assignCaptainToSquad(sabine.id(), squad.id());
        assertTrue(roster.officerInduction().finish(roster));
        assertTrue(depot.stock().values().stream().allMatch(n -> n == 0));
        assertFalse(depot.holdsAnything());
        assertEquals(protectedStart ? Map.of() : Map.of(Commodities.SUPPLIES, 25f), cargo);
        assertFalse(new SquadFoundingWorkshop(roster, resources).foundSquad().succeeded());
        assertEquals("jangala", roundTrip(depot).marketId());
        assertFalse(roundTrip(depot).holdsAnything());
    }

    @Test void unrelatedJobsAndBoatOrdersCannotSpendTheSquadsStores() throws Exception {
        var depot = new PreparationDepot("jangala", true);
        var purse = new MutableValue(0);
        var cargo = cargo(new HashMap<>(), purse);
        var initial = depot.stock();
        int yardCredit = depot.credits();
        assertTrue(yardCredit > 0);
        assertFalse(new WorkshopStores(cargo, depot).spend(Map.of(Commodities.SUPPLIES, 1)));
        assertFalse(new WorkshopStores(cargo, depot, Purpose.PREFLIGHT).spend(initial));
        assertFalse(new WorkshopStores(cargo, depot, Purpose.BOAT).spend(Map.of(Commodities.SUPPLIES, 1)));
        assertFalse(new WorkshopStores(cargo, depot, Purpose.SQUAD).spend(Map.of(Commodities.SUPPLIES, 37)));
        assertFalse(new WorkshopStores(cargo, depot, Purpose.SQUAD).spendCredits(1),
                "the squad cannot draw the boat's yard credit");
        assertFalse(new WorkshopStores(cargo, depot).spendCredits(1));
        assertEquals(initial, depot.stock());
        assertEquals(yardCredit, depot.credits());
        depot = roundTrip(depot);
        assertEquals(0, depot.available(Commodities.SUPPLIES, Purpose.BOAT));
        assertEquals(36, depot.available(Commodities.SUPPLIES, Purpose.SQUAD));
        assertEquals(0, depot.available(Commodities.SUPPLIES, Purpose.GENERAL));
        assertEquals(36, depot.available(Commodities.SUPPLIES, Purpose.PREFLIGHT));
        assertEquals(yardCredit, depot.credits(Purpose.BOAT));
        assertEquals(0, depot.credits(Purpose.SQUAD));
    }

    @Test void unavailableDepotAndShortBillsCannotSpendAnyStores() {
        PreparationDepot depot = new PreparationDepot("jangala");
        Map<String, Integer> initial = depot.stock();
        Map<String, Float> cargo = new HashMap<>(Map.of(Commodities.SUPPLIES, 25f));
        MutableValue purse = new MutableValue(0);
        WorkshopStores away = new WorkshopStores(cargo(cargo, purse), null);
        assertFalse(away.spend(initial));
        WorkshopStores docked = new WorkshopStores(cargo(cargo, purse), depot);
        Map<String, Integer> shortBill = new LinkedHashMap<>(initial);
        shortBill.put(Commodities.SUPPLIES, initial.get(Commodities.SUPPLIES) + 26);
        assertFalse(docked.spend(shortBill));
        assertEquals(initial, depot.stock());
        assertEquals(25f, cargo.get(Commodities.SUPPLIES));
        int held = depot.available(Commodities.SUPPLIES);
        assertTrue(docked.spend(Map.of(Commodities.SUPPLIES, held + 4)));
        assertEquals(0, depot.available(Commodities.SUPPLIES));
        assertEquals(21f, cargo.get(Commodities.SUPPLIES), "the depot is drawn first, then the hold");
    }

    @Test void aBoatJobDrawsTheDepotsCreditBeforeThePurseAndPreflightSpendsNothing() {
        PreparationDepot depot = new PreparationDepot("jangala", true);
        MutableValue purse = new MutableValue(1000);
        int yardCredit = depot.credits();
        var cargo = cargo(new HashMap<>(), purse);
        var boat = new WorkshopStores(cargo, depot, Purpose.BOAT);
        assertEquals(yardCredit + 1000L, boat.credits());

        assertFalse(new WorkshopStores(cargo, depot, Purpose.PREFLIGHT).spendCredits(1));
        assertFalse(boat.spendCredits(yardCredit + 1001), "a price past both is refused whole");
        assertEquals(yardCredit, depot.credits());
        assertEquals(1000f, purse.get());

        assertTrue(boat.spendCredits(yardCredit - 5));
        assertEquals(5, depot.credits());
        assertEquals(1000f, purse.get(), "the purse is untouched while the depot covers the price");
        assertTrue(boat.spendCredits(30));
        assertEquals(0, depot.credits());
        assertEquals(975f, purse.get(), "past the depot's credit the purse pays the rest");

        assertTrue(boat.refund(40));
        assertEquals(1015f, purse.get());
        assertEquals(0, depot.credits(), "recycled credits never go back into the depot");
    }

    @Test void aLegacyUnprotectedDepotTurnsItsBoatMaterialsIntoCreditAndKeepsItsSupplies() throws Exception {
        PreparationDepot legacy = new PreparationDepot("jangala", false);
        setField(legacy, "credits", null);
        Map<String, Integer> stock = field(legacy, "stock");
        int supplies = stock.get(Commodities.SUPPLIES);
        stock.put(Commodities.METALS, 10);
        stock.put(Commodities.HEAVY_MACHINERY, 2);
        stock.put(Commodities.RARE_METALS, 1);

        PreparationDepot loaded = roundTrip(legacy);

        assertEquals(10 * 30 + 2 * 150 + 1 * 200, loaded.credits(), "materials convert at the base prices");
        assertEquals(supplies, loaded.available(Commodities.SUPPLIES), "supplies also feed the squad, so they stay");
        assertEquals(0, loaded.available(Commodities.METALS));
        assertEquals(0, loaded.available(Commodities.HEAVY_MACHINERY));
        assertEquals(0, loaded.available(Commodities.RARE_METALS));
    }

    @Test void aLegacyProtectedDepotTurnsItsWholeBoatStockIntoCreditAndLeavesTheSquadsStoresAlone() throws Exception {
        PreparationDepot legacy = new PreparationDepot("jangala", true);
        Map<String, Integer> squadStores = legacy.stock();
        setField(legacy, "credits", null);
        Map<String, Integer> boatStock = field(legacy, "boatStock");
        boatStock.put(Commodities.SUPPLIES, 30);
        boatStock.put(Commodities.METALS, 10);
        boatStock.put(Commodities.HEAVY_MACHINERY, 2);

        PreparationDepot loaded = roundTrip(legacy);

        assertEquals(30 * 100 + 10 * 30 + 2 * 150, loaded.credits());
        assertEquals(loaded.credits(), loaded.credits(Purpose.BOAT));
        assertEquals(0, loaded.available(Commodities.SUPPLIES, Purpose.BOAT));
        assertEquals(squadStores, loaded.stock(), "the squad's stores are exactly as they were");
        assertEquals(squadStores.get(Commodities.SUPPLIES), loaded.available(Commodities.SUPPLIES, Purpose.SQUAD));
        assertEquals(0, loaded.credits(Purpose.SQUAD));
    }
    @Test void yardRefusesEveryInvalidOrderBeforeMutating() {
        assertNull(CompanyYard.refusal(true, true, false, false, true, MercenaryCompanyConversion.CREDIT_COST));
        assertNotNull(CompanyYard.refusal(false, true, false, false, true, MercenaryCompanyConversion.CREDIT_COST));
        assertNotNull(CompanyYard.refusal(true, false, false, false, true, MercenaryCompanyConversion.CREDIT_COST));
        assertNotNull(CompanyYard.refusal(true, true, true, false, true, MercenaryCompanyConversion.CREDIT_COST));
        assertNotNull(CompanyYard.refusal(true, true, false, true, true, MercenaryCompanyConversion.CREDIT_COST));
        assertNotNull(CompanyYard.refusal(true, true, false, false, false, MercenaryCompanyConversion.CREDIT_COST));
        assertNotNull(CompanyYard.refusal(true, true, false, false, true, MercenaryCompanyConversion.CREDIT_COST - 1));
        int waivedPrice = MercenaryCompanyConversion.CREDIT_COST - CompanyYard.waived(FoundingReserve.CREDITS);
        assertNull(CompanyYard.refusal(true, true, false, false, true, FoundingReserve.CREDITS, waivedPrice),
                "The reserve alone pays the waived estimate");
        assertNotNull(CompanyYard.refusal(true, true, false, false, true, FoundingReserve.CREDITS - 1, waivedPrice));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> field(PreparationDepot depot, String name) throws Exception {
        Field field = PreparationDepot.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, Integer>) field.get(depot);
    }
    private static void setField(PreparationDepot depot, String name, Object value) throws Exception {
        Field field = PreparationDepot.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(depot, value);
    }
    private static CargoAPI cargo(Map<String, Float> stock, MutableValue credits) {
        return (CargoAPI) Proxy.newProxyInstance(CargoAPI.class.getClassLoader(), new Class<?>[]{CargoAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCommodityQuantity" -> stock.getOrDefault(args[0], 0f);
                    case "removeCommodity" -> { stock.merge((String) args[0], -((Number) args[1]).floatValue(), Float::sum); yield null; }
                    case "getCredits" -> credits;
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
    /** The company's credits and the squad's stores, both drawn through one {@link WorkshopStores}. */
    private record Resources(WorkshopStores stores) implements FabricationResources, SquadFoundingResources {
        @Override public long credits() { return stores.credits(); }
        @Override public boolean atMarket() { return true; }
        @Override public int available(String id) { return stores.available(id); }
        @Override public String commodityName(String id) { return id; }
        @Override public String commodityIcon(String id) { return ""; }
        @Override public boolean spend(FabricationCost cost) { return stores.spendCredits(cost.credits()); }
        @Override public boolean spend(SquadFoundingCost cost) {
            Map<String, Integer> bill = new LinkedHashMap<>();
            cost.lines().forEach(l -> bill.merge(l.commodityId(), l.quantity(), Integer::sum));
            return stores.spend(bill);
        }
    }
}
