package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;

/**
 * World-layer producer for the compound capture-state markers. Reads
 * {@link CompoundService}'s records each frame and emits a faction-coloured
 * ring + capture-progress arc + kind glyph at each compound's centroid into the
 * {@code COMPOUND} layer of the draw list. Stateless w.r.t. game state — pure
 * derivation; the only field state is a wall-clock pulse timer (so contested
 * compounds visibly throb) and the reused per-frame {@link PolyMesh}.
 *
 * <p><strong>Command model.</strong> The filled ring + progress arc tessellate
 * into one {@code POLY} (the shared {@link PolyTess} fans); the hairline arc rim
 * emits {@code LINE} commands; the kind glyphs draw via one {@code CUSTOM} (bitmap
 * text is its own foreign-GL subsystem, like {@code SPRITE}'s {@code renderAtCenter}).
 * Emission order is fills → rims → glyphs, mirroring the per-marker
 * ring→arc→rim→glyph paint order (compounds don't overlap, so cross-marker
 * batching is visually identical).
 *
 * <p>Visual lineage: the SABOTAGE charge-site marker (see
 * {@code BattleRenderer.collectObjectiveMarkers}). Same shapes (filled ring +
 * progress arc + center icon), different palette (state-coloured rather than
 * always orange) and slightly larger footprint because compounds span a
 * multi-cell bbox.
 *
 * <p>Position is the {@link TacticalNode}'s bbox centroid (not the anchor): the
 * anchor is the doorway cell the AI targets, but the marker should sit on the
 * building's visual center so the player reads it as "that whole compound."
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

    /** Reused per-frame fan: every compound's ring + arc, emitted as one POLY. */
    private final PolyMesh fillMesh = new PolyMesh(256);

    public CompoundMarkerRenderer() {
        this.font = Fonts.ORBITRON_20;
    }

    /** Per-frame wall-clock advance — pulse phase only; sim pause shouldn't freeze the pulse (player still reads contested state during pauses). */
    public void update(float dtRealtime) {
        wallClock += dtRealtime;
    }

    /**
     * Emits the world-anchored markers — one ring/arc/glyph per compound record
     * in {@link CompoundService#getRecords()} — into the {@code COMPOUND} layer.
     * Collect is GL-free; the glyph {@code CUSTOM} runs its bitmap-font draws at
     * drain time.
     */
    public void collect(BattleSimulation sim, CompoundService service,
                        BattleCamera camera, DrawList out, float alphaMult) {
        if (service == null || sim == null || camera == null) return;
        if (service.getRecords().isEmpty()) return;

        float cellPx = camera.cellPxSize();
        fillMesh.reset();

        // Pass 1 — rings + arcs into one fill mesh (ring first, then arc, so the
        // arc paints over the ring in their overlap band — as the inline pass did).
        for (CompoundService.Record r : service.getRecords()) {
            float sx = centerX(camera, r);
            float sy = centerY(camera, r);

            Color ring = ringColorFor(r.state);
            float pulse = (r.state == CompoundService.CompoundState.CONTESTED)
                    ? 1f + CONTESTED_PULSE_AMP * (float) Math.sin(wallClock * 2.0 * Math.PI * CONTESTED_PULSE_HZ)
                    : 1f;
            float ringInner = (RING_RADIUS_CELLS - RING_THICKNESS_CELLS) * cellPx * pulse;
            float ringOuter = RING_RADIUS_CELLS * cellPx * pulse;
            PolyTess.appendAnnulus(fillMesh, sx, sy, ringInner, ringOuter, RING_SEGMENTS,
                    ring.getRed() / 255f, ring.getGreen() / 255f, ring.getBlue() / 255f, alphaMult);

            if (r.captureProgress > 0f) {
                float arcInner = ARC_RADIUS_CELLS * cellPx;
                float arcOuter = (ARC_RADIUS_CELLS + ARC_THICKNESS_CELLS) * cellPx;
                PolyTess.appendArc(fillMesh, sx, sy, arcInner, arcOuter, r.captureProgress, ARC_SEGMENTS,
                        ARC_TINT.getRed() / 255f, ARC_TINT.getGreen() / 255f, ARC_TINT.getBlue() / 255f, alphaMult);
            }
        }
        if (!fillMesh.isEmpty()) out.addPoly(RenderLayer.COMPOUND, fillMesh);

        // Pass 2 — hairline arc rims (outer edge), for legibility against bright
        // autotiles. One LINE per arc segment, half-alpha, on top of the fill.
        float rimR = ARC_TINT.getRed() / 255f, rimG = ARC_TINT.getGreen() / 255f, rimB = ARC_TINT.getBlue() / 255f;
        for (CompoundService.Record r : service.getRecords()) {
            if (r.captureProgress <= 0f) continue;
            float sx = centerX(camera, r);
            float sy = centerY(camera, r);
            float outerR = (ARC_RADIUS_CELLS + ARC_THICKNESS_CELLS) * cellPx;
            float progress = Math.min(1f, r.captureProgress);
            int filled = (int) Math.ceil(ARC_SEGMENTS * progress);
            for (int i = 0; i < filled; i++) {
                float t1 = (float) i / ARC_SEGMENTS;
                float t2 = Math.min(progress, (float) (i + 1) / ARC_SEGMENTS);
                float a1 = (float) (Math.PI / 2.0) - t1 * (float) (Math.PI * 2.0);
                float a2 = (float) (Math.PI / 2.0) - t2 * (float) (Math.PI * 2.0);
                out.addLine(RenderLayer.COMPOUND,
                        sx + (float) Math.cos(a1) * outerR, sy + (float) Math.sin(a1) * outerR,
                        sx + (float) Math.cos(a2) * outerR, sy + (float) Math.sin(a2) * outerR,
                        1f, rimR, rimG, rimB, 0.5f * alphaMult);
            }
        }

        // Pass 3 — kind glyphs, one CUSTOM (bitmap text = foreign-GL subsystem,
        // runs at drain). Recomputes centers from the camera at draw time.
        out.addCustom(RenderLayer.COMPOUND, () -> {
            font.ensureLoaded();
            for (CompoundService.Record r : service.getRecords()) {
                float sx = centerX(camera, r);
                float sy = centerY(camera, r);
                String glyph = glyphFor(r.node.kind);
                float glyphW = font.measureWidth(glyph);
                font.drawString(glyph,
                        sx - glyphW * 0.5f,
                        sy + font.getLineHeight() * 0.35f,
                        GLYPH_TINT, alphaMult);
            }
        });
    }

    private static float centerX(BattleCamera camera, CompoundService.Record r) {
        TacticalNode node = r.node;
        return camera.cellToScreenX((node.left + node.right + 1) * 0.5f);
    }

    private static float centerY(BattleCamera camera, CompoundService.Record r) {
        TacticalNode node = r.node;
        return camera.cellToScreenY((node.top + node.bottom + 1) * 0.5f);
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
}
