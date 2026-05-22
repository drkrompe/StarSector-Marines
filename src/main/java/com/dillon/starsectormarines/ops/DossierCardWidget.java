package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;

import java.awt.Color;
import java.text.NumberFormat;
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
 * One dossier card in the comms-console stack — a single offered mission
 * surfaced as a clickable record with type glyph + name + target + payout +
 * salvage + days-left bar.
 *
 * <p>Click routes to {@link ScreenId#BRIEFING} via the supplied callback;
 * the screen owns the navigation decision. The days-left bar binds live to
 * {@link CampaignState#contractDaysLeft} so it reflects the current sector
 * day every render.
 *
 * <p>Per the discoverable-narrative principle the card doesn't display the
 * patron archetype as a label — that comes through in the briefing text
 * itself, which is the comms officer's read on the patron. See
 * {@code [[project_comms_officer_narrator]]} memory.
 *
 * <h2>Layout (top-down inside the card rect)</h2>
 * <pre>
 *   row 0 (title)        [GLYPH]  Mission Name
 *   row 1 (metadata)     Target  ·  $payout  ·  salvage %
 *   row 2 (days-left bar) [#####....] N days
 * </pre>
 */
public class DossierCardWidget extends BaseWidget {

    private static final Color FRAME      = new Color(0x4A, 0x6B, 0x8C);
    private static final Color FRAME_HOVR = new Color(0x9C, 0xC0, 0xE0);
    private static final Color BG         = new Color(0x10, 0x18, 0x22);
    private static final Color BG_HOVR    = new Color(0x18, 0x24, 0x30);
    private static final Color TITLE      = new Color(0xE0, 0xE8, 0xF4);
    private static final Color META_LABEL = new Color(0x88, 0xA8, 0xCC);
    private static final Color META_VALUE = new Color(0xC8, 0xD8, 0xE8);

    private static final Color BAR_TRACK    = new Color(0x22, 0x30, 0x40);
    private static final Color BAR_FILL     = new Color(0x70, 0xC0, 0xA0);
    private static final Color BAR_WARN     = new Color(0xE0, 0xB0, 0x70);
    private static final Color BAR_CRITICAL = new Color(0xE0, 0x70, 0x70);

    private static final int DAYS_WARN     = 4;
    private static final int DAYS_CRITICAL = 2;

    private static final float PAD_X        = 10f;
    private static final float PAD_Y        = 8f;
    private static final float ROW_GAP      = 4f;
    private static final float GLYPH_BOX_W  = 24f;
    private static final float BAR_H        = 6f;

    public final Mission mission;
    private final Consumer<Mission> onSelect;

    private boolean hovered;
    private boolean armed;

    public DossierCardWidget(Mission mission,
                             float x, float y, float w, float h,
                             Consumer<Mission> onSelect) {
        this.mission  = mission;
        this.onSelect = onSelect;
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    @Override
    public void onMouseMove(int px, int py) {
        hovered = contains(px, py);
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        armed = true;
        return true;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        boolean wasArmed = armed;
        armed = false;
        if (wasArmed && contains(px, py)) {
            if (onSelect != null) onSelect.accept(mission);
            return true;
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        fillRect(x, y, w, h, hovered ? BG_HOVR : BG, 0.92f * alphaMult);
        strokeRect(x, y, w, h, hovered ? FRAME_HOVR : FRAME, (hovered ? 1.0f : 0.85f) * alphaMult);

        // Title row — glyph in mission-type color, then mission name.
        float topY = y + h - PAD_Y - Fonts.ORBITRON_20_BOLD.getLineHeight();
        float glyphBoxX = x + PAD_X;
        renderGlyph(glyphBoxX, topY, mission, alphaMult);
        Fonts.ORBITRON_20_BOLD.drawString(
                mission.name,
                glyphBoxX + GLYPH_BOX_W + 6f,
                topY + Fonts.ORBITRON_20_BOLD.getLineHeight(),
                TITLE, alphaMult);

        // Metadata row — target · payout · salvage. Cheap inline composition
        // beats six labels; nothing here is hit-testable.
        float metaY = topY - ROW_GAP - Fonts.ORBITRON_20.getLineHeight();
        String meta = buildMetaLine();
        Fonts.ORBITRON_20.drawString(meta,
                x + PAD_X, metaY + Fonts.ORBITRON_20.getLineHeight(),
                META_VALUE, alphaMult);

        // Days-left bar — only rendered when there's an offer expiry to bind to.
        int currentDay = currentSectorDay();
        int daysLeft = lookupDaysLeft(mission.contractId, currentDay);
        if (daysLeft >= 0) {
            float barY = y + PAD_Y;
            renderDaysBar(x + PAD_X, barY, w - 2 * PAD_X, BAR_H,
                    daysLeft, mission.contractId, alphaMult);
        }
    }

    /** Type glyph rendered in the mission-type's color — visually matches map nodes. */
    private static void renderGlyph(float boxX, float boxY, Mission m, float alphaMult) {
        String glyph = String.valueOf(m.type.glyph);
        float glyphW = Fonts.ORBITRON_20_BOLD.measureWidth(glyph);
        float glyphX = boxX + (GLYPH_BOX_W - glyphW) * 0.5f;
        Fonts.ORBITRON_20_BOLD.drawString(glyph, glyphX,
                boxY + Fonts.ORBITRON_20_BOLD.getLineHeight(),
                m.type.color, alphaMult);
    }

    private String buildMetaLine() {
        StringBuilder sb = new StringBuilder();
        if (mission.targetPlanetName != null && !mission.targetPlanetName.isEmpty()) {
            sb.append(mission.targetPlanetName);
        }
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append("$").append(NumberFormat.getIntegerInstance().format(mission.payout));
        int salvage = mission.salvageNegotiated & 0xFF;
        if (salvage > 0) {
            sb.append("  ·  ").append(salvage).append("% salvage");
        }
        return sb.toString();
    }

    private void renderDaysBar(float bx, float by, float bw, float bh,
                               int daysLeft, long contractId, float alphaMult) {
        int max = lookupOfferWindow(contractId);
        float frac = max > 0 ? Math.max(0f, Math.min(1f, daysLeft / (float) max)) : 0f;

        Color fill = daysLeft <= DAYS_CRITICAL ? BAR_CRITICAL
                : daysLeft <= DAYS_WARN ? BAR_WARN
                : BAR_FILL;

        // Track
        fillRect(bx, by, bw, bh, BAR_TRACK, 0.9f * alphaMult);
        // Fill from the right side — visually "draining down to expiry"
        float fillW = bw * frac;
        fillRect(bx, by, fillW, bh, fill, 0.95f * alphaMult);

        // Inline label after the bar.
        String label = daysLeft + (daysLeft == 1 ? " day" : " days");
        float labelX = bx + bw + 8f;
        float labelY = by + bh + 1f;
        Fonts.ORBITRON_20.drawString(label, labelX, labelY + Fonts.ORBITRON_20.getLineHeight() - bh,
                META_LABEL, alphaMult);
    }

    private static int currentSectorDay() {
        if (Global.getSector() != null && Global.getSector().getClock() != null) {
            return (int) Global.getSector().getClock().getDay();
        }
        return 0;
    }

    private static int lookupDaysLeft(long contractId, int currentDay) {
        if (contractId == -1L) return -1;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return -1;
        CampaignState state = script.state();
        int row = state.contractIndex(contractId);
        if (row < 0) return -1;
        return state.contractDaysLeft(row, currentDay);
    }

    /**
     * Looks up the configured offer window length so the bar fills relative to
     * how much room the contract started with — a 7-day-window FALLEN_NOBLE
     * offer at 3 days left reads "half spent," not "3 of 14." Falls back to
     * the days-left value itself so the bar maxes out cleanly when the lookup
     * fails (e.g. acceptedTick missing).
     */
    private static int lookupOfferWindow(long contractId) {
        if (contractId == -1L) return -1;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return -1;
        CampaignState state = script.state();
        int row = state.contractIndex(contractId);
        if (row < 0) return -1;
        int expires  = state.contractOfferExpiresTick[row];
        int accepted = state.contractAcceptedTick[row]; // re-purposed as "offered tick"
        if (expires < 0 || accepted < 0) return -1;
        int window = expires - accepted;
        return window > 0 ? window : -1;
    }

    private static void fillRect(float x, float y, float w, float h, Color c, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }

    private static void strokeRect(float x, float y, float w, float h, Color c, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }
}
