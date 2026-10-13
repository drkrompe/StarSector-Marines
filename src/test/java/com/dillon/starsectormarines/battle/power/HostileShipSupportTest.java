package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostileShipSupportTest {

    private static TargetProfile world(int defenseLevel, String faction) {
        return new TargetProfile(6, 5, defenseLevel, 1, faction, Set.<EconomicFunction>of(),
                SurfacePalette.ROCK, SettlementLink.ROAD);
    }

    @Test
    void anUndefendedWorldBringsNoShip() {
        assertFalse(HostileShipSupport.warrants(world(0, "hegemony")));
        assertFalse(HostileShipSupport.warrants(world(HostileShipSupport.DEFENSE_LEVEL_THRESHOLD - 1, "hegemony")));
        assertFalse(HostileShipSupport.warrants(TargetProfile.NEUTRAL));
        assertFalse(HostileShipSupport.warrants(null));
    }

    @Test
    void aDefendedWorldAtTheThresholdBringsOne() {
        assertTrue(HostileShipSupport.warrants(world(HostileShipSupport.DEFENSE_LEVEL_THRESHOLD, "hegemony")));
        assertTrue(HostileShipSupport.warrants(world(7, "independent")));
    }

    @Test
    void ownersWithNoCapitalBringNone() {
        assertFalse(HostileShipSupport.warrants(world(7, "pirates")));
        assertFalse(HostileShipSupport.warrants(world(7, "luddic_path")));
    }

    @Test
    void theHullFollowsTheOwner() {
        assertEquals("onslaught", HostileShipSupport.hullFor("hegemony"));
        assertEquals("onslaught", HostileShipSupport.hullFor("independent"));
        assertEquals("onslaught", HostileShipSupport.hullFor(""));
        assertEquals("onslaught", HostileShipSupport.hullFor(null));
        assertEquals("paragon", HostileShipSupport.hullFor("tritachyon"));
        assertEquals("paragon", HostileShipSupport.hullFor("persean"));
    }

    @Test
    void theSourceIsAFrozenStockWarship() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        ShipSupportSource hegemony = HostileShipSupport.sourceFor(world(5, "hegemony"));
        assertNotNull(hegemony);
        assertEquals("onslaught", hegemony.hullId());
        assertFalse(hegemony.tender());
        assertFalse(hegemony.weapons().isEmpty());
        assertEquals("paragon", HostileShipSupport.sourceFor(world(5, "tritachyon")).hullId());
        assertNull(HostileShipSupport.sourceFor(world(1, "hegemony")));
    }

    @Test
    void noMarinesOnTheGroundMeansNoCall() {
        assertFalse(HostileShipSupport.shouldCall(false, 100000, 0f));
    }

    @Test
    void aMarineNearGroundTheDefenderMustHoldCallsIt() {
        assertTrue(HostileShipSupport.shouldCall(true, 0, HostileShipSupport.THREAT_RADIUS_CELLS));
        assertFalse(HostileShipSupport.shouldCall(true, 0, HostileShipSupport.THREAT_RADIUS_CELLS + 1f));
    }

    @Test
    void contactAloneCallsItAfterTheWait() {
        int wait = Math.round(HostileShipSupport.CONTACT_SECONDS / BattleSimulation.TICK_DT);
        assertFalse(HostileShipSupport.shouldCall(true, wait - 1, Float.MAX_VALUE));
        assertTrue(HostileShipSupport.shouldCall(true, wait, Float.MAX_VALUE));
    }

    @Test
    void theStationIsTheHeldGroundNearestTheMarines() {
        float[] west = {20f, 40f};
        float[] east = {120f, 40f};
        assertSame(east, HostileShipSupport.nearestHeld(List.of(west, east), 100f, 45f));
        assertSame(west, HostileShipSupport.nearestHeld(List.of(west, east), 30f, 45f));
        assertNull(HostileShipSupport.nearestHeld(List.of(), 30f, 45f));
    }
}
