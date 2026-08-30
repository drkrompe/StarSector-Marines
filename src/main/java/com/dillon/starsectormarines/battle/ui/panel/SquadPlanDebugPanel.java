package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.AudibleBearing;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture;
import com.dillon.starsectormarines.battle.command.SilentColonyCommandSnapshot;
import com.dillon.starsectormarines.battle.combat.FireGate;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
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
import java.util.List;

/**
 * Bottom-right selected-squad GOAP diagnostic. It remains closed while no
 * squad is selected; the former all-squad plan overview duplicated the scale
 * problem of the squad roster and covered the battlefield with developer
 * state. Picking a squad through
 * {@link com.dillon.starsectormarines.battle.ui.picking.WorldPicker} opens its
 * focused plan dump: goal + priority bucket, the squad's commander assignment,
 * one row per GOAP step, and the current world-state predicate grid. The old
 * per-slot/path-highlight controls duplicated world path diagnostics while
 * making the panel too tall to read alongside mission UI. The predicate grid
 * still answers "why isn't this squad doing anything?" from the selected
 * squad's own state. The body remains scrollable through {@link ScrollState}.
 *
 * <p>Detail mode uses {@link Fonts#INSIGNIA_15_AA} rather than Orbitron 20 —
 * predicate names + slot listings are long, and the Orbitron 20 floor for
 * gameplay UI doesn't apply to debug overlays.
 */
@DebugOnly
public final class SquadPlanDebugPanel implements HudPanel {

    // --- Shared layout ---
    private static final float PANEL_W       = 360f;
    private static final float HEADER_H      = 28f;
    private static final float PAD_INNER     = 8f;
    private static final float MAX_PANEL_H   = 480f;

    private static final Color BG            = new Color(0x10, 0x18, 0x22, 0xD8);
    private static final Color BORDER        = new Color(0x60, 0x80, 0xA0);
    private static final Color HEADER_FG     = new Color(0xC8, 0xE0, 0xFF);
    private static final Color MARINE_FG     = new Color(0x80, 0xC0, 0xFF);
    private static final Color DEFENDER_FG   = new Color(0xFF, 0xA0, 0x80);
    private static final Color IDLE_FG       = new Color(0x70, 0x70, 0x70);

    // --- Detail mode ---
    private static final float DETAIL_LINE_H        = 18f;
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
    private static final Color DOCTRINE_ADVANCE_FG  = new Color(0x60, 0xE8, 0xB0);
    private static final Color DOCTRINE_HOLD_FG     = new Color(0xFF, 0xD0, 0x40);
    private static final Color DOCTRINE_DISENGAGE_FG = new Color(0xFF, 0x70, 0x70);
    private static final Color SCROLL_TRACK         = new Color(0x20, 0x2C, 0x3A, 0xC0);
    private static final Color SCROLL_THUMB         = new Color(0x80, 0xA0, 0xC8, 0xE0);

    // --- Header DUMP button ---
    private static final float DUMP_BTN_W            = 48f;
    private static final float DUMP_BTN_H            = 18f;
    private static final float DUMP_BTN_RIGHT_INSET  = 220f;
    private static final Color DUMP_BTN_BG           = new Color(0x32, 0x22, 0x46, 0xC8);
    private static final Color DUMP_BTN_FG           = new Color(0xC0, 0xA0, 0xE0);
    private static final Color DUMP_BTN_BORDER       = new Color(0x80, 0x60, 0xA0);
    /** Sim-seconds the post-dump status banner persists before reverting to the regular hint string. */
    private static final float DUMP_STATUS_DURATION  = 3.0f;
    /** World cells shown forward from the selected squad's published tactical axis. */
    static final int DOCTRINE_AXIS_TRACE_CELLS = 8;

    private final BattleUiContext ctx;
    /** Selected squad pinned each frame from Selection; null while the diagnostic is closed. */
    private Squad detailSquad;
    /** Snapshot of the detail squad's WorldState. Recomputed every frame so diagnostic readout stays fresh. */
    private WorldState detailState;
    /** Pre-computed content height for detail mode — total pixels of the scrollable body, ignoring the fixed header. */
    private float detailContentH;
    /** Last selection id we rendered in detail mode; used to reset scroll when the user picks a different squad so each new selection starts at the top. */
    private int lastDetailSquadId = Selection.NONE;
    /** Scroll bookkeeping for the detail body. Reused frame-to-frame so the offset survives panel re-renders. */
    private final ScrollState detailScroll = new ScrollState();
    /** Header DUMP button hotspot, refreshed per frame. {@code null} when no detail squad is selected (button isn't drawn either). */
    private Hotspot dumpHotspot;
    /** Post-dump status text shown in place of the scroll hint. {@code null} when no status to show. Cleared once the banner expires. */
    private String dumpStatusMessage;
    /** Sim-seconds remaining on the post-dump status banner. Counted down each {@link #update} call; when it hits zero {@link #dumpStatusMessage} clears. */
    private float dumpStatusRemaining;

    /** Small retained click target for a debug action. */
    private static final class Hotspot {
        final float x, y, w, h;
        Hotspot(float x, float y, float w, float h) {
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
        return detailSquad != null;
    }

