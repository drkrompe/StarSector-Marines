package com.dillon.starsectormarines.ui.retained.reactive;

/** A binding or computed value that reads signals. */
sealed interface ReactiveNode permits Binding, ComputedSignal {
    void markStale(long epoch);
}
