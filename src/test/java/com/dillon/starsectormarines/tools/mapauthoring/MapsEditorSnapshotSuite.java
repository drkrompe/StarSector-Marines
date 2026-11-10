package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
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
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Maps page driven as an author drives it, at 1920x1080 / 100%: Heart Front's owned seed opened from its pool, a
 * place pinned and a ruin stroke dragged; undo and redo proved; a placement the layout cannot honour shown refused,
 * its save refused, and undone; the patch saved into a temporary folder and read back equal; then the page captured
 * as three worlds and the all-worlds sheet drawn. Nothing is written under mod/.
 */
public final class MapsEditorSnapshotSuite implements SnapshotSuite {

    private static final String MAP = "heart-front";

    @Override public String id() { return "maps-editor"; }
    @Override public String label() { return "Maps page: a story map's patch edited by its gestures, and its all-worlds sheet"; }
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
            on(() -> page[0].selectMap(MAP));
            await(page[0]);
            on(() -> {
                MapsAuthoringPage p = page[0];
                p.selectTool(MapsAuthoringPage.Tool.PIN);
                p.setPin("workings");
                click(p, 360, 290);
                p.selectTool(MapsAuthoringPage.Tool.RUIN);
                p.setRuin(3, DamagePlan.Mode.BATTERED);
                drag(p, new int[][]{{120, 104}, {126, 106}, {132, 108}, {139, 109}, {145, 110}});
                MapPatch edited = p.patch();
                if (edited.pins().size() != 1 || edited.ruin().size() != 1) throw new IllegalStateException("Gestures did not make the edits: " + edited);
                p.undo();
                if (!p.patch().ruin().isEmpty()) throw new IllegalStateException("Undo did not remove the stroke");
                p.redo();
                if (!p.patch().equals(edited)) throw new IllegalStateException("Redo did not restore the stroke");
            });
            await(page[0]);
            check(page[0], true);
            MapPatch good = page[0].patch();
            on(() -> {
                MapsAuthoringPage p = page[0];
                p.selectTool(MapsAuthoringPage.Tool.PLACE);
                p.setStructure("ammo-dump-1", StructureKind.AMMO_DUMP, 0);
                click(p, 205, 44);
                p.selectTool(MapsAuthoringPage.Tool.SELECT);
            });
            await(page[0]);
            check(page[0], false);
            out.add(new SnapshotArtifact("maps-editor-refused.png", capture(page[0])));
            Path pool = StoryMapsSession.poolFile(temp, page[0].opened().map());
            on(() -> page[0].save());
            await(page[0]);
            if (Files.exists(pool)) throw new IllegalStateException("A refused entry was saved: " + page[0].status());
            System.out.println("[maps-editor] refused save: " + page[0].status());
            on(() -> page[0].undo());
            await(page[0]);
            if (!page[0].patch().equals(good)) throw new IllegalStateException("Undo did not take the placement back");
            check(page[0], true);
            on(() -> page[0].selectEdit("ruin:0"));
            out.add(new SnapshotArtifact("maps-editor-arid.png", capture(page[0])));
            on(() -> page[0].save());
            await(page[0]);
            System.out.println("[maps-editor] save: " + page[0].status());
            StoryMapPool.Entry back = Files.exists(pool) ? StoryMapPool.read(page[0].opened().map(), new JSONObject(Files.readString(pool)))
                    .entries(StoryMapsSession.HOME).get(page[0].opened().index()) : null;
            if (back == null || !back.equals(page[0].entry())) {
                throw new IllegalStateException("The saved pool did not read back as the page holds it: " + page[0].status());
            }
            for (MapWorld world : new MapWorld[]{MapWorld.FROZEN, MapWorld.TOXIC}) {
                on(() -> page[0].selectWorld(world));
                await(page[0]);
                out.add(new SnapshotArtifact("maps-editor-" + world.name().toLowerCase() + ".png", capture(page[0])));
            }
            StoryMapsSession.Opened opened = page[0].opened();
            StoryMapPool.Entry entry = page[0].entry();
            on(() -> page[0].close());
            page[0] = null;
            try (StoryMapsSession session = new StoryMapsSession(c.projectRoot())) {
                out.add(new SnapshotArtifact("maps-all-worlds.png", session.contactSheet(opened.map(), entry)));
            }
        } finally {
            if (page[0] != null) on(() -> page[0].close());
            try (Stream<Path> walk = Files.walk(temp)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        return out;
    }

    private static void check(MapsAuthoringPage page, boolean passes) {
        List<String> problems = page.problems();
        System.out.println("[maps-editor] checks: " + (problems.isEmpty() ? "pass" : problems));
        if (problems.isEmpty() != passes) {
            throw new IllegalStateException((passes ? "The checks should pass: " : "The checks should fail: ") + problems);
        }
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

    private static void click(MapsAuthoringPage p, int x, int y) {
        Component canvas = p.canvas();
        Point at = p.canvas().at(x, y);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, at.x, at.y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 2, 0, at.x, at.y, 1, false, MouseEvent.BUTTON1));
    }

    private static void drag(MapsAuthoringPage p, int[][] cells) {
        Component canvas = p.canvas();
        Point a = p.canvas().at(cells[0][0], cells[0][1]);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, a.x, a.y, 1, false, MouseEvent.BUTTON1));
        for (int k = 1; k < cells.length; k++) {
            Point b = p.canvas().at(cells[k][0], cells[k][1]);
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 2 + k, 0, b.x, b.y, 1, false, MouseEvent.NOBUTTON));
        }
        Point z = p.canvas().at(cells[cells.length - 1][0], cells[cells.length - 1][1]);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 99, 0, z.x, z.y, 1, false, MouseEvent.BUTTON1));
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested);
    }
}
