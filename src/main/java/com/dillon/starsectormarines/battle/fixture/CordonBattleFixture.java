package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;

import java.util.List;
import java.util.Objects;

/**
 * Authored inputs to the Cordon ({@code cordon-nouns.md}): which variant, the
 * world's biome, the seed and the company's lift.
 */
public record CordonBattleFixture(long seed, CordonVariant variant, SurfacePalette biome,
                                  List<ShuttleAssignment> manifest) implements BattleFixture {

    public static final String KIND = "CORDON";

    public CordonBattleFixture {
        variant = Objects.requireNonNull(variant, "variant");
        biome = Objects.requireNonNull(biome, "biome");
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
    }

    @Override public String kind() { return KIND; }

    @Override public BattleSimulation build() {
        return CordonSetup.create(variant, biome, seed, manifest);
    }
}
