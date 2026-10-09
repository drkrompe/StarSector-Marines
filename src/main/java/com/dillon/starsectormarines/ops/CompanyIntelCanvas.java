package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.CompanyHqViewModel.NewsPost;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/** The bridge's intelligence signal diagram, over the same received-report projection as its desk. */
final class CompanyIntelCanvas implements CanvasProducer {
    static final int SURFACE_WIDTH = 1180;
    static final int SURFACE_HEIGHT = 300;
    private static final Color CYAN = new Color(116, 215, 215);
    private static final Color GOLD = new Color(218, 177, 102);
    private static final Color MUTED = new Color(100, 134, 134);
    private static final Color RULE = new Color(43, 70, 71);
    private static final Color DARK = new Color(8, 19, 23);
    private static final Color WHITE = new Color(221, 227, 207);
    private static final String[] SOURCES = {"COMPANY OPERATIONS", "CONFIRMED SECTOR", "UNVERIFIED TRAFFIC"};
    private static final String[] PROTOCOLS = {"OPERATIONS DISPATCH", "VALIDATED REPORTS", "CORROBORATION PENDING"};
    private static final float[] ROWS = {64f, 147f, 230f};
    private static final float[][] ROUTES = {
            {342, 64, 399, 64, 438, 103, 497, 103, 524, 130, 566, 130},
            {342, 147, 414, 147, 443, 176, 487, 176, 516, 147, 566, 147},
            {342, 230, 391, 230, 427, 194, 494, 194, 521, 167, 566, 167}
    };
    private static final float[] OUTPUT = {734, 147, 796, 147, 824, 119, 865, 119, 890, 144, 943, 144};
    private final CompanyHqNewsDesk desk;
    private final CompanyIntelMotion motion = new CompanyIntelMotion();
    private final SvgAsset plate;
    private final SvgAsset[] sourceIcons;
    private final String[] countLabels = {"00", "00", "00"};
    private final int[] labelCounts = {-1, -1, -1};
    private String archiveCount = "0 RECEIVED REPORTS";
    private int totalCount = -1;
    private List<NewsPost> observedPosts;
    private NewsPost observedSelection;

    CompanyIntelCanvas(CompanyHqNewsDesk desk) {
        this.desk = Objects.requireNonNull(desk, "news desk");
        plate = SvgAsset.load("graphics/ui/console/hq-intel-network.svg");
        sourceIcons = new SvgAsset[]{
                SvgAsset.load("graphics/ui/console/hq-source-company.svg"),
                SvgAsset.load("graphics/ui/console/hq-source-confirmed.svg"),
                SvgAsset.load("graphics/ui/console/hq-source-rumor.svg")};
    }

    CompanyIntelMotion motion() { return motion; }

    void advance(float seconds) {
        observe();
        motion.advance(seconds);
    }

