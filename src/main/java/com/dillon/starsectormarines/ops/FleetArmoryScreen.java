package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.marine.CampaignEquipmentIssueResources;
import com.dillon.starsectormarines.marine.CampaignSquadFoundingResources;
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
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
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
    private static final String DESIGNER_COMPONENT = "fleet-armory-doctrine-designer";
    private static final String ARMOR_COMPARISON_COMPONENT = "armory-armor-comparison";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/fleet-armory.mlx",
            "data/ui/components/armory/armory-squad-list.mlx",
            "data/ui/components/armory/fleet-armory-fireteam.mlx",
            "data/ui/components/armory/fleet-armory-doctrine-designer.mlx",
            "data/ui/components/armory/armory-squad-doctrine.mlx",
            "data/ui/components/armory/armory-refit-transaction.mlx",
            "data/ui/components/armory/armory-armor-comparison.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);
    private final ArmoryPreviewAssets previewAssets = new ArmoryPreviewAssets();

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private MarineRoster roster;
    private FleetArmoryViewModel viewModel;
    private EquipmentDoctrineDesignerViewModel designerViewModel;
    private UiViewport viewport;
    private UiDocument document;
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
        script.ensureStartingCompany();
        if (viewModel == null || roster != liveRoster) {
            closeDocument();
            roster = liveRoster;
            viewModel = new FleetArmoryViewModel(reactor, roster, this::showSelectedSquad,
                    CampaignClock::dayFloat, new CampaignEquipmentIssueResources(),
                    new CampaignSquadFoundingResources());
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
        String componentName = switch (view) {
            case SQUADS -> SQUAD_COMPONENT;
            case FIRETEAMS -> FIRETEAM_COMPONENT;
            case DESIGNER -> DESIGNER_COMPONENT;
            case ARMOR_COMPARISON -> ARMOR_COMPARISON_COMPONENT;
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
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(switch (view) {
                case SQUADS -> () -> context.returnFromFleetArmoryWorkspace();
                case FIRETEAMS -> this::showSquadOverview;
                case DESIGNER -> this::showFireTeams;
                case ARMOR_COMPARISON -> this::showFireTeams;
            });
            if (view == View.FIRETEAMS) {
                for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
                    int slot = index;
                    built.canvases().set(candidate.requireElement("marine-preview:" + index),
                            new ArmoryMarinePreviewCanvas(
                                    () -> viewModel.viewerBilletAt(slot), previewAssets,
                                    () -> previewAnimationSeconds + slot * 0.31d));
                }
            } else if (view == View.DESIGNER) {
                for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
                    int slot = index;
                    built.canvases().set(candidate.requireElement("designer-marine-preview:" + index),
                            new ArmoryMarinePreviewCanvas(
                                    () -> designerViewModel.viewerBilletAt(slot), previewAssets,
                                    () -> previewAnimationSeconds + slot * 0.31d));
                }
            }
            candidateSheets = new SpecSheetBinder(built, SpecSheetLayer.install(built));
            if (view == View.FIRETEAMS) {
                ArmorySpecSheets.bindMarineCards(
                        candidateSheets, candidate, viewModel.marineCards());
                // A new document holds new elements even where the projection
                // behind them is the one already bound, so the guard is cleared.
                boundWeaponTiles = null;
                boundArmorTiles = null;
                bindLoadoutCards(candidateSheets, candidate);
            } else if (view == View.DESIGNER) {
                ArmorySpecSheets.bindBilletCards(
                        candidateSheets, candidate, designerViewModel.billets());
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
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("companySummary", viewModel.companySummary());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("squadCards", viewModel.squadCards());
        props.put("squadGalleryCards", viewModel.squadGalleryCards());
        props.put("foundingCargoRows", viewModel.foundingCargoRows());
        props.put("foundingDisabled", viewModel.foundingDisabled());
        props.put("foundingFeedbackText", viewModel.foundingFeedbackText());
        props.put("foundingFeedbackClasses", viewModel.foundingFeedbackClasses());
        props.put("foundSquad", viewModel.foundSquadAction());
        props.put("fireTeamOverviews", viewModel.fireTeamOverviews());
        props.put("squadRows", viewModel.squadRows());
        props.put("teamRows", viewModel.teamRows());
        props.put("targetSummary", viewModel.targetSummary());
        props.put("candidateSummary", viewModel.candidateSummary());
        props.put("weaponDoctrineTiles", viewModel.weaponDoctrineTiles());
        props.put("armorDoctrineTiles", viewModel.armorDoctrineTiles());
        props.put("weaponDoctrineSummary", viewModel.weaponDoctrineSummary());
        props.put("armorDoctrineSummary", viewModel.armorDoctrineSummary());
        props.put("weaponPickerTabClasses", viewModel.weaponPickerTabClasses());
        props.put("armorPickerTabClasses", viewModel.armorPickerTabClasses());
        props.put("weaponPickerPanelClasses", viewModel.weaponPickerPanelClasses());
        props.put("armorPickerPanelClasses", viewModel.armorPickerPanelClasses());
        props.put("showWeaponPicker", viewModel.showWeaponPickerAction());
        props.put("showArmorPicker", viewModel.showArmorPickerAction());
        props.put("loadoutFilters", viewModel.loadoutFilters());
        props.put("issuableFilter", viewModel.issuableFilter());
        props.put("loadoutBrowserSummary", viewModel.loadoutBrowserSummary());
        props.put("showArmorComparison", (Runnable) this::showArmorComparison);
        props.put("armorComparisonSummary", viewModel.armorComparisonSummary());
        props.put("armorComparisonCards", viewModel.armorComparisonCards());
        props.put("marineCards", viewModel.marineCards());
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("issueCargoRows", viewModel.issueCargoRows());
        props.put("issueCargoClasses", viewModel.issueCargoClasses());
        props.put("applyDisabled", viewModel.applyDisabled());
        props.put("applyLabel", viewModel.applyLabel());
        props.put("apply", viewModel.applyAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("back", (Runnable) () -> context.returnFromFleetArmoryWorkspace());
        props.put("backToSquads", (Runnable) this::showSquadOverview);
        props.put("backToFireTeams", (Runnable) this::showFireTeams);
        if (designerViewModel != null) {
            props.put("squadName", designerViewModel.squadName());
            props.put("designerHeading", designerViewModel.heading());
            props.put("designerSubheading", designerViewModel.subheading());
            props.put("draftName", designerViewModel.draftName());
            props.put("editName", designerViewModel.editName());
            props.put("definitions", designerViewModel.definitions());
            props.put("teamTabs", designerViewModel.teamTabs());
            props.put("billets", designerViewModel.billets());
            props.put("feedback", designerViewModel.feedback());
            props.put("newDraft", designerViewModel.newDraft());
            props.put("cloneSelected", designerViewModel.cloneSelected());
            props.put("saveAsNew", designerViewModel.saveAsNew());
            props.put("rename", designerViewModel.rename());
            props.put("renameDisabled", designerViewModel.renameDisabled());
            props.put("delete", designerViewModel.delete());
            props.put("deleteDisabled", designerViewModel.deleteDisabled());
        }
        putPageNavigation(props);
        return props;
    }

    private void putPageNavigation(Map<String, Object> props) {
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY,
                context::roomAboard,
                dismissDialog,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> { },
                () -> context.goTo(ScreenId.MECH_LAB),
                () -> context.goTo(ScreenId.BOAT_DECK));
    }

    private void requireWiredElements(MarkupInstance component) {
        List<String> required = view == View.FIRETEAMS
                ? List.of("fleet-armory-fireteam-root", "marine-ops-page-nav",
                "page-nav-return", "page-nav-hq", "page-nav-barracks",
                "page-nav-armory", "page-nav-mech-lab", "page-nav-boats",
                "fireteam-breadcrumb", "back-to-squads", "fireteam-body",
                "squad-doctrine-strip", "equipment-picker-tabs", "show-weapon-picker",
                "show-armor-picker", "weapon-doctrine-list", "armor-doctrine-list",
                "loadout-browser-tools", "loadout-filter-list", "refit-transaction",
                "viewer-context", "fireteam-tabs", "target-summary",
                "candidate-summary", "marine-card-grid", "squad-equip-row",
                "transaction-result", "apply-squad-equipment", "transaction-feedback",
                "marine-preview:0", "marine-preview:1",
                "marine-preview:2", "marine-preview:3")
                : view == View.DESIGNER
                ? List.of("equipment-designer-root", "marine-ops-page-nav",
                "page-nav-return", "page-nav-hq", "page-nav-barracks",
                "page-nav-armory", "page-nav-mech-lab", "page-nav-boats",
                "designer-breadcrumb", "back-to-fireteams", "designer-mode-row",
                "designer-definition-library", "designer-definition-list",
                "designer-editor", "designer-name-input", "designer-team-tabs",
                "designer-billet-grid", "designer-feedback",
                "designer-marine-preview:0", "designer-marine-preview:1",
                "designer-marine-preview:2", "designer-marine-preview:3")
                : view == View.ARMOR_COMPARISON
                ? List.of("armor-comparison-root", "marine-ops-page-nav",
                "page-nav-return", "page-nav-hq", "page-nav-barracks",
                "page-nav-armory", "page-nav-mech-lab", "page-nav-boats",
                "comparison-breadcrumb", "back-to-fireteams",
                "comparison-intro", "comparison-list")
                : List.of("fleet-armory-root", "marine-ops-page-nav",
                "page-nav-return", "page-nav-hq", "page-nav-barracks",
                "page-nav-armory", "page-nav-mech-lab", "page-nav-boats",
                "squad-breadcrumb", "squad-overview-intro",
                "squad-founder-costs", "found-squad", "squad-founder-feedback",
                "squad-card-list");
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

    private void showDesigner() {
        designerViewModel = new EquipmentDoctrineDesignerViewModel(
                reactor, roster, viewModel.selectedSquadId(),
                viewModel.selectedWeaponDoctrineId());
        view = View.DESIGNER;
        if (viewport != null) installDocument(false);
    }

    private void showFireTeams() {
        viewModel.refresh();
        view = View.FIRETEAMS;
        previewAnimationSeconds = 0f;
        if (viewport != null) installDocument(false);
    }

    private void showArmorComparison() {
        view = View.ARMOR_COMPARISON;
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
        // Loadout cards come and go with the rarity and supplies filters, so
        // the entries a reader can ask about are wired after reconciliation
        // rather than only when the document was installed.
        if (view == View.FIRETEAMS) bindLoadoutCards(specSheets, markupInstance);
        if (specSheets != null) specSheets.update();
        if ((view == View.FIRETEAMS || view == View.DESIGNER)
                && Float.isFinite(dt) && dt > 0f) {
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

    private enum View { SQUADS, FIRETEAMS, DESIGNER, ARMOR_COMPARISON }
}
