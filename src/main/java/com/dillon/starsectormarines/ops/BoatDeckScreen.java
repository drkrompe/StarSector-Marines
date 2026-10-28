package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.PointerButton;
import com.dillon.starsectormarines.ops.battleview.BoatDoll;
import com.dillon.starsectormarines.ops.battleview.BoatHardware;
import com.dillon.starsectormarines.ops.fitting.BoardHover;
import com.dillon.starsectormarines.ops.fitting.PointerDrag;
import com.dillon.starsectormarines.ops.fitting.FittingDragGhost;
import com.dillon.starsectormarines.ops.fitting.FittingBricks;
import com.dillon.starsectormarines.ops.fitting.FittingComponents;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import com.dillon.starsectormarines.campaign.refit.ShipRefits;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas;
import com.dillon.starsectormarines.ops.battleview.BoatHardwareCanvas;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiPointerEvent;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planet-free shipboard room over the company's own boats.
 *
 * <p>A camera on the ship's hangar, in the Mech Lab's grammar: the bay as it
 * actually is, the boats standing in their berths, and beside it whichever of
 * the two right-hand faces the selection calls for — the whole deck, or one
 * boat with her slots and their catalog.
 *
 * <p><b>The deck is reconciled on every attach, before anything is read off
 * it.</b> A company that has moved ship carries its boats into the new hull's
 * berths, and a room that presented a deck belonging to a ship they have left
 * would be showing boats that are not there. That is done through
 * {@code ShipsBoatsAboard}, which is the one place that knows what the
 * reconcile means; this screen only decides when.
 */
public final class BoatDeckScreen implements Screen {

