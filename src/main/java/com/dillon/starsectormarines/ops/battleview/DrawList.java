package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ContrailTrail;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.PagedQuadRun;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.QuadMesh;
import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Arrays;

/**
 * Per-frame, per-layer command buffer the renderer owns. Collecting passes emit
 * {@link DrawCommand}s tagged by {@link RenderLayer} through the typed
 * {@code add*} methods; {@link com.dillon.starsectormarines.render2d.DrawListRenderer}
 * drains each layer in submission order.
 *
 * <p><strong>Pooled.</strong> The command objects are reused across frames: each
 * layer keeps a growable {@code DrawCommand[]} plus a live count, and {@link #clear()}
 * just resets the counts (keeping the objects). Emitting overwrites the next slot
 * in place via {@code DrawCommand.set*}. A dense pass (GROUND, ~38k tiles) thus
 * allocates nothing in steady state — slots are only {@code new}'d while a layer's
 * high-water mark grows. Callers never construct a {@link DrawCommand}; the buffer
 * owns their lifecycle.
 *
 * <p>Not thread-safe; render is single-threaded.
 */
public final class DrawList {

    private static final int INITIAL_CAP = 64;

    private final DrawCommand[][] byLayer = new DrawCommand[RenderLayer.values().length][];
    private final int[] counts = new int[RenderLayer.values().length];

    /** Where a whole-image draw is re-addressed, or null for the sprite path. */
    private SpriteAtlas spriteAtlas;
    /** By layer ordinal: whether {@link #spriteAtlas} takes in what is drawn there and not yet held. */
    private final boolean[] gathered = new boolean[RenderLayer.values().length];
    /**
     * The camera's closest zoom over its zoom this frame: what turns a draw's size into the largest it can be drawn
     * at, which is how big a slot an image too large to hold whole needs ({@link UnitAtlas}). NaN when no camera
     * said, and then such an image stays on its own texture.
     */
    private float closestZoomFactor = Float.NaN;
    /** {@link #spriteAtlas}'s rectangles, as a mesh resolves them once. */
    private final QuadMesh.AtlasAddress atlasAddress = image -> {
        SpriteAtlas.Placement at = spriteAtlas.placement(image);
        if (at == null) return null;
        return at.trimmed()
                ? new int[]{at.x(), at.y(), at.width(), at.height(), at.trimX(), at.trimY(), at.fullW(), at.fullH()}
                : new int[]{at.x(), at.y(), at.width(), at.height()};
    };

    public DrawList() {
        for (int i = 0; i < byLayer.length; i++) {
            byLayer[i] = new DrawCommand[INITIAL_CAP];
        }
    }

    /** Reserve and return the next reusable slot for {@code layer}, growing if needed. */
    private DrawCommand slot(RenderLayer layer) {
        int li = layer.ordinal();
        DrawCommand[] buf = byLayer[li];
        int c = counts[li];
        if (c == buf.length) {
            buf = Arrays.copyOf(buf, c * 2);
            byLayer[li] = buf;
        }
        DrawCommand cmd = buf[c];
        if (cmd == null) {
            cmd = new DrawCommand();
            buf[c] = cmd;
        }
        counts[li] = c + 1;
        cmd.setEffects(flash, light, lightAngle, wear);
        return cmd;
    }

    /** The hit flash stamped on every command added until it is set again; see {@link #setFlash}. */
    private float flash;
    /** The packed light, its direction and the packed wear stamped likewise; see {@link #setLight}, {@link #setWear}. */
    private float light, lightAngle, wear;


    /**
     * Stamps every command added from now on with the light on it, each channel a multiple of its own colour added
     * to it, coming from {@code angleRadians} on screen (law 27); zero is none. Paged runs draw it.
     */
    public void setLight(float r, float g, float b, float angleRadians) {
        light = PagedQuadRun.packLight(r, g, b);
        lightAngle = light == 0f ? 0f : angleRadians;
    }

    /**
     * Stamps every command added from now on with its wear (law 27), each 0 to 1: {@code charred} by fire, glowing
     * {@code embers} while it burns, and {@code dissolved}, eaten away until gone at one. Paged runs draw it.
     */
    public void setWear(float charred, float embers, float dissolved) {
        wear = PagedQuadRun.packWear(charred, embers, dissolved);
    }

