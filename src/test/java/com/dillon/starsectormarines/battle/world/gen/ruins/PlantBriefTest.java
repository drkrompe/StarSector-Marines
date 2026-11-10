package com.dillon.starsectormarines.battle.world.gen.ruins;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantBriefTest {

    private static List<PlantBrief.Space> plant(int bands, int layers) {
        List<PlantBrief.Space> out = new ArrayList<>();
        for (int band = 0; band < bands; band++) out.addAll(PlantBrief.spaces(band, bands, layers));
        return out;
    }

    @Test
    void onlyFrameWideSpacesArePlantHalls() {
        // A hall frame must reach every plant hall; a space behind a service corridor is infantry's.
        for (int[] site : new int[][]{{1, 2}, {2, 3}, {2, 4}}) {
            for (PlantBrief.Space s : plant(site[0], site[1])) {
                if (s.freight()) assertTrue(s.stage() != null, s.name());
                else assertNull(s.stage(), s.name());
            }
        }
    }

    @Test
    void everyPlantHasOneMaintenanceShopAndAStoreAndInfantryOnlySpaces() {
        for (int[] site : new int[][]{{1, 2}, {2, 3}, {2, 4}}) {
            List<PlantBrief.Space> spaces = plant(site[0], site[1]);
            assertEquals(1, spaces.stream().filter(s -> s.stage() == RuinsDelve.PlantStage.MAINTENANCE).count());
            assertTrue(spaces.stream().anyMatch(s -> s.stage() == RuinsDelve.PlantStage.STORAGE));
            assertTrue(spaces.stream().anyMatch(s -> s.role() == RuinsRoom.CONTROL && !s.freight()));
        }
    }

    @Test
    void aLargerSiteBuildsLargerSpaces() {
        int small = PlantBrief.spaces(0, 1, 2).get(1).width(), large = PlantBrief.spaces(0, 2, 4).get(1).width();
        assertTrue(small < large, small + " vs " + large);
    }
}
