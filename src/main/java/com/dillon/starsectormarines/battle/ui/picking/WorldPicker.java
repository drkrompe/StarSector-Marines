package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
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
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * World-selection HUD slot. A stationary LMB gesture keeps the original click
 * behavior: nearest live unit (any faction) within a forgiveness radius wins,
 * and empty world space clears selection. Crossing a small pointer threshold
 * instead draws a selection box and chooses one player infantry squad or combat
 * mech inside it. The single winner is the candidate nearest the box center,
 * with entity id as the stable tie-break.
 *
 * <p>The world is the battle's squad browser. It writes the shared
 * {@link Selection}, and the selected-squad detail and GOAP diagnostic react
 * to that one view-only state while the default HUD remains a force-level
 * rollup. Defender squads are reachable through the same picker for debugging
 * garrison behavior.
 *
 * <p>Registered before the visible panels in {@code BattleHud}; reverse input
 * order lets those panels claim their controls first, so this picker receives
 * only leftover clicks in the world.
 */
public final class WorldPicker implements HudPanel {

    /**
     * Baseline pick radius in cells, used as a floor for small units. Units
     * may be mid-cell during movement (World.renderX/Y, the tolerant
     * center-based reads) so we want forgiveness around the visual centre.
     * {@link #nearestUnit} widens this per-unit to at least the unit's
     * {@link UnitType#radius} physical footprint (plus a small margin) so
     * bigger units (mechs, hubs) are easier to click without shrinking the
     * forgiveness infantry already had.
     */
    private static final float PICK_RADIUS_CELLS = 0.6f;
    private static final float DRAG_THRESHOLD_PX = 4f;
    private static final Color DRAG_FILL = new Color(0x35, 0xC9, 0xF0, 0x24);
    private static final Color DRAG_BORDER = new Color(0x55, 0xDC, 0xFF, 0xF0);

    private final BattleUiContext ctx;
    private boolean primaryGestureActive;
    private float dragStartX;
    private float dragStartY;
    private float dragCurrentX;
    private float dragCurrentY;

    public WorldPicker(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override public boolean isVisible() { return true; }
    @Override public void update(float dt) {}

    @Override
    public void render(float alphaMult) {
        BattleCamera camera = ctx.getCamera();
        if (camera == null || !isDragGesture()) return;

        float minX = Math.min(dragStartX, dragCurrentX);
        float minY = Math.min(dragStartY, dragCurrentY);
        float maxX = Math.max(dragStartX, dragCurrentX);
        float maxY = Math.max(dragStartY, dragCurrentY);

        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        paintRect(minX, minY, maxX, maxY, DRAG_FILL, alphaMult, true);
        glLineWidth(1.5f);
        paintRect(minX, minY, maxX, maxY, DRAG_BORDER, alphaMult, false);
        glPopAttrib();
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (sim == null || camera == null) {
            primaryGestureActive = false;
            return;
        }

        for (InputEventAPI e : events) {
            if (primaryGestureActive && e.isLMBUpEvent()) {
                updateDragPointer(camera, e.getX(), e.getY());
                completePrimaryGesture(sim, camera);
                primaryGestureActive = false;
                e.consume();
                continue;
            }
            if (primaryGestureActive && e.isMouseMoveEvent()) {
                updateDragPointer(camera, e.getX(), e.getY());
                e.consume();
                continue;
            }
            if (e.isConsumed()) continue;
            if (!e.isLMBDownEvent()) continue;
            if (!camera.containsScreen(e.getX(), e.getY())) continue;

            primaryGestureActive = true;
            dragStartX = e.getX();
            dragStartY = e.getY();
            dragCurrentX = dragStartX;
            dragCurrentY = dragStartY;
            e.consume();
        }
    }

    private void completePrimaryGesture(BattleSimulation sim, BattleCamera camera) {
        if (isDragGesture()) {
            long picked = nearestDragCandidate(sim, camera,
                    Math.min(dragStartX, dragCurrentX),
                    Math.min(dragStartY, dragCurrentY),
                    Math.max(dragStartX, dragCurrentX),
                    Math.max(dragStartY, dragCurrentY));
            if (picked != 0L) {
                ctx.getSelection().selectUnit(sim.squad().squadId(picked), picked);
            } else {
                ctx.getSelection().clear();
            }
            return;
        }

        float worldX = camera.screenToCellX(dragCurrentX);
        float worldY = camera.screenToCellY(dragCurrentY);
        long vehicleId = nearestVehicle(sim, worldX, worldY);
        if (vehicleId != 0L) {
            ctx.getSelection().selectVehicle(vehicleId);
            return;
        }

        long picked = nearestUnit(sim, worldX, worldY);
        if (picked != 0L && sim.squad().hasSquad(picked)) {
            ctx.getSelection().selectUnit(sim.squad().squadId(picked), picked);
        } else {
            ctx.getSelection().clear();
        }
    }

    private void updateDragPointer(BattleCamera camera, float screenX, float screenY) {
        dragCurrentX = clamp(screenX, camera.vpX(), camera.vpX() + camera.vpW());
        dragCurrentY = clamp(screenY, camera.vpY(), camera.vpY() + camera.vpH());
    }

    private boolean isDragGesture() {
        if (!primaryGestureActive) return false;
        float dx = dragCurrentX - dragStartX;
        float dy = dragCurrentY - dragStartY;
        return dx * dx + dy * dy > DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
    }

    private static void paintRect(float minX, float minY, float maxX, float maxY,
                                  Color color, float alphaMult, boolean filled) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f * alphaMult);
        glBegin(filled ? GL_QUADS : GL_LINE_LOOP);
        glVertex2f(minX, minY);
        glVertex2f(maxX, minY);
        glVertex2f(maxX, maxY);
        glVertex2f(minX, maxY);
        glEnd();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final float VEHICLE_PICK_RADIUS_CELLS = 1.5f;

