package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestDefenderCommand;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture.WingCommitment;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConquestBattleFixtureTest {

    @Test
    void checkedInFixtureRebuildsThroughTheProductionConquestFactory()
            throws Exception {
        ConquestBattleFixture fixture =
                BattleFixtureTestSupport.loadConquestFixture();

        try (BattleSimulation replay = fixture.build();
             BattleSimulation direct = BattleSetup.createConquest(
                     fixture.seed(), fixture.manifest(),
                     fixture.enemyHasHeavyArmor(), fixture.tier(), fixture.risk(),
                     fixture.targetProfile(), roster(fixture.marineFighterSupport()),
                     roster(fixture.enemyFighterSupport()))) {
            assertEquals(BattleFixtureTestSupport.initialFingerprint(direct),
                    BattleFixtureTestSupport.initialFingerprint(replay));
            assertEquals(BattleSetup.CONQUEST_GRID_W, replay.getGrid().getWidth());
            assertEquals(BattleSetup.CONQUEST_GRID_H, replay.getGrid().getHeight());
            assertEquals(1, replay.getCompoundService().getRecords().stream()
                    .filter(record -> record.node.kind
                            == TacticalNode.Kind.COMMAND_POST)
                    .count());
            assertInstanceOf(ConquestCommand.class,
                    replay.getCommander(Faction.MARINE));
            assertInstanceOf(ConquestDefenderCommand.class,
                    replay.getCommander(Faction.DEFENDER));
        }
    }

    private static FlybyRoster roster(List<WingCommitment> commitments) {
        if (commitments.isEmpty()) return FlybyRoster.EMPTY;
        List<FighterWing> wings = new ArrayList<>();
        for (WingCommitment commitment : commitments) {
            wings.add(new FighterWing(commitment.profile(), commitment.side(),
                    commitment.sortieCount(), commitment.firstArrivalSec(),
                    commitment.spawnIntervalSec()));
        }
        return new FlybyRoster(wings);
    }
}
