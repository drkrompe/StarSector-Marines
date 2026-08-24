package com.dillon.starsectormarines.ui.retained.reactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Signal dependencies and version stamps from one reactive evaluation. */
final class DependencySet {
    private final ReactiveNode owner;
    private final List<Signal<?>> signals = new ArrayList<>(4);
    private long[] versions = new long[4];
    private boolean collecting;

    DependencySet(ReactiveNode owner) {
        this.owner = owner;
    }

    void beginCollect() {
        clear();
        collecting = true;
    }

    void record(Signal<?> signal) {
        if (!collecting || signals.contains(signal)) return;
        signals.add(signal);
        signal.subscribe(owner);
    }

    void endCollect() {
        collecting = false;
        if (versions.length < signals.size()) {
            versions = new long[Math.max(signals.size(), versions.length * 2)];
        }
        for (int i = 0; i < signals.size(); i++) versions[i] = signals.get(i).version();
    }

    boolean anyChanged() {
        for (int i = 0; i < signals.size(); i++) {
            Signal<?> signal = signals.get(i);
            signal.get();
            if (signal.version() != versions[i]) return true;
        }
        return false;
    }

    boolean isEmpty() {
        return signals.isEmpty();
    }

    int size() {
        return signals.size();
    }

    void clear() {
        for (Signal<?> signal : signals) signal.unsubscribe(owner);
        signals.clear();
        Arrays.fill(versions, 0L);
    }
}
