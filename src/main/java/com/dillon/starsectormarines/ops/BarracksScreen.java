package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.battleview.BarracksCanvas;
import com.dillon.starsectormarines.ops.battleview.BattleShotAudio;
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

/**
 * Planet-free, read-only shipboard room for casually browsing line squads.
 *
 * <p>A camera on the company ship's berthing, not a room beside her. The
 * marines in the panel are the roster's own, mustered into the compartment the
 * deck generator gave them, and they are still there - a little further round
 * their watch - when the player comes back from another page.
 */
public final class BarracksScreen implements Screen {

    private static final String ROOT_COMPONENT = "shipboard-barracks";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/shipboard-barracks.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private MarineRoster roster;
    private BarracksViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private int projectedCampaignHour = Integer.MIN_VALUE;

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
        // Defensive for a live session upgraded before onGameLoad can run;
        // ordinary campaign loads establish this before any company screen.
        script.ensureStartingCompany();
        if (viewModel == null || roster != liveRoster) {
            closeDocument();
            roster = liveRoster;
            viewModel = new BarracksViewModel(reactor, roster, CampaignClock::dayFloat);
        } else {
            viewModel.refresh();
        }
        projectedCampaignHour = campaignHour();
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
            built.canvases().set(candidate.requireElement("barracks-canvas"),
                    new BarracksCanvas(context.companyDeck(), viewModel::sceneMarines));
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
        props.put("squadRows", viewModel.squadRows());
        props.put("musterRows", viewModel.musterRows());
        props.put("recordCells", viewModel.recordCells());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("selectedSquadSummary", viewModel.selectedSquadSummary());
        props.put("quartersStatus", viewModel.quartersStatus());
        // Computed rather than fixed: selecting a formation moves the camera to
        // that squad's own berthing, and a heading that stayed put would name a
        // different compartment from the one on screen.
        props.put("contextLabel", reactor.computed(() -> ShipBreadcrumb.of(
                context.companyDeck().ship(),
                context.companyDeck().quartersFor(viewModel.sceneMarines()))));
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BARRACKS,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> { },
                () -> context.openCompanyArmoryFrom(ScreenId.BARRACKS),
                () -> context.goTo(ScreenId.MECH_LAB),
                () -> context.goTo(ScreenId.BOAT_DECK));
        return props;
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "barracks-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks", "page-nav-armory",
                "page-nav-mech-lab", "page-nav-boats",
                "barracks-room-bar", "barracks-body",
                "barracks-squad-list", "barracks-stage", "barracks-canvas",
                "barracks-muster-list")) {
            component.requireElement(id);
        }
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void advance(float dt) {
        // The ship is run by the panel, not by this page. What is left here is
        // the part that belongs to standing in the berthing: hearing the range
        // through the bulkhead.
        if (context != null && context.companyDeck().live()) {
            BattleShotAudio.playCampaignLocal(
                    context.companyDeck().scene().simulation().getShotsThisFrame(), 0.55f);
        }
        int currentHour = campaignHour();
        if (viewModel != null && currentHour != projectedCampaignHour) {
            projectedCampaignHour = currentHour;
            viewModel.refresh();
        }
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(dt);
    }

    private static int campaignHour() {
        return (int) Math.floor(CampaignClock.dayFloat() * 24f);
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
        // The ship outlives the page. Closing her scene here would end the
        // continuity every other room view depends on; the panel puts her away
        // with the dialog.
        if (document != null) document.deactivateInput();
        input = null;
    }

    private void closeDocument() {
        if (document != null) document.deactivateInput();
        if (markupInstance != null) markupInstance.close();
        document = null;
        markupInstance = null;
        input = null;
    }
}
