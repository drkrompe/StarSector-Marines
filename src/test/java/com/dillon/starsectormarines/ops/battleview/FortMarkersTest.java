package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.battery.FortressBatteryService;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortService;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the player is told of what a fort's aspects are doing to its guns. */
class FortMarkersTest {

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
    }

    private static Fort fort(FortressBatteryService batteries, FortService service) {
        FortressBattery battery = batteries.register(1L,
                TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID),
                40f, 40f, Faction.DEFENDER);
        service.tagPlatform(0, battery);
        service.tagAspect(0, FortAspect.FIRE_CONTROL, 2L, Faction.DEFENDER);
        service.tagAspect(0, FortAspect.MAGAZINE, 3L, Faction.DEFENDER);
        service.tagAspect(0, FortAspect.POWER, 4L, Faction.DEFENDER);
        return service.forts().get(0);
    }

    private static void take(Fort.Place place, Faction taker) {
        for (int i = 0; i < 4000 && place.control().holder() != taker; i++) place.control().tick(0.1f, false, taker);
    }

    @Test
    void aFortWithEverythingInOrderHasNothingToSay() {
        Fort fort = fort(new FortressBatteryService(), new FortService());
        assertEquals("", FortMarkers.status(fort, fort.battery()));
    }

    @Test
    void aMagazineTheGunsDoNotHoldRationsThemAndSaysSo() {
        Fort fort = fort(new FortressBatteryService(), new FortService());
        take(fort.place(FortAspect.MAGAZINE), Faction.MARINE);
        assertEquals("RATIONED 12", FortMarkers.status(fort, fort.battery()));
        while (fort.takeRound(Faction.DEFENDER)) { /* fire them out */ }
        assertEquals("OUT OF ROUNDS", FortMarkers.status(fort, fort.battery()));
    }

    @Test
    void powerTheGunsDoNotHoldIsReportedAlongsideTheMagazine() {
        Fort fort = fort(new FortressBatteryService(), new FortService());
        take(fort.place(FortAspect.POWER), Faction.MARINE);
        assertEquals("UNPOWERED", FortMarkers.status(fort, fort.battery()));
        take(fort.place(FortAspect.MAGAZINE), Faction.MARINE);
        assertEquals("RATIONED 12 / UNPOWERED", FortMarkers.status(fort, fort.battery()));
    }

    @Test
    void nothingIsShownForAPartOfAFortThatHasNotBeenStamped() {
        FortService service = new FortService();
        fort(new FortressBatteryService(), service);
        assertTrue(FortMarkers.forForts(service, Faction.MARINE).stream()
                        .noneMatch(m -> m.kind() != FortMarkers.Kind.STATUS),
                "an aspect place is drawn only once its building has been found");
        assertTrue(FortMarkers.forForts(null, Faction.MARINE).isEmpty());
    }
}
