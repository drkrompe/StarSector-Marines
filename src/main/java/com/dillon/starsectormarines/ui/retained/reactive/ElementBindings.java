package com.dillon.starsectormarines.ui.retained.reactive;

import com.dillon.starsectormarines.ui.retained.UiElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Reactive mutations that go through the ordinary retained element API. */
public final class ElementBindings {
    private final Reactor reactor;

    public ElementBindings(Reactor reactor) {
        this.reactor = Objects.requireNonNull(reactor, "reactor");
    }

    public Binding text(UiElement target, Supplier<String> value) {
        return reactor.bind(() -> target.text(value.get()));
    }

    public Binding disabled(UiElement target, Supplier<Boolean> value) {
        return reactor.bind(() -> target.disabled(Boolean.TRUE.equals(value.get())));
    }

    public Binding selected(UiElement target, Supplier<Boolean> value) {
        return reactor.bind(() -> target.selected(Boolean.TRUE.equals(value.get())));
    }

    public <T> Binding children(UiElement target,
                                Supplier<? extends List<T>> items,
                                Function<? super T, String> key,
                                Function<Signal<T>, UiElement> rowFactory) {
        if (target.childCount() != 0) {
            throw new IllegalArgumentException("A child-list binding owns an empty target");
        }
        KeyedChildren<T> keyed = new KeyedChildren<>(reactor, target, items, key, rowFactory);
        return reactor.bind(keyed::reconcile, keyed::dispose);
    }

    private static final class KeyedChildren<T> {
        private final Reactor reactor;
        private final UiElement target;
        private final Supplier<? extends List<T>> items;
        private final Function<? super T, String> key;
        private final Function<Signal<T>, UiElement> rowFactory;
        private final Map<String, Row<T>> rows = new LinkedHashMap<>();

        private KeyedChildren(Reactor reactor, UiElement target,
                              Supplier<? extends List<T>> items,
                              Function<? super T, String> key,
                              Function<Signal<T>, UiElement> rowFactory) {
            this.reactor = reactor;
            this.target = target;
            this.items = items;
            this.key = key;
            this.rowFactory = rowFactory;
        }

        private void reconcile() {
            List<T> current = items.get();
            if (current == null) throw new IllegalStateException("A bound child list is null");
            reactor.untracked(() -> apply(current));
        }

        private void apply(List<T> current) {
            List<String> keys = keysOf(current);
            Map<String, Row<T>> remaining = new LinkedHashMap<>(rows);
            Map<String, Row<T>> next = new LinkedHashMap<>(Math.max(4, current.size()));
            List<UiElement> order = new ArrayList<>(current.size());
            List<Row<T>> created = new ArrayList<>();
            List<Update<T>> updates = new ArrayList<>();
            try {
                for (int i = 0; i < current.size(); i++) {
                    T item = current.get(i);
                    String itemKey = keys.get(i);
                    Row<T> row = remaining.remove(itemKey);
                    if (row == null) {
                        row = createRow(itemKey, item);
                        created.add(row);
                    } else {
                        updates.add(new Update<>(row, item));
                    }
                    next.put(itemKey, row);
                    order.add(row.element());
                }
            } catch (RuntimeException failure) {
                for (Row<T> row : created) row.scope().close();
                throw failure;
            }
            for (Row<T> departed : remaining.values()) {
                departed.scope().close();
                target.remove(departed.element());
            }
            for (Update<T> update : updates) update.row().data().set(update.item());
            rows.clear();
            rows.putAll(next);
            for (int i = 0; i < order.size(); i++) {
                if (i >= target.childCount() || target.childAt(i) != order.get(i)) {
                    target.insert(i, order.get(i));
                }
            }
        }

        private List<String> keysOf(List<T> current) {
            List<String> keys = new ArrayList<>(current.size());
            Set<String> seen = new HashSet<>(Math.max(4, current.size()));
            for (T item : current) {
                String itemKey = key.apply(item);
                if (itemKey == null) throw new IllegalStateException("A bound row has a null key");
                if (!seen.add(itemKey)) {
                    throw new IllegalStateException("Bound child key \"" + itemKey + "\" occurs more than once");
                }
                keys.add(itemKey);
            }
            return keys;
        }

        private Row<T> createRow(String itemKey, T item) {
            MutableSignal<T> data = reactor.signal(item);
            BindingScope rowScope = new BindingScope();
            UiElement[] created = new UiElement[1];
            try {
                reactor.withScope(rowScope, () -> created[0] = rowFactory.apply(data));
            } catch (RuntimeException failure) {
                rowScope.close();
                throw failure;
            }
            if (created[0] == null) {
                rowScope.close();
                throw new IllegalStateException("No row for key \"" + itemKey + "\"");
            }
            created[0].key(itemKey);
            return new Row<>(data, created[0], rowScope);
        }

        private void dispose() {
            for (Row<T> row : rows.values()) row.scope().close();
            rows.clear();
        }
    }

    private record Update<T>(Row<T> row, T item) { }
    private record Row<T>(MutableSignal<T> data, UiElement element, BindingScope scope) { }
}
