package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;

import com.dillon.starsectormarines.marine.OfficerSpecialty;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;

import com.dillon.starsectormarines.marine.BarracksFlowCompany;
import com.dillon.starsectormarines.marine.BarracksEquipmentIssue;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;

import org.json.JSONObject;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.json.JSONArray;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.campaign.CommodityPresentation;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.EquipmentTestFixtures;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.SquadFoundingWorkshop;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.dillon.starsectormarines.battle.mech.MechLanceOrder;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ui.panel.TaskForceStatusPanel;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.ops.battleview.ArmoryMarinePreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.VanillaHullSilhouettes;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipsBoats;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.BarracksCanvas;
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas;
import com.dillon.starsectormarines.ops.battleview.ShipViewCanvas;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.DeckPlanCanvas;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.MechLabCameraController;
import com.dillon.starsectormarines.ops.battleview.MechChassisPreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.MechEquipmentGridCanvas;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
import com.dillon.starsectormarines.ops.battleview.MechWeaponDragCanvas;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Authored retained-view evidence rendered without a Starsector process. */
public final class UiSnapshotSuite implements SnapshotSuite {

    private static final int FULL_SCREEN_WIDTH = 1920;
    private static final int FULL_SCREEN_HEIGHT = 1080;

    private static final List<String> COMPANY_HQ_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-hq.mlx");
    private static final List<String> MISSION_SELECT_COMPONENTS = List.of(
            "data/ui/components/missions/mission-select.mlx");
    private static final List<String> MISSION_BRIEFING_COMPONENTS = List.of(
            "data/ui/components/missions/mission-briefing.mlx");
    private static final List<String> SQUAD_DEPLOYMENT_COMPONENTS = List.of(
            "data/ui/components/missions/squad-deployment.mlx");
    private static final List<String> STATIONING_COMPONENTS = List.of(
            "data/ui/components/missions/stationing-screen.mlx");
    private static final List<String> POLITY_DOCTRINE_COMPONENTS = List.of(
            "data/ui/components/missions/polity-doctrine-screen.mlx");
    private static final List<String> MISSION_RESULTS_COMPONENTS = List.of(
            "data/ui/components/missions/mission-results.mlx");
    private static final List<String> MISSION_LOOT_COMPONENTS = List.of(
            "data/ui/components/missions/mission-loot.mlx");
    private static final List<String> BARRACKS_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/shipboard-barracks.mlx");
    /**
     * The fleet the transfer evidence is drawn from: a plausible mid-campaign
     * mix, best home first, with hulls that would cost the company something
     * deliberately included.
     */
    private static final String[] TRANSFER_FLEET = {
            "valkyrie", "legion", "starliner", "eagle", "atlas", "wolf" };

    /**
     * Where the transfer evidence points at the plan, in canvas pixels. Chosen
     * to land on the mech bay, which is the compartment the whole screen is
     * usually being read for.
     */
    private static final float[] HOVERED_CELL = { 436f, 273f };

    /** A company with a home, and one whose ship did not come back. */
    private static final CompanyShipDesignation.Home QUARTERED =
            new CompanyShipDesignation.Home("home", null, false, 0);
    private static final CompanyShipDesignation.Home DISPLACED =
            new CompanyShipDesignation.Home(null, "SABRE", true, 41);

    /**
     * The company being moved, and what it has to pay with. A mid-campaign
     * outfit with money in hand, and the same outfit after a bad season — a
     * refit it cannot afford is a state the screen has to say out loud, and a
     * screenshot is the only way to see whether it does.
     */
    private static final CompanyMeans SOLVENT = CompanyMeans.of(84, 3, 240_000);
    private static final CompanyMeans BROKE = CompanyMeans.of(84, 3, 18_000);

    /** The hull the ship view is drawn on: a real transport with real art. */
    private static final String SHIP_VIEW_HULL = "conquest";

