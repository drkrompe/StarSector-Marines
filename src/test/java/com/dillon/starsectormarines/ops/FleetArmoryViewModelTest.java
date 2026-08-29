package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.ArmorRole;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
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
            "mod/data/ui/components/marine-ops-page-nav.mlx",
            "mod/data/ui/components/armory/fleet-armory.mlx",
            "mod/data/ui/components/armory/armory-squad-list.mlx",
            "mod/data/ui/components/armory/fleet-armory-fireteam.mlx",
            "mod/data/ui/components/armory/armory-squad-doctrine.mlx",
            "mod/data/ui/components/armory/armory-refit-transaction.mlx",
            "mod/data/ui/components/armory/armory-armor-comparison.mlx");

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
                .stream().map(FleetArmoryViewModel.StatMeter::label).toList());
        assertEquals(List.of("HEALTH", "ARMOR", "RESIST", "SPEED"), firstMarine.armorStats()
                .stream().map(FleetArmoryViewModel.StatMeter::label).toList());
        assertTrue(firstMarine.weaponStats().stream().allMatch(stat ->
                stat.fillStyle().matches("width: \\d{1,3}%;")));
        // The card shows a role from the closed vocabulary rather than whatever
        // text the catalog happened to carry. Asserted as membership, not as a
        // literal, so filling out the role matrix cannot break a test about the
        // Armory rendering a card.
        assertTrue(Arrays.stream(ArmorRole.values())
                        .anyMatch(role -> role.displayName().equals(firstMarine.unitClass())),
                "unexpected role on the card: " + firstMarine.unitClass());
        assertEquals("W II", firstMarine.weaponBadge());
        assertEquals("A II", firstMarine.armorBadge());
        assertTrue(firstMarine.primaryDescription().length() > 80);
        assertTrue(firstMarine.armorDescription().length() > 80);
        assertTrue(viewModel.feedbackText().get().startsWith(
                "Hover equipment names for field notes."));
        FleetArmoryViewModel.DoctrineTile firstLoadout =
                viewModel.weaponDoctrineTiles().get().get(0);
        assertEquals("Common", firstLoadout.rarity());
        assertTrue(firstLoadout.metadata().contains("TIER I"));
        assertTrue(firstLoadout.description().length() > 120);
        assertTrue(viewModel.weaponDoctrineTiles().get().size()
                < roster.armory().weaponDoctrines().size());
        // Every armour plan is always issuable: it names roles and the armoury
        // fills them from whatever is owned, so there is no such thing as an
        // unaffordable composition. Weapons still gate, which is why the
        // assertion above it is still a strict inequality.
        assertEquals(roster.armory().armorDoctrines().size(),
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
    void degradedTeamIsDomainDisabledAndApplyChangesNothing() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);
        assertEquals(SquadEquipmentResult.SQUAD_NOT_READY,
                viewModel.currentSquadEquipmentPreview().result());
        assertTrue(viewModel.applyDisabled().get());
        assertEquals(SquadEquipmentResult.SQUAD_NOT_READY,
                viewModel.applySquadEquipmentSelection());
        assertNull(roster.squads().get(0).weaponDoctrineId());
        assertNull(roster.squads().get(0).armorDoctrineId());
    }

    @Test
    void shippedComponentsKeepKeyedDoctrineAndMarineIdentityAcrossInspection() throws Exception {
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
            for (float[] size : List.of(
                    new float[]{1744f, 938f},
                    new float[]{1395f, 750f},
                    new float[]{1163f, 625f})) {
                document.layout(size[0], size[1]);
                UiElement root = instance.requireElement("fleet-armory-fireteam-root");
                UiElement transaction = instance.requireElement("refit-transaction");
                assertTrue(transaction.box().borderBox().width() > 300f);
                assertTrue(transaction.box().borderBox().right()
                        <= root.box().contentBox().right() + 0.01f);
                assertTrue(transaction.box().borderBox().bottom()
                        <= root.box().contentBox().bottom() + 0.01f);
            }

            UiElement list = instance.requireElement("weapon-doctrine-list");
            assertTrue(instance.requireElement("show-weapon-picker").hasClass("selected"));
            assertTrue(instance.requireElement("armor-doctrine-slot").hasClass("picker-hidden"));
            UiElement marineCard = instance.requireElement("marine-card:0");
            UiElement marineCanvas = instance.requireElement("marine-preview:0");
            UiElement first = list.childAt(0);
            UiElement second = list.childAt(1);
            FleetArmoryViewModel.DoctrineTile alternative =
                    viewModel.weaponDoctrineTiles().get().get(1);
            alternative.select().run();
            instance.flush();

            assertSame(first, list.childAt(0));
            assertSame(second, list.childAt(1));
            assertTrue(instance.requireElement(alternative.id()).selected());
            String selectedWeapon = viewModel.selectedWeaponDoctrineId();
            String alphaName = viewModel.marineCards().get().get(0).name();
            viewModel.fireTeamOverviews().get().get(1).select().run();
            instance.flush();

            assertSame(marineCard, instance.requireElement("marine-card:0"));
            assertSame(marineCanvas, instance.requireElement("marine-preview:0"));
            assertNotEquals(alphaName, viewModel.marineCards().get().get(0).name());
            assertEquals(selectedWeapon, viewModel.selectedWeaponDoctrineId());
            assertEquals(viewModel.marineCards().get().get(0).name(),
                    instance.requireElement("marine-card:0:name").text());

            assertEquals(viewModel.currentSquadEquipmentPreview().canApply(),
                    !instance.requireElement("apply-squad-equipment").disabled());
            assertTrue(viewModel.marineCards().get().get(0).weaponDelta().contains("DMG"));
            viewModel.showArmorPickerAction().run();
            instance.flush();
            assertEquals(FleetArmoryViewModel.EquipmentPickerKind.ARMOR,
                    viewModel.equipmentPickerKind());
            assertTrue(instance.requireElement("show-armor-picker").hasClass("selected"));
            assertTrue(instance.requireElement("weapon-doctrine-slot")
                    .hasClass("picker-hidden"));
            assertFalse(instance.requireElement("armor-doctrine-slot")
                    .hasClass("picker-hidden"));
            for (int slot = 0; slot < MarineSquad.TEAM_SIZE; slot++) {
                assertEquals(viewModel.marineCards().get().get(slot).name(),
                        instance.requireElement("marine-card:" + slot + ":name").text());
            }
        }
    }

    @Test
    void equipmentLoreUsesBoundedHoverTooltipsWithoutDisplacingComparisonStats()
            throws Exception {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(reactor, roster);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(reactor, "fleet-armory-fireteam",
                props(viewModel))) {
            FleetArmoryViewModel.MarineViewerCard marine =
                    viewModel.marineCards().get().get(0);
            ArmoryEquipmentTooltips tooltips = ArmoryEquipmentTooltips.bind(
                    instance, viewModel.marineCards().get());
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(1744f, 938f);

            UiElement card = instance.requireElement(marine.id());
            UiElement target = instance.requireElement(marine.primaryId());
            UiElement popup = instance.requireElement(marine.primaryDescriptionId());
            UiElement stats = instance.requireElement(marine.weaponStatsId());
            assertEquals(UiLayout.STACK, card.layout());
            assertSame(card, popup.parent());
            assertTrue(popup.text().startsWith(marine.primary()));
            assertTrue(popup.text().contains(marine.primaryDescription()));
            assertTrue(popup.hasClass("tooltip-hidden"));
            assertTrue(stats.box().borderBox().width() > 250f);

            document.pointerMoved(centerX(target), centerY(target));
            tooltips.update();
            document.advance(0f);

            assertFalse(popup.hasClass("tooltip-hidden"));
            assertTrue(popup.box().borderBox().width() >= 350f);
            assertTrue(popup.box().borderBox().height() >= 130f);
            assertTrue(popup.box().borderBox().right()
                    <= card.box().contentBox().right() + 0.01f);
            assertTrue(popup.box().borderBox().bottom()
                    <= card.box().contentBox().bottom() + 0.01f);
            assertTrue(stats.box().borderBox().width() > 250f,
                    "opening lore must not resize the comparison meters");

            document.pointerMoved(
                    card.box().contentBox().x() + 8f,
                    card.box().contentBox().bottom() - 8f);
            tooltips.update();
            document.advance(0f);
            assertTrue(popup.hasClass("tooltip-hidden"));
        }
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
    void armorComparisonListsEveryCatalogPatternSortedByTierThenName() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        List<MarineArmorCatalogDef> expectedOrder = new ArrayList<>(
                MarineArmorCatalogRegistry.installed().all());
        expectedOrder.sort(Comparator.comparingInt(MarineArmorCatalogDef::tier)
                .thenComparing(MarineArmorCatalogDef::displayName));

        List<FleetArmoryViewModel.ArmorComparisonCard> cards =
                viewModel.armorComparisonCards().get();
        assertEquals(expectedOrder.size(), cards.size());
        for (int index = 0; index < expectedOrder.size(); index++) {
            assertEquals(expectedOrder.get(index).displayName(), cards.get(index).name());
            assertEquals(expectedOrder.get(index).description(), cards.get(index).description());
        }
        assertTrue(viewModel.armorComparisonSummary().get()
                .startsWith(expectedOrder.size() + " armor patterns"));
    }

    @Test
    void armorComparisonReadsTheSameIntegralSystemCopyEveryOtherSurfaceUses() {
        MarineRoster roster = fullSquad();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);

        MarineArmorCatalogDef withSystem = MarineArmorCatalogRegistry.installed().all().stream()
                .filter(MarineArmorCatalogDef::hasIntegralSystem)
                .findFirst().orElseThrow();
        MarineArmorCatalogDef withoutSystem = MarineArmorCatalogRegistry.installed().all().stream()
                .filter(armor -> !armor.hasIntegralSystem())
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
                    .map(FleetArmoryViewModel.StatMeter::label).toList());
        }
    }

    @Test
    void armorComparisonMarkupBindsEveryCatalogPatternWithoutMissingElements()
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
            document.layout(1744f, 938f);

            assertEquals(MarineArmorCatalogRegistry.installed().size(),
                    viewModel.armorComparisonCards().get().size());
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
            document.layout(1163f, 938f);

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
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        return roster;
    }

    private static Map<String, Object> props(FleetArmoryViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("companySummary", viewModel.companySummary());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("squadCards", viewModel.squadCards());
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
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
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
                () -> { }, () -> { }, () -> { }, () -> { });
    }
}
