package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.battleview.ArmoryMarinePreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authored retained-view evidence rendered without a Starsector process. */
public final class UiSnapshotSuite implements SnapshotSuite {

    private static final List<String> OVERVIEW_COMPONENTS = List.of(
            "data/ui/components/armory/fleet-armory-overview.mlx",
            "data/ui/components/armory/armory-company-list.mlx");
    private static final List<String> WORKSPACE_COMPONENTS = List.of(
            "data/ui/components/armory/fleet-armory.mlx",
            "data/ui/components/armory/armory-squad-list.mlx",
            "data/ui/components/armory/fleet-armory-fireteam.mlx",
            "data/ui/components/armory/armory-fireteam-list.mlx",
            "data/ui/components/armory/armory-squad-doctrine.mlx",
            "data/ui/components/armory/armory-refit-transaction.mlx");

    @Override
    public String id() {
        return "ui";
    }

    @Override
    public String label() {
        return "Retained UI";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                context.modRoot(), context.starsectorCore());
        return List.of(
                new SnapshotArtifact("fleet-armory-overview-wide.png",
                        renderFleetArmoryOverview(
                                context, renderer, 1744, 938)),
                new SnapshotArtifact("fleet-armory-overview-compact.png",
                        renderFleetArmoryOverview(
                                context, renderer, 1163, 625)),
                new SnapshotArtifact("fleet-armory-squads-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, 1744, 938, false, false)),
                new SnapshotArtifact("fleet-armory-workspace-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, 1744, 938, true, false)),
                new SnapshotArtifact("fleet-armory-equipment-preview-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, 1744, 938, true, true)));
    }

    private static BufferedImage renderFleetArmoryWorkspace(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean fireteam, boolean pickerOpen) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> postBattle = new LinkedHashMap<>();
        postBattle.put(squad.memberIds().get(0), MarineSoldierStatus.WIA);
        postBattle.put(squad.memberIds().get(MarineSquad.CAPACITY - 1),
                MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(postBattle, 0, 100f, 1.25f);
        roster.recruitToSquad(roster.reserveSquad().id());
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                reactor, roster, () -> { }, () -> 100d);
        if (fireteam && pickerOpen) viewModel.weaponDoctrineTiles().get().get(1).select().run();
        HeadlessArmoryPreviewRenderer armoryPreview =
                new HeadlessArmoryPreviewRenderer(context.modRoot());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, fireteam ? "fleet-armory-fireteam" : "fleet-armory",
                props(viewModel))) {
            instance.requireElement(fireteam
                            ? "fireteam-reload-status" : "armory-reload-status")
                    .align(UiAlign.STRETCH, UiAlign.CENTER);
            if (fireteam) {
                instance.requireElement("transaction-feedback")
                        .align(UiAlign.STRETCH, UiAlign.CENTER);
            }
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            if (fireteam) {
                for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
                    int billet = index;
                    document.canvases().set(instance.requireElement("marine-preview:" + index),
                            new ArmoryMarinePreviewCanvas(
                                    () -> viewModel.viewerBilletAt(billet),
                                    armoryPreview.assets()));
                }
            }
            return renderer.render(document, width, height);
        }
    }

    private static BufferedImage renderFleetArmoryOverview(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY * 2);
        Map<String, MarineSoldierStatus> postBattle = new LinkedHashMap<>();
        postBattle.put(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(postBattle, 0, 100f, 1.25f);
        FleetArmoryOverviewViewModel viewModel = new FleetArmoryOverviewViewModel(
                reactor, roster, () -> { }, () -> 100d);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), OVERVIEW_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory-overview", props(viewModel))) {
            instance.requireElement("company-overview-summary")
                    .align(UiAlign.STRETCH, UiAlign.CENTER);
            instance.requireElement("company-overview-reload-status")
                    .align(UiAlign.STRETCH, UiAlign.CENTER);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderer.render(document, width, height);
        }
    }

    private static Map<String, Object> props(FleetArmoryOverviewViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fleetSummary", viewModel.fleetSummary());
        props.put("companyCards", viewModel.companyCards());
        props.put("back", (Runnable) () -> { });
        props.put("reload", (Runnable) () -> { });
        props.put("reloadStatus", "Headless UX preview  ·  No engine process");
        return props;
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
        props.put("selectedSquadReadiness", viewModel.selectedSquadReadiness());
        props.put("reinforceLabel", viewModel.reinforceLabel());
        props.put("reinforceDisabled", viewModel.reinforceDisabled());
        props.put("reinforceSquad", viewModel.reinforceSelectedSquadAction());
        props.put("weaponDoctrineTiles", viewModel.weaponDoctrineTiles());
        props.put("armorDoctrineTiles", viewModel.armorDoctrineTiles());
        props.put("weaponDoctrineSummary", viewModel.weaponDoctrineSummary());
        props.put("armorDoctrineSummary", viewModel.armorDoctrineSummary());
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
        props.put("reload", (Runnable) () -> { });
        props.put("reloadStatus", "Headless UX preview  ·  No engine process");
        return props;
    }
}
