package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimLane;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimOrigins;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlPanel;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlScatter;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.tools.snapshot.CompressedFrameList;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A controlled Marine with its left button on the primary or on a direct-fire special, as the player sees it:
 * the battle drawn through the look preset on real generated ground, the aim lane and reticle from the production
 * marks and spread, and the production HUD panels and Marine doll for the same moment. Every value is read out of
 * one simulation driven through the real direct-control session; nothing is set by hand but the input.
 */
public final class MarineLeftSpecialSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "marine-left-special"; }
    @Override public String label() { return "Marine left button on a special"; }

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    /** Close enough to read what the Marine holds, wide enough to keep the cursor in frame. */
    private static final float CELLS_ACROSS = 14f;
    /** A close framing, for reading the carried art itself: the Marine is about a sixth of the frame across. */
    private static final float CLOSE_CELLS_ACROSS = 5f;

    /**
     * One frame: the special the Marine carries (null for a rifleman), its rounds, how many Q presses, whether the
     * Marine is walking at the moment of the frame, and a click before it.
     */
    record Case(String name, String specialId, int ammo, int cycles, boolean walking, boolean fires, int fireLead) {
        Case(String name, String specialId, int ammo, int cycles, boolean walking, boolean fires) {
            this(name, specialId, ammo, cycles, walking, fires, 6);
        }
    }

    /** The flamer picked and held: a charge of the jet on its way, a charge spent from the tank. */
    static final Case FLAMER_STREAMING = new Case("flamer-streaming", SpecialEquipmentRegistry.FLAMER_ID, 8, 1,
            false, true);

    private static final List<Case> CASES = List.of(
            new Case("rifle", null, 0, 0, true, false),
            new Case("rocket-walking", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 3, 1, true, false),
            new Case("rocket-standing", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 3, 1, false, false),
            new Case("rocket-fired", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 3, 1, false, true),
            new Case("amr-walking", SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID, 4, 1, true, false),
            new Case("amr-standing", SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID, 4, 1, false, false),
            new Case("rocket-empty", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 0, 1, true, false),
            new Case("flamer-walking", SpecialEquipmentRegistry.FLAMER_ID, 8, 1, true, false),
            FLAMER_STREAMING);

    /** The flamer's pack, wand and hose at the close framing: walking, held at the aim, streaming. */
    private static final List<Case> CLOSE_CASES = List.of(
            new Case("flamer-walking-close", SpecialEquipmentRegistry.FLAMER_ID, 8, 1, true, false),
            new Case("flamer-held-close", SpecialEquipmentRegistry.FLAMER_ID, 8, 1, false, false),
            new Case("flamer-streaming-close", SpecialEquipmentRegistry.FLAMER_ID, 8, 1, false, true),
            // The first frames of a jet, two and four ticks after the click: it leaves the wand's nozzle.
            new Case("flamer-first-close", SpecialEquipmentRegistry.FLAMER_ID, 8, 1, false, true, 2),
            new Case("flamer-first-later-close", SpecialEquipmentRegistry.FLAMER_ID, 8, 1, false, true, 4));

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(context.modRoot().resolve(path)));
        try {
            HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
            SceneRegistries.installArmoury(context.projectRoot());
            HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            for (Case c : CASES) {
                artifacts.add(new SnapshotArtifact(c.name() + "-1920x1080-ui100.png",
                        frame(renderer, context.modRoot(), c, CELLS_ACROSS)));
            }
            for (Case c : CLOSE_CASES) {
                artifacts.add(new SnapshotArtifact(c.name() + "-1920x1080-ui100.png",
                        frame(renderer, context.modRoot(), c, CLOSE_CELLS_ACROSS)));
            }
            artifacts.add(flamerSweep(renderer, context.modRoot()));
            return artifacts;
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    /** The flamer-streaming frame for another suite, with the armoury catalogs this one installs. */
    static BufferedImage flamerFrame(HeadlessUiRenderer renderer, SnapshotContext context) throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        SceneRegistries.installArmoury(context.projectRoot());
        return frame(renderer, context.modRoot(), FLAMER_STREAMING, CELLS_ACROSS);
    }

    private static BufferedImage frame(HeadlessUiRenderer renderer, Path modRoot, Case c, float cellsAcross)
            throws Exception {
        LookGround ground = LookGround.verdant();
        MapResult map = ground.generate();
        LookGround.Site site = LookGround.openSite(map, 40, 24);
        SceneWorld world = new LookReview.Placement(ground, map, site)
                .builder()
                .unit("marine", Faction.MARINE, UnitType.MARINE, 8, 12, spec -> {
                    spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));
                    if (c.specialId() != null) {
                        spec.specialEquipment(SpecialEquipmentRegistry.require(c.specialId()), c.ammo());
                    }
                })
                .unit("target", Faction.DEFENDER, UnitType.MARINE, 34, 10, spec -> spec.moveSpeed(0f))
                .build();
        try (BattleSimulation sim = world.sim()) {
            sim.random().setSeed(20261023L);
            long marine = world.unit("marine");
            if (!sim.directControl().enter(marine)) throw new IllegalStateException("could not take the Marine");
            for (int i = 0; i < c.cycles(); i++) sim.directControl().cycle(FireTrigger.LEFT);
            ReviewImpactFx fx = new ReviewImpactFx(modRoot);
            // Walk east for two seconds, long enough to ready either special, aiming ahead of the walk.
            int ticks = 60;
            int fireAt = c.fires() ? ticks - c.fireLead() : -1;
            float aimX = Float.NaN;
            float aimY = Float.NaN;
            for (int tick = 0; tick < ticks; tick++) {
                aimX = sim.world().renderX(marine) + 5f;
                aimY = sim.world().renderY(marine) + 1.5f;
                boolean walking = c.walking() || tick < ticks / 2;
                sim.directControl().submit(new ManualIntent(walking ? 1 : 0, 0, aimX, aimY,
                        tick >= fireAt && fireAt >= 0));
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
            }
            float lead = cellsAcross < CELLS_ACROSS ? 0.6f : 2.5f;
            return compose(renderer, modRoot, sim, marine, aimX, aimY, fx,
                    sim.world().renderX(marine) + lead, sim.world().renderY(marine) + 0.5f * lead / 2.5f,
                    cellsAcross);
        }
    }

    /** Ticks between the frames of the sweep: six a second, few enough to keep a GIF of a field on fire small. */
    private static final int SWEEP_FRAME_TICKS = 5;
    /** Long enough to ready the flamer and empty its eight-charge tank on its cooldown. */
    private static final int SWEEP_TICKS = 390;
    /** Thirty seconds after the tank is empty, so the frames show whether the fire goes out or keeps running. */
    private static final int SWEEP_AFTER_TICKS = 30 * 30;
    /** The aftermath is a time-lapse: a frame each half second, played at the sweep's frame delay. */
    private static final int SWEEP_AFTER_FRAME_TICKS = 15;
    /** The sweep's arc about due east, in degrees either side, and its radius in cells. */
    private static final float SWEEP_HALF_ARC = 40f;
    private static final float SWEEP_RADIUS = 6.5f;

    /**
     * The flamer in action: a standing Marine picks it and holds the left button while the cursor sweeps an arc
     * across a line of defenders, so each charge lands further along and the tank empties cell by cell; then it lets
     * go and the frames run on, faster, for half a minute while the fire burns down.
     */
    static SnapshotArtifact flamerSweep(HeadlessUiRenderer renderer, Path modRoot) throws Exception {
        LookGround ground = LookGround.verdant();
        MapResult map = ground.generate();
        LookGround.Site site = LookGround.openSite(map, 40, 24);
        LookReview.Placement placement = new LookReview.Placement(ground, map, site);
        var builder = placement.builder()
                .unit("marine", Faction.MARINE, UnitType.MARINE, 8, 12, spec -> {
                    spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));
                    spec.specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FLAMER_ID), 8);
                });
        int[][] line = {{13, 8}, {14, 11}, {14, 13}, {13, 16}};
        for (int i = 0; i < line.length; i++) {
            // Held in place and unable to answer, so the sweep runs to an empty tank whatever the fire does.
            builder.unit("defender" + i, Faction.DEFENDER, UnitType.MARINE, line[i][0], line[i][1],
                    spec -> spec.moveSpeed(0f).attackRange(1f).accuracy(0f));
        }
        SceneWorld world = builder.build();
        CompressedFrameList frames = new CompressedFrameList();
        try (BattleSimulation sim = world.sim()) {
            sim.random().setSeed(20261023L);
            long marine = world.unit("marine");
            if (!sim.directControl().enter(marine)) throw new IllegalStateException("could not take the Marine");
            sim.directControl().cycle(FireTrigger.LEFT);
            ReviewImpactFx fx = new ReviewImpactFx(modRoot);
            float focusX = sim.world().renderX(marine) + 3f;
            float focusY = sim.world().renderY(marine);
            for (int tick = 0; tick < SWEEP_TICKS; tick++) {
                float t = tick / (float) (SWEEP_TICKS - 1);
                double bearing = Math.toRadians(-SWEEP_HALF_ARC + 2f * SWEEP_HALF_ARC * t);
                float aimX = sim.world().renderX(marine) + SWEEP_RADIUS * (float) Math.cos(bearing);
                float aimY = sim.world().renderY(marine) + SWEEP_RADIUS * (float) Math.sin(bearing);
                sim.directControl().submit(new ManualIntent(0, 0, aimX, aimY, true));
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
                if (!sim.world().isAlive(marine)) break;
                if (tick % SWEEP_FRAME_TICKS == 0) {
                    frames.add(compose(renderer, modRoot, sim, marine, aimX, aimY, fx, focusX, focusY,
                            CELLS_ACROSS));
                }
            }
            float restX = sim.world().renderX(marine) + SWEEP_RADIUS;
            float restY = sim.world().renderY(marine);
            for (int tick = 0; tick < SWEEP_AFTER_TICKS && sim.world().isAlive(marine); tick++) {
                sim.directControl().submit(new ManualIntent(0, 0, restX, restY, false));
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
                if (tick % SWEEP_AFTER_FRAME_TICKS == 0 && sim.world().isAlive(marine)) {
                    frames.add(compose(renderer, modRoot, sim, marine, restX, restY, fx, focusX, focusY,
                            CELLS_ACROSS));
                }
            }
        }
        return SnapshotArtifact.animation("flamer-sweep-1920x1080-ui100.gif", frames,
                SWEEP_FRAME_TICKS * Math.round(BattleSimulation.TICK_DT * 1000f));
    }

    /** The battle through the look preset with the aim drawn over it, under the production HUD and doll. */
    private static BufferedImage compose(HeadlessUiRenderer renderer, Path modRoot, BattleSimulation sim, long marine,
                                         float cursorX, float cursorY, ReviewImpactFx fx,
                                         float focusX, float focusY, float cellsAcross) throws Exception {
        BufferedImage battle = BattleReviewFrameRenderer.look(modRoot, WIDTH, HEIGHT, focusX, focusY, cellsAcross)
                .render(sim, "", ReviewAnnotations.NONE, fx);
        BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
        camera.setViewport(0, 0, WIDTH, HEIGHT, (float) Math.floor(WIDTH / cellsAcross));
        camera.centerOn(focusX, focusY);
        var snapshot = BattleDirectControlStatus.snapshot(sim);
        var copy = ModStrings.fromDisk(modRoot);
        var doll = new BattleMarineDollPlate(path -> Files.readString(modRoot.resolve(path)), copy, true);
        doll.update(BattleMarineDollPlate.MarineDoll.read(sim, marine, copy));
        return DirectControlUiSnapshotSuite.activeComposite(renderer, modRoot, WIDTH, HEIGHT, 1f, snapshot,
                doll.document(), false, false, graphics -> {
                    graphics.drawImage(battle, 0, 0, null);
                    drawAim(graphics, sim, camera, cursorX, cursorY);
                });
    }

    /** The aim lane and the reticle as the panel draws them, flipped from its y-up frame into this y-down one. */
    private static void drawAim(Graphics2D graphics, BattleSimulation sim, BattleCamera camera,
                                float cursorX, float cursorY) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        List<DirectControlAimLane.Stroke> strokes = new ArrayList<>();
        for (var origin : DirectControlAimOrigins.read(sim)) {
            var lane = DirectControlAimLane.observed(sim.getGrid(), origin, cursorX, cursorY, (x, y) -> true);
            if (lane == null) continue;
            strokes.addAll(DirectControlAimLane.strokes(lane, camera.cellPxSize(),
                    camera.cellToScreenX(0f), camera.cellToScreenY(0f)));
        }
        var spread = DirectControlScatter.read(sim);
        float cellPx = camera.cellPxSize();
        float aimX = camera.cellToScreenX(cursorX);
        float aimY = camera.cellToScreenY(cursorY);
        float[] origin = DirectControlFireMarks.fireOrigin(sim);
        float fireX = origin != null ? aimX - camera.cellToScreenX(origin[0]) : 0f;
        float fireY = origin != null ? aimY - camera.cellToScreenY(origin[1]) : 0f;
        strokes.addAll(DirectControlReticle.strokes(DirectControlFireMarks.read(sim),
                spread.leftCells() * cellPx, spread.rightCells() * cellPx, aimX, aimY, fireX, fireY,
                DirectControlPanel.controlledColor()));
        for (var stroke : strokes) {
            Color color = stroke.color();
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(stroke.width()));
            graphics.draw(new Line2D.Float(stroke.x(), HEIGHT - stroke.y(), stroke.endX(), HEIGHT - stroke.endY()));
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }
}
