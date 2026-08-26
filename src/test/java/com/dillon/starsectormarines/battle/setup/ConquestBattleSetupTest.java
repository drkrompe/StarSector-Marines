package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestDefenderCommand;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.objective.ConquestObjective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                assertEquals(CommandAuthority.MISSION_COMMAND,
                        sim.getSquadCommandDirective(squad.id).authority());
                assertEquals("conquest-defender",
                        sim.getSquadCommandDirective(squad.id).issuer());
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

    @Test
    public void everyInfantryDefenderCarriesVariedOwningFactionIssue() {
        TargetProfile pirateWorld = new TargetProfile(
                5, 5, 1, 1, "pirates", Set.of());
        BattleSimulation sim = BattleSetup.createConquest(
                4_269L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)),
                true,
                OperationTier.FULL_STRENGTH,
                RiskLevel.HIGH,
                pirateWorld);

        assertEquals("roster.pirates", sim.getGroundRoster().id());
        Set<String> primaries = new HashSet<>();
        Set<EquipmentGrade> grades = new HashSet<>();
        Set<LayeredArmorFamily> armorFamilies = new HashSet<>();
        Set<String> specials = new HashSet<>();
        int infantry = 0;
        for (int index = 0; index < sim.liveUnitCount(); index++) {
            long unit = sim.liveUnitAt(index);
            if (sim.identity().faction(unit) != Faction.DEFENDER
                    || !sim.identity().type(unit).usesInfantryTraining()) continue;
            infantry++;
            assertTrue(sim.combat().primaryWeapon(unit) != null,
                    "no Conquest defender falls back to generic line fire");
            assertTrue(sim.combat().equipmentGrade(unit) != null);
            assertTrue(sim.combat().soldierProfile(unit) != null);
            assertTrue(sim.world().layeredBodyFamily(unit) != null,
                    "every infantry defender receives authored armor");
            primaries.add(sim.combat().primaryWeapon(unit).id);
            grades.add(sim.combat().equipmentGrade(unit));
            armorFamilies.add(sim.world().layeredBodyFamily(unit));
            if (sim.world().hasSecondaryWeapon(unit)) {
                specials.add(sim.world().specialEquipment(unit).id());
            }
        }

        assertTrue(infantry > 100, "the invariant covers the whole Conquest garrison");
        assertTrue(primaries.size() > 2, "defenders roll multiple primary families");
        assertTrue(grades.size() > 1, "defenders roll multiple equipment grades");
        assertTrue(armorFamilies.size() > 1, "defenders roll multiple armor patterns");
        assertTrue(specials.size() > 1, "defenders roll multiple specialist kits");
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
