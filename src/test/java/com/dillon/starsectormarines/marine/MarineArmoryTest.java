package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarineArmoryTest {

    @Test
    void namedRegularSquadExplicitlyRefitsIntoPowerSuitsWithoutLosingItsPeopleOrHistory() {
        MarineRoster roster = new MarineRoster();
        EquipmentTestFixtures.learnServiceKit(roster.armory());
        MarineSquad squad = roster.createSquad();
        roster.renameSquad(squad.id(), "Rooks");
        List<MarineSoldier> people = roster.recruitToSquad(squad.id(), MarineSquad.CAPACITY);
        String weapons = SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS;
        String regularPlan = SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR;
        assertEquals(SquadEquipmentResult.APPLIED,
                roster.applySquadEquipment(squad.id(), weapons, regularPlan));
        List<String> ids = List.copyOf(squad.memberIds());
        List<String> names = people.stream().map(MarineSoldier::name).toList();
        for (MarineSoldier person : people) {
            person.career().recordDeployment(true, false, 19, 7, 12f, 1f, 2f, 3);
        }
        squad.career().recordMission(true);
        SquadCareer formationHistory = squad.career();
        CampaignMarineDeployment before = CampaignMarineDeployment.freezeSelection(
                roster, Set.of(squad.id()), MarineSquad.CAPACITY);

        EquipmentTestFixtures.learnSheet(roster.armory(), SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR);
        assertEquals(SquadEquipmentResult.APPLIED,
                roster.applySquadEquipment(squad.id(), weapons, regularPlan));
        CampaignMarineDeployment stillRegular = CampaignMarineDeployment.freezeSelection(
                roster, Set.of(squad.id()), MarineSquad.CAPACITY);
        assertTrue(stillRegular.commitments().stream().allMatch(
                seat -> seat.armorClass() == ArmorClass.STANDARD));
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), weapons, SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR));
        CampaignMarineDeployment upgraded = CampaignMarineDeployment.freezeSelection(
                roster, Set.of(squad.id()), MarineSquad.CAPACITY);
        SquadArmorDoctrine upgradedKit = roster.armory().armorDoctrineById(
                SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR);

        assertSame(squad, roster.squadById(squad.id()));
        assertEquals("Rooks", squad.name());
        assertEquals(ids, squad.memberIds());
        assertSame(formationHistory, squad.career());
        assertEquals(1, squad.career().missionsDeployed());
        assertEquals(1, squad.career().missionsWon());
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            MarineSoldier person = roster.soldierById(ids.get(billet));
            assertSame(people.get(billet), person);
            assertEquals(names.get(billet), person.name());
            assertEquals(1, person.career().missionsDeployed());
            assertEquals(1, person.career().missionsWon());
            assertEquals(19, person.career().roundsFired());
            assertEquals(7, person.career().roundsHit());
            assertEquals(12f, person.career().damageDealt());
            assertEquals(1f, person.career().friendlyFireDamage());
            assertEquals(2f, person.career().damageTaken());
            assertEquals(3, person.career().kills());
            assertEquals(0, person.career().timesWounded());
            var seat = upgraded.commitments().get(billet);
            assertEquals(before.commitments().get(billet).campaignSoldierId(), seat.campaignSoldierId());
            assertEquals(before.commitments().get(billet).campaignSquadId(), seat.campaignSquadId());
            assertEquals(before.commitments().get(billet).campaignFireTeamIndex(), seat.campaignFireTeamIndex());
            assertEquals(MarineArmorCatalogRegistry.require(upgradedKit.issueId(billet)).armorCapacity(),
                    seat.armorCapacity());
            assertEquals(ArmorClass.POWER_SUIT, seat.armorClass());
        }
    }

    @Test
    void heavyPlansAreUnavailableUntilOwnedAndRegularPlansNeverConvert() {
        MarineArmory armory = new MarineArmory();
        SquadArmorPlan heavy = SquadEquipmentDoctrines.armorPlanById(
                SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR);
        assertFalse(armory.canIssue(heavy));
        assertNull(armory.armorDoctrineById(heavy.id()));
        assertTrue(armory.armorDoctrines().stream().noneMatch(d -> d.id().equals(heavy.id())));
        EquipmentTestFixtures.learnSheet(armory, SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR);
        assertTrue(armory.canIssue(heavy));
        assertTrue(armory.issue(heavy).issueIds().stream().allMatch(
                id -> MarineArmorCatalogRegistry.require(id).armorClass() == ArmorClass.POWER_SUIT));
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            if (!plan.isMixedClass() && plan.armorClass() == ArmorClass.STANDARD) {
                assertTrue(armory.issue(plan).issueIds().stream().allMatch(
                        id -> MarineArmorCatalogRegistry.require(id).armorClass() == ArmorClass.STANDARD));
            }
        }
    }

    @Test
    void mixedSheetRequiresItsCardAndRetainsNamedSeatsAcrossSave() throws Exception {
        MarineRoster roster = new MarineRoster();
        EquipmentTestFixtures.learnServiceKit(roster.armory());
        MarineSquad squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), MarineSquad.CAPACITY);
        List<String> ids = List.copyOf(squad.memberIds());
        SquadArmorPlan mixed = SquadEquipmentDoctrines.armorPlanById(
                SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR);
        assertFalse(roster.armory().canIssue(mixed));
        EquipmentTestFixtures.learnSheet(roster.armory(), mixed.id());
        assertTrue(roster.armory().canIssue(mixed));
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, mixed.id()));
        MarineRoster loaded = roundTrip(roster);
        assertEquals(ids, loaded.squadById(squad.id()).memberIds());
        assertEquals(mixed.id(), loaded.squadById(squad.id()).armorDoctrineId());
        CampaignMarineDeployment deployed = CampaignMarineDeployment.freezeSelection(
                loaded, Set.of(squad.id()), MarineSquad.CAPACITY);
        assertEquals(8, deployed.commitments().stream()
                .filter(seat -> seat.armorClass() == ArmorClass.STANDARD).count());
        assertEquals(4, deployed.commitments().stream()
                .filter(seat -> seat.armorClass() == ArmorClass.POWER_SUIT).count());
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            assertEquals(ids.get(billet), deployed.commitments().get(billet).campaignSoldierId());
            assertEquals(mixed.armorClassAt(billet), deployed.commitments().get(billet).armorClass());
        }
    }

    @Test
    void newCompanyKeepsOnlyBaselineTechnologyAcrossLoadAndFirstVictory() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(24);
        Set<String> baseline = Set.of(
                EquipmentTemplateCatalog.primaryId(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SURPLUS),
                EquipmentTemplateCatalog.armorId(MarineArmorPattern.ARMORLESS),
                EquipmentTemplateCatalog.armorId("armor.cordon-shell"));
        assertEquals(baseline, roster.armory().ownedEquipmentTemplateIds());
        for (MarineSoldier soldier : roster.soldiers()) {
            assertEquals(WeaponRegistry.STARTER_PRIMARY_ID, soldier.primaryId());
            assertEquals("armor.cordon-shell", soldier.armorId());
            assertEquals(EquipmentGrade.SURPLUS, soldier.primaryGrade());
            assertNull(soldier.specialEquipmentId());
        }
        MarineArmory loaded = roundTrip(roster).armory();
        loaded.repairCollectionProgression(Set.of());
        loaded.recordVictory(false);
        assertEquals(baseline, loaded.ownedEquipmentTemplateIds());
        loaded.unlockPrimary(WeaponRegistry.DMR_ID, EquipmentGrade.SERVICE);
        assertTrue(roundTrip(loaded).ownsPrimaryTemplate(WeaponRegistry.DMR_ID, EquipmentGrade.SERVICE));
    }

    @Test
    void starterRefittingAndDeploymentKeepTierOneKitWhileOlderAcquisitionsSurviveLoad() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        MarineSquad squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
        MarineRoster loaded = roundTrip(roster);
        var deployment = CampaignMarineDeployment.freezeSelection(loaded, Set.of(squad.id()), 12);
        assertEquals(12, deployment.size());
        for (var seat : deployment.commitments()) {
            assertEquals(EquipmentGrade.SURPLUS, seat.equipmentGrade());
            assertEquals(3f, seat.armorCapacity());
            assertEquals(2f, seat.armorRating());
            assertNull(seat.integralSystem());
        }

        roster.armory().unlockPrimary(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE);
        roster.armory().unlockArmor(MarineArmorPattern.MILITIA);
        // Kit set on a marine by hand is not the squad's: the cards dress the seat again.
        for (MarineSoldier soldier : roster.soldiers()) {
            soldier.setPrimary(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE);
            soldier.setArmor(MarineArmorPattern.MILITIA);
        }
        MarineRoster veteranSave = roundTrip(roster);
        assertTrue(veteranSave.armory().ownsPrimaryTemplate(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE));
        assertTrue(veteranSave.armory().ownsArmorTemplate(MarineArmorPattern.MILITIA));
        for (MarineSoldier soldier : veteranSave.squadMembers(veteranSave.squadById(squad.id()))) {
            assertEquals(EquipmentGrade.SURPLUS, soldier.primaryGrade());
            assertNotEquals(MarineArmorPattern.MILITIA.id, soldier.armorId());
        }
    }

    @Test
    void newRecruitStartsWithBasicFieldRifle() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);

        MarineSoldier recruit = roster.activeSoldiers().get(0);
        assertEquals(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), recruit.primaryDef());
        assertEquals(EquipmentGrade.SURPLUS, recruit.primaryGrade());
    }

    @Test
    void saveLoadReinstallsCurrentBuiltInFireSupportDefinition() throws Exception {
        MarineArmory loaded = roundTrip(new MarineArmory());
        FireTeamTemplateCard fireSupport = loaded.templateCardById(
                FireTeamTemplateCards.FIRE_SUPPORT_ID);

        assertNotNull(fireSupport);
        assertEquals(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), fireSupport.billet(1).primaryDef());
    }

    @Test
    void legacyRecipeOwnershipMigratesToEquipmentTemplateCards() throws Exception {
        MarineArmory armory = new MarineArmory();
        armory.unlockPrimary(WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK);
        armory.unlockSecondary(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        Field ownedTemplates = MarineArmory.class.getDeclaredField(
                "ownedEquipmentTemplateIds");
        ownedTemplates.setAccessible(true);
        ownedTemplates.set(armory, null);

        MarineArmory loaded = roundTrip(armory);

        assertTrue(loaded.ownsPrimaryTemplate(
                WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK));
        assertTrue(loaded.ownsSpecialTemplate(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID)));
    }







    @Test
    void fallenSoldiersStayPersistentlyKiaAndSurvivorsStayOnDuty() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2);
        MarineSoldier survivor = roster.activeSoldiers().get(0);
        MarineSoldier fallen = roster.activeSoldiers().get(1);

        roster.applySoldierOutcome(Set.of(survivor.id()), Set.of(fallen.id()));

        assertEquals(MarineSoldierStatus.ACTIVE, survivor.status());
        assertEquals(MarineSoldierStatus.KIA, fallen.status());
        roster.applySoldierOutcome(Collections.emptySet(), Set.of(fallen.id()));
        assertEquals(MarineSoldierStatus.KIA, fallen.status());
    }

    @Test
    void antiMaterielTemplatePersistsTheStableSpecialIdentity() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);

        MarineSoldier carrier = roster.soldierById(squad.teamMembers(0).get(3));
        carrier.setSpecialEquipment(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID);
        assertEquals(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                carrier.specialEquipmentId());

        MarineSoldier persisted = roundTrip(carrier);
        assertEquals(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                persisted.specialEquipmentId());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), persisted.specialEquipmentDef());
    }

    @Test
    void breachTemplateIssuesAReusableKitWithAStableIdentity() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);

        MarineSoldier carrier = roster.soldierById(squad.teamMembers(0).get(1));
        carrier.setSpecialEquipment(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID,
                carrier.specialEquipmentId());

        MarineSoldier persisted = roundTrip(carrier);
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID,
                persisted.specialEquipmentId());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), persisted.specialEquipmentDef());
    }







    private static FireTeamBillet billet(String name, WeaponDef primary,
                                          EquipmentGrade grade, SpecialEquipmentDef secondary,
                                          MarineArmorPattern armor) {
        return new FireTeamBillet(name, primary, grade, secondary, armor);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
