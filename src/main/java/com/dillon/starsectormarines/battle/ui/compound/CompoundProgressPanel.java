package com.dillon.starsectormarines.battle.ui.compound;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
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
 * Top-of-screen aggregate progress strip — the "3 of 5 supply hubs
 * captured" read at a glance. Reads {@link CompoundService} each frame and
 * draws a count + a horizontal strip of per-compound state chips.
 *
 * <p>Pure decoration; no input handled. Hides when no compounds are
 * registered (non-Conquest missions) so it doesn't clutter the HUD on
 * other mission types. Sibling to {@link CompoundMarkerRenderer} which
 * renders the per-compound world-anchored markers — together they form
 * the slice-2 visibility layer (primary read = world, at-a-glance =
 * this strip).
 */
public final class CompoundProgressPanel implements HudPanel {

    /** Wider than the debug strip — leaves room for 8+ compound chips alongside the count label without crowding (the keep design can register that many). */
    private static final float PANEL_W = 280f;
    private static final float PANEL_H = 36f;
    private static final float PADDING = 8f;
    /** Pixels between the right edge of the panel and the right edge of the grid area — keeps the panel off the screen edge by the same gap the other top-of-screen HUD chrome uses. */
    private static final float RIGHT_INSET = 12f;
    /** Each per-compound state chip in the strip. */
    private static final float CHIP_SIZE = 12f;
    private static final float CHIP_GAP = 4f;

    private static final Color PANEL_BG = new Color(0x14, 0x18, 0x20, 0xCC);
    private static final Color PANEL_BORDER = new Color(0x55, 0x70, 0x90, 0xE0);
    private static final Color LABEL_TEXT = new Color(0xE0, 0xE6, 0xEE);
    // Mirrors the world-marker palette in CompoundMarkerRenderer so the
    // strip + world reads carry the same color grammar.
    private static final Color CHIP_DEFENDER  = new Color(0xE0, 0x40, 0x40);
    private static final Color CHIP_CONTESTED = new Color(0xFF, 0xC0, 0x40);
    private static final Color CHIP_MARINE    = new Color(0x4A, 0xB0, 0xFF);

    private final BattleUiContext ctx;
    private final BitmapFont font;

    public CompoundProgressPanel(BattleUiContext ctx) {
        this.ctx = ctx;
        this.font = Fonts.ORBITRON_20;
    }

    @Override
    public boolean isVisible() {
        if (ctx.getLayout() == null) return false;
        if (ctx.getSim() == null) return false;
        // Hide on missions that don't have compounds (non-Conquest) so the
        // strip stays a Conquest-specific surface without explicit
        // mission-type plumbing.
        return !ctx.getSim().getCompoundService().getRecords().isEmpty();
    }

    @Override
    public void update(float dt) { /* no per-frame state */ }

    @Override
    public void render(float alphaMult) {
        CompoundService service = ctx.getSim().getCompoundService();
        if (service.getRecords().isEmpty()) return;
        font.ensureLoaded();

        BattleLayout l = ctx.getLayout();
        // Anchor top-right under the controls strip. DebugTogglesPanel
        // owns top-center; this strip is the player-facing read so the
        // edge slot keeps it visible without crowding the dev panel.
        float x = l.gridX + l.gridW - PANEL_W - RIGHT_INSET;
        float y = l.controlsY - BattleLayout.CONTROLS_GAP - PANEL_H;

        // Background plate.
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(PANEL_BG.getRed() / 255f, PANEL_BG.getGreen() / 255f, PANEL_BG.getBlue() / 255f,
                PANEL_BG.getAlpha() / 255f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,           y);
        glVertex2f(x + PANEL_W, y);
        glVertex2f(x + PANEL_W, y + PANEL_H);
        glVertex2f(x,           y + PANEL_H);
        glEnd();

        // Outer border.
        glColor4f(PANEL_BORDER.getRed() / 255f, PANEL_BORDER.getGreen() / 255f,
                PANEL_BORDER.getBlue() / 255f, PANEL_BORDER.getAlpha() / 255f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,           y);
        glVertex2f(x + PANEL_W, y);
        glVertex2f(x + PANEL_W, y + PANEL_H);
        glVertex2f(x,           y + PANEL_H);
        glEnd();

        // Count of marine-held compounds vs total.
        int total = service.getRecords().size();
        int captured = 0;
        for (CompoundService.Record r : service.getRecords()) {
            if (r.state == CompoundService.CompoundState.MARINE_HELD) captured++;
        }
        String label = "Supply hubs: " + captured + " / " + total;
        font.drawString(label, x + PADDING, y + PANEL_H - 6f, LABEL_TEXT, alphaMult);

        // Per-compound chip strip — right-justified inside the panel. Each
        // chip is colour-coded by state; insertion order is registration
        // order (LinkedHashMap), which keeps the strip stable across frames.
        float chipsW = total * CHIP_SIZE + (total - 1) * CHIP_GAP;
        float chipX = x + PANEL_W - PADDING - chipsW;
        float chipY = y + (PANEL_H - CHIP_SIZE) / 2f;
        for (CompoundService.Record r : service.getRecords()) {
            Color chipColor = switch (r.state) {
                case DEFENDER_HELD -> CHIP_DEFENDER;
                case CONTESTED     -> CHIP_CONTESTED;
                case MARINE_HELD   -> CHIP_MARINE;
            };
            drawChip(chipX, chipY, chipColor, r.captureProgress, alphaMult);
            // Subtle kind tick on top of the chip — different chips read
            // for different compound kinds without dominating the chip.
            drawKindTick(chipX, chipY, r.node.kind, alphaMult);
            chipX += CHIP_SIZE + CHIP_GAP;
        }
    }

