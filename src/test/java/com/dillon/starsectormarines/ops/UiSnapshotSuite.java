package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.ops.battleview.ArmoryMarinePreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
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

    private static final List<String> COMPANY_HQ_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-hq.mlx");
    private static final List<String> OVERVIEW_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/fleet-armory-overview.mlx",
            "data/ui/components/armory/armory-company-list.mlx");
    private static final List<String> WORKSPACE_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/fleet-armory.mlx",
            "data/ui/components/armory/armory-squad-list.mlx",
            "data/ui/components/armory/fleet-armory-fireteam.mlx",
            "data/ui/components/armory/fleet-armory-doctrine-designer.mlx",
            "data/ui/components/armory/armory-fireteam-list.mlx",
            "data/ui/components/armory/armory-squad-doctrine.mlx",
            "data/ui/components/armory/armory-refit-transaction.mlx");
    private static final List<String> MECH_LAB_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/mech-lab/mech-lab.mlx");

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
                new SnapshotArtifact("company-hq-bridge-wide.png",
                        renderCompanyHq(context, renderer, 1744, 938, 1f)),
                new SnapshotArtifact("company-hq-bridge-low-resolution.png",
                        renderCompanyHq(context, renderer, 1163, 625, 1f)),
                new SnapshotArtifact("company-hq-bridge-ui-scale-150.png",
                        renderCompanyHq(context, renderer, 1744, 938, 1.5f)),
                new SnapshotArtifact("fleet-armory-overview-wide.png",
                        renderFleetArmoryOverview(
                                context, renderer, 1744, 938)),
                new SnapshotArtifact("fleet-armory-overview-low-resolution.png",
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
                                context, renderer, 1744, 938, true, true)),
                new SnapshotArtifact("fleet-armory-equipment-designer-wide.png",
                        renderEquipmentDesigner(context, renderer, 1744, 938)),
                new SnapshotArtifact("fleet-armory-equipment-designer-low-resolution.png",
                        renderEquipmentDesigner(context, renderer, 1163, 625)),
                new SnapshotArtifact("mech-lab-wide.png",
                        renderMechLab(context, renderer, 1744, 938, 1f)),
                new SnapshotArtifact("mech-lab-low-resolution.png",
                        renderMechLab(context, renderer, 1163, 625, 1f)),
                new SnapshotArtifact("mech-lab-ui-scale-150.png",
                        renderMechLab(context, renderer, 1744, 938, 1.5f)),
                new SnapshotArtifact("mech-lab-asset-picker-wide.png",
                        renderMechLab(context, renderer, 1744, 938, 1f, true)));
    }

    private static BufferedImage renderCompanyHq(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale) throws Exception {
        Reactor reactor = new Reactor();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), COMPANY_HQ_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "company-hq", CompanyHqViewModel.preview().props())) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document, width, height, uiScale);
        }
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
            return renderRelative(renderer, document, width, height, 1f);
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
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    private static BufferedImage renderEquipmentDesigner(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        FleetArmoryViewModel armory = new FleetArmoryViewModel(reactor, roster);
        EquipmentDoctrineDesignerViewModel designer = new EquipmentDoctrineDesignerViewModel(
                reactor, roster, armory.selectedSquadId(),
                armory.selectedWeaponDoctrineId(), armory.selectedArmorDoctrineId());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory-doctrine-designer", designerProps(designer))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            HeadlessArmoryPreviewRenderer armoryPreview =
                    new HeadlessArmoryPreviewRenderer(context.modRoot());
            for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
                int billet = index;
                document.canvases().set(instance.requireElement(
                                "designer-marine-preview:" + index),
                        new ArmoryMarinePreviewCanvas(
                                () -> designer.viewerBilletAt(billet), armoryPreview.assets()));
            }
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale) throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale, false);
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen) throws Exception {
        Reactor reactor = new Reactor();
        MechBay bay = new MechBay();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id()));
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_03", "Sirocco 03", MechVariant.SIROCCO,
                MechRole.LR_SUPPORT, MissileReplenisherComponent.ACCELERATED_FEED.id()));
        bay.addReplenisher(MissileReplenisherComponent.ACCELERATED_FEED.id(), 1);
        MechLabViewModel viewModel = new MechLabViewModel(reactor, bay);
        if (pickerOpen) viewModel.openAssetPickerAction().run();
        HeadlessArmoryPreviewRenderer technicianPreview =
                new HeadlessArmoryPreviewRenderer(context.modRoot());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), MECH_LAB_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "mech-lab", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("mech-doll-canvas"),
                    new MechLabDollCanvas(viewModel::selectedVariant,
                            MechLabDollCanvas::headlessAssets,
                            () -> technicianPreview.assets().layered(
                                    MarineArmorPattern.ARMY_GREEN),
                            () -> null));
            return renderRelative(renderer, document, width, height, uiScale);
        }
    }

    private static BufferedImage renderRelative(HeadlessUiRenderer renderer,
                                                UiDocument document,
                                                int width, int height,
                                                float uiScale) {
        return renderer.renderRelative(document, width, height, uiScale,
                MarineOpsUiViewport.REFERENCE_WIDTH,
                MarineOpsUiViewport.REFERENCE_HEIGHT);
    }

    private static Map<String, Object> props(FleetArmoryOverviewViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fleetSummary", viewModel.fleetSummary());
        props.put("companyCards", viewModel.companyCards());
        putArmoryPageNavigation(props);
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
        props.put("designEquipment", (Runnable) () -> { });
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
        putArmoryPageNavigation(props);
        return props;
    }

    private static Map<String, Object> designerProps(
            EquipmentDoctrineDesignerViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("squadName", viewModel.squadName());
        props.put("designerHeading", viewModel.heading());
        props.put("designerSubheading", viewModel.subheading());
        props.put("draftName", viewModel.draftName());
        props.put("editName", viewModel.editName());
        props.put("definitions", viewModel.definitions());
        props.put("teamTabs", viewModel.teamTabs());
        props.put("billets", viewModel.billets());
        props.put("feedback", viewModel.feedback());
        props.put("showWeapons", viewModel.showWeapons());
        props.put("showArmor", viewModel.showArmor());
        props.put("newDraft", viewModel.newDraft());
        props.put("cloneSelected", viewModel.cloneSelected());
        props.put("saveAsNew", viewModel.saveAsNew());
        props.put("rename", viewModel.rename());
        props.put("renameDisabled", viewModel.renameDisabled());
        props.put("delete", viewModel.delete());
        props.put("deleteDisabled", viewModel.deleteDisabled());
        props.put("backToFireTeams", (Runnable) () -> { });
        props.put("back", (Runnable) () -> { });
        putArmoryPageNavigation(props);
        return props;
    }

    private static void putArmoryPageNavigation(Map<String, Object> props) {
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY,
                () -> { }, () -> { }, () -> { }, () -> { });
    }

    private static Map<String, Object> props(MechLabViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("selectedMechName", viewModel.selectedMechName());
        props.put("selectedMechIdentity", viewModel.selectedMechIdentity());
        props.put("selectedMechDoctrine", viewModel.selectedMechDoctrine());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("leftSlotRows", viewModel.leftSlotRows());
        props.put("rightSlotRows", viewModel.rightSlotRows());
        props.put("slotRows", viewModel.slotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("selectedSlotRule", viewModel.selectedSlotRule());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("pickerClasses", viewModel.pickerClasses());
        props.put("workspaceClasses", viewModel.workspaceClasses());
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }
}
