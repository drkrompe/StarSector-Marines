package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The LRM carrier and the SRM technical: the mech anti-armor racks on hulls, each a card, a bill and a sheet. */
class MissileCarrierVehiclesTest {

    private static final List<VehicleType> CARRIERS = List.of(VehicleType.LRM_CARRIER, VehicleType.SRM_TECHNICAL);

    @Test
    void eachIsAnOfferedVehiclePatternAtItsTierAndLift() {
        assertEquals(EquipmentAccessTier.ADVANCED, MechPattern.of(VehicleType.LRM_CARRIER).accessTier());
        assertEquals(LiftClass.MEDIUM, VehicleType.LRM_CARRIER.liftClass());
        assertEquals(EquipmentAccessTier.COMMON, MechPattern.of(VehicleType.SRM_TECHNICAL).accessTier());
        assertEquals(LiftClass.LIGHT, VehicleType.SRM_TECHNICAL.liftClass());
        for (VehicleType type : CARRIERS) {
            MechPattern pattern = MechPattern.of(type);
            assertEquals(MechPattern.Kind.VEHICLE, pattern.kind());
            assertEquals(pattern, MechPattern.parse(pattern.id()), type.name());
            assertTrue(MechPattern.offerable().contains(pattern), type.name());
            assertNotNull(MechPatternCardItemPlugin.itemData(pattern.id()), type.name());
        }
    }

    /** The rocket pickup is a field rig; the carrier is an APC variant a navy that fields LRM-15s weights up. */
    @Test
    void theRigIsWeightedByWhoWeldsItAndTheCarrierByTheHegemony() {
        MechPattern rig = MechPattern.of(VehicleType.SRM_TECHNICAL);
        assertTrue(VehicleType.SRM_TECHNICAL.isPickup());
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT, MechPatternPicker.weight("pirates", rig));
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT, MechPatternPicker.weight("independent", rig));
        assertEquals(MechPatternPicker.BASE_WEIGHT, MechPatternPicker.weight("hegemony", rig));
        assertTrue(MechFabricationCatalog.vehicle(VehicleType.SRM_TECHNICAL).provenance().startsWith("FIELD RIG"));

        MechPattern carrier = MechPattern.of(VehicleType.LRM_CARRIER);
        assertTrue(VehicleType.LRM_CARRIER.isApc());
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT, MechPatternPicker.weight("hegemony", carrier));
        assertEquals(MechPatternPicker.BASE_WEIGHT, MechPatternPicker.weight("pirates", carrier));
    }

    /** A launcher bill is its hull's plus the mech racks it carries, and costs less than the class's mech. */
    @Test
    void eachBillIsItsHullPlusItsRacksAndCheaperThanItsClassMech() {
        assertEquals(credits(MechFabricationCatalog.vehicle(VehicleType.HEAVY_APC))
                        + 2 * credits(MechFabricationCatalog.weapon(MechWeaponComponent.LRM_15)),
                credits(MechFabricationCatalog.vehicle(VehicleType.LRM_CARRIER)), 0.5f);
        assertTrue(credits(MechFabricationCatalog.vehicle(VehicleType.SRM_TECHNICAL))
                < credits(MechFabricationCatalog.vehicle(VehicleType.MILITIA_TECHNICAL)), "a cheap rig");
        assertTrue(credits(MechFabricationCatalog.vehicle(VehicleType.LRM_CARRIER))
                < credits(MechFabricationCatalog.chassis(MechVariant.SIROCCO)));
    }

    /** Fragile, seatless, and holding fire at men: the rack is the whole hull. */
    @Test
    void eachCarriesAnAntiArmorRackAndPaysInSeatsAndPlate() {
        for (VehicleType type : CARRIERS) {
            assertEquals(0, type.capacity, type.name());
            assertTrue(type.hasTurretWeapon(), type.name());
            TurretMountDef mount = type.turretStructure().mount;
            assertTrue(mount.hardenedTargetsOnly, type.name());
            assertTrue(mount.weapon.contactDamage > 0f, type.name());
        }
        assertTrue(VehicleType.LRM_CARRIER.maxStructure + VehicleType.LRM_CARRIER.maxArmor
                < VehicleType.FIRE_SUPPORT_APC.maxStructure + VehicleType.FIRE_SUPPORT_APC.maxArmor);
        assertTrue(VehicleType.SRM_TECHNICAL.maxArmor < VehicleType.MILITIA_TECHNICAL.maxArmor);
        assertEquals(VehicleType.Stance.STANDOFF, VehicleType.LRM_CARRIER.engagementStance());
        assertEquals(VehicleType.Stance.AMBUSH, VehicleType.SRM_TECHNICAL.engagementStance());
    }

    /** The hull's racks are the mech's anti-armor racks, warhead and reach, on a turret mount. */
    @Test
    void theRacksFireTheMechAntiArmorWarheads() {
        // The carrier's two racks ripple as one salvo; the rig's pod is one rack.
        assertSameRack("weapon.mech-lrm-artillery", VehicleType.LRM_CARRIER.turretStructure().mount.weapon, 2);
        assertSameRack("weapon.mech-srm-pod", VehicleType.SRM_TECHNICAL.turretStructure().mount.weapon, 1);
        assertTrue(VehicleType.LRM_CARRIER.turretStructure().mount.weapon.indirectFire);
        assertFalse(VehicleType.SRM_TECHNICAL.turretStructure().mount.weapon.indirectFire);
    }

    @Test
    void eachDrawsHullLauncherAndWreckFromItsOwnSheet() throws Exception {
        for (VehicleType type : CARRIERS) {
            Path path = Path.of("mod", type.spritePath);
            assertTrue(Files.isRegularFile(path), path.toString());
            BufferedImage sheet = ImageIO.read(path.toFile());
            assertEquals(4, type.frameCount, type.name());
            int slot = sheet.getWidth() / type.frameCount;
            // The launcher frame is drawn at the hull's own scale, so it spans the hull frame.
            assertEquals(type.visualLengthCells, type.turretVisualCells, 1e-4f, type.name());
            for (int frame : new int[]{type.spriteFrame, type.turretFrame, type.wreckHullFrame(), type.wreckTurretFrame()}) {
                assertTrue(opaque(sheet, frame * slot, slot) > 0, type.name() + " frame " + frame + " is drawn");
            }
        }
    }

    private static void assertSameRack(String mechId, WeaponDef rack, int racks) {
        WeaponDef mech = WeaponRegistry.require(mechId);
        assertEquals(mech.range, rack.range, 1e-4f, rack.id);
        assertEquals(mech.contactDamage, rack.contactDamage, 1e-4f, rack.id);
        assertEquals(mech.contactPenetration, rack.contactPenetration, 1e-4f, rack.id);
        assertEquals(mech.burstCount * racks, rack.burstCount, rack.id);
        assertEquals(mech.cooldown, rack.cooldown, 1e-4f, rack.id);
    }

    private static float credits(MechFabricationCatalog.Recipe recipe) {
        return VehicleVariantLadderTest.credits(recipe.cost());
    }

    private static int opaque(BufferedImage sheet, int x0, int width) {
        int count = 0;
        for (int y = 0; y < sheet.getHeight(); y += 4) {
            for (int x = x0; x < x0 + width; x += 4) {
                if ((sheet.getRGB(x, y) >>> 24) > 128) count++;
            }
        }
        return count;
    }
}
