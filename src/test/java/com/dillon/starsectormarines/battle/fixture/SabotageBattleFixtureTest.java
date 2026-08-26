package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.SabotageCommand;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SabotageBattleFixtureTest {

    @Test
    void rebuildsThroughTheProductionSabotageFactory() {
        FlybyRoster marineSupport = new FlybyRoster(List.of(
                new FighterWing(FighterProfile.BROADSWORD, Faction.MARINE,
                        2, 6f, 20f)));
        FlybyRoster enemySupport = new FlybyRoster(List.of(
                new FighterWing(FighterProfile.TALON, Faction.DEFENDER,
                        3, 9f, 14f)));
        List<ShuttleAssignment> manifest = List.of(
                new ShuttleAssignment(ShuttleType.KITE, 2, 4),
                new ShuttleAssignment(ShuttleType.VALKYRIE, 1, 6));
        TargetProfile profile = new TargetProfile(
                5, 6, 4, 2, "independent",
                EnumSet.of(EconomicFunction.HABITATION,
                        EconomicFunction.SPACEPORT));
        SabotageBattleFixture fixture =
                SabotageBattleFixture.fromFactoryInputs(
                        48_151L, manifest, true, OperationTier.ESTABLISHED,
                        RiskLevel.MEDIUM, profile, marineSupport, enemySupport);

        try (BattleSimulation replay = fixture.build();
             BattleSimulation direct = BattleSetup.createSabotage(
                     fixture.seed(), fixture.manifest(),
                     fixture.enemyHasHeavyArmor(), fixture.tier(), fixture.risk(),
                     fixture.targetProfile(), marineSupport, enemySupport)) {
            assertEquals(BattleFixtureTestSupport.initialFingerprint(direct),
                    BattleFixtureTestSupport.initialFingerprint(replay));
            assertEquals(3, replay.getObjectives().stream()
                    .filter(ChargeSiteObjective.class::isInstance)
                    .count());
            assertInstanceOf(SabotageCommand.class,
                    replay.getCommander(Faction.MARINE));
        }
    }
}
