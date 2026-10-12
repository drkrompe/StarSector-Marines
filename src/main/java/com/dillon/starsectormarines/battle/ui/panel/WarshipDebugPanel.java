package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.DebugTender;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.warship.DebugWarship;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
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
 * Debug control and readout for the briefing's WARSHIP DEBUG ship. Visible only
 * while a warship is over the battle and {@link DevConfig#DEBUG_WARSHIP_PICKER}
 * is on. {@link #FIRE_KEY} orders it to fire on the cell under the cursor,
 * {@link #HOLD_KEY} holds fire; a status line reports hull, order, heading and
 * rounds fired so damage and area of effect can be judged.
 */
@DebugOnly
public final class WarshipDebugPanel implements HudPanel {

    /** Fire on the world cell under the mouse cursor, radius {@link DebugWarship#AREA_RADIUS_CELLS}. */
    public static final int FIRE_KEY = Keyboard.KEY_F6;
    /** Hold fire. */
    public static final int HOLD_KEY = Keyboard.KEY_F7;
    /** Put the ship on, or take it off, its hull's flux. */
    public static final int FLUX_KEY = Keyboard.KEY_F8;
    /** Step the ship's missiles through faithful, flavoured and cinematic. */
    public static final int MISSILE_PRESET_KEY = Keyboard.KEY_F9;

    /** Fly to the cell under the cursor and hover there; starts a fresh route. */
    public static final int GO_KEY = Keyboard.KEY_F10;
    /** Add the cell under the cursor to the route and patrol it in a loop. */
    public static final int ROUTE_KEY = Keyboard.KEY_F11;

    private static final float PAD = 6f;
    private static final Color TEXT = new Color(0xFF, 0xE0, 0x90);
    private static final Color HINT = new Color(0xA8, 0xB8, 0xC8);

    private final BattleUiContext ctx;
    private final List<float[]> route = new ArrayList<>();
    private final BitmapFont font = Fonts.INSIGNIA_15_AA;

    public WarshipDebugPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    private Warship ship() {
        BattleSimulation sim = ctx.getSim();
        if (sim == null || sim.warships().ships().isEmpty()) return null;
        return sim.warships().ships().get(0);
    }

    @Override public void update(float dt) { }

    @Override
    public boolean isVisible() {
        return DevConfig.DEBUG_WARSHIP_PICKER && ship() != null && ctx.getLayout() != null;
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        Warship ship = ship();
        for (InputEventAPI e : events) {
            if (ship == null || e.isConsumed() || !e.isKeyDownEvent()) continue;
            Tender tender = DebugTender.of(ctx.getSim(), ship);
            if (tender != null) {
                if (handleTenderKey(tender, e.getEventValue())) e.consume();
                else if (e.getEventValue() == GO_KEY || e.getEventValue() == ROUTE_KEY) {
                    routeToCursor(ship, e.getEventValue() == ROUTE_KEY);
                    e.consume();
                }
                continue;
            }
            if (e.getEventValue() == FIRE_KEY) {
                fireAtCursor(ship);
                e.consume();
            } else if (e.getEventValue() == HOLD_KEY) {
                DebugWarship.holdFire(ship);
                e.consume();
            } else if (e.getEventValue() == FLUX_KEY) {
                DebugWarship.toggleFlux(ship);
                e.consume();
            } else if (e.getEventValue() == GO_KEY || e.getEventValue() == ROUTE_KEY) {
                routeToCursor(ship, e.getEventValue() == ROUTE_KEY);
                e.consume();
            } else if (e.getEventValue() == MISSILE_PRESET_KEY) {
                DebugWarship.cycleMissilePreset(ship);
                e.consume();
            }
        }
    }

    /** A tender's keys: build at the cursor, cancel the builds, recall or deploy the defence drones. */
    private boolean handleTenderKey(Tender tender, int key) {
        if (key == FIRE_KEY) {
            BattleCamera camera = ctx.getCamera();
            float mouseX = Global.getSettings().getMouseX();
            float mouseY = Global.getSettings().getMouseY();
            if (camera != null && camera.containsScreen(mouseX, mouseY)) {
                DebugTender.buildAt(ctx.getSim(), tender, camera.screenToCellX(mouseX), camera.screenToCellY(mouseY));
            }
            return true;
        }
        if (key == HOLD_KEY) {
            tender.cancelAll();
            return true;
        }
        if (key == FLUX_KEY) {
            DebugTender.toggleDefence(tender);
            return true;
        }
        return false;
    }

    private void routeToCursor(Warship ship, boolean append) {
        BattleCamera camera = ctx.getCamera();
        float mouseX = Global.getSettings().getMouseX();
        float mouseY = Global.getSettings().getMouseY();
        if (camera == null || !camera.containsScreen(mouseX, mouseY)) return;
        if (!append) route.clear();
        route.add(new float[] {camera.screenToCellX(mouseX), camera.screenToCellY(mouseY)});
        DebugWarship.patrol(ship, route);
    }

    private void fireAtCursor(Warship ship) {
        BattleCamera camera = ctx.getCamera();
        // The position is asked for when the key goes down, not remembered from mouse
        // events: another handler may have consumed those, and a consumed event
        // throws if its data is read.
        float mouseX = Global.getSettings().getMouseX();
        float mouseY = Global.getSettings().getMouseY();
        if (camera == null || !camera.containsScreen(mouseX, mouseY)) return;
        DebugWarship.fireAt(ship, camera.screenToCellX(mouseX), camera.screenToCellY(mouseY));
    }

    @Override
    public void render(float alphaMult) {
        Warship ship = ship();
        BattleLayout layout = ctx.getLayout();
        if (ship == null || layout == null) return;
        font.ensureLoaded();
        Tender tender = DebugTender.of(ctx.getSim(), ship);
        String line = tender != null ? tender.statusLine()
                : DebugWarship.statusLine(ship) + "  missiles " + ship.missilePreset();
        String hint = tender != null ? DebugTender.HINT
                : "F6 fire at cursor   F7 hold fire   F8 flux on/off   F9 missile preset   F10 go to cursor   F11 add to patrol route";
        float w = Math.max(font.measureWidth(line), font.measureWidth(hint)) + 2f * PAD;
        float lineH = font.getLineHeight();
        float h = 2f * lineH + 2f * PAD;
        float x = layout.gridX + BattleLayout.PAD;
        float top = layout.gridY + layout.gridH - BattleLayout.PAD;
        float y = top - h;

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0.08f, 0.10f, 0.14f, 0.85f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();

        font.drawString(line, x + PAD, top - PAD, TEXT, alphaMult);
        font.drawString(hint, x + PAD, top - PAD - lineH, HINT, alphaMult);
    }

    @Override
    public boolean blocksWorldPointer(float px, float py) {
        return false;
    }
}
