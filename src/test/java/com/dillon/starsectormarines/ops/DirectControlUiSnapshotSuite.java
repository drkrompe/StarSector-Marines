package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechLanceOrder;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimLane;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimOrigins;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import java.awt.BasicStroke;
import java.awt.geom.Line2D;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Production control HUD and composed host overlays, without a battle simulation or live host. */
public final class DirectControlUiSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "direct-control-ui"; }
    @Override public String label() { return "Direct-control HUD"; }

    private static BufferedImage aimLane(String scenario) {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(24, 30, 35)); graphics.fillRect(0, 0, 640, 360);
        NavigationGrid grid = new NavigationGrid(16, 9);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 9; y++) {
            grid.setWalkable(x, y, true);
            graphics.setColor(new Color(40, 47, 50)); graphics.drawRect(x * 40, y * 40, 40, 40);
        }
        if (scenario.equals("corner") || scenario.equals("closed-edge")) {
            int wy = scenario.equals("corner") ? 4 : 3;
            grid.setWalkable(7, wy, false);
            graphics.setColor(new Color(94, 99, 103)); graphics.fillRect(280, wy * 40, 40, 40);
        }
        float aimY = scenario.equals("corner") ? 7.5f : 3.5f;
        if (!scenario.equals("chrome-hidden")) {
            var loadout = new MechLoadoutComponent(MechVariant.BULWARK, null);
            loadout.torsoFacingDegrees = 90f;
            var origins = scenario.equals("carrier-muzzles")
                    ? DirectControlAimOrigins.mech(loadout, 0, 2.5f, 3.5f, .2f, -.1f)
                    : List.of(new DirectControlAimOrigins.Origin(2.5f, 3.5f, 2.5f, 3.5f));
            for (var origin : origins) {
                var lane = DirectControlAimLane.observed(grid, origin, 13.5f, aimY, (x, y) -> true);
                for (var stroke : DirectControlAimLane.strokes(lane, 40, 0, 0)) {
                    graphics.setColor(stroke.color()); graphics.setStroke(new BasicStroke(stroke.width()));
                    graphics.draw(new Line2D.Float(stroke.x(), stroke.y(), stroke.endX(), stroke.endY()));
                }
            }
        }
        graphics.setColor(new Color(110, 215, 255)); graphics.drawRect(86, 126, 28, 28);
        graphics.dispose(); return image;
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (String scenario : List.of("clear", "corner", "closed-edge", "chrome-hidden", "carrier-muzzles")) {
            artifacts.add(new SnapshotArtifact("aim-lane-" + scenario + ".png", aimLane(scenario)));
        }
        for (int state = 0; state < 2; state++) {
            try (var markup = BattleDirectControlOverlayTest.fixture(
                    context.modRoot(), false, state > 0, () -> {})) {
                var document = BattleDirectControlOverlayTest.document(markup);
                artifacts.add(new SnapshotArtifact(new String[]{"select", "ready"}[state]
                        + ".png", renderer.render(document,
                        (int) BattleDirectControlOverlay.DOCUMENT_WIDTH,
                        (int) BattleDirectControlOverlay.DOCUMENT_HEIGHT)));
            }
        }
        for (var carrier : List.of(BattleDirectControlStatus.Carrier.MARINE,
                BattleDirectControlStatus.Carrier.MECH, BattleDirectControlStatus.Carrier.VEHICLE)) {
            try (var fixture = BattleDirectControlHudTest.fixture(context.modRoot(),
                    BattleDirectControlHudTest.preview(carrier, false), 0, false,
                    ignored -> {}, () -> {}, () -> {})) {
                artifacts.add(new SnapshotArtifact("action-" + carrier.name().toLowerCase(Locale.ROOT)
                        + ".png", renderer.render(fixture.document(),
                        (int) BattleDirectControlOverlay.ACTION_WIDTH, (int) BattleDirectControlOverlay.ACTION_HEIGHT)));
            }
        }
        for (int state = 0; state < 4; state++) {
            try (var fixture = BattleDirectControlHudTest.fixture(context.modRoot(),
                    BattleDirectControlHudTest.abilityPreview(state), 0, false,
                    ignored -> {}, () -> {}, () -> {})) {
                String phase = new String[]{"full", "running", "empty", "recovering"}[state];
                artifacts.add(new SnapshotArtifact("action-abilities-sprint-" + phase + ".png",
                        renderer.render(fixture.document(), (int) BattleDirectControlOverlay.ACTION_WIDTH,
                                (int) BattleDirectControlOverlay.ACTION_HEIGHT)));
            }
        }
        try (var fixture = BattleDirectControlHudTest.fixture(context.modRoot(),
                BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MECH, true), 2, true,
                ignored -> {}, () -> {}, () -> {})) {
            artifacts.add(new SnapshotArtifact("action-exposed-selected.png", renderer.render(fixture.document(),
                    (int) BattleDirectControlOverlay.ACTION_WIDTH, (int) BattleDirectControlOverlay.ACTION_HEIGHT)));
            artifacts.add(new SnapshotArtifact("action-controls.png", renderer.render(fixture.actionsDocument(),
                    (int) BattleDirectControlOverlay.ACTIONS_WIDTH, (int) BattleDirectControlOverlay.ACTIONS_HEIGHT)));
        }
        artifacts.add(new SnapshotArtifact("selected-mech-1744x938-ui100.png",
                composite(renderer, context.modRoot(), 1744, 938, 1f, true)));
        artifacts.add(new SnapshotArtifact("selected-mech-1280x720-ui150.png",
                composite(renderer, context.modRoot(), 1280, 720, 1.5f, true)));
        artifacts.add(new SnapshotArtifact("selected-infantry-1366x768-ui125.png",
                composite(renderer, context.modRoot(), 1366, 768, 1.25f, false)));
        artifacts.add(new SnapshotArtifact("active-mech-1280x720-ui150.png",
                activeComposite(renderer, context.modRoot(), 1280, 720, 1.5f, false)));
        artifacts.add(new SnapshotArtifact("active-mech-paused-1744x938-ui100.png",
                activeComposite(renderer, context.modRoot(), 1744, 938, 1f, true)));
        artifacts.add(new SnapshotArtifact("active-marine-equipped-1280x720-ui150.png",
                activeComposite(renderer, context.modRoot(), 1280, 720, 1.5f,
                        BattleDirectControlHudTest.abilityPreview(1), false)));
        return artifacts;
    }

    /** Active control keeps equipment bottom-center and exit/pause in their independent top-right document. */
    private static BufferedImage activeComposite(HeadlessUiRenderer renderer, Path modRoot,
                                                  int width, int height, float uiScale,
                                                  boolean paused) throws Exception {
        return activeComposite(renderer, modRoot, width, height, uiScale,
                BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MECH, false), paused);
    }

    private static BufferedImage activeComposite(HeadlessUiRenderer renderer, Path modRoot,
                                                  int width, int height, float uiScale,
                                                  BattleDirectControlStatus.Snapshot snapshot,
                                                  boolean paused) throws Exception {
        var layout = BattleDirectControlOverlayTest.layout(width, height, uiScale);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 25, 30));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(25, 34, 39));
            for (int x = 0; x < width; x += 48) graphics.drawLine(x, 0, x, height);
            for (int y = 0; y < height; y += 48) graphics.drawLine(0, y, width, y);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            try (var fixture = BattleDirectControlHudTest.fixture(modRoot,
                    snapshot, 0, paused, ignored -> {}, () -> {}, () -> {})) {
                draw(graphics, renderer, fixture.markup(), layout.activeControl(), layout.host(), uiScale);
                draw(graphics, renderer, fixture.actionsMarkup(), layout.activeActions(), layout.host(), uiScale);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage composite(HeadlessUiRenderer renderer, Path modRoot,
                                            int width, int height, float uiScale,
                                            boolean mech) throws Exception {
        var layout = BattleDirectControlOverlayTest.layout(width, height, uiScale);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 25, 30));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(25, 34, 39));
            for (int x = 0; x < width; x += 48) graphics.drawLine(x, 0, x, height);
            for (int y = 0; y < height; y += 48) graphics.drawLine(0, y, width, y);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Reactor reactor = new Reactor();
            var hud = new BattleHudOverlayModel(reactor, ignored -> {}, "Pause", "1x", "2x", "4x");
            Map<String, Object> hudProps = hud.props();
            hudProps.put("commandPanelClasses", "conquest-command-panel");
            hudProps.put("commandPhase", "FRONT ADJUST");
            hudProps.put("commandForce", "8 SQUADS  |  79 MARINES  |  1 RESERVE");
            try (MarkupInstance markup = build(modRoot, reactor, BattleHudOverlay.COMPONENT_PATH,
                    BattleHudOverlay.COMPONENT, hudProps)) {
                BattleHudOverlay.wireLayout(markup);
                draw(graphics, renderer, markup, layout.hud(), layout.host(), uiScale);
            }
            if (mech) {
                var model = new BattleMechOverlayModel(reactor, () -> {}, (id, role) -> {},
                        (id, order) -> {}, squadId -> {});
                model.updateProjected(new BattleMechOverlayModel.MechState(3, 303L,
                        "Bulwark Lead", "Bulwark", MechRole.BALANCED, MechRole.ASSAULT,
                        MechLanceOrder.FORM_ON_LEAD, false, BattleMechOverlayModel.selectableRoles()));
                try (MarkupInstance markup = build(modRoot, reactor, BattleMechOverlay.COMPONENT_PATH,
                        BattleMechOverlay.COMPONENT, model.props())) {
                    BattleMechOverlay.wireLayout(markup);
                    draw(graphics, renderer, markup, layout.mech(), layout.host(), uiScale);
                }
            } else {
                var model = new BattleSquadOverlayModel(reactor, () -> {});
                List<BattleSquadOverlayModel.MemberState> members = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    members.add(new BattleSquadOverlayModel.MemberState(i + 1L, i / 4, i == 0,
                            i == 0 ? "Rhea Voss" : "Marine " + (i + 1), 100f, 100f,
                            40f, 40f, 12f, "Assault rifle", "AR", new Color(140, 185, 210),
                            "", "", "", "", "", "Regular", "Steady", UnitRole.COMBATANT));
                }
                model.updateProjected(new BattleSquadOverlayModel.SquadState("Squad 20", 12, 12,
                        .72f, members));
                try (MarkupInstance markup = build(modRoot, reactor, BattleSquadOverlay.COMPONENT_PATH,
                        BattleSquadOverlay.COMPONENT, model.props())) {
                    BattleSquadOverlay.wireLayout(markup);
                    draw(graphics, renderer, markup, layout.squad(), layout.host(), uiScale);
                }
            }
            try (MarkupInstance markup = BattleDirectControlOverlayTest.fixture(
                    modRoot, false, true, mech, () -> {})) {
                draw(graphics, renderer, markup, layout.control(), layout.host(), uiScale);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static MarkupInstance build(Path modRoot, Reactor reactor, String path,
                                         String component, Map<String, Object> props) throws Exception {
        MarkupLoader loader = new MarkupLoader(file -> Files.readString(modRoot.resolve(file)), List.of(path));
        loader.reload();
        return loader.build(reactor, component, props);
    }

    private static void draw(Graphics2D graphics, HeadlessUiRenderer renderer, MarkupInstance markup,
                             UiViewport viewport, UiViewport host, float uiScale) {
        BufferedImage panel = renderer.render(BattleDirectControlOverlayTest.document(markup),
                Math.round(viewport.documentWidth()), Math.round(viewport.documentHeight()));
        int x = Math.round((viewport.screenX() - host.screenX()) * uiScale);
        int y = Math.round((host.screenY() + host.height() - viewport.screenY() - viewport.height()) * uiScale);
        graphics.drawImage(panel, x, y, Math.round(viewport.width() * uiScale),
                Math.round(viewport.height() * uiScale), null);
    }
}