    @Override
    public void update(float dt) {
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
        ctx.getHighlights().clear(HighlightOverlay.SRC_ACTION_CELLS);
        ctx.getHighlights().clear(HighlightOverlay.SRC_BELIEVED_CONTACTS);
        ctx.getHighlights().clear(HighlightOverlay.SRC_HEARD_NOISE);
        ctx.getHighlights().clear(HighlightOverlay.SRC_CONTACT_DOCTRINE);
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
                    // previous squad's scroll happened to land.
                    detailScroll.reset();
                    lastDetailSquadId = wantId;
                }
                detailContentH = computeDetailContentHeight(s);
                detailScroll.setMetrics(detailContentH, detailViewportHeight());
                publishCaptainHighlight(s);
                publishBeliefHighlights(s);
                publishDoctrineHighlight(s);
                return;
            }
            // Selected squad vanished (wiped out, or stale id). Close both
            // selected-squad panes rather than leaving a dead selection open.
            sel.clear();
        }
        lastDetailSquadId = Selection.NONE;
        HighlightOverlay overlay = ctx.getHighlights();
        overlay.clear(HighlightOverlay.SRC_ACTION_CELLS);
        overlay.clear(HighlightOverlay.SRC_CAPTAIN);
        overlay.clear(HighlightOverlay.SRC_BELIEVED_CONTACTS);
        overlay.clear(HighlightOverlay.SRC_HEARD_NOISE);
        overlay.clear(HighlightOverlay.SRC_CONTACT_DOCTRINE);
        // SRC_SELECTED_SQUAD is owned by SelectionHighlightPublisher (production)
        // now — it clears itself when the selection drops, so the panel no longer
        // touches it.

    }

    private float panelX() {
        BattleLayout l = ctx.getLayout();
        return l.controlsX + l.controlsW - PANEL_W;
    }

    /** Top edge, below the retained time/objective rail. */
    private float panelTopY() {
        BattleLayout l = ctx.getLayout();
        return l.controlsY + l.controlsH
                - BattleLayout.COMMAND_RAIL_H - BattleLayout.CONTROLS_GAP;
    }

    private float panelY() {
        return panelTopY() - detailPanelHeight();
    }

    /**
     * Largest height the selected-squad panel may grow to. It is top-anchored
     * beneath the retained rail and bounded so selection does not turn the
     * entire right edge into a developer console.
     */
    private float maxPanelHeight() {
        BattleLayout l = ctx.getLayout();
        float floor = l.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
        return Math.max(HEADER_H,
                Math.min(MAX_PANEL_H, panelTopY() - floor));
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
        // Section 2: contact/doctrine, initiative, HOLD freshness, and fire readiness.
        lines += 7;
        dividers += 1;
        // Section 3: goal + assignment; autonomous command adds the committed
        // common envelope, the ownership ledger adds directive provenance,
        // and Conquest adds its typed track reasoning.
        lines += 3;
        CommanderSnapshot<?> commander = commanderSnapshot(s, ctx.getSim());
        CommandDirective directive = commandDirective(s, ctx.getSim(), commander);
        CommandDirective activeDirective = ctx.getSim()
                .getSquadCommandDirective(s.id);
        if (commander != null) lines += 1;
        if (directive != null) lines += 3;
        if (activeDirective != null && !activeDirective.equals(directive)) lines += 1;
        if (commander != null && openingOperationPicture(commander) != null) lines += 2;
        if (commander != null && silentColonySnapshot(commander) != null) lines += 3;
        if (commander != null && conquestSnapshot(commander) != null) lines += 3;
        if (commander != null && assaultSnapshot(commander) != null) lines += 2;
        if (commander != null && assaultDefenseSnapshot(commander) != null) lines += 2;
        if (commander != null && sabotageSnapshot(commander) != null) lines += 2;
        if (commander != null && sabotageDefenseSnapshot(commander) != null) lines += 2;
        dividers += 1;
        // Section 4: "Plan: …" line + one concise action row per step.
        lines += 1;
        if (s.currentPlan != null) {
            lines += s.currentPlan.stepCount();
        }
        dividers += 1;
        // Section 5: "Predicates:" header + one row per declared predicate.
        lines += 1 + Predicate.values().length;
        return lines * DETAIL_LINE_H + dividers * 2f;
    }

    /** Height of the scrollable body — total panel minus the fixed header band. */
    private float detailViewportHeight() {
        return detailPanelHeight() - HEADER_H;
    }

    /**
     * Detail-mode panel height: enough to fit the content if it's short, capped
     * by {@link #maxPanelHeight} when content overflows. The overflow case
     * is what triggers scrolling.
     */
    private float detailPanelHeight() {
        float wanted = HEADER_H + detailContentH + PAD_INNER;
        return Math.min(wanted, maxPanelHeight());
    }

    @Override
    public void render(float alphaMult) {
        renderDetail(alphaMult);
    }

    // -----------------------------------------------------------------------
    // Detail mode — filtered to a single squad. Diagnostic-dense, scrollable.
    // -----------------------------------------------------------------------

    private void renderDetail(float alphaMult) {
        Squad s = detailSquad;
        WorldState ws = detailState;
        if (s == null || ws == null) return;

        BitmapFont font = Fonts.INSIGNIA_15_AA;
        float x0 = panelX();
        float h = detailPanelHeight();
        float y0 = panelY();
        float w = PANEL_W;

        HudDraw.prepBlend();
        HudDraw.filledRect(x0, y0, w, h, BG, alphaMult);
        HudDraw.borderRect(x0, y0, w, h, BORDER, alphaMult);

        // Fixed header — frozen squad label + DUMP button + the
        // "scroll to see more" hint (replaced by the post-dump status
        // banner for DUMP_STATUS_DURATION sim-seconds after a dump click).
        float headerY = y0 + h - HEADER_H;
        Color idColor = (s.faction == Faction.MARINE) ? MARINE_FG : DEFENDER_FG;
        String squadLabel = debugHeaderLabel(s);
        Fonts.ORBITRON_20.drawString(squadLabel, x0 + PAD_INNER,
                headerY + HEADER_H - 6f, idColor, alphaMult);
        renderDumpButton(font, x0 + w - DUMP_BTN_RIGHT_INSET, headerY + HEADER_H - 8f - DUMP_BTN_H / 2f, alphaMult);
        String hint = dumpStatusMessage != null
                ? dumpStatusMessage
                : (detailScroll.overflows() ? "(scroll · click empty to clear)" : "(click empty to clear)");
        font.drawString(hint, x0 + w - 160f, headerY + HEADER_H - 8f, IDLE_FG, alphaMult);

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

        // Section 2: published contact picture. These rows consume the exact
        // snapshot planning used; presentation never reconstructs a score or
        // consults a hostile's live cell.
        SquadContactPicture picture = s.contactPicture;
        lineY = drawLineIfVisible(font, doctrineSummary(picture), lineX, lineY,
                doctrineColor(picture.doctrine()), alphaMult, vpBottomY, vpTopY);
        lineY = drawLineIfVisible(font, threatSummary(picture), lineX, lineY,
                DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        lineY = drawLineIfVisible(font, forceSummary(picture), lineX, lineY,
                DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        lineY = drawLineIfVisible(font, initiativeSummary(picture), lineX, lineY,
                DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        String primary = primaryContactLabel(picture, ctx.getSim());
        lineY = drawLineIfVisible(font, primarySummary(picture, primary), lineX, lineY,
                DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        lineY = drawLineIfVisible(font, holdReactionSummary(s, ctx.getSim()),
                lineX, lineY, DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        lineY = drawLineIfVisible(font, fireSummary(s, ctx.getSim()), lineX, lineY,
                DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
        lineY = dividerIfVisible(x0, bodyW, lineY, alphaMult, vpBottomY, vpTopY);

        // Section 3: goal + priority bucket + commander assignment.
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
        CommanderSnapshot<?> commander = commanderSnapshot(s, ctx.getSim());
        CommandDirective directive = commandDirective(s, ctx.getSim(), commander);
        CommandDirective activeDirective = ctx.getSim()
                .getSquadCommandDirective(s.id);
        ObjectiveAssignment displayedAssignment = directive != null
                && directive.status() != CommandDirective.Status.REJECTED
                ? directive.assignment() : s.assignedObjective;
        // Commander assignment readout — what Tier C told this squad to do
        // (or "—" if no commander wrote one). Distinct from Goal: the goal
        // is what the squad picked to pursue *this tick*; the assignment is
        // what the commander wants the squad to be doing strategically.
        // They diverge when the assignment's zone is unreachable or its
        // kind doesn't match any registered MISSION-priority goal.
        if (detailScroll.lineVisible(lineY, DETAIL_LINE_H, vpBottomY, vpTopY)) {
            font.drawString("Assignment:", lineX, lineY, DETAIL_LABEL_FG, alphaMult);
            String assignLabel = "—";
            if (displayedAssignment != null) {
                ObjectiveAssignment a = displayedAssignment;
                StringBuilder sb = new StringBuilder(a.kind().name());
                if (a.targetZoneId() >= 0) sb.append(" zone:").append(a.targetZoneId());
                if (a.targetNode() != null) sb.append(" node");
                if (a.objectiveId() >= 0) sb.append(" obj:").append(a.objectiveId());
                if (a.targetCellX() >= 0 && a.targetCellY() >= 0) {
                    sb.append(" cell:").append(a.targetCellX())
                            .append(',').append(a.targetCellY());
                }
                assignLabel = sb.toString();
            }
            font.drawString(assignLabel, lineX + 96f, lineY, DETAIL_VALUE_FG, alphaMult);
        }
        lineY -= DETAIL_LINE_H;
        lineY = drawLineIfVisible(font, executionSummary(s),
                lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                vpBottomY, vpTopY);
        if (commander != null) {
            lineY = drawLineIfVisible(font, commandSummary(commander),
                    lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                    vpBottomY, vpTopY);
        }
        if (directive != null) {
            lineY = drawLineIfVisible(font, directiveSummary(directive),
                    lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                    vpBottomY, vpTopY);
            lineY = drawLineIfVisible(font, provenanceSummary(directive),
                    lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                    vpBottomY, vpTopY);
            lineY = drawLineIfVisible(font, stabilitySummary(directive),
                    lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                    vpBottomY, vpTopY);
        }
        if (activeDirective != null && !activeDirective.equals(directive)) {
            lineY = drawLineIfVisible(font, ownershipSummary(activeDirective),
                    lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                    vpBottomY, vpTopY);
        }
        if (commander != null) {
            OpeningOperationCommandPicture opening =
                    openingOperationPicture(commander);
            if (opening != null) {
                OpeningOperationCommandPicture.SquadIntent intent =
                        opening.intentFor(s.id);
                lineY = drawLineIfVisible(font, openingRoleSummary(intent),
                        lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                        vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, openingPlaceSummary(opening),
                        lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                        vpBottomY, vpTopY);
            }
            SilentColonyCommandSnapshot silent =
                    silentColonySnapshot(commander);
            if (silent != null) {
                SilentColonyCommandSnapshot.SquadIntent intent =
                        silent.intentFor(s.id);
                lineY = drawLineIfVisible(font, silentColonyRoleSummary(intent),
                        lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                        vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font,
                        silentColonyObjectiveSummary(silent),
                        lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                        vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font,
                        silentColonyPressureSummary(silent),
                        lineX, lineY, DETAIL_VALUE_FG, alphaMult,
                        vpBottomY, vpTopY);
            }
            ConquestFrontSnapshot conquest = conquestSnapshot(commander);
            if (conquest != null) {
                ConquestFrontSnapshot.SquadDirective conquestDirective =
                        conquest.directiveFor(s.id);
                lineY = drawLineIfVisible(font, conquestOrderSummary(
                                conquestDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, conquestReasonSummary(
                                conquestDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, conquestExecutionSummary(
                                conquest.squadFor(s.id)), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, trackSummary(conquest,
                                conquestDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
            }
            AssaultSearchSnapshot assault = assaultSnapshot(commander);
            if (assault != null) {
                AssaultSearchSnapshot.SquadDirective assaultDirective =
                        assault.directiveFor(s.id);
                lineY = drawLineIfVisible(font, assaultOrderSummary(
                                assaultDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, assaultSectorSummary(
                                assault, assaultDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
            }
            AssaultDefenseSnapshot assaultDefense =
                    assaultDefenseSnapshot(commander);
            if (assaultDefense != null) {
                AssaultDefenseSnapshot.SquadDirective defenseDirective =
                        assaultDefense.directiveFor(s.id);
                lineY = drawLineIfVisible(font, assaultDefenseOrderSummary(
                                defenseDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, assaultDefenseAreaSummary(
                                assaultDefense, defenseDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
            }
            SabotageSiteSnapshot sabotage = sabotageSnapshot(commander);
            if (sabotage != null) {
                SabotageSiteSnapshot.SquadDirective sabotageDirective =
                        sabotage.directiveFor(s.id);
                lineY = drawLineIfVisible(font, sabotageOrderSummary(
                                sabotageDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, sabotageSiteSummary(
                                sabotage, sabotageDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
            }
            SabotageDefenseSnapshot defense = sabotageDefenseSnapshot(commander);
            if (defense != null) {
                SabotageDefenseSnapshot.SquadDirective defenseDirective =
                        defense.directiveFor(s.id);
                lineY = drawLineIfVisible(font, sabotageDefenseOrderSummary(
                                defenseDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
                lineY = drawLineIfVisible(font, sabotageDefenseSiteSummary(
                                defense, defenseDirective), lineX, lineY,
                        DETAIL_VALUE_FG, alphaMult, vpBottomY, vpTopY);
            }
        }
        lineY = dividerIfVisible(x0, bodyW, lineY, alphaMult, vpBottomY, vpTopY);

        // Section 4: the GOAP sequence. Per-slot assignments and path-cell
        // highlight buttons were removed; the selected-squad panel reports
        // decisions while world path diagnostics remain in the DEBUG menu.
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
                boolean stepVisible = detailScroll.lineVisible(lineY, DETAIL_LINE_H, vpBottomY, vpTopY);
                if (stepVisible) {
                    font.drawString(prefix + (i + 1) + ". " + step.action.name(),
                            lineX, lineY, color, alphaMult);
                }
                lineY -= DETAIL_LINE_H;
            }
        }
        lineY = dividerIfVisible(x0, bodyW, lineY, alphaMult, vpBottomY, vpTopY);

        // Section 5: predicate grid — every declared predicate, T/F colored.
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

    private static String debugHeaderLabel(Squad squad) {
        String label = squad.campaignLabel != null && !squad.campaignLabel.isBlank()
                ? squad.campaignLabel : "SQ-" + squad.id;
        return label.length() <= 16 ? label : label.substring(0, 13) + "...";
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
     * Draws the header DUMP button and records its hotspot.
     */
    private void renderDumpButton(BitmapFont font, float x, float y, float alphaMult) {
        HudDraw.filledRect(x, y, DUMP_BTN_W, DUMP_BTN_H, DUMP_BTN_BG, alphaMult);
        HudDraw.borderRect(x, y, DUMP_BTN_W, DUMP_BTN_H, DUMP_BTN_BORDER, alphaMult);
        font.drawString("DUMP", x + 6f, y + DUMP_BTN_H - 3f, DUMP_BTN_FG, alphaMult);
        dumpHotspot = new Hotspot(x, y, DUMP_BTN_W, DUMP_BTN_H);
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

    static String doctrineSummary(SquadContactPicture picture) {
        return String.format("Doctrine %s   Posture %s   Odds %s",
                picture.doctrine(), picture.posture(), picture.forceBalance());
    }

    static String threatSummary(SquadContactPicture picture) {
        return String.format("Threat %s   Motion %s   Seen D%d/T%d",
                picture.dominantSector(), picture.primaryMotion(),
                picture.directContactCount(), picture.contactCount());
    }

    static String forceSummary(SquadContactPicture picture) {
        return String.format("Force H%.2f/F%d   Axis %+.2f,%+.2f",
                picture.hostileStrength(), picture.friendlyStrength(),
                picture.axisX(), picture.axisY());
    }

    static String initiativeSummary(SquadContactPicture picture) {
        return String.format("Initiative %s   Line M%d/%d T%d/%d",
                picture.contactInitiative(), picture.primaryEngageableMembers(),
                picture.liveMembers(), picture.primaryEngageableFireTeams(),
                picture.liveFireTeams());
    }

    static String primarySummary(SquadContactPicture picture, String primaryLabel) {
        if (!picture.hasContacts() || picture.primaryContactId() == 0L) {
            return "Primary —";
        }
        return String.format("Primary %s @%d,%d   Confidence %.2f",
                primaryLabel, picture.primaryCellX(), picture.primaryCellY(),
                picture.primaryConfidence());
    }

    static String holdReactionSummary(Squad squad, BattleSimulation sim) {
        SquadContactPicture picture = squad.contactPicture;
        boolean fresh = TacticalScoring.contactHoldIsFresh(squad, picture,
                sim.getSimTickIndex());
        boolean active = TacticalScoring.shouldHardHoldAdvance(squad, picture,
                sim.getSimTickIndex());
        BelievedContact evidence = squad.believedContact(
                picture.primaryContactId());
        String age = evidence != null
                ? Math.max(0, sim.getSimTickIndex() - evidence.lastSeenTick()) + "t"
                : (picture.directContactCount() > 0 ? "0t" : "—");
        return String.format("Hold stop %s   Evidence %s/%dt",
                active ? "ACTIVE" : "OFF", age,
                TacticalScoring.HOLD_AFTER_LOS_TICKS);
    }

    private static CommanderSnapshot<?> commanderSnapshot(
            Squad squad, BattleSimulation sim) {
        return sim.getCommanderSnapshot(squad.faction);
    }

    private static CommandDirective commandDirective(
            Squad squad, BattleSimulation sim, CommanderSnapshot<?> snapshot) {
        CommandDirective active = sim.getSquadCommandDirective(squad.id);
        if (snapshot == null) return active;
        CommandDirective proposed = snapshot.directiveFor(squad.id);
        return proposed != null ? proposed : active;
    }

    private static ConquestFrontSnapshot conquestSnapshot(
            CommanderSnapshot<?> snapshot) {
        return snapshot.detail() instanceof ConquestFrontSnapshot conquest
                ? conquest : null;
    }

    private static OpeningOperationCommandPicture openingOperationPicture(
            CommanderSnapshot<?> snapshot) {
        return snapshot.detail() instanceof OpeningOperationCommandPicture opening
                ? opening : null;
    }

    static String openingRoleSummary(
            OpeningOperationCommandPicture.SquadIntent intent) {
        return intent == null
                ? "Scenario role —   Reason —"
                : String.format("Scenario role %s   Reason %s",
                intent.role(), intent.reason());
    }

    static String openingPlaceSummary(OpeningOperationCommandPicture picture) {
        return String.format("Scenario place %s   cell:%d,%d zone:%d",
                picture.placeName(), picture.placeCellX(),
                picture.placeCellY(), picture.placeZoneId());
    }

    private static SilentColonyCommandSnapshot silentColonySnapshot(
            CommanderSnapshot<?> snapshot) {
        return snapshot.detail() instanceof SilentColonyCommandSnapshot silent
                ? silent : null;
    }

    static String silentColonyRoleSummary(
            SilentColonyCommandSnapshot.SquadIntent intent) {
        return intent == null
                ? "Expedition branch —   Reason —"
                : String.format("Expedition branch %s   Reason %s",
                intent.role(), intent.assignmentReason());
    }

    static String silentColonyObjectiveSummary(
            SilentColonyCommandSnapshot snapshot) {
        return String.format("Archive %s %.0f%%   Survivors %s %d/%d",
                snapshot.archive().phase(), snapshot.archive().progress() * 100f,
                snapshot.survivors().phase(),
                snapshot.survivors().activeElements(),
                snapshot.survivors().initialElements());
    }

    static String silentColonyPressureSummary(
            SilentColonyCommandSnapshot snapshot) {
        return String.format("Known pressure %d   Branches A%d/S%d",
                snapshot.knownPressureContacts(),
                snapshot.archiveBranchSquads(),
                snapshot.survivorBranchSquads());
    }

    private static AssaultSearchSnapshot assaultSnapshot(
            CommanderSnapshot<?> snapshot) {
        return snapshot.detail() instanceof AssaultSearchSnapshot assault
                ? assault : null;
    }

    private static AssaultDefenseSnapshot assaultDefenseSnapshot(
            CommanderSnapshot<?> snapshot) {
        return snapshot.detail() instanceof AssaultDefenseSnapshot defense
                ? defense : null;
    }

    private static SabotageSiteSnapshot sabotageSnapshot(
            CommanderSnapshot<?> snapshot) {
        return snapshot.detail() instanceof SabotageSiteSnapshot sabotage
                ? sabotage : null;
    }

    private static SabotageDefenseSnapshot sabotageDefenseSnapshot(
            CommanderSnapshot<?> snapshot) {
        return snapshot.detail() instanceof SabotageDefenseSnapshot defense
                ? defense : null;
    }

    static String commandSummary(CommanderSnapshot<?> snapshot) {
        return String.format("Command %s %s   Phase %s",
                snapshot.perspective(), snapshot.strategy(), snapshot.phase());
    }

    static String directiveSummary(CommandDirective directive) {
        return directive == null
                ? "Directive —   Authority —"
                : String.format("Directive %s   Authority %s",
                directive.status(), directive.authority());
    }

    static String executionSummary(Squad squad) {
        String reason = squad.assignmentExecutionSuspension();
        if (reason != null) return "Execution SUSPENDED   Reason " + reason;
        return squad.assignmentForExecution() != null
                ? "Execution READY" : "Execution UNASSIGNED";
    }

    static String ownershipSummary(CommandDirective directive) {
        return String.format("Active owner %s   Authority %s",
                directive.issuer(), directive.authority());
    }

    static String provenanceSummary(CommandDirective directive) {
        return directive == null
                ? "Issuer —   Reason —"
                : String.format("Issuer %s   Reason %s",
                directive.issuer(), directive.reason());
    }

    static String stabilitySummary(CommandDirective directive) {
        if (directive == null) {
            return "Issued —   Stable —   Lease —   Disposition —";
        }
        String stable = directive.stableUntilTick() >= 0
                ? Integer.toString(directive.stableUntilTick()) : "—";
        String lease = directive.leaseUntilTick() >= 0
                ? Integer.toString(directive.leaseUntilTick()) : "—";
        String disposition = directive.dispositionReason().isEmpty()
                ? "—" : directive.dispositionReason();
        return String.format(
                "Issued %d   Stable %s   Lease %s   Disposition %s",
                directive.issuedTick(), stable, lease, disposition);
    }

    static String trackSummary(ConquestFrontSnapshot snapshot,
                               ConquestFrontSnapshot.SquadDirective directive) {
        if (directive == null) return "Track —";
        ConquestFrontSnapshot.TrackState track = snapshot.track(
                directive.effectiveTrack());
        if (track == null) return String.format("Track P%d→E%d",
                directive.preferredTrack(), directive.effectiveTrack());
        return String.format("Track P%d→E%d   Front F%s/H%s   Press %.1f/%.1f",
                directive.preferredTrack(), directive.effectiveTrack(),
                progressLabel(track.friendlyBodyProgress()),
                progressLabel(track.knownHostileFrontProgress()),
                track.friendlyPressure(), track.knownHostilePressure());
    }

    static String conquestOrderSummary(
            ConquestFrontSnapshot.SquadDirective directive) {
        if (directive == null) return "Commander order —";
        String action = directive.assignmentKind() != null
                ? directive.assignmentKind().name() : "UNASSIGNED";
        String target = directive.targetCellX() >= 0
                && directive.targetCellY() >= 0
                ? "cell " + directive.targetCellX() + "," + directive.targetCellY()
                : directive.targetZoneId() >= 0
                        ? "zone " + directive.targetZoneId()
                            + markerSuffix(directive)
                        : directive.markerCellX() >= 0
                                ? "marker " + directive.markerCellX() + ","
                                    + directive.markerCellY() : "—";
        return String.format("Commander order %s   Target %s", action, target);
    }

    static String assaultOrderSummary(
            AssaultSearchSnapshot.SquadDirective directive) {
        if (directive == null) return "Search order —";
        String action = directive.assignmentKind() != null
                ? directive.assignmentKind().name() : "UNASSIGNED";
        String target = directive.targetCellX() >= 0
                ? directive.targetCellX() + "," + directive.targetCellY() : "—";
        return String.format("Search order %s   Sector S%d   Target %s   Reason %s",
                action, directive.sectorIndex() + 1, target, directive.reason());
    }

    static String assaultSectorSummary(
            AssaultSearchSnapshot snapshot,
            AssaultSearchSnapshot.SquadDirective directive) {
        if (directive == null) return "Search sector —";
        AssaultSearchSnapshot.SectorState sector = snapshot.sector(
                directive.sectorIndex());
        if (sector == null) return "Search sector —";
        return String.format("Sector %s   Coverage %d/%d   Contacts %d   Squads %d",
                sector.status(), sector.visitedLegs(), sector.totalLegs(),
                sector.believedContacts(), sector.assignedSquads());
    }

    static String assaultDefenseOrderSummary(
            AssaultDefenseSnapshot.SquadDirective directive) {
        if (directive == null) return "Defense area —";
        String action = directive.assignmentKind() != null
                ? directive.assignmentKind().name() : "UNASSIGNED";
        String target = directive.markerCellX() >= 0
                ? directive.markerCellX() + "," + directive.markerCellY() : "—";
        String area = directive.areaIndex() >= 0
                ? "A" + (directive.areaIndex() + 1) : "—";
        return String.format(
                "Defense area %s   %s   Role %s   Target %s   Reason %s",
                area, action, directive.role(), target,
                directive.reason());
    }

    static String assaultDefenseAreaSummary(
            AssaultDefenseSnapshot snapshot,
            AssaultDefenseSnapshot.SquadDirective directive) {
        if (directive == null) return "Defense report —";
        AssaultDefenseSnapshot.AreaState area = snapshot.area(
                directive.areaIndex());
        if (area == null) return "Defense report —";
        return String.format(
                "Report %s %d contacts exp %d   Cover %d+%d   Press %.1f/%.1f",
                area.reportState(), area.believedContacts(),
                area.reportExpiresTick(), area.routineSquads(),
                area.respondingSquads(), area.friendlyPressure(),
                area.knownHostilePressure());
    }

    static String conquestReasonSummary(
            ConquestFrontSnapshot.SquadDirective directive) {
        if (directive == null) return "Command reason —";
        String capturePolicy = directive.distantCaptureDeferred()
                ? "   Capture DEFERRED_FOR_FRONT_RESISTANCE" : "";
        return "Command reason " + directive.reason() + capturePolicy;
    }

    static String conquestExecutionSummary(
            ConquestFrontSnapshot.SquadState state) {
        if (state == null) return "Command activity —";
        String cause;
        if (state.aliveMembers() <= 0) cause = "LIFECYCLE";
        else if (state.executionSuspension() != null) {
            cause = "SUSPENDED " + state.executionSuspension();
        } else if (state.localContact()) cause = "LOCAL_CONTACT";
        else if (state.activePathMembers() > 0) cause = "ACTIVE_PATH";
        else cause = "IDLE_CANDIDATE";
        return String.format("Command activity %s   Moving %d/%d   At portal %d   In target %d",
                cause, state.activePathMembers(), state.aliveMembers(),
                state.membersInTargetPortal(), state.membersInTargetZone());
    }

    static String sabotageOrderSummary(
            SabotageSiteSnapshot.SquadDirective directive) {
        if (directive == null) return "Site group —";
        return String.format("Site group S%d   %s   Reason %s",
                directive.siteIndex() + 1, directive.groupRole(),
                directive.reason());
    }

    static String sabotageSiteSummary(SabotageSiteSnapshot snapshot,
                                      SabotageSiteSnapshot.SquadDirective directive) {
        if (directive == null) return "Site state —";
        SabotageSiteSnapshot.SiteState site = snapshot.site(directive.siteIndex());
        if (site == null) return "Site state —";
        return String.format("Site %s %.1f/%.1f   %s   Security %d   Press %.1f/%.1f",
                site.id(), site.progress(), site.plantDuration(), site.groupReason(),
                site.securitySquads(),
                site.friendlyPressure(), site.knownHostilePressure());
    }

    static String sabotageDefenseOrderSummary(
            SabotageDefenseSnapshot.SquadDirective directive) {
        if (directive == null) return "Defense site group —";
        return String.format("Defense site S%d   %s   Reason %s",
                directive.siteIndex() + 1, directive.role(), directive.reason());
    }

    static String sabotageDefenseSiteSummary(
            SabotageDefenseSnapshot snapshot,
            SabotageDefenseSnapshot.SquadDirective directive) {
        if (directive == null) return "Defense site state —";
        SabotageDefenseSnapshot.SiteState site = snapshot.site(
                directive.siteIndex());
        if (site == null) return "Defense site state —";
        return String.format("Site %s   Alarm %s   Cover %d+%d   Press %.1f/%.1f",
                site.id(), site.alarmActive() ? "ACTIVE" : "QUIET",
                site.routineSquads(), site.respondingSquads(),
                site.friendlyPressure(), site.knownHostilePressure());
    }

    private static String markerSuffix(
            ConquestFrontSnapshot.SquadDirective directive) {
        return directive.markerCellX() >= 0
                ? " (marker " + directive.markerCellX() + ","
                    + directive.markerCellY() + ")" : "";
    }

    private static String progressLabel(float value) {
        return value >= 0f ? String.format("%.2f", value) : "—";
    }

    static String fireSummary(Squad squad, BattleSimulation sim) {
        int ready = 0;
        int registering = 0;
        int cooldown = 0;
        FireGate latestGate = FireGate.NONE;
        int latestTick = -1;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long member = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(member)
                    || sim.squad().squadId(member) != squad.id
                    || !sim.world().hasCombat(member)) continue;
            if (sim.combat().reflexTimer(member) > 0f) registering++;
            else if (sim.combat().reflexTargetId(member) != 0L
                    && sim.resolveUnit(sim.combat().reflexTargetId(member)) != 0L
                    && sim.combat().cooldownTimer(member) <= 0f) ready++;
            if (sim.combat().cooldownTimer(member) > 0f) cooldown++;
            int gateTick = sim.combat().lastFireGateTick(member);
            FireGate gate = sim.combat().lastFireGate(member);
            if (gate != FireGate.NONE && gateTick >= latestTick) {
                latestTick = gateTick;
                latestGate = gate;
            }
        }
        String last = latestGate == FireGate.NONE ? "—"
                : latestGate + " " + Math.max(0, sim.getSimTickIndex() - latestTick) + "t";
        return String.format("Fire Ready %d   Reg %d   CD %d   Last %s",
                ready, registering, cooldown, last);
    }

    private static String primaryContactLabel(SquadContactPicture picture,
                                              BattleSimulation sim) {
        if (picture.primaryContactId() == 0L) return "—";
        long live = sim.resolveUnit(picture.primaryContactId());
        return live != 0L && sim.identity().has(live)
                ? sim.identity().name(live)
                : "#" + picture.primaryContactId();
    }

    private static Color doctrineColor(Doctrine doctrine) {
        return switch (doctrine) {
            case ADVANCE -> DOCTRINE_ADVANCE_FG;
            case HOLD -> DOCTRINE_HOLD_FG;
            case DISENGAGE -> DOCTRINE_DISENGAGE_FG;
        };
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null || detailSquad == null) return;
        // The only retained action in the consolidated diagnostic is DUMP.
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isLMBDownEvent()) continue;
            float px = e.getX();
            float py = e.getY();
            if (dumpHotspot != null && dumpHotspot.contains(px, py)) {
                triggerDump();
                e.consume();
            }
        }
        // Wheel-over-detail-panel scrolls the body. The no-selection case is
        // closed and owns no input.
        detailScroll.handleWheel(events,
                panelX(), panelY(), PANEL_W, detailPanelHeight(),
                SCROLL_PX_PER_NOTCH);
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
        long selectedUnitEntityId = ctx.getSelection().getSelectedUnitEntityId();
        String path = SquadStateDumper.dump(s, sim, detailState, selectedUnitEntityId);
        dumpStatusMessage = path != null
                ? "(dumped to common/" + path + ")"
                : "(dump failed — see log)";
        dumpStatusRemaining = DUMP_STATUS_DURATION;
    }

    /**
     * Debug captain badge: the selected squad's leader cell in gold. The green
     * selected-squad member highlight is a production cue now, published every
     * frame by {@code SelectionHighlightPublisher} — this panel only adds the
     * (debug-only) captain marker on top. Source is cleared when no leader.
     */
    private void publishCaptainHighlight(Squad squad) {
        HighlightOverlay overlay = ctx.getHighlights();
        long leaderUnit = ctx.getSim().resolveUnit(squad.leaderId);
        if (leaderUnit != 0L) {
            overlay.put(HighlightOverlay.SRC_CAPTAIN, List.of(
                    new CellHighlight(ctx.getSim().world().cellX(leaderUnit), ctx.getSim().world().cellY(leaderUnit), HighlightOverlay.COLOR_CAPTAIN)));
        } else {
            overlay.clear(HighlightOverlay.SRC_CAPTAIN);
        }
    }

    /** Draws the selected squad's remembered hostile cells as fading ghosts. */
    private void publishBeliefHighlights(Squad squad) {
        List<CellHighlight> cells = new ArrayList<>();
        for (BelievedContact contact : squad.believedContacts()) {
            Color base = contact.source() == BeliefSource.AUDIO
                    ? HighlightOverlay.COLOR_AUDIO_CONTACT
                    : HighlightOverlay.COLOR_BELIEVED_CONTACT;
            int alpha = Math.round(64f + 191f * contact.confidence());
            alpha = Math.max(0, Math.min(255, alpha));
            Color faded = new Color(base.getRed(), base.getGreen(),
                    base.getBlue(), alpha);
            cells.add(new CellHighlight(contact.lastSeenCellX(),
                    contact.lastSeenCellY(), faded));
        }
        ctx.getHighlights().put(HighlightOverlay.SRC_BELIEVED_CONTACTS, cells);
        AudibleBearing bearing = squad.audibleBearing();
        ctx.getHighlights().put(HighlightOverlay.SRC_HEARD_NOISE,
                bearing == null ? List.of() : List.of(new CellHighlight(
                        bearing.cellX(), bearing.cellY(), HighlightOverlay.COLOR_HEARD_NOISE)));
    }

    /** Publishes the selected squad's bounded tactical-axis trace. */
    private void publishDoctrineHighlight(Squad squad) {
        ctx.getHighlights().put(HighlightOverlay.SRC_CONTACT_DOCTRINE,
                doctrineAxisCells(squad));
    }

    static List<CellHighlight> doctrineAxisCells(Squad squad) {
        SquadContactPicture picture = squad.contactPicture;
        float lengthSquared = picture.axisX() * picture.axisX()
                + picture.axisY() * picture.axisY();
        if (lengthSquared < 1e-4f) return List.of();
        float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
        float axisX = picture.axisX() * inverseLength;
        float axisY = picture.axisY() * inverseLength;

        Color color = switch (picture.doctrine()) {
            case ADVANCE -> HighlightOverlay.COLOR_DOCTRINE_ADVANCE;
            case HOLD -> HighlightOverlay.COLOR_DOCTRINE_HOLD;
            case DISENGAGE -> HighlightOverlay.COLOR_DOCTRINE_DISENGAGE;
        };
        float startX = squad.centroidX - 0.5f;
        float startY = squad.centroidY - 0.5f;
        List<CellHighlight> cells = new ArrayList<>(DOCTRINE_AXIS_TRACE_CELLS);
        int previousX = Integer.MIN_VALUE;
        int previousY = Integer.MIN_VALUE;
        for (int i = 1; i <= DOCTRINE_AXIS_TRACE_CELLS; i++) {
            int cellX = Math.round(startX + axisX * i);
            int cellY = Math.round(startY + axisY * i);
            if (cellX == previousX && cellY == previousY) continue;
            cells.add(new CellHighlight(cellX, cellY, color));
            previousX = cellX;
            previousY = cellY;
        }
        return List.copyOf(cells);
    }

}
