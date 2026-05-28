package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
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
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Developer-only screen for verifying that each {@link UnitType}'s sprite
 * sheet slices cleanly. Loads every type's PNG, runs {@link SpriteSheetSlicer}
 * over it, and renders each sheet in a 2-column grid with the detected frame
 * bounding boxes outlined in green plus the frame index labeled inside each
 * box. A failing slice (missing frames, merged frames, off-by-one boxes) is
 * obvious at a glance instead of buried in {@code starsector.log}.
 *
 * <p>Empty / failed sheets are still listed so missing assets read as "0
 * frames" rather than disappearing from the grid.
 */
public class UnitSliceDebugScreen implements Screen {

    private static final Logger LOG = Global.getLogger(UnitSliceDebugScreen.class);

    private static final int ALPHA_THRESHOLD = 16;

    private static final float PAD            = 12f;
    private static final float HEADER_H       = 36f;
    private static final float BACK_W         = 120f;
    private static final float BACK_H         = 32f;
    private static final float COLS           = 2f;
    private static final float ROW_GAP        = 10f;
    private static final float COL_GAP        = 16f;
    private static final float CARD_LABEL_H   = 20f;

    private static final Color HEADER_COLOR    = new Color(0xC8, 0xE0, 0xFF);
    private static final Color CARD_BG         = new Color(0x10, 0x18, 0x24);
    private static final Color CARD_BORDER     = new Color(0x4A, 0x6B, 0x8C);
    private static final Color FRAME_OK        = new Color(0x50, 0xE0, 0x80);
    private static final Color FRAME_WARN      = new Color(0xE0, 0x80, 0x40);
    private static final Color LABEL_TEXT      = new Color(0xFF, 0xE0, 0x70);
    private static final Color LABEL_SHADOW    = new Color(0x00, 0x00, 0x00);

    /** Frame count we expect from every unit sheet (W/N/E/S idle + W/E/N weapon-up). A mismatch warns in red. */
    private static final int EXPECTED_FRAMES   = 7;

    private final WidgetRoot widgets = new WidgetRoot();
    private final EnumMap<UnitType, LoadedSheet> loaded = new EnumMap<>(UnitType.class);
    private boolean loadAttempted;

    private PositionAPI position;
    private MarineOpsContext ctx;

    /** Per-type load result. {@code frames == null} signals a load or slice failure — card still renders with "FAILED". */
    private static final class LoadedSheet {
        final SpriteAPI sprite;
        final SpriteSheetFrames frames;
        final int pxW;
        final int pxH;
        final String errorMessage;

