package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
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
            assertNotNull(CompanyYard.install(ship, market));
            assertFalse(installed[0]);
            assertEquals(15000, credits.get());
            uiMethods.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", planet)));
            assertNull(CompanyYard.install(ship, market));
            assertTrue(installed[0]);
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
