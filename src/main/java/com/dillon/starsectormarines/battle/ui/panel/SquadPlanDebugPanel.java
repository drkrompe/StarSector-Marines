package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.ScrollState;
import com.dillon.starsectormarines.battle.ui.debug.SquadStateDumper;
import com.dillon.starsectormarines.battle.ui.highlight.CellHighlight;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bottom-right HUD pane: per-squad GOAP plan readout. Two modes driven by
 * {@link Selection}:
 *
 * <ul>
 *   <li><b>Compact</b> (no selection) — one row per squad with goal +
 *       current posture + step index. Sibling to {@link SquadOverviewPanel}
 *       (bottom-left), so the layout reads "your squads here, what they're
 *       planning there."</li>
 *   <li><b>Filtered detail</b> (a squad is selected — picked from the world
 *       via {@link com.dillon.starsectormarines.battle.ui.picking.WorldPicker}
 *       or from a UI row) — full plan dump for that squad: goal + priority
 *       bucket, every step's action + slot→member assignments, plus the
 *       current world-state predicate grid. The predicate grid is the
 *       diagnostic for "why isn't this squad doing anything?" — preconditions
 *       on Engage / Overwatch / etc. read straight off this list. The body is
 *       scrollable via {@link ScrollState} — long plans (multi-portal cordons,
 *       many slots) routinely overflow the panel and walked off-screen
 *       pre-scroll.</li>
 * </ul>
 *
 * <p>Detail mode uses {@link Fonts#INSIGNIA_15_AA} rather than Orbitron 20 —
 * predicate names + slot listings are long, and the Orbitron 20 floor for
 * gameplay UI doesn't apply to debug overlays. Compact mode keeps Orbitron 20
 * to match the rest of the HUD.
 */
public final class SquadPlanDebugPanel implements HudPanel {

    // --- Shared layout ---
    private static final float PANEL_W       = 360f;
    private static final float HEADER_H      = 28f;
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

    // --- Compact mode ---
    private static final float COMPACT_ROW_H = 26f;
    /** Hard cap on visible rows so a tactical-map mission with dozens of garrison squads doesn't paint over the battlefield. Older squads (lower id) win. */
    private static final int MAX_COMPACT_ROWS = 12;

    // --- Detail mode ---
    private static final float DETAIL_LINE_H        = 18f;
    /** Reserved height at the bottom of the panel for the "(scrolled)" hint, plus a touch of breathing room. Only takes up space when content overflows. */
    private static final float DETAIL_FOOTER_H      = 16f;
    /** Right-side scrollbar gutter inset from the panel border. */
    private static final float SCROLLBAR_W          = 4f;
    private static final float SCROLLBAR_GAP        = 3f;
    /** Pixels scrolled per wheel notch — three lines so a wheel flick feels brisk without overshooting. */
    private static final float SCROLL_PX_PER_NOTCH  = DETAIL_LINE_H * 3f;
    private static final Color DETAIL_LABEL_FG      = new Color(0xA8, 0xB8, 0xC8);
    private static final Color DETAIL_VALUE_FG      = new Color(0xE8, 0xE8, 0xE8);
    private static final Color DETAIL_SECTION_FG    = new Color(0xC8, 0xE0, 0xFF);
    private static final Color DETAIL_DIVIDER       = new Color(0x40, 0x55, 0x70);
    private static final Color DETAIL_CURRENT_STEP  = new Color(0xFF, 0xE0, 0x60);
    private static final Color PRED_TRUE_FG         = new Color(0x80, 0xE0, 0x80);
    private static final Color PRED_FALSE_FG        = new Color(0x80, 0x80, 0x80);
    private static final Color PRIORITY_MISSION_FG  = new Color(0xFF, 0xC0, 0x60);
    private static final Color PRIORITY_SURVIVAL_FG = new Color(0xFF, 0x80, 0x80);
    private static final Color SCROLL_TRACK         = new Color(0x20, 0x2C, 0x3A, 0xC0);
    private static final Color SCROLL_THUMB         = new Color(0x80, 0xA0, 0xC8, 0xE0);

    // --- Per-step highlight buttons ---
    private static final float HL_BTN_W              = 18f;
    private static final float HL_BTN_INSET          = 4f;
    private static final Color HL_BTN_BG_IDLE        = new Color(0x22, 0x32, 0x46, 0xC8);
    private static final Color HL_BTN_BG_ON          = new Color(0x10, 0x60, 0x80, 0xF0);
    private static final Color HL_BTN_FG_IDLE        = new Color(0x80, 0xA0, 0xC0);
    private static final Color HL_BTN_FG_ON          = new Color(0xE8, 0xF8, 0xFF);
    private static final Color HL_BTN_BORDER         = new Color(0x60, 0x80, 0xA0);

    // --- Header DUMP button ---
    private static final float DUMP_BTN_W            = 48f;
    private static final float DUMP_BTN_H            = 18f;
    private static final float DUMP_BTN_RIGHT_INSET  = 210f;  // sits to the left of the existing hint text
    private static final Color DUMP_BTN_BG           = new Color(0x32, 0x22, 0x46, 0xC8);
    private static final Color DUMP_BTN_FG           = new Color(0xC0, 0xA0, 0xE0);
    private static final Color DUMP_BTN_BORDER       = new Color(0x80, 0x60, 0xA0);
    /** Sim-seconds the post-dump status banner persists before reverting to the regular hint string. */
    private static final float DUMP_STATUS_DURATION  = 3.0f;

    private final BattleUiContext ctx;
    /** Per-frame cache filled by update(); consumed by render(). Empty in detail mode. */
    private final List<Squad> compactSquads = new ArrayList<>();
    /** Detail-mode squad pinned each frame from Selection; null in compact mode (or if the squad disappeared). */
    private Squad detailSquad;
    /** Snapshot of the detail squad's WorldState. Recomputed every frame so diagnostic readout stays fresh. */
    private WorldState detailState;
    /** Pre-computed content height for detail mode — total pixels of the scrollable body, ignoring the fixed header. */
    private float detailContentH;
    /** Last selection id we rendered in detail mode; used to reset scroll when the user picks a different squad so each new selection starts at the top. */
    private int lastDetailSquadId = Selection.NONE;
    /** Scroll bookkeeping for the detail body. Reused frame-to-frame so the offset survives panel re-renders. */
    private final ScrollState detailScroll = new ScrollState();
    /** Plan-step indices the user has toggled on for highlight. Cleared whenever the plan reference changes (replan) or the selection switches squads. */
    private final Set<Integer> highlightedStepIndices = new HashSet<>();
    /** Plan reference last known to {@link #highlightedStepIndices}; identity-checked so a replan wipes stale toggle state. */
    private SquadPlan lastPlanForHighlights;
    /** Per-frame button hotspots, populated by {@link #renderDetail} and consumed by {@link #handleInput}. */
    private final List<StepHotspot> stepHotspots = new ArrayList<>();
    /** Header DUMP button hotspot, refreshed per frame. {@code null} when no detail squad is selected (button isn't drawn either). */
    private StepHotspot dumpHotspot;
    /** Post-dump status text shown in place of the scroll hint. {@code null} when no status to show. Cleared once the banner expires. */
    private String dumpStatusMessage;
    /** Sim-seconds remaining on the post-dump status banner. Counted down each {@link #update} call; when it hits zero {@link #dumpStatusMessage} clears. */
    private float dumpStatusRemaining;

    /** Click target for one plan step's highlight toggle. Built per-frame in render. */
    private static final class StepHotspot {
        final int stepIdx;
        final float x, y, w, h;
        StepHotspot(int stepIdx, float x, float y, float w, float h) {
            this.stepIdx = stepIdx;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
        boolean contains(float px, float py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public SquadPlanDebugPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isVisible() {
        return detailSquad != null || !compactSquads.isEmpty();
    }

    @Override
    public void update(float dt) {
        compactSquads.clear();
        detailSquad = null;
        detailState = null;
        detailContentH = 0f;
        dumpHotspot = null;
        if (dumpStatusMessage != null) {
            dumpStatusRemaining -= dt;
            if (dumpStatusRemaining <= 0f) {
                dumpStatusMessage = null;
            }
        }

        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;

        Selection sel = ctx.getSelection();
        if (sel.hasSquadSelection()) {
            int wantId = sel.getSelectedSquadId();
            for (Squad s : sim.getSquads()) {
                if (s.id != wantId) continue;
                if (s.aliveMembers <= 0) continue;
                detailSquad = s;
                detailState = WorldStateBuilder.build(s, sim);
                if (lastDetailSquadId != wantId) {
                    // Fresh squad — scroll back to top so the user sees the
                    // header info on a new pick rather than wherever the
                    // previous squad's scroll happened to land. Highlights
                    // also reset so the new squad starts with a clean slate.
                    detailScroll.reset();
                    highlightedStepIndices.clear();
                    lastDetailSquadId = wantId;
                }
                // Plan-reference change ⇒ replan ⇒ step indices are stale.
                if (s.currentPlan != lastPlanForHighlights) {
                    highlightedStepIndices.clear();
                    lastPlanForHighlights = s.currentPlan;
                }
                detailContentH = computeDetailContentHeight(s);
                detailScroll.setMetrics(detailContentH, detailViewportHeight());
                publishStepHighlights(s, sim);
                publishSquadAndCaptainHighlights(s, sim);
                return;
            }
            // Selected squad vanished (wiped out, or stale id). Fall through to
            // compact mode rather than rendering an empty detail panel.
        }
        lastDetailSquadId = Selection.NONE;
        lastPlanForHighlights = null;
        highlightedStepIndices.clear();
        HighlightOverlay overlay = ctx.getHighlights();
        overlay.clear(HighlightOverlay.SRC_ACTION_CELLS);
        overlay.clear(HighlightOverlay.SRC_SELECTED_SQUAD);
        overlay.clear(HighlightOverlay.SRC_CAPTAIN);

        for (Squad s : sim.getSquads()) {
            if (s.aliveMembers <= 0) continue;
            compactSquads.add(s);
            if (compactSquads.size() >= MAX_COMPACT_ROWS) break;
        }
        compactSquads.sort(Comparator.<Squad, Integer>comparing(s -> s.faction == Faction.MARINE ? 0 : 1)
                .thenComparingInt(s -> s.id));
    }

    private float panelX() {
        BattleLayout l = ctx.getLayout();
        return l.controlsX + l.controlsW - PANEL_W;
    }

    private float panelY() {
        BattleLayout l = ctx.getLayout();
        return l.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
    }

    /**
     * Largest height the detail panel may grow to. Sits below the top control
     * strip with a small gap — past that the panel would overpaint the speed
     * buttons. Compact mode is small enough to never hit this, so the cap is
     * detail-only.
     */
    private float maxDetailPanelHeight() {
        BattleLayout l = ctx.getLayout();
        return l.controlsY - panelY() - BattleLayout.CONTROLS_GAP;
    }

    /**
     * Sum of every scrollable line + divider in the current detail content —
     * the virtual height the scroll system reasons over. Recomputed on each
     * update() so step counts and predicate changes track live.
     */
    private float computeDetailContentHeight(Squad s) {
        // Section 1: 2 lines (status + garrison flags), 1 divider gap.
        int lines = 2;
        int dividers = 1;
        // Section 2: 1 line (goal), 1 divider gap.
        lines += 1;
        dividers += 1;
        // Section 3: "Plan: …" line + per-step (action line + slot lines).
        lines += 1;
        if (s.currentPlan != null) {
            for (SquadPlan.Step step : s.currentPlan.steps()) {
                lines += 1 + step.assignments.size();
            }
        }
        dividers += 1;
        // Section 4: "Predicates:" header + one row per declared predicate.
        lines += 1 + Predicate.values().length;
        return lines * DETAIL_LINE_H + dividers * 2f;
    }

    /** Height of the scrollable body — total panel minus the fixed header band. */
    private float detailViewportHeight() {
        return detailPanelHeight() - HEADER_H;
    }

    /**
     * Detail-mode panel height: enough to fit the content if it's short, capped
     * by {@link #maxDetailPanelHeight} when content overflows. The overflow case
     * is what triggers scrolling.
     */
    private float detailPanelHeight() {
        float wanted = HEADER_H + detailContentH + PAD_INNER;
        return Math.min(wanted, maxDetailPanelHeight());
    }

    private float compactPanelHeight() {
        return HEADER_H + compactSquads.size() * COMPACT_ROW_H + PAD_INNER;
    }

    @Override
    public void render(float alphaMult) {
        if (detailSquad != null) {
            renderDetail(alphaMult);
        } else {
            renderCompact(alphaMult);
        }
    }

    // -----------------------------------------------------------------------
    // Compact mode (unchanged from pre-WorldPicker shape)
    // -----------------------------------------------------------------------

    private void renderCompact(float alphaMult) {
        float x0 = panelX();
        float y0 = panelY();
        float w = PANEL_W;
        float h = compactPanelHeight();

        HudDraw.prepBlend();
        HudDraw.filledRect(x0, y0, w, h, BG, alphaMult);
        HudDraw.borderRect(x0, y0, w, h, BORDER, alphaMult);

        float headerY = y0 + h - HEADER_H;
        Fonts.ORBITRON_20.drawString("GOAP PLANS", x0 + PAD_INNER, headerY + HEADER_H - 6f, HEADER_FG, alphaMult);

        for (int i = 0; i < compactSquads.size(); i++) {
            Squad s = compactSquads.get(i);
            float rowY = headerY - (i + 1) * COMPACT_ROW_H;
            float baseline = rowY + COMPACT_ROW_H - 6f;

            String idLabel = "SQ-" + s.id;
            Color idColor = (s.faction == Faction.MARINE) ? MARINE_FG : DEFENDER_FG;
            Fonts.ORBITRON_20.drawString(idLabel, x0 + PAD_INNER, baseline, idColor, alphaMult);

            float dotX = x0 + 60f;
            float dotY = rowY + COMPACT_ROW_H * 0.5f;
            HudDraw.disc(dotX, dotY, DOT_RADIUS, alertColor(s.alertLevel), alphaMult, 14);

            String goalName = s.currentGoal != null ? s.currentGoal.name() : "—";
            Fonts.ORBITRON_20.drawString(goalName, x0 + 80f, baseline, GOAL_FG, alphaMult);

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

    // -----------------------------------------------------------------------
    // Detail mode — filtered to a single squad. Diagnostic-dense, scrollable.
    // -----------------------------------------------------------------------

    private void renderDetail(float alphaMult) {
        Squad s = detailSquad;
        WorldState ws = detailState;
        if (s == null || ws == null) return;

        // Hotspot list is rebuilt every frame so a scrolled-off button isn't
        // clickable through stale geometry.
        stepHotspots.clear();

        BitmapFont font = Fonts.INSIGNIA_15_AA;
        float x0 = panelX();
        float h = detailPanelHeight();
        float y0 = panelY();
        float w = PANEL_W;

        HudDraw.prepBlend();
        HudDraw.filledRect(x0, y0, w, h, BG, alphaMult);
        HudDraw.borderRect(x0, y0, w, h, BORDER, alphaMult);

        // Fixed header — squad id + faction color + DUMP button + the
        // "scroll to see more" hint (replaced by the post-dump status
        // banner for DUMP_STATUS_DURATION sim-seconds after a dump click).
        float headerY = y0 + h - HEADER_H;
        Color idColor = (s.faction == Faction.MARINE) ? MARINE_FG : DEFENDER_FG;
        Fonts.ORBITRON_20.drawString("SQ-" + s.id, x0 + PAD_INNER, headerY + HEADER_H - 6f, idColor, alphaMult);
        Fonts.ORBITRON_20.drawString(s.faction.name(), x0 + 70f, headerY + HEADER_H - 6f, HEADER_FG, alphaMult);
        renderDumpButton(font, x0 + w - DUMP_BTN_RIGHT_INSET, headerY + HEADER_H - 8f - DUMP_BTN_H / 2f, alphaMult);
        String hint = dumpStatusMessage != null
                ? dumpStatusMessage
                : (detailScroll.overflows() ? "(scroll · click empty to clear)" : "(click empty to clear)");
        font.drawString(hint, x0 + w - 200f, headerY + HEADER_H - 8f, IDLE_FG, alphaMult);

        // Scrollable region — body lines render in this band, anything outside
        // gets skipped per-line. vpTop is just below the header; vpBottom is
        // the panel floor (no separate footer band — content can run all the
        // way to the bottom border).
        float vpTopY = headerY;
        float vpBottomY = y0;
        // Reserve a sliver on the right for the scrollbar so the rightmost text
        // glyph doesn't get visually shouldered by the thumb.
        float bodyW = w - (detailScroll.overflows() ? (SCROLLBAR_W + SCROLLBAR_GAP * 2f) : 0f);

        // First line sits one-line-height below vpTop, shifted by scroll offset
        // so the topmost content starts at vpTop when offset == 0 and slides up
        // (off-screen) as offset grows.
        float lineX = x0 + PAD_INNER;
        float lineY = vpTopY - DETAIL_LINE_H + detailScroll.offset();

        // Section 1: squad status — counts, alert, morale, garrison flags.
        String l1 = String.format("Alive %d/%d   Alert %s   Morale %.2f%s",
                s.aliveMembers, Math.max(s.aliveMembers, s.originalSize),
                s.alertLevel != null ? s.alertLevel.name() : "—",
                s.morale, s.moraleBroken ? " (BROKEN)" : "");
        lineY = drawLineIfVisible(font, l1, lineX, lineY, DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        String l2 = String.format("Garrison %s   ChokePortal %s",
                s.holdsFireUntilKillZone ? "Y" : "N",
                s.chokePointPortalId >= 0 ? String.valueOf(s.chokePointPortalId) : "—");
        lineY = drawLineIfVisible(font, l2, lineX, lineY, DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        lineY = dividerIfVisible(x0, bodyW, lineY, alphaMult, vpBottomY, vpTopY);

        // Section 2: goal + priority bucket + commander assignment.
        String goalLabel = s.currentGoal != null ? s.currentGoal.name() : "(no goal)";
        if (detailScroll.lineVisible(lineY, DETAIL_LINE_H, vpBottomY, vpTopY)) {
            font.drawString("Goal:", lineX, lineY, DETAIL_LABEL_FG, alphaMult);
            font.drawString(goalLabel, lineX + 48f, lineY, DETAIL_VALUE_FG, alphaMult);
            if (s.currentGoal != null) {
                Goal.Priority pri = s.currentGoal.priority();
                font.drawString("[" + pri.name() + "]", lineX + 220f, lineY, priorityColor(pri), alphaMult);
            }
        }
        lineY -= DETAIL_LINE_H;
        // Commander assignment readout — what Tier C told this squad to do
        // (or "—" if no commander wrote one). Distinct from Goal: the goal
        // is what the squad picked to pursue *this tick*; the assignment is
        // what the commander wants the squad to be doing strategically.
        // They diverge when the assignment's zone is unreachable or its
        // kind doesn't match any registered MISSION-priority goal.
        if (detailScroll.lineVisible(lineY, DETAIL_LINE_H, vpBottomY, vpTopY)) {
            font.drawString("Assignment:", lineX, lineY, DETAIL_LABEL_FG, alphaMult);
            String assignLabel = "—";
            if (s.assignedObjective != null) {
                com.dillon.starsectormarines.battle.command.ObjectiveAssignment a = s.assignedObjective;
                StringBuilder sb = new StringBuilder(a.kind().name());
                if (a.targetZoneId() >= 0) sb.append(" zone:").append(a.targetZoneId());
                if (a.targetNode() != null) sb.append(" node");
                if (a.objectiveId() >= 0) sb.append(" obj:").append(a.objectiveId());
                assignLabel = sb.toString();
            }
            font.drawString(assignLabel, lineX + 96f, lineY, DETAIL_VALUE_FG, alphaMult);
        }
        lineY -= DETAIL_LINE_H;
        lineY = dividerIfVisible(x0, bodyW, lineY, alphaMult, vpBottomY, vpTopY);

        // Section 3: plan steps with per-slot assignments.
        SquadPlan plan = s.currentPlan;
        String planHeader = plan == null ? "Plan: (none)"
                : "Plan: step " + (plan.currentIndex() + 1) + "/" + plan.stepCount();
        lineY = drawLineIfVisible(font, planHeader, lineX, lineY, DETAIL_SECTION_FG, alphaMult, vpBottomY, vpTopY);
        if (plan != null) {
            List<SquadPlan.Step> steps = plan.steps();
            for (int i = 0; i < steps.size(); i++) {
                SquadPlan.Step step = steps.get(i);
                boolean current = (i == plan.currentIndex() && !plan.isComplete());
                Color color = current ? DETAIL_CURRENT_STEP : DETAIL_VALUE_FG;
                String prefix = current ? "> " : "  ";
                // Draw the step line + an inline [H] highlight toggle button on
                // the right edge. Both are visibility-gated together — when the
                // line scrolls off, the hotspot doesn't register either.
                boolean stepVisible = detailScroll.lineVisible(lineY, DETAIL_LINE_H, vpBottomY, vpTopY);
                if (stepVisible) {
                    font.drawString(prefix + (i + 1) + ". " + step.action.name(),
                            lineX, lineY, color, alphaMult);
                    renderHighlightButton(font, i, x0 + bodyW, lineY, alphaMult);
                }
                lineY -= DETAIL_LINE_H;
                for (Map.Entry<String, List<Unit>> e : step.assignments.entrySet()) {
                    lineY = drawLineIfVisible(font,
                            "    " + e.getKey() + " → " + memberIds(e.getValue()),
                            lineX, lineY, DETAIL_LABEL_FG, alphaMult, vpBottomY, vpTopY);
                }
            }
        }
        lineY = dividerIfVisible(x0, bodyW, lineY, alphaMult, vpBottomY, vpTopY);

        // Section 4: predicate grid — every declared predicate, T/F colored.
        // Load-bearing diagnostic: a garrison squad statue-mode'ing under fire
        // shows up immediately as ENEMY_IN_PORTAL_CELL=F while
        // UNDER_FIRE_AT_LOS=T, for example.
        lineY = drawLineIfVisible(font, "Predicates:", lineX, lineY, DETAIL_SECTION_FG, alphaMult, vpBottomY, vpTopY);
        float tCol = x0 + bodyW - PAD_INNER - 18f;
        for (Predicate p : Predicate.values()) {
            boolean v = ws.get(p);
            if (detailScroll.lineVisible(lineY, DETAIL_LINE_H, vpBottomY, vpTopY)) {
                font.drawString(p.name(), lineX + 8f, lineY, DETAIL_LABEL_FG, alphaMult);
                font.drawString(v ? "T" : "F", tCol, lineY,
                        v ? PRED_TRUE_FG : PRED_FALSE_FG, alphaMult);
            }
            lineY -= DETAIL_LINE_H;
        }

        // Scrollbar on the right edge. Drops out automatically when content fits.
        float gutterX = x0 + w - SCROLLBAR_W - SCROLLBAR_GAP;
        detailScroll.renderScrollbar(gutterX, vpBottomY + SCROLLBAR_GAP,
                SCROLLBAR_W, (vpTopY - vpBottomY) - 2f * SCROLLBAR_GAP,
                SCROLL_TRACK, SCROLL_THUMB, alphaMult);
    }

    /** Draws {@code text} at the current cursor if it falls inside the viewport band, then advances the cursor by one line. */
    private float drawLineIfVisible(BitmapFont font, String text, float x, float y, Color color,
                                     float alphaMult, float vpBottomY, float vpTopY) {
        if (detailScroll.lineVisible(y, DETAIL_LINE_H, vpBottomY, vpTopY)) {
            font.drawString(text, x, y, color, alphaMult);
        }
        return y - DETAIL_LINE_H;
    }

    /**
     * Draws a small "[H]" toggle button to the right of a plan step line and
     * records its hotspot for click handling. Color flips based on whether the
     * step is in {@link #highlightedStepIndices}, so the user can see at a
     * glance which steps are currently lighting up cells in the world.
     */
    /**
     * Draws the header DUMP button and records its hotspot. Sentinel
     * {@code stepIdx = -1} marks it apart from the per-step [H] hotspots
     * in the shared {@code stepHotspots}-style list; we keep it in its own
     * field instead so the per-step list stays semantically clean.
     */
    private void renderDumpButton(BitmapFont font, float x, float y, float alphaMult) {
        HudDraw.filledRect(x, y, DUMP_BTN_W, DUMP_BTN_H, DUMP_BTN_BG, alphaMult);
        HudDraw.borderRect(x, y, DUMP_BTN_W, DUMP_BTN_H, DUMP_BTN_BORDER, alphaMult);
        font.drawString("DUMP", x + 6f, y + DUMP_BTN_H - 3f, DUMP_BTN_FG, alphaMult);
        dumpHotspot = new StepHotspot(-1, x, y, DUMP_BTN_W, DUMP_BTN_H);
    }

    private void renderHighlightButton(BitmapFont font, int stepIdx,
                                        float rightEdgeX, float lineY, float alphaMult) {
        boolean on = highlightedStepIndices.contains(stepIdx);
        float bx = rightEdgeX - HL_BTN_W - HL_BTN_INSET;
        float by = lineY + 2f;
        float bw = HL_BTN_W;
        float bh = DETAIL_LINE_H - 4f;
        HudDraw.filledRect(bx, by, bw, bh, on ? HL_BTN_BG_ON : HL_BTN_BG_IDLE, alphaMult);
        HudDraw.borderRect(bx, by, bw, bh, HL_BTN_BORDER, alphaMult);
        // Centered-ish "H". Insignia 15 is small enough that 4px from edges reads cleanly.
        font.drawString("H", bx + 5f, by + bh - 3f, on ? HL_BTN_FG_ON : HL_BTN_FG_IDLE, alphaMult);
        stepHotspots.add(new StepHotspot(stepIdx, bx, by, bw, bh));
    }

    /** Draws a horizontal rule when its band overlaps the viewport, then advances the cursor past the gap. */
    private float dividerIfVisible(float x0, float w, float lineY, float alphaMult,
                                    float vpBottomY, float vpTopY) {
        float ruleY = lineY + DETAIL_LINE_H * 0.5f - 1f;
        if (ruleY >= vpBottomY && ruleY < vpTopY) {
            HudDraw.filledRect(x0 + PAD_INNER, ruleY, w - 2 * PAD_INNER, 1f, DETAIL_DIVIDER, alphaMult);
        }
        return lineY - 2f;
    }

    private static Color priorityColor(Goal.Priority pri) {
        switch (pri) {
            case MISSION:  return PRIORITY_MISSION_FG;
            case SURVIVAL: return PRIORITY_SURVIVAL_FG;
            default:       return DETAIL_LABEL_FG;
        }
    }

    /** Comma-joined unit ids, capped so a large slot list doesn't blow the panel. */
    private static String memberIds(List<Unit> members) {
        if (members == null || members.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        int max = Math.min(members.size(), 4);
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(", ");
            sb.append(members.get(i).id);
        }
        if (members.size() > max) sb.append(", +").append(members.size() - max);
        return sb.toString();
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (detailSquad == null) return;
        if (events == null) return;
        // LMB on a step's [H] button toggles that step's highlight. Walk
        // events before the scroll handler so an unconsumed click on a button
        // doesn't get eaten by anything else. Buttons live inside the panel
        // rect, so this can't shadow world clicks.
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isLMBDownEvent()) continue;
            float px = e.getX();
            float py = e.getY();
            // DUMP button checked first so it doesn't shadow a [H] toggle
            // that happens to land on the same pixel after a future layout
            // change. Cheap O(1) check.
            if (dumpHotspot != null && dumpHotspot.contains(px, py)) {
                triggerDump();
                e.consume();
                continue;
            }
            for (StepHotspot hs : stepHotspots) {
                if (hs.contains(px, py)) {
                    toggleStepHighlight(hs.stepIdx);
                    e.consume();
                    break;
                }
            }
        }
        // Wheel-over-detail-panel scrolls the body. Compact mode and the
        // no-selection case stay no-input — there's nothing to scroll there.
        detailScroll.handleWheel(events,
                panelX(), panelY(), PANEL_W, detailPanelHeight(),
                SCROLL_PX_PER_NOTCH);
    }

    /**
     * Flips the highlight toggle for one plan step. The overlay republishes
     * on the next update(), so we don't have to push here — keeps the publish
     * path single-sited.
     */
    private void toggleStepHighlight(int stepIdx) {
        if (!highlightedStepIndices.add(stepIdx)) {
            highlightedStepIndices.remove(stepIdx);
        }
    }

    /**
     * Writes the current detail squad's state to {@code saves/common/} and
     * shows a short-lived status banner in place of the scroll hint. Errors
     * are swallowed (logged in {@link SquadStateDumper}) — a failed write
     * surfaces in the game log, not as a crash mid-battle.
     */
    private void triggerDump() {
        Squad s = detailSquad;
        if (s == null) return;
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;
        String selectedUnitId = ctx.getSelection().getSelectedUnitId();
        String path = SquadStateDumper.dump(s, sim, detailState, selectedUnitId);
        dumpStatusMessage = path != null
                ? "(dumped to common/" + path + ")"
                : "(dump failed — see log)";
        dumpStatusRemaining = DUMP_STATUS_DURATION;
    }

    /**
     * Always-on highlights for the selected squad: every alive member's cell
     * in green, plus the captain's cell in gold layered on top (paint order
     * is insertion order, so we add members first, captain last). Captain
     * source is omitted when no leader is assigned.
     */
    private void publishSquadAndCaptainHighlights(Squad squad, BattleSimulation sim) {
        HighlightOverlay overlay = ctx.getHighlights();
        List<CellHighlight> members = new ArrayList<>();
        for (Unit u : sim.getUnits()) {
            if (u.squadId != squad.id || !u.isAlive()) continue;
            members.add(new CellHighlight(u.getCellX(), u.getCellY(), HighlightOverlay.COLOR_SELECTED_UNIT));
        }
        overlay.put(HighlightOverlay.SRC_SELECTED_SQUAD, members);
        if (squad.leader != null && squad.leader.isAlive()) {
            overlay.put(HighlightOverlay.SRC_CAPTAIN, List.of(
                    new CellHighlight(squad.leader.getCellX(), squad.leader.getCellY(), HighlightOverlay.COLOR_CAPTAIN)));
        } else {
            overlay.clear(HighlightOverlay.SRC_CAPTAIN);
        }
    }

    /**
     * Builds the overlay's {@code SRC_ACTION_CELLS} source from the currently
     * toggled steps. Called from update() so every frame's overlay reflects
     * fresh action-cell positions (replans / movement / new portals all
     * propagate without the user re-clicking).
     */
    private void publishStepHighlights(Squad squad, BattleSimulation sim) {
        HighlightOverlay overlay = ctx.getHighlights();
        SquadPlan plan = squad.currentPlan;
        if (plan == null || highlightedStepIndices.isEmpty()) {
            overlay.clear(HighlightOverlay.SRC_ACTION_CELLS);
            return;
        }
        List<SquadPlan.Step> steps = plan.steps();
        List<CellHighlight> cells = new ArrayList<>();
        for (Integer idx : highlightedStepIndices) {
            if (idx < 0 || idx >= steps.size()) continue;
            List<int[]> stepCells = steps.get(idx).action.highlightCells(squad, sim);
            for (int[] xy : stepCells) {
                cells.add(new CellHighlight(xy[0], xy[1], HighlightOverlay.COLOR_ACTION_CELLS));
            }
        }
        overlay.put(HighlightOverlay.SRC_ACTION_CELLS, cells);
    }
}
