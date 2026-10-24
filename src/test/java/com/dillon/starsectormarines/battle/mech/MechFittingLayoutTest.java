package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechFittingLayoutTest {

    @Test
    void missileFamiliesShareRackSizesAndRespectLightAndHeavyShoulders() {
        SocketDef small = MechFittingLayout.forVariant(MechVariant.HOUND).socket(SocketId.LEFT_SHOULDER);
        SocketDef large = MechFittingLayout.forVariant(MechVariant.BULWARK).socket(SocketId.LEFT_SHOULDER);
        assertEquals(2, large.gridColumns());
        assertEquals(3, large.gridRows());
        for (MechWeaponComponent component : new MechWeaponComponent[]{
                MechWeaponComponent.SRM_5, MechWeaponComponent.LRM_5,
                MechWeaponComponent.SRM_15, MechWeaponComponent.LRM_15}) {
            boolean five = component == MechWeaponComponent.SRM_5 || component == MechWeaponComponent.LRM_5;
            assertEquals(2, component.footprintColumns);
            assertEquals(five ? 2 : 3, component.footprintRows);
            assertEquals(five, small.accommodates(component.footprintColumns, component.footprintRows));
            assertTrue(large.accommodates(component.footprintColumns, component.footprintRows));
        }
    }

    @Test
    void everyChassisOwnsACompleteUniquePositiveSizedSocketLayout() {
        for (MechVariant variant : MechVariant.values()) {
            MechFittingLayout layout = MechFittingLayout.forVariant(variant);
            EnumSet<SocketId> ids = EnumSet.noneOf(SocketId.class);
            assertEquals(variant, layout.variant());
            assertEquals(180f, layout.doll().facingDegrees());
            for (SocketDef socket : layout.sockets()) {
                assertTrue(ids.add(socket.id()));
                assertTrue(socket.capacity() > 0);
                assertTrue(socket.footprintWidthHull() > 0f);
                assertTrue(socket.footprintHeightHull() > 0f);
                double anchorToDock = Math.hypot(
                        socket.dockRight() - socket.anchorRight(),
                        socket.dockForward() - socket.anchorForward());
                assertTrue(anchorToDock > 0.5,
                        "equipment dock should use the gantry around " + socket.id());
            }
            if (variant == MechVariant.HOUND) {
                assertEquals(EnumSet.of(SocketId.CORE, SocketId.ARMS,
                        SocketId.LEFT_SHOULDER, SocketId.AUXILIARY), ids);
            } else if (variant.twoArmed()) {
                assertEquals(EnumSet.complementOf(EnumSet.of(SocketId.ARMS)), ids,
                        "a two-armed chassis has an arm socket each side and no centreline mount");
            } else {
                assertEquals(EnumSet.complementOf(EnumSet.of(SocketId.LEFT_ARM, SocketId.RIGHT_ARM)), ids);
            }
        }
    }

    @Test
    void authoredSocketCanRemainVisibleWhenNoEquipmentIsInstalled() {
        MechFittingLayout hound = MechFittingLayout.forVariant(MechVariant.HOUND);
        MechFittingLayout bulwark = MechFittingLayout.forVariant(MechVariant.BULWARK);

        assertFalse(hound.occupied(SocketId.RIGHT_SHOULDER));
        assertFalse(hound.hasSocket(SocketId.RIGHT_SHOULDER));
        assertTrue(hound.hasSocket(SocketId.LEFT_SHOULDER));
        assertTrue(bulwark.occupied(SocketId.RIGHT_SHOULDER));
        assertEquals(SocketType.OMNI,
                bulwark.socket(SocketId.RIGHT_SHOULDER).type());
        MechFittingLayout warden = MechFittingLayout.forVariant(MechVariant.WARDEN);
        assertNotEquals(hound.socket(SocketId.ARMS).capacity(),
                warden.socket(SocketId.ARMS).capacity());
        assertNotEquals(hound.socket(SocketId.ARMS).footprintWidthHull(),
                warden.socket(SocketId.ARMS).footprintWidthHull());
        assertFalse(bulwark.hasSocket(SocketId.ARMS));
        assertTrue(bulwark.occupied(SocketId.LEFT_ARM));
        assertTrue(bulwark.occupied(SocketId.RIGHT_ARM));
    }

    @Test
    void rectangularFootprintsDistinguishHoundAndHeavyWeaponMounts() {
        SocketDef houndArms = MechFittingLayout.forVariant(MechVariant.HOUND)
                .socket(SocketId.ARMS);

        assertFalse(houndArms.accommodates(
                MechWeaponComponent.SINGLE_HEAVY_CANNON.footprintColumns,
                MechWeaponComponent.SINGLE_HEAVY_CANNON.footprintRows));
        assertTrue(houndArms.accommodates(
                MechWeaponComponent.DEMOLITION_CANNON.footprintColumns,
                MechWeaponComponent.DEMOLITION_CANNON.footprintRows));
    }
}
