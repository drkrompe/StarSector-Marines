package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A plan belongs to one call, and conquest cannot be given one by accident.
 *
 * <p>One generator instance serves every battle in a session — battle setup
 * holds a single static one — so a plan remembered on the generator would build
 * the next battle's map out of the last battle's places, and nothing about the
 * second map would say so. That is why the plan is an argument, and this is the
 * assertion that keeps it one.
 *
 * <p>The refusal is the same law from the other side: {@code precincts.md} pins
 * conquest to the stock crossroad until the grown maps are judged, and a caller
 * that passes both an axis and a plan has asked for two different maps.
 */
class PrecinctPlanIsPerCallTest {

    private static final int W = 280;
    private static final int H = 168;

    private static TargetProfile world() {
        return new TargetProfile(6, 5, 5, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    private static PrecinctPlan plan() {
        return PrecinctPlan.derive(world(), W, H, new Random(42L));
    }

    /** Conquest keeps the stock crossroad; asking for both is a mistake, not a preference. */
    @Test
    void anAxisAndAPlanTogetherAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new BspCityGenerator().generate(W, H, 42L,
                        TraversalAxis.WEST_TO_EAST, world(), plan()));
    }

    /**
     * The battle after a precinct battle is not built out of its places.
     *
     * <p>Compared through the finished map rather than through the generator's
     * fields, because the fault this guards against was invisible from outside:
     * a stale plan produces a perfectly good map of the wrong world.
     */
    @Test
    void aPlanDoesNotSurviveIntoTheNextCall() {
        BspCityGenerator reused = new BspCityGenerator();
        reused.generate(W, H, 42L, null, world(), plan());
        MapResult after = reused.generate(W, H, 42L, null, world(), null);

        MapResult fresh = new BspCityGenerator().generate(W, H, 42L, null, world(), null);

        assertEquals(fresh.pointsOfInterest.size(), after.pointsOfInterest.size(),
                "a generator that had built a precinct map produced "
                        + after.pointsOfInterest.size() + " points of interest on the next "
                        + "plain map against a fresh generator's "
                        + fresh.pointsOfInterest.size() + ", so the plan outlived its call");
        assertEquals(fresh.marineSpawnX, after.marineSpawnX,
                "the marines spawned somewhere the plain map would not have put them");
        assertEquals(fresh.marineSpawnY, after.marineSpawnY,
                "the marines spawned somewhere the plain map would not have put them");
        assertEquals(fresh.defenderSpawnX, after.defenderSpawnX,
                "the defenders spawned somewhere the plain map would not have put them");
        assertEquals(fresh.defenderSpawnY, after.defenderSpawnY,
                "the defenders spawned somewhere the plain map would not have put them");
    }
}
