package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader.PreparedReload;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owned-company landing view between Company HQ and one company's Armory. */
public final class FleetArmoryOverviewScreen implements Screen {

    private static final Logger LOG = Global.getLogger(FleetArmoryOverviewScreen.class);
    private static final String ROOT_COMPONENT = "fleet-armory-overview";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/armory/fleet-armory-overview.mlx",
            "data/ui/components/armory/armory-company-list.mlx");

    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> reloadStatus = reactor.signal(
            "Owned-company overview  ·  Select a formation to enter its armory");
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private MarineRoster roster;
    private FleetArmoryOverviewViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private boolean reloadRequested;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        viewport = new UiViewport(position.getX(), position.getY(),
                position.getWidth(), position.getHeight());
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster liveRoster = script != null ? script.roster() : null;
        if (liveRoster == null) {
            context.returnFromArmory();
            return;
        }
        liveRoster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        liveRoster.reserveSquad();
        if (viewModel == null || roster != liveRoster) {
            closeDocument();
            roster = liveRoster;
            viewModel = new FleetArmoryOverviewViewModel(reactor, roster,
                    () -> context.openFleetArmoryWorkspaceFrom(
                            ScreenId.FLEET_ARMORY_OVERVIEW));
        } else {
            viewModel.refresh();
        }
        if (document == null) installDocument(true);
        document.layout(viewport.width(), viewport.height());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument(boolean reloadSource) {
        PreparedReload prepared = reloadSource
                ? markup.prepareReload(reactor, ROOT_COMPONENT, props()) : null;
        MarkupInstance candidate = prepared == null
                ? markup.build(reactor, ROOT_COMPONENT, props()) : prepared.instance();
        UiDocument built;
        try {
            requireWiredElements(candidate);
            candidate.requireElement("company-overview-summary")
                    .align(UiAlign.STRETCH, UiAlign.CENTER);
            candidate.requireElement("company-overview-reload-status")
                    .align(UiAlign.STRETCH, UiAlign.CENTER);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard())
                    .onCancel(() -> context.returnFromArmory());
            if (viewport != null) built.layout(viewport.width(), viewport.height());
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }

        UiDocument previousDocument = document;
        MarkupInstance previousInstance = markupInstance;
        if (prepared != null) prepared.commit();
        document = built;
        markupInstance = candidate;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
        if (viewport != null) input = new StarsectorUiInputAdapter(document, viewport);
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fleetSummary", viewModel.fleetSummary());
        props.put("companyCards", viewModel.companyCards());
        props.put("back", (Runnable) () -> context.returnFromArmory());
        props.put("legacy", (Runnable) () -> context.goTo(ScreenId.ARMORY));
        props.put("reload", (Runnable) () -> reloadRequested = true);
        props.put("reloadStatus", reloadStatus);
        return props;
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "fleet-armory-overview-root", "company-overview-header",
                "company-overview-intro", "company-overview-summary",
                "company-list", "company-overview-footer", "company-overview-back",
                "company-overview-legacy", "company-overview-reload",
                "company-overview-reload-status")) {
            component.requireElement(id);
        }
    }

    private void reloadDocument() {
        try {
            viewModel.refresh();
            installDocument(true);
            reloadStatus.set("MLX reloaded  ·  Campaign authority preserved");
            LOG.info("Reloaded retained Fleet Armory company overview");
        } catch (RuntimeException failure) {
            LOG.error("Fleet Armory overview reload refused; keeping the previous document", failure);
            reloadStatus.set("Reload refused  ·  Previous document retained");
        }
    }

    @Override
    public void advance(float dt) {
        if (reloadRequested) {
            reloadRequested = false;
            reloadDocument();
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
    }

    private void closeDocument() {
        if (document != null) document.deactivateInput();
        if (markupInstance != null) markupInstance.close();
        document = null;
        markupInstance = null;
        input = null;
    }
}
