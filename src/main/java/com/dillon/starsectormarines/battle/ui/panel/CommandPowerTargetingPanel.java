package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.render2d.BattleCamera;
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
 * World-facing half of the command-power interaction. The retained MLX tray
 * owns power cards and calls {@link #toggle}; this HUD panel stays above the
 * world picker so an armed power can validate and claim the next map click.
 */
public final class CommandPowerTargetingPanel implements HudPanel {

    private static final Color RETICLE = new Color(0x80, 0xF0, 0xA0, 0xE0);
    private static final Color RETICLE_INVALID = new Color(0xF0, 0x60, 0x50, 0xE8);

    private final BattleUiContext ctx;
    private String targetingPowerId;
    private int mouseX;
    private int mouseY;

    public CommandPowerTargetingPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    public String targetingPowerId() {
        return targetingPowerId;
    }

    /** Arms a ready power, or cancels it when its selected card is clicked again. */
    public void toggle(String powerId) {
        BattleSimulation sim = ctx.getSim();
        if (sim == null || powerId == null) return;
        if (powerId.equals(targetingPowerId)) {
            targetingPowerId = null;
            return;
        }
        CommandPowerService service = sim.getCommandPowerService();
        CommandPower power = service.getPower(powerId);
        if (service.canActivate(power)) targetingPowerId = powerId;
    }

    @Override
    public boolean isVisible() {
        return targetingPowerId != null;
    }

    @Override
    public void update(float dt) {
        if (targetingPowerId == null) return;
        BattleSimulation sim = ctx.getSim();
        if (sim == null || sim.getCommandPowerService().getPower(targetingPowerId) == null) {
            targetingPowerId = null;
        }
    }

    @Override
    public void render(float alphaMult) {
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return;
        CommandPower power = sim.getCommandPowerService().getPower(targetingPowerId);
        if (power == null) return;
        BattleCamera camera = ctx.getCamera();
        if (!camera.containsScreen(mouseX, mouseY)) return;

        int cellX = (int) Math.floor(camera.screenToCellX(mouseX));
        int cellY = (int) Math.floor(camera.screenToCellY(mouseY));
        if (cellX < 0 || cellY < 0
                || cellX >= camera.worldCellsW() || cellY >= camera.worldCellsH()) return;

        float centerX = camera.cellToScreenX(cellX + 0.5f);
        float centerY = camera.cellToScreenY(cellY + 0.5f);
        Color color = power.canTarget(cellX, cellY, sim) ? RETICLE : RETICLE_INVALID;
        float alpha = color.getAlpha() / 255f * alphaMult;

        glDisable(GL_TEXTURE_2D);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
        glLineWidth(1.5f);
        float arm = camera.cellPxSize() * 0.6f;
        glBegin(GL_LINES);
        glVertex2f(centerX - arm, centerY);
        glVertex2f(centerX + arm, centerY);
        glVertex2f(centerX, centerY - arm);
        glVertex2f(centerX, centerY + arm);
        glEnd();

        float radius = power.previewRadiusCells() * camera.cellPxSize();
        if (radius <= 0f) return;
        glBegin(GL_LINE_LOOP);
        for (int index = 0; index < 48; index++) {
            double angle = Math.PI * 2.0 * index / 48;
            glVertex2f(centerX + (float) (Math.cos(angle) * radius),
                    centerY + (float) (Math.sin(angle) * radius));
        }
        glEnd();
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null || targetingPowerId == null) return;
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (sim == null || camera == null) return;
        CommandPowerService service = sim.getCommandPowerService();

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (event.isMouseMoveEvent()) {
                mouseX = event.getX();
                mouseY = event.getY();
                continue;
            }
            if (event.isRMBDownEvent()) {
                targetingPowerId = null;
                event.consume();
                continue;
            }
            if (!event.isLMBDownEvent()
                    || !camera.containsScreen(event.getX(), event.getY())) continue;

            int cellX = (int) Math.floor(camera.screenToCellX(event.getX()));
            int cellY = (int) Math.floor(camera.screenToCellY(event.getY()));
            if (cellX < 0 || cellY < 0
                    || cellX >= camera.worldCellsW() || cellY >= camera.worldCellsH()) continue;
            CommandPower power = service.getPower(targetingPowerId);
            if (power != null && power.canTarget(cellX, cellY, sim)) {
                service.requestActivation(targetingPowerId, cellX, cellY);
                targetingPowerId = null;
            }
            // Invalid targets deliberately remain armed, but still claim the click.
            event.consume();
        }
    }
}
