package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FireTeamRefitPreview;
import com.dillon.starsectormarines.marine.FireTeamTemplateResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetArmoryViewModelTest {

    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/armory/fleet-armory.mlx",
            "mod/data/ui/components/armory/armory-squad-list.mlx",
            "mod/data/ui/components/armory/fleet-armory-fireteam.mlx",
            "mod/data/ui/components/armory/armory-fireteam-list.mlx",
            "mod/data/ui/components/armory/armory-template-library.mlx",
            "mod/data/ui/components/armory/armory-refit-transaction.mlx");

    @Test
    void transactionProjectionAndApplyUseTheRosterAuthority() {
        MarineRoster roster = fullSquad();
        Reactor reactor = new Reactor();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(reactor, roster);

        FireTeamRefitPreview authoritative = roster.previewFireTeamTemplate(
                viewModel.selectedSquadId(), viewModel.selectedTeamIndex(),
                viewModel.selectedTemplateId());
        assertEquals(authoritative.result(), viewModel.currentPreview().result());
        assertEquals(authoritative.gear(), viewModel.currentPreview().gear());
        assertEquals(MarineSquad.TEAM_SIZE, viewModel.marineCards().get().size());
        assertTrue(viewModel.marineCards().get().get(0).name().contains(" "));
        FleetArmoryViewModel.MarineViewerCard firstMarine =
                viewModel.marineCards().get().get(0);
        assertEquals(List.of("DMG", "RNG", "ACC", "DPS"), firstMarine.weaponStats()
                .stream().map(FleetArmoryViewModel.StatMeter::label).toList());
        assertEquals(List.of("POOL", "RATING", "MOVE"), firstMarine.armorStats()
                .stream().map(FleetArmoryViewModel.StatMeter::label).toList());
        assertTrue(firstMarine.weaponStats().stream().allMatch(stat ->
                stat.fillStyle().matches("width: \\d{1,3}%;")));

        assertEquals(FireTeamTemplateResult.APPLIED, viewModel.applySelection());
        MarineSquad squad = roster.squadById(viewModel.selectedSquadId());
        assertEquals(viewModel.selectedTemplateId(),
                squad.teamTemplateCardId(viewModel.selectedTeamIndex()));
        assertTrue(viewModel.feedbackText().get().contains("equipped"));
    }

    @Test
    void degradedTeamIsDomainDisabledAndApplyChangesNothing() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), roster);
        viewModel.teamRows().get().get(1).select().run();

        assertEquals(FireTeamTemplateResult.TEAM_NOT_READY,
                viewModel.currentPreview().result());
        assertTrue(viewModel.applyDisabled().get());
        assertEquals(FireTeamTemplateResult.TEAM_NOT_READY, viewModel.applySelection());
        assertNull(roster.squads().get(0).teamTemplateCardId(1));
    }

    @Test
    void shippedComponentsKeepKeyedTemplateIdentityAcrossSelection() throws Exception {
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

            UiElement list = instance.requireElement("template-list");
            UiElement marineCard = instance.requireElement("marine-card:0");
            UiElement marineCanvas = instance.requireElement("marine-preview:0");
            String alphaName = viewModel.marineCards().get().get(0).name();
            viewModel.fireTeamOverviews().get().get(1).select().run();
            instance.flush();

            assertSame(marineCard, instance.requireElement("marine-card:0"));
            assertSame(marineCanvas, instance.requireElement("marine-preview:0"));
            assertNotEquals(alphaName, viewModel.marineCards().get().get(0).name());
            assertEquals(viewModel.marineCards().get().get(0).name(),
                    instance.requireElement("marine-card:0:name").text());

            UiElement first = list.childAt(0);
            UiElement second = list.childAt(1);
            viewModel.toggleLoadoutPickerAction().run();
            FleetArmoryViewModel.TemplateTile alternative = viewModel.templateTiles().get()
                    .stream()
                    .filter(tile -> !tile.disabled())
                    .filter(tile -> !tile.templateId().equals(viewModel.selectedTemplateId()))
                    .findFirst()
                    .orElse(viewModel.templateTiles().get().get(0));
            alternative.select().run();
            instance.flush();

            assertSame(first, list.childAt(0));
            assertSame(second, list.childAt(1));
            assertTrue(viewModel.loadoutPickerOpen());
            assertTrue(instance.requireElement(alternative.id()).selected());
            assertEquals(viewModel.currentPreview().canApply(),
                    !instance.requireElement("apply-template").disabled());
            assertTrue(viewModel.marineCards().get().get(0).weaponDelta().contains("DMG"));
            for (int slot = 0; slot < MarineSquad.TEAM_SIZE; slot++) {
                assertEquals(viewModel.marineCards().get().get(slot).name(),
                        instance.requireElement("marine-card:" + slot + ":name").text());
            }
        }
    }

    @Test
    void billetSelectionDrivesTheMaterializedPreviewWithoutMutatingTheTemplate() {
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(new Reactor(), fullSquad());
        FireTeamBillet first = viewModel.selectedBillet();

        viewModel.billetRows().get().get(1).select().run();

        assertEquals(1, viewModel.selectedBilletIndex());
        assertSame(viewModel.selectedBillet(),
                viewModel.roster().armory().templateCardById(viewModel.selectedTemplateId())
                        .billet(1));
        assertFalse(first == viewModel.selectedBillet());
        assertTrue(viewModel.billetRows().get().get(1).classes().contains("selected"));
        assertTrue(viewModel.previewSummary().get().contains(viewModel.selectedBillet().name()));
    }

    @Test
    void playerFacingMarkupUsesTemplateLanguage() throws Exception {
        for (String path : COMPONENTS) {
            String source = Files.readString(Path.of(path)).toLowerCase(Locale.ROOT);
            assertFalse(source.matches("(?s).*\\b(deck|hand|consume)\\b.*"), path);
        }
    }

    @Test
    void woundedMarinesShowRemainingHoursAndKeepTheirBillets() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new LinkedHashMap<>();
        outcome.put(squad.memberIds().get(0), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(outcome, 0, 100f, 1.25f);
        roster.recruitToSquad(roster.reserveSquad().id());
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                new Reactor(), roster, () -> { }, () -> 100d);

        assertEquals("WIA  ·  RTD in 1d 6h", viewModel.marineCards().get().get(0).status());
        assertEquals("1 WIA  ·  RTD 1d 6h",
                viewModel.squadCards().get().get(0).recovery());
        assertTrue(viewModel.fireTeamOverviews().get().get(0).recovery().contains("1 WIA"));
        assertTrue(viewModel.reinforceDisabled().get(), "WIA personnel still hold billets");
    }

    @Test
    void squadCardReinforcesEveryOpenBilletFromReadyReserveInOneClick() {
        MarineRoster roster = fullSquad();
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new LinkedHashMap<>();
        outcome.put(squad.memberIds().get(0), MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(outcome, 0, 20f, 1f);
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
        roster.applySoldierOutcome(outcome, 0, 20f, 1f);
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
        float x = element.box().borderBox().x() + element.box().borderBox().width() / 2f;
        float y = element.box().borderBox().y() + element.box().borderBox().height() / 2f;
        document.pointerDown(x, y);
        document.pointerUp(x, y);
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
        props.put("templateTiles", viewModel.templateTiles());
        props.put("targetSummary", viewModel.targetSummary());
        props.put("candidateSummary", viewModel.candidateSummary());
        props.put("selectedSquadReadiness", viewModel.selectedSquadReadiness());
        props.put("reinforceLabel", viewModel.reinforceLabel());
        props.put("reinforceDisabled", viewModel.reinforceDisabled());
        props.put("reinforceSquad", viewModel.reinforceSelectedSquadAction());
        props.put("pickerClasses", viewModel.pickerClasses());
        props.put("pickerToggleLabel", viewModel.pickerToggleLabel());
        props.put("togglePicker", viewModel.toggleLoadoutPickerAction());
        props.put("billetRows", viewModel.billetRows());
        props.put("marineCards", viewModel.marineCards());
        props.put("previewSummary", viewModel.previewSummary());
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("applyDisabled", viewModel.applyDisabled());
        props.put("applyClasses", viewModel.applyClasses());
        props.put("applyLabel", viewModel.applyLabel());
        props.put("apply", viewModel.applyAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("back", (Runnable) () -> { });
        props.put("backToSquads", (Runnable) () -> { });
        props.put("legacy", (Runnable) () -> { });
        props.put("reload", (Runnable) () -> { });
        props.put("reloadStatus", "Test");
        return props;
    }
}