    private static final List<String> SHIP_VIEW_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/ship-view.mlx");
    private static final List<String> SHIP_TRANSFER_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/ship-transfer.mlx");
    private static final List<String> OVERVIEW_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/fleet-armory-overview.mlx",
            "data/ui/components/armory/armory-company-list.mlx");
    private static final List<String> WORKSPACE_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/fleet-armory.mlx",
            "data/ui/components/armory/armory-squad-list.mlx",
            "data/ui/components/armory/fleet-armory-fireteam.mlx",
            "data/ui/components/armory/fleet-armory-doctrine-designer.mlx",
            "data/ui/components/armory/armory-weapon-tech-card.mlx", "data/ui/components/armory/armory-squad-doctrine.mlx",
            "data/ui/components/armory/armory-refit-transaction.mlx",
            "data/ui/components/armory/armory-armor-comparison.mlx");
    private static final List<String> MECH_LAB_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/mech-lab/mech-lab.mlx");
    private static final List<String> BOAT_DECK_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/boat-deck/boat-deck.mlx");
    private static final List<String> BATTLE_HUD_COMPONENTS =
            List.of(BattleHudOverlay.COMPONENT_PATH,
                    BattleSquadOverlay.COMPONENT_PATH,
                    BattleMechOverlay.COMPONENT_PATH,
                    BattlePowerOverlay.COMPONENT_PATH,
                    BattleRetreatOverlay.COMPONENT_PATH);

    @Override
    public String id() {
        return "ui";
    }

    @Override
    public String label() {
        return "Retained UI";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessBattleSceneRenderer battleScenes =
                new HeadlessBattleSceneRenderer(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(battleScenes,
                context.modRoot(), context.starsectorCore());
        // Live departures emit GL-owned engine glow. Keep their real hull and
        // simulation pose; this raster preview omits only that decorative pass.
        HeadlessUiRenderer boatRenderer = new HeadlessUiRenderer(
                new HeadlessBattleSceneRenderer(context.modRoot(), true),
                context.modRoot(), context.starsectorCore());
        return List.of(
                new SnapshotArtifact("mission-squad-deployment-wide.png",
                        renderMissionFlow(context, renderer, SQUAD_DEPLOYMENT_COMPONENTS,
                                SquadDeploymentScreen.ROOT_COMPONENT,
                                SquadDeploymentScreen.previewProps())),
                new SnapshotArtifact("mission-stationing-offer-wide.png",
                        renderMissionFlow(context, renderer, STATIONING_COMPONENTS,
                                StationingScreen.ROOT_COMPONENT,
                                StationingScreen.previewProps(false, false))),
                new SnapshotArtifact("mission-stationing-response-wide.png",
                        renderMissionFlow(context, renderer, STATIONING_COMPONENTS,
                                StationingScreen.ROOT_COMPONENT,
                                StationingScreen.previewProps(true, true))),
                new SnapshotArtifact("polity-ground-doctrine-wide.png",
                        renderMissionFlow(context, renderer, POLITY_DOCTRINE_COMPONENTS,
                                PolityDoctrineScreen.ROOT_COMPONENT,
                                PolityDoctrineScreen.previewProps(
                                        ModStrings.fromDisk(context.modRoot())))),
                new SnapshotArtifact("polity-ground-doctrine-spec-sheet-wide.png",
                        renderPolityDoctrineSpecSheet(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("mission-results-wide.png",
                        renderMissionFlow(context, renderer, MISSION_RESULTS_COMPONENTS,
                                ResultsScreen.ROOT_COMPONENT,
                                ResultsScreen.previewProps(true, true))),
                new SnapshotArtifact("mission-loot-wide.png",
                        renderMissionFlow(context, renderer, MISSION_LOOT_COMPONENTS,
                                LootScreen.ROOT_COMPONENT, LootScreen.previewProps())),
                new SnapshotArtifact("company-hq-bridge-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f)),
                new SnapshotArtifact("company-hq-rumor-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.preview(), CompanyHqNewsDesk.Channel.RUMOR)),
                // The one state the shell can be in that nothing else draws:
                // her deck is still being laid out, so the rooms aboard read as
                // pending rather than as places the hull does not have.
                new SnapshotArtifact("company-hq-getting-ready-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.gettingReadyPreview())),
                new SnapshotArtifact("barracks-wide.png",
                        renderBarracks(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("ship-view-wide.png",
                        renderShipView(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("ship-transfer-wide.png",
                        renderShipTransfer(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 0, HOVERED_CELL,
                                QUARTERED, SOLVENT)),
                new SnapshotArtifact("ship-transfer-costly-wide.png",
                        renderShipTransfer(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 2, null,
                                QUARTERED, SOLVENT)),
                new SnapshotArtifact("ship-transfer-unaffordable-wide.png",
                        renderShipTransfer(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 2, null,
                                QUARTERED, BROKE)),
                new SnapshotArtifact("ship-transfer-founding-wide.png",
                        renderShipTransfer(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 2, null,
                                CompanyShipDesignation.Home.NONE, BROKE)),
                new SnapshotArtifact("ship-transfer-displaced-wide.png",
                        renderShipTransfer(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1, null,
                                DISPLACED, BROKE)),
                new SnapshotArtifact("fleet-armory-overview-wide.png",
                        renderFleetArmoryOverview(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("fleet-armory-squads-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                false, false)),
                new SnapshotArtifact("fleet-armory-organization-wide.png",
                        renderFleetArmoryOrganization(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 6, false, false)),
                new SnapshotArtifact("fleet-armory-organization-capacity-wide.png",
                        renderFleetArmoryOrganization(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 6, true, false)),
                new SnapshotArtifact("fleet-armory-organization-unassigned-wide.png",
                        renderFleetArmoryOrganization(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 6, false, true)),
                new SnapshotArtifact("fleet-armory-organization-large-company.png",
                        renderFleetArmoryOrganization(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 24, false, false)),
                new SnapshotArtifact("fleet-armory-founding-wide.png",
                        renderEmptyFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("fleet-armory-workspace-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, false)),
                new SnapshotArtifact("fleet-armory-equipment-tooltip-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, false, false, true)),
                new SnapshotArtifact("fleet-armory-loadout-spec-sheet-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, false, false, false, true)),
                new SnapshotArtifact("fleet-armory-equipment-preview-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, true)),
                new SnapshotArtifact("fleet-armory-armor-collection-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, false, true)),
                new SnapshotArtifact("fleet-armory-equipment-designer-wide.png",
                        renderEquipmentDesigner(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("fleet-armory-equipment-designer-spec-sheet-wide.png",
                        renderEquipmentDesigner(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("fleet-armory-armor-comparison-wide.png",
                        renderArmorComparison(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("fleet-armory-armor-comparison-battlesuits-wide.png",
                        renderArmorComparison(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("mech-lab-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f)),
                new SnapshotArtifact("mech-lab-asset-picker-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, true)),
                new SnapshotArtifact("mech-lab-hound-empty-socket-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, false, true)),
                new SnapshotArtifact("mech-lab-chassis-fabrication-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                false, false, true)),
                // With a shoulder mount chosen, because the catalog lists what
                // could go in the chosen socket and an unselected lab lists
                // nothing to describe.
                new SnapshotArtifact("mech-lab-spec-sheet-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                false, true, false, true)),
                new SnapshotArtifact("boat-deck-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false)),
                new SnapshotArtifact("boat-deck-departure-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, false, true)),
                new SnapshotArtifact("boat-deck-fitting-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("boat-deck-vacant-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, true)),
                new SnapshotArtifact("battle-hud-task-force-wide.png",
                        renderBattleHudTaskForce(
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-hud-selected-squad-wide.png",
                        renderBattleSquadOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false)),
                new SnapshotArtifact("battle-hud-selected-squad-hover-wide.png",
                        renderBattleSquadOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("battle-hud-selected-mech-wide.png",
                        renderBattleMechOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false)),
                new SnapshotArtifact("battle-hud-selected-mech-defend-area-wide.png",
                        renderBattleMechOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("battle-hud-command-overlay-wide.png",
                        renderBattleHudCommandOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-world-capture-targeting-wide.png",
                        renderBattleWorldInteraction(
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, true)),
                new SnapshotArtifact("battle-world-sabotage-blocked-wide.png",
                        renderBattleWorldInteraction(
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false)),
                new SnapshotArtifact("battle-hud-powers-targeting-wide.png",
                        renderBattleHudPowerOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-hud-retreat-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false)),
                new SnapshotArtifact("battle-hud-retreat-confirm-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)));
    }

    /** Mission presentation is reviewed at the shipping baseline: 1920 x 1080, 100%. */
    static List<SnapshotArtifact> renderMissionPreviews(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                context.modRoot(), context.starsectorCore());
        TileRegistryTestInstaller.install(context.projectRoot());
        WaylineNarration narration = WaylineNarration.parse(new JSONObject(Files.readString(
                context.modRoot().resolve(WaylineNarration.PATH))));
        Map<String, Object> narratedBriefing = BriefingViewModel.campaignPreviewProps(false);
        BriefingViewModel.putNarration(narratedBriefing, narration.find("story_opening_relief", "briefing"), true);
        narratedBriefing.put("missionTitle", "Hold Until Relieved");
        narratedBriefing.put("missionKicker", "ASSAULT  ·  LOW RISK  ·  First Contract");
        BriefingViewModel.putForceComparison(narratedBriefing, OperationTier.FIRST_CONTRACT, RiskLevel.LOW, 12, 12);
        BriefingViewModel.putReward(narratedBriefing, Mission.builder().payout(6000).build());
        BriefingViewModel.putFactionIdentity(narratedBriefing, "employer", "Independent", OpeningBroker.CREST);

        narratedBriefing.put("missionFlavor", narration.find("story_opening_relief", "briefing").text());
        BriefingSurvey conquestSurvey = productionSurvey(context, "conquest-full-strength-west-v3.json", 1008);
        BriefingSurvey sabotageSurvey = productionSurvey(context, "sabotage-site-groups-v2.json", 12);
        BriefingSurvey understrengthSurvey = productionSurvey(context, "sabotage-site-groups-v2.json", 6);
        Map<String, Object> stoppedBriefing = new HashMap<>(narratedBriefing);
        BriefingViewModel.putNarration(stoppedBriefing, narration.find("story_opening_relief", "briefing"), false);
        return List.of(
                new SnapshotArtifact("mission-briefing-narration-stopped-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                stoppedBriefing, 1f, false)),
                new SnapshotArtifact("mission-briefing-narration-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                narratedBriefing, 1f, false)),
                new SnapshotArtifact("battle-story-subtitle-wide.png",
                        renderStorySubtitle(context, renderer, narration.find("story_opening_relief",
                                "opening.relief.checkpoint_reached").text())),
                new SnapshotArtifact("battle-company-subtitle-wide.png",
                        renderVoiceSubtitle(context, renderer,
                                "Breathe. Find cover and get yourselves together. I need you alive.", true)),
                new SnapshotArtifact("battle-hud-command-groups-wide.png",
                        renderBattleHudCommandOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("mission-catalog-debug-wide.png",
                        renderMissionSelect(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("mission-briefing-survey-loading-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                loadingBriefingProps(), 1f, false)),
                new SnapshotArtifact("mission-briefing-campaign-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                surveyProps(BriefingViewModel.campaignPreviewProps(true), conquestSurvey), 1f, false)),
                new SnapshotArtifact("mission-briefing-acting-nco-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                surveyProps(BriefingViewModel.actingNcoPreviewProps(), sabotageSurvey), 1f, false)),
                new SnapshotArtifact("mission-briefing-campaign-understrength-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                surveyProps(understrengthBriefingProps(), understrengthSurvey), 1f, false)),
                new SnapshotArtifact("mission-briefing-first-contract-wide.png",
                        renderMissionBriefing(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, surveyProps(BriefingScreen.previewProps(false), sabotageSurvey), 1f, false)),
                new SnapshotArtifact("mission-briefing-conquest-wide.png",
                        renderMissionBriefing(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, surveyProps(BriefingScreen.previewProps(true), conquestSurvey), 1f, false)),
                new SnapshotArtifact("mission-briefing-conquest-debug-expanded-wide.png",
                        renderMissionBriefing(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                surveyProps(BriefingScreen.previewProps(true, true), conquestSurvey), 1f, false)),
                new SnapshotArtifact("mission-briefing-controls-blocked-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                missionControlStateProps(), 1f, false)),
                new SnapshotArtifact("mission-briefing-force-hint-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                surveyProps(understrengthBriefingProps(), understrengthSurvey), 1f, false, "mission-force-comparison")),
                new SnapshotArtifact("mission-briefing-power-hint-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                BriefingScreen.previewProps(true, true), 1f, false, "power-barrage")),
                new SnapshotArtifact("mission-briefing-air-hint-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                BriefingScreen.previewProps(true, true), 1f, false, "air-dagger")));
    }

    /**
     * Full battlefield evidence for the production world-marker presentation.
     * Geometry is intentionally painted without a Starsector process, while
     * labels, tones, opacity, state emphasis, and footprint sizing come from
     * {@link BattlefieldMarkerPresentation}, the same model live GL consumes.
     */
    private static BufferedImage renderBattleWorldInteraction(
            int width, int height, boolean sabotage, boolean validTarget) {
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintBattleContrastField(graphics, width, height);

        if (sabotage) {
            paintWorldObjective(graphics, 420f, 330f,
                    BattlefieldMarkerPresentation.sabotage(
                            "SAB-01", false, false, 0f));
            paintWorldObjective(graphics, 735f, 695f,
                    BattlefieldMarkerPresentation.sabotage(
                            "SAB-02", false, true, 0.62f));
            paintWorldObjective(graphics, 1500f, 315f,
                    BattlefieldMarkerPresentation.sabotage(
                            "SAB-03", true, false, 1f));
        } else {
            paintWorldObjective(graphics, 420f, 330f,
                    BattlefieldMarkerPresentation.capture(
                            TacticalNode.Kind.ARMORY, 1,
                            CompoundService.CompoundState.DEFENDER_HELD, 0f));
            paintWorldObjective(graphics, 735f, 695f,
                    BattlefieldMarkerPresentation.capture(
                            TacticalNode.Kind.BARRACKS, 1,
                            CompoundService.CompoundState.CONTESTED, 0.62f));
            paintWorldObjective(graphics, 1500f, 315f,
                    BattlefieldMarkerPresentation.capture(
                            TacticalNode.Kind.COMMAND_POST, 1,
                            CompoundService.CompoundState.MARINE_HELD, 0f));
        }

        BattlefieldMarkerPresentation.TargetMarker target =
                BattlefieldMarkerPresentation.target(
                        validTarget ? "Orbital Barrage" : "Marine Drop",
                        validTarget, validTarget ? 4f : 1.5f);
        paintWorldTarget(graphics, 1180f, 655f, 24f, target);
        graphics.dispose();
        return image;
    }

    private static void paintWorldObjective(
            Graphics2D graphics, float centerX, float centerY,
            BattlefieldMarkerPresentation.ObjectiveMarker marker) {
        float radius = BattlefieldMarkerPresentation.objectiveRadius(24f);
        Color tone = withAlpha(marker.tone(), marker.opacity());
        graphics.setColor(new Color(8, 16, 22, 190));
        graphics.fill(new Ellipse2D.Float(centerX - radius + 2f,
                centerY - radius + 2f, radius * 2f - 4f, radius * 2f - 4f));
        graphics.setStroke(new BasicStroke(2f));
        graphics.setColor(tone);
        graphics.draw(new Ellipse2D.Float(centerX - radius, centerY - radius,
                radius * 2f, radius * 2f));
        float inner = radius + 2f;
        float outer = inner + 4f;
        graphics.drawLine(Math.round(centerX - outer), Math.round(centerY),
                Math.round(centerX - inner), Math.round(centerY));
        graphics.drawLine(Math.round(centerX + inner), Math.round(centerY),
                Math.round(centerX + outer), Math.round(centerY));
        graphics.drawLine(Math.round(centerX), Math.round(centerY - outer),
                Math.round(centerX), Math.round(centerY - inner));
        graphics.drawLine(Math.round(centerX), Math.round(centerY + inner),
                Math.round(centerX), Math.round(centerY + outer));
        if (marker.emphasized() && marker.progress() > 0f) {
            graphics.setStroke(new BasicStroke(3f));
            graphics.setColor(BattlefieldMarkerPresentation.PROGRESS);
            float arcRadius = radius - 5.5f;
            graphics.draw(new Arc2D.Float(centerX - arcRadius,
                    centerY - arcRadius, arcRadius * 2f, arcRadius * 2f,
                    90f, -360f * marker.progress(), Arc2D.OPEN));
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        graphics.setColor(BattlefieldMarkerPresentation.LABEL);
        int codeWidth = graphics.getFontMetrics().stringWidth(marker.code());
        graphics.drawString(marker.code(), centerX - codeWidth * 0.5f,
                centerY + 4f);
        if (marker.emphasized()) {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            graphics.setColor(marker.tone());
            int statusWidth = graphics.getFontMetrics().stringWidth(marker.status());
            graphics.drawString(marker.status(), centerX - statusWidth * 0.5f,
                    centerY + radius + 15f);
        }
    }

    private static void paintWorldTarget(
            Graphics2D graphics, float centerX, float centerY, float cellPx,
            BattlefieldMarkerPresentation.TargetMarker marker) {
        float radius = BattlefieldMarkerPresentation.targetRadius(
                cellPx, marker.radiusCells());
        graphics.setColor(withAlpha(marker.tone(), 0.10f));
        graphics.fill(new Ellipse2D.Float(centerX - radius, centerY - radius,
                radius * 2f, radius * 2f));
        graphics.setStroke(new BasicStroke(marker.valid() ? 2f : 2.5f));
        graphics.setColor(withAlpha(marker.tone(), 0.92f));
        graphics.draw(new Ellipse2D.Float(centerX - radius, centerY - radius,
                radius * 2f, radius * 2f));
        float half = cellPx * 0.5f;
        float corner = 6f;
        graphics.drawLine(Math.round(centerX - half), Math.round(centerY - half),
                Math.round(centerX - half + corner), Math.round(centerY - half));
        graphics.drawLine(Math.round(centerX - half), Math.round(centerY - half),
                Math.round(centerX - half), Math.round(centerY - half + corner));
        graphics.drawLine(Math.round(centerX + half), Math.round(centerY - half),
                Math.round(centerX + half - corner), Math.round(centerY - half));
        graphics.drawLine(Math.round(centerX + half), Math.round(centerY - half),
                Math.round(centerX + half), Math.round(centerY - half + corner));
        graphics.drawLine(Math.round(centerX - half), Math.round(centerY + half),
                Math.round(centerX - half + corner), Math.round(centerY + half));
        graphics.drawLine(Math.round(centerX - half), Math.round(centerY + half),
                Math.round(centerX - half), Math.round(centerY + half - corner));
        graphics.drawLine(Math.round(centerX + half), Math.round(centerY + half),
                Math.round(centerX + half - corner), Math.round(centerY + half));
        graphics.drawLine(Math.round(centerX + half), Math.round(centerY + half),
                Math.round(centerX + half), Math.round(centerY + half - corner));

        String label = marker.label() + "  //  " + marker.status();
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        int textWidth = graphics.getFontMetrics().stringWidth(label);
        int plateX = Math.round(centerX - (textWidth + 18f) * 0.5f);
        int plateY = Math.round(centerY - radius - 36f);
        graphics.setColor(BattlefieldMarkerPresentation.PLATE);
        graphics.fillRect(plateX, plateY, textWidth + 18, 22);
        graphics.setColor(marker.tone());
        graphics.fillRect(plateX, plateY, 3, 22);
        graphics.setColor(BattlefieldMarkerPresentation.LABEL);
        graphics.drawString(label, plateX + 10f, plateY + 15f);
    }

    private static Color withAlpha(Color color, float opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(255f * Math.max(0f, Math.min(1f, opacity))));
    }

    /** Full-screen evidence for the compact selected-squad roster and hover loadout. */
    private static BufferedImage renderBattleSquadOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean hovered) throws Exception {
        BufferedImage image = renderBattleBackdrop(width, height);
        Reactor reactor = new Reactor();
        BattleSquadOverlayModel model = new BattleSquadOverlayModel(reactor, () -> { });
        model.updateProjected(new BattleSquadOverlayModel.SquadState(
                "Squad 20", 12, 12, 0.72f, previewSquadMembers()));
        if (hovered) model.hover("battle-squad-member-0-0");

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BATTLE_HUD_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleSquadOverlay.COMPONENT, model.props())) {
            BattleSquadOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            BufferedImage overlay = renderer.render(document,
                    Math.round(BattleSquadOverlay.DOCUMENT_WIDTH),
                    Math.round(BattleSquadOverlay.DOCUMENT_HEIGHT));
            Graphics2D graphics = image.createGraphics();
            int bottom = Math.round(BattleLayout.PAD + BattleLayout.BACK_H
                    + BattleLayout.CONTROLS_GAP);
            graphics.drawImage(overlay, 12, height - bottom - overlay.getHeight(), null);
            graphics.dispose();
            return image;
        }
    }

    private static List<BattleSquadOverlayModel.MemberState> previewSquadMembers() {
        String[] names = {
                "Arden Vale", "Mira Chen", "Pavel Ilyin", "Nia Okafor",
                "Jon Bell", "Sana Ruiz", "Ivo Marku", "Tess Ward",
                "Leah Moss", "Dax Holt", "Rei Sato", "Omar Venn" };
        String[] primary = { "RIF-III", "FLD-II", "SAW-II", "DMR-II" };
        List<BattleSquadOverlayModel.MemberState> members = new ArrayList<>();
        for (int team = 0; team < 3; team++) {
            for (int slot = 0; slot < 4; slot++) {
                int index = team * 4 + slot;
                boolean leader = index == 0;
                boolean specialist = slot == 2;
                String specialName = specialist ? switch (team) {
                    case 0 -> "Smoke Grenade";
                    case 1 -> "Rocket Launcher";
                    default -> "Point Defence Emplacement";
                } : null;
                String specialAbbrev = specialist ? switch (team) {
                    case 0 -> "SMK";
                    case 1 -> "RKT";
                    default -> "PDE";
                } : null;
                members.add(new BattleSquadOverlayModel.MemberState(
                        index + 1L, team, leader, names[index],
                        index == 7 ? 38f : 72f + index * 2f, 100f,
                        Math.max(8f, 32f - index), 40f, 12f,
                        switch (slot) {
                            case 1 -> "FLD-2 Line Rifle";
                            case 2 -> "SAW-2 Support Weapon";
                            case 3 -> "DMR-2 Marksman Rifle";
                            default -> "PLS-3 Lancer";
                        },
                        primary[slot], new Color(0x78, 0xD4, 0x94),
                        specialName, specialAbbrev, specialist ? "2 LEFT" : "",
                        leader ? "Kestrel Sweep" : null,
                        leader ? "READY" : "",
                        index < 4 ? "Veteran" : "Regular",
                        index % 3 == 0 ? "Gifted" : "Steady",
                        UnitRole.COMBATANT, leader ? ArmorClass.POWER_SUIT : ArmorClass.STANDARD));
            }
        }
        return members;
    }

    /** Full-screen evidence for one selected mech's battle-local doctrine override. */
    private static BufferedImage renderBattleMechOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean defendAreaTargeting) throws Exception {
        BufferedImage image = renderBattleBackdrop(width, height);
        if (defendAreaTargeting) {
            Graphics2D targetGraphics = image.createGraphics();
            targetGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            targetGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintWorldTarget(targetGraphics, width * 0.56f, height * 0.54f,
                    18f, BattlefieldMarkerPresentation.target(
                            "DEFEND AREA", true, 20f));
            targetGraphics.dispose();
        }
        Reactor reactor = new Reactor();
        BattleMechOverlayModel model = new BattleMechOverlayModel(
                reactor, () -> { }, (mechId, role) -> { },
                (mechId, order) -> { }, squadId -> { });
        model.updateProjected(new BattleMechOverlayModel.MechState(
                3, 303L, "Sirocco Three", MechVariant.SIROCCO.displayName,
                MechRole.LR_SUPPORT, MechRole.BALANCED,
                MechLanceOrder.FREE_REIGN,
                defendAreaTargeting,
                BattleMechOverlayModel.selectableRoles()));

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BATTLE_HUD_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleMechOverlay.COMPONENT, model.props())) {
            BattleMechOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            BufferedImage overlay = renderer.render(document,
                    Math.round(BattleMechOverlay.DOCUMENT_WIDTH),
                    Math.round(BattleMechOverlay.DOCUMENT_HEIGHT));
            Graphics2D graphics = image.createGraphics();
            int bottom = Math.round(BattleLayout.PAD + BattleLayout.BACK_H
                    + BattleLayout.CONTROLS_GAP);
            graphics.drawImage(overlay, 12,
                    height - bottom - overlay.getHeight(), null);
            graphics.dispose();
            return image;
        }
    }

    /** Full-screen evidence for the production MLX command rail over battle contrast. */
    private static BufferedImage renderStorySubtitle(SnapshotContext context,
            HeadlessUiRenderer renderer, String text) throws Exception {
        return renderVoiceSubtitle(context, renderer, text, false);
    }

    private static BufferedImage renderVoiceSubtitle(SnapshotContext context,
            HeadlessUiRenderer renderer, String text, boolean officer) throws Exception {
        BufferedImage image = renderBattleHudTaskForce(FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                List.of(StorySubtitleOverlay.COMPONENT_PATH, "data/ui/components/battle/company-subtitle.mlx"));
        loader.reload();
        try (MarkupInstance instance = loader.build(new Reactor(),
                officer ? "company-subtitle" : StorySubtitleOverlay.COMPONENT,
                officer ? Map.of("speaker", "COMPANY COMMAND", "subtitle", text) : StorySubtitleOverlay.props(text))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            UiViewport viewport = StorySubtitleOverlay.viewport(
                    new UiViewport(0, 0, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT));
            BufferedImage overlay = renderer.render(document,
                    Math.round(viewport.documentWidth()), Math.round(viewport.documentHeight()));
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(overlay, Math.round(viewport.screenX()),
                    Math.round(FULL_SCREEN_HEIGHT - viewport.screenY() - viewport.height()), null);
            graphics.dispose();
            return image;
        }
    }

    private static BufferedImage renderBattleHudCommandOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        return renderBattleHudCommandOverlay(context, renderer, width, height, false);
    }

    private static BufferedImage renderBattleHudCommandOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean groups) throws Exception {
        BufferedImage image = renderBattleHudTaskForce(width, height);
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        BattleHudOverlayModel.Presentation presentation = model.updateProjected(2f, List.of(
                capture(TacticalNode.Kind.COMMAND_POST,
                        CompoundService.CompoundState.MARINE_HELD, 0f),
                capture(TacticalNode.Kind.BARRACKS,
                        CompoundService.CompoundState.CONTESTED, 0.62f),
                capture(TacticalNode.Kind.ARMORY,
                        CompoundService.CompoundState.DEFENDER_HELD, 0f),
                capture(TacticalNode.Kind.BARRACKS,
                        CompoundService.CompoundState.MARINE_HELD, 0f),
                capture(TacticalNode.Kind.ARMORY,
                        CompoundService.CompoundState.DEFENDER_HELD, 0f),
                capture(TacticalNode.Kind.BARRACKS,
                        CompoundService.CompoundState.DEFENDER_HELD, 0f),
                capture(TacticalNode.Kind.ARMORY,
                        CompoundService.CompoundState.MARINE_HELD, 0f)),
                previewConquestCommander(groups));
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BATTLE_HUD_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleHudOverlay.COMPONENT, model.props())) {
            BattleHudOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            BufferedImage overlay = renderer.render(document,
                    width - 24,
                    Math.round(BattleHudOverlay.documentHeight(presentation)));
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(overlay, 12, 12, null);
            graphics.dispose();
            return image;
        }
    }

    private static BattleHudOverlayModel.CaptureObjective capture(
            TacticalNode.Kind kind, CompoundService.CompoundState state,
            float progress) {
        return new BattleHudOverlayModel.CaptureObjective(kind, state, progress);
    }

    private static CommanderSnapshot<ConquestFrontSnapshot> previewConquestCommander(boolean groups) {
        List<ConquestFrontSnapshot.TrackState> tracks = List.of(
                conquestTrack(0, 4, 43, 2, 101),
                conquestTrack(1, 3, 32, 0, 102),
                conquestTrack(2, 4, 41, 1, 103));
        List<ConquestFrontSnapshot.SquadDirective> directives = List.of(
                conquestDirective(1, 0, AssignmentKind.SECURE_COMPOUND, 101),
                conquestDirective(2, 0, AssignmentKind.SECURE_COMPOUND, 101),
                conquestDirective(3, 0, AssignmentKind.ATTACK_MOVE, -1),
                conquestDirective(4, 0, AssignmentKind.ATTACK_MOVE, -1),
                conquestDirective(5, 1, AssignmentKind.ADVANCE_TRACK, -1),
                conquestDirective(6, 1, AssignmentKind.ADVANCE_TRACK, -1),
                conquestDirective(7, 1, AssignmentKind.SECURE_COMPOUND, 102),
                conquestDirective(8, 2, AssignmentKind.SUPPORT, 103),
                conquestDirective(9, 2, AssignmentKind.SUPPORT, 103),
                conquestDirective(10, 2, AssignmentKind.ATTACK_MOVE, -1),
                conquestDirective(11, 2, AssignmentKind.ATTACK_MOVE, -1));
        List<ConquestFrontSnapshot.SquadState> squadStates = new ArrayList<>();
        if (groups) for (int id = 1; id <= 11; id++) {
            squadStates.add(new ConquestFrontSnapshot.SquadState(id, 12, 40, 80,
                    0, null, false));
        }
        ConquestFrontSnapshot front = new ConquestFrontSnapshot(
                420, 390, Faction.MARINE, TraversalAxis.SOUTH_TO_NORTH,
                ConquestFrontSnapshot.Phase.FRONT_ADJUST, 4, 140,
                CompoundService.CompoundState.DEFENDER_HELD,
                tracks, squadStates, directives);
        if (groups) front = front.withGroups(List.of(
                new ConquestFrontSnapshot.GroupState(
                        new CommandGroupIdentity("officer:vale", "vale", "Captain Vale", true),
                        List.of(1, 2, 3, 4, 5, 6), "ASSAULT", AssignmentKind.SECURE_COMPOUND,
                        101, -1, -1, "GROUP_TASK_PRESERVED", false),
                new ConquestFrontSnapshot.GroupState(
                        new CommandGroupIdentity("officer:hale", "hale", "Major Hale", true),
                        List.of(7, 8, 9, 10), "MIXED", AssignmentKind.ADVANCE_TRACK,
                        -1, 40, 80, "GROUP_BOUNDED_RALLY", true),
                new ConquestFrontSnapshot.GroupState(
                        CommandGroupIdentity.actingNco("11", "Squad 11"),
                        List.of(11), "HOLD", AssignmentKind.DEFEND_AREA,
                        -1, 40, 80, "ACTING_NCO_AUTONOMY", false)));
        return new CommanderSnapshot<>(Faction.MARINE, "conquest-command",
                "FRONT_ADJUST", 420, 390, 11, 0,
                List.of("remaining compounds=4"), List.of(), front);
    }

    private static ConquestFrontSnapshot.TrackState conquestTrack(
            int index, int squads, int members, int contacts, int targetZone) {
        return new ConquestFrontSnapshot.TrackState(index, index * 80,
                index * 80 + 79, squads, squads, members,
                0.43f + index * 0.05f, 0.55f + index * 0.04f,
                contacts > 0 ? 0.65f + index * 0.03f : -1f,
                contacts, 18f + index * 3f, contacts * 8f, targetZone);
    }

    private static ConquestFrontSnapshot.SquadDirective conquestDirective(
            int squadId, int track, AssignmentKind kind, int targetZone) {
        return new ConquestFrontSnapshot.SquadDirective(squadId, track, track,
                ConquestFrontSnapshot.AssignmentReason.TRACK_ADVANCE,
                kind, targetZone);
    }

    /** Full-screen evidence for the compact power deck in its armed state. */
    private static BufferedImage renderBattleHudPowerOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        BufferedImage image = renderBattleHudCommandOverlay(
                context, renderer, width, height);
        Reactor reactor = new Reactor();
        BattlePowerOverlayModel model = new BattlePowerOverlayModel(
                reactor, ignored -> { }, snapshotCommodities());
        List<BattlePowerOverlayModel.PowerState> powers = List.of(
                power("recon_ping", "Recon Ping", 2f, 0, 7.4f, -1),
                power("mech_support", "Mech Support", 4f, 0, 0f, 2),
                power("emergency_resupply", "Resupply", 3f, 0, 0f, 1),
                power("orbital_barrage", "Orbital Barrage", 4f, 20, 0f, 1),
                power("marine_insertion", "Marine Drop", 3f, 2, 0f, 0));
        model.updateProjected(4f, 10f, 22, powers, "orbital_barrage");
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BATTLE_HUD_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattlePowerOverlay.COMPONENT, model.props())) {
            BattlePowerOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            int overlayWidth = Math.round(BattlePowerOverlay.documentWidth(powers.size()));
            int overlayHeight = Math.round(BattlePowerOverlay.TARGETING_HEIGHT);
            BufferedImage overlay = renderer.render(document, overlayWidth, overlayHeight);
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(overlay,
                    (width - overlay.getWidth()) / 2,
                    height - 12 - overlay.getHeight(), null);
            graphics.dispose();
            return image;
        }
    }

    private static BattlePowerOverlayModel.PowerState power(
            String id, String name, float cp, int supplies,
            float cooldown, int charges) {
        return new BattlePowerOverlayModel.PowerState(
                id, name, cp, supplies, cooldown, charges);
    }

    /** Full-screen evidence for the compact exit and its deliberate confirmation state. */
    private static BufferedImage renderBattleRetreatOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean confirming) throws Exception {
        BufferedImage image = renderBattleHudPowerOverlay(
                context, renderer, width, height);
        Reactor reactor = new Reactor();
        BattleRetreatOverlayModel model = new BattleRetreatOverlayModel(
                reactor, () -> { }, () -> { }, "Retreat", "Continue",
                "Abandon operation?", "Cancel", "Retreat");
        Map<String, Object> props = model.props();
        if (confirming) ((Runnable) props.get("action")).run();

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BATTLE_HUD_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleRetreatOverlay.COMPONENT, props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            BattleRetreatOverlayModel.Presentation presentation = model.presentation();
            BufferedImage overlay = renderer.render(document,
                    Math.round(presentation.documentWidth()),
                    Math.round(presentation.documentHeight()));
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(overlay, 12,
                    height - 12 - overlay.getHeight(), null);
            graphics.dispose();
            return image;
        }
    }

    /**
     * Full-viewport scale evidence for the production task-force plate. The
     * backdrop is only a neutral contrast field; every HUD operation, value,
     * and coordinate comes through {@link TaskForceStatusPanel#paint} — the
     * same method the live OpenGL panel invokes.
     */
    private static BufferedImage renderBattleHudTaskForce(int width, int height) {
        BufferedImage image = renderBattleBackdrop(width, height);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        TaskForceStatusPanel.Snapshot snapshot =
                TaskForceStatusPanel.Snapshot.capture(battleHudSquads());
        TaskForceStatusPanel.paint(new HeadlessTaskForcePaintTarget(graphics, height),
                snapshot,
                12f,
                12f + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP,
                1f);
        graphics.dispose();
        return image;
    }

    private static BufferedImage renderBattleBackdrop(int width, int height) {
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintBattleContrastField(graphics, width, height);
        graphics.dispose();
        return image;
    }

    /** Representative late-battle force state at the scale the old list could not hold. */
    private static List<Squad> battleHudSquads() {
        List<Squad> squads = new ArrayList<>();
        for (int index = 0; index < 44; index++) {
            Squad squad = new Squad(index + 1, Faction.MARINE);
            squad.originalSize = 12;
            squad.aliveMembers = index < 3 ? 0 : index < 9
                    ? 5 : 9 + index % 4;
            squad.moraleBroken = index >= 3 && index < 9;
            squad.morale = squad.moraleBroken ? 0.12f : 0.66f + (index % 3) * 0.08f;
            squad.alertLevel = switch (index % 4) {
                case 0, 1 -> SquadAlertLevel.ENGAGED;
                case 2 -> SquadAlertLevel.SUSPICIOUS;
                default -> SquadAlertLevel.UNAWARE;
            };
            squads.add(squad);
        }
        return squads;
    }

    private static void paintBattleContrastField(Graphics2D graphics,
                                                 int width, int height) {
        graphics.setColor(new Color(8, 13, 18));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(28, 35, 40));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(38, 45, 48));
        graphics.fillRect(width / 2 - 130, 0, 260, height);
        graphics.setColor(new Color(18, 24, 29));
        graphics.fillRect(210, 170, 460, 310);
        graphics.fillRect(width - 690, 180, 480, 360);
        graphics.fillRect(680, 650, 520, 250);
        graphics.setColor(new Color(53, 63, 68));
        graphics.setStroke(new BasicStroke(1f));
        for (int x = 0; x < width; x += 32) {
            graphics.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += 32) {
            graphics.drawLine(0, y, width, y);
        }
    }

    /** Java2D drain for the task-force panel's backend-neutral paint seam. */
    private static final class HeadlessTaskForcePaintTarget
            implements TaskForceStatusPanel.PaintTarget {
        private final Graphics2D graphics;
        private final int imageHeight;

        private HeadlessTaskForcePaintTarget(Graphics2D graphics, int imageHeight) {
            this.graphics = graphics;
            this.imageHeight = imageHeight;
        }

        @Override
        public void filledRect(float x, float y, float width, float height,
                               Color color, float alphaMult) {
            graphics.setColor(alpha(color, alphaMult));
            graphics.fillRect(Math.round(x), top(y, height),
                    Math.round(width), Math.round(height));
        }

        @Override
        public void borderRect(float x, float y, float width, float height,
                               Color color, float alphaMult) {
            graphics.setColor(alpha(color, alphaMult));
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawRect(Math.round(x), top(y, height),
                    Math.round(width), Math.round(height));
        }

        @Override
        public void disc(float centerX, float centerY, float radius,
                         Color color, float alphaMult) {
            graphics.setColor(alpha(color, alphaMult));
            graphics.fill(new Ellipse2D.Float(centerX - radius,
                    imageHeight - centerY - radius, radius * 2f, radius * 2f));
        }

        @Override
        public void text(TaskForceStatusPanel.TextRole role, String text,
                         float x, float y, Color color, float alphaMult) {
            int style = role == TaskForceStatusPanel.TextRole.HEADER
                    ? Font.BOLD : Font.PLAIN;
            graphics.setFont(new Font(Font.SANS_SERIF, style, 12));
            graphics.setColor(alpha(color, alphaMult));
            graphics.drawString(text, x, imageHeight - y + 10f);
        }

        @Override
        public void moraleBar(float x, float y, float width, float height,
                              float morale, float cap, boolean broken,
                              float breakThreshold, float alphaMult) {
            float fill = cap > 0f ? Math.max(0f, Math.min(1f, morale / cap)) : 0f;
            filledRect(x, y, width, height, new Color(0x14, 0x18, 0x20), alphaMult);
            Color fillColor = fill > 0.5f ? new Color(0x40, 0xC0, 0x40)
                    : fill > 0.3f ? new Color(0xE0, 0xC0, 0x40)
                    : new Color(0xE0, 0x50, 0x40);
            if (fill > 0f) filledRect(x, y, width * fill, height, fillColor, alphaMult);
            float tickX = x + width * Math.max(0f, Math.min(1f, breakThreshold));
            filledRect(tickX, y - 1f, 1.5f, height + 2f,
                    new Color(0xF0, 0xF0, 0xF0, 0xD0), alphaMult);
            borderRect(x, y, width, height,
                    broken ? new Color(0xE0, 0x40, 0x40)
                            : new Color(0x60, 0x80, 0xA0), alphaMult);
        }

        private int top(float y, float height) {
            return Math.round(imageHeight - y - height);
        }

        private static Color alpha(Color color, float alphaMult) {
            int alpha = Math.round(color.getAlpha()
                    * Math.max(0f, Math.min(1f, alphaMult)));
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
    }

    private static BufferedImage renderMissionSelect(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        return renderMissionSelect(context, renderer, width, height, 1f);
    }

    private static BufferedImage renderMissionSelect(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale) throws Exception {
        Reactor reactor = new Reactor();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), MISSION_SELECT_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor,
                MissionSelectScreen.ROOT_COMPONENT,
                MissionSelectScreen.previewProps())) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document, width, height, uiScale);
        }
    }

    private static BufferedImage renderMissionBriefing(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean conquest,
            boolean debugExpanded) throws Exception {
        return renderMissionBriefing(context, renderer, width, height, conquest, debugExpanded, 1f);
    }

    private static BufferedImage renderMissionBriefing(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean conquest, boolean debugExpanded, float uiScale) throws Exception {
        return renderMissionBriefing(context, renderer, width, height, conquest, debugExpanded, uiScale, false);
    }

    private static BufferedImage renderMissionBriefing(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean conquest, boolean debugExpanded, float uiScale,
            boolean scrolled) throws Exception {
        return renderMissionBriefing(context, renderer, width, height,
                BriefingScreen.previewProps(conquest, debugExpanded), uiScale, scrolled);
    }

    private static BriefingSurvey productionSurvey(SnapshotContext context, String file, int personnel) throws Exception {
        Path path = context.modRoot().getParent().resolve("src/test/resources/battle-fixtures").resolve(file);
        JSONObject json = new JSONObject(Files.readString(path));
        if (json.has("construction")) json = json.getJSONObject("construction");
        boolean conquest = json.getString("kind").equals("CONQUEST");
        json.put("tier", conquest ? "FULL_STRENGTH" : "FIRST_CONTRACT");
        json.put("risk", conquest ? "HIGH" : "MEDIUM");
        JSONArray shuttles = new JSONArray();
        int boats = conquest ? 6 : 1;
        for (int i = 0; i < boats; i++) shuttles.put(new JSONObject()
                .put("type", "AEROSHUTTLE").put("cycles", personnel / boats / 6)
                .put("seatsPerSortie", 6));
        json.put("shuttles", shuttles);
        if (conquest) json.getJSONObject("arrivalPlan").put("dropZoneCount", 3)
                .put("shuttlePairsPerZone", 2).put("timingJitterSec", 0.75);
        BattleFixture fixture = BattleFixtureJson.fromJson(json);
        try (BattleSimulation sim = fixture.build()) {
            BriefingSurvey survey = BriefingSurvey.capture(sim);
            if (survey.markers().stream().noneMatch(BriefingSurvey.Marker::landing)) {
                throw new IllegalStateException("Briefing survey lost production landing zones");
            }
            return survey;
        }
    }

    private static Map<String, Object> surveyProps(Map<String, Object> props, BriefingSurvey survey) {
        BriefingViewModel.putSurvey(props, survey, "");
        return props;
    }

    private static Map<String, Object> loadingBriefingProps() {
        Map<String, Object> props = BriefingViewModel.campaignPreviewProps(false);
        props.put("deployLabel", "PREPARING");
        props.put("deployDisabled", true);
        props.put("deploymentStatus", "Preparing deployment area...");
        return props;
    }

    private static Map<String, Object> understrengthBriefingProps() {
        Map<String, Object> props = BriefingViewModel.campaignPreviewProps(false);
        BriefingViewModel.putForceComparison(props, OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, 6, 12);
        props.put("personnelSummary", "6 ready / minimum 4 / recommendation is advisory");
        Mission mission = Mission.builder().id("understrength-preview").name("First contract")
                .type(MissionType.SABOTAGE).source(MissionSource.GENERATED).requiredDrops(2).build();
        BriefingViewModel.putLandingCraft(props, mission,
                List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE)), 6, "HSS Siege of Raesvelg");
        return props;
    }

    private static Map<String, Object> missionControlStateProps() {
        Map<String, Object> props = new LinkedHashMap<>(BriefingScreen.previewProps(false, true));
        props.put("assignLabel", "ASSIGN");
        props.put("assignClasses", "");
        props.put("assignDisabled", false);
        props.put("deployLabel", "DEPLOY");
        props.put("deploymentStatus", "No landing craft available");
        BriefingViewModel.putLandingCraft(props,
                Mission.builder().id("no-lift-preview").name("No lift")
                        .type(MissionType.SABOTAGE).source(MissionSource.DEBUG).requiredDrops(2).build(),
                List.of(), 12, null);
        props.put("deployClasses", "briefing-deploy briefing-blocked");
        props.put("deployDisabled", true);
        return props;
    }

    private static BufferedImage renderMissionBriefing(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, Map<String, Object> props, float uiScale,
            boolean scrolled) throws Exception {
        return renderMissionBriefing(context, renderer, width, height, props, uiScale, scrolled, null);
    }

    private static BufferedImage renderMissionBriefing(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, Map<String, Object> props, float uiScale,
            boolean scrolled, String hintTarget) throws Exception {
        Reactor reactor = new Reactor();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), MISSION_BRIEFING_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor,
                BriefingScreen.ROOT_COMPONENT, props)) {
            BriefingViewModel.applyVisibility(instance, props);
            if (scrolled) {
                instance.requireElement("mission-debug-controls").scrollTop(10000f);
                instance.requireElement("mission-plan-sections").scrollTop(10000f);
                instance.requireElement("mission-overview-copy").scrollTop(10000f);
            }
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            SpecSheetBinder hints = BriefingHints.install(document, instance, props);
            BriefingSurvey.install(document, instance, props);
            if (hintTarget != null) {
                openSpecSheet(document, hints, width, height, uiScale, instance.requireElement(hintTarget));
            }
            return renderRelative(renderer, document, width, height, uiScale);
        }
    }

    static BufferedImage renderMissionFlow(
            SnapshotContext context, HeadlessUiRenderer renderer,
            List<String> components, String rootComponent,
            Map<String, Object> props) throws Exception {
        Reactor reactor = new Reactor();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), components);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, rootComponent, props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document,
                    FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f);
        }
    }

    /**
     * The polity panel with one armour pattern hovered and its spec sheet open.
     *
     * <p>It hovers the real element: lay the production document out, move the
     * pointer to the subject's own centre, and let the binder answer. That is
     * the route {@code fleet-armory-equipment-tooltip-wide.png} already uses,
     * and it is the only one in which the hit test, the placement, and the copy
     * are all exercised by the picture (law 11).
     */
    private static BufferedImage renderPolityDoctrineSpecSheet(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        Reactor reactor = new Reactor();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), POLITY_DOCTRINE_COMPONENTS);
        loader.reload();
        Map<String, Object> props = PolityDoctrineScreen.previewProps(
                ModStrings.fromDisk(context.modRoot()));
        try (MarkupInstance instance = loader.build(
                reactor, PolityDoctrineScreen.ROOT_COMPONENT, props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());

            SpecSheetBinder binder = new SpecSheetBinder(
                    document, SpecSheetLayer.install(document));
            PolityDoctrineScreen.bindSpecSheets(binder, instance, props);
            openSpecSheet(document, binder, width, height, instance.requireElement(
                    PolityDoctrineScreen.firstSpecSheetAnchorId(props)));
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    /**
     * Opens one screen's spec sheet the way the game does: lay the production
     * document out at the photographed size, move the pointer to the subject's
     * own centre, and let the binder answer (law 11). Every spec-sheet artifact
     * goes through here, so the hit test, the placement and the copy are all
     * exercised by the picture rather than staged for it.
     */
    private static void openSpecSheet(UiDocument document, SpecSheetBinder binder,
                                      int width, int height, UiElement subject) {
        openSpecSheet(document, binder, width, height, 1f, subject);
    }

    private static void openSpecSheet(UiDocument document, SpecSheetBinder binder,
                                      int width, int height, float uiScale, UiElement subject) {
        UiViewport viewport = UiViewport.relative(
                0f, 0f, width / uiScale, height / uiScale, uiScale,
                MarineOpsUiViewport.REFERENCE_WIDTH,
                MarineOpsUiViewport.REFERENCE_HEIGHT);
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        Rect box = subject.box().borderBox();
        document.pointerMoved(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
        binder.update();
        if (binder.openTarget() != subject) {
            throw new IllegalStateException("Spec sheet did not open for " + subject.id());
        }
        document.advance(0f);
    }

    private static BufferedImage renderCompanyHq(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale) throws Exception {
        return renderCompanyHq(context, renderer, width, height, uiScale,
                CompanyHqViewModel.preview());
    }

    static BufferedImage renderCompanyHq(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale,
            CompanyHqViewModel viewModel) throws Exception {
        return renderCompanyHq(context, renderer, width, height, uiScale, viewModel,
                CompanyHqNewsDesk.Channel.ALL);
    }

    private static BufferedImage renderCompanyHq(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, CompanyHqViewModel viewModel,
            CompanyHqNewsDesk.Channel channel) throws Exception {
        Reactor reactor = new Reactor();
        CompanyHqNewsDesk desk = new CompanyHqNewsDesk(reactor);
        Map<String, Object> hqProps = viewModel.props();
        desk.put(hqProps);
        desk.choose(channel);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), COMPANY_HQ_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "company-hq", hqProps)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            CompanyIntelTerminal terminal = new CompanyIntelTerminal(desk);
            terminal.bind(document, instance);
            terminal.advance(0f);
            terminal.advance(1f);
            BufferedImage frame = renderRelative(renderer, document, width, height, uiScale);
            if (hqProps.get("openingGuide") instanceof OpeningWorkGuide guide && guide.visible()) {
                Rect panel = instance.requireElement("company-hq-opening-guide").box().borderBox();
                for (String id : List.of("company-hq-opening-heading", "company-hq-opening-mission",
                        "company-hq-opening-payment", "company-hq-opening-route", "company-hq-opening-preparation",
                        "company-hq-opening-contact", "company-hq-opening-location", "company-hq-opening-course")) {
                    Rect child = instance.requireElement(id).box().borderBox();
                    if (child.y() < panel.y() || child.bottom() > panel.bottom()) {
                        throw new IllegalStateException("Opening guidance exceeds its panel: " + id);
                    }
                }
                if (instance.requireElement("company-hq-obligations-header").box().borderBox().y() < panel.bottom()) {
                    throw new IllegalStateException("Opening guidance overlaps obligations");
                }
            }
            return frame;
        }
    }

    /**
     * The transfer screen, driven by the same view model the game drives.
     *
     * <p>The fleet is a fixture because a snapshot has no campaign to read one
     * from, but the ships in it are real hulls and every row, cell, verdict and
     * plan is produced by the production code from their generated decks. A
     * screenshot assembled from authored strings would prove only that the
     * layout compiles.
     *
     * @param selected which candidate to show; one shot is the ship they live
     *     on and the other a hull that would cost them something, since that
     *     second reading is what the screen exists for
     * @param standing whether the company has a home, never had one, or lost
     *     one; without a home the screen judges a hull on what she lacks rather
     *     than on what leaving would cost
     * @param means the company being moved and the purse it is moved out of,
     *     which is what prices the refit and what can refuse it
     */
    private static BufferedImage renderShipTransfer(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, int selected, float[] pointAt,
            CompanyShipDesignation.Home standing, CompanyMeans means) throws Exception {
        Reactor reactor = new Reactor();
        List<ShipTransferViewModel.Candidate> fleet = transferFleet(context);
        if (fleet.isEmpty()) return renderer.renderRelative(new UiDocument(null),
                width, height, 1f, MarineOpsUiViewport.REFERENCE_WIDTH,
                MarineOpsUiViewport.REFERENCE_HEIGHT);
        CompanyShipDesignation.Home home = standing.shipId() == null ? standing
                : new CompanyShipDesignation.Home(fleet.get(0).id(), null, false, 0);
        ShipTransferViewModel viewModel = new ShipTransferViewModel(
                reactor, () -> fleet, () -> home, moved -> { }, means);
        viewModel.select(fleet.get(Math.min(selected, fleet.size() - 1)).id());
        // Evidence is of the finished screen rather than of the moment it
        // opens: the decks are laid out off the screen's thread, and every row
        // says so until its own has landed.
        viewModel.candidateRows().get();
        while (viewModel.reading()) {
            if (viewModel.advance()) viewModel.candidateRows().get();
            else Thread.onSpinWait();
        }

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), SHIP_TRANSFER_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "ship-transfer", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            DeckPlanCanvas plan = new DeckPlanCanvas(viewModel::selectedPlan);
            // Pointed at the deck so the evidence shows what a player hovering
            // one compartment is told about it.
            if (pointAt != null) plan.pointAt(pointAt[0], pointAt[1]);
            document.canvases().set(instance.requireElement("transfer-plan"), plan);
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    /**
     * A fleet of real vanilla hulls, read the way the game reads them: their
     * own collision outlines and their own complements, so the decks drawn are
     * decks the generator would really produce.
     */
    private static List<ShipTransferViewModel.Candidate> transferFleet(
            SnapshotContext context) throws Exception {
        VanillaHullSilhouettes vanilla = new VanillaHullSilhouettes(context.starsectorCore());
        if (!vanilla.available()) return List.of();
        List<ShipTransferViewModel.Candidate> fleet = new ArrayList<>();
        for (String hullId : TRANSFER_FLEET) {
            VanillaHullSilhouettes.Hull hull = vanilla.read(hullId);
            if (hull == null) continue;
            fleet.add(new ShipTransferViewModel.Candidate(hullId,
                    hullId.toUpperCase(Locale.ROOT),
                    hull.hullClass().name().toLowerCase(Locale.ROOT) + ", "
                            + hull.role().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    hull.lift(), hull.cargo(),
                    new CompanyShip(hull.hullClass(), hull.role(), hull.minCrew(),
                            hull.maxCrew(), hull.cargo(), hull.silhouette(), hull.spriteName())));
        }
        return List.copyOf(fleet);
    }

    private static Map<String, Object> props(ShipTransferViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("candidateRows", viewModel.candidateRows());
        props.put("facilityCells", viewModel.facilityCells());
        props.put("selectedName", viewModel.selectedName());
        props.put("selectedSummary", viewModel.selectedSummary());
        props.put("verdict", viewModel.verdict());
        props.put("costLabel", viewModel.costLabel());
        props.put("costClasses", viewModel.costClasses());
        props.put("transferLabel", viewModel.transferLabel());
        props.put("transferClasses", viewModel.transferClasses());
        props.put("transferAction", (Runnable) () -> { });
        props.put("roomTitle", viewModel.roomTitle());
        props.put("roomCopy", viewModel.roomCopy());
        props.put("contextLabel", viewModel.contextLabel());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_TRANSFER,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }

    private static BufferedImage renderFleetArmoryWorkspace(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean fireteam, boolean pickerOpen) throws Exception {
        return renderFleetArmoryWorkspace(
                context, renderer, width, height, fireteam, pickerOpen, false, false);
    }

    /** Fixes the photographed ship's layout so the snapshots compare run to run. */
    private static final long SHIP_SEED = 0x5AFE_DECEL;
    /** Longest a hull here takes to lay out; a capital is seconds. */
    private static final long READY_TIMEOUT_MS = 120_000L;
    /** Hull kept around a framed compartment, matching what the screens ask for. */
    private static final int MECH_LAB_SURROUND_CELLS = 2;

    /**
     * A running company ship for a room snapshot to photograph.
     *
     * <p>Run on a little before the shutter: a compartment photographed at zero
     * has everybody standing on their spawn cell, which is the one arrangement
     * the crew is never actually in.
     */
    private static CompanyDeck companyShip(Supplier<List<MechVariant>> lance,
                                           Supplier<List<MarineSoldier>> company) {
        return companyShip(lance, company, null);
    }

    /**
     * The same, for a hull the company is short a boat on: the berth stands
     * empty on the picture the way it stands empty on the deck.
     *
     * <p>Handed the ship rather than a plain array, because how many berths
     * there are is the hull's answer and the campaign deck that says which of
     * them are held has to be built against it. She lays her deck out for the
     * question and is crewed from the same one, so this costs no second
     * generation.
     */
    private static CompanyDeck companyShip(Supplier<List<MechVariant>> lance,
                                           Supplier<List<MarineSoldier>> company,
                                           Function<CompanyDeck, boolean[]> boatsHeld) {
        boolean[][] held = new boolean[1][];
        CompanyDeck ship = new CompanyDeck(TestHulls.transport(), SHIP_SEED,
                null, lance, company, () -> held[0]);
        if (boatsHeld != null) held[0] = boatsHeld.apply(ship);
        readied(ship);
        ship.advance(18f);
        return ship;
    }

    /**
     * Hold the shutter until she is ready.
     *
     * <p>A deck is laid out away from the frame that asked for it, so a ship
     * built and photographed in the same breath is photographed mid-layout —
     * and a canvas draws a ship that is not ready yet as nothing at all, which
     * is the correct thing for a screen to do and a blank plate here. Which
     * plates it blanked was decided by hull size: a transport won the race and
     * the whole-ship view's capital did not, so that one had been recording an
     * empty panel rather than a ship.
     *
     * <p>An uninhabitable hull is never getting ready, so this returns at once
     * rather than waiting out a clock for a ship that has no deck to lay.
     */
    private static void readied(CompanyDeck ship) {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (ship.gettingReady()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(
                        "the ship was still being laid out after "
                                + READY_TIMEOUT_MS + "ms");
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "interrupted while the ship was laid out", interrupted);
            }
        }
    }

    /** Barracks -> selected squad equipment -> Barracks, at the player's review scale. */
    static List<SnapshotArtifact> renderBarracksEquipment(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        var reactor = new Reactor();
        var roster = new MarineRoster();
        roster.bootstrapInitialComplement(36);
        roster.dismissManagementGuide();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var squad = roster.squads().stream().filter(q -> !q.reserve()).findFirst().orElseThrow();
        var model = new BarracksViewModel(reactor, roster, () -> 100d, snapshotEquipmentResources(),
                () -> 20, id -> new PersonnelDrawResult(0, 0));
        CompanyDeck ship = companyShip(List::of, () -> MarineOpsContext.companyMarines(roster));
        List<ScreenId> routes = new ArrayList<>();
        var navigation = new MarineOpsContext(null) {
            @Override public void goTo(ScreenId screen) { routes.add(screen); }
        };
        model.command().equipmentWorkspace(() -> navigation.openFleetArmoryWorkspaceFrom(ScreenId.BARRACKS));
        model.command().navigation(() -> navigation.openSquadFoundingFrom(ScreenId.BARRACKS),
                navigation::openSquadEquipmentFromBarracks, () -> {}, true);
        var properties = props(model, ship);
        properties.put("contextLabel", "BARRACKS / COMPANY PREPARATION");
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
        List<SnapshotArtifact> frames = new ArrayList<>();
        try (var markup = loader.reloadAndBuild(reactor, "shipboard-barracks", properties)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            var guide = BarracksGuideCallout.bind(document, markup, model.command(), model.command()::showOverview);
            document.canvases().set(markup.requireElement("barracks-canvas"), new BarracksCanvas(ship, model::sceneMarines));
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP,
                    List.of(CompanyNotices.Notice.FIRST_REFIT));
            frames.add(new SnapshotArtifact("01-barracks-equipment-new.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            clickBarracksFlow(document, markup, "barracks-equipment");
            if (!routes.equals(List.of(ScreenId.FLEET_ARMORY))) throw new AssertionError("Equipment entry did not route");
            model.command().toggle(squad.id());
            model.command().inspect(squad.id());
            markup.flush();
            frames.add(new SnapshotArtifact("02-barracks-equip-squad.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            clickBarracksFlow(document, markup, "barracks-edit-equipment");
            if (!squad.id().equals(navigation.takeRequestedArmorySquadId())) throw new AssertionError("Wrong squad requested");
            var editor = new FleetArmoryViewModel(reactor, roster, () -> {}, () -> 100d, snapshotEquipmentResources());
            editor.selectSquad(squad.id());
            var editorProps = props(editor);
            editorProps.put("returnAction", (Runnable) navigation::returnFromFleetArmoryWorkspace);
            var editorLoader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
            try (var editorMarkup = editorLoader.reloadAndBuild(reactor, "fleet-armory-fireteam", editorProps)) {
                var editorDocument = new UiDocument(editorMarkup.root()).theme(MarineOpsThemes.standard());
                editorMarkup.styles().forEach(editorDocument::addStyleSheet);
                MarineOpsPageNav.refresh(editorMarkup, MarineOpsPageNav.Page.ARMORY, MarineOpsPageNav.ANY_SHIP,
                        List.of(CompanyNotices.Notice.FIRST_REFIT));
                frames.add(new SnapshotArtifact("03-existing-equipment-editor.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                editor.showArmorPickerAction().run();
                editorMarkup.flush();
                frames.add(new SnapshotArtifact("03a-starter-tactics.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                roster.firstRefit().claim(roster, FirstRefit.Path.FORWARD_SCREEN);
                editor.refresh();
                editor.showWeaponPickerAction().run();
                editor.weaponDoctrineTiles().get().get(0).select().run();
                editorMarkup.flush();
                frames.add(new SnapshotArtifact("03b-earned-weapon-comparison.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                collectArmorTemplates(roster);
                editor.refresh();
                editor.showArmorPickerAction().run();
                editor.armorDoctrineTiles().get().get(0).select().run();
                editorMarkup.flush();
                frames.add(new SnapshotArtifact("03c-earned-armor-comparison.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                // Production pointer targets remain visible at the player's review scale.
                var grid = editorMarkup.requireElement("loadout-billets").box().borderBox();
                for (int i = 0; i < 12; i++) {
                    var box = editorMarkup.requireElement("loadout-billet:" + i).box().borderBox();
                    if (box.width() < 90 || box.height() < 120 || box.bottom() > grid.bottom())
                        throw new AssertionError("Billet is clipped: " + i);
                }
                if (editorMarkup.requireElement("apply-squad-equipment").box().borderBox().width() > 200)
                    throw new AssertionError("Fit action must stay compact");
                var members = roster.manningMemberIds(squad);
                EquipmentTestFixtures.issueLauncher(roster.soldierById(members.get(0)));
                roster.applySoldierOutcome(Map.of(members.get(0), MarineSoldierStatus.WIA,
                        members.get(11), MarineSoldierStatus.KIA), 100f, 2f);
                editor.refresh(); editorMarkup.flush();
                frames.add(new SnapshotArtifact("03d-wounded-special-removal.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                clickBarracksFlow(editorDocument, editorMarkup, "loadout-billet:11");
                frames.add(new SnapshotArtifact("03e-vacant-billet-plan.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                if (editor.applyDisabled().get()) throw new AssertionError("Wounds or vacancies blocked fitting");
                clickBarracksFlow(editorDocument, editorMarkup, "loadout-billet:0");
                var sheets = new SpecSheetBinder(editorDocument, SpecSheetLayer.install(editorDocument));
                ArmorySpecSheets.bindInspection(sheets, editorMarkup, editor.inspection());
                var target = editorMarkup.requireElement("loadout-item:1:proposed");
                var targetBox = target.box().borderBox();
                editorDocument.pointerMoved(targetBox.x() + targetBox.width()/2, targetBox.y() + targetBox.height()/2);
                sheets.update(); editorDocument.advance(0.3f);
                if (sheets.openTarget() != target) throw new AssertionError("Proposed equipment lost its spec sheet");
                frames.add(new SnapshotArtifact("03f-equipment-spec-sheet.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                sheets.clear();
                clickBarracksFlow(editorDocument, editorMarkup, "page-nav-return");
                if (routes.get(routes.size() - 1) != ScreenId.BARRACKS) throw new AssertionError("Editor return missed Barracks");
            }
            if (!model.command().inspecting() || !model.command().selected().contains(squad.id()))
                throw new AssertionError("Equipment return lost squad context");
            frames.add(new SnapshotArtifact("04-return-to-selected-squad.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            // The existing lesson owns this receipt when displayed; visiting the editor did not clear it.
            CompanyNotices.Notice.FIRST_REFIT.review(roster);
            model.command().showOverview();
            markup.flush();
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP, List.of());
            frames.add(new SnapshotArtifact("05-equipment-after-lesson-review.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
        }
        return frames;
    }

    static List<SnapshotArtifact> renderBarracksCommand(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> images = new ArrayList<>();
        images.add(new SnapshotArtifact("results-prepare-company.png",
                renderMissionFlow(context, renderer, MISSION_RESULTS_COMPONENTS,
                        ResultsScreen.ROOT_COMPONENT, ResultsScreen.previewProps(true, true))));
        for (String state : List.of("dossier", "dossier-trained", "command", "profiles", "quarters", "empty", "large", "transfers", "guide", "guide-transfer", "guide-replenish", "guide-equipment")) {
            Reactor reactor = new Reactor();
            MarineRoster roster = new MarineRoster();
            int count = state.equals("empty") ? 0 : state.equals("large") ? 120 : 12;
            roster.bootstrapInitialComplement(count * MarineSquad.CAPACITY);
            MarineCaptain captain = new MarineCaptain("Tamsin Vale", "graphics/portraits/portrait_hegemony06.png", Rank.MAJOR, 0f);
            roster.add(captain);
            MarineCaptain second = new MarineCaptain("Rafael Kade", "graphics/portraits/portrait_mercenary04.png", Rank.CAPTAIN, 0f);
            roster.add(second);
            var squads = roster.squads().stream().filter(q -> !q.reserve()).toList();
            var profile = roster.armory().saveSquadProfile("Line infantry", SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                    roster.armory().armorDoctrines().get(0).id());
            for (int i = 0; i < squads.size(); i++) {
                if (i < 8) roster.assignCaptainToSquad(captain.id(), squads.get(i).id());
                else if (i < 12) roster.assignCaptainToSquad(second.id(), squads.get(i).id());
                roster.assignSquadProfile(Set.of(squads.get(i).id()), profile.id());
            }
            if (!squads.isEmpty()) {
                roster.applySoldierOutcome(Map.of(squads.get(1).memberIds().get(0), MarineSoldierStatus.WIA,
                        squads.get(2).memberIds().get(0), MarineSoldierStatus.KIA), 100f, 2f);
                roster.bindStationing(1001L, second.id(), List.of(squads.get(10).id()));
            }
            BarracksViewModel viewModel = new BarracksViewModel(reactor, roster, () -> 100d,
                    snapshotEquipmentResources(), () -> 0, id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0));
            if (state.equals("command") || state.equals("profiles")) {
                viewModel.command().selectCommand(captain.id());
                ((Runnable) viewModel.command().props().get("closeDossier")).run();
                viewModel.command().toggle(squads.get(0).id());
                viewModel.command().toggle(squads.get(1).id());
                viewModel.command().toggle(squads.get(2).id());
                viewModel.command().selectProfile(profile.id());
            }
            if (state.startsWith("dossier")) {
                if (state.equals("dossier-trained")) {
                    captain.train(OfficerSpecialty.TACTICAL_COVER);
                    captain.train(OfficerSpecialty.RECOVERY_PLANNING);
                    captain.commendations().add("Day 98: Wayline / victory / +700 XP / ACTIVE.");
                    captain.career().record("snapshot-attempt", true, 700);
                    captain.addXp(700);
                }
                viewModel.command().selectCommand(captain.id());
            }
            if (!state.startsWith("guide") && !state.equals("empty")) ((Runnable) viewModel.command().props().get("guideDismiss")).run();
            if (state.equals("transfers")) {
                viewModel.command().toggle(squads.get(2).id());
                ((Runnable) viewModel.command().props().get("chooseDonors")).run();
                viewModel.command().toggle(squads.get(1).id());
                viewModel.command().reviewTransfers();
            }
            if (state.startsWith("guide-")) {
                viewModel.command().toggle(squads.get(2).id());
                if (state.equals("guide-transfer")) {
                    ((Runnable) viewModel.command().props().get("chooseDonors")).run();
                    viewModel.command().toggle(squads.get(1).id());
                    viewModel.command().reviewTransfers();
                } else {
                    ((Runnable) viewModel.command().props().get("guideNext")).run();
                    if (state.equals("guide-equipment")) ((Runnable) viewModel.command().props().get("guideNext")).run();
                }
            }
            if (state.equals("profiles")) ((Runnable) viewModel.command().props().get("newProfile")).run();
            if (state.equals("quarters")) viewModel.command().inspect(squads.get(0).id());
            CompanyDeck ship = state.equals("quarters") ? companyShip(List::of,
                    () -> MarineOpsContext.companyMarines(roster).stream().limit(36).toList()) : null;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("command", viewModel.command().props());
            properties.put("contextLabel", "FLAGSHIP / PERSONNEL COMMAND");
            properties.put("squadRows", viewModel.squadRows());
            properties.put("musterRows", viewModel.musterRows());
            properties.put("recordCells", viewModel.recordCells());
            properties.put("selectedSquadName", viewModel.selectedSquadName());
            properties.put("selectedSquadSummary", viewModel.selectedSquadSummary());
            properties.put("quartersStatus", viewModel.quartersStatus());
            MarineOpsPageNav.put(properties, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP,
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
            loader.reload();
            try (MarkupInstance instance = loader.build(reactor, "shipboard-barracks", properties)) {
                UiDocument document = new UiDocument(instance.root());
                for (var style : instance.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                var guide = BarracksGuideCallout.bind(document, instance, viewModel.command(), viewModel.command()::showOverview);
                if (ship != null) document.canvases().set(instance.requireElement("barracks-canvas"),
                        new BarracksCanvas(ship, viewModel::sceneMarines));
                SpecSheetBinder hints = new SpecSheetBinder(document, SpecSheetLayer.install(document), true);
                BarracksCardBindings bindings = new BarracksCardBindings();
                bindings.update(document, instance, viewModel, hints);
                images.add(new SnapshotArtifact("barracks-" + state + "-1920x1080.png",
                        renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
                if (state.startsWith("dossier") || state.startsWith("guide") || state.equals("transfers")) {
                    images.add(new SnapshotArtifact("barracks-" + state + "-150pct.png",
                            renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1.5f)));
                }
                if (state.equals("dossier-trained")) {
                    var pane = instance.requireElement("officer-dossier-scroll");
                    pane.scrollTop(pane.box().maxScrollTop());
                    document.advance(0f);
                    images.add(new SnapshotArtifact("barracks-dossier-record-150pct.png",
                            renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1.5f)));
                }
                if (state.equals("large")) {
                    clickBarracksFlow(document, instance, "barracks-select-all");
                    clickBarracksFlow(document, instance, "barracks-page-next");
                    bindings.update(document, instance, viewModel, hints);
                    if (viewModel.command().selected().size() != 119 || viewModel.command().cards().size() != 12) {
                        throw new IllegalStateException("Paged card selection lost matching available squads");
                    }
                    images.add(new SnapshotArtifact("barracks-large-page-2.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
                }
                hints.clear();
            }
        }
        images.addAll(renderOfficerInduction(context, renderer));
        images.addAll(renderBarracksFlow(context, renderer));
        return images;
    }

    /** New-company clicks through the production spotlight, including scrolling to the third choice. */
    private static List<SnapshotArtifact> renderOfficerInduction(
            SnapshotContext context, HeadlessUiRenderer renderer) throws Exception {
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (float scale : new float[]{1f, 1.5f}) {
            Reactor reactor = new Reactor();
            MarineRoster roster = new MarineRoster();
            MarineCaptain mira = new MarineCaptain("Mira Hale", "graphics/portraits/mira-hale.png", Rank.LIEUTENANT, 0f);
            roster.add(mira);
            roster.beginCompanyPreparation(mira);
            frames.addAll(renderPreparationRooms(context, renderer, roster, scale));
            BarracksViewModel model = new BarracksViewModel(reactor, roster, () -> 1d, snapshotEquipmentResources(),
                    () -> 12, id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0));
            var command = model.command();
            boolean[] armoryRequested = {false};
            command.navigation(() -> armoryRequested[0] = true, id -> { }, () -> { }, true);
            command.returnToBriefing(true);
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("command", command.props());
            properties.put("contextLabel", "FLAGSHIP / FIRST COMMAND");
            properties.put("squadRows", model.squadRows());
            properties.put("musterRows", model.musterRows());
            properties.put("recordCells", model.recordCells());
            properties.put("selectedSquadName", model.selectedSquadName());
            properties.put("selectedSquadSummary", model.selectedSquadSummary());
            properties.put("quartersStatus", model.quartersStatus());
            MarineOpsPageNav.put(properties, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP,
                    () -> { }, () -> { }, command::showOverview, () -> { }, () -> { }, () -> { });
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
            loader.reload();
            try (MarkupInstance instance = loader.build(reactor, "shipboard-barracks", properties)) {
                UiDocument document = new UiDocument(instance.root());
                instance.styles().forEach(document::addStyleSheet);
                document.theme(MarineOpsThemes.standard());
                var guide = BarracksGuideCallout.bind(document, instance, command, command::showOverview);
                Consumer<String> capture = name -> frames.add(new SnapshotArtifact("induction-" + name + "-" + (int)(scale * 100) + "pct.png",
                        renderGuidedBarracks(renderer, document, guide, 1920, 1080, scale)));
                Consumer<String> click = id -> {
                    clickBarracksFlow(document, instance, id);
                    guide.update();
                };
                capture.accept("01-introduction");
                click.accept("command:" + mira.id());
                capture.accept("02-specialties");
                Rect first = instance.requireElement("officer-specialty-TACTICAL_COVER-train").box().borderBox();
                document.pointerScrolled(first.x() + 12, first.y() + 12, 420);
                guide.update();
                capture.accept("03-scroll-choice");
                click.accept("officer-specialty-SALVAGE_STEWARDSHIP-train");
                if (mira.career().level(OfficerSpecialty.SALVAGE_STEWARDSHIP) != 1) {
                    throw new IllegalStateException("Click-through specialty choice did not train Mira at scale " + scale);
                }
                capture.accept("04-formation");
                click.accept("officer-formations");
                click.accept("barracks-raise-squad");
                if (!armoryRequested[0]) throw new IllegalStateException("Raise Squad did not route to Armory");
                frames.addAll(renderPreparationArmory(context, renderer, roster, scale));
                model.refresh(); instance.flush(); document.advance(0f); guide.update();
                int clicks = 0;
                while (roster.officerInduction().step(roster) != Step.READY && clicks < 10) {
                    capture.accept("05-assignment-" + clicks);

                    click.accept(command.guideTargetId());
                    clicks++;
                }
                if (roster.officerInduction().step(roster) != Step.READY)
                    throw new IllegalStateException("Real assignment controls did not advance induction");
                capture.accept("06-ready");
                click.accept("guide-next");
                if (roster.officerInductionRequired() || command.guideVisible())
                    throw new IllegalStateException("Ready acknowledgement failed to unlock deployment");
                capture.accept("07-complete");
            }
        }
        return frames;
    }

    private static List<SnapshotArtifact> renderPreparationRooms(SnapshotContext context,
            HeadlessUiRenderer renderer, MarineRoster roster, float scale) throws Exception {
        List<SnapshotArtifact> frames = new ArrayList<>();
        Reactor reactor = new Reactor();
        var fleet = transferFleet(context);
        var candidate = fleet.get(0);
        var model = new ShipTransferViewModel(reactor, () -> fleet,
                () -> new CompanyShipDesignation.Home(roster.companyShipId(), null, false, 0),
                id -> roster.setCompanyShip(id, candidate.name(), candidate.cargo()), CompanyMeans.NONE);
        model.preparation(roster, snapshotMechResources());
        model.select(candidate.id());
        model.candidateRows().get();
        while (model.reading()) { model.advance(); Thread.onSpinWait(); }
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), SHIP_TRANSFER_COMPONENTS);
        loader.reload();
        var properties = props(model);
        properties.put("transferAction", (Runnable) model::commit);
        try (MarkupInstance instance = loader.build(reactor, "ship-transfer", properties)) {
            UiDocument document = PreparationGuide.document(instance.root());
            instance.styles().forEach(document::addStyleSheet); document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("transfer-plan"), new DeckPlanCanvas(model::selectedPlan));
            var guide = preparationGuide(context, document, () -> CompanyPreparationGuide.foundingLesson(null, null));
            frames.add(preparationFrame("00-required-conversion", renderer, document, guide, scale));
        }
        try (MarkupInstance instance = loader.build(reactor, "ship-transfer", properties)) {
            UiDocument document = PreparationGuide.document(instance.root());
            instance.styles().forEach(document::addStyleSheet); document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("transfer-plan"), new DeckPlanCanvas(model::selectedPlan));
            var guide = preparationGuide(context, document, () -> roster.companyShipId() == null
                    ? CompanyPreparationGuide.ship(roster, model, null) : null);
            frames.add(preparationFrame("01-ship", renderer, document, guide, scale));
            clickBarracksFlow(document, instance, "transfer-commit");
            if (roster.companyShipId() == null) throw new IllegalStateException("Preparation ship confirmation failed");
        }
        BoatDeck deck = roster.boatDeck();
        deck.reconcile(candidate.id(), ShipsBoats.carriedBy(candidate.ship().role()), ShipsBoats.aboard(candidate.ship()).size());
        CompanyDeck ship = new CompanyDeck(candidate.ship(), SHIP_SEED, null, List::of, List::of, () -> heldBerths(deck));
        readied(ship);
        var bay = ship.rooms(RoomPurpose.HANGAR).get(0);
        var boats = new BoatDeckViewModel(reactor, deck, snapshotMechResources(), candidate.name());
        loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BOAT_DECK_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, "boat-deck", props(boats, ship, bay, 1))) {
            UiDocument document = PreparationGuide.document(instance.root());
            instance.styles().forEach(document::addStyleSheet); document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("boat-deck-canvas"), new BoatDeckCanvas(ship, () -> bay,
                    boats::selectedBerthIndex, () -> 0, berth -> deck.boats().get(berth) != null));
            var guide = preparationGuide(context, document, () -> CompanyPreparationGuide.boats(roster, boats, () -> { }));
            frames.add(preparationFrame("02-berth", renderer, document, guide, scale));
            clickBarracksFlow(document, instance, "boat:0"); guide.update();
            frames.add(preparationFrame("03-commission", renderer, document, guide, scale));
            clickBarracksFlow(document, instance, "boat-fabricate"); guide.update();
            if (deck.airworthy().size() != 1) throw new IllegalStateException("Click-through commissioning failed");
            // Mirror the production host's occupancy-driven scene adoption, including the new boat.
            CompanyDeck commissioned = new CompanyDeck(candidate.ship(), SHIP_SEED, null,
                    List::of, List::of, () -> heldBerths(deck));
            readied(commissioned);
            commissioned.advance(1f / 30f);
            var commissionedBay = commissioned.rooms(RoomPurpose.HANGAR).get(0);
            document.canvases().set(instance.requireElement("boat-deck-canvas"), new BoatDeckCanvas(commissioned,
                    () -> commissionedBay, boats::selectedBerthIndex, () -> 0, berth -> deck.boats().get(berth) != null));
            frames.add(preparationFrame("04-lift-ready", renderer, document, guide, scale));
            commissioned.dismiss();
        }
        return frames;
    }

    private static List<SnapshotArtifact> renderPreparationArmory(SnapshotContext context,
            HeadlessUiRenderer renderer, MarineRoster roster, float scale) throws Exception {
        Reactor reactor = new Reactor();
        var model = new FleetArmoryViewModel(reactor, roster, () -> { }, () -> 1d,
                snapshotEquipmentResources(), snapshotSquadFoundingResources());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();
        List<SnapshotArtifact> frames = new ArrayList<>();
        try (MarkupInstance instance = loader.build(reactor, "fleet-armory", props(model))) {
            UiDocument document = PreparationGuide.document(instance.root());
            instance.styles().forEach(document::addStyleSheet); document.theme(MarineOpsThemes.standard());
            FleetArmoryScreen.bindGalleryCanvases(document, instance, model);
            var guide = preparationGuide(context, document, () -> CompanyPreparationGuide.armory(roster, () -> { }));
            frames.add(preparationFrame("05-found-squad", renderer, document, guide, scale));
            clickBarracksFlow(document, instance, "found-squad"); guide.update();
            if (roster.officerInduction().step(roster) != Step.ASSIGNMENT)
                throw new IllegalStateException("Click-through squad founding failed");
            frames.add(preparationFrame("06-return-to-mira", renderer, document, guide, scale));
        }
        return frames;
    }

    private static PreparationGuide preparationGuide(SnapshotContext context, UiDocument document,
            Supplier<PreparationGuide.Lesson> lesson) {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                List.of("data/ui/components/company/preparation-guide.mlx"));
        return new PreparationGuide(document, lesson, () -> { }, loader);
    }

    private static SnapshotArtifact preparationFrame(String name, HeadlessUiRenderer renderer,
            UiDocument document, PreparationGuide guide, float scale) {
        renderRelative(renderer, document, 1920, 1080, scale); guide.update();
        return new SnapshotArtifact("preparation-" + name + "-" + (int)(scale * 100) + "pct.png",
                renderRelative(renderer, document, 1920, 1080, scale));
    }

    /** One company, real pointer actions and personnel/cargo transactions at every frame. */
    private static List<SnapshotArtifact> renderBarracksFlow(
            SnapshotContext context, HeadlessUiRenderer renderer) throws Exception {
        BarracksFlowCompany company = new BarracksFlowCompany(snapshotCommodities());
        Reactor reactor = new Reactor();
        BarracksViewModel model = new BarracksViewModel(reactor, company.roster, () -> 100d,
                company, company::recruits, company::reinforce);
        CompanyDeck ship = companyShip(List::of, () -> MarineOpsContext.companyMarines(company.roster));
        Map<String, Object> properties = props(model, ship);
        properties.put("contextLabel", reactor.computed(() -> model.command().inspecting()
                ? ShipBreadcrumb.of(ship.ship(), ship.quartersFor(model.sceneMarines()))
                : ShipBreadcrumb.of(ship.ship(), null) + " / PERSONNEL COMMAND"));
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
        loader.reload();
        List<SnapshotArtifact> frames = new ArrayList<>();
        try (MarkupInstance instance = loader.build(reactor, "shipboard-barracks", properties)) {
            UiDocument document = new UiDocument(instance.root());
            instance.styles().forEach(document::addStyleSheet);
            document.theme(MarineOpsThemes.standard());
            var guide = BarracksGuideCallout.bind(document, instance, model.command(), model.command()::showOverview);
            document.canvases().set(instance.requireElement("barracks-canvas"), new BarracksCanvas(ship, model::sceneMarines));
            SpecSheetBinder hints = new SpecSheetBinder(document, SpecSheetLayer.install(document), true);
            BarracksCardBindings cardBindings = new BarracksCardBindings();
            cardBindings.update(document, instance, model, hints);
            Consumer<String> click = id -> {
                clickBarracksFlow(document, instance, id);
                cardBindings.update(document, instance, model, hints);
            };
            for (String id : List.of("barracks-recruit-stock", "barracks-recruit-draw", "barracks-reinforce")) {
                hints.bindLive(instance.requireElement(id), model.command()::recruitSheet);
            }
            renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f);
            click.accept("guide-dismiss");
            click.accept("command:" + company.captain.id());
            frames.add(new SnapshotArtifact("flow-officer-dossier.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            click.accept("officer-specialty-TACTICAL_COVER-train");
            if (company.captain.career().level(OfficerSpecialty.TACTICAL_COVER) != 1)
                throw new IllegalStateException("Officer training did not commit through the dossier");
            frames.add(new SnapshotArtifact("flow-officer-trained.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            click.accept("officer-formations");
            for (int i = 0; i < 2; i++) click.accept("formation:" + company.squads.get(i).id() + ":mark");
            frames.add(new SnapshotArtifact("flow-01-selected.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            openSpecSheet(document, hints, 1920, 1080, instance.requireElement("formation:" + company.squads.get(2).id()));
            frames.add(new SnapshotArtifact("barracks-card-hint.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            document.deactivateInput();
            hints.update();
            openSpecSheet(document, hints, 1920, 1080, instance.requireElement("barracks-recruit-stock"));
            frames.add(new SnapshotArtifact("flow-02-recruit-hint.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            document.deactivateInput();
            hints.update();
            click.accept("formation:" + company.squads.get(0).id() + ":inspect");
            frames.add(new SnapshotArtifact("muster-vacancies.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            openSpecSheet(document, hints, 1920, 1080, instance.requireElement("barracks-billet:11:vacancy"));
            frames.add(new SnapshotArtifact("muster-vacancy-hint.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            document.deactivateInput();
            hints.update();
            click.accept("barracks-back-command");
            click.accept("barracks-organization");
            click.accept("barracks-new-profile");
            frames.add(new SnapshotArtifact("flow-03-profile-draft.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            click.accept("barracks-save-profile");
            var originalCargo = company.available();
            click.accept("barracks-assign-profile");
            String profileId = company.roster.armory().squadProfiles().get(0).id();
            if (company.recruits() != 20 || !company.available().equals(originalCargo)
                    || company.squads.subList(0, 2).stream().anyMatch(s -> !profileId.equals(s.squadProfileId()))) {
                throw new IllegalStateException("Profile assignment failed or spent recruits");
            }
            frames.add(new SnapshotArtifact("flow-04-profile-assigned.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            click.accept("barracks-preparation");
            click.accept("barracks-reinforce");
            if (company.recruits() != 17 || company.roster.readyReserveCount() != 0
                    || company.squads.subList(0, 2).stream().anyMatch(s -> company.roster.readyCount(s) != 12)
                    || company.roster.readyCount(company.squads.get(2)) != 11) {
                throw new IllegalStateException("Replenishment did not transfer one reserve then enlist three recruits");
            }
            frames.add(new SnapshotArtifact("flow-05-replenished.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            var equipment = new BarracksEquipmentIssue(company.roster, company);
            var bill = equipment.preview(model.command().selected()).cost();
            click.accept("barracks-issue");
            if (bill.isZero() || !company.available().plus(bill).equals(originalCargo)
                    || !equipment.preview(model.command().selected()).cost().isZero() || company.recruits() != 17) {
                throw new IllegalStateException("Equipment issue did not pay its separate combined bill");
            }
            frames.add(new SnapshotArtifact("flow-06-equipped.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            click.accept("formation:" + company.squads.get(0).id() + ":inspect");
            if (!model.command().inspecting()) throw new IllegalStateException("Squad drill-down did not open");
            frames.add(new SnapshotArtifact("flow-07-squad-quarters.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            for (var marine : model.musterRows().get()) {
                Rect card = instance.requireElement(marine.id()).box().borderBox();
                Rect tray = instance.requireElement("barracks-muster-list").box().contentBox();
                if (card.x() < tray.x() || card.y() < tray.y() || card.right() > tray.right() || card.bottom() > tray.bottom()) {
                    throw new IllegalStateException("Muster card requires scrolling at 1920x1080: " + marine.id());
                }
                for (var item : marine.items()) {
                    Rect slot = instance.requireElement(item.id()).box().borderBox();
                    if (slot.x() < card.x() || slot.y() < card.y() || slot.right() > card.right() || slot.bottom() > card.bottom()) {
                        throw new IllegalStateException("Muster equipment clips its card: " + item.id());
                    }
                }
            }
            document.deactivateInput();
            UiElement focusSlot = instance.requireElement("barracks-billet:0:armor");
            document.requestFocus(focusSlot, true);
            hints.update();
            if (hints.openTarget() != focusSlot) throw new IllegalStateException("Muster equipment keyboard hint missing");
            document.deactivateInput();
            hints.update();
            for (String slot : List.of("armor", "primary", "system")) {
                openSpecSheet(document, hints, 1920, 1080, instance.requireElement("barracks-billet:0:" + slot));
                frames.add(new SnapshotArtifact("muster-" + slot + "-hint.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
                document.deactivateInput();
                hints.update();
            }
            click.accept("barracks-back-command");
            if (model.command().inspecting() || model.command().selected().size() != 2) {
                throw new IllegalStateException("Return lost the command selection");
            }
            click.accept("formation:" + company.squads.get(2).id() + ":inspect");
            frames.add(new SnapshotArtifact("muster-recovery.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            openSpecSheet(document, hints, 1920, 1080, instance.requireElement("barracks-billet:0"));
            frames.add(new SnapshotArtifact("muster-recovery-hint.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            hints.clear();
        }
        return frames;
    }

    private static BufferedImage renderGuidedBarracks(HeadlessUiRenderer renderer, UiDocument document,
                                                        BarracksGuideCallout guide, int width, int height, float scale) {
        renderRelative(renderer, document, width, height, scale);
        guide.update();
        return renderRelative(renderer, document, width, height, scale);
    }

    private static void clickBarracksFlow(UiDocument document, MarkupInstance instance, String id) {
        Rect box = instance.requireElement(id).box().borderBox();
        if (box.width() <= 0 || box.height() <= 0 || box.y() < 0 || box.bottom() > 1080) {
            throw new IllegalStateException("Barracks control outside viewport: " + id);
        }
        float x = box.x() + box.width() / 2f, y = box.y() + box.height() / 2f;
        document.pointerMoved(x, y);
        if (!document.pointerDown(x, y) || !document.pointerUp(x, y)) {
            throw new IllegalStateException("Barracks control did not consume click: " + id);
        }
        instance.flush();
        // Let the production selection/disabled transitions reach the new state.
        document.advance(0.25f);
    }

    private static BufferedImage renderBarracks(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY * 3);
        MarineSquad squad = roster.squads().stream()
                .filter(candidate -> !candidate.reserve())
                .findFirst().orElseThrow();
        Map<String, MarineSoldierStatus> postBattle = new LinkedHashMap<>();
        postBattle.put(squad.memberIds().get(3), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(postBattle, 100f, 1.25f);
        BarracksViewModel viewModel = new BarracksViewModel(reactor, roster, () -> 100d);
        CompanyDeck ship = companyShip(List::of,
                () -> MarineOpsContext.companyMarines(roster));
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "shipboard-barracks", props(viewModel, ship))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("barracks-canvas"),
                    new BarracksCanvas(ship, viewModel::sceneMarines));
            SpecSheetBinder hints = new SpecSheetBinder(document, SpecSheetLayer.install(document), true);
            new BarracksCardBindings().update(document, instance, viewModel, hints);
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    /**
     * The whole ship, at the framing the page opens on.
     *
     * <p>Fully zoomed out, because that is the state the player is handed and
     * the one worth checking: whether a generated hull reads as a vessel at the
     * scale a screen can show her at all.
     */
    private static BufferedImage renderShipView(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY * 3);
        // A real hull, because the point of the backdrop is that the deck and
        // the art come out of the same .ship file. A synthetic fixture would
        // prove only that a picture can be drawn behind a plan.
        VanillaHullSilhouettes vanilla =
                new VanillaHullSilhouettes(context.starsectorCore());
        VanillaHullSilhouettes.Hull hull =
                vanilla.available() ? vanilla.read(SHIP_VIEW_HULL) : null;
        CompanyDeck ship = hull == null
                ? companyShip(List::of, () -> MarineOpsContext.companyMarines(roster))
                : new CompanyDeck(new CompanyShip(hull.hullClass(), hull.role(),
                        hull.minCrew(), hull.maxCrew(), hull.cargo(),
                        hull.silhouette(), hull.spriteName()),
                        SHIP_SEED, null, List::of,
                        () -> MarineOpsContext.companyMarines(roster), null);
        // The fallback ship came ready and advanced out of companyShip; this
        // one was built here and has to be waited for here.
        if (hull != null) {
            readied(ship);
            ship.advance(18f);
        }
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), SHIP_VIEW_COMPONENTS);
        loader.reload();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("survey", ShipConditionModel.workshopOnly());
        props.put("equipmentAction", (Runnable) () -> { });
        props.put("workshop", WorkshopRestorationModel.Row.hidden());
        props.put("roomTitle", "COMPANY SHIP  //  UNDERWAY");
        props.put("roomCopy",
                "Drag to look around her. Wheel to close in, WASD to walk the view.");
        props.put("contextLabel", "COMPANY SHIP / UNDERWAY");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        try (MarkupInstance instance = loader.build(reactor, "ship-view", props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("ship-view-deck"),
                    new ShipViewCanvas(ship));
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    /** Organization evidence uses the production draft and ordinary roster authority. */
    private static BufferedImage renderFleetArmoryOrganization(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, int squadCount, boolean overCapacity,
            boolean unassigned) throws Exception {
        return renderFleetArmoryOrganization(context, renderer, width, height,
                squadCount, overCapacity, unassigned, 1f);
    }

    private static BufferedImage renderFleetArmoryOrganization(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, int squadCount, boolean overCapacity,
            boolean unassigned, float uiScale) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(squadCount * MarineSquad.CAPACITY);
        MarineCaptain captain = new MarineCaptain("Tamsin Vale",
                "graphics/portraits/portrait_hegemony06.png", Rank.CAPTAIN, 0f);
        MarineCaptain lieutenant = new MarineCaptain("Rafael Kade",
                "graphics/portraits/portrait_mercenary04.png", Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.add(lieutenant);
        if (squadCount > 6) {
            List<String> names = List.of("Mara Soren", "Inez Ward", "Jonas Reed",
                    "Elian Voss", "Nadia Orlov", "Kellan Price");
            List<String> portraits = List.of("portrait_mercenary05.png", "portrait_hegemony12.png",
                    "portrait_pirate06.png", "portrait_pirate12.png", "portrait_hegemony13.png",
                    "portrait_hegemony07.png");
            for (int index = 0; index < names.size(); index++) {
                roster.add(new MarineCaptain(names.get(index),
                        "graphics/portraits/" + portraits.get(index), Rank.LIEUTENANT, 0f));
            }
        }
        List<MarineSquad> squads = roster.squads();
        Map<String, MarineSoldierStatus> casualties = new LinkedHashMap<>();
        casualties.put(squads.get(2).memberIds().get(0), MarineSoldierStatus.WIA);
        casualties.put(squads.get(2).memberIds().get(1), MarineSoldierStatus.WIA);
        casualties.put(squads.get(2).memberIds().get(11), MarineSoldierStatus.KIA);
        casualties.put(squads.get(3).memberIds().get(0), MarineSoldierStatus.WIA);
        casualties.put(squads.get(4).memberIds().get(0), MarineSoldierStatus.WIA);
        casualties.put(squads.get(4).memberIds().get(10), MarineSoldierStatus.MIA);
        casualties.put(squads.get(4).memberIds().get(11), MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(casualties, 100f, 1.25f);
        roster.assignCaptainToSquad(captain.id(), squads.get(0).id());
        roster.assignCaptainToSquad(captain.id(), squads.get(1).id());
        roster.assignCaptainToSquad(lieutenant.id(), squads.get(2).id());
        roster.assignCaptainToSquad(lieutenant.id(), squads.get(3).id());
        if (!roster.bindStationing(42L, captain.id(), List.of(squads.get(5).id()))) {
            throw new IllegalStateException("Organization snapshot needs one stationed squad");
        }
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                reactor, roster, () -> { }, () -> 100d, snapshotEquipmentResources(),
                snapshotSquadFoundingResources());
        viewModel.beginOrganization();
        viewModel.toggleOrganizationSquad(squads.get(0).id());
        viewModel.toggleOrganizationSquad(squads.get(2).id());
        viewModel.toggleOrganizationSquad(squads.get(4).id());
        viewModel.selectOrganizationTarget(unassigned ? null
                : overCapacity ? lieutenant.id() : captain.id());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            FleetArmoryScreen.bindGalleryCanvases(document, instance, viewModel);
            return renderRelative(renderer, document, width, height, uiScale);
        }
    }

    private static BufferedImage renderEmptyFleetArmoryWorkspace(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        Reactor reactor = new Reactor();
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                reactor, new MarineRoster(), () -> { }, () -> 0d,
                snapshotEquipmentResources(), snapshotSquadFoundingResources());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    private static BufferedImage renderFleetArmoryWorkspace(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean fireteam, boolean pickerOpen,
            boolean armorPicker) throws Exception {
        return renderFleetArmoryWorkspace(
                context, renderer, width, height, fireteam, pickerOpen,
                armorPicker, false);
    }

    private static BufferedImage renderFleetArmoryWorkspace(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean fireteam, boolean pickerOpen,
            boolean armorPicker, boolean equipmentTooltip) throws Exception {
        return renderFleetArmoryWorkspace(context, renderer, width, height,
                fireteam, pickerOpen, armorPicker, equipmentTooltip, false);
    }

    /**
     * @param loadoutSpecSheet hover the first entry of the first loadout card's
     *                         issue line, so the picture shows a catalog item
     *                         asked about from the browser's own column — the
     *                         one nearest the right edge, where the layer has to
     *                         flip to the left of its subject to stay inside the
     *                         frame
     */
    private static BufferedImage renderFleetArmoryWorkspace(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean fireteam, boolean pickerOpen,
            boolean armorPicker, boolean equipmentTooltip,
            boolean loadoutSpecSheet) throws Exception {
        return renderFleetArmoryWorkspace(context, renderer, width, height,
                fireteam, pickerOpen, armorPicker, equipmentTooltip, loadoutSpecSheet, 1f);
    }

    private static BufferedImage renderFleetArmoryWorkspace(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean fireteam, boolean pickerOpen,
            boolean armorPicker, boolean equipmentTooltip,
            boolean loadoutSpecSheet, float uiScale) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        if (armorPicker) collectArmorTemplates(roster);
        if (pickerOpen) {
            var weapons = SquadEquipmentDoctrines.weaponById(SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS);
            for (var issue : weapons.issues()) {
                roster.armory().acquireEquipmentTemplate(
                        EquipmentTemplateCatalog.primaryId(issue.primaryId(), issue.grade()));
                if (issue.specialEquipmentId() != null) roster.armory().acquireEquipmentTemplate(
                        EquipmentTemplateCatalog.specialId(issue.specialEquipmentId()));
            }
        }
        MarineSquad squad = roster.squads().get(0);
        if (!pickerOpen) {
            Map<String, MarineSoldierStatus> postBattle = new LinkedHashMap<>();
            postBattle.put(squad.memberIds().get(0), MarineSoldierStatus.WIA);
            postBattle.put(squad.memberIds().get(MarineSquad.CAPACITY - 1),
                    MarineSoldierStatus.KIA);
            roster.applySoldierOutcome(postBattle, 100f, 1.25f);
            roster.recruitToSquad(roster.reserveSquad().id());
        }
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                reactor, roster, () -> { }, () -> 100d, snapshotEquipmentResources(),
                snapshotSquadFoundingResources());
        if (fireteam && pickerOpen) viewModel.weaponDoctrineTiles().get().stream()
                .filter(tile -> tile.id().equals("weapon-doctrine:" + SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS))
                .findFirst().orElseThrow().select().run();
        if (fireteam && armorPicker) {
            viewModel.showArmorPickerAction().run();
            viewModel.armorDoctrineTiles().get().get(0).select().run();
        }
        HeadlessArmoryPreviewRenderer armoryPreview =
                new HeadlessArmoryPreviewRenderer(context.modRoot());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, fireteam ? "fleet-armory-fireteam" : "fleet-armory",
                props(viewModel))) {
            if (fireteam) {
                instance.requireElement("transaction-feedback")
                        .align(UiAlign.STRETCH, UiAlign.CENTER);
            }
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            if (!fireteam) FleetArmoryScreen.bindGalleryCanvases(document, instance, viewModel);
            if (equipmentTooltip) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                ArmorySpecSheets.bindInspection(binder, instance, viewModel.inspection());
                openSpecSheet(document, binder, width, height,
                        instance.requireElement(
                                "loadout-item:1:current"));
            }
            if (loadoutSpecSheet) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                ArmorySpecSheets.bindDoctrineTiles(
                        binder, instance, viewModel.weaponDoctrineTiles());
                openSpecSheet(document, binder, width, height,
                        instance.requireElement(ArmorySpecSheets.firstSpecSheetAnchorId(
                                viewModel.weaponDoctrineTiles().get())));
            }
            return renderRelative(renderer, document, width, height, uiScale);
        }
    }

    private static BufferedImage renderFleetArmoryOverview(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY * 2);
        Map<String, MarineSoldierStatus> postBattle = new LinkedHashMap<>();
        postBattle.put(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(postBattle, 100f, 1.25f);
        FleetArmoryOverviewViewModel viewModel = new FleetArmoryOverviewViewModel(
                reactor, roster, () -> { }, () -> 100d);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), OVERVIEW_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory-overview", props(viewModel))) {
            instance.requireElement("company-overview-summary")
                    .align(UiAlign.STRETCH, UiAlign.CENTER);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    private static BufferedImage renderEquipmentDesigner(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        return renderEquipmentDesigner(context, renderer, width, height, false);
    }

    /**
     * @param specSheet hover the first billet's primary, so the picture shows
     *                  the weapon being authored described in full
     */
    private static BufferedImage renderEquipmentDesigner(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean specSheet) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        FleetArmoryViewModel armory = new FleetArmoryViewModel(reactor, roster);
        EquipmentDoctrineDesignerViewModel designer = new EquipmentDoctrineDesignerViewModel(
                reactor, roster, armory.selectedSquadId(),
                armory.selectedWeaponDoctrineId());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory-doctrine-designer", designerProps(designer))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            HeadlessArmoryPreviewRenderer armoryPreview =
                    new HeadlessArmoryPreviewRenderer(context.modRoot());
            for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
                int billet = index;
                document.canvases().set(instance.requireElement(
                                "designer-marine-preview:" + index),
                        new ArmoryMarinePreviewCanvas(
                                () -> designer.viewerBilletAt(billet), armoryPreview.assets()));
            }
            if (specSheet) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                ArmorySpecSheets.bindBilletCards(binder, instance, designer.billets());
                openSpecSheet(document, binder, width, height, instance.requireElement(
                        designer.billets().get().get(0).primaryId()));
            }
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    private static BufferedImage renderArmorComparison(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        return renderArmorComparison(context, renderer, width, height, false);
    }

    /**
     * @param scrolledToBattlesuits when true, scrolls past the light/line
     *                              patterns to the tier-IV battlesuits, whose
     *                              descriptions and integral-system lines are
     *                              the longest text this surface renders — the
     *                              case most likely to clip.
     */
    private static BufferedImage renderArmorComparison(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean scrolledToBattlesuits) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        collectArmorTemplates(roster);
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(reactor, roster);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "armory-armor-comparison", armorComparisonProps(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            if (scrolledToBattlesuits) {
                instance.requireElement("comparison-list").scrollTop(100_000f);
            }
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    /** A collected Armory fixture, distinct from the campaign's starter ownership. */
    private static void collectArmorTemplates(MarineRoster roster) {
        for (var armor : MarineArmorCatalogRegistry.installed().all()) {
            roster.armory().acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(armor.id()));
        }
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale) throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale, false, false);
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen) throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale, pickerOpen, false);
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen,
            boolean selectHound) throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale,
                pickerOpen, selectHound, false);
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen,
            boolean selectHound, boolean selectVacant) throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale,
                pickerOpen, selectHound, selectVacant, false);
    }

    /**
     * @param specSheet hover the first catalog row's name, so the picture shows
     *                  what that chassis or piece of hardware actually is
     */
    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen,
            boolean selectHound, boolean selectVacant, boolean specSheet)
            throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale, pickerOpen,
                selectHound, selectVacant, specSheet, null);
    }

    static List<SnapshotArtifact> renderCampaignGantries(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        var frames = new ArrayList<SnapshotArtifact>();
        for (int step = 1; step <= 4; step++) frames.add(new SnapshotArtifact("gantry-" + step + ".png",
                renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "lesson-" + step)));
        return frames;
    }

    static List<SnapshotArtifact> renderMechConsole(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        List<BufferedImage> frames = new ArrayList<>();
        BufferedImage idle = renderMechLab(context, renderer, 1920, 1080, 1f,
                false, true, false, false, frames);
        List<BufferedImage> bulwark = new ArrayList<>();
        BufferedImage heavy = renderMechLab(context, renderer, 1920, 1080, 1f,
                false, false, false, false, bulwark);
        return List.of(
                new SnapshotArtifact("castle-lab-workspace.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "lesson-5")),
                new SnapshotArtifact("castle-lab-introduction.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "lesson-1")),
                new SnapshotArtifact("castle-lab-fit.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "lesson-2")),
                new SnapshotArtifact("castle-lab-swap.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "lesson-3")),
                new SnapshotArtifact("castle-lab-ready.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "lesson-4")),
                new SnapshotArtifact("mech-welding-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f,
                                false, false, false, false, null, null, true)),
                new SnapshotArtifact("mech-overview-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f)),
                new SnapshotArtifact("mech-picker-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, true)),
                new SnapshotArtifact("mech-vacant-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, true)),
                new SnapshotArtifact("mech-material-cost-hint-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, true, false, false, null, "cost")),
                new SnapshotArtifact("mech-material-stock-hint-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, true, false, false, null, "stock")),
                new SnapshotArtifact("mech-disabled-action-hint-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "action")),
                new SnapshotArtifact("mech-replenisher-hint-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "replenisher")),
                new SnapshotArtifact("mech-no-spare-hint-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "no-spare")),
                new SnapshotArtifact("mech-long-callsign-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, true, false, false, false, null, "long-name")),
                new SnapshotArtifact("mech-fitting-bulwark-1920x1080.png", heavy),
                new SnapshotArtifact("mech-fitting-1920x1080.png", idle),
                new SnapshotArtifact("mech-fitting-valid-drop-1920x1080.png", frames.get(18)),
                new SnapshotArtifact("mech-fitting-rejected-drop-1920x1080.png", frames.get(34)),
                SnapshotArtifact.colorAnimation("mech-fitting-1920x1080.gif", frames, 100));
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen,
            boolean selectHound, boolean selectVacant, boolean specSheet,
            List<BufferedImage> motionFrames) throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale, pickerOpen,
                selectHound, selectVacant, specSheet, motionFrames, null);
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen,
            boolean selectHound, boolean selectVacant, boolean specSheet,
            List<BufferedImage> motionFrames, String materialHint) throws Exception {
        return renderMechLab(context, renderer, width, height, uiScale, pickerOpen,
                selectHound, selectVacant, specSheet, motionFrames, materialHint, false);
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen,
            boolean selectHound, boolean selectVacant, boolean specSheet,
            List<BufferedImage> motionFrames, String materialHint, boolean weldingEvidence) throws Exception {
        double[] seconds = {0};
        Reactor reactor = new Reactor();
        MechBay bay = MechBay.legacyStarterFixture();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id()));
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_03", "Sirocco 03", MechVariant.SIROCCO,
                MechRole.LR_SUPPORT, MissileReplenisherComponent.ACCELERATED_FEED.id()));
        bay.addReplenisher(MissileReplenisherComponent.ACCELERATED_FEED.id(), 1);
        if ("no-spare".equals(materialHint)) bay.loseSpares();
        if ("long-name".equals(materialHint)) {
            if (!bay.mechById(MechBay.STARTER_MECH_ID)
                    .rename("Ironclad of the Persean Expeditionary Vanguard 1")) {
                throw new IllegalStateException("Maximum-length callsign fixture was rejected");
            }
        }
        if (materialHint != null && materialHint.startsWith("lesson-")) {
            bay = new MechBay();
            bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
            bay.beginLabLesson();
        }
        MechLabViewModel viewModel = new MechLabViewModel(
                reactor, bay, snapshotMechResources());
        if (materialHint != null && materialHint.startsWith("lesson-")) {
            int step = Integer.parseInt(materialHint.substring(7));
            if (step >= 2) viewModel.guideLabLesson();
            if (step >= 3) viewModel.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER);
            if (step >= 4) {
                viewModel.stripWeapon(SocketId.LEFT_SHOULDER);
                viewModel.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER);
            }
        }
        if ("lesson-5".equals(materialHint)) viewModel.dismissLabPrompt();
        if (selectHound) {
            viewModel.mechRows().get().stream()
                    .filter(row -> row.name().startsWith("Hound"))
                    .findFirst().orElseThrow().select().run();
            viewModel.slotRows().get().stream()
                    .filter(row -> row.name().contains("SHOULDER"))
                    .findFirst().orElseThrow().select().run();
        }
        if (motionFrames != null) {
            if (!selectHound) viewModel.gantryRows().get().get(0).select().run();
            else viewModel.stripWeapon(viewModel.selectedSocket());
        }
        if ("action".equals(materialHint) || "replenisher".equals(materialHint)
                || "no-spare".equals(materialHint)) {
            viewModel.gantryRows().get().get(0).select().run();
        }
        if (weldingEvidence) viewModel.gantryRows().get().get(0).select().run();
        if (selectVacant) viewModel.previousGantryAction().run();
        if (pickerOpen) viewModel.openAssetPickerAction().run();
        // The lab is photographed aboard the same ship it is in game. There is
        // no substitute garage to photograph instead, and a snapshot of one
        // would be evidence about a room the player never sees.
        CompanyDeck ship = companyShip(viewModel::gantryVariants, List::of);
        DeckGraph.Compartment vehicleBay = ship.room(RoomPurpose.VEHICLE_BAY);
        if (weldingEvidence) seekMachineService(ship, vehicleBay, viewModel.selectedGantryIndex());
        ShipDeckBattleScene.RoomView framing =
                ShipDeckBattleScene.RoomView.of(vehicleBay, MECH_LAB_SURROUND_CELLS);
        MechLabCameraController camera = new MechLabCameraController(
                MechLabCameraController.on(framing, ship.scene().berthsIn(vehicleBay)));
        camera.snap(viewModel.fittingFocused(), viewModel.selectedGantryIndex(),
                viewModel.gantryVariants().size());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), MECH_LAB_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "mech-lab", props(viewModel, ship))) {
            UiDocument document = PreparationGuide.document(instance.root());
            var guide = new PreparationGuide(document, viewModel::campaignLesson, () -> {},
                    new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                            List.of("data/ui/components/company/preparation-guide.mlx")));
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            MechLabDollCanvas fittingCanvas = new MechLabDollCanvas(viewModel::gantryDeployments,
                            viewModel::selectedGantryIndex,
                            viewModel::selectedSocket,
                            MechLabDollCanvas::headlessAssets,
                            () -> null, () -> null,
                            viewModel::fittingFocused,
                            ship::scene,
                            () -> framing.lookingAt(camera.pose().worldX(),
                                    camera.pose().worldY(), camera.pose().zoomNotches()),
                            () -> ship.scene().berthsIn(vehicleBay), vehicleBay::id,
                            () -> seconds[0],
                            viewModel::hoveredWeapon);
            document.canvases().set(instance.requireElement("mech-doll-canvas"), fittingCanvas);
            for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
                if (row.chassisPreview() != null) {
                    document.canvases().set(instance.requireElement(row.previewId()),
                            new MechChassisPreviewCanvas(
                                    row.chassisPreview(), MechLabDollCanvas::headlessAssets));
                } else if (row.weaponPreview() != null || row.replenisherPreview() != null) {
                    document.canvases().set(instance.requireElement(row.previewId()),
                            MechEquipmentGridCanvas.catalog(row.weaponPreview(),
                                    row.replenisherPreview(),
                                    MechLabDollCanvas::headlessAssets));
                }
            }
            for (MechLabViewModel.SlotRow row : viewModel.slotRows().get()) {
                document.canvases().set(instance.requireElement(row.gridId()),
                        new MechEquipmentGridCanvas(
                                () -> viewModel.socketDefinition(row.socketId()),
                                () -> viewModel.installedWeapon(row.socketId()),
                                () -> viewModel.installedReplenisher(row.socketId()),
                                () -> viewModel.socketOccupied(row.socketId()),
                                MechLabDollCanvas::headlessAssets));
            }
            // The guide mounts a pointer-active callout before its first update.
            // Initialize its real lesson state before photographing a catalog hover.
            guide.update();
            if (specSheet || materialHint != null && !"long-name".equals(materialHint)
                    && !materialHint.startsWith("lesson-")) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                List<MechLabViewModel.CatalogRow> rows = viewModel.catalogRows().get();
                MechLabSpecSheets.bindCatalogRows(binder, instance, rows);
                MechLabSpecSheets.bindMaterials(binder, instance, viewModel);
                MechLabSpecSheets.bindActions(binder, instance, viewModel);
                String anchor = "no-spare".equals(materialHint) ? rows.get(1).actionId()
                        : "action".equals(materialHint) ? rows.get(0).actionId()
                        : "replenisher".equals(materialHint) ? rows.get(1).nameId()
                        : "stock".equals(materialHint) ? viewModel.materialStock().get().get(0).id()
                        : "cost".equals(materialHint) ? rows.stream().flatMap(row -> row.materials().stream())
                        .findFirst().orElseThrow().id() : MechLabSpecSheets.firstSpecSheetAnchorId(rows);
                openSpecSheet(document, binder, width, height, instance.requireElement(anchor));
            }
            if (motionFrames != null) {
                MechWeaponDragCanvas ghost = new MechWeaponDragCanvas(MechLabDollCanvas::headlessAssets);
                UiElement ghostElement = instance.requireElement("mech-drag-ghost");
                document.canvases().set(ghostElement, ghost);
                for (int frame = 0; frame < (selectHound ? 44 : 1); frame++) {
                    seconds[0] = frame * .1;
                    if (frame >= 12 && frame < 28) {
                        fittingCanvas.previewWeaponDrag(MechWeaponComponent.SRM_5, null,
                                viewModel.selectedSocket(), viewModel.canDropCatalogWeapon(
                                        MechWeaponComponent.SRM_5, viewModel.selectedSocket()));
                    } else if (frame >= 28 && frame < 40) {
                        fittingCanvas.previewWeaponDrag(MechWeaponComponent.SINGLE_HEAVY_CANNON,
                                null, viewModel.selectedSocket(), false);
                    } else fittingCanvas.clearWeaponDragPreview();
                    // A controlled carried-item pose; the well state above is a preview, not a campaign commit.
                    if (frame >= 12 && frame < 40) {
                        var box = instance.requireElement("mech-doll-canvas").box().contentBox();
                        ghost.show(frame < 28 ? MechWeaponComponent.SRM_5 : MechWeaponComponent.SINGLE_HEAVY_CANNON,
                                box.x() + box.width() * .82f, box.y() + box.height() * .25f);
                    } else ghost.clear();
                    document.canvases().invalidate(ghostElement);
                    document.canvases().invalidate(instance.requireElement("mech-doll-canvas"));
                    document.advance(.1f);
                    motionFrames.add(renderRelative(renderer, document, width, height, uiScale));
                }
                return motionFrames.get(Math.min(5, motionFrames.size() - 1));
            }
            renderRelative(renderer, document, width, height, uiScale);
            guide.update();
            return renderRelative(renderer, document, width, height, uiScale);
        }
    }

    /** Wait for a real servicing job on the framed machine; never pose a worker for the image. */
    private static void seekMachineService(CompanyDeck ship, DeckGraph.Compartment bay, int gantry) {
        Gantry berth = ship.scene().berthsIn(bay).get(gantry);
        String service = JobBoard.group(bay.id(), Affordance.SERVICE);
        for (int step = 0; step < 1800; step++) {
            var tasks = ship.scene().simulation().ambientTasks();
            for (long actor : tasks.assigned()) {
                String job = tasks.jobInHand(actor);
                var pose = tasks.pose(actor);
                if (job == null || !job.startsWith(service + "@") || pose == null
                        || pose.activity() != AmbientActivity.WORKING) continue;
                if (Math.hypot(pose.focusX() - berth.worldCenterX(),
                        pose.focusY() - berth.worldCenterY()) < 1.5) return;
            }
            ship.advance(.1f);
        }
        throw new IllegalStateException("No technician serviced the framed machine during the evidence window");
    }

    /**
     * The company's own boats, in the hangar they are actually kept in.
     *
     * <p>Photographed with two of them refitted, because a deck of six
     * yard-standard boats cannot show whether the room says what is fitted
     * where — every plate would read the same and be right by accident.
     */
    private static BufferedImage renderBoatDeck(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean openBoat) throws Exception {
        return renderBoatDeck(context, renderer, width, height, openBoat, false);
    }

    /**
     * @param strikeABoat photograph her a boat down: the third berth struck off
     *     the deck the way a shoot-down strikes it, opened on the fabrication
     *     pane, and against a hold that cannot pay for the replacement
     */
    private static BufferedImage renderBoatDeck(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean openBoat, boolean strikeABoat)
            throws Exception {
        return renderBoatDeck(context, renderer, width, height, openBoat, strikeABoat, false);
    }

    private static BufferedImage renderBoatDeck(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean openBoat, boolean strikeABoat, boolean departing)
            throws Exception {
        Reactor reactor = new Reactor();
        BoatDeck[] campaign = new BoatDeck[1];
        // The berth struck has to be one the camera is looking at, and the page
        // opens on her first hangar. Larger programs can have additional bays,
        // so derive a visible berth rather than assuming campaign numbering.
        int[] struck = {-1};
        CompanyDeck ship = companyShip(List::of, List::of, hull -> {
            campaign[0] = boatsAboard(hull);
            if (strikeABoat) {
                struck[0] = lastBerthInHerFirstHangar(hull);
                campaign[0].lose(List.of(campaign[0].boats().get(struck[0]).id()));
            }
            return heldBerths(campaign[0]);
        });
        // Her boats are put out by the bay on its first tick, and a ship whose
        // deck was still being laid out when the shutter opened has not had one
        // — so wait for her, then run her clock. Photographed before that, the
        // room is a hangar with six empty berths in it.
        ship.scene();
        ship.advance(18f);
        List<DeckGraph.Compartment> hangars = ship.rooms(RoomPurpose.HANGAR);
        BoatDeck deck = campaign[0];
        BoatWorkshop workshop = new BoatWorkshop(deck, snapshotMechResources());
        if (deck.berths() > 0 && deck.boats().get(0) != null) {
            workshop.fit(deck.boats().get(0).id(), BoatFitting.REINFORCED_PLATING.id());
        }
        if (deck.berths() > 1 && deck.boats().get(1) != null) {
            workshop.fit(deck.boats().get(1).id(), BoatFitting.TUNED_DRIVE.id());
        }
        // The hold is the room's rather than the yard's: the refits above are
        // already paid for, and what this photographs is a bill the company
        // cannot meet.
        FabricationResources resources = strikeABoat
                ? snapshotHoldShortOfMetals() : snapshotMechResources();
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                reactor, deck, resources, "Valkyrie");
        if (openBoat) viewModel.selectBoatAction(0).run();
        if (struck[0] >= 0) viewModel.selectBoatAction(struck[0]).run();
        DeckGraph.Compartment bay = hangars.isEmpty() ? null : hangars.get(0);
        if (departing) seekHangarDeparture(ship, bay);

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BOAT_DECK_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "boat-deck", props(viewModel, ship, bay, hangars.size()))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("boat-deck-canvas"),
                    new BoatDeckCanvas(ship, () -> bay,
                            viewModel::selectedBerthIndex, () -> 0,
                            berth -> berth < 0 || berth >= deck.berths()
                                    || deck.boats().get(berth) != null));
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    /** Photograph the actual managed departure at the opening, never a posed sprite. */
    private static void seekHangarDeparture(CompanyDeck ship, DeckGraph.Compartment bay) {
        BayAperture door = ship.scene().bayDoor(bay);
        if (door == null) throw new IllegalStateException("Snapshot hangar has no opening to space");
        for (int step = 0; step < 1800; step++) {
            ship.advance(0.1f);
            var sim = ship.scene().simulation();
            for (long id : sim.getAirEntityIds()) {
                var mission = sim.world().mission(id);
                AirBody body = sim.world().kinematics(id);
                if (mission != null && mission.state == ShuttleState.SHIP_DEPARTURE
                        && body.distanceTo(door.centerX(), door.centerY()) < 0.6f) return;
            }
        }
        throw new IllegalStateException("No shuttle crossed the hangar opening during the evidence window");
    }

    /**
     * Which of the deck's boat berths is the last one standing in her first
     * hangar, by the campaign deck's own numbering.
     *
     * <p>Read off her map rather than off her scene, because the answer is
     * wanted before she is crewed — what stands in her berths is settled while
     * she is being got ready.
     */
    private static int lastBerthInHerFirstHangar(CompanyDeck ship) {
        DeckGraph.Compartment hangar = ship.rooms(RoomPurpose.HANGAR).get(0);
        int berth = 0;
        int last = -1;
        for (Gantry gantry : ship.map().gantries) {
            if (gantry.holds != Gantry.Holds.BOAT) continue;
            if (hangar.contains(gantry.centerX, gantry.centerY)) last = berth;
            berth++;
        }
        return last;
    }

    /** The company's boats, in the berths this hull cut for them. */
    private static BoatDeck boatsAboard(CompanyDeck ship) {
        int berths = 0;
        for (Gantry berth : ship.map().gantries) {
            if (berth.holds == Gantry.Holds.BOAT) berths++;
        }
        BoatDeck deck = new BoatDeck();
        deck.reconcile("snapshot-ship", ShipsBoats.carriedBy(ship.ship().role()), berths);
        return deck;
    }

    /** @see BoatDeck#boats() */
    private static boolean[] heldBerths(BoatDeck deck) {
        List<CampaignBoat> berths = deck.boats();
        boolean[] held = new boolean[berths.size()];
        for (int berth = 0; berth < berths.size(); berth++) {
            held[berth] = berths.get(berth) != null;
        }
        return held;
    }

    /** A hold that cannot pay for an Aeroshuttle's 60 metals. */
    private static FabricationResources snapshotHoldShortOfMetals() {
        FabricationResources stocked = snapshotMechResources();
        return new FabricationResources() {
            @Override public int available(String commodityId) {
                return Commodities.METALS.equals(commodityId)
                        ? 41 : stocked.available(commodityId);
            }
            @Override public String commodityName(String commodityId) {
                return stocked.commodityName(commodityId);
            }
            @Override public String commodityIcon(String commodityId) {
                return stocked.commodityIcon(commodityId);
            }
            @Override public boolean spend(FabricationCost cost) {
                return canAfford(cost) && stocked.spend(cost);
            }
        };
    }

    private static FabricationResources snapshotMechResources() {
        return new FabricationResources() {
            @Override public int available(String commodityId) { return 2_000; }
            @Override public String commodityName(String commodityId) { return switch (commodityId) {
                case Commodities.SUPPLIES -> "Supplies";
                case Commodities.HEAVY_MACHINERY -> "Heavy Machinery";
                case Commodities.METALS -> "Metals";
                case Commodities.RARE_METALS -> "Transplutonics";
                default -> commodityId;
            }; }
            @Override public String commodityIcon(String commodityId) { return switch (commodityId) {
                case Commodities.SUPPLIES -> "graphics/icons/cargo/supplies.png";
                case Commodities.HEAVY_MACHINERY -> "graphics/icons/cargo/heavymachinery.png";
                case Commodities.METALS -> "graphics/icons/cargo/materials.png";
                case Commodities.RARE_METALS -> "graphics/icons/cargo/raremetals.png";
                default -> "";
            }; }
            @Override public boolean spend(FabricationCost cost) { return cost != null; }
        };
    }

    private static EquipmentIssueResources snapshotEquipmentResources() {
        return new EquipmentIssueResources() {
            private final EquipmentTemplateCost available =
                    new EquipmentTemplateCost(2_000, 2_000, 2_000, 2_000);

            @Override public EquipmentTemplateCost available() { return available; }
            @Override public boolean spend(EquipmentTemplateCost cost) { return cost != null; }
            @Override public String commodityName(String commodityId) {
                return snapshotCommodities().commodityName(commodityId);
            }
            @Override public String commodityIcon(String commodityId) {
                return snapshotCommodities().commodityIcon(commodityId);
            }
        };
    }

    private static SquadFoundingResources snapshotSquadFoundingResources() {
        return new SquadFoundingResources() {
            @Override public int available(String commodityId) { return 2_000; }
            @Override public boolean spend(SquadFoundingCost cost) { return cost != null; }
            @Override public String commodityName(String commodityId) {
                return snapshotCommodities().commodityName(commodityId);
            }
            @Override public String commodityIcon(String commodityId) {
                return snapshotCommodities().commodityIcon(commodityId);
            }
        };
    }

    private static CommodityPresentation snapshotCommodities() {
        return new CommodityPresentation() {
            @Override public String commodityName(String commodityId) {
                return switch (commodityId) {
                    case Commodities.SUPPLIES -> "Supplies";
                    case Commodities.HAND_WEAPONS -> "Heavy Armaments";
                    case Commodities.HEAVY_MACHINERY -> "Heavy Machinery";
                    case Commodities.FOOD -> "Food";
                    case Commodities.MARINES -> "Marines";
                    default -> CommodityPresentation.super.commodityName(commodityId);
                };
            }

            @Override public String commodityIcon(String commodityId) {
                return switch (commodityId) {
                    case Commodities.SUPPLIES -> "graphics/icons/cargo/supplies.png";
                    case Commodities.HAND_WEAPONS -> "graphics/icons/cargo/heavyweapons.png";
                    case Commodities.HEAVY_MACHINERY ->
                            "graphics/icons/cargo/heavymachinery.png";
                    case Commodities.FOOD -> "graphics/icons/cargo/food.png";
                    case Commodities.MARINES -> "graphics/icons/cargo/marine.png";
                    default -> "";
                };
            }
        };
    }

    private static BufferedImage renderRelative(HeadlessUiRenderer renderer,
                                                UiDocument document,
                                                int width, int height,
                                                float uiScale) {
        return renderer.renderRelative(document, width, height, uiScale,
                MarineOpsUiViewport.REFERENCE_WIDTH,
                MarineOpsUiViewport.REFERENCE_HEIGHT);
    }

    private static Map<String, Object> props(FleetArmoryOverviewViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fleetSummary", viewModel.fleetSummary());
        props.put("templateCollectionSummary", viewModel.templateCollectionSummary());
        props.put("accessStatusSummary", viewModel.accessStatusSummary());
        props.put("accessNextSummary", viewModel.accessNextSummary());
        props.put("companyCards", viewModel.companyCards());
        putArmoryPageNavigation(props);
        return props;
    }

    private static Map<String, Object> props(BarracksViewModel viewModel,
                                             CompanyDeck ship) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", ShipBreadcrumb.of(ship.ship(),
                ship.quartersFor(viewModel.sceneMarines())));
        props.put("command", viewModel.command().props());
        props.put("squadRows", viewModel.squadRows());
        props.put("musterRows", viewModel.musterRows());
        props.put("recordCells", viewModel.recordCells());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("selectedSquadSummary", viewModel.selectedSquadSummary());
        props.put("quartersStatus", viewModel.quartersStatus());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BARRACKS,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }

    private static Map<String, Object> props(FleetArmoryViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("firstRefitClasses", "hidden");
        props.put("openFirstRefit", (Runnable) () -> { });
        props.put("companySummary", viewModel.companySummary());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("squadCards", viewModel.squadCards());
        props.put("squadGalleryCards", viewModel.squadGalleryCards());
        props.put("organization", viewModel.organization());
        props.put("foundingCargoRows", viewModel.foundingCargoRows());
        props.put("cargoStockRows", viewModel.cargoStockRows());
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
        props.put("loadoutBrowserSummary", viewModel.loadoutBrowserSummary());
        props.put("showArmorComparison", (Runnable) () -> { });
        props.put("armorComparisonSummary", viewModel.armorComparisonSummary());
        props.put("armorComparisonCards", viewModel.armorComparisonCards());
        props.put("marineCards", viewModel.marineCards());
        props.put("inspection", viewModel.inspection());
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("issueCargoRows", viewModel.issueCargoRows());
        props.put("issueCargoClasses", viewModel.issueCargoClasses());
        props.put("applyDisabled", viewModel.applyDisabled());
        props.put("applyLabel", viewModel.applyLabel());
        props.put("apply", viewModel.applyAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("back", (Runnable) () -> { });
        props.put("backToSquads", (Runnable) () -> { });
        putArmoryPageNavigation(props);
        return props;
    }

    private static Map<String, Object> designerProps(
            EquipmentDoctrineDesignerViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("squadName", viewModel.squadName());
        props.put("designerHeading", viewModel.heading());
        props.put("designerSubheading", viewModel.subheading());
        props.put("draftName", viewModel.draftName());
        props.put("editName", viewModel.editName());
        props.put("definitions", viewModel.definitions());
        props.put("teamTabs", viewModel.teamTabs());
        props.put("billets", viewModel.billets());
        props.put("feedback", viewModel.feedback());
        props.put("newDraft", viewModel.newDraft());
        props.put("cloneSelected", viewModel.cloneSelected());
        props.put("saveAsNew", viewModel.saveAsNew());
        props.put("rename", viewModel.rename());
        props.put("renameDisabled", viewModel.renameDisabled());
        props.put("delete", viewModel.delete());
        props.put("deleteDisabled", viewModel.deleteDisabled());
        props.put("backToFireTeams", (Runnable) () -> { });
        props.put("back", (Runnable) () -> { });
        putArmoryPageNavigation(props);
        return props;
    }

    private static Map<String, Object> armorComparisonProps(FleetArmoryViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("armorComparisonSummary", viewModel.armorComparisonSummary());
        props.put("armorComparisonCards", viewModel.armorComparisonCards());
        props.put("backToFireTeams", (Runnable) () -> { });
        props.put("back", (Runnable) () -> { });
        putArmoryPageNavigation(props);
        return props;
    }

    private static void putArmoryPageNavigation(Map<String, Object> props) {
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
    }

    public static BufferedImage renderWorkshopRestoration(SnapshotContext context, CompanyDeck ship,
                                                          WorkshopRestorationModel.Row workshop, float scale) throws Exception {
        return renderWorkshopRestoration(context, ship, workshop, scale, true);
    }

    /** Matched framing for visual review, with optional workshop focus and no purchase-panel requirement. */
    public static BufferedImage renderWorkshopRestoration(SnapshotContext context, CompanyDeck ship,
                                                          WorkshopRestorationModel.Row workshop, float scale,
                                                          boolean focusWorkshop) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), SHIP_VIEW_COMPONENTS);
        loader.reload();
        Map<String, Object> props = new LinkedHashMap<>();
        var survey = ShipConditionModel.workshopOnly();
        if (workshop.offer().state() == WorkshopState.NONE) survey.put("classes", "hidden");
        props.put("survey", survey);
        props.put("equipmentAction", (Runnable) () -> { });
        props.put("workshop", workshop);
        props.put("roomTitle", "SECOND WIND / RESTORATION");
        props.put("roomCopy", "Inspect the workshop. Restoring her rooms keeps their place in the hull.");
        props.put("contextLabel", "COMPANY SHIP / MACHINE SHOP");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW, MarineOpsPageNav.ANY_SHIP,
                () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
        try (var instance = loader.build(new Reactor(), "ship-view", props)) {
            var document = new UiDocument(instance.root());
            instance.styles().forEach(document::addStyleSheet);
            document.theme(MarineOpsThemes.standard());
            var canvas = new ShipViewCanvas(ship);
            if (focusWorkshop) canvas.focus(RoomPurpose.PARTS_CAGE);
            document.canvases().set(instance.requireElement("ship-view-deck"), canvas);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            return renderRelative(renderer, document, 1920, 1080, scale);
        }
    }

    public static BufferedImage renderShipCondition(SnapshotContext context, CompanyDeck ship,
                                                    ShipConditionModel model, Reactor reactor,
                                                    float scale, RoomPurpose focus) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), SHIP_VIEW_COMPONENTS);
        loader.reload();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("survey", model.row());
        props.put("equipmentAction", (Runnable) () -> { });
        props.put("workshop", WorkshopRestorationModel.Row.hidden());
        props.put("roomTitle", "SECOND WIND / ENGINEERING");
        props.put("roomCopy", "Select a room to inspect. Right-drag to pan; wheel to zoom.");
        props.put("contextLabel", "SHIP SERVICES");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW,
                purpose -> ship.has(purpose) ? MarineOpsPageNav.Aboard.YES : MarineOpsPageNav.Aboard.NO,
                () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
        try (var instance = loader.build(reactor, "ship-view", props)) {
            var document = new UiDocument(instance.root());
            instance.styles().forEach(document::addStyleSheet);
            document.theme(MarineOpsThemes.standard());
            var canvas = new ShipViewCanvas(ship);
            if (focus != null) canvas.focus(focus);
            document.canvases().set(instance.requireElement("ship-view-deck"), canvas);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            return renderRelative(renderer, document, 1920, 1080, scale);
        }
    }

    public static BufferedImage renderBayRestoration(SnapshotContext context, CompanyDeck ship,
                                                      boolean restored, int credits, boolean unlocked,
                                                      float scale) throws Exception {
        Reactor reactor = new Reactor();
        BoatDeck boats = new BoatDeck();
        boats.reconcile("starter", ShuttleType.HERMES, 1);
        if (restored) boats.reconcile("starter", ShuttleType.HERMES, 2);
        BoatDeckViewModel model = new BoatDeckViewModel(reactor, boats, snapshotMechResources(), "Second Wind");
        if (restored) model.selectBoatAction(1).run();
        var bay = ship.rooms(RoomPurpose.HANGAR).get(1);
        var properties = props(model, ship, bay, 2);
        properties.put("activeBayLabel", restored ? "02/02" : "02/02 SEALED");
        properties.put("restoration", new BoatBayRestorationModel.Row(!restored, true, credits,
                unlocked ? null : "Complete Hold Until Relieved to unlock this repair.", () -> { }));
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BOAT_DECK_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, "boat-deck", properties)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("boat-deck-canvas"),
                    new BoatDeckCanvas(ship, () -> bay, model::selectedBerthIndex, () -> 1, berth -> berth == 0));
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            return renderRelative(renderer, document, 1920, 1080, scale);
        }
    }

    static Map<String, Object> props(BoatDeckViewModel viewModel,
                                             CompanyDeck ship,
                                             DeckGraph.Compartment bay, int bays) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", ShipBreadcrumb.of(ship.ship(), bay));
        props.put("activeBayLabel", String.format(Locale.ROOT, "BAY 01 / %02d", bays));
        props.put("bayNavigatorClasses",
                bays > 1 ? "bay-navigator" : "bay-navigator hidden");
        props.put("previousBay", (Runnable) () -> { });
        props.put("nextBay", (Runnable) () -> { });
        props.put("deckSummary", viewModel.deckSummary());
        props.put("materialStock", viewModel.materialStock());
        props.put("boatRows", viewModel.boatRows());
        props.put("selectedBoatName", viewModel.selectedBoatName());
        props.put("selectedBoatIdentity", viewModel.selectedBoatIdentity());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("slotRows", viewModel.slotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("overviewClasses", viewModel.overviewClasses());
        props.put("fittingClasses", viewModel.fittingClasses());
        props.put("fabricationClasses", viewModel.fabricationClasses());
        props.put("fabricationBerthLabel", viewModel.fabricationBerthLabel());
        props.put("fabricationPatternName", viewModel.fabricationPatternName());
        props.put("fabricationCopy", viewModel.fabricationCopy());
        props.put("fabricationMaterials", viewModel.fabricationMaterials());
        props.put("fabricationActionClasses", viewModel.fabricationActionClasses());
        props.put("fabricationBlocked", viewModel.fabricationBlocked());
        props.put("fabricationReason", viewModel.fabricationReason());
        props.put("fabricationActionLabel", viewModel.fabricationActionLabel());
        props.put("restoration", BoatBayRestorationModel.Row.hidden());
        props.put("wings", SecondhandWingsModel.Row.hidden());
        props.put("fabricate", viewModel.fabricateAction());
        props.put("backToDeck", viewModel.backToDeckAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BOAT_DECK,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }

    private static Map<String, Object> props(MechLabViewModel viewModel,
                                             CompanyDeck ship) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", ShipBreadcrumb.of(ship.ship(),
                ship.room(RoomPurpose.VEHICLE_BAY)));
        props.put("activeBayLabel", "BAY 01 / 01");
        props.put("bayNavigatorClasses", "bay-navigator hidden");
        props.put("previousBay", (Runnable) () -> { });
        props.put("nextBay", (Runnable) () -> { });
        viewModel.putTutorialProps(props);
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("gantryRows", viewModel.gantryRows());
        props.put("activeGantryLabel", viewModel.activeGantryLabel());
        props.put("garageTitle", viewModel.garageTitle());
        props.put("selectedMechName", viewModel.selectedMechName());
        props.put("selectedMechNameClasses", viewModel.selectedMechNameClasses());
        props.put("callSignDraft", viewModel.callSignDraft());
        props.put("callSignEditorClasses", viewModel.callSignEditorClasses());
        props.put("callSignRenameDisabled", viewModel.callSignRenameDisabled());
        props.put("editCallSign", viewModel.editCallSignAction());
        props.put("renameCallSign", viewModel.renameCallSignAction());
        props.put("selectedMechIdentity", viewModel.selectedMechIdentity());
        props.put("selectedMechDoctrine", viewModel.selectedMechDoctrine());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("leftSlotRows", viewModel.leftSlotRows());
        props.put("rightSlotRows", viewModel.rightSlotRows());
        props.put("slotRows", viewModel.slotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("selectedSlotRule", viewModel.selectedSlotRule());
        props.put("materialStock", viewModel.materialStock());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("pickerClasses", viewModel.pickerClasses());
        props.put("workspaceClasses", viewModel.workspaceClasses());
        props.put("fittingHeaderClasses", viewModel.fittingHeaderClasses());
        props.put("performanceClasses", viewModel.performanceClasses());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("slotRackClasses", viewModel.slotRackClasses());
        props.put("overviewRailClasses", viewModel.overviewRailClasses());
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("previousGantry", viewModel.previousGantryAction());
        props.put("nextGantry", viewModel.nextGantryAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("categoryFilterPills", viewModel.categoryFilterPills());
        props.put("categoryFilterClasses", viewModel.categoryFilterClasses());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }
}
