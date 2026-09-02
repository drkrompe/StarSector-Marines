package com.dillon.starsectormarines.ops.spec;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the copy factory says about each kind of subject.
 *
 * <p>Asserts against the same resolvers the simulation uses rather than against
 * literals, so the sheet is pinned to the shipped catalog rather than to a
 * number somebody typed twice.
 */
class SpecSheetsTest {

    private static final SoldierProfile SHOOTER = SoldierProfile.REGULAR;

    @Test
    void aWeaponSheetScalesWithGradeAndAgreesWithTheCombatResolver() {
        WeaponDef rifle = WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);

        SpecSheet surplus = SpecSheets.weapon(rifle, EquipmentGrade.SURPLUS);
        SpecSheet milspec = SpecSheets.weapon(rifle, EquipmentGrade.MILSPEC);

        assertTrue(damage(milspec) > damage(surplus),
                "Milspec issue should hit harder than surplus: "
                        + damage(surplus) + " -> " + damage(milspec));
        assertEquals(quoted(InfantryCombatStats.damage(rifle, EquipmentGrade.SURPLUS)),
                statValue(surplus, "DMG"));
        assertEquals(quoted(InfantryCombatStats.damage(rifle, EquipmentGrade.MILSPEC)),
                statValue(milspec, "DMG"));
        assertEquals(quoted(InfantryCombatStats.range(rifle, EquipmentGrade.MILSPEC)),
                statValue(milspec, "RNG"));

