package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.DeckPlanCanvas;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiPointerEvent;
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

/**
 * Choosing which ship in the fleet the company lives aboard.
 *
 * <p>The one screen in the shell that is not somewhere aboard. Every other room
 * view is a camera on the company ship; this is the decision about which ship
 * that is, so it shows hulls from outside rather than compartments from within.
 *
 * <p>Each candidate is drawn as her own deck. A list of numbers can say a hull
 * has eighteen berthings; only the plan says they run down a long thin spine
 * with the bay amidships, and recognising the ship is most of what makes the
 * choice feel like a choice. The plans are generated exactly as the company's
 * own deck is, from the same seed, so what the player is shown is what they
 * would get.
 */
public final class ShipTransferScreen implements Screen {

    private static final String ROOT_COMPONENT = "ship-transfer";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/ship-transfer.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private ShipTransferViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private DeckPlanCanvas plan;
    private UiElement planElement;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        if (viewModel == null) {
            viewModel = new ShipTransferViewModel(reactor);
        } else {
            viewModel.refresh();
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
            plan = new DeckPlanCanvas(viewModel::selectedPlan);
            planElement = candidate.requireElement("transfer-plan");
            built.canvases().set(planElement, plan);
            planElement.onPointerMove(this::pointAtDeck);
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
        props.put("candidateRows", viewModel.candidateRows());
        props.put("facilityCells", viewModel.facilityCells());
        props.put("selectedName", viewModel.selectedName());
        props.put("selectedSummary", viewModel.selectedSummary());
        props.put("verdict", viewModel.verdict());
        props.put("transferLabel", viewModel.transferLabel());
        props.put("transferClasses", viewModel.transferClasses());
        props.put("transferAction", (Runnable) this::transfer);
        props.put("roomTitle", viewModel.roomTitle());
        props.put("roomCopy", viewModel.roomCopy());
        props.put("contextLabel", viewModel.contextLabel());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_TRANSFER,
                context::shipHasRoom,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.SHIP_TRANSFER),
                () -> context.goTo(ScreenId.MECH_LAB));
        return props;
    }

    /**
     * Moving house is recorded and nothing else. The ship every other page is a
     * camera on follows the designation, so she is put away and regenerated the
     * next time anybody asks for her — a transfer changes what the Barracks and
     * the Mech Lab show without either screen learning that one happened.
     */
    private void transfer() {
        viewModel.commit();
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "transfer-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks", "page-nav-armory",
                "page-nav-mech-lab", "transfer-room-bar", "transfer-body",
                "transfer-fleet-list", "transfer-stage", "transfer-plan",
                "transfer-commit", "transfer-facility-cells")) {
            component.requireElement(id);
        }
    }

    /**
     * Follow the cursor across the plan so it can name what is under it.
     *
     * <p>Converted here rather than in the canvas because only the document
     * knows where the canvas sits and how its surface is scaled; the plan is
     * told where it is being pointed at in its own coordinates and answers for
     * itself.
     */
    private void pointAtDeck(UiPointerEvent event) {
        if (document == null || plan == null || planElement == null) return;
        CanvasMetrics metrics = document.canvasMetrics(planElement, 1f);
        float x = metrics.toCanvasX(event.x());
        float y = metrics.toCanvasY(event.y());
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        plan.pointAt(x, y);
        document.canvases().invalidate(planElement);
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
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
        if (document != null) document.deactivateInput();
        input = null;
    }
}
