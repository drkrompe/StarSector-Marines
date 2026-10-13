package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgreusArcCampaignTest {
    @Test void onlyTheActualDepartureReceiptEstablishesReferralHistory() {
        assertFalse(AgreusArcCampaign.eligibleHistory(null));
        var p = new ShepherdRecoveryProgress(); assertFalse(AgreusArcCampaign.eligibleHistory(p));
        String id = p.missionId(); p.begin(id, "Asharu"); p.settle(id, "Asharu", true, 18000, 0, true);
        assertFalse(AgreusArcCampaign.eligibleHistory(p));
        id = p.missionId(); p.begin(id, "Asharu"); p.settleColdStart(id, "Asharu", true, 22000, 0, true, true, true, true);
        assertFalse(AgreusArcCampaign.eligibleHistory(p));
        id = p.missionId(); p.begin(id, "Asharu"); p.settleWorkingPassage(id, "Asharu", true, 0, 0, true, false, false, "morrow-owned", 15);
        assertTrue(AgreusArcCampaign.eligibleHistory(p));
    }
    @Test void northWeirVictoryOpensTheBoundedWarrantWithoutOpeningLaterChapters() {
        var p = new AgreusArcProgress(); var m = AgreusArcCampaign.offer(p, "Agreus");
        assertEquals("Water Under Seal", m.name); assertEquals(26000, m.payout);
        assertEquals(OperationTier.ESTABLISHED, m.tier); assertEquals(MissionSource.STORY, m.source);
        assertEquals(FieldPresencePolicy.UNRESTRICTED, m.fieldPresencePolicy);
        assertTrue(m.requirements.contains("owned working construction tender"));
        p.begin(m.id, "agreus"); p.settle(m.id, "agreus", true, 26000, true, true, true, true, true, true, 0, 0, false);
        var warrant = AgreusArcCampaign.offer(p, "Agreus");
        assertEquals("Limited Warrant", warrant.name);
        assertEquals(30000, warrant.payout);
        assertEquals(MissionType.RAID, warrant.type);
        assertEquals(FieldPresencePolicy.UNRESTRICTED, warrant.fieldPresencePolicy);
        assertTrue(warrant.flavor.contains("Yard management and debt enforcement require a separate agreement"));
        assertTrue(p.begin(warrant.id, "agreus"));
        assertEquals(30000, p.settleWarrant(warrant.id, "agreus", true, 30000, true, true, true, true, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> AgreusArcCampaign.offer(p, "Agreus"));
    }
    @Test void missingAgreusHasNoFallbackAndAttachedMarketIdIsNotAssumed() {
        var other = stub(PlanetAPI.class, Map.of("getId", "asharu"));
        var system = stub(StarSystemAPI.class, Map.of("getPlanets", List.of(other), "getName", "Arcadia"));
        var sector = stub(SectorAPI.class, Map.of("getStarSystem", system));
        assertNull(AgreusArcCampaign.resolve(sector));
        PlanetAPI[] planet = new PlanetAPI[1];
        var faction = stub(FactionAPI.class, Map.of());
        var market = (MarketAPI) Proxy.newProxyInstance(MarketAPI.class.getClassLoader(), new Class<?>[]{MarketAPI.class},
                (proxy, method, args) -> method.getName().equals("getPlanetEntity") ? planet[0]
                        : value(method.getReturnType(), Map.of("getSize", 5, "getFactionId", "independent", "getFaction", faction).get(method.getName())));
        planet[0] = stub(PlanetAPI.class, Map.of("getId", "agreus", "getName", "Agreus", "getMarket", market));
        system = stub(StarSystemAPI.class, Map.of("getPlanets", List.of(planet[0]), "getName", "Arcadia"));
        sector = stub(SectorAPI.class, Map.of("getStarSystem", system));
        assertSame(planet[0], AgreusArcCampaign.resolve(sector).planet());
    }
    @Test void incompatibleOrInaccessibleMarketsPauseInsteadOfRelocating() {
        for (Map<String, Object> policy : List.of(Map.<String, Object>of("isHidden", true),
                Map.<String, Object>of("isPlayerOwned", true), Map.<String, Object>of("getFactionId", "hegemony"))) {
            assertNull(AgreusArcCampaign.resolve(agreus(policy, false)));
        }
        assertNull(AgreusArcCampaign.resolve(agreus(Map.of(), true)));
        assertNotNull(AgreusArcCampaign.resolve(agreus(Map.of(), false)));
    }
    private static SectorAPI agreus(Map<String, Object> policy, boolean hostile) {
        PlanetAPI[] planet = new PlanetAPI[1];
        var faction = stub(FactionAPI.class, Map.of("isHostileTo", hostile));
        var values = new HashMap<String, Object>();
        values.put("getSize", 5); values.put("getFactionId", "independent"); values.put("getFaction", faction); values.putAll(policy);
        var market = (MarketAPI) Proxy.newProxyInstance(MarketAPI.class.getClassLoader(), new Class<?>[]{MarketAPI.class},
                (proxy, method, args) -> method.getName().equals("getPlanetEntity") ? planet[0]
                        : value(method.getReturnType(), values.get(method.getName())));
        planet[0] = stub(PlanetAPI.class, Map.of("getId", "agreus", "getName", "Agreus", "getMarket", market));
        var system = stub(StarSystemAPI.class, Map.of("getPlanets", List.of(planet[0]), "getName", "Arcadia"));
        return stub(SectorAPI.class, Map.of("getStarSystem", system, "getPlayerFaction", faction));
    }
    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> value(method.getReturnType(), values.get(method.getName()))));
    }
    private static Object value(Class<?> type, Object value) {
        if (value != null) return value;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        return null;
    }
}
