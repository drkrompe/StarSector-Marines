package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Finds fragile retained layout in the documents the snapshot suites already lay out.
 *
 * <p>Switched on by {@code -Dsnapshot.layoutLint=<dir>} under {@code createSnapshots}: every document
 * {@code HeadlessUiRenderer} paints is laid out again, after its own frame, at each of {@link #SIZES}
 * through the same {@link UiViewport#relative} policy the host uses, inspected, and restored to the size
 * it was painted at. A document painted at a panel size rather than full screen is inspected only at that
 * size. It lays out the page the suite built, with whatever sheets and theme the suite installed, so a
 * suite that leaves out a sheet the game installs is linting a different page from the game.
 *
 * <p>Three findings, each measured in document px:
 * <ul>
 *   <li>{@link Kind#TEXT_CLIP}: wrapped text whose lines do not all fit its content box, or one line
 *       cut by more than a quarter of its line height. A single line only a little taller than its box
 *       still reads, and is the low-severity {@link Kind#TEXT_TIGHT}. {@link Kind#TEXT_WIDE} is the
 *       horizontal twin for unwrapped text.</li>
 *   <li>{@link Kind#CLIP_SPILL}: a border box that crosses the padding box of its nearest clipping
 *       ancestor (the rectangle paint actually cuts to), reported once per spilling subtree. A
 *       {@code overflow: scroll} ancestor's vertical extent is not a spill. An absolutely positioned
 *       subtree is usually placed by the host from boxes measured at the painted size (a spotlight, a
 *       spec sheet beside its card), so it is checked only at the size the document was painted at.</li>
 *   <li>{@link Kind#HIDDEN_GAP}: a flow child collapsed to zero on the main axis (the
 *       {@code width:0;height:0;opacity:0} hide) that still takes one {@code gap} slot.</li>
 * </ul>
 * Subtrees at zero opacity are invisible and are not inspected for text or spill.
 */
public final class LayoutLint {

    public static final String PROPERTY = "snapshot.layoutLint";

    /** Resolutions every full-screen document is linted at, with the native panel column last. */
    public static final List<String> COLUMNS = List.of("1744x938", "1920x1080", "2560x1440", "panel");
    private static final int[][] SIZES = { { 1744, 938 }, { 1920, 1080 }, { 2560, 1440 } };
    private static final int PANEL = 3;
    private static final float EPSILON = 0.5f;

    public enum Kind {
        TEXT_CLIP("text-clip"),
        TEXT_WIDE("text-wide"),
        CLIP_SPILL("clip-spill"),
        HIDDEN_GAP("hidden-gap"),
        TEXT_TIGHT("text-tight");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Finding(Kind kind, String element, float overflow, Rect box) {}

    /** How a full-screen document was fitted to the physical screen. */
    public record Policy(float uiScale, float referenceWidth, float referenceHeight) {}

    private static final Map<String, Map<String, float[]>> RECORDED = new TreeMap<>();
    private static final Map<String, int[]> DOCUMENTS = new TreeMap<>();
    private static final Map<String, Set<String>> SUITES = new TreeMap<>();
    private static final Map<String, Integer> MARKED = new TreeMap<>();
    private static final Map<String, String> MARKED_SUITE = new TreeMap<>();
    /** Outline colour per {@link Kind}, in declaration order: red, orange, magenta, cyan, and a dim grey for tight text. */
    private static final Color[] MARK_COLOURS = {
            new Color(0xFF3B30), new Color(0xFF9F0A), new Color(0xFF2DD4), new Color(0x2DE1FF), new Color(0x8A8F80) };
    private static volatile String suite = "unlabelled";
    private static volatile int translations;

    private LayoutLint() {}

    public static boolean enabled() {
        String value = System.getProperty(PROPERTY);
        return value != null && !value.isBlank();
    }

    public static Path reportDirectory() {
        return Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();
    }

    /** The suite whose documents are observed next; suites render one at a time. */
    public static void suite(String id) {
        suite = id;
    }

    /**
     * Lints a document just painted at {@code documentWidth} x {@code documentHeight}. A null policy
     * marks a panel painted at its own size.
     */
    public static void observe(UiDocument document, float documentWidth, float documentHeight,
                               Policy policy, BufferedImage painted) {
        if (!enabled() || document.root() == null) return;
        UiLayoutEngine engine = new UiLayoutEngine(new UiTextMeasurer(document.styles()));
        String screen = screen(document.root(), policy);
        if (policy == null) {
            record(screen, PANEL, inspect(document, true));
            return;
        }
        for (int index = 0; index < SIZES.length; index++) {
            UiViewport viewport = UiViewport.relative(0f, 0f,
                    SIZES[index][0] / policy.uiScale(), SIZES[index][1] / policy.uiScale(),
                    policy.uiScale(), policy.referenceWidth(), policy.referenceHeight());
            engine.layout(document.root(), viewport.documentWidth(), viewport.documentHeight());
            boolean atPaintedSize = Math.abs(viewport.documentWidth() - documentWidth) < 0.5f
                    && Math.abs(viewport.documentHeight() - documentHeight) < 0.5f;
            List<Finding> findings = inspect(document, atPaintedSize);
            record(screen, index, findings);
            if (atPaintedSize && SIZES[index][0] == 1920 && policy.uiScale() == 1f && painted != null
                    && painted.getWidth() == 1920 && painted.getHeight() == 1080) {
                mark(screen, painted, findings);
            }
        }
        engine.layout(document.root(), documentWidth, documentHeight);
    }

    /**
     * Keeps, per screen, the 1920x1080 frame with the most findings, each outlined in its kind's colour, under
     * {@code <dir>/marked/}. Written when a frame beats the last, so the run holds no frames in memory.
     */
    private static void mark(String screen, BufferedImage painted, List<Finding> findings) {
        // Ranked by the faults that cost the reader something, so a frame of tight labels never wins.
        int faults = (int) findings.stream().filter(finding -> finding.kind() != Kind.TEXT_TIGHT).count();
        synchronized (LayoutLint.class) {
            if (faults == 0 || faults <= MARKED.getOrDefault(screen, 0)) return;
            MARKED.put(screen, faults);
            MARKED_SUITE.put(screen, suite);
        }
        BufferedImage marked = new BufferedImage(painted.getWidth(), painted.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = marked.createGraphics();
        graphics.drawImage(painted, 0, 0, null);
        graphics.setStroke(new BasicStroke(2f));
        for (Finding finding : findings) {
            graphics.setColor(MARK_COLOURS[finding.kind().ordinal()]);
            Rect box = finding.box();
            // A zero-size hide has no box to outline; a small square marks the slot it holds.
            int width = Math.max(6, Math.round(box.width()));
            int height = Math.max(6, Math.round(box.height()));
            graphics.drawRect(Math.round(box.x()), Math.round(box.y()), width, height);
        }
        graphics.dispose();
        try {
            Path file = reportDirectory().resolve("marked").resolve(fileName(screen) + ".png");
            Files.createDirectories(file.getParent());
            ImageIO.write(marked, "png", file.toFile());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String fileName(String screen) {
        return screen.replaceAll("[^A-Za-z0-9.-]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Every finding in the document's current boxes. */
    public static List<Finding> inspect(UiDocument document) {
        return inspect(document, true);
    }

    /** {@code positioned} false leaves absolutely positioned subtrees out of the spill check. */
    static List<Finding> inspect(UiDocument document, boolean positioned) {
        List<Finding> findings = new ArrayList<>();
        UiTextMeasurer text = new UiTextMeasurer(document.styles());
        visit(document, text, document.root(), null, null, !positioned, false, findings);
        return findings;
    }

    private static void visit(UiDocument document, UiTextMeasurer text, UiElement element,
                              Rect clip, UiElement clipper, boolean skipPositioned, boolean spilled,
                              List<Finding> findings) {
        if (element.opacity() <= 0f) return;
        if (skipPositioned && element.position() == UiPosition.ABSOLUTE) spilled = true;
        Rect border = element.box().borderBox();
        boolean visible = border.width() > 0f && border.height() > 0f;
        if (visible) {
            checkText(document, text, element, findings);
            if (!spilled && clip != null) {
                float spill = spill(border, clip, clipper.overflow() == Overflow.SCROLL);
                if (spill > EPSILON) {
                    findings.add(new Finding(Kind.CLIP_SPILL, label(element), spill, border));
                    spilled = true;
                }
            }
        }
        checkGap(element, findings);
        Rect childClip = clip;
        UiElement childClipper = clipper;
        if (element.overflow().clips()) {
            childClip = clip == null ? element.box().paddingBox() : clip.intersect(element.box().paddingBox());
            childClipper = element;
        }
        for (UiElement child : element.children()) {
            visit(document, text, child, childClip, childClipper, skipPositioned, spilled, findings);
        }
    }

    private static void checkText(UiDocument document, UiTextMeasurer text, UiElement element,
                                  List<Finding> findings) {
        String value = element.text();
        if (value == null || value.isBlank()) return;
        BitmapFont font = document.styles().fontFor(element);
        if (font == null) return;
        Rect content = element.box().contentBox();
        UiTextMeasurer.Measurement measured = text.measure(element, content.width());
        float tall = measured.height() - content.height();
        if (tall > EPSILON) {
            boolean lost = measured.lines().size() > 1 || tall > font.getLineHeight() * 0.25f;
            findings.add(new Finding(lost ? Kind.TEXT_CLIP : Kind.TEXT_TIGHT, label(element), tall, content));
        }
        float wide = measured.width() - content.width();
        if (wide > EPSILON) findings.add(new Finding(Kind.TEXT_WIDE, label(element), wide, content));
    }

    private static float spill(Rect box, Rect clip, boolean scrolls) {
        float spill = Math.max(clip.x() - box.x(), box.right() - clip.right());
        if (!scrolls) spill = Math.max(spill, Math.max(clip.y() - box.y(), box.bottom() - clip.bottom()));
        return spill;
    }

    private static void checkGap(UiElement parent, List<Finding> findings) {
        UiLayout layout = parent.layout();
        if (layout == UiLayout.STACK || parent.children().size() < 2) return;
        List<UiElement> flow = new ArrayList<>();
        for (UiElement child : parent.children()) {
            if (child.position() != UiPosition.ABSOLUTE) flow.add(child);
        }
        if (flow.size() < 2) return;
        float width = parent.box().contentBox().width();
        boolean horizontal = layout != UiLayout.COLUMN;
        float gap = layout == UiLayout.GRID ? parent.resolvedColumnGap(width) : parent.resolvedGap(width);
        if (gap <= EPSILON) return;
        for (UiElement child : flow) {
            Rect box = child.box().borderBox();
            if ((horizontal ? box.width() : box.height()) <= 0.01f) {
                findings.add(new Finding(Kind.HIDDEN_GAP, label(child), gap, box));
            }
        }
    }

    /** The last three id-or-class segments down to the element, enough to find it in the markup. */
    static String label(UiElement element) {
        List<String> segments = new ArrayList<>();
        for (UiElement at = element; at != null && segments.size() < 3; at = at.parent()) {
            String segment = segment(at);
            if (segment != null) segments.add(0, segment);
        }
        return segments.isEmpty() ? element.tag().name().toLowerCase(Locale.ROOT) : String.join(" > ", segments);
    }

    private static String segment(UiElement element) {
        StringBuilder out = new StringBuilder();
        // The id names the element; classes only when it has none, and never the `mlx-<component>` scope class.
        if (element.id() != null && !element.id().isBlank()) return "#" + element.id();
        for (String className : element.classes()) {
            if (!className.startsWith("mlx-")) out.append('.').append(className);
        }
        return out.isEmpty() ? null : out.toString();
    }

    /**
     * Marks pages drawn under a translation overlay (a localization suite's stretched strings), which are
     * counted as their own screens so they never inflate or hide what the shipped strings do.
     */
    public static synchronized void translated(boolean entering) {
        translations = Math.max(0, translations + (entering ? 1 : -1));
    }

    private static String screen(UiElement root, Policy policy) {
        String label = segment(root);
        String screen = label == null ? "(root)" : label;
        if (translations > 0) screen += " [translated]";
        if (policy != null && policy.uiScale() != 1f) {
            screen += String.format(Locale.ROOT, " @%.2gx", policy.uiScale());
        }
        return screen;
    }

    private static synchronized void record(String screen, int column, List<Finding> findings) {
        DOCUMENTS.computeIfAbsent(screen, ignored -> new int[COLUMNS.size()])[column]++;
        SUITES.computeIfAbsent(screen, ignored -> new TreeSet<>()).add(suite);
        Map<String, float[]> rows = RECORDED.computeIfAbsent(screen, ignored -> new TreeMap<>());
        for (Finding finding : findings) {
            float[] overflow = rows.computeIfAbsent(finding.kind().label() + "\t" + finding.element(),
                    ignored -> new float[COLUMNS.size()]);
            overflow[column] = Math.max(overflow[column], finding.overflow());
        }
    }

    /** Writes {@code layout-lint.csv} (every row, ranked) and {@code layout-lint.md} (counts and the top rows). */
    public static synchronized void write(Path directory) throws IOException {
        Files.createDirectories(directory);
        List<Row> rows = new ArrayList<>();
        for (var screen : RECORDED.entrySet()) {
            for (var row : screen.getValue().entrySet()) {
                String[] key = row.getKey().split("\t", 2);
                rows.add(new Row(screen.getKey(), key[1], key[0], row.getValue()));
            }
        }
        rows.sort(Comparator.comparingDouble(Row::worst).reversed()
                .thenComparing(Row::screen).thenComparing(Row::element));

        StringBuilder csv = new StringBuilder("screen,element,kind");
        for (String column : COLUMNS) csv.append(',').append(column);
        csv.append(",worst\n");
        for (Row row : rows) {
            csv.append(quote(row.screen())).append(',').append(quote(row.element())).append(',').append(row.kind());
            for (float value : row.overflow()) csv.append(',').append(value > 0f ? px(value) : "");
            csv.append(',').append(px(row.worst())).append('\n');
        }
        Files.writeString(directory.resolve("layout-lint.csv"), csv.toString());
        Files.writeString(directory.resolve("layout-lint.md"), markdown(rows));
    }

    /** One row instance id ({@code transfer-stock:metals}, {@code spotlight-shade-3}) folded into its pattern. */
    private static final Pattern INSTANCE = Pattern.compile(":[^ >.#]+|-[0-9]+(?=$|[ >.])");

    static String pattern(String element) {
        return INSTANCE.matcher(element).replaceAll(match -> match.group().startsWith(":") ? ":*" : "-#");
    }

    private static String markdown(List<Row> rows) {
        Kind[] kinds = Kind.values();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Set<String>> patterns = new LinkedHashMap<>();
        for (String screen : DOCUMENTS.keySet()) {
            counts.put(screen, new int[COLUMNS.size() + kinds.length]);
            patterns.put(screen, new TreeSet<>());
        }
        int[] totals = new int[COLUMNS.size() + kinds.length];
        for (Row row : rows) {
            int[] count = counts.get(row.screen());
            for (int column = 0; column < COLUMNS.size(); column++) {
                if (row.overflow()[column] > 0f) {
                    count[column]++;
                    totals[column]++;
                }
            }
            int kind = COLUMNS.size() + kindOf(row.kind()).ordinal();
            count[kind]++;
            totals[kind]++;
            patterns.get(row.screen()).add(row.kind() + " " + pattern(row.element()));
        }
        int patternTotal = 0;
        for (Set<String> set : patterns.values()) patternTotal += set.size();

        StringBuilder out = new StringBuilder("# Layout lint\n\n");
        out.append(rows.size()).append(" broken spots (").append(patternTotal).append(" distinct patterns) on ")
                .append(counts.size()).append(" screens.\n\n")
                .append("A spot is one element and one kind of fault. A pattern folds repeated instances of one")
                .append(" markup node (`transfer-stock:*`, `spotlight-shade-#`) into one, which is closer to the number")
                .append(" of places to fix. A resolution column counts the spots present at that size; `panel` is a")
                .append(" document painted at its own size rather than full screen. Overflow is in document px.\n\n");
        out.append("| screen | spots | patterns |");
        for (String column : COLUMNS) out.append(' ').append(column).append(" |");
        for (Kind kind : kinds) out.append(' ').append(kind.label()).append(" |");
        out.append(" docs | suites |\n|---|---:|---:|").append("---:|".repeat(COLUMNS.size() + kinds.length))
                .append("---:|---|\n");
        List<String> ranked = new ArrayList<>(counts.keySet());
        ranked.sort(Comparator.comparingInt((String screen) -> patterns.get(screen).size())
                .thenComparingInt(screen -> spots(counts.get(screen), kinds.length)).reversed()
                .thenComparing(Comparator.naturalOrder()));
        for (String screen : ranked) {
            int[] count = counts.get(screen);
            int documents = 0;
            for (int value : DOCUMENTS.get(screen)) documents += value;
            out.append("| `").append(screen).append("` | ").append(spots(count, kinds.length)).append(" | ")
                    .append(patterns.get(screen).size()).append(" |");
            for (int value : count) out.append(' ').append(value).append(" |");
            out.append(' ').append(documents).append(" | ").append(String.join(", ", SUITES.get(screen))).append(" |\n");
        }
        out.append("| **total** | **").append(rows.size()).append("** | **").append(patternTotal).append("** |");
        for (int value : totals) out.append(" **").append(value).append("** |");
        out.append(" | |\n\n## Marked frames\n\nThe 1920x1080 frame with the most findings per screen, under `marked/`:")
                .append(" red clipped text, orange unwrapped text too wide, magenta clip spill, cyan hidden gap slot, grey tight text.\n\n");
        for (var entry : MARKED.entrySet()) {
            out.append("- `").append(entry.getKey()).append("`: `marked/").append(fileName(entry.getKey()))
                    .append(".png`, ").append(entry.getValue()).append(" faults (tight text not counted), from `")
                    .append(MARKED_SUITE.get(entry.getKey())).append("`\n");
        }
        out.append("\n## Worst 80 by overflow\n\n| screen | element | kind |");
        for (String column : COLUMNS) out.append(' ').append(column).append(" |");
        out.append("\n|---|---|---|").append("---:|".repeat(COLUMNS.size())).append('\n');
        Set<String> shown = new TreeSet<>();
        int listed = 0;
        for (Row row : rows) {
            // One line per pattern, so twelve resource rows spilling the same strip read as one finding.
            if (listed == 80 || !shown.add(row.screen() + row.kind() + pattern(row.element()))) continue;
            listed++;
            out.append("| `").append(row.screen()).append("` | `").append(row.element().replace("|", "\\|"))
                    .append("` | ").append(row.kind()).append(" |");
            for (float value : row.overflow()) out.append(' ').append(value > 0f ? px(value) : "").append(" |");
            out.append('\n');
        }
        return out.toString();
    }

    private static Kind kindOf(String label) {
        for (Kind kind : Kind.values()) {
            if (kind.label().equals(label)) return kind;
        }
        throw new IllegalArgumentException(label);
    }

    private static int spots(int[] count, int kinds) {
        int total = 0;
        for (int index = count.length - kinds; index < count.length; index++) total += count[index];
        return total;
    }

    private static String px(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private record Row(String screen, String element, String kind, float[] overflow) {
        float worst() {
            float worst = 0f;
            for (float value : overflow) worst = Math.max(worst, value);
            return worst;
        }
    }
}
