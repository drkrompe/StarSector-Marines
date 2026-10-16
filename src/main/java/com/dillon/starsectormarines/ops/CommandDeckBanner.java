package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.CompanyHqViewModel.NewsPost;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.Rect;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * The command deck drawn live: the painted plate cover-fitted to the banner, each
 * figure as its own sprite, and the overlays the painting leaves blank for — a radar
 * sweep with one contact per received report on the plotting table, and a faint
 * flicker on the console screens.
 *
 * <p>A station a player is pointing at or has focused lights its own people, which is
 * why the figures are separate sprites rather than painted into the plate. A report
 * arriving lights the comms watch the same way for a few seconds. Nothing here is the
 * only place a fact appears: the overlays dramatise the received-report projection the
 * news desk already shows in words.
 */
final class CommandDeckBanner implements CanvasProducer {
    private static final Color SWEEP = new Color(178, 245, 196);
    private static final Color WARM = new Color(255, 214, 150);
    private static final int MAX_CONTACTS = 14;

    private final CompanyHqNewsDesk desk;
    private final CommandDeckLayout layout;
    private final CompanyIntelMotion motion = new CompanyIntelMotion();
    private List<NewsPost> observedPosts;
    private NewsPost observedSelection;
    private String hotStation;

    CommandDeckBanner(CompanyHqNewsDesk desk, CommandDeckLayout layout) {
        this.desk = Objects.requireNonNull(desk, "news desk");
        this.layout = Objects.requireNonNull(layout, "deck layout");
    }

    CommandDeckLayout layout() { return layout; }

    CompanyIntelMotion motion() { return motion; }

    /** The station whose people are lit, or null. */
    void hot(String stationId) { hotStation = stationId; }

    void advance(float seconds) {
        List<NewsPost> posts = desk.posts();
        NewsPost selection = desk.selectedPost();
        if (observedPosts != posts || observedSelection != selection) {
            observedPosts = posts;
            observedSelection = selection;
            motion.observe(posts, selection);
        }
        motion.advance(seconds);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        if (width <= 0f || height <= 0f) return;
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(width, height, layout.width(), layout.height());
        context.sprite(layout.plate(), null, fit.x(layout.width() * 0.5f), fit.y(layout.height() * 0.5f),
                layout.width() * fit.scale(), layout.height() * fit.scale(), 0f, Color.WHITE);
        screens(context, fit);
        plot(context, fit);
        float arrival = Math.max(motion.arrivalStrength(CompanyIntelMotion.COMPANY),
                Math.max(motion.arrivalStrength(CompanyIntelMotion.CONFIRMED),
                        motion.arrivalStrength(CompanyIntelMotion.RUMOR)));
        CommandDeckLayout.Station hot = hotStation == null ? null : layout.station(hotStation);
        if (hot != null) outline(context, fit, hot, width, height);
        CommandDeckLayout.Station comms = layout.station("comms");
        for (CommandDeckLayout.Figure figure : layout.figures()) {
            float cx = fit.x(figure.x() + figure.w() * 0.5f);
            float cy = fit.y(figure.y() + figure.h() * 0.5f);
            float w = figure.w() * fit.scale();
            float h = figure.h() * fit.scale();
            context.sprite(figure.sprite(), null, cx, cy, w, h, 0f, Color.WHITE);
            float glow = hot != null && hot.shows(figure.id()) ? 0.6f : 0f;
            if (comms != null && comms.shows(figure.id())) glow = Math.max(glow, arrival * 0.5f);
            if (glow > 0.01f) {
                context.sprite(figure.sprite(), null, cx, cy, w, h, 0f,
                        new Color(WARM.getRed(), WARM.getGreen(), WARM.getBlue(), Math.round(255 * glow)),
                        CanvasSpriteRegion.FULL, CanvasBlend.ADDITIVE);
            }
        }
    }

