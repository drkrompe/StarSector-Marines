package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.fitting.FittingComponents;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.refit.ShipRefits;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.ops.battleview.LayeredMechAssets;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.MechLabCameraController;
import com.dillon.starsectormarines.ops.battleview.MechChassisPreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.LanceSlotCanvas;
import com.dillon.starsectormarines.ops.battleview.VehiclePreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.MechEquipmentGridCanvas;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
import com.dillon.starsectormarines.ops.battleview.MechWeaponDragCanvas;
import org.lwjgl.input.Keyboard;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.PointerButton;
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
import java.util.Set;

/** Planet-free shipboard room for active support-lance selection and mech refits. */
public final class MechLabScreen implements Screen {

    private static final String ROOT_COMPONENT = "mech-lab";
    private static final List<String> COMPONENT_PATHS = FittingComponents.with(List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/mech-lab/mech-lab.mlx"));

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
    private PreparationGuide preparationGuide;
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
    private UiElement ghostElement;
    private MechWeaponDragCanvas dragGhost;
    private UiElement inventoryDropElement;
    private UiElement returnDropNote;
    private WeaponDrag weaponDrag;
    private boolean weaponDragMoved;
    private double previewSeconds;
    private static final float DRAG_THRESHOLD_SQUARED = 16f;

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
                    new CampaignFabricationResources(true), this::syncGantryScene,
                    CampaignClock::dayFloat, ShipRefits::aboard);
            viewModel.physicalGantries(berths().size());
            syncGantryScene();
            resetCamera(false);
        } else {
            viewModel.refresh();
            syncGantryScene();
        }
        viewModel.resumeCampaignLesson();
        refreshBayPresentation();
        if (document == null) installDocument();
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        if (preparationGuide != null) preparationGuide.update();
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        cancelDrag();
        MarkupInstance candidate = markup.reloadAndBuild(reactor, ROOT_COMPONENT, props());
        UiDocument built;
        SpecSheetBinder candidateSheets = null;
        try {
            requireWiredElements(candidate);
            built = PreparationGuide.document(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard());
            preparationGuide = PreparationGuide.install(built, () -> {
                var roster = CompanyPreparationGuide.roster();
                return CampaignLessons.next(roster) == CampaignLessons.Step.BOAT_RECOVERY
                        ? CampaignLessons.handoff(roster, context, ScreenId.MECH_LAB) : viewModel.campaignLesson();
            }, this::close);
            dollElement = candidate.requireElement("mech-doll-canvas");
            dollCanvas = new MechLabDollCanvas(viewModel::gantryDeployments,
                            viewModel::selectedGantryIndex,
                            viewModel::selectedSocket,
                            this::previewMechs,
                            viewModel::fittingFocused,
                            deck::drawable,
                            this::framing,
                            this::berths,
                            this::currentBayId,
                            () -> previewSeconds,
                            viewModel::hoveredWeapon)
                    .withOccupiedGantries(viewModel::occupiedGantries)
                    .withSideShift(() -> cameraController.pose().sideShift())
                    .withPadPreview(viewModel::padPreview);
            dollCanvas.installedInternals(viewModel::installedInternal);
            built.canvases().set(dollElement, dollCanvas);
            inventoryDropElement = candidate.requireElement("mech-component-catalog");
            returnDropNote = candidate.requireElement("mech-drag-drop-note");
            ghostElement = candidate.requireElement("mech-drag-ghost");
            dragGhost = new MechWeaponDragCanvas(this::previewMechs);
            built.canvases().set(ghostElement, dragGhost);
            candidateSheets = new SpecSheetBinder(built, SpecSheetLayer.install(built));
            wireEquipmentPreviews(candidate, built, candidateSheets);
            dollElement.onPointerMove(this::moveOverDoll);
            dollElement.onPointerDown(this::pressDoll);
            dollElement.onPointerUp(this::releaseDoll);
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
        viewModel.putTutorialProps(props);
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("gantryRows", viewModel.gantryRows());
        props.put("garageTitle", viewModel.garageTitle());
        props.put("garageStatus", viewModel.garageStatus());
        props.put("dragNoteTitle", viewModel.dragNoteTitle());
        props.put("dragNoteCopy", viewModel.dragNoteCopy());
        props.put("scrapLabel", viewModel.scrapLabel());
        props.put("scrapClasses", viewModel.scrapClasses());
        props.put("scrapAction", viewModel.scrapAction());
        viewModel.putBuildPanelProps(props);
        props.put("performanceMeters", viewModel.performanceMeters());
        viewModel.putInternalProps(props);
        viewModel.putRepairProps(props);
        props.put("leftSlotRows", viewModel.leftSlotRows());
        props.put("rightSlotRows", viewModel.rightSlotRows());
        props.put("slotRows", viewModel.selectedSlotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("selectedSlotRule", viewModel.selectedSlotRule());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("materialStock", viewModel.materialStock());
        props.put("pickerClasses", viewModel.pickerClasses());
        props.put("workspaceClasses", viewModel.workspaceClasses());
        props.put("fittingHeaderClasses", viewModel.fittingHeaderClasses());
        props.put("backToLance", viewModel.overviewAction());
        props.put("performanceClasses", viewModel.performanceClasses());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("slotRackClasses", viewModel.slotRackClasses());
        props.put("overviewRailClasses", viewModel.overviewRailClasses());
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("categoryFilterPills", viewModel.categoryFilterPills());
        props.put("categoryFilterClasses", viewModel.categoryFilterClasses());
        props.put("allPartsClasses", viewModel.allPartsClasses());
        props.put("showAllParts", (Runnable) viewModel::showAllParts);
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.MECH_LAB),
                viewModel.overviewAction(),
                () -> context.goTo(ScreenId.BOAT_DECK), context::openEngineering);
        return props;
    }

    /**
     * Catalog rows are keyed and can appear after the document is installed
     * when the player moves from a fitted gantry to a vacant one. Wire every
     * newly attached chassis, equipment, and socket-grid canvas after reconciliation.
     */
    /** The picker's pad tiles and asset-card thumbnails; keyed by occupant, so a changed pad is a new canvas. */
    private void wireLancePictures(MarkupInstance instance, UiDocument target) {
        for (MechLabViewModel.SquadRow squad : viewModel.squadRows().get()) {
            for (MechLabViewModel.LanceSlot slot : squad.slots()) {
                UiElement canvas = instance.requireElement(slot.id());
                if (target.canvases().producerOf(canvas) == null) {
                    target.canvases().set(canvas, new LanceSlotCanvas(slot.letter(), slot.mech(), slot.vehicle(),
                            this::previewMechs));
                }
            }
        }
        for (MechLabViewModel.MechRow row : viewModel.mechRows().get()) {
            UiElement canvas = instance.requireElement(row.previewId());
            if (target.canvases().producerOf(canvas) == null) {
                target.canvases().set(canvas, new LanceSlotCanvas(row.letter(),
                        row.mech(), row.vehicle(), this::previewMechs));
            }
        }
    }

    private void wireEquipmentPreviews(MarkupInstance instance, UiDocument target,
                                       SpecSheetBinder sheets) {
        MechLabSpecSheets.bindCatalogRows(sheets, instance, viewModel.catalogRows().get(), viewModel);
        wireLancePictures(instance, target);
        MechLabSpecSheets.bindMaterials(sheets, instance, viewModel);
        MechLabSpecSheets.bindFilterPills(sheets, instance, viewModel.categoryFilterPills().get());
        MechLabSpecSheets.bindActions(sheets, instance, viewModel);
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            UiElement canvas = instance.requireElement(row.previewId());
            if (target.canvases().producerOf(canvas) == null) {
                if (row.chassisPreview() != null) {
                    target.canvases().set(canvas, new MechChassisPreviewCanvas(
                            CampaignMech.frame(row.id(), row.chassisPreview()), this::previewMechs));
                } else if (row.vehiclePreview() != null) {
                    target.canvases().set(canvas, new VehiclePreviewCanvas(row.vehiclePreview()));
                } else if (row.weaponPreview() != null || row.replenisherPreview() != null
                        || row.internalPreview() != null) {
                    target.canvases().set(canvas, MechEquipmentGridCanvas.catalog(
                            row.weaponPreview(), row.replenisherPreview(), row.internalPreview(),
                            this::previewMechs));
                }
            }
            // The whole row is the drag handle, not only its picture.
            boolean draggable = row.weaponPreview() != null || row.replenisherPreview() != null
                    || row.internalPreview() != null
                    && row.internalPreview().role != MechInternal.Role.PLATING;
            UiElement card = instance.requireElement(row.id());
            card.onPointerDown(event -> {
                if (row.selectsOnClick()) row.select().run();
                if (draggable) beginCatalogDrag(event, row);
            });
            card.onPointerMove(event -> {
                if (weaponDrag != null) {
                    moveWeaponDrag(event);
                    return;
                }
                if (row.weaponPreview() != null) {
                    viewModel.hoverWeapon(row.weaponPreview());
                    if (dollElement != null) target.canvases().invalidate(dollElement);
                } else if (row.internalPreview() != null) {
                    viewModel.hoverInternal(row.internalPreview());
                }
            });
            card.onPointerUp(draggable ? this::finishWeaponDrag : null);
        }
        for (MechLabViewModel.SlotRow row : viewModel.selectedSlotRows().get()) {
            UiElement canvas = instance.requireElement(row.gridId());
            if (target.canvases().producerOf(canvas) == null) {
                target.canvases().set(canvas, new MechEquipmentGridCanvas(
                        () -> viewModel.socketDefinition(row.socketId()),
                        () -> viewModel.installedWeapon(row.socketId()),
                        () -> viewModel.installedReplenisher(row.socketId()),
                        () -> viewModel.installedInternal(row.socketId()),
                        () -> viewModel.socketOccupied(row.socketId()),
                        this::previewMechs));
            }
        }
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "mech-lab-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks",
                "page-nav-mech-lab", "page-nav-boats",
                "mech-lab-room-bar", "mech-bay-navigator", "mech-previous-bay",
                "mech-active-bay", "mech-next-bay", "mech-lab-body", "mech-asset-picker",
                "mech-squad-list", "mech-list", "mech-fitting-workspace", "mech-back-to-lance",
                "mech-performance-grid", "mech-garage-stage", "mech-doll-canvas",
                "mech-overview-rail", "mech-overview-gantries", "mech-overview-browse",
                "mech-slot-rack", "mech-component-catalog",
                "mech-catalog-list", "mech-drag-drop-note")) {
            component.requireElement(id);
        }
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
    }

    private void moveOverDoll(UiPointerEvent event) {
        if (weaponDrag != null) {
            moveWeaponDrag(event);
            return;
        }
        CanvasPoint point = canvasPoint(event);
        if (point == null) return;
        dollCanvas.pointAt(point.x(), point.y());
        document.canvases().invalidate(dollElement);
    }

    private void pressDoll(UiPointerEvent event) {
        if (event.button() != PointerButton.PRIMARY) return;
        CanvasPoint point = canvasPoint(event);
        if (point == null) return;
        SocketId strip = dollCanvas.stripAt(point.x(), point.y());
        if (strip != null) {
            viewModel.stripWeapon(strip);
            document.canvases().invalidate(dollElement);
            event.preventDefault();
            return;
        }
        SocketId socket = dollCanvas.socketAt(point.x(), point.y());
        MechWeaponComponent component = socket != null
                ? viewModel.installedWeapon(socket) : null;
        if (component != null) {
            beginWeaponDrag(event, component, socket);
            return;
        }
        MechInternal internal = socket != null ? viewModel.installedInternal(socket) : null;
        if (internal != null) {
            weaponDrag = new WeaponDrag(null, internal, null, socket, event.x(), event.y());
            weaponDragMoved = false;
            event.capturePointer();
            event.preventDefault();
            return;
        }
        if (socket != null) {
            viewModel.selectSocket(socket);
            document.canvases().invalidate(dollElement);
            event.preventDefault();
            return;
        }
        if (dollCanvas.vacantGantryAt(point.x(), point.y()) < 0) return;
        event.capturePointer();
        event.preventDefault();
    }

    private void releaseDoll(UiPointerEvent event) {
        if (weaponDrag != null) {
            finishWeaponDrag(event);
            return;
        }
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

    /** Picks a spare up out of stores; a row with none to carry only offers its FAB button. */
    private void beginCatalogDrag(UiPointerEvent event, MechLabViewModel.CatalogRow row) {
        if (event.button() != PointerButton.PRIMARY) return;
        // A press on the row's own FAB button is that button's click, not a pick-up.
        for (UiElement e = event.target(); e != null && e != event.currentTarget(); e = e.parent()) {
            if (e.clickable()) return;
        }
        MechWeaponComponent weapon = row.weaponPreview();
        MechInternal internal = row.internalPreview();
        MissileReplenisherComponent loader = row.replenisherPreview();
        boolean spare = weapon != null ? viewModel.inStores(weapon)
                : internal != null ? viewModel.inStores(internal) : viewModel.inStores(loader);
        if (!spare) return;
        weaponDrag = new WeaponDrag(weapon, internal, loader, null, event.x(), event.y());
        weaponDragMoved = false;
        event.capturePointer();
        event.preventDefault();
    }

    private void beginWeaponDrag(UiPointerEvent event, MechWeaponComponent component,
                                 SocketId source) {
        weaponDrag = new WeaponDrag(component, null, null, source, event.x(), event.y());
        weaponDragMoved = false;
        event.capturePointer();
        event.preventDefault();
    }

    private void moveWeaponDrag(UiPointerEvent event) {
        if (weaponDrag == null) return;
        float dx = event.x() - weaponDrag.startX();
        float dy = event.y() - weaponDrag.startY();
        if (!weaponDragMoved && dx * dx + dy * dy < DRAG_THRESHOLD_SQUARED) return;
        weaponDragMoved = true;
        WeaponDrag drag = weaponDrag;
        if (drag.component() != null) dragGhost.show(drag.component(), event.x(), event.y());
        else if (drag.internal() != null) dragGhost.show(drag.internal(), event.x(), event.y());
        else dragGhost.show(drag.loader(), event.x(), event.y());
        document.canvases().invalidate(ghostElement);
        CanvasPoint point = canvasPoint(event);
        SocketId target = point != null ? dollCanvas.socketAt(point.x(), point.y()) : null;
        // An installed internal has only its own socket; it can go back to stores and nowhere else.
        boolean valid = target != null && (drag.source() != null
                ? drag.component() != null && viewModel.canMoveInstalledWeapon(drag.source(), target)
                : drag.component() != null ? viewModel.canDropCatalogWeapon(drag.component(), target)
                : drag.internal() != null ? viewModel.canDropCatalogInternal(drag.internal(), target)
                : viewModel.canDropCatalogLoader(drag.loader(), target));
        if (drag.component() != null) {
            dollCanvas.previewWeaponDrag(drag.component(), drag.source(), target, valid);
        } else {
            dollCanvas.previewPartDrag(target, valid);
        }
        boolean returning = weaponDrag.source() != null && overInventory(event);
        returnDropNote.classed("drop-active", returning);
        document.canvases().invalidate(dollElement);
        event.preventDefault();
    }

    private void finishWeaponDrag(UiPointerEvent event) {
        if (event.button() != PointerButton.PRIMARY) return;
        WeaponDrag drag = weaponDrag;
        if (drag == null) return;
        event.releasePointerCapture();
        CanvasPoint point = canvasPoint(event);
        SocketId target = point != null ? dollCanvas.socketAt(point.x(), point.y()) : null;
        if (!weaponDragMoved) {
            if (drag.source() != null) viewModel.selectSocket(drag.source());
        } else if (drag.source() != null && drag.component() == null) {
            if (overInventory(event)) viewModel.removeInstalledInternal(drag.source());
            else if (target == drag.source()) viewModel.selectSocket(target);
            else viewModel.cancelWeaponDrag();
        } else if (target != null && target != drag.source()) {
            if (drag.source() == null) {
                if (drag.component() != null) viewModel.dropCatalogWeapon(drag.component(), target);
                else if (drag.internal() != null) viewModel.dropCatalogInternal(drag.internal(), target);
                else viewModel.dropCatalogLoader(drag.loader(), target);
            } else {
                viewModel.moveInstalledWeapon(drag.source(), target);
            }
        } else if (drag.source() != null && overInventory(event)) {
            viewModel.removeInstalledWeapon(drag.source());
        } else if (drag.source() != null && target == drag.source()) {
            viewModel.selectSocket(target);
        } else {
            viewModel.cancelWeaponDrag();
        }
        weaponDrag = null;
        weaponDragMoved = false;
        dragGhost.clear();
        document.canvases().invalidate(ghostElement);
        dollCanvas.clearWeaponDragPreview();
        returnDropNote.removeClass("drop-active");
        document.canvases().invalidate(dollElement);
        event.preventDefault();
    }

    private boolean overInventory(UiPointerEvent event) {
        return inventoryDropElement != null
                && inventoryDropElement.box().borderBox().contains(event.x(), event.y());
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

    /**
     * The unpainted machine set the doll and the previews compose from, loaded under the deck's
     * lease. The doll is not the deck scene: it composes a machine at rest against its own
     * backdrop, so it loads what it draws, and asks again each time because a deck that released
     * its textures while the lab was closed hands out a new sprite set when it is reopened.
     */
    private LayeredMechAssets previewMechs() {
        BattleSprites sprites = previewSprites();
        if (sprites == null) return null;
        sprites.ensureLayeredMechSprites(Set.of());
        return sprites.layeredMechSprites();
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
        if (viewModel != null) viewModel.physicalGantries(berths().size());
        List<DeckGraph.Compartment> bays = mechBays();
        if (bays.isEmpty()) {
            activeBayLabel.set(Strings.get("openingMechLabNoMechBay"));
            bayNavigatorClasses.set("bay-navigator hidden");
            bayContextLabel.set(deck == null ? "" : ShipBreadcrumb.of(deck.ship(), null));
            return;
        }
        int index = Math.max(0, Math.min(bays.size() - 1, selectedBayIndex.get()));
        selectedBayIndex.set(index);
        activeBayLabel.set(String.format(Strings.locale(),
                Strings.get("openingMechLabBayDD"), index + 1, bays.size()));
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
        if (document != null && viewModel != null && viewModel.labGuideVisible())
            CompanyNotices.Notice.HOUND_INDUCTION.review(CompanyPreparationGuide.roster());
        if (Float.isFinite(dt)) previewSeconds = (previewSeconds + Math.max(0f, dt)) % 3600d;
        if (viewModel != null) {
            viewModel.refreshTime();
            cameraController.target(viewModel.fittingFocused(),
                    viewModel.selectedGantryIndex(), viewModel.gantryVariants().size());
            cameraController.advance(dt);
        }
        if (markupInstance != null) {
            markupInstance.flush();
            if (context != null) MarineOpsPageNav.refresh(markupInstance, MarineOpsPageNav.Page.MECH_LAB, context::roomAboard, CompanyNotices.current());
            if (document != null) {
                wireEquipmentPreviews(markupInstance, document, specSheets);
            }
        }
        if (document != null && weaponDrag == null && viewModel.hasHoverCandidate()) {
            boolean overCatalog = document.hoverChain().stream().anyMatch(element ->
                    viewModel.catalogRows().get().stream().anyMatch(row -> row.id().equals(element.id())));
            if (!overCatalog) viewModel.clearHoverCandidates();
        }
        if (specSheets != null) specSheets.update();
        if (document != null) {
            document.advance(dt);
            if (dollElement != null) document.canvases().invalidate(dollElement);
            if (preparationGuide != null) preparationGuide.update();
        }
    }

    @Override
    public void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (weaponDrag != null) {
            for (InputEventAPI event : events) {
                if (!event.isConsumed() && event.isKeyDownEvent()
                        && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                    cancelDrag();
                    event.consume();
                }
            }
        }
        if (input != null) input.process(events);
    }

    private void cancelDrag() {
        if (weaponDrag == null) return;
        weaponDrag = null;
        weaponDragMoved = false;
        if (dragGhost != null) dragGhost.clear();
        if (dollCanvas != null) dollCanvas.clearWeaponDragPreview();
        if (returnDropNote != null) returnDropNote.removeClass("drop-active");
        if (document != null) {
            document.releasePointerCapture();
            if (ghostElement != null) document.canvases().invalidate(ghostElement);
            if (dollElement != null) document.canvases().invalidate(dollElement);
            if (preparationGuide != null) preparationGuide.update();
        }
        viewModel.cancelWeaponDrag();
    }

    @Override
    public void detach() {
        cancelDrag();
        if (document != null) document.deactivateInput();
        if (specSheets != null) specSheets.clear();
        input = null;
        // The ship is not this screen's to close. She outlives the page - that
        // is the point of her being the ship rather than this screen's diorama.
    }

    private void closeDocument() {
        cancelDrag();
        if (document != null) document.deactivateInput();
        if (markupInstance != null) markupInstance.close();
        if (specSheets != null) specSheets.clear();
        document = null;
        markupInstance = null;
        specSheets = null;
        dollCanvas = null;
        dollElement = null;
        inventoryDropElement = null;
        returnDropNote = null;
        weaponDrag = null;
        input = null;
    }

    private record CanvasPoint(float x, float y) { }

    /**
     * Hardware on the pointer: one of a weapon, an internal or a missile loader. Only a
     * weapon is ever lifted off the doll ({@code source}); the others come out of stores.
     */
    private record WeaponDrag(MechWeaponComponent component, MechInternal internal,
                              MissileReplenisherComponent loader, SocketId source,
                              float startX, float startY) { }
}
