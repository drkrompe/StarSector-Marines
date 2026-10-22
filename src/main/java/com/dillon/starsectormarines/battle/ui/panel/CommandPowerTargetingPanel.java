package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * World-facing half of command-power interaction. The MLX tray owns cards;
 * this panel owns the snapped map placement, target validity, cancellation,
 * and the next map click without spending resources directly.
 *
 * <p>A power whose call carries a pick (Mech Support's bay manifest) also owns that pick
 * here while it is armed: it starts as the power's default and the tray's picker chips
 * toggle entries in it. The map click sends it with the request, and target validity is
 * judged against it, so an empty or unliftable pick keeps the card armed and spends nothing.
 */
public final class CommandPowerTargetingPanel implements HudPanel {

    private static final int CIRCLE_SEGMENTS = 64;
    private static final Color LABEL_TEXT = new Color(0xEE, 0xF4, 0xF5);

    private final BattleUiContext ctx;
    private static final BitmapFont FONT = Fonts.ORBITRON_12_BOLD;
    private String targetingPowerId;
    /** The armed power's pick, or {@code null} for a power that carries none. */
    private List<Integer> selection;
    private int mouseX;
    private int mouseY;

    public CommandPowerTargetingPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    public String targetingPowerId() {
        return targetingPowerId;
    }

    public void cancel() {
        disarm();
    }

    /** The armed power's pick in manifest order, or {@code null} when it carries none. */
    public List<Integer> selection() {
        return selection == null ? null : Collections.unmodifiableList(selection);
    }

    /** The armed Mech Support's manifest against the current pick; empty for any other power. */
    public List<MechSupport.PickerRow> pickerRows() {
        MechSupport power = armedMechSupport();
        if (power == null || selection == null) return List.of();
        return power.picker(ctx.getSim(), selection);
    }

    /**
     * Toggles manifest entry {@code index} in the pick. A picked entry can always be dropped;
     * an unpicked one joins only when it is aboard and lift allows it beside the rest.
     */
    public void togglePick(int index) {
        if (selection == null) return;
        togglePick(selection, pickerRows(), index);
    }

    /** {@link #togglePick(int)} against {@code rows}, the manifest as it reads for {@code selection}. */
    static void togglePick(List<Integer> selection, List<MechSupport.PickerRow> rows, int index) {
        for (MechSupport.PickerRow row : rows) {
            if (row.entry().index() != index) continue;
            if (row.selected()) {
                selection.remove(Integer.valueOf(index));
            } else if (row.callable()) {
                selection.add(index);
                Collections.sort(selection);
            }
            return;
        }
    }

    /** The pick a power arms with: Mech Support's default call, and none for any other power. */
    static List<Integer> armedSelection(CommandPower power, BattleView battle) {
        return power instanceof MechSupport mech
                ? new ArrayList<>(mech.defaultSelection(battle)) : null;
    }

    private MechSupport armedMechSupport() {
        BattleSimulation sim = ctx.getSim();
        if (sim == null || targetingPowerId == null) return null;
        CommandPower power = sim.getCommandPowerService().getPower(targetingPowerId);
        return power instanceof MechSupport mech ? mech : null;
    }

    private void disarm() {
        targetingPowerId = null;
        selection = null;
    }

    /** Arms a ready power, or cancels it when its selected card is clicked again. */
    public void toggle(String powerId) {
        BattleSimulation sim = ctx.getSim();
        if (sim == null || powerId == null) return;
        if (powerId.equals(targetingPowerId)) {
            disarm();
            return;
        }
        CommandPowerService service = sim.getCommandPowerService();
        CommandPower power = service.getPower(powerId);
        if (!service.canActivate(power)) return;
        targetingPowerId = powerId;
        selection = armedSelection(power, sim);
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
            disarm();
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
        if (!inBounds(camera, cellX, cellY)) return;

        float centerX = camera.cellToScreenX(cellX + 0.5f);
        float centerY = camera.cellToScreenY(cellY + 0.5f);
        boolean valid = power.canTarget(cellX, cellY, sim, selection);
        boolean shipGrade = OrbitalBarrage.ID.equals(power.id);
        if (shipGrade) paintDomes(sim, camera, alphaMult);
        ShieldDomeService.DomeView cover = shipGrade
                ? sim.shieldDomes().coverageAt(cellX + 0.5f, cellY + 0.5f, Faction.MARINE) : null;
        BattlefieldMarkerPresentation.TargetMarker marker =
                BattlefieldMarkerPresentation.target(power.displayName, valid,
                        power.previewRadiusCells(), cover);
        paintTargetingMarker(camera, marker, centerX, centerY, alphaMult);
    }

    /**
     * Outlines every dome the barrage would meet that the player has seen: its
     * reach, and whether it is up (absorbs) or down. A dome in unseen ground is
     * not announced.
     */
    private static void paintDomes(BattleSimulation sim, BattleCamera camera, float alphaMult) {
        for (ShieldDomeService.DomeView dome : sim.shieldDomes().domes()) {
            if (!dome.holder().hostileTo(Faction.MARINE)) continue;
            if (!sim.getFogOfWar().isCellRevealed((int) Math.floor(dome.x()), (int) Math.floor(dome.y()))) continue;
            BattlefieldMarkerPresentation.DomeMarker marker = BattlefieldMarkerPresentation.dome(dome);
            float cx = camera.cellToScreenX(dome.x());
            float cy = camera.cellToScreenY(dome.y());
            float radius = marker.radiusCells() * camera.cellPxSize();
            Color color = marker.tone();
            glPushAttrib(GL_ALL_ATTRIB_BITS);
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                    (marker.up() ? 0.85f : 0.5f) * alphaMult);
            glLineWidth(marker.up() ? 2.25f : 1.25f);
            glBegin(GL_LINE_LOOP);
            for (int index = 0; index < CIRCLE_SEGMENTS; index++) {
                double angle = Math.PI * 2.0 * index / CIRCLE_SEGMENTS;
                glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
            }
            glEnd();
            glPopAttrib();
            String label = Strings.format("battleMarkerDomeLabel", marker.status());
            FONT.drawString(label, cx - FONT.measureWidth(label) * 0.5f, cy - radius - 6f, LABEL_TEXT, alphaMult);
        }
    }

    static void paintTargetingMarker(BattleCamera camera,
                                     BattlefieldMarkerPresentation.TargetMarker marker,
                                     float centerX, float centerY, float alphaMult) {
        Color color = marker.tone();
        float radius = BattlefieldMarkerPresentation.targetRadius(
                camera.cellPxSize(), marker.radiusCells());
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(r, g, b, 0.10f * alphaMult);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(centerX, centerY);
        for (int index = 0; index <= CIRCLE_SEGMENTS; index++) {
            double angle = Math.PI * 2.0 * index / CIRCLE_SEGMENTS;
            glVertex2f(centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        glEnd();

        glColor4f(r, g, b, 0.92f * alphaMult);
        glLineWidth(marker.valid() ? 1.75f : 2.25f);
        glBegin(GL_LINE_LOOP);
        for (int index = 0; index < CIRCLE_SEGMENTS; index++) {
            double angle = Math.PI * 2.0 * index / CIRCLE_SEGMENTS;
            glVertex2f(centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        glEnd();

        paintCellBrackets(centerX, centerY, camera.cellPxSize(), r, g, b,
                alphaMult);
        paintCenterDiamond(centerX, centerY, r, g, b, alphaMult);

        String label = marker.label() + "  //  " + marker.status();
        float labelWidth = FONT.measureWidth(label);
        float plateWidth = labelWidth + 18f;
        float plateHeight = 22f;
        float plateX = clamp(centerX - plateWidth * 0.5f,
                camera.vpX() + 6f, camera.vpX() + camera.vpW() - plateWidth - 6f);
        float plateY = Math.min(camera.vpY() + camera.vpH() - plateHeight - 6f,
                centerY + radius + 10f);
        glColor4f(BattlefieldMarkerPresentation.PLATE.getRed() / 255f,
                BattlefieldMarkerPresentation.PLATE.getGreen() / 255f,
                BattlefieldMarkerPresentation.PLATE.getBlue() / 255f,
                BattlefieldMarkerPresentation.PLATE.getAlpha() / 255f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(plateX, plateY);
        glVertex2f(plateX + plateWidth, plateY);
        glVertex2f(plateX + plateWidth, plateY + plateHeight);
        glVertex2f(plateX, plateY + plateHeight);
        glEnd();
        glColor4f(r, g, b, 0.9f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(plateX, plateY);
        glVertex2f(plateX + 3f, plateY);
        glVertex2f(plateX + 3f, plateY + plateHeight);
        glVertex2f(plateX, plateY + plateHeight);
        glEnd();
        glPopAttrib();

        FONT.drawString(label, plateX + 10f,
                plateY + plateHeight - 5f, LABEL_TEXT, alphaMult);
    }

    private static void paintCellBrackets(float centerX, float centerY,
                                          float cellPx, float r, float g, float b,
                                          float alphaMult) {
        float half = Math.max(7f, cellPx * 0.5f);
        float corner = Math.max(4f, cellPx * 0.25f);
        glColor4f(r, g, b, alphaMult);
        glLineWidth(2f);
        glBegin(GL_LINES);
        corner(centerX - half, centerY - half, corner, 1f, 1f);
        corner(centerX + half, centerY - half, corner, -1f, 1f);
        corner(centerX - half, centerY + half, corner, 1f, -1f);
        corner(centerX + half, centerY + half, corner, -1f, -1f);
        glEnd();
    }

    private static void corner(float x, float y, float length,
                               float xDirection, float yDirection) {
        glVertex2f(x, y);
        glVertex2f(x + length * xDirection, y);
        glVertex2f(x, y);
        glVertex2f(x, y + length * yDirection);
    }

    private static void paintCenterDiamond(float centerX, float centerY,
                                           float r, float g, float b,
                                           float alphaMult) {
        float size = 3.5f;
        glColor4f(r, g, b, alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(centerX, centerY + size);
        glVertex2f(centerX + size, centerY);
        glVertex2f(centerX, centerY - size);
        glVertex2f(centerX - size, centerY);
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
                disarm();
                event.consume();
                continue;
            }
            if (!event.isLMBDownEvent()
                    || !camera.containsScreen(event.getX(), event.getY())) continue;

            int cellX = (int) Math.floor(camera.screenToCellX(event.getX()));
            int cellY = (int) Math.floor(camera.screenToCellY(event.getY()));
            if (!inBounds(camera, cellX, cellY)) continue;
            CommandPower power = service.getPower(targetingPowerId);
            if (power != null && power.canTarget(cellX, cellY, sim, selection)) {
                service.requestActivation(targetingPowerId, cellX, cellY,
                        selection == null ? null : List.copyOf(selection));
                disarm();
            }
            // Invalid targets deliberately remain armed, but still claim the click.
            event.consume();
        }
    }

    private static boolean inBounds(BattleCamera camera, int cellX, int cellY) {
        return cellX >= 0 && cellY >= 0
                && cellX < camera.worldCellsW() && cellY < camera.worldCellsH();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
