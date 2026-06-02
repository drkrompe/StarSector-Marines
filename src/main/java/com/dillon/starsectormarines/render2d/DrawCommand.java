package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * One unit of deferred world-render work, replayed in submission order by the
 * drain ({@link DrawListRenderer}).
 *
 * <p><strong>Pooled, mutable command buffer.</strong> Commands are <em>not</em>
 * immutable value objects — they are recycled slots owned by the per-frame draw
 * list. A dense full-grid pass (GROUND, up to ~38k cells) emits one command per
 * tile every frame; allocating a fresh object per tile would churn the GC on the
 * hottest render path. Instead the draw list keeps a growable array of these and
 * overwrites them in place each frame via {@code set*}; steady-state allocation
 * is zero. Hence one tagged type with a {@link Kind} + a union of fields rather
 * than a sealed record hierarchy.
 *
 * <p>Field interpretation depends on {@link #kind}:
 * <ul>
 *   <li>{@code SHEET_QUAD} — {@code sprite} = sheet; {@code src*} = sub-rect in
 *       sheet pixels; {@code cx/cy} = dst center, {@code w/h} = dst size; tint
 *       {@code r/g/b/a}. Batched per-sheet through a {@link QuadBatch}.</li>
 *   <li>{@code SPRITE} — {@code sprite} = whole-texture sprite; {@code cx/cy} =
 *       center, {@code w/h} = size, {@code angleDeg} = rotation; tint
 *       {@code r/g/b/a}. Drawn via {@code renderAtCenter} (no batching win).</li>
 *   <li>{@code SOLID_RECT} — untextured fill; {@code cx/cy} reused as corner
 *       {@code (x0,y0)} and {@code w/h} as the opposing corner {@code (x1,y1)};
 *       color {@code r/g/b/a}. Batched through a {@link SolidQuadBatch}.</li>
 *   <li>{@code LINE} — untextured line segment; {@code cx/cy} = start
 *       {@code (x0,y0)}, {@code w/h} = end {@code (x1,y1)}, {@code angleDeg}
 *       reused as the line width; color {@code r/g/b/a}. Batched through a
 *       {@link LineBatch} (width is per-flush state — the drain flushes on a
 *       width change).</li>
 *   <li>{@code RIBBON} — a contrail/plume sample history; {@code trail} = the
 *       {@link ContrailTrail}, {@code a} = the alpha mult folded into the
 *       ribbon's per-vertex alpha. Unlike every other kind (which carries
 *       pre-converted screen-space coords), the ribbon's vertices are expanded
 *       cell→screen at drain time, so the drain feeds it the camera. Batched
 *       through a {@link RibbonBatch}.</li>
 *   <li>{@code POLY} — a filled solid-color shape (annulus / progress arc) too
 *       big for inline fields; {@code poly} = the {@link PolyMesh} of trapezoid
 *       quads the producer tessellated this frame. Like {@code RIBBON}, a
 *       variable-length geometry carrier rather than inline fields. Replayed into
 *       the shared {@link SolidQuadBatch} at drain (coalesces with
 *       {@code SOLID_RECT}).</li>
 *   <li>{@code CUSTOM} — {@code custom} owns its own GL state (FBO blits, the
 *       lightmap multiply); the drain just runs it.</li>
 * </ul>
 *
 * <p>Written through the public {@code set*} methods (called cross-package by the
 * game's draw list); read field-direct by the same-package drain.
 */
public final class DrawCommand {

    public enum Kind { SHEET_QUAD, SPRITE, SOLID_RECT, LINE, RIBBON, POLY, CUSTOM }

    Kind kind;
    SpriteAPI sprite;
    int srcX, srcY, srcW, srcH;
    float cx, cy, w, h;
    float angleDeg;
    /** {@code SHEET_QUAD} only: sample the sub-rect mirrored vertically (the SOUTH-weapon-up flip). Always axis-aligned. */
    boolean flipV;
    float r, g, b, a;
    /** {@code RIBBON} only: the contrail sample history the drain expands. */
    ContrailTrail trail;
    /** {@code POLY} only: the tessellated solid-quad fan (annulus / arc) the drain replays. */
    PolyMesh poly;
    Runnable custom;

    public void setSheetQuad(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH,
                             float cx, float cy, float w, float h,
                             float r, float g, float b, float a) {
        setSheetQuad(sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, 0f, r, g, b, a);
    }

    /**
     * Rotated variant of {@link #setSheetQuad}: {@code angleDeg} rotates the dst
     * rect (CCW) about its center; the sub-rect still samples axis-aligned. The
     * drain routes {@code angleDeg != 0} through {@link QuadBatch#appendRotated}
     * and keeps the axis-aligned fast path for the dense tile layers. Reuses the
     * {@code SHEET_QUAD} kind so rotated and unrotated quads batch on one sheet.
     */
    public void setSheetQuad(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH,
                             float cx, float cy, float w, float h, float angleDeg,
                             float r, float g, float b, float a) {
        this.kind = Kind.SHEET_QUAD;
        this.sprite = sheet;
        this.srcX = srcX; this.srcY = srcY; this.srcW = srcW; this.srcH = srcH;
        this.cx = cx; this.cy = cy; this.w = w; this.h = h; this.angleDeg = angleDeg;
        this.flipV = false;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.trail = null;
        this.poly = null;
        this.custom = null;
    }

    /**
     * Vertically-mirrored {@code SHEET_QUAD} (axis-aligned): the dst rect samples
     * the sub-rect top↔bottom flipped. The drain routes this through
     * {@link QuadBatch#appendFlippedV}. Used for the SOUTH-weapon-up infantry pose.
     */
    public void setSheetQuadFlippedV(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH,
                                     float cx, float cy, float w, float h,
                                     float r, float g, float b, float a) {
        setSheetQuad(sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, 0f, r, g, b, a);
        this.flipV = true;
    }

    public void setSprite(SpriteAPI sprite,
                          float cx, float cy, float w, float h, float angleDeg,
                          float r, float g, float b, float a) {
        this.kind = Kind.SPRITE;
        this.sprite = sprite;
        this.cx = cx; this.cy = cy; this.w = w; this.h = h; this.angleDeg = angleDeg;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.trail = null;
        this.poly = null;
        this.custom = null;
    }

    /** {@code (x0,y0)}–{@code (x1,y1)} are opposing corners (screen space). */
    public void setSolidRect(float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        this.kind = Kind.SOLID_RECT;
        this.sprite = null;
        this.cx = x0; this.cy = y0; this.w = x1; this.h = y1;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.trail = null;
        this.poly = null;
        this.custom = null;
    }

    /**
     * {@code (x0,y0)}–{@code (x1,y1)} are the segment endpoints (screen space);
     * {@code width} is the line width (reuses the {@code angleDeg} slot).
     */
    public void setLine(float x0, float y0, float x1, float y1, float width,
                        float r, float g, float b, float a) {
        this.kind = Kind.LINE;
        this.sprite = null;
        this.cx = x0; this.cy = y0; this.w = x1; this.h = y1;
        this.angleDeg = width;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.trail = null;
        this.poly = null;
        this.custom = null;
    }

    /**
     * {@code trail} is the live contrail sample history (expanded cell→screen at
     * drain time by a {@link RibbonBatch}); {@code alphaMult} is folded into every
     * ribbon vertex's alpha.
     */
    public void setRibbon(ContrailTrail trail, float alphaMult) {
        this.kind = Kind.RIBBON;
        this.sprite = null;
        this.trail = trail;
        this.a = alphaMult;
        this.poly = null;
        this.custom = null;
    }

    /** {@code mesh} is the producer-owned tessellated fan, replayed into the shared solid batch at drain. */
    public void setPoly(PolyMesh mesh) {
        this.kind = Kind.POLY;
        this.sprite = null;
        this.trail = null;
        this.poly = mesh;
        this.custom = null;
    }

    public void setCustom(Runnable custom) {
        this.kind = Kind.CUSTOM;
        this.sprite = null;
        this.trail = null;
        this.poly = null;
        this.custom = custom;
    }
}