    /** Clears every per-quad effect: flash, light and wear. */
    public void clearEffects() {
        flash = 0f;
        light = 0f;
        lightAngle = 0f;
        wear = 0f;
    }

    /**
     * Stamps every command added from now on with a hit flash of {@code amount}, the share of white in its colour
     * (law 27), until set again: a unit's collector sets it before emitting the unit and zero after. Paged runs draw
     * it; the per-sheet path and the Java2D drain do not.
     */
    public void setFlash(float amount) {
        flash = amount;
    }

    public void addSheetQuad(RenderLayer layer, SpriteAPI sheet,
                             int srcX, int srcY, int srcW, int srcH,
                             float cx, float cy, float w, float h,
                             float r, float g, float b, float a) {
        TextureUsage.sheet(sheet, srcW, srcH, w, h);
        if (gathered[layer.ordinal()]
                && cutIntoAtlas(layer, sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, 0f, false, r, g, b, a)) return;
        slot(layer).setSheetQuad(sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, r, g, b, a);
    }

    /**
     * In a layer the atlas {@linkplain SpriteAtlas#gathers gathers}, a quad cut from a sheet the atlas holds whole is
     * addressed inside its slot, so it joins the run around it instead of flushing it twice; a sheet the atlas does
     * not hold is noted for it. False leaves the quad on its own sheet.
     */
    private boolean cutIntoAtlas(RenderLayer layer, SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH,
                                 float cx, float cy, float w, float h, float angleDeg, boolean flipV,
                                 float r, float g, float b, float a) {
        SpriteAtlas.Placement at = heldCut(layer, sheet, w, h, srcW, srcH);
        if (at == null) return false;
        ModTextures.Loaded loaded = spriteAtlas.loadedImage(sheet);
        if (loaded == null || loaded.width() <= 0 || loaded.height() <= 0) return false;
        // A fitted image's slot is its shipped size, so logical pixels scale into it.
        float sx = at.fullW() / (float) loaded.width();
        float sy = at.fullH() / (float) loaded.height();
        emitHeld(layer, at, Math.round(srcX * sx), Math.round(srcY * sy), Math.round(srcW * sx), Math.round(srcH * sy),
                cx, cy, w, h, angleDeg, flipV, false, r, g, b, a);
        return true;
    }

    /**
     * The {@code srcX, srcY, srcW x srcH} rectangle of a held image's slot (slot texels, {@code y} down from the top)
     * drawn over {@code w x h} at {@code (cx, cy)}, as a quad of the atlas sheet.
     *
     * <p>An image the atlas holds trimmed has only part of its slot in the texture, and every texel outside that is
     * transparent and out of reach of a bilinear tap that could colour a pixel. So the rectangle is clipped to the
     * part held and drawn over the matching part of its destination, turned with it, which paints what the whole
     * rectangle painted; a rectangle that lies wholly in the margin paints nothing and is not emitted.
     */
    private void emitHeld(RenderLayer layer, SpriteAtlas.Placement at, int srcX, int srcY, int srcW, int srcH,
                          float cx, float cy, float w, float h, float angleDeg, boolean flipV, boolean additive,
                          float r, float g, float b, float a) {
        int x0 = srcX, y0 = srcY, x1 = srcX + srcW, y1 = srcY + srcH;
        if (at.trimmed() && srcW > 0 && srcH > 0) {
            x0 = Math.max(x0, at.trimX());
            y0 = Math.max(y0, at.trimY());
            x1 = Math.min(x1, at.trimX() + at.width());
            y1 = Math.min(y1, at.trimY() + at.height());
            if (x1 <= x0 || y1 <= y0) return;
        }
        int ax = at.x() + x0 - at.trimX();
        int ay = at.y() + y0 - at.trimY();
        DrawCommand cmd = slot(layer);
        SpriteAPI sheet = spriteAtlas.sheet();
        if (additive) cmd.setAdditiveSheetQuad(sheet, ax, ay, x1 - x0, y1 - y0, cx, cy, w, h, angleDeg, r, g, b, a);
        else if (flipV) cmd.setSheetQuadFlippedV(sheet, ax, ay, x1 - x0, y1 - y0, cx, cy, w, h, angleDeg, r, g, b, a);
        else cmd.setSheetQuad(sheet, ax, ay, x1 - x0, y1 - y0, cx, cy, w, h, angleDeg, r, g, b, a);
        if (x0 != srcX || y0 != srcY || x1 != srcX + srcW || y1 != srcY + srcH) {
            // The destination stays the whole quad's, so the part's corners come from its corners.
            cmd.setPart((x0 - srcX) / (float) srcW, (x1 - srcX) / (float) srcW,
                    (y0 - srcY) / (float) srcH, (y1 - srcY) / (float) srcH);
        }
    }

