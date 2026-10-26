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
import com.dillon.starsectormarines.battle.ui.highlight.FightingPositionHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.highlight.SquadMoveOrderHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
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
        return List.of(renderSelection(context, true), renderSelection(context, false),
                renderFightingPositions(context), renderLine(context, false), renderLine(context, true),
                renderAttached(context), renderMixedGroup(context, false), renderMixedGroup(context, true));
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
            orders.update(selection.squadCount(), false, group ? Mode.DEFEND_AREA : Mode.NONE);
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

    /**
     * A selected squad that has been clicked onto a trench and dug in, a second selected-nothing squad
     * already dug in on a neighbouring trench, and one trench nobody holds: the overlay's three marks.
     */
    private SnapshotArtifact renderFightingPositions(SnapshotContext context) throws Exception {
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        SceneWorld world = SceneBuilder.openGround(64, 36)
                .squad("alpha").size(6).inFile(16, 24, 1, 0).done()
                .squad("bravo").size(6).inFile(34, 12, 1, 0).done()
                .squad("enemy").faction(Faction.DEFENDER).size(2).inFile(60, 30, 1, 0).done()
                .build();
        boolean previous = DigInSystem.isEnabled();
        DigInSystem.setEnabledForEvidence(true);
        try (BattleSimulation sim = world.sim()) {
            dig(sim, 22, 33, 24);
            dig(sim, 30, 41, 12);
            dig(sim, 22, 33, 31);
            sim.getSquadMoveOrderService().requestMove(world.squadId("alpha"), 27, 24);
            sim.getSquadMoveOrderService().requestMove(world.squadId("bravo"), 35, 12);
            for (int i = 0; i < 420; i++) sim.advance(1f / 30f);
            sim.getFogOfWar().tick(0, sim.getRoster());
            Selection selection = new Selection();
            selection.selectSquad(world.squadId("alpha"));
            HighlightOverlay highlights = new HighlightOverlay();
            BattleCamera camera = new BattleCamera(64, 36);
            camera.setViewport(0, 0, 1920, 1080, 1920 / 64f);
            FightingPositionHighlightPublisher.publish(selection, sim, highlights, camera.visibleCells());
            BattleSceneHostPass pass = new BattleSceneHostPass() {
                @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                    return new BattleSceneFrame(new RenderContext(sim, camera, null,
                            alpha, 0, false, highlights, selection,
                            BattleRenderHostProfile.EMBEDDED_SCENE),
                            EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.HIGHLIGHTS,
                                    RenderLayer.UNITS));
                }
                @Override public void draw(CanvasHostViewport viewport, float alpha) {
                    throw new IllegalStateException("Headless evidence only");
                }
            };
            return new SnapshotArtifact("fighting-positions.png", renderer.renderHostPass(pass, 1920, 1080));
        } finally {
            DigInSystem.setEnabledForEvidence(previous);
        }
    }

    /**
     * A selected squad of eight dragging a line across a trench and on over open ground: while the
     * drag is held, a ring where each marine will stand (green on the trench, pale beyond it); once
     * released and walked, the same squad standing where the rings were, with nothing drawn.
     */
    private SnapshotArtifact renderLine(SnapshotContext context, boolean held) throws Exception {
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        SceneWorld world = SceneBuilder.openGround(64, 36)
                .squad("alpha").size(8).inFile(22, 8, 1, 0).done()
                .squad("enemy").faction(Faction.DEFENDER).size(2).inFile(60, 33, 1, 0).done()
                .build();
        boolean previous = DigInSystem.isEnabled();
        DigInSystem.setEnabledForEvidence(true);
        try (BattleSimulation sim = world.sim()) {
            dig(sim, 14, 25, 20);
            for (int i = 0; i < 5; i++) sim.advance(1f / 30f);
            Selection selection = new Selection();
            selection.selectSquad(world.squadId("alpha"));
            HighlightOverlay highlights = new HighlightOverlay();
            if (held) {
                SelectionOrders.requestLine(sim, selection, 12, 20, 48, 20);
                for (int i = 0; i < 900; i++) sim.advance(1f / 30f);
                SquadMoveOrderHighlightPublisher.publish(selection, sim, highlights);
            } else {
                highlights.putLines(HighlightOverlay.SRC_SQUAD_LINE_PREVIEW,
                        SelectionOrders.previewMarks(sim, selection, 12, 20, 48, 20));
            }
            sim.getFogOfWar().tick(0, sim.getRoster());
            BattleCamera camera = new BattleCamera(64, 36);
            camera.setViewport(0, 0, 1920, 1080, 1920 / 64f);
            BattleSceneHostPass pass = new BattleSceneHostPass() {
                @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                    return new BattleSceneFrame(new RenderContext(sim, camera, null,
                            alpha, 0, false, highlights, selection,
                            BattleRenderHostProfile.EMBEDDED_SCENE),
                            EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.HIGHLIGHTS,
                                    RenderLayer.UNITS));
                }
                @Override public void draw(CanvasHostViewport viewport, float alpha) {
                    throw new IllegalStateException("Headless evidence only");
                }
            };
            return new SnapshotArtifact(held ? "line-held.png" : "line-preview.png",
                    renderer.renderHostPass(pass, 1920, 1080));
        } finally {
            DigInSystem.setEnabledForEvidence(previous);
        }
    }

    /**
     * A squad on the march with a Hound and a Warden attached, the squad selected: both mechs walk
     * behind it out of contact, ringed with it and tethered to it ({@code mechs-nouns.md}, attachment).
     */
    private SnapshotArtifact renderAttached(SnapshotContext context) throws Exception {
        SceneWorld world = SceneBuilder.openGround(64, 36)
                .squad("alpha").size(6).inFile(22, 16, 0, 1).done()
                .squad("hound").size(1).at(12, 14).mech(MechVariant.HOUND, MechRole.ASSAULT).done()
                .squad("warden").size(1).at(12, 22).mech(MechVariant.WARDEN, MechRole.LR_SUPPORT).done()
                .squad("enemy").faction(Faction.DEFENDER).size(2).inFile(62, 34, 1, 0).done()
                .build();
        try (BattleSimulation sim = world.sim()) {
            int alpha = world.squadId("alpha");
            sim.getMechAttachmentService().requestAttach(world.squadId("hound"), alpha);
            sim.getMechAttachmentService().requestAttach(world.squadId("warden"), alpha);
            Selection selection = new Selection();
            selection.selectSquad(alpha);
            SelectionOrders.requestMove(sim, selection, 50, 18);
            for (int i = 0; i < 300; i++) sim.advance(1f / 30f);
            HighlightOverlay highlights = new HighlightOverlay();
            SquadMoveOrderHighlightPublisher.publish(selection, sim, highlights);
            sim.getFogOfWar().tick(0, sim.getRoster());
            return new SnapshotArtifact("attached-mechs-march.png",
                    renderWorld(context, sim, highlights, selection));
        }
    }

    /**
     * A box-selected group of a squad, a free Bulwark and an APC: while a right-drag is held, each
     * recipient's stretch of the line marked; after a group click, the three standing spread round
     * it rather than on one cell.
     */
    private SnapshotArtifact renderMixedGroup(SnapshotContext context, boolean moved) throws Exception {
        SceneWorld world = SceneBuilder.openGround(64, 36)
                .squad("alpha").size(6).inFile(14, 14, 1, 0).done()
                .squad("bulwark").size(1).at(12, 20).mech(MechVariant.BULWARK, MechRole.BALANCED).done()
                .squad("enemy").faction(Faction.DEFENDER).size(2).inFile(62, 34, 1, 0).done()
                .build();
        try (BattleSimulation sim = world.sim()) {
            long apc = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE, VehicleMission.deployed(16f, 25f));
            sim.convoy().body(apc).teleport(16f, 25f, 0f);
            Selection selection = new Selection();
            selection.selectGroup(List.of(world.squadId("alpha"), world.squadId("bulwark")), List.of(apc),
                    world.squadId("alpha"), 0L, 0L, false);
            HighlightOverlay highlights = new HighlightOverlay();
            if (moved) {
                SelectionOrders.requestMove(sim, selection, 44, 18);
                for (int i = 0; i < 1200; i++) sim.advance(1f / 30f);
            } else {
                highlights.putLines(HighlightOverlay.SRC_SQUAD_LINE_PREVIEW,
                        SelectionOrders.previewMarks(sim, selection, 40, 8, 40, 30));
            }
            sim.getFogOfWar().tick(0, sim.getRoster());
            return new SnapshotArtifact(moved ? "mixed-group-moved.png" : "mixed-group-line-preview.png",
                    renderWorld(context, sim, highlights, selection));
        }
    }

    /** The battle scene alone at 1920x1080, 64 cells across. */
    private static BufferedImage renderWorld(SnapshotContext context, BattleSimulation sim,
                                             HighlightOverlay highlights, Selection selection) throws Exception {
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        BattleCamera camera = new BattleCamera(64, 36);
        camera.setViewport(0, 0, 1920, 1080, 1920 / 64f);
        BattleSceneHostPass pass = new BattleSceneHostPass() {
            @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                return new BattleSceneFrame(new RenderContext(sim, camera, null,
                        alpha, 0, false, highlights, selection,
                        BattleRenderHostProfile.EMBEDDED_SCENE),
                        EnumSet.of(RenderLayer.GROUND, RenderLayer.HIGHLIGHTS, RenderLayer.UNITS,
                                RenderLayer.CONVOY));
            }
            @Override public void draw(CanvasHostViewport viewport, float alpha) {
                throw new IllegalStateException("Headless evidence only");
            }
        };
        return renderer.renderHostPass(pass, 1920, 1080);
    }

    private static void dig(BattleSimulation sim, int x0, int x1, int y) {
        TileRegistry registry = TileRegistry.installed();
        for (int x = x0; x <= x1; x++) {
            int mask = (x < x1 ? 2 : 0) | (x > x0 ? 8 : 0);
            DoodadDef def = registry.doodad("doodad.fort-trench-" + mask);
            if (def == null) throw new IllegalStateException("no trench piece for mask " + mask);
            sim.addDoodad(new Doodad(x, y, def));
        }
    }
}
