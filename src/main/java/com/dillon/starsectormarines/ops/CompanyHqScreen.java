package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CompanyClocks;
import com.dillon.starsectormarines.ops.event.PlayerEventPresenter;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;

/** Planet-free Company HQ dashboard for readiness, standing, obligations, and news. */
public final class CompanyHqScreen implements Screen {

    private static final String ROOT_COMPONENT = "company-hq";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-hq.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private UiViewport viewport;
    private UiDocument document;
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
        }
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        CompanyHqViewModel viewModel = CompanyHqViewModel.current(
                this::onBarracks,
                this::onArmory,
                this::onMechLab,
                this::onClose,
                this::responseAction);
        MarkupInstance candidate = markup.reloadAndBuild(
                reactor, ROOT_COMPONENT, viewModel.props());
        UiDocument built;
        try {
            requireWiredElements(candidate);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::onClose);
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

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "company-hq-root", "marine-ops-page-nav", "page-nav-return",
                "page-nav-hq", "page-nav-barracks", "page-nav-armory",
                "page-nav-mech-lab",
                "company-hq-assessment",
                "company-hq-main", "company-hq-sidebar", "company-hq-force",
                "company-hq-finance", "company-hq-standing", "company-hq-board",
                "company-hq-obligation-list", "company-hq-news-list")) {
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

    private void onArmory() {
        if (context != null) context.openCompanyArmoryFrom(ScreenId.COMPANY_HQ);
    }

    private void onBarracks() {
        if (context != null) context.goTo(ScreenId.BARRACKS);
    }

    private void onMechLab() {
        if (context != null) context.goTo(ScreenId.MECH_LAB);
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
            input = new StarsectorUiInputAdapter(document, viewport);
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
        if (document != null) document.deactivateInput();
        input = null;
    }
}
