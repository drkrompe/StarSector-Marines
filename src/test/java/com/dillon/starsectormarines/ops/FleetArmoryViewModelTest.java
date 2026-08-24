package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.FireTeamGearDelta;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FireTeamRefitPreview;
import com.dillon.starsectormarines.marine.FireTeamTemplateResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.battleview.ArmoryLoadoutPreviewComposer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetArmoryViewModelTest {

    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/armory/fleet-armory.mlx",
            "mod/data/ui/components/armory/armory-formation-rail.mlx",
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
        assertEquals(authoritative.gear().size(), viewModel.gearRows().get().size());
        for (int index = 0; index < authoritative.gear().size(); index++) {
            FireTeamGearDelta delta = authoritative.gear().get(index);
            assertTrue(viewModel.gearRows().get().get(index).label().contains(delta.label()));
        }

        assertEquals(FireTeamTemplateResult.APPLIED, viewModel.applySelection());
        MarineSquad squad = roster.squadById(viewModel.selectedSquadId());
        assertEquals(viewModel.selectedTemplateId(),
                squad.teamTemplateCardId(viewModel.selectedTeamIndex()));
        assertTrue(viewModel.feedbackText().get().contains("atomic transaction"));
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

        try (MarkupInstance instance = loader.build(reactor, "fleet-armory",
                props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            for (float[] size : List.of(
                    new float[]{1744f, 938f},
                    new float[]{1395f, 750f},
                    new float[]{1163f, 625f})) {
                document.layout(size[0], size[1]);
                UiElement root = instance.requireElement("fleet-armory-root");
                UiElement transaction = instance.requireElement("refit-transaction");
                assertTrue(transaction.box().borderBox().width() > 300f);
                assertTrue(transaction.box().borderBox().right()
                        <= root.box().contentBox().right() + 0.01f);
                assertTrue(transaction.box().borderBox().bottom()
                        <= root.box().contentBox().bottom() + 0.01f);
            }

            UiElement list = instance.requireElement("template-list");
            UiElement preview = instance.requireElement("loadout-preview");
            assertEquals(ArmoryLoadoutPreviewComposer.SURFACE_WIDTH,
                    preview.canvasWidth());
            assertEquals(ArmoryLoadoutPreviewComposer.SURFACE_HEIGHT,
                    preview.canvasHeight());
            UiElement first = list.childAt(0);
            UiElement second = list.childAt(1);
            viewModel.templateRows().get().get(1).select().run();
            instance.flush();

            assertSame(first, list.childAt(0));
            assertSame(second, list.childAt(1));
            assertFalse(first.selected());
            assertTrue(second.selected());
            assertEquals(viewModel.currentPreview().canApply(),
                    !instance.requireElement("apply-template").disabled());
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
            assertFalse(source.matches("(?s).*\\b(card|deck|hand|consume)\\b.*"), path);
        }
    }

    private static MarineRoster fullSquad() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        return roster;
    }

    private static Map<String, Object> props(FleetArmoryViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("companySummary", viewModel.companySummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("teamRows", viewModel.teamRows());
        props.put("templateRows", viewModel.templateRows());
        props.put("targetSummary", viewModel.targetSummary());
        props.put("candidateSummary", viewModel.candidateSummary());
        props.put("billetRows", viewModel.billetRows());
        props.put("previewSummary", viewModel.previewSummary());
        props.put("gearRows", viewModel.gearRows());
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("applyDisabled", viewModel.applyDisabled());
        props.put("apply", viewModel.applyAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("back", (Runnable) () -> { });
        props.put("legacy", (Runnable) () -> { });
        props.put("reload", (Runnable) () -> { });
        props.put("reloadStatus", "Test");
        return props;
    }
}
