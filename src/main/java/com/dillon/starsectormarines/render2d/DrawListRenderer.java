package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The engine-side drain: replays a flat list of {@link DrawCommand}s in
 * submission order, turning the deferred command stream into batched GL calls.
 *
 * <p>A run of consecutive {@link DrawCommand.SheetQuad}s is grouped by sheet,
 * appended to each sheet's {@link QuadBatch} (resolved through the caller-owned
 * {@code batchBySheet} registry), and flushed under one
 * {@link GlStateBracket#textured2D()}; a run of {@link DrawCommand.Sprite}s
 * shares one bracket and renders each via {@code renderAtCenter}; each
 * {@link DrawCommand.Custom} runs standalone (it owns its own GL state).
 *
 * <p>Pure mechanism: this knows nothing about <em>which</em> layers exist or
 * <em>what order</em> they paint in — the caller (game side) owns that and hands
 * over one layer's commands plus the sheet→batch map. The render-side analog of
 * a sim system's stateless tick.
 */
public final class DrawListRenderer {

    private DrawListRenderer() {}

    /**
     * Drain {@code cmds} into GL, resolving {@link DrawCommand.SheetQuad} sheets
     * through {@code batchBySheet}. See the class doc for the per-command-kind
     * batching contract.
     */
    public static void drain(List<DrawCommand> cmds, Map<SpriteAPI, QuadBatch> batchBySheet) {
        int n = cmds.size();
        int i = 0;
        while (i < n) {
            DrawCommand c = cmds.get(i);
            if (c instanceof DrawCommand.SheetQuad) {
                try (GlStateBracket gl = GlStateBracket.textured2D()) {
                    // First-touched order; cross-sheet doodad overlap doesn't occur,
                    // so inter-sheet flush order is immaterial.
                    LinkedHashSet<QuadBatch> touched = new LinkedHashSet<>();
                    while (i < n && cmds.get(i) instanceof DrawCommand.SheetQuad sq) {
                        QuadBatch b = batchBySheet.get(sq.sheet());
                        if (b != null) {
                            b.append(sq.srcX(), sq.srcY(), sq.srcW(), sq.srcH(),
                                    sq.cx(), sq.cy(), sq.w(), sq.h(),
                                    sq.r(), sq.g(), sq.b(), sq.a());
                            touched.add(b);
                        }
                        i++;
                    }
                    for (QuadBatch b : touched) b.flush();
                }
            } else if (c instanceof DrawCommand.Sprite) {
                try (GlStateBracket gl = GlStateBracket.textured2D()) {
                    while (i < n && cmds.get(i) instanceof DrawCommand.Sprite sp) {
                        drawSprite(sp);
                        i++;
                    }
                }
            } else if (c instanceof DrawCommand.Custom custom) {
                custom.draw().run();
                i++;
            } else {
                i++;
            }
        }
    }

    private static void drawSprite(DrawCommand.Sprite q) {
        SpriteAPI sprite = q.sprite();
        sprite.setSize(q.w(), q.h());
        sprite.setAngle(q.angleDeg());
        sprite.setAlphaMult(q.a());
        sprite.setNormalBlend();
        // Avoid a per-quad Color alloc for the common untinted case (all SHOTS
        // projectiles are white); only build a Color when an actual tint is set.
        sprite.setColor(q.r() == 1f && q.g() == 1f && q.b() == 1f
                ? Color.WHITE : new Color(q.r(), q.g(), q.b()));
        sprite.renderAtCenter(q.cx(), q.cy());
        // Reset rotation so the shared cached sprite carries no angle into other passes.
        sprite.setAngle(0f);
    }
}
