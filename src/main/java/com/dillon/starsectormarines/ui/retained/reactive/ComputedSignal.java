package com.dillon.starsectormarines.ui.retained.reactive;

import java.util.Objects;
import java.util.function.Supplier;

/** Lazily recomputed, memoised signal derived from other signals. */
public final class ComputedSignal<T> extends Signal<T> implements ReactiveNode {
    private final Supplier<? extends T> body;
    private final DependencySet dependencies = new DependencySet(this);
    private T value;
    private boolean computed;
    private boolean computing;
    private long validatedAt = -1;
    private long notifiedEpoch = -1;

    ComputedSignal(Reactor reactor, Supplier<? extends T> body) {
        super(reactor);
        this.body = Objects.requireNonNull(body, "body");
    }

    @Override
    public T get() {
        reactor.recordRead(this);
        if (computed && validatedAt == reactor.writeCount()) return value;
        reactor.untracked(() -> {
            if (!computed || dependencies.isEmpty() || dependencies.anyChanged()) recompute();
        });
        validatedAt = reactor.writeCount();
        return value;
    }

    public T peek() {
        return value;
    }

    private void recompute() {
        if (computing) throw new IllegalStateException("Computed signal depends on itself");
        computing = true;
        try {
            T next = reactor.evaluate(dependencies, body);
            if (!computed || !Objects.equals(value, next)) {
                value = next;
                version++;
            }
            computed = true;
        } finally {
            computing = false;
        }
    }

    @Override
    public void markStale(long epoch) {
        if (notifiedEpoch == epoch) return;
        notifiedEpoch = epoch;
        for (int i = 0; i < subscribers.size(); i++) subscribers.get(i).markStale(epoch);
    }

    public void dispose() {
        dependencies.clear();
        computed = false;
        validatedAt = -1;
    }

    public int dependencyCount() {
        return dependencies.size();
    }
}
