package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.panel.ShipOrderTargetingPanel;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.ops.battleview.BattleSceneFrame;
import com.dillon.starsectormarines.ops.battleview.BattleSceneHostPass;
import com.dillon.starsectormarines.ops.battleview.BattleRenderHostProfile;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderContext;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in command flow evidence: actual retained controls over the production world renderer. */
public final class ShipCommandUiSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ship-command-ui"; }
    @Override public String label() { return "Friendly ship selection and remote commands"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        String property = "battle.unitUpdate.minimumParallelUnits";
        String previous = System.getProperty(property);
        System.setProperty(property, Integer.toString(Integer.MAX_VALUE));
        try { return renderFlow(context); }
        finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private List<SnapshotArtifact> renderFlow(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> frames = new ArrayList<>();
        var world = SceneBuilder.openGround(120, 68).missionCompletion(false)
                .unit("observer", Faction.MARINE, UnitType.MARINE, 40, 34)
                .unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 116, 64).build();
        try (BattleSimulation sim = world.sim(); Plates plates = new Plates(context)) {
            // Authored ground art for this controlled scene, using the same terrain kinds as production maps.
            for (int y = 0; y < 68; y++) for (int x = 0; x < 120; x++) {
                sim.getTopology().setGroundKind(x, y, y >= 30 && y <= 38 ? GroundKind.STREET : GroundKind.DIRT);
            }
            Tender tender = Tender.fit(Faction.MARINE, 20, 34, -90);
            tender.ship().stationTo(20, 34);
            sim.tenders().add(sim, tender);
            Selection selection = new Selection();
            ShipOrderTargetingPanel targeting = new ShipOrderTargetingPanel(uiContext(sim, selection), (x, y) -> false);
            BattleShipCommandController controller = new BattleShipCommandController(() -> sim, selection, targeting,
                    ignored -> {}, () -> null, () -> {});
            tick(sim, 1);
            plates.update(controller.state());
            require(plates.state.ships().size() == 1, "Only the actual friendly tender is offered");
            plates.clickHero(plates.state.ships().get(0).id());
            require(selection.getSelectedWarship() == tender.ship(), "Hero click selected the exact hull");
            plates.update(controller.state());
            String heroId = plates.state.ships().get(0).id();
            Runnable selectedCallback = plates.state.ships().get(0).select();
            Runnable moveCallback = plates.state.actions().stream().filter(a -> a.id().equals("move"))
                    .findFirst().orElseThrow().action();
            var heroNode = plates.heroes.requireElement("ship-hero-" + heroId);
            var moveNode = plates.actions.requireElement("ship-action-move");
            plates.update(controller.state());
            require(selectedCallback == plates.state.ships().get(0).select(), "Refresh retains the exact select callback");
            require(moveCallback == plates.state.actions().stream().filter(a -> a.id().equals("move"))
                    .findFirst().orElseThrow().action(), "Refresh retains the exact command callback");
            require(heroNode == plates.heroes.requireElement("ship-hero-" + heroId)
                    && moveNode == plates.actions.requireElement("ship-action-move"),
                    "Repeated selected-state refresh retains interactive UI nodes");
            frames.add(new SnapshotArtifact("01-selected-mobile-1920x1080-ui100.png", plates.frame(context, sim, selection)));

            plates.clickAction("move");
            require(targeting.active(), "Move icon owns the next world click");
            require(targeting.commitAt(35.5f, 34.5f), "Observed remote move committed");
            tick(sim, 1);
            plates.update(controller.state());
            frames.add(new SnapshotArtifact("02a-move-issued-1920x1080-ui100.png", plates.frame(context, sim, selection)));
            tick(sim, 90);
            plates.update(controller.state());
            frames.add(new SnapshotArtifact("02b-moving-1920x1080-ui100.png", plates.frame(context, sim, selection)));
            tick(sim, 269);
            require(sim.shipCommands().notice(tender.ship()) == ShipCommandService.Notice.MOVING,
                    "The serial tick accepted remote movement");
            require(tender.ship().x > 28, "The remote ship physically moved toward its destination");
            plates.update(controller.state());
            frames.add(new SnapshotArtifact("02-moved-1920x1080-ui100.png", plates.frame(context, sim, selection)));
            SelectionOrders.requestMove(sim, selection, 38, 34);
            tick(sim, 1);
            require(sim.shipCommands().notice(tender.ship()) == ShipCommandService.Notice.MOVING
                    && tender.ship().stationX() == 38.5f && tender.ship().stationY() == 34.5f,
                    "The shared right-click release endpoint queues the selected ship's remote move");
            plates.clickAction("hold");
            tick(sim, 1);
            require(tender.ship().remoteOrder() == Warship.RemoteOrder.HOLD, "Hold replaces the travel order");
            plates.update(controller.state());
            frames.add(new SnapshotArtifact("03-held-1920x1080-ui100.png", plates.frame(context, sim, selection)));
            plates.clickAction("auto");
            tick(sim, 1);
            require(tender.ship().remoteOrder() == Warship.RemoteOrder.NONE, "Auto restores mission authority");

            plates.update(controller.state());
            String build = plates.state.actions().stream().filter(a -> a.id().contains("vulcan"))
                    .map(BattleShipCommandOverlayModel.ActionState::id).findFirst().orElseThrow();
            plates.clickAction(build);
            require(targeting.active() && targeting.option() == BuildOption.VULCAN_TURRET,
                    "Vulcan icon arms the production construction targeter");
            require(!targeting.commitAt(110.5f, 2.5f) && targeting.active(),
                    "Hidden ground refuses the build and preserves its armed targeter");
            require(tender.orders().isEmpty(), "Hidden build refusal cannot create an order");
            sim.getGrid().setWalkable(45, 34, false);
            require(!targeting.commitAt(45.5f, 34.5f) && targeting.active(),
                    "Blocked ground refuses the build and preserves its armed targeter");
            tick(sim, 1);
            require(tender.orders().isEmpty(), "Refused clicks queue no construction for the next tick");
            sim.getGrid().setWalkableFloor(45, 34);
            selection.clear();
            targeting.update(0f);
            require(!targeting.active(), "Changing selection cancels the armed build targeter");
            plates.update(controller.state());
            plates.clickHero(heroId);
            plates.update(controller.state());
            plates.clickAction(build);
            require(targeting.commitAt(45.5f, 34.5f), "Remote build target accepted");
            tick(sim, 1);
            require(sim.shipCommands().notice(tender.ship()) == ShipCommandService.Notice.LAID && !tender.orders().isEmpty(),
                    "The normal tick laid a construction site without entering direct control");
            require(!sim.directControl().active(), "Remote build required no pilot session");
            plates.update(controller.state());
            frames.add(new SnapshotArtifact("04-build-queued-1920x1080-ui100.png", plates.frame(context, sim, selection)));
            tick(sim, 1500);
            require(countTurrets(sim) > 0, "Remote build physically completed a turret");
            plates.update(controller.state());
            frames.add(new SnapshotArtifact("05-build-complete-1920x1080-ui100.png", plates.frame(context, sim, selection)));

            sim.shipCommands().requestHold(tender.ship());
            tick(sim, 1);
            require(sim.directControl().enterWarship(tender.ship()), "The same hull can enter direct control");
            require(tender.ship().remoteOrder() == Warship.RemoteOrder.HOLD, "Takeover retains the accepted remote task");
            require(!controller.state().visible(), "Command chrome is hidden during direct control");
            sim.shipCommands().requestMove(tender.ship(), 35.5f, 34.5f);
            tick(sim, 1);
            require(sim.shipCommands().notice(tender.ship()) == ShipCommandService.Notice.PILOTED,
                    "A remote move cannot compete with pilot ownership");
            sim.directControl().exit();
            plates.update(controller.state());
            frames.add(new SnapshotArtifact("06-handback-1920x1080-ui100.png", plates.frame(context, sim, selection)));
            require(tender.ship().remoteOrder() == Warship.RemoteOrder.HOLD, "Handback restores the accepted hold without the refused move");

            Tender hostile = Tender.fit(Faction.DEFENDER, 110, 60, 0);
            sim.tenders().add(sim, hostile);
            sim.shipCommands().requestHold(hostile.ship());
            tick(sim, 1);
            require(sim.shipCommands().notice(hostile.ship()) == ShipCommandService.Notice.UNAVAILABLE,
                    "Hostile hull refuses remote orders");
            require(controller.state().ships().size() == 1, "Hostile hull never appears in friendly heroes");
            tender.ship().departForward(120, 68);
            sim.shipCommands().requestHold(tender.ship());
            tick(sim, 1);
            require(sim.shipCommands().notice(tender.ship()) == ShipCommandService.Notice.UNAVAILABLE,
                    "A departing hull refuses stale remote orders");
            plates.update(controller.state());
            require(!plates.state.visible(), "Lost friendly hull leaves no hero controls");
            frames.add(new SnapshotArtifact("07-lost-ship-1920x1080-ui100.png", plates.frame(context, sim, selection)));
        }
        addGrounded(context, frames);
        return frames;
    }

    private static int countTurrets(BattleSimulation sim) {
        int count = 0;
        var roster = sim.getRoster();
        for (int i = 0; i < roster.liveCount(); i++) {
            long id = roster.denseArray()[i];
            if (roster.identity().type(id).isTurret() && roster.identity().faction(id) == Faction.MARINE) count++;
        }
        return count;
    }

    private static void addGrounded(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        for (boolean powered : List.of(false, true)) {
            try (BattleSimulation sim = BattleSetup.createColdStart(14101,
                    List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)),
                    new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD),
                    true, powered); Plates plates = new Plates(context)) {
                Selection selection = new Selection();
                ShipOrderTargetingPanel targeting = new ShipOrderTargetingPanel(uiContext(sim, selection), (x, y) -> false);
                AtomicReference<String> power = new AtomicReference<>();
                BattleShipCommandController controller = new BattleShipCommandController(() -> sim, selection, targeting,
                        power::set, power::get, () -> {});
                tick(sim, 1);
                plates.update(controller.state());
                if (!powered) {
                    require(plates.state.ships().isEmpty(), "No construction hero is granted before shore power");
                } else {
                    require(plates.state.ships().size() == 1, "Shore power activates the grounded construction host");
                    plates.clickHero(plates.state.ships().get(0).id());
                    plates.update(controller.state());
                    require(plates.actions.requireElement("ship-action-move").disabled(), "A grounded hull cannot move");
                    var buildActions = plates.state.actions().stream().filter(a -> a.id().contains("vulcan")).toList();
                    require(buildActions.size() == 1, "Grounded mission exposes its single authored construction option");
                    require(plates.state.actions().stream().filter(a -> a.id().startsWith("build-")).count() == 1,
                            "Mobile tender kit does not bypass the mission construction owner");
                    if (buildActions.get(0).enabled()) {
                        plates.clickAction(buildActions.get(0).id());
                        require("yard-construction".equals(power.get()), "Grounded build uses the command power gate");
                    }
                }
                frames.add(new SnapshotArtifact(powered ? "09-grounded-powered-1920x1080-ui100.png"
                        : "08-grounded-unpowered-1920x1080-ui100.png", plates.frame(context, sim, selection)));
            }
        }
    }

    private static BattleUiContext uiContext(BattleSimulation sim, Selection selection) {
        return new BattleUiContext() {
            @Override public BattleSimulation getSim() { return sim; }
            @Override public BattleFixture getBattleFixture() { return null; }
            @Override public BattleCamera getCamera() { return null; }
            @Override public BattleLayout getLayout() { return null; }
            @Override public Selection getSelection() { return selection; }
            @Override public HighlightOverlay getHighlights() { return new HighlightOverlay(); }
        };
    }

    private static void require(boolean condition, String reading) {
        if (!condition) throw new IllegalStateException(reading);
    }

    private static void tick(BattleSimulation sim, int count) {
        for (int i = 0; i < count; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static final class Plates implements AutoCloseable {
        private final BattleShipCommandOverlayModel model;
        private final MarkupInstance heroes;
        private final MarkupInstance actions;
        private final UiDocument heroDocument;
        private final UiDocument actionDocument;
        private final BattleHudOverlayModel hudModel;
        private final MarkupInstance hud;
        private final UiDocument hudDocument;
        private BattleShipCommandOverlayModel.State state;

        Plates(SnapshotContext context) throws Exception {
            Reactor reactor = new Reactor();
            model = new BattleShipCommandOverlayModel(reactor);
            hudModel = new BattleHudOverlayModel(reactor, ignored -> {}, "Pause", "1x", "2x", "4x");
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                    List.of(BattleShipCommandOverlay.COMPONENT_PATH, BattleShipCommandOverlay.ACTION_COMPONENT_PATH,
                            BattleHudOverlay.COMPONENT_PATH));
            loader.reload();
            heroes = loader.build(reactor, "battle-ship-command-overlay", model.props());
            actions = loader.build(reactor, "battle-ship-command-actions", model.props());
            heroDocument = BattleDirectControlOverlay.document(heroes);
            actionDocument = BattleDirectControlOverlay.document(actions);
            hud = loader.build(reactor, BattleHudOverlay.COMPONENT, hudModel.props());
            BattleHudOverlay.wireLayout(hud);
            hudDocument = BattleDirectControlOverlay.document(hud);
        }

        void update(BattleShipCommandOverlayModel.State next) {
            state = model.update(next);
            heroes.flush(); actions.flush();
            heroDocument.layout(BattleShipCommandOverlay.heroWidth(state.ships().size()), BattleShipCommandOverlay.HERO_HEIGHT);
            actionDocument.layout(BattleShipCommandOverlay.actionWidth(state.actions().size()), BattleShipCommandOverlay.ACTION_HEIGHT);
        }

        void clickHero(String id) { click(heroes, heroDocument, "ship-hero-" + id); }
        void clickAction(String id) { click(actions, actionDocument, "ship-action-" + id); }
        private static void click(MarkupInstance markup, UiDocument document, String id) {
            var element = markup.requireElement(id);
            require(!element.disabled(), "Command is unexpectedly unavailable: " + id);
            var box = element.box().borderBox();
            document.pointerDown(box.x() + box.width() / 2, box.y() + box.height() / 2);
            document.pointerUp(box.x() + box.width() / 2, box.y() + box.height() / 2);
            markup.flush();
        }

        BufferedImage frame(SnapshotContext context, BattleSimulation sim, Selection selection) {
            HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                    HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            BattleSceneHostPass pass = new BattleSceneHostPass() {
                @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                    BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
                    camera.setViewport(0, 0, viewport.width(), viewport.height(), viewport.width() /
                            (sim.getColdStart() == null ? 70f : 120f));
                    camera.centerOn(sim.getColdStart() == null ? 35f : 131f,
                            sim.getColdStart() == null ? 34f : 115f);
                    return new BattleSceneFrame(new RenderContext(sim, camera, null, alpha, 0, false,
                            new HighlightOverlay(), selection, BattleRenderHostProfile.EMBEDDED_SCENE),
                            EnumSet.of(RenderLayer.GROUND, RenderLayer.VEHICLES, RenderLayer.DOODADS, RenderLayer.HIGHLIGHTS,
                                    RenderLayer.UNIT_SHADOWS, RenderLayer.UNITS, RenderLayer.ROOFS,
                                    RenderLayer.DRONES, RenderLayer.OBJECTIVES, RenderLayer.CONVOY,
                                    RenderLayer.SHUTTLES, RenderLayer.NIGHT_LIGHTING));
                }
                @Override public void draw(CanvasHostViewport viewport, float alpha) {
                    throw new IllegalStateException("Headless evidence only");
                }
            };
            BufferedImage image = renderer.renderHostPass(pass, 1920, 1080);
            Graphics2D graphics = image.createGraphics();
            try {
                var presentation = hudModel.update(1f, sim.getCompoundService().getRecords(),
                        sim.getCommanderSnapshot(Faction.MARINE));
                var mission = MissionObjectiveProjection.capture(sim);
                if (!mission.objectives().isEmpty()) presentation = hudModel.updateMission(mission, ignored -> {});
                hud.flush();
                graphics.drawImage(renderer.render(hudDocument, 1920 - 24,
                        Math.round(BattleHudOverlay.documentHeight(presentation))), 12, 12, null);
                if (state.visible()) graphics.drawImage(renderer.render(heroDocument,
                        Math.round(BattleShipCommandOverlay.heroWidth(state.ships().size())),
                        Math.round(BattleShipCommandOverlay.HERO_HEIGHT)), 12,
                        1080 - 12 - 132 - Math.round(BattleShipCommandOverlay.HERO_HEIGHT), null);
                if (state.selected()) {
                    BufferedImage tray = renderer.render(actionDocument,
                            Math.round(BattleShipCommandOverlay.actionWidth(state.actions().size())),
                            Math.round(BattleShipCommandOverlay.ACTION_HEIGHT));
                    graphics.drawImage(tray, (1920 - tray.getWidth()) / 2, 1080 - 12 - tray.getHeight(), null);
                }
            } finally { graphics.dispose(); }
            return image;
        }

        @Override public void close() { heroes.close(); actions.close(); hud.close(); }
    }
}
