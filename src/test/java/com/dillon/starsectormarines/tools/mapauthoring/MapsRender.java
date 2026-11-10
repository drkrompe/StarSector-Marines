package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a story map's battle as it will play, through the battle renderer's owner-facing look preset
 * ({@link BattleReviewFrameRenderer#look}), framed whole; the patch's marks over it (pins, ruin strokes, placed
 * structures, and a colony's lift, bores and holdouts); and the all-worlds sheet.
 */
public final class MapsRender {

    public static final int WIDTH = 1920, HEIGHT = 1080;

    /** A rendered map and how its cells land on the picture. */
    public record Frame(BufferedImage image, float originX, float originY, float cellPx, int mapW, int mapH) {
        public float sx(float cellX) { return originX + cellX * cellPx; }
        public float sy(float cellY) { return originY - cellY * cellPx; }
        public float cellX(float px) { return (px - originX) / cellPx; }
        public float cellY(float py) { return (originY - py) / cellPx; }
    }

    private final Path modRoot;
    /** One renderer for every frame: each new one loads its own copy of the battle's textures. */
    private BattleReviewFrameRenderer renderer;
    private int rendererWidth, rendererHeight;

    public MapsRender(Path modRoot) { this.modRoot = modRoot; }

    /** The battle's whole map in the look preset, {@code width} by {@code height}. */
    public synchronized Frame whole(BattleSimulation sim, int width, int height) throws Exception {
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        float across = Math.max(w, h * width / (float) height) + 4f;
        if (renderer == null || rendererWidth != width || rendererHeight != height) {
            renderer = BattleReviewFrameRenderer.look(modRoot, width, height, w / 2f, h / 2f, across);
            rendererWidth = width;
            rendererHeight = height;
        } else {
            renderer.refocus(w / 2f, h / 2f, across);
        }
        BufferedImage image = renderer.render(sim, "");
        BattleCamera camera = renderer.cameraOf(sim);
        float px = camera.cellPxSize();
        float ox = camera.cellToScreenX(0f), oy = height - camera.cellToScreenY(0f);
        // The battle is re-dressed between frames; never draw it from a cache.
        renderer.forget(sim);
        return new Frame(image, ox, oy, px, w, h);
    }

    // ------------------------------------------------------------------ marks

    static final Color PLACE = new Color(200, 210, 215), PIN = new Color(80, 220, 255), RUIN = new Color(255, 96, 70),
            PLACED = new Color(255, 120, 230), STRUCTURE = new Color(150, 235, 200), COLONY = new Color(255, 214, 64),
            BAD = new Color(255, 80, 70);

    /** The page's marks over {@code f}, in place; {@code selected} is an edit's id ({@link Edit#id}). */
    public static void mark(Graphics2D g, Frame f, StoryMapsSession.Stood stood, MapPatch patch, String selected) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        if (stood != null) {
            for (StoryMapsSession.LaidStructure s : stood.structures()) {
                if (patch.place().stream().anyMatch(p -> p.name().equals(s.name()))) continue;
                int x0 = Math.round(f.sx(s.x0())), y0 = Math.round(f.sy(s.y1() + 1));
                int x1 = Math.round(f.sx(s.x1() + 1)), y1 = Math.round(f.sy(s.y0()));
                g.setColor(STRUCTURE);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
                g.drawRect(x0, y0, x1 - x0, y1 - y0);
                tag(g, x0 + 2, y0 - 4, s.name(), STRUCTURE);
            }
            for (StoryMapsSession.Place p : stood.places()) {
                if (patch.pins().stream().anyMatch(pin -> pin.place().equals(p.name()))) continue;
                dot(g, f, p.x(), p.y(), PLACE, false, 5, p.name());
            }
        }
        for (MapPatch.Pin pin : patch.pins()) {
            dot(g, f, pin.x(), pin.y(), PIN, Edit.pin(pin).id().equals(selected), 8, "pin · " + pin.place());
        }
        for (int k = 0; k < patch.ruin().size(); k++) {
            DamagePlan.Stroke s = patch.ruin().get(k);
            boolean on = Edit.ruin(k, s).id().equals(selected);
            Path2D.Float line = new Path2D.Float();
            for (int i = 0; i < s.points().size(); i++) {
                float x = f.sx((float) s.points().get(i).x() + .5f), y = f.sy((float) s.points().get(i).y() + .5f);
                if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
            }
            g.setColor(new Color(RUIN.getRed(), RUIN.getGreen(), RUIN.getBlue(), on ? 130 : 80));
            g.setStroke(new BasicStroke(Math.max(4f, (float) s.radius() * 2f * f.cellPx()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
            if (s.points().size() == 1) {
                float r = Math.max(2f, (float) s.radius() * f.cellPx());
                float x = f.sx((float) s.points().get(0).x() + .5f), y = f.sy((float) s.points().get(0).y() + .5f);
                g.fillOval(Math.round(x - r), Math.round(y - r), Math.round(2 * r), Math.round(2 * r));
            }
            DamagePlan.Point first = s.points().get(0);
            tag(g, Math.round(f.sx((float) first.x() + .5f)) + 8, Math.round(f.sy((float) first.y() + .5f)) - 8,
                    "ruin " + (k + 1) + " · " + s.mode().name().toLowerCase(Locale.ROOT) + " · r " + s.radius(), RUIN);
        }
        for (Structure s : patch.place()) {
            boolean on = Edit.place(s).id().equals(selected);
            int x = Math.round(f.sx(s.x())), y = Math.round(f.sy(s.y() + 1));
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(x - 6, y - 6, 12, 12);
            g.setColor(PLACED);
            g.setStroke(new BasicStroke(on ? 3f : 2f));
            g.drawRect(x - 5, y - 5, 10, 10);
            tag(g, x + 10, y + 5, "place · " + s.name() + " · " + s.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + (s.turns() == 0 ? "" : " · " + s.turns() * 90 + "°"), PLACED);
        }
        if (patch.lift() != null) dot(g, f, patch.lift().x(), patch.lift().y(), COLONY, false, 7, "lift");
        for (MapPatch.Bore b : patch.bores()) dot(g, f, b.x(), b.y(), COLONY, false, b.mother() ? 8 : 5, b.mother() ? "mother bore" : "bore");
        for (int k = 0; k < patch.holdouts().size(); k++) {
            dot(g, f, patch.holdouts().get(k).x(), patch.holdouts().get(k).y(), COLONY, false, 6, "holdout " + (k + 1));
        }
    }

    /** One line the page lists and the marks highlight: a pin, a ruin stroke or a placed structure. */
    public record Edit(String id, String text) {
        @Override public String toString() { return text; }
        static Edit pin(MapPatch.Pin p) { return new Edit("pin:" + p.place(), "pin · " + p.place() + " at " + p.x() + "," + p.y()); }
        static Edit ruin(int k, DamagePlan.Stroke s) {
            return new Edit("ruin:" + k, "ruin " + (k + 1) + " · " + s.mode().name().toLowerCase(Locale.ROOT) + ", radius "
                    + s.radius() + ", " + s.points().size() + " point(s)");
        }
        static Edit place(Structure s) {
            return new Edit("place:" + s.name(), "place · " + s.name() + " · " + s.kind() + " at " + s.x() + "," + s.y()
                    + (s.turns() == 0 ? "" : ", " + s.turns() * 90 + "°"));
        }

        /** Every edit of {@code patch}, in the order the page lists them. */
        static List<Edit> of(MapPatch patch) {
            List<Edit> out = new ArrayList<>();
            for (MapPatch.Pin p : patch.pins()) out.add(pin(p));
            for (int k = 0; k < patch.ruin().size(); k++) out.add(ruin(k, patch.ruin().get(k)));
            for (Structure s : patch.place()) out.add(place(s));
            return out;
        }
    }

    /** The check results in a panel at the bottom left, green when every check passed. */
    public static void problems(Graphics2D g, int height, List<String> problems, String passed) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        List<String> lines = problems.isEmpty() ? List.of(passed) : problems;
        int shown = Math.min(lines.size(), 8), lh = 22, y0 = height - 18 - shown * lh;
        g.setColor(new Color(10, 12, 12, 200));
        g.fillRoundRect(12, y0 - 20, 1300, shown * lh + 18, 10, 10);
        for (int i = 0; i < shown; i++) {
            g.setColor(problems.isEmpty() ? new Color(120, 235, 140) : BAD);
            g.drawString((problems.isEmpty() ? "" : "✗ ") + lines.get(i), 24, y0 + i * lh);
        }
    }

    private static void dot(Graphics2D g, Frame f, int cx, int cy, Color c, boolean selected, int radius, String label) {
        int x = Math.round(f.sx(cx + .5f)), y = Math.round(f.sy(cy + .5f)), r = selected ? radius + 3 : radius;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillOval(x - r - 2, y - r - 2, 2 * r + 4, 2 * r + 4);
        g.setColor(c);
        g.fillOval(x - r, y - r, 2 * r, 2 * r);
        tag(g, x + r + 4, y + 5, label, c);
    }

    static void tag(Graphics2D g, int x, int y, String text, Color c) {
        FontMetrics m = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRoundRect(x - 4, y - m.getAscent() - 1, m.stringWidth(text) + 8, m.getHeight() + 2, 6, 6);
        g.setColor(c);
        g.drawString(text, x, y);
    }

    // ------------------------------------------------------------------ contact sheet

    /** One tile of the all-worlds sheet: a world's whole-map picture and two lines on what it chose. */
    public record Tile(MapWorld world, BufferedImage image, String line1, String line2) { }

    /** Every world's picture side by side, five across and two down, 1920 by 1080, each labelled. */
    public static BufferedImage contactSheet(String title, List<Tile> tiles) {
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(22, 24, 24));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.setColor(new Color(232, 225, 203));
        g.drawString(title, 16, 32);
        int cols = 5, top = 46, tw = WIDTH / cols, th = (HEIGHT - top) / 2, text = 50;
        for (int k = 0; k < tiles.size() && k < 10; k++) {
            Tile t = tiles.get(k);
            int x = (k % cols) * tw, y = top + (k / cols) * th;
            int iw = tw - 8, ih = Math.round(iw * t.image().getHeight() / (float) t.image().getWidth());
            if (ih > th - text - 8) { ih = th - text - 8; iw = Math.round(ih * t.image().getWidth() / (float) t.image().getHeight()); }
            int iy = y + (th - text - ih) / 2;
            g.drawImage(t.image(), x + (tw - iw) / 2, iy, iw, ih, null);
            int ty = iy + ih + 20;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.setColor(new Color(240, 232, 208));
            g.drawString(t.world().label() + " · " + t.world().planetType(), x + 10, ty);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.setColor(new Color(190, 200, 205));
            g.drawString(t.line1(), x + 10, ty + 19);
            if (t.line2() != null) g.drawString(t.line2(), x + 10, ty + 36);
        }
        g.dispose();
        return out;
    }
}
