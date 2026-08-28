package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfantryEquipmentPersistenceMigrationTest {

    @Test
    void historicalEnumNamesPreservePrimaryAndRocketIssue() throws Exception {
        MarineSoldier soldier = new MarineSoldier("legacy", "Legacy", SoldierAptitude.STEADY);
        set(soldier, "primaryId", null);
        set(soldier, "primary", "PULSE_RIFLE");
        set(soldier, "specialEquipmentId", null);
        set(soldier, "secondary", "ROCKET_LAUNCHER");

        resolve(soldier);

        assertEquals(WeaponRegistry.PULSE_RIFLE_ID, soldier.primaryId());
        assertEquals(WeaponRegistry.PULSE_RIFLE_ID, soldier.primaryDef().id);
        assertEquals(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID,
                soldier.specialEquipmentId());
        assertEquals(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID,
                soldier.specialEquipmentDef().id());
    }

    @Test
    void removedProviderRepairsPrimaryAndClearsSpecial() throws Exception {
        MarineSoldier soldier = new MarineSoldier("removed", "Removed", SoldierAptitude.STEADY);
        set(soldier, "primaryId", "removed.weapon-primary");
        set(soldier, "specialEquipmentId", "removed.special-equipment");

        resolve(soldier);

        assertEquals(WeaponRegistry.STARTER_PRIMARY_ID, soldier.primaryId());
        assertNull(soldier.specialEquipmentId());
        assertNull(soldier.specialEquipmentDef());
    }

    @Test
    void historicalFireTeamTemplateNamesMigrateToStableIds() throws Exception {
        FireTeamBillet billet = new FireTeamBillet("Gunner",
                WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE,
                null, MarineArmorPattern.ARMORLESS.id);
        set(billet, "primaryId", null);
        set(billet, "primary", "DMR");
        set(billet, "specialEquipmentId", null);
        set(billet, "secondary", "ROCKET_LAUNCHER");

        resolve(billet);

        assertEquals(WeaponRegistry.DMR_ID, billet.primaryId());
        assertEquals(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID,
                billet.specialEquipmentId());
    }

    @Test
    void legacyRecipeKeysMoveToStableCatalogIds() throws Exception {
        MarineArmory armory = new MarineArmory();
        Set<String> recipes = new HashSet<>(List.of(
                "primary:DMR:MASTERWORK", "secondary:ROCKET_LAUNCHER"));
        set(armory, "unlockedRecipes", recipes);

        resolve(armory);

        assertTrue(armory.unlockedRecipes().contains(MarineArmory.primaryKey(
                WeaponRegistry.DMR_ID, EquipmentGrade.MASTERWORK)));
        assertTrue(armory.unlockedRecipes().contains(MarineArmory.secondaryKey(
                SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)));
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void resolve(Object target) throws Exception {
        Method method = target.getClass().getDeclaredMethod("readResolve");
        method.setAccessible(true);
        method.invoke(target);
    }
}