        LoadedSheet(SpriteAPI sprite, SpriteSheetFrames frames, int pxW, int pxH, String errorMessage) {
            this.sprite = sprite;
            this.frames = frames;
            this.pxW = pxW;
            this.pxH = pxH;
            this.errorMessage = errorMessage;
        }
    }

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        ensureSheets();
        rebuild();
    }

    /**
     * Loads each {@link UnitType} sheet via the same lazy-load + auto-slice
     * pattern {@code BattleScreen} uses, but stores per-type results in this
     * screen's own cache so a failed load in either place doesn't poison the
     * other. Per-type errors are kept as strings so the card can show them.
     */
    private void ensureSheets() {
        if (loadAttempted) return;
        loadAttempted = true;
        for (UnitType type : UnitType.values()) {
            loaded.put(type, loadSheet(type));
        }
    }

    private LoadedSheet loadSheet(UnitType type) {
        String path = type.spritePath;
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                return new LoadedSheet(null, null, 0, 0, "getSprite returned null");
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(path)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    return new LoadedSheet(sprite, null, 0, 0, "ImageIO returned null");
                }
                SpriteSheetFrames frames = SpriteSheetSlicer.slice(img);
                LOG.info("UnitSliceDebugScreen: " + type + " — " + frames.frames.length
                        + " frames, sheet " + img.getWidth() + "x" + img.getHeight());
                return new LoadedSheet(sprite, frames, img.getWidth(), img.getHeight(), null);
            }
        } catch (Exception e) {
            LOG.error("UnitSliceDebugScreen: failed to load " + path, e);
            return new LoadedSheet(null, null, 0, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        float backX = position.getX() + PAD;
        float backY = position.getY() + PAD;
        widgets.add(new ButtonWidget(backX, backY, BACK_W, BACK_H,
                () -> ctx.goTo(ScreenId.MISSION_SELECT)));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("actionBack"),
                backX + 12f, backY + BACK_H - 6f, HEADER_COLOR));

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Unit Slice Debug — " + UnitType.values().length + " sheets",
                position.getX() + PAD,
                position.getY() + position.getHeight() - PAD - 6f,
                HEADER_COLOR));
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;
        renderGrid(alphaMult);
        widgets.render(alphaMult);
    }

    private void renderGrid(float alphaMult) {
        float viewportX = position.getX() + PAD;
        float viewportY = position.getY() + PAD + BACK_H + PAD;
        float viewportW = position.getWidth()  - 2 * PAD;
        float viewportH = position.getHeight() - 2 * PAD - BACK_H - PAD - HEADER_H;

        UnitType[] types = UnitType.values();
        int cols = (int) COLS;
        int rows = (types.length + cols - 1) / cols;

        float cardW = (viewportW - COL_GAP * (cols - 1)) / cols;
        float cardH = (viewportH - ROW_GAP * (rows - 1)) / rows;

        for (int idx = 0; idx < types.length; idx++) {
            int col = idx % cols;
            int row = idx / cols;
            float cardX = viewportX + col * (cardW + COL_GAP);
            // Stack top-down so MARINE is at the top — GL Y goes up, so start
            // from the viewport top and walk down.
            float cardY = viewportY + viewportH - (row + 1) * cardH - row * ROW_GAP;
            renderCard(types[idx], cardX, cardY, cardW, cardH, alphaMult);
        }
    }

    /**
     * Draws one type's card: bordered background, header label with type +
     * frame count + sheet pixel dims, the sheet itself scaled to fit, and
     * green outlines around each detected frame with the frame index in the
     * corner. Falls back to a red "FAILED" overlay when the load returned
     * an error or zero frames.
     */
    private void renderCard(UnitType type, float x, float y, float w, float h, float alphaMult) {
        LoadedSheet ls = loaded.get(type);

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // Background.
        glBegin(GL_QUADS);
        glColor4f(CARD_BG.getRed() / 255f, CARD_BG.getGreen() / 255f, CARD_BG.getBlue() / 255f, alphaMult);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
        // Border.
        glColor4f(CARD_BORDER.getRed() / 255f, CARD_BORDER.getGreen() / 255f, CARD_BORDER.getBlue() / 255f, alphaMult);
        glLineWidth(1.5f);
        glBegin(GL_LINES);
        glVertex2f(x,     y);     glVertex2f(x + w, y);
        glVertex2f(x + w, y);     glVertex2f(x + w, y + h);
        glVertex2f(x + w, y + h); glVertex2f(x,     y + h);
        glVertex2f(x,     y + h); glVertex2f(x,     y);
        glEnd();

        int frameCount = (ls != null && ls.frames != null) ? ls.frames.frames.length : 0;
        boolean ok = ls != null && ls.frames != null && frameCount == EXPECTED_FRAMES;
        String header = type.name() + " — " + frameCount + " frames"
                + (ls != null && ls.pxW > 0 ? "  (" + ls.pxW + "×" + ls.pxH + "px)" : "")
                + (ok ? "" : "  ⚠");
        Color headerColor = ok ? HEADER_COLOR : FRAME_WARN;
        float labelX = x + 6f;
        float labelY = y + h - 6f;
        Fonts.ORBITRON_20.drawString(header, labelX, labelY, headerColor, alphaMult);

        if (ls == null || ls.sprite == null || ls.frames == null || ls.pxW == 0) {
            String err = ls != null && ls.errorMessage != null ? ls.errorMessage : "no data";
            Fonts.ORBITRON_20.drawString("FAILED: " + err,
                    x + 6f, y + h / 2f, FRAME_WARN, alphaMult);
            return;
        }

        // Sheet image scaled to fit the card's remaining vertical space.
        float imgAreaX = x + 6f;
        float imgAreaY = y + 6f;
        float imgAreaW = w - 12f;
        float imgAreaH = h - CARD_LABEL_H - 12f;
        float scale = Math.min(imgAreaW / ls.pxW, imgAreaH / ls.pxH);
        if (scale <= 0f) return;
        float drawW = ls.pxW * scale;
        float drawH = ls.pxH * scale;
        float drawX = imgAreaX + (imgAreaW - drawW) / 2f;
        float drawY = imgAreaY + (imgAreaH - drawH) / 2f;

        SpriteAPI sprite = ls.sprite;
        sprite.setTexX(0f);
        sprite.setTexY(0f);
        sprite.setTexWidth(sprite.getTextureWidth());
        sprite.setTexHeight(sprite.getTextureHeight());
        sprite.setSize(drawW, drawH);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.setColor(Color.WHITE);
        sprite.renderAtCenter(drawX + drawW / 2f, drawY + drawH / 2f);

        // Frame outlines + index labels.
        Color frameColor = ok ? FRAME_OK : FRAME_WARN;
        glDisable(GL_TEXTURE_2D);
        glColor4f(frameColor.getRed() / 255f, frameColor.getGreen() / 255f,
                frameColor.getBlue() / 255f, alphaMult);
        glLineWidth(1.2f);
        glBegin(GL_LINES);
        for (int i = 0; i < ls.frames.frames.length; i++) {
            SpriteSheetFrames.Frame f = ls.frames.frames[i];
            float fx = drawX + f.x * scale;
            // Sheet's y origin is top-left; GL's is bottom-left. Convert.
            float fy = drawY + (ls.pxH - (f.y + f.h)) * scale;
            float fw = f.w * scale;
            float fh = f.h * scale;
            glVertex2f(fx,      fy);      glVertex2f(fx + fw, fy);
            glVertex2f(fx + fw, fy);      glVertex2f(fx + fw, fy + fh);
            glVertex2f(fx + fw, fy + fh); glVertex2f(fx,      fy + fh);
            glVertex2f(fx,      fy + fh); glVertex2f(fx,      fy);
        }
        glEnd();

        for (int i = 0; i < ls.frames.frames.length; i++) {
            SpriteSheetFrames.Frame f = ls.frames.frames[i];
            float fx = drawX + f.x * scale;
            float fy = drawY + (ls.pxH - (f.y + f.h)) * scale;
            float fh = f.h * scale;
            String idx = Integer.toString(i);
            float tx = fx + 2f;
            float ty = fy + fh - 4f;
            Fonts.ORBITRON_20.drawString(idx, tx + 1f, ty - 1f, LABEL_SHADOW, alphaMult);
            Fonts.ORBITRON_20.drawString(idx, tx,        ty,        LABEL_TEXT,   alphaMult);
        }
    }
}
