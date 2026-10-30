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
 * fades briefly into the next. Transmissions may overlap (several speakers keyed up at once); each
 * live one is then its own {@link Transmission}, with its own tag and pages, earliest first.
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

    /** One speaker's transmission on screen: its tag and its visible pages. */
    public record Transmission(String speaker, double tagAlpha, List<Page> pages) { }

    /**
     * {@code tagSpeaker}, {@code tagAlpha} and {@code pages} describe a single-speaker exchange (the
     * latest live tag, every visible page); {@code transmissions} splits the same pages by
     * transmission, earliest first, and holds more than one only while transmissions overlap.
     */
    public record State(double fieldAlpha, String tagSpeaker, double tagAlpha, List<Page> pages,
                        List<Transmission> transmissions) {
        public boolean empty() { return fieldAlpha <= 0 && pages.isEmpty(); }
        /** Whether two or more transmissions show text at once. */
        public boolean overlapping() { return transmissions.size() > 1; }
    }

    private record Line(String speaker, double in, double out) { }

    /** One page of a take, already timed, and the index of the transmission it belongs to. */
    private record Entry(String speaker, double in, double out, List<String> rows, int line) { }

    private final List<Entry> captions = new ArrayList<>();
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
        List<Caption> ordered = new ArrayList<>(takes);
        ordered.sort((a, b) -> Double.compare(a.in(), b.in()));
        for (Caption take : ordered) {
            List<List<String>> pages = CaptionPager.pages(strings.apply(take.key()), measure, maxWidth);
            double[] shares = CaptionPager.shares(pages);
            double at = take.in();
            for (int i = 0; i < pages.size(); i++) {
                double end = i + 1 == pages.size() ? take.out() : at + (take.out() - take.in()) * shares[i];
                int line = assign(take.speaker(), at, end);
                captions.add(new Entry(take.speaker(), at, end, pages.get(i), line));
                at = end;
            }
        }
    }

    /**
     * Widens the caption field over a page and files it under its transmission: it continues its
     * speaker's latest transmission when it follows straight on, even if another speaker keyed up in
     * between, and opens a new one otherwise.
     */
    private int assign(String speaker, double in, double out) {
        double[] last = spans.isEmpty() ? null : spans.get(spans.size() - 1);
        if (last != null && in - last[1] < BRIDGE) last[1] = Math.max(last[1], out);
        else spans.add(new double[] {in, out});
        for (int i = lines.size() - 1; i >= 0; i--) {
            Line l = lines.get(i);
            if (!l.speaker().equals(speaker)) continue;
            if (in - l.out() > CONTIGUOUS) break;
            lines.set(i, new Line(speaker, l.in(), Math.max(l.out(), out)));
            return i;
        }
        lines.add(new Line(speaker, in, out));
        return lines.size() - 1;
    }

    public State at(double t) {
        double field = 0;
        for (double[] s : spans) field = Math.max(field, Ease.window(t, s[0], s[1], 0.5, 0.6));
        String tag = null;
        double tagAlpha = 0;
        double[] lineTag = new double[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (t < l.in() - 0.01 || t > l.out()) continue;
            tag = l.speaker();
            tagAlpha = Ease.window(t, l.in(), l.out(), TAG_FADE_IN, LINE_END_FADE);
            lineTag[i] = tagAlpha;
        }
        List<Page> pages = new ArrayList<>();
        List<List<Page>> byLine = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) byLine.add(new ArrayList<>());
        for (int i = 0; i < captions.size(); i++) {
            Entry c = captions.get(i);
            if (t < c.in() || t > c.out()) continue;
            Entry next = nextOfSpeaker(i);
            boolean lineEnds = next == null || next.in() - c.out() > CONTIGUOUS;
            double a = Ease.window(t, c.in(), c.out(), PAGE_FADE, lineEnds ? LINE_END_FADE : PAGE_FADE);
            if (a <= 0) continue;
            Page page = new Page(c.speaker(), c.rows(), a);
            pages.add(page);
            byLine.get(c.line()).add(page);
        }
        List<Transmission> transmissions = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++)
            if (!byLine.get(i).isEmpty())
                transmissions.add(new Transmission(lines.get(i).speaker(), lineTag[i], List.copyOf(byLine.get(i))));
        return new State(field, tag, tagAlpha, pages, transmissions);
    }

    /** The next page in the same speaker's voice after {@code index}, or null. */
    private Entry nextOfSpeaker(int index) {
        String speaker = captions.get(index).speaker();
        for (int i = index + 1; i < captions.size(); i++)
            if (captions.get(i).speaker().equals(speaker)) return captions.get(i);
        return null;
    }
}
