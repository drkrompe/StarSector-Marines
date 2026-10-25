package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy.Lobe;
import com.dillon.starsectormarines.battle.world.gen.forest.Undergrowth;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Overhead undergrowth on three 1536x1024 sheets, one per state, six species at 512 px pitch in {@link Undergrowth}
 * order. Each species fills a centered footprint box 480 px on its longest side; the box is the cut, so the exported
 * art maps one to one onto the closed cells. Standing growth fills the lobes; damaged growth is thinned inside them;
 * trampled growth is a sparse scatter of pressed clumps held in from the edge, with no grain, so a quarter-turned
 * patch does not look stretched.
 */
public final class UndergrowthReskinTemplate {
    public static final int PITCH = 512, FRAME = 480;
    public static final String[] STATES = {"standing", "damaged", "trampled"};
    private static final String[] SHEETS = {"forest-undergrowth", "forest-undergrowth-damaged", "forest-undergrowth-destroyed"};
    private static final String[] SUFFIXES = {"", "-damaged", "-destroyed"};
    private UndergrowthReskinTemplate() {}

    public static double scale(Undergrowth u) { return FRAME / (double) Math.max(u.width, u.height); }

    /** The footprint box of a species on its sheet: {x, y, w, h} in pixels. */
    public static int[] rect(Undergrowth u) {
        double s = scale(u);
        int w = (int) Math.round(u.width * s), h = (int) Math.round(u.height * s);
        int slot = u.ordinal();
        return new int[]{slot % 3 * PITCH + (PITCH - w) / 2, slot / 3 * PITCH + (PITCH - h) / 2, w, h};
    }

    public static BufferedImage render(int state) {
        BufferedImage image = new BufferedImage(PITCH * 3, PITCH * 2, BufferedImage.TYPE_INT_ARGB);
        for (Undergrowth u : Undergrowth.values()) {
            int[] r = rect(u);
            double s = scale(u);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(r[0], r[1]);
            g.scale(s, s);
            // Runtime cell rows run upward; the sheet's rows run downward.
            Area body = new Area();
            for (Lobe l : u.lobes()) body.add(new Area(ellipse(u, l, 1)));
            Random random = new Random(u.ordinal() * 31L + state);
            Color base = new Color(u.color);
            // Trampled clusters stay inside by placement, so no lobe edge cuts a straight arc through them.
            if (state == 2) trampled(g, u, body, random);
            else { g.setClip(body); standing(g, u, base, random, state == 1); }
            g.dispose();
        }
        return image;
    }

    private static Shape ellipse(Undergrowth u, Lobe l, double k) {
        return new Ellipse2D.Double(l.x() - l.rx() * k, u.height - l.y() - l.ry() * k, l.rx() * 2 * k, l.ry() * 2 * k);
    }

