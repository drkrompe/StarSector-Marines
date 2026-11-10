package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MountGlyphTest {

    @Test
    void weaponPicksItsShape() {
        assertEquals(MountGlyph.ROTARY, MountGlyph.of(MechWeaponComponent.CHAINGUN));
        assertEquals(MountGlyph.CANNON, MountGlyph.of(MechWeaponComponent.HC_10));
        assertEquals(MountGlyph.LASER, MountGlyph.of(MechWeaponComponent.PL_1));
        assertEquals(MountGlyph.SMALL_RACK, MountGlyph.of(MechWeaponComponent.SRM_5));
        assertEquals(MountGlyph.LARGE_RACK, MountGlyph.of(MechWeaponComponent.SRM_15_FRAG));
        assertNull(MountGlyph.of(null));
    }

    @Test
    void partNameSplitsIntoLocationAndShape() {
        assertEquals("LEFT_ARM", MountGlyph.location("LEFT_ARM.CANNON"));
        assertEquals(MountGlyph.CANNON, MountGlyph.ofPart("LEFT_ARM.CANNON"));
        assertEquals("TORSO", MountGlyph.location("TORSO"));
        assertNull(MountGlyph.ofPart("TORSO"));
    }
}
