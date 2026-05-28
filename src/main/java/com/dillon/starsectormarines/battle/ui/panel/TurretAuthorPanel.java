package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.ground.VehicleType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Runtime authoring tool for dialing in turret mount and pivot offsets on
 * ground vehicles. Renders the vehicle's sprite sheet frames at large scale,
 * lets the user LMB-click to place the turret mount point (on the chassis)
 * or pivot point (on the turret), and auto-rotates the turret 360 degrees
 * so the result is immediately visible.
 *
 * <p>Two modes, toggled by RMB:
 * <ul>
 *   <li><b>MOUNT</b> — turret auto-rotates on the chassis. LMB on chassis
 *       sets the mount point. The turret orbits around it so the user can
 *       visually confirm placement.</li>
 *   <li><b>PIVOT</b> — turret rotation pauses and the turret is drawn with
 *       pivot forced to (0,0) so the sprite center sits exactly on the mount
 *       crosshair. LMB on the turret sets the rotation center within the
 *       turret sprite. This avoids the circular-dependency problem of
 *       computing pivot relative to a pivot-shifted position.</li>
 * </ul>
 *
 * <p>R resets both points to (0,0). Esc closes the panel. All values are
 * clamped to the frame's AABB so a mis-click can't send the turret off-screen.
 */
public final class TurretAuthorPanel implements HudPanel {

    private static final Logger LOG = Global.getLogger(TurretAuthorPanel.class);

    private enum Mode { MOUNT, PIVOT }

