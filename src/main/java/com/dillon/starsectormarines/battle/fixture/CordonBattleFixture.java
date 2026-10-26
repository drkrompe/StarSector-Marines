package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonPosture;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;

import java.util.List;
import java.util.Objects;

/**
 * Authored inputs to the Cordon ({@code cordon-nouns.md}): which variant, the
 * world's biome, how built-up its country is, the seed, the company's lift,
 * the line posture its commander plays (Hold where none is given) and the mech
 * lances the company may call (none where none is given).
 */
public record CordonBattleFixture(long seed, CordonVariant variant, SurfacePalette biome,
                                  CordonPlan.Density density, List<ShuttleAssignment> manifest, CordonPosture posture,
                                  MechDropDeclaration mechDrops)
        implements BattleFixture {

    public static final String KIND = "CORDON";

    public CordonBattleFixture {
        variant = Objects.requireNonNull(variant, "variant");
        biome = Objects.requireNonNull(biome, "biome");
        density = Objects.requireNonNull(density, "density");
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
        posture = CordonPosture.orDefault(posture);
        mechDrops = mechDrops == null ? MechDropDeclaration.NONE : mechDrops;
    }

    /** A Cordon whose company calls no mech lance. */
    public CordonBattleFixture(long seed, CordonVariant variant, SurfacePalette biome,
                               CordonPlan.Density density, List<ShuttleAssignment> manifest, CordonPosture posture) {
        this(seed, variant, biome, density, manifest, posture, MechDropDeclaration.NONE);
    }

    /** A Cordon whose commander holds. */
    public CordonBattleFixture(long seed, CordonVariant variant, SurfacePalette biome,
                               CordonPlan.Density density, List<ShuttleAssignment> manifest) {
        this(seed, variant, biome, density, manifest, CordonPosture.DEFAULT);
    }

    /** A Cordon in open country. */
    public CordonBattleFixture(long seed, CordonVariant variant, SurfacePalette biome,
                               List<ShuttleAssignment> manifest) {
        this(seed, variant, biome, CordonPlan.Density.RURAL, manifest, CordonPosture.DEFAULT);
    }

    @Override public String kind() { return KIND; }

    @Override public BattleSimulation build() {
        return CordonSetup.create(CordonSetup.map(variant, density, biome, seed), variant, manifest,
                CordonSequence.Timetable.STANDARD, posture, mechDrops);
    }
}
