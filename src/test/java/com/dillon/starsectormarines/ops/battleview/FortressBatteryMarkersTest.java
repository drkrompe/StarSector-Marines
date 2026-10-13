package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.battery.FortressBatteryService;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the player is shown of a battery: the envelope while it can fire, the contest while it cannot, nothing once it is gone. */
class FortressBatteryMarkersTest {

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
    }

    private static StructureDef def() {
        return TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID);
    }

    @Test
    void aLiveHostileBatteryShowsItsEnvelope() {
        FortressBatteryService service = new FortressBatteryService();
        FortressBattery b = service.register(1L, def(), 40f, 40f, Faction.DEFENDER);
        List<FortressBatteryMarkers.Marker> markers = FortressBatteryMarkers.forBatteries(service, Faction.MARINE);
        assertEquals(1, markers.size());
        assertEquals(FortressBatteryMarkers.Kind.ENVELOPE, markers.get(0).kind());
        assertTrue(markers.get(0).hostile());
        assertEquals(b.envelopeCells(), markers.get(0).radius(), 1e-4f);
    }

    @Test
    void aContestedBatteryShowsTheFireControlPointInsteadOfTheEnvelope() {
        FortressBatteryService service = new FortressBatteryService();
        FortressBattery b = service.register(1L, def(), 40f, 40f, Faction.DEFENDER);
        b.fireControl().tick(1f, false, Faction.MARINE);
        b.fireControl().tick(1f, false, Faction.MARINE);
        List<FortressBatteryMarkers.Marker> markers = FortressBatteryMarkers.forBatteries(service, Faction.MARINE);
        assertEquals(1, markers.size());
        assertEquals(FortressBatteryMarkers.Kind.CONTEST, markers.get(0).kind());
        assertTrue(markers.get(0).progress() > 0f);
    }

    @Test
    void aDestroyedBatteryShowsNothing() {
        FortressBatteryService service = new FortressBatteryService();
        service.register(1L, def(), 40f, 40f, Faction.DEFENDER).markDestroyed();
        assertTrue(FortressBatteryMarkers.forBatteries(service, Faction.MARINE).isEmpty());
    }
}
