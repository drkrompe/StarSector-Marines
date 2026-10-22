package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Cordon's map over a handful of seeds ({@code cordon-nouns.md},
 * The map): it generates within its re-rolls, every redoubt of the line
 * stands in a row across the map, and the Road has a corridor from the rear
 * edge to the town's compound. Generates whole maps, so evidence, not a test.
 */
@Tag("generated-space-evidence")
class CordonMapEvidenceTest {

    private static final long[] SEEDS = {7117L, 3L, 11L, 42L};

    @Test
    void everySeedBuildsTheWholeLine() {
        for (CordonVariant variant : CordonVariant.values()) {
            for (long seed : SEEDS) {
                CordonSetup.CordonMap cordon = CordonSetup.map(variant, seed, TargetProfile.NEUTRAL);
                List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
                assertEquals(CordonPlan.REDOUBTS, redoubts.size(), variant + " " + seed);
                for (int i = 1; i < redoubts.size(); i++) {
                    assertTrue(redoubts.get(i).seedX() > redoubts.get(i - 1).seedX(),
                            variant + " " + seed + ": redoubts are numbered west to east");
                }
                int spread = redoubts.get(redoubts.size() - 1).seedX() - redoubts.get(0).seedX();
                assertTrue(spread > CordonSetup.WIDTH / 2, variant + " " + seed + ": the line spans the map, " + spread);
                if (variant == CordonVariant.ROAD) {
                    assertNotNull(cordon.corridor(), "the Road has its corridor on seed " + seed);
                }
            }
        }
    }
}
