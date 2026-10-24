package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectControlAimOriginsTest {
    @Test void pickUsesActualPosedPickedMuzzleAndLinkedKeepsOnlyDirectMounts() {
        var loadout = new MechLoadoutComponent(MechVariant.BULWARK, null);
        loadout.torsoFacingDegrees = 43f;
        var mount = loadout.mount(MechMountSlot.LEFT_SHOULDER);
        var expected = MechHardpointGeometry.muzzle(4f, 5f, .2f, -.1f, 43f,
                loadout, mount, MechHardpointGeometry.nextReleaseIndex(mount));
        var leftShoulder = new TriggerPicks(MechMountSlot.LEFT_ARM.ordinal() + 1,
                MechMountSlot.LEFT_SHOULDER.ordinal() + 1);
        var selected = DirectControlAimOrigins.mech(loadout, leftShoulder, 4f, 5f, .2f, -.1f);
        assertEquals(2, selected.size(), "one arm gun on the left button and the left shoulder's rack on the right");
        assertEquals(expected.x(), selected.get(1).x());
        assertEquals(expected.y(), selected.get(1).y());
        assertEquals(4, DirectControlAimOrigins.mech(loadout, TriggerPicks.LINKED, 4f, 5f, .2f, -.1f).size(),
                "a gun on each arm plus both racks");
        var indirect = new MechLoadoutComponent(MechVariant.WARDEN, null);
        assertEquals(1, DirectControlAimOrigins.mech(indirect, TriggerPicks.LINKED, 4f, 5f, 0f, 0f).size());
        assertEquals(1, DirectControlAimOrigins.mech(indirect, new TriggerPicks(MechFireGroups.LINKED,
                        MechMountSlot.LEFT_SHOULDER.ordinal() + 1), 4f, 5f, 0f, 0f).size(),
                "an arcing rack draws no lane, picked or not");
    }
}
