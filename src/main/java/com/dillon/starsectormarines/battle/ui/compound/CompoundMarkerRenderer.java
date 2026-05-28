package com.dillon.starsectormarines.battle.ui.compound;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.ops.battleview.BattleCamera;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
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
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * World-layer renderer for the compound capture-state markers. Reads
 * {@link CompoundService}'s records each frame and draws a faction-coloured
 * ring + capture-progress arc + kind glyph at each compound's centroid.
 * Stateless w.r.t. game state — pure derivation; the only field state is
 * a wall-clock pulse timer so contested compounds visibly throb.
 *
 * <p>Visual lineage: the SABOTAGE charge-site marker rendered in
 * {@code BattleScreen.renderObjectiveMarkers}. Same shapes (filled ring +
 * progress arc + center icon), different palette (state-coloured rather
 * than always orange) and slightly larger footprint because compounds
 * span a multi-cell bbox.
 *
 * <p>Position is the {@link TacticalNode}'s bbox centroid (not the
 * anchor): the anchor is the doorway cell the AI targets, but the marker
 * should sit on the building's visual center so the player reads it as
 * "that whole compound."
 */
public final class CompoundMarkerRenderer {

    /** Outer ring radius in cell units. Larger than the charge-site icon (1.5 cells) because compounds are visually larger structures the player should see at a glance. */
    private static final float RING_RADIUS_CELLS = 1.3f;
    /** Width of the outer ring annulus, in cell units. */
    private static final float RING_THICKNESS_CELLS = 0.18f;
    /** Capture-progress arc sits just inside the ring. */
    private static final float ARC_RADIUS_CELLS = RING_RADIUS_CELLS - RING_THICKNESS_CELLS - 0.08f;
    private static final float ARC_THICKNESS_CELLS = 0.16f;
    /** Number of arc segments — same resolution as the charge-site arc (32 reads as smooth at 1-cell scale). */
    private static final int ARC_SEGMENTS = 32;
    /** Number of ring segments — smooth circle in pixel space at common cell scales. */
    private static final int RING_SEGMENTS = 48;

    /** Pulse amplitude added to the ring radius when contested — a 10% breath. Mirrors the charge-site marker's pulse. */
    private static final float CONTESTED_PULSE_AMP = 0.10f;
    private static final float CONTESTED_PULSE_HZ = 1.2f;

    // Palette — read against a dark map at all TOD presets. Mirrors the
    // CHARGE_TINT_ACTIVE / CHARGE_TINT_COMPLETE language so the visual
    // grammar is consistent across markers.
    private static final Color RING_DEFENDER  = new Color(0xE0, 0x40, 0x40); // crimson — defender-held
    private static final Color RING_CONTESTED = new Color(0xFF, 0xC0, 0x40); // amber — contested
    private static final Color RING_MARINE    = new Color(0x4A, 0xB0, 0xFF); // marine blue — captured
    private static final Color ARC_TINT       = new Color(0xFF, 0xE0, 0x80); // warm fill — same arc color the charge sites use
    private static final Color GLYPH_TINT     = new Color(240, 240, 240);

    private final BitmapFont font;
    private float wallClock;

    public CompoundMarkerRenderer() {
        this.font = Fonts.ORBITRON_20;
    }

    /** Per-frame wall-clock advance — pulse phase only; sim pause shouldn't freeze the pulse (player still reads contested state during pauses). */
    public void update(float dtRealtime) {
        wallClock += dtRealtime;
    }

    /**
     * Draws the world-anchored markers — one per compound record in
     * {@link CompoundService#getRecords()}. Call from the world-layer
     * render pass after units / charge sites and before air vehicles
     * (mirrors the layer order {@code renderObjectiveMarkers} sits in).
     */
    public void render(BattleSimulation sim, CompoundService service,
                       BattleCamera camera, float alphaMult) {
        if (service == null || sim == null || camera == null) return;
        if (service.getRecords().isEmpty()) return;

        font.ensureLoaded();
        float cellPx = camera.cellPxSize();

        for (CompoundService.Record r : service.getRecords()) {
            TacticalNode node = r.node;
            float worldCx = (node.left + node.right + 1) * 0.5f;
            float worldCy = (node.top + node.bottom + 1) * 0.5f;
            float sx = camera.cellToScreenX(worldCx);
            float sy = camera.cellToScreenY(worldCy);

            Color ringColor = ringColorFor(r.state);
            float pulse = (r.state == CompoundService.CompoundState.CONTESTED)
                    ? 1f + CONTESTED_PULSE_AMP * (float) Math.sin(wallClock * 2.0 * Math.PI * CONTESTED_PULSE_HZ)
                    : 1f;

            // 1. Faction-coloured ring (annulus). The ring itself does the
            //    state read — color encodes DEFENDER/CONTESTED/MARINE.
            float ringInner = (RING_RADIUS_CELLS - RING_THICKNESS_CELLS) * cellPx * pulse;
            float ringOuter = RING_RADIUS_CELLS * cellPx * pulse;
            drawAnnulus(sx, sy, ringInner, ringOuter, RING_SEGMENTS, ringColor, alphaMult);

            // 2. Capture-progress arc — partial annulus inside the ring.
            //    Only renders when there's progress to show.
            if (r.captureProgress > 0f) {
                float arcInner = ARC_RADIUS_CELLS * cellPx;
                float arcOuter = (ARC_RADIUS_CELLS + ARC_THICKNESS_CELLS) * cellPx;
                drawProgressArc(sx, sy, arcInner, arcOuter,
                        r.captureProgress, ARC_TINT, alphaMult);
            }

            // 3. Kind glyph — single letter inside the ring. Cheap, readable,
            //    no per-kind sprite required for v1. Future iteration can
            //    swap this for proper icon sprites (a small barracks /
            //    armory / command flag).
            String glyph = glyphFor(node.kind);
            float glyphW = font.measureWidth(glyph);
            font.drawString(glyph,
                    sx - glyphW * 0.5f,
                    sy + font.getLineHeight() * 0.35f,
                    GLYPH_TINT, alphaMult);
        }
    }

