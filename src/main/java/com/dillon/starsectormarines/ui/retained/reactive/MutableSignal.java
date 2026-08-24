package com.dillon.starsectormarines.ui.retained.reactive;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** Directly writable signal with write-if-changed semantics. */
public final class MutableSignal<T> extends Signal<T> {
    private T value;

    MutableSignal(Reactor reactor, T initial) {
        super(reactor);
        value = initial;
    }

    @Override
    public T get() {
        reactor.recordRead(this);
        return value;
    }

    public T peek() {
        return value;
    }

    public boolean set(T next) {
        if (Objects.equals(value, next)) return false;
        value = next;
        version++;
        reactor.sourceChanged(this);
        return true;
    }

    public boolean update(UnaryOperator<T> operator) {
        return set(operator.apply(value));
    }
}
