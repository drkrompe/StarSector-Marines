package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadEquipmentDoctrineTest {

    @Test
    void everyBuiltInLoadoutUsesAFactionLogoShippedByVanilla() {
        Path starsectorCore = Path.of(System.getProperty("starsectorDir"))
                .resolve("starsector-core").toAbsolutePath().normalize();
        List<String> loadoutIds = new ArrayList<>();
        SquadEquipmentDoctrines.weaponDoctrines().stream()
                .map(SquadWeaponDoctrine::id).forEach(loadoutIds::add);
        SquadEquipmentDoctrines.armorPlans().stream()
                .map(SquadArmorPlan::id).forEach(loadoutIds::add);

        for (String loadoutId : loadoutIds) {
            SquadLoadoutPresentationDef presentation =
                    SquadLoadoutPresentationRegistry.get(loadoutId);
            assertNotNull(presentation, loadoutId + " has authored presentation");
            Path logo = starsectorCore.resolve(presentation.factionLogo()).normalize();
            assertTrue(logo.startsWith(starsectorCore),
                    loadoutId + " must use an asset inside the vanilla install");
            assertTrue(Files.isRegularFile(logo),
                    loadoutId + " faction logo does not exist: " + logo);
        }
    }

    /**
     * Every authored plan issues a whole squad and is presentable.
     *
     * <p>This replaced a test that counted plans per power band. Bands were a
     * property of the old model, in which a doctrine named twelve concrete
     * patterns and therefore carried a tier — the thing
     * {@code role-and-access.md} separated. A plan has no tier: the same plan
     * issues tier-I kit to a poor company and tier-IV kit to a rich one.
     */
    @Test
    void everyArmorPlanIssuesAWholeSquadFromWhateverIsAvailable() {
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            assertEquals(MarineSquad.CAPACITY, plan.mix().billets().size(), plan.id());
            SquadArmorDoctrine issued = ArmorIssueResolver.resolveUnrestricted(plan);
            assertEquals(MarineSquad.CAPACITY, issued.issueIds().size(), plan.id());
            issued.issueIds().forEach(MarineArmorCatalogRegistry::require);
            assertNotNull(SquadLoadoutPresentationRegistry.get(plan.id()),
                    plan.id() + " has authored lore");
        }

        MarineArmorCatalogDef cordon =
                MarineArmorCatalogRegistry.require("armor.cordon-shell");
        MarineArmorCatalogDef lashplate =
                MarineArmorCatalogRegistry.require("armor.lashplate-harness");
        assertEquals(1, cordon.tier());
        assertEquals(1, lashplate.tier());
        assertEquals(LayeredArmorFamily.MILITIA, cordon.appearanceFamily());
        assertEquals(LayeredArmorFamily.OUTLAW, lashplate.appearanceFamily());
        assertTrue(cordon.armorCapacity()
                < MarineArmorCatalogRegistry.require("armor.militia").armorCapacity());
        assertTrue(lashplate.armorRating()
                < MarineArmorCatalogRegistry.require("armor.outlaw").armorRating());

        MarineArmorCatalogDef hegemonyLine = MarineArmorCatalogRegistry.require("armor.line");
        MarineArmorCatalogDef corporateLine =
                MarineArmorCatalogRegistry.require("armor.aegis-composite");
        MarineArmorCatalogDef churchLine = MarineArmorCatalogRegistry.require("armor.palatine");
        MarineArmorCatalogDef diktatLine =
                MarineArmorCatalogRegistry.require("armor.furnace-line");
        MarineArmorCatalogDef outlawLine = MarineArmorCatalogRegistry.require("armor.reaver");
        assertEquals(3, corporateLine.tier());
        assertEquals(LayeredArmorFamily.AEGIS_COMPOSITE,
                corporateLine.appearanceFamily());
        assertEquals(LayeredArmorFamily.PALATINE, churchLine.appearanceFamily());
        assertEquals(LayeredArmorFamily.FURNACE_LINE, diktatLine.appearanceFamily());
        assertEquals(LayeredArmorFamily.REAVER, outlawLine.appearanceFamily());
        assertTrue(corporateLine.moveSpeedMult() > hegemonyLine.moveSpeedMult());
        assertTrue(corporateLine.incomingAccuracyMult()
                < hegemonyLine.incomingAccuracyMult());
        assertTrue(churchLine.armorRating() > hegemonyLine.armorRating());
        assertTrue(diktatLine.armorCapacity() > hegemonyLine.armorCapacity());
        assertTrue(outlawLine.armorCapacity() > hegemonyLine.armorCapacity());
        assertTrue(outlawLine.armorRating() < hegemonyLine.armorRating());
        assertComparableProtection(hegemonyLine, List.of(corporateLine, churchLine,
                diktatLine, outlawLine, MarineArmorCatalogRegistry.require("armor.combat")));

        MarineArmorCatalogDef xiv = MarineArmorCatalogRegistry.require("armor.heavy");
        MarineArmorCatalogDef specter =
                MarineArmorCatalogRegistry.require("armor.specter-heavy");
        MarineArmorCatalogDef reliquary =
                MarineArmorCatalogRegistry.require("armor.reliquary-heavy");
        MarineArmorCatalogDef foundry =
                MarineArmorCatalogRegistry.require("armor.foundry-breaker");
        assertEquals(4, specter.tier());
        assertEquals(LayeredArmorFamily.SPECTER_HEAVY, specter.appearanceFamily());
        assertEquals(LayeredArmorFamily.BULWARK_HEAVY,
                MarineArmorCatalogRegistry.require("armor.bulwark-heavy")
                        .appearanceFamily());
        assertEquals(LayeredArmorFamily.RELIQUARY_HEAVY,
                reliquary.appearanceFamily());
        assertEquals(LayeredArmorFamily.LIONS_MANTLE,
                MarineArmorCatalogRegistry.require("armor.lions-mantle")
                        .appearanceFamily());
        assertEquals(LayeredArmorFamily.FOUNDRY_BREAKER,
                foundry.appearanceFamily());
        assertTrue(specter.moveSpeedMult() > xiv.moveSpeedMult());
        assertTrue(specter.armorCapacity() < xiv.armorCapacity());
        assertTrue(reliquary.armorRating() > xiv.armorRating());
        assertTrue(foundry.armorCapacity() > xiv.armorCapacity());
        assertTrue(foundry.armorRating() < xiv.armorRating());
        assertComparableProtection(xiv, List.of(specter, reliquary, foundry,
                MarineArmorCatalogRegistry.require("armor.bulwark-heavy"),
                MarineArmorCatalogRegistry.require("armor.lions-mantle")));
    }

    private static void assertComparableProtection(
            MarineArmorCatalogDef reference, List<MarineArmorCatalogDef> peers) {
        float referenceExposure = expectedDamageToBreak(reference, 7f);
        for (MarineArmorCatalogDef peer : peers) {
            float ratio = expectedDamageToBreak(peer, 7f) / referenceExposure;
            assertTrue(ratio >= 0.80f && ratio <= 1.20f,
                    peer.id() + " protection ratio " + ratio + " leaves its peer band");
        }
    }

    /** Expected aimed damage before armor breaks, including the suit's hit profile. */
    private static float expectedDamageToBreak(
            MarineArmorCatalogDef armor, float penetration) {
        return armor.armorCapacity()
                / DurabilityModel.armorEfficiency(penetration, armor.armorRating())
                / armor.incomingAccuracyMult();
    }

    @Test
    void patherAssaultDoctrineDoesNotMirrorStatePulseIssue() {
        SquadWeaponDoctrine doctrine = SquadEquipmentDoctrines.weaponById(
                SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS);

        assertNotNull(doctrine);
        assertFalse(doctrine.issues().stream().anyMatch(issue ->
                issue.primaryDef() == WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID)));
    }

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
            // Armour is issued per billet ROLE now, from what the roster owns,
            // so the pattern is not fixed by the plan. A starter company owns no
            // recon or support kit at all, which is the resolver's documented
            // fallback rather than a defect: you cannot field a scout until you
            // buy a scout suit, and a marine in the wrong suit beats a marine in
            // none.
            SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(
                    SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
            for (int local = 0; local < MarineSquad.TEAM_SIZE; local++) {
                int billet = leader + local;
                ArmorRole wanted = plan.roleAt(billet);
                ArmorRole worn = MarineArmorCatalogRegistry.require(
                        preview.billet(billet).armorId()).role();
                boolean ownsWanted = MarineArmorCatalogRegistry.installed().all().stream()
                        .anyMatch(pattern -> pattern.role() == wanted
                                && roster.armory().ownsArmorTemplate(pattern.id()));
                assertEquals(ownsWanted ? wanted : ArmorRole.LINE, worn,
                        "billet " + billet + " wanted a " + wanted.key);
            }
            for (int local = 1; local < MarineSquad.TEAM_SIZE; local++) {
                assertEquals(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                        preview.billet(leader + local).primaryDef());
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
        assertEquals(SquadEquipmentDoctrines.armorPlanById(
                        SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR).roleAt(1),
                MarineArmorCatalogRegistry.require(preview.billet(1).armorId()).role());
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
        // Two plans, one weapon doctrine: the weapons are identical above and
        // the armour is not, which is the whole claim in this test's name. The
        // literal patterns are no longer the plan's to fix — they come from what
        // the roster owns — so this asserts the difference rather than naming it.
        assertNotEquals(fatigues.billets().stream()
                        .map(SquadEquipmentBillet::armorId).toList(),
                combatArmor.billets().stream()
                        .map(SquadEquipmentBillet::armorId).toList(),
                "two armour plans should not issue the same twelve suits");
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

        // The refusal comes from the weapon side. An armour plan names roles
        // rather than patterns and is issued from what the armoury already owns,
        // so it degrades to worse kit instead of being refused for missing
        // stock — a change of behaviour, and a deliberate one: composition is
        // the plan's business and supply is the armoury's.
        String unaffordableWeapons = null;
        for (SquadWeaponDoctrine candidate : SquadEquipmentDoctrines.weaponDoctrines()) {
            if (!roster.armory().canAuthorWeaponDoctrine(candidate.issues())) {
                unaffordableWeapons = candidate.id();
                break;
            }
        }
        assertNotNull(unaffordableWeapons, "fixture assumption: a starter armoury cannot"
                + " author every authored weapon doctrine");
        assertEquals(SquadEquipmentResult.MISSING_TEMPLATE, roster.applySquadEquipment(
                squad.id(), unaffordableWeapons,
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
        // Only weapons are player-authored. Armour is a sheet, and the sheet
        // this squad is issued is a built-in plan resolved against what the
        // armoury owns ({@code role-and-access.md}).
        String sheet = SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR;

        assertTrue(roster.previewSquadEquipment(
                squad.id(), customWeapons.id(), sheet).canApply());
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), customWeapons.id(), sheet));
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