    private static void standing(Graphics2D g, Undergrowth u, Color base, Random random, boolean damaged) {
        boolean sparse = u.seeThrough;
        if (!sparse && !damaged) {
            g.setColor(base.darker());
            for (Lobe l : u.lobes()) g.fill(ellipse(u, l, 1));
        }
        int clumps = (int) (u.width * u.height * (sparse ? 9 : 14) * (damaged ? .55 : 1));
        if (u == Undergrowth.BRAMBLE || u == Undergrowth.BRAMBLE_SMALL) {
            // Arching canes: the gaps between them are where sight passes.
            g.setColor(new Color(0x5A2E22));
            g.setStroke(new BasicStroke(.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < u.width * 7 * (damaged ? .5 : 1); i++) {
                double x = random.nextDouble() * u.width, y = random.nextDouble() * u.height;
                double a = random.nextDouble() * Math.PI;
                g.draw(new Line2D.Double(x, y, x + Math.cos(a) * .9, y + Math.sin(a) * .6));
            }
        }
        if (u == Undergrowth.SAPLINGS) {
            // Three young crowns with ground between them.
            for (Lobe l : u.lobes()) {
                g.setColor(damaged ? base.darker() : base);
                g.fill(ellipse(u, l, damaged ? .6 : .82));
                g.setColor(new Color(0x6E5338));
                g.fill(new Ellipse2D.Double(l.x() - .07, u.height - l.y() - .07, .14, .14));
            }
        }
        for (int i = 0; i < clumps; i++) {
            double x = random.nextDouble() * u.width, y = random.nextDouble() * u.height;
            double r = u == Undergrowth.BRACKEN ? .22 : sparse ? .13 : .2;
            Color c = random.nextInt(3) == 0 ? base.brighter() : random.nextBoolean() ? base : base.darker();
            g.setColor(c);
            if (u == Undergrowth.BRACKEN) {
                double a = random.nextDouble() * Math.PI * 2;
                g.setStroke(new BasicStroke(.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(x, y, x + Math.cos(a) * r * 2.4, y + Math.sin(a) * r * 2.4));
            } else {
                g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            }
        }
        if (damaged) {
            g.setColor(new Color(0x8A6A48));
            g.setStroke(new BasicStroke(.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < u.width * 3; i++) {
                double x = random.nextDouble() * u.width, y = random.nextDouble() * u.height;
                g.draw(new Line2D.Double(x, y, x + .3, y + .18));
            }
        }
    }

    /**
     * Pressed flat: irregular leaf clusters scattered evenly over about a third of the footprint, each leaf at its own
     * angle, so no direction runs through the patch; the rim is left open, so the patch never reads as its box.
     */
    private static void trampled(Graphics2D g, Undergrowth u, Area body, Random random) {
        Color muted = new Color(0x4F5133);
        // A jittered grid of small leaf clusters, so the cover spreads evenly instead of pooling.
        for (double gy = .3; gy < u.height - .25; gy += .4) for (double gx = .3; gx < u.width - .25; gx += .4) {
            if (random.nextInt(5) == 0) continue;
            double x = gx + (random.nextDouble() - .5) * .2, y = gy + (random.nextDouble() - .5) * .2;
            // Every leaf lands within .3 of its centre: held that far inside the box, nothing is cut by the frame.
            if (!body.contains(x, y) || x < .3 || y < .3 || x > u.width - .3 || y > u.height - .3) continue;
            int leaves = 2 + random.nextInt(4);
            for (int leaf = 0; leaf < leaves; leaf++) {
                double lx = x + (random.nextDouble() - .5) * .24, ly = y + (random.nextDouble() - .5) * .24;
                double r = .05 + random.nextDouble() * .05, a = random.nextDouble() * Math.PI;
                g.setColor(random.nextBoolean() ? muted : muted.darker());
                g.fill(AffineTransform.getRotateInstance(a, lx, ly)
                        .createTransformedShape(new Ellipse2D.Double(lx - r * 1.6, ly - r * .8, r * 3.2, r * 1.6)));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (int state = 0; state < 3; state++) {
            ImageIO.write(render(state), "png",
                    root.resolve("art-source/templates/undergrowth-" + STATES[state] + ".png").toFile());
            Path doc = root.resolve("art-source/tilesets/" + SHEETS[state] + ".tileset-authoring.json");
            // Accepted source cuts may carry measured ImageGen registration. Never overwrite them.
            if (Files.exists(doc)) continue;
            JSONArray entries = new JSONArray();
            for (Undergrowth u : Undergrowth.values()) {
                int[] r = rect(u);
                JSONObject entry = new JSONObject().put("id", u.id + SUFFIXES[state]).put("included", true)
                        .put("cover", "none").put("ballisticHalfHeight", 0)
                        .put("footprintCells", new JSONArray().put(u.width).put(u.height))
                        .put("rect", new JSONArray().put(r[0]).put(r[1]).put(r[2]).put(r[3]));
                if (state < 2) {
                    JSONArray mask = new JSONArray();
                    for (int y = 0; y < u.height; y++) {
                        StringBuilder row = new StringBuilder();
                        for (int x = 0; x < u.width; x++) row.append(u.blocks(x, y, 0) ? '#' : '.');
                        mask.put(row.toString());
                    }
                    if (!u.seeThrough) entry.put("fogSurface", mask);
                }
                entries.put(entry);
            }
            Files.writeString(doc, new JSONObject().put("sheetName", SHEETS[state])
                    .put("sheet", "art-source/tilesets/" + SHEETS[state] + ".raw.png")
                    .put("cellPx", 64).put("gridCols", 3).put("gridRows", 2).put("idPrefix", "forest").put("alphaMin", 24)
                    .put("note", "UndergrowthReskinTemplate (" + STATES[state] + "): six species in Undergrowth order at"
                            + " 512 px pitch; each cut is the species' exact footprint box, 480 px on its longest side, so"
                            + " the art maps one to one onto the closed cells. Undergrowth lobes own collision, sight and"
                            + " the fog masks; cover is none because the natural-solid cells own obstruction. Preserve"
                            + " alpha; never trim or recenter. Prompt: " + SHEETS[state] + ".prompt.txt.")
                    .put("entries", entries).toString(2) + "\n");
        }
    }
}
