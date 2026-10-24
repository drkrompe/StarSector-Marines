package com.dillon.starsectormarines.render2d.texture;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Values keyed by an object's identity, holding the key weakly: what {@link ModTextures} records about the sprites it
 * hands out, without keeping a finished battle's sprites alive and without asking a sprite for its {@code hashCode},
 * which a sprite is free to define however it likes.
 */
final class WeakIdentityMap<V> {

    private final ReferenceQueue<Object> cleared = new ReferenceQueue<>();
    private final Map<Key, V> values = new HashMap<>();

    synchronized void put(Object key, V value) {
        purge();
        values.put(new Key(key, cleared), value);
    }

    synchronized V get(Object key) {
        purge();
        return values.get(new Key(key, null));
    }

    private void purge() {
        for (Reference<?> gone = cleared.poll(); gone != null; gone = cleared.poll()) values.remove(gone);
    }

    private static final class Key extends WeakReference<Object> {
        private final int hash;

        Key(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            Object mine = get();
            return mine != null && mine == key.get();
        }
    }
}
