package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.MechLabCameraController;
import com.dillon.starsectormarines.ops.battleview.MechChassisPreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.MechEquipmentGridCanvas;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiPointerEvent;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Planet-free shipboard room for active support-lance selection and mech refits. */
public final class MechLabScreen implements Screen {

    private static final String ROOT_COMPONENT = "mech-lab";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/mech-lab/mech-lab.mlx");

    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> bayContextLabel = reactor.signal("");
    private final MutableSignal<String> activeBayLabel = reactor.signal("");
    private final MutableSignal<String> bayNavigatorClasses = reactor.signal("bay-navigator hidden");
    private final MutableSignal<Integer> selectedBayIndex = reactor.signal(0);
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
    private CompanyDeck deck;
    private MechLabViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    /**
     * This screen's spec-sheet bindings. It is not a {@code MissionFlowMlxScreen},
     * so it owns the binder itself and drives it from {@link #advance}, beside the
     * paper-doll hover rather than instead of it: pointing at a catalog row still
     * previews that weapon on the doll, and now also says what it is.
     */
    private SpecSheetBinder specSheets;
    private StarsectorUiInputAdapter input;
    private MechLabDollCanvas dollCanvas;
    private UiElement dollElement;
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
        CompanyDeck liveDeck = context.companyDeck();
        if (viewModel == null || roster != liveRoster || deck != liveDeck) {
            closeDocument();
            roster = liveRoster;
            deck = liveDeck;
            selectedBayIndex.set(0);
            viewModel = new MechLabViewModel(reactor, roster.mechBay(),
                    new CampaignFabricationResources(), this::syncGantryScene);
            syncGantryScene();
            resetCamera(false);
        } else {
            viewModel.refresh();
            syncGantryScene();
        }
        refreshBayPresentation();
        if (document == null) installDocument();
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, ROOT_COMPONENT, props());
        UiDocument built;
        SpecSheetBinder candidateSheets = null;
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
            dollElement = candidate.requireElement("mech-doll-canvas");
            dollCanvas = new MechLabDollCanvas(viewModel::gantryDeployments,
                            viewModel::selectedGantryIndex,
                            viewModel::selectedSocket,
                            () -> previewSprites().layeredMechSprites(),
                            () -> previewSprites().mechLabWeldingTorch(),
                            () -> previewSprites().mechLabWeldingSparks(),
                            viewModel::fittingFocused,
                            deck::scene,
                            this::framing,
                            this::berths,
                            this::currentBayId,
                            () -> previewSeconds,
                            viewModel::hoveredWeapon);
            built.canvases().set(dollElement, dollCanvas);
            candidateSheets = new SpecSheetBinder(built, SpecSheetLayer.install(built));
            wireEquipmentPreviews(candidate, built, candidateSheets);
            dollElement.onPointerMove(this::pointAtDoll);
            dollElement.onPointerDown(this::pressDoll);
            dollElement.onPointerUp(this::activateDoll);
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
        specSheets = candidateSheets;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", bayContextLabel);
        props.put("activeBayLabel", activeBayLabel);
        props.put("bayNavigatorClasses", bayNavigatorClasses);
        props.put("previousBay", (Runnable) () -> cycleBay(-1));
        props.put("nextBay", (Runnable) () -> cycleBay(1));
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("gantryRows", viewModel.gantryRows());
        props.put("activeGantryLabel", viewModel.activeGantryLabel());
        props.put("garageTitle", viewModel.garageTitle());
        props.put("selectedMechName", viewModel.selectedMechName());
        props.put("selectedMechNameClasses", viewModel.selectedMechNameClasses());
        props.put("callSignDraft", viewModel.callSignDraft());
        props.put("callSignEditorClasses", viewModel.callSignEditorClasses());
        props.put("callSignRenameDisabled", viewModel.callSignRenameDisabled());
        props.put("editCallSign", viewModel.editCallSignAction());
        props.put("renameCallSign", viewModel.renameCallSignAction());
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
        props.put("categoryFilterPills", viewModel.categoryFilterPills());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.MECH_LAB),
                viewModel.overviewAction(),
                () -> context.goTo(ScreenId.BOAT_DECK));
        return props;
    }

    /**
     * Catalog rows are keyed and can appear after the document is installed
     * when the player moves from a fitted gantry to a vacant one. Wire every
     * newly attached chassis, equipment, and socket-grid canvas after reconciliation.
     */
    private void wireEquipmentPreviews(MarkupInstance instance, UiDocument target,
                                       SpecSheetBinder sheets) {
        MechLabSpecSheets.bindCatalogRows(sheets, instance, viewModel.catalogRows().get());
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            UiElement canvas = instance.requireElement(row.previewId());
            if (target.canvases().producerOf(canvas) == null) {
                if (row.chassisPreview() != null) {
                    target.canvases().set(canvas, new MechChassisPreviewCanvas(
                            row.chassisPreview(), () -> previewSprites().layeredMechSprites()));
                } else if (row.weaponPreview() != null || row.replenisherPreview() != null) {
                    target.canvases().set(canvas, MechEquipmentGridCanvas.catalog(
                            row.weaponPreview(), row.replenisherPreview(),
                            () -> previewSprites().layeredMechSprites()));
                }
            }
            UiElement card = instance.requireElement(row.id());
            card.onPointerMove(event -> {
                if (row.weaponPreview() != null) {
                    viewModel.hoverWeapon(row.weaponPreview());
                    if (dollElement != null) target.canvases().invalidate(dollElement);
                }
            });
        }
        for (MechLabViewModel.SlotRow row : viewModel.slotRows().get()) {
            UiElement canvas = instance.requireElement(row.gridId());
            if (target.canvases().producerOf(canvas) == null) {
                target.canvases().set(canvas, new MechEquipmentGridCanvas(
                        () -> viewModel.socketDefinition(row.socketId()),
                        () -> viewModel.installedWeapon(row.socketId()),
                        () -> viewModel.installedReplenisher(row.socketId()),
                        () -> viewModel.socketOccupied(row.socketId()),
                        () -> previewSprites().layeredMechSprites()));
            }
        }
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "mech-lab-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks", "page-nav-armory",
                "page-nav-mech-lab", "page-nav-boats",
                "mech-lab-room-bar", "mech-bay-navigator", "mech-previous-bay",
                "mech-active-bay", "mech-next-bay", "mech-lab-body", "mech-asset-picker",
                "mech-squad-list", "mech-list", "mech-fitting-workspace",
                "mech-previous-gantry", "mech-active-gantry", "mech-next-gantry",
                "mech-call-sign-editor", "mech-call-sign-input", "mech-call-sign-set",
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

    private void pointAtDoll(UiPointerEvent event) {
        CanvasPoint point = canvasPoint(event);
        if (point == null) return;
        dollCanvas.pointAt(point.x(), point.y());
        document.canvases().invalidate(dollElement);
    }

    private void pressDoll(UiPointerEvent event) {
        CanvasPoint point = canvasPoint(event);
        if (point == null) return;
        if (dollCanvas.vacantGantryAt(point.x(), point.y()) >= 0
                || dollCanvas.socketAt(point.x(), point.y()) != null) {
            event.capturePointer();
            event.preventDefault();
        }
    }

    private void activateDoll(UiPointerEvent event) {
        CanvasPoint point = canvasPoint(event);
        event.releasePointerCapture();
        if (point == null) return;
        int gantry = dollCanvas.vacantGantryAt(point.x(), point.y());
        if (gantry >= 0) {
            viewModel.selectGantryAction(gantry).run();
            document.canvases().invalidate(dollElement);
            event.preventDefault();
            return;
        }
        SocketId socket = dollCanvas.socketAt(point.x(), point.y());
        if (socket != null) {
            viewModel.selectSlot(socket);
            document.canvases().invalidate(dollElement);
            event.preventDefault();
        }
    }

    private CanvasPoint canvasPoint(UiPointerEvent event) {
        if (document == null || dollElement == null || dollCanvas == null) return null;
        CanvasMetrics metrics = document.canvasMetrics(dollElement, 1f);
        float x = metrics.toCanvasX(event.x());
        float y = metrics.toCanvasY(event.y());
        return Float.isFinite(x) && Float.isFinite(y) ? new CanvasPoint(x, y) : null;
    }

    /**
     * The compartment this screen is a camera on: the company ship's vehicle
     * bay. The lab is a place aboard, not a room built beside the ship.
     */
    private ShipDeckBattleScene.RoomView bayFraming() {
        DeckGraph.Compartment bay = currentBay();
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
        return deck.sprites();
    }

    private void syncGantryScene() {
        if (deck == null || viewModel == null) return;
        deck.scene().syncGantries(viewModel.gantryDeployments());
    }

    /** The berths standing in that bay, in the order the deck authored them. */
    private List<Gantry> berths() {
        DeckGraph.Compartment bay = currentBay();
        if (bay == null) return List.of();
        return deck.scene().berthsIn(bay);
    }

    private List<DeckGraph.Compartment> mechBays() {
        return deck == null ? List.of() : deck.rooms(RoomPurpose.VEHICLE_BAY);
    }

    private DeckGraph.Compartment currentBay() {
        List<DeckGraph.Compartment> bays = mechBays();
        if (bays.isEmpty()) return null;
        int index = Math.max(0, Math.min(bays.size() - 1, selectedBayIndex.get()));
        return bays.get(index);
    }

    private int currentBayId() {
        DeckGraph.Compartment bay = currentBay();
        return bay != null ? bay.id() : -1;
    }

    private void cycleBay(int delta) {
        List<DeckGraph.Compartment> bays = mechBays();
        if (bays.size() < 2) return;
        selectedBayIndex.set(Math.floorMod(selectedBayIndex.get() + delta, bays.size()));
        refreshBayPresentation();
        resetCamera(viewModel.fittingFocused());
        if (document != null && dollElement != null) {
            document.canvases().invalidate(dollElement);
        }
    }

    private void refreshBayPresentation() {
        List<DeckGraph.Compartment> bays = mechBays();
        if (bays.isEmpty()) {
            activeBayLabel.set("NO MECH BAY");
            bayNavigatorClasses.set("bay-navigator hidden");
            bayContextLabel.set(deck == null ? "" : ShipBreadcrumb.of(deck.ship(), null));
            return;
        }
        int index = Math.max(0, Math.min(bays.size() - 1, selectedBayIndex.get()));
        selectedBayIndex.set(index);
        activeBayLabel.set(String.format(Locale.ROOT,
                "BAY %02d / %02d", index + 1, bays.size()));
        bayNavigatorClasses.set(bays.size() > 1 ? "bay-navigator" : "bay-navigator hidden");
        bayContextLabel.set(ShipBreadcrumb.of(deck.ship(), bays.get(index)));
    }

    private void resetCamera(boolean focused) {
        cameraController = new MechLabCameraController(
                MechLabCameraController.on(bayFraming(), berths()));
        cameraController.snap(focused, viewModel.selectedGantryIndex(),
                viewModel.gantryVariants().size());
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
        if (markupInstance != null) {
            markupInstance.flush();
            if (document != null) {
                wireEquipmentPreviews(markupInstance, document, specSheets);
            }
        }
        if (specSheets != null) specSheets.update();
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
        if (specSheets != null) specSheets.clear();
        input = null;
        // The ship is not this screen's to close. She outlives the page - that
        // is the point of her being the ship rather than this screen's diorama.
    }

    private void closeDocument() {
        if (document != null) document.deactivateInput();
        if (markupInstance != null) markupInstance.close();
        if (specSheets != null) specSheets.clear();
        document = null;
        markupInstance = null;
        specSheets = null;
        dollCanvas = null;
        dollElement = null;
        input = null;
    }

    private record CanvasPoint(float x, float y) { }
}
