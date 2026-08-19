package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.command.reinforcement.CounterattackSystem;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.comms.BattleCommsFeed;
import com.dillon.starsectormarines.battle.ui.comms.CounterattackCommsPresenter;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Battle comms dispatch plate plus the conquest counterattack's world-anchored
 * threatened-district signpost. The plate is a reusable newest-message
 * surface; {@link CounterattackCommsPresenter} is merely its first producer.
 * Both are presentation-only reads over the sim.
 */
public final class BattleCommsPanel implements HudPanel {

    private static final float PANEL_MAX_W = 560f;
    private static final float PANEL_H = 76f;
    private static final float PANEL_INSET = 18f;
    private static final float PAD = 10f;

    private static final Color PANEL_BG = new Color(0x10, 0x14, 0x1E, 0xEC);
    private static final Color TEXT = new Color(0xE6, 0xEC, 0xF2);
    private static final Color WARNING = new Color(0xFF, 0xC0, 0x40);
    private static final Color DANGER = new Color(0xF0, 0x58, 0x48);
    private static final Color GOOD_NEWS = new Color(0x70, 0xD8, 0x90);
    private static final Color STATUS = new Color(0x80, 0xB8, 0xE8);

    private final BattleUiContext ctx;
    private final BattleCommsFeed feed = new BattleCommsFeed();
    private final CounterattackCommsPresenter counterattackPresenter = new CounterattackCommsPresenter();
    private final BitmapFont font = Fonts.ORBITRON_20;
    private float wallClock;

    public BattleCommsPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void update(float dt) {
        feed.update(dt);
        counterattackPresenter.update(ctx.getSim(), feed);
        wallClock += Math.max(0f, dt);
    }

    @Override
    public void render(float alphaMult) {
        // BitmapFont and the immediate-mode marker both touch foreign GL
        // state. Bracket the whole panel so neither Starsector's incoming
        // state nor the next HUD/widget draw inherits our blend/texture/
        // shader choices.
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            CounterattackSystem counterattack = counterattack();
            if (hasActiveSignpost(counterattack)) renderSignpost(counterattack, alphaMult);

            BattleCommsFeed.Notice notice = feed.activeNotice();
            if (notice != null) renderNotice(notice, alphaMult);
        } finally {
            glPopAttrib();
            glUseProgram(previousProgram);
        }
    }

    private void renderNotice(BattleCommsFeed.Notice notice, float alphaMult) {
        BattleLayout layout = ctx.getLayout();
        if (layout == null) return;

        float width = Math.min(PANEL_MAX_W, Math.max(240f, layout.gridW - PANEL_INSET * 2f));
        float x = layout.gridX + (layout.gridW - width) * 0.5f;
        float y = layout.controlsY - BattleLayout.CONTROLS_GAP - PANEL_H;
        float fade = Math.min(1f, feed.remainingSec());
        float alpha = alphaMult * fade;
        Color accent = toneColor(notice.tone());

        HudDraw.prepBlend();
        HudDraw.filledRect(x, y, width, PANEL_H, PANEL_BG, alpha);
        HudDraw.filledRect(x, y, 4f, PANEL_H, accent, alpha);
        HudDraw.borderRect(x, y, width, PANEL_H, accent, alpha);

        font.drawString(notice.heading(), x + PAD + 4f, y + PANEL_H - 8f, accent, alpha);
        font.drawStringWrapped(notice.body(), x + PAD + 4f, y + PANEL_H - 32f,
                width - PAD * 2f - 4f, TEXT, alpha);
    }

    private void renderSignpost(CounterattackSystem counterattack, float alphaMult) {
        BattleCamera camera = ctx.getCamera();
        float worldX = counterattack.getBulgeCenterX();
        float worldY = counterattack.getBulgeCenterY();
        if (camera == null || !Float.isFinite(worldX) || !Float.isFinite(worldY)) return;

        float sx = camera.cellToScreenX(worldX);
        float sy = camera.cellToScreenY(worldY);
        if (!camera.containsScreen(sx, sy)) return;

        boolean telegraph = counterattack.getPhase() == CounterattackSystem.Phase.TELEGRAPH;
        Color color = telegraph ? WARNING : DANGER;
        float pulse = 1f + 0.10f * (float) Math.sin(wallClock * Math.PI * 3.0);
        float radius = Math.max(16f, Math.min(42f, camera.cellPxSize() * 1.8f)) * pulse;
        float a = color.getAlpha() / 255f * alphaMult;

        HudDraw.prepBlend();
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, a);
        glLineWidth(2f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(sx, sy + radius);
        glVertex2f(sx + radius, sy);
        glVertex2f(sx, sy - radius);
        glVertex2f(sx - radius, sy);
        glEnd();

        float arm = radius * 0.42f;
        glBegin(GL_LINES);
        glVertex2f(sx - radius - arm, sy); glVertex2f(sx - radius + arm, sy);
        glVertex2f(sx + radius - arm, sy); glVertex2f(sx + radius + arm, sy);
        glVertex2f(sx, sy - radius - arm); glVertex2f(sx, sy - radius + arm);
        glVertex2f(sx, sy + radius - arm); glVertex2f(sx, sy + radius + arm);
        glEnd();

        String label = signpostLabel(counterattack);
        float labelX = sx - font.measureWidth(label) * 0.5f;
        font.drawString(label, labelX, sy + radius + font.getLineHeight() + 5f, color, alphaMult);
    }

    private static String signpostLabel(CounterattackSystem counterattack) {
        String district = CounterattackCommsPresenter.districtName(counterattack.getBulgeSlice()).toUpperCase();
        if (counterattack.getPhase() == CounterattackSystem.Phase.TELEGRAPH) {
            return "COUNTERATTACK // " + district + " // "
                    + (int) Math.ceil(counterattack.getPhaseTimeRemaining()) + "s";
        }
        return "COUNTERATTACK // " + district;
    }

    private CounterattackSystem counterattack() {
        return ctx.getSim() == null ? null : ctx.getSim().getCounterattackSystem();
    }

    private static boolean hasActiveSignpost(CounterattackSystem counterattack) {
        if (counterattack == null || counterattack.getBulgeSlice() == null) return false;
        return counterattack.getPhase() == CounterattackSystem.Phase.TELEGRAPH
                || counterattack.getPhase() == CounterattackSystem.Phase.ASSAULT
                || counterattack.getPhase() == CounterattackSystem.Phase.RESOLVE;
    }

    private static Color toneColor(BattleCommsFeed.Tone tone) {
        return switch (tone) {
            case WARNING -> WARNING;
            case DANGER -> DANGER;
            case GOOD_NEWS -> GOOD_NEWS;
            case STATUS -> STATUS;
        };
    }

    @Override
    public void handleInput(List<InputEventAPI> events) { /* read-only presentation */ }

    @Override
    public boolean isVisible() {
        return ctx.getLayout() != null && (feed.activeNotice() != null || hasActiveSignpost(counterattack()));
    }
}
