package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The market-industry to ground-defence-signal mapping, on a fake market. */
class TargetProfileDefenceSignalsTest {

    private static MarketAPI market(String... industries) {
        Set<String> present = Set.of(industries);
        return (MarketAPI) Proxy.newProxyInstance(MarketAPI.class.getClassLoader(),
                new Class<?>[]{MarketAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasIndustry")) return present.contains((String) args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void emptyMarketHasNoSignals() {
        MarketAPI m = market();
        assertFalse(TargetProfileResolver.hasGroundDefences(m));
        assertFalse(TargetProfileResolver.hasHeavyBatteries(m));
        assertFalse(TargetProfileResolver.hasPlanetaryShield(m));
    }

    @Test
    void groundDefencesAloneIsTheNetworkOnly() {
        MarketAPI m = market(Industries.GROUNDDEFENSES);
        assertTrue(TargetProfileResolver.hasGroundDefences(m));
        assertFalse(TargetProfileResolver.hasHeavyBatteries(m));
        assertFalse(TargetProfileResolver.hasPlanetaryShield(m));
    }

    @Test
    void heavyBatteriesKeepTheNetworkAndAddTheBatteries() {
        MarketAPI m = market(Industries.HEAVYBATTERIES);
        assertTrue(TargetProfileResolver.hasGroundDefences(m));
        assertTrue(TargetProfileResolver.hasHeavyBatteries(m));
        assertFalse(TargetProfileResolver.hasPlanetaryShield(m));
    }

    @Test
    void planetaryShieldStandsAlone() {
        MarketAPI m = market(Industries.PLANETARYSHIELD);
        assertFalse(TargetProfileResolver.hasGroundDefences(m));
        assertFalse(TargetProfileResolver.hasHeavyBatteries(m));
        assertTrue(TargetProfileResolver.hasPlanetaryShield(m));
    }

    @Test
    void withDefencesRestatesOnlyTheThreeSignals() {
        TargetProfile base = new TargetProfile(5, 6, 3, 1, "hegemony", Set.of(), null, null, 40f, 4f);
        TargetProfile fort = base.withDefences(true, true, true);
        assertTrue(fort.groundDefences() && fort.heavyBatteries() && fort.planetaryShield());
        assertEquals(base.defenseLevel(), fort.defenseLevel());
        assertEquals(base.groundDefence(), fort.groundDefence());
        assertEquals("hegemony", fort.withFactionId("pirates").withFactionId("hegemony").factionId());
        assertTrue(fort.withFactionId("pirates").heavyBatteries(), "wither keeps the signals");
        assertFalse(TargetProfile.NEUTRAL.groundDefences());
    }
}
