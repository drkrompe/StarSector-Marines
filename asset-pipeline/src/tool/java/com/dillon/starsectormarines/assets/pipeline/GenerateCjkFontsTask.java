package com.dillon.starsectormarines.assets.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
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
 * of the same name: its info, line metrics, Latin glyphs and kerning stay as they are, and every
 * code point the recipe covers that vanilla lacks is rasterised from the recipe's font. The game
 * loads one page per face, so vanilla's page 0 is copied to the origin of a single power-of-two
 * atlas, {@code <face>_zh.png} beside the new {@code .fnt}, with the CJK glyphs packed below it
 * (the translation runs only on the base game, whose font art it carries). The atlas stores
 * coverage in RGB and alpha alike, as the vanilla pages do; {@link #checkLoadable} holds the
 * output to the line order the game's loader reads.
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
    private static final int MAX_ATLAS = 4096;
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
            bake(name, vanilla, vanillaFonts, font, coverage, output);
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

    private static void bake(String name, String vanilla, Path vanillaFonts, Font font, TreeSet<Integer> coverage,
                             Path output)
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

        // The game reads exactly one page, so vanilla's page sits at the atlas origin (its glyphs
        // keep their x/y) and the CJK glyphs shelf-pack below it in code point order. The atlas
        // is the narrowest power-of-two square-or-wider that holds them all.
        BufferedImage vanillaPage = ImageIO.read(vanillaFonts.resolve(values(pages.get(0)).get("file").replace("\"", "")).toFile());
        if (vanillaPage.getWidth() != scaleW || vanillaPage.getHeight() != scaleH) {
            throw new IllegalStateException(name + ": page 0 is not scaleW x scaleH");
        }
        int width = Math.max(scaleW, 64), height;
        List<int[]> placements;
        while (true) {
            placements = new ArrayList<>();
            int x = 0, y = scaleH + SPACING, shelf = 0;
            for (Raster r : rasters) {
                int w = r.ink() == null ? 0 : r.ink().getWidth();
                int h = r.ink() == null ? 0 : r.ink().getHeight();
                if (x + w > width) { x = 0; y += shelf + SPACING; shelf = 0; }
                placements.add(new int[] {x, y});
                x += w + SPACING;
                shelf = Math.max(shelf, h);
            }
            height = Math.max(Integer.highestOneBit(Math.max(y + shelf, scaleH) - 1) << 1, scaleH);
            if (height <= width) break;
            width <<= 1;
        }
        if (width > MAX_ATLAS) throw new IllegalStateException(name + ": atlas " + width + "x" + height + " exceeds " + MAX_ATLAS);

        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(vanillaPage, 0, 0, null);
        g.dispose();
        for (int i = 0; i < rasters.size(); i++) {
            Raster r = rasters.get(i);
            int x = placements.get(i)[0], y = placements.get(i)[1];
            int w = r.ink() == null ? 0 : r.ink().getWidth();
            int h = r.ink() == null ? 0 : r.ink().getHeight();
            if (r.ink() != null) atlas.setRGB(x, y, w, h, r.ink().getRGB(0, 0, w, h, null, 0, w), 0, w);
            chars.add(String.format("char id=%-5d x=%-5d y=%-5d width=%-5d height=%-5d xoffset=%-5d yoffset=%-5d xadvance=%-5d page=0  chnl=15",
                    r.codePoint(), x, y, w, h, r.xoffset(), r.yoffset(), r.xadvance()));
        }

        String file = name + "_zh.png";
        StringBuilder fnt = new StringBuilder();
        info.forEach(line -> fnt.append(line).append('\n'));
        fnt.append(common.replaceFirst("scaleW=\\d+", "scaleW=" + width)
                .replaceFirst("scaleH=\\d+", "scaleH=" + height)).append('\n');
        fnt.append("page id=0 file=\"").append(file).append("\"\n");
        fnt.append("chars count=").append(chars.size()).append('\n');
        chars.forEach(line -> fnt.append(line).append('\n'));
        rest.forEach(line -> fnt.append(line).append('\n'));
        checkLoadable(name, fnt.toString());

        try (var stale = Files.list(output)) {
            for (Path old : stale.filter(p -> p.getFileName().toString().startsWith(name + "_zh")).toList()) {
                Files.delete(old);
            }
        }
        ImageIO.write(atlas, "png", output.resolve(file).toFile());
        Files.writeString(output.resolve(name + ".fnt"), fnt.toString());
        System.out.printf("%s: %d vanilla + %d CJK glyphs on one %dx%d page; %d not in %s%n",
                name, vanillaChars.size(), rasters.size(), width, height, missing, font.getFontName());
    }

    /**
     * Fails unless {@code fnt} has the shape Starsector 0.98a's loader reads by position: info,
     * common, exactly one page, {@code chars count=N}, then N char lines. The loader ignores
     * {@code pages=} and reads a second page line as the char count, so a multi-page face throws
     * {@code NumberFormatException} at game launch.
     */
    static void checkLoadable(String name, String fnt) {
        String[] lines = fnt.split("\\r?\\n");
        String[] expected = {"info", "common", "page", "chars"};
        for (int i = 0; i < expected.length; i++) {
            if (lines.length <= i || !lines[i].startsWith(expected[i] + " ")) {
                throw new IllegalStateException(name + ": line " + (i + 1) + " must be '" + expected[i]
                        + "' (the game reads info, common, one page, chars in that order)");
            }
        }
        if (!"1".equals(values(lines[1]).get("pages")) || !"0".equals(values(lines[2]).get("id"))) {
            throw new IllegalStateException(name + ": the game reads exactly one page, id 0");
        }
        int count = Integer.parseInt(values(lines[3]).get("count"));
        for (int i = 4; i < 4 + count; i++) {
            if (lines.length <= i || !lines[i].startsWith("char ") || !"0".equals(values(lines[i]).get("page"))) {
                throw new IllegalStateException(name + ": line " + (i + 1) + " must be a page-0 char ("
                        + count + " declared)");
            }
        }
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