    /** Entity id of the nearest visible convoy vehicle within the pick radius, or {@code 0L} if none. */
    private static long nearestVehicle(BattleSimulation sim, float worldX, float worldY) {
        long[] ids = sim.getConvoyVehicleIds();
        ConvoyService convoy = sim.convoy();
        long bestId = 0L;
        float bestDistSq = VEHICLE_PICK_RADIUS_CELLS * VEHICLE_PICK_RADIUS_CELLS;
        for (long id : ids) {
            VehicleMission v = convoy.mission(id);
            if (v == null || !v.isVisible()) continue;
            // Selection is the front door to commanding a vehicle, so it is
            // the player's own chassis or nothing. An enemy APC is a target,
            // not a unit, and picking one would have handed the right-click
            // dispatch somebody else's vehicle to order about.
            if (convoy.faction(id) != Faction.MARINE) continue;
            GroundBody body = convoy.body(id);
            float dx = body.x - worldX;
            float dy = body.y - worldY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestId = id;
            }
        }
        return bestId;
    }

    /**
     * Nearest live unit within its own pick radius, or {@code 0L} if none
     * qualify. Each unit's radius scales with its physical footprint
     * ({@link UnitType#radius}) so bigger units (mechs, hubs) are easier to
     * click; {@code bestDistSq} tracks absolute nearest-distance among
     * qualifying candidates, not distance relative to each unit's own radius,
     * so a small unit slightly closer than a big one still wins.
     */
    private static long nearestUnit(BattleSimulation sim, float worldX, float worldY) {
        long best = 0L;
        float bestDistSq = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            float dx = sim.world().renderX(u) - worldX;
            float dy = sim.world().renderY(u) - worldY;
            float d2 = dx * dx + dy * dy;
            float pickRadius = Math.max(PICK_RADIUS_CELLS, sim.getRoster().radius(u) + 0.3f);
            if (d2 > pickRadius * pickRadius) continue;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }

    /**
     * One commandable player squad member intersecting the drag box. Infantry
     * members select their squad; mechs additionally pin the exact chassis so
     * the mech command plate retains its member-level scope.
     */
    private static long nearestDragCandidate(BattleSimulation sim, BattleCamera camera,
                                             float minX, float minY,
                                             float maxX, float maxY) {
        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        long best = 0L;
        float bestDistSq = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!isDragSelectable(sim, unit)) continue;

            float screenX = camera.cellToScreenX(sim.world().renderX(unit));
            float screenY = camera.cellToScreenY(sim.world().renderY(unit));
            float radiusPx = Math.max(1f,
                    sim.getRoster().radius(unit) * camera.cellPxSize());
            if (screenX + radiusPx < minX || screenX - radiusPx > maxX
                    || screenY + radiusPx < minY || screenY - radiusPx > maxY) {
                continue;
            }

            float dx = screenX - centerX;
            float dy = screenY - centerY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq < bestDistSq
                    || distanceSq == bestDistSq && (best == 0L || unit < best)) {
                bestDistSq = distanceSq;
                best = unit;
            }
        }
        return best;
    }

    private static boolean isDragSelectable(BattleSimulation sim, long unit) {
        if (!sim.squad().hasSquad(unit)
                || sim.identity().faction(unit) != Faction.MARINE) return false;
        UnitType type = sim.identity().type(unit);
        if (!type.usesInfantryTraining() && !type.isMech()) return false;
        Squad squad = sim.getSquad(sim.squad().squadId(unit));
        return squad != null && (!type.isMech() || !squad.rescuePickupMech);
    }
}
