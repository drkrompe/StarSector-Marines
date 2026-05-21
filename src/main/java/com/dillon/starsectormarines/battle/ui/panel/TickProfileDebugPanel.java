package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.profile.TickProfile;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.debug.TickProfileDumper;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

/**
 * Top-left HUD pane: per-phase tick wall-time readout, sourced from
 * {@link BattleSimulation#getTickProfile()}. One row per {@link TickProfile.Phase}
 * showing name, average µs, worst-tick µs, and a horizontal bar sized by
 * the phase's share of total tick time — a quick visual of "where is the
 * tick spent right now."
 *
 * <p>Diagnostic-density layout matches {@link SquadPlanDebugPanel}'s detail
 * mode — Insignia 15 for the rows, Orbitron 20 for the header — so the
 * panel reads as a debug overlay rather than a polished gameplay HUD.
 *
 * <p>Header carries a DUMP button mirroring the squad panel: one click writes
 * the current display window's per-phase numbers to
 * {@code saves/common/starsector_marines/debug/tick_profile_<tickIndex>.json}
 * via {@link TickProfileDumper}. Pairs nicely with the runtime panel for
 * grabbing a snapshot at a moment of interest — late-Conquest unit storm,
 * heavy-FX firefight, mech retreat — for offline diff against future
 * refactor passes (DoD, ECS pivot, parallel update split).
 *
 * <p>Visibility is gated on
 * {@link com.dillon.starsectormarines.DevConfig#PROFILE_TICK_PHASES} so a
 * ship build with the flag off pays no draw cost. Sim-side instrumentation
 * is always on — the lap calls are cheap and we want the numbers warm
 * whenever someone flips the panel on.
 */
public final class TickProfileDebugPanel implements HudPanel {

    private static final float PANEL_W       = 320f;
    private static final float HEADER_H      = 28f;
    private static final float PAD_INNER     = 8f;
    private static final float LINE_H        = 14f;
    /** Pixels reserved between the phase-name column and the right edge for the avg/max numbers + share bar. */
    private static final float NUMERIC_COL_W = 150f;
    private static final float BAR_W         = 70f;
    private static final float BAR_H         = 8f;

    private static final Color BG        = new Color(0x10, 0x18, 0x22, 0xD8);
    private static final Color BORDER    = new Color(0x60, 0x80, 0xA0);
    private static final Color HEADER_FG = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_FG  = new Color(0xA8, 0xB8, 0xC8);
    private static final Color VALUE_FG  = new Color(0xE8, 0xE8, 0xE8);
    private static final Color MAX_FG    = new Color(0x90, 0xA0, 0xB0);
    private static final Color IDLE_FG   = new Color(0x70, 0x70, 0x70);
    private static final Color BAR_BG    = new Color(0x20, 0x2C, 0x3A, 0xC0);
    private static final Color BAR_FG    = new Color(0x60, 0xB0, 0xE0);
    /** Phases that consume >= this share of total tick time get a warning-tinted bar so the eye lands on the cost centers. */
    private static final float BAR_WARN_SHARE = 0.20f;
    private static final Color BAR_WARN  = new Color(0xE0, 0xA0, 0x40);

    // --- Header DUMP button — same shape and color as SquadPlanDebugPanel's. ---
    private static final float DUMP_BTN_W            = 48f;
    private static final float DUMP_BTN_H            = 18f;
    private static final float DUMP_BTN_RIGHT_INSET  = 12f;
    private static final Color DUMP_BTN_BG           = new Color(0x32, 0x22, 0x46, 0xC8);
    private static final Color DUMP_BTN_FG           = new Color(0xC0, 0xA0, 0xE0);
    private static final Color DUMP_BTN_BORDER       = new Color(0x80, 0x60, 0xA0);
    /** Sim-seconds the post-dump status banner persists below the panel header. */
    private static final float DUMP_STATUS_DURATION  = 3.0f;

    /** Square expand/collapse toggle, sits to the left of DUMP. Same H as DUMP so they top/bottom-align in the header. */
    private static final float TOGGLE_BTN_W          = 18f;
    private static final float TOGGLE_BTN_GAP        = 6f;
    private static final Color TOGGLE_BTN_BG         = new Color(0x22, 0x32, 0x46, 0xC8);
    private static final Color TOGGLE_BTN_FG         = new Color(0xC0, 0xD0, 0xE8);
    private static final Color TOGGLE_BTN_BORDER     = new Color(0x60, 0x80, 0xA0);

    private final BattleUiContext ctx;

    /** Collapsed by default — at idle the panel is just a title + total ms readout + DUMP. Click {@code [+]} to unfold the per-phase rows. */
    private boolean expanded = false;

