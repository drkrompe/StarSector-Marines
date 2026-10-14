package com.dillon.starsectormarines.tools.damageauthoring;

import com.dillon.starsectormarines.battle.world.gen.damage.DamageCatalog;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Layer;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Mode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Production damage, intact restoration and real workbench gestures at 1920x1080 / 100%. */
public final class DamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "relief-damage"; }
    public String label() { return "Relief damage layers and authoring"; }
    public List<SnapshotArtifact> render(SnapshotContext c) throws Exception {
        DiskRegistries.installMapGeneration(c.projectRoot());
        DamagePlan plan = DamagePlan.read(new JSONObject(Files.readString(c.modRoot().resolve(DamageCatalog.RELIEF))));
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(c.modRoot()), c.modRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        long intactRandom = 0;
        for (long seed = 8101; seed <= 8103; seed++) for (boolean intact : new boolean[]{true, false}) {
            MapResult map = DamagePreview.prepare(plan, seed, intact);
            try (var scene = new ShipDeckBattleScene(map, 0)) {
                long nextRandom = scene.simulation().random().nextLong();
                if (intact) intactRandom = nextRandom;
                else if (intactRandom != nextRandom) throw new IllegalStateException("Authored damage consumed battle randomness");
                int scars = 0, roofs = 0, remnants = 0;
                for (int y = 0; y < plan.height(); y++) for (int x = 0; x < plan.width(); x++) {
                    if (map.topology.hasTag(x, y, Tag.GROUND_DAMAGED)) scars++;
                    if (map.topology.isRoofDestroyed(x, y)) roofs++;
                    if (map.topology.getWallRemnant(x, y) != null) remnants++;
                }
                if (!intact && (scars < 70 || roofs < 25 || remnants < 8)) throw new IllegalStateException("Relief aftermath missing: " + scars + "/" + roofs + "/" + remnants);
                if (intact && (scars != 0 || roofs != 0 || remnants != 0)) throw new IllegalStateException("Intact base retained damage");
                for (var pad : map.landingPads) if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Damage reached a berth");
                for (var poi : map.pointsOfInterest) if (Paths.isEmpty(GridPathfinder.findPath(map.grid, poi.interiorAnchorX, poi.interiorAnchorY, 83, 30))) throw new IllegalStateException("Damage disconnected an objective");
                if (!intact) {
                    if (!map.grid.isWalkable(92, 50)) throw new IllegalStateException("Broken fence retained obstruction");
                    if (scene.simulation().getDoodads().stream().noneMatch(d -> "doodad.industrial-machine-tool-destroyed".equals(d.definitionId()))) throw new IllegalStateException("Workshop machinery is not wrecked");
                }
                if (seed == 8101) {
                    String state = intact ? "intact" : "damaged";
                    frames.add(new SnapshotArtifact("relief-" + state + ".png", renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.fitting(0, 0, 144, 64, 1920, 1080)), 1920, 1080)));
                    frames.add(new SnapshotArtifact("workshop-" + state + ".png", renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.fitting(78, 29, 48, 27, 1920, 1080)), 1920, 1080)));
                }
                System.out.println("[relief-damage] seed=" + seed + " intact=" + intact + " scars=" + scars + " roof holes=" + roofs + " wall remnants=" + remnants);
            }
        }
        var preview = new DamagePreview(c.projectRoot());
        DamagePlan hidden = new DamagePlan(plan.width(), plan.height(), plan.seed(), plan.layers().stream().map(l -> new Layer(l.name(), false, l.strokes())).toList());
        BufferedImage base = preview.render(plan, true), restored = preview.render(hidden, false);
        if (!Arrays.equals(base.getRGB(0, 0, 1920, 1080, null, 0, 1920), restored.getRGB(0, 0, 1920, 1080, null, 0, 1920))) throw new IllegalStateException("Hidden layers did not restore the intact pixels");
        frames.add(new SnapshotArtifact("relief-restored.png", restored));
        DamageAuthoringPage[] page = {null}; DamagePlan[] edited = {null};
        Path folder = Files.createTempDirectory("damage-editor-"), saved = folder.resolve("relief.damage.json");
        try {
            SwingUtilities.invokeAndWait(() -> { try {
                page[0] = new DamageAuthoringPage(new AuthoringPageContext(c.projectRoot(), c.projectRoot(), ignored -> {}, () -> {}));
                page[0].component().setSize(1920, 1080); layout(page[0].component());
                Component canvas = find(page[0].component(), "damage-canvas");
                gesture(canvas, 104, 47, 105, 46, MouseEvent.BUTTON1);
                edited[0] = page[0].document();
                if (edited[0].layers().get(0).strokes().size() != plan.layers().get(0).strokes().size() + 1) throw new IllegalStateException("Drag did not append one stroke");
                page[0].undo(); if (!page[0].document().equals(plan)) throw new IllegalStateException("Undo failed");
                page[0].redo(); if (!page[0].document().equals(edited[0])) throw new IllegalStateException("Redo failed");
                gesture(canvas, 104, 47, 105, 46, MouseEvent.BUTTON3);
                var strokes = page[0].document().layers().get(0).strokes();
                if (strokes.get(strokes.size() - 1).mode() != Mode.ERASE) throw new IllegalStateException("Right drag did not erase");
                page[0].undo();
                JCheckBox enabled = (JCheckBox)find(page[0].component(), "damage-layer-enabled"); enabled.doClick();
                if (page[0].document().layers().get(0).enabled()) throw new IllegalStateException("Layer toggle failed");
                page[0].undo();
                page[0].saveDocument(saved); page[0].openDocument(saved);
                if (!page[0].document().equals(edited[0]) || page[0].hasUnsavedChanges()) throw new IllegalStateException("Save/reopen failed");
            } catch (Exception ex) { throw new IllegalStateException(ex); } });
            long until = System.nanoTime() + 60_000_000_000L; boolean[] ready = {false};
            while (!ready[0] && System.nanoTime() < until) { Thread.sleep(100); SwingUtilities.invokeAndWait(() -> ready[0] = page[0].previewReady()); }
            if (!ready[0]) throw new IllegalStateException("Editor preview did not finish");
            BufferedImage ui = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> { Graphics2D g = ui.createGraphics(); page[0].component().printAll(g); g.dispose(); });
            frames.add(new SnapshotArtifact("damage-editor.png", ui));
        } finally { if (page[0] != null) SwingUtilities.invokeAndWait(() -> page[0].close()); Files.deleteIfExists(saved); Files.deleteIfExists(folder); }
        System.out.println("[relief-damage] Hidden layers restore exact intact pixels; paint/erase/undo/redo/toggle/save/reopen verified.");
        return frames;
    }
    private static void gesture(Component canvas, double ax, double ay, double bx, double by, int button) {
        double scale = Math.min(canvas.getWidth() / 144d, canvas.getHeight() / 64d), ox = (canvas.getWidth() - 144 * scale) / 2, oy = (canvas.getHeight() - 64 * scale) / 2;
        int x0 = (int)Math.round(ox + (ax + .5) * scale), y0 = (int)Math.round(canvas.getHeight() - oy - (ay + .5) * scale);
        int x1 = (int)Math.round(ox + (bx + .5) * scale), y1 = (int)Math.round(canvas.getHeight() - oy - (by + .5) * scale);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, x0, y0, 1, false, button));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 2, 0, x1, y1, 1, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 3, 0, x1, y1, 1, false, button));
    }
    private static Component find(Component c, String name) { if (name.equals(c.getName())) return c; if (c instanceof Container p) for (Component child : p.getComponents()) { Component found = find(child, name); if (found != null) return found; } return null; }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }
}
