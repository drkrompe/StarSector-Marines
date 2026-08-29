package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ui.panel.TaskForceStatusPanel;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.ArmoryMarinePreviewCanvas;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.VanillaHullSilhouettes;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.BarracksCanvas;
import com.dillon.starsectormarines.ops.battleview.ShipViewCanvas;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.DeckPlanCanvas;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.MechLabCameraController;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Authored retained-view evidence rendered without a Starsector process. */
public final class UiSnapshotSuite implements SnapshotSuite {

    private static final int FULL_SCREEN_WIDTH = 1920;
    private static final int FULL_SCREEN_HEIGHT = 1080;

    private static final List<String> COMPANY_HQ_COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-hq.mlx");
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
    private static final List<String> BATTLE_HUD_COMPONENTS =
            List.of(BattleHudOverlay.COMPONENT_PATH,
                    BattlePowerOverlay.COMPONENT_PATH);

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
                new SnapshotArtifact("battle-hud-task-force-wide.png",
                        renderBattleHudTaskForce(
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-hud-command-overlay-wide.png",
                        renderBattleHudCommandOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)),
                new SnapshotArtifact("battle-hud-powers-targeting-wide.png",
                        renderBattleHudPowerOverlay(context, renderer,
                                FULL_SCREEN_WIDTH, FULL_SCREEN_HEIGHT)));
    }

    /** Full-screen evidence for the production MLX command rail over battle contrast. */
    private static BufferedImage renderBattleHudCommandOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        BufferedImage image = renderBattleHudTaskForce(width, height);
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(2f, List.of(
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
                    Math.round(BattleHudOverlay.DOCUMENT_WIDTH),
                    Math.round(BattleHudOverlay.OBJECTIVE_HEIGHT));
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(overlay,
                    width - 12 - overlay.getWidth(), 12, null);
            graphics.dispose();
            return image;
        }
    }

    private static BattleHudOverlayModel.CaptureObjective capture(
            TacticalNode.Kind kind, CompoundService.CompoundState state,
            float progress) {
        return new BattleHudOverlayModel.CaptureObjective(kind, state, progress);
    }

    /** Full-screen evidence for the compact power deck in its armed state. */
    private static BufferedImage renderBattleHudPowerOverlay(
            SnapshotContext context, HeadlessUiRenderer renderer,
            int width, int height) throws Exception {
        BufferedImage image = renderBattleHudCommandOverlay(
                context, renderer, width, height);
        Reactor reactor = new Reactor();
        BattlePowerOverlayModel model = new BattlePowerOverlayModel(reactor, ignored -> { });
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

    /**
     * Full-viewport scale evidence for the production task-force plate. The
     * backdrop is only a neutral contrast field; every HUD operation, value,
     * and coordinate comes through {@link TaskForceStatusPanel#paint} — the
     * same method the live OpenGL panel invokes.
     */
    private static BufferedImage renderBattleHudTaskForce(int width, int height) {
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintBattleContrastField(graphics, width, height);

        TaskForceStatusPanel.Snapshot snapshot =
                TaskForceStatusPanel.Snapshot.capture(battleHudSquads());
        TaskForceStatusPanel.paint(new HeadlessTaskForcePaintTarget(graphics, height),
                snapshot, 12f, 56f, 1f);
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
        graphics.fillRect(78, 48, width - 156, height - 96);
        graphics.setColor(new Color(38, 45, 48));
        graphics.fillRect(width / 2 - 130, 48, 260, height - 96);
        graphics.setColor(new Color(18, 24, 29));
        graphics.fillRect(210, 170, 460, 310);
        graphics.fillRect(width - 690, 180, 480, 360);
        graphics.fillRect(680, 650, 520, 250);
        graphics.setColor(new Color(53, 63, 68));
        graphics.setStroke(new BasicStroke(1f));
        for (int x = 78; x < width - 78; x += 32) {
            graphics.drawLine(x, 48, x, height - 48);
        }
        for (int y = 48; y < height - 48; y += 32) {
            graphics.drawLine(78, y, width - 78, y);
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
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
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
        CompanyDeck ship = new CompanyDeck(TestHulls.transport(), SHIP_SEED,
                null, lance, company);
        ship.advance(18f);
        return ship;
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
                        () -> MarineOpsContext.companyMarines(roster));
        if (hull != null) ship.advance(18f);
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
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        try (MarkupInstance instance = loader.build(reactor, "ship-view", props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.canvases().set(instance.requireElement("ship-view-deck"),
                    new ShipViewCanvas(ship));
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
        Map<String, MarineSoldierStatus> postBattle = new LinkedHashMap<>();
        postBattle.put(squad.memberIds().get(0), MarineSoldierStatus.WIA);
        postBattle.put(squad.memberIds().get(MarineSquad.CAPACITY - 1),
                MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(postBattle, 100f, 1.25f);
        roster.recruitToSquad(roster.reserveSquad().id());
        FleetArmoryViewModel viewModel = new FleetArmoryViewModel(
                reactor, roster, () -> { }, () -> 100d);
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
            ArmoryEquipmentTooltips tooltips = ArmoryEquipmentTooltips.empty();
            if (fireteam) {
                instance.requireElement("transaction-feedback")
                        .align(UiAlign.STRETCH, UiAlign.CENTER);
                tooltips = ArmoryEquipmentTooltips.bind(
                        instance, viewModel.marineCards().get());
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
                UiViewport viewport = UiViewport.relative(
                        0f, 0f, width, height, 1f,
                        MarineOpsUiViewport.REFERENCE_WIDTH,
                        MarineOpsUiViewport.REFERENCE_HEIGHT);
                document.layout(viewport.documentWidth(), viewport.documentHeight());
                UiElement target = instance.requireElement(
                        viewModel.marineCards().get().get(0).primaryId());
                document.pointerMoved(
                        target.box().borderBox().x() + target.box().borderBox().width() / 2f,
                        target.box().borderBox().y() + target.box().borderBox().height() / 2f);
                tooltips.update();
                document.advance(0f);
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
        Reactor reactor = new Reactor();
        MechBay bay = new MechBay();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id()));
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_03", "Sirocco 03", MechVariant.SIROCCO,
                MechRole.LR_SUPPORT, MissileReplenisherComponent.ACCELERATED_FEED.id()));
        bay.addReplenisher(MissileReplenisherComponent.ACCELERATED_FEED.id(), 1);
        MechLabViewModel viewModel = new MechLabViewModel(reactor, bay);
        if (selectHound) {
            viewModel.mechRows().get().stream()
                    .filter(row -> row.name().startsWith("Hound"))
                    .findFirst().orElseThrow().select().run();
            viewModel.slotRows().get().stream()
                    .filter(row -> row.name().equals("R. SHOULDER"))
                    .findFirst().orElseThrow().select().run();
        }
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
                    new MechLabDollCanvas(viewModel::gantryVariants,
                            viewModel::selectedGantryIndex,
                            viewModel::selectedSocket,
                            MechLabDollCanvas::headlessAssets,
                            () -> null, () -> null,
                            viewModel::fittingFocused,
                            ship::scene,
                            () -> framing.lookingAt(camera.pose().worldX(),
                                    camera.pose().worldY(), camera.pose().zoomNotches()),
                            () -> ship.scene().berthsIn(vehicleBay), () -> 0d));
            return renderRelative(renderer, document, width, height, uiScale);
        }
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
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }

    private static Map<String, Object> props(FleetArmoryViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("companySummary", viewModel.companySummary());
        props.put("selectedSquadName", viewModel.selectedSquadName());
        props.put("squadCards", viewModel.squadCards());
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
        props.put("transactionSummary", viewModel.transactionSummary());
        props.put("transactionClasses", viewModel.transactionClasses());
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
                () -> { }, () -> { }, () -> { }, () -> { });
    }

    private static Map<String, Object> props(MechLabViewModel viewModel,
                                             CompanyDeck ship) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contextLabel", ShipBreadcrumb.of(ship.ship(),
                ship.room(RoomPurpose.VEHICLE_BAY)));
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
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }
}
