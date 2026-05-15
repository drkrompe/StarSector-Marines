package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.function.Consumer;

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
 * One row in the client list. Renders its own background (hover/selected/locked
 * states all collapse to bg color choice), faction crest icon on the left, name
 * + rep status text in the body. Selection is sticky: clicking calls the
 * onSelect callback which the panel uses to update its "currently selected"
 * state across all rows.
 *
 * <p>Locked rows render dimmed and consume nothing; the rep text shows the
 * lock reason instead of the rep level when locked.
 */
public class ClientRowWidget extends BaseWidget {

    private static final Logger LOG = Global.getLogger(ClientRowWidget.class);

    private static final Color BG_DEFAULT   = new Color(0x18, 0x22, 0x2E);
    private static final Color BG_HOVER     = new Color(0x24, 0x36, 0x4C);
    private static final Color BG_SELECTED  = new Color(0x2C, 0x48, 0x66);
    private static final Color BORDER       = new Color(0x4A, 0x6B, 0x8C);
    private static final Color ACCENT_LINE  = new Color(0xC8, 0xE0, 0xFF);
    private static final Color NAME_COLOR   = new Color(0xE0, 0xE8, 0xF4);
    private static final Color NAME_LOCKED  = new Color(0x70, 0x80, 0x90);

    private static final float ICON_SIZE = 32f;
    private static final float ICON_PAD  = 8f;

    public final Client client;
    private final Consumer<Client> onSelect;
    private final java.util.function.Supplier<Client> selectedSupplier;

    private SpriteAPI crest;
    private boolean crestAttempted;
    private boolean hovered;
    private boolean armed;

    public ClientRowWidget(Client client,
                           float x, float y, float w, float h,
                           java.util.function.Supplier<Client> selectedSupplier,
                           Consumer<Client> onSelect) {
        this.client = client;
        this.selectedSupplier = selectedSupplier;
        this.onSelect = onSelect;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public void onMouseMove(int px, int py) {
        if (client.locked) {
            hovered = false;
            return;
        }
        hovered = contains(px, py);
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        if (client.locked) return false;
        armed = true;
        return true;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        if (client.locked) return false;
        boolean wasArmed = armed;
        armed = false;
        if (wasArmed && contains(px, py)) {
            if (onSelect != null) onSelect.accept(client);
            return true;
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        boolean selected = selectedSupplier != null && selectedSupplier.get() == client;

        // Locked rows render at lower opacity overall.
        float a = (client.locked ? 0.55f : 1.0f) * alphaMult;

        Color bg = selected ? BG_SELECTED : (hovered || armed ? BG_HOVER : BG_DEFAULT);
        fillRect(x, y, w, h, bg, 0.85f * a);
        strokeRect(x, y, w, h, BORDER, 0.7f * a);

        if (selected) {
            // Bright left-edge accent — reads as "this is the active client"
            fillRect(x, y, 3f, h, ACCENT_LINE, 0.95f * a);
        }

        // Faction crest, left side
        ensureCrest();
        if (crest != null) {
            float ix = x + ICON_PAD;
            float iy = y + (h - ICON_SIZE) * 0.5f;
            crest.setSize(ICON_SIZE, ICON_SIZE);
            crest.setAlphaMult(a);
            crest.setNormalBlend();
            crest.render(ix, iy);
        }

        // Faction name + rep line, body
        float textX = x + ICON_PAD + ICON_SIZE + 10f;
        float nameY = y + h - 6f;
        Color nameColor = client.locked ? NAME_LOCKED : NAME_COLOR;
        Fonts.ORBITRON_20.drawString(client.displayName, textX, nameY, nameColor, a);

        float repY = nameY - Fonts.ORBITRON_20.getLineHeight() - 4f;
        String repText;
        Color repColor;
        if (client.locked && client.lockReason != null) {
            repText = Strings.get(client.lockReason);
            repColor = NAME_LOCKED;
        } else {
            repText = client.repLevel != null ? client.repLevel.getDisplayName() : "";
            repColor = repColorFor(client.repLevel);
        }
        Fonts.ORBITRON_20.drawString(repText, textX, repY, repColor, a);
    }

    private void ensureCrest() {
        if (crestAttempted) return;
        crestAttempted = true;
        if (client.crestPath == null || client.crestPath.isEmpty()) return;
        try {
            Global.getSettings().loadTexture(client.crestPath);
            crest = Global.getSettings().getSprite(client.crestPath);
        } catch (Exception e) {
            LOG.warn("ClientRow: crest load failed for " + client.factionId + " (" + client.crestPath + ")");
        }
    }

    private static Color repColorFor(RepLevel level) {
        if (level == null) return NAME_COLOR;
        switch (level) {
            case VENGEFUL:
            case HOSTILE:
            case INHOSPITABLE:
                return new Color(0xE0, 0x70, 0x70);
            case SUSPICIOUS:
                return new Color(0xE0, 0xB0, 0x70);
            case NEUTRAL:
                return new Color(0xA0, 0xA8, 0xB0);
            case FAVORABLE:
            case WELCOMING:
                return new Color(0x9C, 0xCC, 0x9C);
            case FRIENDLY:
            case COOPERATIVE:
                return new Color(0x70, 0xE0, 0x9C);
            default:
                return NAME_COLOR;
        }
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
