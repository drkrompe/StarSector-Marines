package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** One annotated review card over a wide look frame: numbered steps, the power path, a panel and an inset. */
final class ReviewCard {
    private static final int W = 1920, H = 1080;
    private static final Color POWER = new Color(255, 196, 64);
    private static final Color STEP = new Color(70, 170, 255);
    private static final Color PAIR = new Color(120, 230, 140);

    private final BufferedImage image;
    private final Graphics2D g;
    private final BattleCamera camera;
    private final List<Runnable> badges = new ArrayList<>();

    ReviewCard(BufferedImage base, BattleCamera camera, BufferedImage inset, String title) {
        this.image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        this.camera = camera;
        g = image.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        label(24, 24, title, 30, new Color(0, 0, 0, 185));
        int ix = 24, iy = 92;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(ix - 6, iy - 6, inset.getWidth() + 12, inset.getHeight() + 40);
        g.drawImage(inset, ix, iy, null);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.setColor(Color.WHITE);
        g.drawString("The vault door at play zoom", ix + 6, iy + inset.getHeight() + 26);
    }

    int sx(float[] p) { return Math.round(camera.cellToScreenX(p[0])); }
    int sy(float[] p) { return Math.round(H - camera.cellToScreenY(p[1])); }

    /** The power's path: hall to junction to door, a dashed cable. */
    void power(float[]... path) {
        g.setColor(POWER);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[]{16f, 12f}, 0f));
        for (int i = 1; i < path.length; i++) g.drawLine(sx(path[i - 1]), sy(path[i - 1]), sx(path[i]), sy(path[i]));
    }

    /** Two places held at the same moment. */
    void pair(float[] p, float[] q) {
        g.setColor(PAIR);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[]{4f, 10f}, 0f));
        g.drawLine(sx(p), sy(p), sx(q), sy(q));
        int mx = (sx(p) + sx(q)) / 2, my = (sy(p) + sy(q)) / 2;
        badges.add(() -> label(mx - 110, my - 18, "AT THE SAME TIME", 20, new Color(20, 90, 40, 220)));
    }

    /** A numbered step at a place, drawn above the lines. */
    void step(float[] at, int n, String what, String how) {
        int x = sx(at), y = sy(at);
        badges.add(() -> {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillOval(x - 21, y - 21, 42, 42);
            g.setColor(STEP);
            g.setStroke(new BasicStroke(4f));
            g.drawOval(x - 19, y - 19, 38, 38);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            FontMetrics m = g.getFontMetrics();
            g.setColor(Color.WHITE);
            String s = Integer.toString(n);
            g.drawString(s, x - m.stringWidth(s) / 2, y + 9);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            int wide = g.getFontMetrics().stringWidth(what) + 20;
            int lx = x + 28 + wide > W - 8 ? x - 28 - wide : x + 28;
            label(lx, y - 30, what, 22, new Color(0, 0, 0, 190));
            label(lx, y + 6, how, 18, new Color(20, 40, 70, 200));
        });
    }

    /** What the door's prompt tells the player, in a panel along the bottom: a heading, then lines wrapped to fit. */
    void panel(String head, String... lines) {
        badges.add(() -> {
            int x = 24, w = 1180, pad = 18;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            FontMetrics m = g.getFontMetrics();
            List<String> wrapped = new ArrayList<>();
            for (String line : lines) wrapped.addAll(wrap(line, m, w - 2 * pad));
            int h = 72 + wrapped.size() * 26 + 10, y = H - h - 24;
            g.setColor(new Color(10, 14, 20, 215));
            g.fillRoundRect(x, y, w, h, 16, 16);
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, w, h, 16, 16);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.setColor(new Color(150, 200, 255));
            g.drawString("What the player sees at the door", x + pad, y + 32);
            g.setColor(Color.WHITE);
            g.drawString(head, x + pad, y + 62);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            g.setColor(new Color(225, 225, 225));
            for (int i = 0; i < wrapped.size(); i++) g.drawString(wrapped.get(i), x + pad, y + 90 + i * 26);
        });
    }

    private static List<String> wrap(String text, FontMetrics m, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (m.stringWidth(next) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private void label(int x, int y, String text, int size, Color back) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics m = g.getFontMetrics();
        g.setColor(back);
        g.fillRoundRect(x, y, m.stringWidth(text) + 20, size + 14, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 10, y + size + 3);
    }

    BufferedImage done() {
        for (Runnable badge : badges) badge.run();
        g.dispose();
        return image;
    }
}
