package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimLane;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimOrigins;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlPanel;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlScatter;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleRenderHostProfile;
import com.dillon.starsectormarines.ops.battleview.BattleSceneFrame;
import com.dillon.starsectormarines.ops.battleview.BattleSceneHostPass;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
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
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * The aim reticle's shape options on a real battle: a Bulwark under direct control, chainguns on the left button
 * and two LRM racks on the right, with the cursor where the chainguns fall off, inside the racks' minimum and past
 * the chainguns' range; then a Marine with smoke on the right button. The marks are {@link DirectControlFireMarks#read}, the strokes
 * {@link DirectControlReticle}'s, both production; only the stroke drain is a Java2D copy of the GL panel's.
 * A look study for the picks page, so it is left out of {@code all}.
 */
public final class AimReticleSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "aim-reticle"; }
    @Override public String label() { return "Direct-control aim reticle shape options"; }
    @Override public boolean inAll() { return false; }

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final float CELLS_ACROSS = 80f;

    /** A cursor {@code cells} from the controlled body, along its facing. */
    private record Aim(String name, float cells) { }

    private static final List<Aim> MECH_AIMS = List.of(
            new Aim("falling-off", 22f), new Aim("too-close", 5f), new Aim("past-range", 34f));

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        InstalledHullSpecs.install(); InstalledVanillaData.install();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        DiskRegistries.installMapGeneration(context.projectRoot());
        SettingsAPI previous = Global.getSettings();
        Global.setSettings(settings(context, previous));
        List<SnapshotArtifact> frames = new ArrayList<>();
        try {
            var bulwark = new MechDeploymentSpec(MechVariant.BULWARK, MechRole.BALANCED,
                    MissileReplenisherComponent.STANDARD, MechWeaponComponent.CHAINGUN,
                    MechWeaponComponent.LRM_5, MechWeaponComponent.LRM_5);
            var world = SceneBuilder.openGround(120, 68).missionCompletion(false)
                    .squad("infantry").size(6).primary("weapon.field-rifle").special("special.smoke-grenade", 6, 2).inFile(30, 22, 1, 0).done()
                    .squad("lance").size(1).mech(bulwark).inFile(40, 40, 3, 0).done()
                    .unit("enemy", Faction.DEFENDER, UnitType.MARINE, 116, 65).build();
            try (BattleSimulation sim = world.sim()) {
                for (int y = 0; y < 68; y++) for (int x = 0; x < 120; x++) {
                    sim.getTopology().setGroundKind(x, y, y >= 36 && y <= 44 ? GroundKind.STREET : GroundKind.DIRT);
                }
                // Long enough for the drop-in to finish, so the Bulwark stands on the street rather than descending.
                for (int tick = 0; tick < 300; tick++) sim.advance(BattleSimulation.TICK_DT);
                Selection selection = new Selection();
                long mech = world.members("lance")[0];
                selection.selectUnit(world.squadId("lance"), mech);
                require(sim.directControl().enter(mech), "The Bulwark can take control");
                int index = 1;
                for (DirectControlReticle.Look look : DirectControlReticle.Look.values()) {
                    for (Aim aim : MECH_AIMS) {
                        frames.add(frame(context, sim, selection, mech, aim, look,
                                String.format(Locale.ROOT, "%02d-%s-bulwark-%s", index++,
                                        look.name().toLowerCase(Locale.ROOT), aim.name())));
                    }
                }
                sim.directControl().exit();
                long marine = world.members("infantry")[0];
                selection.selectUnit(world.squadId("infantry"), marine);
                require(sim.directControl().enter(marine), "The Marine can take control");
                float range = sim.world().attackRange(marine);
                frames.add(frame(context, sim, selection, marine, new Aim("marine", range * 0.75f),
                        DirectControlReticle.LIVE, String.format(Locale.ROOT, "%02d-%s-marine", index,
                                DirectControlReticle.LIVE.name().toLowerCase(Locale.ROOT))));
                sim.directControl().exit();
            }
        } finally { Global.setSettings(previous); }
        return frames;
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, Selection selection,
                                          long id, Aim aim, DirectControlReticle.Look look, String name)
            throws Exception {
        float x = sim.world().renderX(id);
        float y = sim.world().renderY(id);
        // Along the body's own facing, so a mech's torso is aligned without a turn.
        double bearing = Math.toRadians(facing(sim, id) + 90.0);
        float aimX = x + (float) Math.cos(bearing) * aim.cells();
        float aimY = y + (float) Math.sin(bearing) * aim.cells();
        sim.directControl().submit(new ManualIntent(0f, 0f, aimX, aimY, false, false, false));
        if (sim.world().hasMechLoadout(id)) sim.world().mechLoadout(id).torsoFacingDegrees = facing(sim, id);
        var marks = DirectControlFireMarks.read(sim);

        PositionAPI position = position();
        BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
        camera.setViewport(0, 0, WIDTH, HEIGHT, WIDTH / CELLS_ACROSS);
        camera.centerOn((x + aimX) * 0.5f, (y + aimY) * 0.5f);
        BattleLayout layout = new BattleLayout(position, sim.getGrid().getWidth(), sim.getGrid().getHeight());
        BattleDirectControlOverlay control = new BattleDirectControlOverlay(selection, () -> {}, () -> {});
        control.attach(position, sim, 1);
        control.update(0, sim, 1);
        control.avoidControlledBody(camera.cellToScreenX(x), camera.cellToScreenY(y),
                sim.getRoster().radius(id) * camera.cellPxSize());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        BufferedImage image = renderer.renderHostPass(new BattleSceneHostPass() {
            @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                return new BattleSceneFrame(new RenderContext(sim, camera, layout, alpha, 0, false,
                        new HighlightOverlay(), selection, BattleRenderHostProfile.EMBEDDED_SCENE),
                        EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.VEHICLES,
                                RenderLayer.HIGHLIGHTS, RenderLayer.UNIT_SHADOWS, RenderLayer.UNITS,
                                RenderLayer.DRONES, RenderLayer.SHUTTLES, RenderLayer.AIRCRAFT_LIGHTS,
                                RenderLayer.NIGHT_LIGHTING));
            }
            @Override public void draw(CanvasHostViewport viewport, float alpha) { throw new IllegalStateException("Headless only"); }
        }, WIDTH, HEIGHT);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float sx = camera.cellToScreenX(aimX);
            float sy = camera.cellToScreenY(aimY);
            for (var origin : DirectControlAimOrigins.read(sim)) {
                var lane = DirectControlAimLane.observed(sim.getGrid(), origin, aimX, aimY, (cx, cy) -> true);
                if (lane == null) continue;
                draw(graphics, DirectControlAimLane.strokes(lane, camera.cellPxSize(),
                        camera.cellToScreenX(0f), camera.cellToScreenY(0f)), 1f);
            }
            if (sim.world().hasMechLoadout(id)) {
                var ring = DirectControlScatter.mech(sim.world().mechLoadout(id), sim.directControl().mechPicks(),
                        aim.cells());
                float r = ring != null ? DirectControlPanel.scatterRingPx(ring, camera.cellPxSize()) : -1f;
                if (r >= 0f) draw(graphics, DirectControlReticle.scatterRing(ring.mark(), sx, sy, r), 0.8f);
            }
            draw(graphics, DirectControlReticle.strokes(look, marks, sx, sy, DirectControlPanel.controlledColor()), 1f);
            caption(graphics, look, aim, marks);
            inset(graphics, image, sx, HEIGHT - sy);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            for (var surface : control.surfaces()) {
                UiViewport viewport = surface.viewport();
                require(viewport.documentScale() == 1, "100% scale");
                BufferedImage panel = renderer.render(surface.document(), Math.round(viewport.documentWidth()),
                        Math.round(viewport.documentHeight()));
                graphics.drawImage(panel, Math.round(viewport.screenX()),
                        HEIGHT - Math.round(viewport.screenY() + viewport.height()), null);
            }
        } finally {
            graphics.dispose();
        }
        return new SnapshotArtifact(name + "-1920x1080-ui100.png", image);
    }

    /** The review label: which shape, where the cursor is, and what each button's mark reads. */
    private static void caption(Graphics2D graphics, DirectControlReticle.Look look, Aim aim,
                                DirectControlFireMarks.Marks marks) {
        String text = String.format(Locale.ROOT, "%s   cursor %.0f cells   LMB %s   RMB %s", look.name(), aim.cells(),
                label(marks.left()), label(marks.right()));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        graphics.setColor(new Color(0, 0, 0, 170));
        graphics.fillRect(24, 24, graphics.getFontMetrics().stringWidth(text) + 24, 36);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, 36, 49);
    }

    /** The cursor's surroundings at four times, top right, so the shape can be judged; the frame keeps 100%. */
    private static void inset(Graphics2D graphics, BufferedImage image, float x, float y) {
        int half = 40;
        int zoom = 4;
        int left = Math.max(0, Math.min(WIDTH - half * 2, Math.round(x) - half));
        int top = Math.max(0, Math.min(HEIGHT - half * 2, Math.round(y) - half));
        BufferedImage crop = new BufferedImage(half * 2, half * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D copy = crop.createGraphics();
        copy.drawImage(image.getSubimage(left, top, half * 2, half * 2), 0, 0, null);
        copy.dispose();
        int size = half * 2 * zoom;
        int insetX = WIDTH - size - 24;
        int insetY = 80;
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(crop, insetX, insetY, size, size, null);
        graphics.setColor(new Color(0, 0, 0, 200));
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawRect(insetX, insetY, size, size);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        graphics.setColor(Color.WHITE);
        graphics.drawString("cursor at 4x", insetX + 8, insetY + size - 10);
    }

    private static String label(Mark mark) {
        return switch (mark) {
            case GOOD -> "white (fires well)";
            case FALLING_OFF -> "yellow (falling off)";
            case WASTED -> "red (past range)";
            case HELD -> "grey (won't fire)";
            case NONE -> "none";
        };
    }

    /** Strokes are in GL screen space, y up; the image is y down. */
    private static void draw(Graphics2D graphics, List<DirectControlAimLane.Stroke> strokes, float alpha) {
        for (var stroke : strokes) {
            Color c = stroke.color();
            graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                    Math.round(c.getAlpha() * alpha)));
            graphics.setStroke(new BasicStroke(stroke.width(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            graphics.draw(new Line2D.Float(stroke.x(), HEIGHT - stroke.y(), stroke.endX(), HEIGHT - stroke.endY()));
        }
    }

    private static float facing(BattleSimulation sim, long id) {
        var components = sim.getRoster().components();
        if (sim.world().hasMechLoadout(id)) {
            return sim.getEntityWorld().getFloat(id, components.MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        }
        return 0f;
    }

    private static void require(boolean valid, String reading) { if (!valid) throw new IllegalStateException(reading); }

    private static PositionAPI position() {
        return (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(), new Class<?>[]{PositionAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX", "getY" -> 0f; case "getWidth" -> (float) WIDTH; case "getHeight" -> (float) HEIGHT;
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
}
