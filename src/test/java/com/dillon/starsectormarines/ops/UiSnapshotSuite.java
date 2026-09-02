package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CommodityPresentation;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.MarineRoster;
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
import com.dillon.starsectormarines.battle.mech.MechLanceOrder;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ui.panel.TaskForceStatusPanel;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
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
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;
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
            "data/ui/components/armory/armory-squad-doctrine.mlx",
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
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessBattleSceneRenderer battleScenes =
                new HeadlessBattleSceneRenderer(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(battleScenes,
                context.modRoot(), context.starsectorCore());
        return List.of(
                new SnapshotArtifact("mission-catalog-debug-wide.png",
                        renderMissionSelect(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("mission-briefing-first-contract-wide.png",
                        renderMissionBriefing(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, false)),
                new SnapshotArtifact("mission-briefing-conquest-wide.png",
                        renderMissionBriefing(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, false)),
                new SnapshotArtifact("mission-briefing-conquest-debug-expanded-wide.png",
                        renderMissionBriefing(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true, true)),
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
                // The one state the shell can be in that nothing else draws:
                // her deck is still being laid out, so the rooms aboard read as
                // pending rather than as places the hull does not have.
                new SnapshotArtifact("company-hq-getting-ready-wide.png",
                        renderCompanyHq(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, 1f,
                                CompanyHqViewModel.gettingReadyPreview())),
                new SnapshotArtifact("company-hq-bridge-low-resolution.png",
                        renderCompanyHq(context, renderer, 1163, 625, 1f)),
                new SnapshotArtifact("company-hq-bridge-ui-scale-150.png",
                        renderCompanyHq(context, renderer, 1744, 938, 1.5f)),
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
                new SnapshotArtifact("fleet-armory-overview-low-resolution.png",
                        renderFleetArmoryOverview(
                                context, renderer, 1163, 625)),
                new SnapshotArtifact("fleet-armory-squads-wide.png",
                        renderFleetArmoryWorkspace(
                                context, renderer, FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT,
                                false, false)),
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
                new SnapshotArtifact("fleet-armory-equipment-designer-low-resolution.png",
                        renderEquipmentDesigner(context, renderer, 1163, 625)),
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
                new SnapshotArtifact("mech-lab-low-resolution.png",
                        renderMechLab(context, renderer, 1163, 625, 1f)),
                new SnapshotArtifact("mech-lab-ui-scale-150.png",
                        renderMechLab(context, renderer, 1744, 938, 1.5f)),
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
                        renderBoatDeck(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false)),
                new SnapshotArtifact("boat-deck-fitting-wide.png",
                        renderBoatDeck(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("boat-deck-vacant-wide.png",
                        renderBoatDeck(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false, true)),
                new SnapshotArtifact("battle-hud-task-force-wide.png",
                        renderBattleHudTaskForce(
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-hud-selected-squad-wide.png",
                        renderBattleSquadOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false)),
                new SnapshotArtifact("battle-hud-selected-squad-2560x1440.png",
                        renderBattleSquadOverlay(context, renderer,
                                2560, 1440, false)),
                new SnapshotArtifact("battle-hud-selected-squad-hover-wide.png",
                        renderBattleSquadOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, true)),
                new SnapshotArtifact("battle-hud-selected-mech-wide.png",
                        renderBattleMechOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT, false)),
                new SnapshotArtifact("battle-hud-selected-mech-defend-area-2560x1440.png",
                        renderBattleMechOverlay(context, renderer,
                                2560, 1440, true)),
                new SnapshotArtifact("battle-hud-command-overlay-wide.png",
                        renderBattleHudCommandOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-hud-command-overlay-low-resolution.png",
                        renderBattleHudCommandOverlay(context, renderer,
                                1163, 625)),
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
                        UnitRole.COMBATANT));
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
    private static BufferedImage renderBattleHudCommandOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
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
                previewConquestCommander());
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

    private static CommanderSnapshot<ConquestFrontSnapshot> previewConquestCommander() {
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
        ConquestFrontSnapshot front = new ConquestFrontSnapshot(
                420, 390, Faction.MARINE, TraversalAxis.SOUTH_TO_NORTH,
                ConquestFrontSnapshot.Phase.FRONT_ADJUST, 4, 140,
                CompoundService.CompoundState.DEFENDER_HELD,
                tracks, List.of(), directives);
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
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    private static BufferedImage renderMissionBriefing(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, boolean conquest,
            boolean debugExpanded) throws Exception {
        Reactor reactor = new Reactor();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), MISSION_BRIEFING_COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor,
                BriefingScreen.ROOT_COMPONENT,
                BriefingScreen.previewProps(conquest, debugExpanded))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document, width, height, 1f);
        }
    }

    private static BufferedImage renderMissionFlow(
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
        UiViewport viewport = UiViewport.relative(
                0f, 0f, width, height, 1f,
                MarineOpsUiViewport.REFERENCE_WIDTH,
                MarineOpsUiViewport.REFERENCE_HEIGHT);
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        Rect box = subject.box().borderBox();
        document.pointerMoved(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
        binder.update();
        document.advance(0f);
    }

    private static BufferedImage renderCompanyHq(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale) throws Exception {
        return renderCompanyHq(context, renderer, width, height, uiScale,
                CompanyHqViewModel.preview());
    }

    private static BufferedImage renderCompanyHq(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height, float uiScale,
            CompanyHqViewModel viewModel) throws Exception {
        Reactor reactor = new Reactor();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                context.modRoot().resolve(path)), COMPANY_HQ_COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "company-hq", viewModel.props())) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderRelative(renderer, document, width, height, uiScale);
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
                            hull.maxCrew(), hull.cargo(), hull.silhouette())));
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
        Reactor reactor = new Reactor();
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
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
        if (fireteam && pickerOpen) viewModel.weaponDoctrineTiles().get().get(1).select().run();
        if (fireteam && armorPicker) viewModel.showArmorPickerAction().run();
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
            if (fireteam) {
                for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
                    int billet = index;
                    document.canvases().set(instance.requireElement("marine-preview:" + index),
                            new ArmoryMarinePreviewCanvas(
                                    () -> viewModel.viewerBilletAt(billet),
                                    armoryPreview.assets()));
                }
            }
            if (equipmentTooltip) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                ArmorySpecSheets.bindMarineCards(binder, instance, viewModel.marineCards());
                openSpecSheet(document, binder, width, height,
                        instance.requireElement(
                                viewModel.marineCards().get().get(0).systemId()));
            }
            return renderRelative(renderer, document, width, height, 1f);
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
        Reactor reactor = new Reactor();
        MechBay bay = MechBay.legacyStarterFixture();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id()));
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_03", "Sirocco 03", MechVariant.SIROCCO,
                MechRole.LR_SUPPORT, MissileReplenisherComponent.ACCELERATED_FEED.id()));
        bay.addReplenisher(MissileReplenisherComponent.ACCELERATED_FEED.id(), 1);
        MechLabViewModel viewModel = new MechLabViewModel(
                reactor, bay, snapshotMechResources());
        if (selectHound) {
            viewModel.mechRows().get().stream()
                    .filter(row -> row.name().startsWith("Hound"))
                    .findFirst().orElseThrow().select().run();
            viewModel.slotRows().get().stream()
                    .filter(row -> row.name().contains("SHOULDER"))
                    .findFirst().orElseThrow().select().run();
        }
        if (selectVacant) viewModel.previousGantryAction().run();
        if (pickerOpen) viewModel.openAssetPickerAction().run();
        // The lab is photographed aboard the same ship it is in game. There is
        // no substitute garage to photograph instead, and a snapshot of one
        // would be evidence about a room the player never sees.
        CompanyDeck ship = companyShip(viewModel::gantryVariants, List::of);
        DeckGraph.Compartment vehicleBay = ship.room(RoomPurpose.VEHICLE_BAY);
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
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("mech-doll-canvas"),
                    new MechLabDollCanvas(viewModel::gantryDeployments,
                            viewModel::selectedGantryIndex,
                            viewModel::selectedSocket,
                            MechLabDollCanvas::headlessAssets,
                            () -> null, () -> null,
                            viewModel::fittingFocused,
                            ship::scene,
                            () -> framing.lookingAt(camera.pose().worldX(),
                                    camera.pose().worldY(), camera.pose().zoomNotches()),
                            () -> ship.scene().berthsIn(vehicleBay), vehicleBay::id,
                            () -> 0d,
                            viewModel::hoveredWeapon));
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
            if (specSheet) {
                SpecSheetBinder binder = new SpecSheetBinder(
                        document, SpecSheetLayer.install(document));
                List<MechLabViewModel.CatalogRow> rows = viewModel.catalogRows().get();
                MechLabSpecSheets.bindCatalogRows(binder, instance, rows);
                openSpecSheet(document, binder, width, height, instance.requireElement(
                        MechLabSpecSheets.firstSpecSheetAnchorId(rows)));
            }
            return renderRelative(renderer, document, width, height, uiScale);
        }
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
        Reactor reactor = new Reactor();
        BoatDeck[] campaign = new BoatDeck[1];
        // The berth struck has to be one the camera is looking at, and the page
        // opens on her first hangar. A transport spreads six berths over three
        // bays, so striking the deck's third boat would put the hole in a bay
        // nobody can see and photograph two intact ones.
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
        props.put("showArmorComparison", (Runnable) () -> { });
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

    private static Map<String, Object> props(BoatDeckViewModel viewModel,
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
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("gantryRows", viewModel.gantryRows());
        props.put("activeGantryLabel", viewModel.activeGantryLabel());
        props.put("garageTitle", viewModel.garageTitle());
        props.put("selectedMechName", viewModel.selectedMechName());
        props.put("selectedMechIdentity", viewModel.selectedMechIdentity());
        props.put("selectedMechDoctrine", viewModel.selectedMechDoctrine());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("leftSlotRows", viewModel.leftSlotRows());
        props.put("rightSlotRows", viewModel.rightSlotRows());
        props.put("slotRows", viewModel.slotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("selectedSlotRule", viewModel.selectedSlotRule());
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
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }
}
