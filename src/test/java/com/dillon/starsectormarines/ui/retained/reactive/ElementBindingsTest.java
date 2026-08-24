package com.dillon.starsectormarines.ui.retained.reactive;

import com.dillon.starsectormarines.ui.retained.UiElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElementBindingsTest {
    private final Reactor reactor = new Reactor();
    private final ElementBindings bindings = new ElementBindings(reactor);

    @Test
    void textAndStateBindingsUseOrdinaryElementMutations() {
        UiElement element = new UiElement("row");
        MutableSignal<String> text = reactor.signal("Line");
        MutableSignal<Boolean> selected = reactor.signal(false);
        bindings.text(element, text::get);
        bindings.selected(element, selected::get);

        text.set("Recon");
        selected.set(true);
        reactor.flush();

        assertEquals("Recon", element.text());
        assertEquals(true, element.selected());
    }

    @Test
    void keyedReorderAndDataReplacementPreserveRowIdentity() {
        record Row(String id, String label) { }
        UiElement list = new UiElement("list");
        MutableSignal<List<Row>> rows = reactor.signal(List.of(
                new Row("line", "Line"), new Row("recon", "Recon")));
        bindings.children(list, rows::get, Row::id, row -> {
            UiElement element = new UiElement(row.get().id());
            bindings.text(element, () -> row.get().label());
            return element;
        });
        UiElement recon = list.childAt(1);

        rows.set(List.of(new Row("recon", "Recon updated"), new Row("line", "Line")));
        reactor.flush();

        assertSame(recon, list.childAt(0));
        assertEquals("Recon updated", recon.text());
    }

    @Test
    void duplicateKeysAreRejectedBeforeChildrenMove() {
        UiElement list = new UiElement("list");
        MutableSignal<List<String>> rows = reactor.signal(List.of("line"));
        bindings.children(list, rows::get, value -> value,
                value -> new UiElement(value.get()).text(value.get()));
        UiElement line = list.childAt(0);

        rows.set(List.of("line", "line"));

        assertThrows(IllegalStateException.class, reactor::flush);
        assertEquals(1, list.childCount());
        assertSame(line, list.childAt(0));
    }

    @Test
    void failedRowCreationLeavesExistingRowsAndTheirDataUntouched() {
        record Row(String id, String label) { }
        UiElement list = new UiElement("list");
        MutableSignal<List<Row>> rows = reactor.signal(List.of(new Row("line", "Line")));
        bindings.children(list, rows::get, Row::id, row -> {
            UiElement element = new UiElement(row.get().id());
            bindings.text(element, () -> row.get().label());
            if ("bad".equals(row.get().id())) throw new IllegalStateException("bad row");
            return element;
        });
        UiElement line = list.childAt(0);

        rows.set(List.of(new Row("line", "Changed too early"), new Row("bad", "Bad")));
        assertThrows(IllegalStateException.class, reactor::flush);
        assertSame(line, list.childAt(0));
        assertEquals("Line", line.text());

        rows.set(List.of(new Row("line", "Recovered"), new Row("recon", "Recon")));
        reactor.flush();
        assertSame(line, list.childAt(0));
        assertEquals("Recovered", line.text());
        assertEquals("Recon", list.childAt(1).text());
    }
}
