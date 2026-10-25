package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectControlScatterTest {
    @Test
    void gunsHaveNoSpreadAndRocketsGrowOneOnTheirOwnButton() {
        var guns = new MechLoadoutComponent(MechVariant.BULWARK, MechWeaponComponent.CHAINGUN,
                null, null, MechRole.ARMORED_SUPPORT);
        assertTrue(DirectControlScatter.mech(guns, TriggerPicks.LINKED, FireTrigger.LEFT, 15f) < 0f);

        var rockets = new MechLoadoutComponent(MechVariant.BULWARK, MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_5, null, MechRole.ARMORED_SUPPORT);
        float range = rockets.mount(MechMountSlot.LEFT_SHOULDER).weaponDef().range;
        assertTrue(DirectControlScatter.mech(rockets, TriggerPicks.LINKED, FireTrigger.LEFT, range) < 0f,
                "the guns' button has no ground burst");
        float near = DirectControlScatter.mech(rockets, TriggerPicks.LINKED, FireTrigger.RIGHT, range * 0.5f);
        float atRange = DirectControlScatter.mech(rockets, TriggerPicks.LINKED, FireTrigger.RIGHT, range);
        float far = DirectControlScatter.mech(rockets, TriggerPicks.LINKED, FireTrigger.RIGHT, range * 1.5f);
        assertTrue(near > 0f && near < atRange);
        assertTrue(far > atRange * 1.5f, "past range the disk outgrows distance");
        assertEquals(DirectControlScatter.mech(rockets, TriggerPicks.LINKED, FireTrigger.RIGHT, range * 2f),
                DirectControlScatter.mech(rockets, TriggerPicks.LINKED, FireTrigger.RIGHT, range * 5f), 1e-5f,
                "a point past the ceiling lands at the ceiling");
    }

    @Test
    void aVehicleLauncherSpreadsLikeAMechRackAndAVehicleGunDoesNot() {
        WeaponDef lrm = VehicleType.LRM_CARRIER.turretStructure().mount.weapon;
        assertTrue(DirectControlScatter.vehicle(lrm, lrm.range * 0.6f) > 0f);
        assertTrue(DirectControlScatter.vehicle(lrm, lrm.range * 1.2f) > DirectControlScatter.vehicle(lrm, lrm.range));
        assertTrue(DirectControlScatter.vehicle(VehicleType.HEAVY_APC.turretStructure().mount.weapon, 15f) < 0f);
    }

    @Test
    void aBloomingTickOpensToTheLandingRadiusWithoutGrowingOrPassingItsCap() {
        var rest = DirectControlReticle.extent(-1f);
        var open = DirectControlReticle.extent(25f);
        var wide = DirectControlReticle.extent(500f);
        assertEquals(25f, open[0], 1e-5f, "the inner end sits on the landing radius");
        assertEquals(rest[1] - rest[0], open[1] - open[0], 1e-5f, "it keeps its length");
        assertEquals(DirectControlReticle.MAX_BLOOM_PX, wide[0], 1e-5f);
        assertArrayEquals(rest, DirectControlReticle.extent(2f), 1e-5f,
                "a disk inside the resting gap leaves the tick at rest");
    }
}
