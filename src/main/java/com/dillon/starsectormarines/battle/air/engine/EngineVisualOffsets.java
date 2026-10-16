package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import java.util.Map;

/** Artwork-only nozzle corrections in native canvas pixels; never supplied to handling or thruster demand. */
public final class EngineVisualOffsets {
    public record Offset(float lateralPixels, float forwardPixels) {
        public Offset {
            if (!Float.isFinite(lateralPixels) || !Float.isFinite(forwardPixels))
                throw new IllegalArgumentException("Engine appearance offsets must be finite");
        }
    }
    public static final EngineVisualOffsets NONE = new EngineVisualOffsets(Map.of());
    private final Map<Integer, Offset> offsets;
    private EngineSlotData[] cachedNative, cachedVisual;

    /** Native engine array index; +X is right and +Y is nose, independent of packed texture resolution. */
    public EngineVisualOffsets(Map<Integer, Offset> offsets) {
        this.offsets = Map.copyOf(offsets);
        if (this.offsets.keySet().stream().anyMatch(index -> index < 0))
            throw new IllegalArgumentException("Engine appearance indices cannot be negative");
    }

    /** Reuses native slots when unmodified; corrected arrays are cached by native array identity without mutating it. */
    public synchronized EngineSlotData[] applyTo(EngineSlotData[] nativeSlots) {
        if (offsets.isEmpty() || nativeSlots.length == 0) return nativeSlots;
        if (nativeSlots == cachedNative) return cachedVisual;
        EngineSlotData[] visual = nativeSlots.clone();
        float cellsPerPixel = AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
        for (int index = 0; index < visual.length; index++) {
            Offset offset = offsets.get(index);
            if (offset == null) continue;
            EngineSlotData slot = nativeSlots[index];
            visual[index] = new EngineSlotData(slot.localX + offset.lateralPixels * cellsPerPixel,
                    slot.localY + offset.forwardPixels * cellsPerPixel, slot.angleDegrees,
                    slot.lengthCells, slot.widthCells, slot.contrailSizeCells, slot.style);
        }
        cachedNative = nativeSlots; cachedVisual = visual;
        return visual;
    }
}
