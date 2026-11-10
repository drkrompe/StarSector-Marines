package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Crossing;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Place;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Program;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Rise;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Road;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Role;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Settlement;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Draws an edited map as it will play, through the battle renderer's owner-facing look preset
 * ({@link BattleReviewFrameRenderer#look}), framed whole; and the marks the page draws over it, which say what the
 * picture cannot: where the paths run, which crossing is which, what a settlement's program is.
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

    /** The whole map in the look preset, {@code width} by {@code height}. */
    public Frame whole(MapResult map, int width, int height) throws Exception {
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        float across = Math.max(w, h * width / (float) height) + 4f;
        if (renderer == null || rendererWidth != width || rendererHeight != height) {
            renderer = BattleReviewFrameRenderer.look(modRoot, width, height, w / 2f, h / 2f, across);
            rendererWidth = width;
            rendererHeight = height;
        } else {
            renderer.refocus(w / 2f, h / 2f, across);
        }
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(map, 0)) {
            BattleSimulation sim = scene.simulation();
            sim.setMissionCompletionEnabled(false);
            BufferedImage image = renderer.render(sim, "");
            BattleCamera camera = renderer.cameraOf(sim);
            float px = camera.cellPxSize();
            float ox = camera.cellToScreenX(0f), oy = height - camera.cellToScreenY(0f);
            // Every frame is a fresh battle that is never drawn again.
            renderer.forget(sim);
            return new Frame(image, ox, oy, px, w, h);
        }
    }

    // ------------------------------------------------------------------ marks

    static final Color DROP = new Color(120, 200, 255), RISE = new Color(225, 170, 90), ROAD = new Color(240, 240, 240),
            CROSSING = new Color(120, 255, 160), SITE = new Color(255, 214, 64), LANDING = new Color(80, 220, 255),
            BAD = new Color(255, 80, 70);

    /** The page's marks over {@code frame}, in place. {@code selected} is drawn brighter; null for none. */
    public static void mark(Graphics2D g, Frame f, MapSketch sketch, BiomeSkin skin, String selected) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (Rise r : sketch.rises()) {
            path(g, f, r.path(), RISE, r.id().equals(selected));
            for (int k = 0; k < r.path().size(); k++) label(g, f, r.path().get(k), String.format(Locale.ROOT, "+%.0f m", r.heights().get(k)), RISE);
        }
        for (Drop d : sketch.drops()) {
            path(g, f, d.path(), DROP, d.id().equals(selected));
            for (int k = 0; k < d.path().size(); k++) label(g, f, d.path().get(k), String.format(Locale.ROOT, "−%.0f m", d.depths().get(k)), DROP);
            float length = SketchRaster.length(d.path());
            for (Crossing c : d.crossings()) {
                Pt at = SketchRaster.pointAt(d.path(), c.at() * length), n = SketchRaster.normalAt(d.path(), c.at() * length);
                float reach = d.width() / 2f + 1.5f;
                g.setColor(CROSSING);
                g.setStroke(new BasicStroke(Math.max(3f, c.width() * f.cellPx() * .6f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.setComposite(AlphaComposite.SrcOver.derive(.55f));
                g.drawLine(Math.round(f.sx(at.x() - n.x() * reach)), Math.round(f.sy(at.y() - n.y() * reach)),
                        Math.round(f.sx(at.x() + n.x() * reach)), Math.round(f.sy(at.y() + n.y() * reach)));
                g.setComposite(AlphaComposite.SrcOver);
                label(g, f, new Pt(at.x() + n.x() * reach, at.y() + n.y() * reach),
                        c.kind() == MapSketch.CrossingKind.BRIDGE ? "bridge" : "land bridge", CROSSING);
            }
        }
        for (Road r : sketch.roads()) path(g, f, r.path(), ROAD, r.id().equals(selected));
        for (Settlement s : sketch.settlements()) {
            int x0 = Math.round(f.sx(s.x())), y0 = Math.round(f.sy(s.y() + s.h()));
            int x1 = Math.round(f.sx(s.x() + s.w())), y1 = Math.round(f.sy(s.y()));
            Color c = s.program() == Program.COMPOUND ? new Color(255, 120, 90) : s.program() == Program.CREVASSE_TOWN ? new Color(170, 220, 255) : new Color(255, 230, 150);
            g.setColor(c);
            g.setStroke(new BasicStroke(s.id().equals(selected) ? 4f : 2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{12f, 7f}, 0f));
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
            String text = program(s.program()) + (s.role().isBlank() ? "" : " · " + s.role()) + " → " + skin.adaptation(s.program());
            tag(g, x0 + 4, y0 + 18, text, c);
        }
        for (Place p : sketch.places()) {
            Color c = p.role() == Role.LANDING ? LANDING : p.role() == Role.SITE ? SITE : Color.WHITE;
            int x = Math.round(f.sx(p.x() + .5f)), y = Math.round(f.sy(p.y() + .5f)), r = p.id().equals(selected) ? 10 : 7;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillOval(x - r - 2, y - r - 2, 2 * r + 4, 2 * r + 4);
            g.setColor(c);
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            tag(g, x + r + 4, y + 5, p.label(), c);
        }
    }

    /** The check results in a panel at the bottom left, green when every check passed. */
    public static void problems(Graphics2D g, int height, List<String> problems) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        List<String> lines = problems.isEmpty() ? List.of("All checks pass: every place reachable, every crossing crosses, nothing built on a way.") : problems;
        int shown = Math.min(lines.size(), 8), lh = 22, y0 = height - 18 - shown * lh;
        g.setColor(new Color(10, 12, 12, 200));
        g.fillRoundRect(12, y0 - 20, 980, shown * lh + 18, 10, 10);
        for (int i = 0; i < shown; i++) {
            g.setColor(problems.isEmpty() ? new Color(120, 235, 140) : BAD);
            g.drawString((problems.isEmpty() ? "" : "✗ ") + lines.get(i), 24, y0 + i * lh);
        }
    }

    static String program(Program p) {
        return switch (p) { case TOWN -> "Town"; case COMPOUND -> "Compound"; case CREVASSE_TOWN -> "Crevasse town"; };
    }

    private static void path(Graphics2D g, Frame f, List<Pt> path, Color c, boolean selected) {
        Path2D.Float line = new Path2D.Float();
        for (int k = 0; k < path.size(); k++) {
            float x = f.sx(path.get(k).x()), y = f.sy(path.get(k).y());
            if (k == 0) line.moveTo(x, y); else line.lineTo(x, y);
        }
        g.setColor(new Color(0, 0, 0, 150));
        g.setStroke(new BasicStroke(selected ? 6f : 4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        g.setColor(c);
        g.setStroke(new BasicStroke(selected ? 3.5f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{10f, 6f}, 0f));
        g.draw(line);
        for (Pt p : path) {
            int x = Math.round(f.sx(p.x())), y = Math.round(f.sy(p.y()));
            g.fillRect(x - 4, y - 4, 8, 8);
        }
    }

    private static void label(Graphics2D g, Frame f, Pt at, String text, Color c) {
        tag(g, Math.round(f.sx(at.x())) + 7, Math.round(f.sy(at.y())) - 7, text, c);
    }

    static void tag(Graphics2D g, int x, int y, String text, Color c) {
        FontMetrics m = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRoundRect(x - 4, y - m.getAscent() - 1, m.stringWidth(text) + 8, m.getHeight() + 2, 6, 6);
        g.setColor(c);
        g.drawString(text, x, y);
    }

    // ------------------------------------------------------------------ contact sheet

    /** One tile of the all-biomes sheet: a skin's whole-map picture and what it chose. */
    public record Tile(BiomeSkin skin, BufferedImage image, String drop, String adaptation, int problems) { }

    /** Every skin's picture side by side, five across and two down, 1920 by 1080, each labelled with its choices. */
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
        int cols = 5, top = 46, tw = WIDTH / cols, th = (HEIGHT - top) / 2;
        for (int k = 0; k < tiles.size() && k < 10; k++) {
            Tile t = tiles.get(k);
            int x = (k % cols) * tw, y = top + (k / cols) * th;
            int ih = th - 58, iw = Math.round(ih * t.image().getWidth() / (float) t.image().getHeight());
            if (iw > tw - 8) { iw = tw - 8; ih = Math.round(iw * t.image().getHeight() / (float) t.image().getWidth()); }
            g.drawImage(t.image(), x + (tw - iw) / 2, y + 4, iw, ih, null);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.setColor(new Color(240, 232, 208));
            g.drawString(t.skin().label() + " · " + t.skin().planetType(), x + 10, y + ih + 24);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.setColor(new Color(190, 200, 205));
            g.drawString("drop: " + t.drop() + " · towns: " + t.adaptation(), x + 10, y + ih + 44);
            if (t.problems() > 0) {
                g.setColor(BAD);
                g.drawString(t.problems() + " check(s) fail", x + tw - 130, y + ih + 24);
            }
        }
        g.dispose();
        return out;
    }
}
