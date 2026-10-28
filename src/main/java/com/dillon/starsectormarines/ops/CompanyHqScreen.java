package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.marine.HegemonyAuxiliaryWarrant;
import com.dillon.starsectormarines.campaign.AuxiliaryWarrantProgress;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.CompanyClocks;
import com.dillon.starsectormarines.ops.event.PlayerEventPresenter;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Planet-free Company HQ dashboard for readiness, standing, obligations, and news. */
public final class CompanyHqScreen implements Screen {

    private static final String ROOT_COMPONENT = "company-hq";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-hq.mlx");

    private final Reactor reactor = new Reactor();
    private final CompanyHqNewsDesk newsDesk = new CompanyHqNewsDesk(reactor);
    private final HqActionDesk actionDesk = new HqActionDesk(reactor, newsDesk);
    private final CompanyIntelTerminal intelTerminal = new CompanyIntelTerminal(newsDesk,
            CommandDeckLayout.parse(loadText(CommandDeckLayout.PATH)));
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    /** Held across rebuilds so an open letter stays open when the campaign hour turns over. */
    private final AuxiliaryWarrantDesk auxiliary = new AuxiliaryWarrantDesk(reactor,
            CompanyHqScreen::auxiliaryStage, CompanyHqScreen::acceptAuxiliary, CompanyHqScreen::declineAuxiliary,
            () -> projectedCampaignHour = Integer.MIN_VALUE);

    /** The Latchkey custody letter; held across rebuilds for the same reason as the warrant's. */
    private final AgreusHandoverDesk handover = new AgreusHandoverDesk(reactor,
            AgreusArcCampaign::handoverStage, AgreusArcCampaign::signedAgreement, AgreusArcCampaign::sign,
            AgreusArcCampaign::declineFurtherWork, () -> projectedCampaignHour = Integer.MIN_VALUE);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private UiViewport viewport;
    private UiDocument document;
    private PreparationGuide preparationGuide;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private int projectedCampaignHour = Integer.MIN_VALUE;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        boolean reactivating = input == null;
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        int currentHour = campaignHour();
        if (document == null || reactivating || currentHour != projectedCampaignHour) {
            projectedCampaignHour = currentHour;
            installDocument();
        } else {
            document.layout(viewport.documentWidth(), viewport.documentHeight());
            if (intelTerminal.place()) document.layout(viewport.documentWidth(), viewport.documentHeight());
        }
        if (preparationGuide != null) preparationGuide.update();
        if (AgreusArcCampaign.current() != null)
            CompanyNotices.Notice.AGREUS_REFERRAL.review(CompanyPreparationGuide.roster());
        if (ShepherdRecoveryCampaign.current() != null) {
            CompanyNotices.Notice.SHEPHERD_RECOVERY.review(CompanyPreparationGuide.roster());
            CompanyNotices.Notice.COLD_START.review(CompanyPreparationGuide.roster());
            CompanyNotices.Notice.WORKING_PASSAGE.review(CompanyPreparationGuide.roster());
        }
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        CompanyHqViewModel viewModel = CompanyHqViewModel.current(
                context::roomAboard,
                this::onBarracks,
                this::onArmory,
                this::onMechLab,
                this::onBoatDeck,
                this::onShipView,
                this::onStore,
                this::onClose,
                this::responseAction, this::plotCourse, auxiliary.row().get(), handover.row().get());
        Map<String, Object> props = viewModel.props();
        props.put("debugAction", (Runnable) () -> { if (DevConfig.DEBUG_CLIENT) context.goTo(ScreenId.DEBUG_MISSIONS); });
        props.put("debugClasses", DevConfig.DEBUG_CLIENT ? "hq-debug console-action" : "hq-debug-hidden");
        newsDesk.put(props);
        actionDesk.put(props);
        auxiliary.put(props);
        handover.put(props);
        intelTerminal.actions(actionDesk);
        intelTerminal.put(props);
        MarkupInstance candidate = markup.reloadAndBuild(
                reactor, ROOT_COMPONENT, props);
        UiDocument built;
        try {
            requireWiredElements(candidate);
            built = PreparationGuide.document(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::onClose);
            intelTerminal.bind(built, candidate);
            if (viewport != null) {
                built.layout(viewport.documentWidth(), viewport.documentHeight());
                if (intelTerminal.place()) built.layout(viewport.documentWidth(), viewport.documentHeight());
            }
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }

