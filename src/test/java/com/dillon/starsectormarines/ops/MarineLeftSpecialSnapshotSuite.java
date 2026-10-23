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

    /**
     * One frame: the special the Marine carries (null for a rifleman), its rounds, how many Q presses, whether the
     * Marine is walking at the moment of the frame, and a click before it.
     */
    private record Case(String name, String specialId, int ammo, int cycles, boolean walking, boolean fires) { }

    private static final List<Case> CASES = List.of(
            new Case("rifle", null, 0, 0, true, false),
            new Case("rocket-walking", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 3, 1, true, false),
            new Case("rocket-standing", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 3, 1, false, false),
            new Case("rocket-fired", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 3, 1, false, true),
            new Case("amr-walking", SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID, 4, 1, true, false),
            new Case("amr-standing", SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID, 4, 1, false, false),
            new Case("rocket-empty", SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 0, 1, true, false));

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
                        frame(renderer, context.modRoot(), c)));
            }
            return artifacts;
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    private static BufferedImage frame(HeadlessUiRenderer renderer, Path modRoot, Case c) throws Exception {
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
            int fireAt = c.fires() ? ticks - 6 : -1;
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
            float focusX = sim.world().renderX(marine) + 2.5f;
            float focusY = sim.world().renderY(marine) + 0.5f;
            BufferedImage battle = BattleReviewFrameRenderer.look(modRoot, WIDTH, HEIGHT, focusX, focusY, CELLS_ACROSS)
                    .render(sim, "", ReviewAnnotations.NONE, fx);
            BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
            camera.setViewport(0, 0, WIDTH, HEIGHT, (float) Math.floor(WIDTH / CELLS_ACROSS));
            camera.centerOn(focusX, focusY);
            var snapshot = BattleDirectControlStatus.snapshot(sim);
            var copy = ModStrings.fromDisk(modRoot);
            var doll = new BattleMarineDollPlate(path -> Files.readString(modRoot.resolve(path)), copy, true);
            doll.update(BattleMarineDollPlate.MarineDoll.read(sim, marine, copy));
            float cursorX = aimX;
            float cursorY = aimY;
            return DirectControlUiSnapshotSuite.activeComposite(renderer, modRoot, WIDTH, HEIGHT, 1f, snapshot,
                    doll.document(), false, false, graphics -> {
                        graphics.drawImage(battle, 0, 0, null);
                        drawAim(graphics, sim, camera, cursorX, cursorY);
                    });
        }
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
