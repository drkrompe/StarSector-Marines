package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Program;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Role;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Maps page driven as an author drives it, at 1920x1080 / 100%: Shepherd Basin opened, a drop drawn by clicks
 * across its south, the roads it cuts bridged, a land bridge clicked onto it, a rise drawn, a town dragged out and a
 * site placed; undo and redo proved; then the page captured as two worlds and the all-worlds sheet drawn. Writes the
 * edits as a draft under {@code build/map-authoring/}; nothing under mod/.
 */
public final class MapsEditorSnapshotSuite implements SnapshotSuite {

    @Override public String id() { return "maps-editor"; }
    @Override public String label() { return "Maps page: owned-map editor, gestures and all-worlds sheet"; }
    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext c) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        MapsAuthoringPage[] page = {null};
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    page[0] = new MapsAuthoringPage(new AuthoringPageContext(c.projectRoot(), c.projectRoot(), s -> { }, () -> { }));
                    page[0].component().setSize(1920, 1080);
                    layout(page[0].component());
                } catch (Exception ex) { throw new IllegalStateException(ex); }
            });
            await(page[0]);
            on(() -> {
                MapsAuthoringPage p = page[0];
                Component canvas = p.canvas();
                MapSketch before = p.document();
                // A drop across the south: clicks along its course, each point at the depth set before it.
                p.selectTool(MapsAuthoringPage.Tool.DROP);
                p.setWidth(10); p.setTerraces(3);
                double[][] course = {{2, 37, 30}, {90, 36, 45}, {165, 38, 55}, {240, 33, 38}, {302, 40, 28}};
                for (int k = 0; k < course.length; k++) {
                    p.setMetres(course[k][2]);
                    click(canvas, p, new Pt((float) course[k][0], (float) course[k][1]), 1);
                    if (k == course.length - 1) click(canvas, p, new Pt((float) course[k][0], (float) course[k][1]), 2);
                }
                if (p.document().drops().size() != 1) throw new IllegalStateException("Clicking a course did not add one drop");
                p.undo();
                if (!p.document().equals(before)) throw new IllegalStateException("Undo did not remove the drop");
                p.redo();
                p.bridgeRoads();
                p.selectTool(MapsAuthoringPage.Tool.CROSSING);
                p.setWidth(6);
                p.setCrossing(MapSketch.CrossingKind.LAND_BRIDGE);
                click(canvas, p, new Pt(28, 37), 1);
                if (p.document().drops().get(0).crossings().size() < 2) throw new IllegalStateException("Crossings were not added");
                p.selectTool(MapsAuthoringPage.Tool.RISE);
                p.setWidth(12); p.setTerraces(5);
                double[][] crest = {{60, 150, 18}, {95, 175, 26}, {150, 190, 22}};
                for (int k = 0; k < crest.length; k++) {
                    p.setMetres(crest[k][2]);
                    click(canvas, p, new Pt((float) crest[k][0], (float) crest[k][1]), 1);
                    if (k == crest.length - 1) click(canvas, p, new Pt((float) crest[k][0], (float) crest[k][1]), 2);
                }
                p.selectTool(MapsAuthoringPage.Tool.SETTLEMENT);
                p.setProgram(Program.TOWN, "pump workers");
                drag(canvas, p, new Pt(200, 77), new Pt(234, 55));
                p.selectTool(MapsAuthoringPage.Tool.PLACE);
                p.setPlace(Role.SITE, "Gorge bridge picket");
                click(canvas, p, new Pt(186, 52), 1);
                MapSketch s = p.document();
                if (s.rises().size() != 1 || s.settlements().size() != 1 || s.places().size() != 8) {
                    throw new IllegalStateException("Gestures did not make the edits: " + s);
                }
                p.selectTool(MapsAuthoringPage.Tool.SELECT);
                p.selectEdit(s.drops().get(0).id());
            });
            await(page[0]);
            out.add(new SnapshotArtifact("maps-editor-arid.png", capture(page[0])));
            List<String> problems = page[0].problems();
            System.out.println("[maps-editor] checks: " + (problems.isEmpty() ? "all pass" : problems));
            on(() -> page[0].save());
            on(() -> page[0].selectWorld(BiomeSkin.FROZEN));
            await(page[0]);
            out.add(new SnapshotArtifact("maps-editor-frozen.png", capture(page[0])));
            on(() -> page[0].selectWorld(BiomeSkin.TOXIC));
            await(page[0]);
            out.add(new SnapshotArtifact("maps-editor-toxic.png", capture(page[0])));
            MapSketch edits = page[0].document();
            MapBases.Base base = MapBases.find(edits.base());
            SwingUtilities.invokeAndWait(() -> page[0].close());
            page[0] = null;
            try (MapsSession session = new MapsSession(c.projectRoot())) {
                out.add(new SnapshotArtifact("maps-all-worlds.png", session.contactSheet(base, edits)));
            }
        } finally {
            if (page[0] != null) SwingUtilities.invokeAndWait(() -> page[0].close());
        }
        return out;
    }

    private static void on(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void await(MapsAuthoringPage page) throws Exception {
        long until = System.nanoTime() + 180_000_000_000L;
        boolean[] ready = {false};
        while (!ready[0] && System.nanoTime() < until) {
            Thread.sleep(150);
            SwingUtilities.invokeAndWait(() -> ready[0] = page.previewReady());
        }
        if (!ready[0]) throw new IllegalStateException("The Maps page did not finish redrawing");
    }

    private static BufferedImage capture(MapsAuthoringPage page) throws Exception {
        BufferedImage ui = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Supplier<Void> paint = () -> { Graphics2D g = ui.createGraphics(); page.component().printAll(g); g.dispose(); return null; };
        SwingUtilities.invokeAndWait(paint::get);
        return ui;
    }

    private static void click(Component canvas, MapsAuthoringPage p, Pt cell, int count) {
        Point at = p.canvas().at(cell);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, at.x, at.y, count, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 2, 0, at.x, at.y, count, false, MouseEvent.BUTTON1));
    }

    private static void drag(Component canvas, MapsAuthoringPage p, Pt from, Pt to) {
        Point a = p.canvas().at(from), b = p.canvas().at(to);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, a.x, a.y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 2, 0, b.x, b.y, 1, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 3, 0, b.x, b.y, 1, false, MouseEvent.BUTTON1));
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested);
    }
}
