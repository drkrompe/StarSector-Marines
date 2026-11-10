package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.BiomeSkin;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.model.Doodad;
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
import java.util.List;
import java.util.Locale;

/**
 * Draws a built owned map as it will play, through the battle renderer's owner-facing look preset
 * ({@link BattleReviewFrameRenderer#look}), framed whole; and the marks the page draws over it, which say what the
 * picture cannot: where a drop's path runs and how deep it is on this world, which crossing is which, what a
 * settlement is for and how it adapted here.
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

    /** The whole map in the look preset, {@code width} by {@code height}, as the owned-map look suite draws it. */
    public synchronized Frame whole(MapResult map, SurfacePalette surface, int width, int height) throws Exception {
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        float across = Math.max(w, h * width / (float) height) + 4f;
        if (renderer == null || rendererWidth != width || rendererHeight != height) {
            renderer = BattleReviewFrameRenderer.look(modRoot, width, height, w / 2f, h / 2f, across);
            rendererWidth = width;
            rendererHeight = height;
        } else {
            renderer.refocus(w / 2f, h / 2f, across);
        }
        try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
            for (Doodad d : map.doodads) sim.addDoodad(d);
            sim.setSurface(surface);
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

    static final Color DROP = new Color(120, 200, 255), RISE = new Color(225, 170, 90),
            CROSSING = new Color(120, 255, 160), SITE = new Color(255, 214, 64), ORIGIN = new Color(80, 220, 255),
            OBJECTIVE = new Color(255, 110, 90), TOWN = new Color(255, 230, 150), BAD = new Color(255, 80, 70);

    /** The page's marks over {@code f}, in place: {@code built} says how deep and how adapted on its world. */
    public static void mark(Graphics2D g, Frame f, MapDocument doc, MapsSession.Built built, String selected) {
        OwnedMap map = doc.map();
        BiomeSkin skin = built.world().skin();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (OwnedMap.Rise r : map.rises()) {
            OwnedMap.Box b = r.box();
            OwnedMap.DropRole role = map.drops().isEmpty() ? OwnedMap.DropRole.RIFT : map.drops().get(0).role();
            box(g, f, b, RISE, r.name().equals(selected), new float[]{5f, 4f});
            tag(g, Math.round(f.sx(b.x0())) + 4, Math.round(f.sy(b.y1() + 1)) + 16,
                    String.format(Locale.ROOT, "%s · bank +%.0f m", r.name(), skin.floorMeters(role, 1f) * r.share()), RISE);
        }
        for (OwnedMap.Drop d : map.drops()) {
            path(g, f, d.path(), DROP, d.name().equals(selected));
            for (int k = 0; k < d.path().size(); k++) {
                float share = MapDocument.depthAt(d, k);
                if (share < 0f) continue;
                tag(g, Math.round(f.sx(d.path().get(k).x() + .5f)) + 7, Math.round(f.sy(d.path().get(k).y() + .5f)) - 7,
                        String.format(Locale.ROOT, "−%.0f m", skin.floorMeters(d.role(), share)), DROP);
            }
            Cell first = d.path().get(0);
            tag(g, Math.round(f.sx(first.x() + .5f)) + 7, Math.round(f.sy(first.y() + .5f)) + 20,
                    d.name() + " · " + d.role().name().toLowerCase(Locale.ROOT) + " · " + built.world().dropStyle(d.role()), DROP);
            for (Cell c : d.crossings()) {
                float[] n = MapChecks.across(d.path(), c.x() + .5f, c.y() + .5f);
                float reach = d.width() / 2f + 1.5f, cx = c.x() + .5f, cy = c.y() + .5f;
                g.setColor(new Color(CROSSING.getRed(), CROSSING.getGreen(), CROSSING.getBlue(), 140));
                g.setStroke(new BasicStroke(Math.max(3f, d.crossingWidth() * f.cellPx() * .6f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.drawLine(Math.round(f.sx(cx - n[0] * reach)), Math.round(f.sy(cy - n[1] * reach)),
                        Math.round(f.sx(cx + n[0] * reach)), Math.round(f.sy(cy + n[1] * reach)));
                tag(g, Math.round(f.sx(cx + n[0] * reach)) + 4, Math.round(f.sy(cy + n[1] * reach)), "crossing", CROSSING);
            }
        }
        if (doc.roads() != null) {
            for (RoadPlan.Route r : doc.roads().routes()) {
                Path2D.Float line = new Path2D.Float();
                for (int k = 0; k < r.points().size(); k++) {
                    float x = f.sx((float) r.points().get(k).x() + .5f), y = f.sy((float) r.points().get(k).y() + .5f);
                    if (k == 0) line.moveTo(x, y); else line.lineTo(x, y);
                }
                boolean on = r.id().equals(selected);
                g.setColor(new Color(255, 255, 255, on ? 230 : 70));
                g.setStroke(new BasicStroke(on ? 3f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 6f}, 0f));
                g.draw(line);
            }
        }
        for (OwnedMap.Settlement s : map.settlements()) {
            box(g, f, s.lot(), TOWN, s.name().equals(selected), new float[]{12f, 7f});
            for (Cell gate : s.gates()) {
                g.setColor(TOWN);
                int x = Math.round(f.sx(gate.x() + .5f)), y = Math.round(f.sy(gate.y() + .5f));
                g.fillRect(x - 4, y - 4, 8, 8);
            }
            BiomeSkin.Adaptation a = built.adaptations().get(s.name());
            String text = s.name() + " · " + s.program() + " · " + s.role().name().toLowerCase(Locale.ROOT)
                    + (s.terraceInto() == null ? "" : " · terraces into " + s.terraceInto())
                    + (a == null ? "" : " → " + MapWorld.words(a));
            tag(g, Math.round(f.sx(s.lot().x0())) + 4, Math.round(f.sy(s.lot().y1() + 1)) + 18, text, TOWN);
        }
        for (OwnedMap.Site s : map.sites()) {
            Cell at = s.at() != null ? s.at() : buildingCell(map, s.building());
            if (at == null) continue;
            dot(g, f, at, SITE, s.name().equals(selected), s.name() + (s.building() == null ? "" : " (" + s.building() + ")"));
        }
        dot(g, f, map.origin(), ORIGIN, "origin".equals(selected), "Origin · landing");
        dot(g, f, map.objective(), OBJECTIVE, false, "Objective");
    }

    private static Cell buildingCell(OwnedMap map, String name) {
        OwnedMap.Building b = name == null ? null : map.building(name);
        if (b == null) return null;
        if (b.at() != null) return b.at();
        return b.box() == null ? null : new Cell((b.box().x0() + b.box().x1()) / 2, (b.box().y0() + b.box().y1()) / 2);
    }

    /** The check results in a panel at the bottom left, green when every check passed. */
    public static void problems(Graphics2D g, int height, List<String> problems) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        List<String> lines = problems.isEmpty() ? List.of("Every check passes: every place reachable from the origin, every crossing crosses, nothing built on a way.") : problems;
        int shown = Math.min(lines.size(), 8), lh = 22, y0 = height - 18 - shown * lh;
        g.setColor(new Color(10, 12, 12, 200));
        g.fillRoundRect(12, y0 - 20, 1100, shown * lh + 18, 10, 10);
        for (int i = 0; i < shown; i++) {
            g.setColor(problems.isEmpty() ? new Color(120, 235, 140) : BAD);
            g.drawString((problems.isEmpty() ? "" : "✗ ") + lines.get(i), 24, y0 + i * lh);
        }
    }

    private static void box(Graphics2D g, Frame f, OwnedMap.Box b, Color c, boolean selected, float[] dash) {
        int x0 = Math.round(f.sx(b.x0())), y0 = Math.round(f.sy(b.y1() + 1));
        int x1 = Math.round(f.sx(b.x1() + 1)), y1 = Math.round(f.sy(b.y0()));
        g.setColor(c);
        g.setStroke(new BasicStroke(selected ? 4f : 2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g.drawRect(x0, y0, x1 - x0, y1 - y0);
    }

    private static void dot(Graphics2D g, Frame f, Cell at, Color c, boolean selected, String label) {
        int x = Math.round(f.sx(at.x() + .5f)), y = Math.round(f.sy(at.y() + .5f)), r = selected ? 10 : 7;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillOval(x - r - 2, y - r - 2, 2 * r + 4, 2 * r + 4);
        g.setColor(c);
        g.fillOval(x - r, y - r, 2 * r, 2 * r);
        tag(g, x + r + 4, y + 5, label, c);
    }

    private static void path(Graphics2D g, Frame f, List<Cell> path, Color c, boolean selected) {
        Path2D.Float line = new Path2D.Float();
        for (int k = 0; k < path.size(); k++) {
            float x = f.sx(path.get(k).x() + .5f), y = f.sy(path.get(k).y() + .5f);
            if (k == 0) line.moveTo(x, y); else line.lineTo(x, y);
        }
        g.setColor(new Color(0, 0, 0, 150));
        g.setStroke(new BasicStroke(selected ? 6f : 4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        g.setColor(c);
        g.setStroke(new BasicStroke(selected ? 3.5f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{10f, 6f}, 0f));
        g.draw(line);
        for (Cell p : path) {
            int x = Math.round(f.sx(p.x() + .5f)), y = Math.round(f.sy(p.y() + .5f));
            g.fillRect(x - 4, y - 4, 8, 8);
        }
    }

    static void tag(Graphics2D g, int x, int y, String text, Color c) {
        FontMetrics m = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRoundRect(x - 4, y - m.getAscent() - 1, m.stringWidth(text) + 8, m.getHeight() + 2, 6, 6);
        g.setColor(c);
        g.drawString(text, x, y);
    }

    // ------------------------------------------------------------------ contact sheet

    /** One tile of the all-worlds sheet: a world's whole-map picture and what it chose. */
    public record Tile(MapWorld world, BufferedImage image, String drop, String adaptation, int problems) { }

    /** Every world's picture side by side, five across and two down, 1920 by 1080, each labelled with its choices. */
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
            int ih = th - 62, iw = Math.round(ih * t.image().getWidth() / (float) t.image().getHeight());
            if (iw > tw - 8) { iw = tw - 8; ih = Math.round(iw * t.image().getHeight() / (float) t.image().getWidth()); }
            int iy = y + (th - 62 - ih) / 2 + 4;
            g.drawImage(t.image(), x + (tw - iw) / 2, iy, iw, ih, null);
            int ty = y + th - 52;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.setColor(new Color(240, 232, 208));
            g.drawString(t.world().label() + " · " + t.world().planetType(), x + 10, ty);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.setColor(new Color(190, 200, 205));
            g.drawString("drop: " + t.drop(), x + 10, ty + 20);
            g.drawString("settlements: " + t.adaptation(), x + 10, ty + 38);
            if (t.problems() > 0) {
                g.setColor(BAD);
                g.drawString(t.problems() + " check(s) fail", x + tw - 130, ty);
            }
        }
        g.dispose();
        return out;
    }
}
