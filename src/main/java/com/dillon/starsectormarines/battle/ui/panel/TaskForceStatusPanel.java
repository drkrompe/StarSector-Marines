package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

/**
 * Compact bottom-left projection of the player's whole task force.
 *
 * <p>The battle may contain dozens of squads, so the default HUD deliberately
 * has no squad roster. It answers the force-level questions that remain useful
 * at any scale, then leaves squad selection to the world. Selecting a squad
 * swaps this plate for the retained 3x4 selected-squad roster; no scrolling
 * list competes with the battlefield for screen area.
 *
 * <p>All displayed values are copied during {@link #update(float)}. Rendering
 * therefore never retains or dereferences live squad state after the
 * simulation advances.
 */
public final class TaskForceStatusPanel implements HudPanel {

    public static final float PANEL_W = 386f;
    public static final float PANEL_H = 58f;

    private static final float PAD_INNER = 8f;
    private static final float MORALE_BAR_H = 4f;
    private static final float DOT_RADIUS = 4f;

    private static final Color BG = new Color(0x10, 0x18, 0x22, 0xD8);
    private static final Color BORDER = new Color(0x60, 0x80, 0xA0);
    private static final Color HEADER_FG = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE_FG = new Color(0xE0, 0xE0, 0xE0);
    private static final Color ALERT_UNAWARE = new Color(0x60, 0xC0, 0x60);
    private static final Color ALERT_SUSPICIOUS = new Color(0xE0, 0xC0, 0x40);
    private static final Color ALERT_ENGAGED = new Color(0xE0, 0x60, 0x40);

    private final BattleUiContext ctx;
    private Snapshot snapshot = Snapshot.EMPTY;

    public TaskForceStatusPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isVisible() {
        return !ctx.getSelection().hasSquadSelection() && !snapshot.empty();
    }

    @Override
    public void update(float dt) {
        BattleSimulation sim = ctx.getSim();
        snapshot = sim == null ? Snapshot.EMPTY : Snapshot.capture(sim.getSquads());
    }

    private float panelX() {
        return ctx.getLayout().backX;
    }

    private float panelY() {
        BattleLayout layout = ctx.getLayout();
        return layout.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
    }

