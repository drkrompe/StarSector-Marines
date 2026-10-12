package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.Global;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.impl.campaign.rulecmd.CompanyYardCMD;
import com.dillon.starsectormarines.ops.CompanyMeans;
import com.dillon.starsectormarines.ops.CompanyShipDesignation;
import com.dillon.starsectormarines.ops.ShipTransferViewModel;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.MutableValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CompanyYardTest {
    @Test void installRequiresActualPlanetInteractionAndIssuesTheDepotOnlyOnce() {
        SectorAPI previous = Global.getSector();
        MutableValue credits = new MutableValue(15000);
        boolean[] installed = {false};
        Map<String, Object> planetMethods = new HashMap<>();
        PlanetAPI planet = stub(PlanetAPI.class, planetMethods);
        var industry = stub(Industry.class, Map.of("isFunctional", true,
                "getSpec", stub(IndustrySpecAPI.class, Map.of("hasTag", true))));
        var faction = stub(FactionAPI.class, Map.of("isHostileTo", false));
        MarketAPI market = stub(MarketAPI.class, Map.of("getId", "jangala", "getPlanetEntity", planet,
                "getIndustries", List.of(industry), "getFaction", faction));
        planetMethods.put("getMarket", market);
        ShipVariantAPI[] variant = new ShipVariantAPI[1];
        variant[0] = stub(ShipVariantAPI.class, Map.of("clone", (Function<Object[], Object>) a -> variant[0],
                "hasHullMod", (Function<Object[], Object>) a -> installed[0],
                "addPermaMod", (Function<Object[], Object>) a -> { installed[0] = true; return null; }));
        var ship = stub(FleetMemberAPI.class, Map.of("getId", "starter", "getVariant", variant[0],
                "getHullSpec", stub(ShipHullSpecAPI.class, Map.of("getBaseHullId", "cerberus"))));
        var fleet = stub(CampaignFleetAPI.class, Map.of(
                "getFleetData", stub(FleetDataAPI.class, Map.of("getMembersListCopy", List.of(ship))),
                "getCargo", stub(CargoAPI.class, Map.of("getCredits", credits))));
        Map<String, Object> uiMethods = new HashMap<>();
        Map<String, Object> saved = new HashMap<>(Map.of(CerberusCompanyStart.SHIP_KEY, "starter"));
        try {
            Global.setSector(stub(SectorAPI.class, Map.of("getPersistentData", saved, "getPlayerFleet", fleet,
                    "getPlayerFaction", faction, "getCampaignUI", stub(CampaignUIAPI.class, uiMethods))));
            assertTrue(CerberusCompanyStart.conversionRequired());
            var candidate = new ShipTransferViewModel.Candidate("other", "Transport", "Transport", 100, 200, TestHulls.transport());
            var means = CompanyMeans.of(12, 0, 100_000);
            var transfer = new ShipTransferViewModel(new Reactor(), () -> List.of(candidate),
                    () -> CompanyShipDesignation.Home.NONE, id -> fail("Cannot bypass the founding conversion"), means);
            transfer.commit();
            assertTrue(transfer.preparationBlocked());
            assertEquals(100_000, means.credits());
            assertNotNull(CompanyYard.install(ship, market));
            assertFalse(installed[0]);
            assertEquals(15000, credits.get());
            Map<String, Object> localData = new HashMap<>();
            MemoryAPI memory = stub(MemoryAPI.class, Map.of("set", (Function<Object[], Object>) a -> {
                localData.put((String) a[0], a[1]); return null;
            }));
            var lines = new ArrayList<String>();
            var options = new ArrayList<String>();
            var dialog = stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", planet,
                    "getTextPanel", stub(TextPanelAPI.class, Map.of("addPara", (Function<Object[], Object>) a -> { lines.add((String) a[0]); return null; })),
                    "getOptionPanel", stub(OptionPanelAPI.class, Map.of("addOption", (Function<Object[], Object>) a -> { options.add((String) a[0]); return null; }))));
            assertFalse(CompanyYardCMD.openRequired(dialog, Map.of(MemKeys.LOCAL, memory)), "No yard review away from port");
            uiMethods.put("getCurrentInteractionDialog", dialog);
            assertTrue(CompanyYardCMD.openRequired(dialog, Map.of(MemKeys.LOCAL, memory)));
            assertTrue(lines.stream().anyMatch(line -> line.contains("Mercenary Company Conversion")));
            assertTrue(options.contains("Install conversion - 5,000 credits"));
            assertEquals("starter", localData.get("$smYardSelectedShip"));
            assertEquals(15000, credits.get(), "Opening the required review spends nothing");
            assertFalse(installed[0]);
            assertNull(CompanyYard.install(ship, market));
            assertTrue(installed[0]);
            assertFalse(CerberusCompanyStart.conversionRequired());
            assertFalse(CompanyYardCMD.openRequired(dialog, Map.of(MemKeys.LOCAL, memory)));
            assertEquals(10000, credits.get());
            PreparationDepot depot = CerberusCompanyStart.accessibleDepot();
            assertNotNull(depot);
            depot.draw("metals", 30);
            assertNotNull(CompanyYard.install(ship, market));
            CerberusCompanyStart.establishDepot(ship, market);
            assertSame(depot, CerberusCompanyStart.depot());
            assertEquals(0, depot.available("metals"));
            assertEquals(10000, credits.get());
            uiMethods.clear();
            assertNull(CerberusCompanyStart.accessibleDepot());
            assertSame(depot, CerberusCompanyStart.depot());
            var other = stub(PlanetAPI.class, Map.of("getMarket", stub(MarketAPI.class, Map.of(
                    "getId", "other", "getPlanetEntity", planet, "getIndustries", List.of(industry), "getFaction", faction))));
            uiMethods.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", other)));
            assertNull(CerberusCompanyStart.accessibleDepot());
        } finally { Global.setSector(previous); }
    }

    @Test void bayRestorationIsPaidOnceAfterReliefAndPreservesTheExistingBoat() throws Exception {
        SectorAPI previous = Global.getSector();
        MutableValue credits = new MutableValue(20000);
        var faction = stub(FactionAPI.class, Map.of("isHostileTo", false));
        Map<String, Object> planetMethods = new HashMap<>();
        PlanetAPI planet = stub(PlanetAPI.class, planetMethods);
        var industry = stub(Industry.class, Map.of("isFunctional", true,
                "getSpec", stub(IndustrySpecAPI.class, Map.of("hasTag", true))));
        MarketAPI market = stub(MarketAPI.class, Map.of("getPlanetEntity", planet,
                "getIndustries", List.of(industry), "getFaction", faction));
        planetMethods.put("getMarket", market);
        var ship = stub(FleetMemberAPI.class, Map.of("getId", "starter",
                "getVariant", stub(ShipVariantAPI.class, Map.of("hasHullMod", true)),
                "getHullSpec", stub(ShipHullSpecAPI.class, Map.of("getBaseHullId", "cerberus"))));
        var owned = new ArrayList<>(List.of(ship));
        var fleet = stub(CampaignFleetAPI.class, Map.of(
                "getFleetData", stub(FleetDataAPI.class, Map.of("getMembersListCopy", owned)),
                "getCargo", stub(CargoAPI.class, Map.of("getCredits", credits))));
        Map<String, Object> ui = new HashMap<>();
        Map<String, Object> saved = new HashMap<>(Map.of(CerberusCompanyStart.SHIP_KEY, "starter"));
        var roster = new MarineRoster();
        roster.boatDeck().reconcile("starter", ShuttleType.HERMES, 1);
        var first = roster.boatDeck().boats().get(0);
        try {
            Global.setSector(stub(SectorAPI.class, Map.of("getPersistentData", saved, "getPlayerFleet", fleet,
                    "getPlayerFaction", faction, "getCampaignUI", stub(CampaignUIAPI.class, ui))));
            assertEquals(BoatBayState.DAMAGED, CompanyBayRestoration.state(ship));
            assertNotNull(CompanyBayRestoration.repair(ship, roster));
            roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
            assertNotNull(CompanyBayRestoration.repair(ship, roster), "Space is not a yard");
            ui.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", planet)));
            credits.set(7999);
            assertNotNull(CompanyBayRestoration.repair(ship, roster));
            assertEquals(7999, credits.get());
            assertEquals(BoatBayState.DAMAGED, CompanyBayRestoration.state(ship));
            credits.set(20000);
            assertNull(CompanyBayRestoration.offer(ship, roster).refusal());
            assertEquals(20000, credits.get(), "Review spends nothing");
            owned.clear();
            assertNotNull(CompanyBayRestoration.repair(ship, roster), "A stale review cannot repair an unowned hull");
            owned.add(ship);
            assertNull(CompanyBayRestoration.repair(ship, roster));
            assertEquals(12000, credits.get());
            assertEquals(BoatBayState.RESTORED, CompanyBayRestoration.state(ship));
            roster.boatDeck().reconcile("starter", ShuttleType.HERMES, 2);
            assertSame(first, roster.boatDeck().boats().get(0));
            assertNull(roster.boatDeck().boats().get(1), "Yard work awards no boat");
            assertNotNull(CompanyBayRestoration.repair(ship, roster));
            assertEquals(12000, credits.get());
            // Campaign state is plain saved data, independent of screen/model lifetime.
            var bytes = new ByteArrayOutputStream();
            try (var output = new ObjectOutputStream(bytes)) { output.writeObject(saved); }
            saved.clear();
            try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                Map<?, ?> reloaded = (Map<?, ?>) input.readObject();
                reloaded.forEach((key, value) -> saved.put((String) key, value));
            }
            assertEquals(BoatBayState.RESTORED, CompanyBayRestoration.state(ship));
            assertNull(CerberusCompanyStart.depot(), "Restoration never issues starting stores");
        } finally { Global.setSector(previous); }
    }

    @Test void restorationRefusesUnconvertedInactiveAndUnrelatedHulls() {
        assertNotNull(CompanyBayRestoration.refusal(true, BoatBayState.NONE, true, true, false, true, 9000));
        assertNotNull(CompanyBayRestoration.refusal(true, BoatBayState.DAMAGED, false, true, false, true, 9000));
        assertNotNull(CompanyBayRestoration.refusal(true, BoatBayState.DAMAGED, true, true, true, true, 9000));
        assertNull(CompanyBayRestoration.refusal(true, BoatBayState.DAMAGED, true, true, false, true, 8000));
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Object> methods) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            Object value = methods.get(method.getName());
            if (value instanceof Function<?, ?> fn) return ((Function<Object[], Object>) fn).apply(args);
            if (value != null) return value;
            if (method.getReturnType() == boolean.class) return false;
            return null;
        }));
    }
}
