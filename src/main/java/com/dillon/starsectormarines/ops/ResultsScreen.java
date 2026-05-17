package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
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
 * Debrief card shown after a mission resolves. Reads
 * {@link MarineOpsContext#getLastOutcome()} and renders a centered summary —
 * outcome line, payout, casualties, captain status change, XP gained — plus a
 * Return button that drops the player back to the mission picker.
 *
 * <p>This screen is read-only; {@link MissionResolver#apply} already mutated
 * cargo and captain state before we got here. Re-entering after Return won't
 * re-apply anything because the outcome lives on the context, not on the
 * cargo's untouched state.
 */
public class ResultsScreen implements Screen {

    private static final Color FRAME_COLOR    = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_COLOR    = new Color(0x8F, 0xA8, 0xC0);
    private static final Color VALUE_COLOR    = new Color(0xE0, 0xE8, 0xFF);
    private static final Color VICTORY_COLOR  = new Color(0x80, 0xE0, 0x80);
    private static final Color DEFEAT_COLOR   = new Color(0xE0, 0x60, 0x60);
    private static final Color STATUS_ACTIVE  = new Color(0x9C, 0xCC, 0x9C);
    private static final Color STATUS_INJURED = new Color(0xE0, 0xB0, 0x70);
    private static final Color STATUS_KIA     = new Color(0xE0, 0x60, 0x60);
    private static final Color PROMOTION_COLOR = new Color(0xFF, 0xD0, 0x60);

    private static final float CARD_W      = 520f;
    private static final float CARD_H      = 380f;
    private static final float INNER_PAD   = 20f;
    private static final float ROW_GAP     = 32f;
    private static final float LABEL_COL_W = 200f;
    private static final float BTN_W       = 160f;
    private static final float BTN_H       = 36f;

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;

    /** Card rect, captured at layout time so render can draw the frame. */
    private float cardX, cardY;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        cardX = position.getX() + (position.getWidth()  - CARD_W) / 2f;
        cardY = position.getY() + (position.getHeight() - CARD_H) / 2f;

        MissionOutcome outcome = ctx.getLastOutcome();

        // Header row at top of card
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("resultsHeader"),
                cardX + INNER_PAD, cardY + CARD_H - INNER_PAD, HEADER_COLOR));

        // Outcome banner (large, color-coded)
        boolean victory = outcome != null && outcome.victory;
        String outcomeText = Strings.get(victory ? "battleVictory" : "battleDefeat");
        Color outcomeColor = victory ? VICTORY_COLOR : DEFEAT_COLOR;
        float outcomeY = cardY + CARD_H - INNER_PAD - 40f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                outcomeText, cardX + INNER_PAD, outcomeY, outcomeColor));

        // Mission name below outcome
        if (outcome != null && outcome.missionName != null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    outcome.missionName,
                    cardX + INNER_PAD, outcomeY - 28f, LABEL_COLOR));
        }

        // Stat rows
        float rowY = outcomeY - 72f;
        if (outcome != null) {
            // Payout
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("resultsPayoutLabel"),
                    cardX + INNER_PAD, rowY, LABEL_COLOR));
            String payoutStr = outcome.payoutEarned > 0
                    ? MessageFormat.format(Strings.get("payoutFmt"),
                        NumberFormat.getIntegerInstance().format(outcome.payoutEarned))
                    : "—";
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, payoutStr,
                    cardX + INNER_PAD + LABEL_COL_W, rowY, VALUE_COLOR));
            rowY -= ROW_GAP;

            // Casualties
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("resultsCasualtiesLabel"),
                    cardX + INNER_PAD, rowY, LABEL_COLOR));
            String casualtiesStr = MessageFormat.format(
                    Strings.get("resultsCasualtiesFmt"),
                    outcome.marinesLost, outcome.marinesEngaged);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, casualtiesStr,
                    cardX + INNER_PAD + LABEL_COL_W, rowY, VALUE_COLOR));
            rowY -= ROW_GAP;

            // Captain row (only if a captain led the mission)
            if (outcome.captainId != null) {
                widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                        Strings.get("resultsCaptainLabel"),
                        cardX + INNER_PAD, rowY, LABEL_COLOR));
                widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                        formatCaptainStatus(outcome),
                        cardX + INNER_PAD + LABEL_COL_W, rowY,
                        statusColor(outcome.newCaptainStatus)));
                rowY -= ROW_GAP;
            }

            // XP gained (only if nonzero)
            if (outcome.xpGained > 0) {
                widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                        Strings.get("resultsXpLabel"),
                        cardX + INNER_PAD, rowY, LABEL_COLOR));
                String xpStr = MessageFormat.format(
                        Strings.get("resultsXpFmt"), outcome.xpGained);
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, xpStr,
                        cardX + INNER_PAD + LABEL_COL_W, rowY, VALUE_COLOR));
                rowY -= ROW_GAP;
            }

            // Promotion (only if the mission's XP crossed a rank threshold)
            Rank promotedTo = outcome.promotedTo;
            if (promotedTo != null) {
                widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                        Strings.get("resultsPromotionLabel"),
                        cardX + INNER_PAD, rowY, LABEL_COLOR));
                String promoStr = MessageFormat.format(
                        Strings.get("resultsPromotionFmt"), promotedTo.displayName());
                widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, promoStr,
                        cardX + INNER_PAD + LABEL_COL_W, rowY, PROMOTION_COLOR));
                rowY -= ROW_GAP;
            }
        }

        // Return button — centered bottom of card
        float btnX = cardX + (CARD_W - BTN_W) / 2f;
        float btnY = cardY + INNER_PAD;
        ButtonWidget btn = new ButtonWidget(btnX, btnY, BTN_W, BTN_H,
                () -> ctx.goTo(ScreenId.MISSION_SELECT));
        widgets.add(btn);
        String btnLabel = Strings.get("resultsReturn");
        float btnLabelW = Fonts.ORBITRON_20.measureWidth(btnLabel);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, btnLabel,
                btnX + (BTN_W - btnLabelW) / 2f, btnY + BTN_H - 6f, HEADER_COLOR));
    }

    private String formatCaptainStatus(MissionOutcome outcome) {
        Status status = outcome.newCaptainStatus;
        if (status == null) status = Status.ACTIVE;
        switch (status) {
            case INJURED: {
                float currentDay = Global.getSector() != null
                        ? Global.getSector().getClock().getDay()
                        : 0f;
                int days = Math.max(1, (int) Math.ceil(outcome.injuredUntilDay - currentDay));
                return outcome.captainName + " — " + MessageFormat.format(
                        Strings.get("resultsStatusInjuredFmt"), days);
            }
            case KIA:
                return outcome.captainName + " — " + Strings.get("resultsStatusKia");
            case ACTIVE:
            default:
                return outcome.captainName + " — " + Strings.get("resultsStatusActive");
        }
    }

    private static Color statusColor(Status status) {
        if (status == Status.INJURED) return STATUS_INJURED;
        if (status == Status.KIA)     return STATUS_KIA;
        return STATUS_ACTIVE;
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
        // Card backdrop + frame
        drawCardFrame(alphaMult);
        widgets.render(alphaMult);
    }

    private void drawCardFrame(float alphaMult) {
        // Tinted backdrop
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0x10 / 255f, 0x14 / 255f, 0x1E / 255f, 0.92f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(cardX,          cardY);
        glVertex2f(cardX + CARD_W, cardY);
        glVertex2f(cardX + CARD_W, cardY + CARD_H);
        glVertex2f(cardX,          cardY + CARD_H);
        glEnd();

        // Border
        glColor4f(FRAME_COLOR.getRed() / 255f, FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue() / 255f, 0.9f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(cardX,          cardY);
        glVertex2f(cardX + CARD_W, cardY);
        glVertex2f(cardX + CARD_W, cardY + CARD_H);
        glVertex2f(cardX,          cardY + CARD_H);
        glEnd();
    }
}
