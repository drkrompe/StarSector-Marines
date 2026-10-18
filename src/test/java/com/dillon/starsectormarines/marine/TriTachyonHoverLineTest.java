package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.drone.DroneRole;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tri-Tachyon's hover line: its cards, tiers, lift, bills, faction weighting, roles and sheets. */
class TriTachyonHoverLineTest {

    private static final List<VehicleType> LINE = List.of(VehicleType.HOVER_SCOUT, VehicleType.DRONE_SKIMMER);

    @Test
    void eachIsAnOfferedVehiclePatternAtAnAuthoredTier() {
        assertEquals(EquipmentAccessTier.ADVANCED, MechPattern.of(VehicleType.HOVER_SCOUT).accessTier());
        assertEquals(EquipmentAccessTier.PRESTIGE, MechPattern.of(VehicleType.DRONE_SKIMMER).accessTier());
        for (VehicleType type : LINE) {
            MechPattern pattern = MechPattern.of(type);
            assertEquals(MechPattern.Kind.VEHICLE, pattern.kind());
            assertEquals(pattern, MechPattern.parse(pattern.id()), type.name());
            assertFalse(pattern.starter(), type.name());
            assertTrue(type.presentationDeclared(), type.name());
            assertTrue(MechPattern.offerable().contains(pattern), type.name());
            assertNotNull(MechPatternCardItemPlugin.itemData(pattern.id()), type.name());
        }
    }

    @Test
    void theScoutRidesALightCradleAndTheCarrierAMediumOne() {
        assertEquals(LiftClass.LIGHT, VehicleType.HOVER_SCOUT.liftClass());
        assertEquals(LiftClass.MEDIUM, VehicleType.DRONE_SKIMMER.liftClass());
        for (VehicleType type : LINE) {
            assertEquals(type.liftClass().name(), MechPattern.weightClass(type).name(), type.name());
        }
    }

    /** A purpose-built corporate hull: Tri-Tachyon weights it up, the outfits that weld pickups do not. */
    @Test
    void triTachyonWeightsItsHoverLineAndNotTheTank() {
        for (VehicleType type : LINE) {
            MechPattern pattern = MechPattern.of(type);
            assertFalse(type.isPickup(), type.name());
            assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT, MechPatternPicker.weight("tritachyon", pattern));
            for (String other : List.of("pirates", "independent", "hegemony", "lions_guard", "luddic_path")) {
                assertEquals(MechPatternPicker.BASE_WEIGHT, MechPatternPicker.weight(other, pattern), other + " " + type);
            }
            assertEquals("TRI-TACHYON PATTERN", MechFabricationCatalog.vehicle(type).provenance(), type.name());
        }
        assertEquals(MechPatternPicker.BASE_WEIGHT,
                MechPatternPicker.weight("tritachyon", MechPattern.of(VehicleType.HEAVY_GUN_TANK)));
    }

    /** Lift fans, sensors and drone control are rare metals; the skimmer costs more than the scout it is built on. */
    @Test
    void billsLeanOnRareMetalsAndTheCarrierCostsMore() {
        int scout = total(VehicleType.HOVER_SCOUT);
        int skimmer = total(VehicleType.DRONE_SKIMMER);
        assertTrue(skimmer > scout);
        assertTrue(rareMetals(VehicleType.HOVER_SCOUT) > rareMetals(VehicleType.SCOUT_CAR));
        assertTrue(rareMetals(VehicleType.DRONE_SKIMMER) > rareMetals(VehicleType.HOVER_SCOUT));
    }

    @Test
    void theScoutScreensAndSeesAndTheCarrierFightsWithItsDrones() {
        VehicleType scout = VehicleType.HOVER_SCOUT;
        assertEquals(TurretCatalogRegistry.HOVER_PULSE_STRUCTURE_ID, scout.turretStructureId);
        assertTrue(scout.hasTurretWeapon());
        assertTrue(scout.sightCells() > 0f);
        assertEquals(List.of(DroneRole.SENSOR, DroneRole.SENSOR), scout.droneBay(), "a sensor pair, nothing armed");

        VehicleType skimmer = VehicleType.DRONE_SKIMMER;
        assertNull(skimmer.turretStructureId);
        assertFalse(skimmer.hasTurretWeapon());
        assertEquals(List.of(DroneRole.ESCORT, DroneRole.ESCORT, DroneRole.HALO, DroneRole.HALO, DroneRole.STRIKE),
                skimmer.droneBay());
        assertEquals(0f, skimmer.sightCells());

        // Both pay in plate: thinner than the technical, and neither carries a squad.
        for (VehicleType type : LINE) {
            assertEquals(0, type.capacity, type.name());
            assertTrue(type.maxArmor < VehicleType.MILITIA_TECHNICAL.maxArmor, type.name());
            assertTrue(type.armorRating < VehicleType.MILITIA_TECHNICAL.armorRating, type.name());
        }
        // The stripped pickup stays the fastest hull; the hover scout is its armed, water-crossing sidegrade.
        assertTrue(scout.maxSpeed < VehicleType.SCOUT_CAR.maxSpeed);
        assertTrue(scout.maxStructure > VehicleType.SCOUT_CAR.maxStructure);
    }

    @Test
    void eachDrawsFromItsOwnFourSlotSheet() throws Exception {
        for (VehicleType type : LINE) {
            Path path = Path.of("mod", type.spritePath);
            assertTrue(Files.isRegularFile(path), path.toString());
            BufferedImage sheet = ImageIO.read(path.toFile());
            assertEquals(4, type.frameCount, type.name());
            int slot = sheet.getWidth() / type.frameCount;
            assertEquals(0, sheet.getWidth() % type.frameCount, type.name());
            // The hull frame spans the hull's authored length at the sheets' shared 256 px per cell.
            assertEquals(type.visualLengthCells * 256f, slot, .5f, type.name());
            assertTrue(opaque(sheet, type.spriteFrame * slot, slot) > 0, type.name() + " hull");
            assertTrue(opaque(sheet, type.wreckHullFrame() * slot, slot) > 0, type.name() + " wreck");
            if (type.turretFrame >= 0) {
                assertTrue(opaque(sheet, type.turretFrame * slot, slot) > 0, type.name() + " turret");
                assertEquals(type.visualLengthCells, type.turretVisualCells, 1e-6f, "turret drawn at hull scale");
            }
        }
    }

    private static int opaque(BufferedImage sheet, int x0, int width) {
        int count = 0;
        for (int y = 0; y < sheet.getHeight(); y += 4) {
            for (int x = x0; x < x0 + width; x += 4) if ((sheet.getRGB(x, y) >>> 24) > 128) count++;
        }
        return count;
    }

    private static int total(VehicleType type) {
        return MechFabricationCatalog.vehicle(type).cost().lines().stream().mapToInt(FabricationCost.Line::quantity).sum();
    }

    private static int rareMetals(VehicleType type) {
        return MechFabricationCatalog.vehicle(type).cost().lines().stream()
                .filter(line -> line.commodityId().equals("rare_metals")).mapToInt(FabricationCost.Line::quantity).sum();
    }
}
