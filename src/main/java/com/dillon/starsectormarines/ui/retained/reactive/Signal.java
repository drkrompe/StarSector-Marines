package com.dillon.starsectormarines.ui.retained.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A reactive cell; reads inside a binding establish precise dependencies. */
public abstract sealed class Signal<T> permits MutableSignal, ComputedSignal {
    final Reactor reactor;
    final List<ReactiveNode> subscribers = new ArrayList<>(2);
    long version;

    Signal(Reactor reactor) {
        this.reactor = reactor;
    }

    public abstract T get();

    public long version() {
        return version;
    }

    public Reactor reactor() {
        return reactor;
    }

    public <R> Signal<R> map(Function<? super T, ? extends R> mapper) {
        return reactor.computed(() -> mapper.apply(get()));
    }

    void subscribe(ReactiveNode node) {
        if (!subscribers.contains(node)) subscribers.add(node);
    }

    void unsubscribe(ReactiveNode node) {
        subscribers.remove(node);
    }

    public int subscriberCount() {
        return subscribers.size();
    }
}
