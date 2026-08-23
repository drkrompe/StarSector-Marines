package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.ConquestObjective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mode-level Conquest factory invariants shared by campaign and bridge hosts. */
public class ConquestBattleSetupTest {

    @Test
    public void lowTierStillUsesCanonicalSizeAndRequiresTheKeep() {
        BattleSimulation sim = BattleSetup.createConquest(
                91L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)),
                false,
                OperationTier.FIRST_CONTRACT,
                RiskLevel.LOW,
                TargetProfile.NEUTRAL);

        assertEquals(240, BattleSetup.CONQUEST_GRID_W);
        assertEquals(160, BattleSetup.CONQUEST_GRID_H);
        assertEquals(BattleSetup.CONQUEST_GRID_W, sim.getGrid().getWidth());
        assertEquals(BattleSetup.CONQUEST_GRID_H, sim.getGrid().getHeight());
        assertTrue(sim.getObjectives().stream().anyMatch(ConquestObjective.class::isInstance));
        assertEquals(1, sim.getCompoundService().getRecords().stream()
                .filter(record -> record.node.kind == TacticalNode.Kind.COMMAND_POST)
                .count());
    }
}
