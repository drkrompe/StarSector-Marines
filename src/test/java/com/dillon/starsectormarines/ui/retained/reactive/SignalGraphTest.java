package com.dillon.starsectormarines.ui.retained.reactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalGraphTest {

    @Test
    void aWriteWakesOnlyBindingsThatReadItsSignal() {
        Reactor reactor = new Reactor();
        MutableSignal<Integer> wood = reactor.signal(0);
        MutableSignal<Integer> stone = reactor.signal(0);
        Binding woodBinding = reactor.bind(wood::get);
        Binding stoneBinding = reactor.bind(stone::get);

        wood.set(5);
        reactor.flush();

        assertEquals(2, woodBinding.runCount());
        assertEquals(1, stoneBinding.runCount());
    }

    @Test
    void sameValueWritesAndIdleFlushesCostNothing() {
        Reactor reactor = new Reactor();
        MutableSignal<String> season = reactor.signal("summer");
        Binding binding = reactor.bind(season::get);

        assertFalse(season.set("summer"));
        assertEquals(0, reactor.flush());
        assertEquals(1, binding.runCount());
    }

    @Test
    void dependenciesAreRecollectedAfterConditionalReads() {
        Reactor reactor = new Reactor();
        MutableSignal<Boolean> show = reactor.signal(true);
        MutableSignal<String> detail = reactor.signal("a");
        Binding binding = reactor.bind(() -> {
            if (show.get()) detail.get();
        });

        show.set(false);
        reactor.flush();
        int runs = binding.runCount();
        detail.set("b");
        reactor.flush();

        assertEquals(runs, binding.runCount());
        assertEquals(0, detail.subscriberCount());
    }

    @Test
    void computedSignalsAreLazyMemoisedAndGlitchFree() {
        Reactor reactor = new Reactor();
        MutableSignal<Integer> source = reactor.signal(1);
        AtomicInteger evaluations = new AtomicInteger();
        ComputedSignal<Integer> doubled = reactor.computed(() -> {
            evaluations.incrementAndGet();
            return source.get() * 2;
        });
        List<String> observations = new ArrayList<>();
        reactor.bind(() -> observations.add(source.get() + ":" + doubled.get()));

        assertEquals(1, evaluations.get());
        assertEquals(2, doubled.get());
        assertEquals(1, evaluations.get());
        source.set(4);
        reactor.flush();

        assertEquals(List.of("1:2", "4:8"), observations);
        assertEquals(2, evaluations.get());
    }

    @Test
    void unchangedComputedValueStopsPropagation() {
        Reactor reactor = new Reactor();
        MutableSignal<Integer> hour = reactor.signal(9);
        Signal<Boolean> daytime = reactor.computed(() -> hour.get() >= 6 && hour.get() < 18);
        Binding binding = reactor.bind(daytime::get);

        hour.set(10);
        reactor.flush();

        assertEquals(1, binding.runCount());
    }

    @Test
    void scopesDisposeTheirBindings() {
        Reactor reactor = new Reactor();
        MutableSignal<Integer> value = reactor.signal(0);
        BindingScope scope = new BindingScope();
        Binding[] captured = new Binding[1];
        reactor.withScope(scope, () -> captured[0] = reactor.bind(value::get));

        scope.close();
        value.set(1);
        reactor.flush();

        assertTrue(captured[0].isClosed());
        assertEquals(0, value.subscriberCount());
    }

    @Test
    void aBindingCycleIsReported() {
        Reactor reactor = new Reactor();
        MutableSignal<Integer> ping = reactor.signal(0);
        MutableSignal<Integer> pong = reactor.signal(0);
        reactor.bind(() -> pong.set(ping.get() + 1));
        reactor.bind(() -> ping.set(pong.get() + 1));
        ping.set(1);

        IllegalStateException failure = assertThrows(IllegalStateException.class, reactor::flush);
        assertTrue(failure.getMessage().contains("cycle"));
    }
}
