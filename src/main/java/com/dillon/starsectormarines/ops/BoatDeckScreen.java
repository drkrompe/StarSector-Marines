package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas;
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
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/boat-deck/boat-deck.mlx");

    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> bayContextLabel = reactor.signal("");
    private final MutableSignal<String> activeBayLabel = reactor.signal("");
    private final MutableSignal<String> bayNavigatorClasses = reactor.signal("bay-navigator hidden");
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
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private BoatDeckCanvas bayCanvas;
    private UiElement bayElement;

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
        ShipsBoatsAboard.reconcile(liveRoster.boatDeck());
        CompanyDeck liveDeck = context.companyDeck();
        if (viewModel == null || roster != liveRoster || deck != liveDeck) {
            closeDocument();
            roster = liveRoster;
            deck = liveDeck;
            selectedBayIndex.set(0);
            viewModel = new BoatDeckViewModel(reactor, roster.boatDeck(),
                    new CampaignFabricationResources(), ShipsBoatsAboard.carrier(),
                    this::invalidateBay);
        } else {
            viewModel.refresh();
        }
        refreshBayPresentation();
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
            bayElement = candidate.requireElement("boat-deck-canvas");
            bayCanvas = new BoatDeckCanvas(deck, this::currentBay,
                    viewModel::selectedBerthIndex, this::berthOffset);
            built.canvases().set(bayElement, bayCanvas);
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
        markupInstance = candidate;
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
        props.put("deckSummary", viewModel.deckSummary());
        props.put("boatRows", viewModel.boatRows());
        props.put("selectedBoatName", viewModel.selectedBoatName());
        props.put("selectedBoatIdentity", viewModel.selectedBoatIdentity());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("slotRows", viewModel.slotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("overviewClasses", viewModel.overviewClasses());
        props.put("fittingClasses", viewModel.fittingClasses());
        props.put("backToDeck", viewModel.backToDeckAction());
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
                viewModel.backToDeckAction());
        return props;
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "boat-deck-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks", "page-nav-armory",
                "page-nav-mech-lab", "page-nav-boats",
                "boat-deck-room-bar", "boat-deck-room-name", "boat-bay-navigator",
                "boat-previous-bay", "boat-active-bay", "boat-next-bay",
                "boat-deck-summary", "boat-deck-body", "boat-deck-canvas",
                "boat-overview", "boat-list", "boat-fitting",
                "selected-boat-name", "selected-boat-identity", "boat-back-to-deck",
                "boat-performance-grid", "boat-slot-rack", "boat-catalog",
                "boat-catalog-heading", "boat-catalog-copy", "boat-catalog-list",
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

    private void pointAtBerth(UiPointerEvent event) {
        CanvasPoint point = canvasPoint(event);
        if (point == null) return;
        bayCanvas.pointAt(point.x(), point.y());
        invalidateBay();
    }

    private void pressBerth(UiPointerEvent event) {
        CanvasPoint point = canvasPoint(event);
        if (point == null || bayCanvas.berthAt(point.x(), point.y()) < 0) return;
        event.capturePointer();
        event.preventDefault();
    }

    private void activateBerth(UiPointerEvent event) {
        CanvasPoint point = canvasPoint(event);
        event.releasePointerCapture();
        if (point == null) return;
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
        selectedBayIndex.set(Math.floorMod(selectedBayIndex.get() + delta, bays.size()));
        refreshBayPresentation();
        invalidateBay();
    }

    private void refreshBayPresentation() {
        List<DeckGraph.Compartment> bays = hangars();
        if (bays.isEmpty()) {
            activeBayLabel.set("NO HANGAR");
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

    @Override
    public void advance(float dt) {
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
        input = null;
    }

    private record CanvasPoint(float x, float y) { }
}
