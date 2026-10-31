package com.dillon.starsectormarines.battle.mech.components;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MachineSystems;
import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A live mech shows active protection exactly when its deployment frame's compartment carries it. */
class MechLoadoutFitAppearanceTest {

    @Test
    void activeProtectionInTheFrameShowsOnTheMech() {
        MechLoadoutComponent fitted = MechVariant.HOUND.createLoadout(MechVariant.HOUND.defaultRole);
        fitted.applyFrame(frame(new MachineSystems(1f, 1f, 1f, List.of(), true)));
        assertEquals(LayeredMechAppearance.FLAG_ACTIVE_PROTECTION, fitted.fitAppearanceFlags());
    }

    @Test
    void aMechWithoutItShowsNone() {
        MechLoadoutComponent bare = MechVariant.HOUND.createLoadout(MechVariant.HOUND.defaultRole);
        assertEquals(0, bare.fitAppearanceFlags());
        bare.applyFrame(frame(MachineSystems.NONE));
        assertEquals(0, bare.fitAppearanceFlags());
    }

    private static MechFrame frame(MachineSystems systems) {
        MechFrame stock = MechFrame.stock(MechVariant.HOUND);
        return new MechFrame(stock.armorCapacity(), stock.moveSpeed(), stock.fluxCapacity(),
                stock.dissipationPerSecond(), systems);
    }
}
