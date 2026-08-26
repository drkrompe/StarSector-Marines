package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineSeatCommitmentTest {

    @Test
    void roundTripsIdentityEquipmentArmorAndFullSquadTagByValue() {
        SoldierProfile profile = new SoldierProfile(SoldierAptitude.GIFTED, 412);
        MarineLoadout loadout = MarineLoadout.fromCatalog(
                UnitRole.PLANTER,
                new EliminateFactionObjective(Faction.MARINE, Faction.DEFENDER),
                WeaponRegistry.require(WeaponRegistry.DMR_ID),
                EquipmentGrade.MASTERWORK,
                profile,
                SpecialEquipmentRegistry.require(
                        SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                "marine-17",
                LayeredArmorFamily.BLUE_SCOUT,
                63f,
                4.5f,
                0.92f,
                0.81f,
                new CampaignSquadTag("squad-3", "Harrier", true, 9, 2));

        MarineSeatCommitment commitment = MarineSeatCommitment.capture(loadout);
        MarineLoadout restored = commitment.toLoadout();

        assertEquals("marine-17", restored.campaignSoldierId);
        assertEquals(WeaponRegistry.require(WeaponRegistry.DMR_ID).id, restored.primaryDef().id);
        assertEquals(EquipmentGrade.MASTERWORK, restored.equipmentGrade);
        assertEquals(profile, restored.soldierProfile);
        assertEquals(SpecialEquipmentRegistry.SMOKE_GRENADE_ID,
                restored.specialDef().id());
        assertEquals(LayeredArmorFamily.BLUE_SCOUT, restored.armorFamily);
        assertEquals(63f, restored.armorPool);
        assertEquals(4.5f, restored.armorRating);
        assertEquals(0.92f, restored.armorMoveSpeedMult);
        assertEquals(0.81f, restored.armorIncomingAccuracyMult);
        assertEquals("squad-3", restored.campaignSquad.squadId);
        assertEquals("Harrier", restored.campaignSquad.label);
        assertTrue(restored.campaignSquad.leader);
        assertEquals(9, restored.campaignSquad.strength);
        assertEquals(2, restored.campaignSquad.fireTeamIndex);
        assertEquals(UnitRole.COMBATANT, restored.role);
        assertNull(restored.objective);
        assertEquals(commitment, MarineSeatCommitment.capture(restored));
    }

    @Test
    void deploymentCommitmentsRetainOrderedValueEquality() {
        MarineSeatCommitment first = MarineSeatCommitment.capture(
                MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                        WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.SERVICE,
                        SoldierProfile.REGULAR, null, "marine-a",
                        LayeredArmorFamily.CHARCOAL, 50f, 4f, 1f, 1f,
                        new CampaignSquadTag("squad-a", "A", true, 2, 0)));
        MarineSeatCommitment second = MarineSeatCommitment.capture(
                MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                        WeaponRegistry.require(WeaponRegistry.SMG_ID), EquipmentGrade.SURPLUS,
                        new SoldierProfile(SoldierAptitude.STEADY, 23), null,
                        "marine-b", LayeredArmorFamily.ARMY_GREEN,
                        40f, 3f, 0.98f, 0.9f,
                        new CampaignSquadTag("squad-a", "A", false, 2, 1)));
        List<MarineSeatCommitment> commitments = List.of(first, second);

        CampaignMarineDeployment deployment =
                CampaignMarineDeployment.fromCommitments(commitments);

        assertEquals(commitments, deployment.commitments());
        assertEquals("marine-a", deployment.seat(0).campaignSoldierId);
        assertEquals("marine-b", deployment.seat(1).campaignSoldierId);
    }
}