    @Override
    public void render(float alphaMult) {
        paint(LIVE_PAINTER, snapshot, panelX(), panelY(), alphaMult);
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        float x = panelX();
        float y = panelY();
        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isLMBDownEvent()) continue;
            float pointerX = event.getX();
            float pointerY = event.getY();
            if (pointerX >= x && pointerX < x + PANEL_W
                    && pointerY >= y && pointerY < y + PANEL_H) {
                // The plate is read-only, but an opaque HUD region must not
                // select a world unit hidden behind it.
                event.consume();
            }
        }
    }

    /**
     * Paints the production plate through a backend-neutral target. The live
     * HUD and deterministic headless evidence call this same layout method.
     * Coordinates use Starsector's bottom-left, Y-up convention.
     */
    public static void paint(PaintTarget target, Snapshot value, float x, float y,
                             float alphaMult) {
        if (target == null || value == null || value.empty()) return;

        target.filledRect(x, y, PANEL_W, PANEL_H, BG, alphaMult);
        target.borderRect(x, y, PANEL_W, PANEL_H, BORDER, alphaMult);

        target.text(TextRole.HEADER, "TASK FORCE", x + PAD_INNER,
                y + PANEL_H - 7f, HEADER_FG, alphaMult);
        target.text(TextRole.BODY,
                value.effectiveSquads + "/" + value.committedSquads + " EFFECTIVE",
                x + 220f, y + PANEL_H - 7f, HEADER_FG, alphaMult);

        float bodyY = y + 29f;
        target.text(TextRole.BODY,
                value.aliveMarines + "/" + value.landedMarines + " MARINES",
                x + PAD_INNER, bodyY, VALUE_FG, alphaMult);
        drawAlert(target, x + 145f, bodyY, ALERT_ENGAGED,
                "ENG " + value.engagedSquads, alphaMult);
        drawAlert(target, x + 213f, bodyY, ALERT_SUSPICIOUS,
                "WATCH " + value.suspiciousSquads, alphaMult);
        drawAlert(target, x + 302f, bodyY, ALERT_UNAWARE,
                "CLEAR " + value.unawareSquads, alphaMult);

        target.moraleBar(x + PAD_INNER, y + 5f, PANEL_W - 2f * PAD_INNER,
                MORALE_BAR_H, value.totalMorale, value.totalMoraleCap,
                value.brokenSquads > 0, SquadMoraleSystem.MORALE_BROKEN_THRESHOLD,
                alphaMult);
    }

    private static void drawAlert(PaintTarget target, float dotX, float lineY,
                                  Color color, String label, float alphaMult) {
        target.disc(dotX, lineY - 5f, DOT_RADIUS, color, alphaMult);
        target.text(TextRole.BODY, label, dotX + 9f, lineY, VALUE_FG, alphaMult);
    }

    /** Immutable force-level values captured from the simulation in one pass. */
    public record Snapshot(int committedSquads, int effectiveSquads,
                           int aliveMarines, int landedMarines,
                           int engagedSquads, int suspiciousSquads,
                           int unawareSquads, int brokenSquads,
                           float totalMorale, float totalMoraleCap) {

        public static final Snapshot EMPTY = new Snapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0f, 0f);

        public boolean empty() {
            return committedSquads == 0;
        }

        public static Snapshot capture(Iterable<Squad> squads) {
            if (squads == null) return EMPTY;
            int committed = 0;
            int effective = 0;
            int alive = 0;
            int landed = 0;
            int engaged = 0;
            int suspicious = 0;
            int unaware = 0;
            int broken = 0;
            float morale = 0f;
            float moraleCap = 0f;

            for (Squad squad : squads) {
                if (squad == null || squad.faction != Faction.MARINE) continue;
                if (squad.originalSize <= 0 && squad.aliveMembers <= 0) continue;
                committed++;
                int squadAlive = Math.max(0, squad.aliveMembers);
                int squadLanded = Math.max(squadAlive, squad.originalSize);
                alive += squadAlive;
                landed += squadLanded;

                if (squadAlive <= 0) continue;
                if (!squad.moraleBroken) effective++;
                else broken++;
                SquadAlertLevel alert = squad.alertLevel;
                if (alert == SquadAlertLevel.ENGAGED) engaged++;
                else if (alert == SquadAlertLevel.SUSPICIOUS) suspicious++;
                else unaware++;

                float cap = squadLanded > 0
                        ? (float) squadAlive / squadLanded : 1f;
                moraleCap += cap;
                morale += Math.max(0f, Math.min(cap, squad.morale));
            }
            if (committed == 0) return EMPTY;
            return new Snapshot(committed, effective, alive, landed,
                    engaged, suspicious, unaware, broken, morale, moraleCap);
        }
    }

    public enum TextRole {
        HEADER,
        BODY
    }

    /** Drawing seam shared by the live OpenGL HUD and headless snapshot drain. */
    public interface PaintTarget {
        void filledRect(float x, float y, float width, float height,
                        Color color, float alphaMult);

        void borderRect(float x, float y, float width, float height,
                        Color color, float alphaMult);

        void disc(float centerX, float centerY, float radius,
                  Color color, float alphaMult);

        void text(TextRole role, String text, float x, float y,
                  Color color, float alphaMult);

        void moraleBar(float x, float y, float width, float height,
                       float morale, float cap, boolean broken,
                       float breakThreshold, float alphaMult);
    }

    private static final PaintTarget LIVE_PAINTER = new PaintTarget() {
        @Override
        public void filledRect(float x, float y, float width, float height,
                               Color color, float alphaMult) {
            HudDraw.prepBlend();
            HudDraw.filledRect(x, y, width, height, color, alphaMult);
        }

        @Override
        public void borderRect(float x, float y, float width, float height,
                               Color color, float alphaMult) {
            HudDraw.borderRect(x, y, width, height, color, alphaMult);
        }

        @Override
        public void disc(float centerX, float centerY, float radius,
                         Color color, float alphaMult) {
            HudDraw.disc(centerX, centerY, radius, color, alphaMult, 14);
        }

        @Override
        public void text(TextRole role, String text, float x, float y,
                         Color color, float alphaMult) {
            if (role == TextRole.HEADER) {
                Fonts.ORBITRON_20_BOLD.drawString(text, x, y, color, alphaMult);
            } else {
                Fonts.ORBITRON_20.drawString(text, x, y, color, alphaMult);
            }
        }

        @Override
        public void moraleBar(float x, float y, float width, float height,
                              float morale, float cap, boolean broken,
                              float breakThreshold, float alphaMult) {
            HudDraw.prepBlend();
            HudDraw.moraleBar(x, y, width, height, morale, cap, broken,
                    breakThreshold, alphaMult);
        }
    };
}
