package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechBayVehicleTest {

    private static final String SQUAD = MechBay.STARTER_SQUAD_ID;

    @Test
    void anUnlearnedVehiclePatternRefusesFabricationWithoutSpending() {
        MechBay bay = new MechBay();
        MechWorkshopTest.TestResources resources = MechWorkshopTest.TestResources.stocked(1000);
        MechWorkshop workshop = new MechWorkshop(bay, resources);

        assertEquals(MechWorkshop.Status.PATTERN_LOCKED,
                workshop.fabricateVehicle(SQUAD, VehicleType.HEAVY_APC).status());
        assertEquals(1000, resources.available(Commodities.SUPPLIES));
        assertEquals(0, bay.activeSquad().occupied());
    }

    @Test
    void aLearnedPatternFabricatesOntoAVacantGantryForItsBill() {
        MechBay bay = new MechBay();
        assertTrue(bay.learnPattern(MechPattern.of(VehicleType.HEAVY_APC).id()));
        MechWorkshopTest.TestResources resources = MechWorkshopTest.TestResources.stocked(1000);

        MechWorkshop.Result result = new MechWorkshop(bay, resources)
                .fabricateVehicle(SQUAD, VehicleType.HEAVY_APC);

        assertEquals(MechWorkshop.Status.VEHICLE_FABRICATED, result.status());
        assertTrue(result.succeeded());
        assertNull(result.mech());
        CampaignVehicle vehicle = result.vehicle();
        assertEquals(VehicleType.HEAVY_APC, vehicle.type());
        assertEquals(vehicle, bay.vehicleById(vehicle.id()));
        int supplies = MechFabricationCatalog.vehicle(VehicleType.HEAVY_APC).cost().lines().stream()
                .filter(line -> line.commodityId().equals(Commodities.SUPPLIES))
                .mapToInt(FabricationCost.Line::quantity).sum();
        assertEquals(1000 - supplies, resources.available(Commodities.SUPPLIES));
    }

    @Test
    void mechsAndVehiclesShareTheFourGantries() {
        MechBay bay = MechBay.legacyStarterFixture();
        bay.learnPattern(MechPattern.of(VehicleType.BREAKTHROUGH_TANK).id());
        MechWorkshop workshop = new MechWorkshop(bay, MechWorkshopTest.TestResources.stocked(5000));

        assertTrue(workshop.fabricateChassis(SQUAD, MechVariant.HOUND).succeeded());
        assertTrue(workshop.fabricateVehicle(SQUAD, VehicleType.BREAKTHROUGH_TANK).succeeded());
        assertTrue(workshop.fabricateVehicle(SQUAD, VehicleType.BREAKTHROUGH_TANK).succeeded());

        assertEquals(4, bay.activeSquad().occupied());
        assertEquals(MechWorkshop.Status.LANCE_FULL,
                workshop.fabricateVehicle(SQUAD, VehicleType.BREAKTHROUGH_TANK).status());
        assertEquals(MechWorkshop.Status.LANCE_FULL,
                workshop.fabricateChassis(SQUAD, MechVariant.HOUND).status());
        // The bought Hound is a bare frame and stays in its gantry; the starter walker drops.
        assertEquals(1, bay.activeDeployment().size());
        assertEquals(2, bay.activeVehicleDeployment().size());
    }

    @Test
    void theFrozenManifestCarriesIdentityNotTheCampaignObject() {
        MechBay bay = new MechBay();
        CampaignVehicle apc = new CampaignVehicle("support_vehicle_07", "Mule", VehicleType.ESCORT_APC);
        assertTrue(bay.addVehicle(SQUAD, apc));

        assertEquals(List.of(new VehicleDeploymentSpec("support_vehicle_07", "Mule", VehicleType.ESCORT_APC)),
                bay.activeVehicleDeployment());
    }

    @Test
    void aDestroyedVehicleLeavesItsGantryAndUnknownIdsAreIgnored() {
        MechBay bay = new MechBay();
        bay.addVehicle(SQUAD, new CampaignVehicle("v1", "One", VehicleType.HEAVY_APC));
        bay.addVehicle(SQUAD, new CampaignVehicle("v2", "Two", VehicleType.HEAVY_APC));

        assertEquals(1, bay.loseVehicles(List.of("v1", "debug-nobody", "v1")));

        assertNull(bay.vehicleById("v1"));
        assertNotNull(bay.vehicleById("v2"));
        assertEquals(1, bay.activeSquad().occupied());
    }

    @Test
    void fabricatedVehiclesTakeFreshSerials() {
        MechBay bay = new MechBay();
        bay.learnPattern(MechPattern.of(VehicleType.HEAVY_APC).id());
        CampaignVehicle first = bay.fabricateVehicle(SQUAD, VehicleType.HEAVY_APC);
        bay.loseVehicles(List.of(first.id()));
        CampaignVehicle second = bay.fabricateVehicle(SQUAD, VehicleType.HEAVY_APC);

        assertEquals("support_vehicle_01", first.id());
        assertEquals("support_vehicle_02", second.id());
    }

    @Test
    void anOlderSaveKeepsEveryGantryAMechAndGainsAVehicleList() throws Exception {
        MechBay bay = MechBay.legacyStarterFixture();
        Field vehicles = CampaignMechSquad.class.getDeclaredField("vehicles");
        vehicles.setAccessible(true);
        vehicles.set(bay.activeSquad(), null);

        MechBay restored = roundTrip(bay);

        assertEquals(1, restored.activeSquad().mechs().size());
        assertTrue(restored.activeSquad().vehicles().isEmpty());
        assertEquals(1, restored.activeSquad().occupied());
    }

    @Test
    void aMixedBayRoundTrips() throws Exception {
        MechBay bay = MechBay.legacyStarterFixture();
        bay.addVehicle(SQUAD, new CampaignVehicle("v1", "Ox", VehicleType.BREAKTHROUGH_TANK));

        MechBay restored = roundTrip(bay);

        assertEquals(MechBay.STARTER_MECH_ID, restored.activeSquad().mechs().get(0).id());
        assertEquals(VehicleType.BREAKTHROUGH_TANK, restored.vehicleById("v1").type());
        assertEquals("Ox", restored.vehicleById("v1").displayName());
    }

    private static MechBay roundTrip(MechBay bay) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bay);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MechBay) in.readObject();
        }
    }
}
