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
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

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

    private static final Logger LOG = Global.getLogger(BattleScreen.class);

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

    private static final float UNIT_FRAC      = 1.00f; // sprite fills the cell
    private static final float HP_BAR_H       = 3f;
    private static final float HP_BAR_GAP     = 2f;
    private static final float SPEED_BTN_W    = 60f;
    private static final float SPEED_BTN_H    = 32f;
    private static final float SPEED_BTN_GAP  = 6f;
    private static final float SPEED_MARK_H   = 3f;

    /** Marine sprite sheet path (Starsector resource lookup). */
    private static final String MARINE_SHEET  = "graphics/battle/marine.png";
    /**
     * Slot count across the sheet. 8 slots reserved for an even power-of-2 strip,
     * 7 actually drawn — slot 7 is intentionally empty (south weapon-up is a
     * vertical flip of slot 6).
     */
    private static final int   SHEET_SLOTS    = 8;
    /** Window (s) after a unit fires during which we show the weapon-up pose. */
    private static final float WEAPON_UP_TIME = 0.25f;
    /** Multiplicative tint applied to defender sprites (marines are untinted). */
    private static final Color DEFENDER_TINT  = new Color(0xE0, 0x90, 0x90);

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
    /** Tracks the last-seen sim completion flag so we can rebuild widgets when it flips. */
    private boolean lastSimComplete;
    /** Cached marine sprite sheet — lazy-loaded once per Screen lifetime. Null if load failed. */
    private SpriteAPI marineSheet;
    private boolean marineSheetLoadAttempted;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.speedMultiplier = 1f;
        ensureMarineSheet();
        rebuild();
    }

    /**
     * Lazy-loads the marine sprite sheet on first attach. {@code getSprite}
     * alone returns a wrapper whose backing texture is null until
     * {@code loadTexture} is called — same pattern BitmapFont uses for font
     * pages. Survives across multiple attach calls via the cached field.
     */
    private void ensureMarineSheet() {
        if (marineSheetLoadAttempted) return;
        marineSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(MARINE_SHEET);
            marineSheet = Global.getSettings().getSprite(MARINE_SHEET);
            if (marineSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + MARINE_SHEET);
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load marine sheet " + MARINE_SHEET, e);
            marineSheet = null;
        }
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        BattleSimulation sim = ctx.getBattleSimulation();
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        layout = new BattleLayout(position, gridW, gridH);
        lastSimComplete = sim != null && sim.isComplete();

        // Bottom-left action button — Back when in-progress, Continue when done.
        String actionLabelKey = lastSimComplete ? "battleContinue" : "actionBack";
        ButtonWidget actionBtn = new ButtonWidget(layout.backX, layout.backY,
                BattleLayout.BACK_W, BattleLayout.BACK_H,
                () -> onBackOrContinue());
        widgets.add(actionBtn);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get(actionLabelKey),
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
        // Rebuild widgets when the sim transitions to complete so the bottom
        // action button swaps from Back to Continue.
        if (sim.isComplete() != lastSimComplete) {
            lastSimComplete = sim.isComplete();
            rebuild();
        }
    }

    private void onBackOrContinue() {
        if (ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim != null && sim.isComplete()) {
            // Compute + apply outcome once, then hand off to RESULTS.
            Mission mission = ctx.getSelectedMission();
            MissionOutcome outcome = MissionResolver.compute(sim, mission, ctx.getSelectedCaptain());
            MissionResolver.apply(outcome);
            ctx.setLastOutcome(outcome);
            ctx.goTo(ScreenId.RESULTS);
        } else {
            // Abandon mid-battle — no resolution, no penalty.
            ctx.goTo(ScreenId.MISSION_SELECT);
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

        SpriteAPI sheet = marineSheet;
        if (sheet != null) {
            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            float frameW = texW / SHEET_SLOTS;

            for (Unit u : units) {
                if (!u.isAlive()) continue;

                Facing facing = computeFacing(u);
                boolean weaponUp = u.cooldownTimer > (u.attackCooldown - WEAPON_UP_TIME)
                        && u.cooldownTimer > 0f;
                int frameIdx = pickFrame(facing, weaponUp);
                boolean flipY = weaponUp && facing == Facing.SOUTH;

                sheet.setTexX(frameIdx * frameW);
                sheet.setTexWidth(frameW);
                if (flipY) {
                    // Vertical mirror of slot 6 (weapon-up north) to fake south.
                    sheet.setTexY(texH);
                    sheet.setTexHeight(-texH);
                } else {
                    sheet.setTexY(0f);
                    sheet.setTexHeight(texH);
                }
                sheet.setSize(unitSize, unitSize);
                sheet.setAlphaMult(alphaMult);
                sheet.setNormalBlend();
                sheet.setColor(u.faction == Faction.MARINE ? Color.WHITE : DEFENDER_TINT);

                float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
                float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
                sheet.renderAtCenter(cx, cy);
            }
            // Reset tint so the singleton sprite doesn't carry our red into
            // anything that draws it next session.
            sheet.setColor(Color.WHITE);
        } else {
            // Sprite missing — fall back to colored quads so the sim is still readable.
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
        }

        // HP bars above each unit (always, regardless of sprite fallback)
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
            float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
            float barW = unitSize;
            float barX = cx - barW / 2f;
            float barY = cy + half + HP_BAR_GAP;
            fillRect(barX, barY, barW, HP_BAR_H, HP_BG, alphaMult);
            float frac = Math.max(0f, Math.min(1f, u.hp / u.maxHp));
            fillRect(barX, barY, barW * frac, HP_BAR_H, HP_FG, alphaMult);
        }
    }

    private enum Facing { WEST, NORTH, EAST, SOUTH }

    /** Prefer target direction (units face their target while attacking); fall back to movement; default south. */
    private static Facing computeFacing(Unit u) {
        if (u.target != null && u.target.isAlive()) {
            int dx = u.target.cellX - u.cellX;
            int dy = u.target.cellY - u.cellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        if (!u.path.isEmpty() && u.pathIdx < u.path.size()) {
            int[] next = u.path.get(u.pathIdx);
            int dx = next[0] - u.cellX;
            int dy = next[1] - u.cellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        return Facing.SOUTH;
    }

    private static Facing facingFromDelta(int dx, int dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? Facing.EAST : Facing.WEST;
        return dy > 0 ? Facing.NORTH : Facing.SOUTH;
    }

    /** Maps (facing, weaponUp) to a slot in the sheet. South+weaponUp uses slot 6 + a vertical flip. */
    private static int pickFrame(Facing facing, boolean weaponUp) {
        if (weaponUp) {
            switch (facing) {
                case WEST:  return 4;
                case EAST:  return 5;
                case NORTH: return 6;
                case SOUTH: return 6; // vertical mirror applied at draw time
            }
        } else {
            switch (facing) {
                case WEST:  return 0;
                case NORTH: return 1;
                case EAST:  return 2;
                case SOUTH: return 3;
            }
        }
        return 3;
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
