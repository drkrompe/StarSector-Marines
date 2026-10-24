package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;

import java.util.List;
import java.util.Objects;

/**
 * Authored inputs to the Cordon ({@code cordon-nouns.md}): which variant, the
 * world's biome, how built-up its country is, the seed and the company's lift.
 */
public record CordonBattleFixture(long seed, CordonVariant variant, SurfacePalette biome,
                                  CordonPlan.Density density, List<ShuttleAssignment> manifest)
        implements BattleFixture {

    public static final String KIND = "CORDON";

    public CordonBattleFixture {
        variant = Objects.requireNonNull(variant, "variant");
        biome = Objects.requireNonNull(biome, "biome");
        density = Objects.requireNonNull(density, "density");
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
    }

    /** A Cordon in open country. */
    public CordonBattleFixture(long seed, CordonVariant variant, SurfacePalette biome,
                               List<ShuttleAssignment> manifest) {
        this(seed, variant, biome, CordonPlan.Density.RURAL, manifest);
    }

    @Override public String kind() { return KIND; }

    @Override public BattleSimulation build() {
        if (density == CordonPlan.Density.RURAL) return CordonSetup.create(variant, biome, seed, manifest);
        return CordonSetup.create(CordonSetup.map(variant, density, biome, seed), variant, manifest,
                CordonSequence.Timetable.STANDARD);
    }
}
