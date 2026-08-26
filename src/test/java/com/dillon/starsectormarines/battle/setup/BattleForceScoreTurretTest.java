package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleForceScoreTurretTest {

    @Test
    void oneGreenSquadCannotAffordAStaticTurretAfterItsInfantryOpposition() {
        List<ShuttleAssignment> attackers = valkyries(1);
        DefenderRoster roster = rosterFor(attackers);

        List<DefensePost> selected = BattleForceScore.affordableDefensePosts(
                candidates(), roster, BattleForceScore.attackers(attackers));

        assertTrue(selected.isEmpty());
    }

    @Test
    void alliedLiftRetainsAStableAffordablePrefixInsteadOfRerollingTheMap() {
        List<ShuttleAssignment> attackers = valkyries(4);
        DefenderRoster roster = rosterFor(attackers);

        List<DefensePost> selected = BattleForceScore.affordableDefensePosts(
                candidates(), roster, BattleForceScore.attackers(attackers));

        assertEquals(2, selected.size());
        assertEquals(TurretCatalogRegistry.VULCAN_STRUCTURE_ID,
                selected.get(0).turrets.get(0).structureId);
        assertEquals(TurretCatalogRegistry.ARBALEST_STRUCTURE_ID,
                selected.get(1).turrets.get(0).structureId);
    }

    @Test
    void turretCandidatesShareTheBudgetWithAnAffordableMech() {
        List<ShuttleAssignment> attackers = valkyries(3);
        DefenderRoster roster = rosterFor(attackers);

        List<DefensePost> selected = BattleForceScore.affordableDefensePosts(
                candidates(), roster, BattleForceScore.attackers(attackers));

        assertEquals(List.of(MechVariant.BULWARK), roster.mechVariants);
        assertEquals(1, selected.size());
        assertEquals(TurretCatalogRegistry.VULCAN_STRUCTURE_ID,
                selected.get(0).turrets.get(0).structureId);
    }

    @Test
    void balancingKeepsDroneHubsButDropsEmptyGunPosts() {
        List<ShuttleAssignment> attackers = valkyries(1);
        DefenderRoster roster = rosterFor(attackers);
        DefensePost hub = new DefensePost(
                DefensePostKind.DRONE_HUB, 9, 9, List.of());
        List<DefensePost> withHub = List.of(candidates().get(0), hub);

        List<DefensePost> selected = BattleForceScore.affordableDefensePosts(
                withHub, roster, BattleForceScore.attackers(attackers));

        assertEquals(List.of(hub), selected);
    }

    @Test
    void partiallyAffordablePostKeepsItsFirstGunsAndOriginalGeometryAnchor() {
        List<ShuttleAssignment> attackers = valkyries(4);
        DefenderRoster roster = rosterFor(attackers);
        DefensePost large = new DefensePost(
                DefensePostKind.LARGE, 6, 7, List.of(
                new DefensePost.TurretSpec(TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 5, 7),
                new DefensePost.TurretSpec(TurretCatalogRegistry.ARBALEST_STRUCTURE_ID, 7, 7),
                new DefensePost.TurretSpec(TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID, 6, 6)));

        List<DefensePost> selected = BattleForceScore.affordableDefensePosts(
                List.of(large), roster, BattleForceScore.attackers(attackers));

        assertEquals(1, selected.size());
        assertEquals(6, selected.get(0).anchorX);
        assertEquals(7, selected.get(0).anchorY);
        assertEquals(List.of(TurretCatalogRegistry.VULCAN_STRUCTURE_ID,
                        TurretCatalogRegistry.ARBALEST_STRUCTURE_ID),
                selected.get(0).turrets.stream()
                        .map(spec -> spec.structureId).toList());
        assertEquals(3, large.turrets.size(), "map-authored candidates stay untouched");
    }

    @Test
    void genericMissionFactoryBalancesBeforeStaticTurretsSpawn() {
        BattleSimulation unsupported = BattleSetup.createPlaceholder(
                42L, valkyries(1), true, OperationTier.FIRST_CONTRACT,
                RiskLevel.MEDIUM, MissionType.RAID,
                TargetProfile.NEUTRAL);
        BattleSimulation jointOperation = BattleSetup.createPlaceholder(
                42L, valkyries(4), true, OperationTier.FIRST_CONTRACT,
                RiskLevel.MEDIUM, MissionType.RAID,
                TargetProfile.NEUTRAL);

        assertEquals(0, turretCount(unsupported));
        assertTrue(turretCount(jointOperation) > 0,
                "combined allied lift should retain at least one authored gun");
    }

    private static DefenderRoster rosterFor(List<ShuttleAssignment> attackers) {
        return DefenderRoster.forMission(
                MissionType.RAID, OperationTier.FIRST_CONTRACT,
                RiskLevel.MEDIUM, true, BattleForceScore.attackers(attackers));
    }

    private static List<ShuttleAssignment> valkyries(int count) {
        List<ShuttleAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            assignments.add(new ShuttleAssignment(ShuttleType.VALKYRIE, 1));
        }
        return assignments;
    }

    private static List<DefensePost> candidates() {
        return List.of(
                post(DefensePostKind.LIGHT, TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 1),
                post(DefensePostKind.MEDIUM, TurretCatalogRegistry.ARBALEST_STRUCTURE_ID, 2),
                post(DefensePostKind.LARGE, TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID, 3));
    }

    private static DefensePost post(DefensePostKind tier, String structureId, int x) {
        return new DefensePost(tier, x, 1,
                List.of(new DefensePost.TurretSpec(structureId, x, 1)));
    }

    private static int turretCount(BattleSimulation sim) {
        int turrets = 0;
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = sim.getRoster().get(i);
            if (sim.getRoster().identity().type(unit).isTurret()) turrets++;
        }
        return turrets;
    }
}
