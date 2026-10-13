package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.BattleSceneHostPass;
import com.dillon.starsectormarines.ops.battleview.BattleSceneFrame;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderContext;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.BattleRenderHostProfile;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.util.EnumSet;
import java.util.List;

/** Production ground/body/ring composition at 1920x1080, with no UI scaling. */
public final class SelectionSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "squad-selection"; }
    @Override public String label() { return "Squad selection circles"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return List.of(renderSelection(context, true), renderSelection(context, false));
    }

    private SnapshotArtifact renderSelection(SnapshotContext context, boolean group) throws Exception {
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        SceneWorld world = SceneBuilder.openGround(64, 36)
                .squad("alpha").size(6).inFile(20, 20, 2, 0).done()
                .squad("bravo").size(6).inFile(20, 16, 2, 0).done()
                .squad("hover").size(6).inFile(20, 12, 2, 0).done()
                .squad("enemy").faction(Faction.DEFENDER).size(6).inFile(38, 16, 2, 0).done()
                .build();
        try (BattleSimulation sim = world.sim()) {
            sim.getFogOfWar().tick(0, sim.getRoster());
            Selection selection = new Selection();
            selection.selectSquads(List.of(world.squadId("alpha"), world.squadId("bravo")),
                    world.squadId("alpha"), world.members("alpha")[0], false);
            if (!group) selection.selectUnit(world.squadId("alpha"), world.members("alpha")[2]);
            selection.hoverSquad(world.squadId("hover"));
            BattleSceneHostPass pass = new BattleSceneHostPass() {
                @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                    BattleCamera camera = new BattleCamera(64, 36);
                    camera.setViewport(0, 0, viewport.width(), viewport.height(), viewport.width() / 64f);
                    return new BattleSceneFrame(new RenderContext(sim, camera, null,
                            alpha, 0, false, new HighlightOverlay(), selection,
                            BattleRenderHostProfile.EMBEDDED_SCENE),
                            EnumSet.of(RenderLayer.GROUND, RenderLayer.HIGHLIGHTS, RenderLayer.UNITS));
                }
                @Override public void draw(CanvasHostViewport viewport, float alpha) {
                    throw new IllegalStateException("Headless evidence only");
                }
            };
            BufferedImage image = renderer.renderHostPass(pass, 1920, 1080);
            Reactor reactor = new Reactor();
            BattleSquadOverlayModel model = new BattleSquadOverlayModel(reactor, () -> { });
            model.update(sim, selection.getSelectedSquadId(),
                    selection.getSelectedUnitEntityId(), selection.squadCount() == 1);
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                    List.of(BattleSquadOverlay.COMPONENT_PATH));
            loader.reload();
            try (MarkupInstance markup = loader.build(reactor, BattleSquadOverlay.COMPONENT, model.props())) {
                BattleSquadOverlay.wireLayout(markup);
                UiDocument document = new UiDocument(markup.root());
                for (var style : markup.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                BufferedImage plate = renderer.render(document,
                        (int) BattleSquadOverlay.DOCUMENT_WIDTH, (int) BattleSquadOverlay.DOCUMENT_HEIGHT);
                Graphics2D graphics = image.createGraphics();
                graphics.drawImage(plate, 12, 800, null);
                graphics.dispose();
            }
            BattleOrderOverlayModel orders = new BattleOrderOverlayModel(reactor, mode -> {});
            orders.update(selection.squadCount(), false, group ? Mode.DEFEND_AREA : Mode.ATTACK_MOVE);
            MarkupLoader orderLoader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                    List.of(BattleOrderOverlay.COMPONENT_PATH));
            try (var markup = orderLoader.reloadAndBuild(reactor, BattleOrderOverlay.COMPONENT, orders.props())) {
                UiDocument document = new UiDocument(markup.root());
                for (var style : markup.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                var plate = renderer.render(document, (int) BattleOrderOverlay.WIDTH, (int) BattleOrderOverlay.HEIGHT);
                Graphics2D graphics = image.createGraphics();
                graphics.drawImage(plate, (1920 - plate.getWidth()) / 2, 1080 - 12 - plate.getHeight(), null);
                graphics.dispose();
            }
            return new SnapshotArtifact(group ? "selected-and-hovered.png" : "single-member-focus.png", image);
        }
    }
}
