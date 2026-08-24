package com.dillon.starsectormarines.ui.retained.reactive;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Supplier;

/** Owns one signal graph and its explicit per-frame binding flush. */
public final class Reactor {
    private static final int MAX_GENERATIONS = 32;
    private final ArrayDeque<Binding> pending = new ArrayDeque<>();
    private DependencySet collecting;
    private BindingScope scope;
    private long writeCount;
    private long epoch;
    private boolean flushing;

    public <T> MutableSignal<T> signal(T initial) {
        return new MutableSignal<>(this, initial);
    }

    public <T> ComputedSignal<T> computed(Supplier<? extends T> body) {
        ComputedSignal<T> computed = new ComputedSignal<>(this, body);
        if (scope != null) scope.add(computed);
        return computed;
    }

    public Binding bind(Runnable body) {
        return bind(body, null);
    }

    Binding bind(Runnable body, Runnable onClose) {
        Binding binding = new Binding(this, body, onClose);
        if (scope != null) scope.add(binding);
        binding.run();
        return binding;
    }

    public void untracked(Runnable body) {
        untracked(() -> {
            body.run();
            return null;
        });
    }

    public <R> R untracked(Supplier<R> body) {
        DependencySet previous = collecting;
        collecting = null;
        try {
            return body.get();
        } finally {
            collecting = previous;
        }
    }

    public void withScope(BindingScope next, Runnable body) {
        BindingScope previous = scope;
        scope = Objects.requireNonNull(next, "scope");
        try {
            body.run();
        } finally {
            scope = previous;
        }
    }

    void recordRead(Signal<?> signal) {
        if (collecting != null) collecting.record(signal);
    }

    <R> R evaluate(DependencySet dependencies, Supplier<R> body) {
        DependencySet previous = collecting;
        collecting = dependencies;
        dependencies.beginCollect();
        try {
            return body.get();
        } finally {
            dependencies.endCollect();
            collecting = previous;
        }
    }

    void sourceChanged(Signal<?> source) {
        writeCount++;
        long currentEpoch = ++epoch;
        for (int i = 0; i < source.subscribers.size(); i++) {
            source.subscribers.get(i).markStale(currentEpoch);
        }
    }

    void enqueue(Binding binding) {
        pending.add(binding);
    }

    public int flush() {
        if (flushing) throw new IllegalStateException("Reactor.flush is already running");
        flushing = true;
        int ran = 0;
        try {
            int generation = 0;
            while (!pending.isEmpty()) {
                if (++generation > MAX_GENERATIONS) {
                    throw new IllegalStateException("Reactive binding cycle after "
                            + MAX_GENERATIONS + " generations");
                }
                for (int remaining = pending.size(); remaining > 0; remaining--) {
                    Binding binding = pending.poll();
                    if (binding != null) {
                        binding.run();
                        ran++;
                    }
                }
            }
        } finally {
            flushing = false;
        }
        return ran;
    }

    public int pendingCount() {
        return pending.size();
    }

    long writeCount() {
        return writeCount;
    }
}