    private static final String ROOT_COMPONENT = "boat-deck";
    private static final List<String> COMPONENT_PATHS = FittingComponents.with(List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/boat-deck/boat-deck.mlx"));

    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> bayContextLabel = reactor.signal("");
    private final MutableSignal<String> activeBayLabel = reactor.signal("");
    private final MutableSignal<String> bayNavigatorClasses = reactor.signal("bay-navigator hidden");
    private final MutableSignal<String> bayNoteClasses = reactor.signal("hidden");
    private final MutableSignal<Integer> selectedBayIndex = reactor.signal(0);
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private MarineRoster roster;
    private CompanyDeck deck;
    private BoatDeckViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private PreparationGuide preparationGuide;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private BoatDeckCanvas bayCanvas;
    private UiElement bayElement;
    /** Every part's sheet, on its stores card and fitted on the doll. */
    private SpecSheetBinder specSheets;
    private UiElement ghostElement;
    private final FittingDragGhost dragGhost = new FittingDragGhost();
    /** A stores part on the end of the pointer, on its way to a socket on the board. */
    /** The board's pointer; the drag holds it, so nothing covers the socket a part is going into. */
    private final BoardHover boardHover = new BoardHover();
    private final PointerDrag<Carried> drag = new PointerDrag<Carried>().holding(boardHover);
    /** The stores panel, which takes back a part lifted off the board. */
    private UiElement storesElement;
    private UiElement returnDropNote;
    /**
     * Whether the ship has to be looked up again because what is standing in
     * her berths changed. Answered on the next advance rather than where the
     * change happens: the change happens inside a click handler, and taking the
     * deck out from under the document that is dispatching it is a tear-down in
     * the middle of a traversal.
     */
    private boolean deckMayHaveChanged;
    /** Drives the doll's wire pulses; wraps hourly so it never loses float precision. */
    private double elapsedSeconds;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        // One brick size for the room, from the document it lays out on, before any card is built.
        FittingBricks.fitTo(viewport.documentHeight());
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster liveRoster = script != null ? script.roster() : null;
        if (liveRoster == null) {
            context.goTo(ScreenId.COMPANY_HQ);
            return;
        }
        ShipsBoatsAboard.reconcile(liveRoster.boatDeck());
        CompanyDeck liveDeck = context.companyDeck();
        if (viewModel == null || roster != liveRoster || deck != liveDeck) {
            closeDocument();
            roster = liveRoster;
            deck = liveDeck;
            selectedBayIndex.set(0);
            viewModel = new BoatDeckViewModel(reactor, roster.boatDeck(),
                    CampaignFabricationResources.forBoats(), ShipsBoatsAboard.carrier(),
                    this::deckChanged);
            viewModel.reserve(roster.reserveHangar(), () -> ShipRefits.aboard().reserveCapacity());
        } else {
            viewModel.refresh();
        }
        // Rebinding uses a new model when the shell re-enters this screen.
        if (document != null) closeDocument();
        refreshBayPresentation();
        if (document == null) installDocument();
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        if (preparationGuide != null) preparationGuide.update();
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, ROOT_COMPONENT, props());
        UiDocument built;
        try {
            requireWiredElements(candidate);
            built = PreparationGuide.document(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::close);
            bayElement = candidate.requireElement("boat-deck-canvas");
            bayCanvas = bayCanvas(deck);
            built.canvases().set(bayElement, bayCanvas);
            ghostElement = candidate.requireElement("boat-drag-ghost");
            storesElement = candidate.requireElement("boat-catalog");
            returnDropNote = candidate.requireElement("boat-drag-drop-note");
            built.canvases().set(ghostElement, dragGhost);
            specSheets = new SpecSheetBinder(built, SpecSheetLayer.install(built))
                    .heldWhile(boardHover::held);
            // A fitted part on the doll opens the sheet its stores card does, beside the part.
            BoatDeckSpecSheets.bindDoll(specSheets, built, bayElement, () -> bayCanvas, viewModel::selectedPattern);
            wireStores(candidate, built);
            bayElement.onPointerMove(this::pointAtBerth);
            bayElement.onPointerDown(this::pressBerth);
            bayElement.onPointerUp(this::activateBerth);
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
        preparationGuide = PreparationGuide.install(built, () -> CompanyPreparationGuide.withDepotAccess(roster,
                CompanyPreparationGuide.boats(roster, viewModel, context), this::close), this::close);
        markupInstance = candidate;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", bayContextLabel);
        props.put("bayNoteClasses", bayNoteClasses);
        props.put("activeBayLabel", activeBayLabel);
        props.put("bayNavigatorClasses", bayNavigatorClasses);
        props.put("previousBay", (Runnable) () -> cycleBay(-1));
        props.put("nextBay", (Runnable) () -> cycleBay(1));
        props.put("deckSummary", viewModel.deckSummary());
        props.put("materialStock", viewModel.materialStock());
        props.put("boatRows", viewModel.boatRows());
        props.put("recovery", viewModel.recovery());
        props.put("selectedBoatName", viewModel.selectedBoatName());
        props.put("selectedBoatIdentity", viewModel.selectedBoatIdentity());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("readinessRows", viewModel.readinessRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("filterPills", viewModel.filterPills());
        props.put("filterClasses", viewModel.filterClasses());
        props.put("backClasses", viewModel.backClasses());
        props.put("stageTitle", viewModel.stageTitle());
        props.put("stageStatus", viewModel.stageStatus());
        props.put("overviewClasses", viewModel.overviewClasses());
        props.put("fittingClasses", viewModel.fittingClasses());
        props.put("fabricationClasses", viewModel.fabricationClasses());
        props.put("fabricationBerthLabel", viewModel.fabricationBerthLabel());
        props.put("fabricationPatternName", viewModel.fabricationPatternName());
        props.put("fabricationCopy", viewModel.fabricationCopy());
        props.put("fabricationMaterials", viewModel.fabricationMaterials());
        props.put("fabricationActionClasses", viewModel.fabricationActionClasses());
        props.put("fabricationBlocked", viewModel.fabricationBlocked());
        props.put("fabricationReason", viewModel.fabricationReason());
        props.put("fabricationActionLabel", viewModel.fabricationActionLabel());
        props.put("fabricate", viewModel.fabricateAction());
        props.put("backToDeck", viewModel.backToDeckAction());
        props.put("scrapLabel", viewModel.scrapLabel());
        props.put("scrapClasses", viewModel.scrapClasses());
        props.put("scrapAction", viewModel.scrapAction());
        viewModel.putReserveProps(props);
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BOAT_DECK,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.BOAT_DECK),
                () -> context.goTo(ScreenId.MECH_LAB),
                // The room's own route puts the deck back, the way the Mech
                // Lab's does: a page already open is not a dead button.
                viewModel.backToDeckAction(), context::openEngineering, context::openStore);
        return props;
    }

    /**
     * Gives each stores card its piece on its bricks. Rows are keyed and come and go as the
     * filter changes, so this runs after every reconciliation; a card keeps its picture.
     */
    static void wireCatalogPreviews(MarkupInstance instance, UiDocument target,
                                    List<BoatDeckViewModel.CatalogRow> rows) {
        for (BoatDeckViewModel.CatalogRow row : rows) {
            UiElement canvas = instance.requireElement(row.previewId());
            if (target.canvases().producerOf(canvas) == null) {
                target.canvases().set(canvas, new BoatHardwareCanvas(row.kind(), row.itemId()));
            }
        }
    }

    /** The pictures, and each card as a handle the part is picked up by, as the lab's stores are. */
    private void wireStores(MarkupInstance instance, UiDocument target) {
        List<BoatDeckViewModel.CatalogRow> rows = viewModel.catalogRows().get();
        wireCatalogPreviews(instance, target, rows);
        BoatDeckSpecSheets.bindCatalogRows(specSheets, instance, rows, viewModel::selectedPattern);
        for (BoatDeckViewModel.CatalogRow row : rows) {
            UiElement card = instance.requireElement(row.id());
            card.onPointerDown(event -> pickUp(event, row));
            card.onPointerMove(this::carry);
            card.onPointerUp(this::letGo);
        }
    }

    /** Picks a part up out of stores; a card with nothing to carry only offers its BUY. */
    private void pickUp(UiPointerEvent event, BoatDeckViewModel.CatalogRow row) {
        if (event.button() != PointerButton.PRIMARY) return;
        // A press on the card's own BUY button is that button's click, not a pick-up.
        for (UiElement e = event.target(); e != null && e != event.currentTarget(); e = e.parent()) {
            if (e.clickable()) return;
        }
        if (!viewModel.canPickUp(row.kind(), row.itemId())) return;
        drag.begin(event, new Carried(row.kind(), row.itemId(), null));
    }

    private void carry(UiPointerEvent event) {
        if (!drag.move(event)) return;
        Carried carried = drag.carried();
        dragGhost.show((c, x, y) -> BoatHardware.drawCarried(c, carried.kind(), carried.itemId(), x, y),
                event.x(), event.y());
        invalidateGhost();
        CanvasPoint point = canvasPoint(event);
        String socket = point == null ? null : bayCanvas.slotAt(point.x(), point.y());
        if (socket != null && socket.equals(carried.source())) socket = null;
        bayCanvas.previewDrop(socket, socket != null && (carried.source() == null
                ? viewModel.canDropOnSocket(carried.kind(), carried.itemId(), socket)
                : viewModel.canMoveBetweenSockets(carried.source(), socket)),
                BoatHardware.footprint(carried.kind(), carried.itemId())[1] > 1);
        returnDropNote.classed("drop-active", carried.source() != null && overStores(event));
        invalidateBay();
    }

    private void letGo(UiPointerEvent event) {
        Carried carried = drag.carried();
        boolean moved = drag.moved();
        drag.end(event);
        if (carried == null) return;
        dragGhost.clear();
        invalidateGhost();
        bayCanvas.clearDropPreview();
        returnDropNote.removeClass("drop-active");
        CanvasPoint point = canvasPoint(event);
        String socket = point == null ? null : bayCanvas.slotAt(point.x(), point.y());
        if (!moved) {
            if (carried.source() != null) viewModel.selectDollSlot(carried.source());
        } else if (carried.source() == null) {
            if (socket != null) viewModel.dropOnSocket(carried.kind(), carried.itemId(), socket);
        } else if (overStores(event)) {
            viewModel.returnToStores(carried.source());
        } else if (socket != null && !socket.equals(carried.source())) {
            viewModel.moveBetweenSockets(carried.source(), socket);
        }
        invalidateBay();
        event.preventDefault();
    }

    private boolean overStores(UiPointerEvent event) {
        return storesElement != null && storesElement.box().borderBox().contains(event.x(), event.y());
    }

    private void invalidateGhost() {
        if (document != null && ghostElement != null) document.canvases().invalidate(ghostElement);
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "boat-deck-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks",
                "page-nav-mech-lab", "page-nav-boats",
                "boat-deck-room-bar", "boat-deck-room-name", "boat-bay-navigator",
                "boat-previous-bay", "boat-active-bay", "boat-next-bay",
                "boat-deck-summary", "boat-deck-body", "boat-deck-canvas",
                "boat-overview", "boat-list", "boat-fitting",
                "boat-fabrication", "boat-fabrication-header", "boat-fabrication-berth",
                "boat-fabrication-pattern", "boat-fabrication-back",
                "boat-fabrication-copy", "boat-fabrication-materials",
                "boat-fabricate", "boat-fabrication-reason",
                "selected-boat-name", "selected-boat-identity", "boat-back-to-deck",
                "boat-performance-grid", "boat-slot-rack", "boat-catalog",
                "boat-catalog-heading", "boat-catalog-copy", "boat-catalog-list",
                "boat-category-filters", "boat-drag-ghost",
                "boat-deck-feedback")) {
            component.requireElement(id);
        }
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
    }

    private void invalidateBay() {
        if (document != null && bayElement != null) document.canvases().invalidate(bayElement);
    }

    /**
     * What the room ran after changing something: put any refitted guns on the
     * parked boats, repaint the bay, and look the ship up again in case a berth
     * changed hands.
     */
    private void deckChanged() {
        if (deck != null) deck.refitDoorGuns();
        invalidateBay();
        deckMayHaveChanged = true;
    }

    /**
     * Whether the company still has a boat for this campaign berth.
     *
     * <p>A berth the campaign deck does not have at all counts as held, for the
     * same reason the canvas draws no mark on one: the two counts are measured
     * equal rather than guaranteed equal, and a disagreement is not a hole.
     */
    private boolean berthHeld(int berth) {
        if (roster == null) return true;
        List<CampaignBoat> berths = roster.boatDeck().boats();
        return berth < 0 || berth >= berths.size() || berths.get(berth) != null;
    }

    /**
     * Take delivery of a ship laid out again because her berths changed hands.
     *
     * <p>The picture follows the deck: a fabricated boat arrives as a
     * regenerated ship rather than as an airframe pushed into a running scene,
     * so what has to happen here is swapping the camera onto the new one. The
     * document and the view model stay, because the player is still looking at
     * the same berth of the same room.
     */
    private void adoptChangedDeck() {
        deckMayHaveChanged = false;
        if (context == null || document == null || bayElement == null) return;
        CompanyDeck liveDeck = context.companyDeck();
        if (liveDeck == null || liveDeck == deck) return;
        deck = liveDeck;
        bayCanvas = bayCanvas(deck);
        document.canvases().set(bayElement, bayCanvas);
        refreshBayPresentation();
        invalidateBay();
    }

    /** The bay's camera, with the doll the selected boat is drawn as. */
    private BoatDeckCanvas bayCanvas(CompanyDeck ship) {
        BoatDeckCanvas canvas = new BoatDeckCanvas(ship, this::currentBay,
                viewModel::selectedBerthIndex, this::berthOffset, this::berthHeld)
                .recovery(viewModel::berthRecovery)
                .doll(() -> viewModel.doll().get(), () -> elapsedSeconds)
                .hover(boardHover);
        canvas.snap();
        return canvas;
    }

    private void pointAtBerth(UiPointerEvent event) {
        if (drag.active()) {
            carry(event);
            return;
        }
        CanvasPoint point = canvasPoint(event);
        if (point == null) return;
        bayCanvas.pointAt(point.x(), point.y());
        invalidateBay();
    }

    private void pressBerth(UiPointerEvent event) {
        CanvasPoint point = canvasPoint(event);
        if (point == null) return;
        // A part comes off the board as it goes on: lifted by the pointer and dropped on stores.
        String slot = bayCanvas.slotAt(point.x(), point.y());
        if (slot != null && event.button() == PointerButton.PRIMARY && viewModel.canLiftOff(slot)) {
            drag.begin(event, new Carried(viewModel.kindOf(slot), viewModel.installedIn(slot), slot));
            return;
        }
        if (slot == null && bayCanvas.berthAt(point.x(), point.y()) < 0) return;
        event.capturePointer();
        event.preventDefault();
    }

    private void activateBerth(UiPointerEvent event) {
        if (drag.active()) {
            letGo(event);
            return;
        }
        CanvasPoint point = canvasPoint(event);
        event.releasePointerCapture();
        if (point == null) return;
        String slot = bayCanvas.slotAt(point.x(), point.y());
        if (slot != null) {
            viewModel.selectDollSlot(slot);
            invalidateBay();
            event.preventDefault();
            return;
        }
        int berth = bayCanvas.berthAt(point.x(), point.y());
        if (berth < 0) return;
        viewModel.selectBoatAction(berth).run();
        invalidateBay();
        event.preventDefault();
    }

    private CanvasPoint canvasPoint(UiPointerEvent event) {
        if (document == null || bayElement == null || bayCanvas == null) return null;
        CanvasMetrics metrics = document.canvasMetrics(bayElement, 1f);
        float x = metrics.toCanvasX(event.x());
        float y = metrics.toCanvasY(event.y());
        return Float.isFinite(x) && Float.isFinite(y) ? new CanvasPoint(x, y) : null;
    }

    private List<DeckGraph.Compartment> hangars() {
        return deck == null ? List.of() : deck.rooms(RoomPurpose.HANGAR);
    }

    private DeckGraph.Compartment currentBay() {
        List<DeckGraph.Compartment> bays = hangars();
        if (bays.isEmpty()) return null;
        int index = Math.max(0, Math.min(bays.size() - 1, selectedBayIndex.get()));
        return bays.get(index);
    }

    /**
     * How many of the campaign deck's berths lie in the hangars ahead of the
     * one on screen.
     *
     * <p>The campaign deck is one list across the whole ship and a bay knows
     * only its own berths, so this is the arithmetic that lets a berth in the
     * second hangar select the boat the deck actually keeps there instead of
     * the first hangar's.
     */
    private int berthOffset() {
        if (deck == null || !deck.ready()) return 0;
        int offset = 0;
        for (DeckGraph.Compartment bay : hangars()) {
            if (bay == currentBay()) break;
            offset += BoatDeckCanvas.boatBerthsIn(deck.scene(), bay).size();
        }
        return offset;
    }

    private void cycleBay(int delta) {
        List<DeckGraph.Compartment> bays = hangars();
        if (bays.size() < 2) return;
        viewModel.backToDeckAction().run();
        selectedBayIndex.set(Math.floorMod(selectedBayIndex.get() + delta, bays.size()));
        refreshBayPresentation();
        invalidateBay();
    }

    private void refreshBayPresentation() {
        List<DeckGraph.Compartment> bays = hangars();
        if (bays.isEmpty()) {
            activeBayLabel.set(Strings.get("openingBoatDeckNoHangar"));
            bayNavigatorClasses.set("bay-navigator hidden");
            bayNoteClasses.set("hidden");
            bayContextLabel.set(deck == null ? "" : ShipBreadcrumb.of(deck.ship(), null));
            return;
        }
        int index = Math.max(0, Math.min(bays.size() - 1, selectedBayIndex.get()));
        selectedBayIndex.set(index);
        activeBayLabel.set(Strings.format("openingBoatDeckBayPosition", index + 1, bays.size(),
                deck.rooms().damaged(bays.get(index).id()) ? Strings.get("openingBoatDeckSealed") : ""));
        bayNavigatorClasses.set(bays.size() > 1 ? "bay-navigator" : "bay-navigator hidden");
        // The bay is restored from Engineering; this room only says where.
        bayNoteClasses.set(bays.stream().anyMatch(bay -> deck.rooms().damaged(bay.id()))
                ? "bay-sealed-note label tone-muted" : "hidden");
        bayContextLabel.set(ShipBreadcrumb.of(deck.ship(), bays.get(index)));
    }

    @Override
    public void advance(float dt) {
        if (deckMayHaveChanged) adoptChangedDeck();
        if (Float.isFinite(dt)) elapsedSeconds = (elapsedSeconds + Math.max(0f, dt)) % 3600d;
        if (viewModel != null) viewModel.refreshTime();
        if (deck != null && deck.ready()) {
            int selected = viewModel.selectedBerthIndex();
            int offset = 0;
            List<DeckGraph.Compartment> bays = hangars();
            for (int index = 0; selected >= 0 && index < bays.size(); index++) {
                int count = BoatDeckCanvas.boatBerthsIn(deck.scene(), bays.get(index)).size();
                if (selected < offset + count) { selectedBayIndex.set(index); break; }
                offset += count;
            }
            refreshBayPresentation();
        }
        if (markupInstance != null) {
            markupInstance.flush();
            if (document != null) wireStores(markupInstance, document);
        }
        if (context != null) MarineOpsPageNav.refresh(markupInstance, MarineOpsPageNav.Page.BOAT_DECK, context::roomAboard, CompanyNotices.current());
        if (document != null) document.advance(dt);
        if (specSheets != null) specSheets.update();
        if (bayElement != null) BoatDeckCanvas.fitSurface(bayElement);
        // A dragged part is drawn at the board's brick size only on a surface the screen's size.
        if (ghostElement != null) ghostElement.fitCanvasToBox();
        // The doll's wires pulse and the camera eases, so the bay is redrawn every frame it is open on a boat.
        if (bayCanvas != null && (bayCanvas.advance(dt) || viewModel.fittingFocused())) invalidateBay();
        if (preparationGuide != null) preparationGuide.update();
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
        // The deck outlives the page, on both senses of the word: the ship is
        // run by the panel, and the company's boats are campaign state that a
        // screen closing has no business touching.
        if (document != null) document.deactivateInput();
        input = null;
    }

    private void closeDocument() {
        if (document != null) document.deactivateInput();
        if (markupInstance != null) markupInstance.close();
        document = null;
        markupInstance = null;
        bayCanvas = null;
        bayElement = null;
        ghostElement = null;
        storesElement = null;
        returnDropNote = null;
        if (specSheets != null) specSheets.clear();
        specSheets = null;
        drag.cancel();
        dragGhost.clear();
        input = null;
    }

    private record CanvasPoint(float x, float y) { }

    /** A part on the pointer, and the socket it was lifted off, or null when it came out of stores. */
    private record Carried(BoatDoll.Kind kind, String itemId, String source) { }
}
