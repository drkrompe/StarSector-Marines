package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.LiftClass;
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

/** The supply truck and the scout car: company vehicles like the others, each paying for its one job. */
class LightRoleVehiclesTest {

    private static final List<VehicleType> ROLES = List.of(VehicleType.SUPPLY_TRUCK, VehicleType.SCOUT_CAR);

    @Test
    void eachIsAVehiclePatternAtAnAuthoredTierOfferedAsACard() {
        assertEquals(EquipmentAccessTier.COMMON, MechPattern.of(VehicleType.SUPPLY_TRUCK).accessTier());
        assertEquals(EquipmentAccessTier.ADVANCED, MechPattern.of(VehicleType.SCOUT_CAR).accessTier());
        for (VehicleType type : ROLES) {
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
    void bothRideALightLiftAndHaveABill() {
        for (VehicleType type : ROLES) {
            assertEquals(LiftClass.LIGHT, type.liftClass(), type.name());
            assertEquals(MechPattern.WeightClass.LIGHT, MechPattern.weightClass(type), type.name());
            MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.vehicle(type);
            assertNotNull(recipe, type.name());
            assertFalse(recipe.cost().lines().isEmpty(), type.name());
        }
    }

    @Test
    void aFactionWeightsUpEachRole() {
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT,
                MechPatternPicker.weight("hegemony", MechPattern.of(VehicleType.SUPPLY_TRUCK)));
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT,
                MechPatternPicker.weight("tritachyon", MechPattern.of(VehicleType.SCOUT_CAR)));
        assertEquals(MechPatternPicker.BASE_WEIGHT,
                MechPatternPicker.weight("independent", MechPattern.of(VehicleType.SCOUT_CAR)));
    }

    @Test
    void onlyTheTruckCarriesSupplyAndOnlyTheScoutIsASightSource() {
        for (VehicleType type : VehicleType.values()) {
            assertEquals(type == VehicleType.SUPPLY_TRUCK, type.supplyStock() > 0, type.name());
            assertEquals(type == VehicleType.SCOUT_CAR, type.sightCells() > 0f, type.name());
        }
    }

    @Test
    void eachGivesUpTheGunTheSeatsAndThePlate() {
        VehicleType technical = VehicleType.MILITIA_TECHNICAL;
        for (VehicleType type : ROLES) {
            assertNull(type.turretStructureId, type.name());
            assertFalse(type.hasTurretWeapon(), type.name());
            assertEquals(0, type.capacity, type.name());
            assertTrue(type.maxArmor < technical.maxArmor && type.armorRating < technical.armorRating, type.name());
        }
        // The scout's speed is its one strength: the fastest hull a bay can hold, and the thinnest.
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.vehicles()) {
            VehicleType other = recipe.vehicle();
            if (other == VehicleType.SCOUT_CAR) continue;
            assertTrue(VehicleType.SCOUT_CAR.maxSpeed > other.maxSpeed, other.name());
            assertTrue(VehicleType.SCOUT_CAR.maxStructure <= other.maxStructure, other.name());
        }
    }

    @Test
    void eachDrawsFromItsOwnSheetWithAHullAndAWreck() throws Exception {
        for (VehicleType type : ROLES) {
            assertFalse(type.spritePath.equals(VehicleType.MILITIA_TECHNICAL.spritePath), type.name());
            Path path = Path.of("mod", type.spritePath);
            assertTrue(Files.isRegularFile(path), path.toString());
            BufferedImage sheet = ImageIO.read(path.toFile());
            assertEquals(0, sheet.getWidth() % type.frameCount, type.name());
            int slot = sheet.getWidth() / type.frameCount;
            // The pixel scale is the technical's, so a shorter hull is a narrower frame, not a smaller drawing.
            assertEquals(VehicleType.MILITIA_TECHNICAL.visualLengthCells / 768f, type.visualLengthCells / slot,
                    1e-4f, type.name());
            assertEquals(-1, type.turretFrame, type.name());
            for (int frame : new int[]{type.spriteFrame, type.wreckHullFrame()}) {
                assertTrue(opaque(sheet, frame * slot, slot) > slot * sheet.getHeight() / 20,
                        type.name() + " frame " + frame + " is drawn");
            }
        }
    }

    private static int opaque(BufferedImage sheet, int x0, int width) {
        int count = 0;
        for (int y = 0; y < sheet.getHeight(); y += 2) {
            for (int x = x0; x < x0 + width; x += 2) {
                if ((sheet.getRGB(x, y) >>> 24) > 128) count += 4;
            }
        }
        return count;
    }
}
