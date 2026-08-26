package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadEquipmentDoctrineTest {

    @Test
    void fleetAssaultDoctrineCarriesExactlyOneFragKit() {
        SquadWeaponDoctrine doctrine = SquadEquipmentDoctrines.weaponById(
                SquadEquipmentDoctrines.ASSAULT_WEAPONS);
        assertNotNull(doctrine);
        assertEquals(1, doctrine.issues().stream()
                .filter(issue -> issue.specialDef() == SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID))
                .count());
    }

    @Test
    void starterDefinitionsConcentrateBetterIssueOnEachFireTeamLeader() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        SquadEquipmentPreview preview = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);

        assertTrue(preview.canApply());
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            int leader = team * MarineSquad.TEAM_SIZE;
            assertEquals(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), preview.billet(leader).primaryDef());
            assertEquals(MarineArmorPattern.MILITIA, preview.billet(leader).armor());
            for (int local = 1; local < MarineSquad.TEAM_SIZE; local++) {
                assertEquals(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), preview.billet(leader + local).primaryDef());
                assertEquals(MarineArmorPattern.ARMORLESS, preview.billet(leader + local).armor());
            }
        }
    }

    @Test
    void fireSupportDoctrineIssuesOneSquadAutomaticPerTeamFromStarterStock() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);

        SquadEquipmentPreview preview = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);

        assertTrue(preview.canApply());
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            assertEquals(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID),
                    preview.billet(team * MarineSquad.TEAM_SIZE + 1).primaryDef());
        }
    }

    @Test
    void appliesOneWeaponAndOneArmorDefinitionAcrossAllTwelveBillets() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);

        SquadEquipmentPreview preview = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);

        assertTrue(preview.canApply());
        assertEquals(MarineSquad.CAPACITY, preview.billets().size());
        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID), preview.billet(1).primaryDef());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), preview.billet(1).specialDef());
        assertEquals(MarineArmorPattern.ARMORLESS, preview.billet(1).armor());
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));

        List<String> members = roster.manningMemberIds(squad);
        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID), roster.soldierById(members.get(1)).primaryDef());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                roster.soldierById(members.get(1)).specialEquipmentDef());
        assertEquals(SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                squad.weaponDoctrineId());
        assertEquals(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR,
                squad.armorDoctrineId());
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            assertNull(squad.teamTemplateCardId(team),
                    "new squad intent retires stale per-team assignment ids");
        }
    }

    @Test
    void weaponSelectionOwnsSpecialsWhileArmorSelectionChangesOnlyProtection() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        SquadEquipmentPreview fatigues = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        SquadEquipmentPreview combatArmor = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR);

        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            assertEquals(fatigues.billet(billet).primaryDef(), combatArmor.billet(billet).primaryDef());
            assertEquals(fatigues.billet(billet).grade(), combatArmor.billet(billet).grade());
            assertEquals(fatigues.billet(billet).specialEquipmentId(),
                    combatArmor.billet(billet).specialEquipmentId());
        }
        assertEquals(MarineArmorPattern.MILITIA, fatigues.billet(0).armor());
        assertEquals(MarineArmorPattern.CHARCOAL, combatArmor.billet(0).armor());
    }

    @Test
    void failedSquadIssueLeavesIntentAndEveryMaterializedKitUntouched() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
        List<WeaponDef> priorWeapons = roster.manningMemberIds(squad).stream()
                .map(roster::soldierById).map(MarineSoldier::primaryDef).toList();
        String priorWeaponDoctrine = squad.weaponDoctrineId();
        String priorArmorDoctrine = squad.armorDoctrineId();

        assertEquals(SquadEquipmentResult.MISSING_TEMPLATE, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR));

        assertEquals(priorWeapons, roster.manningMemberIds(squad).stream()
                .map(roster::soldierById).map(MarineSoldier::primaryDef).toList());
        assertEquals(priorWeaponDoctrine, squad.weaponDoctrineId());
        assertEquals(priorArmorDoctrine, squad.armorDoctrineId());
    }

    @Test
    void degradedSquadCannotPartiallyIssueAValidDefinitionPair() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY - 1);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(SquadEquipmentResult.SQUAD_NOT_READY,
                roster.applySquadEquipment(squad.id(),
                        SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                        SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
        assertNull(squad.weaponDoctrineId());
        assertNull(squad.armorDoctrineId());
    }

    @Test
    void squadIssueConsumesChangedIncomingCargoAtomicallyAndMatchingKitIsFree() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        TestResources resources = new TestResources(EquipmentTemplateCost.ZERO);
        List<WeaponDef> priorWeapons = roster.manningMemberIds(squad).stream()
                .map(roster::soldierById).map(MarineSoldier::primaryDef).toList();

        SquadEquipmentPreview blocked = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR, resources);
        assertFalse(blocked.issueCost().isZero());
        assertEquals(SquadEquipmentResult.INSUFFICIENT_CARGO, blocked.result());
        assertEquals(SquadEquipmentResult.INSUFFICIENT_CARGO, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR, resources));
        assertEquals(0, resources.spendCalls);
        assertNull(squad.weaponDoctrineId());
        assertEquals(priorWeapons, roster.manningMemberIds(squad).stream()
                .map(roster::soldierById).map(MarineSoldier::primaryDef).toList());

        resources.available = blocked.issueCost();
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR, resources));
        assertEquals(1, resources.spendCalls);
        assertEquals(EquipmentTemplateCost.ZERO, resources.available);

        SquadEquipmentPreview matching = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR, resources);
        assertEquals(EquipmentTemplateCost.ZERO, matching.issueCost());
        assertTrue(matching.canApply());
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR, resources));
        assertEquals(1, resources.spendCalls);
    }

    @Test
    void squadDoctrineIdsAndMaterializedSpecialIssuePersist() throws Exception {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));

        MarineRoster loaded = roundTrip(roster);
        MarineSquad persisted = loaded.squadById(squad.id());
        assertEquals(SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                persisted.weaponDoctrineId());
        assertEquals(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR,
                persisted.armorDoctrineId());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                loaded.soldierById(loaded.manningMemberIds(persisted).get(1)).specialEquipmentDef());
    }

    @Test
    void playerAuthoredDefinitionsPersistAndUseTheAuthoritativeIssueTransaction() throws Exception {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        SquadWeaponDoctrine customWeapons = roster.armory().createWeaponDoctrine(
                "My Fleet Issue",
                SquadEquipmentDoctrines.weaponById(
                        SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS).issues());
        SquadArmorDoctrine customArmor = roster.armory().createArmorDoctrine(
                "My Field Protection",
                SquadEquipmentDoctrines.armorById(
                        SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR).issues());

        assertTrue(roster.previewSquadEquipment(
                squad.id(), customWeapons.id(), customArmor.id()).canApply());
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), customWeapons.id(), customArmor.id()));
        assertFalse(roster.deleteWeaponDoctrine(customWeapons.id()),
                "an assigned custom definition remains protected");

        MarineRoster loaded = roundTrip(roster);
        assertEquals("My Fleet Issue",
                loaded.armory().weaponDoctrineById(customWeapons.id()).displayName());
        assertEquals(customWeapons.id(), loaded.squadById(squad.id()).weaponDoctrineId());
    }

    @Test
    void legacyThreeTemplateIntentMigratesWithoutReissuingCurrentKits() throws Exception {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        squad.setTeamTemplateCardId(0, FireTeamTemplateCards.FIELD_ID);
        squad.setTeamTemplateCardId(1, FireTeamTemplateCards.RECON_ID);
        squad.setTeamTemplateCardId(2, FireTeamTemplateCards.FIRE_SUPPORT_ID);
        List<WeaponDef> before = roster.manningMemberIds(squad).stream()
                .map(roster::soldierById).map(MarineSoldier::primaryDef).toList();

        MarineRoster loaded = roundTrip(roster);
        MarineSquad migrated = loaded.squadById(squad.id());

        assertNotNull(migrated.weaponDoctrineId());
        assertNotNull(migrated.armorDoctrineId());
        assertNotNull(loaded.armory().weaponDoctrineById(migrated.weaponDoctrineId()));
        assertEquals(before, loaded.manningMemberIds(migrated).stream()
                .map(loaded::soldierById).map(MarineSoldier::primaryDef).toList());
        assertEquals(FireTeamTemplateCards.FIELD_ID, migrated.teamTemplateCardId(0),
                "migration preserves compatibility intent until the next successful issue");
    }

    private static MarineRoster fullSquad() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        return roster;
    }

    private static final class TestResources implements EquipmentIssueResources {
        private EquipmentTemplateCost available;
        private int spendCalls;

        private TestResources(EquipmentTemplateCost available) {
            this.available = available;
        }

        @Override
        public EquipmentTemplateCost available() {
            return available;
        }

        @Override
        public boolean spend(EquipmentTemplateCost cost) {
            if (!available.covers(cost)) return false;
            spendCalls++;
            available = new EquipmentTemplateCost(
                    available.supplies() - cost.supplies(),
                    available.heavyArmaments() - cost.heavyArmaments(),
                    available.heavyMachinery() - cost.heavyMachinery(),
                    available.food() - cost.food());
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
