package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.battleview.ArmoryMarinePreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.ArmoryPreviewAssets;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader.PreparedReload;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Retained company workspace: squad gallery, fire-team breakdown, and atomic issue. */
public final class FleetArmoryScreen implements Screen {

    private static final String SQUAD_COMPONENT = "fleet-armory";
    private static final String FIRETEAM_COMPONENT = "fleet-armory-fireteam";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/fleet-armory.mlx",
            "data/ui/components/armory/armory-squad-list.mlx",
            "data/ui/components/armory/fleet-armory-fireteam.mlx",
            "data/ui/components/armory/armory-fireteam-list.mlx",
            "data/ui/components/armory/armory-squad-doctrine.mlx",
            "data/ui/components/armory/armory-refit-transaction.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);
    private final ArmoryPreviewAssets previewAssets = new ArmoryPreviewAssets();

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private MarineRoster roster;
    private FleetArmoryViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private float previewAnimationSeconds;
    private int projectedCampaignHour = Integer.MIN_VALUE;
    private View view = View.SQUADS;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster liveRoster = script != null ? script.roster() : null;
        if (liveRoster == null) {
            context.returnFromFleetArmoryWorkspace();
            return;
        }
        liveRoster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        liveRoster.reserveSquad();
        if (viewModel == null || roster != liveRoster) {
            closeDocument();
            roster = liveRoster;
            viewModel = new FleetArmoryViewModel(reactor, roster, this::showSelectedSquad,
                    CampaignClock::dayFloat);
        } else {
            viewModel.refresh();
        }
        projectedCampaignHour = campaignHour();
        view = View.SQUADS;
        installDocument(true);
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument(boolean reloadSource) {
        String componentName = view == View.SQUADS ? SQUAD_COMPONENT : FIRETEAM_COMPONENT;
        PreparedReload prepared = reloadSource
                ? markup.prepareReload(reactor, componentName, props()) : null;
        MarkupInstance candidate = prepared == null
                ? markup.build(reactor, componentName, props()) : prepared.instance();
        UiDocument built;
        try {
            requireWiredElements(candidate);
            if (view == View.FIRETEAMS) {
                candidate.requireElement("transaction-feedback")
                        .align(UiAlign.STRETCH, UiAlign.CENTER);
            }
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard())
                    .onCancel(view == View.SQUADS
                            ? () -> context.returnFromFleetArmoryWorkspace()
                            : this::showSquadOverview);
            if (view == View.FIRETEAMS) {
                for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
                    int slot = index;
                    built.canvases().set(candidate.requireElement("marine-preview:" + index),
                            new ArmoryMarinePreviewCanvas(
                                    () -> viewModel.viewerBilletAt(slot), previewAssets,
                                    () -> previewAnimationSeconds + slot * 0.31d));
                }
            }
            if (viewport != null) {
                built.layout(viewport.documentWidth(), viewport.documentHeight());
            }
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
        props.put("back", (Runnable) () -> context.returnFromFleetArmoryWorkspace());
        props.put("backToSquads", (Runnable) this::showSquadOverview);
        putPageNavigation(props);
        return props;
    }

    private void putPageNavigation(Map<String, Object> props) {
        props.put("returnAction", dismissDialog);
        props.put("hqAction", (Runnable) () -> context.goTo(ScreenId.COMPANY_HQ));
        props.put("armoryAction", (Runnable) () -> { });
        props.put("hqClasses", "");
        props.put("hqDisabled", false);
        props.put("armoryClasses", "selected page-nav-current");
        props.put("armoryDisabled", false);
    }

    private void requireWiredElements(MarkupInstance component) {
        List<String> required = view == View.FIRETEAMS
                ? List.of("fleet-armory-fireteam-root", "marine-ops-page-nav",
                "page-nav-return", "page-nav-hq", "page-nav-armory",
                "fireteam-breadcrumb", "back-to-squads", "fireteam-body",
                "squad-doctrine-strip", "weapon-doctrine-list", "armor-doctrine-list",
                "fireteam-rail", "fireteam-list", "refit-transaction", "selected-squad-readiness",
                "reinforce-selected-squad", "viewer-context", "target-summary",
                "candidate-summary", "marine-card-grid", "squad-equip-row",
                "transaction-result", "apply-squad-equipment", "transaction-feedback",
                "marine-preview:0", "marine-preview:1",
                "marine-preview:2", "marine-preview:3")
                : List.of("fleet-armory-root", "marine-ops-page-nav",
                "page-nav-return", "page-nav-hq", "page-nav-armory",
                "squad-breadcrumb", "squad-overview-intro", "squad-card-list");
        for (String id : required) {
            component.requireElement(id);
        }
    }

    private void showSelectedSquad() {
        view = View.FIRETEAMS;
        previewAnimationSeconds = 0f;
        if (viewport != null) installDocument(false);
    }

    private void showSquadOverview() {
        view = View.SQUADS;
        if (viewport != null) installDocument(false);
    }

    @Override
    public void advance(float dt) {
        int currentHour = campaignHour();
        if (currentHour != projectedCampaignHour) {
            projectedCampaignHour = currentHour;
            viewModel.refresh();
        }
        if (markupInstance != null) markupInstance.flush();
        if (view == View.FIRETEAMS && Float.isFinite(dt) && dt > 0f) {
            previewAnimationSeconds = (previewAnimationSeconds + dt) % 60f;
        }
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

    private enum View { SQUADS, FIRETEAMS }
}
