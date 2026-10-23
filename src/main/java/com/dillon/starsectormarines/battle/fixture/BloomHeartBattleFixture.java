package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;

import java.util.List;
import java.util.Objects;

/**
 * The heart of a Rav Bloom (`heart-nouns.md`): one variant, the world's biome, how built-up its country is, the seed
 * and the company's boats.
 */
public record BloomHeartBattleFixture(long seed, Form form, SurfacePalette biome, Density density,
                                      List<ShuttleAssignment> manifest)
        implements BattleFixture {
    public static final String KIND = "BLOOM_HEART";

    public BloomHeartBattleFixture {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(biome, "biome");
        if (density == null) density = Density.RURAL;
        manifest = List.copyOf(manifest);
    }

    /** An open-country heart. */
    public BloomHeartBattleFixture(long seed, Form form, SurfacePalette biome, List<ShuttleAssignment> manifest) {
        this(seed, form, biome, Density.RURAL, manifest);
    }

    @Override public String kind() { return KIND; }

    @Override public BattleSimulation build() {
        return BloomHeartSetup.mission(form, biome, density, seed, manifest);
    }
}