    /** A convex clipped part of a tile; vertices are normalized within its uncut destination. */
    public void addSheetPatch(RenderLayer layer, SpriteAPI sheet,int sx,int sy,int sw,int sh,
                              float cx,float cy,float w,float h,float alpha,float[] patch) {
        TextureUsage.sheet(sheet, sw, sh, w, h);
        DrawCommand cmd=slot(layer);cmd.setSheetQuad(sheet,sx,sy,sw,sh,cx,cy,w,h,1,1,1,alpha);cmd.setPatch(patch);
    }

    /** Rotated sheet-quad: {@code angleDeg} rotates the dst rect about its center. */
    public void addSheetQuad(RenderLayer layer, SpriteAPI sheet,
                             int srcX, int srcY, int srcW, int srcH,
                             float cx, float cy, float w, float h, float angleDeg,
                             float r, float g, float b, float a) {
        TextureUsage.sheet(sheet, srcW, srcH, w, h);
        if (gathered[layer.ordinal()]
                && cutIntoAtlas(layer, sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, angleDeg, false, r, g, b, a)) return;
        slot(layer).setSheetQuad(sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, angleDeg, r, g, b, a);
    }

    /**
     * A rotated sub-rectangle of a whole image, {@code imageW x imageH} logical pixels: addressed inside the image's
     * atlas slot when the {@link UnitAtlas} holds it, so it joins the run its neighbours are in, and as a quad of
     * the image's own sheet otherwise. The atlas rule of {@link #addSprite}, for a caller that draws part of an
     * image rather than all of it (a scuff mark, one cell of its sheet).
     */
    public void addImageRegion(RenderLayer layer, SpriteAPI image, int imageW, int imageH,
                               int srcX, int srcY, int srcW, int srcH,
                               float cx, float cy, float w, float h, float angleDeg,
                               float r, float g, float b, float a) {
        SpriteAtlas.Placement at = srcW > 0 && srcH > 0
                ? held(layer, image, w * imageW / (float) srcW, h * imageH / (float) srcH)
                : held(layer, image, Float.NaN, Float.NaN);
        TextureUsage.sheet(image, srcW, srcH, w, h);
        if (at != null) {
            // A fitted image's slot is its shipped size, so logical pixels scale into it.
            float sx = at.fullW() / (float) imageW;
            float sy = at.fullH() / (float) imageH;
            emitHeld(layer, at, Math.round(srcX * sx), Math.round(srcY * sy), Math.round(srcW * sx),
                    Math.round(srcH * sy), cx, cy, w, h, angleDeg, false, false, r, g, b, a);
            return;
        }
        slot(layer).setSheetQuad(image, srcX, srcY, srcW, srcH, cx, cy, w, h, angleDeg, r, g, b, a);
    }

    /** Vertically-mirrored sheet-quad (axis-aligned): samples the sub-rect top↔bottom flipped. */
    public void addSheetQuadFlippedV(RenderLayer layer, SpriteAPI sheet,
                                     int srcX, int srcY, int srcW, int srcH,
                                     float cx, float cy, float w, float h,
                                     float r, float g, float b, float a) {
        TextureUsage.sheet(sheet, srcW, srcH, w, h);
        if (gathered[layer.ordinal()]
                && cutIntoAtlas(layer, sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, 0f, true, r, g, b, a)) return;
        slot(layer).setSheetQuadFlippedV(sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, r, g, b, a);
    }

    /** Vertically-mirrored sheet-quad at an angle — a half turn of it is a horizontal mirror. */
    public void addSheetQuadFlippedV(RenderLayer layer, SpriteAPI sheet,
                                     int srcX, int srcY, int srcW, int srcH,
                                     float cx, float cy, float w, float h, float angleDeg,
                                     float r, float g, float b, float a) {
        TextureUsage.sheet(sheet, srcW, srcH, w, h);
        if (gathered[layer.ordinal()]
                && cutIntoAtlas(layer, sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, angleDeg, true, r, g, b, a)) return;
        slot(layer).setSheetQuadFlippedV(sheet, srcX, srcY, srcW, srcH,
                cx, cy, w, h, angleDeg, r, g, b, a);
    }

