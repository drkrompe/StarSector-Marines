package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class FirstRefitTest {
    @Test void reliefRewardIsExplicitAndIdempotentAndNeverIssuesKit() {
        MarineRoster roster = company();
        var soldier = roster.soldiers().get(0);
        String oldArmor = soldier.armorId(), oldPrimary = soldier.primaryId();
        assertFalse(roster.firstRefit().claim(roster));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.recurringId);
        assertFalse(roster.firstRefit().claim(roster));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertTrue(roster.firstRefit().claim(roster));
        int owned = roster.armory().ownedEquipmentTemplateIds().size();
        assertFalse(roster.firstRefit().claim(roster));
        assertEquals(owned, roster.armory().ownedEquipmentTemplateIds().size());
        assertEquals(oldArmor, soldier.armorId());
        assertEquals(oldPrimary, soldier.primaryId());
        assertTrue(roster.armory().ownsPrimaryTemplate(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.MILSPEC));
        assertTrue(roster.armory().ownsArmorTemplate(MarineArmorPattern.BLUE_SCOUT));
        assertEquals(2, FirstRefit.Path.values().length);
        assertFalse(roster.deleteWeaponDoctrine(FirstRefit.Path.STEADY_LINE.weapons));
        assertFalse(roster.armory().renameWeaponDoctrine(FirstRefit.Path.STEADY_LINE.weapons, "Changed"));
    }

    @Test void bothPathsIssueExactTwelveAndCompleteOnlyOnce() {
        for (var path : FirstRefit.Path.values()) {
            MarineRoster roster = claimed();
            var squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
            var preview = roster.previewSquadEquipment(squad.id(), path.weapons, path.armor);
            assertEquals(0, preview.issueCost().heavyArmaments(), "The opening refit must use legal civilian-market cargo");
            assertEquals(0, preview.issueCost().food());
            assertTrue(preview.issueCost().isZero());
            Hold hold = new Hold(preview.issueCost());
            assertEquals(SquadEquipmentResult.APPLIED, roster.firstRefit().issue(roster, squad.id(), path, hold));
            assertEquals(EquipmentTemplateCost.ZERO, hold.stock);
            assertEquals(0, hold.spends);
            for (int i = 0; i < 12; i++) {
                var soldier = roster.soldierById(roster.manningMemberIds(squad).get(i));
                assertEquals(preview.billet(i).primaryId(), soldier.primaryId());
                assertEquals(preview.billet(i).grade(), soldier.primaryGrade());
                assertEquals(preview.billet(i).armorId(), soldier.armorId());
            }
            assertFalse(FirstRefit.available(roster));
            assertEquals(SquadEquipmentResult.INVALID_SQUAD, roster.firstRefit().issue(roster, squad.id(), path, hold));
            assertEquals(0, hold.spends);
        }
    }

    @Test void stationingLeavesTheOrderUnchanged() {
        {
            MarineRoster roster = claimed();
            var squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
            var soldier = roster.soldierById(roster.manningMemberIds(squad).get(0));
            var path = FirstRefit.Path.STEADY_LINE;
            String oldWeapon = soldier.primaryId(), oldArmor = soldier.armorId(), oldDoctrine = squad.weaponDoctrineId();
            Hold hold = new Hold(EquipmentTemplateCost.ZERO);
            squad.setStationingContractId(1);
            assertNotEquals(SquadEquipmentResult.APPLIED, roster.firstRefit().issue(roster, squad.id(), path, hold));
            assertEquals(0, hold.spends);
            assertEquals(oldWeapon, soldier.primaryId());
            assertEquals(oldArmor, soldier.armorId());
            assertEquals(oldDoctrine, squad.weaponDoctrineId());
            assertFalse(roster.firstRefit().complete());
        }
    }

    @Test void woundedAndUnderstrengthSquadsIssueOnlyToCurrentOccupants() {
        MarineRoster roster = claimed();
        var squad = roster.squads().get(0);
        var members = roster.manningMemberIds(squad);
        var wounded = roster.soldierById(members.get(0));
        roster.applySoldierOutcome(Map.of(wounded.id(), MarineSoldierStatus.WIA,
                members.get(10), MarineSoldierStatus.KIA, members.get(11), MarineSoldierStatus.MIA), 10f, 5f);
        float recovery = wounded.unavailableUntilDay();
        var dead = roster.soldierById(members.get(10));
        var missing = roster.soldierById(members.get(11));
        String deadKit = dead.primaryId(), missingKit = missing.primaryId();
        var path = FirstRefit.Path.FORWARD_SCREEN;
        var preview = roster.previewSquadEquipment(squad.id(), path.weapons, path.armor);
        assertTrue(preview.canApply());
        Hold hold = new Hold(preview.issueCost());
        assertEquals(SquadEquipmentResult.APPLIED, roster.firstRefit().issue(roster, squad.id(), path, hold));
        assertTrue(roster.firstRefit().complete());
        assertEquals(0, hold.spends);
        assertEquals(EquipmentTemplateCost.ZERO, hold.stock);
        assertEquals(MarineSoldierStatus.WIA, wounded.status());
        assertEquals(recovery, wounded.unavailableUntilDay());
        assertEquals(preview.billet(0).primaryId(), wounded.primaryId());
        assertEquals(preview.billet(0).grade(), wounded.primaryGrade());
        assertEquals(deadKit, dead.primaryId()); assertEquals(missingKit, missing.primaryId());
        assertEquals(10, roster.manningCount(squad));
        assertEquals(2, roster.vacancies(squad));
        assertTrue(roster.previewSquadEquipment(squad.id(), path.weapons, path.armor).issueCost().isZero());
    }

    @Test void emptySquadStoresItsPlanAndReplacementsCanBeFittedForFree() {
        var roster = new MarineRoster();
        var squad = roster.createSquad();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.firstRefit().claim(roster);
        var path = FirstRefit.Path.FORWARD_SCREEN;
        var hold = new Hold(EquipmentTemplateCost.ZERO);
        assertEquals(SquadEquipmentResult.APPLIED, roster.firstRefit().issue(roster, squad.id(), path, hold));
        assertEquals(0, hold.spends);
        assertEquals(path.weapons, squad.weaponDoctrineId());
        assertEquals(0, roster.manningCount(squad));
        roster.recruitToSquad(squad.id(), 1);
        var next = roster.previewSquadEquipment(squad.id(), path.weapons, path.armor);
        assertTrue(next.issueCost().isZero(), "Replacement equipment is also free once learned");
    }

    @Test void savedRewardAndCompletionSurviveReloadAndMissingLegacyStateIsSafe() throws Exception {
        MarineRoster roster = claimed();
        var saved = roundTrip(roster.firstRefit());
        assertTrue(saved.claimed());
        assertFalse(saved.complete());
        var squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
        roster.firstRefit().issue(roster, squad.id(), FirstRefit.Path.FORWARD_SCREEN, EquipmentIssueResources.UNLIMITED);
        assertEquals(squad.id(), roundTrip(roster.firstRefit()).issuedSquadId());
        var field = MarineRoster.class.getDeclaredField("firstRefit");
        field.setAccessible(true); field.set(roster, null);
        assertNotNull(roster.firstRefit());
        assertFalse(roster.firstRefit().claimed());
    }

    private static FirstRefit roundTrip(FirstRefit value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (FirstRefit) in.readObject(); }
    }
    private static MarineRoster company() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12); return roster;
    }
    private static MarineRoster claimed() {
        MarineRoster roster = company();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.firstRefit().claim(roster); return roster;
    }
    private static final class Hold implements EquipmentIssueResources {
        EquipmentTemplateCost stock; int spends;
        Hold(EquipmentTemplateCost stock) { this.stock = stock; }
        public EquipmentTemplateCost available() { return stock; }
        public boolean spend(EquipmentTemplateCost cost) {
            if (!stock.covers(cost)) return false;
            stock = new EquipmentTemplateCost(stock.supplies() - cost.supplies(), stock.heavyArmaments() - cost.heavyArmaments(),
                    stock.heavyMachinery() - cost.heavyMachinery(), stock.food() - cost.food());
            spends++; return true;
        }
    }
}
