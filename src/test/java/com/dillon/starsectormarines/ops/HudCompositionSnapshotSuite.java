package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.EmergencyResupply;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.panel.ShipOrderTargetingPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleRenderHostProfile;
import com.dillon.starsectormarines.ops.battleview.BattleSceneFrame;
import com.dillon.starsectormarines.ops.battleview.BattleSceneHostPass;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderContext;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Actual retained overlay instances, their visibility, and production host viewports at 100% scale. */
public final class HudCompositionSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "hud-composition"; }
    @Override public String label() { return "Complete retained battle HUD placement matrix"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        InstalledHullSpecs.install(); InstalledVanillaData.install();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        DiskRegistries.installMapGeneration(context.projectRoot());
        SettingsAPI previous = Global.getSettings();
        Global.setSettings(settings(context, previous));
        List<SnapshotArtifact> frames = new ArrayList<>();
        try {
            var world = SceneBuilder.openGround(120, 68).missionCompletion(false)
                    .squad("infantry").size(12).inFile(28, 34, 1, 0).done()
                    .squad("edge").size(1).inFile(42, 1, 1, 0).done()
                    .squad("lance").size(4).mech(MechVariant.BULWARK, MechRole.BALANCED)
                    .inFile(38, 42, 3, 0).done()
                    .unit("enemy", Faction.DEFENDER, UnitType.MARINE, 116, 65).build();
            try (BattleSimulation sim = world.sim()) {
                for (int y = 0; y < 68; y++) for (int x = 0; x < 120; x++) {
                    sim.getTopology().setGroundKind(x, y, y >= 30 && y <= 38 ? GroundKind.STREET : GroundKind.DIRT);
                }
                sim.addObjective(new ChargeSiteObjective(72, 20, 8, "communications", "Communications relay"));
                sim.addObjective(new ChargeSiteObjective(82, 36, 8, "generator", "Compound power generator"));
                sim.addObjective(new ChargeSiteObjective(70, 50, 8, "stores", "Fortified ammunition stores"));
                sim.getCommandPowerService().setPowers(List.of(new ReconPing(), new EmergencyResupply(),
                        new OrbitalBarrage(), new MechSupport(), new CloseAirSupport()));
                Tender tender = Tender.fit(Faction.MARINE, 26, 24, -90);
                sim.tenders().add(sim, tender);
                sim.advance(BattleSimulation.TICK_DT);
                Selection selection = new Selection();
                frames.add(frame(context, sim, selection, "01-company", false));
                selection.selectUnit(world.squadId("infantry"), world.members("infantry")[0]);
                frames.add(frame(context, sim, selection, "02-infantry-powers-orders", false));
                frames.add(frame(context, sim, selection, "03-infantry-retreat-confirm", true));
                selection.selectUnit(world.squadId("lance"), world.members("lance")[0]);
                frames.add(frame(context, sim, selection, "04-mech-powers-orders", false));
                selection.selectWarship(tender.ship());
                frames.add(frame(context, sim, selection, "05-mobile-ship", false));
                selection.selectUnit(world.squadId("infantry"), world.members("infantry")[0]);
                require(sim.directControl().enter(world.members("infantry")[0]), "Fixture Marine can take control");
                frames.add(frame(context, sim, selection, "06-manual-marine", false));
                sim.directControl().exit();
                selection.selectUnit(world.squadId("edge"), world.members("edge")[0]);
                require(sim.directControl().enter(world.members("edge")[0]), "Map-edge Marine can take control");
                frames.add(frame(context, sim, selection, "06b-manual-docked-subtitle", false));
                sim.directControl().exit();
            }
            try (BattleSimulation sim = BattleSetup.createColdStart(14101,
                    List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)),
                    new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD), true, true)) {
                sim.advance(BattleSimulation.TICK_DT);
                Selection selection = new Selection();
                selection.selectWarship(sim.getColdStart().tender().ship());
                frames.add(frame(context, sim, selection, "07-grounded-morrow", false));
            }
        } finally { Global.setSettings(previous); }
        return frames;
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim,
                                          Selection selection, String name, boolean confirm) throws Exception {
        PositionAPI position = position();
        BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
        camera.setViewport(0, 0, 1920, 1080, 1920f / (sim.getColdStart() == null ? 85 : 120));
        camera.centerOn(sim.getColdStart() == null ? 42 : 131, sim.getColdStart() == null ? 34 : 115);
        if (name.contains("docked")) camera.centerOn(42, 0);
        BattleLayout layout = new BattleLayout(position, sim.getGrid().getWidth(), sim.getGrid().getHeight());
        BattleUiContext ui = uiContext(sim, selection, camera, layout);
        AtomicReference<String> powerTarget = new AtomicReference<>();
        BattleHudOverlay hud = new BattleHudOverlay(ignored -> {}, camera);
        BattleSquadOverlay squad = new BattleSquadOverlay(selection);
        BattleMechOverlay mech = new BattleMechOverlay(selection);
        BattlePowerOverlay powers = new BattlePowerOverlay(powerTarget::set, powerTarget::get);
        BattleOrderOverlay orders = new BattleOrderOverlay(selection, () -> Mode.NONE, ignored -> {});
        ShipOrderTargetingPanel targeter = new ShipOrderTargetingPanel(ui, (x, y) -> false);
        BattleShipCommandController controller = new BattleShipCommandController(() -> sim, selection, targeter,
                powerTarget::set, powerTarget::get, () -> {});
        BattleShipCommandOverlay ships = new BattleShipCommandOverlay(controller::state);
        BattleRetreatOverlay retreat = new BattleRetreatOverlay(() -> {}, () -> {});
        BattleManualObjectiveOverlay manual = new BattleManualObjectiveOverlay();
        BattleDirectControlOverlay control = new BattleDirectControlOverlay(selection, () -> {}, () -> {});
        StorySubtitleOverlay subtitle = new StorySubtitleOverlay();
        hud.attach(position, sim, 1); squad.attach(position, sim); mech.attach(position, sim);
        powers.attach(position, sim); powers.suppressed(selection.hasWarshipSelection());
        orders.attach(position, sim, powers.occupiedHeight());
        boolean selectedMech = selection.getSelectedUnitEntityId() != 0L
                && sim.world().hasMechLoadout(selection.getSelectedUnitEntityId());
        ships.heroBottomInset(BattleShipCommandOverlay.heroBottomInset(position, selection, selectedMech));
        ships.attach(position); retreat.attach(position, false);
        manual.attach(position, sim); control.attach(position, sim, 1);
        subtitle.attach(position);
        subtitle.update(new WaylineNarration.Line("hud-layout", "fixture", "check",
                "Keep the company together. Select a ship to command it, or take control of one marine when the line needs you.",
                10, 1, 10, List.of(), "MARA VENN / WAYLINE", OpeningBroker.PORTRAIT), 0);
        UiViewport messageBounds = subtitle.surface().viewport();
        float messageX = messageBounds.screenX() + messageBounds.width() / 2f;
        float messageY = messageBounds.screenY() + messageBounds.height() / 2f;
        require(subtitle.blocksWorldPointer(messageX, messageY), "Visible subtitles suppress world actions");
        require(!subtitle.blocksWorldPointer(messageBounds.screenX() - 1, messageY),
                "Subtitle pointer blocking remains inside its visible bounds");
        control.topReservedHeight(position.getY() + position.getHeight() - BattleLayout.PAD
                - subtitle.surface().viewport().screenY());
        control.update(0, sim, 1);
        if (sim.directControl().active()) {
            long unit = sim.directControl().activeUnitId();
            control.avoidControlledBody(camera.cellToScreenX(sim.world().renderX(unit)),
                    camera.cellToScreenY(sim.world().renderY(unit)), sim.getRoster().radius(unit) * camera.cellPxSize());
        }
        if (name.contains("docked")) {
            UiViewport strip = control.surfaces().get(0).viewport();
            UiViewport message = subtitle.surface().viewport();
            require(strip.screenY() > 540, "Map-edge body docks equipment in the upper lane");
            require(strip.screenY() + strip.height() <= message.screenY(),
                    "Top-docked equipment clears the active subtitle");
        }
        if (confirm) {
            var surface = retreat.surface();
            var element = find(surface.document().root(), "battle-exit-action");
            var box = element.box().borderBox();
            surface.document().pointerDown(box.x() + box.width() / 2, box.y() + box.height() / 2);
            surface.document().pointerUp(box.x() + box.width() / 2, box.y() + box.height() / 2);
            retreat.update(0, false);
        }
        List<NamedSurface> surfaces = new ArrayList<>();
        if (!sim.directControl().active()) {
            add(surfaces, "mission/time", hud.surface()); add(surfaces, "orders", orders.surface());
            add(surfaces, "infantry", squad.surface()); add(surfaces, "mech", mech.surface());
            add(surfaces, "fleet powers", powers.surface());
            for (var surface : ships.surfaces()) add(surfaces, "ship command", surface);
            add(surfaces, "retreat", retreat.surface());
        }
        add(surfaces, "manual objectives", manual.surface());
        for (var surface : control.surfaces()) add(surfaces, "direct control", surface);
        add(surfaces, "subtitle", subtitle.surface());
        require(surfaces.stream().anyMatch(s -> s.name().equals("subtitle")), "Active subtitle is included");
        if (sim.directControl().active()) require(surfaces.stream().noneMatch(s -> s.name().equals("fleet powers")
                || s.name().equals("ship command") || s.name().equals("retreat")), "Manual mode suppresses command surfaces");
        else require(surfaces.stream().anyMatch(s -> s.name().equals("ship command")), "Actual fleet heroes are included");
        if (!sim.directControl().active() && selection.hasSquadSelection()) {
            require(surfaces.stream().anyMatch(s -> s.name().equals(selectedMech ? "mech" : "infantry")),
                    "The actual selected body panel is included");
            require(surfaces.stream().noneMatch(s -> s.name().equals(selectedMech ? "infantry" : "mech")),
                    "The other selected body panel stays hidden");
            require(surfaces.stream().anyMatch(s -> s.name().equals("fleet powers"))
                    && surfaces.stream().anyMatch(s -> s.name().equals("orders")), "Selection keeps fleet powers and contextual orders");
        }
        if (selection.hasWarshipSelection()) require(surfaces.stream().noneMatch(s -> s.name().equals("fleet powers")),
                "Selected ship tray replaces fleet powers");
        if (!sim.directControl().active() && !control.surfaces().isEmpty()) {
            UiViewport shortcut = control.surfaces().get(0).viewport();
            UiViewport heroes = ships.surfaces().get(0).viewport();
            if (selection.hasWarshipSelection()) {
                require(shortcut.screenX() == heroes.screenX()
                        && shortcut.screenY() == heroes.screenY() + heroes.height() + 6f,
                        "Ship takeover shortcut joins the left-aligned hero stack");
            } else {
                require(heroes.screenY() == shortcut.screenY() + shortcut.height() + 12f,
                        "Heroes clear the actual selected body's shortcut without a taller-panel reservation");
            }
        }
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        BufferedImage image = renderer.renderHostPass(new BattleSceneHostPass() {
            @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                return new BattleSceneFrame(new RenderContext(sim, camera, layout, alpha, 0, false,
                        new HighlightOverlay(), selection, BattleRenderHostProfile.EMBEDDED_SCENE),
                        EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.VEHICLES,
                                RenderLayer.HIGHLIGHTS, RenderLayer.UNIT_SHADOWS, RenderLayer.UNITS,
                                RenderLayer.DRONES, RenderLayer.SHUTTLES, RenderLayer.NIGHT_LIGHTING));
            }
            @Override public void draw(CanvasHostViewport viewport, float alpha) { throw new IllegalStateException("Headless only"); }
        }, 1920, 1080);
        Graphics2D graphics = image.createGraphics();
        try {
            for (NamedSurface named : surfaces) {
                UiViewport viewport = named.surface().viewport();
                require(viewport.documentScale() == 1, "100% scale: " + named.name());
                require(viewport.screenX() >= 0 && viewport.screenY() >= 0
                        && viewport.screenX() + viewport.width() <= 1920 && viewport.screenY() + viewport.height() <= 1080,
                        "Visible surface lies inside full host: " + named.name());
                BufferedImage panel = renderer.render(named.surface().document(), Math.round(viewport.documentWidth()),
                        Math.round(viewport.documentHeight()));
                graphics.drawImage(panel, Math.round(viewport.screenX()),
                        1080 - Math.round(viewport.screenY() + viewport.height()), null);
            }
        } finally {
            graphics.dispose(); subtitle.detach();
            require(!subtitle.blocksWorldPointer(messageX, messageY), "Hidden subtitles capture no world pointer");
        }
        return new SnapshotArtifact(name + "-1920x1080-ui100.png", image);
    }

    private record NamedSurface(String name, BattleHudSurface surface) { }
    private static UiElement find(UiElement root, String id) {
        if (id.equals(root.id())) return root;
        for (UiElement child : root.children()) {
            UiElement match = find(child, id);
            if (match != null) return match;
        }
        return null;
    }
    private static void add(List<NamedSurface> surfaces, String name, BattleHudSurface surface) {
        if (surface != null) surfaces.add(new NamedSurface(name, surface));
    }
    private static void require(boolean valid, String reading) { if (!valid) throw new IllegalStateException(reading); }

    private static PositionAPI position() {
        return (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(), new Class<?>[]{PositionAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX", "getY" -> 0f; case "getWidth" -> 1920f; case "getHeight" -> 1080f;
                    default -> throw new AssertionError("Unexpected position query " + method.getName());
                });
    }
    private static SettingsAPI settings(SnapshotContext context, SettingsAPI previous) {
        return (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getScreenScaleMult")) return 1f;
                    if (method.getName().equals("loadText")) {
                        Path mod = context.modRoot().resolve((String) args[0]);
                        return Files.readString(Files.exists(mod) ? mod : context.starsectorCore().resolve((String) args[0]));
                    }
                    if (previous != null) return method.invoke(previous, args);
                    return null;
                });
    }
    private static BattleUiContext uiContext(BattleSimulation sim, Selection selection, BattleCamera camera, BattleLayout layout) {
        return new BattleUiContext() {
            @Override public BattleSimulation getSim() { return sim; }
            @Override public BattleFixture getBattleFixture() { return null; }
            @Override public BattleCamera getCamera() { return camera; }
            @Override public BattleLayout getLayout() { return layout; }
            @Override public Selection getSelection() { return selection; }
            @Override public HighlightOverlay getHighlights() { return new HighlightOverlay(); }
        };
    }
}