    private void observe() {
        List<NewsPost> posts = desk.posts();
        NewsPost selection = desk.selectedPost();
        if (observedPosts == posts && observedSelection == selection) return;
        observedPosts = posts;
        observedSelection = selection;
        motion.observe(posts, selection);
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            int count = motion.count(i);
            sum += count;
            if (labelCounts[i] != count) {
                labelCounts[i] = count;
                countLabels[i] = (count < 10 ? "0" : "") + count;
            }
        }
        if (sum != totalCount) {
            totalCount = sum;
            archiveCount = sum + (sum == 1 ? " RECEIVED REPORT" : " RECEIVED REPORTS");
        }
    }

    @Override public void draw(CanvasContext context) {
        context.fillRect(0, 0, SURFACE_WIDTH, SURFACE_HEIGHT, DARK);
        context.svg(plate, 0, 0, SURFACE_WIDTH, SURFACE_HEIGHT, Color.WHITE);
        label(context, "INTELLIGENCE / SIGNAL PATH", 25, 13, GOLD);
        label(context, "RECEIVERS  >  COLLATION  >  ARCHIVE", 818, 13, MUTED);
        // A slow wash crosses only the diagram, leaving the readable briefing untouched.
        float sweepX = 14 + motion.phase(24, 0) * 1152;
        for (int i = 0; i < 5; i++) {
            context.fillRect(sweepX - i * 9, 32, 9, 232, tint(CYAN, 4 - i * 0.6f));
        }
        for (int i = 0; i < 3; i++) {
            receiver(context, i);
            route(context, ROUTES[i], i == 2 ? GOLD : CYAN, i == 2);
            carrier(context, ROUTES[i], i == 2 ? GOLD : CYAN,
                    motion.phase(i == 2 ? 12 : 6, i * 0.31f), i == 2);
            if (i != 2) carrier(context, ROUTES[i], CYAN, motion.phase(6, i * 0.31f + 0.5f), false);
            float arrival = motion.arrivalStrength(i);
            if (arrival > 0) {
                arc(context, 72, ROWS[i], 34 + (1 - arrival) * 16, 0, 360, tint(WHITE, arrival * 150), 1.7f);
                label(context, "REPORT RECEIVED", 348, ROWS[i] - 15, tint(GOLD, arrival * 255));
            }
        }
        processor(context);
        route(context, OUTPUT, GOLD, false);
        carrier(context, OUTPUT, GOLD, motion.phase(8, 0.12f), false);
        archive(context);
        label(context, "CARRIER MONITOR / CONTINUOUS", 26, 277, MUTED);
        context.fillRect(335, 281, 5, 5, CYAN);
        label(context, "ARCHIVE UPLINK", 349, 277, MUTED);
        label(context, "OPEN BAND / CORROBORATION REQUIRED", 700, 277, GOLD);
    }

    private void receiver(CanvasContext context, int channel) {
        float y = ROWS[channel];
        boolean selected = motion.selectedChannel() == channel;
        Color color = channel == 2 ? GOLD : CYAN;
        arc(context, 72, y, 30, 0, 360, RULE, 1);
        arc(context, 72, y, 36, 208, 92, tint(color, 65), 1);
        arc(context, 72, y, 36, 20, 108, tint(color, 65), 1);
        float phase = motion.phase(channel == 2 ? 16 : 8, channel * 0.19f);
        arc(context, 72, y, 30, phase * 360, channel == 2 ? 40 : 70, tint(color, 195), 1.8f);
        arc(context, 72, y, 22, -phase * 360, 85, tint(color, 60), 1);
        context.svg(sourceIcons[channel], 56, y - 16, 32, 32, selected ? WHITE : tint(color, 185));
        context.line(106, y, 117, y, color, 1);
        label(context, SOURCES[channel], 126, y - 18, selected ? WHITE : color);
        label(context, PROTOCOLS[channel], 126, y + 3, MUTED);
        context.text(Fonts.ORBITRON_16, countLabels[channel], 287, y - 17, selected ? GOLD : WHITE);
        label(context, "REPORTS", 282, y + 5, MUTED);
        context.line(126, y + 22, 322, y + 22, RULE, 1);
        if (selected) {
            context.fillRect(120, y - 18, 2, 14, GOLD);
            context.line(126, y + 22, 126 + 196 * motion.selectionReveal(), y + 22, GOLD, 1.6f);
        }
        // Oscilloscope samples are a fixed carrier pattern, not synthetic report content.
        for (int j = 0; j < 24; j++) {
            float amplitude = 2 + (float) Math.sin(j * 1.8 + channel * 2.1) * 2;
            float light = motion.phase(6, channel * 0.31f) * 24;
            context.line(126 + j * 8, y + 30 - amplitude, 126 + j * 8, y + 30 + amplitude,
                    tint(color, Math.abs(j - light) < 3 ? 105 : 28), 1);
        }
    }

    private void processor(CanvasContext context) {
        float phase = motion.phase(48, 0);
        for (int i = 0; i < 4; i++) {
            arc(context, 650, 147, 75, i * 90 + phase * 360, 48, tint(CYAN, 130), 1.4f);
            arc(context, 650, 147, 67, i * 90 - phase * 360 * 2, 21, tint(GOLD, 175), 2);
        }
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16;
            float a = i % 4 == 0 ? 86 : 82;
            context.line(650 + (float) Math.cos(angle) * 79, 147 + (float) Math.sin(angle) * 79,
                    650 + (float) Math.cos(angle) * a, 147 + (float) Math.sin(angle) * a,
                    tint(MUTED, i % 4 == 0 ? 140 : 60), 1);
        }
        context.fillRect(597, 109, 106, 75, DARK);
        context.strokeRect(597, 109, 106, 75, tint(CYAN, 155), 1);
        label(context, "COLLATION", 609, 119, WHITE);
        label(context, "SIGNAL INDEX", 608, 137, CYAN);
        label(context, "FLAGSHIP CORE", 605, 158, GOLD);
        float beam = 110 + motion.phase(8, 0) * 73;
        context.line(599, beam, 701, beam, tint(CYAN, 60), 1.5f);
        for (int i = 0; i < 9; i++) {
            float brightness = 0.5f + 0.5f * (float) Math.sin(motion.phase(6, i / 9f) * Math.PI * 2);
            context.fillRect(610 + i * 9, 193, 5, 3, tint(CYAN, 35 + brightness * 100));
        }
        label(context, "REPORT INDEX", 604, 244, MUTED);
        context.line(609, 231, 691, 231, RULE, 1);
    }

    private void archive(CanvasContext context) {
        Color active = motion.hasSelection() ? GOLD : MUTED;
        context.fillRect(944, 57, 207, 184, new Color(13, 27, 29));
        context.strokeRect(944, 57, 207, 184, RULE, 1);
        context.line(944, 57, 1008, 57, active, 2);
        label(context, "INTELLIGENCE ARCHIVE", 959, 70, WHITE);
        label(context, archiveCount, 959, 91, CYAN);
        // Nested document outlines identify the output as an archive, not a tactical contact map.
        context.strokeRect(1053, 127, 69, 66, tint(MUTED, 65), 1);
        context.strokeRect(1045, 120, 69, 66, tint(MUTED, 110), 1);
        context.fillRect(970, 113, 131, 89, DARK);
        context.strokeRect(970, 113, 131, 89, active, 1.2f);
        label(context, motion.hasSelection() ? "DOSSIER IN VIEW" : "NO REPORT IN VIEW", 980, 126, active);
        for (int i = 0; i < 4; i++) {
            context.line(982, 152 + i * 10, 1079 - (i % 2) * 21, 152 + i * 10, tint(MUTED, 90), 1);
        }
        if (motion.hasSelection()) {
            float reveal = motion.selectionReveal();
            context.line(970, 202, 970 + reveal * 131, 202, GOLD, 2);
            if (reveal < 1) context.fillRect(970 + reveal * 128, 115, 3, 85, tint(GOLD, (1 - reveal) * 100));
        }
        label(context, motion.selectedChannel() == 2 ? "UNCONFIRMED / ANALYST REVIEW" :
                motion.hasSelection() ? "DOSSIER ACCESS / AUTHORIZED" : "AWAITING RECEIVED REPORTS", 959, 221, active);
    }

    private static void route(CanvasContext context, float[] points, Color color, boolean fragmented) {
        for (int i = 2; i < points.length; i += 2) {
            float x = points[i - 2], y = points[i - 1], dx = points[i] - x, dy = points[i + 1] - y;
            if (!fragmented) {
                context.line(x, y, points[i], points[i + 1], tint(color, 17), 6);
                context.line(x, y, points[i], points[i + 1], tint(color, 90), 1);
            } else {
                float length = (float) Math.hypot(dx, dy);
                for (float position = 0; position < length; position += 11) {
                    float end = Math.min(position + 5, length);
                    context.line(x + dx * position / length, y + dy * position / length,
                            x + dx * end / length, y + dy * end / length, tint(color, 70), 1);
                }
            }
            context.fillRect(points[i] - 2, points[i + 1] - 2, 4, 4, tint(color, 100));
        }
    }

    private static void carrier(CanvasContext context, float[] points, Color color, float phase, boolean fragmented) {
        if (fragmented && phase > 0.68f) return;
        float length = 0;
        for (int i = 2; i < points.length; i += 2) length += (float) Math.hypot(points[i] - points[i - 2], points[i + 1] - points[i - 1]);
        float head = phase * length;
        for (int trail = 0; trail < 9; trail++) {
            float distance = head - trail * 4;
            if (distance < 0) continue;
            for (int i = 2; i < points.length; i += 2) {
                float dx = points[i] - points[i - 2], dy = points[i + 1] - points[i - 1];
                float segment = (float) Math.hypot(dx, dy);
                if (distance <= segment) {
                    float x = points[i - 2] + dx * distance / segment;
                    float y = points[i - 1] + dy * distance / segment;
                    context.fillRect(x - 3, y - 3, 6, 6, tint(color, (9 - trail) * 3));
                    context.fillRect(x - 1, y - 1, 2, 2, tint(trail == 0 ? WHITE : color, (9 - trail) * 25));
                    break;
                }
                distance -= segment;
            }
        }
    }

    private static void arc(CanvasContext context, float x, float y, float radius, float start, float extent, Color color, float width) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(extent) / 6));
        double angle = Math.toRadians(start);
        float px = x + (float) Math.cos(angle) * radius, py = y + (float) Math.sin(angle) * radius;
        for (int i = 1; i <= steps; i++) {
            angle = Math.toRadians(start + extent * i / steps);
            float nx = x + (float) Math.cos(angle) * radius, ny = y + (float) Math.sin(angle) * radius;
            context.line(px, py, nx, ny, color, width);
            px = nx; py = ny;
        }
    }

    private static void label(CanvasContext context, String text, float x, float y, Color color) {
        context.text(Fonts.ORBITRON_10, text, x, y, color);
    }

    private static Color tint(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, Math.round(alpha))));
    }
}