        assertEquals(rifle.catalogName(EquipmentGrade.MILSPEC), milspec.title());
        assertTrue(milspec.subtitle().contains(rifle.designation(EquipmentGrade.MILSPEC)));
        assertTrue(milspec.subtitle().contains(EquipmentGrade.MILSPEC.displayName));
        assertEquals(SpecSheets.ACCENT_WEAPON, milspec.accent());
        assertEquals(rifle.catalogFactionLogo, milspec.crestPath());
        assertEquals(List.of("DMG", "RNG", "ACC", "DPS"),
                milspec.stats().stream().map(SpecSheet.Stat::label).toList());
        assertTrue(milspec.stats().stream().allMatch(SpecSheet.Stat::hasMeter));
        assertFalse(milspec.notes().isEmpty(), "a weapon carries an authored field note");
    }

    /**
     * The accuracy meter is the one that is not measured against the catalog:
     * a hit chance is already a fraction of one, so its own ceiling is one.
     */
    @Test
    void aWeaponSheetMeasuresAccuracyAgainstCertaintyRatherThanTheCatalog() {
        WeaponDef rifle = WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);
        SpecSheet sheet = SpecSheets.weapon(rifle, EquipmentGrade.SERVICE);
        float accuracy = InfantryCombatStats.accuracy(rifle, EquipmentGrade.SERVICE, SHOOTER);
        assertEquals(accuracy, stat(sheet, "ACC").fill(), 0.001f);
    }

    @Test
    void anArmourSheetCarriesItsIntegralSystemAsItsOwnParagraph() {
        MarineArmorCatalogDef carrier = patternWithSystem();
        MarineArmorCatalogDef bare = patternWithoutSystem();

        SpecSheet carried = SpecSheets.armor(carrier);
        SpecSheet plain = SpecSheets.armor(bare);

        assertEquals(carrier.displayName(), carried.title());
        assertEquals(carrier.tradition().factionLogo(), carried.crestPath());
        assertEquals(SpecSheets.ACCENT_ARMOR, carried.accent());
        assertTrue(carried.subtitle().startsWith("TIER " + carrier.tier()));
        assertTrue(carried.subtitle().contains(carrier.role().displayName()));
        assertTrue(carried.subtitle().contains(carrier.tradition().displayName()));
        assertEquals(List.of("ARMOR", "RESIST", "MOVE", "EVASION"),
                carried.stats().stream().map(SpecSheet.Stat::label).toList());

        assertEquals(plain.notes().size() + 1, carried.notes().size(),
                "a carried system is exactly one more paragraph than the same pattern without");
        String system = carried.notes().get(carried.notes().size() - 1);
        assertTrue(system.startsWith(IntegralSystemCopy.flavorName(carrier)),
                "the system's paragraph leads with what this tradition calls it: " + system);
        assertTrue(system.contains(IntegralSystemCopy.summary(carrier)));
    }

    @Test
    void anIntegralSystemSheetDelegatesToTheCopyThatOwnsIt() {
        MarineArmorCatalogDef carrier = patternWithSystem();
        SpecSheet sheet = SpecSheets.integralSystem(carrier);

        assertEquals(IntegralSystemCopy.flavorName(carrier), sheet.title());
        assertEquals(IntegralSystemCopy.summary(carrier), sheet.subtitle());
        assertEquals(IntegralSystemCopy.iconPath(carrier), sheet.crestPath());
        assertEquals(SpecSheets.ACCENT_SYSTEM, sheet.accent());
        assertTrue(sheet.notes().contains(IntegralSystemCopy.detail(carrier)));

        assertThrows(IllegalArgumentException.class,
                () -> SpecSheets.integralSystem(patternWithoutSystem()),
                "a pattern carrying nothing has no system sheet to write");
    }

    @Test
    void aSpecialSheetQuotesTheGunBehindItAtTheValuesItActuallyFiresAt() {
        SpecialEquipmentDef rocket =
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);
        SpecSheet sheet = SpecSheets.special(rocket);

        assertEquals(rocket.displayName(), sheet.title());
        assertEquals(rocket.catalogSubtitle(), sheet.subtitle());
        assertEquals(rocket.catalogFactionLogo(), sheet.crestPath());
        assertEquals(SpecSheets.ACCENT_SPECIAL, sheet.accent());
        // No equipment grade is applied on the special firing path, so the sheet
        // quotes the weapon's own numbers. Service grade is numerically the same
        // and is asserted alongside so a future grade-aware path fails here.
        assertEquals(quoted(rocket.damage()), statValue(sheet, "DMG"));
        assertEquals(quoted(InfantryCombatStats.damage(rocket.weaponDef(),
                EquipmentGrade.SERVICE)), statValue(sheet, "DMG"));
        assertEquals(quoted(rocket.range()), statValue(sheet, "RNG"));
        assertTrue(statValue(sheet, "AMMO").contains(String.valueOf(rocket.startingAmmo())));
        assertFalse(sheet.notes().isEmpty());
    }

    @Test
    void aSpecialWithNoWeaponShowsNoWeaponRows() {
        SpecialEquipmentDef smoke =
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        SpecSheet sheet = SpecSheets.special(smoke);
        boolean armed = smoke.weaponId() != null && !smoke.weaponId().isBlank();
        assertEquals(armed, sheet.stats().stream()
                .anyMatch(stat -> stat.label().equals("DMG")));
    }

    @Test
    void everyMechVariantHasAFieldNoteAndFiveMeters() {
        for (MechVariant variant : MechVariant.values()) {
            SpecSheet sheet = SpecSheets.mech(variant);
            assertEquals(variant.displayName, sheet.title());
            assertEquals(SpecSheets.ACCENT_MECH, sheet.accent());
            assertFalse(sheet.subtitle().isBlank(), variant + " needs a designation and role");
            assertFalse(sheet.notes().isEmpty(), variant + " needs a field note");
            assertTrue(sheet.notes().get(0).length() > 80,
                    variant + " should carry real provenance, not a label");
            assertEquals(List.of("STRUCTURE", "ARMOR", "SPEED", "ACCURACY", "VISION"),
                    sheet.stats().stream().map(SpecSheet.Stat::label).toList());
            assertTrue(sheet.stats().stream().allMatch(SpecSheet.Stat::hasMeter));
        }
    }

    /**
     * A chassis's fitted hardware is named in the sheet, and a symmetric pair is
     * one line rather than the same sentence twice.
     */
    @Test
    void aMechSheetNamesTheHardwareItIsCarryingOnce() {
        SpecSheet bulwark = SpecSheets.mech(MechVariant.BULWARK);
        long armLines = bulwark.notes().stream()
                .filter(note -> note.startsWith(MechVariant.BULWARK.arms.displayName)).count();
        long podLines = bulwark.notes().stream()
                .filter(note -> note.startsWith(
                        MechVariant.BULWARK.leftShoulder.displayName)).count();
        assertEquals(1, armLines);
        assertEquals(1, podLines, "matched shoulders are described once");
        assertEquals(3, bulwark.notes().size(), "chassis prose plus two pieces of hardware");
    }

    @Test
    void everyMechComponentHasItsOwnSheet() {
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            SpecSheet sheet = SpecSheets.mechWeapon(component);
            assertEquals(component.displayName, sheet.title());
            assertEquals(SpecSheets.ACCENT_MECH, sheet.accent());
            assertFalse(sheet.subtitle().isBlank(), component + " needs a designation");
            assertFalse(sheet.notes().isEmpty(), component + " needs a field note");
        }
    }

    @Test
    void aCardResolvesToItsEquipmentAndLeadsWithItsAccessTier() {
        EquipmentTemplateCard primary = firstCard(EquipmentTemplateCard.Kind.PRIMARY);
        SpecSheet sheet = SpecSheets.card(primary);
        WeaponDef weapon = WeaponRegistry.require(primary.equipmentId());

        assertEquals(weapon.catalogName(primary.grade()), sheet.title(),
                "the title stays the equipment's; a card is a way of owning one");
        assertTrue(sheet.subtitle().startsWith(titleCase(primary.accessTier().name())),
                "the card's access tier leads: " + sheet.subtitle());
        assertTrue(sheet.subtitle().contains(primary.grade().displayName + " issue"));
        assertTrue(sheet.subtitle().contains(weapon.catalogRole));
        assertEquals(SpecSheets.ACCENT_WEAPON, sheet.accent());
        assertEquals(SpecSheets.weapon(weapon, primary.grade()).stats(), sheet.stats());

        EquipmentTemplateCard armorCard = firstCard(EquipmentTemplateCard.Kind.ARMOR);
        SpecSheet armorSheet = SpecSheets.card(armorCard);
        assertEquals(MarineArmorCatalogRegistry.require(armorCard.equipmentId()).displayName(),
                armorSheet.title());
        assertEquals(SpecSheets.ACCENT_ARMOR, armorSheet.accent());
        assertFalse(armorSheet.subtitle().contains(" issue"),
                "an armour card issues at no grade, so it names none: " + armorSheet.subtitle());

        EquipmentTemplateCard specialCard = firstCard(EquipmentTemplateCard.Kind.SPECIAL);
        SpecSheet specialSheet = SpecSheets.card(specialCard);
        assertEquals(SpecialEquipmentRegistry.require(specialCard.equipmentId()).displayName(),
                specialSheet.title());
        assertEquals(SpecSheets.ACCENT_SPECIAL, specialSheet.accent());
    }

    private static EquipmentTemplateCard firstCard(EquipmentTemplateCard.Kind kind) {
        assertNotNull(EquipmentTemplateCatalog.installed(),
                "the equipment template catalog is installed for tests");
        return EquipmentTemplateCatalog.all().stream().filter(card -> card.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " card in the catalog"));
    }

    private static MarineArmorCatalogDef patternWithSystem() {
        return MarineArmorCatalogRegistry.installed().all().stream()
                .filter(MarineArmorCatalogDef::hasIntegralSystem).findFirst()
                .orElseThrow(() -> new AssertionError("no pattern carries an integral system"));
    }

    private static MarineArmorCatalogDef patternWithoutSystem() {
        return MarineArmorCatalogRegistry.installed().all().stream()
                .filter(pattern -> !pattern.hasIntegralSystem()).findFirst()
                .orElseThrow(() -> new AssertionError("every pattern carries a system"));
    }

    private static SpecSheet.Stat stat(SpecSheet sheet, String label) {
        return sheet.stats().stream().filter(row -> row.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + label + " row on " + sheet.title()));
    }

    private static String statValue(SpecSheet sheet, String label) {
        return stat(sheet, label).value();
    }

    private static float damage(SpecSheet sheet) {
        return Float.parseFloat(statValue(sheet, "DMG"));
    }

    private static String quoted(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String titleCase(String constant) {
        return constant.charAt(0) + constant.substring(1).toLowerCase(Locale.ROOT);
    }
}
