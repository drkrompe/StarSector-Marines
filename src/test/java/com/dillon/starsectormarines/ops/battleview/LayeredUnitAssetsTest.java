package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredUnitAssetsTest {

    @Test
    void integralAndSpecialIdentityRemainSeparateAndBothReachTheAtlasInventory() {
        LayeredUnitAssets assets = new LayeredUnitAssets(null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        LayeredSpriteCache special = LayeredSpriteCache.headless("special.png", 32, 64);
        LayeredSpriteCache integral = LayeredSpriteCache.headless("integral.png", 64, 96);
        assets.registerSpecialEquipment("same-id", special);
        assets.registerIntegralSystem("same-id", integral);
        assertSame(special, assets.specialEquipment("same-id"));
        assertSame(integral, assets.integralSystem("same-id"));
        assertTrue(assets.layers().contains(special));
        assertTrue(assets.layers().contains(integral));
    }
}
