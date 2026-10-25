package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.TownZoning.Part;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownZoningTest {

    @Test
    void theCentreIsATwoByTwoCoreRingedByTheHighStreetWithIndustryOnTheFarthestEdge() {
        // A 7x4 grid of 20-cell blocks; the centre sits in the upper-left quarter of block (3, 2).
        Part[][] plan = TownZoning.plan(7, 4, 20, 20, 64, 44, false);
        assertEquals(Part.CORE, plan[2][1]);
        assertEquals(Part.CORE, plan[3][2]);
        assertEquals(Part.CORE, plan[2][2]);
        assertEquals(Part.CORE, plan[3][1]);
        assertEquals(Part.RING, plan[1][1]);
        assertEquals(Part.RING, plan[4][2]);
        assertEquals(Part.RING, plan[2][0]);
        assertEquals(Part.RING, plan[3][3]);
        // The core spans columns 2..3, so the east edge (three blocks away) is farther than the west (two).
        for (int dy = 0; dy < 4; dy++) assertEquals(Part.BELT, plan[6][dy]);
        assertEquals(Part.REST, plan[0][1]);
    }

    @Test
    void anIndustrialEconomyDeepensTheBeltWithoutTakingTheRing() {
        Part[][] plan = TownZoning.plan(7, 4, 20, 20, 64, 44, true);
        for (int dy = 0; dy < 4; dy++) {
            assertEquals(Part.BELT, plan[6][dy]);
            assertTrue(plan[5][dy] == Part.BELT || plan[5][dy] == Part.RING);
        }
    }

    @Test
    void theRolledMapGetsOneCentreAndItsIndustryOnTheBelt() {
        for (long seed = 0; seed < 32; seed++) {
            DistrictMap map = new DistrictMap(144, 80, new Random(seed));
            TownZoning.apply(map, 64, 44);
            Part[][] plan = TownZoning.plan(7, 4, 20, 20, 64, 44, false);
            for (int dx = 0; dx < 7; dx++) {
                for (int dy = 0; dy < 4; dy++) {
                    MapDistrictTheme theme = map.themeAtDistrict(dx, dy);
                    if (theme == MapDistrictTheme.WATERFRONT) continue; // the coast keeps its own
                    switch (plan[dx][dy]) {
                        case CORE -> assertEquals(MapDistrictTheme.CIVIC, theme);
                        case RING -> assertEquals(MapDistrictTheme.MIXED, theme);
                        case BELT -> assertEquals(MapDistrictTheme.INDUSTRIAL, theme);
                        case REST -> {
                            assertNotEquals(MapDistrictTheme.CIVIC, theme, "a second centre at " + dx + "," + dy);
                            if (theme == MapDistrictTheme.INDUSTRIAL) {
                                assertEquals(6 - 1, dx, "industry off the belt at " + dx + "," + dy);
                            }
                        }
                    }
                }
            }
        }
    }
}