    private static Color ringColorFor(CompoundService.CompoundState state) {
        return switch (state) {
            case DEFENDER_HELD -> RING_DEFENDER;
            case CONTESTED     -> RING_CONTESTED;
            case MARINE_HELD   -> RING_MARINE;
        };
    }

    private static String glyphFor(TacticalNode.Kind kind) {
        return switch (kind) {
            case COMMAND_POST -> "C";
            case BARRACKS     -> "B";
            case ARMORY       -> "A";
            // Compounds-only filter upstream rejects other kinds, but the
            // exhaustive switch covers it defensively.
            default           -> "?";
        };
    }

    /**
     * Draws a filled annulus (filled ring band) around {@code (cx, cy)}.
     * Built as a fan of quads — same construction as {@link #drawProgressArc}
     * but for the full sweep.
     */
    private static void drawAnnulus(float cx, float cy, float innerR, float outerR,
                                    int segments, Color color, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f * alphaMult);
        glBegin(GL_QUADS);
        float prevC = 1f, prevS = 0f;
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float a = t * (float) (Math.PI * 2.0);
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            glVertex2f(cx + prevC * innerR, cy + prevS * innerR);
            glVertex2f(cx + prevC * outerR, cy + prevS * outerR);
            glVertex2f(cx + c * outerR,     cy + s * outerR);
            glVertex2f(cx + c * innerR,     cy + s * innerR);
            prevC = c;
            prevS = s;
        }
        glEnd();
    }

    /**
     * Clockwise-filling annulus sweep from the 12 o'clock position. Mirrors
     * the construction the charge-site marker uses — kept here as a local
     * copy rather than a shared helper because the BattleScreen helper is
     * private and extracting it is out of scope for this slice.
     */
    private static void drawProgressArc(float cx, float cy, float innerR, float outerR,
                                        float progress, Color color, float alphaMult) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (progress <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f * alphaMult);

        int filled = (int) Math.ceil(ARC_SEGMENTS * progress);
        glBegin(GL_QUADS);
        for (int i = 0; i < filled; i++) {
            float t1 = (float) i / ARC_SEGMENTS;
            float t2 = Math.min(progress, (float) (i + 1) / ARC_SEGMENTS);
            float a1 = (float) (Math.PI / 2.0) - t1 * (float) (Math.PI * 2.0);
            float a2 = (float) (Math.PI / 2.0) - t2 * (float) (Math.PI * 2.0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            glVertex2f(cx + c1 * innerR, cy + s1 * innerR);
            glVertex2f(cx + c1 * outerR, cy + s1 * outerR);
            glVertex2f(cx + c2 * outerR, cy + s2 * outerR);
            glVertex2f(cx + c2 * innerR, cy + s2 * innerR);
        }
        glEnd();

        // Hairline outer rim on the filled arc for legibility against
        // bright autotiles. Costs one line-loop pass per marker.
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                0.5f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINES);
        for (int i = 0; i < filled; i++) {
            float t1 = (float) i / ARC_SEGMENTS;
            float t2 = Math.min(progress, (float) (i + 1) / ARC_SEGMENTS);
            float a1 = (float) (Math.PI / 2.0) - t1 * (float) (Math.PI * 2.0);
            float a2 = (float) (Math.PI / 2.0) - t2 * (float) (Math.PI * 2.0);
            glVertex2f(cx + (float) Math.cos(a1) * outerR, cy + (float) Math.sin(a1) * outerR);
            glVertex2f(cx + (float) Math.cos(a2) * outerR, cy + (float) Math.sin(a2) * outerR);
        }
        glEnd();
    }
}
