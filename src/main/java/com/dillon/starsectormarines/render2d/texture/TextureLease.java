package com.dillon.starsectormarines.render2d.texture;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One owner's claim on the textures it draws: a battle, or the session itself.
 *
 * <p>Everything an owner loads goes through its lease, and releasing the lease gives every one
 * of those textures back to the {@link TextureLedger}, which unloads those nobody else holds.
 * A sprite handed out under a lease is dead once the lease is released — its GL texture may be
 * deleted, and a later load of the same path gets a different one — so an owner drops every
 * sprite it holds when it releases, and loads afresh under its next lease.
 */
public final class TextureLease {

    private final TextureLedger ledger;
    private final String name;
    private final boolean permanent;
    private final Set<String> paths = new LinkedHashSet<>();

    TextureLease(TextureLedger ledger, String name, boolean permanent) {
        this.ledger = ledger;
        this.name = name;
        this.permanent = permanent;
    }

    public String name() {
        return name;
    }

    /** Loads {@code path} if it is not resident and holds it until this lease is released. */
    public void acquire(String path) throws IOException {
        ledger.acquire(this, path);
        paths.add(path);
    }

    /** Whether this lease holds {@code path}. */
    public boolean holds(String path) {
        return paths.contains(path);
    }

    /** The paths held, in the order they were first acquired. */
    public Set<String> paths() {
        return Collections.unmodifiableSet(paths);
    }

    /** Bytes on the card of the textures this lease holds, shared ones counted in full. */
    public long bytesHeld() {
        return ledger.bytesHeldBy(this);
    }

    /**
     * Gives every texture back. Those no other lease holds are unloaded; the lease is empty
     * afterwards and may acquire again.
     *
     * @throws IllegalStateException for the session lease, which is never released
     */
    public TextureLedger.Release release() {
        if (permanent) throw new IllegalStateException("the " + name + " lease is never released");
        TextureLedger.Release result = ledger.release(this, paths);
        paths.clear();
        return result;
    }
}
