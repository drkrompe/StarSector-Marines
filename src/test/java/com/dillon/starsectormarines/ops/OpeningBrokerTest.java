package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpeningBrokerTest {
    @Test void jangalaWinsOverIndependentMarketsAndEnumerationOrder() {
        MarketAPI jangala = market("jangala", "hegemony", false, false, false, true);
        MarketAPI other = market("a-independent", "independent", false, false, false, true);
        assertEquals("jangala", OpeningBroker.resolve(sector(List.of(other, jangala))).planetId());
        assertEquals("jangala", OpeningBroker.resolve(sector(List.of(jangala, other))).planetId());
    }

    @Test void unavailableJangalaUsesStableAccessiblePlanetaryOffice() {
        MarketAPI hostile = market("jangala", "hegemony", true, false, false, true);
        MarketAPI independent = market("b-independent", "independent", false, false, false, true);
        MarketAPI government = market("a-government", "hegemony", false, false, false, true);
        MarketAPI station = market("a-station", "independent", false, false, false, false);
        MarketAPI hidden = market("a-hidden", "independent", false, true, false, true);
        MarketAPI owned = market("a-owned", "independent", false, false, true, true);
        assertEquals("b-independent", OpeningBroker.resolve(sector(
                List.of(hostile, government, station, hidden, owned, independent))).planetId());
        assertEquals("a-government", OpeningBroker.resolve(sector(List.of(hostile, government))).planetId());
        assertNull(OpeningBroker.resolve(sector(List.of(hostile, station, hidden, owned))));
        assertNull(OpeningBroker.resolve(null));
    }

    @Test void officeIdentityDoesNotChangeFactionOrClientAccess() {
        Client base = new Client("independent", "Independent", null, RepLevel.NEUTRAL, false, null);
        Client broker = OpeningBroker.present(base, true);
        assertEquals(OpeningBroker.NAME, broker.displayName);
        assertEquals(base.identity(), broker.identity());
        assertEquals(base.factionId, broker.factionId);
        assertEquals(-1, broker.patronHouseId);
        assertTrue(OpeningBroker.isContact(broker));
        assertSame(base, OpeningBroker.present(base, false));
        Client house = new Client("independent", "House", null, RepLevel.NEUTRAL, false, null, 99L);
        assertSame(house, OpeningBroker.present(house, true));
    }

    @Test void referralAndHqAgreeAndCompletedSavesNeedNoNewFlag() {
        MarineRoster roster = new MarineRoster();
        Client independent = new Client("independent", "Independent", null, RepLevel.NEUTRAL, false, null);
        OpeningBroker.Destination office = OpeningBroker.resolve(sector(List.of(
                market("jangala", "hegemony", false, false, false, true))));
        assertTrue(OpeningBroker.referral(independent, roster, office).contains(office.label()));
        OpeningWorkGuide guide = OpeningWorkGuide.from(roster, office, () -> { });
        assertEquals(office.label(), guide.markupProperty("location"));
        assertEquals(false, guide.markupProperty("courseDisabled"));
        assertEquals(true, OpeningWorkGuide.from(roster, null, () -> { }).markupProperty("courseDisabled"));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertEquals("", OpeningBroker.referral(independent, roster, office));
        assertFalse(OpeningWorkGuide.from(roster, office, () -> { }).visible());
    }

    static PlanetAPI planet(String id, String name) {
        return stub(PlanetAPI.class, Map.of("getId", id, "getName", name,
                "getStarSystem", stub(StarSystemAPI.class, Map.of("getBaseName", "Corvus"))));
    }

    private static MarketAPI market(String id, String factionId, boolean hostile,
                                    boolean hidden, boolean playerOwned, boolean planetary) {
        FactionAPI faction = stub(FactionAPI.class, Map.of("getId", factionId, "isHostileTo", hostile));
        Map<String, Object> values = new HashMap<>(Map.of("getId", id, "getSize", 4,
                "getFaction", faction, "getFactionId", factionId, "isHidden", hidden, "isPlayerOwned", playerOwned));
        if (planetary) values.put("getPlanetEntity", planet(id, id));
        return stub(MarketAPI.class, values);
    }

    private static SectorAPI sector(List<MarketAPI> markets) {
        return stub(SectorAPI.class, Map.of("getEconomy", stub(EconomyAPI.class, Map.of("getMarketsCopy", markets)),
                "getPlayerFaction", stub(FactionAPI.class, Map.of("getId", "player"))));
    }

    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> values.get(method.getName())));
    }
}
