package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.start.OpeningPort.Use;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpeningPortTest {
    private static final StarSystemAPI CORVUS = system("Corvus", 0, 0);
    private static final StarSystemAPI NEAR = system("Near", 3000, 0);
    private static final StarSystemAPI FAR = system("Far", 9000, 0);

    @Test void jangalaThenCorvusThenIndependentThenNearest() {
        var jangala = market("jangala", CORVUS, "hegemony");
        var asharu = market("asharu", CORVUS, "independent");
        var agreus = market("agreus", FAR, "independent");
        var far = market("a-far", FAR, "hegemony");
        var near = market("z-near", NEAR, "hegemony");
        assertEquals("jangala", resolve(Use.YARD, near, far, agreus, asharu, jangala));
        assertEquals("asharu", resolve(Use.YARD, near, far, agreus, asharu));
        assertEquals("agreus", resolve(Use.YARD, near, far, agreus), "an Independent world beats a nearer one");
        assertEquals("z-near", resolve(Use.YARD, far, near), "nearest Corvus before market id");
    }

    @Test void aRepickStaysInTheStartSystemBeforeJangalaOrCorvus() {
        var agreusYard = stub(MarketAPI.class, market("agreus", FAR, "hegemony"));
        var jangala = stub(MarketAPI.class, market("jangala", CORVUS, "hegemony"));
        var near = stub(MarketAPI.class, market("z-near", NEAR, "independent"));
        var markets = sector(List.of(jangala, near, agreusYard));
        assertEquals("agreus", OpeningPort.resolve(markets, Use.YARD, FAR).getId());
        assertEquals("agreus", OpeningPort.resolve(markets, Use.OFFICE, FAR).getId());
        assertEquals("jangala", OpeningPort.resolve(markets, Use.YARD, null).getId());
        assertEquals("jangala", OpeningPort.resolve(sector(List.of(jangala, near)), Use.YARD, FAR).getId(),
                "with nothing left at home, the ordinary order decides");
    }

    @Test void aYardNeedsASpaceportEvenIfItIsDisruptedTodayAndAnOfficeDoesNot() {
        var noPort = market("jangala", CORVUS, "hegemony");
        noPort.put("getIndustries", List.of());
        var disrupted = market("asharu", CORVUS, "independent");
        disrupted.put("getIndustries", List.of(spaceport(false)));
        assertEquals("asharu", resolve(Use.YARD, stub(MarketAPI.class, noPort), stub(MarketAPI.class, disrupted)));
        assertEquals("jangala", resolve(Use.OFFICE, stub(MarketAPI.class, noPort), stub(MarketAPI.class, disrupted)));
    }

    @Test void hostileHiddenOwnedDecivilizedAndStationMarketsAreNeverChosen() {
        var hostile = market("jangala", CORVUS, "hegemony");
        hostile.put("getFaction", stub(FactionAPI.class, Map.of("getId", "hegemony", "isHostileTo", true)));
        var hidden = market("a", CORVUS, "independent"); hidden.put("isHidden", true);
        var owned = market("b", CORVUS, "independent"); owned.put("isPlayerOwned", true);
        var decivilized = market("c", CORVUS, "independent"); decivilized.put("isPlanetConditionMarketOnly", true);
        var station = market("d", CORVUS, "independent"); station.remove("getPlanetEntity");
        var fallback = market("e", FAR, "hegemony");
        List<MarketAPI> all = new ArrayList<>();
        for (var values : List.of(hostile, hidden, owned, decivilized, station, fallback))
            all.add(stub(MarketAPI.class, values));
        assertEquals("e", OpeningPort.resolve(sector(all), Use.OFFICE).getId());
        assertNull(OpeningPort.resolve(sector(all.subList(0, 5)), Use.YARD));
        assertNull(OpeningPort.resolve(null, Use.YARD));
    }

    @Test void aLostDepotPortIsGoneDecivilizedHiddenOrHostileButNotMerelyDisrupted() {
        assertTrue(CerberusCompanyStart.lost(null, null));
        var hostile = market("jangala", CORVUS, "hegemony");
        hostile.put("getFaction", stub(FactionAPI.class, Map.of("getId", "hegemony", "isHostileTo", true)));
        assertTrue(CerberusCompanyStart.lost(stub(MarketAPI.class, hostile), player()));
        var decivilized = market("jangala", CORVUS, "hegemony"); decivilized.put("isPlanetConditionMarketOnly", true);
        assertTrue(CerberusCompanyStart.lost(stub(MarketAPI.class, decivilized), player()));
        var disrupted = market("jangala", CORVUS, "hegemony"); disrupted.put("getIndustries", List.of(spaceport(false)));
        assertFalse(CerberusCompanyStart.lost(stub(MarketAPI.class, disrupted), player()));
    }

    @Test void aRelocatedDepotKeepsWhatIsLeftAndAddsNothing() {
        var depot = new PreparationDepot("jangala", true);
        int before = depot.stock().values().stream().mapToInt(Integer::intValue).sum();
        depot.relocate("asharu");
        assertEquals("asharu", depot.marketId());
        assertEquals(before, depot.stock().values().stream().mapToInt(Integer::intValue).sum());
        assertThrows(IllegalArgumentException.class, () -> depot.relocate(" "));
    }

    private static String resolve(Use use, MarketAPI... markets) {
        MarketAPI market = OpeningPort.resolve(sector(List.of(markets)), use);
        return market == null ? null : market.getId();
    }
    @SafeVarargs
    private static String resolve(Use use, Map<String, Object>... markets) {
        List<MarketAPI> stubs = new ArrayList<>();
        for (var values : markets) stubs.add(stub(MarketAPI.class, values));
        return resolve(use, stubs.toArray(MarketAPI[]::new));
    }

    private static Map<String, Object> market(String id, StarSystemAPI system, String factionId) {
        PlanetAPI planet = stub(PlanetAPI.class, Map.of("getId", id, "getName", id, "getStarSystem", system));
        return new HashMap<>(Map.of("getId", id, "getSize", 4, "getFactionId", factionId,
                "getFaction", stub(FactionAPI.class, Map.of("getId", factionId)),
                "getPlanetEntity", planet, "getIndustries", List.of(spaceport(true))));
    }
    private static Industry spaceport(boolean functional) {
        return stub(Industry.class, Map.of("isFunctional", functional,
                "getSpec", stub(IndustrySpecAPI.class, Map.of("hasTag", true))));
    }
    private static StarSystemAPI system(String name, float x, float y) {
        return stub(StarSystemAPI.class, Map.of("getBaseName", name, "getLocation", new Vector2f(x, y)));
    }
    private static FactionAPI player() { return stub(FactionAPI.class, Map.of("getId", "player")); }
    private static SectorAPI sector(List<MarketAPI> markets) {
        return stub(SectorAPI.class, Map.of("getEconomy", stub(EconomyAPI.class, Map.of("getMarketsCopy", markets)),
                "getPlayerFaction", player(), "getStarSystem", CORVUS));
    }

    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object value = values.get(method.getName());
            if (value != null || !method.getReturnType().isPrimitive()) return value;
            Class<?> t = method.getReturnType();
            return t == boolean.class ? (Object) false : t == float.class ? (Object) 0f
                    : t == double.class ? (Object) 0d : t == long.class ? (Object) 0L : (Object) 0;
        }));
    }
}
