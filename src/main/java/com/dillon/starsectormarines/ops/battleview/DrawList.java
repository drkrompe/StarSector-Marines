package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.DrawCommand;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Per-frame collector the renderer owns. Collecting passes append
 * {@link DrawCommand}s tagged by {@link RenderLayer}; {@link BattleRenderer}
 * drains layer-by-layer in ordinal order.
 *
 * <p>One instance lives on {@link BattleRenderer} and is {@link #clear() cleared}
 * at the top of each {@code renderWorld} call — the per-layer lists keep their
 * backing capacity across frames so a steady-state frame allocates nothing here.
 *
 * <p>Not thread-safe; render is single-threaded.
 */
public final class DrawList {

    private final EnumMap<RenderLayer, List<DrawCommand>> byLayer = new EnumMap<>(RenderLayer.class);

    public DrawList() {
        for (RenderLayer layer : RenderLayer.values()) {
            byLayer.put(layer, new ArrayList<>());
        }
    }

    /** Append one command to its layer. */
    public void add(RenderLayer layer, DrawCommand cmd) {
        byLayer.get(layer).add(cmd);
    }

    /** The submission-ordered commands queued for one layer (live list — do not mutate externally). */
    public List<DrawCommand> commands(RenderLayer layer) {
        return byLayer.get(layer);
    }

    /** Empties every layer's queue, retaining backing capacity. Call once per frame. */
    public void clear() {
        for (List<DrawCommand> list : byLayer.values()) {
            list.clear();
        }
    }
}
