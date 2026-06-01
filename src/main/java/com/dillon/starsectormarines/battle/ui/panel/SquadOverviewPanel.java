package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bottom-left HUD pane listing the player's deployed squads. One row per
 * squad: id, alive/peak member count, alert color dot, weapon-loadout
 * summary. Clicking a row writes the squad id into {@link Selection} — the
 * sibling {@link SquadDetailPanel} swaps in based on that state.
 *
 * <p>Visible only while {@link Selection#hasSquadSelection()} is false, so
 * this and {@link SquadDetailPanel} share the same dock slot without an
 * explicit modal-stack manager.
 */
public final class SquadOverviewPanel implements HudPanel {

    private static final float PANEL_W       = 320f;
    private static final float HEADER_H      = 28f;
    private static final float ROW_H         = 34f;
    private static final float PAD_INNER     = 8f;
    private static final float DOT_RADIUS    = 5f;
    private static final float MORALE_BAR_H  = 4f;
    private static final float MORALE_BAR_PAD_Y = 2f;

    private static final Color BG            = new Color(0x10, 0x18, 0x22, 0xD8);
    private static final Color BG_HOVER      = new Color(0x20, 0x30, 0x44, 0xE0);
    private static final Color BORDER        = new Color(0x60, 0x80, 0xA0);
    private static final Color HEADER_FG     = new Color(0xC8, 0xE0, 0xFF);
    private static final Color COUNT_FG      = new Color(0xE0, 0xE0, 0xE0);

    private static final Color ALERT_UNAWARE    = new Color(0x60, 0xC0, 0x60);
    private static final Color ALERT_SUSPICIOUS = new Color(0xE0, 0xC0, 0x40);
    private static final Color ALERT_ENGAGED    = new Color(0xE0, 0x60, 0x40);

    private final BattleUiContext ctx;
    /** Cached per-frame snapshot — built in update(), consumed by render() / handleInput(). */
    private final List<Squad> playerSquads = new ArrayList<>();
    /** Equipment-summary string per cached squad, in the same order. */
    private final List<String> equipSummaries = new ArrayList<>();
    private int hoveredRow = -1;

    public SquadOverviewPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isVisible() {
        return !ctx.getSelection().hasSquadSelection() && !playerSquads.isEmpty();
    }

    @Override
    public void update(float dt) {
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        playerSquads.clear();
        equipSummaries.clear();
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;

        // Bucket marines by squad so the equipment summary is one pass over
        // the unit list rather than nSquads × nUnits.
        Map<Integer, int[]> weaponCounts = new HashMap<>(); // 0: RIF, 1: SMG, 2: DMR, 3: RKT
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Unit u = sim.liveUnitAt(i);
            if (u.faction != Faction.MARINE || u.squadId == Unit.NO_SQUAD) continue;
            int[] counts = weaponCounts.computeIfAbsent(u.squadId, k -> new int[4]);
            if (u.primaryWeapon != null) {
                switch (u.primaryWeapon) {
                    case PULSE_RIFLE: counts[0]++; break;
                    case SMG:         counts[1]++; break;
                    case DMR:         counts[2]++; break;
                }
            } else {
                counts[0]++;
            }
            if (u.secondaryWeapon != null && u.secondaryAmmo > 0) counts[3]++;
        }

        for (Squad s : sim.getSquads()) {
            if (s.faction != Faction.MARINE) continue;
            if (s.aliveMembers <= 0) continue;
            playerSquads.add(s);
        }
        playerSquads.sort(Comparator.comparingInt(s -> s.id));

        StringBuilder sb = new StringBuilder();
        for (Squad s : playerSquads) {
            int[] c = weaponCounts.getOrDefault(s.id, new int[4]);
            sb.setLength(0);
            appendCount(sb, "RIF", c[0]);
            appendCount(sb, "SMG", c[1]);
            appendCount(sb, "DMR", c[2]);
            appendCount(sb, "RKT", c[3]);
            equipSummaries.add(sb.toString());
        }
    }

    private static void appendCount(StringBuilder sb, String tag, int n) {
        if (n <= 0) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(tag);
        if (n > 1) sb.append('x').append(n);
    }

    private float panelHeight() {
        return HEADER_H + playerSquads.size() * ROW_H + PAD_INNER;
    }

    private float panelX() {
        return ctx.getLayout().backX;
    }

    private float panelY() {
        BattleLayout l = ctx.getLayout();
        return l.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
    }

    private int rowAt(float px, float py) {
        if (playerSquads.isEmpty()) return -1;
        float x0 = panelX();
        float y0 = panelY();
        float w = PANEL_W;
        float h = panelHeight();
        if (px < x0 || px >= x0 + w || py < y0 || py >= y0 + h) return -1;
        // Rows stack downward from below the header. y0 is the panel's bottom edge.
        float headerBottom = y0 + h - HEADER_H;
        if (py >= headerBottom) return -1; // hit the header
        int row = (int) Math.floor((headerBottom - py) / ROW_H);
        if (row < 0 || row >= playerSquads.size()) return -1;
        return row;
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

        // Header.
        float headerY = y0 + h - HEADER_H;
        Fonts.ORBITRON_20.drawString("SQUADS", x0 + PAD_INNER, headerY + HEADER_H - 6f, HEADER_FG, alphaMult);

        // Rows.
        for (int i = 0; i < playerSquads.size(); i++) {
            Squad s = playerSquads.get(i);
            float rowY = headerY - (i + 1) * ROW_H;
            boolean hovered = (i == hoveredRow);
            if (hovered) {
                HudDraw.filledRect(x0 + 1, rowY, w - 2, ROW_H, BG_HOVER, alphaMult);
            }

            // Text in the top portion of the row (same "6 below row top" as
            // before); morale bar pinned to the bottom strip.
            float textBaseline = rowY + ROW_H - 6f;

            String label = "SQ-" + s.id;
            Fonts.ORBITRON_20.drawString(label, x0 + PAD_INNER, textBaseline, HEADER_FG, alphaMult);

            String count = s.aliveMembers + "/" + Math.max(s.aliveMembers, s.originalSize);
            Fonts.ORBITRON_20.drawString(count, x0 + 70f, textBaseline, COUNT_FG, alphaMult);

            float dotX = x0 + 138f;
            // Center the alert dot vertically with the text (text is at
            // baseline rowY + ROW_H - 6, glyphs span ~10 below baseline).
            float dotY = textBaseline - 5f;
            HudDraw.disc(dotX, dotY, DOT_RADIUS, alertColor(s.alertLevel), alphaMult, 14);

            String equip = equipSummaries.get(i);
            Fonts.ORBITRON_20.drawString(equip, x0 + 160f, textBaseline, COUNT_FG, alphaMult);

            // Morale bar — bottom strip of the row, full width minus padding.
            // Fill is morale/cap, so a lone survivor at full recovery reads
            // the same as a fresh squad at full recovery. Break tick stays
            // at MORALE_BROKEN_THRESHOLD (fraction of cap); border turns red
            // when the hysteresis flag is set.
            float cap = (s.originalSize > 0)
                    ? (float) s.aliveMembers / s.originalSize
                    : 1f;
            float barX = x0 + PAD_INNER;
            float barY = rowY + MORALE_BAR_PAD_Y;
            float barW = PANEL_W - 2f * PAD_INNER;
            HudDraw.moraleBar(barX, barY, barW, MORALE_BAR_H,
                    s.morale, cap, s.moraleBroken,
                    SquadMoraleSystem.MORALE_BROKEN_THRESHOLD, alphaMult);
        }
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
        hoveredRow = -1;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            int px = e.getX();
            int py = e.getY();
            if (e.isMouseMoveEvent()) {
                hoveredRow = rowAt(px, py);
                continue;
            }
            if (e.isLMBDownEvent()) {
                int row = rowAt(px, py);
                if (row >= 0) {
                    ctx.getSelection().selectSquad(playerSquads.get(row).id);
                    e.consume();
                }
            }
        }
    }
}