    /** Solid-coloured square with a thin inner bar showing in-flight capture progress. Progress is only non-zero during CONTESTED; both terminal states (DEFENDER_HELD, MARINE_HELD) read as 0 so the chip body's faction colour is the read, not the overlay. */
    private static void drawChip(float x, float y, Color color, float progress, float alphaMult) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                0.85f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,               y);
        glVertex2f(x + CHIP_SIZE,   y);
        glVertex2f(x + CHIP_SIZE,   y + CHIP_SIZE);
        glVertex2f(x,               y + CHIP_SIZE);
        glEnd();

        // Progress bar along the bottom edge — brightens the chip from the
        // bottom up as capture-progress accumulates. Mirrors the
        // capture-arc read on the world marker.
        if (progress > 0f) {
            float h = CHIP_SIZE * progress;
            glColor4f(1f, 1f, 1f, 0.4f * alphaMult);
            glBegin(GL_QUADS);
            glVertex2f(x,               y);
            glVertex2f(x + CHIP_SIZE,   y);
            glVertex2f(x + CHIP_SIZE,   y + h);
            glVertex2f(x,               y + h);
            glEnd();
        }

        // Chip border.
        glColor4f(0f, 0f, 0f, 0.6f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,               y);
        glVertex2f(x + CHIP_SIZE,   y);
        glVertex2f(x + CHIP_SIZE,   y + CHIP_SIZE);
        glVertex2f(x,               y + CHIP_SIZE);
        glEnd();
    }

    /** Single-pixel-style marker in the upper corner of a chip — distinguishes COMMAND_POST / BARRACKS / ARMORY without text. */
    private static void drawKindTick(float x, float y, TacticalNode.Kind kind, float alphaMult) {
        // Different anchor positions per kind — top-left, top-center,
        // top-right — keeps each kind visually distinct at chip scale.
        float dotR = 1.5f;
        float dx, dy;
        switch (kind) {
            case COMMAND_POST -> { dx = CHIP_SIZE * 0.5f; dy = CHIP_SIZE - dotR - 1f; }  // top-center
            case BARRACKS     -> { dx = dotR + 1f;        dy = CHIP_SIZE - dotR - 1f; }  // top-left
            case ARMORY       -> { dx = CHIP_SIZE - dotR - 1f; dy = CHIP_SIZE - dotR - 1f; }  // top-right
            default           -> { return; }
        }
        glColor4f(1f, 1f, 1f, 0.85f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x + dx - dotR, y + dy - dotR);
        glVertex2f(x + dx + dotR, y + dy - dotR);
        glVertex2f(x + dx + dotR, y + dy + dotR);
        glVertex2f(x + dx - dotR, y + dy + dotR);
        glEnd();
    }

    @Override
    public void handleInput(List<InputEventAPI> events) { /* read-only HUD strip */ }
}
