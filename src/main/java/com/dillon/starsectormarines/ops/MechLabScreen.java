package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.CampaignMechFabricationResources;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.MechLabCameraController;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Planet-free shipboard room for active support-lance selection and mech refits. */
public final class MechLabScreen implements Screen {

    private static final String ROOT_COMPONENT = "mech-lab";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/mech-lab/mech-lab.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);
    /**
     * The bay's own bulkheads are worth seeing, so the framing keeps a couple of
     * cells of ship around the room: without them a hatch and a hole in the
     * bulkhead look the same.
     */
    private static final int SURROUND_CELLS = 2;

    private MechLabCameraController cameraController;

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private MarineRoster roster;
    private MechLabViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private double previewSeconds;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster liveRoster = script != null ? script.roster() : null;
        if (liveRoster == null) {
            context.goTo(ScreenId.COMPANY_HQ);
            return;
        }
        if (viewModel == null || roster != liveRoster) {
            closeDocument();
            roster = liveRoster;
            viewModel = new MechLabViewModel(reactor, roster.mechBay(),
                    new CampaignMechFabricationResources(), this::syncGantryScene);
            syncGantryScene();
            cameraController = new MechLabCameraController(
                    MechLabCameraController.on(bayFraming(), berths()));
            cameraController.snap(false, viewModel.selectedGantryIndex(),
                    viewModel.gantryVariants().size());
        } else {
            viewModel.refresh();
            syncGantryScene();
        }
        if (document == null) installDocument();
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, ROOT_COMPONENT, props());
        UiDocument built;
        try {
            requireWiredElements(candidate);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::close);
            // The doll is not the deck scene: it composes a machine at rest
            // against its own backdrop, so it loads what it draws. Everything
            // the deck itself paints is the deck's own business.
            previewSprites().ensureLayeredMechSprites();
            previewSprites().ensureMechLabFxSprites();
            built.canvases().set(candidate.requireElement("mech-doll-canvas"),
                    new MechLabDollCanvas(viewModel::gantryDeployments,
                            viewModel::selectedGantryIndex,
                            viewModel::selectedSocket,
                            () -> previewSprites().layeredMechSprites(),
                            () -> previewSprites().mechLabWeldingTorch(),
                            () -> previewSprites().mechLabWeldingSparks(),
                            viewModel::fittingFocused,
                            () -> context.companyDeck().scene(),
                            this::framing,
                            this::berths,
                            () -> previewSeconds));
            if (viewport != null) {
                built.layout(viewport.documentWidth(), viewport.documentHeight());
            }
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }

        UiDocument previousDocument = document;
        MarkupInstance previousInstance = markupInstance;
        document = built;
        markupInstance = candidate;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", ShipBreadcrumb.of(context.companyDeck().ship(),
                context.companyDeck().room(RoomPurpose.VEHICLE_BAY)));
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("gantryRows", viewModel.gantryRows());
        props.put("activeGantryLabel", viewModel.activeGantryLabel());
        props.put("garageTitle", viewModel.garageTitle());
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
        props.put("fittingHeaderClasses", viewModel.fittingHeaderClasses());
        props.put("performanceClasses", viewModel.performanceClasses());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("slotRackClasses", viewModel.slotRackClasses());
        props.put("overviewRailClasses", viewModel.overviewRailClasses());
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("previousGantry", viewModel.previousGantryAction());
        props.put("nextGantry", viewModel.nextGantryAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.MECH_LAB),
                viewModel.overviewAction());
        return props;
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "mech-lab-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks", "page-nav-armory",
                "page-nav-mech-lab",
                "mech-lab-room-bar", "mech-lab-body", "mech-asset-picker",
                "mech-squad-list", "mech-list", "mech-fitting-workspace",
                "mech-previous-gantry", "mech-active-gantry", "mech-next-gantry",
                "mech-performance-grid", "mech-garage-stage", "mech-doll-canvas",
                "mech-overview-rail", "mech-overview-gantries", "mech-overview-browse",
                "mech-slot-rack", "mech-component-catalog",
                "mech-catalog-list",
                "mech-lab-feedback")) {
            component.requireElement(id);
        }
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
    }

    /**
     * The compartment this screen is a camera on: the company ship's vehicle
     * bay. The lab is a place aboard, not a room built beside the ship.
     */
    private ShipDeckBattleScene.RoomView bayFraming() {
        DeckGraph.Compartment bay = context.companyDeck().room(RoomPurpose.VEHICLE_BAY);
        if (bay == null) throw new IllegalStateException("this ship has no vehicle bay");
        return ShipDeckBattleScene.RoomView.of(bay, SURROUND_CELLS);
    }

    /**
     * The ship's own sprite cache, not this screen's.
     *
     * <p>One per ship for the same reason there is one deck: two screens onto
     * the same vessel loading their own copies of her tiles is the duplication
     * this screen is being moved off.
     */
    private BattleSprites previewSprites() {
        return context.companyDeck().sprites();
    }

    private void syncGantryScene() {
        if (context == null || viewModel == null || context.companyDeck() == null) return;
        context.companyDeck().scene().syncGantries(viewModel.gantryDeployments());
    }

    /** The berths standing in that bay, in the order the deck authored them. */
    private List<Gantry> berths() {
        DeckGraph.Compartment bay = context.companyDeck().room(RoomPurpose.VEHICLE_BAY);
        if (bay == null) return List.of();
        return context.companyDeck().scene().berthsIn(bay);
    }

    /** The framing for this frame: the bay, looked at from the eased camera pose. */
    private ShipDeckBattleScene.RoomView framing() {
        MechLabCameraController.CameraPose pose = cameraController.pose();
        return bayFraming().lookingAt(pose.worldX(), pose.worldY(), pose.zoomNotches());
    }

    @Override
    public void advance(float dt) {
        previewSeconds += Math.max(0f, dt);
        if (viewModel != null) {
            cameraController.target(viewModel.fittingFocused(),
                    viewModel.selectedGantryIndex(), viewModel.gantryVariants().size());
            cameraController.advance(dt);
        }
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(dt);
    }

    @Override
    public void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (input != null) input.process(events);
    }

    @Override
    public void detach() {
        if (document != null) document.deactivateInput();
        input = null;
        // The ship is not this screen's to close. She outlives the page - that
        // is the point of her being the ship rather than this screen's diorama.
    }

    private void closeDocument() {
        if (document != null) document.deactivateInput();
        if (markupInstance != null) markupInstance.close();
        document = null;
        markupInstance = null;
        input = null;
    }
}
