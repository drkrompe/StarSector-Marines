package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.BattleSetup;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
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
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Top-down 2D auto-battler screen. Owns a layout + speed control state and
 * reads the active {@link BattleSimulation} from {@link MarineOpsContext}.
 *
 * <p>Loop: {@link #advance(float)} multiplies real dt by the player's speed
 * multiplier (pause / 1x / 2x / 4x) and feeds it to the sim, which catches up
 * in 1/30s ticks. {@link #render(float)} draws floor + walls + units + HP bars,
 * plus a centered Victory/Defeat banner when the sim completes.
 *
 * <p>Back returns to {@link ScreenId#MISSION_SELECT}. A dedicated RESULTS screen
 * (casualties, XP, payout) comes after MVP — for now the player just sees the
 * banner and backs out.
 */
public class BattleScreen implements Screen {

    private static final Color FLOOR_COLOR    = new Color(0x18, 0x22, 0x30);
    private static final Color WALL_COLOR     = new Color(0x06, 0x0A, 0x10);
    private static final Color GRID_LINE      = new Color(0x25, 0x32, 0x44);
    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
    private static final Color HP_BG          = new Color(0x60, 0x20, 0x20);
    private static final Color HP_FG          = new Color(0x40, 0xC0, 0x40);
    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color ACTIVE_SPEED   = new Color(0xFF, 0xB8, 0x00);
    private static final Color BANNER_BG      = new Color(0x10, 0x14, 0x1E);
    private static final Color VICTORY_COLOR  = new Color(0x80, 0xE0, 0x80);
    private static final Color DEFEAT_COLOR   = new Color(0xE0, 0x60, 0x60);

    private static final float UNIT_FRAC      = 0.70f; // unit quad is 70% of cell
    private static final float HP_BAR_H       = 3f;
    private static final float HP_BAR_GAP     = 3f;
    private static final float SPEED_BTN_W    = 60f;
    private static final float SPEED_BTN_H    = 32f;
    private static final float SPEED_BTN_GAP  = 6f;
    private static final float SPEED_MARK_H   = 3f;

    private static final float[] SPEED_OPTIONS = {0f, 1f, 2f, 4f};
    private static final String[] SPEED_KEYS   = {
            "battleSpeedPause", "battleSpeed1x", "battleSpeed2x", "battleSpeed4x"
    };

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BattleLayout layout;
    private float speedMultiplier = 1f;
    /** Pixel x-center of each speed button, captured at layout time for the active-marker dot. */
    private final float[] speedBtnCenterX = new float[SPEED_OPTIONS.length];
    private float speedBtnBottomY;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.speedMultiplier = 1f;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        BattleSimulation sim = ctx.getBattleSimulation();
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        layout = new BattleLayout(position, gridW, gridH);

        // Back button (bottom-left)
        ButtonWidget back = new ButtonWidget(layout.backX, layout.backY,
                BattleLayout.BACK_W, BattleLayout.BACK_H,
                () -> ctx.goTo(ScreenId.MISSION_SELECT));
        widgets.add(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("actionBack"),
                layout.backX + 12f, layout.backY + BattleLayout.BACK_H - 6f, HEADER_COLOR));

        // Speed buttons (top-right of controls strip)
        float rowW = SPEED_OPTIONS.length * SPEED_BTN_W
                + (SPEED_OPTIONS.length - 1) * SPEED_BTN_GAP;
        float startX = layout.controlsX + layout.controlsW - rowW;
        float btnY = layout.controlsY + (layout.controlsH - SPEED_BTN_H) / 2f;
        speedBtnBottomY = btnY;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            float bx = startX + i * (SPEED_BTN_W + SPEED_BTN_GAP);
            final float target = SPEED_OPTIONS[i];
            ButtonWidget btn = new ButtonWidget(bx, btnY, SPEED_BTN_W, SPEED_BTN_H,
                    () -> speedMultiplier = target);
            widgets.add(btn);
            // Center the label inside the button.
            String label = Strings.get(SPEED_KEYS[i]);
            float labelW = Fonts.ORBITRON_20.measureWidth(label);
            float labelX = bx + (SPEED_BTN_W - labelW) / 2f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, label,
                    labelX, btnY + SPEED_BTN_H - 6f, HEADER_COLOR));
            speedBtnCenterX[i] = bx + SPEED_BTN_W / 2f;
        }
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        if (sim == null) return;
        if (speedMultiplier > 0f) {
            sim.advance(dt * speedMultiplier);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (layout == null) return;
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;

        if (sim != null) {
            renderGrid(sim.getGrid(), alphaMult);
            renderUnits(sim.getUnits(), alphaMult);
        }

        renderSpeedMarker(alphaMult);

        if (sim != null && sim.isComplete()) {
            renderBanner(sim.getWinner(), alphaMult);
        }

        widgets.render(alphaMult);
    }

    // ---- rendering ---------------------------------------------------------

    private void renderGrid(NavigationGrid grid, float alphaMult) {
        // One big GL_QUADS pass — walls and floor in a single bind-free run.
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                Color c = grid.isWalkable(x, y) ? FLOOR_COLOR : WALL_COLOR;
                glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
                float x0 = layout.gridX + x * layout.cellSize;
                float y0 = layout.gridY + y * layout.cellSize;
                float x1 = x0 + layout.cellSize;
                float y1 = y0 + layout.cellSize;
                glVertex2f(x0, y0);
                glVertex2f(x1, y0);
                glVertex2f(x1, y1);
                glVertex2f(x0, y1);
            }
        }
        glEnd();

        // Subtle grid lines so cells read individually.
        glColor4f(GRID_LINE.getRed() / 255f, GRID_LINE.getGreen() / 255f,
                GRID_LINE.getBlue() / 255f, 0.4f * alphaMult);
        glBegin(org.lwjgl.opengl.GL11.GL_LINES);
        for (int x = 0; x <= grid.getWidth(); x++) {
            float px = layout.gridX + x * layout.cellSize;
            glVertex2f(px, layout.gridY);
            glVertex2f(px, layout.gridY + layout.gridH);
        }
        for (int y = 0; y <= grid.getHeight(); y++) {
            float py = layout.gridY + y * layout.cellSize;
            glVertex2f(layout.gridX,                py);
            glVertex2f(layout.gridX + layout.gridW, py);
        }
        glEnd();
    }

    private void renderUnits(List<Unit> units, float alphaMult) {
        float unitSize = layout.cellSize * UNIT_FRAC;
        float half = unitSize / 2f;
        float cellHalf = layout.cellSize / 2f;

        // Unit quads
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            Color c = (u.faction == Faction.MARINE) ? MARINE_COLOR : DEFENDER_COLOR;
            glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
            float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
            float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
            glVertex2f(cx - half, cy - half);
            glVertex2f(cx + half, cy - half);
            glVertex2f(cx + half, cy + half);
            glVertex2f(cx - half, cy + half);
        }
        glEnd();

        // HP bars above each unit
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
            float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
            float barW = unitSize;
            float barX = cx - barW / 2f;
            float barY = cy + half + HP_BAR_GAP;
            // background (full red)
            fillRect(barX, barY, barW, HP_BAR_H, HP_BG, alphaMult);
            // foreground (green proportional)
            float frac = Math.max(0f, Math.min(1f, u.hp / u.maxHp));
            fillRect(barX, barY, barW * frac, HP_BAR_H, HP_FG, alphaMult);
        }
    }

    /** Amber underline under whichever speed button is currently active. */
    private void renderSpeedMarker(float alphaMult) {
        int activeIdx = -1;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (SPEED_OPTIONS[i] == speedMultiplier) { activeIdx = i; break; }
        }
        if (activeIdx < 0) return;
        float w = SPEED_BTN_W - 16f;
        float markX = speedBtnCenterX[activeIdx] - w / 2f;
        float markY = speedBtnBottomY - SPEED_MARK_H - 2f;
        fillRect(markX, markY, w, SPEED_MARK_H, ACTIVE_SPEED, alphaMult);
    }

    private void renderBanner(Faction winner, float alphaMult) {
        boolean victory = winner == Faction.MARINE;
        String text = Strings.get(victory ? "battleVictory" : "battleDefeat");
        Color color = victory ? VICTORY_COLOR : DEFEAT_COLOR;

        float textW = Fonts.ORBITRON_24_BOLD.measureWidth(text);
        float textH = Fonts.ORBITRON_24_BOLD.getLineHeight();
        float padX = 24f;
        float padY = 12f;
        float boxW = textW + 2 * padX;
        float boxH = textH + 2 * padY;
        float boxX = layout.gridX + (layout.gridW - boxW) / 2f;
        float boxY = layout.gridY + (layout.gridH - boxH) / 2f;

        fillRect(boxX, boxY, boxW, boxH, BANNER_BG, 0.92f * alphaMult);
        // Color-tinted border
        outlineRect(boxX, boxY, boxW, boxH, color, alphaMult);

        Fonts.ORBITRON_24_BOLD.drawString(text,
                boxX + padX, boxY + padY + textH, color, alphaMult);
    }

    // ---- raw-GL helpers ----------------------------------------------------

    private static void fillRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        if (rw <= 0f || rh <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(rx,      ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx,      ry + rh);
        glEnd();
    }

    private static void outlineRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        org.lwjgl.opengl.GL11.glLineWidth(1.5f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
        glVertex2f(rx,      ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx,      ry + rh);
        glEnd();
    }
}
