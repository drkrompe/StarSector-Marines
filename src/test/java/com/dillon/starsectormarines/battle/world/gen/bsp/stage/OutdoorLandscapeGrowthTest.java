package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.Basin;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which worlds grow anything at all ({@link OutdoorLandscapeStage#grows}). */
class OutdoorLandscapeGrowthTest {

    private static SurfaceField world(SurfacePalette palette, Basin basin) {
        return new SurfaceField(palette, basin, 40, 24, 7);
    }

    @Test
    void aLivingWorldGrowsWoods() {
        assertTrue(OutdoorLandscapeStage.grows(world(SurfacePalette.VERDANT, Basin.NONE), false));
    }

    @Test
    void aPlainDesertGrowsScrubOnlyWithItsDryArt() {
        assertTrue(OutdoorLandscapeStage.grows(world(SurfacePalette.ARID, Basin.NONE), true));
        assertFalse(OutdoorLandscapeStage.grows(world(SurfacePalette.ARID, Basin.NONE), false),
                "without the dry art a desert grows nothing rather than green trees");
    }

    @Test
    void aLivingBasinOnADesertGrowsItsWoods() {
        assertTrue(OutdoorLandscapeStage.grows(
                world(SurfacePalette.ARID, new Basin(SurfacePalette.VERDANT, .6f, .6f)), false));
    }

    @Test
    void barrenAndFrozenWorldsGrowNothing() {
        assertFalse(OutdoorLandscapeStage.grows(world(SurfacePalette.ROCK, Basin.NONE), true));
        assertFalse(OutdoorLandscapeStage.grows(world(SurfacePalette.FROZEN, Basin.NONE), true));
    }
}