    /**
     * A whole image drawn centred on {@code (cx, cy)}.
     *
     * <p><b>Where the atlas redirect lives.</b> An image the {@link UnitAtlas}
     * holds is emitted as a rotated sheet quad over its own slot instead of as a
     * foreign sprite call, so a run of them coalesces. It is done here rather
     * than in the composers because there are a dozen emit sites and one of them
     * disagreeing with the others would be a body drawn out of order; here there
     * is one rule, applied to every caller alike, and the command's layer,
     * position and order are untouched. In a layer the atlas
     * {@linkplain SpriteAtlas#gathers gathers}, an image it does not hold yet is
     * noted for it, so a later frame's collect finds it there.
     */
    public void addSprite(RenderLayer layer, SpriteAPI sprite,
                          float cx, float cy, float w, float h, float angleDeg,
                          float r, float g, float b, float a) {
        TextureUsage.sprite(sprite, w, h);
        SpriteAtlas.Placement at = held(layer, sprite, w, h);
        if (at != null) {
            emitHeld(layer, at, 0, 0, at.fullW(), at.fullH(), cx, cy, w, h, angleDeg, false, false, r, g, b, a);
            return;
        }
        slot(layer).setSprite(sprite, cx, cy, w, h, angleDeg, r, g, b, a);
    }

    /**
     * {@code image}'s slot while the atlas is serving and holds it, else null; an image drawn in a layer the atlas
     * {@linkplain SpriteAtlas#gathers gathers} that the atlas does not hold is noted for it on the way, with the
     * size this draw gives the whole image, {@code drawnW x drawnH} screen pixels, taken to the closest zoom.
     */
    private SpriteAtlas.Placement held(RenderLayer layer, SpriteAPI image, float drawnW, float drawnH) {
        if (spriteAtlas == null) return null;
        SpriteAtlas.Placement at = spriteAtlas.placement(image);
        if (at == null) {
            if (gathered[layer.ordinal()]) {
                spriteAtlas.noteMissed(image, Math.abs(drawnW) * closestZoomFactor,
                        Math.abs(drawnH) * closestZoomFactor);
            }
            return null;
        }
        return spriteAtlas.isServing() ? at : null;
    }

    /**
     * {@link #held} for the {@code srcW x srcH} cut of {@code sheet} drawn at {@code w x h}: the whole sheet's
     * drawn size is worked out only while the atlas still has the sheet to judge.
     */
    private SpriteAtlas.Placement heldCut(RenderLayer layer, SpriteAPI sheet, float w, float h, int srcW, int srcH) {
        if (spriteAtlas == null) return null;
        SpriteAtlas.Placement at = spriteAtlas.placement(sheet);
        if (at != null) return spriteAtlas.isServing() ? at : null;
        if (!gathered[layer.ordinal()] || !spriteAtlas.awaitsJudgement(sheet)) return null;
        float drawnW = Float.NaN;
        float drawnH = Float.NaN;
        ModTextures.Loaded loaded = srcW > 0 && srcH > 0 && closestZoomFactor > 0f
                ? spriteAtlas.loadedImage(sheet) : null;
        if (loaded != null && loaded.width() > 0 && loaded.height() > 0) {
            drawnW = Math.abs(w) * loaded.width() / srcW * closestZoomFactor;
            drawnH = Math.abs(h) * loaded.height() / srcH * closestZoomFactor;
        }
        spriteAtlas.noteMissed(sheet, drawnW, drawnH);
        return null;
    }

