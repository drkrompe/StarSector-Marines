package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.marine.CampaignEquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.battleview.ArmoryLoadoutPreviewComposer;
import com.dillon.starsectormarines.ops.battleview.ArmoryPreviewAssets;
import com.dillon.starsectormarines.ops.battleview.ArmoryRangeTrial;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader.PreparedReload;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retained equipment workspace for one squad: its loadout and atomic issue, and
 * armour comparison. Squads are chosen in Barracks.
 */
public final class FleetArmoryScreen implements Screen {

    private static final String FIRETEAM_COMPONENT = "fleet-armory-fireteam";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/fitting/fitting-filter-pill.mlx",
            "data/ui/components/armory/fleet-armory-fireteam.mlx",
            "data/ui/components/armory/armory-loadout-card.mlx", "data/ui/components/armory/armory-squad-view.mlx", "data/ui/components/armory/armory-card-chip.mlx", "data/ui/components/armory/armory-squad-doctrine.mlx",
            "data/ui/components/armory/armory-refit-transaction.mlx",
            "data/ui/components/armory/first-refit.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);
    private final ArmoryPreviewAssets previewAssets = new ArmoryPreviewAssets();

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private MarineRoster roster;
    private FleetArmoryViewModel viewModel;
    private FirstRefitModel firstRefit;
    private UiViewport viewport;
    private UiDocument document;
    private PreparationGuide preparationGuide;
    private MarkupInstance markupInstance;
    /**
     * This screen's spec-sheet bindings. It is not a {@code MissionFlowMlxScreen},
     * so it owns the binder itself and drives it from {@link #advance}, after the
     * markup has flushed and the hover chain is the one the player is pointing at.
     */
    private SpecSheetBinder specSheets;
    /** The loadout projections the live document's entries were bound against. */
    private List<FleetArmoryViewModel.DoctrineTile> boundWeaponTiles;
    private List<FleetArmoryViewModel.DoctrineTile> boundArmorTiles;
    private StarsectorUiInputAdapter input;
    private int projectedCampaignHour = Integer.MIN_VALUE;
    private View view = View.FIRETEAMS;
    private PositionAPI position;
    private ArmoryRangeTrial rangeTrial;
    private BattleScreen rangeScreen;
    private final MutableSignal<Boolean> rangeDisabled = reactor.signal(true);
    private final MutableSignal<String> rangeHint = reactor.signal("");

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        if (rangeScreen != null) {
            rangeScreen.attach(position, null, dismissDialog);
            return;
        }
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster liveRoster = script != null ? script.roster() : null;
        if (liveRoster == null) {
            context.returnFromFleetArmoryWorkspace();
            return;
        }
        script.ensureStartingCompany();
        if (viewModel == null || roster != liveRoster) {
            closeDocument();
            roster = liveRoster;
            viewModel = new FleetArmoryViewModel(reactor, roster,
                    CampaignClock::dayFloat, new CampaignEquipmentIssueResources());
        } else {
            viewModel.refresh();
        }
        firstRefit = new FirstRefitModel(reactor, roster, new CampaignEquipmentIssueResources(), id -> {
            viewModel.selectSquad(id);
            showFireTeams();
        });
        projectedCampaignHour = campaignHour();
        String requestedSquad = context.takeRequestedArmorySquadId();
        if (requestedSquad != null && roster.squadById(requestedSquad) != null) {
            viewModel.selectSquad(requestedSquad);
            view = View.FIRETEAMS;
        } else if (FirstRefit.available(roster)) {
            view = View.FIRST_REFIT;
        } else if (viewModel.selectedSquadId() != null) {
            view = View.FIRETEAMS;
        } else {
            // Squads are chosen and raised in Barracks; with none there is no loadout to open.
            context.returnFromFleetArmoryWorkspace();
            return;
        }
        installDocument(true);
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        if (preparationGuide != null) preparationGuide.update();
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument(boolean reloadSource) {
        String componentName = switch (view) {
            case FIRETEAMS -> FIRETEAM_COMPONENT;
            case FIRST_REFIT -> "first-refit";
        };
        PreparedReload prepared = reloadSource
                ? markup.prepareReload(reactor, componentName, props()) : null;
        MarkupInstance candidate = prepared == null
                ? markup.build(reactor, componentName, props()) : prepared.instance();
        UiDocument built;
        SpecSheetBinder candidateSheets = null;
        try {
            requireWiredElements(candidate);
            if (view == View.FIRETEAMS) {
                candidate.requireElement("transaction-feedback")
                        .align(UiAlign.STRETCH, UiAlign.CENTER);
            }
            built = PreparationGuide.document(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(switch (view) {
                case FIRETEAMS -> this::returnFromWorkspace;
                case FIRST_REFIT -> this::leaveFirstRefit;
            });
            candidateSheets = new SpecSheetBinder(built, SpecSheetLayer.install(built));
            if (view == View.FIRST_REFIT) {
                bindFirstRefitSheets(candidateSheets, candidate, firstRefit);
                LoadoutInspection.bindCanvases(built, candidate, firstRefit::inspection, previewAssets);
            }
            if (view == View.FIRETEAMS) {
                ArmorySpecSheets.bindInspection(candidateSheets, candidate, viewModel.inspection()::get);
                LoadoutInspection.bindCanvases(built, candidate, viewModel.inspection()::get, previewAssets);
                // A new document holds new elements even where the projection
                // behind them is the one already bound, so the guard is cleared.
                boundWeaponTiles = null;
                boundArmorTiles = null;
                bindLoadoutCards(candidateSheets, candidate);
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
        if (view == View.FIRST_REFIT) CompanyNotices.Notice.FIRST_REFIT.review(roster);
        preparationGuide = PreparationGuide.install(built, () -> CompanyPreparationGuide.withDepotAccess(roster,
                companyLesson(), dismissDialog), this::returnFromWorkspace);
        markupInstance = candidate;
        specSheets = candidateSheets;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
        if (viewport != null) input = new StarsectorUiInputAdapter(document, viewport);
    }

    /**
     * Both pickers are laid out at once, so both lists' entries are wired.
     *
     * <p>Finding an entry's element costs a walk of the document — a span's id
     * is minted by the reconciler and is not in the instance's own index — so
     * this runs only when a picker has actually reprojected. A computed signal
     * hands back the same list until it recomputes, which is exactly the
     * question being asked.
     */
    private void bindLoadoutCards(SpecSheetBinder binder, MarkupInstance instance) {
        if (binder == null || instance == null) return;
        List<FleetArmoryViewModel.DoctrineTile> weapons = viewModel.weaponDoctrineTiles().get();
        List<FleetArmoryViewModel.DoctrineTile> armor = viewModel.armorDoctrineTiles().get();
        if (weapons == boundWeaponTiles && armor == boundArmorTiles) return;
        boundWeaponTiles = weapons;
        boundArmorTiles = armor;
        ArmorySpecSheets.bindDoctrineTiles(binder, instance, viewModel.weaponDoctrineTiles());
        ArmorySpecSheets.bindDoctrineTiles(binder, instance, viewModel.armorDoctrineTiles());
        LoadoutCardBricks.bind(document, instance, viewModel.weaponDoctrineTiles()::get);
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("firstRefitClasses", FirstRefit.available(roster) ? "console-action" : "hidden");
        props.put("openFirstRefit", (Runnable) this::showFirstRefit);
        props.put("lesson", firstRefit.row());
        props.put("companySummary", viewModel.companySummary());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("contextLabel", viewModel.issueContextLabel());
        props.put("fireTeamOverviews", viewModel.fireTeamOverviews());
        props.put("squadRows", viewModel.squadRows());
        props.put("teamRows", viewModel.teamRows());
        props.put("targetSummary", viewModel.targetSummary());
        props.put("candidateSummary", viewModel.candidateSummary());
        props.put("weaponDoctrineTiles", viewModel.weaponDoctrineTiles());
        props.put("armorDoctrineTiles", viewModel.armorDoctrineTiles());
        props.put("weaponPickerTabClasses", viewModel.weaponPickerTabClasses());
        props.put("armorPickerTabClasses", viewModel.armorPickerTabClasses());
        props.put("weaponPickerPanelClasses", viewModel.weaponPickerPanelClasses());
        props.put("armorPickerPanelClasses", viewModel.armorPickerPanelClasses());
        props.put("showWeaponPicker", viewModel.showWeaponPickerAction());
        props.put("showArmorPicker", viewModel.showArmorPickerAction());
        props.put("loadoutFilters", viewModel.loadoutFilters());
        props.put("loadoutBrowserSummary", viewModel.loadoutBrowserSummary());
        props.put("marineCards", viewModel.marineCards());
        props.put("inspection", viewModel.inspection());
        props.put("testLoadout", (Runnable) this::testLoadout);
        props.put("rangeDisabled", rangeDisabled);
        props.put("rangeHint", rangeHint);
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("issueCargoRows", viewModel.issueCargoRows());
        props.put("issueCargoClasses", viewModel.issueCargoClasses());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("back", (Runnable) this::returnFromWorkspace);
        putPageNavigation(props);
        return props;
    }

    private void putPageNavigation(Map<String, Object> props) {
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY,
                context::roomAboard,
                this::returnFromWorkspace,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> { },
                () -> context.goTo(ScreenId.MECH_LAB),
                () -> context.goTo(ScreenId.BOAT_DECK),
                context::openEngineering);
    }

    private void requireWiredElements(MarkupInstance component) {
        List<String> required = view == View.FIRST_REFIT
                ? List.of("first-refit-root", "refit-claim", "refit-issue", "loadout-billets")
                : List.of("fleet-armory-fireteam-root", "marine-ops-page-nav",
                "page-nav-return", "page-nav-hq", "page-nav-barracks",
                "page-nav-mech-lab", "page-nav-boats",
                "armory-first-refit", "fireteam-body",
                "squad-doctrine-strip", "equipment-picker-tabs", "show-weapon-picker",
                "show-armor-picker", "weapon-doctrine-list", "armor-doctrine-list",
                "loadout-browser-tools", "loadout-filter-list", "refit-transaction",
                "loadout-squad-head", "loadout-billets", "loadout-inspection", "squad-equip-row",
                "transaction-result", "transaction-feedback");
        for (String id : required) {
            component.requireElement(id);
        }
    }

    /** Each path card's seats, and the shared squad view's kit lines, as the Loadout screen binds them. */
    static void bindFirstRefitSheets(SpecSheetBinder binder, MarkupInstance markup, FirstRefitModel model) {
        for (var tile : model.pathCards()) {
            for (int seat = 0; seat < tile.seatSheets().size(); seat++) {
                SpecSheet sheet = tile.seatSheets().get(seat);
                binder.bind(markup.requireElement(ArmorySpecSheets.seatId(tile.id(), seat)), () -> sheet);
            }
        }
        ArmorySpecSheets.bindInspection(binder, markup, model::inspection);
    }

    private PreparationGuide.Lesson companyLesson() {
        var founding = CompanyPreparationGuide.armory(roster, context);
        if (founding != null) return founding;
        if (view == View.FIRST_REFIT && firstRefit.guide() != null) return firstRefit.guide();
        if (CampaignLessons.next(roster) == CampaignLessons.Step.REFIT)
            return CampaignLessons.lesson(0, "lessonRefitTitle", "lessonRefitHandoff", List.of("armory-first-refit"),
                    "lessonEquipment", this::showFirstRefit);
        return CampaignLessons.handoff(roster, context, ScreenId.FLEET_ARMORY);
    }

    private void showFirstRefit() {
        firstRefit.refresh();
        view = View.FIRST_REFIT;
        if (viewport != null) installDocument(false);
    }

    /** First refit is offered before any loadout; leaving it opens the squad it was for. */
    private void leaveFirstRefit() {
        if (firstRefit != null && !firstRefit.leavable()) return;
        if (viewModel.selectedSquadId() != null) showFireTeams();
        else returnFromWorkspace();
    }

    private void showFireTeams() {
        viewModel.refresh();
        view = View.FIRETEAMS;
        if (viewport != null) installDocument(false);
    }

    private void returnFromWorkspace() {
        context.returnFromFleetArmoryWorkspace();
    }

    @Override
    public void advance(float dt) {
        if (rangeScreen != null) {
            rangeScreen.advance(dt);
            if (rangeScreen.rangeFinished()) closeRange();
            return;
        }
        refreshRangeAvailability();
        int currentHour = campaignHour();
        if (currentHour != projectedCampaignHour) {
            projectedCampaignHour = currentHour;
            viewModel.refresh();
            if (firstRefit != null) firstRefit.refresh();
        }
        if (markupInstance != null) markupInstance.flush();
        // First Refit carries no page navigation: the reward is chosen before leaving.
        if (context != null && view != View.FIRST_REFIT) MarineOpsPageNav.refresh(markupInstance, MarineOpsPageNav.Page.ARMORY, context::roomAboard, CompanyNotices.current());
        // Loadout cards come and go with the rarity and supplies filters, so
        // the entries a reader can ask about are wired after reconciliation
        // rather than only when the document was installed.
        if (view == View.FIRETEAMS) bindLoadoutCards(specSheets, markupInstance);
        if (specSheets != null) specSheets.update();
        if (document != null) document.advance(dt);
        if (preparationGuide != null) preparationGuide.update();
    }

    private static int campaignHour() {
        return (int) Math.floor(CampaignClock.dayFloat() * 24f);
    }

    @Override
    public void render(float alphaMult) {
        if (rangeScreen != null) { rangeScreen.render(alphaMult); return; }
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (rangeScreen != null) {
            rangeScreen.processInput(events);
            if (rangeScreen.rangeFinished()) closeRange();
            return;
        }
        if (input != null) input.process(events);
    }

    @Override
    public void detach() {
        closeRange();
        // The soldier previews' textures are this screen's; reopening loads what it shows again.
        previewAssets.release();
        if (document != null) document.deactivateInput();
        if (specSheets != null) specSheets.clear();
        input = null;
    }

    private void closeDocument() {
        if (document != null) document.deactivateInput();
        if (markupInstance != null) markupInstance.close();
        if (specSheets != null) specSheets.clear();
        document = null;
        markupInstance = null;
        specSheets = null;
        boundWeaponTiles = null;
        boundArmorTiles = null;
        input = null;
    }

    private void refreshRangeAvailability() {
        if (viewModel == null || context == null) return;
        String reason = viewModel.rangeUnavailableReason();
        var deck = context.companyDeck();
        if (reason.isEmpty() && deck == null) reason = Strings.get("armoryRangeMissing");
        if (reason.isEmpty() && !deck.ready()) reason = Strings.get("armoryRangePreparing");
        if (reason.isEmpty() && ArmoryRangeTrial.availableRoom(deck) == null) reason = Strings.get("armoryRangeMissing");
        rangeDisabled.set(!reason.isEmpty());
        rangeHint.set(reason.isEmpty() ? Strings.get("armoryRangeHint") : reason);
    }

    private void testLoadout() {
        refreshRangeAvailability();
        if (rangeDisabled.peek()) return;
        var candidate = viewModel.rangeCandidate();
        var deck = context.companyDeck();
        var room = ArmoryRangeTrial.availableRoom(deck);
        if (candidate == null || room == null) return;
        document.deactivateInput();
        rangeTrial = new ArmoryRangeTrial(deck.map(), room, candidate.name(), candidate.loadout());
        rangeScreen = new BattleScreen(rangeTrial);
        try {
            rangeScreen.attach(position, null, dismissDialog);
            if (rangeScreen.rangeFinished()) closeRange();
        } catch (RuntimeException failure) {
            closeRange();
            throw failure;
        }
    }

    private void closeRange() {
        if (rangeScreen != null) rangeScreen.detach();
        rangeScreen = null;
        if (rangeTrial != null) rangeTrial.close();
        rangeTrial = null;
        if (document != null && position != null) {
            viewport = MarineOpsUiViewport.from(position);
            document.layout(viewport.documentWidth(), viewport.documentHeight());
            input = new StarsectorUiInputAdapter(document, viewport);
        }
    }

    private enum View { FIRETEAMS, FIRST_REFIT }
}
