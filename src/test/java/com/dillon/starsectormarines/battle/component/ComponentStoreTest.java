package com.dillon.starsectormarines.battle.component;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link ComponentStore} — the presence-based, entity-id-
 * keyed component store. Covers attach/detach, absent-entity reads, replace,
 * size/isEmpty, and the insertion-ordered {@link ComponentStore#entries()} a
 * system iterates.
 */
public class ComponentStoreTest {

    @Test
    public void absentEntityHasNoComponent() {
        ComponentStore<String> store = new ComponentStore<>();
        assertTrue(store.isEmpty());
        assertEquals(0, store.size());
        assertFalse(store.has(7L));
        assertNull(store.get(7L), "get on an absent entity returns null, not a default");
    }

    @Test
    public void addThenGetAndHas() {
        ComponentStore<String> store = new ComponentStore<>();
        store.add(7L, "comp");
        assertTrue(store.has(7L));
        assertEquals("comp", store.get(7L));
        assertEquals(1, store.size());
        assertFalse(store.isEmpty());
    }

    @Test
    public void addReplacesAnExistingComponent() {
        ComponentStore<String> store = new ComponentStore<>();
        store.add(7L, "first");
        store.add(7L, "second");
        assertEquals("second", store.get(7L), "re-adding for the same entity replaces");
        assertEquals(1, store.size(), "replace does not grow the store");
    }

    @Test
    public void removeDetaches() {
        ComponentStore<String> store = new ComponentStore<>();
        store.add(7L, "comp");
        store.remove(7L);
        assertFalse(store.has(7L));
        assertNull(store.get(7L));
        assertTrue(store.isEmpty());
        // Removing an absent entity is a no-op.
        store.remove(999L);
    }

    @Test
    public void entriesIterateInInsertionOrder() {
        ComponentStore<String> store = new ComponentStore<>();
        store.add(30L, "c");
        store.add(10L, "a");
        store.add(20L, "b");

        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, String> e : store.entries()) {
            ids.add(e.getKey());
        }
        assertEquals(List.of(30L, 10L, 20L), ids,
                "iteration is insertion order (deterministic), not key order");
    }
}
