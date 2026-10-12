package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import java.util.Objects;

/** The Wayline evidence-and-intervention contract, with its reproducible base inputs. */
public record ColdAccountBattleFixture(AssaultBattleFixture base) implements BattleFixture {
    public static final String KIND = "COLD_ACCOUNT";
    public ColdAccountBattleFixture { Objects.requireNonNull(base, "base"); }
    @Override public String kind() { return KIND; }
    @Override public BattleSimulation build() {
        return BattleSetup.createColdAccount(base.seed(), base.manifest(), base.enemyHasHeavyArmor(),
                base.tier(), base.risk(), base.targetProfile(),
                FighterWingCommitment.toRoster(base.marineFighterSupport()),
                FighterWingCommitment.toRoster(base.enemyFighterSupport()), base.sprawl());
    }
}
