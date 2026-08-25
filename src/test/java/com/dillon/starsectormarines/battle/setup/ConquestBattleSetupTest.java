package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.ConquestObjective;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestDefenderCommand;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertTrue(sim.getCommander(Faction.MARINE) instanceof ConquestCommand);
        assertTrue(sim.getCommander(Faction.DEFENDER) instanceof ConquestDefenderCommand);
        assertEquals(1, sim.getCompoundService().getRecords().stream()
                .filter(record -> record.node.kind == TacticalNode.Kind.COMMAND_POST)
                .count());
        int garrisons = 0;
        for (var squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER
                    || sim.squadMemberCount(squad.id) <= 0) continue;
            UnitRole role = sim.role().role(sim.squadMemberAt(squad.id, 0));
            if (role == UnitRole.GARRISON) {
                garrisons++;
                assertEquals(CommandAuthority.GARRISON,
                        sim.getSquadCommandDirective(squad.id).authority());
                assertEquals("conquest-setup-garrison",
                        sim.getSquadCommandDirective(squad.id).issuer());
            } else if (role == UnitRole.PATROL) {
                assertNull(sim.getSquadCommandDirective(squad.id),
                        "setup patrols remain available to Conquest mission command");
            }
        }
        assertTrue(garrisons > 0);
    }

    @Test
    public void conquestNeverTrimsAuthoredSupportToTheAttackingManifest() {
        FlybyRoster enemyAir = new FlybyRoster(List.of(
                new FighterWing(FighterProfile.DAGGER, Faction.DEFENDER,
                        3, 10f, 20f)));
        BattleSimulation sim = BattleSetup.createConquest(
                91L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)),
                true,
                OperationTier.FIRST_CONTRACT,
                RiskLevel.HIGH,
                TargetProfile.NEUTRAL,
                FlybyRoster.EMPTY,
                enemyAir);

        assertEquals(6, count(sim, UnitType.HEAVY_MECH),
                "one tiny attacker shuttle must not trim Conquest's two mech groups");
        assertTrue(turretCount(sim) > 0,
                "one tiny attacker shuttle must not disarm Conquest fortifications");
        assertEquals(enemyAir.wings, sim.getFlybyRoster().wings,
                "one tiny attacker shuttle must not cancel authored enemy air");
    }

    private static int count(BattleSimulation sim, UnitType type) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.DEFENDER
                    && sim.identity().type(unit) == type) count++;
        }
        return count;
    }

    private static int turretCount(BattleSimulation sim) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            if (sim.identity().type(sim.liveUnitAt(i)).isTurret()) count++;
        }
        return count;
    }
}
