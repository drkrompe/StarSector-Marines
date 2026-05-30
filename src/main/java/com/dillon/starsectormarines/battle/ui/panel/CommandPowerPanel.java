package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * The S1 command-power bar: a row of power buttons along the bottom-center of
 * the battle viewport, plus the click-to-target flow. Clicking a ready power
 * arms <em>targeting mode</em> (a view-only lifecycle state — the sim never
 * sees it); the next click in the world queues the activation via
 * {@link CommandPowerService#requestActivation}, which the sim commits on its
 * next tick. A reticle + reveal-radius ring track the cursor while targeting.
 *
 * <p>Registered <em>after</em> {@code WorldPicker} in {@code BattleScreen} so —
 * with {@code BattleHud}'s reverse-order input — it claims the button click and
 * the targeting world-click before the picker can turn them into a squad
 * selection. When not targeting, world clicks fall through to the picker
 * untouched.
 *
 * <p>Buttons are drawn manually (rather than via {@code ui.ButtonWidget}) so the
 * button, its cooldown/affordability state, and the targeting reticle all live
 * in one panel that already sits in the HUD input order.
 */
public final class CommandPowerPanel implements HudPanel {

    private static final float BTN_W = 132f;
    private static final float BTN_H = 46f;
    private static final float BTN_GAP = 8f;
    private static final float BOTTOM_MARGIN = 18f;
    private static final float PAD = 8f;

    private static final Color BG_READY    = new Color(0x12, 0x1E, 0x2C, 0xE0);
    private static final Color BG_HOVER     = new Color(0x22, 0x36, 0x4C, 0xF0);
    private static final Color BG_ARMED     = new Color(0x2A, 0x46, 0x30, 0xF0);
    private static final Color BG_DISABLED  = new Color(0x14, 0x18, 0x1E, 0xC0);
    private static final Color BORDER       = new Color(0x60, 0x80, 0xA0);
    private static final Color BORDER_ARMED = new Color(0x70, 0xE0, 0x80);
    private static final Color NAME_FG      = new Color(0xC8, 0xE0, 0xFF);
    private static final Color NAME_DISABLED= new Color(0x60, 0x6A, 0x74);
    private static final Color COST_FG      = new Color(0x90, 0xC0, 0xFF);
    private static final Color CP_FG        = new Color(0xC8, 0xE0, 0xFF);
    private static final Color RETICLE      = new Color(0x80, 0xF0, 0xA0, 0xE0);

    private final BattleUiContext ctx;

    /** Id of the power currently in targeting mode, or {@code null}. */
    private String targetingPowerId;
    /** Index of the hovered button (for highlight), or -1. */
    private int hoveredButton = -1;
    /** Last known cursor position, for the targeting reticle. */
    private int mouseX, mouseY;

    public CommandPowerPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isVisible() {
        BattleSimulation sim = ctx.getSim();
        return sim != null && !sim.getCommandPowerService().getAvailablePowers().isEmpty();
    }

    @Override
    public void update(float dt) {
        // Drop targeting if the armed power somehow went away (e.g. roster
        // change). Cheap guard; the common case is a no-op.
        if (targetingPowerId != null) {
            BattleSimulation sim = ctx.getSim();
            if (sim == null || sim.getCommandPowerService().getPower(targetingPowerId) == null) {
                targetingPowerId = null;
            }
        }
    }

    // ---- layout ----

    private float barWidth(int count) {
        return count * BTN_W + (count - 1) * BTN_GAP;
    }

    private float barX(int count) {
        BattleCamera cam = ctx.getCamera();
        return cam.vpX() + (cam.vpW() - barWidth(count)) * 0.5f;
    }

    private float barY() {
        return ctx.getCamera().vpY() + BOTTOM_MARGIN;
    }

    private float buttonX(int i, int count) {
        return barX(count) + i * (BTN_W + BTN_GAP);
    }

    /** Button index under the screen point, or -1. */
    private int buttonAt(float px, float py, int count) {
        float y0 = barY();
        if (py < y0 || py >= y0 + BTN_H) return -1;
        for (int i = 0; i < count; i++) {
            float x0 = buttonX(i, count);
            if (px >= x0 && px < x0 + BTN_W) return i;
        }
        return -1;
    }

    // ---- render ----

    @Override
    public void render(float alphaMult) {
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;
        CommandPowerService svc = sim.getCommandPowerService();
        List<CommandPower> powers = svc.getAvailablePowers();
        if (powers.isEmpty()) return;

        HudDraw.prepBlend();

        // Command-point readout, just above the button row.
        float y0 = barY();
        String cp = "CP " + Math.round(svc.getCommandPoints()) + " / "
                + Math.round(svc.getMaxCommandPoints());
        Fonts.ORBITRON_20.drawString(cp, barX(powers.size()), y0 + BTN_H + 22f, CP_FG, alphaMult);

        for (int i = 0; i < powers.size(); i++) {
            CommandPower p = powers.get(i);
            float x0 = buttonX(i, powers.size());
            boolean armed = p.id.equals(targetingPowerId);
            boolean ready = svc.canActivate(p);
            float cd = svc.getCooldownRemaining(p.id);

            Color bg = !ready ? BG_DISABLED : armed ? BG_ARMED : (i == hoveredButton ? BG_HOVER : BG_READY);
            HudDraw.filledRect(x0, y0, BTN_W, BTN_H, bg, alphaMult);
            HudDraw.borderRect(x0, y0, BTN_W, BTN_H, armed ? BORDER_ARMED : BORDER, alphaMult);

            Color nameColor = ready || armed ? NAME_FG : NAME_DISABLED;
            Fonts.ORBITRON_20.drawString(p.displayName, x0 + PAD, y0 + BTN_H - 10f, nameColor, alphaMult);

            // Second line: cost when ready, cooldown countdown otherwise.
            String sub = cd > 0f
                    ? ((int) Math.ceil(cd)) + "s"
                    : Math.round(p.cpCost) + " CP";
            Fonts.ORBITRON_20.drawString(sub, x0 + PAD, y0 + 8f, cd > 0f ? NAME_DISABLED : COST_FG, alphaMult);
        }

        if (targetingPowerId != null) {
            renderReticle(svc.getPower(targetingPowerId), alphaMult);
        }
    }

    private void renderReticle(CommandPower power, float alphaMult) {
        if (power == null) return;
        BattleCamera cam = ctx.getCamera();
        if (!cam.containsScreen(mouseX, mouseY)) return;

        int cellX = (int) Math.floor(cam.screenToCellX(mouseX));
        int cellY = (int) Math.floor(cam.screenToCellY(mouseY));
        if (cellX < 0 || cellY < 0 || cellX >= cam.worldCellsW() || cellY >= cam.worldCellsH()) return;

        float cx = cam.cellToScreenX(cellX + 0.5f);
        float cy = cam.cellToScreenY(cellY + 0.5f);
        float a = RETICLE.getAlpha() / 255f * alphaMult;

        glDisable(GL_TEXTURE_2D);
        glColor4f(RETICLE.getRed() / 255f, RETICLE.getGreen() / 255f, RETICLE.getBlue() / 255f, a);
        glLineWidth(1.5f);

        // Crosshair.
        float arm = cam.cellPxSize() * 0.6f;
        glBegin(GL_LINES);
        glVertex2f(cx - arm, cy); glVertex2f(cx + arm, cy);
        glVertex2f(cx, cy - arm); glVertex2f(cx, cy + arm);
        glEnd();

        // Reveal-radius ring (skipped for point-target powers).
        float r = power.previewRadiusCells() * cam.cellPxSize();
        if (r > 0f) {
            glBegin(GL_LINE_LOOP);
            for (int i = 0; i < 48; i++) {
                double t = (Math.PI * 2.0 * i) / 48;
                glVertex2f(cx + (float) (Math.cos(t) * r), cy + (float) (Math.sin(t) * r));
            }
            glEnd();
        }
    }

    // ---- input ----

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        BattleSimulation sim = ctx.getSim();
        BattleCamera cam = ctx.getCamera();
        if (sim == null || cam == null) return;
        CommandPowerService svc = sim.getCommandPowerService();
        int count = svc.getAvailablePowers().size();

        hoveredButton = -1;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;

            if (e.isMouseMoveEvent()) {
                mouseX = e.getX();
                mouseY = e.getY();
                hoveredButton = buttonAt(e.getX(), e.getY(), count);
                continue;
            }

            // Right-click cancels targeting.
            if (e.isRMBDownEvent() && targetingPowerId != null) {
                targetingPowerId = null;
                e.consume();
                continue;
            }

            if (!e.isLMBDownEvent()) continue;

            int btn = buttonAt(e.getX(), e.getY(), count);
            if (btn >= 0) {
                CommandPower p = svc.getAvailablePowers().get(btn);
                if (p.id.equals(targetingPowerId)) {
                    targetingPowerId = null;          // click armed button again = cancel
                } else if (svc.canActivate(p)) {
                    targetingPowerId = p.id;          // arm targeting
                }
                e.consume();
                continue;
            }

            // A world click while targeting → queue the activation at that cell.
            if (targetingPowerId != null && cam.containsScreen(e.getX(), e.getY())) {
                int cellX = (int) Math.floor(cam.screenToCellX(e.getX()));
                int cellY = (int) Math.floor(cam.screenToCellY(e.getY()));
                if (cellX >= 0 && cellY >= 0 && cellX < cam.worldCellsW() && cellY < cam.worldCellsH()) {
                    svc.requestActivation(targetingPowerId, cellX, cellY);
                    targetingPowerId = null;
                    e.consume();
                }
            }
        }
    }
}
