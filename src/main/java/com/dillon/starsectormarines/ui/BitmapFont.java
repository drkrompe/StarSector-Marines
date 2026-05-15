package com.dillon.starsectormarines.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * BMFont .fnt loader + immediate-mode GL renderer. Parses the plain-text
 * .fnt manifest, loads the page texture atlas via Starsector's
 * {@link SpriteAPI} (so it benefits from POT padding compensation), and
 * draws glyphs as textured quads with a color tint via the default
 * {@code GL_MODULATE} texture env.
 *
 * <p>Coordinate convention: caller passes the top-left of the text bounding
 * box in Starsector UI coords (Y-up). Glyph yoffset is interpreted in BMFont
 * convention (image-y going down) and flipped to GL-y at render time.
 *
 * <p>V mapping accounts for Starsector loading textures so V=0 sits at the
 * image bottom and V=textureHeightFraction sits at the image top — same
 * convention the planet billboard already relies on.
 */
public class BitmapFont {

    private static final Logger LOG = Global.getLogger(BitmapFont.class);

    public static class Glyph {
        public int x, y, w, h;
        public int xoffset, yoffset, xadvance;
    }

    private final String fntPath;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private SpriteAPI page;
    private int lineHeight;
    private int base;
    private int scaleW;
    private int scaleH;
    private boolean loaded;
    private boolean failed;

    public BitmapFont(String fntPath) {
        this.fntPath = fntPath;
    }

    /** Returns true if the font is ready to render after this call. */
    public boolean ensureLoaded() {
        if (loaded) return true;
        if (failed) return false;
        try {
            String text = Global.getSettings().loadText(fntPath);
            String pageRelative = parse(text);
            String dir = fntPath.substring(0, fntPath.lastIndexOf('/') + 1);
            String pagePath = dir + pageRelative;
            Global.getSettings().loadTexture(pagePath);
            page = Global.getSettings().getSprite(pagePath);
            if (page == null) {
                LOG.error("BitmapFont: page sprite missing for " + fntPath + " (" + pagePath + ")");
                failed = true;
                return false;
            }
            loaded = true;
            LOG.info("BitmapFont: loaded " + fntPath + " (" + glyphs.size()
                    + " glyphs, lineHeight=" + lineHeight + ", base=" + base + ")");
            return true;
        } catch (Exception e) {
            LOG.error("BitmapFont: failed to load " + fntPath, e);
            failed = true;
            return false;
        }
    }

    /** Top-edge y in screen GL coords; renders left-to-right from (x, y). */
    public void drawString(String s, float x, float y, Color color, float alphaMult) {
        if (!ensureLoaded()) return;
        if (s == null || s.isEmpty()) return;

        beginTextDraw(color, alphaMult);
        glBegin(GL_QUADS);
        emitLineQuads(s, x, y);
        glEnd();
        endTextDraw();
    }

    /**
     * Wraps {@code s} to {@code maxWidth} and draws starting with the top of the first line
     * at (x, y) — same coord convention as {@link #drawString}. Wraps at whitespace; falls
     * back to char-break for words longer than maxWidth. {@code '\n'} is a hard break.
     * Returns total height consumed (line count * lineHeight).
     */
    public float drawStringWrapped(String s, float x, float y, float maxWidth, Color color, float alphaMult) {
        if (!ensureLoaded()) return 0f;
        if (s == null || s.isEmpty()) return 0f;

        List<String> lines = wrapLines(s, maxWidth);
        if (lines.isEmpty()) return 0f;

        beginTextDraw(color, alphaMult);
        // Single glBegin/glEnd across every glyph on every line — cheaper than per-line.
        glBegin(GL_QUADS);
        for (int i = 0; i < lines.size(); i++) {
            emitLineQuads(lines.get(i), x, y - lineHeight * i);
        }
        glEnd();
        endTextDraw();

        return lines.size() * (float) lineHeight;
    }

    /** Preflight: returns the height {@link #drawStringWrapped} would consume, without drawing. */
    public float measureWrappedHeight(String s, float maxWidth) {
        if (!ensureLoaded() || s == null || s.isEmpty()) return 0f;
        return wrapLines(s, maxWidth).size() * (float) lineHeight;
    }

    /** Helper that returns the wrapped lines (exposed in case callers need finer control). */
    public List<String> wrapLines(String s, float maxWidth) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        if (!ensureLoaded()) {
            out.add(s);
            return out;
        }