        UiDocument previousDocument = document;
        MarkupInstance previousInstance = markupInstance;
        document = built;
        preparationGuide = PreparationGuide.install(built, () -> CompanyPreparationGuide.withDepotAccess(
                CompanyPreparationGuide.roster(), CompanyPreparationGuide.headquarters(context), this::onClose), this::onClose);
        markupInstance = candidate;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "company-hq-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks",
                "page-nav-mech-lab", "page-nav-boats",
                "company-hq-deck", "company-hq-board-heading",
                "company-hq-main", "company-hq-finance", "company-hq-board",
                "company-hq-action-list", "company-hq-news-list")) {
            component.requireElement(id);
        }
    }

    private Runnable responseAction(CompanyClocks.Entry entry) {
        return () -> {
            if (entry.notice == null) return;
            PlayerEventPresenter.requestDeployment(entry.notice);
            onClose();
        };
    }

    private static AuxiliaryWarrantProgress.Stage auxiliaryStage() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script == null ? AuxiliaryWarrantProgress.Stage.NONE : script.state().auxiliaryWarrant().stage();
    }

    private static boolean acceptAuxiliary() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null && HegemonyAuxiliaryWarrant.accept(script.state(), CampaignClock.day());
    }

    private static boolean declineAuxiliary() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null && HegemonyAuxiliaryWarrant.decline(script.state(), CampaignClock.day());
    }

    /** Plot course as the campaign does it: the ordinary navigation destination, then close HQ. */
    private void plotCourse(SectorEntityToken destination) {
        if (destination == null) return;
        Global.getSector().getCampaignUI().layInCourseForNextStep(destination);
        onClose();
    }

    private void onArmory() {
        if (context == null) return;
        context.openCompanyArmoryFrom(ScreenId.COMPANY_HQ);
    }

    private void onBarracks() {
        if (context != null) context.goTo(ScreenId.BARRACKS);
    }

    private void onMechLab() {
        if (context != null) context.goTo(ScreenId.MECH_LAB);
    }

    private void onBoatDeck() {
        if (context != null) context.goTo(ScreenId.BOAT_DECK);
    }

    private void onShipView() {
        if (context == null) return;
        // A company with no ship is sent to choose one rather than shown an
        // empty page called the ship.
        context.goTo(context.companyDeck() == null
                ? ScreenId.SHIP_TRANSFER : ScreenId.SHIP_VIEW);
    }

    private void onStore() {
        if (context != null) context.openStore();
    }

    private void onClose() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void advance(float dt) {
        int currentHour = campaignHour();
        if (document != null && currentHour != projectedCampaignHour) {
            projectedCampaignHour = currentHour;
            installDocument();
            if (preparationGuide != null) preparationGuide.update();
            input = new StarsectorUiInputAdapter(document, viewport);
        }
        if (intelTerminal.place() && viewport != null) document.layout(viewport.documentWidth(), viewport.documentHeight());
        intelTerminal.advance(dt);
        if (markupInstance != null) markupInstance.flush();
        if (context != null) MarineOpsPageNav.refresh(markupInstance, MarineOpsPageNav.Page.HQ, context::roomAboard, CompanyNotices.current());
        if (document != null) document.advance(dt);
        if (preparationGuide != null) preparationGuide.update();
    }

    private static String loadText(String path) {
        try {
            return Global.getSettings().loadText(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read " + path, failure);
        }
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
}
