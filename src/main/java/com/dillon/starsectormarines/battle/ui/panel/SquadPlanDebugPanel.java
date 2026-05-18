package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bottom-right HUD pane: per-squad GOAP plan readout. Visible whenever
 * {@link BattleSimulation#USE_GOAP_INFANTRY} is true; off otherwise. One row
 * per squad with goal, current posture, and step index — sibling to
 * {@link SquadOverviewPanel} (bottom-left), so the layout reads "your squads
 * here, what they're planning there."
 *
 * <p>Read-only and lightweight — no input handling, no selection mutation.
 * Lives behind the {@code USE_GOAP_INFANTRY} flag because there's no useful
 * data to show when the planner isn't running.
 */
public final class SquadPlanDebugPanel implements HudPanel {

    private static final float PANEL_W       = 360f;
    private static final float HEADER_H      = 28f;
    private static final float ROW_H         = 26f;
    private static final float PAD_INNER     = 8f;
    private static final float DOT_RADIUS    = 5f;

    private static final Color BG            = new Color(0x10, 0x18, 0x22, 0xD8);
    private static final Color BORDER        = new Color(0x60, 0x80, 0xA0);
    private static final Color HEADER_FG     = new Color(0xC8, 0xE0, 0xFF);
    private static final Color MARINE_FG     = new Color(0x80, 0xC0, 0xFF);
    private static final Color DEFENDER_FG   = new Color(0xFF, 0xA0, 0x80);
    private static final Color GOAL_FG       = new Color(0xC0, 0xC0, 0xC0);
    private static final Color STEP_FG       = new Color(0xE8, 0xE8, 0xE8);
    private static final Color IDLE_FG       = new Color(0x70, 0x70, 0x70);

    private static final Color ALERT_UNAWARE    = new Color(0x60, 0xC0, 0x60);
    private static final Color ALERT_SUSPICIOUS = new Color(0xE0, 0xC0, 0x40);
    private static final Color ALERT_ENGAGED    = new Color(0xE0, 0x60, 0x40);

    /** Hard cap on visible rows so a tactical-map mission with dozens of garrison squads doesn't paint over the battlefield. Older squads (lower id) win. */
    private static final int MAX_ROWS = 12;

    private final BattleUiContext ctx;
    /** Cached per-frame snapshot — built in update(), consumed by render(). */
    private final List<Squad> squads = new ArrayList<>();

    public SquadPlanDebugPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isVisible() {
        return BattleSimulation.USE_GOAP_INFANTRY && !squads.isEmpty();
    }

    @Override
    public void update(float dt) {
        squads.clear();
        if (!BattleSimulation.USE_GOAP_INFANTRY) return;
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;

        for (Squad s : sim.getSquads()) {
            if (s.aliveMembers <= 0) continue;
            squads.add(s);
            if (squads.size() >= MAX_ROWS) break;
        }
        squads.sort(Comparator.<Squad, Integer>comparing(s -> s.faction == Faction.MARINE ? 0 : 1)
                .thenComparingInt(s -> s.id));
    }

    private float panelHeight() {
        return HEADER_H + squads.size() * ROW_H + PAD_INNER;
    }

    private float panelX() {
        BattleLayout l = ctx.getLayout();
        return l.controlsX + l.controlsW - PANEL_W;
    }

    private float panelY() {
        BattleLayout l = ctx.getLayout();
        return l.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
    }

    @Override
    public void render(float alphaMult) {
        float x0 = panelX();
        float y0 = panelY();
        float w = PANEL_W;
        float h = panelHeight();

        HudDraw.prepBlend();
        HudDraw.filledRect(x0, y0, w, h, BG, alphaMult);
        HudDraw.borderRect(x0, y0, w, h, BORDER, alphaMult);

        float headerY = y0 + h - HEADER_H;
        Fonts.ORBITRON_20.drawString("GOAP PLANS", x0 + PAD_INNER, headerY + HEADER_H - 6f, HEADER_FG, alphaMult);

        for (int i = 0; i < squads.size(); i++) {
            Squad s = squads.get(i);
            float rowY = headerY - (i + 1) * ROW_H;
            float baseline = rowY + ROW_H - 6f;

            // Column 1: SQ-id colored by faction.
            String idLabel = "SQ-" + s.id;
            Color idColor = (s.faction == Faction.MARINE) ? MARINE_FG : DEFENDER_FG;
            Fonts.ORBITRON_20.drawString(idLabel, x0 + PAD_INNER, baseline, idColor, alphaMult);

            // Column 2: alert dot.
            float dotX = x0 + 60f;
            float dotY = rowY + ROW_H * 0.5f;
            HudDraw.disc(dotX, dotY, DOT_RADIUS, alertColor(s.alertLevel), alphaMult, 14);

            // Column 3: goal name (or "—" if idle).
            String goalName = s.currentGoal != null ? s.currentGoal.name() : "—";
            Fonts.ORBITRON_20.drawString(goalName, x0 + 80f, baseline, GOAL_FG, alphaMult);

            // Column 4: current posture + step n/total.
            String stepLabel = formatStep(s.currentPlan);
            Color stepColor = (s.currentPlan == null) ? IDLE_FG : STEP_FG;
            Fonts.ORBITRON_20.drawString(stepLabel, x0 + 220f, baseline, stepColor, alphaMult);
        }
    }

    private static String formatStep(SquadPlan plan) {
        if (plan == null) return "idle";
        if (plan.isComplete()) return "done";
        SquadPlan.Step step = plan.currentStep();
        return step.action.name() + " [" + (plan.currentIndex() + 1) + "/" + plan.stepCount() + "]";
    }

    private static Color alertColor(SquadAlertLevel level) {
        if (level == null) return ALERT_UNAWARE;
        switch (level) {
            case ENGAGED:    return ALERT_ENGAGED;
            case SUSPICIOUS: return ALERT_SUSPICIOUS;
            default:         return ALERT_UNAWARE;
        }
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        // Read-only debug overlay — no input handling.
    }
}
