package com.dillon.starsectormarines.ui.retained.reactive;

import java.util.ArrayList;
import java.util.List;

/** Bindings and computed values that share one declared lifetime. */
public final class BindingScope implements AutoCloseable {
    private final List<Binding> bindings = new ArrayList<>(4);
    private final List<ComputedSignal<?>> computeds = new ArrayList<>(1);
    private boolean closed;

    void add(Binding binding) {
        if (closed) binding.close(); else bindings.add(binding);
    }

    void add(ComputedSignal<?> computed) {
        if (closed) computed.dispose(); else computeds.add(computed);
    }

    public int size() {
        return bindings.size() + computeds.size();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Binding binding : bindings) binding.close();
        for (ComputedSignal<?> computed : computeds) computed.dispose();
        bindings.clear();
        computeds.clear();
    }
}
