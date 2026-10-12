package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.Global;
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
