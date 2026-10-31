package com.dillon.starsectormarines.assets.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bakes CJK AngelCode faces for a translation mod from the recipe at
 * {@code src/tool/resources/fonts/<locale>.json}. Each face starts from the vanilla {@code .fnt}
 * of the same name: its info, line metrics, Latin glyphs, kerning and page 0 (which still names
 * the game's own PNG, so vanilla art is never copied) stay as they are, and every code point the
 * recipe covers that vanilla lacks is rasterised from the recipe's font onto further pages of
 * vanilla's {@code scaleW x scaleH}, written as {@code <face>_zh_<n>.png} beside the new
 * {@code .fnt}. Pages store coverage in RGB and alpha alike, as the vanilla pages do.
 *
 * <p>Coverage is the GB2312 level-1 hanzi (rows 16-55), GB2312 symbol rows 1 and 3 (CJK
 * punctuation and full-width ASCII), the CJK Symbols and Punctuation block, and every non-ASCII
 * character the recipe's {@code coverageFrom} files use, so a translation's own rare characters
 * are never missing.
 *
 * <p>Usage: {@code java GenerateCjkFontsTask <rootDir> <recipe.json> <starsectorCore>}.
 * Output is regenerated, not tracked: it is deterministic for a given JDK and font file.
 */
public final class GenerateCjkFontsTask {

    private static final int SPACING = 1;
    private static final Pattern KEY_VALUE = Pattern.compile("(\\w+)=(\"[^\"]*\"|\\S+)");

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: GenerateCjkFontsTask <rootDir> <recipe.json> <starsectorCore>");
            System.exit(1);
        }
        Path root = Path.of(args[0]);
        JsonNode recipe = JsonMapper.builder().build().readTree(Files.readString(Path.of(args[1])));
        Path vanillaFonts = Path.of(args[2]).resolve("graphics/fonts");
        Path fontDir = root.resolve(recipe.get("fontDir").asText());
        Path output = root.resolve(recipe.get("output").asText());
        Files.createDirectories(output);

        TreeSet<Integer> coverage = baseCoverage();
        for (JsonNode file : recipe.path("coverageFrom")) {
            Files.readString(root.resolve(file.asText())).codePoints()
                    .filter(cp -> cp > 0x7F && !Character.isISOControl(cp)).forEach(coverage::add);
        }

        Map<String, Font> fonts = new HashMap<>();
        for (JsonNode face : recipe.get("faces")) {
            String name = face.get("fnt").asText();
            Font font = fonts.computeIfAbsent(face.get("font").asText(), file -> load(fontDir.resolve(file)))
                    .deriveFont((float) face.get("size").asInt());
            String vanilla = Files.readString(vanillaFonts.resolve(name + ".fnt"));
            bake(name, vanilla, font, coverage, output);
        }
    }

    /** GB2312 rows 1, 3 and 16-55 (level 1), plus U+3000-U+303F. */
    static TreeSet<Integer> baseCoverage() throws CharacterCodingException {
        CharsetDecoder decoder = Charset.forName("GB2312").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        TreeSet<Integer> out = new TreeSet<>();
        List<Integer> rows = new ArrayList<>(List.of(1, 3));
        for (int row = 16; row <= 55; row++) rows.add(row);
        for (int row : rows) {
            for (int cell = 1; cell <= 94; cell++) {
                byte[] bytes = {(byte) (0xA0 + row), (byte) (0xA0 + cell)};
                try {
                    out.add(decoder.decode(ByteBuffer.wrap(bytes)).toString().codePointAt(0));
                } catch (CharacterCodingException unassigned) {
                    // the code table has holes (row 55 ends at cell 89)
                }
            }
        }
        for (int cp = 0x3000; cp <= 0x303F; cp++) out.add(cp);
        return out;
    }

    private static Font load(Path file) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, file.toFile());
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Cannot load " + file, e);
        }
    }

    private record Raster(int codePoint, BufferedImage ink, int xoffset, int yoffset, int xadvance) {}

    private static void bake(String name, String vanilla, Font font, TreeSet<Integer> coverage, Path output)
            throws IOException {
        List<String> info = new ArrayList<>(), pages = new ArrayList<>(), chars = new ArrayList<>(), rest = new ArrayList<>();
        String common = null;
        TreeSet<Integer> vanillaChars = new TreeSet<>();
        for (String line : vanilla.split("\\r?\\n")) {
            String tag = line.strip().split("\\s+", 2)[0];
            switch (tag) {
                case "info" -> info.add(line);
                case "common" -> common = line;
                case "page" -> pages.add(line);
                case "chars" -> { }
                case "char" -> {
                    chars.add(line);
                    vanillaChars.add(Integer.parseInt(values(line).get("id")));
                }
                default -> { if (!line.isBlank()) rest.add(line); }
            }
        }
        if (common == null || pages.size() != 1) {
            throw new IllegalStateException(name + ": expected a single-page vanilla face");
        }
        Map<String, String> metrics = values(common);
        int scaleW = Integer.parseInt(metrics.get("scaleW"));
        int scaleH = Integer.parseInt(metrics.get("scaleH"));
        int base = Integer.parseInt(metrics.get("base"));

        List<Raster> rasters = new ArrayList<>();
        int missing = 0;
        for (int cp : coverage) {
            if (vanillaChars.contains(cp)) continue;
            if (font.canDisplayUpTo(new String(Character.toChars(cp))) != -1) { missing++; continue; }
            rasters.add(rasterise(font, cp, base));
        }

        // Shelf-pack in code point order onto pages 1..n; page 0 stays vanilla's.
        List<BufferedImage> atlases = new ArrayList<>();
        BufferedImage atlas = null;
        int x = 0, y = 0, shelf = 0;
        for (Raster r : rasters) {
            int w = r.ink() == null ? 0 : r.ink().getWidth();
            int h = r.ink() == null ? 0 : r.ink().getHeight();
            if (atlas != null && x + w > scaleW) { x = 0; y += shelf + SPACING; shelf = 0; }
            if (atlas == null || y + h > scaleH) {
                atlas = new BufferedImage(scaleW, scaleH, BufferedImage.TYPE_INT_ARGB);
                atlases.add(atlas);
                x = 0; y = 0; shelf = 0;
            }
            if (r.ink() != null) atlas.setRGB(x, y, w, h, r.ink().getRGB(0, 0, w, h, null, 0, w), 0, w);
            chars.add(String.format("char id=%-5d x=%-5d y=%-5d width=%-5d height=%-5d xoffset=%-5d yoffset=%-5d xadvance=%-5d page=%d  chnl=15",
                    r.codePoint(), x, y, w, h, r.xoffset(), r.yoffset(), r.xadvance(), atlases.size()));
            x += w + SPACING;
            shelf = Math.max(shelf, h);
        }

        StringBuilder fnt = new StringBuilder();
        info.forEach(line -> fnt.append(line).append('\n'));
        fnt.append(common.replaceFirst("pages=\\d+", "pages=" + (atlases.size() + 1))).append('\n');
        fnt.append(pages.get(0)).append('\n');
        try (var stale = Files.list(output)) {
            for (Path old : stale.filter(p -> p.getFileName().toString().startsWith(name + "_zh_")).toList()) {
                Files.delete(old);
            }
        }
        for (int i = 0; i < atlases.size(); i++) {
            String file = name + "_zh_" + (i + 1) + ".png";
            fnt.append("page id=").append(i + 1).append(" file=\"").append(file).append("\"\n");
            ImageIO.write(atlases.get(i), "png", output.resolve(file).toFile());
        }
        fnt.append("chars count=").append(chars.size()).append('\n');
        chars.forEach(line -> fnt.append(line).append('\n'));
        rest.forEach(line -> fnt.append(line).append('\n'));
        Files.writeString(output.resolve(name + ".fnt"), fnt.toString());
        System.out.printf("%s: %d vanilla + %d CJK glyphs on %d new page(s) of %dx%d; %d not in %s%n",
                name, vanillaChars.size(), rasters.size(), atlases.size(), scaleW, scaleH, missing, font.getFontName());
    }

    /** One glyph drawn white at {@code base} below the line top, cropped to its ink. */
    private static Raster rasterise(Font font, int cp, int base) {
        int em = font.getSize();
        int pad = em;
        BufferedImage scratch = new BufferedImage(em * 3, em * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setColor(Color.WHITE);
        FontRenderContext context = g.getFontRenderContext();
        GlyphVector vector = font.createGlyphVector(context, new String(Character.toChars(cp)));
        int baseline = pad + base;
        g.drawGlyphVector(vector, pad, baseline);
        g.dispose();
        int advance = Math.round(vector.getGlyphMetrics(0).getAdvanceX());

        int left = scratch.getWidth(), top = scratch.getHeight(), right = -1, bottom = -1;
        for (int py = 0; py < scratch.getHeight(); py++) {
            for (int px = 0; px < scratch.getWidth(); px++) {
                if ((scratch.getRGB(px, py) >>> 24) == 0) continue;
                left = Math.min(left, px); right = Math.max(right, px);
                top = Math.min(top, py); bottom = Math.max(bottom, py);
            }
        }
        if (right < 0) return new Raster(cp, null, 0, 0, advance);
        BufferedImage ink = new BufferedImage(right - left + 1, bottom - top + 1, BufferedImage.TYPE_INT_ARGB);
        for (int py = top; py <= bottom; py++) {
            for (int px = left; px <= right; px++) {
                int c = scratch.getRGB(px, py) >>> 24;
                ink.setRGB(px - left, py - top, c << 24 | c << 16 | c << 8 | c);
            }
        }
        return new Raster(cp, ink, left - pad, top - pad, advance);
    }

    private static Map<String, String> values(String line) {
        Map<String, String> out = new HashMap<>();
        Matcher m = KEY_VALUE.matcher(line);
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }
}
