package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectControlAimOriginsTest {
    @Test void selectionUsesActualPosedSelectedMuzzleAndAllKeepsOnlyDirectMounts() {
        var loadout = new MechLoadoutComponent(MechVariant.BULWARK, null);
        loadout.torsoFacingDegrees = 43f;
        var mount = loadout.mount(MechMountSlot.LEFT_SHOULDER);
        var expected = MechHardpointGeometry.muzzle(4f, 5f, .2f, -.1f, 43f,
                loadout, mount, MechHardpointGeometry.nextReleaseIndex(mount));
        var selected = DirectControlAimOrigins.mech(loadout, 2, 4f, 5f, .2f, -.1f);
        assertEquals(1, selected.size()); assertEquals(expected.x(), selected.get(0).x());
        assertEquals(expected.y(), selected.get(0).y());
        assertEquals(3, DirectControlAimOrigins.mech(loadout, 0, 4f, 5f, .2f, -.1f).size());
        var indirect = new MechLoadoutComponent(MechVariant.SIROCCO, null);
        assertEquals(1, DirectControlAimOrigins.mech(indirect, 0, 4f, 5f, 0f, 0f).size());
        assertTrue(DirectControlAimOrigins.mech(indirect, 2, 4f, 5f, 0f, 0f).isEmpty());
    }
}
