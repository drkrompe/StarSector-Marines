package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.FirstRefit;
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
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;
import com.dillon.starsectormarines.ops.spec.StatMeter;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetArmoryViewModelTest {

    @Test
    void rangeUsesSelectedMarineAndProposedKitWithoutIssuingOrCampaignIdentity() throws Exception {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel model = new FleetArmoryViewModel(reactor, roster);
        MarineSquad squad = roster.squadById(model.selectedSquadId());
        var soldier = roster.soldierById(roster.manningMemberIds(squad).get(4));
        var issued = soldier.armorDef();
        model.armorDoctrineTiles().get().get(0).select().run();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(Path.of(path)), COMPONENTS);
        try (var instance = loader.reloadAndBuild(reactor, "fleet-armory-fireteam", props(model))) {
            UiDocument document = new UiDocument(instance.root()).theme(MarineOpsThemes.standard());
            instance.styles().forEach(document::addStyleSheet);
            document.layout(1920, 1080);
            UiElement billet = instance.requireElement("loadout-billet:4");
            document.pointerDown(centerX(billet), centerY(billet));
            document.pointerUp(centerX(billet), centerY(billet));
            instance.flush();
            var trial = model.rangeCandidate();
            assertNotNull(trial);
            assertEquals(soldier.name(), trial.name());
            assertEquals(soldier.aptitude(), trial.loadout().soldierProfile.aptitude());
            assertEquals(model.currentSquadEquipmentPreview().billet(4).armorDef().armorCapacity(),
                    trial.loadout().armorCapacity);
            assertEquals(model.currentSquadEquipmentPreview().billet(4).primaryDef().id,
                    trial.loadout().primaryWeaponId);
            assertEquals(model.currentSquadEquipmentPreview().billet(4).armorDef().integralSystem(),
                    trial.loadout().integralSystem);
            assertNull(trial.loadout().campaignSoldierId);
            assertNull(trial.loadout().campaignSquad);
            assertSame(issued, soldier.armorDef());
            roster.applySoldierOutcome(Map.of(soldier.id(), MarineSoldierStatus.WIA), 100f, 1f);
            model.refresh();
            assertNull(model.rangeCandidate(), "a wounded marine cannot be borrowed for practice");
        }
    }

    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/company/company-resource-stock.mlx", "mod/data/ui/components/company/company-resource-cost.mlx", "mod/data/ui/components/marine-ops-page-nav.mlx",
            "mod/data/ui/components/armory/fleet-armory-fireteam.mlx",
            "mod/data/ui/components/armory/armory-loadout-card.mlx", "mod/data/ui/components/armory/armory-squad-view.mlx", "mod/data/ui/components/armory/armory-card-chip.mlx", "mod/data/ui/components/armory/armory-squad-doctrine.mlx",
            "mod/data/ui/components/armory/armory-refit-transaction.mlx",
            "mod/data/ui/components/fitting/fitting-filter-pill.mlx");

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
    void aCardShownWithoutAnIssueActionStillHasAClickHandler() {
        // The First Refit screen shows the same weapon card with nothing to issue; a null handler
        // made the markup loader throw when it built that screen.
        SquadWeaponDoctrine doctrine = SquadEquipmentDoctrines.weaponDoctrines().get(0);
        FleetArmoryViewModel.DoctrineTile tile = FleetArmoryViewModel.weaponTile(
                "refit-path:test", doctrine, false, () -> { });
        assertTrue(tile.markupProperty("issue") instanceof Runnable);
        assertEquals("doctrine-issue-button-hidden", tile.markupProperty("issueButtonClasses"));
    }

    @Test
    void scoutAcquisitionCannotPopulateTheBrowserWithUnlearnedFactionSheets() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(12);
        FleetArmoryViewModel model = new FleetArmoryViewModel(new Reactor(), roster);
        model.showArmorPickerAction().run();
        assertEquals("1 known", model.loadoutBrowserSummary().get());

        EquipmentTestFixtures.learnSheet(roster.armory(), SquadEquipmentDoctrines.RECON_ARMOR);
        model.refresh();

        assertEquals(2, model.armorDoctrineTiles().get().size());
        assertTrue(tileIds(model.armorDoctrineTiles().get()).containsAll(List.of(
                "armor-doctrine:" + SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR,
                "armor-doctrine:" + SquadEquipmentDoctrines.RECON_ARMOR)));
        assertEquals("2 known", model.loadoutBrowserSummary().get());
        assertTrue(roster.soldiers().stream().allMatch(
                person -> "armor.cordon-shell".equals(person.armorId())),
                "learning a sheet or specification must not issue new kit");
    }

    @Test
    void waylineRefitExposesItsLearnedScreenWithoutUnrelatedFactionFormations() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertTrue(roster.firstRefit().claim(roster, FirstRefit.Path.FORWARD_SCREEN));
        FleetArmoryViewModel model = new FleetArmoryViewModel(new Reactor(), roster);
        model.showArmorPickerAction().run();

        assertEquals(2, model.armorDoctrineTiles().get().size());
        assertTrue(tileIds(model.armorDoctrineTiles().get()).containsAll(List.of(
                "armor-doctrine:" + SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR,
                "armor-doctrine:" + FirstRefit.Path.FORWARD_SCREEN.armor)));
        assertEquals("2 known", model.loadoutBrowserSummary().get());
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
        assertTrue(tile.distribution().contains("3 " + WeaponRegistry.require(WeaponRegistry.DMR_ID).displayName()));
        assertTrue(tile.distribution().contains("9 " + WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).displayName()));
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
        assertFalse(firstMarine.weaponBadge().isBlank());
        assertFalse(firstMarine.armorBadge().isBlank());
        // The lore the card used to carry for its own popup is now the sheet's,
        // written from the item's owning catalog rather than from a view-model prop.
        assertTrue(String.join(" ", firstMarine.primarySheet().notes()).length() > 80);
        assertTrue(String.join(" ", firstMarine.armorSheet().notes()).length() > 80);
        assertNotNull(firstMarine.primaryFactionLogo());
        assertFalse(firstMarine.primaryFactionLogoClasses().contains("faction-logo-hidden"));
        assertTrue(viewModel.feedbackText().get().startsWith(
                "Hover equipment names for field notes."));
        FleetArmoryViewModel.DoctrineTile assignedLoadout =
                viewModel.weaponDoctrineTiles().get().stream()
                        .filter(tile -> tile.id().endsWith(
                                viewModel.selectedWeaponDoctrineId()))
                        .findFirst().orElseThrow();
        assertEquals("Common", assignedLoadout.rarity());
        assertEquals(1, assignedLoadout.tier());
        assertEquals("graphics/factions/neutral_traders.png",
                assignedLoadout.factionLogo());
        assertTrue(assignedLoadout.description().length() > 120);
        assertTrue(viewModel.weaponDoctrineTiles().get().size()
                < roster.armory().weaponDoctrines().size());
        // The picker and armory expose the same plans whose carrier class the
        // company holds; missing roles may fall back within that class.
        assertEquals(roster.armory().armorDoctrines().stream().map(SquadArmorDoctrine::issueIds).distinct().count(),
                viewModel.armorDoctrineTiles().get().size());
        assertNull(viewModel.loadoutFilter(), "no tier chosen shows every tier");
        viewModel.showLoadoutFilterAction(FleetArmoryViewModel.LoadoutFilter.TIER_1).run();
        assertEquals(FleetArmoryViewModel.LoadoutFilter.TIER_1, viewModel.loadoutFilter());
        assertFalse(viewModel.weaponDoctrineTiles().get().isEmpty());
        assertTrue(viewModel.weaponDoctrineTiles().get().stream()
                .allMatch(loadout -> loadout.tier() == 1));
        assertTrue(viewModel.loadoutBrowserSummary().get().contains(" of "));
        viewModel.showLoadoutFilterAction(FleetArmoryViewModel.LoadoutFilter.TIER_1).run();
        assertNull(viewModel.loadoutFilter(), "choosing the chosen tier clears it");

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
        SquadWeaponDoctrine custom = roster.armory().ensureWeaponDoctrine(
                "custom:weapons:saved", "Company Pattern", source.issues());
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
    void weaponCardIssueSelectsAndAppliesThatLoadout() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);
        var card = viewModel.weaponDoctrineTiles().get().get(1);
        assertFalse(card.issueDisabled());
        card.issue().run();
        assertEquals(card.id(), "weapon-doctrine:" + roster.squads().get(0).weaponDoctrineId());
        assertEquals(card.id(), "weapon-doctrine:" + viewModel.selectedWeaponDoctrineId());
        var issued = viewModel.weaponDoctrineTiles().get().stream()
                .filter(tile -> tile.id().equals(card.id())).findFirst().orElseThrow();
        assertEquals("doctrine-issue-check", issued.markupProperty("issueCheckClasses"));
        // The issued card's box stays lit, not greyed out, and ticking it again changes nothing.
        assertEquals(false, issued.markupProperty("issueDisabled"));
        ((Runnable) issued.markupProperty("issue")).run();
        assertEquals(card.id(), "weapon-doctrine:" + roster.squads().get(0).weaponDoctrineId());
        assertEquals("", viewModel.transactionSummary().get());
    }

    /**
     * A card's tier chip and check box are centred by layout (an align-items row and growing
     * spacers), not by padding sums, so they stay centred at any document size: the smallest
     * the screen fits and a large panel that lays out wider.
     */
    @Test
    void cardTierChipAndCheckBoxStayCentredAtAnyDocumentSize() throws Exception {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel model = new FleetArmoryViewModel(reactor, roster);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(Path.of(path)), COMPONENTS);
        try (MarkupInstance instance = loader.reloadAndBuild(reactor, "fleet-armory-fireteam", props(model))) {
            UiDocument document = new UiDocument(instance.root()).theme(MarineOpsThemes.standard());
            instance.styles().forEach(document::addStyleSheet);
            SpecSheetLayer.install(document);
            for (float[] size : new float[][] {{1744f, 938f}, {2560f, 1440f}}) {
                document.layout(size[0], size[1]);
                for (FleetArmoryViewModel.DoctrineTile tile : model.weaponDoctrineTiles().get()) {
                    UiElement heading = instance.requireElement(tile.headerId());
                    UiElement chip = instance.requireElement(tile.id() + ":tier");
                    UiElement chevrons = instance.requireElement(tile.id() + ":tier-icon");
                    UiElement box = instance.requireElement(tile.id() + ":issue-box");
                    UiElement tick = instance.requireElement(tile.id() + ":issue-check");
                    String at = tile.id() + " at " + (int) size[0] + "x" + (int) size[1];
                    assertEquals(centerX(chip), centerX(chevrons), 0.01f, "chevrons centred across the chip, " + at);
                    assertEquals(centerY(chip), centerY(chevrons), 0.01f, "chevrons centred down the chip, " + at);
                    assertEquals(centerY(heading), centerY(chip), 0.01f, "chip centred on the heading, " + at);
                    assertEquals(centerY(heading), centerY(box), 0.01f, "box centred on the heading, " + at);
                    assertEquals(centerX(box), centerX(tick), 0.01f, "tick centred across the box, " + at);
                    assertEquals(centerY(box), centerY(tick), 0.01f, "tick centred down the box, " + at);
                }
            }
        }
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
            SpecSheetLayer.install(document);
            document.layout(1920, 1080);
            UiElement list = instance.requireElement("weapon-doctrine-list");
            UiElement first = list.childAt(0);
            UiElement billet = instance.requireElement("loadout-billet:0");
            UiElement comparison = instance.requireElement(LoadoutInspection.partId(0, LoadoutInspection.WEAPON));
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
            assertSame(comparison, instance.requireElement(LoadoutInspection.partId(0, LoadoutInspection.WEAPON)));
            assertEquals(weapon, model.selectedWeaponDoctrineId());
            assertTrue(next.hasClass("selected"));
            var chosen = model.weaponDoctrineTiles().get().stream()
                    .filter(tile -> tile.id().endsWith(weapon)).findFirst().orElseThrow();
            assertEquals(model.currentSquadEquipmentPreview().canApply(),
                    !instance.requireElement(chosen.id() + ":issue").disabled());
            // The Issue button lives inside its card; a click on it issues rather than merely selects.
            UiElement issueButton = instance.requireElement(chosen.id() + ":issue");
            document.pointerDown(centerX(issueButton), centerY(issueButton));
            document.pointerUp(centerX(issueButton), centerY(issueButton));
            instance.flush();
            assertEquals(weapon, roster.squads().get(0).weaponDoctrineId());
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
            ArmorySpecSheets.bindInspection(binder, instance, model.inspection()::get);
            document.layout(1920, 1080);
            assertEquals(MarineSquad.CAPACITY * LoadoutInspection.PARTS, binder.size());
            UiElement stats = instance.requireElement("loadout-billet:0:stat:DPS");
            var box = stats.box().borderBox();
            UiElement target = instance.requireElement(LoadoutInspection.partId(0, LoadoutInspection.ARMOR));
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
                for (int seat = 0; seat < tile.seatSheets().size(); seat++) {
                    assertTrue(binder.isBound(instance.requireElement(ArmorySpecSheets.seatId(tile.id(), seat))),
                            "seat " + seat + " of " + tile.id() + " is painted and is not askable");
                    entries++;
                }
                assertFalse(binder.isBound(instance.requireElement(tile.nameId())),
                        "a loadout is a definition, not a catalog item");
                // The card face prints no field note; hovering the card itself shows it.
                assertTrue(binder.isBound(instance.requireElement(tile.id())),
                        tile.id() + " keeps its field note nowhere a reader can reach");
                entries++;
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
                assertEquals(issued.gradedName(issuedGrade(doctrine, issued)),
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
            // is laid out — a hidden picker's rows have no box to point at. The
            // card paints its seats, and a seat describes what it shows.
            UiElement target = instance.requireElement(ArmorySpecSheets.seatId(card.id(), 0));
            assertFalse(layer.visible());
            document.pointerMoved(centerX(target), centerY(target));
            binder.update();
            document.advance(0f);
            assertTrue(layer.visible());
            assertSame(target, binder.openTarget());
            assertEquals(card.seatSheets().get(0).title(),
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
                    && text.endsWith(" " + weapon.displayName())) {
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
    void woundedMarinesShowRemainingHoursAndKeepTheirBillets() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new LinkedHashMap<>();
        outcome.put(squad.memberIds().get(0), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(outcome, 100f, 1.25f);
        roster.recruitToSquad(roster.reserveSquad().id());
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                new Reactor(), roster, () -> 100d);

        assertEquals("WIA  ·  RTD in 1d 6h", viewModel.marineCards().get().get(0).status());
        assertTrue(viewModel.marineCards().get().get(0).classes().contains("status-wia"));
    }

    @Test
    void freeFittingChargesNoCargo() {
        MarineRoster roster = fullSquad();
        EquipmentIssueResources resources = presentedHold();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                new Reactor(), roster, () -> 0d, resources);

        viewModel.weaponDoctrineTiles().get().stream()
                .filter(tile -> !tile.id().endsWith(viewModel.selectedWeaponDoctrineId()))
                .findFirst().orElseThrow().select().run();

        List<CompanyResources.Row> costs = viewModel.issueCargoRows().get();
        assertTrue(costs.isEmpty());
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
        props.put("testLoadout", (Runnable) () -> {});
        props.put("rangeDisabled", false);
        props.put("rangeHint", "Try this marine's proposed equipment. Escape returns here; practice changes nothing.");
        props.put("firstRefitClasses", "hidden");
        props.put("openFirstRefit", (Runnable) () -> { });
        props.put("companySummary", viewModel.companySummary());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("contextLabel", viewModel.issueContextLabel());
        props.put("fireTeamOverviews", viewModel.fireTeamOverviews());
        props.put("squadRows", viewModel.squadRows());
        props.put("teamRows", viewModel.teamRows());
        props.put("targetSummary", viewModel.targetSummary());
        props.put("candidateSummary", viewModel.candidateSummary());
        props.put("weaponDoctrineTiles", viewModel.weaponDoctrineTiles());
        props.put("armorDoctrineTiles", viewModel.armorDoctrineTiles());
        props.put("weaponPickerTabClasses", viewModel.weaponPickerTabClasses());
        props.put("armorPickerTabClasses", viewModel.armorPickerTabClasses());
        props.put("weaponPickerPanelClasses", viewModel.weaponPickerPanelClasses());
        props.put("armorPickerPanelClasses", viewModel.armorPickerPanelClasses());
        props.put("showWeaponPicker", viewModel.showWeaponPickerAction());
        props.put("showArmorPicker", viewModel.showArmorPickerAction());
        props.put("loadoutFilters", viewModel.loadoutFilters());
        props.put("loadoutBrowserSummary", viewModel.loadoutBrowserSummary());
        props.put("marineCards", viewModel.marineCards());
        props.put("inspection", viewModel.inspection());
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("issueCargoRows", viewModel.issueCargoRows());
        props.put("issueCargoClasses", viewModel.issueCargoClasses());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("back", (Runnable) () -> { });
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
        FleetArmoryViewModel model = new FleetArmoryViewModel(new Reactor(), roster, () -> 0d, emptyHold());
        for (var tile : model.armorDoctrineTiles().get()) {
            var preview = roster.previewSquadEquipment(model.selectedSquadId(), model.selectedWeaponDoctrineId(),
                    tile.id().substring("armor-doctrine:".length()), emptyHold());
            assertTrue(preview.canApply());
            assertTrue(preview.issueCost().isZero());
        }
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


}
