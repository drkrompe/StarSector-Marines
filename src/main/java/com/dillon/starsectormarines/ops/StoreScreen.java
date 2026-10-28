package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CivilizedPort;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import com.dillon.starsectormarines.marine.EquipmentStore;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.StoreSlot;
import com.dillon.starsectormarines.ops.battleview.StoreBrickCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The company store: the technology the docked market teaches for credits.
 *
 * <p>A sister page to the shipboard rooms but not a room: it is a counter at a port, so the shell
 * offers it only while the fleet is docked at a civilized market ({@link MarineOpsPageNav#storeOpen})
 * and {@link MarineOpsContext#openStore} refuses the route otherwise. The rules of the shelf and of
 * a sale are {@link EquipmentStore}'s; this screen only hosts the page.
 */
public final class StoreScreen implements Screen {

    private static final String ROOT_COMPONENT = "company-store";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-store.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private StoreViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        if (viewModel == null) {
            viewModel = new StoreViewModel(reactor, StoreScreen::liveShelf, StoreScreen::holdings,
                    new CampaignFabricationResources(), StoreScreen::marketName);
        } else {
            // Opened again: the market may be another one, and the month may have turned.
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
            for (String id : List.of("store-root", "marine-ops-page-nav", "page-nav-return", "page-nav-shop",
                    "store-room-bar", "store-body", "store-offer-list", "store-buy", "store-selected-detail")) {
                candidate.requireElement(id);
            }
            built = PreparationGuide.document(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::close);
            if (viewport != null) built.layout(viewport.documentWidth(), viewport.documentHeight());
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
        Map<String, Object> props = props(viewModel);
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.STORE,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.STORE),
                () -> context.goTo(ScreenId.MECH_LAB),
                () -> context.goTo(ScreenId.BOAT_DECK), context::openEngineering,
                // The page already open is not a dead button.
                viewModel::refresh);
        return props;
    }

    /** The page's own properties, without the navigation shell; shared with the evidence suite. */
    static Map<String, Object> props(StoreViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("resourceStock", viewModel.resourceStock());
        props.put("roomTitle", Strings.get("storeRoomTitle"));
        props.put("roomCopy", Strings.get("storeRoomCopy"));
        props.put("contextLabel", viewModel.contextLabel());
        props.put("shelfNote", viewModel.shelfNote());
        props.put("categoryRows", viewModel.categoryRows());
        props.put("offerRows", viewModel.rows());
        props.put("selectedName", viewModel.selectedName());
        props.put("selectedKind", viewModel.selectedKind());
        props.put("selectedDetail", viewModel.selectedDetail());
        props.put("priceLabel", viewModel.priceLabel());
        props.put("buyLabel", viewModel.buyLabel());
        props.put("buyClasses", viewModel.buyClasses());
        props.put("buyAction", (Runnable) viewModel::buySelected);
        props.put("verdict", viewModel.verdict());
        return props;
    }

    private static List<EquipmentStore.Offer> liveShelf() {
        return EquipmentStore.shelfAt(CivilizedPort.docked());
    }

    private static EquipmentStore.Holdings holdings() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script == null ? new EquipmentStore.Holdings(null, null, null)
                : new EquipmentStore.Holdings(script.roster().armory(), script.roster().mechBay(),
                        script.roster().boatDeck());
    }

    private static String marketName() {
        MarketAPI market = CivilizedPort.docked();
        return market == null ? "" : market.getName();
    }

    /**
     * Gives each shelf row and slot row its brick symbol. Rows are keyed and come and go as the
     * category changes, so this runs after every reconciliation; a row keeps its picture. Shared
     * with the evidence suite, which draws the page the same way.
     */
    static void bindSymbols(UiDocument target, MarkupInstance instance, StoreViewModel model) {
        for (StoreViewModel.OfferRow row : model.rows().get()) {
            bindSymbol(target, instance, row.symbolId(), row.symbol());
        }
        for (StoreViewModel.CategoryRow row : model.categoryRows().get()) {
            bindSymbol(target, instance, row.symbolId(), row.symbol());
        }
    }

    private static void bindSymbol(UiDocument target, MarkupInstance instance, String id, StoreSlot slot) {
        StoreBrickCanvas symbol = StoreBrickCanvas.of(slot);
        if (symbol == null) return;
        UiElement canvas = instance.requireElement(id);
        if (target.canvases().producerOf(canvas) == null) target.canvases().set(canvas, symbol);
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void advance(float dt) {
        if (markupInstance != null) markupInstance.flush();
        if (markupInstance != null && document != null) bindSymbols(document, markupInstance, viewModel);
        if (context != null) {
            MarineOpsPageNav.refresh(markupInstance, MarineOpsPageNav.Page.STORE, context::roomAboard,
                    CompanyNotices.current());
        }
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
