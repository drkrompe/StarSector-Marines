package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CampaignMarineDeploymentTest {

    @Test
    void freezesPersistentIdentityProgressionAndAllocatedVisuals() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSoldier soldier = roster.activeSoldiers().get(0);
        assertTrue(roster.allocateArmor(soldier.id(), MarineArmorPattern.CHARCOAL));
        assertTrue(roster.allocatePrimary(soldier.id(), MarineWeapon.DMR,
                EquipmentGrade.SERVICE));
        soldier.addExperience(123);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 1);
        MarineLoadout seat = deployment.seat(0);

        assertNotNull(seat);
        assertEquals(soldier.id(), seat.campaignSoldierId);
        assertEquals(MarineWeapon.DMR, seat.primary);
        assertEquals(123, seat.soldierProfile.experienceXp());
        assertEquals(LayeredArmorFamily.CHARCOAL, seat.armorFamily);
        assertEquals(MarineArmorPattern.CHARCOAL.bonusHp, seat.armorBonusHp, 1e-6f);
        assertEquals(MarineArmorPattern.CHARCOAL.damageReduction,
                seat.armorDamageReduction, 1e-6f);
        assertEquals(MarineArmorPattern.CHARCOAL.moveSpeedMult,
                seat.armorMoveSpeedMult, 1e-6f);
        assertEquals(MarineArmorPattern.CHARCOAL.incomingAccuracyMult,
                seat.armorIncomingAccuracyMult, 1e-6f);
    }

    @Test
    void selectedFireteamIsLoadedBeforeOtherRosterPersonnel() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
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