        // maxWidth <= 0 means no width-based wrapping; still honor hard breaks.
        boolean noWrap = maxWidth <= 0f;

        // Split into hard-break paragraphs first; each gets word-wrapped independently.
        int n = s.length();
        int paraStart = 0;
        for (int i = 0; i <= n; i++) {
            if (i == n || s.charAt(i) == '\n') {
                String paragraph = s.substring(paraStart, i);
                if (noWrap) {
                    out.add(paragraph);
                } else {
                    wrapParagraph(paragraph, maxWidth, out);
                }
                paraStart = i + 1;
            }
        }
        return out;
    }

    public float measureWidth(String s) {
        if (!ensureLoaded() || s == null) return 0f;
        float w = 0f;
        for (int i = 0; i < s.length(); i++) {
            Glyph g = glyphs.get((int) s.charAt(i));
            if (g != null) w += g.xadvance;
        }
        return w;
    }

    public int getLineHeight() {
        return lineHeight;
    }

    // ---- internal helpers -------------------------------------------------

    /** Sets shader / blend / color / texture binding shared by all text draws. */
    private void beginTextDraw(Color color, float alphaMult) {
        glUseProgram(0);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        page.bindTexture();
        glColor4f(
                color.getRed()   / 255f,
                color.getGreen() / 255f,
                color.getBlue()  / 255f,
                color.getAlpha() / 255f * alphaMult);
    }

    private void endTextDraw() {
        glDisable(GL_TEXTURE_2D);
    }

    /** Emits textured quads for one line. Caller must wrap with glBegin/glEnd(GL_QUADS). */
    private void emitLineQuads(String s, float x, float y) {
        if (s == null || s.isEmpty()) return;
        float texU = page.getTextureWidth();
        float texV = page.getTextureHeight();

        float cursorX = x;
        for (int i = 0; i < s.length(); i++) {
            Glyph g = glyphs.get((int) s.charAt(i));
            if (g == null) continue;
            if (g.w > 0 && g.h > 0) {
                float qx0 = cursorX + g.xoffset;
                float qy0 = y - g.yoffset;
                float qx1 = qx0 + g.w;
                float qy1 = qy0 - g.h;

                float u0 = ((float) g.x         / scaleW) * texU;
                float u1 = ((float)(g.x + g.w)  / scaleW) * texU;
                float v0 = texV - ((float)  g.y          / scaleH) * texV;
                float v1 = texV - ((float)( g.y + g.h)   / scaleH) * texV;

                glTexCoord2f(u0, v0); glVertex2f(qx0, qy0);
                glTexCoord2f(u1, v0); glVertex2f(qx1, qy0);
                glTexCoord2f(u1, v1); glVertex2f(qx1, qy1);
                glTexCoord2f(u0, v1); glVertex2f(qx0, qy1);
            }
            cursorX += g.xadvance;
        }
    }

    /** Greedy word-wrap for one paragraph (no embedded '\n'). Appends one or more lines to out. */
    private void wrapParagraph(String paragraph, float maxWidth, List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }

        StringBuilder current = new StringBuilder();
        float currentWidth = 0f;
        boolean sawAnyWord = false;

        int n = paragraph.length();
        int i = 0;
        while (i < n) {
            // Skip a run of whitespace between words. A leading space on a new line is dropped.
            while (i < n && isWrapSpace(paragraph.charAt(i))) i++;
            if (i >= n) break;

            // Find end of this word (next whitespace or end-of-string).
            int wordEnd = i;
            while (wordEnd < n && !isWrapSpace(paragraph.charAt(wordEnd))) wordEnd++;
            String word = paragraph.substring(i, wordEnd);
            i = wordEnd;
            sawAnyWord = true;

            float wordWidth = measureWidth(word);
            float spaceWidth = current.length() == 0 ? 0f : measureWidth(" ");

            if (current.length() == 0) {
                // First word on the line: take it as-is, char-break only if it itself overflows.
                if (wordWidth <= maxWidth) {
                    current.append(word);
                    currentWidth = wordWidth;
                } else {
                    charBreakWord(word, maxWidth, out, current);
                    currentWidth = measureWidth(current.toString());
                }
            } else if (currentWidth + spaceWidth + wordWidth <= maxWidth) {
                current.append(' ').append(word);
                currentWidth += spaceWidth + wordWidth;
            } else {
                // Word doesn't fit on current line: flush and start fresh.
                out.add(current.toString());
                current.setLength(0);
                currentWidth = 0f;
                if (wordWidth <= maxWidth) {
                    current.append(word);
                    currentWidth = wordWidth;
                } else {
                    charBreakWord(word, maxWidth, out, current);
                    currentWidth = measureWidth(current.toString());
                }
            }
        }

        if (current.length() > 0) out.add(current.toString());
        else if (!sawAnyWord) out.add(""); // whitespace-only paragraph still consumes a line
    }

    /** Spaces/tabs separate words during wrapping. '\n' is consumed by the paragraph splitter. */
    private static boolean isWrapSpace(char c) {
        return c == ' ' || c == '\t';
    }

    /**
     * Splits a word that's longer than maxWidth into char-sized chunks. Fills the current
     * line first (if non-empty, it gets flushed), then keeps emitting full lines, leaving
     * any leftover tail in {@code current} for subsequent words to extend.
     */
    private void charBreakWord(String word, float maxWidth, List<String> out, StringBuilder current) {
        // Flush any existing content first so the broken word starts on a fresh line.
        if (current.length() > 0) {
            out.add(current.toString());
            current.setLength(0);
        }

        StringBuilder chunk = new StringBuilder();
        float chunkWidth = 0f;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Glyph g = glyphs.get((int) c);
            float adv = g == null ? 0f : g.xadvance;
            if (chunk.length() > 0 && chunkWidth + adv > maxWidth) {
                out.add(chunk.toString());
                chunk.setLength(0);
                chunkWidth = 0f;
            }
            chunk.append(c);
            chunkWidth += adv;
        }
        // Leave the trailing partial chunk in current so the next word can extend the line.
        current.append(chunk);
    }

    /** Returns the page file name from the manifest. */
    private String parse(String text) {
        String pageFile = "";
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int sp = line.indexOf(' ');
            if (sp < 0) continue;
            String tag = line.substring(0, sp);
            Map<String, String> kv = parseKeyValues(line.substring(sp + 1));
            switch (tag) {
                case "common":
                    lineHeight = Integer.parseInt(kv.getOrDefault("lineHeight", "0"));
                    base = Integer.parseInt(kv.getOrDefault("base", "0"));
                    scaleW = Integer.parseInt(kv.getOrDefault("scaleW", "1"));
                    scaleH = Integer.parseInt(kv.getOrDefault("scaleH", "1"));
                    break;
                case "page":
                    pageFile = stripQuotes(kv.getOrDefault("file", ""));
                    break;
                case "char":
                    Glyph g = new Glyph();
                    int id = Integer.parseInt(kv.getOrDefault("id", "-1"));
                    g.x        = Integer.parseInt(kv.getOrDefault("x",        "0"));
                    g.y        = Integer.parseInt(kv.getOrDefault("y",        "0"));
                    g.w        = Integer.parseInt(kv.getOrDefault("width",    "0"));
                    g.h        = Integer.parseInt(kv.getOrDefault("height",   "0"));
                    g.xoffset  = Integer.parseInt(kv.getOrDefault("xoffset",  "0"));
                    g.yoffset  = Integer.parseInt(kv.getOrDefault("yoffset",  "0"));
                    g.xadvance = Integer.parseInt(kv.getOrDefault("xadvance", "0"));
                    if (id >= 0) glyphs.put(id, g);
                    break;
                default:
                    // info / chars / kerning lines — ignored for now
                    break;
            }
        }
        return pageFile;
    }

    private static Map<String, String> parseKeyValues(String s) {
        Map<String, String> out = new HashMap<>();
        // Tokenize on whitespace BUT keep quoted values intact.
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            int eq = s.indexOf('=', i);
            if (eq < 0) break;
            String key = s.substring(i, eq);
            int valStart = eq + 1;
            int valEnd;
            if (valStart < n && s.charAt(valStart) == '"') {
                valStart++;
                valEnd = s.indexOf('"', valStart);
                if (valEnd < 0) valEnd = n;
                out.put(key, s.substring(valStart, valEnd));
                i = valEnd + 1;
            } else {
                valEnd = valStart;
                while (valEnd < n && !Character.isWhitespace(s.charAt(valEnd))) valEnd++;
                out.put(key, s.substring(valStart, valEnd));
                i = valEnd;
            }
        }
        return out;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