    private static final float PREVIEW_CELL_PX = 120f;
    private static final float TURRET_SPIN_DEG_PER_SEC = 45f;
    private static final Color MOUNT_COLOR = new Color(0x40, 0xFF, 0x40);
    private static final Color PIVOT_COLOR = new Color(0xFF, 0x80, 0x20);
    private static final Color BG_COLOR = new Color(0x08, 0x0A, 0x10, 0xDD);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE8, 0xF0);
    private static final float CROSSHAIR_PX = 12f;
    private static final Color CHASSIS_FWD_COLOR = new Color(0x20, 0xA0, 0xFF);
    private static final Color TURRET_FWD_COLOR = new Color(0xFF, 0x40, 0x40);
    private static final float ARROW_LENGTH = 40f;
    private static final float ARROW_HEAD = 10f;

    private final BattleUiContext ctx;
    private final BitmapFont font;

    private SpriteAPI sheet;
    private SpriteSheetFrames frames;
    private boolean loadAttempted;

    public boolean active;
    private Mode mode = Mode.MOUNT;

    private float mountX, mountY;
    private float pivotX, pivotY;
    private float turretAngleDeg;

    // Cached from render() for handleInput
    private float previewCX, previewCY;
    private float chassisAngle;

    public TurretAuthorPanel(BattleUiContext ctx) {
        this.ctx = ctx;
        this.font = Fonts.ORBITRON_20;
    }

    private VehicleType targetType() {
        return VehicleType.HEAVY_APC;
    }

    @Override
    public boolean isVisible() {
        return active && ctx.getLayout() != null;
    }

    @Override
    public void update(float dt) {
        if (!active) return;
        if (mode == Mode.MOUNT) {
            turretAngleDeg += TURRET_SPIN_DEG_PER_SEC * dt;
            if (turretAngleDeg >= 360f) turretAngleDeg -= 360f;
        }
    }

    @Override
    public void render(float alphaMult) {
        ensureLoaded();
        if (sheet == null || frames == null) return;
        VehicleType type = targetType();
        if (type.turretFrame < 0 || type.turretFrame >= frames.frames.length) return;
        if (type.spriteFrame < 0 || type.spriteFrame >= frames.frames.length) return;

        font.ensureLoaded();
        BattleLayout layout = ctx.getLayout();

        previewCX = layout.gridX + layout.gridW / 2f;
        previewCY = layout.gridY + layout.gridH / 2f;
        chassisAngle = type.spriteFacingOffsetDeg;

        SpriteSheetFrames.Frame cf = frames.frames[type.spriteFrame];
        SpriteSheetFrames.Frame tf = frames.frames[type.turretFrame];
        float texW = sheet.getTextureWidth();
        float texH = sheet.getTextureHeight();
        int sheetW = frames.sheetWidth;
        int sheetH = frames.sheetHeight;

        float frameAspect = (float) cf.w / (float) cf.h;
        float chassisDrawW = type.visualLengthCells * PREVIEW_CELL_PX;
        float chassisDrawH = chassisDrawW / frameAspect;

        // Background overlay
        HudDraw.prepBlend();
        HudDraw.filledRect(layout.gridX, layout.gridY, layout.gridW, layout.gridH, BG_COLOR, alphaMult);

        // --- Chassis ---
        glEnable(GL_TEXTURE_2D);
        setSheetUV(cf, texW, texH, sheetW, sheetH);
        sheet.setSize(chassisDrawW, chassisDrawH);
        sheet.setAngle(chassisAngle);
        sheet.setAlphaMult(alphaMult);
        sheet.setNormalBlend();
        sheet.setColor(Color.WHITE);
        sheet.renderAtCenter(previewCX, previewCY);

        // --- Turret ---
        float tAspect = (float) tf.w / (float) tf.h;
        float tDrawW = type.turretVisualCells * PREVIEW_CELL_PX;
        float tDrawH = tDrawW / tAspect;

        // Mount position in screen coords
        float cRad = (float) Math.toRadians(chassisAngle);
        float cc = (float) Math.cos(cRad);
        float cs = (float) Math.sin(cRad);
        float mountScreenX = previewCX + (mountX * cc - mountY * cs) * PREVIEW_CELL_PX;
        float mountScreenY = previewCY + (mountX * cs + mountY * cc) * PREVIEW_CELL_PX;

        float turretRenderAngle;
        float turretDrawX, turretDrawY;

        float turretSpriteAngle = type.turretSpriteFacingOffsetDeg;
        if (mode == Mode.PIVOT) {
            turretRenderAngle = turretSpriteAngle;
            turretDrawX = mountScreenX;
            turretDrawY = mountScreenY;
        } else {
            turretRenderAngle = turretAngleDeg + turretSpriteAngle;
            float tRad = (float) Math.toRadians(turretRenderAngle);
            float tc = (float) Math.cos(tRad);
            float ts = (float) Math.sin(tRad);
            float pivotScreenDX = (pivotX * tc - pivotY * ts) * PREVIEW_CELL_PX;
            float pivotScreenDY = (pivotX * ts + pivotY * tc) * PREVIEW_CELL_PX;
            turretDrawX = mountScreenX - pivotScreenDX;
            turretDrawY = mountScreenY - pivotScreenDY;
        }

        setSheetUV(tf, texW, texH, sheetW, sheetH);
        sheet.setSize(tDrawW, tDrawH);
        sheet.setAngle(turretRenderAngle);
        sheet.setAlphaMult(alphaMult);
        sheet.renderAtCenter(turretDrawX, turretDrawY);

        sheet.setAngle(0f);
        glDisable(GL_TEXTURE_2D);

        // --- Crosshair markers ---
        HudDraw.prepBlend();
        drawCrosshair(mountScreenX, mountScreenY, MOUNT_COLOR, alphaMult);
        if (mode == Mode.PIVOT) {
            // In pivot mode, also show where the current pivot would be
            // (relative to turret center = mount point, at static angle)
            float pRad = (float) Math.toRadians(turretRenderAngle);
            float pc = (float) Math.cos(pRad);
            float ps = (float) Math.sin(pRad);
            float pivotMarkerX = mountScreenX + (pivotX * pc - pivotY * ps) * PREVIEW_CELL_PX;
            float pivotMarkerY = mountScreenY + (pivotX * ps + pivotY * pc) * PREVIEW_CELL_PX;
            drawCrosshair(pivotMarkerX, pivotMarkerY, PIVOT_COLOR, alphaMult);
        }

        // --- Forward arrows — always +Y (north/up), representing sim-forward ---
        // The sprite's visual nose should align with this arrow when the
        // facing offset is correct.
        drawArrow(previewCX, previewCY, 0f, 1f, ARROW_LENGTH, ARROW_HEAD,
                CHASSIS_FWD_COLOR, alphaMult, "DRIVE");
        drawArrow(turretDrawX, turretDrawY, 0f, 1f, ARROW_LENGTH * 0.7f, ARROW_HEAD * 0.7f,
                TURRET_FWD_COLOR, alphaMult, "BARREL");

        // --- Coordinate readout ---
        Color modeColor = (mode == Mode.MOUNT) ? MOUNT_COLOR : PIVOT_COLOR;
        String modeName = (mode == Mode.MOUNT) ? "MOUNT" : "PIVOT";
        float textX = layout.gridX + 16f;
        float textY = layout.gridY + layout.gridH - 40f;
        font.drawString("Mode: " + modeName + "  (RMB toggle)", textX, textY, modeColor, alphaMult);
        font.drawString(String.format("Mount: (%.3f, %.3f) cells", mountX, mountY),
                textX, textY - 24f, MOUNT_COLOR, alphaMult);
        font.drawString(String.format("Pivot: (%.3f, %.3f) cells", pivotX, pivotY),
                textX, textY - 48f, PIVOT_COLOR, alphaMult);
        font.drawString(String.format("Turret angle: %.0f deg", turretAngleDeg),
                textX, textY - 72f, TEXT_COLOR, alphaMult);
        font.drawString("LMB: set   RMB: mode   R: reset   Esc: close",
                textX, layout.gridY + 20f, TEXT_COLOR, alphaMult * 0.6f);
    }

    private void setSheetUV(SpriteSheetFrames.Frame f, float texW, float texH,
                            int sheetW, int sheetH) {
        sheet.setTexX((float) f.x * texW / sheetW);
        sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
        sheet.setTexWidth((float) f.w * texW / sheetW);
        sheet.setTexHeight((float) f.h * texH / sheetH);
    }

    private void drawCrosshair(float cx, float cy, Color color, float alphaMult) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        glColor4f(r, g, b, alphaMult);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2f(cx - CROSSHAIR_PX, cy);
        glVertex2f(cx + CROSSHAIR_PX, cy);
        glVertex2f(cx, cy - CROSSHAIR_PX);
        glVertex2f(cx, cy + CROSSHAIR_PX);
        glEnd();
        glBegin(GL_LINE_LOOP);
        int segs = 16;
        for (int i = 0; i < segs; i++) {
            double a = Math.PI * 2.0 * i / segs;
            glVertex2f(cx + (float) (Math.cos(a) * 4f), cy + (float) (Math.sin(a) * 4f));
        }
        glEnd();
    }

    private void drawArrow(float ox, float oy, float dx, float dy, float length, float headSize,
                           Color color, float alphaMult, String label) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        glColor4f(r, g, b, alphaMult);
        glLineWidth(2f);
        float tipX = ox + dx * length;
        float tipY = oy + dy * length;
        glBegin(GL_LINES);
        glVertex2f(ox, oy);
        glVertex2f(tipX, tipY);
        glEnd();
        float px = -dy;
        float py = dx;
        glBegin(GL_TRIANGLES);
        glVertex2f(tipX, tipY);
        glVertex2f(tipX - dx * headSize + px * headSize * 0.4f,
                   tipY - dy * headSize + py * headSize * 0.4f);
        glVertex2f(tipX - dx * headSize - px * headSize * 0.4f,
                   tipY - dy * headSize - py * headSize * 0.4f);
        glEnd();
        if (label != null) {
            font.drawString(label, tipX + 6f, tipY - 4f, color, alphaMult);
        }
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null || !active) return;
        BattleLayout layout = ctx.getLayout();
        if (layout == null) return;

        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            int px = e.getX();
            int py = e.getY();
            boolean insideGrid = px >= layout.gridX && px < layout.gridX + layout.gridW
                    && py >= layout.gridY && py < layout.gridY + layout.gridH;
            if (!insideGrid) continue;

            if (e.isLMBDownEvent()) {
                handleClick(px, py);
                e.consume();
            } else if (e.isRMBDownEvent()) {
                mode = (mode == Mode.MOUNT) ? Mode.PIVOT : Mode.MOUNT;
                e.consume();
            } else if (e.isKeyDownEvent() && (e.getEventChar() == 'r' || e.getEventChar() == 'R')) {
                mountX = mountY = pivotX = pivotY = 0f;
                saveToCommon();
                e.consume();
            } else if (e.isKeyDownEvent() && e.getEventChar() == 27) {
                active = false;
                e.consume();
            } else if (e.isMouseMoveEvent()) {
                e.consume();
            }
        }
    }

    private void handleClick(int screenX, int screenY) {
        VehicleType type = targetType();
        if (sheet == null || frames == null) return;

        SpriteSheetFrames.Frame cf = frames.frames[type.spriteFrame];
        SpriteSheetFrames.Frame tf = frames.frames[type.turretFrame];

        if (mode == Mode.MOUNT) {
            // Screen offset from chassis center → un-rotate by chassisAngle → local cells
            float dx = (screenX - previewCX) / PREVIEW_CELL_PX;
            float dy = (screenY - previewCY) / PREVIEW_CELL_PX;
            float rad = (float) Math.toRadians(-chassisAngle);
            float c = (float) Math.cos(rad);
            float s = (float) Math.sin(rad);
            float localX = dx * c - dy * s;
            float localY = dx * s + dy * c;

            // Clamp to chassis frame AABB (half-extents in cells)
            float halfLong = type.visualLengthCells / 2f;
            float aspect = (float) cf.w / (float) cf.h;
            float halfShort = halfLong / aspect;
            localX = clamp(localX, -halfLong, halfLong);
            localY = clamp(localY, -halfShort, halfShort);

            mountX = localX;
            mountY = localY;
            saveToCommon();

        } else {
            // Pivot mode: turret is drawn at (mountScreenX, mountScreenY) with
            // pivot=(0,0) and angle=chassisAngle. Click relative to that center,
            // un-rotate by chassisAngle, gives turret-local pivot.
            float cRad = (float) Math.toRadians(chassisAngle);
            float cc = (float) Math.cos(cRad);
            float cs = (float) Math.sin(cRad);
            float mountScreenX = previewCX + (mountX * cc - mountY * cs) * PREVIEW_CELL_PX;
            float mountScreenY = previewCY + (mountX * cs + mountY * cc) * PREVIEW_CELL_PX;

            float dx = (screenX - mountScreenX) / PREVIEW_CELL_PX;
            float dy = (screenY - mountScreenY) / PREVIEW_CELL_PX;
            float rad = (float) Math.toRadians(-chassisAngle);
            float c = (float) Math.cos(rad);
            float s = (float) Math.sin(rad);
            float localX = dx * c - dy * s;
            float localY = dx * s + dy * c;

            // Clamp to turret frame AABB (half-extents in cells)
            float tHalfLong = type.turretVisualCells / 2f;
            float tAspect = (float) tf.w / (float) tf.h;
            float tHalfShort = tHalfLong / tAspect;
            localX = clamp(localX, -tHalfLong, tHalfLong);
            localY = clamp(localY, -tHalfShort, tHalfShort);

            pivotX = localX;
            pivotY = localY;
            saveToCommon();
        }
    }

    // --- JSON persistence (saves/common) ---

    private static String commonPath(VehicleType type) {
        return StarsectorMarinesModPlugin.MOD_ID + "/vehicles/" + type.name().toLowerCase() + "-turret.json";
    }

    private void loadFromCommon() {
        VehicleType type = targetType();
        String path = commonPath(type);
        try {
            SettingsAPI s = Global.getSettings();
            if (s.fileExistsInCommon(path)) {
                JSONObject o = s.readJSONFromCommon(path, false);
                mountX = (float) o.optDouble("mountX", 0);
                mountY = (float) o.optDouble("mountY", 0);
                pivotX = (float) o.optDouble("pivotX", 0);
                pivotY = (float) o.optDouble("pivotY", 0);
                LOG.info("TurretAuthor: loaded from saves/common/" + path);
            }
        } catch (Exception e) {
            LOG.warn("TurretAuthor: failed to read " + path, e);
        }
    }

    private void saveToCommon() {
        VehicleType type = targetType();
        String path = commonPath(type);
        try {
            JSONObject o = new JSONObject();
            o.put("vehicleType", type.name());
            o.put("mountX", mountX);
            o.put("mountY", mountY);
            o.put("pivotX", pivotX);
            o.put("pivotY", pivotY);
            Global.getSettings().writeJSONToCommon(path, o, false);
            LOG.info("TurretAuthor: saved to saves/common/" + path
                    + "  →  turretMountX=" + mountX + "f, turretMountY=" + mountY + "f"
                    + ", turretPivotX=" + pivotX + "f, turretPivotY=" + pivotY + "f");
        } catch (Exception e) {
            LOG.warn("TurretAuthor: failed to write " + path, e);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        VehicleType type = targetType();
        try {
            Global.getSettings().loadTexture(type.spritePath);
            sheet = Global.getSettings().getSprite(type.spritePath);
            if (sheet == null) {
                LOG.warn("TurretAuthor: getSprite returned null for " + type.spritePath);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(type.spritePath)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("TurretAuthor: ImageIO.read returned null for " + type.spritePath);
                    return;
                }
                frames = SpriteSheetSlicer.slice(img);
                LOG.info("TurretAuthor: sliced " + type.spritePath + " — "
                        + frames.frames.length + " frames");
            }
            loadFromCommon();
        } catch (Exception e) {
            LOG.error("TurretAuthor: failed to load " + type.spritePath, e);
        }
    }
}
