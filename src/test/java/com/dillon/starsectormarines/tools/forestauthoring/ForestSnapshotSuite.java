package com.dillon.starsectormarines.tools.forestauthoring;

import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.tilesetauthoring.TreeReskinTemplate;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Container;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;

/** Real paint/erase/undo/save gestures and production rendering, outside the unit-test suite. */
public final class ForestSnapshotSuite implements SnapshotSuite {
    public String id() { return "forest-brush"; }
    public String label() { return "Overhead forest brush and canopy obstruction"; }
    public List<SnapshotArtifact> render(SnapshotContext c) throws Exception {
        TileRegistryTestInstaller.install(c.projectRoot());
        ForestPlan input = ForestPlan.read(new JSONObject(Files.readString(c.projectRoot().resolve("art-source/forests/woodland.forest.json"))));
        ForestPreview preview = new ForestPreview(c.projectRoot()); List<SnapshotArtifact> frames = new ArrayList<>();
        frames.add(new SnapshotArtifact("forest.png", preview.render(input, null).image()));
        frames.add(new SnapshotArtifact("canopy-variants.png", variants(c)));
        frames.add(new SnapshotArtifact("tree-reskin-template.png", TreeReskinTemplate.render()));
        ForestAuthoringPage[] page = new ForestAuthoringPage[1];
        SwingUtilities.invokeAndWait(() -> { try {
            page[0] = new ForestAuthoringPage(new AuthoringPageContext(c.projectRoot(), c.starsectorCore(), s -> {}, () -> {}));
            page[0].component().setSize(1920, 1080); layout(page[0].component());
        } catch (Exception ex) { throw new IllegalStateException(ex); } });
        Path folder = Files.createTempDirectory("forest-brush-flow-"), saved = folder.resolve("edited.forest.json");
        try {
            await(page[0]); frames.add(new SnapshotArtifact("editor.png", capture(page[0])));
            ForestPlan[] edited = new ForestPlan[1];
            SwingUtilities.invokeAndWait(() -> {
                Component canvas = find(page[0].component(), "forest-canvas");
                JSpinner radius = (JSpinner)find(page[0].component(), "forest-radius"); radius.setValue(4d);
                gesture(canvas, 42, 27, 61, 27, MouseEvent.BUTTON1);
                ForestField painted = ForestField.build(page[0].document());
                if (!painted.blocked(51, 27) || ForestField.build(input).blocked(51, 27))
                    throw new IllegalStateException("Paint gesture failed to add trees inside the empty lane");
                page[0].undo(); if (!page[0].document().equals(input)) throw new IllegalStateException("Paint undo failed");
                radius.setValue(3d);
                gesture(canvas, 49, 0, 49, 53, MouseEvent.BUTTON3);
                edited[0] = page[0].document();
                if (edited[0].strokes().size() != input.strokes().size() + 1) throw new IllegalStateException("Drag was not one stroke");
                page[0].undo(); if (!page[0].document().equals(input)) throw new IllegalStateException("Erase undo failed");
                page[0].redo(); if (!page[0].document().equals(edited[0])) throw new IllegalStateException("Redo failed");
                ((JCheckBox)find(page[0].component(), "forest-navigation")).doClick();
            });
            SwingUtilities.invokeAndWait(() -> { try {
                page[0].saveDocument(saved); page[0].openDocument(saved);
                if (!page[0].document().equals(edited[0]) || page[0].hasUnsavedChanges()) throw new IllegalStateException("Forest save/open failed");
            } catch (Exception ex) { throw new IllegalStateException(ex); } });
            await(page[0]); frames.add(new SnapshotArtifact("editor-cleared-path.png", capture(page[0])));
            var prepared = ForestPreview.prepare(edited[0], null);
            for (int y = 0; y < 54; y++) for (int x = 47; x <= 51; x++)
                if (!prepared.map().grid.isWalkable(x, y)) throw new IllegalStateException("Erased path still blocked");
            frames.add(new SnapshotArtifact("cleared-path.png", preview.render(edited[0], null).image()));
            System.out.println("Forest: " + prepared.field().trees().size() + " trees; full canopy obstruction; paint/erase/undo/redo/save/open verified.");
        } finally {
            SwingUtilities.invokeAndWait(() -> page[0].close()); Files.deleteIfExists(saved); Files.deleteIfExists(folder);
        }
        return frames;
    }
    private static BufferedImage variants(SnapshotContext c) throws Exception {
        var grid = new NavigationGrid(28, 19); var topology = new CellTopology(28, 19); List<Doodad> props = new ArrayList<>();
        for (int y = 0; y < 19; y++) for (int x = 0; x < 28; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        for (TreeCanopy tree : TreeCanopy.values()) {
            int x = 2 + tree.ordinal() % 3 * 9, y = tree.ordinal() < 3 ? 12 : 2;
            props.add(new Doodad(x, y, TileRegistry.installed().doodad(tree.id)));
            for (int dy = 0; dy < tree.height; dy++) for (int dx = 0; dx < tree.width; dx++) if (tree.blocks(dx, dy, 0)) {
                grid.setWalkable(x + dx, y + dy, false); grid.setSeeThrough(x + dx, y + dy, false);
                topology.setFixture(x + dx, y + dy, true);
            }
        }
        return new HeadlessBattleMapRenderer(c.modRoot()).render(new MapResult(grid, topology, 0, 0, 27, 18, List.of(), props), 42, 40);
    }
    private static void gesture(Component canvas, double x0, double y0, double x1, double y1, int button) {
        double scale = Math.min(canvas.getWidth() / 96d, canvas.getHeight() / 54d), ox = (canvas.getWidth() - 96 * scale) / 2, oy = (canvas.getHeight() - 54 * scale) / 2;
        int a = (int)Math.round(ox + (x0 + .5) * scale), b = (int)Math.round(canvas.getHeight() - oy - (y0 + .5) * scale);
        int x = (int)Math.round(ox + (x1 + .5) * scale), y = (int)Math.round(canvas.getHeight() - oy - (y1 + .5) * scale);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, a, b, 1, false, button));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 2, 0, x, y, 1, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 3, 0, x, y, 1, false, button));
    }
    private static void await(ForestAuthoringPage page) throws Exception {
        long until = System.nanoTime() + 60_000_000_000L; boolean[] ready = {false};
        while (!ready[0] && System.nanoTime() < until) { Thread.sleep(100); SwingUtilities.invokeAndWait(() -> ready[0] = page.previewReady()); }
        if (!ready[0]) throw new IllegalStateException("Forest preview did not finish");
    }
    private static BufferedImage capture(ForestAuthoringPage page) throws Exception {
        BufferedImage ui = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> { Graphics2D g = ui.createGraphics(); page.component().printAll(g); g.dispose(); }); return ui;
    }
    private static Component find(Component c, String name) { if (name.equals(c.getName())) return c; if (c instanceof Container p) for (Component child : p.getComponents()) { Component found = find(child, name); if (found != null) return found; } return null; }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }
}
