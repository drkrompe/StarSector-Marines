package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.EquipmentTestFixtures;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.StatMeter;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetArmoryViewModelTest {

    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/company/company-resource-stock.mlx", "mod/data/ui/components/company/company-resource-cost.mlx", "mod/data/ui/components/marine-ops-page-nav.mlx",
            "mod/data/ui/components/armory/fleet-armory.mlx",
            "mod/data/ui/components/armory/armory-squad-list.mlx",
            "mod/data/ui/components/armory/fleet-armory-fireteam.mlx",
            "mod/data/ui/components/armory/armory-weapon-tech-card.mlx", "mod/data/ui/components/armory/armory-squad-doctrine.mlx",
            "mod/data/ui/components/armory/armory-refit-transaction.mlx",
            "mod/data/ui/components/armory/armory-armor-comparison.mlx");

    @Test
    void startingCompanyHasOneRealIssueAndLearningSpecsAddsDistinctChoices() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(12);
        FleetArmoryViewModel model = new FleetArmoryViewModel(new Reactor(), roster);
        assertEquals(List.of("Dockside Issue"), model.weaponDoctrineTiles().get().stream().map(FleetArmoryViewModel.DoctrineTile::name).toList());
        assertEquals(List.of("Watchman's Kit"), model.armorDoctrineTiles().get().stream().map(FleetArmoryViewModel.DoctrineTile::name).toList());
        assertEquals("12 LINE", model.weaponDoctrineTiles().get().get(0).composition());
        var members = roster.manningMemberIds(roster.squadById(model.selectedSquadId()));
        List<String> before = members.stream().map(id -> roster.soldierById(id).armorDef().id()).toList();
        EquipmentTestFixtures.learnServiceKit(roster.armory());
        model.refresh();
        assertTrue(model.weaponDoctrineTiles().get().size() > 1);
        assertTrue(model.armorDoctrineTiles().get().size() > 1);
        var issues = model.armorDoctrineTiles().get().stream().map(tile ->
                roster.armory().armorDoctrineById(tile.id().substring("armor-doctrine:".length())).issueIds()).toList();
        assertEquals(issues.size(), issues.stream().distinct().count());
        assertEquals(before, members.stream().map(id -> roster.soldierById(id).armorDef().id()).toList(),
                "learning specifications must not silently fit existing personnel");
    }

    @Test
    void weaponRoleCountsCombineGradesAndKeepNamedQuantitiesVisible() {
        List<SquadWeaponIssue> issues = new ArrayList<>();
        for (int i = 0; i < 12; i++) issues.add(new SquadWeaponIssue("Marine",
                i < 3 ? WeaponRegistry.DMR_ID : WeaponRegistry.STARTER_PRIMARY_ID,
                i % 2 == 0 ? EquipmentGrade.SERVICE : EquipmentGrade.MILSPEC, null));
        var doctrine = new SquadWeaponDoctrine("test:mixed-grades", "Mixed marksmen", "", issues);
        var tile = FleetArmoryViewModel.weaponTile("test:card", doctrine, false, () -> {});
        assertEquals("3 MARKSMAN  ·  9 LINE", tile.composition());
        assertTrue(tile.distribution().contains("3 Longbow"));
        assertTrue(tile.distribution().contains("9 Rook"));
    }

    @Test
    void transactionProjectionAndApplyUseTheRosterAuthority() {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(reactor, roster);

        SquadEquipmentPreview authoritative = roster.previewSquadEquipment(
                viewModel.selectedSquadId(), viewModel.selectedWeaponDoctrineId(),
                viewModel.selectedArmorDoctrineId());
        assertEquals(authoritative.result(),
                viewModel.currentSquadEquipmentPreview().result());
        assertEquals(MarineSquad.TEAM_SIZE, viewModel.marineCards().get().size());
        assertTrue(viewModel.marineCards().get().get(0).name().contains(" "));
        FleetArmoryViewModel.MarineViewerCard firstMarine =
                viewModel.marineCards().get().get(0);
        assertEquals(List.of("DMG", "RNG", "ACC", "DPS"), firstMarine.weaponStats()
                .stream().map(StatMeter::label).toList());
        assertEquals(List.of("HEALTH", "ARMOR", "RESIST", "SPEED"), firstMarine.armorStats()
                .stream().map(StatMeter::label).toList());
        assertTrue(firstMarine.weaponStats().stream().allMatch(stat ->
                stat.fillStyle().matches("width: \\d{1,3}%;")));
        assertTrue(Arrays.stream(ArmorClass.values())
                        .anyMatch(armorClass -> armorClass.displayName().equals(firstMarine.unitClass())),
                "unexpected equipment class on the card: " + firstMarine.unitClass());
        assertEquals("W II", firstMarine.weaponBadge());
        assertEquals("A II", firstMarine.armorBadge());
        // The lore the card used to carry for its own popup is now the sheet's,
        // written from the item's owning catalog rather than from a view-model prop.
        assertTrue(String.join(" ", firstMarine.primarySheet().notes()).length() > 80);
        assertTrue(String.join(" ", firstMarine.armorSheet().notes()).length() > 80);
        assertNotNull(firstMarine.primaryFactionLogo());
        assertFalse(firstMarine.primaryFactionLogoClasses().contains("faction-logo-hidden"));
        assertEquals(firstMarine.armorFactionLogo(),
                firstMarine.systemFactionLogo());
        assertTrue(viewModel.feedbackText().get().startsWith(
                "Hover equipment names for field notes."));
        FleetArmoryViewModel.DoctrineTile assignedLoadout =
                viewModel.weaponDoctrineTiles().get().stream()
                        .filter(tile -> tile.id().endsWith(
                                viewModel.selectedWeaponDoctrineId()))
                        .findFirst().orElseThrow();
        assertEquals("Common", assignedLoadout.rarity());
        assertTrue(assignedLoadout.metadata().contains("TIER I"));
        assertEquals("graphics/factions/neutral_traders.png",
                assignedLoadout.factionLogo());
        assertTrue(assignedLoadout.description().length() > 120);
        assertTrue(viewModel.weaponDoctrineTiles().get().size()
                < roster.armory().weaponDoctrines().size());
        // The picker and armory expose the same plans whose carrier class the
        // company holds; missing roles may fall back within that class.
        assertEquals(roster.armory().armorDoctrines().stream().map(SquadArmorDoctrine::issueIds).distinct().count(),
                viewModel.armorDoctrineTiles().get().size());
        viewModel.showLoadoutFilterAction(FleetArmoryViewModel.LoadoutFilter.RARE).run();
        assertEquals(FleetArmoryViewModel.LoadoutFilter.RARE, viewModel.loadoutFilter());
        assertTrue(viewModel.weaponDoctrineTiles().get().stream()
                .allMatch(loadout -> "Rare".equals(loadout.rarity())));
        assertTrue(viewModel.loadoutBrowserSummary().get().contains(" of "));

        assertEquals(SquadEquipmentResult.APPLIED,
                viewModel.applySquadEquipmentSelection());
        MarineSquad squad = roster.squadById(viewModel.selectedSquadId());
        assertEquals(viewModel.selectedWeaponDoctrineId(), squad.weaponDoctrineId());
        assertEquals(viewModel.selectedArmorDoctrineId(), squad.armorDoctrineId());
        assertTrue(viewModel.feedbackText().get().contains("issued"));
    }

    @Test
    void companyAuthoredLoadoutsDoNotBorrowAFactionFlag() {
        MarineRoster roster = fullSquad();
        SquadWeaponDoctrine source = SquadEquipmentDoctrines.weaponById(
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        SquadWeaponDoctrine custom = roster.armory().createWeaponDoctrine(
                "Company Pattern", source.issues());
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        FleetArmoryViewModel.DoctrineTile tile = viewModel.weaponDoctrineTiles().get()
                .stream().filter(candidate -> candidate.id().endsWith(custom.id()))
                .findFirst().orElseThrow();
        assertNull(tile.factionLogo());
        assertTrue(tile.factionLogoClasses().contains("faction-logo-hidden"));
        assertTrue(tile.metadata().contains("Company-authored"));
    }

    @Test
    void understrengthSquadCanApplyEquipment() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);
        assertEquals(SquadEquipmentResult.APPLIED,
                viewModel.currentSquadEquipmentPreview().result());
        assertFalse(viewModel.applyDisabled().get());
        assertEquals(SquadEquipmentResult.APPLIED,
                viewModel.applySquadEquipmentSelection());
        assertNotNull(roster.squads().get(0).weaponDoctrineId());
        assertNotNull(roster.squads().get(0).armorDoctrineId());
    }

    @Test
    void shippedComponentsKeepKeyedIdentityAcrossBilletInspection() throws Exception {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel model = new FleetArmoryViewModel(reactor, roster);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(Path.of(path)), COMPONENTS);
        try (MarkupInstance instance = loader.reloadAndBuild(reactor, "fleet-armory-fireteam", props(model))) {
            UiDocument document = new UiDocument(instance.root()).theme(MarineOpsThemes.standard());
            instance.styles().forEach(document::addStyleSheet);
            document.layout(1920, 1080);
            UiElement list = instance.requireElement("weapon-doctrine-list");
            UiElement first = list.childAt(0);
            UiElement billet = instance.requireElement("loadout-billet:0");
            UiElement comparison = instance.requireElement("loadout-item:0");
            var alternative = model.weaponDoctrineTiles().get().get(1);
            alternative.select().run();
            instance.flush();
            document.layout(1920, 1080);
            assertSame(first, list.childAt(0));
            assertTrue(instance.requireElement(alternative.id()).selected());
            String weapon = model.selectedWeaponDoctrineId();
            String title = (String) model.inspection().get().get("title");
            UiElement next = instance.requireElement("loadout-billet:4");
            document.pointerDown(centerX(next), centerY(next));
            document.pointerUp(centerX(next), centerY(next));
            instance.flush();
            assertNotEquals(title, model.inspection().get().get("title"));
            assertSame(billet, instance.requireElement("loadout-billet:0"));
            assertSame(comparison, instance.requireElement("loadout-item:0"));
            assertEquals(weapon, model.selectedWeaponDoctrineId());
            assertTrue(next.hasClass("selected"));
            assertEquals(model.currentSquadEquipmentPreview().canApply(),
                    !instance.requireElement("apply-squad-equipment").disabled());
            model.showArmorPickerAction().run(); instance.flush();
            assertTrue(instance.requireElement("show-armor-picker").hasClass("selected"));
            assertTrue(instance.requireElement("weapon-doctrine-slot").hasClass("picker-hidden"));
            assertFalse(instance.requireElement("armor-doctrine-slot").hasClass("picker-hidden"));
        }
    }

    @Test
    void currentAndProposedEquipmentOpenSharedSpecSheetsWithoutMovingComparison() throws Exception {
        Reactor reactor = new Reactor();
        FleetArmoryViewModel model = new FleetArmoryViewModel(reactor, fullSquad());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(Path.of(path)), COMPONENTS);
        try (MarkupInstance instance = loader.reloadAndBuild(reactor, "fleet-armory-fireteam", props(model))) {
            UiDocument document = new UiDocument(instance.root()).theme(MarineOpsThemes.standard());
            instance.styles().forEach(document::addStyleSheet);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            ArmorySpecSheets.bindInspection(binder, instance, model.inspection());
            document.layout(1920, 1080);
            assertEquals(6, binder.size());
            for (int item = 0; item < 3; item++) for (String side : List.of("current", "proposed"))
                assertTrue(binder.isBound(instance.requireElement("loadout-item:" + item + ":" + side)));
            UiElement stats = instance.requireElement("loadout-item:0:stat:DMG");
            var box = stats.box().borderBox();
            UiElement target = instance.requireElement("loadout-item:1:current");
            document.pointerMoved(centerX(target), centerY(target));
            binder.update(); document.advance(0f);
            assertTrue(layer.visible());
            assertSame(target, binder.openTarget());
            assertEquals(box, stats.box().borderBox());
            assertTrue(layer.element().box().borderBox().right() <= 1920.01f);
            assertTrue(layer.element().box().borderBox().bottom() <= 1080.01f);
        }
    }

    /**
     * A loadout card's issue line names catalog items, so each entry on it is a
     * subject rather than a run of text: a weapon at the grade this loadout
     * issues it, an armour pattern, or a capability whose one carrier can be
     * named ({@code company-view-nouns.md}). The card itself is a loadout and
     * carries no sheet of its own.
     */
    @Test
    void everyLoadoutCardIssueEntryOpensItsOwnSpecSheet() throws Exception {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(reactor, roster);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(reactor, "fleet-armory-fireteam",
                props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            ArmorySpecSheets.bindDoctrineTiles(
                    binder, instance, viewModel.weaponDoctrineTiles());
            ArmorySpecSheets.bindDoctrineTiles(
                    binder, instance, viewModel.armorDoctrineTiles());
            document.layout(1920f, 1080f);

            List<FleetArmoryViewModel.DoctrineTile> weapons =
                    viewModel.weaponDoctrineTiles().get();
            List<FleetArmoryViewModel.DoctrineTile> armor =
                    viewModel.armorDoctrineTiles().get();
            assertFalse(weapons.isEmpty(), "no weapon loadout is known, so this proves nothing");
            assertFalse(armor.isEmpty(), "no tactic sheet is known, so this proves nothing");

            int entries = 0;
            for (FleetArmoryViewModel.DoctrineTile tile : concat(weapons, armor)) {
                for (FleetArmoryViewModel.DoctrineSpan span : concatSpans(tile)) {
                    assertTrue(binder.isBound(instance.requireElement(span.id())),
                            span.id() + " is a name in the issue line and is not askable");
                    entries++;
                }
                assertFalse(binder.isBound(instance.requireElement(tile.nameId())),
                        "a loadout is a definition, not a catalog item");
            }
            assertEquals(entries, binder.size());

            // The joined sentence and the spans are the same line said twice.
            FleetArmoryViewModel.DoctrineTile card = weapons.get(0);
            List<String> texts = new ArrayList<>();
            for (FleetArmoryViewModel.DoctrineSpan span : card.distributionSpans()) {
                texts.add(span.text());
            }
            assertEquals(card.distribution(), String.join("  ·  ", texts));

            // A weapon entry is quoted at the grade this loadout issues it at.
            SquadWeaponDoctrine doctrine = roster.armory().weaponDoctrines().stream()
                    .filter(known -> card.id().endsWith(":" + known.id()))
                    .findFirst().orElseThrow();
            int weaponEntries = 0;
            for (FleetArmoryViewModel.DoctrineSpan span : card.distributionSpans()) {
                WeaponDef issued = primaryNamedBy(span.text());
                if (issued == null) continue;
                weaponEntries++;
                assertEquals(issued.catalogName(issuedGrade(doctrine, issued)),
                        span.sheet().title(),
                        span.text() + " is not described as this loadout issues it");
            }
            assertTrue(weaponEntries > 0,
                    "the first weapon loadout lists no weapon, so this proves nothing");

            // A tactic sheet's ISSUED entries are the armour patterns themselves.
            FleetArmoryViewModel.DoctrineTile sheet = armor.get(0);
            assertFalse(sheet.distributionSpans().isEmpty());
            for (FleetArmoryViewModel.DoctrineSpan span : sheet.distributionSpans()) {
                MarineArmorCatalogDef pattern = patternNamedBy(span.text());
                assertNotNull(pattern, span.text() + " names no known armour pattern");
                assertEquals(pattern.displayName(), span.sheet().title());
            }

            // And the binding actually answers the pointer, on the picker that
            // is laid out — a hidden picker's rows have no box to point at.
            FleetArmoryViewModel.DoctrineSpan first = card.distributionSpans().get(0);
            UiElement target = instance.requireElement(first.id());
            assertFalse(layer.visible());
            document.pointerMoved(centerX(target), centerY(target));
            binder.update();
            document.advance(0f);
            assertTrue(layer.visible());
            assertSame(target, binder.openTarget());
            assertEquals(first.sheet().title(),
                    instance.requireElement(SpecSheetLayer.ELEMENT_ID + "-title").text());
        }
    }

    private static List<FleetArmoryViewModel.DoctrineTile> concat(
            List<FleetArmoryViewModel.DoctrineTile> first,
            List<FleetArmoryViewModel.DoctrineTile> second) {
        List<FleetArmoryViewModel.DoctrineTile> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<FleetArmoryViewModel.DoctrineSpan> concatSpans(
            FleetArmoryViewModel.DoctrineTile tile) {
        List<FleetArmoryViewModel.DoctrineSpan> all =
                new ArrayList<>(tile.distributionSpans());
        all.addAll(tile.carriesSpans());
        return all;
    }

    /** The marine primary an entry such as {@code 3 Field Rifle} counts, or null. */
    private static WeaponDef primaryNamedBy(String text) {
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            if (weapon.mount == MountClass.MARINE_PRIMARY
                    && text.endsWith(" " + weapon.modelName())) {
                return weapon;
            }
        }
        return null;
    }

    private static MarineArmorCatalogDef patternNamedBy(String text) {
        for (MarineArmorCatalogDef pattern : MarineArmorCatalogRegistry.installed().all()) {
            if (text.endsWith(" " + pattern.displayName())) return pattern;
        }
        return null;
    }

    /** The grade most of this loadout's carriers of one weapon are issued at. */
    private static EquipmentGrade issuedGrade(SquadWeaponDoctrine doctrine, WeaponDef weapon) {
        Map<EquipmentGrade, Integer> counts = new LinkedHashMap<>();
        for (SquadWeaponIssue issue : doctrine.issues()) {
            if (issue.primaryId().equals(weapon.id)) counts.merge(issue.grade(), 1, Integer::sum);
        }
        EquipmentGrade quoted = null;
        int best = 0;
        for (Map.Entry<EquipmentGrade, Integer> issued : counts.entrySet()) {
            if (quoted == null || issued.getValue() > best
                    || (issued.getValue() == best && issued.getKey().tier > quoted.tier)) {
                quoted = issued.getKey();
                best = issued.getValue();
            }
        }
        return quoted;
    }

    @Test
    void playerFacingMarkupUsesDoctrineLanguageAndHasNoLegacyPicker() throws Exception {
        for (String path : COMPONENTS) {
            String source = Files.readString(Path.of(path)).toLowerCase(Locale.ROOT);
            assertFalse(source.matches("(?s).*\\b(deck|hand|consume)\\b.*"), path);
            assertFalse(source.contains("armory-template-library"), path);
            assertFalse(source.contains("change loadout"), path);
            assertFalse(source.contains("missing cards"), path);
        }
    }

    @Test
    void armorComparisonListsEveryHeldPatternSortedByTierThenName() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        List<MarineArmorCatalogDef> expectedOrder = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : MarineArmorCatalogRegistry.installed().all()) {
            if (roster.armory().ownsArmorTemplate(pattern.id())) expectedOrder.add(pattern);
        }
        expectedOrder.sort(Comparator.comparingInt(MarineArmorCatalogDef::tier)
                .thenComparing(MarineArmorCatalogDef::displayName));

        List<FleetArmoryViewModel.ArmorComparisonCard> cards =
                viewModel.armorComparisonCards().get();
        assertEquals(expectedOrder.size(), cards.size());
        for (int index = 0; index < expectedOrder.size(); index++) {
            assertEquals(expectedOrder.get(index).displayName(), cards.get(index).name());
            MarineArmorCatalogDef armor = expectedOrder.get(index);
            assertEquals(armor.armorClass().displayName(), cards.get(index).unitClass());
            assertEquals("ROLE · " + armor.role().displayName() + "\n" + armor.description(),
                    cards.get(index).description());
        }
        assertTrue(viewModel.armorComparisonSummary().get()
                .startsWith(expectedOrder.size() + " of "));
    }

    /**
     * <b>The decision surface has to carry the decision.</b> A sheet's other two
     * lines say which roles it organises and which patterns fill them; neither
     * tells a player that one sheet fields seven braces and another fields four
     * breachers, unless they have the catalog memorised. The capability line is
     * derived from the issue rather than authored, so it stays true as the
     * company's stock improves.
     */
    @Test
    void everyTacticSheetSaysWhatItsTwelveBilletsWouldCarry() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        int inspected = 0;
        for (FleetArmoryViewModel.DoctrineTile tile : viewModel.armorDoctrineTiles().get()) {
            String planId = tile.id().substring("armor-doctrine:".length());
            SquadArmorDoctrine issued = roster.armory().armorDoctrines().stream()
                    .filter(doctrine -> doctrine.id().equals(planId))
                    .findFirst().orElseThrow();

            Map<String, Integer> expected = new LinkedHashMap<>();
            for (String issueId : issued.issueIds()) {
                MarineArmorCatalogDef pattern = MarineArmorCatalogRegistry.installed().get(issueId);
                if (pattern != null && pattern.hasIntegralSystem()) {
                    expected.merge(pattern.integralSystem().familyName(), 1, Integer::sum);
                }
            }
            if (expected.isEmpty()) {
                assertEquals("Nothing beyond plate and training", tile.carries(),
                        planId + " issues nothing that carries a system, and a blank line"
                                + " reads as a line that failed to render");
                continue;
            }
            inspected++;
            for (Map.Entry<String, Integer> family : expected.entrySet()) {
                assertTrue(tile.carries().contains(family.getValue() + " " + family.getKey()),
                        planId + " puts " + family.getValue() + " x " + family.getKey()
                                + " in the field and does not say so: " + tile.carries());
            }
        }
        assertTrue(inspected > 0,
                "no armour sheet issues a single capability, so this proves nothing");
    }

    @Test
    void armorComparisonReadsTheSameIntegralSystemCopyEveryOtherSurfaceUses() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        MarineArmorCatalogDef withSystem = MarineArmorCatalogRegistry.installed().all().stream()
                .filter(MarineArmorCatalogDef::hasIntegralSystem)
                .filter(armor -> roster.armory().ownsArmorTemplate(armor.id()))
                .findFirst().orElseThrow();
        MarineArmorCatalogDef withoutSystem = MarineArmorCatalogRegistry.installed().all().stream()
                .filter(armor -> !armor.hasIntegralSystem())
                .filter(armor -> roster.armory().ownsArmorTemplate(armor.id()))
                .findFirst().orElseThrow();

        Map<String, FleetArmoryViewModel.ArmorComparisonCard> byName = new LinkedHashMap<>();
        for (FleetArmoryViewModel.ArmorComparisonCard card : viewModel.armorComparisonCards().get()) {
            byName.put(card.name(), card);
        }

        FleetArmoryViewModel.ArmorComparisonCard carrierCard = byName.get(withSystem.displayName());
        FleetArmoryViewModel.ArmorComparisonCard bareCard = byName.get(withoutSystem.displayName());
        assertEquals(IntegralSystemCopy.tile(withSystem), carrierCard.system());
        assertTrue(carrierCard.systemClasses().contains("tone-accent"));
        assertEquals(IntegralSystemCopy.tile(withoutSystem), bareCard.system());
        assertEquals("No integral system", bareCard.system());
        assertTrue(bareCard.systemClasses().contains("tone-muted"));

        assertNotNull(carrierCard.systemIcon(), "a carried system has a family icon to show");
        assertEquals(IntegralSystemCopy.iconPath(withSystem), carrierCard.systemIcon());
        assertNull(bareCard.systemIcon(), "a pattern carrying nothing offers no icon");

        for (FleetArmoryViewModel.ArmorComparisonCard card : viewModel.armorComparisonCards().get()) {
            assertEquals(List.of("ARMOR", "RESIST", "MOVE", "EVASION"), card.stats().stream()
                    .map(StatMeter::label).toList());
        }
    }

    @Test
    void armorComparisonMarkupBindsEveryHeldPatternWithoutMissingElements()
            throws Exception {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(reactor, roster);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("armorComparisonSummary", viewModel.armorComparisonSummary());
        props.put("armorComparisonCards", viewModel.armorComparisonCards());
        props.put("backToFireTeams", (Runnable) () -> { });
        props.put("back", (Runnable) () -> { });
        putPageNavigation(props);

        try (MarkupInstance instance = loader.build(reactor, "armory-armor-comparison", props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(1920f, 1080f);

            assertFalse(viewModel.armorComparisonCards().get().isEmpty(),
                    "a fresh company holds something to compare");
            for (FleetArmoryViewModel.ArmorComparisonCard card
                    : viewModel.armorComparisonCards().get()) {
                assertEquals(card.name(), instance.requireElement(card.nameId()).text());
                assertEquals(card.description(),
                        instance.requireElement(card.descriptionId()).text());
                assertEquals(card.system(), instance.requireElement(card.systemId()).text());
                assertEquals(card.systemIcon(),
                        instance.requireElement(card.systemIconId()).imageSource());
            }
        }
    }

    @Test
    void woundedMarinesShowRemainingHoursAndKeepTheirBillets() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new LinkedHashMap<>();
        outcome.put(squad.memberIds().get(0), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(outcome, 100f, 1.25f);
        roster.recruitToSquad(roster.reserveSquad().id());
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                new Reactor(), roster, () -> { }, () -> 100d);

        assertEquals("WIA  ·  RTD in 1d 6h", viewModel.marineCards().get().get(0).status());
        assertTrue(viewModel.marineCards().get().get(0).classes().contains("status-wia"));
        assertEquals("1 WIA  ·  RTD 1d 6h",
                viewModel.squadCards().get().get(0).recovery());
        assertTrue(viewModel.reinforceDisabled().get(), "WIA personnel still hold billets");
    }

    @Test
    void squadCardReinforcesEveryOpenBilletFromReadyReserveInOneClick() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new LinkedHashMap<>();
        outcome.put(squad.memberIds().get(0), MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(outcome, 20f, 1f);
        roster.recruitToSquad(roster.reserveSquad().id());
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        FleetArmoryViewModel.SquadCard card = viewModel.squadCards().get().get(0);
        assertEquals("Reinforce +1", card.reinforceLabel());
        card.reinforce().run();

        assertEquals(0, roster.vacancies(squad));
        assertTrue(viewModel.feedbackText().get().contains("reinforced"));
        assertEquals(MarineSquad.TEAM_SIZE,
                roster.teamMemberIds(squad, 2).size());
    }

    @Test
    void freeFittingKeepsPersonnelCommodityPresentation() {
        MarineRoster roster = fullSquad();
        EquipmentIssueResources resources = presentedHold();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                new Reactor(), roster, () -> { }, () -> 0d, resources);

        viewModel.weaponDoctrineTiles().get().stream()
                .filter(tile -> !tile.id().endsWith(viewModel.selectedWeaponDoctrineId()))
                .findFirst().orElseThrow().select().run();

        List<CompanyResources.Row> costs = viewModel.issueCargoRows().get();
        assertTrue(costs.isEmpty());
        assertEquals("vanilla/" + Commodities.MARINES,
                viewModel.squadCards().get().get(0).reinforceIcon());
    }

    @Test
    void emptyCompanyCanFoundItsFirstSquadFromPresentedCargo() throws Exception {
        MarineRoster roster = new MarineRoster();
        Reactor reactor = new Reactor();
        PresentedFoundingHold founding = new PresentedFoundingHold(100);
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                reactor, roster, () -> { }, () -> 0d, presentedHold(), founding);

        assertTrue(viewModel.squadCards().get().isEmpty());
        assertFalse(viewModel.foundingDisabled().get());
        assertTrue(viewModel.foundingCargoRows().get().stream()
                .allMatch(row -> row.icon().startsWith("vanilla/")));

        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, "fleet-armory",
                props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(1920f, 1080f);
            click(document, instance.requireElement("found-squad"));
        }

        assertEquals(1, viewModel.squadCards().get().size());
        assertEquals(MarineSquad.CAPACITY,
                roster.manningCount(roster.squadById(viewModel.selectedSquadId())));
        assertTrue(viewModel.foundingFeedbackText().get().contains("founded"));
    }

    @Test
    void squadCardBodyInspectsWhileReinforceRemainsAnIndependentAction() throws Exception {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new LinkedHashMap<>();
        outcome.put(squad.memberIds().get(0), MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(outcome, 20f, 1f);
        roster.recruitToSquad(roster.reserveSquad().id());
        AtomicInteger inspections = new AtomicInteger();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                reactor, roster, inspections::incrementAndGet);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(reactor, "fleet-armory",
                props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(1920f, 1080f);

            FleetArmoryViewModel.SquadCard card = viewModel.squadCards().get().get(0);
            UiElement inspect = instance.requireElement(card.openId());
            UiElement reinforce = instance.requireElement(card.reinforceId());
            assertSame(inspect, instance.requireElement(card.nameId()).parent());
            assertSame(instance.requireElement(card.id()), reinforce.parent());
            assertFalse(reinforce.disabled());

            click(document, inspect);
            assertEquals(1, inspections.get());
            assertEquals(1, roster.vacancies(squad));

            click(document, reinforce);
            assertEquals(1, inspections.get());
            assertEquals(0, roster.vacancies(squad),
                    "inspect=" + inspect.box() + " reinforce=" + reinforce.box()
                            + " card=" + reinforce.parent().box());
        }
    }

    private static void click(UiDocument document, UiElement element) {
        float x = centerX(element);
        float y = centerY(element);
        document.pointerDown(x, y);
        document.pointerUp(x, y);
    }

    private static float centerX(UiElement element) {
        return element.box().borderBox().x() + element.box().borderBox().width() / 2f;
    }

    private static float centerY(UiElement element) {
        return element.box().borderBox().y() + element.box().borderBox().height() / 2f;
    }

    private static MarineRoster fullSquad() {
        MarineRoster roster = new MarineRoster();
        EquipmentTestFixtures.learnServiceKit(roster.armory());
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        return roster;
    }

    private static Map<String, Object> props(FleetArmoryViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("firstRefitClasses", "hidden");
        props.put("openFirstRefit", (Runnable) () -> { });
        props.put("companySummary", viewModel.companySummary());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("squadCards", viewModel.squadCards());
        props.put("squadGalleryCards", viewModel.squadGalleryCards());
        props.put("organization", viewModel.organization());
        props.put("foundingCargoRows", viewModel.foundingCargoRows());
        props.put("cargoStockRows", viewModel.cargoStockRows());
        props.put("foundingDisabled", viewModel.foundingDisabled());
        props.put("foundingFeedbackText", viewModel.foundingFeedbackText());
        props.put("foundingFeedbackClasses", viewModel.foundingFeedbackClasses());
        props.put("foundSquad", viewModel.foundSquadAction());
        props.put("fireTeamOverviews", viewModel.fireTeamOverviews());
        props.put("squadRows", viewModel.squadRows());
        props.put("teamRows", viewModel.teamRows());
        props.put("targetSummary", viewModel.targetSummary());
        props.put("candidateSummary", viewModel.candidateSummary());
        props.put("weaponDoctrineTiles", viewModel.weaponDoctrineTiles());
        props.put("armorDoctrineTiles", viewModel.armorDoctrineTiles());
        props.put("weaponDoctrineSummary", viewModel.weaponDoctrineSummary());
        props.put("armorDoctrineSummary", viewModel.armorDoctrineSummary());
        props.put("weaponPickerTabClasses", viewModel.weaponPickerTabClasses());
        props.put("armorPickerTabClasses", viewModel.armorPickerTabClasses());
        props.put("weaponPickerPanelClasses", viewModel.weaponPickerPanelClasses());
        props.put("armorPickerPanelClasses", viewModel.armorPickerPanelClasses());
        props.put("showWeaponPicker", viewModel.showWeaponPickerAction());
        props.put("showArmorPicker", viewModel.showArmorPickerAction());
        props.put("loadoutFilters", viewModel.loadoutFilters());
        props.put("loadoutBrowserSummary", viewModel.loadoutBrowserSummary());
        props.put("showArmorComparison", (Runnable) () -> { });
        props.put("armorComparisonSummary", viewModel.armorComparisonSummary());
        props.put("armorComparisonCards", viewModel.armorComparisonCards());
        props.put("marineCards", viewModel.marineCards());
        props.put("inspection", viewModel.inspection());
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("issueCargoRows", viewModel.issueCargoRows());
        props.put("issueCargoClasses", viewModel.issueCargoClasses());
        props.put("applyDisabled", viewModel.applyDisabled());
        props.put("applyLabel", viewModel.applyLabel());
        props.put("apply", viewModel.applyAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("back", (Runnable) () -> { });
        props.put("backToSquads", (Runnable) () -> { });
        props.put("backToFireTeams", (Runnable) () -> { });
        putPageNavigation(props);
        return props;
    }

    private static void putPageNavigation(Map<String, Object> props) {
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
    }

    /**
     * <b>Best first.</b> The picker exists to answer "which of the things I can
     * field is the strongest", and authored declaration order cannot: the
     * built-in definitions are listed in the order somebody wrote them, which
     * put the starter kit above everything the company has bought since.
     */
    @Test
    void loadoutsAreListedStrongestFirst() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        assertRankedByRating(viewModel.weaponDoctrineTiles().get(), "weapon");
        assertRankedByRating(viewModel.armorDoctrineTiles().get(), "tactic sheet");
    }

    private static void assertRankedByRating(
            List<FleetArmoryViewModel.DoctrineTile> tiles, String what) {
        assertTrue(tiles.size() > 1, "nothing to rank among the " + what + " tiles");
        for (int index = 1; index < tiles.size(); index++) {
            assertTrue(tiles.get(index - 1).rating() >= tiles.get(index).rating(),
                    what + " tiles are out of order at " + index + ": "
                            + tiles.stream().map(FleetArmoryViewModel.DoctrineTile::rating)
                            .toList());
        }
        for (FleetArmoryViewModel.DoctrineTile tile : tiles) {
            assertTrue(tile.ratingLabel().endsWith(tile.rating() + "%"),
                    "the chip has to show the number the order is built on");
        }
    }

    @Test
    void emptyCargoDoesNotRestrictKnownEquipment() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel model = new FleetArmoryViewModel(new Reactor(), roster, () -> { }, () -> 0d, emptyHold());
        for (var tile : model.armorDoctrineTiles().get()) {
            var preview = roster.previewSquadEquipment(model.selectedSquadId(), model.selectedWeaponDoctrineId(),
                    tile.id().substring("armor-doctrine:".length()), emptyHold());
            assertTrue(preview.canApply());
            assertTrue(preview.issueCost().isZero());
        }
    }

    /**
     * <b>Compare Patterns is a stock list, not a catalog.</b> The twenty-seventh
     * pattern is not an option a company can weigh, it is a rumour, and putting
     * it in the table makes the screen answer a different question badly. The
     * summary still names the catalog's size so the rumour survives.
     */
    @Test
    void comparingPatternsShowsOnlyWhatTheCompanyHolds() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        List<FleetArmoryViewModel.ArmorComparisonCard> cards =
                viewModel.armorComparisonCards().get();
        int catalogued = MarineArmorCatalogRegistry.installed().all().size();
        assertTrue(cards.size() < catalogued,
                "fixture assumption: a fresh company does not hold the whole catalog");
        for (FleetArmoryViewModel.ArmorComparisonCard card : cards) {
            String armorId = card.id().substring("armor-comparison:".length());
            assertTrue(roster.armory().ownsArmorTemplate(armorId),
                    armorId + " is on the comparison screen and has never been held");
        }
        assertTrue(viewModel.armorComparisonSummary().get()
                        .contains(cards.size() + " of " + catalogued),
                "the summary names both numbers: " + viewModel.armorComparisonSummary().get());
    }

    private static List<String> tileIds(List<FleetArmoryViewModel.DoctrineTile> tiles) {
        return tiles.stream().map(FleetArmoryViewModel.DoctrineTile::id).toList();
    }

    /** A fleet carrying nothing at all, so every priced change is refused. */
    private static EquipmentIssueResources emptyHold() {
        return new EquipmentIssueResources() {
            @Override public EquipmentTemplateCost available() {
                return EquipmentTemplateCost.ZERO;
            }

            @Override public boolean spend(EquipmentTemplateCost cost) {
                return cost != null && cost.isZero();
            }
        };
    }

    private static EquipmentIssueResources presentedHold() {
        return new EquipmentIssueResources() {
            private final EquipmentTemplateCost available =
                    new EquipmentTemplateCost(2_000, 2_000, 2_000, 2_000);

            @Override public EquipmentTemplateCost available() { return available; }
            @Override public boolean spend(EquipmentTemplateCost cost) { return cost != null; }
            @Override public String commodityName(String commodityId) { return commodityId; }
            @Override public String commodityIcon(String commodityId) {
                return "vanilla/" + commodityId;
            }
        };
    }

    private static final class PresentedFoundingHold implements SquadFoundingResources {
        private final Map<String, Integer> stock = new LinkedHashMap<>();

        private PresentedFoundingHold(int quantity) {
            for (SquadFoundingCost.Line line : SquadFoundingCost.STANDARD.lines()) {
                stock.put(line.commodityId(), quantity);
            }
        }

        @Override public int available(String commodityId) {
            return stock.getOrDefault(commodityId, 0);
        }

        @Override public String commodityName(String commodityId) { return commodityId; }

        @Override public String commodityIcon(String commodityId) {
            return "vanilla/" + commodityId;
        }

        @Override public boolean spend(SquadFoundingCost cost) {
            if (!canAfford(cost)) return false;
            for (SquadFoundingCost.Line line : cost.lines()) {
                stock.merge(line.commodityId(), -line.quantity(), Integer::sum);
            }
            return true;
        }
    }

}
