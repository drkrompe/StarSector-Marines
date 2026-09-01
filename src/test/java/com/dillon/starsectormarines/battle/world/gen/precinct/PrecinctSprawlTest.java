package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same world, settled three ways.
 *
 * <p>Whether a map is an installation in wilderness or a city with an
 * installation in it is a decision about the battle, not about the planet, so
 * it is a knob rather than a consequence of market size. What is pinned here is
 * that the three presets are actually different in kind — measured through the
 * generator at 560x336, they come out at 1.2%, 8.6% and 18.8% built ground, and
 * 93.6%, 58.2% and 15.4% wild.
 */
class PrecinctSprawlTest {

    private static final int W = 560;
    private static final int H = 336;

    private static TargetProfile world() {
        return new TargetProfile(9, 5, 5, 2, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    private static PrecinctPlan plan(PrecinctPlan.Sprawl sprawl) {
        return PrecinctPlan.derive(world(), sprawl, W, H, new Random(42L));
    }

    /**
     * A remote map is an installation and country. Adding a town to it is the
     * one thing that would stop it being one.
     */
    @Test
    void aRemoteMapIsTheInstallationAndNothingElse() {
        PrecinctPlan remote = plan(PrecinctPlan.Sprawl.REMOTE);
        assertEquals(1, remote.precincts().size(),
                "a remote map came out with " + remote.precincts().size() + " places");
        assertTrue(remote.precincts().get(0).isProgrammed(),
                "the one place on a remote map is not the installation");
    }

    /** A remote map with nothing to defend still has somewhere to fight. */
    @Test
    void anUndefendedRemoteWorldStillGetsSomewhere() {
        TargetProfile undefended = new TargetProfile(9, 5, 0, 2, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
        PrecinctPlan remote = PrecinctPlan.derive(undefended,
                PrecinctPlan.Sprawl.REMOTE, W, H, new Random(42L));
        assertTrue(!remote.precincts().isEmpty(), "an undefended remote world produced a "
                + "map with no places at all, because the garrison that was going to be "
                + "the somewhere never existed");
    }

    /** Each step up settles more of the map than the one below it. */
    @Test
    void eachPresetSettlesMoreThanTheOneBelow() {
        int remote = plan(PrecinctPlan.Sprawl.REMOTE).precincts().size();
        int balanced = plan(PrecinctPlan.Sprawl.BALANCED).precincts().size();
        int dense = plan(PrecinctPlan.Sprawl.DENSE).precincts().size();
        assertTrue(balanced > remote, "balanced has " + balanced + " places against remote's "
                + remote);
        assertTrue(dense > balanced, "dense has " + dense + " places against balanced's "
                + balanced);
    }

    /**
     * A city is districts meeting each other, not one place with a long reach.
     *
     * <p>A single settlement at full density claims outward evenly and comes
     * out a disc with a city in the middle of it. Several of them meeting is
     * what makes a grid.
     */
    @Test
    void aDenseMapIsDistrictsRatherThanOneHugePlace() {
        PrecinctPlan dense = plan(PrecinctPlan.Sprawl.DENSE);
        long zoned = dense.precincts().stream().filter(p -> !p.isProgrammed()).count();
        assertTrue(zoned >= 4, "a dense map has only " + zoned + " settled places, so it is "
                + "one settlement with a very long reach rather than a city");
    }

    /** The installation survives every preset — it is what the map is about. */
    @Test
    void theInstallationIsThereAtEveryPreset() {
        for (PrecinctPlan.Sprawl sprawl : PrecinctPlan.Sprawl.values()) {
            assertTrue(plan(sprawl).precincts().stream().anyMatch(Precinct::isProgrammed),
                    sprawl + " lost the garrison a defended world is supposed to have");
        }
    }
}
