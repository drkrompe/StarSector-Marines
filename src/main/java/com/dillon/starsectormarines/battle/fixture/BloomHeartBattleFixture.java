package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;

import java.util.List;
import java.util.Objects;

/**
 * The heart of a Rav Bloom (`heart-nouns.md`): one variant, the world's biome, how built-up its country is, the seed
 * and the company's boats. {@code objectives}, when not empty, names the optional objectives the battle runs instead
 * of the draw (each still only where its place was laid), so evidence can ask for a beat. {@code mechDrops} declares the
 * lances the company may have called down for it (none in the campaign, where the player calls their own).
 */
public record BloomHeartBattleFixture(long seed, Form form, SurfacePalette biome, Density density,
                                      List<ShuttleAssignment> manifest, List<String> objectives,
                                      MechDropDeclaration mechDrops)
        implements BattleFixture {
    public static final String KIND = "BLOOM_HEART";

    public BloomHeartBattleFixture {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(biome, "biome");
        if (density == null) density = Density.RURAL;
        manifest = List.copyOf(manifest);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        if (mechDrops == null) mechDrops = MechDropDeclaration.NONE;
    }

    /** A heart that declares no lances. */
    public BloomHeartBattleFixture(long seed, Form form, SurfacePalette biome, Density density,
                                   List<ShuttleAssignment> manifest, List<String> objectives) {
        this(seed, form, biome, density, manifest, objectives, MechDropDeclaration.NONE);
    }

    /** A heart whose objectives are drawn. */
    public BloomHeartBattleFixture(long seed, Form form, SurfacePalette biome, Density density,
                                   List<ShuttleAssignment> manifest) {
        this(seed, form, biome, density, manifest, List.of());
    }

    /** An open-country heart. */
    public BloomHeartBattleFixture(long seed, Form form, SurfacePalette biome, List<ShuttleAssignment> manifest) {
        this(seed, form, biome, Density.RURAL, manifest);
    }

    @Override public String kind() { return KIND; }

    @Override public BattleSimulation build() {
        return BloomHeartSetup.mission(form, biome, density, seed, manifest, objectives, mechDrops);
    }
}
