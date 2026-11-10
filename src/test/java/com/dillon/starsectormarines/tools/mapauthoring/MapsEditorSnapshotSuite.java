package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapCatalog;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapJson;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Maps page driven as an author drives it, at 1920x1080 / 100%: Relay Nine opened from its owned map, a rift drawn
 * by clicks down the west strip, deepened with the wheel and crossed, a hamlet's lot dragged out to terrace into the
 * south wash, a bank dragged out and a watch post placed; undo and redo proved; the edits saved into a temporary
 * folder (every check on every world first) and read back equal; then the page captured as three worlds and the
 * all-worlds sheet drawn. Nothing is written under mod/.
 */
public final class MapsEditorSnapshotSuite implements SnapshotSuite {

    @Override public String id() { return "maps-editor"; }
    @Override public String label() { return "Maps page: the owned-map editor driven by its gestures, and its all-worlds sheet"; }
    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext c) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        MapsAuthoringPage[] page = {null};
        Path temp = Files.createTempDirectory("maps-editor-");
        try {
            on(() -> {
                try {
                    page[0] = new MapsAuthoringPage(new AuthoringPageContext(c.projectRoot(), c.projectRoot(), s -> { }, () -> { }));
                    page[0].component().setSize(1920, 1080);
                    layout(page[0].component());
                    page[0].saveInto(temp);
                } catch (Exception ex) { throw new IllegalStateException(ex); }
            });
            await(page[0]);
            on(() -> {
                MapsAuthoringPage p = page[0];
                Component canvas = p.canvas();
                MapDocument before = p.document();
                p.selectTool(MapsAuthoringPage.Tool.DROP);
                p.setDropRole(OwnedMap.DropRole.RIFT);
                p.setWidth(5);
                int[][] course = {{4, 4, 40}, {5, 30, 70}, {4, 54, 60}};
                for (int k = 0; k < course.length; k++) {
                    p.setShare(course[k][2]);
                    click(canvas, p, new Cell(course[k][0], course[k][1]), 1);
                }
                click(canvas, p, new Cell(4, 54), 2);
                if (p.document().map().drops().size() != 2) throw new IllegalStateException("Clicking a course did not add one drop");
                p.undo();
                if (!p.document().equals(before)) throw new IllegalStateException("Undo did not remove the drop");
                p.redo();
                OwnedMap.Drop rift = p.document().map().drop("drop-1");
                p.selectTool(MapsAuthoringPage.Tool.SELECT);
                wheel(canvas, p, new Cell(5, 30), -4);
                if (MapDocument.depthAt(p.document().map().drop("drop-1"), 1) <= MapDocument.depthAt(rift, 1)) {
                    throw new IllegalStateException("The wheel did not deepen the drop's point");
                }
                p.selectTool(MapsAuthoringPage.Tool.CROSSING);
                p.setWidth(4);
                click(canvas, p, new Cell(5, 30), 1);
                p.selectTool(MapsAuthoringPage.Tool.SETTLEMENT);
                p.setSettlement(OwnedMap.SettlementRole.TOWN, "wash-hamlet", true);
                drag(canvas, p, new Cell(136, 26), new Cell(150, 8));
                p.selectTool(MapsAuthoringPage.Tool.RISE);
                p.setShare(50);
                drag(canvas, p, new Cell(50, 118), new Cell(62, 106));
                p.selectTool(MapsAuthoringPage.Tool.SITE);
                p.setSite(TacticalNode.Kind.GUARDPOST, "Wash watch");
                click(canvas, p, new Cell(140, 30), 1);
                OwnedMap m = p.document().map();
                OwnedMap.Settlement hamlet = m.settlements().get(m.settlements().size() - 1);
                if (m.drop("drop-1").crossings().size() != 1 || m.rises().size() != 1 || m.site("Wash watch") == null
                        || !"south-wash".equals(hamlet.terraceInto())) {
                    throw new IllegalStateException("Gestures did not make the edits");
                }
                p.selectTool(MapsAuthoringPage.Tool.SELECT);
                p.selectEdit("drop-1");
            });
            await(page[0]);
            out.add(new SnapshotArtifact("maps-editor-arid.png", capture(page[0])));
            System.out.println("[maps-editor] arid checks: " + (page[0].problems().isEmpty() ? "all pass" : page[0].problems()));
            on(() -> page[0].save());
            await(page[0]);
            System.out.println("[maps-editor] save: " + page[0].status());
            Path saved = temp.resolve("mod").resolve(OwnedMapCatalog.path(OwnedMapCatalog.RELAY_NINE));
            if (!Files.exists(saved) || !OwnedMapJson.read(new JSONObject(Files.readString(saved))).equals(page[0].document().map())) {
                throw new IllegalStateException("The saved map did not read back as the page holds it: " + page[0].status());
            }
            for (MapWorld world : new MapWorld[]{MapWorld.FROZEN, MapWorld.TOXIC}) {
                on(() -> page[0].selectWorld(world));
                await(page[0]);
                out.add(new SnapshotArtifact("maps-editor-" + world.name().toLowerCase() + ".png", capture(page[0])));
            }
            MapDocument edits = page[0].document();
            on(() -> page[0].close());
            page[0] = null;
            try (MapsSession session = new MapsSession(c.projectRoot())) {
                out.add(new SnapshotArtifact("maps-all-worlds.png", session.contactSheet(edits)));
            }
        } finally {
            if (page[0] != null) on(() -> page[0].close());
            try (Stream<Path> walk = Files.walk(temp)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        return out;
    }

    private static void on(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void await(MapsAuthoringPage page) throws Exception {
        long until = System.nanoTime() + 600_000_000_000L;
        boolean[] ready = {false};
        while (!ready[0] && System.nanoTime() < until) {
            Thread.sleep(150);
            SwingUtilities.invokeAndWait(() -> ready[0] = page.previewReady());
        }
        if (!ready[0]) throw new IllegalStateException("The Maps page did not finish");
    }

    private static BufferedImage capture(MapsAuthoringPage page) throws Exception {
        BufferedImage ui = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        on(() -> { Graphics2D g = ui.createGraphics(); page.component().printAll(g); g.dispose(); });
        return ui;
    }

    private static void click(Component canvas, MapsAuthoringPage p, Cell cell, int count) {
        Point at = p.canvas().at(cell);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, at.x, at.y, count, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 2, 0, at.x, at.y, count, false, MouseEvent.BUTTON1));
    }

    private static void wheel(Component canvas, MapsAuthoringPage p, Cell cell, int notches) {
        Point at = p.canvas().at(cell);
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 1, 0, at.x, at.y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, notches));
    }

    private static void drag(Component canvas, MapsAuthoringPage p, Cell from, Cell to) {
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
