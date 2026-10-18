package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.CompanyHqViewModel.NewsPost;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.Rect;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The command deck drawn live: the painted plate cover-fitted to the banner, each
 * figure as its own sprite, and the overlays the painting leaves blank for — the
 * action board's hologram over the plotting table ({@link HqHoloBoard}), and a faint
 * flicker on the console screens.
 *
 * <p>A station a player is pointing at or has focused lights its own people, which is
 * why the figures are separate sprites rather than painted into the plate. A report
 * arriving lights the comms watch the same way for a few seconds. Nothing here is the
 * only place a fact appears: the overlays dramatise the received-report projection the
 * news desk already shows in words, and the hologram projects the action list.
 */
final class CommandDeckBanner implements CanvasProducer {
    private static final Color WARM = new Color(255, 214, 150);

    private final CompanyHqNewsDesk desk;
    private final CommandDeckLayout layout;
    private final CompanyIntelMotion motion = new CompanyIntelMotion();
    private final HqHoloBoard holo = new HqHoloBoard();
    private HqActionBoard board;
    private String holoSelected;
    private String holoHovered;
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

    /** The action board the hologram shows; null or empty leaves the bare scanning disc. */
    void board(HqActionBoard board) { this.board = board; }

    /** The selected action item's id, or null; it gets the beam from the table. */
    void holoSelected(String itemId) { holoSelected = itemId; }

    /** The action item the pointer is over, or null; its marker brightens. */
    void holoHovered(String itemId) { holoHovered = itemId; }

    /**
     * Each action item's marker and label on a banner of this size, in banner pixels,
     * keyed by item id in board order: where buttons over the hologram belong.
     */
    Map<String, HqHoloBoard.Hit> holoHits(float width, float height) {
        if (width <= 0f || height <= 0f) return Map.of();
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(width, height, layout.width(), layout.height());
        return holo.hits(layout, fit, board);
    }

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
        holo.draw(context, layout, fit, board, holoSelected, holoHovered, motion.timeSeconds());
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

    private static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
