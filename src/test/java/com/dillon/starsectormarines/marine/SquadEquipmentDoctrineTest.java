package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadEquipmentDoctrineTest {

    @Test
    void fireSupportDoctrineIssuesOneSquadAutomaticPerTeamFromStarterStock() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);

        SquadEquipmentPreview preview = roster.previewSquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);

        assertTrue(preview.canApply());
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            assertEquals(MarineWeapon.SQUAD_AUTOMATIC,
                    preview.billet(team * MarineSquad.TEAM_SIZE + 1).primary());
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
        assertEquals(MarineWeapon.SMG, preview.billet(1).primary());
        assertEquals(MarineSecondary.SATCHEL_CHARGE, preview.billet(1).special());
        assertEquals(MarineArmorPattern.ARMORLESS, preview.billet(1).armor());
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));

        List<String> members = roster.manningMemberIds(squad);
        assertEquals(MarineWeapon.SMG, roster.soldierById(members.get(1)).primary());
        assertEquals(MarineSecondary.SATCHEL_CHARGE,
                roster.soldierById(members.get(1)).secondary());
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
            assertEquals(fatigues.billet(billet).primary(), combatArmor.billet(billet).primary());
            assertEquals(fatigues.billet(billet).grade(), combatArmor.billet(billet).grade());
            assertEquals(fatigues.billet(billet).specialEquipmentId(),
                    combatArmor.billet(billet).specialEquipmentId());
        }
        assertEquals(MarineArmorPattern.ARMORLESS, fatigues.billet(0).armor());
        assertEquals(MarineArmorPattern.CHARCOAL, combatArmor.billet(0).armor());
    }

    @Test
    void failedSquadIssueLeavesIntentAndEveryMaterializedKitUntouched() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
        List<MarineWeapon> priorWeapons = roster.manningMemberIds(squad).stream()
                .map(roster::soldierById).map(MarineSoldier::primary).toList();
        String priorWeaponDoctrine = squad.weaponDoctrineId();
        String priorArmorDoctrine = squad.armorDoctrineId();

        assertEquals(SquadEquipmentResult.LOCKED_RECIPE, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS,
                SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR));

        assertEquals(priorWeapons, roster.manningMemberIds(squad).stream()
                .map(roster::soldierById).map(MarineSoldier::primary).toList());
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
        assertEquals(MarineSecondary.SATCHEL_CHARGE,
                loaded.soldierById(loaded.manningMemberIds(persisted).get(1)).secondary());
    }

    private static MarineRoster fullSquad() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        return roster;
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
