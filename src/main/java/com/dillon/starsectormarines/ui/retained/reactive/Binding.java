package com.dillon.starsectormarines.ui.retained.reactive;

import java.util.Objects;

/** One precise invalidation edge from signals to a retained mutation. */
public final class Binding implements ReactiveNode, AutoCloseable {
    private final Reactor reactor;
    private final Runnable body;
    private final DependencySet dependencies = new DependencySet(this);
    private final Runnable onClose;
    private boolean queued;
    private boolean closed;
    private boolean evaluated;
    private int runCount;

    Binding(Reactor reactor, Runnable body, Runnable onClose) {
        this.reactor = reactor;
        this.body = Objects.requireNonNull(body, "body");
        this.onClose = onClose;
    }

    @Override
    public void markStale(long epoch) {
        if (closed || queued) return;
        queued = true;
        reactor.enqueue(this);
    }

    void run() {
        queued = false;
        if (closed) return;
        if (evaluated && !reactor.untracked(dependencies::anyChanged)) return;
        evaluated = true;
        runCount++;
        reactor.evaluate(dependencies, () -> {
            body.run();
            return null;
        });
    }

    public int runCount() {
        return runCount;
    }

    public int dependencyCount() {
        return dependencies.size();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        queued = false;
        dependencies.clear();
        if (onClose != null) onClose.run();
    }
}
