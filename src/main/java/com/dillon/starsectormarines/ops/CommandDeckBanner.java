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
 * The command deck drawn live, back to front: the view of space and the screens'
 * readouts ({@link DeckDisplays}), which a report arriving brightens on the comms bank;
 * the painted plate over them, whose windows and screens are holes, so whatever was
 * painted in front of them stays in front; the consoles' buttons ({@link DeckPanels});
 * the people beyond the table, the action board's hologram over it
 * ({@link HqHoloBoard}), and the people before it.
 *
 * <p>A station a player is pointing at or has focused lights its own people, which is
 * why the figures are separate sprites rather than painted into the plate. A report
 * arriving lights the comms watch the same way for a few seconds. Nothing here is the
 * only place a fact appears: the overlays dramatise the received-report projection the
 * news desk already shows in words, and the hologram projects the action list.
 */
final class CommandDeckBanner implements CanvasProducer {
    private static final Color WARM = new Color(255, 214, 150);
    private static final float SPACE_MARGIN = 0.03f;
    private static final float SPACE_DRIFT = 0.012f;

    private final CompanyHqNewsDesk desk;
    private final CommandDeckLayout layout;
    private final CompanyIntelMotion motion = new CompanyIntelMotion();
    private final HqHoloBoard holo = new HqHoloBoard();
    private final DeckDisplays displays = new DeckDisplays();
    private final DeckPanels panels = new DeckPanels();
    private DeckPanels.Tone runway = DeckPanels.Tone.NONE;
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

    /** How the payroll runway stands, for the quartermaster's indicator lamps. */
    void runway(DeckPanels.Tone tone) { runway = Objects.requireNonNull(tone, "runway tone"); }

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
        float time = motion.timeSeconds();
        float arrival = Math.max(motion.arrivalStrength(CompanyIntelMotion.COMPANY),
                Math.max(motion.arrivalStrength(CompanyIntelMotion.CONFIRMED),
                        motion.arrivalStrength(CompanyIntelMotion.RUMOR)));
        space(context, fit, time);
        displays.screens(context, layout, fit, time, arrival);
        context.sprite(layout.plate(), null, fit.x(layout.width() * 0.5f), fit.y(layout.height() * 0.5f),
                layout.width() * fit.scale(), layout.height() * fit.scale(), 0f, Color.WHITE);
        displays.bloom(context);
        panels.draw(context, layout, fit, time,
                new DeckPanels.Signals(arrival, board == null ? 0 : board.items().size(), runway));
        CommandDeckLayout.Station hot = hotStation == null ? null : layout.station(hotStation);
        figures(context, fit, hot, arrival, true);
        holo.draw(context, layout, fit, board, holoSelected, holoHovered, time);
        if (hot != null) outline(context, fit, hot, width, height);
        figures(context, fit, hot, arrival, false);
    }

    /** The view out of the windows, drifting very slowly as the ship does. */
    private void space(CanvasContext context, CommandDeckLayout.Fit fit, float time) {
        if (layout.space() == null) return;
        float across = 0.5f + 0.5f * (float) Math.sin(time * SPACE_DRIFT);
        CanvasSpriteRegion region = new CanvasSpriteRegion(across * SPACE_MARGIN, 0f, 1f - SPACE_MARGIN, 1f);
        context.sprite(layout.space(), null, fit.x(layout.width() * 0.5f), fit.y(layout.height() * 0.5f),
                layout.width() * fit.scale(), layout.height() * fit.scale(), 0f, Color.WHITE, region, CanvasBlend.NORMAL);
    }

    private void figures(CanvasContext context, CommandDeckLayout.Fit fit, CommandDeckLayout.Station hot,
                         float arrival, boolean behindPlot) {
        CommandDeckLayout.Station comms = layout.station("comms");
        for (CommandDeckLayout.Figure figure : layout.figures()) {
            if (figure.behindPlot() != behindPlot) continue;
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

    private static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
