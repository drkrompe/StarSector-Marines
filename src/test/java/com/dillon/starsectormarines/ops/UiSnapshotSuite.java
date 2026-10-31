package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.ops.battleview.BoatDoll;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.ops.fitting.FittingComponents;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.i18n.Strings;

import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;

import com.dillon.starsectormarines.marine.OfficerSpecialty;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;

import com.dillon.starsectormarines.marine.BarracksFlowCompany;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;

import org.json.JSONObject;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
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
import com.dillon.starsectormarines.campaign.CivilizedPort;
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
import com.dillon.starsectormarines.marine.MechFieldRepair;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.BoatFabricationCatalog;
import com.dillon.starsectormarines.marine.Reclamation;
import com.dillon.starsectormarines.marine.ReserveHangar;
import com.dillon.starsectormarines.marine.WorkshopTooling;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.marine.BoatHardpoint;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.LoadoutCardLearning;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.TestPurse;
import com.dillon.starsectormarines.marine.SquadFoundingWorkshop;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.LiftQueue;
import com.dillon.starsectormarines.battle.power.LiftBudget;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.air.LiftQueueReadout;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
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
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas;
import com.dillon.starsectormarines.ops.battleview.ShipViewCanvas;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.DeckPlanCanvas;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.MechLabCameraController;
import com.dillon.starsectormarines.ops.battleview.MechChassisPreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.LanceSlotCanvas;
import com.dillon.starsectormarines.ops.battleview.VehiclePreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.MechEquipmentGridCanvas;
import com.dillon.starsectormarines.ops.battleview.MechHardwareSheetCanvas;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
import com.dillon.starsectormarines.ops.fitting.FittingDragGhost;
import com.dillon.starsectormarines.ops.battleview.MechHardware;
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
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import com.dillon.starsectormarines.battle.weapon.WeaponFireCycle;
import com.dillon.starsectormarines.ops.battleview.HeadlessWeaponPreviewAssets;
import com.dillon.starsectormarines.ops.battleview.WeaponPreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.WeaponPreviewScenes;
import com.dillon.starsectormarines.ops.spec.SpecSheets;

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
import java.util.Arrays;
import java.lang.reflect.Proxy;
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
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-hq.mlx");
    private static final List<String> MISSION_SELECT_COMPONENTS = List.of(
            "data/ui/components/missions/mission-dialogue.mlx",
            "data/ui/components/missions/mission-select.mlx");
    private static final List<String> MISSION_BRIEFING_COMPONENTS = BriefingScreen.COMPONENT_PATHS;
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
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/armory-card-chip.mlx", "data/ui/components/company/shipboard-barracks.mlx");
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
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/ship-view.mlx");
    private static final List<String> SHIP_TRANSFER_COMPONENTS = List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/ship-transfer.mlx");
    private static final List<String> WORKSPACE_COMPONENTS = List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/armory/fleet-armory-fireteam.mlx",
            "data/ui/components/armory/armory-loadout-card.mlx", "data/ui/components/armory/armory-squad-view.mlx", "data/ui/components/armory/armory-card-chip.mlx", "data/ui/components/armory/armory-squad-doctrine.mlx",
            "data/ui/components/armory/armory-refit-transaction.mlx",
            "data/ui/components/fitting/fitting-filter-pill.mlx");
    private static final List<String> MECH_LAB_COMPONENTS = FittingComponents.with(List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/mech-lab/mech-lab.mlx"));
    private static final List<String> BOAT_DECK_COMPONENTS = FittingComponents.with(List.of(
            "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/boat-deck/boat-deck.mlx"));
    private static final List<String> BATTLE_HUD_COMPONENTS =
            List.of(BattleHudOverlay.COMPONENT_PATH,
                    BattleSquadOverlay.COMPONENT_PATH,
                    BattleMechOverlay.COMPONENT_PATH,
                    BattlePowerOverlay.COMPONENT_PATH,
                    BattleAirOverlay.COMPONENT_PATH,
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
                // Her deck is still being laid out, so the rooms aboard read as
                // pending rather than as places the hull does not have.
                new SnapshotArtifact("company-hq-getting-ready-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.gettingReadyPreview())),
                // The Hegemony auxiliary warrant's correspondence: the terms with their
                // limits and stage-two condition, then the office's reply to each answer.
                new SnapshotArtifact("company-hq-auxiliary-terms-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.auxiliaryWarrantPreview(AuxiliaryWarrantDesk.Page.TERMS))),
                new SnapshotArtifact("company-hq-auxiliary-accepted-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.auxiliaryWarrantPreview(AuxiliaryWarrantDesk.Page.ACCEPTED))),
                new SnapshotArtifact("company-hq-auxiliary-declined-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.auxiliaryWarrantPreview(AuxiliaryWarrantDesk.Page.DECLINED))),
                // The Latchkey custody decision: each written agreement, Castle's comparison,
                // the signing confirmation and the recorded signature with Voss's warning.
                new SnapshotArtifact("company-hq-handover-custody-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.agreusHandoverPreview(AgreusHandoverDesk.Page.CUSTODY, null))),
                new SnapshotArtifact("company-hq-handover-compact-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.agreusHandoverPreview(AgreusHandoverDesk.Page.COMPACT, null))),
                new SnapshotArtifact("company-hq-handover-compare-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.agreusHandoverPreview(AgreusHandoverDesk.Page.COMPARE, null))),
                new SnapshotArtifact("company-hq-handover-confirm-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.agreusHandoverPreview(AgreusHandoverDesk.Page.CONFIRM_COMPACT, null))),
                new SnapshotArtifact("company-hq-handover-signed-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.agreusHandoverPreview(AgreusHandoverDesk.Page.SIGNED, AgreusYardAgreement.YARD_COMPACT))),
                new SnapshotArtifact("barracks-wide.png",
                        renderBarracks(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                // The company roster: every marine on one comparable row, sorted by aptitude.
                new SnapshotArtifact("barracks-roster-wide.png",
                        renderBarracks(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
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
                new SnapshotArtifact("mech-lab-hardware-sheet-wide.png",
                        renderer.renderCanvas(new MechHardwareSheetCanvas(),
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("mech-lab-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f)),
                new SnapshotArtifact("mech-lab-asset-picker-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, true)),
                new SnapshotArtifact("mech-lab-hound-empty-socket-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, false, true, false, false,
                                null, "empty-socket")),
                // The two heavier chassis, arm mount chosen: their 2x3 arm sockets and long guns.
                new SnapshotArtifact("mech-lab-bulwark-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, false, false, false, false,
                                null, "chassis-Bulwark")),
                // The starter Bulwark half a day into a free repair: the damage card, and it still drops.
                new SnapshotArtifact("mech-lab-repairing-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, false, false, false, false,
                                null, "repairing")),
                new SnapshotArtifact("mech-lab-warden-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, false, false, false, false,
                                null, "chassis-Warden")),
                new SnapshotArtifact("mech-lab-chassis-fabrication-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                false, false, true)),
                // A vehicle selected on the vacant pad: previewed nose-up, its lore and bill in the build panel.
                new SnapshotArtifact("mech-lab-vehicle-preview-wide.png",
                        renderMechLab(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, false, false, true, false,
                                null, "preview-recipe.vehicle.breakthrough_tank")),
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
                new SnapshotArtifact("boat-deck-door-opening-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, false, DOOR_OPENING)),
                new SnapshotArtifact("boat-deck-departure-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, false, AT_OPENING)),
                new SnapshotArtifact("boat-deck-fitting-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("boat-deck-recycle-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false, RECYCLING)),
                new SnapshotArtifact("boat-deck-recycle-half-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false, RECYCLING_HALF)),
                new SnapshotArtifact("boat-deck-scrap-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false, SCRAP_ARMED)),
                new SnapshotArtifact("boat-deck-hardpoint-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false, NO_DEPARTURE, 0)),
                new SnapshotArtifact("boat-deck-vacant-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, true)),
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
                new SnapshotArtifact("battle-hud-mech-drop-picker-wide.png",
                        renderBattleHudDropPicker(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-hud-air-idle-wide.png",
                        renderBattleAirOverlay(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                airView(3, 0, 3, 0, 0f, 2, 0, 2, 0, 0f, List.of(), 0))),
                new SnapshotArtifact("battle-hud-air-sorties-wide.png",
                        renderBattleAirOverlay(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                airView(1, 2, 3, 0, 20f, 1, 1, 2, 0, -1f, List.of(
                                        new AirPoolView.SortieRow(AirPoolKind.BOMBER, AirPoolView.Mission.STRIKE),
                                        new AirPoolView.SortieRow(AirPoolKind.BOMBER, AirPoolView.Mission.STRIKE),
                                        new AirPoolView.SortieRow(AirPoolKind.FIGHTER, AirPoolView.Mission.COVER)), 0))),
                new SnapshotArtifact("battle-hud-air-depleted-wide.png",
                        renderBattleAirOverlay(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                airView(0, 0, 3, 3, 24.2f, 0, 0, 2, 2, 41f, List.of(), 2))),
                new SnapshotArtifact("battle-hud-air-losses-wide.png",
                        renderBattleAirOverlay(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                airView(1, 1, 3, 2, 12f, 2, 0, 2, 0, -1f, List.of(
                                        new AirPoolView.SortieRow(AirPoolKind.BOMBER, AirPoolView.Mission.STRIKE)), 1))),
                new SnapshotArtifact("battle-hud-air-no-pool-wide.png",
                        renderBattleAirOverlay(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                AirPoolView.NONE)),
                new SnapshotArtifact("battle-hud-retreat-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, BattleRetreatOverlayModel.Phase.ACTIVE,
                                STORY_RETRY_FREE, false)),
                new SnapshotArtifact("battle-hud-retreat-confirm-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, BattleRetreatOverlayModel.Phase.ACTIVE,
                                STORY_RETRY_FREE, true)),
                new SnapshotArtifact("battle-hud-retreat-confirm-contract-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, BattleRetreatOverlayModel.Phase.ACTIVE,
                                BattleRetreatOverlayModel.RetryOffer.NONE, true)),
                new SnapshotArtifact("battle-end-victory-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, BattleRetreatOverlayModel.Phase.VICTORY,
                                BattleRetreatOverlayModel.RetryOffer.NONE, false)),
                new SnapshotArtifact("battle-end-defeat-retry-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, BattleRetreatOverlayModel.Phase.DEFEAT,
                                STORY_RETRY_POINT, false)),
                new SnapshotArtifact("battle-end-defeat-no-points-wide.png",
                        renderBattleRetreatOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, BattleRetreatOverlayModel.Phase.DEFEAT,
                                STORY_RETRY_BROKE, false)));
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
        BriefingViewModel.putNarration(narratedBriefing, narration.find("story_opening_relief", "briefing.01"), true);
        narratedBriefing.put("missionTitle", "Hold Until Relieved");
        narratedBriefing.put("missionKicker", "ASSAULT  ·  LOW RISK  ·  First Contract");
        BriefingViewModel.putForceComparison(narratedBriefing, OperationTier.FIRST_CONTRACT, RiskLevel.LOW, 12, 12);
        BriefingViewModel.putReward(narratedBriefing, Mission.builder().payout(6000).build());
        BriefingViewModel.putFactionIdentity(narratedBriefing, "employer", "Independent", OpeningBroker.CREST);

        narratedBriefing.put("missionFlavor", narration.find("story_opening_relief", "briefing.01").text());
        // As the first mission opens: one commander card, no command powers and no fleet ships.
        narratedBriefing.put("taskForceRows", List.of(BriefingViewModel.forceCard("op-commander",
                "Lieutenant Morgan Hale", "1 squad · 12 marines",
                "graphics/portraits/portrait_mercenary01.png", "").asOperationCommander()));
        narratedBriefing.put("powerRows", List.of());
        narratedBriefing.put("powerEmpty", "No powers available from fleet or employer.");
        narratedBriefing.put("sourceRows", List.of());
        narratedBriefing.put("sourceEmpty", "No fleet ships provide command powers.");
        narratedBriefing.put("assignClasses", "");
        BriefingViewModel.withEmptyStyles(narratedBriefing);
        BriefingSurvey conquestSurvey = productionSurvey(context, "conquest-full-strength-west-v3.json", 1008);
        BriefingSurvey sabotageSurvey = productionSurvey(context, "sabotage-site-groups-v2.json", 12);
        BriefingSurvey understrengthSurvey = productionSurvey(context, "sabotage-site-groups-v2.json", 6);
        Map<String, Object> stoppedBriefing = new HashMap<>(narratedBriefing);
        BriefingViewModel.putNarration(stoppedBriefing, narration.find("story_opening_relief", "briefing.01"), false);
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
                new SnapshotArtifact("mission-briefing-colony-breach-posture-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                surveyProps(BriefingViewModel.colonyBreachPreviewProps(), colonySurvey(context)),
                                1f, false)),
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
                                BriefingScreen.previewProps(true, true), 1f, false, "air-dagger")),
                new SnapshotArtifact("mission-briefing-debug-kit-drawer-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                DebugKitPicker.previewProps(null), 1f, false)),
                new SnapshotArtifact("mission-briefing-debug-kit-drawer-scrolled-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                DebugKitPicker.previewProps(null), 1f, true)),
                new SnapshotArtifact("mission-briefing-debug-kit-weapons-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                DebugKitPicker.previewProps(DebugKitPicker.Kind.WEAPONS), 1f, false)),
                new SnapshotArtifact("mission-briefing-debug-kit-armor-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                DebugKitPicker.previewProps(DebugKitPicker.Kind.ARMOR), 1f, false)),
                new SnapshotArtifact("mission-briefing-debug-kit-commander-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                DebugKitPicker.previewProps(DebugKitPicker.Kind.COMMANDER), 1f, false)),
                new SnapshotArtifact("mission-briefing-debug-kit-bay-wide.png",
                        renderMissionBriefing(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                DebugKitPicker.previewProps(DebugKitPicker.Kind.BAY), 1f, false)));
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
                        validTarget ? "Orbital Barrage" : "Close Air Support",
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
                        UnitRole.COMBATANT, leader ? ArmorClass.POWER_SUIT : ArmorClass.STANDARD,
                        30 + index * 4, 12 + index * 2, index == 5 ? 3 : index % 4 == 0 ? 1 : 0,
                        40f + index * 11f, index % 2 == 0, null));
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
                mechId -> { });
        model.updateProjected(new BattleMechOverlayModel.MechState(
                3, 303L, "Warden Three", MechVariant.WARDEN.displayName(),
                MechRole.LR_SUPPORT, MechRole.BALANCED,
                "Alpha",
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
        BufferedImage image = renderBattleBackdrop(FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT);
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
        return renderBattleHudCommandOverlay(context, renderer, width, height, groups, 2f);
    }

    private static BufferedImage renderBattleHudCommandOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean groups, float speed) throws Exception {
        BufferedImage image = renderBattleBackdrop(width, height);
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        BattleHudOverlayModel.Presentation presentation = model.updateProjected(speed, List.of(
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
                        CompoundService.CompoundState.MARINE_HELD, 0f)));
        presentation = presentation.withLiftRows(model.updateLiftQueue(previewLiftQueue(groups),
                List.of(), ignored -> { }, ignored -> { }));
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

    /** A ferry mid-landing: craft in the air, squad loads to come, and queued machine drops behind them. */
    private static LiftQueueReadout.Reading previewLiftQueue(boolean busy) {
        float[] route = {-8f, 40f, -12f, 40f};
        ArmorLoad hound = ArmorLoad.of(MechDeploymentSpec.standard(MechVariant.HOUND));
        ArmorLoad apc = ArmorLoad.of(VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC));
        List<LiftQueueReadout.Flight> flights = new ArrayList<>(List.of(
                new LiftQueueReadout.Flight(1L, ShuttleType.AEROSHUTTLE, LiftQueueReadout.Cargo.SQUAD, 6, null, 0f, true),
                new LiftQueueReadout.Flight(2L, ShuttleType.AEROSHUTTLE, LiftQueueReadout.Cargo.SQUAD, 6, null, 7.4f, false)));
        List<LiftQueueReadout.Waiting> waiting = new ArrayList<>(List.of(
                new LiftQueueReadout.Waiting(LiftQueueReadout.Cargo.SQUAD, 4, null, 19f),
                new LiftQueueReadout.Waiting(LiftQueueReadout.Cargo.MACHINE, 1,
                        new LiftQueue.MachineDrop(1L, "mech_support", 0, hound, 40f, 40f, route, null), 62f)));
        if (busy) {
            waiting.add(new LiftQueueReadout.Waiting(LiftQueueReadout.Cargo.MACHINE, 1,
                    new LiftQueue.MachineDrop(2L, "mech_support", 2, apc, 44f, 40f, route, null), 88f));
            waiting.add(new LiftQueueReadout.Waiting(LiftQueueReadout.Cargo.MACHINE, 1,
                    new LiftQueue.MachineDrop(3L, "mech_support", 1, apc, 46f, 40f, route, null), Float.NaN));
        }
        return new LiftQueueReadout.Reading(flights, waiting);
    }

    private static AirPoolView airView(int bAvail, int bOut, int bCap, int bLost, float bNext,
                                       int fAvail, int fOut, int fCap, int fLost, float fNext,
                                       List<AirPoolView.SortieRow> sorties, int recentLosses) {
        return new AirPoolView(List.of(
                new AirPoolView.KindRow(AirPoolKind.BOMBER, bAvail, bOut, bCap, bLost, bNext),
                new AirPoolView.KindRow(AirPoolKind.FIGHTER, fAvail, fOut, fCap, fLost, fNext)),
                sorties, 0, recentLosses);
    }

    /** Full-screen evidence for the air support plate under the command rail; absent for a battle with no pool. */
    private static BufferedImage renderBattleAirOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, AirPoolView view) throws Exception {
        BufferedImage image = renderBattleHudCommandOverlay(context, renderer, width, height);
        Reactor reactor = new Reactor();
        BattleAirOverlayModel model = new BattleAirOverlayModel(reactor);
        BattleAirOverlayModel.Presentation presentation = model.update(view);
        if (!presentation.visible()) return image;
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BATTLE_HUD_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleAirOverlay.COMPONENT, model.props())) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            BufferedImage overlay = renderer.render(document,
                    Math.round(BattleAirOverlay.DOCUMENT_WIDTH),
                    Math.round(BattleAirOverlay.documentHeight(presentation.kindCount(), presentation.lineCount())));
            int railBottom = 12 + Math.round(BattleHudOverlay.OBJECTIVE_HEIGHT);
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(overlay, width - 12 - overlay.getWidth(), railBottom + 8, null);
            graphics.dispose();
            return image;
        }
    }

    /** Full-screen evidence for the compact power deck in its armed state. */
    private static BufferedImage renderBattleHudPowerOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        return renderBattleHudPowerOverlay(context, renderer, width, height, "orbital_barrage", List.of(), 2f);
    }

    /**
     * Mech Support armed with its bay picker open over the placement pause: a Hound picked, a Warden
     * aboard, a Bulwark the mission cannot lift, and an APC already queued.
     */
    private static BufferedImage renderBattleHudDropPicker(
            SnapshotContext context, HeadlessUiRenderer renderer, int width, int height) throws Exception {
        BayManifest manifest = BayManifest.of(List.of(
                MechDeploymentSpec.standard(MechVariant.HOUND),
                MechDeploymentSpec.standard(MechVariant.WARDEN),
                MechDeploymentSpec.standard(MechVariant.BULWARK)),
                List.of(VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC)));
        List<ArmorDropPower.PickerRow> rows = List.of(
                new ArmorDropPower.PickerRow(manifest.get(0), ArmorDropPower.Status.ABOARD, true, null),
                new ArmorDropPower.PickerRow(manifest.get(1), ArmorDropPower.Status.ABOARD, false, null),
                new ArmorDropPower.PickerRow(manifest.get(2), ArmorDropPower.Status.ABOARD, false,
                        LiftBudget.Refusal.NO_LIFT),
                new ArmorDropPower.PickerRow(manifest.get(3), ArmorDropPower.Status.QUEUED, false, null));
        return renderBattleHudPowerOverlay(context, renderer, width, height, "mech_support", rows, 0f);
    }

    private static BufferedImage renderBattleHudPowerOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer, int width, int height,
            String armed, List<ArmorDropPower.PickerRow> pickerRows, float speed) throws Exception {
        BufferedImage image = renderBattleHudCommandOverlay(
                context, renderer, width, height, false, speed);
        Reactor reactor = new Reactor();
        BattlePowerOverlayModel model = new BattlePowerOverlayModel(
                reactor, ignored -> { }, snapshotCommodities());
        List<BattlePowerOverlayModel.PowerState> powers = List.of(
                power("close_air_support", "Close Air Support", 3f, 0, 7.4f, 1),
                power("mech_support", "Mech Support", 0f, 0, 0f, 3),
                power("orbital_barrage", "Orbital Barrage", 4f, 20, 0f, 1));
        BattlePowerOverlayModel.Presentation presentation = model.withPicker(
                model.updateProjected(4f, 10f, 22, powers, armed), pickerRows);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BATTLE_HUD_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattlePowerOverlay.COMPONENT, model.props())) {
            BattlePowerOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            int overlayWidth = Math.round(BattlePowerOverlay.documentWidth(presentation));
            int overlayHeight = Math.round(BattlePowerOverlay.documentHeight(presentation));
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

    private static final BattleRetreatOverlayModel.RetryOffer STORY_RETRY_FREE =
            new BattleRetreatOverlayModel.RetryOffer(true, true, "Free restart", "Free retry");
    private static final BattleRetreatOverlayModel.RetryOffer STORY_RETRY_POINT =
            new BattleRetreatOverlayModel.RetryOffer(true, true, "Restart for 1 story point", "Retry for 1 story point");
    private static final BattleRetreatOverlayModel.RetryOffer STORY_RETRY_BROKE =
            new BattleRetreatOverlayModel.RetryOffer(true, false, "Restart needs a story point", "Retry needs a story point");

    /**
     * Full-screen evidence for the battle exit: the corner Retreat control and its
     * confirmation while the battle runs, then the end panel under the banner.
     */
    private static BufferedImage renderBattleRetreatOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer, int width, int height,
            BattleRetreatOverlayModel.Phase phase, BattleRetreatOverlayModel.RetryOffer offer,
            boolean confirming) throws Exception {
        BufferedImage image = renderBattleHudPowerOverlay(
                context, renderer, width, height);
        Reactor reactor = new Reactor();
        BattleRetreatOverlayModel model = new BattleRetreatOverlayModel(reactor,
                new BattleRetreatOverlayModel.Actions() {
                    @Override public void withdraw() { }
                    @Override public void proceed() { }
                    @Override public void retry() { }
                },
                new BattleRetreatOverlayModel.Labels(Strings.get("battleRetreat"), Strings.get("battleContinue"),
                        Strings.get("battleAcceptDefeat"), Strings.get("battleRetreatConfirm"),
                        Strings.get("battleRetreatConfirmDetail"), Strings.get("battleRetreatCancel"),
                        Strings.get("battleRetreatConfirmAction")));
        model.update(phase, offer);
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
            if (presentation.centred()) {
                // Stand-in for BattleScreen's raw-GL banner, which the headless renderer cannot draw.
                boolean victory = phase == BattleRetreatOverlayModel.Phase.VICTORY;
                String banner = Strings.get(victory ? "battleVictory" : "battleDefeat");
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                var metrics = graphics.getFontMetrics();
                int boxW = metrics.stringWidth(banner) + 48, boxH = metrics.getHeight() + 24;
                int boxX = (width - boxW) / 2, boxY = height / 2 - boxH / 2;
                graphics.setColor(new Color(0x0c, 0x12, 0x10, 235));
                graphics.fillRect(boxX, boxY, boxW, boxH);
                Color tone = victory ? new Color(0x80, 0xE0, 0x80) : new Color(0xE0, 0x60, 0x60);
                graphics.setColor(tone);
                graphics.drawRect(boxX, boxY, boxW, boxH);
                graphics.drawString(banner, boxX + 24, boxY + 12 + metrics.getAscent());
                graphics.drawImage(overlay, (width - overlay.getWidth()) / 2, height / 2 + 40, null);
            } else {
                graphics.drawImage(overlay, 12,
                        height - 12 - overlay.getHeight(), null);
            }
            graphics.dispose();
            return image;
        }
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

    /** The canonical Colony Breach colony's survey, built from its fixture as it is. */
    private static BriefingSurvey colonySurvey(SnapshotContext context) throws Exception {
        Path path = context.modRoot().getParent().resolve("src/test/resources/battle-fixtures/colony-breach-v1.json");
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(Files.readString(path)));
        try (BattleSimulation sim = fixture.build()) {
            return BriefingSurvey.capture(sim);
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

    static BufferedImage renderMissionBriefing(
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
            BriefingLift.install(document, instance, props);
            BriefingAir.install(document, instance, props);
            BriefingViewModel.installMusters(document, instance, props);
            if (Boolean.TRUE.equals(props.get("debugExpanded"))) {
                DebugKitPicker.wireBayPictures(document, instance, (List<?>) props.get("debugBaySlots"),
                        MechLabDollCanvas::headlessAssets);
                if (props.get("kitPicker") instanceof Map<?, ?> picker && Boolean.TRUE.equals(picker.get("open"))) {
                    DebugKitPicker.wireBayPictures(document, instance, (List<?>) picker.get("bayTiles"),
                            MechLabDollCanvas::headlessAssets);
                }
            }
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
        // The sheet may belong to the subject or to a row around it (a stores row owns its sheet).
        boolean opened = false;
        for (UiElement element = subject; element != null && !opened; element = element.parent()) {
            opened = binder.openTarget() == element;
        }
        if (!opened) {
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
        CompanyIntelTerminal terminal = new CompanyIntelTerminal(desk, CommandDeckLayout.parse(
                Files.readString(context.modRoot().resolve(CommandDeckLayout.PATH))));
        terminal.put(hqProps);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), COMPANY_HQ_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "company-hq", hqProps)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            terminal.bind(document, instance);
            terminal.advance(0f);
            terminal.advance(1f);
            BufferedImage frame = renderRelative(renderer, document, width, height, uiScale);
            if (terminal.place()) frame = renderRelative(renderer, document, width, height, uiScale);
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
                if (panel.bottom() > instance.requireElement("company-hq-detail").box().borderBox().bottom()) {
                    throw new IllegalStateException("Opening guidance exceeds the detail pane");
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
        props.put("resourceStock", viewModel.resourceStock());
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
    private static CompanyDeck companyShip(SnapshotContext context,
                                           Supplier<List<MechVariant>> lance,
                                           Supplier<List<MarineSoldier>> company) throws Exception {
        return companyShip(context, lance, company, null);
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
    private static CompanyDeck companyShip(SnapshotContext context,
                                           Supplier<List<MechVariant>> lance,
                                           Supplier<List<MarineSoldier>> company,
                                           Function<CompanyDeck, boolean[]> boatsHeld) throws Exception {
        boolean[][] held = new boolean[1][];
        CompanyDeck ship = new CompanyDeck(TestHulls.secondWind(context.starsectorCore()), SHIP_SEED,
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
    /**
     * A marine's primary hovered, its sheet playing the weapon on the range on a fixed scene clock: the
     * first marine's weapon as a round leaves, then the first magazine-fed one as a round leaves and
     * while its load is changed.
     */
    private static List<SnapshotArtifact> renderWeaponSceneSheet(SnapshotContext context, HeadlessUiRenderer renderer,
            UiDocument document, MarkupInstance markup, SpecSheetBinder sheets) throws Exception {
        SpecSheetLayer.registerSceneSource(SpecSheets.SCENE_WEAPON, () -> new WeaponPreviewScenes(
                new HeadlessWeaponPreviewAssets(context.modRoot(), context.starsectorCore())));
        var layer = SpecSheetLayer.install(document);
        double[] clock = {0d};
        layer.sceneClock(() -> clock[0]);
        List<SnapshotArtifact> frames = new ArrayList<>();
        WeaponPreviewCanvas first = hoverWeaponScene(document, markup, sheets, layer, 0);
        clock[0] = firingMoment(first.cycle());
        frames.add(new SnapshotArtifact("03g-weapon-sheet-firing.png", renderRelative(renderer, document, 1920, 1080, 1f)));
        for (int billet = 1; billet < MarineSquad.CAPACITY; billet++) {
            WeaponPreviewCanvas scene = hoverWeaponScene(document, markup, sheets, layer, billet);
            WeaponFireCycle cycle = scene.cycle();
            if (!cycle.magazineFed()) continue;
            clock[0] = firingMoment(cycle);
            frames.add(new SnapshotArtifact("03h-magazine-sheet-firing.png", renderRelative(renderer, document, 1920, 1080, 1f)));
            clock[0] = cycle.releaseTime(cycle.releasesPerLoop() - 1) + 0.12d + cycle.reloadSeconds() * 0.4d;
            frames.add(new SnapshotArtifact("03i-magazine-sheet-reloading.png", renderRelative(renderer, document, 1920, 1080, 1f)));
            break;
        }
        layer.sceneClock(null);
        return frames;
    }

    /** A moment a round has just left, with one already spent from the load. */
    private static double firingMoment(WeaponFireCycle cycle) {
        return cycle.releaseTime(Math.min(1, cycle.releasesPerLoop() - 1)) + 0.03d;
    }

    private static WeaponPreviewCanvas hoverWeaponScene(UiDocument document, MarkupInstance markup,
            SpecSheetBinder sheets, SpecSheetLayer layer, int billet) {
        var target = markup.requireElement(LoadoutInspection.partId(billet, LoadoutInspection.WEAPON));
        var box = target.box().borderBox();
        document.pointerMoved(box.x() + box.width() / 2, box.y() + box.height() / 2);
        sheets.update(); document.advance(0.3f);
        if (sheets.openTarget() != target) throw new AssertionError("Billet " + billet + "'s primary lost its spec sheet");
        if (!(document.canvases().producerOf(layer.sceneCanvas()) instanceof WeaponPreviewCanvas scene)) {
            throw new AssertionError("Billet " + billet + "'s primary sheet shows no range scene");
        }
        return scene;
    }

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
        CompanyDeck ship = companyShip(context, List::of, () -> MarineOpsContext.companyMarines(roster));
        List<ScreenId> routes = new ArrayList<>();
        var navigation = new MarineOpsContext(null) {
            @Override public void goTo(ScreenId screen) { routes.add(screen); }
        };
        model.command().equipmentWorkspace(() -> navigation.openFleetArmoryWorkspaceFrom(ScreenId.BARRACKS));
        model.command().navigation(navigation::openSquadEquipmentFromBarracks, () -> {}, true);
        var properties = props(model, ship);
        properties.put("contextLabel", "BARRACKS / COMPANY PREPARATION");
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
        List<SnapshotArtifact> frames = new ArrayList<>();
        try (var markup = loader.reloadAndBuild(reactor, "shipboard-barracks", properties)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            var guide = BarracksGuideCallout.bind(document, markup, model.command(), model.command()::showOverview);
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP,
                    List.of(CompanyNotices.Notice.FIRST_REFIT));
            frames.add(new SnapshotArtifact("01-barracks-equipment-new.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            clickBarracksFlow(document, markup, "barracks-equipment");
            if (!routes.equals(List.of(ScreenId.FLEET_ARMORY))) throw new AssertionError("Equipment entry did not route");
            model.command().toggle(squad.id());
            markup.flush();
            frames.add(new SnapshotArtifact("02-barracks-equip-squad.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            clickBarracksFlow(document, markup, "formation:" + squad.id() + ":loadout");
            if (!squad.id().equals(navigation.takeRequestedArmorySquadId())) throw new AssertionError("Wrong squad requested");
            var editor = new FleetArmoryViewModel(reactor, roster, () -> 100d, snapshotEquipmentResources());
            editor.selectSquad(squad.id());
            var editorProps = props(editor);
            editorProps.put("returnAction", (Runnable) navigation::returnFromFleetArmoryWorkspace);
            var editorLoader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), WORKSPACE_COMPONENTS);
            try (var editorMarkup = editorLoader.reloadAndBuild(reactor, "fleet-armory-fireteam", editorProps)) {
                var editorDocument = new UiDocument(editorMarkup.root()).theme(MarineOpsThemes.standard());
                editorMarkup.styles().forEach(editorDocument::addStyleSheet);
                // Installed with the document, as the screen does: the cards wear its classes.
                var sheets = new SpecSheetBinder(editorDocument, SpecSheetLayer.install(editorDocument));
                LoadoutInspection.bindCanvases(editorDocument, editorMarkup, editor.inspection()::get,
                        new HeadlessArmoryPreviewRenderer(context.modRoot()).assets());
                MarineOpsPageNav.refresh(editorMarkup, MarineOpsPageNav.Page.ARMORY, MarineOpsPageNav.ANY_SHIP,
                        List.of(CompanyNotices.Notice.FIRST_REFIT));
                frames.add(new SnapshotArtifact("03-existing-equipment-editor.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                editor.showArmorPickerAction().run();
                editorMarkup.flush();
                LoadoutCardBricks.bind(editorDocument, editorMarkup, editor.armorDoctrineTiles()::get);
                frames.add(new SnapshotArtifact("03a-starter-tactics.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                roster.firstRefit().claim(roster, FirstRefit.Path.FORWARD_SCREEN);
                editor.refresh();
                editor.showWeaponPickerAction().run();
                editor.weaponDoctrineTiles().get().get(0).select().run();
                editorMarkup.flush();
                // As the screen does after each reconciliation: the weapon cards' bricks get painters.
                LoadoutCardBricks.bind(editorDocument, editorMarkup, editor.weaponDoctrineTiles()::get);
                editorDocument.advance(0.3f); // let the selection's border transition settle
                frames.add(new SnapshotArtifact("03b-earned-weapon-comparison.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                collectArmorTemplates(roster);
                editor.refresh();
                editor.showArmorPickerAction().run();
                editor.armorDoctrineTiles().get().get(0).select().run();
                editorMarkup.flush();
                LoadoutCardBricks.bind(editorDocument, editorMarkup, editor.armorDoctrineTiles()::get);
                editorDocument.advance(0.3f);
                frames.add(new SnapshotArtifact("03c-earned-armor-comparison.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                // Production pointer targets remain visible at the player's review scale.
                var grid = editorMarkup.requireElement("loadout-billets").box().borderBox();
                for (int i = 0; i < 12; i++) {
                    var box = editorMarkup.requireElement("loadout-billet:" + i).box().borderBox();
                    if (box.width() < 90 || box.height() < 120 || box.bottom() > grid.bottom())
                        throw new AssertionError("Billet is clipped: " + i);
                }
                var issue = editorMarkup.requireElement(editor.weaponDoctrineTiles().get().get(0).id() + ":issue");
                if (issue.box().borderBox().width() > 200)
                    throw new AssertionError("Issue action must stay compact");
                var members = roster.manningMemberIds(squad);
                EquipmentTestFixtures.issueLauncher(roster.soldierById(members.get(0)));
                roster.applySoldierOutcome(Map.of(members.get(0), MarineSoldierStatus.WIA,
                        members.get(11), MarineSoldierStatus.KIA), 100f, 2f);
                editor.refresh(); editorMarkup.flush();
                LoadoutCardBricks.bind(editorDocument, editorMarkup, editor.armorDoctrineTiles()::get);
                frames.add(new SnapshotArtifact("03d-wounded-special-removal.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                clickBarracksFlow(editorDocument, editorMarkup, "loadout-billet:11");
                frames.add(new SnapshotArtifact("03e-vacant-billet-plan.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                if (editor.applyDisabled().get()) throw new AssertionError("Wounds or vacancies blocked fitting");
                clickBarracksFlow(editorDocument, editorMarkup, "loadout-billet:0");
                ArmorySpecSheets.bindInspection(sheets, editorMarkup, editor.inspection()::get);
                var target = editorMarkup.requireElement(LoadoutInspection.partId(0, LoadoutInspection.ARMOR));
                var targetBox = target.box().borderBox();
                editorDocument.pointerMoved(targetBox.x() + targetBox.width()/2, targetBox.y() + targetBox.height()/2);
                sheets.update(); editorDocument.advance(0.3f);
                if (sheets.openTarget() != target) throw new AssertionError("Proposed equipment lost its spec sheet");
                frames.add(new SnapshotArtifact("03f-equipment-spec-sheet.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                frames.addAll(renderWeaponSceneSheet(context, renderer, editorDocument, editorMarkup, sheets));
                sheets.clear();
                // Every weapon card known, one issuing secondaries chosen: the cards' and the
                // marines' kit bricks in each colour, a launcher and a rifle on their mounts.
                for (var doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
                    roster.armory().learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrine.id()));
                }
                editor.refresh();
                editor.showWeaponPickerAction().run();
                editor.weaponDoctrineTiles().get().stream()
                        .filter(tile -> tile.id().equals("weapon-doctrine:" + SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS))
                        .findFirst().orElseThrow().select().run();
                editorMarkup.flush();
                LoadoutCardBricks.bind(editorDocument, editorMarkup, editor.weaponDoctrineTiles()::get);
                editorDocument.advance(0.3f);
                frames.add(new SnapshotArtifact("03j-weapon-card-secondaries.png", renderRelative(renderer, editorDocument, 1920, 1080, 1f)));
                clickBarracksFlow(editorDocument, editorMarkup, "page-nav-return");
                if (routes.get(routes.size() - 1) != ScreenId.BARRACKS) throw new AssertionError("Editor return missed Barracks");
            }
            if (!model.command().selected().contains(squad.id()))
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
        return renderBarracksCommand(context, List.of("dossier", "dossier-trained", "command",
                "empty", "large", "guide", "guide-replenish"), true, true);
    }

    /**
     * The command board in {@code states}; {@code full} adds results and the click-through
     * flow, and {@code induction} the officer induction at 100% and 150%.
     */
    static List<SnapshotArtifact> renderBarracksCommand(SnapshotContext context, List<String> states,
                                                        boolean full, boolean induction) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> images = new ArrayList<>();
        if (full) {
            images.add(new SnapshotArtifact("results-prepare-company.png",
                    renderMissionFlow(context, renderer, MISSION_RESULTS_COMPONENTS,
                            ResultsScreen.ROOT_COMPONENT, ResultsScreen.previewProps(true, true))));
        }
        for (String state : states) {
            Reactor reactor = new Reactor();
            MarineRoster roster = new MarineRoster();
            int count = state.equals("empty") ? 0 : state.equals("large") ? 120 : 12;
            roster.bootstrapInitialComplement(count * MarineSquad.CAPACITY);
            MarineCaptain captain = new MarineCaptain("Tamsin Vale", "graphics/portraits/portrait_hegemony06.png", Rank.MAJOR, 0f);
            roster.add(captain);
            MarineCaptain second = new MarineCaptain("Rafael Kade", "graphics/portraits/portrait_mercenary04.png", Rank.CAPTAIN, 0f);
            roster.add(second);
            // Part-way to promotion each; Kade has spent his points, so only Vale's card wears the badge.
            captain.addXp(2600);
            second.addXp(1450);
            second.train(OfficerSpecialty.TACTICAL_COVER);
            second.train(OfficerSpecialty.FIELD_MEDICINE);
            var squads = roster.squads().stream().filter(q -> !q.reserve()).toList();
            for (int i = 0; i < squads.size(); i++) {
                if (i < 8) roster.assignCaptainToSquad(captain.id(), squads.get(i).id());
                else if (i < 12) roster.assignCaptainToSquad(second.id(), squads.get(i).id());
            }
            if (!squads.isEmpty()) {
                roster.applySoldierOutcome(Map.of(squads.get(1).memberIds().get(0), MarineSoldierStatus.WIA,
                        squads.get(2).memberIds().get(0), MarineSoldierStatus.KIA), 100f, 2f);
                roster.bindStationing(1001L, second.id(), List.of(squads.get(10).id()));
            }
            BarracksViewModel viewModel = new BarracksViewModel(reactor, roster, () -> 100d,
                    snapshotEquipmentResources(), snapshotSquadFoundingResources(), () -> 0,
                    id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0));
            if (state.equals("medic")) {
                // Field Medicine marks each of the officer's squads' medic billet.
                captain.train(OfficerSpecialty.FIELD_MEDICINE);
                viewModel.command().selectCommand(captain.id());
                ((Runnable) viewModel.command().props().get("closeDossier")).run();
            }
            if (state.equals("command")) {
                // Squad 01 carries mixed cards, so its hovers show specials and integral systems.
                LoadoutCardLearning.learn(roster.armory(), new LoadoutCard(LoadoutCard.Kind.WEAPON,
                        SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS).id());
                LoadoutCardLearning.learn(roster.armory(), new LoadoutCard(LoadoutCard.Kind.SHEET,
                        SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR).id());
                if (roster.applySquadEquipment(squads.get(0).id(), SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS,
                        SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR) != SquadEquipmentResult.APPLIED) {
                    throw new IllegalStateException("Squad 01's showcase cards did not apply");
                }
                // Squad 02 holds a Restricted card, so its face wears the seal and its hover the stamp.
                LoadoutCardLearning.learn(roster.armory(), new LoadoutCard(LoadoutCard.Kind.SHEET,
                        SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR).id());
                if (roster.applySquadEquipment(squads.get(1).id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                        SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR) != SquadEquipmentResult.APPLIED) {
                    throw new IllegalStateException("Squad 02's Restricted card did not apply");
                }
                viewModel.command().selectCommand(captain.id());
                ((Runnable) viewModel.command().props().get("closeDossier")).run();
                viewModel.command().toggle(squads.get(0).id());
                viewModel.command().toggle(squads.get(1).id());
                viewModel.command().toggle(squads.get(2).id());
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
            if (state.startsWith("guide-")) {
                // Selecting a squad opens the replenish lesson.
                viewModel.command().toggle(squads.get(2).id());
            }
            if (state.startsWith("call-")) {
                // The way back to work: quiet, a planet with work, a mission in progress.
                viewModel.command().navigation(id -> { }, () -> { }, true);
                viewModel.command().missionReturn(state.contains("briefing") ? MissionReturn.BRIEFING
                        : MissionReturn.MISSIONS, state.contains("work"));
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("command", viewModel.command().props());
            properties.put("contextLabel", "FLAGSHIP / PERSONNEL COMMAND");
            MarineOpsPageNav.put(properties, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP,
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
            loader.reload();
            try (MarkupInstance instance = loader.build(reactor, "shipboard-barracks", properties)) {
                UiDocument document = new UiDocument(instance.root());
                for (var style : instance.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                var guide = BarracksGuideCallout.bind(document, instance, viewModel.command(), viewModel.command()::showOverview);
                SpecSheetBinder hints = new SpecSheetBinder(document, SpecSheetLayer.install(document), true);
                BarracksCardBindings bindings = new BarracksCardBindings();
                bindings.update(document, instance, viewModel, hints);
                images.add(new SnapshotArtifact("barracks-" + state + "-1920x1080.png",
                        renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
                if (state.startsWith("dossier") || state.startsWith("guide")) {
                    images.add(new SnapshotArtifact("barracks-" + state + "-150pct.png",
                            renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1.5f)));
                }
                if (state.equals("dossier-trained")) {
                    // A rank describes itself on hover; the tree carries no effect copy.
                    openSpecSheet(document, hints, 1920, 1080, instance.requireElement("officer-specialty-TACTICAL_COVER-rank-2"));
                    images.add(new SnapshotArtifact("barracks-dossier-rank-hint-1920x1080.png",
                            renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
                    document.deactivateInput();
                    hints.update();
                    var pane = instance.requireElement("officer-dossier-scroll");
                    pane.scrollTop(pane.box().maxScrollTop());
                    document.advance(0f);
                    images.add(new SnapshotArtifact("barracks-dossier-record-150pct.png",
                            renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1.5f)));
                }
                if (state.equals("command")) {
                    // Each card face and a short squad's wrench describe themselves on hover.
                    Map<String, String> hovers = new LinkedHashMap<>();
                    hovers.put("barracks-weapon-card-hint-1920x1080.png", "formation:" + squads.get(0).id() + ":weapon-card");
                    hovers.put("barracks-armor-card-hint-1920x1080.png", "formation:" + squads.get(0).id() + ":armor-card");
                    hovers.put("barracks-restricted-card-hint-1920x1080.png", "formation:" + squads.get(1).id() + ":armor-card");
                    hovers.put("barracks-fill-hint-1920x1080.png", "formation:" + squads.get(2).id() + ":fill");
                    for (var hover : hovers.entrySet()) {
                        openSpecSheet(document, hints, 1920, 1080, instance.requireElement(hover.getValue()));
                        images.add(new SnapshotArtifact(hover.getKey(), renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
                        document.deactivateInput();
                        hints.update();
                    }
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
        if (induction) images.addAll(renderOfficerInduction(context, renderer, 1f, 1.5f));
        if (full) images.addAll(renderBarracksFlow(context, renderer));
        return images;
    }

    /** New-company clicks through the production spotlight, including scrolling to the third choice. */
    static List<SnapshotArtifact> renderOfficerInduction(
            SnapshotContext context, HeadlessUiRenderer renderer, float... scales) throws Exception {
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (float scale : scales) {
            Reactor reactor = new Reactor();
            MarineRoster roster = new MarineRoster();
            MarineCaptain sabine = new MarineCaptain("Morgan Hale", "graphics/portraits/sabine-hale.png", Rank.LIEUTENANT, 0f);
            roster.add(sabine);
            roster.beginCompanyPreparation(sabine);
            frames.addAll(renderPreparationRooms(context, renderer, roster, scale));
            BarracksViewModel model = new BarracksViewModel(reactor, roster, () -> 1d, snapshotEquipmentResources(),
                    snapshotSquadFoundingResources(), () -> 12,
                    id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0));
            var command = model.command();
            command.navigation(id -> { }, () -> { }, true);
            command.missionReturn(MissionReturn.BRIEFING, false);
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("command", command.props());
            properties.put("contextLabel", "FLAGSHIP / FIRST COMMAND");
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
                click.accept("command:" + sabine.id());
                capture.accept("02-specialties");
                // Bring the whole tree into the dossier pane, as a player scrolling to the choice would.
                var pane = instance.requireElement("officer-dossier-scroll");
                float treeTop = instance.requireElement("specialty-root").box().borderBox().y()
                        - pane.box().borderBox().y() + pane.scrollTop();
                pane.scrollTop(Math.min(pane.box().maxScrollTop(), treeTop));
                document.advance(0f);
                guide.update();
                capture.accept("03-scroll-choice");
                click.accept("officer-specialty-SALVAGE_STEWARDSHIP-rank-1");
                if (sabine.career().level(OfficerSpecialty.SALVAGE_STEWARDSHIP) != 1) {
                    throw new IllegalStateException("Click-through specialty choice did not train Morgan at scale " + scale);
                }
                capture.accept("04-formation");
                click.accept("officer-formations");
                capture.accept("05-raise-squad");
                click.accept("barracks-raise-squad");
                if (roster.squads().stream().noneMatch(s -> !s.reserve() && sabine.id().equals(s.homeCaptainId())))
                    throw new IllegalStateException("Raise Squad did not muster a squad under the selected officer");
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
        model.preparation(roster, snapshotMechResources(), snapshotSquadFoundingResources());
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
            // While the company is still being prepared the lesson says the boats can wait and holds input to
            // Barracks; the player commissions after that, with the lesson gone, so the clicks run without it.
            boolean[] lessonOn = {true};
            var guide = preparationGuide(context, document,
                    () -> lessonOn[0] ? CompanyPreparationGuide.boats(roster, boats, () -> { }) : null);
            frames.add(preparationFrame("02-berth", renderer, document, guide, scale));
            lessonOn[0] = false; guide.update();
            clickBarracksFlow(document, instance, "boat:0"); guide.update();
            frames.add(preparationFrame("03-commission", renderer, document, guide, scale));
            clickBarracksFlow(document, instance, "boat-fabricate"); guide.update();
            if (deck.flightReady().size() != 1) throw new IllegalStateException("Click-through commissioning failed");
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
        CompanyDeck ship = companyShip(context, List::of, () -> MarineOpsContext.companyMarines(company.roster));
        Map<String, Object> properties = props(model, ship);
        properties.put("contextLabel", ShipBreadcrumb.of(ship.ship(), null) + " / PERSONNEL COMMAND");
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
        loader.reload();
        List<SnapshotArtifact> frames = new ArrayList<>();
        try (MarkupInstance instance = loader.build(reactor, "shipboard-barracks", properties)) {
            UiDocument document = new UiDocument(instance.root());
            instance.styles().forEach(document::addStyleSheet);
            document.theme(MarineOpsThemes.standard());
            var guide = BarracksGuideCallout.bind(document, instance, model.command(), model.command()::showOverview);
            SpecSheetBinder hints = new SpecSheetBinder(document, SpecSheetLayer.install(document), true);
            BarracksCardBindings cardBindings = new BarracksCardBindings();
            cardBindings.update(document, instance, model, hints);
            Consumer<String> click = id -> {
                clickBarracksFlow(document, instance, id);
                cardBindings.update(document, instance, model, hints);
            };
            hints.bindLive(instance.requireElement("barracks-recruit-stock"), model.command()::recruitSheet);
            hints.bindLive(instance.requireElement("barracks-fill-selected"), model.command()::fillSelectedSheet);
            renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f);
            click.accept("guide-dismiss");
            click.accept("command:" + company.captain.id());
            frames.add(new SnapshotArtifact("flow-officer-dossier.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            click.accept("officer-specialty-TACTICAL_COVER-rank-1");
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
            var originalCargo = company.available();
            click.accept("barracks-fill-selected");
            if (company.recruits() != 17 || company.roster.readyReserveCount() != 0
                    || company.squads.subList(0, 2).stream().anyMatch(s -> company.roster.readyCount(s) != 12)
                    || company.roster.readyCount(company.squads.get(2)) != 11) {
                throw new IllegalStateException("Replenishment did not transfer one reserve then enlist three recruits");
            }
            frames.add(new SnapshotArtifact("flow-03-replenished.png", renderGuidedBarracks(renderer, document, guide, 1920, 1080, 1f)));
            // Replacements wear what their seats' cards issue, with no separate order and nothing spent.
            if (!company.available().equals(originalCargo) || company.recruits() != 17) {
                throw new IllegalStateException("Filling vacancies spent stores beyond the recruits it enlisted");
            }
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
        return renderBarracks(context, renderer, width, height, false);
    }

    private static BufferedImage renderBarracks(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean companyRoster) throws Exception {
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY * 3);
        MarineSquad squad = roster.squads().stream()
                .filter(candidate -> !candidate.reserve())
                .findFirst().orElseThrow();
        Map<String, MarineSoldierStatus> postBattle = new LinkedHashMap<>();
        postBattle.put(squad.memberIds().get(3), MarineSoldierStatus.WIA);
        if (companyRoster) {
            // A service record that varies across the company, so the SERVICE column reads.
            List<MarineSoldier> all = roster.soldiers();
            for (int mission = 0; mission < 6; mission++) {
                Map<String, MarineSoldierStatus> deployed = new LinkedHashMap<>();
                for (int i = 0; i < all.size(); i++) {
                    if ((i + mission) % 3 != 0) deployed.put(all.get(i).id(), MarineSoldierStatus.ACTIVE);
                }
                roster.applySoldierOutcome(deployed, 90f + mission, 1f, null, true);
            }
        }
        roster.applySoldierOutcome(postBattle, 100f, 1.25f);
        BarracksViewModel viewModel = new BarracksViewModel(reactor, roster, () -> 100d, EquipmentIssueResources.UNLIMITED,
                snapshotSquadFoundingResources(), () -> 0, id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0));
        if (companyRoster) viewModel.command().showCompanyRoster();
        CompanyDeck ship = companyShip(context, List::of,
                () -> MarineOpsContext.companyMarines(roster));
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BARRACKS_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "shipboard-barracks", props(viewModel, ship))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
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
                ? companyShip(context, List::of, () -> MarineOpsContext.companyMarines(roster))
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
        var survey = snapshotShipCondition(reactor, ship);
        props.put("survey", survey.row());
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
            var canvas = new ShipViewCanvas(ship);
            document.canvases().set(instance.requireElement("ship-view-deck"), canvas);
            var overlay = new ShipConditionOverlay(document, instance, survey);
            renderRelative(renderer, document, width, height, 1f);
            overlay.update(0f, ship, canvas);
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
            roster.armory().learnLoadoutCard(new LoadoutCard(
                    LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS));
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
                reactor, roster, () -> 100d, snapshotEquipmentResources());
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
                reactor, "fleet-armory-fireteam",
                props(viewModel))) {
            if (fireteam) {
                instance.requireElement("transaction-feedback")
                        .align(UiAlign.STRETCH, UiAlign.CENTER);
            }
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            // As the screen does: the cards wear the spec sheet's classes, and each marine has a portrait.
            SpecSheetLayer.install(document);
            if (fireteam) LoadoutInspection.bindCanvases(document, instance, viewModel.inspection()::get, armoryPreview.assets());
            if (equipmentTooltip) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                ArmorySpecSheets.bindInspection(binder, instance, viewModel.inspection()::get);
                openSpecSheet(document, binder, width, height,
                        instance.requireElement(LoadoutInspection.partId(0, LoadoutInspection.ARMOR)));
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

    /** A collected Armory fixture, distinct from the campaign's starter ownership. */
    private static void collectArmorTemplates(MarineRoster roster) {
        for (var armor : MarineArmorCatalogRegistry.installed().all()) {
            roster.armory().acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(armor.id()));
        }
        for (var plan : SquadEquipmentDoctrines.armorPlans()) roster.armory().acquireArmorPlan(plan.id());
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

    static List<SnapshotArtifact> renderPreparationResourceBills(SnapshotContext context) throws Exception {
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()), context.modRoot(), context.starsectorCore());
        var roster = new MarineRoster();
        var sabine = new MarineCaptain("Morgan Hale", "graphics/portraits/sabine-hale.png", Rank.LIEUTENANT, 0f);
        roster.add(sabine); roster.beginCompanyPreparation(sabine);
        return renderPreparationRooms(context, renderer, roster, 1f);
    }

    static List<SnapshotArtifact> renderResourceBills(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        return List.of(
                new SnapshotArtifact("boat-fitting.png", renderBoatDeck(context, renderer, 1920, 1080, true)),
                new SnapshotArtifact("boat-shortage.png", renderBoatDeck(context, renderer, 1920, 1080, false, true)),
                new SnapshotArtifact("mech-large-stock.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, true, false, false, null, "resource-large")),
                new SnapshotArtifact("mech-shortage.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, true, false, false, null, "resource-short")),
                new SnapshotArtifact("mech-chassis.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, false, true, false)),
                // The fleet away from a port: credits in hand, but BUY is refused and the strip says so.
                new SnapshotArtifact("boat-no-port-fabrication.png", renderBoatDeck(context, renderer, 1920, 1080, false, true, NO_PORT)),
                new SnapshotArtifact("boat-no-port-fitting.png", renderBoatDeck(context, renderer, 1920, 1080, true, false, NO_PORT)),
                new SnapshotArtifact("mech-no-port.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, true, false, false, null, "no-port")),
                // A hostile port, decided by vanilla's trade mode: dark and unseen its black market sells; identified, it refuses.
                new SnapshotArtifact("mech-hostile-port-black-market.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, true, false, false, null, "hostile-SNEAK")),
                new SnapshotArtifact("mech-hostile-port-refused.png", renderMechLab(context, renderer, 1920, 1080, 1f, false, true, false, false, null, "hostile-NONE")));
    }

    static List<SnapshotArtifact> renderEquipmentSheets(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        return List.of(
                new SnapshotArtifact("mech-component-spec.png", renderMechLab(context, renderer,
                        1920, 1080, 1f, false, true, false, true)),
                new SnapshotArtifact("mech-chassis-spec.png", renderMechLab(context, renderer,
                        1920, 1080, 1f, false, false, true, true)));
    }

    static List<SnapshotArtifact> renderCampaignGantries(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        var frames = new ArrayList<SnapshotArtifact>();
        for (int step = 1; step <= 2; step++) frames.add(new SnapshotArtifact("gantry-" + step + ".png",
                renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "lesson-" + step)));
        return frames;
    }

    private static String actionOrName(MechLabViewModel.CatalogRow row) {
        return String.valueOf(row.markupProperty("actionClasses")).contains("hidden") ? row.nameId() : row.actionId();
    }

    /** The Boat Deck's refit board alone: its doll fitting and a hardpoint selected. */
    static List<SnapshotArtifact> renderBoatConsole(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer boatRenderer = new HeadlessUiRenderer(
                new HeadlessBattleSceneRenderer(context.modRoot(), true),
                context.modRoot(), context.starsectorCore());
        return List.of(
                new SnapshotArtifact("boat-deck-fitting-wide.png",
                        renderBoatDeck(context, boatRenderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                // The reserve hangar: the opened boat's STORE armed, and the deck with one boat stored.
                new SnapshotArtifact("boat-deck-store-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false, STORE_ARMED)),
                new SnapshotArtifact("boat-deck-reserve-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, false, RESERVE_STRIP)),
                new SnapshotArtifact("boat-deck-hardpoint-wide.png",
                        renderBoatDeck(context, boatRenderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false, NO_DEPARTURE, 0)),
                // Her drive and her gun taken off into stores: the empty drive warns, the hardpoint is bare.
                new SnapshotArtifact("boat-deck-stripped-wide.png",
                        renderBoatDeck(context, boatRenderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, false, STRIPPED, -1)),
                // The pointer resting on the fitted Arbalest, nothing in hand: its spec sheet opens.
                new SnapshotArtifact("boat-deck-hover-part-wide.png",
                        renderBoatDeck(context, boatRenderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, false, NO_DEPARTURE, 0, BoatDoll.positionKey(0, BoatHardpoint.TOP))),
                // Two 2x1 guns on one hardpoint: a twin SRM pod on her second, two Twin PE-1 emitters on her third.
                new SnapshotArtifact("boat-deck-pod-wide.png",
                        renderBoatDeck(context, boatRenderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                true, false, POD, 2)));
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
                new SnapshotArtifact("mech-long-callsign-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, true, false, false, false, null, "long-name")),
                new SnapshotArtifact("mech-fitting-bulwark-1920x1080.png", heavy),
                new SnapshotArtifact("mech-lab-recycle-weapons-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "recycle-weapons")),
                new SnapshotArtifact("mech-lab-recycle-compartment-parts-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "vehicle-compartment-recycle")),
                new SnapshotArtifact("mech-lab-reserve-store-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "reserve-store")),
                new SnapshotArtifact("mech-lab-reserve-strip-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "reserve-strip")),
                new SnapshotArtifact("mech-lab-reserve-recalled-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "reserve-recalled")),
                new SnapshotArtifact("mech-vehicle-compartment-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "vehicle-compartment")),
                new SnapshotArtifact("mech-vehicle-apc-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "vehicle-apc")),
                new SnapshotArtifact("mech-vehicle-gun-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "vehicle-gun")),
                new SnapshotArtifact("mech-fitting-1920x1080.png", idle),
                new SnapshotArtifact("mech-internal-engine-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "internal-engine")),
                new SnapshotArtifact("mech-internal-plating-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "internal-plating")),
                new SnapshotArtifact("mech-internal-empty-engine-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "internal-empty-engine")),
                new SnapshotArtifact("mech-internal-capacitor-bank-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "internal-flux")),
                // A refit Bulwark: better engine, bank and plate, and a shorter arm gun, so the stat
                // bars show gains past stock and a shortfall below it.
                new SnapshotArtifact("mech-lab-refit-stats-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "refit-stats")),
                new SnapshotArtifact("mech-drag-snap-internal-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "drag-snap-internal")),
                new SnapshotArtifact("mech-drag-refused-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "drag-refused")),
                new SnapshotArtifact("mech-drag-floor-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "drag-floor")),
                new SnapshotArtifact("mech-hover-installed-1920x1080.png",
                        renderMechLab(context, renderer, 1920, 1080, 1f, false, false, false, false, null, "hover-installed")),
                new SnapshotArtifact("mech-fitting-valid-drop-1920x1080.png", frames.get(18)),
                new SnapshotArtifact("mech-fitting-rejected-drop-1920x1080.png", frames.get(34)),
                SnapshotArtifact.colorAnimation("mech-fitting-1920x1080.gif", frames, 100));
    }

    /**
     * Holds a stores part over the doll: an internal over a socket that takes it
     * ({@code drag-snap-internal}), a weapon over one that refuses it ({@code drag-refused}), or a
     * weapon over open floor ({@code drag-floor}). The ghost follows the screen's rule,
     * {@link MechLabDollCanvas#carriedSnapped}.
     */
    private static void showCarriedPart(UiDocument document, MarkupInstance instance, MechLabDollCanvas doll,
                                        MechLabViewModel viewModel, String pose) {
        UiElement dollElement = instance.requireElement("mech-doll-canvas");
        MechInternal internal = null;
        MechWeaponComponent weapon = null;
        SocketId target = null;
        float[] at = null;
        if ("drag-floor".equals(pose)) {
            for (MechWeaponComponent candidate : MechWeaponComponent.values()) {
                if (viewModel.inStores(candidate)) { weapon = candidate; break; }
            }
            var box = dollElement.box().contentBox();
            CanvasMetrics metrics = document.canvasMetrics(dollElement, 1f);
            at = new float[]{metrics.toCanvasX(box.x() + box.width() * .72f), metrics.toCanvasY(box.y() + box.height() * .45f)};
        } else {
            boolean snap = "drag-snap-internal".equals(pose);
            search:
            for (float y = 0f; y < 2000f; y += 6f) {
                for (float x = 0f; x < 2000f; x += 6f) {
                    SocketId socket = doll.socketAt(x, y);
                    if (socket == null) continue;
                    if (snap) {
                        // Into the compartment, where a new part lands on bare bricks and reads plainly.
                        if (!socket.compartment()) continue;
                        for (MechInternal candidate : MechInternal.values()) {
                            if (viewModel.canDropCatalogInternal(candidate, socket)) {
                                internal = candidate;
                                target = socket;
                                at = new float[]{x + 30f, y + 40f};
                                break search;
                            }
                        }
                    } else if (viewModel.installedWeapon(socket) != null || socket.compartment()) {
                        for (MechWeaponComponent candidate : MechWeaponComponent.values()) {
                            if (viewModel.inStores(candidate) && !viewModel.canDropCatalogWeapon(candidate, socket)) {
                                weapon = candidate;
                                target = socket;
                                at = new float[]{x + 30f, y + 40f};
                                break search;
                            }
                        }
                    }
                }
            }
        }
        if (at == null || internal == null && weapon == null) throw new IllegalStateException("No carried pose for " + pose);
        if (weapon != null) {
            doll.previewWeaponDrag(weapon, null, target, target != null && viewModel.canDropCatalogWeapon(weapon, target));
        } else {
            doll.previewPartDrag(internal, null, target, viewModel.canDropCatalogInternal(internal, target));
        }
        FittingDragGhost ghost = new FittingDragGhost();
        UiElement ghostElement = instance.requireElement("mech-drag-ghost");
        document.canvases().set(ghostElement, ghost);
        if (!doll.carriedSnapped()) {
            CanvasMetrics metrics = document.canvasMetrics(dollElement, 1f);
            MechWeaponComponent carriedWeapon = weapon;
            MechInternal carriedInternal = internal;
            ghost.show((c, x, y) -> {
                if (carriedWeapon != null) MechHardware.drawCarried(c, carriedWeapon, x, y);
                else MechHardware.drawCarried(c, carriedInternal, x, y);
            }, metrics.toDocumentX(at[0]), metrics.toDocumentY(at[1]));
        }
        ghostElement.fitCanvasToBox();
        document.canvases().invalidate(ghostElement);
        document.canvases().invalidate(dollElement);
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
        return renderMechLab(context, renderer, width, height, uiScale, pickerOpen, selectHound, selectVacant,
                specSheet, motionFrames, materialHint, weldingEvidence, null);
    }

    /** What the lab drew a machine's doll from, handed out after the last frame: the fit and the doll's canvas. */
    interface DollProbe {
        void probe(MechLabViewModel viewModel, CanvasMetrics doll);
    }

    /**
     * One machine alone on the first pad, as it ships, its doll drawn at 1920x1080 / 100%:
     * {@code machine-mech.<MechVariant>} or {@code machine-vehicle.<VehicleType>}.
     */
    static BufferedImage renderMachineDoll(SnapshotContext context, HeadlessUiRenderer renderer,
                                           String machineHint, DollProbe probe) throws Exception {
        return renderMechLab(context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f, false, false, false,
                false, null, machineHint, false, probe);
    }

    private static BufferedImage renderMechLab(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale, boolean pickerOpen,
            boolean selectHound, boolean selectVacant, boolean specSheet,
            List<BufferedImage> motionFrames, String materialHint, boolean weldingEvidence,
            DollProbe dollProbe) throws Exception {
        double[] seconds = {0};
        Reactor reactor = new Reactor();
        MechBay bay = MechBay.legacyStarterFixture();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id()));
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_03", "Warden 03", MechVariant.WARDEN,
                MechRole.LR_SUPPORT, MissileReplenisherComponent.ACCELERATED_FEED.id()));
        bay.addInternal(MechInternal.ACCELERATED_LOADER.id, 1);
        // A spare of every part, so the carried poses find one that fits and one that is refused.
        if (materialHint != null && materialHint.startsWith("drag-")) {
            for (MechInternal internal : MechInternal.values()) bay.addInternal(internal.id, 1);
            for (MechWeaponComponent weapon : MechWeaponComponent.values()) bay.addWeapon(weapon.id, 1);
        }
        if ("long-name".equals(materialHint)) {
            if (!bay.mechById(MechBay.STARTER_MECH_ID)
                    .rename("Ironclad of the Persean Expeditionary Vanguard 1")) {
                throw new IllegalStateException("Maximum-length callsign fixture was rejected");
            }
        }
        // One machine as it ships, alone in the bay, for its doll.
        String machineName = null;
        if (materialHint != null && materialHint.startsWith("machine-mech.")) {
            MechVariant variant = MechVariant.valueOf(materialHint.substring("machine-mech.".length()));
            bay = new MechBay();
            machineName = variant.displayName();
            bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech("doll_mech", machineName, variant,
                    variant.defaultRole, MissileReplenisherComponent.STANDARD.id()));
        }
        if (materialHint != null && materialHint.startsWith("machine-vehicle.")) {
            VehicleType type = VehicleType.valueOf(materialHint.substring("machine-vehicle.".length()));
            bay = new MechBay();
            machineName = CampaignVehicle.className(type) + " 01";
            bay.addVehicle(MechBay.STARTER_SQUAD_ID, new CampaignVehicle("doll_vehicle", machineName, type));
        }
        if (materialHint != null && materialHint.startsWith("lesson-")) {
            bay = new MechBay();
            bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
            bay.beginLabLesson();
        }
        if ("repairing".equals(materialHint)) {
            int sections = MechSection.values().length;
            boolean[] fitted = new boolean[sections];
            float[] structure = new float[sections];
            float[] armor = new float[sections];
            Arrays.fill(fitted, true);
            Arrays.fill(structure, 1f);
            Arrays.fill(armor, 1f);
            structure[MechSection.LEGS.ordinal()] = 0f;
            armor[MechSection.LEGS.ordinal()] = 0f;
            structure[MechSection.HEAD.ordinal()] = 0.5f;
            armor[MechSection.TORSO.ordinal()] = 0.4f;
            bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(MechBay.STARTER_MECH_ID,
                    new MechSections.Report("snapshot", fitted, structure, armor, new boolean[sections],
                            new MechWeaponComponent[MechMountSlot.values().length]), false)),
                    -0.5f, MechFieldRepair.NEUTRAL);
        }
        // Recycling frames stand aboard a ship whose machine shop has its tooling in.
        boolean recycling = materialHint != null && materialHint.contains("recycle");
        MechLabViewModel viewModel = new MechLabViewModel(
                reactor, bay, recycling ? snapshotMachineShop(Reclamation.TOOLED)
                        : snapshotPurse("resource-large".equals(materialHint) ? 15_000_000 : "resource-short".equals(materialHint) ? 0 : 60_000,
                                "no-port".equals(materialHint) || materialHint != null && materialHint.startsWith("hostile-")
                                        && !CivilizedPort.trades(hostilePort(), materialHint.substring(8))));
        if (materialHint != null && materialHint.startsWith("lesson-")) {
            int step = Integer.parseInt(materialHint.substring(7));
            if (step >= 2) viewModel.guideLabLesson();
        }
        if ("lesson-5".equals(materialHint)) viewModel.dismissLabPrompt();
        if (machineName != null) {
            String name = machineName;
            viewModel.gantryRows().get().stream().filter(row -> name.equals(row.name()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No pad holds " + name))
                    .select().run();
        }
        if (selectHound) {
            viewModel.mechRows().get().stream()
                    .filter(row -> row.name().startsWith("Hound"))
                    .findFirst().orElseThrow().select().run();
            viewModel.slotRows().get().stream()
                    .filter(row -> row.name().contains("SHOULDER"))
                    .findFirst().orElseThrow().select().run();
        }
        if (materialHint != null && materialHint.startsWith("chassis-")) {
            String chassis = materialHint.substring("chassis-".length());
            viewModel.mechRows().get().stream()
                    .filter(row -> row.name().startsWith(chassis))
                    .findFirst().orElseThrow().select().run();
            viewModel.slotRows().get().get(0).select().run();
        }
        // The shoulder stripped, so the doll shows an empty socket wearing its hardpoint colour.
        if ("empty-socket".equals(materialHint)) viewModel.stripWeapon(viewModel.selectedSocket());
        if (motionFrames != null) {
            if (!selectHound) viewModel.gantryRows().get().get(0).select().run();
            else viewModel.stripWeapon(viewModel.selectedSocket());
        }
        if ("action".equals(materialHint) || "replenisher".equals(materialHint)
) {
            viewModel.gantryRows().get().get(0).select().run();
        }
        if (weldingEvidence || "repairing".equals(materialHint)) viewModel.gantryRows().get().get(0).select().run();
        if ("hover-installed".equals(materialHint) || materialHint != null && materialHint.startsWith("drag-")) {
            viewModel.gantryRows().get().get(0).select().run();
        }
        // Internals: the engine socket with an uprated engine hovered, then frame plating
        // with heavy plate hovered, so the budget line and meter ghosts show the trade.
        if (materialHint != null && materialHint.startsWith("internal-")) {
            for (MechInternal internal : MechInternal.values()) bay.learnPattern(MechPattern.of(internal).id());
        }
        if ("internal-engine".equals(materialHint)) {
            viewModel.gantryRows().get().get(0).select().run();
            viewModel.selectSlot(SocketId.CORE);
            viewModel.hoverInternal(MechInternal.UPRATED_ENGINE);
        }
        if ("internal-plating".equals(materialHint)) {
            viewModel.gantryRows().get().get(0).select().run();
            viewModel.selectPlating();
            viewModel.hoverInternal(MechInternal.HEAVY_PLATING);
        }
        if ("refit-stats".equals(materialHint)) {
            String mechId = bay.activeSquad().mechs().get(0).id();
            for (MechInternal internal : List.of(MechInternal.UPRATED_ENGINE, MechInternal.DOUBLE_CAPACITOR_BANK,
                    MechInternal.COMPOSITE_PLATING)) {
                bay.addInternal(internal.id, 1);
                bay.installInternal(mechId, internal);
            }
            CampaignMech refitted = bay.mechById(mechId);
            for (MechMountSlot arm : refitted.variant().armMounts()) {
                bay.addWeapon(MechWeaponComponent.AC_6_QS.id, 1);
                bay.installWeapon(mechId, arm, MechWeaponComponent.AC_6_QS.id);
            }
            viewModel.gantryRows().get().get(0).select().run();
            viewModel.selectSlot(SocketId.CORE);
        }
        // A tank on the fourth pad, its factory bank, a vent array and a liner in its auxiliary compartment and a ready
        // rack spare in stores: the sealed doll and the two compartment bays, with the stores listing the parts.
        if ("vehicle-compartment".equals(materialHint) || "vehicle-compartment-recycle".equals(materialHint)) {
            CampaignVehicle tank = new CampaignVehicle("support_vehicle_04", "Breaker 04", VehicleType.BREAKTHROUGH_TANK);
            bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank);
            bay.learnPattern(MechPattern.of(MechInternal.SPALL_LINER).id());
            bay.learnPattern(MechPattern.of(MechInternal.READY_RACK).id());
            bay.addInternal(MechInternal.SPALL_LINER.id, 1);
            bay.addInternal(MechInternal.READY_RACK.id, 1);
            bay.fitRackPart(tank.id(), MechInternal.SPALL_LINER);
            bay.addInternal(MechInternal.AUXILIARY_VENT_ARRAY.id, 1);
            bay.fitRackPart(tank.id(), MechInternal.AUXILIARY_VENT_ARRAY);
            bay.addInternal(MechInternal.COMPOSITE_PLATING.id, 1);
            bay.fitPlating(tank.id(), MechInternal.COMPOSITE_PLATING);
            viewModel.gantryRows().get().get(3).select().run();
        }
        // Spare assemblies in stores, each with its RECYCLE and green recovery; one card armed.
        if ("recycle-weapons".equals(materialHint)) {
            bay.addWeapon(MechWeaponComponent.SRM_5.id, 2);
            bay.addWeapon(MechWeaponComponent.LRM_5.id, 1);
            viewModel.gantryRows().get().get(0).select().run();
            viewModel.selectSlot(SocketId.RIGHT_SHOULDER);
        }
        if (recycling) {
            String armed = "recycle-weapons".equals(materialHint)
                    ? MechWeaponComponent.SRM_5.id : MechInternal.READY_RACK.id;
            viewModel.catalogRows().get().stream()
                    .filter(row -> row.card().id().equals("mech-catalog:" + armed))
                    .findFirst().orElseThrow().card().recycle().action().run();
        }
        // An APC with a spall liner: the seats bay card beside the turret and hull.
        if ("vehicle-apc".equals(materialHint)) {
            CampaignVehicle apc = new CampaignVehicle("support_vehicle_04", "Bastion 04", VehicleType.HEAVY_APC);
            bay.addVehicle(MechBay.STARTER_SQUAD_ID, apc);
            bay.learnPattern(MechPattern.of(MechInternal.SPALL_LINER).id());
            bay.addInternal(MechInternal.SPALL_LINER.id, 1);
            bay.fitRackPart(apc.id(), MechInternal.SPALL_LINER);
            viewModel.gantryRows().get().get(3).select().run();
        }
        // A tank with an HC-20 fitted in its gun card and an HC-10 spare, the gun card selected:
        // the stores list the weapons the cannon card takes.
        if ("vehicle-gun".equals(materialHint)) {
            CampaignVehicle tank = new CampaignVehicle("support_vehicle_04", "Breaker 04", VehicleType.BREAKTHROUGH_TANK);
            bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank);
            bay.addWeapon(MechWeaponComponent.HC_20.id, 1);
            bay.addWeapon(MechWeaponComponent.HC_10.id, 1);
            bay.fitGun(tank.id(), MechWeaponComponent.HC_20);
            viewModel.gantryRows().get().get(3).select().run();
            viewModel.selectSlot(SocketId.ARMS);
        }
        if ("internal-flux".equals(materialHint)) {
            viewModel.gantryRows().get().get(0).select().run();
            viewModel.selectSlot(SocketId.AUXILIARY);
        }
        // The engine dragged back to stores: the empty, required socket warns on the doll, and
        // the stores' engine category lists the engine that came out at full strength.
        if ("internal-empty-engine".equals(materialHint)) {
            viewModel.gantryRows().get().get(0).select().run();
            viewModel.removeInstalledInternal(SocketId.CORE);
            // The engine category: the stripped engine is a spare beside patterns with none.
            viewModel.setFilter(MechLabViewModel.CategoryFilter.ENGINE);
        }
        // A two-machine reserve hangar: STORE armed over the first pad; a Warden and a tank stored, the
        // lance back on its overview with the Reserve strip listing them; and the Warden recalled.
        if (materialHint != null && materialHint.startsWith("reserve-")) {
            viewModel.reserve(new ReserveHangar(), () -> 2);
            Map<String, Object> reserveProps = new HashMap<>();
            viewModel.putReserveProps(reserveProps);
            Runnable store = (Runnable) reserveProps.get("storeAction");
            if ("reserve-store".equals(materialHint)) {
                viewModel.gantryRows().get().get(0).select().run();
                store.run();
            } else {
                bay.addVehicle(MechBay.STARTER_SQUAD_ID,
                        new CampaignVehicle("support_vehicle_04", "Breaker 04", VehicleType.BREAKTHROUGH_TANK));
                for (String machine : List.of("Warden 03", "Breaker 04")) {
                    viewModel.gantryRows().get().stream().filter(row -> machine.equals(row.name()))
                            .findFirst().orElseThrow().select().run();
                    store.run();
                    store.run();
                }
                if ("reserve-recalled".equals(materialHint)) viewModel.reserveRows().get(0).recall().run();
            }
        }
        if (selectVacant) viewModel.previousGantryAction().run();
        if (materialHint != null && materialHint.startsWith("preview-")) {
            viewModel.previewPattern(materialHint.substring("preview-".length()));
        }
        if (pickerOpen) viewModel.openAssetPickerAction().run();
        // The lab is photographed aboard the same ship it is in game. There is
        // no substitute garage to photograph instead, and a snapshot of one
        // would be evidence about a room the player never sees.
        CompanyDeck ship = new CompanyDeck(TestHulls.secondWind(context.starsectorCore()), SHIP_SEED, null, viewModel::gantryVariants,
                List::of, () -> null).vehicles(viewModel::gantryVehicles);
        readied(ship);
        ship.advance(18f);
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
                            viewModel::fittingFocused,
                            ship::scene,
                            () -> framing.lookingAt(camera.pose().worldX(),
                                    camera.pose().worldY(), camera.pose().zoomNotches()),
                            () -> ship.scene().berthsIn(vehicleBay), vehicleBay::id,
                            () -> seconds[0],
                            viewModel::hoveredWeapon)
                    .withOccupiedGantries(viewModel::occupiedGantries)
                    .withSideShift(() -> camera.pose().sideShift())
                    .withPadPreview(viewModel::padPreview);
            fittingCanvas.installedInternals(viewModel::installedInternal);
            fittingCanvas.installedWeapons(viewModel::installedWeapon);
            fittingCanvas.installedCompartment(viewModel::installedCompartment);
            fittingCanvas.selectedVehicle(viewModel::selectedVehicleType);
            fittingCanvas.installedRack(viewModel::installedRack);
            fittingCanvas.rackIncomplete(viewModel::rackIncomplete);
            document.canvases().set(instance.requireElement("mech-doll-canvas"), fittingCanvas);
            for (MechLabViewModel.SquadRow squad : viewModel.squadRows().get()) {
                for (MechLabViewModel.LanceSlot slot : squad.slots()) {
                    document.canvases().set(instance.requireElement(slot.id()), new LanceSlotCanvas(
                            slot.letter(), slot.mech(), slot.vehicle(), MechLabDollCanvas::headlessAssets));
                }
            }
            for (MechLabViewModel.MechRow row : viewModel.mechRows().get()) {
                document.canvases().set(instance.requireElement(row.previewId()), new LanceSlotCanvas(
                        row.letter(), row.mech(), row.vehicle(),
                        MechLabDollCanvas::headlessAssets));
            }
            for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
                if (row.chassisPreview() != null) {
                    document.canvases().set(instance.requireElement(row.previewId()),
                            new MechChassisPreviewCanvas(CampaignMech.frame(row.id(), row.chassisPreview()),
                                    MechLabDollCanvas::headlessAssets));
                } else if (row.vehiclePreview() != null) {
                    document.canvases().set(instance.requireElement(row.previewId()),
                            new VehiclePreviewCanvas(row.vehiclePreview()));
                } else if (row.weaponPreview() != null || row.internalPreview() != null) {
                    document.canvases().set(instance.requireElement(row.previewId()),
                            MechEquipmentGridCanvas.catalog(row.weaponPreview(), row.internalPreview(),
                                    MechLabDollCanvas::headlessAssets));
                }
            }
            for (MechLabViewModel.SlotRow row : viewModel.selectedSlotRows().get()) {
                document.canvases().set(instance.requireElement(row.gridId()),
                        new MechEquipmentGridCanvas(
                                () -> viewModel.socketDefinition(row.socketId()),
                                () -> viewModel.installedWeapon(row.socketId()),
                                () -> viewModel.compartmentPlacements(row.socketId()),
                                () -> viewModel.installedInternal(row.socketId()),
                                () -> viewModel.socketOccupied(row.socketId()),
                                MechLabDollCanvas::headlessAssets));
            }
            // The guide mounts a pointer-active callout before its first update.
            // Initialize its real lesson state before photographing a catalog hover.
            var initialViewport = UiViewport.relative(0, 0, width / uiScale, height / uiScale, uiScale,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            document.layout(initialViewport.documentWidth(), initialViewport.documentHeight());
            guide.update();
            if (specSheet || materialHint != null && !"long-name".equals(materialHint)
                    && !materialHint.startsWith("lesson-") && !materialHint.startsWith("resource-")
                    && !materialHint.startsWith("internal-") && !materialHint.startsWith("chassis-")
                    && !materialHint.startsWith("refit-") && !materialHint.startsWith("preview-")
                    && !materialHint.startsWith("vehicle-") && !materialHint.startsWith("hover-")
                    && !materialHint.startsWith("drag-") && !materialHint.startsWith("reserve-")
                    && !materialHint.startsWith("machine-")) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                List<MechLabViewModel.CatalogRow> rows = viewModel.catalogRows().get();
                MechLabSpecSheets.bindCatalogRows(binder, instance, rows);
                MechLabSpecSheets.bindMaterials(binder, instance, viewModel);
                MechLabSpecSheets.bindActions(binder, instance, viewModel);
                // A row with nothing to build shows no button; its sheet then hangs off the name.
                String anchor = "action".equals(materialHint) ? actionOrName(rows.get(0))
                        : "replenisher".equals(materialHint) ? rows.get(Math.min(1, rows.size() - 1)).nameId()
                        : "stock".equals(materialHint) ? viewModel.materialStock().get().get(0).id()
                        : "cost".equals(materialHint) ? rows.stream().flatMap(row -> row.materials().stream())
                        .findFirst().orElseThrow().id() : MechLabSpecSheets.firstSpecSheetAnchorId(rows);
                openSpecSheet(document, binder, width, height, instance.requireElement(anchor));
            }
            // Lay out once and fit the bay view's surface to its box, as the screen does each frame.
            renderRelative(renderer, document, width, height, uiScale);
            instance.requireElement("mech-doll-canvas").fitCanvasToBox();
            if (motionFrames != null) {
                FittingDragGhost ghost = new FittingDragGhost();
                UiElement ghostElement = instance.requireElement("mech-drag-ghost");
                document.canvases().set(ghostElement, ghost);
                for (int frame = 0; frame < (selectHound ? 44 : 1); frame++) {
                    seconds[0] = frame * .1;
                    if (frame >= 12 && frame < 28) {
                        fittingCanvas.previewWeaponDrag(MechWeaponComponent.SRM_5, null,
                                viewModel.selectedSocket(), viewModel.canDropCatalogWeapon(
                                        MechWeaponComponent.SRM_5, viewModel.selectedSocket()));
                    } else if (frame >= 28 && frame < 40) {
                        fittingCanvas.previewWeaponDrag(MechWeaponComponent.HC_20,
                                null, viewModel.selectedSocket(), false);
                    } else fittingCanvas.clearWeaponDragPreview();
                    // A controlled carried-item pose; the well state above is a preview, not a campaign commit.
                    // As on the screen, a part snapped into its socket has no ghost on the pointer.
                    if (frame >= 12 && frame < 40 && !fittingCanvas.carriedSnapped()) {
                        var box = instance.requireElement("mech-doll-canvas").box().contentBox();
                        MechWeaponComponent carried = frame < 28 ? MechWeaponComponent.SRM_5
                                : MechWeaponComponent.HC_20;
                        ghost.show((c, x, y) -> MechHardware.drawCarried(c, carried, x, y),
                                box.x() + box.width() * .82f, box.y() + box.height() * .25f);
                    } else ghost.clear();
                    document.canvases().invalidate(ghostElement);
                    document.canvases().invalidate(instance.requireElement("mech-doll-canvas"));
                    document.advance(.1f);
                    motionFrames.add(renderRelative(renderer, document, width, height, uiScale));
                }
                return motionFrames.get(Math.min(5, motionFrames.size() - 1));
            }
            // A stores part in hand over the doll, shown as MechLabScreen shows it: snapped into a
            // socket that takes it, or on the pointer over a socket that refuses it or open floor.
            if (materialHint != null && materialHint.startsWith("drag-")) {
                showCarriedPart(document, instance, fittingCanvas, viewModel, materialHint);
            }
            // The pointer resting on a fitted missile pod, nothing in hand: the board names it.
            if ("hover-installed".equals(materialHint)) {
                float[] at = null;
                for (float y = 0f; y < 2000f && at == null; y += 4f) {
                    for (float x = 0f; x < 2000f; x += 4f) {
                        SocketId socket = fittingCanvas.socketAt(x, y);
                        MechWeaponComponent fitted = socket != null ? viewModel.installedWeapon(socket) : null;
                        if (fitted != null && fitted.hardpointType == MechWeaponComponent.HardpointType.MISSILE) {
                            at = new float[]{x + 24f, y + 40f};
                            break;
                        }
                    }
                }
                if (at == null) throw new IllegalStateException("No fitted missile pod drawn on the doll");
                UiElement doll = instance.requireElement("mech-doll-canvas");
                SpecSheetBinder binder = new SpecSheetBinder(document, SpecSheetLayer.install(document));
                MechLabSpecSheets.bindDoll(binder, document, doll, fittingCanvas, viewModel);
                // The point, back in document pixels, is where the screen's pointer would be.
                CanvasMetrics metrics = document.canvasMetrics(doll, 1f);
                document.pointerMoved(metrics.toDocumentX(at[0]), metrics.toDocumentY(at[1]));
                fittingCanvas.pointAt(at[0], at[1]);
                document.canvases().invalidate(doll);
                renderRelative(renderer, document, width, height, uiScale);
                binder.update();
                if (binder.openTarget() != doll) throw new IllegalStateException("No spec sheet for the pointed pod");
                document.advance(0f);
            }
            renderRelative(renderer, document, width, height, uiScale);
            guide.update();
            BufferedImage last = renderRelative(renderer, document, width, height, uiScale);
            if (dollProbe != null) {
                dollProbe.probe(viewModel, document.canvasMetrics(instance.requireElement("mech-doll-canvas"), 1f));
            }
            return last;
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
        return renderBoatDeck(context, renderer, width, height, openBoat, strikeABoat, NO_DEPARTURE);
    }

    private static BufferedImage renderBoatDeck(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean openBoat, boolean strikeABoat, int departure)
            throws Exception {
        return renderBoatDeck(context, renderer, width, height, openBoat, strikeABoat, departure, -1);
    }

    /**
     * @param hardpoint the hardpoint the opened boat is shown on, with an
     *     Arbalest fitted there so the doll reads a refit; -1 for her plating
     */
    private static BufferedImage renderBoatDeck(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean openBoat, boolean strikeABoat, int departure, int hardpoint)
            throws Exception {
        return renderBoatDeck(context, renderer, width, height, openBoat, strikeABoat, departure, hardpoint, null);
    }

    /** @param pointAt the doll socket the pointer rests on, by key, its part's sheet open; null for none */
    private static BufferedImage renderBoatDeck(
            SnapshotContext context, HeadlessUiRenderer renderer, int width, int height, boolean openBoat,
            boolean strikeABoat, int departure, int hardpoint, String pointAt)
            throws Exception {
        // The doll draws each turret spot where the hull's weapon slot is, which needs the install's specs.
        InstalledWeaponSlots.install();
        try {
            return renderBoatDeckWithSlots(context, renderer, width, height, openBoat, strikeABoat,
                    departure, hardpoint, pointAt);
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private static BufferedImage renderBoatDeckWithSlots(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean openBoat, boolean strikeABoat, int departure, int hardpoint,
            String pointAt) throws Exception {
        Reactor reactor = new Reactor();
        BoatDeck[] campaign = new BoatDeck[1];
        // The berth struck has to be one the camera is looking at, and the page
        // opens on her first hangar. Larger programs can have additional bays,
        // so derive a visible berth rather than assuming campaign numbering.
        int[] struck = {-1};
        ReserveHangar reserve = new ReserveHangar();
        CompanyDeck ship = companyShip(context, List::of, List::of, hull -> {
            campaign[0] = boatsAboard(hull);
            if (departure == RESERVE_STRIP) {
                reserve.storeBoat(campaign[0], campaign[0].boats().get(lastBerthInHerFirstHangar(hull)).id(), 2);
            }
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
            workshop.fabricatePart(BoatFitting.REINFORCED_PLATING.id());
            workshop.fit(deck.boats().get(0).id(), BoatFitting.REINFORCED_PLATING.id());
        }
        if (deck.berths() > 1 && deck.boats().get(1) != null) {
            workshop.fabricatePart(BoatFitting.TUNED_DRIVE.id());
            workshop.fit(deck.boats().get(1).id(), BoatFitting.TUNED_DRIVE.id());
        }
        // The hold is the room's rather than the yard's: the refits above are
        // already paid for, and what this photographs is a bill the company
        // cannot meet.
        FabricationResources resources = departure == NO_PORT ? snapshotPurse(60_000, true)
                : strikeABoat ? snapshotHoldShortOfCredits()
                : departure == RECYCLING ? snapshotMachineShop(Reclamation.TOOLED)
                : departure == RECYCLING_HALF ? snapshotMachineShop(Reclamation.MACHINERY)
                : departure == SCRAP_ARMED ? snapshotMachineShop(Reclamation.FULL) : snapshotMechResources();
        if (departure == RECYCLING || departure == RECYCLING_HALF) {
            // Spares in stores under a tooled machine shop: each card carries RECYCLE and its recovery.
            workshop.fabricatePart(BoatFitting.TUNED_DRIVE.id());
            workshop.fabricatePart(BoatFitting.REINFORCED_PLATING.id());
            workshop.fabricatePart(BoatFitting.REINFORCED_PLATING.id());
            workshop.fabricatePart(BoatGun.ARBALEST.id());
        }
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                reactor, deck, resources, "Valkyrie");
        // A two-machine reserve hangar for its own shots; the others are aboard a ship without the refit.
        viewModel.reserve(reserve, () -> departure == STORE_ARMED || departure == RESERVE_STRIP ? 2 : 0);
        if (hardpoint >= 0 && deck.berths() > 0 && deck.boats().get(0) != null) {
            workshop.fabricatePart(BoatGun.ARBALEST.id());
            workshop.fitGun(deck.boats().get(0).id(), hardpoint, BoatGun.ARBALEST.id());
            // Her other two turrets carry the other families, a missile pod and a laser.
            workshop.fabricatePart(BoatGun.SRM_15.id());
            workshop.fitGun(deck.boats().get(0).id(), 1, BoatGun.SRM_15.id());
            workshop.fabricatePart(BoatGun.TWIN_PE_1.id());
            workshop.fitGun(deck.boats().get(0).id(), 2, BoatGun.TWIN_PE_1.id());
        }
        if (departure == POD && deck.berths() > 0 && deck.boats().get(0) != null) {
            // A second gun beside each 2x1 gun already on: her SRM pod and her laser each become a pod of two.
            workshop.fabricatePart(BoatGun.SRM_15.id());
            workshop.fitGun(deck.boats().get(0).id(), 1, BoatHardpoint.BOTTOM, BoatGun.SRM_15.id());
            workshop.fabricatePart(BoatGun.TWIN_PE_1.id());
            workshop.fitGun(deck.boats().get(0).id(), 2, BoatHardpoint.BOTTOM, BoatGun.TWIN_PE_1.id());
        }
        if (departure == STRIPPED && deck.berths() > 0 && deck.boats().get(0) != null) {
            // Her drive, plating and gun dragged off into stores: the drive's socket warns and she is off the lift;
            // the plating's reads a bare hull, which flies weak.
            workshop.strip(deck.boats().get(0).id(), BoatFittingSlot.DRIVE);
            workshop.strip(deck.boats().get(0).id(), BoatFittingSlot.PLATING);
            workshop.stripGun(deck.boats().get(0).id(), 0);
        }
        // The parked hulls wear what the yard put on them, as the room's refit hands them over.
        ship.doorGuns(deck::doorGuns).refitDoorGuns();
        if (openBoat) viewModel.selectBoatAction(0).run();
        if (hardpoint >= 0) viewModel.selectDollSlot(BoatDoll.HARDPOINT_KEY + hardpoint);
        if (departure == STRIPPED) viewModel.selectDollSlot(BoatDoll.slotKey(BoatFittingSlot.DRIVE));
        if (struck[0] >= 0) viewModel.selectBoatAction(struck[0]).run();
        DeckGraph.Compartment bay = hangars.isEmpty() ? null : hangars.get(0);
        if (departure == DOOR_OPENING) seekDoorOpening(ship, bay);
        // The first press of SCRAP, under a shop with its reclamation line: what comes back is shown.
        if (departure == SCRAP_ARMED) viewModel.scrapAction().run();
        // The first press of STORE: the boat is named and nothing has moved yet.
        if (departure == STORE_ARMED) {
            Map<String, Object> reserveProps = new HashMap<>();
            viewModel.putReserveProps(reserveProps);
            ((Runnable) reserveProps.get("storeAction")).run();
        }
        if (departure == RECYCLING || departure == RECYCLING_HALF) {
            viewModel.selectDollSlot(BoatDoll.slotKey(BoatFittingSlot.PLATING));
            // The first press arms the card, which is the review the owner sees before committing.
            viewModel.catalogRows().get().stream()
                    .filter(row -> BoatFitting.REINFORCED_PLATING.id().equals(row.itemId()))
                    .findFirst().orElseThrow().card().recycle().action().run();
        }
        if (departure == AT_OPENING) seekHangarDeparture(ship, bay);

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), BOAT_DECK_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, "boat-deck", props(viewModel, ship, bay, hangars.size()))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            BoatDeckCanvas bayCanvas = new BoatDeckCanvas(ship, () -> bay,
                    viewModel::selectedBerthIndex, () -> 0,
                    berth -> berth < 0 || berth >= deck.berths()
                            || deck.boats().get(berth) != null)
                    .doll(() -> viewModel.doll().get(), () -> 0d);
            UiElement bayElement = instance.requireElement("boat-deck-canvas");
            document.canvases().set(bayElement, bayCanvas);
            BoatDeckScreen.wireCatalogPreviews(instance, document, viewModel.catalogRows().get());
            // As the room does each frame: the bay draws one surface unit to a pixel, not stretched.
            document.layout(width, height);
            BoatDeckCanvas.fitSurface(bayElement);
            if (pointAt != null) {
                // Drawn once so the doll knows where its sockets are, then pointed at as the room is.
                renderRelative(renderer, document, width, height, 1f);
                float[] at = null;
                for (float y = 0f; y < 2200f && at == null; y += 4f) {
                    for (float x = 0f; x < 4000f; x += 4f) {
                        if (pointAt.equals(bayCanvas.slotAt(x, y))) {
                            at = new float[]{x + 24f, y + 30f};
                            break;
                        }
                    }
                }
                if (at == null) throw new IllegalStateException("No doll socket drawn for " + pointAt);
                SpecSheetBinder binder = new SpecSheetBinder(document, SpecSheetLayer.install(document));
                BoatDeckSpecSheets.bindDoll(binder, document, bayElement, () -> bayCanvas, viewModel::selectedPattern);
                CanvasMetrics metrics = document.canvasMetrics(bayElement, 1f);
                document.pointerMoved(metrics.toDocumentX(at[0]), metrics.toDocumentY(at[1]));
                bayCanvas.pointAt(at[0], at[1]);
                document.canvases().invalidate(bayElement);
                renderRelative(renderer, document, width, height, 1f);
                binder.update();
                if (binder.openTarget() != bayElement) throw new IllegalStateException("No spec sheet for " + pointAt);
                document.advance(0f);
            }
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    /**
     * No boat leaving; a boat whose door is part open; a boat crossing the opening; or none
     * leaving, with spares in stores under a tooled machine shop and one RECYCLE armed; or
     * the same under a shop whose machinery runs without tooling; or the opened boat's SCRAP
     * armed under a shop with its reclamation line; the opened boat's STORE armed; or the deck
     * overview with one boat in the reserve hangar and her berth vacant; or the fleet away from a port.
     */
    private static final int NO_DEPARTURE = 0, DOOR_OPENING = 1, AT_OPENING = 2, RECYCLING = 3, RECYCLING_HALF = 4,
            STRIPPED = 5, SCRAP_ARMED = 6, STORE_ARMED = 7, RESERVE_STRIP = 8, NO_PORT = 9, POD = 10;

    /** Catch the bay door half open for a real departure, before the boat reaches it. */
    private static void seekDoorOpening(CompanyDeck ship, DeckGraph.Compartment bay) {
        var leaves = ship.scene().bayDoorLeaves(bay);
        if (leaves == null) throw new IllegalStateException("Snapshot hangar has no opening to space");
        for (int step = 0; step < 3600 && leaves.openness() > 0f; step++) ship.advance(0.05f);
        for (int step = 0; step < 36000; step++) {
            ship.advance(0.05f);
            if (leaves.openness() >= 0.5f) return;
        }
        throw new IllegalStateException("The bay door never opened during the evidence window");
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

    /** A purse that cannot pay for an Aeroshuttle by a credit. */
    private static FabricationResources snapshotHoldShortOfCredits() {
        return new TestPurse(BoatFabricationCatalog.recipe(ShuttleType.AEROSHUTTLE).bill().credits() - 1);
    }

    /** The ordinary snapshot purse: a company with credits, docked at a civilized port. */
    private static TestPurse snapshotMechResources() { return snapshotPurse(60_000, false); }

    /** @param away whether the fleet is out of port, where nothing can be bought */
    /** A hostile faction's market with a black market, as vanilla's trade mode sees it. */
    private static MarketAPI hostilePort() {
        return (MarketAPI) Proxy.newProxyInstance(MarketAPI.class.getClassLoader(), new Class<?>[]{MarketAPI.class},
                (proxy, method, args) -> method.getName().equals("hasSubmarket") ? (Object) true : null);
    }

    private static TestPurse snapshotPurse(long credits, boolean away) {
        TestPurse purse = new TestPurse(credits);
        return away ? purse.away() : purse;
    }

    /** The ordinary snapshot purse aboard a ship whose machine shop recycles at {@code rate}. */
    private static FabricationResources snapshotMachineShop(Reclamation rate) {
        TestPurse purse = snapshotMechResources().reclaiming(rate);
        purse.quote = base -> WorkshopTooling.quote(base, rate == Reclamation.TOOLED);
        return purse;
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
            @Override public long credits() { return 60_000; }
            @Override public boolean spend(SquadFoundingCost cost) { return cost != null; }
            @Override public String commodityName(String commodityId) {
                return snapshotCommodities().commodityName(commodityId);
            }
            @Override public String commodityIcon(String commodityId) {
                return snapshotCommodities().commodityIcon(commodityId);
            }
        };
    }

    static CommodityPresentation snapshotCommodities() {
        return new CommodityPresentation() {
            @Override public String commodityName(String commodityId) {
                return switch (commodityId) {
                    case Commodities.SUPPLIES -> "Supplies";
                    case Commodities.HAND_WEAPONS -> "Heavy Armaments";
                    case Commodities.HEAVY_MACHINERY -> "Heavy Machinery";
                    case Commodities.FOOD -> "Food";
                    case Commodities.METALS -> "Metals";
                    case Commodities.RARE_METALS -> "Transplutonics";
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
                    case Commodities.METALS -> "graphics/icons/cargo/materials.png";
                    case Commodities.RARE_METALS -> "graphics/icons/cargo/raremetals.png";
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

    private static Map<String, Object> props(BarracksViewModel viewModel,
                                             CompanyDeck ship) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", ShipBreadcrumb.of(ship.ship(), null));
        props.put("command", viewModel.command().props());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BARRACKS,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }

    private static Map<String, Object> props(FleetArmoryViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("testLoadout", (Runnable) () -> {});
        props.put("rangeDisabled", !viewModel.rangeUnavailableReason().isEmpty());
        props.put("rangeHint", viewModel.rangeUnavailableReason().isEmpty()
                ? Strings.get("armoryRangeHint") : viewModel.rangeUnavailableReason());
        props.put("firstRefitClasses", "hidden");
        props.put("openFirstRefit", (Runnable) () -> { });
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
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
        props.put("issueCargoRows", viewModel.issueCargoRows());
        props.put("issueCargoClasses", viewModel.issueCargoClasses());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
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
        var reactor = new Reactor();
        var survey = snapshotShipCondition(reactor, ship);
        props.put("survey", survey.row());
        props.put("workshop", workshop);
        props.put("roomTitle", "SECOND WIND / RESTORATION");
        props.put("roomCopy", "Inspect the workshop. Restoring her rooms keeps their place in the hull.");
        props.put("contextLabel", "COMPANY SHIP / MACHINE SHOP");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW, MarineOpsPageNav.ANY_SHIP,
                () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
        try (var instance = loader.build(reactor, "ship-view", props)) {
            var document = new UiDocument(instance.root());
            instance.styles().forEach(document::addStyleSheet);
            document.theme(MarineOpsThemes.standard());
            var canvas = new ShipViewCanvas(ship);
            if (focusWorkshop) canvas.focus(RoomPurpose.PARTS_CAGE);
            document.canvases().set(instance.requireElement("ship-view-deck"), canvas);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            var overlay = new ShipConditionOverlay(document, instance, survey);
            renderRelative(renderer, document, 1920, 1080, scale);
            var room = ship.rooms().largest(RoomPurpose.PARTS_CAGE);
            if (workshop.offer().state() != WorkshopState.NONE && room != null) overlay.select(room.id());
            overlay.update(0f, ship, canvas);
            return renderRelative(renderer, document, 1920, 1080, scale);
        }
    }

    private static ShipConditionModel snapshotShipCondition(Reactor reactor, CompanyDeck ship) {
        return new ShipConditionModel(reactor, () -> ship,
                work -> new CompanyShipRestoration.Offer(ship.ship().restoration(), 20000, null),
                work -> "Snapshot only", () -> new CompanyBayRestoration.Offer(true, 20000, null),
                () -> "Snapshot only", ignored -> {}, () -> {});
    }
    public static BufferedImage renderShipCondition(SnapshotContext context, CompanyDeck ship,
                                                    ShipConditionModel model, Reactor reactor,
                                                    float scale, RoomPurpose focus) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), SHIP_VIEW_COMPONENTS);
        loader.reload();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("survey", model.row());
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
            var overlay = new ShipConditionOverlay(document, instance, model);
            renderRelative(renderer, document, 1920, 1080, scale);
            overlay.select(model.selectedRoomId());
            overlay.update(0f, ship, canvas);
            return renderRelative(renderer, document, 1920, 1080, scale);
        }
    }

    /** The boat deck over its second bay; a sealed bay only says where it is restored. */
    public static BufferedImage renderBayRestoration(SnapshotContext context, CompanyDeck ship,
                                                      boolean restored, float scale) throws Exception {
        Reactor reactor = new Reactor();
        BoatDeck boats = new BoatDeck();
        boats.reconcile("starter", ShuttleType.HERMES, 1);
        if (restored) boats.reconcile("starter", ShuttleType.HERMES, 2);
        BoatDeckViewModel model = new BoatDeckViewModel(reactor, boats, snapshotMechResources(), "Second Wind");
        if (restored) model.selectBoatAction(1).run();
        var bay = ship.rooms(RoomPurpose.HANGAR).get(1);
        var properties = props(model, ship, bay, 2);
        properties.put("activeBayLabel", restored ? "02/02" : "02/02 SEALED");
        properties.put("bayNoteClasses", restored ? "hidden" : "bay-sealed-note label tone-muted");
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
        props.put("recovery", viewModel.recovery());
        props.put("selectedBoatName", viewModel.selectedBoatName());
        props.put("selectedBoatIdentity", viewModel.selectedBoatIdentity());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("readinessRows", viewModel.readinessRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("filterPills", viewModel.filterPills());
        props.put("filterClasses", viewModel.filterClasses());
        props.put("backClasses", viewModel.backClasses());
        props.put("stageTitle", viewModel.stageTitle());
        props.put("stageStatus", viewModel.stageStatus());
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
        props.put("bayNoteClasses", "hidden");
        props.put("fabricate", viewModel.fabricateAction());
        props.put("backToDeck", viewModel.backToDeckAction());
        props.put("scrapLabel", viewModel.scrapLabel());
        props.put("scrapClasses", viewModel.scrapClasses());
        props.put("scrapAction", viewModel.scrapAction());
        viewModel.putReserveProps(props);
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
        props.put("garageTitle", viewModel.garageTitle());
        props.put("garageStatus", viewModel.garageStatus());
        props.put("dragNoteTitle", viewModel.dragNoteTitle());
        props.put("dragNoteCopy", viewModel.dragNoteCopy());
        props.put("scrapLabel", viewModel.scrapLabel());
        props.put("scrapClasses", viewModel.scrapClasses());
        props.put("scrapAction", viewModel.scrapAction());
        viewModel.putReserveProps(props);
        viewModel.putBuildPanelProps(props);
        props.put("performanceMeters", viewModel.performanceMeters());
        viewModel.putInternalProps(props);
        viewModel.putRepairProps(props);
        props.put("leftSlotRows", viewModel.leftSlotRows());
        props.put("rightSlotRows", viewModel.rightSlotRows());
        props.put("slotRows", viewModel.selectedSlotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("selectedSlotRule", viewModel.selectedSlotRule());
        props.put("materialStock", viewModel.materialStock());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("pickerClasses", viewModel.pickerClasses());
        props.put("workspaceClasses", viewModel.workspaceClasses());
        props.put("fittingHeaderClasses", viewModel.fittingHeaderClasses());
        props.put("backToLance", viewModel.overviewAction());
        props.put("performanceClasses", viewModel.performanceClasses());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("slotRackClasses", viewModel.slotRackClasses());
        props.put("overviewRailClasses", viewModel.overviewRailClasses());
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("categoryFilterPills", viewModel.categoryFilterPills());
        props.put("categoryFilterClasses", viewModel.categoryFilterClasses());
        props.put("allPartsClasses", viewModel.allPartsClasses());
        props.put("showAllParts", (Runnable) viewModel::showAllParts);
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }
}