    /** Corner brackets round the station being pointed at; the figures' glow does the rest. */
    private void outline(CanvasContext context, CommandDeckLayout.Fit fit, CommandDeckLayout.Station station,
                         float width, float height) {
        Rect r = CompanyIntelTerminal.stationRect(station, fit, layout, width, height);
        if (r.width() <= 0f || r.height() <= 0f) return;
        Color edge = alpha(WARM, 255);
        float arm = Math.min(18f, Math.min(r.width(), r.height()) * 0.3f);
        float x0 = r.x() + 1f, y0 = r.y() + 1f, x1 = r.x() + r.width() - 1f, y1 = r.y() + r.height() - 1f;
        context.fillRect(r.x(), r.y(), r.width(), r.height(), alpha(WARM, 24));
        context.line(x0, y0, x0 + arm, y0, edge, 2f);
        context.line(x0, y0, x0, y0 + arm, edge, 2f);
        context.line(x1, y0, x1 - arm, y0, edge, 2f);
        context.line(x1, y0, x1, y0 + arm, edge, 2f);
        context.line(x0, y1, x0 + arm, y1, edge, 2f);
        context.line(x0, y1, x0, y1 - arm, edge, 2f);
        context.line(x1, y1, x1 - arm, y1, edge, 2f);
        context.line(x1, y1, x1, y1 - arm, edge, 2f);
    }

    /** A sweep across the table's painted ring, and a contact for each received report. */
    private void plot(CanvasContext context, CommandDeckLayout.Fit fit) {
        float[] q = layout.plot();
        if (q.length < 8) return;
        float turn = motion.phase(5.5f, 0f);
        for (int trail = 6; trail >= 0; trail--) {
            double angle = (turn - trail * 0.012f) * Math.PI * 2;
            float[] edge = table(q, fit, 0.5f + 0.36f * (float) Math.cos(angle), 0.5f + 0.42f * (float) Math.sin(angle));
            float[] centre = table(q, fit, 0.5f, 0.5f);
            int alpha = trail == 0 ? 230 : 110 - trail * 14;
            context.line(centre[0], centre[1], edge[0], edge[1], alpha(SWEEP, alpha), trail == 0 ? 2f : 1.4f);
        }
        int contacts = Math.min(MAX_CONTACTS, desk.posts().size());
        for (int i = 0; i < contacts; i++) {
            // Deterministic positions per slot, so a contact holds its place while the sweep passes.
            float bearing = fraction(i * 0.618034f + 0.11f);
            float range = 0.25f + 0.7f * fraction(i * 0.414214f + 0.37f);
            double angle = bearing * Math.PI * 2;
            float[] p = table(q, fit, 0.5f + 0.36f * range * (float) Math.cos(angle),
                    0.5f + 0.42f * range * (float) Math.sin(angle));
            float since = fraction(turn - bearing);
            int alpha = Math.round(90 + 165 * (1f - since) * (1f - since));
            float size = 3.6f * fit.scale() + 2f;
            context.fillRect(p[0] - size * 0.5f, p[1] - size * 0.5f, size, size, alpha(SWEEP, alpha));
        }
    }

    /** Barely-there scan flicker on the painted screens, each on its own beat. */
    private void screens(CanvasContext context, CommandDeckLayout.Fit fit) {
        float[] anchors = layout.screens();
        float w = 34f * fit.scale();
        float h = 22f * fit.scale();
        for (int i = 0; i + 1 < anchors.length; i += 2) {
            float beat = motion.phase(2.6f + (i % 5) * 0.7f, i * 0.137f);
            int alpha = Math.round(6 + 16 * beat * beat);
            context.fillRect(fit.x(anchors[i]) - w * 0.5f, fit.y(anchors[i + 1]) - h * 0.5f, w, h, alpha(WARM, alpha));
        }
    }

    /** A point on the table top: u from the left edge to the right, v from the near rim to the far. */
    private static float[] table(float[] q, CommandDeckLayout.Fit fit, float u, float v) {
        // corners: near-left, near-right, far-right, far-left
        float nearX = q[0] + (q[2] - q[0]) * u, nearY = q[1] + (q[3] - q[1]) * u;
        float farX = q[6] + (q[4] - q[6]) * u, farY = q[7] + (q[5] - q[7]) * u;
        return new float[]{fit.x(nearX + (farX - nearX) * v), fit.y(nearY + (farY - nearY) * v)};
    }

    private static float fraction(float value) {
        return value - (float) Math.floor(value);
    }

    private static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
