package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.highlight.BuildPreview;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * <p>An armor drop ({@link ArmorDropPower}) also owns its pick here while it is armed: one
 * machine still aboard, starting on the first the lift allows, which the tray's picker chips
 * change. The battle is paused for as long as the picker is open and returns to the pause
 * state it had ({@link PlacementPause}). LMB places the drop and closes the picker;
 * Shift+LMB places it and keeps the picker open on the next machine; RMB or Escape closes it.
 * An invalid target or an unliftable pick keeps it armed and spends nothing.
 *
 * <p>A line power ({@link CommandPower#lineMaxCells()} above zero) is drawn: LMB down anchors the line's start on a
 * valid cell, and releasing past the shortest line commits it from there to the cursor, cut to the power's length; a
 * click that does not drag far enough leaves the start anchored, and a second click commits the line.
 */
public final class CommandPowerTargetingPanel implements HudPanel {

    private static final int CIRCLE_SEGMENTS = 64;
    private static final Color LABEL_TEXT = new Color(0xEE, 0xF4, 0xF5);

    private final BattleUiContext ctx;
    private final PlacementPause pause;
    private static final BitmapFont FONT = Fonts.ORBITRON_12_BOLD;
    private String targetingPowerId;
    /** The armed armor drop's machine, or {@code null} (another power, or nothing to pick). */
    private Integer pick;
    private int mouseX;
    private int mouseY;
    /** A line power's anchored start cell, or {@code null} before the first press. */
    private int[] lineStart;

    public CommandPowerTargetingPanel(BattleUiContext ctx) {
        this(ctx, null);
    }

    /** @param clock the battle clock the drop picker pauses while it is open */
    public CommandPowerTargetingPanel(BattleUiContext ctx, PlacementPause.Clock clock) {
        this.ctx = ctx;
        this.pause = new PlacementPause(clock);
    }

    public String targetingPowerId() {
        return targetingPowerId;
    }

    public void cancel() {
        disarm();
    }

    /** The armed drop's machine as a one-entry pick, or {@code null} when none is picked. */
    public List<Integer> selection() {
        return pick == null ? null : List.of(pick);
    }

    /**
     * The armed drop's manifest against the current pick, with machines already placed this
     * pause shown as queued; empty for any other power.
     */
    public List<ArmorDropPower.PickerRow> pickerRows() {
        ArmorDropPower power = armedDrop();
        if (power == null) return List.of();
        return withPlaced(power.picker(ctx.getSim(), pick), placed(power.id));
    }

    /** Picks manifest entry {@code index} when it can be ordered now. */
    public void togglePick(int index) {
        if (armedDrop() == null) return;
        pick = choose(pickerRows(), index, pick);
    }

    /** {@code index} when it is callable in {@code rows}, otherwise {@code current}. */
    static Integer choose(List<ArmorDropPower.PickerRow> rows, int index, Integer current) {
        for (ArmorDropPower.PickerRow row : rows) {
            if (row.entry().index() == index) return row.callable() ? Integer.valueOf(index) : current;
        }
        return current;
    }

    /** The first callable machine in {@code rows}, or {@code null}. */
    static Integer firstCallable(List<ArmorDropPower.PickerRow> rows) {
        for (ArmorDropPower.PickerRow row : rows) if (row.callable()) return row.entry().index();
        return null;
    }

    /** {@code rows} with the entries in {@code placed} shown as queued and not picked. */
    static List<ArmorDropPower.PickerRow> withPlaced(List<ArmorDropPower.PickerRow> rows,
                                                     Set<Integer> placed) {
        if (placed.isEmpty()) return rows;
        List<ArmorDropPower.PickerRow> out = new ArrayList<>(rows.size());
        for (ArmorDropPower.PickerRow row : rows) {
            out.add(placed.contains(row.entry().index()) && row.status() == ArmorDropPower.Status.ABOARD
                    ? new ArmorDropPower.PickerRow(row.entry(), ArmorDropPower.Status.QUEUED, false, null)
                    : row);
        }
        return List.copyOf(out);
    }

    /** Manifest entries of {@code powerId} placed but not yet committed by the sim. */
    private Set<Integer> placed(String powerId) {
        BattleSimulation sim = ctx.getSim();
        Set<Integer> placed = new HashSet<>();
        if (sim == null) return placed;
        for (CommandPowerService.PendingDrop drop : sim.getCommandPowerService().pendingDrops()) {
            if (drop.powerId().equals(powerId)) placed.add(drop.manifestIndex());
        }
        return placed;
    }

    private ArmorDropPower armedDrop() {
        BattleSimulation sim = ctx.getSim();
        if (sim == null || targetingPowerId == null) return null;
        CommandPower power = sim.getCommandPowerService().getPower(targetingPowerId);
        return power instanceof ArmorDropPower drop ? drop : null;
    }

    private void disarm() {
        targetingPowerId = null;
        pick = null;
        lineStart = null;
        pause.release();
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
        if (targetingPowerId != null) disarm();
        targetingPowerId = powerId;
        if (power instanceof ArmorDropPower) {
            pick = firstCallable(pickerRows());
            pause.hold();
        }
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
        BuildPreview ghost = paint(alphaMult);
        if (ctx.getHighlights() != null) ctx.getHighlights().setBuildPreview(HighlightOverlay.SRC_BUILD_GHOST_POWER, ghost);
    }

    /** Paints the cursor for the power being aimed; returns the build ghost it shows, or null. */
    private BuildPreview paint(float alphaMult) {
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return null;
        CommandPower power = sim.getCommandPowerService().getPower(targetingPowerId);
        if (power == null) return null;
        BattleCamera camera = ctx.getCamera();
        if (!camera.containsScreen(mouseX, mouseY)) return null;

        int cellX = (int) Math.floor(camera.screenToCellX(mouseX));
        int cellY = (int) Math.floor(camera.screenToCellY(mouseY));
        if (!inBounds(camera, cellX, cellY)) return null;

        if (power.lineMaxCells() > 0f && lineStart != null) {
            int[] end = lineEnd(lineStart[0], lineStart[1], cellX, cellY, power.lineMaxCells());
            boolean valid = power.canTargetLine(lineStart[0], lineStart[1], end[0], end[1], sim);
            BattlefieldMarkerPresentation.TargetMarker marker =
                    BattlefieldMarkerPresentation.target(power.displayName, valid, 0f, null);
            paintLine(camera, marker, lineStart, end, power.previewRadiusCells(), alphaMult);
            paintTargetingMarker(camera, marker, camera.cellToScreenX(end[0] + 0.5f),
                    camera.cellToScreenY(end[1] + 0.5f), alphaMult);
            return null;
        }
        float centerX = camera.cellToScreenX(cellX + 0.5f);
        float centerY = camera.cellToScreenY(cellY + 0.5f);
        boolean valid = power.canTarget(cellX, cellY, sim, selection());
        boolean shipGrade = OrbitalBarrage.ID.equals(power.id);
        if (shipGrade) paintDomes(sim, camera, alphaMult);
        ShieldDomeService.DomeView cover = shipGrade
                ? sim.shieldDomes().coverageAt(cellX + 0.5f, cellY + 0.5f, Faction.MARINE) : null;
        BattlefieldMarkerPresentation.TargetMarker marker =
                BattlefieldMarkerPresentation.target(power.displayName, valid,
                        power.previewRadiusCells(), cover);
        if (power.previewSquareCells() > 0) {
            paintPlot(camera, marker, cellX + 0.5f, cellY + 0.5f, power.previewSquareCells(), alphaMult);
        }
        paintTargetingMarker(camera, marker, centerX, centerY, alphaMult);
        return power instanceof YardConstruction yard
                ? BuildPreview.of(sim, yard.source(), yard.option(), cellX, cellY, valid) : null;
    }

    /** Outlines the {@code side}-cell square a build would lay, centred on the cell at {@code (x, y)}, in the marker's tone. */
    static void paintPlot(BattleCamera camera, BattlefieldMarkerPresentation.TargetMarker marker,
                          float x, float y, int side, float alphaMult) {
        float half = side / 2f;
        float left = camera.cellToScreenX(x - half), right = camera.cellToScreenX(x + half);
        float top = camera.cellToScreenY(y - half), bottom = camera.cellToScreenY(y + half);
        float extent = Math.abs(right - left);
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        HudDraw.borderRect(Math.min(left, right), Math.min(top, bottom), extent, extent, marker.tone(), alphaMult);
        glPopAttrib();
    }

    /**
     * The end of a drawn line: the cursor cell, pulled back along the line to {@code maxCells} from the start when it
     * lies further out.
     */
    static int[] lineEnd(int ax, int ay, int cursorX, int cursorY, float maxCells) {
        float dx = cursorX - ax;
        float dy = cursorY - ay;
        float len = (float) Math.hypot(dx, dy);
        if (len <= maxCells || len < 1e-3f) return new int[]{cursorX, cursorY};
        float k = maxCells / len;
        return new int[]{ax + (int) (dx * k), ay + (int) (dy * k)};
    }

    /** The drawn line from its start to its end, with the corridor either side of it shaded. */
    private static void paintLine(BattleCamera camera, BattlefieldMarkerPresentation.TargetMarker marker,
                                  int[] start, int[] end, float halfWidthCells, float alphaMult) {
        Color color = marker.tone();
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float x0 = camera.cellToScreenX(start[0] + 0.5f);
        float y0 = camera.cellToScreenY(start[1] + 0.5f);
        float x1 = camera.cellToScreenX(end[0] + 0.5f);
        float y1 = camera.cellToScreenY(end[1] + 0.5f);
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        if (len > 1e-3f) {
            float half = halfWidthCells * camera.cellPxSize();
            float nx = -dy / len * half;
            float ny = dx / len * half;
            glColor4f(r, g, b, 0.10f * alphaMult);
            glBegin(GL_QUADS);
            glVertex2f(x0 + nx, y0 + ny);
            glVertex2f(x1 + nx, y1 + ny);
            glVertex2f(x1 - nx, y1 - ny);
            glVertex2f(x0 - nx, y0 - ny);
            glEnd();
        }
        glColor4f(r, g, b, 0.92f * alphaMult);
        glLineWidth(2.25f);
        glBegin(GL_LINES);
        glVertex2f(x0, y0);
        glVertex2f(x1, y1);
        glEnd();
        glPopAttrib();
        paintCenterDiamond(x0, y0, r, g, b, alphaMult);
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
            if (event.isRMBDownEvent()
                    || event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                disarm();
                event.consume();
                continue;
            }
            CommandPower linePower = service.getPower(targetingPowerId);
            if (linePower != null && linePower.lineMaxCells() > 0f) {
                handleLine(event, linePower, sim, camera, service);
                continue;
            }
            if (!event.isLMBDownEvent()
                    || !camera.containsScreen(event.getX(), event.getY())) continue;

            int cellX = (int) Math.floor(camera.screenToCellX(event.getX()));
            int cellY = (int) Math.floor(camera.screenToCellY(event.getY()));
            if (!inBounds(camera, cellX, cellY)) continue;
            CommandPower power = service.getPower(targetingPowerId);
            List<Integer> selection = selection();
            if (power != null && power.canTarget(cellX, cellY, sim, selection)) {
                service.requestActivation(targetingPowerId, cellX, cellY, selection);
                if (power instanceof ArmorDropPower && event.isShiftDown()) {
                    // Placed; the picker stays open on the next machine still aboard.
                    pick = firstCallable(pickerRows());
                    if (pick == null) disarm();
                } else {
                    disarm();
                }
            }
            // Invalid targets deliberately remain armed, but still claim the click.
            event.consume();
        }
    }

    /** A line power's press and release: the press anchors the start, a long enough release or a second press commits. */
    private void handleLine(InputEventAPI event, CommandPower power, BattleSimulation sim, BattleCamera camera,
                            CommandPowerService service) {
        boolean down = event.isLMBDownEvent();
        if (!down && !(event.isLMBUpEvent() && lineStart != null)) return;
        if (!camera.containsScreen(event.getX(), event.getY())) return;
        int cellX = (int) Math.floor(camera.screenToCellX(event.getX()));
        int cellY = (int) Math.floor(camera.screenToCellY(event.getY()));
        if (!inBounds(camera, cellX, cellY)) return;
        event.consume();
        if (lineStart == null) {
            if (power.canTarget(cellX, cellY, sim)) lineStart = new int[]{cellX, cellY};
            return;
        }
        int[] end = lineEnd(lineStart[0], lineStart[1], cellX, cellY, power.lineMaxCells());
        if (power.canTargetLine(lineStart[0], lineStart[1], end[0], end[1], sim)) {
            service.requestLineActivation(targetingPowerId, lineStart[0], lineStart[1], end[0], end[1]);
            disarm();
        }
        // Too short on release: the start stays anchored for a second click.
    }

    private static boolean inBounds(BattleCamera camera, int cellX, int cellY) {
        return cellX >= 0 && cellY >= 0
                && cellX < camera.worldCellsW() && cellY < camera.worldCellsH();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
