package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.List;

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
 * Renders the detail popup for whichever {@link MissionNodeWidget} reports
 * itself as hovered. Always added <em>after</em> the node widgets in the
 * widget tree so the popup paints on top of every node, regardless of which
 * one is hovered.
 *
 * <p>Doesn't participate in hit-testing — {@link #contains} always returns
 * false. Pure render-only widget.
 */
public class MissionPopupOverlay extends BaseWidget {

    private static final Color BG          = new Color(0x12, 0x1B, 0x26);
    private static final Color BORDER      = new Color(0x88, 0xA8, 0xCC);
    private static final Color NAME_COLOR  = new Color(0xE0, 0xE8, 0xF4);
    private static final Color LABEL_COLOR = new Color(0x88, 0xA8, 0xCC);
    private static final Color VALUE_COLOR = new Color(0xE0, 0xE8, 0xF4);

    private static final float POPUP_W      = 320f;
    private static final float POPUP_H      = 168f;
    private static final float POPUP_PAD    = 12f;
    private static final float ROW_GAP      = 4f;
    private static final float NODE_GAP     = 8f;

    private final List<MissionNodeWidget> nodes;
    private final float clampMinX;
    private final float clampMaxX;
    private final float clampMinY;
    private final float clampMaxY;

    public MissionPopupOverlay(List<MissionNodeWidget> nodes,
                               float mapX, float mapY, float mapW, float mapH) {
        this.nodes = nodes;
        this.clampMinX = mapX;
        this.clampMaxX = mapX + mapW;
        this.clampMinY = mapY;
        this.clampMaxY = mapY + mapH;
    }

    @Override
    public boolean contains(int px, int py) {
        return false;
    }

    @Override
    public void render(float alphaMult) {
        MissionNodeWidget hovered = null;
        for (MissionNodeWidget n : nodes) {
            if (n.isHovered()) { hovered = n; break; }
        }
        if (hovered == null) return;

        // Default: popup above the node, clamped within the map area.
        float nodeCx = hovered.x + hovered.w * 0.5f;
        float nodeTop = hovered.y + hovered.h;
        float px = nodeCx - POPUP_W * 0.5f;
        float py = nodeTop + NODE_GAP;

        // If it'd overflow the top, flip to below the node.
        if (py + POPUP_H > clampMaxY) {
            py = hovered.y - NODE_GAP - POPUP_H;
        }
        // Clamp horizontally within map bounds.
        if (px < clampMinX) px = clampMinX;
        if (px + POPUP_W > clampMaxX) px = clampMaxX - POPUP_W;
        if (py < clampMinY) py = clampMinY;

        Mission m = hovered.mission;

        // Box
        fillRect(px, py, POPUP_W, POPUP_H, BG, 0.94f * alphaMult);
        strokeRect(px, py, POPUP_W, POPUP_H, BORDER, 0.9f * alphaMult);

        float lineH = Fonts.ORBITRON_20.getLineHeight() + ROW_GAP;
        float tx = px + POPUP_PAD;
        float ty = py + POPUP_H - POPUP_PAD;

        // Name (H3)
        Fonts.ORBITRON_20_BOLD.drawString(m.name, tx, ty, NAME_COLOR, alphaMult);
        ty -= Fonts.ORBITRON_20_BOLD.getLineHeight() + ROW_GAP + 2f;

        drawRow(tx, ty, Strings.get("missionPopupType"),
                Strings.get(m.type.displayKey), VALUE_COLOR, alphaMult);
        ty -= lineH;

        drawRow(tx, ty, Strings.get("missionPopupRisk"),
                Strings.get(m.risk.displayKey), m.risk.color, alphaMult);
        ty -= lineH;

        String payoutText = Strings.get("payoutFmt")
                .replace("{0}", String.format("%,d", m.payout));
        drawRow(tx, ty, Strings.get("missionPopupPayout"),
                payoutText, VALUE_COLOR, alphaMult);
        ty -= lineH;

        drawRow(tx, ty, Strings.get("missionPopupRequires"),
                m.requirements, VALUE_COLOR, alphaMult);
    }

    private static void drawRow(float x, float y, String label, String value,
                                Color valueColor, float alphaMult) {
        Fonts.ORBITRON_20.drawString(label, x, y, LABEL_COLOR, alphaMult);
        float labelW = Fonts.ORBITRON_20.measureWidth(label);
        Fonts.ORBITRON_20.drawString(value, x + labelW + 8f, y, valueColor, alphaMult);
    }

    private static void fillRect(float rx, float ry, float rw, float rh, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glBegin(GL_QUADS);
        glVertex2f(rx,       ry);
        glVertex2f(rx + rw,  ry);
        glVertex2f(rx + rw,  ry + rh);
        glVertex2f(rx,       ry + rh);
        glEnd();
    }

    private static void strokeRect(float rx, float ry, float rw, float rh, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(rx,       ry);
        glVertex2f(rx + rw,  ry);
        glVertex2f(rx + rw,  ry + rh);
        glVertex2f(rx,       ry + rh);
        glEnd();
    }
}
