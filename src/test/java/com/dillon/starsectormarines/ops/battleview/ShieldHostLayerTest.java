package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.battleview.ShieldDomeRenderSystem.Hosts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShieldHostLayerTest {

    @Test
    void aShieldIsDrawnInItsHostsLayerOverTheHost() {
        assertEquals(Hosts.HULLS, ShieldDomeRenderSystem.hostsOf(false, true));
        assertEquals(Hosts.BOATS, ShieldDomeRenderSystem.hostsOf(true, false));
        assertEquals(Hosts.GROUND, ShieldDomeRenderSystem.hostsOf(false, false));
        ShieldDomeRenderSystem ground = new ShieldDomeRenderSystem();
        assertEquals(RenderLayer.HAZARDS, ground.layer());
        assertEquals(RenderLayer.CONVOY, ground.hullShields().layer());
        assertEquals(RenderLayer.SHUTTLES, ground.boatShields().layer());
        assertEquals(true, RenderLayer.CONVOY.ordinal() > RenderLayer.UNITS.ordinal());
    }
}
