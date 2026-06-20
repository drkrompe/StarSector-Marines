package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detail view for the squad currently in {@link Selection}. Renders one row
 * per marine — HP bar, primary symbol, secondary symbol + ammo, and the
 * role badge for non-combatant slots (PLANTER / KIT_RETRIEVER / VIP).
 *
 * <p>Header carries a small back chip on the left ({@code "<- BACK"}) that
 * clears the selection and routes the HUD back to {@link SquadOverviewPanel}.
 * The same dock slot as the overview, so visibility is keyed off
 * {@link Selection#hasSquadSelection()}.
 */
public final class SquadDetailPanel implements HudPanel {

    private static final float PANEL_W       = 380f;
    private static final float HEADER_H      = 32f;
    private static final float ROW_H         = 28f;
    private static final float PAD_INNER     = 8f;
    private static final float BACK_W        = 64f;
    private static final float BACK_H        = 22f;
    private static final float MORALE_BAR_W  = 110f;
    private static final float MORALE_BAR_H  = 8f;

    private static final float COL_NAME      = 0f;
    private static final float COL_HP_BAR    = 56f;
    private static final float COL_HP_TEXT   = 156f;
    private static final float COL_PRIMARY   = 218f;
    private static final float COL_SECONDARY = 268f;
    private static final float COL_ROLE      = 338f;
    private static final float HP_BAR_W      = 92f;
    private static final float HP_BAR_H      = 10f;

    private static final Color BG            = new Color(0x10, 0x18, 0x22, 0xD8);
    private static final Color BORDER        = new Color(0x60, 0x80, 0xA0);
    private static final Color HEADER_FG     = new Color(0xC8, 0xE0, 0xFF);
    private static final Color BACK_BG       = new Color(0x22, 0x32, 0x46, 0xE8);
    private static final Color BACK_BG_HOVER = new Color(0x36, 0x4E, 0x68, 0xF0);
    private static final Color NAME_FG       = new Color(0xE0, 0xE0, 0xE0);

    private final BattleUiContext ctx;
    /**
     * Per-frame snapshot of the selected squad's marine rows, captured in
     * {@link #update} (pre-advance) and read in {@link #render} (post-advance).
     * Snapshots the displayed <em>values</em> rather than holding live
     * {@link Entity} refs: a member killed during the frame's {@code advance()} is
     * released from the registry by render time, and reading its Group-C hp /
     * Group-S maxHp accessors would fail loud. Freezing the row at update keeps
     * the just-killed member's last bar on screen for that frame without any
     * post-release accessor read.
     */
    private final List<MemberRow> rows = new ArrayList<>();
    private Squad currentSquad;
    private boolean backHovered;

    /**
     * One marine's render data, frozen at snapshot time. {@code primary} /
     * {@code secondary} are the loadout descriptors (immutable, not
     * registry-backed — safe to hold); hp/maxHp/ammo/role are copied by value.
     */
    private record MemberRow(float hp, float maxHp, MarineWeapon primary,
                             MarineSecondary secondary, int secondaryAmmo, UnitRole role) {}

    public SquadDetailPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isVisible() {
        return ctx.getSelection().hasSquadSelection() && currentSquad != null;
    }

    @Override
    public void update(float dt) {
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        rows.clear();
        currentSquad = null;
        BattleSimulation sim = ctx.getSim();
        Selection sel = ctx.getSelection();
        if (sim == null || !sel.hasSquadSelection()) return;

        int squadId = sel.getSelectedSquadId();
        Squad s = sim.getSquad(squadId);
        // Auto-clear: if the squad was wiped (or never existed), drop selection
        // so the overview pops back. Same instinct as a real RTS — losing the
        // last member of the selected unit kicks you out of the detail panel.
        if (s == null || s.aliveMembers <= 0) {
            sel.clear();
            return;
        }
        // Non-marine selections (defender squads picked from the world for
        // debug) don't populate this panel — it's marine-only by design — but
        // we leave the selection alone so SquadPlanDebugPanel's filtered detail
        // mode picks it up.
        if (s.faction != Faction.MARINE) return;
        currentSquad = s;
        // Gather the live members, sort for stable row order, then freeze each
        // into a MemberRow. All accessor reads happen here, while every unit is
        // still registered — render() touches only the frozen values.
        List<Entity> live = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            if (u.squadId != squadId) continue;
            live.add(u);
        }
        // Leader first (if set + alive), then stable by unit id so the row
        // order is deterministic across frames.
        live.sort(Comparator
                .comparing((Entity u) -> currentSquad.leaderId == u.entityId ? 0 : 1)
                .thenComparing(u -> u.id));
        for (Entity u : live) {
            boolean hasSec = sim.world().hasSecondaryWeapon(u.entityId);
            rows.add(new MemberRow(sim.world().hp(u.entityId), sim.world().maxHp(u.entityId), u.primaryWeapon,
                    hasSec ? sim.world().secondaryWeapon(u.entityId) : null,
                    hasSec ? sim.world().secondaryAmmo(u.entityId) : 0, u.role));
        }
    }

    private float panelHeight() {
        return HEADER_H + rows.size() * ROW_H + PAD_INNER;
    }

    private float panelX() {
        return ctx.getLayout().backX;
    }

    private float panelY() {
        BattleLayout l = ctx.getLayout();
        return l.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
    }

    private boolean backHit(float px, float py) {
        float x0 = panelX();
        float y0 = panelY();
        float h = panelHeight();
        float headerY = y0 + h - HEADER_H;
        float bx = x0 + PAD_INNER;
        float by = headerY + (HEADER_H - BACK_H) * 0.5f;
        return px >= bx && px < bx + BACK_W && py >= by && py < by + BACK_H;
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

        // Header: back chip + "SQUAD N · alive/peak"
        float headerY = y0 + h - HEADER_H;
        float bx = x0 + PAD_INNER;
        float by = headerY + (HEADER_H - BACK_H) * 0.5f;
        HudDraw.filledRect(bx, by, BACK_W, BACK_H, backHovered ? BACK_BG_HOVER : BACK_BG, alphaMult);
        HudDraw.borderRect(bx, by, BACK_W, BACK_H, BORDER, alphaMult);
        Fonts.ORBITRON_20.drawString("< BACK", bx + 6f, by + BACK_H - 4f, HEADER_FG, alphaMult);

        String title = "SQUAD " + currentSquad.id
                + "   " + currentSquad.aliveMembers
                + "/" + Math.max(currentSquad.aliveMembers, currentSquad.originalSize);
        Fonts.ORBITRON_20.drawString(title, bx + BACK_W + 12f, headerY + HEADER_H - 8f, HEADER_FG, alphaMult);

        // Squad morale bar — right edge of the header. Fill is morale/cap;
        // break tick stays at MORALE_BROKEN_THRESHOLD (fraction of cap);
        // border flips red when the squad's moraleBroken flag is set so the
        // bar reads as an alert state at a glance.
        float cap = (currentSquad.originalSize > 0)
                ? (float) currentSquad.aliveMembers / currentSquad.originalSize
                : 1f;
        float moraleBarX = x0 + w - PAD_INNER - MORALE_BAR_W;
        float moraleBarY = headerY + (HEADER_H - MORALE_BAR_H) * 0.5f;
        HudDraw.moraleBar(moraleBarX, moraleBarY, MORALE_BAR_W, MORALE_BAR_H,
                currentSquad.morale, cap, currentSquad.moraleBroken,
                SquadMoraleSystem.MORALE_BROKEN_THRESHOLD, alphaMult);

        // Marine rows.
        for (int i = 0; i < rows.size(); i++) {
            MemberRow m = rows.get(i);
            float rowY = headerY - (i + 1) * ROW_H;
            float textBaseline = rowY + ROW_H - 6f;
            float rowLeft = x0 + PAD_INNER;

            String tag = "M-" + (i + 1);
            Fonts.ORBITRON_20.drawString(tag, rowLeft + COL_NAME, textBaseline, NAME_FG, alphaMult);

            float memberHp = m.hp();
            float memberMaxHp = m.maxHp();
            float frac = memberMaxHp > 0f ? memberHp / memberMaxHp : 0f;
            float barY = rowY + (ROW_H - HP_BAR_H) * 0.5f;
            HudDraw.prepBlend();
            HudDraw.hpBar(rowLeft + COL_HP_BAR, barY, HP_BAR_W, HP_BAR_H, frac, alphaMult);

            int curHp = (int) Math.ceil(Math.max(0f, memberHp));
            int maxHp = (int) Math.ceil(Math.max(1f, memberMaxHp));
            Fonts.ORBITRON_20.drawString(curHp + "/" + maxHp,
                    rowLeft + COL_HP_TEXT, textBaseline, NAME_FG, alphaMult);

            String primary = WeaponSymbols.primaryAbbrev(m.primary());
            Fonts.ORBITRON_20.drawString(primary,
                    rowLeft + COL_PRIMARY, textBaseline,
                    WeaponSymbols.primaryColor(m.primary()), alphaMult);

            if (m.secondary() != null) {
                String sec = WeaponSymbols.secondaryAbbrev(m.secondary());
                if (sec != null) {
                    String text = m.secondaryAmmo() > 0 ? sec + "x" + m.secondaryAmmo() : sec;
                    Color c = m.secondaryAmmo() > 0 ? WeaponSymbols.SECONDARY_FG : WeaponSymbols.SECONDARY_EMPTY;
                    Fonts.ORBITRON_20.drawString(text, rowLeft + COL_SECONDARY, textBaseline, c, alphaMult);
                }
            }

            String role = WeaponSymbols.roleBadge(m.role());
            if (role != null) {
                Fonts.ORBITRON_20.drawString(role,
                        rowLeft + COL_ROLE, textBaseline, WeaponSymbols.ROLE_BADGE_FG, alphaMult);
            }
        }
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        backHovered = false;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            int px = e.getX();
            int py = e.getY();
            if (e.isMouseMoveEvent()) {
                backHovered = backHit(px, py);
                continue;
            }
            if (e.isLMBDownEvent()) {
                if (backHit(px, py)) {
                    ctx.getSelection().clear();
                    e.consume();
                    continue;
                }
                // Swallow plain clicks inside the panel body so they don't
                // fall through to a (future) world-picker on the cells the
                // panel currently overlays.
                float x0 = panelX();
                float y0 = panelY();
                if (px >= x0 && px < x0 + PANEL_W
                        && py >= y0 && py < y0 + panelHeight()) {
                    e.consume();
                }
            }
        }
    }
}
