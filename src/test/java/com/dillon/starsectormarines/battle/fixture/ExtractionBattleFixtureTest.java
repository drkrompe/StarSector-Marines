package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionBattleFixtureTest {

    @Test
    void jsonRoundTripRebuildsProductionExtraction() throws Exception {
        ExtractionBattleFixture fixture = new ExtractionBattleFixture(
                141_418L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6)),
                false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM,
                TargetProfile.NEUTRAL, List.of(), List.of());

        BattleFixture decoded = BattleFixtureJson.fromJson(
                BattleFixtureJson.toJson(fixture));
        ExtractionBattleFixture extraction = assertInstanceOf(
                ExtractionBattleFixture.class, decoded);
        assertEquals(fixture, extraction);
        try (BattleSimulation sim = extraction.build()) {
            assertTrue(sim.getObjectives().stream()
                    .anyMatch(ExtractionObjective.class::isInstance));
        }
    }
}