    /**
     * A recorded {@link QuadMesh} drawn with its origin at {@code (x, y)}, its local units {@code scale} px long,
     * every quad's alpha multiplied by {@code alphaMult}: one command where the quads it holds were one each.
     *
     * <p>The atlas rule is {@link #addSprite}'s, applied to the mesh as a whole: addressed into the atlas when
     * every image in it is held there, so it joins the sheet batch its neighbours are in; drawn through its
     * images otherwise.
     */
    public void addMesh(RenderLayer layer, QuadMesh mesh, float x, float y, float scale, float alphaMult) {
        if (TextureUsage.isEnabled()) {
            for (int i = 0; i < mesh.size(); i++) {
                TextureUsage.sprite(mesh.image(i), mesh.width(i) * scale, mesh.height(i) * scale);
            }
        }
        SpriteAPI sheet = null;
        if (spriteAtlas != null) {
            if (spriteAtlas.isServing()
                    && mesh.addressAtlas(spriteAtlas.layoutToken(), spriteAtlas.sheet(), atlasAddress)) {
                sheet = spriteAtlas.sheet();
            } else if (gathered[layer.ordinal()]) {
                for (int i = 0; i < mesh.size(); i++) {
                    held(layer, mesh.image(i), mesh.width(i) * scale, mesh.height(i) * scale);
                }
            }
        }
        slot(layer).setMesh(mesh, sheet, x, y, scale, alphaMult);
    }

    /**
     * Points whole-image draws at {@code atlas} for any image it holds.
     *
     * <p>Null — the default — leaves every sprite on the host's own sprite call,
     * which is what a host with no GL context of its own (the headless evidence
     * drains) and a host that never built one both get.
     */
    public void setSpriteAtlas(SpriteAtlas atlas) {
        this.spriteAtlas = atlas;
        for (RenderLayer layer : RenderLayer.values()) {
            gathered[layer.ordinal()] = atlas != null && atlas.gathers(layer);
        }
    }

    /**
     * The camera's closest zoom over its zoom, for the frame about to be collected; a value not above zero (or NaN)
     * when nothing can say, and then an image too large to hold whole is never given a slot.
     */
    public void setClosestZoomFactor(float factor) {
        this.closestZoomFactor = factor > 0f ? factor : Float.NaN;
    }

    /**
     * As {@link #addSprite}, but the sprite adds light instead of compositing over what is beneath it. The atlas rule
     * is the same: a held image is an additive quad of the atlas sheet, which the drain runs beside the normal ones.
     */
    public void addAdditiveSprite(RenderLayer layer, SpriteAPI sprite,
                                  float cx, float cy, float w, float h, float angleDeg,
                                  float r, float g, float b, float a) {
        TextureUsage.sprite(sprite, w, h);
        SpriteAtlas.Placement at = held(layer, sprite, w, h);
        if (at != null) {
            emitHeld(layer, at, 0, 0, at.fullW(), at.fullH(), cx, cy, w, h, angleDeg, false, true, r, g, b, a);
            return;
        }
        slot(layer).setSprite(sprite, cx, cy, w, h, angleDeg, r, g, b, a, true);
    }

    /** {@code (x0,y0)}–{@code (x1,y1)} are opposing screen-space corners. */
    public void addSolidRect(RenderLayer layer, float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        slot(layer).setSolidRect(x0, y0, x1, y1, r, g, b, a);
    }

    /** {@code (x0,y0)}–{@code (x1,y1)} are screen-space endpoints; {@code width} is the line width. */
    public void addLine(RenderLayer layer, float x0, float y0, float x1, float y1, float width,
                        float r, float g, float b, float a) {
        slot(layer).setLine(x0, y0, x1, y1, width, r, g, b, a);
    }

    /** Queue a contrail ribbon; the drain expands {@code trail}'s samples cell→screen at draw time. */
    public void addRibbon(RenderLayer layer, ContrailTrail trail, float alphaMult) {
        slot(layer).setRibbon(trail, alphaMult);
    }

    /** Queue a filled-poly fan (annulus / arc); the drain replays {@code mesh} into the shared solid batch. */
    public void addPoly(RenderLayer layer, PolyMesh mesh) {
        slot(layer).setPoly(mesh);
    }

    public void addCustom(RenderLayer layer, Runnable draw) {
        slot(layer).setCustom(draw);
    }

    /** The live command backing array for one layer (valid indices: {@code 0..count(layer)}). */
    public DrawCommand[] buffer(RenderLayer layer) {
        return byLayer[layer.ordinal()];
    }

    /** Number of commands queued for one layer this frame. */
    public int count(RenderLayer layer) {
        return counts[layer.ordinal()];
    }

    /** Resets every layer's count to 0, retaining the pooled command objects. Call once per frame. */
    public void clear() {
        Arrays.fill(counts, 0);
        clearEffects();
    }
}