    /** Per-frame button hotspots. Cached on every render so the input pass on the same frame sees fresh geometry; no harm if the panel re-renders before input dispatch since the rect doesn't move within a frame. */
    private float dumpBtnX, dumpBtnY;
    private float toggleBtnX, toggleBtnY;

    /** Post-dump status text drawn below the header for {@link #DUMP_STATUS_DURATION}s after a click. */
    private String dumpStatusMessage;
    private float dumpStatusRemaining;

    public TickProfileDebugPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isVisible() {
        if (!com.dillon.starsectormarines.DevConfig.PROFILE_TICK_PHASES) return false;
        return ctx.getSim() != null;
    }

    @Override
    public void update(float dt) {
        if (dumpStatusMessage != null) {
            dumpStatusRemaining -= dt;
            if (dumpStatusRemaining <= 0f) {
                dumpStatusMessage = null;
            }
        }
    }

    @Override
    public void render(float alphaMult) {
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;
        TickProfile profile = sim.getTickProfile();

        BitmapFont rowFont = Fonts.INSIGNIA_15_AA;
        float x0 = panelX();
        float w  = PANEL_W;
        float h  = panelHeight();
        float y0 = panelY() - h;

        HudDraw.prepBlend();
        HudDraw.filledRect(x0, y0, w, h, BG, alphaMult);
        HudDraw.borderRect(x0, y0, w, h, BORDER, alphaMult);

        // Header band — title left, [+/-] toggle + DUMP buttons right.
        float headerY = y0 + h - HEADER_H;
        Fonts.ORBITRON_20.drawString("TICK PROFILE", x0 + PAD_INNER, headerY + HEADER_H - 6f, HEADER_FG, alphaMult);
        float dumpX = x0 + w - DUMP_BTN_W - DUMP_BTN_RIGHT_INSET;
        float btnY  = headerY + HEADER_H - 8f - DUMP_BTN_H / 2f;
        float toggleX = dumpX - TOGGLE_BTN_W - TOGGLE_BTN_GAP;
        renderToggleButton(rowFont, toggleX, btnY, alphaMult);
        renderDumpButton(rowFont, dumpX, btnY, alphaMult);

        // Summary line — total ms + tick-rate equivalent + sample/unit counts.
        // Doubles as the post-dump status banner for DUMP_STATUS_DURATION.
        long totalNs = profile.totalAvgNanos();
        int samples = profile.sampleCount();
        String summary;
        if (dumpStatusMessage != null) {
            summary = dumpStatusMessage;
        } else if (samples == 0) {
            summary = "warming up (window " + TickProfile.WINDOW_TICKS + ")";
        } else {
            double ms = totalNs / 1_000_000.0;
            double hz = ms > 0.0 ? (1000.0 / ms) : 0.0;
            summary = String.format("%.2f ms / %.0f Hz  (%du)",
                    ms, hz, sim.getUnits().size());
        }
        Color summaryColor = (samples == 0) ? IDLE_FG : LABEL_FG;
        rowFont.drawString(summary, x0 + PAD_INNER, headerY - 4f, summaryColor, alphaMult);

        if (!expanded) return;

        // Phase rows — execution order so the panel reads top-to-bottom as
        // the tick chain. Skipped entirely in collapsed mode (above guard).
        float rowY = headerY - 4f - LINE_H;
        float nameX     = x0 + PAD_INNER;
        float valueRightX = x0 + w - PAD_INNER - BAR_W - 6f;
        float barX      = x0 + w - PAD_INNER - BAR_W;

        for (TickProfile.Phase p : TickProfile.Phase.VALUES) {
            long avgNs = profile.avgNanos(p);
            long maxNs = profile.maxNanos(p);
            float share = (totalNs > 0) ? (float) avgNs / totalNs : 0f;

            rowFont.drawString(p.name(), nameX, rowY, LABEL_FG, alphaMult);

            // Two numeric values right-aligned: avg (bright) followed by max
            // (dim, in parens). Right-aligned to the column boundary so the
            // decimal points roughly stack across rows.
            String avgStr = formatUs(avgNs);
            String maxStr = "(" + formatUs(maxNs) + ")";
            float maxStrW = rowFont.measureWidth(maxStr);
            float avgStrW = rowFont.measureWidth(avgStr);
            float maxX = valueRightX - maxStrW;
            float avgX = maxX - 4f - avgStrW;
            rowFont.drawString(avgStr, avgX, rowY, VALUE_FG, alphaMult);
            rowFont.drawString(maxStr, maxX, rowY, MAX_FG, alphaMult);

            // Share bar. Background always drawn (so the bar column has visual
            // structure even for zero-cost phases); foreground sized by share.
            float barY = rowY - BAR_H + 2f;
            HudDraw.filledRect(barX, barY, BAR_W, BAR_H, BAR_BG, alphaMult);
            if (share > 0f) {
                Color fg = (share >= BAR_WARN_SHARE) ? BAR_WARN : BAR_FG;
                HudDraw.filledRect(barX, barY, BAR_W * Math.min(1f, share), BAR_H, fg, alphaMult);
            }

            rowY -= LINE_H;
        }
    }

