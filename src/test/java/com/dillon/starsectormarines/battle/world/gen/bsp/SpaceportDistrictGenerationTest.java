package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end generation contract for campaign-backed civilian spaceports. */
class SpaceportDistrictGenerationTest {

    @Test
    void spaceportWorldPublishesUsableBerthsOnOrdinaryUrbanMap() {
        TargetProfile profile = new TargetProfile(5, 6, 1, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT));

        MapResult map = new BspCityGenerator().generate(80, 80, 42L, null, profile);

        assertFalse(map.landingPads.isEmpty(), "spaceport tier should reserve authored berths");
        for (LandingPad pad : map.landingPads) {
            assertTrue(pad.isClear(map.grid, map.topology));
        }
    }

    @Test
    void neutralWorldDoesNotForceCivilianSpaceportDistrict() {
        MapResult map = new BspCityGenerator().generate(
                80, 80, 42L, null, TargetProfile.NEUTRAL);
        // Random civic pads remain allowed. What matters is that the neutral
        // profile doesn't receive the guaranteed three-berth port contract.
        assertTrue(map.landingPads.size() < 3);
    }
}
