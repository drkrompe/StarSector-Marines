package com.dillon.starsectormarines.battle.perception;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Thread-safe per-battle mailbox for transient noises. Producers may post from
 * parallel firing; the serial squad-alert pass drains each event exactly once.
 */
public final class NoiseEventBus {

    private static final Comparator<NoiseEvent> STABLE_ORDER =
            Comparator.comparingInt(NoiseEvent::emittedTick)
                    .thenComparing(event -> event.kind().ordinal())
                    .thenComparingLong(NoiseEvent::sourceUnitId)
                    .thenComparingDouble(NoiseEvent::x)
                    .thenComparingDouble(NoiseEvent::y)
                    .thenComparingDouble(NoiseEvent::magnitude);

    private final IntSupplier tickSupplier;
    private final List<NoiseEvent> pending = new ArrayList<>();

    public NoiseEventBus(IntSupplier tickSupplier) {
        this.tickSupplier = tickSupplier;
    }

    public void post(float x, float y, float magnitude, long sourceUnitId,
                     Faction sourceFaction, NoiseKind kind) {
        if (magnitude <= 0f || kind == null) return;
        NoiseEvent event = new NoiseEvent(x, y, magnitude, sourceUnitId,
                sourceFaction, kind, tickSupplier.getAsInt());
        synchronized (pending) {
            pending.add(event);
        }
    }

    /** Returns every pending event in deterministic order and empties the bus. */
    public List<NoiseEvent> drain() {
        synchronized (pending) {
            if (pending.isEmpty()) return List.of();
            List<NoiseEvent> drained = new ArrayList<>(pending);
            pending.clear();
            drained.sort(STABLE_ORDER);
            return drained;
        }
    }

    /** Diagnostic/test-only count; production consumers should use {@link #drain()}. */
    public int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }
}
