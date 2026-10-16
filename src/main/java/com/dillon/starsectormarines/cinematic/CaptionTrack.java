package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CinematicShot.Caption;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

/**
 * What the caption layer shows at a time: the dark field under the exchange, the speaker tag, and
 * the page of text. The rules follow {@code ui-overlay.md}: the field stays up across short gaps
 * so the picture does not pump between turns, the tag belongs to a whole transmission, and a page
 * fades briefly into the next.
 */
public final class CaptionTrack {
    /** Gaps shorter than this keep the caption field up. */
    static final double BRIDGE = 1.8;
    static final double PAGE_FADE = 0.14;
    static final double LINE_END_FADE = 0.25;
    static final double TAG_FADE_IN = 0.2;
    /** Pages of one transmission meet within this; a longer gap starts a new transmission. */
    private static final double CONTIGUOUS = 0.05;

    public record Page(String speaker, List<String> rows, double alpha) {
        public String text() { return String.join(" ", rows); }
    }

    public record State(double fieldAlpha, String tagSpeaker, double tagAlpha, List<Page> pages) {
        public boolean empty() { return fieldAlpha <= 0 && pages.isEmpty(); }
    }

    private record Line(String speaker, double in, double out) { }

    /** One page of a take, already timed. */
    private record Entry(String speaker, double in, double out, List<String> rows) { }

    private final List<Entry> captions;
    private final List<double[]> spans = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();

    /**
     * Pages every take's text in the active language ({@code strings} resolves a key) and spreads the
     * pages across the take in proportion to their length.
     *
     * @param measure the caption font's width of a string, in the units {@code maxWidth} is in
     */
    public CaptionTrack(List<Caption> takes, UnaryOperator<String> strings, ToDoubleFunction<String> measure,
                        double maxWidth) {
        this.captions = new ArrayList<>();
        for (Caption take : takes) {
            List<List<String>> pages = CaptionPager.pages(strings.apply(take.key()), measure, maxWidth);
            double[] shares = CaptionPager.shares(pages);
            double at = take.in();
            for (int i = 0; i < pages.size(); i++) {
                double end = i + 1 == pages.size() ? take.out() : at + (take.out() - take.in()) * shares[i];
                captions.add(new Entry(take.speaker(), at, end, pages.get(i)));
                at = end;
            }
        }
        for (Entry c : this.captions) {
            double[] last = spans.isEmpty() ? null : spans.get(spans.size() - 1);
            if (last != null && c.in() - last[1] < BRIDGE) last[1] = Math.max(last[1], c.out());
            else spans.add(new double[] {c.in(), c.out()});
            Line prev = lines.isEmpty() ? null : lines.get(lines.size() - 1);
            if (prev != null && prev.speaker().equals(c.speaker()) && c.in() - prev.out() <= CONTIGUOUS)
                lines.set(lines.size() - 1, new Line(prev.speaker(), prev.in(), c.out()));
            else lines.add(new Line(c.speaker(), c.in(), c.out()));
        }
    }

    public State at(double t) {
        double field = 0;
        for (double[] s : spans) field = Math.max(field, Ease.window(t, s[0], s[1], 0.5, 0.6));
        String tag = null;
        double tagAlpha = 0;
        for (Line l : lines) {
            if (t < l.in() - 0.01 || t > l.out()) continue;
            tag = l.speaker();
            tagAlpha = Ease.window(t, l.in(), l.out(), TAG_FADE_IN, LINE_END_FADE);
        }
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < captions.size(); i++) {
            Entry c = captions.get(i);
            if (t < c.in() || t > c.out()) continue;
            boolean lineEnds = i + 1 == captions.size() || captions.get(i + 1).in() - c.out() > CONTIGUOUS
                    || !captions.get(i + 1).speaker().equals(c.speaker());
            double a = Ease.window(t, c.in(), c.out(), PAGE_FADE, lineEnds ? LINE_END_FADE : PAGE_FADE);
            if (a > 0) pages.add(new Page(c.speaker(), c.rows(), a));
        }
        return new State(field, tag, tagAlpha, pages);
    }
}
