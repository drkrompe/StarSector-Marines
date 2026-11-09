package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.VoiceOver;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.nexerelin.InvasionForkSetting;
import com.dillon.starsectormarines.campaign.nexerelin.Nexerelin;
import com.dillon.starsectormarines.campaign.raid.RaidForkSetting;
import com.dillon.starsectormarines.campaign.ruins.RuinsForkSetting;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.MarineRuinsCMD;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The company's settings, each saved with the game. */
public final class SettingsScreen implements Screen {

    static final String ROOT_COMPONENT = "company-settings";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-settings.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private SettingsViewModel viewModel;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        if (viewModel == null) viewModel = new SettingsViewModel(reactor, settings());
        else viewModel.refresh();
        if (document == null) installDocument();
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    /**
     * The voice-over, and whether the raid, invasion and ruins forks ask. Each fork's answer is made every time it
     * is offered; turning one off hands that action to the game (or Nexerelin) untouched. The invasion key shows only
     * with Nexerelin, and the ruins key only where this build offers the ruins fork.
     */
    private static List<SettingsViewModel.Setting> settings() {
        return List.of(
                new SettingsViewModel.Setting("voiceOver", VoiceOver::enabled, VoiceOver::setEnabled),
                new SettingsViewModel.Setting("raidFork",
                        () -> RaidForkSetting.asks(state()), on -> RaidForkSetting.setAsks(state(), on)),
                new SettingsViewModel.Setting("invasionFork",
                        () -> InvasionForkSetting.asks(state()), on -> InvasionForkSetting.setAsks(state(), on),
                        Nexerelin::isEnabled),
                new SettingsViewModel.Setting("ruinsFork",
                        () -> RuinsForkSetting.asks(state()), on -> RuinsForkSetting.setAsks(state(), on),
                        MarineRuinsCMD::offered));
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, ROOT_COMPONENT, props());
        try {
            candidate.requireElement("settings-voice-over-key");
            UiDocument built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::close);
            document = built;
            markupInstance = candidate;
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        viewModel.put(props);
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SETTINGS,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.SETTINGS),
                () -> context.goTo(ScreenId.MECH_LAB),
                () -> context.goTo(ScreenId.BOAT_DECK), context::openEngineering, context::openStore);
        MarineOpsPageNav.routeShared(props, context);
        return props;
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void advance(float dt) {
        if (markupInstance != null) markupInstance.flush();
        if (context != null) MarineOpsPageNav.refresh(markupInstance, MarineOpsPageNav.Page.SETTINGS, context::roomAboard, CompanyNotices.current());
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
