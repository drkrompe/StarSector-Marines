package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignMarineDeploymentTest {

    @Test
    void pairedConquestLoadsEveryConfiguredPairBeforeAdvancingToTheNextWave() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(48);
        List<ShuttleAssignment> manifest = List.of(
                new ShuttleAssignment(ShuttleType.VALKYRIE, 8, 6));
        CampaignMarineDeployment deployment =
                CampaignMarineDeployment.freeze(roster, 48);

        try (BattleSimulation sim = BattleSetup.createConquest(
                5_151L, manifest, false, RiskLevel.LOW,
                TargetProfile.NEUTRAL)) {
            deployment.applyTo(sim);
            List<ShuttleMission> missions = manifestMissions(sim);
            List<String> expected = deployment.commitments().stream()
                    .map(commitment -> commitment.campaignSoldierId())
                    .toList();

            assertEquals(6, missions.size());
            for (int mission = 0; mission < missions.size(); mission++) {
                int firstSeat = mission * 6;
                assertEquals(expected.subList(firstSeat, firstSeat + 6),
                        soldierIds(missions.get(mission), 0));
            }
            assertEquals(expected.subList(36, 42),
                    soldierIds(missions.get(0), 1));
            assertEquals(expected.subList(42, 48),
                    soldierIds(missions.get(1), 1));
        }
    }

    @Test
    void freezesPersistentIdentityProgressionAndAllocatedVisuals() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSoldier soldier = roster.activeSoldiers().get(0);
        assertTrue(roster.allocateArmor(soldier.id(), MarineArmorPattern.CHARCOAL));
        assertTrue(roster.allocatePrimary(soldier.id(), WeaponRegistry.require(WeaponRegistry.DMR_ID),
                EquipmentGrade.SERVICE));

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 1);
        MarineLoadout seat = deployment.seat(0);

        assertNotNull(seat);
        assertEquals(soldier.id(), seat.campaignSoldierId);
        assertEquals(WeaponRegistry.require(WeaponRegistry.DMR_ID), seat.primaryDef());
        // Experience is issued with the armour: Bastion line armor is a
        // tier-3 pattern, so the seat freezes at the veteran band.
        assertEquals(ExperienceTier.VETERAN.minimumXp, seat.soldierProfile.experienceXp());
        assertEquals(LayeredArmorFamily.CHARCOAL, seat.armorFamily);
        assertEquals(MarineArmorPattern.CHARCOAL.armorCapacity, seat.armorCapacity, 1e-6f);
        assertEquals(MarineArmorPattern.CHARCOAL.armorRating,
                seat.armorRating, 1e-6f);
        assertEquals(MarineArmorPattern.CHARCOAL.moveSpeedMult,
                seat.armorMoveSpeedMult, 1e-6f);
        assertEquals(MarineArmorPattern.CHARCOAL.incomingAccuracyMult,
                seat.armorIncomingAccuracyMult, 1e-6f);
    }

    @Test
    void selectedFireteamIsLoadedBeforeOtherRosterPersonnel() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2 * MarineSquad.CAPACITY);
        MarineSquad second = roster.squads().get(1);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(
                roster, Collections.singleton(second.id()), 2);

        assertEquals(second.memberIds().get(0), deployment.seat(0).campaignSoldierId);
        assertEquals(second.memberIds().get(1), deployment.seat(1).campaignSoldierId);
    }

    @Test
    void freezingDeploymentDoesNotGenerateFreeReplacements() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 4);

        assertEquals(1, deployment.size());
        assertEquals(1, roster.soldiers().size());
    }

    @Test
    void reservePersonnelAreNotAutoLoadedWithoutFireteamAssignment() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2);
        MarineSoldier reserveMarine = roster.soldiers().get(0);
        assertTrue(roster.transferSoldier(
                reserveMarine.id(), roster.reserveSquad().id()));

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 2);

        assertEquals(1, deployment.size());
        assertNotEquals(reserveMarine.id(), deployment.seat(0).campaignSoldierId);
    }

    @Test
    void staleExplicitSelectionDoesNotFallBackToUnselectedFireteams() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(
                roster, Collections.singleton("missing-squad"), 2);

        assertEquals(0, deployment.size());
    }

    @Test
    void initializedEmptySelectionDoesNotLoadTheWholeCompany() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);

        CampaignMarineDeployment deployment =
                CampaignMarineDeployment.freezeSelection(
                        roster, Collections.emptySet(), 2);

        assertEquals(0, deployment.size());
    }

    @Test
    void employerShuttleSeatsRemainGeneratedWhenApplyingPlayerPersonnel() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSoldier playerMarine = roster.lineReadySoldiers().get(0);
        BattleSimulation sim = BattleSetup.createPlaceholder(7_007L, Arrays.asList(
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                new ShuttleAssignment(ShuttleType.HERMES, 1)),
                false, RiskLevel.LOW, MissionType.ASSAULT);

        CampaignMarineDeployment.freeze(roster, 1).applyTo(sim, 1);

        assertEquals(0, assignedPersonnel(sim, ShuttleType.AEROSHUTTLE, null));
        assertEquals(1, assignedPersonnel(sim, ShuttleType.HERMES, playerMarine.id()));
    }

    @Test
    void commitmentReplayPreservesScenarioRoleAndObjective() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSoldier marine = roster.lineReadySoldiers().get(0);
        BattleSimulation sim = BattleSetup.createSabotage(
                8_191L,
                Collections.singletonList(new ShuttleAssignment(ShuttleType.HERMES, 1)),
                false,
                RiskLevel.LOW);
        try {
            ShuttleMission mission = missionFor(sim, ShuttleType.HERMES);
            MarineLoadout scenarioSeat = mission.cycleLoadouts[0][0];
            assertEquals(UnitRole.PLANTER, scenarioSeat.role);
            assertNotNull(scenarioSeat.objective);

            CampaignMarineDeployment frozen = CampaignMarineDeployment.freeze(roster, 1);
            CampaignMarineDeployment.fromCommitments(frozen.commitments()).applyTo(sim);

            MarineLoadout applied = mission.cycleLoadouts[0][0];
            assertEquals(UnitRole.PLANTER, applied.role);
            assertSame(scenarioSeat.objective, applied.objective);
            assertEquals(marine.id(), applied.campaignSoldierId);
        } finally {
            sim.close();
        }
    }

    private static ShuttleMission missionFor(BattleSimulation sim, ShuttleType shuttleType) {
        BattleComponents components = sim.getBattleComponents();
        for (ArchetypeTable table : sim.getEntityWorld().matched(components.airCraft)) {
            Object[] types = table.objects(components.AIR_IDENTITY,
                    BattleComponents.AIR_IDENTITY_TYPE).array();
            Object[] missions = table.objects(components.SHUTTLE_MISSION,
                    BattleComponents.SHUTTLE_MISSION_STATE).array();
            for (int row = 0; row < table.rowCount(); row++) {
                if (types[row] == shuttleType) return (ShuttleMission) missions[row];
            }
        }
        throw new AssertionError("No " + shuttleType + " mission");
    }

    private static List<ShuttleMission> manifestMissions(BattleSimulation sim) {
        List<ShuttleMission> missions = new ArrayList<>();
        BattleComponents components = sim.getBattleComponents();
        for (ArchetypeTable table : sim.getEntityWorld().matched(components.airCraft)) {
            Object[] encoded = table.objects(components.SHUTTLE_MISSION,
                    BattleComponents.SHUTTLE_MISSION_STATE).array();
            for (int row = 0; row < table.rowCount(); row++) {
                ShuttleMission mission = (ShuttleMission) encoded[row];
                if (mission != null && mission.manifestOrdinal >= 0) missions.add(mission);
            }
        }
        missions.sort(Comparator.comparingInt(mission -> mission.manifestOrdinal));
        return missions;
    }

    private static List<String> soldierIds(ShuttleMission mission, int cycle) {
        return Arrays.stream(mission.cycleLoadouts[cycle])
                .map(loadout -> loadout.campaignSoldierId)
                .toList();
    }

    private static int assignedPersonnel(BattleSimulation sim, ShuttleType shuttleType,
                                         String expectedSoldierId) {
        BattleComponents components = sim.getBattleComponents();
        int matches = 0;
        int matchingShuttles = 0;
        for (ArchetypeTable table : sim.getEntityWorld().matched(components.airCraft)) {
            Object[] types = table.objects(components.AIR_IDENTITY,
                    BattleComponents.AIR_IDENTITY_TYPE).array();
            Object[] missions = table.objects(components.SHUTTLE_MISSION,
                    BattleComponents.SHUTTLE_MISSION_STATE).array();
            for (int row = 0; row < table.rowCount(); row++) {
                if (types[row] != shuttleType) continue;
                matchingShuttles++;
                ShuttleMission mission = (ShuttleMission) missions[row];
                for (MarineLoadout[] cycle : mission.cycleLoadouts) {
                    for (MarineLoadout loadout : cycle) {
                        if (expectedSoldierId == null) {
                            assertNull(loadout.campaignSoldierId);
                        } else if (expectedSoldierId.equals(loadout.campaignSoldierId)) {
                            matches++;
                        }
                    }
                }
            }
        }
        assertEquals(1, matchingShuttles);
        return matches;
    }
}
