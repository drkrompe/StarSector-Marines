package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A destroyed mount is an absent mount to every reader, and is recorded for the campaign. */
class MechMountLossTest {

    @Test
    void destroyingTheLeftShoulderSilencesItAndRecordsTheLostComponent() {
        MechVariant variant = MechVariant.HOUND;
        MechLoadoutComponent loadout = variant.createLoadout(null);
        MechSections sections = loadout.sectionsFor(variant.maxStructure, variant.maxStructure,
                variant.armorCapacity, variant.armorCapacity);
        float armsRange = loadout.mount(MechMountSlot.ARMS).weaponDef().range;
        assertTrue(loadout.maxWeaponRange() >= armsRange);

        MechWeaponComponent lost = loadout.destroyMount(MechMountSlot.LEFT_SHOULDER);

        assertSame(MechWeaponComponent.SRM_5, lost);
        assertNull(loadout.mount(MechMountSlot.LEFT_SHOULDER));
        assertEquals(LayeredMechAppearance.POD_NONE, loadout.appearanceSelector(MechMountSlot.LEFT_SHOULDER));
        assertEquals(armsRange, loadout.maxWeaponRange(), 1e-4f, "only the nose gun's band is left");
        assertSame(MechWeaponComponent.SRM_5, sections.lostMount(MechMountSlot.LEFT_SHOULDER));
        assertSame(MechWeaponComponent.SRM_5,
                sections.report().lostMounts()[MechMountSlot.LEFT_SHOULDER.ordinal()]);
        assertNull(sections.lostMount(MechMountSlot.ARMS));
    }

    @Test
    void anEmptyArmsMountSaysNothingAndDestroyingAnEmptySlotLosesNothing() {
        MechLoadoutComponent loadout = MechVariant.HOUND.createLoadout(null);

        assertNull(loadout.destroyMount(MechMountSlot.RIGHT_SHOULDER));
        loadout.destroyMount(MechMountSlot.ARMS);
        loadout.destroyMount(MechMountSlot.LEFT_SHOULDER);

        assertEquals(LayeredMechAppearance.ARMS_NONE, loadout.appearanceSelector(MechMountSlot.ARMS));
        assertEquals(0f, loadout.maxWeaponRange());
    }
}
