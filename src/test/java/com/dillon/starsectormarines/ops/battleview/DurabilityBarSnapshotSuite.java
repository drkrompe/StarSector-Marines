package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Deterministic evidence for the ownership-coded durability bar: every
 * {@link Allegiance} against every durability state, at true screen scale and
 * magnified far enough to audit the individual pixel rows.
 *
 * <p>This suite drains {@link DurabilityBarDecor} directly rather than a battle
 * frame. The bar is pure {@code SOLID_RECT} geometry in screen pixels, so a
 * Java2D replay of its draw list is the same picture the live drain paints —
 * without a simulation, an asset load, or an OpenGL context. Judging the design
 * is what this evidence is for; the geometry contract is pinned separately by
 * {@code DurabilityBarDecorTest}.
 */
public final class DurabilityBarSnapshotSuite implements SnapshotSuite {

    /** True-scale bar width, matching a unit rendered at a mid-zoom cell size. */
    private static final float BAR_WIDTH = 34f;
    /** Magnification for the audit column. Integer, so every bar pixel stays square. */
    private static final int ZOOM = 6;

    private static final Color BACKDROP = new Color(0x11, 0x16, 0x1D);
    private static final Color PANEL = new Color(0x18, 0x1F, 0x28);
    private static final Color RULE = new Color(0x2A, 0x35, 0x42);
    private static final Color TEXT = new Color(0xD9, 0xE8, 0xF1);
    private static final Color TEXT_DIM = new Color(0x8A, 0x9B, 0xAA);
    /** Stand-in body under each true-scale bar, so placement above a unit is visible. */
    private static final Color BODY = new Color(0x3A, 0x46, 0x54);

    /** One durability state to show. A negative armor fraction means "no armor pool". */
    private record State(String label, float hp, float armor) {

        boolean armored() {
            return armor >= 0f;
        }
    }

    private static final List<State> STATES = List.of(
            new State("full", 1f, 1f),
            new State("armor chipped", 1f, 0.45f),
            new State("armor broken", 0.62f, 0f),
            new State("unarmored", 0.75f, -1f),
            new State("critical", 0.08f, -1f));

    private static final int LABEL_W = 132;
    private static final int CELL_W = (int) (BAR_WIDTH * ZOOM) + 34;
    private static final int ROW_H = 128;
    /** Baseline for the magnified bar, low enough that the tallest bar clears the row's top edge. */
    private static final int ZOOM_BASELINE = 74;
    /** Baseline for the true-scale bar, leaving room for the stand-in body beneath it. */
    private static final int TRUE_BASELINE = 104;
    private static final int HEADER_H = 74;
    private static final int MARGIN = 22;

    @Override
    public String id() {
        return "durability-bars";
    }

    @Override
    public String label() {
        return "Durability bars";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) {
        return List.of(new SnapshotArtifact("durability-bar-matrix.png", matrix()));
    }

    private static BufferedImage matrix() {
        int width = MARGIN * 2 + LABEL_W + CELL_W * STATES.size();
        int height = MARGIN * 2 + HEADER_H + ROW_H * Allegiance.values().length;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BACKDROP);
        g.fillRect(0, 0, width, height);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(TEXT);
        g.drawString("Durability bars — ownership coding and armor state", MARGIN, MARGIN + 18);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(TEXT_DIM);
        g.drawString("armor band over structure band; left column is true screen scale, right is "
                + ZOOM + "x", MARGIN, MARGIN + 38);

        int headerY = MARGIN + HEADER_H - 8;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int column = 0; column < STATES.size(); column++) {
            g.setColor(TEXT_DIM);
            g.drawString(STATES.get(column).label(),
                    MARGIN + LABEL_W + column * CELL_W, headerY);
        }

        for (int row = 0; row < Allegiance.values().length; row++) {
            Allegiance owner = Allegiance.values()[row];
            int rowTop = MARGIN + HEADER_H + row * ROW_H;
            g.setColor(row % 2 == 0 ? PANEL : BACKDROP);
            g.fillRect(MARGIN, rowTop, width - MARGIN * 2, ROW_H);
            g.setColor(RULE);
            g.drawLine(MARGIN, rowTop, width - MARGIN, rowTop);

            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.drawString(owner.name(), MARGIN + 10, rowTop + 30);
            g.setColor(TEXT_DIM);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.drawString(owner.friendly() ? "friendly" : "not friendly", MARGIN + 10, rowTop + 48);

            for (int column = 0; column < STATES.size(); column++) {
                State state = STATES.get(column);
                int cellX = MARGIN + LABEL_W + column * CELL_W;
                paintTrueScale(g, owner, state, cellX + 6, rowTop + TRUE_BASELINE);
                paintZoomed(g, owner, state, cellX + 6, rowTop + ZOOM_BASELINE);
            }
        }

        g.dispose();
        return sheet;
    }

    /** The bar as it actually paints, sitting above a stand-in body. */
    private static void paintTrueScale(Graphics2D g, Allegiance owner, State state,
                                       int originX, int baselineY) {
        g.setColor(BODY);
        g.fillRect(originX + 8, baselineY + 2, (int) BAR_WIDTH - 16, 14);
        paintBar(g, owner, state, originX, baselineY, 1);
    }

    /** The same bar magnified, so each authored pixel row can be judged. */
    private static void paintZoomed(Graphics2D g, Allegiance owner, State state,
                                    int originX, int baselineY) {
        paintBar(g, owner, state, originX, baselineY, ZOOM);
    }

    /**
     * Replays the bar's own draw list. The render pipeline's screen space has +Y
     * up and the bar grows from {@code baseY}, so a row at bar-local {@code y} lands
     * {@code y * scale} pixels above the device baseline.
     */
    private static void paintBar(Graphics2D g, Allegiance owner, State state,
                                 int originX, int baselineY, int scale) {
        DrawList out = new DrawList();
        float cx = BAR_WIDTH / 2f;
        if (state.armored()) {
            DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, cx, 0f, BAR_WIDTH,
                    state.hp(), state.armor(), 1f);
        } else {
            DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, cx, 0f, BAR_WIDTH,
                    state.hp(), 1f);
        }

        Object antialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        DrawCommand[] buffer = out.buffer(RenderLayer.UNITS);
        for (int i = 0; i < out.count(RenderLayer.UNITS); i++) {
            DrawCommand command = buffer[i];
            int x0 = originX + Math.round(command.centerX() * scale);
            int x1 = originX + Math.round(command.width() * scale);
            int y0 = baselineY - Math.round(command.height() * scale);
            int y1 = baselineY - Math.round(command.centerY() * scale);
            g.setColor(new Color(command.red(), command.green(), command.blue(), command.alpha()));
            g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    }
}
