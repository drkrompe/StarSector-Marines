package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Objects;

/** The Dust Ledger's raid on Saltwell Cross ({@code dust-ledger-crossing.md}), with its reproducible inputs. */
public record DustLedgerBattleFixture(long seed, List<ShuttleAssignment> manifest, RiskLevel risk,
                                      TargetProfile targetProfile) implements BattleFixture {
    public static final String KIND = "DUST_LEDGER";

    public DustLedgerBattleFixture {
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
        risk = Objects.requireNonNull(risk, "risk");
    }

    @Override public String kind() { return KIND; }
    @Override public BattleSimulation build() { return BattleSetup.createDustLedger(seed, manifest, risk, targetProfile); }
}
