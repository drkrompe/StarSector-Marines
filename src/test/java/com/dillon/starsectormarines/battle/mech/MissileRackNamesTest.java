package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The number in a missile weapon's name is the missiles it holds ready (`mechs-nouns.md`, Missile racks). */
class MissileRackNamesTest {

    private static final Pattern COUNT = Pattern.compile("[SL]RM-(\\d+)");

    @Test
    void aMechRackIsNamedForItsBuffer() {
        for (MechWeaponComponent rack : MechWeaponComponent.values()) {
            if (rack.hardpointType != MechWeaponComponent.HardpointType.MISSILE) continue;
            assertEquals(count(rack.displayName()), rack.readyRounds(), rack.displayName());
        }
    }

    @Test
    void aTurretRackIsNamedForItsMagazine() {
        for (String id : new String[]{TurretCatalogRegistry.VEHICLE_SRM_STRUCTURE_ID,
                TurretCatalogRegistry.VEHICLE_LRM_STRUCTURE_ID, TurretCatalogRegistry.BOAT_SRM_STRUCTURE_ID,
                TurretCatalogRegistry.BOAT_LRM_STRUCTURE_ID}) {
            StructureDef structure = TurretCatalogRegistry.requireStructure(id);
            assertEquals(count(structure.displayName), structure.mount.ammoCapacity, structure.displayName);
            assertEquals(count(structure.displayName), count(structure.mount.weapon.displayName()), id);
        }
    }

    private static int count(String name) {
        Matcher matcher = COUNT.matcher(name);
        assertTrue(matcher.find(), name + " names no rack size");
        return Integer.parseInt(matcher.group(1));
    }
}
