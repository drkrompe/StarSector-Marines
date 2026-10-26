package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Objects;

/**
 * Blind Watch at the Sounding on Warion ({@code blind-watch-sounding.md}), with its reproducible inputs. {@code stoodTo}
 * is the campaign's answer read at the boundary ({@code BlindWatchProgress.stoodTo}), so a replay plays the same post.
 */
public record BlindWatchBattleFixture(long seed, List<ShuttleAssignment> manifest, RiskLevel risk,
                                      TargetProfile targetProfile, boolean stoodTo) implements BattleFixture {
    public static final String KIND = "BLIND_WATCH";

    public BlindWatchBattleFixture {
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
        risk = Objects.requireNonNull(risk, "risk");
    }

    @Override public String kind() { return KIND; }
    @Override public BattleSimulation build() {
        return BattleSetup.createBlindWatch(seed, manifest, risk, targetProfile, stoodTo);
    }
}
