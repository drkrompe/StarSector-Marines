package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CivilizedPortTest {

    @Test void aVisibleColonyWithASpaceportIsCivilizedEvenWhileItsPortIsDisrupted() {
        assertTrue(CivilizedPort.civilized(stub(MarketAPI.class, market())));
        var disrupted = market();
        disrupted.put("getIndustries", List.of(spaceport(false)));
        assertTrue(CivilizedPort.civilized(stub(MarketAPI.class, disrupted)));
    }

    @Test void hiddenBasesBareWorldsAndPortlessColoniesAreNot() {
        assertFalse(CivilizedPort.civilized(null), "a derelict or an empty world has no market");
        var hidden = market(); hidden.put("isHidden", true);
        var bare = market(); bare.put("isPlanetConditionMarketOnly", true);
        var empty = market(); empty.put("getSize", 0);
        var portless = market(); portless.put("getIndustries", List.of());
        for (var values : List.of(hidden, bare, empty, portless))
            assertFalse(CivilizedPort.civilized(stub(MarketAPI.class, values)), values.toString());
    }

    /** Vanilla's trade mode decides: open trade anywhere it was granted, the black market only when sneaking in. */
    @Test void itTradesWhereVanillaWouldOpenASubmarket() {
        var withBlackMarket = market(); withBlackMarket.put("hasSubmarket", true);
        MarketAPI hostile = stub(MarketAPI.class, withBlackMarket);
        MarketAPI lawful = stub(MarketAPI.class, market());
        assertTrue(CivilizedPort.trades(lawful, "OPEN"), "docking clearance opens the market");
        assertTrue(CivilizedPort.trades(hostile, "SNEAK"), "dark and unseen, the black market sells");
        assertFalse(CivilizedPort.trades(lawful, "SNEAK"), "no black market, nothing to buy from in the dark");
        assertFalse(CivilizedPort.trades(hostile, "NONE"), "a port that refused docking sells nothing");
        assertFalse(CivilizedPort.trades(hostile, null), "a dialog vanilla's market rules never ran in");
    }

    private static Map<String, Object> market() {
        return new HashMap<>(Map.of("getId", "jangala", "getSize", 4,
                "getFaction", stub(FactionAPI.class, Map.of("getId", "hegemony")),
                "getIndustries", List.of(spaceport(true))));
    }

    private static Industry spaceport(boolean functional) {
        return stub(Industry.class, Map.of("isFunctional", functional,
                "getSpec", stub(IndustrySpecAPI.class, Map.of("hasTag", true))));
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
