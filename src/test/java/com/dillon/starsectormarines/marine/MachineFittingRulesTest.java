package com.dillon.starsectormarines.marine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.vehicle.VehicleGunCard;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Space at an install point, and what a bought machine stands with, on every mech and vehicle. */
class MachineFittingRulesTest {

    private static SocketDef point(SocketType type, int columns, int rows) {
        return new SocketDef(SocketId.ARMS, type, columns, rows, 0f, 0f, 0f, 0f, 1f, 1f, false);
    }

    @Test
    void everyMachineRequiresAnEngineAndAnArmedOneABank() {
        MechInternal.Role engine = MechInternal.Role.ENGINE, bank = MechInternal.Role.CAPACITOR_BANK;
        assertEquals(List.of(engine, bank), new MachineCapabilities(true, false).required());
        assertEquals(List.of(engine), new MachineCapabilities(false, false).required());
    }

    @Test
    void aWeaponPointIsTwoColumnsAndOneToFourRows() {
        for (int rows = 1; rows <= 4; rows++) assertEquals(rows, point(SocketType.OMNI, 2, rows).gridRows());
        assertThrows(IllegalArgumentException.class, () -> point(SocketType.OMNI, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> point(SocketType.BALLISTIC, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> point(SocketType.MISSILE, 3, 2));
    }

    @Test
    void aPartNeedsTheWholeOfItsFootprintAndNeverTurns() {
        assertFalse(point(SocketType.OMNI, 2, 1).accommodates(2, 2), "a 2x2 does not go in one row");
        assertTrue(point(SocketType.OMNI, 2, 1).accommodates(2, 1));
        assertTrue(point(SocketType.OMNI, 2, 4).accommodates(2, 3), "a smaller part goes in a larger point");
        assertFalse(point(SocketType.OMNI, 2, 1).accommodates(1, 2), "a part never turns to fit");
    }

    @Test
    void aOneRowGunCardRefusesATwoRowWeapon() {
        assertEquals(2, MechWeaponComponent.CHAINGUN.footprintRows);
        assertFalse(VehicleGunCard.SMALL.accepts(MechWeaponComponent.CHAINGUN));
        assertTrue(VehicleGunCard.SMALL.accepts(MechWeaponComponent.LIGHT_MACHINE_GUN));
    }

    @Test
    void everyWeaponPointIsAuthoredToTheRule() {
        for (MechVariant variant : MechVariant.values()) {
            for (SocketDef socket : MechFittingLayout.forVariant(variant).sockets()) {
                if (socket.type().weaponPoint()) assertEquals(MechFittingLayout.WEAPON_POINT_COLUMNS, socket.gridColumns());
            }
        }
    }

    @Test
    void aBoughtMechHasNothingFittedAtAnyPoint() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateFrame(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);

        for (MechMountSlot slot : MechMountSlot.values()) assertNull(mech.weaponAt(slot));
        assertNull(mech.internal(MechInternal.Role.ENGINE));
        assertNull(mech.internal(MechInternal.Role.PLATING));
        assertTrue(mech.compartment().isEmpty());
        assertEquals(CampaignMech.Readiness.INCOMPLETE, mech.readiness(0f));
        assertEquals(0, bay.ownedInternal(MechInternal.FACTORY_PLATING.id));
    }

    @Test
    void aBoughtHullHasNothingFittedAtAnyPoint() {
        for (VehicleType type : VehicleType.values()) {
            CampaignVehicle hull = CampaignVehicle.hull("hull", "Hull", type);
            assertNull(hull.gun());
            assertNull(hull.engine());
            assertNull(hull.plating());
            assertTrue(hull.compartment().isEmpty());
            assertFalse(hull.complete());
            assertTrue(hull.freezeForDeployment().noOwnGun());
        }
    }

    @Test
    void aBoughtMachineTakesItsFirstPlateAtTheArmorPoint() {
        MechBay bay = new MechBay();
        bay.addInternal(MechInternal.FACTORY_PLATING.id, 2);
        CampaignMech mech = bay.fabricateFrame(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        CampaignVehicle hull = CampaignVehicle.hull("hull", "Hull", VehicleType.HEAVY_APC);
        bay.addVehicle(MechBay.STARTER_SQUAD_ID, hull);

        assertTrue(bay.installInternal(mech.id(), MechInternal.FACTORY_PLATING));
        assertTrue(bay.fitPlating(hull.id(), MechInternal.FACTORY_PLATING));
        assertEquals(MechInternal.FACTORY_PLATING, mech.internal(MechInternal.Role.PLATING));
        assertEquals(MechInternal.FACTORY_PLATING, hull.plating());
        assertEquals(0, bay.availableInternal(MechInternal.FACTORY_PLATING.id));
    }

    @Test
    void theGameProvidedHoundIsTheOneMachineThatArrivesFitted() {
        CampaignMech hound = CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment());
        assertEquals(MechWeaponComponent.CHAINGUN, hound.arms());
        assertEquals(CampaignMech.Readiness.READY, hound.readiness(0f));
    }

    @Test
    void aHullsOwnMechGunIsAFittedPartAndOnlyATurretsOwnWeaponStaysBuiltIn() {
        CampaignVehicle onager = new CampaignVehicle("o", "Onager", VehicleType.BREAKTHROUGH_TANK);
        assertSame(MechWeaponComponent.HC_20, onager.gun(), "the cannon is in the card, not built in");
        assertFalse(onager.hasOwnGun());
        assertTrue(onager.fitGun(MechWeaponComponent.LIGHT_MACHINE_GUN), "the card takes anything that fits");
        assertTrue(onager.stripGun());
        assertNull(onager.gun());

        CampaignVehicle mortar = new CampaignVehicle("m", "Pounder", VehicleType.FIRE_SUPPORT_APC);
        assertNull(mortar.gun());
        assertTrue(mortar.hasOwnGun(), "the mortar is its turret's own weapon, no part");
        assertFalse(mortar.freezeForDeployment().noOwnGun());
        assertTrue(mortar.fitGun(MechWeaponComponent.CHAINGUN));
        assertTrue(mortar.stripGun());
        assertFalse(mortar.hasOwnGun(), "the card stands empty once its own gun has gone");
    }
}
