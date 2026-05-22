package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.text.NumberFormat;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
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
 * Visual frame for an expanded dossier card. Renders the bg/border, title row,
 * metadata row, and the wrapped briefing prose. Functional controls (salvage
 * slider, transport toggles, captain rows, accept/decline buttons) are added
 * by {@link CommsConsolePanel} into the shared widget tree at known positions
 * inside the card rect — that keeps the widget container model flat.
 *
 * <p>Non-interactive ({@link #contains} returns {@code false}) so the
 * sub-widgets above it in z-order get every click without the card body
 * absorbing them.
 *
 * <p>{@link #BRIEFING_HEIGHT_AT_W} predicts the rendered height of the
 * briefing prose at a given wrap width — used by the panel to size the
 * card rect tall enough to fit everything.
 */
public class ExpandedCardWidget extends BaseWidget {

    private static final Color FRAME      = new Color(0x9C, 0xC0, 0xE0);
    private static final Color BG         = new Color(0x12, 0x1A, 0x24);
    private static final Color TITLE      = new Color(0xE0, 0xE8, 0xF4);
    private static final Color META_VALUE = new Color(0xC8, 0xD8, 0xE8);
    private static final Color FLAVOR     = new Color(0xC0, 0xD0, 0xE8);

    static final float PAD_X         = 12f;
    static final float PAD_Y         = 10f;
    static final float ROW_GAP       = 6f;
    static final float SECTION_GAP   = 12f;
    static final float GLYPH_BOX_W   = 24f;

    public final Mission mission;
    /** Wrap width used by {@link #render} and by {@link #measureBriefingHeight}. */
    private final float briefingWrapW;

    public ExpandedCardWidget(Mission mission,
                              float x, float y, float w, float h) {
        this.mission = mission;
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.briefingWrapW = w - 2 * PAD_X;
    }

    /**
     * Pre-computes the briefing block height for the given wrap width so the
     * panel can size the card rect without instantiating one first.
     */
    public static float measureBriefingHeight(String text, float wrapWidth) {
        if (text == null || text.isEmpty()) return 0f;
        return Fonts.ORBITRON_20.measureWrappedHeight(text, wrapWidth);
    }

    /** Height of the top header block (title + metadata + briefing prose). */
    public float headerBlockHeight() {
        float titleH = Fonts.ORBITRON_20_BOLD.getLineHeight();
        float metaH  = Fonts.ORBITRON_20.getLineHeight();
        float flavorH = measureBriefingHeight(mission.flavor, briefingWrapW);
        return PAD_Y + titleH + ROW_GAP + metaH + SECTION_GAP + flavorH;
    }

    /** Y at the bottom of the header block — sub-widgets stack below this. */
    public float subWidgetTopY() {
        return y + h - headerBlockHeight() - SECTION_GAP;
    }

    @Override
    public boolean contains(int px, int py) {
        // Non-interactive — sub-widgets in z-order above this handle clicks.
        return false;
    }

    @Override
    public void render(float alphaMult) {
        fillRect(x, y, w, h, BG, 0.93f * alphaMult);
        strokeRect(x, y, w, h, FRAME, 1.0f * alphaMult);

        // Title row — type glyph + mission name.
        float topY = y + h - PAD_Y - Fonts.ORBITRON_20_BOLD.getLineHeight();
        float glyphBoxX = x + PAD_X;
        String glyph = String.valueOf(mission.type.glyph);
        float glyphW = Fonts.ORBITRON_20_BOLD.measureWidth(glyph);
        Fonts.ORBITRON_20_BOLD.drawString(glyph,
                glyphBoxX + (GLYPH_BOX_W - glyphW) * 0.5f,
                topY + Fonts.ORBITRON_20_BOLD.getLineHeight(),
                mission.type.color, alphaMult);
        Fonts.ORBITRON_20_BOLD.drawString(mission.name,
                glyphBoxX + GLYPH_BOX_W + 6f,
                topY + Fonts.ORBITRON_20_BOLD.getLineHeight(),
                TITLE, alphaMult);

        // Metadata row — target · payout · salvage.
        float metaY = topY - ROW_GAP - Fonts.ORBITRON_20.getLineHeight();
        Fonts.ORBITRON_20.drawString(buildMetaLine(),
                x + PAD_X, metaY + Fonts.ORBITRON_20.getLineHeight(),
                META_VALUE, alphaMult);

        // Briefing prose — wrapped paragraph from PatronBriefingFlavor →
        // BriefingComposer (officer dispatch in the comms-officer frame).
        float flavorTopY = metaY - SECTION_GAP;
        if (mission.flavor != null && !mission.flavor.isEmpty()) {
            Fonts.ORBITRON_20.drawStringWrapped(mission.flavor,
                    x + PAD_X, flavorTopY,
                    briefingWrapW, FLAVOR, alphaMult);
        }
    }

    private String buildMetaLine() {
        StringBuilder sb = new StringBuilder();
        if (mission.targetPlanetName != null && !mission.targetPlanetName.isEmpty()) {
            sb.append(mission.targetPlanetName);
        }
        if (sb.length() > 0) sb.append("  ·  ");
        int cashMult = mission.cashMultiplier & 0xFF;
        if (cashMult <= 0) cashMult = 100;
        long effectivePayout = (long) mission.payout * cashMult / 100L;
        sb.append("$").append(NumberFormat.getIntegerInstance().format(effectivePayout));
        int salvage = mission.salvageNegotiated & 0xFF;
        if (salvage > 0) sb.append("  ·  ").append(salvage).append("% salvage");
        return sb.toString();
    }

    private static void fillRect(float x, float y, float w, float h, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }

    private static void strokeRect(float x, float y, float w, float h, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }
}