    /** Draws the +/- expand/collapse button and records its hotspot. Glyph flips with {@link #expanded} state. */
    private void renderToggleButton(BitmapFont font, float x, float y, float alphaMult) {
        HudDraw.filledRect(x, y, TOGGLE_BTN_W, DUMP_BTN_H, TOGGLE_BTN_BG, alphaMult);
        HudDraw.borderRect(x, y, TOGGLE_BTN_W, DUMP_BTN_H, TOGGLE_BTN_BORDER, alphaMult);
        String glyph = expanded ? "-" : "+";
        // Center the glyph by eyeballed offsets — Insignia 15 is small enough
        // that single-char centering is forgiving.
        font.drawString(glyph, x + 6f, y + DUMP_BTN_H - 3f, TOGGLE_BTN_FG, alphaMult);
        toggleBtnX = x;
        toggleBtnY = y;
    }

    /**
     * Formats a nanosecond duration as either microseconds (xxx.x µs) or
     * milliseconds (x.xx ms) depending on magnitude. Sub-1µs values clamp to
     * "0.0 µs" rather than showing noise. Width is bounded so the right-align
     * stays tidy across rows.
     */
    private static String formatUs(long ns) {
        if (ns < 1_000L) return "0.0 us";
        if (ns >= 1_000_000L) return String.format("%.2f ms", ns / 1_000_000.0);
        return String.format("%.1f us", ns / 1_000.0);
    }

    private void renderDumpButton(BitmapFont font, float x, float y, float alphaMult) {
        HudDraw.filledRect(x, y, DUMP_BTN_W, DUMP_BTN_H, DUMP_BTN_BG, alphaMult);
        HudDraw.borderRect(x, y, DUMP_BTN_W, DUMP_BTN_H, DUMP_BTN_BORDER, alphaMult);
        font.drawString("DUMP", x + 6f, y + DUMP_BTN_H - 3f, DUMP_BTN_FG, alphaMult);
        dumpBtnX = x;
        dumpBtnY = y;
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        if (!isVisible()) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isLMBDownEvent()) continue;
            float px = e.getX();
            float py = e.getY();
            // Toggle button checked before DUMP — the two rects are adjacent
            // but disjoint, so order doesn't actually matter, but checking the
            // smaller hotspot first keeps the early-out cheap.
            if (px >= toggleBtnX && px < toggleBtnX + TOGGLE_BTN_W
                    && py >= toggleBtnY && py < toggleBtnY + DUMP_BTN_H) {
                expanded = !expanded;
                e.consume();
                continue;
            }
            if (px >= dumpBtnX && px < dumpBtnX + DUMP_BTN_W
                    && py >= dumpBtnY && py < dumpBtnY + DUMP_BTN_H) {
                triggerDump();
                e.consume();
            }
        }
    }

    private void triggerDump() {
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;
        String path = TickProfileDumper.dump(sim);
        dumpStatusMessage = path != null
                ? "(dumped to common/" + path + ")"
                : "(dump failed — see log)";
        dumpStatusRemaining = DUMP_STATUS_DURATION;
    }

    // -----------------------------------------------------------------------
    // Layout — top-left, anchored under the controls strip. Leaves the
    // bottom-left (squad overview) and bottom-right (GOAP debug) clear, plus
    // the centered battlefield itself.
    // -----------------------------------------------------------------------

    private float panelX() {
        BattleLayout l = ctx.getLayout();
        return l.controlsX;
    }

    /** Y of the panel's TOP edge — render() builds the bottom-left from here. */
    private float panelY() {
        BattleLayout l = ctx.getLayout();
        return l.controlsY - BattleLayout.CONTROLS_GAP;
    }

    private float panelHeight() {
        // Header band + summary line are always present. Per-phase rows only
        // when expanded — collapsed mode is a compact two-line panel.
        float base = HEADER_H + LINE_H + PAD_INNER;
        if (!expanded) return base;
        return base + TickProfile.Phase.VALUES.length * LINE_H;
    }
}
