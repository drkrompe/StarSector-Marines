package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;

import java.util.List;
import java.util.Objects;

/** The heart of a Rav Bloom (`heart-nouns.md`): one variant, the seed and the company's boats. */
public record BloomHeartBattleFixture(long seed, Form form, List<ShuttleAssignment> manifest) implements BattleFixture {
    public static final String KIND = "BLOOM_HEART";

    public BloomHeartBattleFixture {
        Objects.requireNonNull(form, "form");
        manifest = List.copyOf(manifest);
    }

    @Override public String kind() { return KIND; }

    @Override public BattleSimulation build() {
        return BloomHeartSetup.mission(form, seed, manifest);
    }
}
