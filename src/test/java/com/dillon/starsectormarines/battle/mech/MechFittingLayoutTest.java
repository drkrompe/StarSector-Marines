package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechFittingLayoutTest {

    @Test
    void everyChassisOwnsACompleteUniquePositiveSizedSocketLayout() {
        for (MechVariant variant : MechVariant.values()) {
            MechFittingLayout layout = MechFittingLayout.forVariant(variant);
            EnumSet<SocketId> ids = EnumSet.noneOf(SocketId.class);
            assertEquals(variant, layout.variant());
            for (SocketDef socket : layout.sockets()) {
                assertTrue(ids.add(socket.id()));
                assertTrue(socket.capacity() > 0);
                assertTrue(socket.footprintWidthHull() > 0f);
                assertTrue(socket.footprintHeightHull() > 0f);
            }
            assertEquals(EnumSet.allOf(SocketId.class), ids);
        }
    }

    @Test
    void authoredSocketCanRemainVisibleWhenNoEquipmentIsInstalled() {
        MechFittingLayout hound = MechFittingLayout.forVariant(MechVariant.HOUND);
        MechFittingLayout bulwark = MechFittingLayout.forVariant(MechVariant.BULWARK);

        assertFalse(hound.occupied(SocketId.RIGHT_SHOULDER));
        assertTrue(bulwark.occupied(SocketId.RIGHT_SHOULDER));
        assertNotEquals(hound.socket(SocketId.RIGHT_SHOULDER).capacity(),
                bulwark.socket(SocketId.RIGHT_SHOULDER).capacity());
        assertNotEquals(hound.socket(SocketId.ARMS).footprintWidthHull(),
                bulwark.socket(SocketId.ARMS).footprintWidthHull());
    }
}
